export type RoomStatus = "AVAILABLE" | "MAINTENANCE"
export type DeviceStatus = "ENABLED" | "DISABLED"
export type CapacityType = "small" | "medium" | "large"

export interface RoomQueryRequestData {
  currentPage: number
  size: number
  keyword?: string
  status?: RoomStatus
  capacityType?: CapacityType
  location?: string
  deviceIds?: string
}

export interface RoomDeviceData {
  id: number
  deviceCode: string
  name: string
  quantity: number
  total: number
  status: DeviceStatus
}

export interface RoomDeviceOptionData {
  id: number
  name: string
}

export interface RoomData {
  id: number
  roomCode: string
  name: string
  location: string
  capacity: number
  status: RoomStatus
  description: string
  devices: RoomDeviceData[]
}

export interface RoomListStats {
  totalCount: number
  availableCount: number
  maintenanceCount: number
}

export interface AdminRoomListItem extends RoomData {
  deviceCount: number
  deviceBindingSummary: string
  maintenanceRemark?: string
}

export interface AdminRoomListStats extends RoomListStats {
  unboundCount: number
  largeRoomCount: number
}

export interface AdminRoomListRequestData extends RoomQueryRequestData {}

export interface AdminRoomUpsertRequestData {
  roomCode: string
  name: string
  location: string
  capacity: number
  status: RoomStatus
  description: string
  maintenanceRemark?: string
}

export interface AdminRoomStatusPatchRequestData {
  status: RoomStatus
  maintenanceRemark?: string
}

export interface AdminRoomDeviceBindingItem {
  deviceId: number
  quantity: number
}

export interface AdminRoomDeviceBindingRequestData {
  devices: AdminRoomDeviceBindingItem[]
}

export interface DeviceInventoryData {
  id: number
  deviceCode: string
  name: string
  total: number
  status: DeviceStatus
}

export interface AdminDeviceQueryRequestData {
  currentPage: number
  size: number
  keyword?: string
  status?: DeviceStatus
}

export interface AdminDeviceBoundRoomItem {
  roomId: number
  roomCode: string
  roomName: string
  location: string
  quantity: number
}

export interface AdminDeviceListItem extends DeviceInventoryData {
  boundRoomCount: number
  boundQuantity: number
  availableQuantity: number
  boundRooms: AdminDeviceBoundRoomItem[]
}

export interface AdminDeviceUpsertRequestData {
  deviceCode: string
  name: string
  total: number
  status: DeviceStatus
}

export interface AdminDeviceStatusPatchRequestData {
  status: DeviceStatus
}

export interface AdminDeviceListStats {
  totalCount: number
  enabledCount: number
  disabledCount: number
  warningCount: number
}

export type BindingLevel = "none" | "light" | "medium" | "heavy"

export interface AdminDeviceBindingRoomItem {
  roomId: number
  roomCode: string
  roomName: string
  location: string
  roomStatus?: RoomStatus
}

export interface AdminDeviceBindingStatsItem extends DeviceInventoryData {
  boundRoomCount: number
  rooms: AdminDeviceBindingRoomItem[]
  bindingRate: number
}

export interface AdminRoomBindingDeviceItem {
  deviceId: number
  deviceCode: string
  deviceName: string
  deviceStatus: DeviceStatus
  name?: string
  status?: DeviceStatus
}

export interface AdminRoomBindingStatsItem {
  roomId: number
  roomCode: string
  roomName: string
  location: string
  roomStatus: RoomStatus
  deviceTypeCount: number
  boundDevices: AdminRoomBindingDeviceItem[]
  bindingLevel: BindingLevel
}

export interface AdminDeviceBindingOverview {
  totalBindingCount: number
  boundDeviceTypeCount: number
  boundRoomCount: number
  unboundRoomCount: number
  devices: AdminDeviceBindingStatsItem[]
  rooms: AdminRoomBindingStatsItem[]
}

export type RoomListResponseData = ApiResponseData<{
  list: RoomData[]
  total: number
  stats: RoomListStats
}>

export type AdminRoomListResponseData = ApiResponseData<{
  list: AdminRoomListItem[]
  total: number
  stats: AdminRoomListStats
}>

export type AdminRoomDetailResponseData = ApiResponseData<AdminRoomListItem>

export type AdminRoomCreateResponseData = ApiResponseData<number>

export type AdminDeviceListResponseData = ApiResponseData<{
  list: AdminDeviceListItem[]
  total: number
  stats: AdminDeviceListStats
}>

export type AdminDeviceDetailResponseData = ApiResponseData<AdminDeviceListItem>

export type AdminDeviceBindingStatsResponseData = ApiResponseData<AdminDeviceBindingOverview>

export type RoomLocationListResponseData = ApiResponseData<string[]>

export type RoomDeviceOptionListResponseData = ApiResponseData<RoomDeviceOptionData[]>
