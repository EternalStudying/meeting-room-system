<script lang="ts" setup>
import { computed, nextTick, onMounted, ref } from "vue"
import { ElMessage } from "element-plus"
import {
  ChatDotRound,
  Delete,
  MagicStick,
  Plus,
  Promotion,
  RefreshRight
} from "@element-plus/icons-vue"
import { storeToRefs } from "pinia"
import {
  cancelAssistantActionApi,
  confirmAssistantActionApi,
  createAssistantSessionApi,
  sendAssistantMessageApi
} from "@/common/apis/assistant"
import { searchUsersApi } from "@/common/apis/users"
import type {
  AssistantCard,
  AssistantDeviceRequirementValue,
  AssistantFieldOption,
  AssistantFieldValue,
  AssistantMissingField,
  AssistantPendingAction,
  AssistantSummaryItem
} from "@/common/apis/assistant/type"
import type { UserSearchOption } from "@/common/apis/users/type"
import { useAssistantStore } from "@/pinia/stores/assistant"
import type { AssistantConversationTurn } from "@/pinia/stores/assistant"

const RUNNING_HINT = "AI 正在整理任务上下文与页面操作信息..."
const FALLBACK_SUGGESTIONS = ["帮我创建一个预约", "取消我明天下午的预约", "查看我本周的预约"]

const loading = ref(false)
const sending = ref(false)
const executingAction = ref(false)
const inputMessage = ref("")
const messageListRef = ref<HTMLElement>()
const collectUserSearching = ref<Record<string, boolean>>({})
const collectUserOptions = ref<Record<string, UserSearchOption[]>>({})
const collectUserOptionMap = ref<Record<number, UserSearchOption>>({})
const assistantStore = useAssistantStore()
const { sessionId, quickPrompts, turns, activeFieldValues } = storeToRefs(assistantStore)

const latestAssistantTurn = computed(() => {
  return [...turns.value].reverse().find(turn => turn.role === "assistant") ?? null
})

const activeCollectTurn = computed(() => {
  const latestTurn = latestAssistantTurn.value
  const card = latestTurn?.cards.find(item => item.type === "field_form") ?? null
  return latestTurn && card ? { id: latestTurn.id, missingFields: card.fields ?? [] } : null
})

const activeConfirmTurn = computed(() => {
  const latestTurn = latestAssistantTurn.value
  const card = latestTurn?.cards.find(item => item.type === "confirmation" && item.pendingAction) ?? null
  return latestTurn && card?.pendingAction ? { id: latestTurn.id, pendingAction: card.pendingAction } : null
})

const sendDisabled = computed(() => {
  return !inputMessage.value.trim() || sending.value || executingAction.value
})

const timeOptions = createTimeOptions()

onMounted(() => {
  if (turns.value.length > 0) {
    void scrollToBottom()
    return
  }
  void startNewSession()
})

function resetCollectUserState() {
  collectUserSearching.value = {}
  collectUserOptions.value = {}
  collectUserOptionMap.value = {}
}

async function startNewSession() {
  loading.value = true
  inputMessage.value = ""
  assistantStore.resetConversation()
  resetCollectUserState()

  try {
    const { data } = await createAssistantSessionApi()
    assistantStore.appendAssistantTurn(data)
  } catch {
    appendAssistantError("我没能初始化助手会话。请稍后重试，或刷新页面后再试。")
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

async function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message || sending.value || executingAction.value) return

  assistantStore.appendUserTextTurn(message)
  inputMessage.value = ""
  sending.value = true
  await scrollToBottom()

  try {
    const { data } = await sendAssistantMessageApi({
      sessionId: sessionId.value || undefined,
      message
    })
    assistantStore.appendAssistantTurn(data)
  } catch {
    appendAssistantError("我没能继续处理这条消息。你可以换个说法再试，或稍后重试。")
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function sendPrompt(prompt: string) {
  inputMessage.value = prompt
  await sendMessage()
}

function handleComposerKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent)) return
  if (event.key !== "Enter" || event.isComposing) return
  if (event.ctrlKey || event.shiftKey || event.altKey || event.metaKey) return
  event.preventDefault()
  void sendMessage()
}

function updateCollectFieldValue(key: string, value: AssistantFieldValue) {
  assistantStore.updateActiveFieldValue(key, value)
}

function getCollectFieldValue(key: string) {
  return activeFieldValues.value[key] ?? null
}

function getCollectScalarFieldValue(key: string) {
  const value = getCollectFieldValue(key)
  return typeof value === "string" || typeof value === "number" ? value : null
}

