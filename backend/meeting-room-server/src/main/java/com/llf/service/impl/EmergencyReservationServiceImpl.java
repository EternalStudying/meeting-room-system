package com.llf.service.impl;

import com.llf.dto.admin.reservation.EmergencyReservationRequestDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.EmergencyReservationService;
import com.llf.service.NotificationService;
import com.llf.service.UserService;
import com.llf.vo.admin.reservation.EmergencyReservationActionVO;
import com.llf.vo.admin.reservation.EmergencyReservationConfirmVO;
import com.llf.vo.admin.reservation.EmergencyReservationConflictVO;
import com.llf.vo.admin.reservation.EmergencyReservationNotificationVO;
import com.llf.vo.admin.reservation.EmergencyReservationPreviewVO;
import com.llf.vo.admin.reservation.EmergencyReservationSummaryVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.room.RoomOptionVO;
import com.llf.vo.user.UserOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EmergencyReservationServiceImpl implements EmergencyReservationService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReservationMapper reservationMapper;
    private final RoomMapper roomMapper;
    private final NotificationService notificationService;
    private final UserService userService;
    private final Map<String, PreviewSnapshot> previewSnapshots = new ConcurrentHashMap<>();

    public EmergencyReservationServiceImpl(ReservationMapper reservationMapper,
                                           RoomMapper roomMapper,
                                           NotificationService notificationService,
                                           UserService userService) {
        this.reservationMapper = reservationMapper;
        this.roomMapper = roomMapper;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @Override
    public EmergencyReservationPreviewVO preview(EmergencyReservationRequestDTO dto, Long adminUserId) {
        ComputedPlan plan = computePlan(dto, adminUserId);
        if (Boolean.TRUE.equals(plan.preview().getCanExecute())) {
            previewSnapshots.put(previewKey(adminUserId, plan.request()), new PreviewSnapshot(plan.fingerprint(), LocalDateTime.now()));
        }
        return plan.preview();
    }

    @Override
    @Transactional
    public EmergencyReservationConfirmVO confirm(EmergencyReservationRequestDTO dto, Long adminUserId) {
        ComputedPlan plan = computePlan(dto, adminUserId);
        String key = previewKey(adminUserId, plan.request());
        PreviewSnapshot snapshot = previewSnapshots.get(key);
        if (snapshot == null || snapshot.createdAt().plus(PREVIEW_TTL).isBefore(LocalDateTime.now())) {
            previewSnapshots.remove(key);
            throw new BizException(409, "请先预览抢占调配方案后再确认执行");
        }
        if (!Objects.equals(snapshot.fingerprint(), plan.fingerprint())) {
            previewSnapshots.remove(key);
            throw new BizException(409, "会议室占用情况已变化，请重新预览后再确认");
        }
        if (!Boolean.TRUE.equals(plan.preview().getCanExecute())) {
            throw new BizException(400, plan.preview().getMessage());
        }

        for (EmergencyReservationActionVO action : plan.preview().getActions()) {
            ReservationMapper.EmergencyConflictReservationRow conflict = plan.conflictMap().get(action.getReservationId());
            if ("MOVE_ROOM".equals(action.getActionType())) {
                int updated = reservationMapper.updateActiveReservationRoomAndRemark(
                        action.getReservationId(),
                        action.getTargetRoomId(),
                        appendDispatchRemark(conflict == null ? null : conflict.getRemark(), action)
                );
                if (updated != 1) {
                    throw new BizException(409, "会议室占用情况已变化，请重新预览后再确认");
                }
            } else if ("CANCEL".equals(action.getActionType())) {
                int updated = reservationMapper.cancelActiveReservation(
                        action.getReservationId(),
                        buildCancelReason(plan.request(), action)
                );
                if (updated != 1) {
                    throw new BizException(409, "会议室占用情况已变化，请重新预览后再确认");
                }
            }
        }

        String reservationNo = generateReservationNo();
        reservationMapper.insertReservation(
                reservationNo,
                plan.request().room().getId(),
                adminUserId,
                plan.request().title(),
                plan.request().remark(),
                plan.request().attendees(),
                Timestamp.valueOf(plan.request().startTime()),
                Timestamp.valueOf(plan.request().endTime())
        );
        Long reservationId = reservationMapper.lastInsertId();
        for (Map.Entry<Long, Integer> entry : plan.request().requiredDevices().entrySet()) {
            reservationMapper.insertReservationDevice(reservationId, entry.getKey(), entry.getValue());
        }
        for (Long participantUserId : plan.request().participantUserIds()) {
            reservationMapper.insertReservationParticipant(reservationId, participantUserId);
        }
        reservationMapper.approveReservation(reservationId, adminUserId, "紧急会议自动通过");

        ReservationCreateVO created = reservationMapper.selectCreateResultById(reservationId);
        sendNotifications(plan, reservationId);
        previewSnapshots.remove(key);

        EmergencyReservationConfirmVO result = new EmergencyReservationConfirmVO();
        result.setReservationId(reservationId);
        result.setReservationNo(created == null ? reservationNo : created.getReservationNo());
        result.setStatus(created == null ? "ACTIVE" : created.getStatus());
        result.setMessage("紧急会议已创建并完成抢占调配。");
        result.setExecutedPlan(plan.preview());
        return result;
    }

    private ComputedPlan computePlan(EmergencyReservationRequestDTO dto, Long adminUserId) {
        NormalizedRequest request = normalizeRequest(dto, adminUserId);
        List<ReservationMapper.EmergencyConflictReservationRow> conflicts = safeList(
                reservationMapper.selectActiveConflictsByRoomId(
                        request.room().getId(),
                        Timestamp.valueOf(request.startTime()),
                        Timestamp.valueOf(request.endTime())
                )
        );
        List<EmergencyReservationActionVO> actions = List.of();
        Map<Long, ReservationMapper.EmergencyConflictReservationRow> conflictMap = conflicts.stream()
                .collect(Collectors.toMap(ReservationMapper.EmergencyConflictReservationRow::getId, item -> item, (a, b) -> a, LinkedHashMap::new));
        if (!conflicts.isEmpty() && Boolean.TRUE.equals(request.allowPreempt())) {
            actions = buildDispatchActions(request, conflicts);
        }

        EmergencyReservationPreviewVO preview = new EmergencyReservationPreviewVO();
        preview.setEmergencySummary(toSummary(request));
        preview.setConflicts(conflicts.stream().map(this::toConflict).toList());
        preview.setActions(actions);
        preview.setNotifications(buildNotificationPreview(request, conflicts, actions));
        boolean canExecute = conflicts.isEmpty() || Boolean.TRUE.equals(request.allowPreempt());
        preview.setCanExecute(canExecute);
        preview.setMessage(resolvePreviewMessage(conflicts, actions, canExecute));

        return new ComputedPlan(request, preview, conflictMap, fingerprint(preview));
    }

    private NormalizedRequest normalizeRequest(EmergencyReservationRequestDTO dto, Long adminUserId) {
        if (adminUserId == null) {
            throw new BizException(401, "not logged in");
        }
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }
        LocalDateTime startTime = parseMeetingTime(dto.getMeetingDate(), dto.getStartClock(), "startClock");
        LocalDateTime endTime = parseMeetingTime(dto.getMeetingDate(), dto.getEndClock(), "endClock");
        if (!endTime.isAfter(startTime)) {
            throw new BizException(400, "endClock must be greater than startClock");
        }

        RoomOptionVO room = roomMapper.selectOptionById(dto.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (!"AVAILABLE".equalsIgnoreCase(room.getStatus())) {
            throw new BizException(400, "room is under maintenance");
        }
        if (dto.getAttendees() == null || dto.getAttendees() < 1) {
            throw new BizException(400, "attendees must be greater than 0");
        }
        if (dto.getAttendees() > room.getCapacity()) {
            throw new BizException(400, "attendees exceeds room capacity");
        }

        String title = trimToNull(dto.getTitle());
        if (title == null) {
            throw new BizException(400, "title must not be blank");
        }
        String emergencyReason = trimToNull(dto.getEmergencyReason());
        if (emergencyReason == null) {
            throw new BizException(400, "emergencyReason must not be blank");
        }
        Map<Long, Integer> requiredDevices = normalizeDeviceRequirements(dto.getDeviceRequirements());
        validateRoomDeviceRequirements(room.getId(), requiredDevices);
        List<Long> participantUserIds = normalizeParticipantUserIds(dto.getParticipantUserIds());

        return new NormalizedRequest(
                room,
                emergencyTitle(title),
                startTime,
                endTime,
                dto.getAttendees(),
                Boolean.TRUE.equals(dto.getAllowPreempt()),
                emergencyReason,
                appendEmergencyReason(trimToNull(dto.getRemark()), emergencyReason),
                requiredDevices,
                participantUserIds
        );
    }

    private List<EmergencyReservationActionVO> buildDispatchActions(NormalizedRequest request,
                                                                    List<ReservationMapper.EmergencyConflictReservationRow> conflicts) {
        Map<Long, Map<Long, Integer>> deviceRequirementsByReservation = loadConflictDeviceRequirements(conflicts);
        Set<Long> assignedRoomIds = new LinkedHashSet<>();
        List<EmergencyReservationActionVO> actions = new ArrayList<>();
        for (ReservationMapper.EmergencyConflictReservationRow conflict : conflicts) {
            RoomMapper.RecommendationRoomRow targetRoom = findAlternativeRoom(
                    request,
                    conflict,
                    deviceRequirementsByReservation.getOrDefault(conflict.getId(), Map.of()),
                    assignedRoomIds
            );
            EmergencyReservationActionVO action = new EmergencyReservationActionVO();
            action.setReservationId(conflict.getId());
            action.setReservationTitle(conflict.getTitle());
            action.setSourceRoomId(conflict.getRoomId());
            action.setSourceRoomName(conflict.getRoomName());
            if (targetRoom == null) {
                action.setActionType("CANCEL");
                action.setReason("无同时间可替代会议室，原预约将取消");
            } else {
                assignedRoomIds.add(targetRoom.getId());
                action.setActionType("MOVE_ROOM");
                action.setTargetRoomId(targetRoom.getId());
                action.setTargetRoomName(targetRoom.getName());
                action.setReason("调配到同时间可用会议室");
            }
            actions.add(action);
        }
        return actions;
    }

    private RoomMapper.RecommendationRoomRow findAlternativeRoom(NormalizedRequest request,
                                                                 ReservationMapper.EmergencyConflictReservationRow conflict,
                                                                 Map<Long, Integer> requiredDevices,
                                                                 Set<Long> assignedRoomIds) {
        List<RoomMapper.RecommendationRoomRow> candidates = safeList(roomMapper.selectRecommendationCandidates(conflict.getAttendees())).stream()
                .filter(room -> !Objects.equals(room.getId(), request.room().getId()))
                .filter(room -> !assignedRoomIds.contains(room.getId()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<Long> candidateIds = candidates.stream().map(RoomMapper.RecommendationRoomRow::getId).toList();
        Set<Long> occupiedRoomIds = new LinkedHashSet<>(safeList(reservationMapper.selectConflictRoomIds(
                Timestamp.valueOf(request.startTime()),
                Timestamp.valueOf(request.endTime()),
                candidateIds
        )));
        List<RoomMapper.RecommendationRoomRow> availableCandidates = candidates.stream()
                .filter(room -> !occupiedRoomIds.contains(room.getId()))
                .toList();
        if (availableCandidates.isEmpty()) {
            return null;
        }

        Map<Long, Map<Long, Integer>> roomDeviceMap = buildRoomDeviceMap(
                roomMapper.selectEnabledRoomDevices(availableCandidates.stream().map(RoomMapper.RecommendationRoomRow::getId).toList())
        );
        return availableCandidates.stream()
                .filter(room -> deviceRequirementsSatisfied(requiredDevices, roomDeviceMap.getOrDefault(room.getId(), Map.of())))
                .min(Comparator.comparing(RoomMapper.RecommendationRoomRow::getId))
                .orElse(null);
    }

    private Map<Long, Map<Long, Integer>> loadConflictDeviceRequirements(List<ReservationMapper.EmergencyConflictReservationRow> conflicts) {
        if (conflicts.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, Integer>> result = new LinkedHashMap<>();
        List<Long> reservationIds = conflicts.stream().map(ReservationMapper.EmergencyConflictReservationRow::getId).toList();
        for (MyReservationVO.DeviceRow row : safeList(reservationMapper.selectMyReservationDevices(reservationIds))) {
            if (row.getReservationId() == null || row.getDeviceId() == null || row.getQuantity() == null) {
                continue;
            }
            result.computeIfAbsent(row.getReservationId(), ignored -> new LinkedHashMap<>())
                    .merge(row.getDeviceId(), row.getQuantity(), Integer::sum);
        }
        return result;
    }

    private List<EmergencyReservationNotificationVO> buildNotificationPreview(NormalizedRequest request,
                                                                              List<ReservationMapper.EmergencyConflictReservationRow> conflicts,
                                                                              List<EmergencyReservationActionVO> actions) {
        Map<Long, EmergencyReservationActionVO> actionMap = actions.stream()
                .collect(Collectors.toMap(EmergencyReservationActionVO::getReservationId, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<ReservationMapper.ReservationParticipantRow>> participantsByReservation = loadParticipantsByReservation(conflicts);
        List<EmergencyReservationNotificationVO> notifications = new ArrayList<>();
        for (ReservationMapper.EmergencyConflictReservationRow conflict : conflicts) {
            EmergencyReservationActionVO action = actionMap.get(conflict.getId());
            String reason = action == null ? "冲突提醒" : action.getReason();
            addNotification(notifications, conflict.getOrganizerId(), conflict.getOrganizerName(), conflict.getId(), conflict.getTitle(), reason);
            for (ReservationMapper.ReservationParticipantRow participant : participantsByReservation.getOrDefault(conflict.getId(), List.of())) {
                addNotification(notifications, participant.getUserId(), participant.getDisplayName(), conflict.getId(), conflict.getTitle(), reason);
            }
        }
        for (Long participantUserId : request.participantUserIds()) {
            addNotification(notifications, participantUserId, String.valueOf(participantUserId), null, request.title(), "紧急会议参会通知");
        }
        return notifications;
    }

    private Map<Long, List<ReservationMapper.ReservationParticipantRow>> loadParticipantsByReservation(List<ReservationMapper.EmergencyConflictReservationRow> conflicts) {
        if (conflicts.isEmpty()) {
            return Map.of();
        }
        List<Long> reservationIds = conflicts.stream().map(ReservationMapper.EmergencyConflictReservationRow::getId).toList();
        Map<Long, List<ReservationMapper.ReservationParticipantRow>> result = new LinkedHashMap<>();
        for (ReservationMapper.ReservationParticipantRow row : safeList(reservationMapper.selectReservationParticipants(reservationIds))) {
            result.computeIfAbsent(row.getReservationId(), ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private void sendNotifications(ComputedPlan plan, Long emergencyReservationId) {
        Map<Long, EmergencyReservationActionVO> actionMap = plan.preview().getActions().stream()
                .collect(Collectors.toMap(EmergencyReservationActionVO::getReservationId, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<ReservationMapper.ReservationParticipantRow>> participantsByReservation = loadParticipantsByReservation(new ArrayList<>(plan.conflictMap().values()));
        for (ReservationMapper.EmergencyConflictReservationRow conflict : plan.conflictMap().values()) {
            EmergencyReservationActionVO action = actionMap.get(conflict.getId());
            if (action == null) {
                continue;
            }
            String title = "MOVE_ROOM".equals(action.getActionType()) ? "紧急会议调配通知" : "紧急会议取消通知";
            String content = "MOVE_ROOM".equals(action.getActionType())
                    ? "您的预约《" + conflict.getTitle() + "》因紧急会议已从 " + action.getSourceRoomName() + " 调整到 " + action.getTargetRoomName() + "。"
                    : "您的预约《" + conflict.getTitle() + "》因紧急会议《" + plan.request().title() + "》已取消。";
            sendAffectedNotification(conflict.getOrganizerId(), conflict.getId(), title, content);
            for (ReservationMapper.ReservationParticipantRow participant : participantsByReservation.getOrDefault(conflict.getId(), List.of())) {
                sendAffectedNotification(participant.getUserId(), conflict.getId(), title, content);
            }
        }
        for (Long participantUserId : plan.request().participantUserIds()) {
            notificationService.createEmergencyReservationNotification(
                    participantUserId,
                    emergencyReservationId,
                    "紧急会议通知",
                    "您已被加入紧急会议《" + plan.request().title() + "》。",
                    "success"
            );
        }
    }

    private void sendAffectedNotification(Long userId, Long reservationId, String title, String content) {
        if (userId == null) {
            return;
        }
        notificationService.createEmergencyReservationNotification(userId, reservationId, title, content, "warning");
    }

    private EmergencyReservationSummaryVO toSummary(NormalizedRequest request) {
        EmergencyReservationSummaryVO summary = new EmergencyReservationSummaryVO();
        summary.setRoomId(request.room().getId());
        summary.setRoomCode(request.room().getRoomCode());
        summary.setRoomName(request.room().getName());
        summary.setTitle(request.title());
        summary.setAttendees(request.attendees());
        summary.setStartTime(formatDateTime(request.startTime()));
        summary.setEndTime(formatDateTime(request.endTime()));
        summary.setEmergencyReason(request.emergencyReason());
        return summary;
    }

    private EmergencyReservationConflictVO toConflict(ReservationMapper.EmergencyConflictReservationRow row) {
        EmergencyReservationConflictVO conflict = new EmergencyReservationConflictVO();
        conflict.setReservationId(row.getId());
        conflict.setReservationNo(row.getReservationNo());
        conflict.setRoomId(row.getRoomId());
        conflict.setRoomCode(row.getRoomCode());
        conflict.setRoomName(row.getRoomName());
        conflict.setRoomLocation(row.getRoomLocation());
        conflict.setOrganizerId(row.getOrganizerId());
        conflict.setOrganizerName(row.getOrganizerName());
        conflict.setTitle(row.getTitle());
        conflict.setAttendees(row.getAttendees());
        conflict.setStatus(row.getStatus());
        conflict.setStartTime(formatTimestamp(row.getStartTime()));
        conflict.setEndTime(formatTimestamp(row.getEndTime()));
        return conflict;
    }

    private String resolvePreviewMessage(List<ReservationMapper.EmergencyConflictReservationRow> conflicts,
                                         List<EmergencyReservationActionVO> actions,
                                         boolean canExecute) {
        if (conflicts.isEmpty()) {
            return "目标会议室无冲突，可以直接创建紧急会议。";
        }
        if (!canExecute) {
            return "存在冲突预约，请允许抢占后再预览调配方案。";
        }
        long moveCount = actions.stream().filter(item -> "MOVE_ROOM".equals(item.getActionType())).count();
        long cancelCount = actions.stream().filter(item -> "CANCEL".equals(item.getActionType())).count();
        return "检测到 " + conflicts.size() + " 条冲突预约，拟调配 " + moveCount + " 条、取消 " + cancelCount + " 条。";
    }

    private void addNotification(List<EmergencyReservationNotificationVO> notifications,
                                 Long userId,
                                 String displayName,
                                 Long reservationId,
                                 String title,
                                 String reason) {
        if (userId == null) {
            return;
        }
        boolean exists = notifications.stream()
                .anyMatch(item -> Objects.equals(item.getUserId(), userId) && Objects.equals(item.getReservationId(), reservationId));
        if (exists) {
            return;
        }
        EmergencyReservationNotificationVO notification = new EmergencyReservationNotificationVO();
        notification.setUserId(userId);
        notification.setDisplayName(displayName);
        notification.setReservationId(reservationId);
        notification.setTitle(title);
        notification.setReason(reason);
        notifications.add(notification);
    }

    private void validateRoomDeviceRequirements(Long roomId, Map<Long, Integer> requiredDevices) {
        if (requiredDevices.isEmpty()) {
            return;
        }
        Map<Long, Map<Long, Integer>> roomDeviceMap = buildRoomDeviceMap(roomMapper.selectEnabledRoomDevices(List.of(roomId)));
        if (!deviceRequirementsSatisfied(requiredDevices, roomDeviceMap.getOrDefault(roomId, Map.of()))) {
            throw new BizException(400, "device requirements cannot be satisfied");
        }
    }

    private boolean deviceRequirementsSatisfied(Map<Long, Integer> requiredDevices, Map<Long, Integer> roomDevices) {
        for (Map.Entry<Long, Integer> entry : requiredDevices.entrySet()) {
            if (roomDevices.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private Map<Long, Map<Long, Integer>> buildRoomDeviceMap(List<RoomMapper.RoomDeviceAvailabilityRow> rows) {
        Map<Long, Map<Long, Integer>> result = new LinkedHashMap<>();
        for (RoomMapper.RoomDeviceAvailabilityRow row : safeList(rows)) {
            if (row.getRoomId() == null || row.getDeviceId() == null || row.getQuantity() == null) {
                continue;
            }
            result.computeIfAbsent(row.getRoomId(), ignored -> new LinkedHashMap<>())
                    .merge(row.getDeviceId(), row.getQuantity(), Integer::sum);
        }
        return result;
    }

    private Map<Long, Integer> normalizeDeviceRequirements(List<ReservationDeviceRequirementDTO> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (ReservationDeviceRequirementDTO requirement : requirements) {
            if (requirement == null || requirement.getDeviceId() == null || requirement.getQuantity() == null || requirement.getQuantity() <= 0) {
                continue;
            }
            normalized.merge(requirement.getDeviceId(), requirement.getQuantity(), Integer::sum);
        }
        return normalized;
    }

    private List<Long> normalizeParticipantUserIds(List<Long> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = participantUserIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<UserOptionVO> users = userService.listActiveUsersByIds(normalizedIds);
        if (users.size() != normalizedIds.size()) {
            throw new BizException(400, "participant user does not exist");
        }
        return users.stream().map(UserOptionVO::getId).toList();
    }

    private String previewKey(Long adminUserId, NormalizedRequest request) {
        String devices = request.requiredDevices().entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("|"));
        String participants = request.participantUserIds().stream().map(String::valueOf).collect(Collectors.joining("|"));
        return adminUserId + "::" + request.room().getId() + "::" + request.title() + "::"
                + formatDateTime(request.startTime()) + "::" + formatDateTime(request.endTime()) + "::"
                + request.attendees() + "::" + request.allowPreempt() + "::" + request.emergencyReason()
                + "::" + devices + "::" + participants;
    }

    private String fingerprint(EmergencyReservationPreviewVO preview) {
        String conflicts = preview.getConflicts().stream()
                .map(item -> item.getReservationId() + ":" + item.getRoomId() + ":" + item.getStatus())
                .collect(Collectors.joining("|"));
        String actions = preview.getActions().stream()
                .map(item -> item.getReservationId() + ":" + item.getActionType() + ":" + item.getTargetRoomId())
                .collect(Collectors.joining("|"));
        return preview.getCanExecute() + "::" + conflicts + "::" + actions;
    }

    private LocalDateTime parseMeetingTime(String meetingDate, String clock, String fieldName) {
        try {
            LocalDate date = LocalDate.parse(meetingDate);
            LocalTime time = LocalTime.parse(clock);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(400, fieldName + " format is invalid");
        }
    }

    private String emergencyTitle(String title) {
        return title.startsWith("[紧急]") ? title : "[紧急] " + title;
    }

    private String appendEmergencyReason(String remark, String emergencyReason) {
        String reasonLine = "紧急会议原因：" + emergencyReason;
        return remark == null ? reasonLine : remark + "\n" + reasonLine;
    }

    private String appendDispatchRemark(String remark, EmergencyReservationActionVO action) {
        String dispatchLine = "系统调配：因紧急会议从 " + action.getSourceRoomName() + " 调整到 " + action.getTargetRoomName() + "。";
        return trimToNull(remark) == null ? dispatchLine : remark + "\n" + dispatchLine;
    }

    private String buildCancelReason(NormalizedRequest request, EmergencyReservationActionVO action) {
        return "被紧急会议抢占：" + request.title() + " 占用 " + action.getSourceRoomName()
                + " " + formatDateTime(request.startTime()) + " - " + formatDateTime(request.endTime()) + "。";
    }

    private String generateReservationNo() {
        return "RSV" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : DATE_TIME_FORMATTER.format(timestamp.toLocalDateTime());
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private record NormalizedRequest(RoomOptionVO room,
                                     String title,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime,
                                     Integer attendees,
                                     Boolean allowPreempt,
                                     String emergencyReason,
                                     String remark,
                                     Map<Long, Integer> requiredDevices,
                                     List<Long> participantUserIds) {
    }

    private record ComputedPlan(NormalizedRequest request,
                                EmergencyReservationPreviewVO preview,
                                Map<Long, ReservationMapper.EmergencyConflictReservationRow> conflictMap,
                                String fingerprint) {
    }

    private record PreviewSnapshot(String fingerprint, LocalDateTime createdAt) {
    }
}
