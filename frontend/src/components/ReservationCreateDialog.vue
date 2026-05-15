<script lang="ts" setup>
import { Plus } from "@element-plus/icons-vue"
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue"
import dayjs from "dayjs"
import {
  confirmAdminEmergencyReservationApi,
  createReservationApi,
  getReservationRecommendationsApi,
  previewAdminEmergencyReservationApi
} from "@/common/apis/reservations"
import type {
  CreateReservationRequestData,
  EmergencyReservationPreviewData,
  EmergencyReservationRequestData,
  ReservationDeviceRequirementInput,
  ReservationRecommendationItem
} from "@/common/apis/reservations/type"
import type { RoomData } from "@/common/apis/rooms/type"
import { searchUsersApi } from "@/common/apis/users"
import type { UserSearchOption } from "@/common/apis/users/type"
import type { ReservationCreateDeviceRequirementDraft, ReservationCreateDraft } from "./reservation-create-dialog"
import { createReservationCreateDraft } from "./reservation-create-dialog"

defineOptions({
  name: "ReservationCreateDialog"
})

interface DeviceOption {
  id: number
  label: string
}

const props = defineProps<{
  modelValue: boolean
  room?: RoomData | null
  rooms?: RoomData[]
  preset?: Partial<ReservationCreateDraft> | null
  emergency?: boolean
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
  submitted: []
}>()

const submitting = ref(false)
const recommending = ref(false)
const recommendations = ref<ReservationRecommendationItem[]>([])
const recommendationError = ref("")
const recommendTimer = ref<number | null>(null)
const participantSearching = ref(false)
const participantOptions = ref<UserSearchOption[]>([])
const participantOptionMap = ref<Record<number, UserSearchOption>>({})
const emergencyReason = ref("")
const allowPreempt = ref(true)
const emergencyPreviewDialogVisible = ref(false)
const emergencyPreview = ref<EmergencyReservationPreviewData | null>(null)
const emergencyPayload = ref<EmergencyReservationRequestData | null>(null)
const timeOptions = createTimeOptions()
const form = reactive<ReservationCreateDraft>(createReservationCreateDraft())

const mergedRooms = computed<RoomData[]>(() => {
  const roomMap = new Map<number, RoomData>()

  for (const room of props.rooms ?? []) {
    roomMap.set(room.id, room)
  }

  if (props.room) {
    roomMap.set(props.room.id, props.room)
  }

  for (const item of recommendations.value) {
    if (!roomMap.has(item.roomId)) {
      roomMap.set(item.roomId, {
        id: item.roomId,
        roomCode: item.roomCode,
        name: item.roomName,
        location: item.location,
        capacity: item.capacity,
        status: "AVAILABLE",
        description: "",
        devices: []
      })
    }
  }

  return [...roomMap.values()].sort((a, b) => a.name.localeCompare(b.name, "zh-CN", { numeric: true, sensitivity: "base" }))
})

const selectableRooms = computed(() => {
  return mergedRooms.value.filter(item => item.status === "AVAILABLE" || item.id === form.roomId)
})

const selectedRoom = computed(() => {
  return mergedRooms.value.find(item => item.id === form.roomId) ?? null
})

const topRecommendation = computed(() => {
  return recommendations.value[0] ?? null
})

const deviceOptions = computed<DeviceOption[]>(() => {
  const deviceMap = new Map<number, DeviceOption>()

  for (const room of mergedRooms.value) {
    for (const device of room.devices ?? []) {
      if (device.status === "DISABLED" || deviceMap.has(device.id)) continue
      deviceMap.set(device.id, {
        id: device.id,
        label: `${device.name} · ${device.deviceCode}`
      })
    }
  }

  return [...deviceMap.values()].sort((a, b) => a.label.localeCompare(b.label, "zh-CN", { numeric: true, sensitivity: "base" }))
})

const deviceRequirements = computed<ReservationDeviceRequirementInput[]>(() => {
  return form.deviceRequirements
    .filter(item => item.deviceId !== null && item.quantity > 0)
    .map(item => ({
      deviceId: item.deviceId as number,
      quantity: item.quantity
    }))
})