function getCollectFieldLabel(field: AssistantMissingField) {
  if (field.key === "participantUserIds") return "参会人"
  return field.label
}

function getCollectUserSelectValue(key: string) {
  const value = getCollectFieldValue(key)
  return Array.isArray(value) ? value.map(item => Number(item)).filter(item => Number.isInteger(item) && item > 0) : []
}

function getCollectUserFieldOptions(field: AssistantMissingField) {
  return (field.options ?? [])
    .map(optionToUserSearchOption)
    .filter((item): item is UserSearchOption => item !== null)
}

function optionToUserSearchOption(option: AssistantFieldOption): UserSearchOption | null {
  const id = Number(option.value)
  if (!Number.isInteger(id) || id <= 0) return null
  return {
    id,
    username: String(option.value),
    displayName: option.label
  }
}

function updateCollectUserSelectValue(field: AssistantMissingField, value: unknown) {
  const selectedIds = Array.isArray(value)
    ? value.map(item => Number(item)).filter(item => Number.isInteger(item) && item > 0)
    : []
  const selectedIdSet = new Set(selectedIds)
  mergeCollectUserOptions(getCollectUserOptions(field).filter(option => selectedIdSet.has(option.id)))
  updateCollectFieldValue(field.key, selectedIds)
}

function getCollectDeviceRequirementRows(field: AssistantMissingField) {
  const rows = getCollectDeviceRequirementDraftRows(field.key)
  return rows.length > 0 ? rows : [createEmptyDeviceRequirement()]
}

function updateCollectDeviceRequirementValue(key: string, index: number, patch: Partial<AssistantDeviceRequirementValue>) {
  const rows = getCollectDeviceRequirementDraftRows(key)
  if (rows.length === 0) {
    rows.push(createEmptyDeviceRequirement())
  }
  while (rows.length <= index) {
    rows.push(createEmptyDeviceRequirement())
  }
  rows[index] = {
    ...rows[index],
    ...patch
  }
  updateCollectFieldValue(key, rows)
}

function addCollectDeviceRequirement(key: string) {
  const rows = getCollectDeviceRequirementDraftRows(key)
  const nextRows = rows.length > 0 ? rows : [createEmptyDeviceRequirement()]
  updateCollectFieldValue(key, [
    ...nextRows,
    createEmptyDeviceRequirement()
  ])
}

function removeCollectDeviceRequirement(key: string, index: number) {
  const rows = getCollectDeviceRequirementDraftRows(key)
  const nextRows = (rows.length > 0 ? rows : [createEmptyDeviceRequirement()]).filter((_, itemIndex) => itemIndex !== index)
  updateCollectFieldValue(key, nextRows)
}

function mergeCollectUserOptions(options: UserSearchOption[]) {
  if (options.length === 0) return

  collectUserOptionMap.value = {
    ...collectUserOptionMap.value,
    ...Object.fromEntries(options.map(item => [item.id, item]))
  }
}

function getCollectUserOptions(field: AssistantMissingField) {
  const fieldOptions = getCollectUserFieldOptions(field)
  const selectedOptions = getCollectUserSelectValue(field.key)
    .map(id => collectUserOptionMap.value[id] ?? fieldOptions.find(option => option.id === id) ?? null)
    .filter((item): item is UserSearchOption => item !== null)

  const queriedOptions = collectUserOptions.value[field.key] ?? []
  const mergedMap = new Map<number, UserSearchOption>()

  for (const option of [...fieldOptions, ...selectedOptions, ...queriedOptions]) {
    mergedMap.set(option.id, option)
  }

  return [...mergedMap.values()]
}

function isCollectUserSearching(key: string) {
  return collectUserSearching.value[key] ?? false
}

async function searchCollectUsers(field: AssistantMissingField, keyword: string) {
  const trimmedKeyword = keyword.trim()

  if (!trimmedKeyword) {
    collectUserOptions.value = {
      ...collectUserOptions.value,
      [field.key]: getCollectUserOptions(field)
    }
    return
  }

  collectUserSearching.value = {
    ...collectUserSearching.value,
    [field.key]: true
  }

  try {
    const response = await searchUsersApi({
      keyword: trimmedKeyword,
      limit: 10
    })
    const options = response.data ?? []
    mergeCollectUserOptions(options)
    collectUserOptions.value = {
      ...collectUserOptions.value,
      [field.key]: options
    }
  } catch {
    collectUserOptions.value = {
      ...collectUserOptions.value,
      [field.key]: getCollectUserOptions(field)
    }
  } finally {
    collectUserSearching.value = {
      ...collectUserSearching.value,
      [field.key]: false
    }
  }
}

