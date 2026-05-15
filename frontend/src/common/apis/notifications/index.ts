import type * as Notifications from "./type"
import { request } from "@/http/axios"

export function getNotificationSummaryApi() {
  return request<Notifications.NotificationSummaryResponseData>({
    url: "notifications/summary",
    method: "get",
    silentError: true
  })
}

export function getNotificationPageApi(params: Notifications.NotificationListQueryRequestData) {
  return request<Notifications.NotificationListResponseData>({
    url: "notifications",
    method: "get",
    params,
    silentError: true
  })
}

export function readNotificationApi(id: number) {
  return request<Notifications.ReadNotificationResponseData>({
    url: `notifications/${id}/read`,
    method: "post",
    silentError: true
  })
}

export function readAllNotificationsApi(params: Notifications.ReadAllNotificationsRequestData) {
  return request<Notifications.ReadAllNotificationsResponseData>({
    url: "notifications/read-all",
    method: "post",
    data: params,
    silentError: true
  })
}

export function publishAdminNotificationApi(data: Notifications.AdminNotificationPublishRequestData) {
  return request<Notifications.AdminNotificationPublishResponseData>({
    url: "admin/notifications",
    method: "post",
    data,
    silentError: true
  })
}
