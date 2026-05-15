import type { DeviceStatus } from "@/common/apis/rooms/type"
import type { ReservationStatus } from "@/common/apis/reservations/type"

export type AdminStatsTone = "steel" | "mint" | "amber" | "rose"
export type AdminStatsAlertType =
  | "room_maintenance"
  | "room_unbound"
  | "device_disabled_bound"
  | "room_high_cancel"

export interface AdminStatsKpi {
  key: string
  label: string
  value: number
  unit?: string
  detail: string
  tone: AdminStatsTone
}

export interface AdminReservationTrendPoint {
  date: string
  label: string
  reservationCount: number
}

export interface AdminReservationHeatmapCell {
  weekday: string
  weekdayIndex: number
  hour: string
  hourIndex: number
  reservationCount: number
}

export interface AdminRoomUsageRankItem {
  roomId: number
  roomCode: string
  roomName: string
  location: string
  reservationCount: number
  activeCount: number
  cancelledCount: number
}

export interface AdminDeviceUsageRankItem {
  deviceId: number
  deviceCode: string
  deviceName: string
  status: DeviceStatus
  usageQuantity: number
  reservationCount: number
}

export interface AdminStatsCoverage {
  configuredRoomCount: number
  unconfiguredRoomCount: number
}

export interface AdminStatsAlertItem {
  id: string
  type: AdminStatsAlertType
  targetName: string
  summary: string
  level: "warning" | "danger"
}

export interface AdminStatsReservationItem {
  id: number
  reservationNo: string
  title: string
  roomName: string
  organizerName: string
  startTime: string
  endTime: string
  status: ReservationStatus
  deviceSummary: string
}

export interface AdminStatsOverview {
  recentDays: number
  kpis: {
    totalReservations: AdminStatsKpi
    activeReservations: AdminStatsKpi
    cancelledReservations: AdminStatsKpi
    roomCoverageRate: AdminStatsKpi
    deviceCoverageRate: AdminStatsKpi
    maintenanceRooms: AdminStatsKpi
  }
  trend: AdminReservationTrendPoint[]
  heatmap: AdminReservationHeatmapCell[]
  roomUsageRanks: AdminRoomUsageRankItem[]
  deviceUsageRanks: AdminDeviceUsageRankItem[]
  coverage: AdminStatsCoverage
  alerts: AdminStatsAlertItem[]
  reservations: AdminStatsReservationItem[]
  staticSummary: {
    totalRooms: number
    availableRooms: number
    maintenanceRooms: number
    totalDevices: number
    enabledDevices: number
    disabledDevices: number
  }
}

export type AdminStatsResponseData = ApiResponseData<AdminStatsOverview>
