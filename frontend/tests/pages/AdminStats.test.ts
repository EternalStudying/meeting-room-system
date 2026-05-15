import { flushPromises, shallowMount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"

const statsApiMocks = vi.hoisted(() => ({
  getAdminStatsApi: vi.fn().mockResolvedValue({
    data: {
      recentDays: 30,
      kpis: {
        totalReservations: { key: "totalReservations", label: "预约总数", value: 8, unit: "场", detail: "", tone: "steel" },
        activeReservations: { key: "activeReservations", label: "进行中预约", value: 4, unit: "场", detail: "", tone: "mint" },
        cancelledReservations: { key: "cancelledReservations", label: "已取消预约", value: 1, unit: "场", detail: "", tone: "rose" },
        roomCoverageRate: { key: "roomCoverageRate", label: "会议室配置覆盖率", value: 83, unit: "%", detail: "", tone: "amber" },
        deviceCoverageRate: { key: "deviceCoverageRate", label: "设备绑定覆盖率", value: 100, unit: "%", detail: "", tone: "steel" },
        maintenanceRooms: { key: "maintenanceRooms", label: "维护中会议室", value: 1, unit: "间", detail: "", tone: "rose" }
      },
      trend: [{ date: "2026-04-06", label: "04-06", reservationCount: 2 }],
      heatmap: [{ weekday: "周一", weekdayIndex: 0, hour: "08:00", hourIndex: 0, reservationCount: 1 }],
      roomUsageRanks: [
        {
          roomId: 101,
          roomCode: "A101",
          roomName: "A101 多媒体会议室",
          location: "A楼-1层",
          reservationCount: 3,
          activeCount: 2,
          cancelledCount: 0
        }
      ],
      deviceUsageRanks: [
        {
          deviceId: 2,
          deviceCode: "MIC-02",
          deviceName: "无线麦克风",
          status: "ENABLED",
          usageQuantity: 5,
          reservationCount: 3
        }
      ],
      coverage: {
        configuredRoomCount: 5,
        unconfiguredRoomCount: 1
      },
      alerts: [
        {
          id: "room-unbound-202",
          type: "room_unbound",
          targetName: "C202 小会议室",
          summary: "当前没有绑定任何设备",
          level: "warning"
        }
      ],
      reservations: [
        {
          id: 1,
          reservationNo: "RSV-20260406-001",
          title: "项目周会",
          roomName: "A101 多媒体会议室",
          organizerName: "张三",
          startTime: "2026-04-06 09:00:00",
          endTime: "2026-04-06 10:00:00",
          status: "ACTIVE",
          deviceSummary: "投影仪 x1"
        }
      ],
      staticSummary: {
        totalRooms: 6,
        availableRooms: 5,
        maintenanceRooms: 1,
        totalDevices: 5,
        enabledDevices: 4,
        disabledDevices: 1
      }
    }
  })
}))

const chartInstances: Array<{
  setOption: ReturnType<typeof vi.fn>
  on: ReturnType<typeof vi.fn>
  off: ReturnType<typeof vi.fn>
  resize: ReturnType<typeof vi.fn>
  dispose: ReturnType<typeof vi.fn>
}> = []

vi.mock("@/common/apis/stats", () => ({
  getAdminStatsApi: statsApiMocks.getAdminStatsApi
}))

vi.mock("echarts", () => ({
  graphic: {
    LinearGradient: function (_x0: number, _y0: number, _x1: number, _y1: number, colorStops: unknown) {
      return { colorStops }
    }
  },
  init: () => {
    const instance = {
      setOption: vi.fn(),
      on: vi.fn(),
      off: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn()
    }
    chartInstances.push(instance)
    return instance
  }
}))

describe("AdminStats", () => {
  it("挂载时按默认 30 天窗口请求后端统计接口", async () => {
    chartInstances.length = 0
    const AdminStats = (await import("@/pages/admin/stats/index.vue")).default
    const wrapper = shallowMount(AdminStats)

    await flushPromises()

    expect(statsApiMocks.getAdminStatsApi).toHaveBeenCalledWith(30)
    expect(wrapper.text()).toContain("统计分析")
    expect(wrapper.text()).toContain("预约趋势")
    expect(wrapper.text()).toContain("设备使用排行")
    expect(wrapper.text()).toContain("统计窗口")
    expect(wrapper.text()).toContain("近 1 天")
    expect(wrapper.text()).toContain("近 7 天")
    expect(wrapper.text()).toContain("近 30 天")
  }, 20000)

  it("接口失败时展示空统计数据", async () => {
    chartInstances.length = 0
    statsApiMocks.getAdminStatsApi.mockRejectedValueOnce(new Error("network"))

    const AdminStats = (await import("@/pages/admin/stats/index.vue")).default
    const wrapper = shallowMount(AdminStats)

    await flushPromises()

    expect(statsApiMocks.getAdminStatsApi).toHaveBeenCalledWith(30)
    expect(wrapper.text()).toContain("统计分析")
    expect(wrapper.text()).toContain("预约趋势")
  }, 10000)

  it("排行图使用详细 tooltip，设备排行显示设备名称", async () => {
    chartInstances.length = 0
    const AdminStats = (await import("@/pages/admin/stats/index.vue")).default
    shallowMount(AdminStats)
    await flushPromises()

    const roomRankOption = chartInstances[2]?.setOption.mock.calls.at(-1)?.[0]
    const deviceRankOption = chartInstances[3]?.setOption.mock.calls.at(-1)?.[0]
    const roomTooltip = roomRankOption?.tooltip?.formatter?.([{
      axisValueLabel: "A101",
      data: {
        roomName: "A101 多媒体会议室",
        location: "A楼-1层",
        reservationCount: 3,
        activeCount: 2,
        cancelledCount: 0
      }
    }])
    const deviceTooltip = deviceRankOption?.tooltip?.formatter?.([{
      axisValueLabel: "无线麦克风",
      data: {
        deviceName: "无线麦克风",
        deviceCode: "MIC-02",
        usageQuantity: 5,
        reservationCount: 3,
        status: "ENABLED"
      }
    }])

    expect(deviceRankOption?.yAxis?.data?.[0]).toBe("无线麦克风")
    expect(roomTooltip).toContain("A101 多媒体会议室")
    expect(roomTooltip).toContain("A楼-1层")
    expect(deviceTooltip).toContain("MIC-02")
    expect(deviceTooltip).toContain("调用数量")
  }, 10000)

  it("资源覆盖概览保留中心文案", async () => {
    chartInstances.length = 0
    const AdminStats = (await import("@/pages/admin/stats/index.vue")).default
    shallowMount(AdminStats)
    await flushPromises()

    const coverageOption = chartInstances[4]?.setOption.mock.calls.at(-1)?.[0]
    const graphicText = coverageOption?.graphic?.elements?.map((item: { style?: { text?: string } }) => item.style?.text)

    expect(graphicText).toContain("5 / 6")
    expect(graphicText).toContain("已配置")
  }, 10000)

  it("wraps both detail tables with pagination transition", async () => {
    chartInstances.length = 0
    const AdminStats = (await import("@/pages/admin/stats/index.vue")).default
    const wrapper = shallowMount(AdminStats)

    await flushPromises()

    const html = wrapper.html()

    expect(html.match(/name="pagination-switch"/g)).toHaveLength(2)
  }, 10000)
})
