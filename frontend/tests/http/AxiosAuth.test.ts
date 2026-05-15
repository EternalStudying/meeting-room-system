import type { AxiosAdapter, AxiosResponse } from "axios"
import { ElMessage } from "element-plus"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { request } from "@/http/axios"

const userStoreMock = vi.hoisted(() => ({
  logout: vi.fn()
}))

vi.mock("@@/utils/local-storage", () => ({
  getToken: () => ""
}))

vi.mock("@/pinia/stores/user", () => ({
  useUserStore: () => userStoreMock
}))

function apiResponse(data: unknown): AxiosAdapter {
  return config => Promise.resolve({
    data,
    status: 200,
    statusText: "OK",
    headers: {},
    config
  } as AxiosResponse)
}

describe("axios auth error handling", () => {
  beforeEach(() => {
    userStoreMock.logout.mockReset()
    vi.spyOn(ElMessage, "error").mockImplementation(vi.fn() as any)
  })

  it("登录密码错误时提示中文错误且不触发登出刷新", async () => {
    await expect(request({
      url: "auth/login",
      method: "post",
      adapter: apiResponse({
        code: 401,
        message: "username or password is incorrect",
        data: null
      })
    })).rejects.toThrow("用户名或密码错误")

    expect(ElMessage.error).toHaveBeenCalledWith("用户名或密码错误")
    expect(userStoreMock.logout).not.toHaveBeenCalled()
  })

  it("登录验证码错误时提示验证码错误", async () => {
    await expect(request({
      url: "auth/login",
      method: "post",
      adapter: apiResponse({
        code: 1001,
        message: "captcha invalid",
        data: null
      })
    })).rejects.toThrow("验证码错误")

    expect(ElMessage.error).toHaveBeenCalledWith("验证码错误")
    expect(userStoreMock.logout).not.toHaveBeenCalled()
  })

  it.each(["account is disabled", "account is disable"])("登录停用账号时提示中文错误：%s", async (message) => {
    await expect(request({
      url: "auth/login",
      method: "post",
      adapter: apiResponse({
        code: 403,
        message,
        data: null
      })
    })).rejects.toThrow("账号已停用，请联系管理员")

    expect(ElMessage.error).toHaveBeenCalledWith("账号已停用，请联系管理员")
  })
})