const canAddMoreDevices = computed(() => {
  return deviceOptions.value.length > 0 && form.deviceRequirements.length < deviceOptions.value.length
})

const selectedParticipants = computed(() => {
  return form.participantUserIds
    .map((id) => participantOptionMap.value[id] ?? null)
    .filter((item): item is UserSearchOption => item !== null)
})

const attendeeCount = computed(() => form.participantUserIds.length + 1)

const recommendationPayload = computed(() => {
  const range = getTimeRange()
  if (!props.modelValue || !range || attendeeCount.value <= 0) return null

  return {
    title: form.title.trim(),
    attendees: attendeeCount.value,
    startTime: range.start.format("YYYY-MM-DD HH:mm:ss"),
    endTime: range.end.format("YYYY-MM-DD HH:mm:ss"),
    preferredRoomId: form.roomId ?? undefined,
    deviceRequirements: deviceRequirements.value
  }
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      applyPreset()
      void fetchRecommendations()
      return
    }

    clearRecommendTimer()
    recommendations.value = []
    recommendationError.value = ""
  },
  { immediate: true }
)

watch(
  () => [
    props.modelValue,
    form.title,
    serializeParticipantUserIds(form.participantUserIds),
    form.meetingDate,
    form.startClock,
    form.endClock,
    form.roomId,
    serializeDeviceRequirements(form.deviceRequirements)
  ] as const,
  ([visible]) => {
    if (!visible) return
    scheduleRecommendationFetch()
  }
)

watch(
  () => serializeParticipantUserIds(form.participantUserIds),
  () => {
    form.attendees = attendeeCount.value
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  clearRecommendTimer()
})

function applyPreset() {
  const defaults = createReservationCreateDraft()
  const nextForm = {
    ...defaults,
    ...props.preset,
    deviceRequirements: normalizeDeviceRequirementDrafts(props.preset?.deviceRequirements),
    participantUserIds: normalizeParticipantUserIds(props.preset?.participantUserIds),
    roomId: props.preset?.roomId ?? props.room?.id ?? defaults.roomId
  }

  form.roomId = nextForm.roomId
  form.title = nextForm.title
  form.meetingDate = nextForm.meetingDate
  form.startClock = nextForm.startClock
  form.endClock = nextForm.endClock
  form.participantUserIds = [...nextForm.participantUserIds]
  form.attendees = Math.max(1, nextForm.participantUserIds.length + 1)
  form.deviceRequirements = [...nextForm.deviceRequirements]
  form.remark = nextForm.remark
  emergencyReason.value = ""
  allowPreempt.value = true
  emergencyPreview.value = null
  emergencyPreviewDialogVisible.value = false
  emergencyPayload.value = null

  recommendations.value = []
  recommendationError.value = ""
}

function normalizeDeviceRequirementDrafts(source?: ReservationCreateDraft["deviceRequirements"] | null) {
  if (!Array.isArray(source)) return []

  return source
    .filter(item => item && typeof item === "object")
    .map(item => ({
      deviceId: typeof item.deviceId === "number" ? item.deviceId : null,
      quantity: Math.max(1, Number(item.quantity) || 1)
    }))
}

function normalizeParticipantUserIds(source?: ReservationCreateDraft["participantUserIds"] | null) {
  if (!Array.isArray(source)) return []

  return [...new Set(
    source
      .map(item => Number(item))
      .filter(item => Number.isInteger(item) && item > 0)
  )]
}

function clearRecommendTimer() {
  if (recommendTimer.value !== null) {
    window.clearTimeout(recommendTimer.value)
    recommendTimer.value = null
  }
}

function scheduleRecommendationFetch() {
  clearRecommendTimer()
  recommendTimer.value = window.setTimeout(() => {
    void fetchRecommendations()
  }, 260)
}

