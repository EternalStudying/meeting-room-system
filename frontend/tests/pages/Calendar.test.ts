import { flushPromises, shallowMount } from "@vue/test-utils"
import dayjs from "dayjs"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import CalendarPage from "@/pages/calendar/index.vue"

const reservationApiMocks = vi.hoisted(() => ({
  getReservationCalendarApi: vi.fn(),
  getMyReservationsApi: vi.fn(),
  updateMyReservationApi: vi.fn(),
  createReservationApi: vi.fn(),
  getReservationRecommendationsApi: vi.fn(),
  previewAdminEmergencyReservationApi: vi.fn(),
  confirmAdminEmergencyReservationApi: vi.fn()
}))

const roomApiMocks = vi.hoisted(() => ({
  getRoomListApi: vi.fn()
}))

vi.mock("@/common/apis/reservations", () => ({
  getReservationCalendarApi: reservationApiMocks.getReservationCalendarApi,
  getMyReservationsApi: reservationApiMocks.getMyReservationsApi,
  updateMyReservationApi: reservationApiMocks.updateMyReservationApi,
  createReservationApi: reservationApiMocks.createReservationApi,
  getReservationRecommendationsApi: reservationApiMocks.getReservationRecommendationsApi,
  previewAdminEmergencyReservationApi: reservationApiMocks.previewAdminEmergencyReservationApi,
  confirmAdminEmergencyReservationApi: reservationApiMocks.confirmAdminEmergencyReservationApi
}))

vi.mock("@/common/apis/rooms", () => ({
  getRoomListApi: roomApiMocks.getRoomListApi
}))

