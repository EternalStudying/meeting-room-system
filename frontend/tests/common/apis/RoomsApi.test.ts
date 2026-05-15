import { describe, expect, it, vi } from "vitest"
import { createAdminDeviceApi, createAdminRoomApi, deleteAdminDeviceApi, deleteAdminRoomApi, getRoomDeviceOptionsApi, getRoomListApi } from "@/common/apis/rooms"
import { request } from "@/http/axios"

vi.mock("@/http/axios", () => ({
  request: vi.fn()
}))

describe("rooms api", () => {
  it("passes deviceIds to room list query", async () => {
    vi.mocked(request).mockResolvedValueOnce({
      data: {
        list: [],
        total: 0,
        stats: {
          totalCount: 0,
          availableCount: 0,
          maintenanceCount: 0
        }
      }
    })

    await getRoomListApi({
      currentPage: 1,
      size: 6,
      deviceIds: "1,2"
    })

    expect(request).toHaveBeenCalledWith({
      url: "rooms",
      method: "get",
      params: {
        currentPage: 1,
        size: 6,
        deviceIds: "1,2"
      }
    })
  })

  it("fetches room device options", async () => {
    vi.mocked(request).mockResolvedValueOnce({
      data: [{ id: 1, name: "投影仪" }]
    })

    await getRoomDeviceOptionsApi()

    expect(request).toHaveBeenCalledWith({
      url: "rooms/device-options",
      method: "get"
    })
  })

  it("creates admin rooms with local error handling enabled", async () => {
    vi.mocked(request).mockResolvedValueOnce({ data: 101 })

    await createAdminRoomApi({
      roomCode: "A101",
      name: "A101 多媒体会议室",
      location: "A楼 1层",
      capacity: 12,
      status: "AVAILABLE",
      description: "",
      maintenanceRemark: ""
    })

    expect(request).toHaveBeenCalledWith({
      url: "admin/rooms",
      method: "post",
      data: {
        roomCode: "A101",
        name: "A101 多媒体会议室",
        location: "A楼 1层",
        capacity: 12,
        status: "AVAILABLE",
        description: "",
        maintenanceRemark: ""
      },
      silentError: true
    })
  })

  it("deletes admin rooms with local error handling enabled", async () => {
    vi.mocked(request).mockResolvedValueOnce({})

    await deleteAdminRoomApi(101)

    expect(request).toHaveBeenCalledWith({
      url: "admin/rooms/101",
      method: "delete",
      silentError: true
    })
  })

  it("creates admin devices with local error handling enabled", async () => {
    vi.mocked(request).mockResolvedValueOnce({})

    await createAdminDeviceApi({
      deviceCode: "PROJ-01",
      name: "投影仪",
      total: 6,
      status: "ENABLED"
    })

    expect(request).toHaveBeenCalledWith({
      url: "admin/devices",
      method: "post",
      data: {
        deviceCode: "PROJ-01",
        name: "投影仪",
        total: 6,
        status: "ENABLED"
      },
      silentError: true
    })
  })

  it("deletes admin devices with local error handling enabled", async () => {
    vi.mocked(request).mockResolvedValueOnce({})

    await deleteAdminDeviceApi(1)

    expect(request).toHaveBeenCalledWith({
      url: "admin/devices/1",
      method: "delete",
      silentError: true
    })
  })
})