async function submitActiveCollectTurn() {
  const turn = activeCollectTurn.value
  if (!turn?.missingFields?.length || sending.value || executingAction.value) return
  const fields = visibleCollectFields(turn.missingFields)

  const missingRequired = fields.find((field) => {
    if (!field.required) return false
    const normalizedValue = normalizeFieldValue(field, activeFieldValues.value[field.key] ?? null)
    return normalizedValue === null || normalizedValue === "" || (Array.isArray(normalizedValue) && normalizedValue.length === 0)
  })

  if (missingRequired) {
    ElMessage.warning(`请补充${missingRequired.label}`)
    return
  }

  assistantStore.appendUserSummaryTurn(buildSummaryItems(turn.missingFields))
  sending.value = true
  await scrollToBottom()

  try {
    const { data } = await sendAssistantMessageApi({
      sessionId: sessionId.value || undefined,
      fieldValues: buildFieldPayload(turn.missingFields)
    })
    assistantStore.appendAssistantTurn(data)
  } catch {
    appendAssistantError("我没能继续处理这些参数。请检查填写内容后再试。")
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function confirmPendingAction(pendingAction: AssistantPendingAction | null = activeConfirmTurn.value?.pendingAction ?? null) {
  if (!pendingAction || sending.value || executingAction.value) return

  assistantStore.appendUserTextTurn(`确认执行：${pendingAction.title}`)
  executingAction.value = true
  await scrollToBottom()

  try {
    const { data } = await confirmAssistantActionApi(pendingAction.executionId)
    assistantStore.appendAssistantTurn(data)
  } catch {
    appendAssistantError("我没能执行这次操作。请稍后重试，或重新发起操作。")
  } finally {
    executingAction.value = false
    await scrollToBottom()
  }
}

async function cancelPendingAction(pendingAction: AssistantPendingAction | null = activeConfirmTurn.value?.pendingAction ?? null) {
  if (!pendingAction || sending.value || executingAction.value) return

  assistantStore.appendUserTextTurn(`取消操作：${pendingAction.title}`)
  executingAction.value = true
  await scrollToBottom()

  try {
    const { data } = await cancelAssistantActionApi(pendingAction.executionId)
    assistantStore.appendAssistantTurn(data)
  } catch {
    appendAssistantError("我没能取消这次操作。你可以稍后重试，或重新开始。")
  } finally {
    executingAction.value = false
    await scrollToBottom()
  }
}

function buildSummaryItems(fields: AssistantMissingField[]): AssistantSummaryItem[] {
  const items = visibleCollectFields(fields)
    .map((field) => {
      const rawValue = activeFieldValues.value[field.key] ?? null
      const normalizedValue = normalizeFieldValue(field, rawValue)
      if (normalizedValue === null || normalizedValue === "" || (Array.isArray(normalizedValue) && normalizedValue.length === 0)) return null
      return { label: getCollectFieldLabel(field), value: formatFieldValue(field, normalizedValue) }
    })
    .filter((item): item is AssistantSummaryItem => item !== null)

  if (shouldDeriveAttendees(fields)) {
    items.push({ label: "参会人数", value: `${deriveAttendeesFromParticipants()}人` })
  }
  return items
}

function buildFieldPayload(fields: AssistantMissingField[]) {
  const payload = Object.fromEntries(visibleCollectFields(fields).map(field => [field.key, normalizeFieldValue(field, activeFieldValues.value[field.key] ?? null)]))
  if (shouldDeriveAttendees(fields)) {
    payload.attendees = deriveAttendeesFromParticipants()
  }
  return payload
}

function normalizeFieldValue(field: AssistantMissingField, value: AssistantFieldValue) {
  if (value === undefined || value === "") return null
  if (field.inputType === "number") return value === null ? null : Number(value)
  if (field.inputType === "user-select") {
    if (!Array.isArray(value)) return null

    const normalized = [...new Set(
      value
        .map(item => Number(item))
        .filter(item => Number.isInteger(item) && item > 0)
    )]

    return normalized.length > 0 ? normalized : null
  }
  if (field.inputType === "device-requirements") {
    return normalizeDeviceRequirementValue(value)
  }
  return value
}

function formatFieldValue(field: AssistantMissingField, value: AssistantFieldValue) {
  if (field.inputType === "select") {
    return field.options?.find(option => option.value === value)?.label ?? String(value)
  }
  if (field.inputType === "user-select" && Array.isArray(value)) {
    return value
      .map((item) => collectUserOptionMap.value[Number(item)]?.displayName ?? `用户#${item}`)
      .join("、")
  }
  if (field.inputType === "device-requirements" && Array.isArray(value)) {
    return value
      .map((item) => {
        if (typeof item !== "object" || item === null || Array.isArray(item)) return null
        const deviceId = Number(item.deviceId)
        const option = field.options?.find(option => Number(option.value) === deviceId)
        return option ? `${option.label} x${item.quantity}` : `设备#${deviceId} x${item.quantity}`
      })
      .filter((item): item is string => item !== null)
      .join("、")
  }
  return String(value)
}

function visibleCollectFields(fields: AssistantMissingField[]) {
  return fields.filter(field => field.key !== "attendees")
}

function shouldDeriveAttendees(fields: AssistantMissingField[]) {
  return fields.some(field => field.key === "attendees" || field.key === "participantUserIds")
}

function deriveAttendeesFromParticipants() {
  return getCollectUserSelectValue("participantUserIds").length + 1
}

function createEmptyDeviceRequirement(): AssistantDeviceRequirementValue {
  return { deviceId: null, quantity: 1 }
}

function getCollectDeviceRequirementDraftRows(key: string): AssistantDeviceRequirementValue[] {
  return normalizeDeviceRequirementRows(getCollectFieldValue(key))
}

function normalizeDeviceRequirementRows(value: AssistantFieldValue): AssistantDeviceRequirementValue[] {
  if (!Array.isArray(value)) return []

  return value
    .map((item): AssistantDeviceRequirementValue | null => {
      if (typeof item !== "object" || item === null || Array.isArray(item)) return null
      const rawDeviceId = Number(item.deviceId)
      const rawQuantity = Number(item.quantity)
      return {
        deviceId: Number.isInteger(rawDeviceId) && rawDeviceId > 0 ? rawDeviceId : null,
        quantity: Number.isInteger(rawQuantity) && rawQuantity > 0 ? rawQuantity : 1
      }
    })
    .filter((item): item is AssistantDeviceRequirementValue => item !== null)
}

function normalizeDeviceRequirementValue(value: AssistantFieldValue): AssistantDeviceRequirementValue[] {
  return normalizeDeviceRequirementRows(value).filter(item => item.deviceId !== null)
}

function appendAssistantError(text: string) {
  assistantStore.appendAssistantTurn({
    sessionId: sessionId.value || "assistant-local",
    turnId: `local-${Date.now()}`,
    role: "assistant",
    state: "error",
    message: text,
    suggestions: quickPrompts.value.length > 0 ? quickPrompts.value : FALLBACK_SUGGESTIONS,
    cards: [
      {
        type: "error",
        title: "处理失败",
        message: text,
        result: { status: "error", title: "处理失败", summaryItems: [] }
      }
    ]
  })
}

function visibleTurnCards(turn: AssistantConversationTurn) {
  return turn.cards.filter((card) => {
    if (card.type === "field_form") return turn.id === activeCollectTurn.value?.id && visibleCollectFields(card.fields ?? []).length > 0
    if (card.type === "confirmation") return turn.id === activeConfirmTurn.value?.id && Boolean(card.pendingAction)
    if (card.type === "clarification") return turn.id === latestAssistantTurn.value?.id && Boolean((card.message ?? "").trim())
    return false
  })
}

function cardSummaryItems(card: AssistantCard) {
  return card.summaryItems ?? card.pendingAction?.summaryItems ?? card.result?.summaryItems ?? []
}

async function scrollToBottom() {
  await nextTick()
  const target = messageListRef.value
  if (!target) return
  target.scrollTop = target.scrollHeight
}

function createTimeOptions() {
  const options: string[] = []
  for (let hour = 8; hour <= 18; hour += 1) {
    for (const minute of [0, 30]) {
      if (hour === 18 && minute > 0) continue
      options.push(`${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`)
    }
  }
  return options
}

defineExpose({
  inputMessage,
  quickPrompts,
  turns,
  sessionId,
  sendDisabled,
  sendMessage,
  startNewSession,
  activeCollectTurn,
  activeConfirmTurn,
  updateCollectFieldValue,
  submitActiveCollectTurn,
  confirmPendingAction,
  cancelPendingAction
})
</script>

<template>
  <div class="assistant-page">
    <section class="assistant-topbar" aria-hidden="true">
      <div class="hero-copy">
        <p class="eyebrow">Future Lab Interface</p>
        <h1 class="page-hero-title">AI 助手</h1>
      </div>
    </section>

    <section class="lab-shell">
      <div class="conversation-stage">
        <div class="conversation-shell">
          <header class="conversation-head">
            <span class="session-badge">
              <el-icon><ChatDotRound /></el-icon>
              {{ sessionId || "会话启动中" }}
            </span>
            <div class="head-actions">
              <el-button class="ghost-button" :icon="RefreshRight" plain @click="startNewSession">
                新对话
              </el-button>
            </div>
          </header>

          <div class="conversation-grid">
            <div ref="messageListRef" class="message-stream">
              <div v-if="loading && turns.length === 0" class="stream-skeleton">
                <div class="skeleton-line long" />
                <div class="skeleton-line medium" />
                <div class="skeleton-line short" />
              </div>

              <article
                v-for="turn in turns"
                :key="turn.id"
                class="message-card"
                :class="`is-${turn.role}`"
              >
                <div class="message-badge">
                  <el-icon>
                    <component :is="turn.role === 'assistant' ? MagicStick : Promotion" />
                  </el-icon>
                </div>

                <div class="message-content">
                  <div class="message-meta">
                    <strong>{{ turn.role === "assistant" ? "智能任务助手" : "我" }}</strong>
                    <span>{{ turn.timestamp }}</span>
                  </div>

                  <div class="message-bubble">{{ turn.text }}</div>

                  <div
                    v-for="(card, cardIndex) in visibleTurnCards(turn)"
                    :key="`${turn.id}-card-${cardIndex}-${card.type}`"
                    class="assistant-card"
                    :class="`is-${card.type}`"
                  >
                    <template v-if="card.type === 'field_form'">
                      <div class="assistant-card-head">
                        <strong>{{ card.title || "补齐参数" }}</strong>
                        <span>填写后继续推进任务</span>
                      </div>

                      <p v-if="card.message" class="card-message">{{ card.message }}</p>

                      <div class="collect-grid">
                        <div v-for="field in visibleCollectFields(card.fields ?? [])" :key="field.key" class="collect-field">
                          <span class="field-label">{{ getCollectFieldLabel(field) }}<em v-if="field.required">*</em></span>

                          <el-input
                            v-if="field.inputType === 'text'"
                            :model-value="getCollectScalarFieldValue(field.key)"
                            :placeholder="field.placeholder"
                            @update:model-value="updateCollectFieldValue(field.key, $event)"
                          />

                          <el-input
                            v-else-if="field.inputType === 'textarea'"
                            type="textarea"
                            :rows="3"
                            resize="none"
                            :model-value="getCollectScalarFieldValue(field.key)"
                            :placeholder="field.placeholder"
                            @update:model-value="updateCollectFieldValue(field.key, $event)"
                          />

                          <el-input-number
                            v-else-if="field.inputType === 'number'"
                            :min="1"
                            :model-value="Number(getCollectFieldValue(field.key) ?? 1)"
                            @update:model-value="updateCollectFieldValue(field.key, $event ?? null)"
                          />

                          <el-date-picker
                            v-else-if="field.inputType === 'date'"
                            type="date"
                            value-format="YYYY-MM-DD"
                            format="YYYY-MM-DD"
                            :model-value="getCollectScalarFieldValue(field.key)"
                            @update:model-value="updateCollectFieldValue(field.key, $event ?? null)"
                          />

                          <el-select
                            v-else-if="field.inputType === 'time'"
                            :model-value="getCollectScalarFieldValue(field.key)"
                            placeholder="请选择时间"
                            @update:model-value="updateCollectFieldValue(field.key, $event ?? null)"
                          >
                            <el-option v-for="item in timeOptions" :key="item" :label="item" :value="item" />
                          </el-select>

                          <el-select
                            v-else-if="field.inputType === 'select'"
                            :model-value="getCollectScalarFieldValue(field.key)"
                            placeholder="请选择"
                            @update:model-value="updateCollectFieldValue(field.key, $event ?? null)"
                          >
                            <el-option
                              v-for="option in field.options ?? []"
                              :key="option.value"
                              :label="option.label"
                              :value="option.value"
                            />
                          </el-select>

                          <el-select
                            v-else-if="field.inputType === 'user-select'"
                            class="assistant-user-select"
                            multiple
                            filterable
                            remote
                            reserve-keyword
                            collapse-tags
                            collapse-tags-tooltip
                            :model-value="getCollectUserSelectValue(field.key)"
                            :loading="isCollectUserSearching(field.key)"
                            :remote-method="(keyword) => searchCollectUsers(field, keyword)"
                            :placeholder="field.placeholder || '搜索并选择参会人'"
                            @update:model-value="updateCollectUserSelectValue(field, $event ?? [])"
                          >
                            <el-option
                              v-for="option in getCollectUserOptions(field)"
                              :key="option.id"
                              :label="option.displayName"
                              :value="option.id"
                            />
                          </el-select>

                          <div v-else-if="field.inputType === 'device-requirements'" class="device-requirements">
                            <div
                              v-for="(requirement, requirementIndex) in getCollectDeviceRequirementRows(field)"
                              :key="`${field.key}-${requirementIndex}`"
                              class="device-requirement-row"
                            >
                              <el-select
                                class="device-select"
                                :model-value="requirement.deviceId"
                                :placeholder="field.placeholder || '选择设备'"
                                @update:model-value="updateCollectDeviceRequirementValue(field.key, requirementIndex, { deviceId: Number($event) || null })"
                              >
                                <el-option
                                  v-for="option in field.options ?? []"
                                  :key="option.value"
                                  :label="option.label"
                                  :value="option.value"
                                />
                              </el-select>
                              <el-input-number
                                class="device-quantity"
                                :min="1"
                                :model-value="requirement.quantity"
                                @update:model-value="updateCollectDeviceRequirementValue(field.key, requirementIndex, { quantity: Number($event) || 1 })"
                              />
                              <el-button
                                class="device-row-button"
                                :icon="Delete"
                                circle
                                plain
                                :disabled="getCollectDeviceRequirementRows(field).length <= 1"
                                @click="removeCollectDeviceRequirement(field.key, requirementIndex)"
                              />
                            </div>
                            <el-button class="add-device-button" :icon="Plus" plain @click="addCollectDeviceRequirement(field.key)">
                              添加设备
                            </el-button>
                          </div>
                        </div>
                      </div>

                      <div v-if="turn.id === activeCollectTurn?.id" class="panel-foot">
                        <span class="panel-hint">补齐这些信息后，助手会继续追问或进入确认阶段。</span>
                        <el-button class="panel-primary-button" type="primary" :loading="sending" @click="submitActiveCollectTurn">
                          继续处理
                        </el-button>
                      </div>
                    </template>

                    <template v-else-if="card.type === 'clarification'">
                      <div class="assistant-card-head">
                        <strong>{{ card.title || "需要补充信息" }}</strong>
                        <span>回复后继续处理</span>
                      </div>
                      <p class="card-message">{{ card.message }}</p>
                    </template>

                    <template v-else-if="card.type === 'confirmation' && card.pendingAction">
                      <div class="assistant-card-head">
                        <strong>{{ card.pendingAction.title }}</strong>
                        <span>确认后将直接调用业务接口</span>
                      </div>

                      <p v-if="card.message" class="card-message">{{ card.message }}</p>

                      <div class="summary-list">
                        <div v-for="item in cardSummaryItems(card)" :key="`${turn.id}-${cardIndex}-${item.label}`" class="summary-item">
                          <span>{{ item.label }}</span>
                          <strong>{{ item.value }}</strong>
                        </div>
                      </div>

                      <div v-if="turn.id === activeConfirmTurn?.id" class="panel-foot">
                        <el-button class="panel-secondary-button" :disabled="executingAction" @click="cancelPendingAction(card.pendingAction)">
                          取消本次操作
                        </el-button>
                        <el-button class="panel-primary-button" type="primary" :loading="executingAction" @click="confirmPendingAction(card.pendingAction)">
                          确认执行
                        </el-button>
                      </div>
                    </template>
                  </div>

                </div>
              </article>

              <article v-if="sending || executingAction" class="message-card is-pending">
                <div class="message-badge">
                  <el-icon><MagicStick /></el-icon>
                </div>
                <div class="message-content">
                  <div class="message-meta">
                    <strong>智能任务助手</strong>
                    <span>处理中</span>
                  </div>
                  <div class="message-bubble pending-bubble">
                    <span>{{ RUNNING_HINT }}</span>
                    <span class="loading-dots" aria-hidden="true">
                      <i /><i /><i />
                    </span>
                  </div>
                </div>
              </article>

              <section
                v-if="quickPrompts.length > 0 && !sending && !executingAction"
                class="prompt-wall"
              >
                <div class="prompt-wall-copy">
                  <h3>有什么我能帮你的吗？</h3>
                </div>
                <div class="prompt-wall-list">
                  <button
                    v-for="prompt in quickPrompts"
                    :key="prompt"
                    type="button"
                    class="task-chip"
                    @click="sendPrompt(prompt)"
                  >
                    <span>{{ prompt }}</span>
                  </button>
                </div>
              </section>
            </div>

            <div class="composer-dock">
              <div class="composer-input-shell">
                <el-input
                  v-model="inputMessage"
                  class="composer-input"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  placeholder="例如：帮我创建一个预约；或取消我明天下午的预约。"
                  @keydown="handleComposerKeydown"
                />
                <button
                  type="button"
                  class="send-icon-button"
                  :disabled="sendDisabled"
                  @click="sendMessage"
                >
                  <el-icon><Promotion /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.assistant-page {
  --shell-line: rgba(190, 211, 235, 0.92);
  --shell-line-strong: rgba(139, 181, 231, 0.5);
  --shell-shadow: rgba(121, 150, 191, 0.14);
  --ink-main: #15304d;
  --ink-subtle: #617895;
  --lab-accent: #67b7ff;
  --lab-accent-soft: #66efd2;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  padding: 18px 20px 20px;
  background:
    radial-gradient(circle at top left, rgba(90, 152, 255, 0.16), transparent 22%),
    radial-gradient(circle at top right, rgba(103, 239, 210, 0.12), transparent 18%),
    linear-gradient(180deg, #eef4fb 0%, #e6edf6 100%);
  color: var(--ink-main);
}

.assistant-topbar {
  display: none;
}

.lab-shell {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.conversation-stage {
  min-width: 0;
  min-height: 0;
}

.conversation-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 20px;
  border-radius: 28px;
  border: 1px solid var(--shell-line);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.96));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92), 0 20px 42px var(--shell-shadow);
}

