import { beforeEach, describe, expect, it, vi } from "vitest"

const requestMock = vi.hoisted(() => vi.fn())

vi.mock("@/http/axios", () => ({
  request: requestMock
}))

import {
  getNotificationPageApi,
  getNotificationSummaryApi,
  publishAdminNotificationApi,
  readAllNotificationsApi,
  readNotificationApi
} from "@/common/apis/notifications"

describe("notifications api", () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it("后端可用时优先使用真实接口", async () => {
    const backendResponse = {
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
    }
    requestMock.mockResolvedValue(backendResponse)

    const result = await getNotificationSummaryApi()

    expect(requestMock).toHaveBeenCalledWith({
      url: "notifications/summary",
      method: "get",
      silentError: true
    })
    expect(result).toEqual(backendResponse)
  })

  it("后端失败时直接抛出错误", async () => {
    requestMock.mockRejectedValue(new Error("404"))

    await expect(getNotificationSummaryApi()).rejects.toThrow("404")
  })

  it("列表和已读操作直接请求真实接口", async () => {
    requestMock.mockResolvedValue({ code: 0, message: "ok", data: {} })

    await getNotificationPageApi({
      category: "NOTICE",
      pageNum: 1,
      pageSize: 20
    })
    await readNotificationApi(7)
    await readAllNotificationsApi({
      category: "TODO"
    })

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: "notifications",
      method: "get",
      params: {
        category: "NOTICE",
        pageNum: 1,
        pageSize: 20
      },
      silentError: true
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: "notifications/7/read",
      method: "post",
      silentError: true
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: "notifications/read-all",
      method: "post",
      data: {
        category: "TODO"
      },
      silentError: true
    })
  })

  it("列表和已读操作失败时直接抛出错误", async () => {
    requestMock.mockRejectedValue(new Error("network"))

    await expect(getNotificationPageApi({
      category: "NOTICE",
      pageNum: 1,
      pageSize: 20
    })).rejects.toThrow("network")
    await expect(readNotificationApi(7)).rejects.toThrow("network")
    await expect(readAllNotificationsApi({
      category: "TODO"
    })).rejects.toThrow("network")
  })

  it("管理员发布通知优先调用真实接口", async () => {
    const payload = {
      type: "ANNOUNCEMENT" as const,
      recipientScope: "ALL" as const,
      title: "系统公告",
      content: "明天上午系统升级。"
    }
    const backendResponse = {
      code: 0,
      message: "ok",
      data: {
        type: "ANNOUNCEMENT",
        category: "NOTICE",
        recipientScope: "ALL",
        title: "系统公告",
        publishedCount: 6
      }
    }
    requestMock.mockResolvedValue(backendResponse)

    const result = await publishAdminNotificationApi(payload)

    expect(requestMock).toHaveBeenCalledWith({
      url: "admin/notifications",
      method: "post",
      data: payload,
      silentError: true
    })
    expect(result).toEqual(backendResponse)
  })

  it("管理员发布通知接口失败时直接抛出错误", async () => {
    const payload = {
      type: "MAINTENANCE" as const,
      recipientScope: "USERS" as const,
      title: "维护通知",
      content: "今晚 22:00 维护 A101 会议室。"
    }
    const error = new Error("404")
    requestMock.mockRejectedValue(error)

    await expect(publishAdminNotificationApi(payload)).rejects.toThrow("404")
  })
})
