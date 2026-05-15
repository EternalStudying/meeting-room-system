import { flushPromises, shallowMount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"
import AdminDeviceStats from "@/pages/admin/device-stats/index.vue"

const roomApiMocks = vi.hoisted(() => ({
  getAdminDeviceBindingStatsApi: vi.fn().mockResolvedValue({
    data: {
      totalBindingCount: 63,
      boundDeviceTypeCount: 10,
      boundRoomCount: 8,
      unboundRoomCount: 0,
      devices: [
        {
          id: 6,
          deviceCode: "AC",
          name: "空调",
          total: 10,
          status: "ENABLED",
          boundRoomCount: 8,
          rooms: [
            {
              roomId: 1,
              roomCode: "R-A101",
              roomName: "A101 多媒体会议室",
              location: "A楼-1层"
            }
          ],
          bindingRate: 1
        }
      ],
      rooms: [
        {
          roomId: 1,
          roomCode: "R-A101",
          roomName: "A101 多媒体会议室",
          location: "A楼-1层",
          roomStatus: "AVAILABLE",
          deviceTypeCount: 3,
          boundDevices: [
            {
              deviceId: 6,
              deviceCode: "AC",
              name: "空调",
              status: "ENABLED"
            },
            {
              deviceId: 7,
              deviceCode: "LIGHT",
              name: "智能灯光",
              status: "ENABLED"
            },
            {
              deviceId: 4,
              deviceCode: "MIC",
              name: "无线麦克风",
              status: "ENABLED"
            }
          ],
          bindingLevel: "heavy"
        }
      ]
    }
  })
}))

vi.mock("@/common/apis/rooms", () => ({
  getAdminDeviceBindingStatsApi: roomApiMocks.getAdminDeviceBindingStatsApi
}))

vi.mock("echarts", () => ({
  init: () => ({
    setOption: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn()
  })
}))

describe("AdminDeviceStats", () => {
  it("挂载时优先请求后端统计接口，并兼容后端返回的绑定设备字段", async () => {
    const wrapper = shallowMount(AdminDeviceStats)

    await flushPromises()

    const setupState = (wrapper.vm.$ as any).setupState as {
      overview: {
        rooms: Array<{ boundDevices: Array<{ deviceName: string, deviceStatus: string }> }>
        devices: Array<{ rooms: Array<{ roomStatus?: string }> }>
      }
    }

    expect(roomApiMocks.getAdminDeviceBindingStatsApi).toHaveBeenCalled()
    expect(wrapper.text()).toContain("Binding Overview")
    expect(wrapper.text()).toContain("Device Detail")
    expect(wrapper.text()).toContain("Room Detail")
    expect(setupState.overview.rooms[0]?.boundDevices.map(item => item.deviceName)).toEqual(["空调", "智能灯光", "无线麦克风"])
    expect(setupState.overview.rooms[0]?.boundDevices.map(item => item.deviceStatus)).toEqual(["ENABLED", "ENABLED", "ENABLED"])
    expect(setupState.overview.devices[0]?.rooms[0]?.roomStatus).toBe("AVAILABLE")
  })

  it("接口失败时展示空绑定数据", async () => {
    roomApiMocks.getAdminDeviceBindingStatsApi.mockRejectedValueOnce(new Error("network"))

    const wrapper = shallowMount(AdminDeviceStats)

    await flushPromises()

    expect(roomApiMocks.getAdminDeviceBindingStatsApi).toHaveBeenCalled()
    expect(wrapper.text()).toContain("Binding Overview")
    expect(wrapper.text()).toContain("Device Ranking")
    expect((wrapper.vm as any).overview.devices).toEqual([])
    expect((wrapper.vm as any).overview.rooms).toEqual([])
  })
})
