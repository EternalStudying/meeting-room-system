import { flushPromises, mount } from "@vue/test-utils"
import ElementPlus from "element-plus"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"

const assistantApiMocks = vi.hoisted(() => ({
  createAssistantSessionApi: vi.fn(),
  sendAssistantMessageApi: vi.fn(),
  confirmAssistantActionApi: vi.fn(),
  cancelAssistantActionApi: vi.fn()
}))

const userApiMocks = vi.hoisted(() => ({
  searchUsersApi: vi.fn()
}))

vi.mock("@/common/apis/assistant", () => ({
  createAssistantSessionApi: assistantApiMocks.createAssistantSessionApi,
  sendAssistantMessageApi: assistantApiMocks.sendAssistantMessageApi,
  confirmAssistantActionApi: assistantApiMocks.confirmAssistantActionApi,
  cancelAssistantActionApi: assistantApiMocks.cancelAssistantActionApi
}))

vi.mock("@/common/apis/users", () => ({
  searchUsersApi: userApiMocks.searchUsersApi
}))

function createAssistantTurn(overrides: Record<string, unknown> = {}) {
  return {
    sessionId: "asst-001",
    turnId: "turn-001",
    role: "assistant",
    state: "idle",
    message: "你好，我可以帮你处理预约和会议室事务。",
    suggestions: ["帮我创建一个预约", "查看我本周的预约"],
    cards: [{ type: "text", message: "你好，我可以帮你处理预约和会议室事务。" }],
    ...overrides
  }
}

