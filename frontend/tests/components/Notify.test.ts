import type { NotificationItem } from "@/common/apis/notifications/type"
import Notify from "@@/components/Notify/index.vue"
import List from "@@/components/Notify/List.vue"
import { flushPromises, mount } from "@vue/test-utils"
import { beforeEach, describe, expect, it, vi } from "vitest"

const notificationApiMocks = vi.hoisted(() => ({
  getNotificationSummaryApi: vi.fn(),
  getNotificationPageApi: vi.fn(),
  readNotificationApi: vi.fn(),
  readAllNotificationsApi: vi.fn(),
  publishAdminNotificationApi: vi.fn()
}))

const routerPush = vi.hoisted(() => vi.fn())
const userStoreMock = vi.hoisted(() => ({
  roles: ["admin"] as string[]
}))

vi.mock("@/common/apis/notifications", () => ({
  getNotificationSummaryApi: notificationApiMocks.getNotificationSummaryApi,
  getNotificationPageApi: notificationApiMocks.getNotificationPageApi,
  readNotificationApi: notificationApiMocks.readNotificationApi,
  readAllNotificationsApi: notificationApiMocks.readAllNotificationsApi,
  publishAdminNotificationApi: notificationApiMocks.publishAdminNotificationApi
}))

vi.mock("@/pinia/stores/user", () => ({
  useUserStore: () => userStoreMock
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({
    push: routerPush
  })
}))

const elementStubs = {
  "el-popover": {
    template: "<div class='el-popover-stub'><slot name='reference' /><slot /></div>"
  },
  "el-badge": {
    template: "<div class='el-badge-stub'><slot /></div>"
  },
  "el-tooltip": {
    template: "<div class='el-tooltip-stub'><slot /></div>"
  },
  "el-icon": {
    template: "<i class='el-icon-stub'><slot /></i>"
  },
  "el-tabs": {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<div class='el-tabs-stub'><slot /></div>"
  },
  "el-tab-pane": {
    props: ["name"],
    template: "<div class='el-tab-pane-stub'><slot name='label' /><slot /></div>"
  },
  "el-scrollbar": {
    template: "<div class='el-scrollbar-stub'><slot /></div>"
  },
  "el-button": {
    emits: ["click"],
    template: "<button class='el-button-stub' @click=\"$emit('click', $event)\"><slot /></button>"
  },
  AdminNotificationPublishDialog: {
    props: ["modelValue"],
    emits: ["update:modelValue", "published"],
    template: "<div v-if='modelValue' class='publish-dialog-stub'><button class='publish-dialog-published' @click=\"$emit('published', { type: 'ANNOUNCEMENT', category: 'NOTICE', recipientScope: 'ALL', title: '系统公告', publishedCount: 6 })\">done</button></div>"
  },
  "el-empty": {
    template: "<div class='el-empty-stub' />"
  },
  "el-skeleton": {
    template: "<div class='el-skeleton-stub' />"
  },
  "el-card": {
    emits: ["click"],
    template: "<div class='el-card-stub' @click=\"$emit('click')\"><slot name='header' /><slot /></div>"
  },
  "el-tag": {
    template: "<span class='el-tag-stub'><slot /></span>"
  }
} as const

const buildItem = (overrides: Partial<NotificationItem> = {}): NotificationItem => ({
  id: 1,
  category: "NOTICE",
  title: "会议时间已调整",
  content: "周会复盘已调整到今天 16:30，请留意最新时间。",
  createdAt: "2026-04-16 09:00:00",
  read: false,
  route: "/reservations/index",
  routeQuery: {
    reservationId: 5001,
    status: "ACTIVE"
  },
  ...overrides
})

function mountNotify() {
  return mount(Notify, {
    global: {
      stubs: elementStubs
    }
  })
}

function mountList(data: NotificationItem[], loading = false) {
  return mount(List, {
    props: {
      data,
      loading
    },
    global: {
      stubs: elementStubs
    }
  })
}

beforeEach(() => {
  routerPush.mockReset()
  notificationApiMocks.getNotificationSummaryApi.mockReset()
  notificationApiMocks.getNotificationPageApi.mockReset()
  notificationApiMocks.readNotificationApi.mockReset()
  notificationApiMocks.readAllNotificationsApi.mockReset()
  notificationApiMocks.publishAdminNotificationApi.mockReset()
  userStoreMock.roles = ["admin"]

  notificationApiMocks.getNotificationSummaryApi.mockResolvedValue({
    code: 0,
    message: "ok",
    data: {
      totalUnread: 2,
      unreadByCategory: {
        NOTICE: 1,
        MESSAGE: 1,
        TODO: 0
      }
    }
  })

  notificationApiMocks.getNotificationPageApi.mockImplementation(async ({ category }) => ({
    code: 0,
    message: "ok",
    data: {
      list: category === "NOTICE" ? [buildItem()] : [],
      total: category === "NOTICE" ? 1 : 0,
      pageNum: 1,
      pageSize: 20
    }
  }))

  notificationApiMocks.readNotificationApi.mockResolvedValue({
    code: 0,
    message: "ok",
    data: {
      id: 1,
      read: true
    }
  })

  notificationApiMocks.readAllNotificationsApi.mockResolvedValue({
    code: 0,
    message: "ok",
    data: {
      category: "NOTICE",
      updatedCount: 1
    }
  })
})

