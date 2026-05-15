import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const projectRoot = resolve(__dirname, "..", "..")

function readSource(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), "utf-8")
}

describe("Route pages consistency", () => {
  it("所有实际路由页都接入统一顶栏标题类", () => {
    const routePages = [
      "src/pages/dashboard/index.vue",
      "src/pages/rooms/index.vue",
      "src/pages/calendar/index.vue",
      "src/pages/reservations/index.vue",
      "src/pages/admin/reservations/index.vue",
      "src/pages/admin/rooms/index.vue",
      "src/pages/admin/devices/index.vue",
      "src/pages/admin/device-stats/index.vue",
      "src/pages/admin/stats/index.vue"
    ]

    routePages.forEach((filePath) => {
      const source = readSource(filePath)
      expect(source).toContain("page-hero-title")
    })
  })

  it("所有业务分页都提供页大小选择", () => {
    const pagedPages = [
      "src/pages/rooms/index.vue",
      "src/pages/admin/reservations/index.vue",
      "src/pages/admin/rooms/index.vue",
      "src/pages/admin/devices/index.vue",
      "src/pages/admin/stats/index.vue"
    ]

    pagedPages.forEach((filePath) => {
      const source = readSource(filePath)
      expect(source).toContain(":page-sizes=")
      expect(source).toMatch(/@size-change=|v-model:page-size=/)
    })
  })

  it("除概览外的顶栏页面都接入固定高度顶栏类", () => {
    const topbarPages = [
      "src/pages/rooms/index.vue",
      "src/pages/calendar/index.vue",
      "src/pages/reservations/index.vue",
      "src/pages/admin/reservations/index.vue",
      "src/pages/admin/rooms/index.vue",
      "src/pages/admin/devices/index.vue",
      "src/pages/admin/device-stats/index.vue",
      "src/pages/admin/stats/index.vue"
    ]

    topbarPages.forEach((filePath) => {
      const source = readSource(filePath)
      expect(source).toContain("page-topbar-fixed")
    })
  })

  it("assistant page uses a full-screen workbench without topbar", () => {
    const source = readSource("src/pages/assistant/index.vue")

    expect(source).not.toContain('class="hero-panel page-topbar-fixed"')
    expect(source).toContain('class="assistant-topbar"')
    expect(source).toContain("height: 100%")
    expect(source).toContain("overflow: hidden")
    expect(source).toContain("grid-template-columns: minmax(0, 1fr)")
    expect(source).toContain("grid-template-rows: minmax(0, 1fr) auto")
    expect(source).not.toContain('class="support-stage"')
    expect(source).toContain('class="prompt-wall"')
    expect(source).toContain('class="send-icon-button"')
  })

  it("rooms page keeps hero topbar and truncated device tooltip", () => {
    const source = readSource("src/pages/rooms/index.vue")

    expect(source).toContain('class="hero-panel page-topbar-fixed"')
    expect(source).toContain("el-tooltip")
    expect(source).toContain("device-overflow-trigger")
    expect(source).toContain("device-popover-panel")
    expect(source).toContain("device-popover-item")
    expect(source).toContain("repeat(3, minmax(0, 1fr))")
    expect(source).toContain("hero-metrics")
    expect(source).toContain("panel-filters")
    expect(source).toContain('class="metric-item"')
  })

  it("route pages use bilingual panel titles outside dialogs", () => {
    const sourceFiles = [
      "src/pages/dashboard/index.vue",
      "src/pages/rooms/index.vue",
      "src/pages/calendar/index.vue",
      "src/pages/reservations/index.vue"
    ]

    sourceFiles.forEach((filePath) => {
      const source = readSource(filePath)
      expect(source).toContain("panel-kicker")
    })
  })

  it("calendar page keeps metrics inside hero instead of standalone strip", () => {
    const source = readSource("src/pages/calendar/index.vue")

    expect(source).toContain("hero-metrics")
    expect(source).not.toContain("hero-focus soft")
  })

  it("calendar page keeps room and status filters while using rich event cards", () => {
    const source = readSource("src/pages/calendar/index.vue")

    expect(source).toContain("eventMaxStack: 3")
    expect(source).not.toContain("slotEventOverlap: false")
    expect(source).toContain("eventContent: renderCalendarEventContent")
    expect(source).toContain("calendar-event-rich")
    expect(source).not.toContain("calendar-event-shell")
    expect(source).not.toContain("calendar-more-link")
    expect(source).toContain('const selectedRoomId = ref<number | undefined>(undefined)')
    expect(source).toContain("selectedStatus")
    expect(source).not.toMatch(/label=".*全部会议室/)
    expect(source).toContain("const sortedRoomOptions = computed(() =>")
    expect(source).toContain("localeCompare")
    expect(source).toContain("ArrowLeft")
    expect(source).toContain("ArrowRight")
    expect(source).toContain("toolbar-dot")
    expect(source).not.toContain("insight-icon")
  })

  it("admin reservation review route is registered under admin menu", () => {
    const source = readSource("src/router/index.ts")

    expect(source).toContain('path: "reservations"')
    expect(source).toContain('name: "AdminReservations"')
    expect(source).toContain('title: "预约审核"')
    expect(source).toContain('component: () => import("@/pages/admin/reservations/index.vue")')
  })

  it("admin notification publish route is not registered under admin menu", () => {
    const source = readSource("src/router/index.ts")

    expect(source).not.toContain('path: "notifications"')
    expect(source).not.toContain('name: "AdminNotifications"')
    expect(source).not.toContain('title: "通知发布"')
    expect(source).not.toContain('component: () => import("@/pages/admin/notifications/index.vue")')
  })
})