function serializeDeviceRequirements(source: ReservationCreateDeviceRequirementDraft[]) {
  return source.map(item => `${item.deviceId ?? "none"}:${item.quantity}`).join("|")
}

function serializeParticipantUserIds(source: number[]) {
  return [...source]
    .map(item => Number(item))
    .filter(item => Number.isInteger(item) && item > 0)
    .join("|")
}

function getSelectableDeviceOptions(index: number) {
  const selectedDeviceIds = new Set(
    form.deviceRequirements
      .filter((item, currentIndex) => currentIndex !== index && item.deviceId !== null)
      .map(item => item.deviceId as number)
  )

  return deviceOptions.value.filter(option => !selectedDeviceIds.has(option.id))
}

function addDeviceRequirement() {
  if (!canAddMoreDevices.value) return

  const selectedDeviceIds = new Set(
    form.deviceRequirements
      .filter(item => item.deviceId !== null)
      .map(item => item.deviceId as number)
  )

  const nextDevice = deviceOptions.value.find(option => !selectedDeviceIds.has(option.id)) ?? null

  form.deviceRequirements.push({
    deviceId: nextDevice?.id ?? null,
    quantity: 1
  })
}

function removeDeviceRequirement(index: number) {
  form.deviceRequirements.splice(index, 1)
}

function mergeParticipantOptions(options: UserSearchOption[]) {
  if (options.length === 0) return

  participantOptionMap.value = {
    ...participantOptionMap.value,
    ...Object.fromEntries(options.map(item => [item.id, item]))
  }
}

async function searchParticipantUsers(keyword: string) {
  const trimmedKeyword = keyword.trim()
  if (!trimmedKeyword) {
    participantOptions.value = selectedParticipants.value
    return
  }

  participantSearching.value = true
  try {
    const response = await searchUsersApi({
      keyword: trimmedKeyword,
      limit: 10
    })
    participantOptions.value = response.data ?? []
    mergeParticipantOptions(participantOptions.value)
  } catch {
    participantOptions.value = selectedParticipants.value
  } finally {
    participantSearching.value = false
  }
}

async function fetchRecommendations() {
  const payload = recommendationPayload.value
  if (!payload) {
    recommendations.value = []
    recommendationError.value = ""
    return
  }

  recommending.value = true
  recommendationError.value = ""

  try {
    const response = await getReservationRecommendationsApi(payload)
    recommendations.value = response.data.recommendations ?? []
  } catch {
    recommendations.value = []
    recommendationError.value = "推荐服务暂时不可用"
  } finally {
    recommending.value = false
  }
}

function selectRecommendation(item: ReservationRecommendationItem) {
  form.roomId = item.roomId
}

function selectTopRecommendation() {
  if (!topRecommendation.value) {
    ElMessage.warning("当前没有可用会议室")
    return
  }

  selectRecommendation(topRecommendation.value)
}

function getTimeRange() {
  const start = dayjs(`${form.meetingDate} ${form.startClock}`)
  const end = dayjs(`${form.meetingDate} ${form.endClock}`)
  if (!start.isValid() || !end.isValid() || !end.isAfter(start)) return null
  return { start, end }
}

function closeDialog() {
  emit("update:modelValue", false)
}