describe("AssistantPage", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    assistantApiMocks.createAssistantSessionApi.mockReset()
    assistantApiMocks.sendAssistantMessageApi.mockReset()
    assistantApiMocks.confirmAssistantActionApi.mockReset()
    assistantApiMocks.cancelAssistantActionApi.mockReset()
    userApiMocks.searchUsersApi.mockReset()

    assistantApiMocks.createAssistantSessionApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: createAssistantTurn()
    })

    userApiMocks.searchUsersApi.mockResolvedValue({
      code: 0,
      message: "ok",
      data: [
        {
          id: 201,
          username: "zhangsan",
          displayName: "张三（zhangsan）"
        }
      ]
    })
  })

  async function mountAssistantPage(pinia = createPinia()) {
    const AssistantPage = (await import("@/pages/assistant/index.vue")).default
    const wrapper = mount(AssistantPage, {
      global: {
        plugins: [ElementPlus, pinia]
      }
    })

    await flushPromises()
    return wrapper
  }

  it("初始化后展示 cards 协议欢迎消息和快捷任务", async () => {
    const wrapper = await mountAssistantPage()
    const welcomeText = "你好，我可以帮你处理预约和会议室事务。"

    expect(assistantApiMocks.createAssistantSessionApi).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain(welcomeText)
    expect(wrapper.text()).toContain("帮我创建一个预约")
    expect(wrapper.find(".assistant-card.is-text").exists()).toBe(false)
    expect(wrapper.findAll(".message-bubble").filter(item => item.text() === welcomeText)).toHaveLength(1)
    expect(wrapper.find(".message-stream").exists()).toBe(true)
    expect(wrapper.find(".send-icon-button").exists()).toBe(true)
  })

  it("查询结果只展示聊天气泡，不额外渲染结果卡片", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-002",
        state: "executed",
        message: "明天共有 1 条预约：项目周会。",
        suggestions: ["查看详情"],
        cards: [
          {
            type: "query_result",
            title: "查询我的预约",
            message: "明天共有 1 条预约：项目周会。"
          }
        ]
      })
    })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "明天有哪些会"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.find(".assistant-card.is-query_result").exists()).toBe(false)
    expect(wrapper.text()).toContain("项目周会")
  })

  it("渲染参数补充卡片并提交字段", async () => {
    assistantApiMocks.sendAssistantMessageApi
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-002",
          state: "collecting",
          message: "还缺少会议日期和会议室。",
          suggestions: ["今天下午", "明天上午"],
          cards: [
            {
              type: "field_form",
              title: "补齐参数",
              message: "还缺少会议日期和会议室。",
              fields: [
                { key: "meetingDate", label: "会议日期", inputType: "date", required: true },
                {
                  key: "roomId",
                  label: "会议室",
                  inputType: "select",
                  required: true,
                  options: [
                    { label: "云杉会议室", value: 101 },
                    { label: "潮汐会议室", value: 102 }
                  ]
                }
              ]
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-003",
          state: "awaiting_confirmation",
          message: "信息已齐全，请确认是否创建预约。",
          suggestions: ["确认执行", "取消本次操作"],
          cards: [
            {
              type: "confirmation",
              title: "创建预约",
              message: "信息已齐全，请确认是否创建预约。",
              pendingAction: {
                executionId: "exec-001",
                actionType: "reservations.create",
                title: "创建预约",
                confirmRequired: true,
                summaryItems: [
                  { label: "会议日期", value: "2026-04-20" },
                  { label: "会议室", value: "云杉会议室" }
                ]
              }
            }
          ]
        })
      })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "帮我创建一个预约"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.find(".assistant-card.is-field_form").exists()).toBe(true)
    vm.updateCollectFieldValue("meetingDate", "2026-04-20")
    vm.updateCollectFieldValue("roomId", 101)
    await vm.submitActiveCollectTurn()
    await flushPromises()

    expect(assistantApiMocks.sendAssistantMessageApi).toHaveBeenNthCalledWith(2, {
      sessionId: "asst-001",
      fieldValues: {
        meetingDate: "2026-04-20",
        roomId: 101
      }
    })
    expect(wrapper.find(".assistant-card.is-confirmation").exists()).toBe(true)
  })

  it("创建预约首张补参卡隐藏参会人数和所需设备", async () => {
    assistantApiMocks.sendAssistantMessageApi
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-002",
          state: "collecting",
          message: "要继续创建预约，我还需要一些关键信息。",
          cards: [
            {
              type: "field_form",
              title: "补齐参数",
              message: "要继续创建预约，我还需要一些关键信息。",
              fields: [
                { key: "title", label: "会议主题", inputType: "text", required: true },
                { key: "meetingDate", label: "会议日期", inputType: "date", required: true },
                { key: "startClock", label: "开始时间", inputType: "time", required: true },
                { key: "endClock", label: "结束时间", inputType: "time", required: true },
                { key: "attendees", label: "参会人数", inputType: "number", required: true },
                { key: "participantUserIds", label: "参会人", inputType: "user-select", required: false }
              ]
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-003",
          state: "awaiting_confirmation",
          message: "信息已经齐了，请确认是否创建预约。",
          cards: [
            {
              type: "confirmation",
              title: "创建预约",
              pendingAction: {
                executionId: "exec-create-001",
                actionType: "reservations.create",
                title: "创建预约",
                confirmRequired: true,
                summaryItems: [{ label: "会议主题", value: "111" }]
              }
            }
          ]
        })
      })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "帮我创建一个预约"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.find(".assistant-card.is-field_form").exists()).toBe(true)
    expect(wrapper.text()).not.toContain("参会人数")
    expect(wrapper.text()).not.toContain("所需设备")

    vm.updateCollectFieldValue("title", "111")
    vm.updateCollectFieldValue("meetingDate", "2026-05-15")
    vm.updateCollectFieldValue("startClock", "08:30")
    vm.updateCollectFieldValue("endClock", "10:00")
    vm.updateCollectFieldValue("participantUserIds", [201, 202])
    await vm.submitActiveCollectTurn()
    await flushPromises()

    expect(assistantApiMocks.sendAssistantMessageApi).toHaveBeenNthCalledWith(2, {
      sessionId: "asst-001",
      fieldValues: {
        title: "111",
        meetingDate: "2026-05-15",
        startClock: "08:30",
        endClock: "10:00",
        participantUserIds: [201, 202],
        attendees: 3
      }
    })
    expect(wrapper.find(".assistant-card.is-field_form").exists()).toBe(false)
    expect(wrapper.find(".assistant-card.is-confirmation").exists()).toBe(true)
  })

  it("补参卡保留单个已选参会人的显示标签", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-002",
        state: "collecting",
        message: "请选择参会人。",
        cards: [
          {
            type: "field_form",
            title: "补齐参数",
            fields: [
              {
                key: "participantUserIds",
                label: "参会人",
                inputType: "user-select",
                required: false,
                value: [201],
                options: [{ label: "系统管理员（admin）", value: 201 }]
              }
            ]
          }
        ]
      })
    })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "帮我创建一个预约"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.text()).toContain("系统管理员（admin）")
    expect(wrapper.find("label.collect-field .assistant-user-select").exists()).toBe(false)
  })

  it("设备需求卡点击添加设备会新增一行并可提交空设备列表", async () => {
    assistantApiMocks.sendAssistantMessageApi
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-002",
          state: "collecting",
          message: "请选择所需设备。",
          cards: [
            {
              type: "field_form",
              title: "补齐参数",
              fields: [
                {
                  key: "deviceRequirements",
                  label: "所需设备",
                  inputType: "device-requirements",
                  required: false,
                  options: [
                    { label: "投影仪", value: 1 },
                    { label: "白板", value: 2 }
                  ]
                }
              ]
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        code: 0,
        message: "ok",
        data: createAssistantTurn({
          turnId: "turn-003",
          state: "collecting",
          message: "我已经筛出推荐会议室了，请选择一个。",
          cards: [
            {
              type: "field_form",
              fields: [
                {
                  key: "roomId",
                  label: "推荐会议室",
                  inputType: "select",
                  required: true,
                  options: [{ label: "A101 多媒体会议室", value: 101 }]
                }
              ]
            }
          ]
        })
      })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "补充设备"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.findAll(".device-requirement-row")).toHaveLength(1)
    await wrapper.find(".add-device-button").trigger("click")
    await flushPromises()
    expect(wrapper.findAll(".device-requirement-row")).toHaveLength(2)

    await vm.submitActiveCollectTurn()
    await flushPromises()

    expect(assistantApiMocks.sendAssistantMessageApi).toHaveBeenNthCalledWith(2, {
      sessionId: "asst-001",
      fieldValues: {
        deviceRequirements: []
      }
    })
    expect(wrapper.text()).toContain("推荐会议室")
  })

  it("渲染取消会议室的澄清卡片", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-002",
        state: "collecting",
        message: "你是想取消某个预约，还是只是放弃当前选择的会议室？",
        suggestions: ["取消预约", "重新选择会议室"],
        cards: [
          {
            type: "clarification",
            title: "需要补充信息",
            message: "你是想取消某个预约，还是只是放弃当前选择的会议室？"
          }
        ]
      })
    })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "取消这个会议室"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.find(".assistant-card.is-clarification").exists()).toBe(true)
    expect(wrapper.text()).toContain("取消某个预约")
  })

  it("RAG 文本回答只展示聊天气泡，不额外渲染文本卡片", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-002",
        state: "idle",
        message: "取消预约需要先明确目标预约，并填写取消原因。",
        suggestions: ["查看我的预约"],
        cards: [
          {
            type: "text",
            title: "系统帮助",
            message: "取消预约需要先明确目标预约，并填写取消原因。"
          }
        ]
      })
    })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "怎么取消预约"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.text()).toContain("取消预约需要先明确目标预约")
    expect(wrapper.find(".assistant-card.is-text").exists()).toBe(false)
  })

  it("保留确认卡片，执行结果只展示聊天气泡", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-002",
        state: "awaiting_confirmation",
        message: "请确认是否取消这场预约。",
        suggestions: ["确认执行"],
        cards: [
          {
            type: "confirmation",
            title: "取消预约",
            pendingAction: {
              executionId: "exec-009",
              actionType: "reservations.cancel",
              title: "取消预约",
              confirmRequired: true,
              summaryItems: [{ label: "会议主题", value: "项目同步" }]
            }
          }
        ]
      })
    })

    assistantApiMocks.confirmAssistantActionApi.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: createAssistantTurn({
        turnId: "turn-003",
        state: "executed",
        message: "预约已取消。",
        suggestions: ["查看我的预约"],
        cards: [
          {
            type: "execution_result",
            title: "取消预约成功",
            message: "预约已取消。",
            result: {
              status: "success",
              title: "取消预约成功",
              summaryItems: [{ label: "状态", value: "已取消" }],
              deepLink: "/reservations/index"
            }
          }
        ]
      })
    })

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "取消我明天下午的预约"
    await vm.sendMessage()
    await flushPromises()
    expect(wrapper.find(".assistant-card.is-confirmation").exists()).toBe(true)

    await vm.confirmPendingAction()
    await flushPromises()

    expect(assistantApiMocks.confirmAssistantActionApi).toHaveBeenCalledWith("exec-009")
    expect(wrapper.find(".assistant-card.is-execution_result").exists()).toBe(false)
    expect(wrapper.text()).toContain("预约已取消。")
    expect(wrapper.find(".result-link-button").exists()).toBe(false)
  })

  it("接口异常时展示可恢复聊天提示，不额外渲染错误卡片", async () => {
    assistantApiMocks.sendAssistantMessageApi.mockRejectedValueOnce(new Error("network error"))

    const wrapper = await mountAssistantPage()
    const vm = wrapper.vm as any

    vm.inputMessage = "明天有哪些会"
    await vm.sendMessage()
    await flushPromises()

    expect(wrapper.find(".assistant-card.is-error").exists()).toBe(false)
    expect(wrapper.text()).toContain("我没能继续处理")
    expect(wrapper.text()).not.toContain("当前消息服务暂时不可用")
  })
})
