import { flushPromises, shallowMount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"
import AdminNotifications from "@/pages/admin/notifications/index.vue"

const notificationApiMocks = vi.hoisted(() => ({
  publishAdminNotificationApi: vi.fn().mockResolvedValue({
    data: {
      type: "ANNOUNCEMENT",
      category: "NOTICE",
      recipientScope: "ALL",
      title: "系统公告",
      publishedCount: 6
    }
  })
}))

vi.mock("@/common/apis/notifications", () => ({
  publishAdminNotificationApi: notificationApiMocks.publishAdminNotificationApi
}))

function mountAdminNotifications() {
  return shallowMount(AdminNotifications, {
    global: {
      stubs: {
        "el-form": {
          template: "<form><slot /></form>"
        },
        "el-form-item": {
          props: ["label"],
          template: "<div class='form-item-stub'><label>{{ label }}</label><slot /></div>"
        }
      }
    }
  })
}

describe("AdminNotifications", () => {
  it("渲染管理端通知发布页", () => {
    const wrapper = mountAdminNotifications()

    expect(wrapper.text()).toContain("通知发布")
    expect(wrapper.text()).toContain("发布类型")
    expect(wrapper.text()).toContain("接收范围")
  })

  it("提交公告发布参数", async () => {
    const wrapper = mountAdminNotifications()
    const vm = wrapper.vm as any

    vm.publishForm.type = "ANNOUNCEMENT"
    vm.publishForm.recipientScope = "ALL"
    vm.publishForm.title = "系统公告"
    vm.publishForm.content = "明天上午系统升级。"
    await vm.submitPublish()
    await flushPromises()

    expect(notificationApiMocks.publishAdminNotificationApi).toHaveBeenCalledWith({
      type: "ANNOUNCEMENT",
      recipientScope: "ALL",
      title: "系统公告",
      content: "明天上午系统升级。"
    })
  })
})