async function submitReservation() {
  const title = form.title.trim()
  const remark = form.remark.trim()
  const room = selectedRoom.value
  const range = getTimeRange()

  if (!title) {
    ElMessage.warning("请输入会议主题")
    return
  }
  if (!room) {
    ElMessage.warning("请选择会议室")
    return
  }
  if (room.status !== "AVAILABLE") {
    ElMessage.warning("当前会议室不可预约")
    return
  }
  if (!range) {
    ElMessage.warning("结束时间必须晚于开始时间")
    return
  }
  if (attendeeCount.value <= 0) {
    ElMessage.warning("参会人数需要大于 0")
    return
  }
  if (attendeeCount.value > room.capacity) {
    ElMessage.warning("参会人数不能超过会议室容量")
    return
  }

  submitting.value = true
  try {
    const payload: CreateReservationRequestData = {
      roomId: room.id,
      title,
      meetingDate: form.meetingDate,
      startClock: form.startClock,
      endClock: form.endClock,
      attendees: attendeeCount.value,
      remark: remark || undefined
    }

    if (form.participantUserIds.length > 0) {
      payload.participantUserIds = [...form.participantUserIds]
    }

    if (deviceRequirements.value.length > 0) {
      payload.deviceRequirements = deviceRequirements.value
    }

    if (props.emergency) {
      const reason = emergencyReason.value.trim()
      if (!reason) {
        ElMessage.warning("请输入紧急原因")
        return
      }
      const emergencyRequest: EmergencyReservationRequestData = {
        ...payload,
        allowPreempt: allowPreempt.value,
        emergencyReason: reason
      }
      const response = await previewAdminEmergencyReservationApi(emergencyRequest)
      emergencyPayload.value = emergencyRequest
      emergencyPreview.value = response.data
      if (!response.data.canExecute) {
        ElMessage.warning(response.data.message || "当前无法执行抢占调配")
        return
      }
      emergencyPreviewDialogVisible.value = true
      return
    }

    await createReservationApi(payload)
    ElMessage.success("预约已提交")
    emit("update:modelValue", false)
    emit("submitted")
  } finally {
    submitting.value = false
  }
}

async function confirmEmergencyReservation() {
  if (!emergencyPayload.value) {
    ElMessage.warning("请先预览抢占调配方案")
    return
  }
  submitting.value = true
  try {
    await confirmAdminEmergencyReservationApi(emergencyPayload.value)
    ElMessage.success("紧急会议已创建")
    emergencyPreviewDialogVisible.value = false
    emit("update:modelValue", false)
    emit("submitted")
  } finally {
    submitting.value = false
  }
}

