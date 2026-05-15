import type * as Rooms from "./type"
import { request } from "@/http/axios"

export function getRoomListApi(params: Rooms.RoomQueryRequestData) {
  return request<Rooms.RoomListResponseData>({
    url: "rooms",
    method: "get",
    params
  })
}

export function getRoomLocationsApi() {
  return request<Rooms.RoomLocationListResponseData>({
    url: "rooms/locations",
    method: "get"
  })
}

export function getRoomDeviceOptionsApi() {
  return request<Rooms.RoomDeviceOptionListResponseData>({
    url: "rooms/device-options",
    method: "get"
  })
}

export function getAdminRoomListApi(params: Rooms.AdminRoomListRequestData) {
  return request<Rooms.AdminRoomListResponseData>({
    url: "admin/rooms",
    method: "get",
    params
  })
}

export function getAdminRoomDetailApi(id: number) {
  return request<Rooms.AdminRoomDetailResponseData>({
    url: `admin/rooms/${id}`,
    method: "get"
  })
}

export function createAdminRoomApi(data: Rooms.AdminRoomUpsertRequestData) {
  return request<Rooms.AdminRoomCreateResponseData>({
    url: "admin/rooms",
    method: "post",
    data,
    silentError: true
  })
}

export function updateAdminRoomApi(id: number, data: Rooms.AdminRoomUpsertRequestData) {
  return request<ApiResponseData<unknown>>({
    url: `admin/rooms/${id}`,
    method: "put",
    data,
    silentError: true
  })
}

export function updateAdminRoomStatusApi(id: number, data: Rooms.AdminRoomStatusPatchRequestData) {
  return request<ApiResponseData<unknown>>({
    url: `admin/rooms/${id}/status`,
    method: "patch",
    data
  })
}

export function updateAdminRoomDevicesApi(id: number, data: Rooms.AdminRoomDeviceBindingRequestData) {
  return request<ApiResponseData<unknown>>({
    url: `admin/rooms/${id}/devices`,
    method: "put",
    data
  })
}

export function deleteAdminRoomApi(id: number) {
  return request<ApiResponseData<unknown>>({
    url: `admin/rooms/${id}`,
    method: "delete",
    silentError: true
  })
}

export function getAdminDeviceListApi(params: Rooms.AdminDeviceQueryRequestData) {
  return request<Rooms.AdminDeviceListResponseData>({
    url: "admin/devices",
    method: "get",
    params
  })
}

export function getAdminDeviceDetailApi(id: number) {
  return request<Rooms.AdminDeviceDetailResponseData>({
    url: `admin/devices/${id}`,
    method: "get"
  })
}

export function createAdminDeviceApi(data: Rooms.AdminDeviceUpsertRequestData) {
  return request<ApiResponseData<unknown>>({
    url: "admin/devices",
    method: "post",
    data,
    silentError: true
  })
}

export function updateAdminDeviceApi(id: number, data: Rooms.AdminDeviceUpsertRequestData) {
  return request<ApiResponseData<unknown>>({
    url: `admin/devices/${id}`,
    method: "put",
    data,
    silentError: true
  })
}

export function updateAdminDeviceStatusApi(id: number, data: Rooms.AdminDeviceStatusPatchRequestData) {
  return request<ApiResponseData<unknown>>({
    url: `admin/devices/${id}/status`,
    method: "patch",
    data
  })
}

export function deleteAdminDeviceApi(id: number) {
  return request<ApiResponseData<unknown>>({
    url: `admin/devices/${id}`,
    method: "delete",
    silentError: true
  })
}

export function getAdminDeviceBindingStatsApi() {
  return request<Rooms.AdminDeviceBindingStatsResponseData>({
    url: "admin/device-stats",
    method: "get"
  })
}