.conversation-head,
.head-actions,
.assistant-panel-head,
.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.conversation-grid {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.session-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(132, 175, 231, 0.32);
  background: rgba(243, 248, 255, 0.96);
  color: #54708f;
  font-size: 12px;
}

.ghost-button,
.panel-primary-button,
.panel-secondary-button {
  border-radius: 999px;
}

.message-stream {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  height: 100%;
  padding: 2px 6px 4px 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.stream-skeleton,
.summary-list {
  display: grid;
  gap: 10px;
}

.stream-skeleton {
  padding-top: 12px;
}

.skeleton-line {
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(73, 108, 145, 0.34), rgba(131, 181, 238, 0.42), rgba(73, 108, 145, 0.34));
  background-size: 220% 100%;
  animation: shimmer 1.1s linear infinite;
}

.skeleton-line.long {
  width: 76%;
}

.skeleton-line.medium {
  width: 58%;
}

.skeleton-line.short {
  width: 42%;
}

.message-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  animation: message-rise 0.24s ease;
}

.message-card.is-user {
  grid-template-columns: minmax(0, 1fr) 42px;
}

.message-card.is-user .message-badge {
  order: 2;
  background: linear-gradient(135deg, rgba(76, 139, 255, 0.94), rgba(93, 235, 208, 0.84));
  color: #fff;
}

