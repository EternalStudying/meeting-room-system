import { flushPromises, shallowMount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import MyReservations from "@/pages/reservations/index.vue"

const reservationApiMocks = vi.hoisted(() => ({
  getMyReservationsApi: vi.fn(),
  getMyEndedReservationsPageApi: vi.fn(),
  getMyReservationRoomOptionsApi: vi.fn(),
  getMyReservationDetailApi: vi.fn(),
  updateMyReservationApi: vi.fn(),
  cancelMyReservationApi: vi.fn(),
  submitMyReservationReviewApi: vi.fn()
}))

const roomApiMocks = vi.hoisted(() => ({
  getRoomListApi: vi.fn()
}))

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, string>
}))

vi.mock("@/common/apis/reservations", () => ({
  getMyReservationsApi: reservationApiMocks.getMyReservationsApi,
  getMyEndedReservationsPageApi: reservationApiMocks.getMyEndedReservationsPageApi,
  getMyReservationRoomOptionsApi: reservationApiMocks.getMyReservationRoomOptionsApi,
  getMyReservationDetailApi: reservationApiMocks.getMyReservationDetailApi,
  updateMyReservationApi: reservationApiMocks.updateMyReservationApi,
  cancelMyReservationApi: reservationApiMocks.cancelMyReservationApi,
  submitMyReservationReviewApi: reservationApiMocks.submitMyReservationReviewApi
}))

vi.mock("@/common/apis/rooms", () => ({
  getRoomListApi: roomApiMocks.getRoomListApi
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...await importOriginal<typeof import("vue-router")>(),
  useRoute: () => routeState
}))

const activeReservations = [
  {
    id: 1,
    reservationNo: "RSV-20260405-001",
    roomId: 101,
    roomCode: "A101",
    roomName: "A101 Room",
    roomLocation: "Floor A1",
    roomCapacity: 12,
    roomDescription: "Focus room",
    organizerId: 1,
    organizerName: "Alice",
    title: "Weekly Sync",
    attendees: 8,
    startTime: "2026-04-05 09:00:00",
    endTime: "2026-04-05 10:00:00",
    status: "ACTIVE" as const,
    remark: "Keep this note for edit flow only.",
    cancelReason: "",
    devices: [
      { id: 301, deviceId: 301, deviceCode: "TV-301", name: "显示屏", quantity: 1, status: "ENABLED" as const }
    ],
    participants: [
      { id: 201, username: "lisi", displayName: "李四（lisi）" },
      { id: 202, username: "wangwu", displayName: "王五（wangwu）" }
    ],
    role: "ORGANIZER" as const,
    canEdit: true,
    canCancel: true,
    reviewed: false
  }
]

const endedReservations = [
  {
    id: 2,
    reservationNo: "RSV-20260402-002",
    roomId: 102,
    roomCode: "A102",
    roomName: "A102 Room",
    roomLocation: "Floor A1",
    roomCapacity: 8,
    roomDescription: "Review room",
    organizerId: 2,
    organizerName: "Bob",
    title: "Retrospective",
    attendees: 6,
    startTime: "2026-04-02 14:00:00",
    endTime: "2026-04-02 15:00:00",
    status: "ENDED" as const,
    remark: "This remark should not appear in detail.",
    cancelReason: "",
    devices: [],
    role: "PARTICIPANT" as const,
    canEdit: false,
    canCancel: false,
    reviewed: false
  },
  {
    id: 3,
    reservationNo: "RSV-20260329-003",
    roomId: 103,
    roomCode: "A103",
    roomName: "A103 Room",
    roomLocation: "Floor A2",
    roomCapacity: 16,
    roomDescription: "Workshop room",
    organizerId: 3,
    organizerName: "Carol",
    title: "Project Review",
    attendees: 10,
    startTime: "2026-03-29 09:30:00",
    endTime: "2026-03-29 11:00:00",
    status: "ENDED" as const,
    remark: "",
    cancelReason: "",
    devices: [],
    role: "ORGANIZER" as const,
    canEdit: false,
    canCancel: false,
    reviewed: true,
    myReview: {
      rating: 4,
      content: "Good overall.",
      createdAt: "2026-03-29 12:00:00"
    }
  }
]

