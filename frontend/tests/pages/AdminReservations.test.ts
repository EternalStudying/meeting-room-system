import { flushPromises, shallowMount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AdminReservations from "@/pages/admin/reservations/index.vue"

const reservationApiMocks = vi.hoisted(() => ({
  getAdminReservationListApi: vi.fn(),
  approveAdminReservationApi: vi.fn(),
  rejectAdminReservationApi: vi.fn(),
  markAdminReservationExceptionApi: vi.fn(),
  previewAdminEmergencyReservationApi: vi.fn(),
  confirmAdminEmergencyReservationApi: vi.fn()
}))

const roomApiMocks = vi.hoisted(() => ({
  getRoomListApi: vi.fn()
}))

vi.mock("@/common/apis/reservations", () => ({
  getAdminReservationListApi: reservationApiMocks.getAdminReservationListApi,
  approveAdminReservationApi: reservationApiMocks.approveAdminReservationApi,
  rejectAdminReservationApi: reservationApiMocks.rejectAdminReservationApi,
  markAdminReservationExceptionApi: reservationApiMocks.markAdminReservationExceptionApi,
  previewAdminEmergencyReservationApi: reservationApiMocks.previewAdminEmergencyReservationApi,
  confirmAdminEmergencyReservationApi: reservationApiMocks.confirmAdminEmergencyReservationApi
}))

vi.mock("@/common/apis/rooms", () => ({
  getRoomListApi: roomApiMocks.getRoomListApi
}))

describe("AdminReservations", () => {
  beforeEach(() => {
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "error").mockImplementation(vi.fn() as any)
    reservationApiMocks.getAdminReservationListApi.mockReset()
    reservationApiMocks.approveAdminReservationApi.mockReset()
    reservationApiMocks.rejectAdminReservationApi.mockReset()
    reservationApiMocks.markAdminReservationExceptionApi.mockReset()
    reservationApiMocks.previewAdminEmergencyReservationApi.mockReset()
    reservationApiMocks.confirmAdminEmergencyReservationApi.mockReset()
    roomApiMocks.getRoomListApi.mockReset()

    reservationApiMocks.getAdminReservationListApi.mockResolvedValue({
      data: {
        list: [
          {
            id: 101,
            reservationNo: "RSV-20260512-001",
            roomId: 1,
            roomCode: "A101",
            roomName: "A101 多媒体会议室",
            roomLocation: "A楼-1层",
            organizerId: 10,
            organizerName: "张三",
            title: "项目周会",
            attendees: 3,
            startTime: "2026-05-13 09:00:00",
            endTime: "2026-05-13 10:00:00",
            status: "PENDING",
            remark: "需要投影",
            devices: [],
            participants: []
          }
        ],
        total: 1,
        stats: {
          totalCount: 1,
          pendingCount: 1,
          activeCount: 0,
          rejectedCount: 0,
          exceptionCount: 0
        }
      }
    })
    roomApiMocks.getRoomListApi.mockResolvedValue({
      data: {
        list: [
          {
            id: 1,
            roomCode: "A101",
            name: "A101 多媒体会议室",
            location: "A楼-1层",
            capacity: 12,
            status: "AVAILABLE",
            devices: []
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
  })

  it("renders admin reservation review page in existing admin style", async () => {
    const wrapper = shallowMount(AdminReservations)

    await flushPromises()

    expect(reservationApiMocks.getAdminReservationListApi).toHaveBeenCalledWith({
      currentPage: 1,
      size: 8,
      keyword: undefined,
      status: "PENDING"
    })
    expect(wrapper.text()).toContain("预约审核")
    expect(wrapper.text()).toContain("待审核")
    expect(wrapper.text()).toContain("项目周会")
    expect(wrapper.find(".hero-panel.page-topbar-fixed").exists()).toBe(true)
    expect(wrapper.html()).toContain("page-hero-title")
    expect(wrapper.html()).toContain("name=\"pagination-switch\"")
  })

  it("opens action dialog before approving pending reservations", async () => {
    const wrapper = shallowMount(AdminReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openActionDialog("approve", vm.reservationCollection[0])
    await wrapper.vm.$nextTick()

    expect(vm.actionDialogVisible).toBe(true)
    expect(vm.actionMode).toBe("approve")
    expect(vm.actionTargetReservation.title).toBe("项目周会")
  })

  it("does not report success when an admin action fails", async () => {
    reservationApiMocks.approveAdminReservationApi.mockRejectedValue(new Error("forbidden"))
    const wrapper = shallowMount(AdminReservations)

    await flushPromises()

    const vm = wrapper.vm as any
    vm.openActionDialog("approve", vm.reservationCollection[0])
    await vm.submitAction()
    await flushPromises()

    expect(ElMessage.success).not.toHaveBeenCalledWith("预约已通过")
    expect(ElMessage.error).toHaveBeenCalledWith("操作失败，请稍后重试")
  })

  it("opens emergency reservation dialog from admin review page", async () => {
    const wrapper = shallowMount(AdminReservations)

    await flushPromises()

    expect(wrapper.find(".emergency-admin-button").exists()).toBe(true)

    const vm = wrapper.vm as any
    vm.openEmergencyReservationDialog()
    await wrapper.vm.$nextTick()

    const dialog = wrapper.findComponent({ name: "ReservationCreateDialog" })
    expect(vm.emergencyDialogVisible).toBe(true)
    expect(dialog.exists()).toBe(true)
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("emergency")).toBe(true)
  })
})
