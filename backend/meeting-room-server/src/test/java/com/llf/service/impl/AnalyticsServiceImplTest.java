package com.llf.service.impl;

import com.llf.mapper.AnalyticsMapper;
import com.llf.vo.admin.stats.AdminStatsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private AnalyticsMapper analyticsMapper;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Test
    void adminStats_shouldReturnReadableCopyAndFrontendToneTokens() {
        when(analyticsMapper.countRooms()).thenReturn(4L);
        when(analyticsMapper.countConfiguredRooms()).thenReturn(2);
        when(analyticsMapper.countAllDevices()).thenReturn(5);
        when(analyticsMapper.countEnabledDevices()).thenReturn(4);
        when(analyticsMapper.countDisabledDevices()).thenReturn(1);
        when(analyticsMapper.countMaintenanceRooms()).thenReturn(1);
        when(analyticsMapper.countBoundDeviceTypes()).thenReturn(3);
        when(analyticsMapper.countRecentReservations(any(), any())).thenReturn(8L);
        when(analyticsMapper.countRecentReservationsByStatus(any(), any(), eq("ACTIVE"))).thenReturn(3L);
        when(analyticsMapper.countRecentReservationsByStatus(any(), any(), eq("CANCELLED"))).thenReturn(2L);
        when(analyticsMapper.selectRecentTrend(any(), any())).thenReturn(List.of());
        when(analyticsMapper.selectRecentHeatmap(any(), any())).thenReturn(List.of());
        when(analyticsMapper.selectRoomUsageRanks(any(), any())).thenReturn(List.of());
        when(analyticsMapper.selectDeviceUsageRanks(any(), any())).thenReturn(List.of());
        when(analyticsMapper.selectMaintenanceRoomAlerts()).thenReturn(List.of(maintenanceRoomAlert()));
        when(analyticsMapper.selectUnboundRoomAlerts()).thenReturn(List.of(unboundRoomAlert()));
        when(analyticsMapper.selectDisabledBoundDeviceAlerts()).thenReturn(List.of(disabledBoundDeviceAlert()));
        when(analyticsMapper.selectHighCancelRoomAlerts(any(), any())).thenReturn(List.of(highCancelRoomAlert()));
        when(analyticsMapper.selectRecentReservations(any(), any())).thenReturn(List.of(recentReservation()));

        AdminStatsVO result = analyticsService.adminStats(7);

        assertEquals("近期预约总数", result.getKpis().getTotalReservations().getLabel());
        assertEquals("场", result.getKpis().getTotalReservations().getUnit());
        assertEquals("steel", result.getKpis().getTotalReservations().getTone());
        assertEquals("mint", result.getKpis().getActiveReservations().getTone());
        assertEquals("rose", result.getKpis().getCancelledReservations().getTone());
        assertEquals("amber", result.getKpis().getRoomCoverageRate().getTone());
        assertEquals("周一", result.getHeatmap().get(0).getWeekday());
        assertEquals("梧桐会议室 当前处于维护状态", result.getAlerts().get(0).getSummary());
        assertEquals("海棠会议室 尚未绑定任何静态设备", result.getAlerts().get(1).getSummary());
        assertEquals("投影仪 已停用但仍绑定在会议室中", result.getAlerts().get(2).getSummary());
        assertEquals("银杏会议室 近期预约取消率偏高：2/4", result.getAlerts().get(3).getSummary());
        assertEquals("未调用设备", result.getReservations().get(0).getDeviceSummary());
    }

    private AnalyticsMapper.MaintenanceRoomAlertRow maintenanceRoomAlert() {
        AnalyticsMapper.MaintenanceRoomAlertRow row = new AnalyticsMapper.MaintenanceRoomAlertRow();
        row.roomId = 1L;
        row.roomName = "梧桐会议室";
        return row;
    }

    private AnalyticsMapper.UnboundRoomAlertRow unboundRoomAlert() {
        AnalyticsMapper.UnboundRoomAlertRow row = new AnalyticsMapper.UnboundRoomAlertRow();
        row.roomId = 2L;
        row.roomName = "海棠会议室";
        return row;
    }

    private AnalyticsMapper.DisabledBoundDeviceAlertRow disabledBoundDeviceAlert() {
        AnalyticsMapper.DisabledBoundDeviceAlertRow row = new AnalyticsMapper.DisabledBoundDeviceAlertRow();
        row.deviceId = 3L;
        row.deviceName = "投影仪";
        return row;
    }

    private AnalyticsMapper.HighCancelRoomAlertRow highCancelRoomAlert() {
        AnalyticsMapper.HighCancelRoomAlertRow row = new AnalyticsMapper.HighCancelRoomAlertRow();
        row.roomId = 4L;
        row.roomName = "银杏会议室";
        row.cancelledCount = 2L;
        row.reservationCount = 4L;
        return row;
    }

    private AnalyticsMapper.RecentReservationRow recentReservation() {
        AnalyticsMapper.RecentReservationRow row = new AnalyticsMapper.RecentReservationRow();
        row.id = 5L;
        row.reservationNo = "RSV-5";
        row.title = "周会";
        row.roomName = "松月会议室";
        row.organizerName = "张三";
        row.startTime = "2026-05-12 09:00:00";
        row.endTime = "2026-05-12 10:00:00";
        row.status = "PENDING";
        return row;
    }
}
