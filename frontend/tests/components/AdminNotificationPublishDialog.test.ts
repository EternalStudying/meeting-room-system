import { flushPromises, shallowMount } from "@vue/test-utils"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AdminNotificationPublishDialog from "@/common/components/Notify/AdminNotificationPublishDialog.vue"

const notificationApiMocks = vi.hoisted(() => ({
  publishAdminNotificationApi: vi.fn()
}))

vi.mock("@/common/apis/notifications", () => ({
  publishAdminNotificationApi: notificationApiMocks.publishAdminNotificationApi
}))

const publishResult = {
  type: "ANNOUNCEMENT",
  category: "NOTICE",
  recipientScope: "ALL",
  title: "系统公告",
  publishedCount: 6
}

function mountDialog() {
  return shallowMount(AdminNotificationPublishDialog, {
    props: {
      modelValue: true
    },
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

describe("AdminNotificationPublishDialog", () => {
  beforeEach(() => {
    notificationApiMocks.publishAdminNotificationApi.mockReset()
    vi.spyOn(ElMessage, "warning").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "success").mockImplementation(vi.fn() as any)
    vi.spyOn(ElMessage, "error").mockImplementation(vi.fn() as any)
  })

  it("标题为空时阻止提交并提示", async () => {
    const wrapper = mountDialog()
    const vm = wrapper.vm as any
    vm.publishForm.content = "明天上午系统升级。"

    await vm.submitPublish()

    expect(notificationApiMocks.publishAdminNotificationApi).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith("请输入通知标题")
  })

  it("内容为空时阻止提交并提示", async () => {
    const wrapper = mountDialog()
    const vm = wrapper.vm as any
    vm.publishForm.title = "系统公告"

    await vm.submitPublish()

    expect(notificationApiMocks.publishAdminNotificationApi).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith("请输入通知内容")
  })

  it("发布成功后关闭弹窗、清空表单并通知父组件刷新", async () => {
    notificationApiMocks.publishAdminNotificationApi.mockResolvedValue({
      data: publishResult
    })
    const wrapper = mountDialog()
    const vm = wrapper.vm as any
    vm.publishForm.type = "ANNOUNCEMENT"
    vm.publishForm.recipientScope = "ALL"
    vm.publishForm.title = "  系统公告  "
    vm.publishForm.content = "  明天上午系统升级。  "

    await vm.submitPublish()
    await flushPromises()

    expect(notificationApiMocks.publishAdminNotificationApi).toHaveBeenCalledWith({
      type: "ANNOUNCEMENT",
      recipientScope: "ALL",
      title: "系统公告",
      content: "明天上午系统升级。"
    })
    expect(ElMessage.success).toHaveBeenCalledWith("已发布给 6 个接收人")
    expect(wrapper.emitted("published")?.[0]).toEqual([publishResult])
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
    expect(vm.publishForm.title).toBe("")
    expect(vm.publishForm.content).toBe("")
  })

  it("发布失败后弹窗保持打开且保留已输入内容", async () => {
    notificationApiMocks.publishAdminNotificationApi.mockRejectedValue(new Error("network"))
    const wrapper = mountDialog()
    const vm = wrapper.vm as any
    vm.publishForm.title = "维护通知"
    vm.publishForm.content = "今晚 22:00 维护 A101 会议室。"

    await vm.submitPublish()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith("通知发布失败，请稍后重试")
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    expect(vm.publishForm.title).toBe("维护通知")
    expect(vm.publishForm.content).toBe("今晚 22:00 维护 A101 会议室。")
  })
})
