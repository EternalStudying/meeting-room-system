import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { flushPromises, mount } from "@vue/test-utils"
import ElementPlus from "element-plus"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import ReservationCreateDialog from "@/components/ReservationCreateDialog.vue"
import type { RoomData } from "@/common/apis/rooms/type"

const reservationApiMocks = vi.hoisted(() => ({
  createReservationApi: vi.fn(),
  getReservationRecommendationsApi: vi.fn(),
  previewAdminEmergencyReservationApi: vi.fn(),
  confirmAdminEmergencyReservationApi: vi.fn()
}))

const userApiMocks = vi.hoisted(() => ({
  searchUsersApi: vi.fn()
}))

vi.mock("@/common/apis/reservations", () => ({
  createReservationApi: reservationApiMocks.createReservationApi,
  getReservationRecommendationsApi: reservationApiMocks.getReservationRecommendationsApi,
  previewAdminEmergencyReservationApi: reservationApiMocks.previewAdminEmergencyReservationApi,
  confirmAdminEmergencyReservationApi: reservationApiMocks.confirmAdminEmergencyReservationApi
}))

vi.mock("@/common/apis/users", () => ({
  searchUsersApi: userApiMocks.searchUsersApi
}))

const buildRoom = (overrides: Partial<RoomData> = {}): RoomData => ({
  id: 101,
  roomCode: "R-A101",
  name: "A101 多媒体会议室",
  location: "A楼-1层",
  capacity: 12,
  status: "AVAILABLE",
  description: "适合部门周会",
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

function buildRect(height: number): DOMRect {
  return {
    x: 0,
    y: 0,
    width: 520,
    height,
    top: 0,
    left: 0,
    right: 520,
    bottom: height,
    toJSON: () => ({})
  } as DOMRect
}

async function mountDialog(preset: Record<string, unknown> = {}, rooms: RoomData[] = [buildRoom()], emergency = false) {
  const wrapper = mount(ReservationCreateDialog, {
    attachTo: document.body,
    props: {
      modelValue: true,
      room: rooms[0],
      rooms,
      preset,
      emergency
    },
    global: {
      plugins: [ElementPlus],
      stubs: {
        transition: false
      }
    }
  })

  await flushPromises()
  return wrapper
}

describe("ReservationCreateDialog", () => {
  it("预约信息和智能推荐面板保持等高拉伸", () => {
    const source = readFileSync(resolve(__dirname, "../../src/components/ReservationCreateDialog.vue"), "utf-8")
    const getRuleBody = (selector: string) => {
      const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
      return source.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\n\\}`))?.[1] ?? ""
    }

    expect(getRuleBody(".reservation-workbench")).toContain("align-items: start")
    expect(getRuleBody(".reservation-workbench.is-emergency")).toContain("align-items: stretch")
    expect(getRuleBody(".workbench-panel")).toContain("display: flex")
    expect(getRuleBody(".panel-shell")).toContain("flex: 1")
    expect(getRuleBody(".recommendation-list")).toContain("flex: 1")
    expect(getRuleBody(".recommendation-list")).toContain("min-height: 0")
    expect(getRuleBody(".recommendation-empty")).toContain("flex: 1")
  })

  it("普通预约用左侧表单高度限制智能推荐面板", async () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      const element = this
      if (element.classList.contains("panel-shell") && element.closest(".workbench-panel--form")) {
        return buildRect(640)
      }
      return buildRect(0)
    })

    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30"
    })
    await flushPromises()

    const workbench = wrapper.find(".reservation-workbench")
    const recommendationShell = document.body.querySelector(".reservation-workbench-dialog .recommendation-shell") as HTMLElement

    expect(workbench.classes()).toContain("is-standard")
    expect(workbench.classes()).not.toContain("is-emergency")
    expect(recommendationShell.style.height).toBe("640px")

    rectSpy.mockRestore()
    wrapper.unmount()
  })

  beforeEach(() => {
    reservationApiMocks.createReservationApi.mockReset()
    reservationApiMocks.getReservationRecommendationsApi.mockReset()
    reservationApiMocks.previewAdminEmergencyReservationApi.mockReset()
    reservationApiMocks.confirmAdminEmergencyReservationApi.mockReset()
    userApiMocks.searchUsersApi.mockReset()

    reservationApiMocks.createReservationApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: {
        id: 9001,
        reservationNo: "RSV-20260418-001"
      }
    })

    reservationApiMocks.getReservationRecommendationsApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: {
        recommendations: [
          {
            roomId: 102,
            roomCode: "R-A102",
            roomName: "A102 讨论间",
            location: "A楼-1层",
            capacity: 8,
            score: 100,
            wasteRate: 0,
            requiredDeviceTypeCount: 1,
            matchedDeviceTypeCount: 1,
            deviceFullyMatched: true,
            isPreferred: true,
            tags: ["容量匹配好", "设备齐全"]
          }
        ]
      }
    })

    reservationApiMocks.previewAdminEmergencyReservationApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: {
        canExecute: true,
        message: "检测到 1 条冲突预约，拟调配 1 条、取消 0 条。",
        emergencySummary: {
          roomId: 101,
          roomCode: "R-A101",
          roomName: "A101 多媒体会议室",
          title: "[紧急] 核心客户事故复盘",
          attendees: 1,
          startTime: "2026-05-15 15:00:00",
          endTime: "2026-05-15 16:00:00",
          emergencyReason: "客户生产事故"
        },
        conflicts: [
          {
            reservationId: 2001,
            reservationNo: "RSV2001",
            roomId: 101,
            roomCode: "R-A101",
            roomName: "A101 多媒体会议室",
            organizerId: 7,
            organizerName: "张三",
            title: "客户例会",
            attendees: 6,
            startTime: "2026-05-15 15:00:00",
            endTime: "2026-05-15 16:00:00",
            status: "ACTIVE"
          }
        ],
        actions: [
          {
            reservationId: 2001,
            reservationTitle: "客户例会",
            actionType: "MOVE_ROOM",
            sourceRoomId: 101,
            sourceRoomName: "A101 多媒体会议室",
            targetRoomId: 102,
            targetRoomName: "A102 讨论间",
            reason: "调配到同时间可用会议室"
          }
        ],
        notifications: [
          { userId: 7, displayName: "张三", reservationId: 2001, title: "客户例会", reason: "调配到同时间可用会议室" }
        ]
      }
    })
    reservationApiMocks.confirmAdminEmergencyReservationApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: {
        reservationId: 9001,
        reservationNo: "RSV9001",
        status: "ACTIVE",
        message: "紧急会议已创建并完成抢占调配。"
      }
    })

    userApiMocks.searchUsersApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: [
        { id: 201, username: "zhangsan", displayName: "张三（zhangsan）" },
        { id: 202, username: "lisi", displayName: "李四（lisi）" }
      ]
    })
  })

  afterEach(() => {
    document.body.innerHTML = ""
  })

  it("不再展示参会人数区块", async () => {
    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:00",
      participantUserIds: [201, 202]
    })

    const text = document.body.textContent ?? ""

    expect(text).not.toContain("参会人数")
    expect(text).not.toContain("自动计算")

    wrapper.unmount()
  })

  it("智能推荐按参会人加发起人计算人数", async () => {
    const rooms = [buildRoom(), buildRoom({ id: 102, roomCode: "R-A102", name: "A102 讨论间", capacity: 8, devices: [] })]
    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30",
      attendees: 99,
      participantUserIds: [201, 202],
      deviceRequirements: [{ deviceId: 1, quantity: 2 }]
    }, rooms)

    expect(reservationApiMocks.getReservationRecommendationsApi).toHaveBeenCalledWith({
      title: "项目周会",
      attendees: 3,
      startTime: "2026-04-20 09:00:00",
      endTime: "2026-04-20 10:30:00",
      preferredRoomId: 101,
      deviceRequirements: [{ deviceId: 1, quantity: 2 }]
    })

    wrapper.unmount()
  })

  it("提交时无人参会也按发起人 1 人计算", async () => {
    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30",
      attendees: 99
    })

    const vm = wrapper.vm as any
    await vm.submitReservation()

    expect(reservationApiMocks.createReservationApi).toHaveBeenCalledWith({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30",
      attendees: 1,
      remark: undefined
    })

    wrapper.unmount()
  })

  it("选择参会人后提交 participantUserIds 并自动计算人数", async () => {
    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30"
    })

    const vm = wrapper.vm as any

    await vm.searchParticipantUsers("zhang")
    await flushPromises()

    vm.form.participantUserIds = [201, 202]
    await vm.submitReservation()

    expect(userApiMocks.searchUsersApi).toHaveBeenCalledWith({
      keyword: "zhang",
      limit: 10
    })
    expect(reservationApiMocks.createReservationApi).toHaveBeenCalledWith({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30",
      attendees: 3,
      participantUserIds: [201, 202],
      remark: undefined
    })

    wrapper.unmount()
  })

  it("已选参会人只在输入框内显示，不再渲染下方重复标签", async () => {
    const wrapper = await mountDialog({
      roomId: 101,
      title: "项目周会",
      meetingDate: "2026-04-20",
      startClock: "09:00",
      endClock: "10:30"
    })

    const vm = wrapper.vm as any

    await vm.searchParticipantUsers("zhang")
    await flushPromises()

    vm.form.participantUserIds = [201]
    await flushPromises()

    expect(wrapper.find(".participant-builder__selected").exists()).toBe(false)
    expect(wrapper.find(".participant-builder__empty").exists()).toBe(false)

    wrapper.unmount()
  })

  it("紧急会议先预览冲突调配，再由管理员确认执行", async () => {
    const wrapper = await mountDialog({
      roomId: 101,
      title: "核心客户事故复盘",
      meetingDate: "2026-05-15",
      startClock: "15:00",
      endClock: "16:00"
    }, [buildRoom(), buildRoom({ id: 102, roomCode: "R-A102", name: "A102 讨论间", capacity: 8 })], true)

    const vm = wrapper.vm as any
    vm.emergencyReason = "客户生产事故"
    vm.allowPreempt = true

    await vm.submitReservation()
    await flushPromises()

    expect(reservationApiMocks.createReservationApi).not.toHaveBeenCalled()
    expect(reservationApiMocks.previewAdminEmergencyReservationApi).toHaveBeenCalledWith(expect.objectContaining({
      roomId: 101,
      title: "核心客户事故复盘",
      meetingDate: "2026-05-15",
      startClock: "15:00",
      endClock: "16:00",
      attendees: 1,
      allowPreempt: true,
      emergencyReason: "客户生产事故"
    }))
    expect(vm.emergencyPreviewDialogVisible).toBe(true)
    expect(document.body.textContent).toContain("抢占调配确认")
    expect(document.body.textContent).toContain("客户例会")
    expect(document.body.textContent).toContain("A102 讨论间")

    await vm.confirmEmergencyReservation()
    await flushPromises()

    expect(reservationApiMocks.confirmAdminEmergencyReservationApi).toHaveBeenCalledWith(expect.objectContaining({
      roomId: 101,
      allowPreempt: true,
      emergencyReason: "客户生产事故"
    }))
    expect(wrapper.emitted("submitted")).toHaveLength(1)

    wrapper.unmount()
  })
})
