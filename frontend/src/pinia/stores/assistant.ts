import type {
  AssistantActionResult,
  AssistantCard,
  AssistantFieldValue,
  AssistantMissingField,
  AssistantPendingAction,
  AssistantSummaryItem,
  AssistantTurnPayload,
  AssistantTurnState
} from "@/common/apis/assistant/type"

export type AssistantRole = "assistant" | "user"

export interface AssistantConversationTurn {
  id: number
  role: AssistantRole
  text: string
  timestamp: string
  state?: AssistantTurnState
  cards: AssistantCard[]
}

function createTimestamp() {
  return new Date().toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  })
}

function summarizeItems(items: AssistantSummaryItem[]) {
  return items.map(item => `${item.label}: ${item.value}`).join("\n")
}

function normalizeCards(cards: AssistantCard[] = []) {
  return cards.map(card => ({
    ...card,
    summaryItems: card.summaryItems ?? card.pendingAction?.summaryItems ?? card.result?.summaryItems ?? [],
    fields: card.fields ?? [],
    pendingAction: card.pendingAction ?? null,
    result: card.result ?? null
  }))
}

export const useAssistantStore = defineStore("assistant", () => {
  const sessionId = ref("")
  const quickPrompts = ref<string[]>([])
  const turns = ref<AssistantConversationTurn[]>([])
  const activeFieldValues = ref<Record<string, AssistantFieldValue>>({})
  const turnSeed = ref(1)

  const setSessionId = (value: string) => {
    sessionId.value = value
  }

  const setQuickPrompts = (value: string[]) => {
    quickPrompts.value = [...value]
  }

  const appendUserTextTurn = (text: string) => {
    turns.value.push({
      id: turnSeed.value++,
      role: "user",
      text,
      timestamp: createTimestamp(),
      cards: []
    })
  }

  const appendUserSummaryTurn = (items: AssistantSummaryItem[]) => {
    appendUserTextTurn(summarizeItems(items))
  }

  const appendAssistantTurn = (payload: AssistantTurnPayload) => {
    const cards = normalizeCards(payload.cards)
    const fieldForm = cards.find(card => card.type === "field_form")
    const fields = fieldForm?.fields ?? []

    sessionId.value = payload.sessionId
    quickPrompts.value = [...payload.suggestions]
    turns.value.push({
      id: turnSeed.value++,
      role: "assistant",
      text: payload.message,
      timestamp: createTimestamp(),
      state: payload.state,
      cards
    })

    if (payload.state === "collecting" && fields.length > 0) {
      activeFieldValues.value = Object.fromEntries(
        fields.map(field => [field.key, field.value ?? null])
      )
    } else {
      activeFieldValues.value = {}
    }
  }

  const updateActiveFieldValue = (key: string, value: AssistantFieldValue) => {
    activeFieldValues.value = {
      ...activeFieldValues.value,
      [key]: value
    }
  }

  const resetConversation = () => {
    sessionId.value = ""
    quickPrompts.value = []
    turns.value = []
    activeFieldValues.value = {}
    turnSeed.value = 1
  }

  return {
    sessionId,
    quickPrompts,
    turns,
    activeFieldValues,
    setSessionId,
    setQuickPrompts,
    appendUserTextTurn,
    appendUserSummaryTurn,
    appendAssistantTurn,
    updateActiveFieldValue,
    resetConversation
  }
})

export type {
  AssistantActionResult,
  AssistantCard,
  AssistantMissingField,
  AssistantPendingAction,
  AssistantSummaryItem
}
