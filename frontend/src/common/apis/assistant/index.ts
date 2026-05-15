import type * as Assistant from "./type"
import { request } from "@/http/axios"

const ASSISTANT_REQUEST_TIMEOUT = 30000

export function createAssistantSessionApi() {
  return request<Assistant.AssistantSessionResponseData>({
    url: "ai/assistant/session",
    method: "post",
    timeout: ASSISTANT_REQUEST_TIMEOUT,
    silentError: true
  })
}

export function sendAssistantMessageApi(data: Assistant.AssistantMessageRequestData) {
  return request<Assistant.AssistantMessageResponseData>({
    url: "ai/assistant/message",
    method: "post",
    data,
    timeout: ASSISTANT_REQUEST_TIMEOUT,
    silentError: true
  })
}

export function confirmAssistantActionApi(executionId: string) {
  return request<Assistant.AssistantActionResponseData>({
    url: `ai/assistant/actions/${executionId}/confirm`,
    method: "post",
    timeout: ASSISTANT_REQUEST_TIMEOUT,
    silentError: true
  })
}

export function cancelAssistantActionApi(executionId: string) {
  return request<Assistant.AssistantActionResponseData>({
    url: `ai/assistant/actions/${executionId}/cancel`,
    method: "post",
    timeout: ASSISTANT_REQUEST_TIMEOUT,
    silentError: true
  })
}
