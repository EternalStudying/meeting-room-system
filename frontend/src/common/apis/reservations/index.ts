import type * as Reservations from "./type"
import { request } from "@/http/axios"

export function createReservationApi(data: Reservations.CreateReservationRequestData) {
  return request<Reservations.CreateReservationResponseData>({
    url: "reservations",
    method: "post",
    data
  })
}

export function getReservationRecommendationsApi(data: Reservations.ReservationRecommendationRequestData) {
  return request<Reservations.ReservationRecommendationResponseData>({
    url: "reservations/recommendations",
    method: "post",
    data
  })
}

export function previewAdminEmergencyReservationApi(data: Reservations.EmergencyReservationRequestData) {
  return request<Reservations.EmergencyReservationPreviewResponseData>({
    url: "admin/emergency-reservations/preview",
    method: "post",
    data
  })
}

export function confirmAdminEmergencyReservationApi(data: Reservations.EmergencyReservationRequestData) {
  return request<Reservations.EmergencyReservationConfirmResponseData>({
    url: "admin/emergency-reservations/confirm",
    method: "post",
    data
  })
}

export function getReservationCalendarApi(params: Reservations.ReservationCalendarQueryRequestData) {
  return request<Reservations.ReservationCalendarResponseData>({
    url: "reservations/calendar",
    method: "get",
    params
  })
}

export function getMyReservationsApi(params: Reservations.MyReservationQueryRequestData) {
  return request<Reservations.MyReservationResponseData>({
    url: "reservations/my",
    method: "get",
    params
  })
}

export function getMyEndedReservationsPageApi(params: Reservations.MyEndedReservationPageQueryRequestData) {
  return request<Reservations.MyEndedReservationPageResponseData>({
    url: "reservations/my/ended",
    method: "get",
    params
  })
}

export function getMyReservationRoomOptionsApi() {
  return request<Reservations.MyReservationRoomOptionResponseData>({
    url: "reservations/my/room-options",
    method: "get"
  })
}

export function getMyReservationDetailApi(id: number) {
  return request<Reservations.MyReservationDetailResponseData>({
    url: `reservations/my/${id}`,
    method: "get"
  })
}

export function updateMyReservationApi(id: number, data: Reservations.UpdateMyReservationRequestData) {
  return request<ApiResponseData<Reservations.MyReservationItem>>({
    url: `reservations/my/${id}`,
    method: "put",
    data
  })
}

export function cancelMyReservationApi(id: number, data: Reservations.CancelMyReservationRequestData) {
  return request<ApiResponseData<Reservations.MyReservationItem>>({
    url: `reservations/my/${id}/cancel`,
    method: "patch",
    data
  })
}

export function submitMyReservationReviewApi(id: number, data: Reservations.SubmitMyReservationReviewRequestData) {
  return request<Reservations.SubmitMyReservationReviewResponseData>({
    url: `reservations/my/${id}/review`,
    method: "post",
    data
  })
}

export function getAdminReservationListApi(params: Reservations.AdminReservationQueryRequestData) {
  return request<Reservations.AdminReservationListResponseData>({
    url: "admin/reservations",
    method: "get",
    params
  })
}

export function approveAdminReservationApi(id: number, data: Reservations.AdminReservationActionRequestData = {}) {
  return request<Reservations.AdminReservationActionResponseData>({
    url: `admin/reservations/${id}/approve`,
    method: "patch",
    data
  })
}

export function rejectAdminReservationApi(id: number, data: Reservations.AdminReservationActionRequestData) {
  return request<Reservations.AdminReservationActionResponseData>({
    url: `admin/reservations/${id}/reject`,
    method: "patch",
    data
  })
}

export function markAdminReservationExceptionApi(id: number, data: Reservations.AdminReservationActionRequestData) {
  return request<Reservations.AdminReservationActionResponseData>({
    url: `admin/reservations/${id}/exception`,
    method: "patch",
    data
  })
}
