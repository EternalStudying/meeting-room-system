import { flushPromises, shallowMount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { ref } from "vue"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AdminDevices from "@/pages/admin/devices/index.vue"

const buildDevice = (overrides: Partial<Record<string, unknown>> = {}) => ({
  id: 1,
  deviceCode: "PROJ-01",
  name: "投影仪",
  total: 6,
  status: "ENABLED",
  boundRoomCount: 1,
  boundQuantity: 1,
  availableQuantity: 5,
  boundRooms: [
    {
      roomId: 101,
      roomCode: "A101",
      roomName: "A101 多媒体会议室",
      location: "A楼-1层",
      quantity: 1
    }
  ],
  ...overrides
})

const buildDevicePage = (devices = [buildDevice()]) => ({
  data: {
    list: devices,
    total: devices.length,
    stats: {
      totalCount: devices.length,
      enabledCount: devices.filter(device => device.status === "ENABLED").length,
      disabledCount: devices.filter(device => device.status === "DISABLED").length,
      warningCount: 0
    }
  }
})

const deviceApiMocks = vi.hoisted(() => ({
  getAdminDeviceListApi: vi.fn(),
  createAdminDeviceApi: vi.fn(),
  updateAdminDeviceApi: vi.fn(),
  updateAdminDeviceStatusApi: vi.fn(),
  deleteAdminDeviceApi: vi.fn()
}))

vi.mock("@@/composables/useDevice", () => ({
  useDevice: () => ({
    isMobile: ref(false),
    isDesktop: ref(true)
  })
}))

vi.mock("@/common/apis/rooms", () => ({
  getAdminDeviceListApi: deviceApiMocks.getAdminDeviceListApi,
  createAdminDeviceApi: deviceApiMocks.createAdminDeviceApi,
  updateAdminDeviceApi: deviceApiMocks.updateAdminDeviceApi,
  updateAdminDeviceStatusApi: deviceApiMocks.updateAdminDeviceStatusApi,
  deleteAdminDeviceApi: deviceApiMocks.deleteAdminDeviceApi
}))

describe("AdminDevices", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "error").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "warning").mockImplementation(vi.fn() as any)

    deviceApiMocks.getAdminDeviceListApi.mockResolvedValue(buildDevicePage())
  })

  it("渲染管理端设备管理页，并使用弹窗承载设备详情与状态确认", async () => {
    const wrapper = shallowMount(AdminDevices)

    await flushPromises()

    expect(deviceApiMocks.getAdminDeviceListApi).toHaveBeenCalled()
    expect(wrapper.text()).toContain("设备管理")
    expect(wrapper.text()).toContain("库存设备清单")
    expect(wrapper.text()).toContain("库存概览")
    expect(wrapper.find("aside.detail-panel").exists()).toBe(false)
    expect(wrapper.findComponent({ name: "ElDrawer" }).exists()).toBe(false)
    expect(wrapper.findAllComponents({ name: "ElDialog" })).toHaveLength(4)
  })

  it("wraps the device list page content with pagination transition", async () => {
    const wrapper = shallowMount(AdminDevices)

    await flushPromises()

    expect(wrapper.html()).toContain("name=\"pagination-switch\"")
  })

  it("triggers device page animation only after async data arrives", async () => {
    deviceApiMocks.getAdminDeviceListApi.mockReset()
    let resolveNextPage: ((value: unknown) => void) | null = null

    deviceApiMocks.getAdminDeviceListApi
      .mockResolvedValueOnce({
        data: {
          list: [
            {
              id: 1,
              deviceCode: "PROJ-01",
              name: "投影仪",
              total: 6,
              status: "ENABLED",
              boundRoomCount: 1,
              boundQuantity: 1,
              availableQuantity: 5,
              boundRooms: []
            }
          ],
          total: 2,
          stats: {
            totalCount: 2,
            enabledCount: 2,
            disabledCount: 0,
            warningCount: 0
          }
        }
      })
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveNextPage = resolve
      }))

    const wrapper = shallowMount(AdminDevices)

    await flushPromises()

    const vm = wrapper.vm as any

    expect(vm.devicePageTransitionKey).toBe(0)

    vm.devicePage = 2
    await wrapper.vm.$nextTick()

    expect(vm.devicePageTransitionKey).toBe(0)

    ;(resolveNextPage as unknown as (value: unknown) => void)({
      data: {
        list: [
          {
            id: 2,
            deviceCode: "MIC-02",
            name: "无线麦克风",
            total: 4,
            status: "ENABLED",
            boundRoomCount: 1,
            boundQuantity: 1,
            availableQuantity: 3,
            boundRooms: []
          }
        ],
        total: 2,
        stats: {
          totalCount: 2,
          enabledCount: 2,
          disabledCount: 0,
          warningCount: 0
        }
      }
    })

    await flushPromises()

    expect(vm.deviceCollection[0].id).toBe(2)
    expect(vm.devicePageTransitionKey).toBe(1)
  })

  it("shows one clear error when device code already exists", async () => {
    deviceApiMocks.createAdminDeviceApi.mockRejectedValueOnce(new Error("deviceCode already exists"))

    const wrapper = shallowMount(AdminDevices)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deviceForm = {
      id: null,
      deviceCode: "PROJ-01",
      name: "投影仪",
      total: 6,
      status: "ENABLED"
    }
    await vm.submitDeviceForm()

    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(ElMessage.error).toHaveBeenCalledWith("设备编码已存在，请更换编码")
  })

  it("uses an in-page delete confirmation dialog before deleting an unbound device", async () => {
    const wrapper = shallowMount(AdminDevices)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deleteDevice(buildDevice({
      id: 2,
      deviceCode: "MIC-01",
      name: "无线麦克风",
      boundRoomCount: 0,
      boundQuantity: 0,
      availableQuantity: 4,
      boundRooms: []
    }))
    await wrapper.vm.$nextTick()

    expect(vm.deleteDialogVisible).toBe(true)
    expect(vm.deleteTargetDevice.name).toBe("无线麦克风")
    expect(deviceApiMocks.deleteAdminDeviceApi).not.toHaveBeenCalled()
  })
})
