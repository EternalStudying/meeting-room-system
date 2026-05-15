import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("@@/apis/users", () => ({
  getCurrentUserApi: vi.fn()
}))

vi.mock("@/router", () => ({
  resetRouter: vi.fn()
}))

describe("user store", () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
    setActivePinia(createPinia())
  })

  it("退出登录时会清空 assistant 结构化会话", async () => {
    const { useAssistantStore } = await import("@/pinia/stores/assistant")
    const { useUserStore } = await import("@/pinia/stores/user")

    const assistantStore = useAssistantStore()
    const userStore = useUserStore()

    assistantStore.sessionId = "session-001"
    assistantStore.quickPrompts = ["查看我本周的预约"]
    assistantStore.turns.push({
      id: 1,
      role: "assistant",
      text: "你好",
      timestamp: "10:00",
      state: "idle",
      cards: [{ type: "text", message: "你好" }]
    })

    userStore.logout()

    expect(assistantStore.sessionId).toBe("")
    expect(assistantStore.quickPrompts).toEqual([])
    expect(assistantStore.turns).toEqual([])
  })
})
