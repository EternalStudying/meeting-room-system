import { beforeEach, describe, expect, it, vi } from "vitest"

const requestMock = vi.hoisted(() => vi.fn())

vi.mock("@/http/axios", () => ({
  request: requestMock
}))

describe("assistant api", () => {
  beforeEach(() => {
    requestMock.mockReset()
    vi.resetModules()
  })

  it("请求新版助手会话接口", async () => {
    const response = {
      code: 0,
      message: "ok",
      data: {
        sessionId: "asst-001",
        turnId: "turn-001",
        role: "assistant",
        state: "idle",
        message: "你好，我可以帮你处理预约和会议室事务。",
        cards: [{ type: "text", message: "你好，我可以帮你处理预约和会议室事务。" }],
        suggestions: ["帮我创建一个预约"],
      }
    }
    requestMock.mockResolvedValueOnce(response)

    const { createAssistantSessionApi } = await import("@/common/apis/assistant")
    await expect(createAssistantSessionApi()).resolves.toEqual(response)
    expect(requestMock).toHaveBeenCalledWith({
      url: "ai/assistant/session",
      method: "post",
      timeout: 30000,
      silentError: true
    })
  })

  it("发送消息时请求新版结构化 message 接口", async () => {
    const response = {
      code: 0,
      message: "ok",
      data: {
        sessionId: "asst-001",
        turnId: "turn-002",
        role: "assistant",
        state: "collecting",
        message: "还缺少会议日期和参会人数。",
        suggestions: ["今天下午", "10人"],
        cards: [
          {
            type: "field_form",
            title: "补齐参数",
            message: "还缺少会议日期和参会人数。",
            fields: [
              { key: "meetingDate", label: "会议日期", inputType: "date", required: true },
              { key: "attendees", label: "参会人数", inputType: "number", required: true }
            ]
          }
        ]
      }
    }
    requestMock.mockResolvedValueOnce(response)

    const { sendAssistantMessageApi } = await import("@/common/apis/assistant")
    await expect(sendAssistantMessageApi({
      sessionId: "asst-001",
      message: "帮我创建一个预约"
    })).resolves.toEqual(response)

    expect(requestMock).toHaveBeenCalledWith({
      url: "ai/assistant/message",
      method: "post",
      data: {
        sessionId: "asst-001",
        message: "帮我创建一个预约"
      },
      timeout: 30000,
      silentError: true
    })
  })

  it("确认执行时调用 confirm 接口", async () => {
    const response = {
      code: 0,
      message: "ok",
      data: {
        sessionId: "asst-001",
        turnId: "turn-003",
        role: "assistant",
        state: "executed",
        message: "预约已创建成功。",
        suggestions: ["查看我的预约"],
        cards: [
          {
            type: "execution_result",
            title: "预约创建成功",
            message: "预约已创建成功。",
            result: {
              status: "success",
              title: "预约创建成功",
              summaryItems: [{ label: "预约编号", value: "R20260418001" }],
              deepLink: "/reservations/index"
            }
          }
        ]
      }
    }
    requestMock.mockResolvedValueOnce(response)

    const { confirmAssistantActionApi } = await import("@/common/apis/assistant")
    await expect(confirmAssistantActionApi("exec-001")).resolves.toEqual(response)
    expect(requestMock).toHaveBeenCalledWith({
      url: "ai/assistant/actions/exec-001/confirm",
      method: "post",
      timeout: 30000,
      silentError: true
    })
  })

  it("后端失败时直接抛出错误", async () => {
    requestMock.mockRejectedValueOnce(new Error("network error"))

    const { sendAssistantMessageApi } = await import("@/common/apis/assistant")
    await expect(sendAssistantMessageApi({
      sessionId: "asst-001",
      message: "帮我创建一个预约"
    })).rejects.toThrow("network error")
  })
})
