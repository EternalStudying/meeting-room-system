import { flushPromises, mount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import RoomsPage from "@/pages/rooms/index.vue"

const buildRoom = (overrides: Partial<Record<string, unknown>> = {}) => ({
  id: 101,
  roomCode: "A101",
  name: "A101 多媒体会议室",
  location: "A栋 1 层",
  capacity: 12,
  status: "AVAILABLE",
  description: "适合小组讨论",
  devices: [
    {
      id: 1,
      deviceCode: "PROJ-01",
      name: "投影仪",
      quantity: 1,
      total: 4,
      status: "ENABLED"
    }
  ],
  ...overrides
})

const roomApiMocks = vi.hoisted(() => ({
  getRoomListApi: vi.fn(),
  getRoomLocationsApi: vi.fn(),
  getRoomDeviceOptionsApi: vi.fn()
}))

const reservationApiMocks = vi.hoisted(() => ({
  createReservationApi: vi.fn()
}))

vi.mock("@/common/apis/rooms", () => ({
  getRoomListApi: roomApiMocks.getRoomListApi,
  getRoomLocationsApi: roomApiMocks.getRoomLocationsApi,
  getRoomDeviceOptionsApi: roomApiMocks.getRoomDeviceOptionsApi
}))

vi.mock("@/common/apis/reservations", () => ({
  createReservationApi: reservationApiMocks.createReservationApi
}))

describe("RoomsPage", () => {
  beforeEach(() => {
    vi.spyOn(ElMessage, "warning").mockImplementation(vi.fn() as any)
    roomApiMocks.getRoomListApi.mockReset()
    roomApiMocks.getRoomLocationsApi.mockReset()
    roomApiMocks.getRoomDeviceOptionsApi.mockReset()
    reservationApiMocks.createReservationApi.mockReset()

    roomApiMocks.getRoomLocationsApi.mockResolvedValue({
      data: ["A栋 1 层", "A栋 2 层"]
    })

    roomApiMocks.getRoomDeviceOptionsApi.mockResolvedValue({
      data: [
        { id: 1, name: "投影仪" },
        { id: 2, name: "电子白板" }
      ]
    })

    roomApiMocks.getRoomListApi.mockResolvedValue({
      data: {
        list: [buildRoom()],
        total: 1,
        stats: {
          totalCount: 1,
          availableCount: 1,
          maintenanceCount: 0
        }
      }
    })

    reservationApiMocks.createReservationApi.mockResolvedValue({
      data: {
        id: 9001,
        reservationNo: "RSV-20260407-001"
      }
    })
  })

  it("点击房间卡片状态可打开预约弹窗并带入当前房间", async () => {
    const wrapper = mount(RoomsPage)

    await flushPromises()

    const vm = wrapper.vm as any
    const room = vm.roomList[0]

    vm.openReservationDialog(room)
    await flushPromises()

    expect(vm.reservationDialogVisible).toBe(true)
    expect(vm.reservationDialogPreset).toMatchObject({
      roomId: 101,
      attendees: 1
    })
  })

  it("点击维护中状态时提示不可预约且不打开预约弹窗", async () => {
    roomApiMocks.getRoomListApi.mockResolvedValue({
      data: {
        list: [buildRoom({ status: "MAINTENANCE" })],
        total: 1,
        stats: {
          totalCount: 1,
          availableCount: 0,
          maintenanceCount: 1
        }
      }
    })

    const wrapper = mount(RoomsPage)

    await flushPromises()

    await wrapper.find(".room-status").trigger("click")

    const vm = wrapper.vm as any
    expect(ElMessage.warning).toHaveBeenCalledWith("当前会议室不可预约")
    expect(vm.reservationDialogVisible).toBe(false)
  })

  it("打开房间详情后能复用当前房间打开预约弹窗", async () => {
    const wrapper = mount(RoomsPage)

    await flushPromises()

    const vm = wrapper.vm as any
    const room = vm.roomList[0]

    vm.openRoomDialog(room)
    await flushPromises()
    vm.openReservationDialog(vm.selectedRoom)
    await flushPromises()

    expect(vm.dialogVisible).toBe(true)
    expect(vm.reservationDialogVisible).toBe(true)
    expect(vm.reservationDialogPreset).toMatchObject({
      roomId: 101,
      attendees: 1
    })
  })

  it("选择设备条件时按设备 ID 重新查询房间", async () => {
    const wrapper = mount(RoomsPage)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.deviceFilter = [1, 2]
    await wrapper.vm.$nextTick()
    await flushPromises()

    expect(roomApiMocks.getRoomListApi).toHaveBeenLastCalledWith(expect.objectContaining({
      currentPage: 1,
      deviceIds: "1,2"
    }))
  })

  it("only starts the rooms page animation after async page data arrives", async () => {
    roomApiMocks.getRoomListApi.mockReset()
    let resolveNextPage: ((value: unknown) => void) | null = null

    roomApiMocks.getRoomListApi
      .mockResolvedValueOnce({
        data: {
          list: [buildRoom()],
          total: 2,
          stats: {
            totalCount: 2,
            availableCount: 2,
            maintenanceCount: 0
          }
        }
      })
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveNextPage = resolve
      }))

    const wrapper = mount(RoomsPage)

    await flushPromises()

    const vm = wrapper.vm as any

    expect(vm.roomsPageTransitionKey).toBe(0)

    vm.handlePageChange(2)
    await wrapper.vm.$nextTick()

    expect(vm.roomsPageTransitionKey).toBe(0)

    ;(resolveNextPage as unknown as (value: unknown) => void)({
      data: {
        list: [buildRoom({ id: 202, roomCode: "B202", name: "B202 Room" })],
        total: 2,
        stats: {
          totalCount: 2,
          availableCount: 2,
          maintenanceCount: 0
        }
      }
    })

    await flushPromises()

    expect(vm.roomList[0].id).toBe(202)
    expect(vm.roomsPageTransitionKey).toBe(1)
  })
})
