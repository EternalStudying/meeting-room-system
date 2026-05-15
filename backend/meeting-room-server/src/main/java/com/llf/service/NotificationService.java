package com.llf.service;

import com.llf.vo.notification.NotificationItemVO;
import com.llf.vo.notification.NotificationReadAllResultVO;
import com.llf.vo.notification.NotificationReadResultVO;
import com.llf.vo.notification.NotificationSummaryVO;
import com.llf.vo.notification.NotificationTodoTargetVO;
import com.llf.vo.notification.AdminNotificationPublishVO;
import com.llf.vo.common.PageResultVO;

import java.util.List;

public interface NotificationService {
    NotificationSummaryVO getSummary(Long userId);

    PageResultVO<NotificationItemVO> list(Long userId, String category, Integer pageNum, Integer pageSize);

    NotificationReadResultVO markRead(Long userId, Long id);

    NotificationReadAllResultVO markAllRead(Long userId, String category);

    AdminNotificationPublishVO publishAdminNotification(String type, String title, String content, String recipientScope);

    void createReservationCreatedNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String roomName, String startTime, String endTime);

    void createReservationUpdatedNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String roomName, String startTime, String endTime);

    void createReservationCancelledNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String cancelReason);

    void createReservationApprovedNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String roomName, String startTime, String endTime);

    void createReservationRejectedNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String rejectReason);

    void createReservationExceptionNotification(Long userId, Long reservationId, String reservationStatus, String reservationTitle, String exceptionReason);

    void createEmergencyReservationNotification(Long userId, Long reservationId, String title, String content, String status);

    void createReviewTodoNotifications(List<NotificationTodoTargetVO> targets);
}