.message-card.is-user .message-content {
  order: 1;
  justify-items: end;
}

.message-card.is-user .message-meta {
  justify-content: flex-end;
}

.message-card.is-user .message-bubble {
  border-bottom-right-radius: 12px;
  background: linear-gradient(135deg, rgba(65, 124, 246, 0.98), rgba(79, 211, 211, 0.92));
  color: #fff;
}

.message-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(241, 248, 255, 0.96), rgba(221, 238, 255, 0.92));
  color: #4f8fe3;
  font-size: 18px;
}

.message-content,
.collect-field {
  display: grid;
  gap: 8px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--ink-subtle);
}

.message-meta strong {
  color: #17324a;
  font-weight: 600;
}

.message-bubble {
  max-width: min(100%, 720px);
  padding: 13px 14px;
  border-radius: 20px;
  border: 1px solid rgba(115, 171, 255, 0.14);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(243, 248, 255, 0.96));
  color: #28455f;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.assistant-panel {
  display: grid;
  gap: 12px;
  margin-top: 4px;
  padding: 14px;
  border-radius: 20px;
  border: 1px solid rgba(122, 171, 237, 0.18);
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.98), rgba(240, 246, 255, 0.94));
}

.assistant-card {
  display: grid;
  gap: 12px;
  max-width: min(100%, 760px);
  margin-top: 4px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(122, 171, 237, 0.2);
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.98), rgba(240, 246, 255, 0.94));
}

