export type NotificationCategory = "NOTICE" | "MESSAGE" | "TODO"
export type NotificationTagType = "primary" | "success" | "info" | "warning" | "danger"
export type AdminNotificationType = "ANNOUNCEMENT" | "MAINTENANCE"
export type AdminNotificationRecipientScope = "ALL" | "USERS" | "ADMINS"

export interface NotificationRouteQuery {
  [key: string]: string | number | boolean | null | undefined
}

export interface NotificationItem {
  id: number
  category: NotificationCategory
  title: string
  content: string
  createdAt: string
  read: boolean
  route?: string
  routeQuery?: NotificationRouteQuery
  avatar?: string
  extra?: string
  status?: NotificationTagType
}

export interface NotificationSummaryCount {
  NOTICE: number
  MESSAGE: number
  TODO: number
}

export interface NotificationSummaryData {
  totalUnread: number
  unreadByCategory: NotificationSummaryCount
}

export interface NotificationListQueryRequestData {
  category: NotificationCategory
  pageNum?: number
  pageSize?: number
}

export interface NotificationPageData {
  list: NotificationItem[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ReadAllNotificationsRequestData {
  category: NotificationCategory
}

export interface AdminNotificationPublishRequestData {
  type: AdminNotificationType
  recipientScope: AdminNotificationRecipientScope
  title: string
  content: string
}

export interface AdminNotificationPublishData {
  type: AdminNotificationType
  category: NotificationCategory
  recipientScope: AdminNotificationRecipientScope
  title: string
  publishedCount: number
}

export type NotificationSummaryResponseData = ApiResponseData<NotificationSummaryData>
export type NotificationListResponseData = ApiResponseData<NotificationPageData>
export type ReadNotificationResponseData = ApiResponseData<{
  id: number
  read: boolean
}>
export type ReadAllNotificationsResponseData = ApiResponseData<{
  category: NotificationCategory
  updatedCount: number
}>
export type AdminNotificationPublishResponseData = ApiResponseData<AdminNotificationPublishData>
