package com.llf.service.impl;

import com.llf.dto.admin.reservation.EmergencyReservationRequestDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.NotificationService;
import com.llf.service.UserService;
import com.llf.vo.admin.reservation.EmergencyReservationConfirmVO;
import com.llf.vo.admin.reservation.EmergencyReservationPreviewVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.room.RoomOptionVO;
import com.llf.vo.user.UserOptionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyReservationServiceImplTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private EmergencyReservationServiceImpl emergencyReservationService;

    @Test
    void confirm_noConflict_shouldCreateEmergencyReservationAsActive() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(), List.of());
        when(reservationMapper.lastInsertId()).thenReturn(9001L);
        when(reservationMapper.approveReservation(eq(9001L), eq(99L), contains("紧急"))).thenReturn(1);
        when(reservationMapper.selectCreateResultById(9001L)).thenReturn(created(9001L, "RSV9001", "ACTIVE"));

        emergencyReservationService.preview(dto, 99L);
        EmergencyReservationConfirmVO result = emergencyReservationService.confirm(dto, 99L);

        assertEquals(9001L, result.getReservationId());
        assertEquals("ACTIVE", result.getStatus());
        verify(reservationMapper).insertReservation(any(), eq(101L), eq(99L), eq("[紧急] 核心客户事故复盘"),
                contains("紧急会议原因：客户生产事故"), eq(6), any(Timestamp.class), any(Timestamp.class));
        verify(reservationMapper).approveReservation(9001L, 99L, "紧急会议自动通过");
    }

    @Test
    void preview_activeConflictWithoutPreemptPermission_shouldReturnConflictAndBlockExecution() {
        EmergencyReservationRequestDTO dto = request(101L, false);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2001L, 101L, "客户例会", 7L, 6)));

        EmergencyReservationPreviewVO result = emergencyReservationService.preview(dto, 99L);

        assertFalse(result.getCanExecute());
        assertEquals(1, result.getConflicts().size());
        assertTrue(result.getActions().isEmpty());
        assertTrue(result.getMessage().contains("存在冲突"));
        verify(reservationMapper, never()).insertReservation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void preview_activeConflictWithPreemptPermission_shouldReturnDispatchPlan() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2001L, 101L, "客户例会", 7L, 6)));
        when(reservationMapper.selectMyReservationDevices(List.of(2001L))).thenReturn(List.of());
        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(roomCandidate(102L, "R-B202", "B202 培训室", 12)));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(102L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(102L))).thenReturn(List.of());

        EmergencyReservationPreviewVO result = emergencyReservationService.preview(dto, 99L);

        assertTrue(result.getCanExecute());
        assertEquals("MOVE_ROOM", result.getActions().get(0).getActionType());
        assertEquals(102L, result.getActions().get(0).getTargetRoomId());
        assertTrue(result.getNotifications().stream().anyMatch(item -> item.getUserId().equals(7L)));
    }

    @Test
    void confirm_withAlternativeRoom_shouldMoveOriginalReservationAndKeepActive() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2001L, 101L, "客户例会", 7L, 6)), List.of(conflict(2001L, 101L, "客户例会", 7L, 6)));
        when(reservationMapper.selectMyReservationDevices(List.of(2001L))).thenReturn(List.of(), List.of());
        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(roomCandidate(102L, "R-B202", "B202 培训室", 12)), List.of(roomCandidate(102L, "R-B202", "B202 培训室", 12)));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(102L)))).thenReturn(List.of(), List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(102L))).thenReturn(List.of(), List.of());
        when(reservationMapper.updateActiveReservationRoomAndRemark(eq(2001L), eq(102L), contains("系统调配"))).thenReturn(1);
        when(reservationMapper.lastInsertId()).thenReturn(9002L);
        when(reservationMapper.approveReservation(eq(9002L), eq(99L), contains("紧急"))).thenReturn(1);
        when(reservationMapper.selectCreateResultById(9002L)).thenReturn(created(9002L, "RSV9002", "ACTIVE"));

        emergencyReservationService.preview(dto, 99L);
        EmergencyReservationConfirmVO result = emergencyReservationService.confirm(dto, 99L);

        assertEquals("ACTIVE", result.getStatus());
        verify(reservationMapper).updateActiveReservationRoomAndRemark(eq(2001L), eq(102L), contains("从 A101 多媒体会议室 调整到 B202 培训室"));
        verify(reservationMapper, never()).cancelActiveReservation(eq(2001L), any());
    }

    @Test
    void confirm_withoutAlternativeRoom_shouldCancelOriginalReservationWithReason() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2002L, 101L, "部门站会", 7L, 6)), List.of(conflict(2002L, 101L, "部门站会", 7L, 6)));
        when(reservationMapper.selectMyReservationDevices(List.of(2002L))).thenReturn(List.of(), List.of());
        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(), List.of());
        when(reservationMapper.cancelActiveReservation(eq(2002L), contains("被紧急会议抢占"))).thenReturn(1);
        when(reservationMapper.lastInsertId()).thenReturn(9003L);
        when(reservationMapper.approveReservation(eq(9003L), eq(99L), contains("紧急"))).thenReturn(1);
        when(reservationMapper.selectCreateResultById(9003L)).thenReturn(created(9003L, "RSV9003", "ACTIVE"));

        emergencyReservationService.preview(dto, 99L);
        EmergencyReservationConfirmVO result = emergencyReservationService.confirm(dto, 99L);

        assertEquals("ACTIVE", result.getStatus());
        verify(reservationMapper).cancelActiveReservation(eq(2002L), contains("核心客户事故复盘"));
    }

    @Test
    void confirm_whenOccupancyChangedAfterPreview_shouldRejectAndRequireNewPreview() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2001L, 101L, "客户例会", 7L, 6)), List.of(conflict(2001L, 101L, "客户例会", 7L, 6)));
        when(reservationMapper.selectMyReservationDevices(List.of(2001L))).thenReturn(List.of(), List.of());
        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(roomCandidate(102L, "R-B202", "B202 培训室", 12)), List.of(roomCandidate(102L, "R-B202", "B202 培训室", 12)));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(102L)))).thenReturn(List.of(), List.of(102L));
        when(roomMapper.selectEnabledRoomDevices(List.of(102L))).thenReturn(List.of(), List.of());

        emergencyReservationService.preview(dto, 99L);
        BizException ex = assertThrows(BizException.class, () -> emergencyReservationService.confirm(dto, 99L));

        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("重新预览"));
        verify(reservationMapper, never()).insertReservation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirm_shouldNotifyAffectedOrganizerParticipantsAndEmergencyParticipants() {
        EmergencyReservationRequestDTO dto = request(101L, true);
        dto.setParticipantUserIds(List.of(11L, 12L));
        when(userService.listActiveUsersByIds(List.of(11L, 12L))).thenReturn(List.of(
                user(11L, "lisi", "李四"),
                user(12L, "wangwu", "王五")
        ));
        when(roomMapper.selectOptionById(101L)).thenReturn(room(101L, "R-A101", "A101 多媒体会议室", 12));
        when(reservationMapper.selectActiveConflictsByRoomId(eq(101L), any(), any()))
                .thenReturn(List.of(conflict(2001L, 101L, "客户例会", 7L, 6)), List.of(conflict(2001L, 101L, "客户例会", 7L, 6)));
        when(reservationMapper.selectReservationParticipants(List.of(2001L))).thenReturn(List.of(
                participant(2001L, 8L, "zhaoliu", "赵六"),
                participant(2001L, 9L, "sunqi", "孙七")
        ));
        when(reservationMapper.selectMyReservationDevices(List.of(2001L))).thenReturn(List.of(), List.of());
        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(), List.of());
        when(reservationMapper.cancelActiveReservation(eq(2001L), any())).thenReturn(1);
        when(reservationMapper.lastInsertId()).thenReturn(9004L);
        when(reservationMapper.approveReservation(eq(9004L), eq(99L), contains("紧急"))).thenReturn(1);
        when(reservationMapper.selectCreateResultById(9004L)).thenReturn(created(9004L, "RSV9004", "ACTIVE"));

        emergencyReservationService.preview(dto, 99L);
        emergencyReservationService.confirm(dto, 99L);

        verify(notificationService).createEmergencyReservationNotification(eq(7L), eq(2001L), contains("取消"), any(), eq("warning"));
        verify(notificationService).createEmergencyReservationNotification(eq(8L), eq(2001L), contains("取消"), any(), eq("warning"));
        verify(notificationService).createEmergencyReservationNotification(eq(9L), eq(2001L), contains("取消"), any(), eq("warning"));
        verify(notificationService).createEmergencyReservationNotification(eq(11L), eq(9004L), contains("紧急会议"), any(), eq("success"));
        verify(notificationService).createEmergencyReservationNotification(eq(12L), eq(9004L), contains("紧急会议"), any(), eq("success"));
    }

    private EmergencyReservationRequestDTO request(Long roomId, boolean allowPreempt) {
        EmergencyReservationRequestDTO dto = new EmergencyReservationRequestDTO();
        dto.setRoomId(roomId);
        dto.setTitle("核心客户事故复盘");
        dto.setMeetingDate("2026-05-15");
        dto.setStartClock("15:00");
        dto.setEndClock("16:00");
        dto.setAttendees(6);
        dto.setAllowPreempt(allowPreempt);
        dto.setEmergencyReason("客户生产事故");
        return dto;
    }

    private RoomOptionVO room(Long id, String roomCode, String name, Integer capacity) {
        RoomOptionVO room = new RoomOptionVO();
        room.setId(id);
        room.setRoomCode(roomCode);
        room.setName(name);
        room.setLocation("A楼-1层");
        room.setCapacity(capacity);
        room.setStatus("AVAILABLE");
        return room;
    }

    private ReservationMapper.EmergencyConflictReservationRow conflict(Long id, Long roomId, String title, Long organizerId, Integer attendees) {
        ReservationMapper.EmergencyConflictReservationRow row = new ReservationMapper.EmergencyConflictReservationRow();
        row.setId(id);
        row.setReservationNo("RSV" + id);
        row.setRoomId(roomId);
        row.setRoomCode("R-A101");
        row.setRoomName("A101 多媒体会议室");
        row.setRoomLocation("A楼-1层");
        row.setOrganizerId(organizerId);
        row.setOrganizerName("张三");
        row.setTitle(title);
        row.setAttendees(attendees);
        row.setStatus("ACTIVE");
        row.setRemark("原备注");
        row.setStartTime(Timestamp.valueOf(LocalDateTime.parse("2026-05-15T15:00:00")));
        row.setEndTime(Timestamp.valueOf(LocalDateTime.parse("2026-05-15T16:00:00")));
        return row;
    }

    private RoomMapper.RecommendationRoomRow roomCandidate(Long id, String roomCode, String name, Integer capacity) {
        RoomMapper.RecommendationRoomRow row = new RoomMapper.RecommendationRoomRow();
        row.setId(id);
        row.setRoomCode(roomCode);
        row.setName(name);
        row.setLocation("B楼-2层");
        row.setCapacity(capacity);
        return row;
    }

    private ReservationCreateVO created(Long id, String reservationNo, String status) {
        ReservationCreateVO vo = new ReservationCreateVO();
        vo.setId(id);
        vo.setReservationNo(reservationNo);
        vo.setTitle("[紧急] 核心客户事故复盘");
        vo.setStatus(status);
        vo.setStartTime("2026-05-15 15:00:00");
        vo.setEndTime("2026-05-15 16:00:00");
        return vo;
    }

    private UserOptionVO user(Long id, String username, String displayName) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setDisplayName(displayName);
        return vo;
    }

    private ReservationMapper.ReservationParticipantRow participant(Long reservationId, Long userId, String username, String displayName) {
        ReservationMapper.ReservationParticipantRow row = new ReservationMapper.ReservationParticipantRow();
        row.setReservationId(reservationId);
        row.setId(userId);
        row.setUserId(userId);
        row.setUsername(username);
        row.setDisplayName(displayName);
        return row;
    }
}
