export type AssistantTurnState = "idle" | "collecting" | "awaiting_confirmation" | "executed" | "error"
export type AssistantCardType =
  | "text"
  | "query_result"
  | "field_form"
  | "confirmation"
  | "execution_result"
  | "clarification"
  | "error"

export type AssistantActionType =
  | "overview.summary.query"
  | "overview.todaySchedule.query"
  | "rooms.search"
  | "rooms.detail"
  | "calendar.query"
  | "reservations.list"
  | "reservations.detail"
  | "reservations.create"
  | "reservations.update"
  | "reservations.cancel"
  | "reservations.review"
  | "admin.reservations.pending"
  | "admin.reservations.approve"
  | "admin.reservations.reject"

export type AssistantFieldInputType = "text" | "textarea" | "number" | "date" | "time" | "select" | "user-select" | "device-requirements"
export interface AssistantDeviceRequirementValue {
  deviceId: number | null
  quantity: number
}
export type AssistantFieldValue = string | number | boolean | number[] | AssistantDeviceRequirementValue[] | null
export type AssistantResultStatus = "success" | "error" | "cancelled"

export interface AssistantFieldOption {
  label: string
  value: string | number
}

export interface AssistantMissingField {
  key: string
  label: string
  inputType: AssistantFieldInputType
  required: boolean
  placeholder?: string
  options?: AssistantFieldOption[]
  value?: AssistantFieldValue
}

export interface AssistantSummaryItem {
  label: string
  value: string
}

export interface AssistantPendingAction {
  executionId: string
  actionType: AssistantActionType
  title: string
  summaryItems: AssistantSummaryItem[]
  confirmRequired: boolean
}

export interface AssistantActionResult {
  status: AssistantResultStatus
  title: string
  summaryItems: AssistantSummaryItem[]
  deepLink?: string
}

export interface AssistantCard {
  type: AssistantCardType
  title?: string
  message?: string
  summaryItems?: AssistantSummaryItem[]
  fields?: AssistantMissingField[]
  pendingAction?: AssistantPendingAction | null
  result?: AssistantActionResult | null
}

export interface AssistantTurnPayload {
  sessionId: string
  turnId: string
  role: "assistant"
  message: string
  cards: AssistantCard[]
  suggestions: string[]
  state: AssistantTurnState
}

export interface AssistantSessionRequestData {}

export interface AssistantMessageRequestData {
  sessionId?: string
  message?: string
  fieldValues?: Record<string, AssistantFieldValue>
}

export type AssistantSessionResponseData = ApiResponseData<AssistantTurnPayload>
export type AssistantMessageResponseData = ApiResponseData<AssistantTurnPayload>
export type AssistantActionResponseData = ApiResponseData<AssistantTurnPayload>
