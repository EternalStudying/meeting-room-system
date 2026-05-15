export type ReservationStatus = "PENDING" | "ACTIVE" | "ENDED" | "CANCELLED" | "REJECTED" | "EXCEPTION"
export type ReservationRole = "ORGANIZER" | "PARTICIPANT"
export type MyReservationScope = "organizer" | "participant" | "all"

export interface ReservationCalendarQueryRequestData {
  startDate: string
  endDate: string
  roomId?: number
  status?: ReservationStatus
}

export interface MyReservationQueryRequestData {
  startDate: string
  endDate: string
  scope: MyReservationScope
  status?: ReservationStatus
  futureOnly?: boolean
}

export interface MyEndedReservationPageQueryRequestData {
  scope: MyReservationScope
  pageNum?: number
  pageSize?: number
}

export interface MyReservationRoomOption {
  id: number
  roomCode: string
  name: string
  location: string
  capacity: number
  status: "AVAILABLE" | "MAINTENANCE"
  description?: string
}

export interface CreateReservationRequestData {
  roomId: number
  title: string
  meetingDate: string
  startClock: string
  endClock: string
  attendees: number
  participantUserIds?: number[]
  deviceRequirements?: ReservationDeviceRequirementInput[]
  remark?: string
}

export interface UpdateMyReservationRequestData {
  title: string
  roomId: number
  meetingDate: string
  startClock: string
  endClock: string
  attendees: number
  participantUserIds?: number[]
  deviceRequirements?: ReservationDeviceRequirementInput[]
  remark?: string
}

export interface CancelMyReservationRequestData {
  cancelReason: string
}

export interface SubmitMyReservationReviewRequestData {
  rating: number
  content?: string
}

export interface AdminReservationQueryRequestData {
  currentPage: number
  size: number
  keyword?: string
  status?: ReservationStatus
}

export interface AdminReservationActionRequestData {
  remark?: string
  reason?: string
}

export interface ReservationDeviceRequirementInput {
  deviceId: number
  quantity: number
}

export interface ReservationReviewData {
  rating: number
  content: string
  createdAt: string
}

export interface ReservationParticipantUser {
  id: number
  username: string
  displayName: string
}

export interface ReservationDeviceData {
  id: number
  deviceId: number
  deviceCode: string
  name: string
  quantity: number
  status?: "ENABLED" | "DISABLED"
}

export interface ReservationCalendarItem {
  id: number
  reservationNo: string
  roomId: number
  roomName: string
  organizerId: number
  organizerName: string
  title: string
  attendees: number
  startTime: string
  endTime: string
  status: ReservationStatus
  remark?: string
  cancelReason?: string
  devices: ReservationDeviceData[]
  participants?: ReservationParticipantUser[]
}

export interface AdminReservationItem extends ReservationCalendarItem {
  roomCode: string
  roomLocation: string
  roomCapacity?: number
  organizerUsername?: string
  createdAt?: string
  approvalRemark?: string
  rejectReason?: string
  exceptionReason?: string
  processedByName?: string
  processedAt?: string
}

export interface AdminReservationStats {
  totalCount: number
  pendingCount: number
  activeCount: number
  rejectedCount: number
  exceptionCount: number
}

export interface ReservationRecommendationRequestData {
  title?: string
  attendees: number
  startTime: string
  endTime: string
  preferredRoomId?: number
  deviceRequirements?: ReservationDeviceRequirementInput[]
}

export interface ReservationRecommendationItem {
  roomId: number
  roomCode: string
  roomName: string
  location: string
  capacity: number
  score: number
  wasteRate: number
  requiredDeviceTypeCount: number
  matchedDeviceTypeCount: number
  deviceFullyMatched: boolean
  isPreferred: boolean
  tags: string[]
}

export interface EmergencyReservationRequestData extends CreateReservationRequestData {
  allowPreempt: boolean
  emergencyReason: string
}

export interface EmergencyReservationSummaryData {
  roomId: number
  roomCode: string
  roomName: string
  title: string
  attendees: number
  startTime: string
  endTime: string
  emergencyReason: string
}

export interface EmergencyReservationConflictData {
  reservationId: number
  reservationNo: string
  roomId: number
  roomCode: string
  roomName: string
  roomLocation?: string
  organizerId: number
  organizerName: string
  title: string
  attendees: number
  startTime: string
  endTime: string
  status: ReservationStatus
}

export interface EmergencyReservationActionData {
  reservationId: number
  reservationTitle: string
  actionType: "MOVE_ROOM" | "CANCEL"
  sourceRoomId: number
  sourceRoomName: string
  targetRoomId?: number
  targetRoomName?: string
  reason: string
}

export interface EmergencyReservationNotificationData {
  userId: number
  displayName?: string
  reservationId?: number
  title: string
  reason: string
}

export interface EmergencyReservationPreviewData {
  emergencySummary: EmergencyReservationSummaryData
  conflicts: EmergencyReservationConflictData[]
  actions: EmergencyReservationActionData[]
  notifications: EmergencyReservationNotificationData[]
  canExecute: boolean
  message: string
}

export interface EmergencyReservationConfirmData {
  reservationId: number
  reservationNo: string
  status: ReservationStatus
  message: string
  executedPlan?: EmergencyReservationPreviewData
}

export type ReservationCalendarResponseData = ApiResponseData<ReservationCalendarItem[]>
export type ReservationRecommendationResponseData = ApiResponseData<{
  recommendations: ReservationRecommendationItem[]
}>
export type EmergencyReservationPreviewResponseData = ApiResponseData<EmergencyReservationPreviewData>
export type EmergencyReservationConfirmResponseData = ApiResponseData<EmergencyReservationConfirmData>

export interface MyReservationItem extends ReservationCalendarItem {
  roomCode: string
  roomLocation: string
  roomCapacity: number
  roomDescription?: string
  role: ReservationRole
  canEdit: boolean
  canCancel: boolean
  reviewed?: boolean
  myReview?: ReservationReviewData
}

export interface MyReservationPageData {
  list: MyReservationItem[]
  total: number
  pageNum?: number
  pageSize?: number
}

export type CreateReservationResponseData = ApiResponseData<{
  id: number
  reservationNo?: string
}>
export type MyReservationResponseData = ApiResponseData<MyReservationItem[] | MyReservationPageData>
export type MyReservationDetailResponseData = ApiResponseData<MyReservationItem>
export type MyEndedReservationPageResponseData = ApiResponseData<MyReservationPageData>
export type MyReservationRoomOptionResponseData = ApiResponseData<MyReservationRoomOption[]>
export type SubmitMyReservationReviewResponseData = ApiResponseData<{
  reviewed: boolean
  myReview: ReservationReviewData
}>
export type AdminReservationListResponseData = ApiResponseData<{
  list: AdminReservationItem[]
  total: number
  stats: AdminReservationStats
}>
export type AdminReservationActionResponseData = ApiResponseData<AdminReservationItem | null>