function closeEmergencyPreviewDialog() {
  emergencyPreviewDialogVisible.value = false
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

function formatRoomLabel(room: RoomData) {
  return `${room.name} (${room.roomCode})`
}

function formatWasteRate(wasteRate: number) {
  return `${Math.max(0, Math.round(wasteRate * 100))}%`
}

function getScoreTone(score: number) {
  if (score >= 90) return "is-excellent"
  if (score >= 70) return "is-good"
  return "is-normal"
}

function isSelectedRecommendation(item: ReservationRecommendationItem) {
  return item.roomId === form.roomId
}

defineExpose({
  form,
  recommendations,
  emergencyReason,
  allowPreempt,
  emergencyPreviewDialogVisible,
  selectRecommendation,
  selectTopRecommendation,
  searchParticipantUsers,
  submitReservation,
  confirmEmergencyReservation
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="min(1120px, calc(100vw - 24px))"
    class="reservation-workbench-dialog"
    destroy-on-close
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <p class="dialog-kicker">Reservation Studio</p>
          <h3>{{ emergency ? "新建紧急会议" : "新建预约" }}</h3>
        </div>
      </div>
    </template>

    <div class="reservation-workbench">
      <section class="workbench-panel workbench-panel--form">
        <div class="panel-shell">
          <div class="panel-title-row">
            <p class="panel-kicker">预约信息</p>
          </div>

          <el-form label-position="top" class="reservation-form">
            <el-form-item label="主题" required>
              <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="例如：项目周会" />
            </el-form-item>

            <div class="form-grid">
              <el-form-item label="会议室" required>
                <el-select v-model="form.roomId" placeholder="请选择会议室">
                  <el-option
                    v-for="item in selectableRooms"
                    :key="item.id"
                    :label="formatRoomLabel(item)"
                    :value="item.id"
                  >
                    <div class="room-option">
                      <span>{{ item.name }}</span>
                      <span>{{ item.location }} · 容量 {{ item.capacity }}</span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </div>

            <el-form-item label="参会人">
              <div class="participant-builder">
                <div class="participant-builder__canvas">
                  <el-select
                    v-model="form.participantUserIds"
                    class="participant-builder__select"
                    multiple
                    filterable
                    remote
                    reserve-keyword
                    collapse-tags
                    collapse-tags-tooltip
                    :remote-method="searchParticipantUsers"
                    :loading="participantSearching"
                    placeholder="输入姓名搜索参会人"
                  >
                    <el-option
                      v-for="user in participantOptions"
                      :key="user.id"
                      :label="user.displayName"
                      :value="user.id"
                    />
                  </el-select>
                </div>
              </div>
            </el-form-item>

            <el-form-item label="设备需求">
              <div class="device-builder">
                <div class="device-builder__canvas" :class="{ 'is-empty': form.deviceRequirements.length === 0 }">
                  <el-button
                    class="device-builder__add"
                    circle
                    type="primary"
                    plain
                    :disabled="!canAddMoreDevices"
                    @click="addDeviceRequirement"
                  >
                    <el-icon><Plus /></el-icon>
                  </el-button>

                  <div v-if="form.deviceRequirements.length === 0" class="device-builder__empty">设备为空</div>

                  <div v-else class="device-builder__list">
                    <div
                      v-for="(item, index) in form.deviceRequirements"
                      :key="`${item.deviceId ?? 'draft'}-${index}`"
                      class="device-builder__row"
                    >
                      <el-select v-model="item.deviceId" placeholder="选择设备">
                        <el-option
                          v-for="device in getSelectableDeviceOptions(index)"
                          :key="device.id"
                          :label="device.label"
                          :value="device.id"
                        />
                      </el-select>

                      <el-input-number v-model="item.quantity" :min="1" :max="99" controls-position="right" />

                      <el-button text @click="removeDeviceRequirement(index)">删除</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </el-form-item>

            <div class="form-grid">
              <el-form-item label="开始时间" required>
                <div class="time-field">
                  <el-date-picker
                    v-model="form.meetingDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                  />
                  <el-select v-model="form.startClock" placeholder="开始时间">
                    <el-option v-for="time in timeOptions" :key="time" :label="time" :value="time" />
                  </el-select>
                </div>
              </el-form-item>

              <el-form-item label="结束时间" required>
                <div class="time-field">
                  <el-date-picker
                    v-model="form.meetingDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                  />
                  <el-select v-model="form.endClock" placeholder="结束时间">
                    <el-option v-for="time in timeOptions" :key="time" :label="time" :value="time" />
                  </el-select>
                </div>
              </el-form-item>
            </div>

            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="3"
                maxlength="255"
                show-word-limit
                placeholder="补充会议背景或特殊说明"
              />
            </el-form-item>

            <template v-if="emergency">
              <div class="emergency-fields">
                <el-form-item label="是否紧急会议">
                  <el-switch :model-value="true" disabled active-text="是" />
                </el-form-item>
                <el-form-item label="允许抢占已有预约">
                  <el-switch v-model="allowPreempt" active-text="允许" inactive-text="不允许" />
                </el-form-item>
                <el-form-item label="紧急原因" required>
                  <el-input
                    v-model="emergencyReason"
                    type="textarea"
                    :rows="3"
                    maxlength="255"
                    show-word-limit
                    placeholder="说明为什么需要抢占已有预约"
                  />
                </el-form-item>
              </div>
            </template>
          </el-form>
        </div>
      </section>

      <section class="workbench-panel workbench-panel--recommend">
        <div class="panel-shell recommendation-shell" v-loading="recommending">
          <div class="panel-title-row panel-title-row--recommend">
            <p class="panel-kicker">智能推荐</p>
            <div v-if="topRecommendation" class="top-badge">最优 {{ topRecommendation.score }} 分</div>
          </div>

          <div v-if="recommendations.length > 0" class="recommendation-list">
            <article
              v-for="item in recommendations"
              :key="item.roomId"
              class="recommendation-card"
              :class="{ 'is-selected': isSelectedRecommendation(item) }"
            >
              <div class="recommendation-score" :class="getScoreTone(item.score)">{{ item.score }}分</div>

              <div class="recommendation-card__body">
                <div class="recommendation-card__headline">
                  <div>
                    <h5>{{ item.roomName }}</h5>
                    <p>{{ item.location }} · 容量 {{ item.capacity }}</p>
                  </div>
                </div>

                <div class="recommendation-card__meta">
                  <span>容量余量 {{ formatWasteRate(item.wasteRate) }}</span>
                  <span>设备 {{ item.matchedDeviceTypeCount }}/{{ item.requiredDeviceTypeCount }}</span>
                </div>

                <div class="recommendation-tag-row">
                  <span v-for="tag in item.tags" :key="tag" class="recommendation-tag">{{ tag }}</span>
                </div>

                <div class="recommendation-card__actions">
                  <el-button
                    :type="isSelectedRecommendation(item) ? 'primary' : 'default'"
                    @click="selectRecommendation(item)"
                  >
                    {{ isSelectedRecommendation(item) ? "已选择" : "选择此会议室" }}
                  </el-button>
                  <span class="recommendation-card__code">{{ item.roomCode }}</span>
                </div>
              </div>
            </article>
          </div>

          <div v-else-if="recommendationError" class="recommendation-empty">
            <p>{{ recommendationError }}</p>
          </div>

          <button class="best-choice-button" :disabled="!topRecommendation" @click="selectTopRecommendation">
            一键选择最优<span v-if="topRecommendation">（{{ topRecommendation.roomCode }}）</span>
          </button>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReservation">
          {{ emergency ? "预览抢占调配" : "提交预约" }}
        </el-button>
      </div>
    </template>
  </el-dialog>

  <el-dialog
    v-model="emergencyPreviewDialogVisible"
    width="min(920px, calc(100vw - 24px))"
    class="emergency-preview-dialog"
    destroy-on-close
    align-center
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <p class="dialog-kicker">Emergency Plan</p>
          <h3>抢占调配确认</h3>
        </div>
      </div>
    </template>

    <div v-if="emergencyPreview" class="emergency-preview">
      <section class="emergency-preview__section">
        <h4>紧急会议</h4>
        <div class="preview-grid">
          <div><span>主题</span><strong>{{ emergencyPreview.emergencySummary.title }}</strong></div>
          <div><span>会议室</span><strong>{{ emergencyPreview.emergencySummary.roomName }}</strong></div>
          <div><span>时间</span><strong>{{ emergencyPreview.emergencySummary.startTime }} - {{ emergencyPreview.emergencySummary.endTime }}</strong></div>
          <div><span>原因</span><strong>{{ emergencyPreview.emergencySummary.emergencyReason }}</strong></div>
        </div>
      </section>

      <section class="emergency-preview__section">
        <h4>被影响预约</h4>
        <div v-if="emergencyPreview.conflicts.length > 0" class="preview-list">
          <article v-for="item in emergencyPreview.conflicts" :key="item.reservationId" class="preview-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.roomName }} · {{ item.startTime }} - {{ item.endTime }} · {{ item.organizerName }}</span>
          </article>
        </div>
        <div v-else class="preview-empty">没有冲突预约。</div>
      </section>

      <section class="emergency-preview__section">
        <h4>调配动作</h4>
        <div v-if="emergencyPreview.actions.length > 0" class="preview-list">
          <article v-for="item in emergencyPreview.actions" :key="item.reservationId" class="preview-item">
            <strong>{{ item.actionType === "MOVE_ROOM" ? "换会议室" : "取消预约" }}</strong>
            <span>
              {{ item.reservationTitle }}：
              <template v-if="item.actionType === 'MOVE_ROOM'">{{ item.sourceRoomName }} 调整到 {{ item.targetRoomName }}</template>
              <template v-else>{{ item.reason }}</template>
            </span>
          </article>
        </div>
        <div v-else class="preview-empty">无需调配。</div>
      </section>

      <section class="emergency-preview__section">
        <h4>通知对象</h4>
        <div v-if="emergencyPreview.notifications.length > 0" class="notification-list">
          <span v-for="item in emergencyPreview.notifications" :key="`${item.userId}-${item.reservationId ?? 'emergency'}`">
            {{ item.displayName || item.userId }} · {{ item.reason }}
          </span>
        </div>
        <div v-else class="preview-empty">暂无通知对象。</div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeEmergencyPreviewDialog">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmEmergencyReservation">确认抢占调配</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
:deep(.reservation-workbench-dialog) {
  --surface: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.94));
  --panel: rgba(255, 255, 255, 0.92);
  --text-main: #182c42;
  --success: #22a06b;
  --warn: #c88428;
  --neutral: #6d7e92;

  .el-dialog {
    overflow: hidden;
    border: 1px solid rgba(210, 223, 240, 0.9);
    border-radius: 28px;
    background: var(--surface);
    box-shadow: 0 28px 70px rgba(59, 91, 134, 0.2);
  }

  .el-dialog__header {
    padding: 24px 24px 10px;
  }

  .el-dialog__body {
    padding: 0 24px 20px;
  }

  .el-dialog__footer {
    padding: 0 24px 24px;
  }

  .el-form-item {
    margin-bottom: 18px;
  }

  .el-input-number,
  .el-select,
  .el-date-editor {
    width: 100%;
  }

  .el-input__wrapper,
  .el-textarea__inner,
  .el-select__wrapper,
  .el-date-editor.el-input__wrapper,
  .el-input-number__wrapper {
    border-radius: 16px;
    box-shadow: 0 0 0 1px rgba(209, 220, 236, 0.92) inset;
  }
}

.dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.dialog-kicker,
.panel-kicker {
  margin: 0;
  color: #7d90a8;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.dialog-header h3 {
  margin: 8px 0 0;
  font-size: 28px;
  color: #17304b;
}

.reservation-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.92fr);
  gap: 18px;
}