.assistant-card.is-confirmation {
  border-color: rgba(83, 153, 255, 0.34);
}

.assistant-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-card-head strong {
  font-size: 15px;
  color: #16304e;
}

.assistant-card-head span,
.card-message {
  color: #678099;
}

.assistant-card-head span {
  font-size: 12px;
}

.card-message {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.assistant-panel-head strong {
  font-size: 15px;
  color: #16304e;
}

.assistant-panel-head span,
.panel-hint,
.composer-status,
.composer-tip,
.field-label,
.summary-item span {
  color: #678099;
}

.assistant-panel-head span,
.panel-hint,
.field-label,
.composer-status,
.composer-tip {
  font-size: 12px;
}

.field-label em {
  color: #ea6f7f;
  font-style: normal;
}

.collect-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.assistant-user-select {
  width: 100%;
}

.device-requirements {
  display: grid;
  gap: 10px;
}

.device-requirement-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px 36px;
  gap: 8px;
  align-items: center;
}

.device-select,
.device-quantity {
  width: 100%;
}

.device-row-button {
  width: 36px;
  height: 36px;
}

.add-device-button {
  justify-self: start;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(178, 208, 241, 0.28);
}

.summary-item strong {
  color: #17324a;
  text-align: right;
}

.pending-bubble {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border-style: dashed;
}