describe("MyReservations", () => {
  beforeEach(() => {
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "warning").mockImplementation(vi.fn() as any)

    reservationApiMocks.getMyReservationsApi.mockReset()
    reservationApiMocks.getMyEndedReservationsPageApi.mockReset()
    reservationApiMocks.getMyReservationRoomOptionsApi.mockReset()
    reservationApiMocks.getMyReservationDetailApi.mockReset()
    reservationApiMocks.updateMyReservationApi.mockReset()
    reservationApiMocks.cancelMyReservationApi.mockReset()
    reservationApiMocks.submitMyReservationReviewApi.mockReset()
    roomApiMocks.getRoomListApi.mockReset()
    routeState.query = {}

    reservationApiMocks.getMyReservationsApi.mockResolvedValue({
      data: activeReservations
    })

    reservationApiMocks.getMyEndedReservationsPageApi.mockResolvedValue({
      data: {
        list: endedReservations,
        total: 8,
        pageNum: 1,
        pageSize: 8
      }
    })

    reservationApiMocks.getMyReservationRoomOptionsApi.mockResolvedValue({
      data: [
        {
          id: 101,
          roomCode: "A101",
          name: "A101 Room",
          location: "Floor A1",
          capacity: 12,
          status: "AVAILABLE",
          description: "Focus room"
        }
      ]
    })

    roomApiMocks.getRoomListApi.mockResolvedValue({
      data: {
        list: [
          {
            id: 101,
            roomCode: "A101",
            name: "A101 Room",
            location: "Floor A1",
            capacity: 12,
            status: "AVAILABLE",
            description: "Focus room",
            devices: [
              { id: 301, deviceCode: "TV-301", name: "显示屏", quantity: 2, total: 2, status: "ENABLED" },
              { id: 302, deviceCode: "WB-302", name: "白板", quantity: 1, total: 1, status: "ENABLED" }
            ]
          }
        ],
        total: 1,
        stats: {
          totalCount: 1,
          availableCount: 1,
          maintenanceCount: 0
        }
      }
    })

    reservationApiMocks.getMyReservationDetailApi.mockImplementation(async (id: number) => ({
      data: activeReservations.find(item => item.id === id) ?? activeReservations[0]
    }))

    reservationApiMocks.updateMyReservationApi.mockResolvedValue({
      data: activeReservations[0]
    })

    reservationApiMocks.submitMyReservationReviewApi.mockResolvedValue({
      data: {
        reviewed: true,
        myReview: {
          rating: 5,
          content: "Very smooth meeting.",
          createdAt: "2026-04-15 16:30:00"
        }
      }
    })
  })

  it("页面初始化时会加载未来预约和最近五场历史摘要", async () => {
    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any

    expect(reservationApiMocks.getMyReservationsApi).toHaveBeenCalledTimes(1)
    expect(reservationApiMocks.getMyEndedReservationsPageApi).toHaveBeenCalledTimes(1)
    expect(reservationApiMocks.getMyEndedReservationsPageApi).toHaveBeenLastCalledWith(expect.objectContaining({
      scope: "all",
      pageNum: 1,
      pageSize: 5
    }))
    expect(roomApiMocks.getRoomListApi).toHaveBeenCalledTimes(1)
    expect(vm.reservationList).toHaveLength(1)
    expect(vm.recentSummaries.map((item: any) => item.id)).toEqual([2, 3])

    vm.openDetail(vm.recentSummaries[0])
    await flushPromises()

    expect(vm.selectedReservation.id).toBe(2)
    expect(vm.selectedReservation.status).toBe("ENDED")
  })

  it("切到已结束模式时走已结束分页接口并支持提交评价", async () => {
    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.selectedStatus = "ENDED"
    await flushPromises()

    expect(vm.isEndedMode).toBe(true)
    expect(vm.showSignalRail).toBe(false)
    expect(vm.endedReservations).toHaveLength(2)
    expect(vm.endedTotal).toBe(8)
    expect(reservationApiMocks.getMyEndedReservationsPageApi).toHaveBeenLastCalledWith(expect.objectContaining({
      scope: "all",
      pageNum: 1,
      pageSize: 8
    }))
    expect(reservationApiMocks.getMyReservationsApi).toHaveBeenCalledTimes(1)
    expect(reservationApiMocks.getMyEndedReservationsPageApi).toHaveBeenCalledTimes(2)

    vm.openDetail(vm.endedReservations[0])
    await flushPromises()

    expect(vm.selectedReservation.status).toBe("ENDED")
    expect(vm.canReviewSelectedReservation).toBe(true)
    expect(wrapper.html()).not.toContain("预约备注")

    vm.openReviewDialog()
    vm.reviewForm.rating = 5
    vm.reviewForm.content = "Very smooth meeting."
    await vm.submitReview()
    await flushPromises()

    expect(reservationApiMocks.submitMyReservationReviewApi).toHaveBeenCalledWith(2, {
      rating: 5,
      content: "Very smooth meeting."
    })
    expect(vm.selectedReservation.reviewed).toBe(true)
    expect(vm.selectedReservation.myReview).toMatchObject({
      rating: 5,
      content: "Very smooth meeting."
    })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it("预约详情展示具体参会人姓名", async () => {
    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openDetail(vm.reservationList[0])
    await flushPromises()

    expect(vm.formatParticipantNames(vm.selectedReservation)).toBe("李四（lisi）、王五（wangwu）")
  })

  it("从通知进入时按预约 ID 打开具体详情", async () => {
    routeState.query = {
      reservationId: "1",
      status: "ACTIVE"
    }

    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.detailDialogVisible).toBe(true)
    expect(vm.selectedReservation.id).toBe(1)
  })

  it("修改会议只编辑参会人和设备，不展示可手填参会人数", async () => {
    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openDetail(vm.reservationList[0])
    vm.openEditDialog()
    await flushPromises()

    expect(wrapper.html()).not.toContain("参会人数</label>")

    vm.editForm.participantUserIds = [201]
    vm.editForm.deviceRequirements = [{ deviceId: 302, quantity: 1 }]
    await vm.submitEdit()
    await flushPromises()

    expect(reservationApiMocks.updateMyReservationApi).toHaveBeenCalledWith(1, expect.objectContaining({
      attendees: 2,
      participantUserIds: [201],
      deviceRequirements: [{ deviceId: 302, quantity: 1 }]
    }))
  })

  it("从已结束切回其他状态时恢复未来预约接口并保留历史摘要", async () => {
    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.selectedStatus = "ENDED"
    await flushPromises()

    vm.selectedStatus = "ACTIVE"
    await flushPromises()

    expect(vm.isEndedMode).toBe(false)
    expect(vm.showSignalRail).toBe(true)
    expect(vm.recentSummaries.map((item: any) => item.id)).toEqual([2, 3])
    expect(reservationApiMocks.getMyReservationsApi).toHaveBeenLastCalledWith(expect.objectContaining({
      scope: "all",
      status: "ACTIVE"
    }))
    expect(reservationApiMocks.getMyReservationsApi).toHaveBeenLastCalledWith(expect.not.objectContaining({
      pageNum: expect.anything(),
      pageSize: expect.anything()
    }))
    expect(reservationApiMocks.getMyEndedReservationsPageApi).toHaveBeenCalledTimes(2)
  })

  it("only starts the ended-page animation after async page data arrives", async () => {
    reservationApiMocks.getMyReservationsApi.mockReset()
    reservationApiMocks.getMyEndedReservationsPageApi.mockReset()

    let resolveNextEndedPage: ((value: unknown) => void) | null = null

    reservationApiMocks.getMyReservationsApi.mockResolvedValue({
      data: activeReservations
    })
    reservationApiMocks.getMyEndedReservationsPageApi
      .mockResolvedValueOnce({
        data: {
          list: endedReservations,
          total: 2,
          pageNum: 1,
          pageSize: 5
        }
      })
      .mockResolvedValueOnce({
        data: {
          list: endedReservations,
          total: 16,
          pageNum: 1,
          pageSize: 8
        }
      })
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveNextEndedPage = resolve
      }))

    const wrapper = shallowMount(MyReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.selectedStatus = "ENDED"
    await flushPromises()

    expect(vm.endedPageTransitionKey).toBe(0)

    vm.endedPageNum = 2
    await wrapper.vm.$nextTick()

    expect(vm.endedPageTransitionKey).toBe(0)

    ;(resolveNextEndedPage as unknown as (value: unknown) => void)({
      data: {
        list: [
          {
            ...endedReservations[0],
            id: 8,
            reservationNo: "RSV-20260320-008",
            title: "Quarterly Review"
          }
        ],
        total: 16,
        pageNum: 2,
        pageSize: 8
      }
    })

    await flushPromises()

    expect(vm.reservationList[0].id).toBe(8)
    expect(vm.endedPageTransitionKey).toBe(1)
  })
})