.panel-shell {
  height: 100%;
  padding: 20px;
  border: 1px solid rgba(208, 221, 239, 0.92);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 40px rgba(102, 134, 176, 0.1);
}

.workbench-panel--form .panel-shell {
  background:
    radial-gradient(circle at top left, rgba(82, 150, 255, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.95));
}

.recommendation-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background:
    radial-gradient(circle at top right, rgba(78, 172, 255, 0.16), transparent 24%),
    linear-gradient(180deg, rgba(252, 254, 255, 0.98), rgba(247, 250, 254, 0.96));
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.device-builder {
  width: 100%;
}

.participant-builder {
  width: 100%;
}

.participant-builder__canvas {
  width: 100%;
  padding: 14px;
  border: 1px solid rgba(210, 221, 236, 0.92);
  border-radius: 18px;
  background: rgba(248, 251, 255, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.participant-builder__select {
  width: 100%;
}

.device-builder__canvas {
  position: relative;
  width: 100%;
  min-height: 122px;
  padding: 52px 14px 14px;
  border: 1px solid rgba(210, 221, 236, 0.92);
  border-radius: 18px;
  background: rgba(248, 251, 255, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.device-builder__canvas.is-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 14px 12px;
}

.device-builder__add {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  min-width: 18px;
  height: 18px;
  padding: 0;
}

.device-builder__canvas.is-empty .device-builder__add {
  position: static;
  order: 2;
  flex: none;
}

.device-builder__canvas.is-empty .device-builder__empty {
  order: 1;
}

.device-builder__add :deep(.el-icon) {
  font-size: 9px;
}

.device-builder__empty {
  color: #8a98aa;
  font-size: 13px;
  line-height: 1;
}

.device-builder__list {
  display: grid;
  gap: 12px;
}

.device-builder__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 144px 64px;
  gap: 12px;
  align-items: center;
}

.time-field {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.95fr);
  gap: 10px;
}

.emergency-fields {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(224, 131, 89, 0.28);
  border-radius: 18px;
  background: rgba(255, 247, 241, 0.72);
}

.emergency-preview {
  display: grid;
  gap: 14px;
}

.emergency-preview__section {
  padding: 14px;
  border: 1px solid rgba(210, 221, 236, 0.92);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
}

.emergency-preview__section h4 {
  margin: 0 0 10px;
  color: #17324e;
  font-size: 16px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.preview-grid div,
.preview-item {
  display: grid;
  gap: 4px;
  padding: 10px;
  border-radius: 14px;
  background: rgba(245, 249, 255, 0.92);
}

.preview-grid span,
.preview-item span,
.preview-empty,
.notification-list span {
  color: #6e8094;
  font-size: 12px;
}

.preview-list,
.notification-list {
  display: grid;
  gap: 8px;
}

.notification-list {
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.notification-list span {
  padding: 8px 10px;
  border-radius: 999px;
  background: rgba(82, 150, 255, 0.1);
}

.room-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.room-option span:first-child {
  color: #1d344e;
}

.room-option span:last-child {
  color: #7a8a9d;
  font-size: 12px;
}

.recommendation-list {
  display: grid;
  gap: 14px;
  max-height: 460px;
  padding-top: 4px;
  padding-right: 4px;
  overflow: auto;
}

.recommendation-card {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(210, 221, 236, 0.92);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.95);
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.recommendation-card.is-selected {
  border-color: rgba(76, 149, 255, 0.95);
  box-shadow: 0 14px 30px rgba(61, 122, 214, 0.18);
  transform: translateY(-2px);
}

.recommendation-score {
  align-self: flex-start;
  padding: 7px 9px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 700;
}

.recommendation-score.is-excellent {
  background: rgba(235, 247, 230, 0.94);
  color: var(--success);
}

.recommendation-score.is-good {
  background: rgba(255, 243, 223, 0.94);
  color: var(--warn);
}

.recommendation-score.is-normal {
  background: rgba(240, 243, 247, 0.95);
  color: var(--neutral);
}

.recommendation-card__body {
  display: grid;
  gap: 10px;
}

.recommendation-card__headline h5 {
  margin: 0;
  color: #17324e;
  font-size: 17px;
}

.recommendation-card__headline p,
.recommendation-card__meta,
.recommendation-card__code {
  margin: 4px 0 0;
  color: #6e8094;
  font-size: 12px;
}

.recommendation-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.recommendation-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recommendation-tag {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(231, 242, 224, 0.88);
  color: #517f2d;
  font-size: 11px;
}

.recommendation-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.top-badge {
  padding: 10px 14px;
  border: 1px solid rgba(186, 208, 236, 0.95);
  border-radius: 999px;
  background: rgba(247, 251, 255, 0.92);
  color: #305376;
  font-size: 13px;
  white-space: nowrap;
}

.recommendation-empty {
  display: grid;
  place-items: center;
  min-height: 280px;
  border: 1px dashed rgba(192, 207, 229, 0.95);
  border-radius: 24px;
  background: rgba(247, 251, 255, 0.84);
  color: #72839a;
  text-align: center;
}

.best-choice-button {
  width: 100%;
  padding: 14px 18px;
  border: 1px solid rgba(135, 186, 255, 0.92);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(233, 244, 255, 0.98), rgba(219, 238, 255, 0.94));
  color: #2872e8;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.best-choice-button:enabled:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(65, 126, 214, 0.14);
}

.best-choice-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .reservation-workbench {
    grid-template-columns: minmax(0, 1fr);
  }

  .recommendation-card__actions,
  .panel-title-row {
    flex-direction: column;
    align-items: stretch;
  }

  .top-badge {
    white-space: normal;
  }
}

@media (max-width: 640px) {
  .form-grid,
  .time-field,
  .device-builder__row {
    grid-template-columns: minmax(0, 1fr);
  }

  :deep(.reservation-workbench-dialog) {
    .el-dialog__header,
    .el-dialog__body,
    .el-dialog__footer {
      padding-left: 16px;
      padding-right: 16px;
    }
  }

  .panel-shell {
    padding: 16px;
    border-radius: 20px;
  }

  .recommendation-card__headline h5 {
    font-size: 18px;
  }
}
</style>