.loading-dots {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.loading-dots i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(76, 139, 255, 0.92), rgba(93, 235, 208, 0.9));
  animation: dot-pulse 1.1s ease-in-out infinite;
}

.loading-dots i:nth-child(2) {
  animation-delay: 0.14s;
}

.loading-dots i:nth-child(3) {
  animation-delay: 0.28s;
}

.composer-dock {
  display: block;
}

.prompt-wall {
  display: grid;
  justify-items: center;
  gap: 18px;
  margin-top: auto;
  padding: 24px 20px 28px;
}

.prompt-wall-copy {
  text-align: center;
}

.prompt-wall-copy h3 {
  margin: 0;
  font-size: 22px;
  color: #102744;
}

.prompt-wall-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  width: min(960px, 100%);
}

.task-chip {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 10px 14px;
  border-radius: 18px;
  border: 1px solid rgba(178, 208, 241, 0.38);
  background: rgba(255, 255, 255, 0.72);
  color: #28455f;
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
}

.task-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(112, 166, 255, 0.42);
  background: rgba(245, 249, 255, 0.96);
}

.composer-input-shell {
  position: relative;
}

.composer-input :deep(.el-textarea__inner) {
  min-height: 104px;
  max-height: 104px;
  padding-right: 58px;
  border-radius: 18px;
  background: rgba(249, 252, 255, 0.98);
}

