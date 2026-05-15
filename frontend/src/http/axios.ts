import type { AxiosInstance, AxiosRequestConfig } from "axios"
import { getToken } from "@@/utils/local-storage"
import axios from "axios"
import { get, merge } from "lodash-es"
import { useUserStore } from "@/pinia/stores/user"

type RequestConfig = AxiosRequestConfig & {
  silentError?: boolean
}

function logout() {
  useUserStore().logout()
  location.reload()
}

function isLoginRequest(config?: AxiosRequestConfig) {
  return String(config?.url ?? "").includes("auth/login")
}

function normalizeErrorMessage(message: unknown, fallback = "操作失败，请稍后重试") {
  const text = typeof message === "string" ? message.trim() : ""
  if (!text) return fallback

  const lowerText = text.toLowerCase()
  if (lowerText.includes("account is disabled") || lowerText.includes("account is disable")) return "账号已停用，请联系管理员"
  if (lowerText.includes("username or password is incorrect") || lowerText.includes("bad credentials")) return "用户名或密码错误"
  if (lowerText.includes("captcha invalid") || lowerText.includes("invalid captcha")) return "验证码错误"
  if (lowerText.includes("captcha expired")) return "验证码已过期，请重新获取"
  if (lowerText.includes("not logged in")) return "登录已过期，请重新登录"
  if (lowerText.includes("endclock must be greater than startclock") || lowerText.includes("endtime must be greater than starttime") || lowerText.includes("enddate must be greater than startdate")) return "结束时间必须晚于开始时间"
  if (lowerText.includes("room not found")) return "会议室不存在"
  if (lowerText.includes("roomcode already exists") || lowerText.includes("room code already exists")) return "会议室编码已存在，请更换编码"
  if (lowerText.includes("room has related reservations") || (lowerText.includes("reservation") && lowerText.includes("cannot be deleted"))) return "该会议室已有预约记录，暂不允许删除"
  if (lowerText.includes("devicecode already exists") || lowerText.includes("device code already exists")) return "设备编码已存在，请更换编码"
  if (lowerText.includes("device is still bound to rooms")) return "该设备仍绑定会议室，暂不允许删除"
  if (lowerText.includes("room is under maintenance")) return "当前会议室不可预约"
  if (lowerText.includes("attendees exceeds room capacity")) return "参会人数不能超过会议室容量"
  if (lowerText.includes("reservation time conflicts")) return "当前时间段已有已通过预约"
  if (lowerText.includes("participant user does not exist")) return "参会人不存在或已不可用"
  if (lowerText.includes("device requirements cannot be satisfied")) return "设备需求无法满足"
  if (lowerText.includes("only pending reservation can be approved") || lowerText.includes("only pending reservation can be rejected")) return "仅待审核预约可执行该操作"
  if (lowerText.includes("only active reservation can be marked exception")) return "仅进行中预约可标记异常"
  if (lowerText.includes("reservation status changed")) return "预约状态已变化，请刷新后重试"
  if (lowerText.includes("reservation not found")) return "预约不存在"
  if (lowerText.includes("reservation is not active")) return "当前预约不是进行中状态"
  if (lowerText.includes("reservation has already ended")) return "预约已结束"
  if (lowerText.includes("only ended reservation can be reviewed")) return "仅已结束预约可评价"
  if (lowerText.includes("review already exists")) return "已评价，不能重复提交"
  if (lowerText.includes("reason must not be blank")) return "请填写原因"
  if (lowerText.includes("content length must be less than or equal to 300")) return "评价内容不能超过 300 字"
  if (lowerText.includes("format is invalid")) return "参数格式不正确"
  if (lowerText.includes("clock format is invalid")) return "时间格式不正确"
  if (lowerText.includes("request body must not be null")) return "请求参数不能为空"

  return /[\u4E00-\u9FFF]/.test(text) ? text : fallback
}

function createInstance() {
  const instance = axios.create()

  instance.interceptors.request.use(
    config => config,
    error => Promise.reject(error)
  )

  instance.interceptors.response.use(
    (response) => {
      const apiData = response.data
      const responseType = response.config.responseType
      const requestConfig = response.config as RequestConfig
      const shouldNotifyError = requestConfig.silentError !== true

      if (responseType === "blob" || responseType === "arraybuffer") return apiData

      const code = apiData.code
      if (code === undefined) {
        shouldNotifyError && ElMessage.error("非本系统的接口")
        return Promise.reject(new Error("非本系统的接口"))
      }

      switch (code) {
        case 0:
          return apiData
        case 401:
          if (!isLoginRequest(response.config)) {
            logout()
          }
          {
            const message = normalizeErrorMessage(apiData.message, "未授权")
            shouldNotifyError && ElMessage.error(message)
            return Promise.reject(new Error(message))
          }
        default:
          {
            const message = normalizeErrorMessage(apiData.message)
            shouldNotifyError && ElMessage.error(message)
            return Promise.reject(new Error(message))
          }
      }
    },
    (error) => {
      const requestConfig = get(error, "config") as RequestConfig | undefined
      const shouldNotifyError = requestConfig?.silentError !== true
      const status = get(error, "response.status")
      const message = get(error, "response.data.message")

      switch (status) {
        case 400:
          error.message = normalizeErrorMessage(message, "请求错误")
          break
        case 401:
          error.message = normalizeErrorMessage(message, "未授权")
          if (!isLoginRequest(requestConfig)) {
            logout()
          }
          break
        case 403:
          error.message = normalizeErrorMessage(message, "拒绝访问")
          break
        case 404:
          error.message = "请求地址出错"
          break
        case 408:
          error.message = "请求超时"
          break
        case 500:
          error.message = "服务器内部错误"
          break
        case 501:
          error.message = "服务未实现"
          break
        case 502:
          error.message = "网关错误"
          break
        case 503:
          error.message = "服务不可用"
          break
        case 504:
          error.message = "网关超时"
          break
        case 505:
          error.message = "HTTP 版本不受支持"
          break
      }

      shouldNotifyError && ElMessage.error(error.message)
      return Promise.reject(error)
    }
  )

  return instance
}

function createRequest(instance: AxiosInstance) {
  return <T>(config: RequestConfig): Promise<T> => {
    const token = getToken()
    const defaultConfig: RequestConfig = {
      baseURL: import.meta.env.VITE_BASE_URL,
      headers: {
        token: token || undefined,
        "Content-Type": "application/json"
      },
      data: {},
      timeout: 5000,
      withCredentials: false
    }

    const mergeConfig = merge(defaultConfig, config)
    return instance(mergeConfig)
  }
}

const instance = createInstance()

export const request = createRequest(instance)
