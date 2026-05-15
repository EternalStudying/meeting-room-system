import type { AdminRoomDeviceBindingItem, RoomStatus } from "@/common/apis/rooms/type"

export interface RoomUpsertForm {
  id: number | null
  roomCode: string
  name: string
  location: string
  capacity: number
  status: RoomStatus
  description: string
  maintenanceRemark: string
  deviceBindings: AdminRoomDeviceBindingItem[]
}

export function createEmptyRoomForm(): RoomUpsertForm {
  return {
    id: null,
    roomCode: "",
    name: "",
    location: "",
    capacity: 8,
    status: "AVAILABLE",
    description: "",
    maintenanceRemark: "",
    deviceBindings: []
  }
}