describe("CalendarPage", () => {
  beforeEach(() => {
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    reservationApiMocks.getReservationCalendarApi.mockReset()
    reservationApiMocks.getMyReservationsApi.mockReset()
    reservationApiMocks.updateMyReservationApi.mockReset()
    reservationApiMocks.createReservationApi.mockReset()
    reservationApiMocks.getReservationRecommendationsApi.mockReset()
    reservationApiMocks.previewAdminEmergencyReservationApi.mockReset()
    reservationApiMocks.confirmAdminEmergencyReservationApi.mockReset()
    roomApiMocks.getRoomListApi.mockReset()

    reservationApiMocks.getReservationCalendarApi.mockResolvedValue({
      data: []
    })
    reservationApiMocks.getMyReservationsApi.mockResolvedValue({
      data: []
    })
    reservationApiMocks.updateMyReservationApi.mockResolvedValue({
      data: {
        id: 5001
      }
    })

    roomApiMocks.getRoomListApi.mockResolvedValue({
      data: {
        list: [
          {
            id: 101,
            roomCode: "A101",
            name: "Room A101",
            location: "Floor 1",
            capacity: 12,
            status: "AVAILABLE",
            description: "Project sync room",
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

  it("supports selecting a time range to create a reservation", async () => {
    const wrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })

    await flushPromises()

    const vm = wrapper.vm as any
    const unselect = vi.fn()

    await vm.calendarOptions.select({
      start: new Date("2026-04-14T09:00:00"),
      end: new Date("2026-04-14T10:30:00"),
      view: {
        type: "timeGridWeek",
        calendar: {
          unselect
        }
      }
    })

    const reservationDialog = wrapper.findComponent({ name: "ReservationCreateDialog" })

    expect(vm.calendarOptions.selectable).toBe(true)
    expect(typeof vm.calendarOptions.select).toBe("function")
    expect(unselect).toHaveBeenCalled()
    expect(reservationDialog.exists()).toBe(true)
    expect(reservationDialog.props("modelValue")).toBe(true)
    expect(reservationDialog.props("preset")).toMatchObject({
      roomId: 101,
      meetingDate: "2026-04-14",
      startClock: "09:00",
      endClock: "10:30"
    })
  })

  it("only allows dragging reservations after enabling my reservations mode", async () => {
    reservationApiMocks.getMyReservationsApi.mockResolvedValue({
      data: [
        {
          id: 5001,
          reservationNo: "RSV-5001",
          roomId: 101,
          roomCode: "A101",
          roomName: "Room A101",
          roomLocation: "Floor 1",
          roomCapacity: 12,
          organizerId: 7,
          organizerName: "Me",
          title: "Weekly Sync",
          attendees: 6,
          startTime: "2026-04-14 09:00:00",
          endTime: "2026-04-14 10:00:00",
          status: "ACTIVE",
          devices: [],
          role: "ORGANIZER",
          canEdit: true,
          canCancel: true
        }
      ]
    })

    const wrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })

    await flushPromises()

    const vm = wrapper.vm as any
    vm.visibleRange = {
      startDate: "2026-04-14 00:00:00",
      endDate: "2026-04-21 00:00:00"
    }
    vm.viewMyReservationsOnly = true
    await flushPromises()

    expect(reservationApiMocks.getMyReservationsApi).toHaveBeenCalledWith({
      startDate: "2026-04-14 00:00:00",
      endDate: "2026-04-21 00:00:00",
      scope: "organizer",
      status: undefined
    })
    expect(vm.calendarOptions.editable).toBe(true)
    expect(typeof vm.calendarOptions.eventDrop).toBe("function")
    expect(typeof vm.calendarOptions.eventContent).toBe("function")

    const eventContent = vm.calendarOptions.eventContent({
      timeText: "09:00 - 10:00",
      event: {
        extendedProps: {
          reservation: vm.reservationList[0]
        }
      }
    })

    expect(eventContent.html).toContain("09:00 - 10:00")
    expect(eventContent.html).toContain("Weekly Sync")
    expect(eventContent.html).toContain("Room A101")

    const revert = vi.fn()
    await vm.calendarOptions.eventDrop({
      event: {
        start: new Date("2026-04-14T11:00:00"),
        end: new Date("2026-04-14T12:30:00"),
        extendedProps: {
          reservation: vm.reservationList[0]
        }
      },
      revert
    })
    await flushPromises()

    expect(reservationApiMocks.updateMyReservationApi).toHaveBeenCalledWith(5001, expect.objectContaining({
      roomId: 101,
      title: "Weekly Sync",
      meetingDate: "2026-04-14",
      startClock: "11:00",
      endClock: "12:30",
      attendees: 6
    }))
    expect(ElMessage.success).toHaveBeenCalledWith("预约时间已更新")
    expect(revert).not.toHaveBeenCalled()
  })

  it("日历预约详情展示具体参会人姓名", async () => {
    reservationApiMocks.getReservationCalendarApi.mockResolvedValue({
      data: [
        {
          id: 5002,
          reservationNo: "RSV-5002",
          roomId: 101,
          roomCode: "A101",
          roomName: "Room A101",
          roomLocation: "Floor 1",
          roomCapacity: 12,
          organizerId: 7,
          organizerName: "Me",
          title: "Design Review",
          attendees: 3,
          startTime: "2026-04-15 09:00:00",
          endTime: "2026-04-15 10:00:00",
          status: "ACTIVE",
          devices: [],
          participants: [
            { id: 201, username: "lisi", displayName: "李四（lisi）" },
            { id: 202, username: "wangwu", displayName: "王五（wangwu）" }
          ]
        }
      ]
    })

    const wrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })

    await flushPromises()

    const vm = wrapper.vm as any
    vm.calendarOptions.datesSet({
      start: new Date("2026-04-15T00:00:00"),
      end: new Date("2026-04-16T00:00:00"),
      view: {
        title: "2026-04-15",
        type: "timeGridWeek"
      }
    })
    await flushPromises()

    vm.selectedReservationId = 5002
    vm.detailDialogVisible = true
    await flushPromises()

    expect(vm.formatParticipantNames(vm.selectedReservation)).toBe("李四（lisi）、王五（wangwu）")
  })

  it("时间中心只统计当前时间之后的会议并显示月日时分", async () => {
    const now = dayjs()
    const today = now.format("YYYY-MM-DD")
    const pastReservation = {
      id: 6001,
      reservationNo: "RSV-6001",
      roomId: 101,
      roomName: "Room A101",
      organizerId: 7,
      organizerName: "Me",
      title: "Past Review",
      attendees: 3,
      startTime: now.subtract(2, "hour").format("YYYY-MM-DD HH:mm:ss"),
      endTime: now.subtract(1, "hour").format("YYYY-MM-DD HH:mm:ss"),
      status: "ACTIVE",
      devices: []
    }
    const futureReservation = {
      ...pastReservation,
      id: 6002,
      reservationNo: "RSV-6002",
      title: "Future Review",
      startTime: now.add(1, "hour").format("YYYY-MM-DD HH:mm:ss"),
      endTime: now.add(2, "hour").format("YYYY-MM-DD HH:mm:ss")
    }

    const wrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })

    await flushPromises()

    const vm = wrapper.vm as any
    vm.selectedDate = today
    vm.reservationList = [pastReservation, futureReservation]
    await flushPromises()

    expect(vm.selectedDayReservations.map((item: any) => item.id)).toEqual([6002])
    expect(vm.formatDateTimeRange(futureReservation.startTime, futureReservation.endTime)).toContain(now.add(1, "hour").format("M月D日 HH:mm"))
  })

  it("does not show the emergency reservation entry on calendar for admin or user accounts", async () => {
    const adminWrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })
    await flushPromises()

    expect(adminWrapper.find(".emergency-calendar-button").exists()).toBe(false)
    expect(adminWrapper.findAllComponents({ name: "ReservationCreateDialog" }).some(dialog => dialog.props("emergency") === true)).toBe(false)

    const userWrapper = shallowMount(CalendarPage, {
      global: {
        stubs: {
          FullCalendar: {
            template: "<div class='full-calendar-stub' />"
          }
        }
      }
    })
    await flushPromises()

    expect(userWrapper.find(".emergency-calendar-button").exists()).toBe(false)
    expect(userWrapper.findAllComponents({ name: "ReservationCreateDialog" }).some(dialog => dialog.props("emergency") === true)).toBe(false)
  })
})