describe("notify", () => {
  it("加载未读汇总和当前分类列表", async () => {
    const wrapper = mountNotify()

    await flushPromises()

    expect(wrapper.classes("notify")).toBe(true)
    expect(notificationApiMocks.getNotificationSummaryApi).toHaveBeenCalled()
    expect(notificationApiMocks.getNotificationPageApi).toHaveBeenCalledWith({
      category: "NOTICE",
      pageNum: 1,
      pageSize: 20
    })
  })

  it("点击通知卡片会先跳转到具体预约再标记已读", async () => {
    const wrapper = mountNotify()

    await flushPromises()
    await wrapper.find(".card-container").trigger("click")
    await flushPromises()

    expect(notificationApiMocks.readNotificationApi).toHaveBeenCalledWith(1)
    expect(routerPush).toHaveBeenCalledWith({
      path: "/reservations/index",
      query: {
        reservationId: "5001",
        status: "ACTIVE"
      }
    })
    expect(routerPush.mock.invocationCallOrder[0]).toBeLessThan(notificationApiMocks.readNotificationApi.mock.invocationCallOrder[0])
  })

  it("支持当前分类全部标记已读", async () => {
    const wrapper = mountNotify()

    await flushPromises()
    await wrapper.find(".notify-read-all-button").trigger("click")
    await flushPromises()

    expect(notificationApiMocks.readAllNotificationsApi).toHaveBeenCalledWith({
      category: "NOTICE"
    })
  })

  it("管理员可以在铃铛弹层打开通知发布弹窗", async () => {
    const wrapper = mountNotify()

    await flushPromises()

    expect(wrapper.find(".notify-publish-button").exists()).toBe(true)

    await wrapper.find(".notify-publish-button").trigger("click")
    await flushPromises()

    expect(wrapper.find(".publish-dialog-stub").exists()).toBe(true)
  })

  it("普通用户看不到铃铛弹层通知发布入口", async () => {
    userStoreMock.roles = ["user"]
    const wrapper = mountNotify()

    await flushPromises()

    expect(wrapper.find(".notify-publish-button").exists()).toBe(false)
    expect(wrapper.find(".publish-dialog-stub").exists()).toBe(false)
  })

  it("通知发布成功后刷新未读摘要和当前分类列表", async () => {
    const wrapper = mountNotify()

    await flushPromises()
    notificationApiMocks.getNotificationSummaryApi.mockClear()
    notificationApiMocks.getNotificationPageApi.mockClear()

    await wrapper.find(".notify-publish-button").trigger("click")
    await wrapper.find(".publish-dialog-published").trigger("click")
    await flushPromises()

    expect(notificationApiMocks.getNotificationSummaryApi).toHaveBeenCalledTimes(1)
    expect(notificationApiMocks.getNotificationPageApi).toHaveBeenCalledWith({
      category: "NOTICE",
      pageNum: 1,
      pageSize: 20
    })
  })

  it("通知接口失败时不抛出未处理错误", async () => {
    notificationApiMocks.getNotificationSummaryApi.mockRejectedValue(new Error("network"))
    notificationApiMocks.getNotificationPageApi.mockRejectedValue(new Error("network"))

    const wrapper = mountNotify()

    await flushPromises()

    expect(wrapper.find(".el-empty-stub").exists()).toBe(true)
  })
})

describe("list", () => {
  it("list 长度为 0 时显示空状态", () => {
    const wrapper = mountList([], false)

    expect(wrapper.find(".el-empty-stub").exists()).toBe(true)
  })

  it("未读卡片支持点击跳转和标记已读", async () => {
    const item = buildItem()
    const wrapper = mountList([item])

    expect(wrapper.find(".card-container").classes()).toContain("is-unread")

    await wrapper.find(".card-container").trigger("click")
    await wrapper.findAll("button")[1].trigger("click")

    expect(wrapper.emitted("item-click")?.[0]).toEqual([item])
    expect(wrapper.emitted("mark-read")?.[0]).toEqual([item])
  })
})