.send-icon-button {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 10px 20px rgba(17, 24, 39, 0.22);
  transition: transform 0.18s ease, opacity 0.18s ease, box-shadow 0.18s ease;
}

.send-icon-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.send-icon-button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
  box-shadow: none;
}

@keyframes message-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shimmer {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -20% 0;
  }
}

@keyframes dot-pulse {
  0%, 80%, 100% {
    opacity: 0.32;
    transform: translateY(0) scale(0.88);
  }

  40% {
    opacity: 1;
    transform: translateY(-2px) scale(1);
  }
}

@media screen and (max-width: 900px) {
  .assistant-page {
    padding: 14px;
  }

  .conversation-head,
  .assistant-panel-head,
  .panel-foot {
    flex-direction: column;
    align-items: stretch;
  }

  .collect-grid {
    grid-template-columns: 1fr;
  }

  .message-card,
  .message-card.is-user {
    grid-template-columns: 1fr;
  }

  .message-card.is-user .message-badge,
  .message-card.is-user .message-content {
    order: initial;
  }

  .message-card.is-user .message-meta {
    justify-content: flex-start;
  }

  .task-chip {
    width: auto;
    max-width: 100%;
  }

  .prompt-wall {
    padding: 12px 0 20px;
    min-height: auto;
  }

  .prompt-wall-copy h3 {
    font-size: 18px;
  }

  .prompt-wall-list {
    width: 100%;
    justify-content: flex-start;
  }

  .ghost-button,
  .panel-primary-button,
  .panel-secondary-button {
    width: 100%;
  }
}
</style>
