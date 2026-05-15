import { flushPromises, shallowMount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AdminRooms from "@/pages/admin/rooms/index.vue"

const buildRoom = (overrides: Partial<Record<string, unknown>> = {}) => ({
  id: 101,
  roomCode: "A101",
  name: "Room A101",
  location: "Floor 1",
  capacity: 12,
  status: "AVAILABLE",
  description: "Project sync room",
  devices: [],
  deviceCount: 0,
  deviceBindingSummary: "No devices",
  maintenanceRemark: "",
  ...overrides
})

const roomApiMocks = vi.hoisted(() => ({
  getRoomLocationsApi: vi.fn(),
  getAdminRoomListApi: vi.fn(),
  createAdminRoomApi: vi.fn(),
  updateAdminRoomApi: vi.fn(),
  updateAdminRoomStatusApi: vi.fn(),
  updateAdminRoomDevicesApi: vi.fn(),
  deleteAdminRoomApi: vi.fn(),
  getAdminDeviceListApi: vi.fn()
}))

function resolveIfPending(resolve: ((value: unknown) => void) | null, value: unknown) {
  if (resolve) resolve(value)
}

vi.mock("@/common/apis/rooms", () => ({
  getRoomLocationsApi: roomApiMocks.getRoomLocationsApi,
  getAdminRoomListApi: roomApiMocks.getAdminRoomListApi,
  createAdminRoomApi: roomApiMocks.createAdminRoomApi,
  updateAdminRoomApi: roomApiMocks.updateAdminRoomApi,
  updateAdminRoomStatusApi: roomApiMocks.updateAdminRoomStatusApi,
  updateAdminRoomDevicesApi: roomApiMocks.updateAdminRoomDevicesApi,
  deleteAdminRoomApi: roomApiMocks.deleteAdminRoomApi,
  getAdminDeviceListApi: roomApiMocks.getAdminDeviceListApi
}))

describe("AdminRooms", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "error").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "warning").mockImplementation(vi.fn() as any)

    roomApiMocks.getRoomLocationsApi.mockReset()
    roomApiMocks.getAdminRoomListApi.mockReset()
    roomApiMocks.createAdminRoomApi.mockReset()
    roomApiMocks.updateAdminRoomApi.mockReset()
    roomApiMocks.updateAdminRoomStatusApi.mockReset()
    roomApiMocks.updateAdminRoomDevicesApi.mockReset()
    roomApiMocks.deleteAdminRoomApi.mockReset()
    roomApiMocks.getAdminDeviceListApi.mockReset()

    roomApiMocks.getRoomLocationsApi.mockResolvedValue({
      data: ["Floor 1", "Floor 2"]
    })

    roomApiMocks.getAdminRoomListApi.mockResolvedValue({
      data: {
        list: [buildRoom()],
        total: 1,
        stats: {
          totalCount: 1,
          availableCount: 1,
          maintenanceCount: 0,
          unboundCount: 1,
          largeRoomCount: 0
        }
      }
    })

    roomApiMocks.getAdminDeviceListApi.mockResolvedValue({
      data: {
        list: [
          {
            id: 1,
            deviceCode: "PROJ-01",
            name: "Projector",
            total: 6,
            status: "ENABLED"
          }
        ],
        total: 1
      }
    })
  })

  it("renders the admin room dashboard and keeps room list scrollable", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    expect(roomApiMocks.getAdminRoomListApi).toHaveBeenCalled()
    expect(roomApiMocks.getAdminDeviceListApi).toHaveBeenCalled()
    expect(wrapper.find(".room-list-scroll").exists()).toBe(true)
  })

  it("keeps watch rooms derived from the full filtered collection", async () => {
    const fullRooms = [
      buildRoom({ id: 1, roomCode: "A101", name: "Room A101", devices: [{ id: 1 }], deviceCount: 1 }),
      buildRoom({ id: 2, roomCode: "A102", name: "Room A102", devices: [{ id: 2 }], deviceCount: 1 }),
      buildRoom({ id: 3, roomCode: "A201", name: "Room A201", devices: [{ id: 3 }], deviceCount: 1 }),
      buildRoom({ id: 4, roomCode: "B301", name: "Room B301", devices: [{ id: 4 }], deviceCount: 1 }),
      buildRoom({ id: 5, roomCode: "C102", name: "Room C102", description: "", devices: [{ id: 5 }], deviceCount: 1 })
    ]

    roomApiMocks.getAdminRoomListApi.mockImplementation(async ({ currentPage, size }: { currentPage: number, size: number }) => {
      if (size === 200) {
        return {
          data: {
            list: fullRooms,
            total: fullRooms.length,
            stats: {
              totalCount: fullRooms.length,
              availableCount: fullRooms.length,
              maintenanceCount: 0,
              unboundCount: 0,
              largeRoomCount: 0
            }
          }
        }
      }

      const start = (currentPage - 1) * size
      return {
        data: {
          list: fullRooms.slice(start, start + size),
          total: fullRooms.length,
          stats: {
            totalCount: fullRooms.length,
            availableCount: fullRooms.length,
            maintenanceCount: 0,
            unboundCount: 0,
            largeRoomCount: 0
          }
        }
      }
    })

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any

    expect(vm.roomCollection.map((item: { roomCode: string }) => item.roomCode)).toEqual(["A101", "A102", "A201", "B301"])
    expect(vm.watchRooms.map((item: { roomCode: string }) => item.roomCode)).toEqual(["C102"])
    expect(vm.pagedWatchRooms.map((item: { roomCode: string }) => item.roomCode)).toEqual(["C102"])
  })

  it("renders the room upsert dialog as a separate component", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openCreateDialog()
    await wrapper.vm.$nextTick()

    const upsertDialog = wrapper.findComponent({ name: "RoomUpsertDialog" })

    expect(upsertDialog.exists()).toBe(true)
    expect(upsertDialog.props("modelValue")).toBe(true)
  })

  it("passes selected device ids to the admin room query", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deviceFilterIds = [1]
    await wrapper.vm.$nextTick()
    await flushPromises()

    const latestCalls = roomApiMocks.getAdminRoomListApi.mock.calls.slice(-2).map(([params]) => params)

    expect(latestCalls).toEqual([
      expect.objectContaining({ currentPage: 1, size: 4, deviceIds: "1" }),
      expect.objectContaining({ currentPage: 1, size: 200, deviceIds: "1" })
    ])
  })

  it("binds selected devices after creating a room", async () => {
    roomApiMocks.createAdminRoomApi.mockResolvedValueOnce({ data: 202 })

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    await vm.submitRoomFormPayload({
      id: null,
      roomCode: "B201",
      name: "Room B201",
      location: "Floor 2",
      capacity: 10,
      status: "AVAILABLE",
      description: "Review room",
      maintenanceRemark: "",
      deviceBindings: [{ deviceId: 1, quantity: 2 }]
    })
    await flushPromises()

    expect(roomApiMocks.createAdminRoomApi).toHaveBeenCalledWith(expect.objectContaining({
      roomCode: "B201",
      name: "Room B201"
    }))
    expect(roomApiMocks.updateAdminRoomDevicesApi).toHaveBeenCalledWith(202, {
      devices: [{ deviceId: 1, quantity: 2 }]
    })
  })

  it("clears stale radial overlay when changing room pages", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openRadialMenu({
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      clientX: 240,
      clientY: 240
    }, vm.roomCollection[0])

    expect(vm.radialMenuVisible).toBe(true)

    vm.roomPage = 2
    await wrapper.vm.$nextTick()

    expect(vm.radialMenuVisible).toBe(false)
  })

  it("opens detail after paging for a room with an empty backend description", async () => {
    const fullRooms = [
      buildRoom({ id: 1, roomCode: "A101", name: "Room A101" }),
      buildRoom({ id: 2, roomCode: "A102", name: "Room A102" }),
      buildRoom({ id: 3, roomCode: "A103", name: "Room A103" }),
      buildRoom({ id: 4, roomCode: "A104", name: "Room A104" }),
      buildRoom({
        id: 106,
        roomCode: "A401",
        name: "阶梯会议室",
        location: "A楼 4层",
        description: null,
        devices: [],
        deviceCount: 0,
        deviceBindingSummary: "0 类设备 / 0 类可用"
      })
    ]

    roomApiMocks.getAdminRoomListApi.mockImplementation(async ({ currentPage, size }: { currentPage: number, size: number }) => {
      if (size === 200) {
        return {
          data: {
            list: fullRooms,
            total: fullRooms.length,
            stats: {
              totalCount: fullRooms.length,
              availableCount: fullRooms.length,
              maintenanceCount: 0,
              unboundCount: 1,
              largeRoomCount: 0
            }
          }
        }
      }

      const start = (currentPage - 1) * size
      return {
        data: {
          list: fullRooms.slice(start, start + size),
          total: fullRooms.length,
          stats: {
            totalCount: fullRooms.length,
            availableCount: fullRooms.length,
            maintenanceCount: 0,
            unboundCount: 1,
            largeRoomCount: 0
          }
        }
      }
    })

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.roomPage = 2
    await wrapper.vm.$nextTick()
    await flushPromises()

    vm.openDetail(vm.roomCollection[0])
    await wrapper.vm.$nextTick()

    expect(vm.detailDrawerVisible).toBe(true)
    expect(vm.selectedRoom.name).toBe("阶梯会议室")
    expect(vm.selectedRoomWarnings).toEqual(["未绑定设备", "缺少空间说明"])
  })

  it("shows one clear error when room code already exists", async () => {
    roomApiMocks.createAdminRoomApi.mockRejectedValueOnce(new Error("roomCode already exists"))

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    await vm.submitRoomFormPayload({
      id: null,
      roomCode: "A101",
      name: "Room A101",
      location: "Floor 1",
      capacity: 12,
      status: "AVAILABLE",
      description: "Duplicate code",
      maintenanceRemark: "",
      deviceBindings: []
    })

    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(ElMessage.error).toHaveBeenCalledWith("会议室编码已存在，请更换编码")
  })

  it("opens a maintenance remark dialog before switching an available room to maintenance", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    await vm.toggleRoomStatus(buildRoom({ id: 101, status: "AVAILABLE", maintenanceRemark: "" }))

    expect(vm.maintenanceDialogVisible).toBe(true)
    expect(vm.maintenanceTargetRoom.name).toBe("Room A101")
    expect(roomApiMocks.updateAdminRoomStatusApi).not.toHaveBeenCalled()
  })

  it("requires a maintenance remark before saving maintenance status", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    await vm.submitRoomFormPayload({
      id: null,
      roomCode: "M101",
      name: "Maintenance Room",
      location: "Floor 1",
      capacity: 8,
      status: "MAINTENANCE",
      description: "Under repair",
      maintenanceRemark: "   ",
      deviceBindings: []
    })

    expect(ElMessage.warning).toHaveBeenCalledWith("请填写维护备注")
    expect(roomApiMocks.createAdminRoomApi).not.toHaveBeenCalled()
  })

  it("submits the maintenance remark when confirming the status switch", async () => {
    roomApiMocks.updateAdminRoomStatusApi.mockResolvedValueOnce({})

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.toggleRoomStatus(buildRoom({ id: 101, status: "AVAILABLE", maintenanceRemark: "" }))
    vm.maintenanceRemarkDraft = "投影线路检修"
    await vm.submitMaintenanceStatus()
    await flushPromises()

    expect(roomApiMocks.updateAdminRoomStatusApi).toHaveBeenCalledWith(101, {
      status: "MAINTENANCE",
      maintenanceRemark: "投影线路检修"
    })
    expect(vm.maintenanceDialogVisible).toBe(false)
  })

  it("uses an in-page delete confirmation dialog before deleting a room", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deleteRoom(buildRoom({ id: 101, name: "Room A101" }))
    await wrapper.vm.$nextTick()

    expect(vm.deleteDialogVisible).toBe(true)
    expect(vm.deleteTargetRoom.name).toBe("Room A101")
    expect(roomApiMocks.deleteAdminRoomApi).not.toHaveBeenCalled()
  })

  it("shows one clear error when deleting a room with reservations", async () => {
    roomApiMocks.deleteAdminRoomApi.mockRejectedValueOnce(new Error("room has related reservations and cannot be deleted"))

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deleteRoom(buildRoom({ id: 101, name: "Room A101" }))
    await vm.confirmDeleteRoom()
    await flushPromises()

    expect(roomApiMocks.deleteAdminRoomApi).toHaveBeenCalledWith(101)
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(ElMessage.error).toHaveBeenCalledWith("该会议室已有预约记录，暂不允许删除")
    expect(vm.deleteDialogVisible).toBe(false)
  })

  it("wraps both paginated room sections with pagination transition", async () => {
    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const html = wrapper.html()

    expect(html.match(/name="pagination-switch"/g)).toHaveLength(2)
  })

  it("triggers room list animation only after async data arrives", async () => {
    let resolvePagedPageTwo: ((value: unknown) => void) | null = null
    let resolveFilteredPageTwo: ((value: unknown) => void) | null = null

    roomApiMocks.getAdminRoomListApi.mockReset()
    roomApiMocks.getAdminRoomListApi.mockImplementation(({ currentPage, size }: { currentPage: number, size: number }) => {
      if (currentPage === 1 && size === 4) {
        return Promise.resolve({
          data: {
            list: [buildRoom({ id: 1, roomCode: "A101", name: "Room A101" })],
            total: 2,
            stats: {
              totalCount: 2,
              availableCount: 2,
              maintenanceCount: 0,
              unboundCount: 2,
              largeRoomCount: 0
            }
          }
        })
      }

      if (currentPage === 1 && size === 200) {
        return Promise.resolve({
          data: {
            list: [
              buildRoom({ id: 1, roomCode: "A101", name: "Room A101" }),
              buildRoom({ id: 2, roomCode: "A102", name: "Room A102" })
            ],
            total: 2,
            stats: {
              totalCount: 2,
              availableCount: 2,
              maintenanceCount: 0,
              unboundCount: 2,
              largeRoomCount: 0
            }
          }
        })
      }

      if (currentPage === 2 && size === 4) {
        return new Promise((resolve) => {
          resolvePagedPageTwo = resolve
        })
      }

      return new Promise((resolve) => {
        resolveFilteredPageTwo = resolve
      })
    })

    const wrapper = shallowMount(AdminRooms)

    await flushPromises()

    const vm = wrapper.vm as any

    expect(vm.roomPageTransitionKey).toBe(0)

    vm.roomPage = 2
    await wrapper.vm.$nextTick()

    expect(vm.roomPageTransitionKey).toBe(0)

    ;(resolvePagedPageTwo as unknown as (value: unknown) => void)({
      data: {
        list: [buildRoom({ id: 2, roomCode: "A102", name: "Room A102" })],
        total: 2,
        stats: {
          totalCount: 2,
          availableCount: 2,
          maintenanceCount: 0,
          unboundCount: 2,
          largeRoomCount: 0
        }
      }
    })
    resolveIfPending(resolveFilteredPageTwo, {
      data: {
        list: [
          buildRoom({ id: 1, roomCode: "A101", name: "Room A101" }),
          buildRoom({ id: 2, roomCode: "A102", name: "Room A102" })
        ],
        total: 2,
        stats: {
          totalCount: 2,
          availableCount: 2,
          maintenanceCount: 0,
          unboundCount: 2,
          largeRoomCount: 0
        }
      }
    })

    await flushPromises()

    expect(vm.roomCollection[0].id).toBe(2)
    expect(vm.roomPageTransitionKey).toBe(1)
  })
})
