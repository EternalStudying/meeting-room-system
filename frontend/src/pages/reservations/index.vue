<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { useRoute } from "vue-router"
import dayjs from "dayjs"
import "dayjs/locale/zh-cn"
import * as echarts from "echarts"
import { ElMessage } from "element-plus"
import { Calendar, Clock, CloseBold, EditPen, Monitor, OfficeBuilding, Tickets, User } from "@element-plus/icons-vue"
import {
  cancelMyReservationApi,
  getMyEndedReservationsPageApi,
  getMyReservationDetailApi,
  getMyReservationRoomOptionsApi,
  getMyReservationsApi,
  submitMyReservationReviewApi,
  updateMyReservationApi
} from "@/common/apis/reservations"
import type {
  MyReservationItem,
  MyReservationPageData,
  MyReservationRoomOption,
  MyReservationScope,
  ReservationDeviceRequirementInput,
  ReservationReviewData,
  ReservationRole,
  ReservationStatus
} from "@/common/apis/reservations/type"
import { getRoomListApi } from "@/common/apis/rooms"
import type { RoomData } from "@/common/apis/rooms/type"
import { searchUsersApi } from "@/common/apis/users"
import type { UserSearchOption } from "@/common/apis/users/type"

dayjs.locale("zh-cn")

type StatusFilter = ReservationStatus | "all"

interface MetricCard {
  label: string
  value: string
  icon: typeof Calendar
}

interface MeetingGroup {
  key: string
  label: string
  meetings: MyReservationItem[]
}

interface DeviceOption {
  id: number
  label: string
}

interface EditDeviceRequirementDraft {
  deviceId: number | null
  quantity: number
}

interface EditMeetingForm {
  title: string
  roomId: number | null
  meetingDate: string
  startClock: string
  endClock: string
  participantUserIds: number[]
  deviceRequirements: EditDeviceRequirementDraft[]
  remark: string
}

interface ReviewForm {
  rating: number
  content: string
}

const roomOptions = ref<RoomData[]>([])
const reservationList = ref<MyReservationItem[]>([])
const recentSummaryList = ref<MyReservationItem[]>([])
const route = useRoute()

const selectedScope = ref<MyReservationScope>("all")
const selectedStatus = ref<StatusFilter>("all")
const selectedDate = ref(dayjs().format("YYYY-MM-DD"))
const endedPageNum = ref(1)
const endedPageSize = 8
const recentSummaryPageSize = 5
const endedTotal = ref(0)
const endedPageTransitionKey = ref(0)

const selectedReservationId = ref<number | null>(null)
const detailDialogVisible = ref(false)
const editDialogVisible = ref(false)
const cancelDialogVisible = ref(false)
const reviewDialogVisible = ref(false)
const cancelReasonInput = ref("")
const statusChartRef = ref<HTMLDivElement | null>(null)
const dataLoading = ref(false)

const editForm = ref<EditMeetingForm>(createEmptyEditForm())
const reviewForm = ref<ReviewForm>({ rating: 0, content: "" })
const editParticipantSearching = ref(false)
const editParticipantOptions = ref<UserSearchOption[]>([])
const editParticipantOptionMap = ref<Record<number, UserSearchOption>>({})
let hasHydratedEndedPage = false

const cancelReasonOptions = [
  "时间冲突，需要重新安排",
  "需求取消，会议无需继续",
  "关键参会人无法到场",
  "议题变更，改期处理"
]

const scopeOptions: Array<{ label: string, value: MyReservationScope }> = [
  { label: "全部", value: "all" },
  { label: "我发起的", value: "organizer" },
  { label: "我参与的", value: "participant" }
]

const statusOptions: Array<{ label: string, value: StatusFilter }> = [
  { label: "全部状态", value: "all" },
  { label: "待审核", value: "PENDING" },
  { label: "进行中", value: "ACTIVE" },
  { label: "已结束", value: "ENDED" },
  { label: "已取消", value: "CANCELLED" },
  { label: "已驳回", value: "REJECTED" },
  { label: "异常", value: "EXCEPTION" }
]

const filteredReservations = computed(() => reservationList.value.filter((item) => {
  const hitScope = selectedScope.value === "all"
    || (selectedScope.value === "organizer" && item.role === "ORGANIZER")
    || (selectedScope.value === "participant" && item.role === "PARTICIPANT")
  const hitStatus = selectedStatus.value === "all" || item.status === selectedStatus.value
  return hitScope && hitStatus
}))

const sortedReservations = computed(() => {
  return [...filteredReservations.value].sort((a, b) => dayjs(a.startTime).valueOf() - dayjs(b.startTime).valueOf())
})

const isEndedMode = computed(() => selectedStatus.value === "ENDED")
const showSignalRail = computed(() => !isEndedMode.value)
const endedReservations = computed(() => {
  return [...filteredReservations.value]
    .filter(item => item.status === "ENDED")
    .sort((a, b) => {
      const dateDiff = dayjs(b.startTime).startOf("day").valueOf() - dayjs(a.startTime).startOf("day").valueOf()
      if (dateDiff !== 0) return dateDiff
      return dayjs(b.endTime).valueOf() - dayjs(a.endTime).valueOf()
    })
})
const currentMonthReservations = computed(() => {
  const monthStart = dayjs().startOf("month")
  const monthEnd = dayjs().endOf("month")
  const now = dayjs()
  return sortedReservations.value.filter((item) => {
    const start = dayjs(item.startTime)
    return start.isAfter(monthStart.subtract(1, "second"))
      && start.isBefore(monthEnd.add(1, "second"))
      && dayjs(item.endTime).isAfter(now)
  })
})
const todayReservations = computed(() => sortedReservations.value.filter(item => dayjs(item.startTime).format("YYYY-MM-DD") === dayjs().format("YYYY-MM-DD")))
const selectedDateReservations = computed(() => currentMonthReservations.value.filter(item => dayjs(item.startTime).format("YYYY-MM-DD") === selectedDate.value))
const upcomingReservations = computed(() => sortedReservations.value.filter(item => item.status === "ACTIVE" && dayjs(item.startTime).isAfter(dayjs())))
const nextMeeting = computed(() => upcomingReservations.value[0] ?? null)
const organizerCount = computed(() => filteredReservations.value.filter(item => item.role === "ORGANIZER").length)
const participantCount = computed(() => filteredReservations.value.filter(item => item.role === "PARTICIPANT").length)
const recentSummaries = computed(() => [...recentSummaryList.value].sort((a, b) => dayjs(b.endTime).valueOf() - dayjs(a.endTime).valueOf()).slice(0, 5))
const meetingGroups = computed<MeetingGroup[]>(() => {
  const grouped = new Map<string, MyReservationItem[]>()
  currentMonthReservations.value.forEach((item) => {
    const key = dayjs(item.startTime).format("YYYY-MM-DD")
    grouped.set(key, [...(grouped.get(key) ?? []), item])
  })
  return [...grouped.entries()].map(([key, meetings]) => ({ key, label: dayjs(key).format("M月D日 dddd"), meetings }))
})

const reservationPool = computed(() => {
  const pool = new Map<number, MyReservationItem>()
  recentSummaryList.value.forEach(item => pool.set(item.id, item))
  reservationList.value.forEach(item => pool.set(item.id, item))
  return [...pool.values()]
})
const selectedReservation = computed(() => reservationPool.value.find(item => item.id === selectedReservationId.value) ?? null)
const selectedRoomLocation = computed(() => selectedReservation.value?.roomLocation ?? "--")
const selectedRoomCode = computed(() => selectedReservation.value?.roomCode ?? "--")
const selectedRoomCapacityText = computed(() => selectedReservation.value?.roomCapacity ? `${selectedReservation.value.roomCapacity} 人` : "--")
const selectedRoomDescription = computed(() => selectedReservation.value?.roomDescription?.trim() || "")
const selectedDateText = computed(() => selectedReservation.value ? dayjs(selectedReservation.value.startTime).format("YYYY年M月D日 dddd") : "")
const selectedDurationText = computed(() => {
  if (!selectedReservation.value) return ""
  const minutes = dayjs(selectedReservation.value.endTime).diff(dayjs(selectedReservation.value.startTime), "minute")
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  if (hours > 0 && remainMinutes > 0) return `${hours} 小时 ${remainMinutes} 分钟`
  if (hours > 0) return `${hours} 小时`
  return `${remainMinutes} 分钟`
})
const selectedDeviceCount = computed(() => selectedReservation.value?.devices.reduce((sum, item) => sum + item.quantity, 0) ?? 0)
const selectedCancelReasonText = computed(() => selectedReservation.value?.cancelReason?.trim() || "")
const canEditSelectedReservation = computed(() => selectedReservation.value?.status === "ACTIVE" && selectedReservation.value.canEdit)
const canCancelSelectedReservation = computed(() => selectedReservation.value?.status === "ACTIVE" && selectedReservation.value.canCancel)
const canReviewSelectedReservation = computed(() => selectedReservation.value?.status === "ENDED" && !selectedReservation.value?.reviewed)
const selectedReview = computed<ReservationReviewData | null>(() => selectedReservation.value?.myReview ?? null)
const detailFooterText = computed(() => {
  if (!selectedReservation.value) return ""
  if (selectedReservation.value.status === "PENDING") return "当前会议待管理员审核"
  if (selectedReservation.value.status === "REJECTED") return "当前会议已驳回"
  if (selectedReservation.value.status === "EXCEPTION") return "当前会议已标记异常"
  if (selectedReservation.value.status === "CANCELLED") return "当前会议已取消"
  if (selectedReservation.value.status === "ENDED") return "当前会议已结束"
  return "当前以参会人身份查看"
})

const metrics = computed<MetricCard[]>(() => [
  { label: "今日会议", value: padNumber(todayReservations.value.length), icon: Calendar },
  { label: "我发起的", value: padNumber(organizerCount.value), icon: Tickets },
  { label: "我参与的", value: padNumber(participantCount.value), icon: User },
  { label: "待开始", value: padNumber(upcomingReservations.value.length), icon: Clock },
  { label: "下一场", value: nextMeeting.value ? dayjs(nextMeeting.value.startTime).format("HH:mm") : "--", icon: OfficeBuilding }
])

const statusBreakdown = computed(() => [
  { label: "待审核", value: filteredReservations.value.filter(item => item.status === "PENDING").length, tone: "pending", color: "#e2a54b" },
  { label: "进行中", value: filteredReservations.value.filter(item => item.status === "ACTIVE").length, tone: "active", color: "#59c3b0" },
  { label: "已结束", value: filteredReservations.value.filter(item => item.status === "ENDED").length, tone: "ended", color: "#5f81ff" },
  { label: "已取消", value: filteredReservations.value.filter(item => item.status === "CANCELLED").length, tone: "cancelled", color: "#f28dac" },
  { label: "已驳回", value: filteredReservations.value.filter(item => item.status === "REJECTED").length, tone: "rejected", color: "#d66a82" },
  { label: "异常", value: filteredReservations.value.filter(item => item.status === "EXCEPTION").length, tone: "exception", color: "#df8b45" }
])

const roomSummary = computed(() => {
  const counter = new Map<number, number>()
  const roomSnapshot = new Map<number, { name: string, location: string, capacity: number }>()
  filteredReservations.value.forEach((item) => {
    counter.set(item.roomId, (counter.get(item.roomId) ?? 0) + 1)
    roomSnapshot.set(item.roomId, { name: item.roomName, location: item.roomLocation, capacity: item.roomCapacity })
  })
  return [...counter.entries()].map(([roomId, count]) => ({
    id: roomId,
    name: roomSnapshot.get(roomId)?.name ?? `会议室 ${roomId}`,
    location: roomSnapshot.get(roomId)?.location ?? "--",
    capacity: roomSnapshot.get(roomId)?.capacity ?? 0,
    count
  })).sort((a, b) => b.count - a.count).slice(0, 5)
})

const timeOptions = createTimeOptions()

const editDeviceOptions = computed<DeviceOption[]>(() => {
  const deviceMap = new Map<number, DeviceOption>()
  for (const room of roomOptions.value) {
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

const canAddMoreEditDevices = computed(() => {
  return editDeviceOptions.value.length > 0 && editForm.value.deviceRequirements.length < editDeviceOptions.value.length
})

const selectedEditParticipants = computed(() => {
  return editForm.value.participantUserIds
    .map(id => editParticipantOptionMap.value[id] ?? null)
    .filter((item): item is UserSearchOption => item !== null)
})

watch(currentMonthReservations, (list) => {
  if (isEndedMode.value) return
  const hasCurrent = list.some(item => dayjs(item.startTime).format("YYYY-MM-DD") === selectedDate.value)
  if (!hasCurrent) {
    selectedDate.value = list[0] ? dayjs(list[0].startTime).format("YYYY-MM-DD") : dayjs().format("YYYY-MM-DD")
  }
}, { immediate: true })

watch(reservationPool, (list) => {
  if (selectedReservationId.value !== null && !list.some(item => item.id === selectedReservationId.value)) {
    selectedReservationId.value = null
    detailDialogVisible.value = false
  }
})

watch([selectedScope, selectedStatus], ([scope, status], [prevScope, prevStatus]) => {
  if (status === "ENDED" && (scope !== prevScope || status !== prevStatus) && endedPageNum.value !== 1) {
    endedPageNum.value = 1
    return
  }
  if (status !== "ENDED" && endedPageNum.value !== 1) {
    endedPageNum.value = 1
  }
  fetchReservations()
})

watch(selectedScope, () => {
  fetchRecentSummaries()
})

watch(endedPageNum, (page, previousPage) => {
  if (page !== previousPage && isEndedMode.value) {
    fetchReservations()
  }
})

watch(statusBreakdown, async () => {
  await nextTick()
  renderStatusChart()
}, { deep: true })

watch(showSignalRail, async (value) => {
  if (value) {
    await nextTick()
    renderStatusChart()
  }
})

watch(
  () => route.query,
  () => {
    void openReservationFromRoute()
  }
)

onMounted(async () => {
  await Promise.all([fetchRoomOptions(), fetchReservations(), fetchRecentSummaries()])
  await openReservationFromRoute()
  renderStatusChart()
  window.addEventListener("resize", handleStatusChartResize)
})

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleStatusChartResize)
  disposeStatusChart()
})

async function fetchRoomOptions() {
  try {
    const response = await getRoomListApi({ currentPage: 1, size: 100, status: "AVAILABLE" })
    roomOptions.value = normalizeRoomOptions(response.data.list)
  } catch {
    try {
      const response = await getMyReservationRoomOptionsApi()
      roomOptions.value = normalizeRoomOptions(response.data)
    } catch {
      roomOptions.value = []
      ElMessage.error("会议室选项加载失败")
    }
  }
}

async function fetchReservations() {
  dataLoading.value = true
  try {
    if (isEndedMode.value) {
      await fetchEndedReservations()
      return
    }
    await fetchRegularReservations()
  } catch {
    applyReservationResponse(isEndedMode.value ? { list: [], total: 0 } : [])
    ElMessage.error("预约列表加载失败")
  } finally {
    dataLoading.value = false
  }
}

async function fetchRegularReservations() {
  const params: Record<string, unknown> = {
    startDate: dayjs().subtract(180, "day").startOf("day").format("YYYY-MM-DD HH:mm:ss"),
    endDate: dayjs().add(180, "day").endOf("day").format("YYYY-MM-DD HH:mm:ss"),
    scope: selectedScope.value,
    futureOnly: true
  }
  if (selectedStatus.value !== "all") params.status = selectedStatus.value
  const response = await getMyReservationsApi(params as any)
  applyReservationResponse(response.data)
}

async function fetchEndedReservations() {
  const response = await getMyEndedReservationsPageApi({
    scope: selectedScope.value,
    pageNum: endedPageNum.value,
    pageSize: endedPageSize
  })
  applyReservationResponse(response.data)
  if (hasHydratedEndedPage) {
    endedPageTransitionKey.value += 1
  } else {
    hasHydratedEndedPage = true
  }
}

async function fetchRecentSummaries() {
  try {
    const response = await getMyEndedReservationsPageApi({
      scope: selectedScope.value,
      pageNum: 1,
      pageSize: recentSummaryPageSize
    })
    recentSummaryList.value = sortRecentSummaryMeetings(normalizeReservationList(response.data.list ?? []))
  } catch {
    recentSummaryList.value = []
  }
}

async function openReservationFromRoute() {
  const reservationId = getRouteReservationId()
  if (reservationId === null) return

  const routeStatus = getRouteReservationStatus()
  if (routeStatus !== null && selectedStatus.value !== routeStatus) {
    selectedStatus.value = routeStatus
    await fetchReservations()
  }

  let reservation = reservationPool.value.find(item => item.id === reservationId) ?? null
  if (!reservation) {
    reservation = await loadReservationDetail(reservationId)
  }
  if (reservation) {
    openDetail(reservation)
  }
}

async function loadReservationDetail(id: number) {
  try {
    const response = await getMyReservationDetailApi(id)
    const reservation = normalizeReservationList([response.data])[0]
    reservationList.value = [
      reservation,
      ...reservationList.value.filter(item => item.id !== id)
    ]
    return reservation
  } catch {
    return null
  }
}

function getRouteReservationId() {
  const value = firstRouteQueryValue(route.query.reservationId)
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

function getRouteReservationStatus(): ReservationStatus | null {
  const value = firstRouteQueryValue(route.query.status)
  if (!value) return null
  return isReservationStatus(value) ? value : null
}

function firstRouteQueryValue(value: unknown) {
  if (Array.isArray(value)) return value[0] == null ? "" : String(value[0])
  return value == null ? "" : String(value)
}

function isReservationStatus(value: string): value is ReservationStatus {
  return ["PENDING", "ACTIVE", "ENDED", "CANCELLED", "REJECTED", "EXCEPTION"].includes(value)
}

function openDetail(reservation: MyReservationItem) {
  selectedDate.value = dayjs(reservation.startTime).format("YYYY-MM-DD")
  selectedReservationId.value = reservation.id
  detailDialogVisible.value = true
}

function openEditDialog() {
  if (!selectedReservation.value || !canEditSelectedReservation.value) return
  editForm.value = createEditForm(selectedReservation.value)
  mergeEditParticipantOptions(selectedReservation.value.participants ?? [])
  editParticipantOptions.value = selectedEditParticipants.value
  editDialogVisible.value = true
}

async function submitEdit() {
  if (!selectedReservation.value) return
  const title = editForm.value.title.trim()
  if (!title) {
    ElMessage.warning("请输入会议标题")
    return
  }
  if (!editForm.value.roomId) {
    ElMessage.warning("请选择会议室")
    return
  }
  const room = roomOptions.value.find(item => item.id === editForm.value.roomId)
  if (!room) {
    ElMessage.warning("会议室不存在")
    return
  }
  const startTime = buildDateTime(editForm.value.meetingDate, editForm.value.startClock)
  const endTime = buildDateTime(editForm.value.meetingDate, editForm.value.endClock)
  if (!dayjs(endTime).isAfter(dayjs(startTime))) {
    ElMessage.warning("结束时间必须晚于开始时间")
    return
  }
  const participantUserIds = normalizeParticipantUserIds(editForm.value.participantUserIds)
  const attendees = participantUserIds.length + 1
  if (attendees > room.capacity) {
    ElMessage.warning("参会人数不能超过会议室容量")
    return
  }
  try {
    await updateMyReservationApi(selectedReservation.value.id, {
      title,
      roomId: room.id,
      meetingDate: editForm.value.meetingDate,
      startClock: editForm.value.startClock,
      endClock: editForm.value.endClock,
      attendees,
      participantUserIds,
      deviceRequirements: normalizeDeviceRequirements(editForm.value.deviceRequirements),
      remark: editForm.value.remark.trim()
    })
    await fetchReservations()
    editDialogVisible.value = false
    ElMessage.success("会议信息已更新")
  } catch {
    ElMessage.error("会议更新失败，请稍后重试")
  }
}

function openCancelDialog() {
  if (!canCancelSelectedReservation.value) return
  cancelReasonInput.value = ""
  cancelDialogVisible.value = true
}

function fillCancelReason(reason: string) {
  cancelReasonInput.value = reason
}

async function submitCancel() {
  if (!selectedReservation.value) return
  const reason = cancelReasonInput.value.trim()
  if (!reason) {
    ElMessage.warning("请填写取消原因")
    return
  }
  try {
    await cancelMyReservationApi(selectedReservation.value.id, { cancelReason: reason })
    await fetchReservations()
    cancelDialogVisible.value = false
    detailDialogVisible.value = false
    ElMessage.success("会议已取消")
  } catch {
    ElMessage.error("会议取消失败，请稍后重试")
  }
}

function openReviewDialog() {
  if (!selectedReservation.value || !canReviewSelectedReservation.value) return
  reviewForm.value = { rating: 0, content: "" }
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (!selectedReservation.value) return
  if (reviewForm.value.rating <= 0) {
    ElMessage.warning("请先评分")
    return
  }
  const content = reviewForm.value.content.trim()
  const response = await submitMyReservationReviewApi(selectedReservation.value.id, {
    rating: reviewForm.value.rating,
    content
  })
  reservationList.value = reservationList.value.map((item) => item.id === selectedReservation.value?.id ? {
    ...item,
    reviewed: response.data.reviewed,
    myReview: response.data.myReview
  } : item)
  reviewDialogVisible.value = false
  ElMessage.success("评价已提交")
}

function padNumber(value: number) {
  return String(value).padStart(2, "0")
}

function formatStatus(status: ReservationStatus) {
  return {
    PENDING: "待审核",
    ACTIVE: "进行中",
    ENDED: "已结束",
    CANCELLED: "已取消",
    REJECTED: "已驳回",
    EXCEPTION: "异常"
  }[status]
}

function formatRole(role: ReservationRole) {
  return role === "ORGANIZER" ? "我发起的" : "我参与的"
}

function formatTimeRange(startTime: string, endTime: string) {
  return `${dayjs(startTime).format("HH:mm")} - ${dayjs(endTime).format("HH:mm")}`
}

function formatDateTimeRange(startTime: string, endTime: string) {
  const start = dayjs(startTime)
  const end = dayjs(endTime)
  const endFormat = start.isSame(end, "day") ? "HH:mm" : "M月D日 HH:mm"
  return `${start.format("M月D日 HH:mm")} - ${end.format(endFormat)}`
}

function formatMeetingDate(dateTime: string) {
  return dayjs(dateTime).format("YYYY年M月D日")
}

function formatParticipantNames(reservation: MyReservationItem | null | undefined) {
  const names = reservation?.participants?.map(item => item.displayName || item.username).filter(Boolean) ?? []
  return names.length > 0 ? names.join("、") : "暂无额外参会人"
}

function createEmptyEditForm(): EditMeetingForm {
  return {
    title: "",
    roomId: null,
    meetingDate: dayjs().format("YYYY-MM-DD"),
    startClock: "09:00",
    endClock: "10:00",
    participantUserIds: [],
    deviceRequirements: [],
    remark: ""
  }
}

function createEditForm(reservation: MyReservationItem): EditMeetingForm {
  return {
    title: reservation.title,
    roomId: reservation.roomId,
    meetingDate: dayjs(reservation.startTime).format("YYYY-MM-DD"),
    startClock: dayjs(reservation.startTime).format("HH:mm"),
    endClock: dayjs(reservation.endTime).format("HH:mm"),
    participantUserIds: normalizeParticipantUserIds(reservation.participants?.map(item => item.id) ?? []),
    deviceRequirements: normalizeDeviceRequirementDrafts(reservation.devices?.map(item => ({
      deviceId: item.deviceId,
      quantity: item.quantity
    })) ?? []),
    remark: reservation.remark ?? ""
  }
}

function normalizeParticipantUserIds(source?: number[] | null) {
  if (!Array.isArray(source)) return []
  return [...new Set(
    source
      .map(item => Number(item))
      .filter(item => Number.isInteger(item) && item > 0)
  )]
}

function normalizeDeviceRequirementDrafts(source?: EditDeviceRequirementDraft[] | null) {
  if (!Array.isArray(source)) return []
  return source
    .filter(item => item && typeof item === "object")
    .map(item => ({
      deviceId: typeof item.deviceId === "number" ? item.deviceId : null,
      quantity: Math.max(1, Number(item.quantity) || 1)
    }))
}

function normalizeDeviceRequirements(source: EditDeviceRequirementDraft[]): ReservationDeviceRequirementInput[] {
  return source
    .filter(item => item.deviceId !== null && item.quantity > 0)
    .map(item => ({
      deviceId: item.deviceId as number,
      quantity: item.quantity
    }))
}

function getSelectableEditDeviceOptions(index: number) {
  const selectedDeviceIds = new Set(
    editForm.value.deviceRequirements
      .filter((item, currentIndex) => currentIndex !== index && item.deviceId !== null)
      .map(item => item.deviceId as number)
  )
  return editDeviceOptions.value.filter(option => !selectedDeviceIds.has(option.id))
}

function addEditDeviceRequirement() {
  if (!canAddMoreEditDevices.value) return
  const selectedDeviceIds = new Set(
    editForm.value.deviceRequirements
      .filter(item => item.deviceId !== null)
      .map(item => item.deviceId as number)
  )
  const nextDevice = editDeviceOptions.value.find(option => !selectedDeviceIds.has(option.id)) ?? null
  editForm.value.deviceRequirements.push({
    deviceId: nextDevice?.id ?? null,
    quantity: 1
  })
}

function removeEditDeviceRequirement(index: number) {
  editForm.value.deviceRequirements.splice(index, 1)
}

function mergeEditParticipantOptions(options: UserSearchOption[]) {
  if (options.length === 0) return
  editParticipantOptionMap.value = {
    ...editParticipantOptionMap.value,
    ...Object.fromEntries(options.map(item => [item.id, item]))
  }
}

async function searchEditParticipantUsers(keyword: string) {
  const trimmedKeyword = keyword.trim()
  if (!trimmedKeyword) {
    editParticipantOptions.value = selectedEditParticipants.value
    return
  }

  editParticipantSearching.value = true
  try {
    const response = await searchUsersApi({
      keyword: trimmedKeyword,
      limit: 10
    })
    editParticipantOptions.value = response.data ?? []
    mergeEditParticipantOptions(editParticipantOptions.value)
  } catch {
    editParticipantOptions.value = selectedEditParticipants.value
  } finally {
    editParticipantSearching.value = false
  }
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

function normalizeRoomOptions(options: Array<MyReservationRoomOption | RoomData>): RoomData[] {
  return options.map((option) => ({
    id: option.id,
    roomCode: option.roomCode,
    name: option.name,
    location: option.location,
    capacity: option.capacity,
    status: option.status,
    description: option.description ?? "",
    devices: "devices" in option ? option.devices : []
  }))
}

function normalizeReservationList(items: MyReservationItem[]): MyReservationItem[] {
  const roomMap = new Map(roomOptions.value.map(room => [room.id, room]))
  return items.map((item) => {
    const room = roomMap.get(item.roomId)
    return {
      ...item,
      roomCode: item.roomCode ?? room?.roomCode ?? "--",
      roomLocation: item.roomLocation ?? room?.location ?? "--",
      roomCapacity: item.roomCapacity ?? room?.capacity ?? 0,
      roomDescription: item.roomDescription ?? room?.description ?? "",
      reviewed: item.reviewed ?? false
    }
  })
}

function applyReservationResponse(data: MyReservationItem[] | MyReservationPageData) {
  if (Array.isArray(data)) {
    reservationList.value = normalizeReservationList(data)
    endedTotal.value = isEndedMode.value ? data.length : 0
    return
  }
  reservationList.value = normalizeReservationList(data.list ?? [])
  endedTotal.value = data.total ?? reservationList.value.length
}

function sortRecentSummaryMeetings(list: MyReservationItem[]) {
  const now = dayjs()
  return [...list]
    .filter(item => item.status === "ENDED" && dayjs(item.endTime).isBefore(now.add(1, "second")))
    .sort((a, b) => dayjs(b.endTime).valueOf() - dayjs(a.endTime).valueOf())
}

function buildDateTime(date: string, time: string) {
  return `${date} ${time}:00`
}

let statusChart: echarts.ECharts | null = null

function renderStatusChart() {
  if (!statusChartRef.value) return
  if (statusChartRef.value.clientWidth === 0 || statusChartRef.value.clientHeight === 0) return
  if (!statusChart) statusChart = echarts.init(statusChartRef.value)
  const total = statusBreakdown.value.reduce((sum, item) => sum + item.value, 0)
  const chartData = total > 0
    ? statusBreakdown.value.map(item => ({ name: item.label, value: item.value, itemStyle: { color: item.color } }))
    : [{ name: "暂无会议", value: 1, itemStyle: { color: "rgba(122, 142, 163, 0.16)" } }]
  statusChart.setOption({
    animationDuration: 360,
    tooltip: { trigger: "item", formatter: total > 0 ? "{b}<br/>{c} 场 ({d}%)" : "暂无会议" },
    series: [{ type: "pie", radius: ["58%", "80%"], center: ["50%", "46%"], label: { show: false }, labelLine: { show: false }, itemStyle: { borderColor: "#fff", borderWidth: 4 }, data: chartData }],
    graphic: [
      { type: "text", left: "center", top: "39%", style: { text: String(total), fill: "#1b3350", fontSize: 26, fontWeight: 700 } },
      { type: "text", left: "center", top: "50%", style: { text: "当前总数", fill: "#7a8ea3", fontSize: 12 } }
    ]
  })
}

function handleStatusChartResize() {
  statusChart?.resize()
}

function disposeStatusChart() {
  statusChart?.dispose()
  statusChart = null
}
</script>
<template>
  <div class="my-reservations-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">My Reservations</p>
        <h1 class="page-hero-title">我的预约</h1>
      </div>
      <div class="hero-focus">
        <span>下一场</span>
        <strong>{{ nextMeeting ? nextMeeting.title : "--" }}</strong>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-item">
        <div class="metric-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div>
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </div>
      </article>
    </section>

    <section class="command-panel">
      <div class="command-group">
        <div class="scope-switch">
          <button v-for="item in scopeOptions" :key="item.value" type="button" class="scope-button" :class="{ active: selectedScope === item.value }" @click="selectedScope = item.value">
            {{ item.label }}
          </button>
        </div>
        <div class="status-switch">
          <button v-for="item in statusOptions" :key="item.value" type="button" class="status-button" :class="{ active: selectedStatus === item.value }" @click="selectedStatus = item.value">
            {{ item.label }}
          </button>
        </div>
      </div>
    </section>

    <section class="main-grid" :class="{ 'is-ended-mode': isEndedMode }">
      <main class="timeline-stage">
        <section v-if="isEndedMode" class="panel ended-panel">
          <div class="section-head">
            <div>
              <p class="panel-kicker">Ended Meetings</p>
              <h2>已结束会议</h2>
            </div>
            <span class="section-meta">{{ endedTotal || endedReservations.length }} 场</span>
          </div>
          <transition name="page-switch" mode="out-in">
            <div :key="`ended-page-${endedPageTransitionKey}`" class="ended-page-shell">
              <div v-if="endedReservations.length > 0" class="meeting-list ended-grid">
                <article v-for="meeting in endedReservations" :key="meeting.id" class="meeting-card ended-card" :class="`tone-${meeting.status.toLowerCase()}`" @click="openDetail(meeting)">
                  <div class="meeting-top">
                    <span class="meeting-hours">{{ formatTimeRange(meeting.startTime, meeting.endTime) }}</span>
                    <span class="review-pill" :class="{ done: meeting.reviewed }">{{ meeting.reviewed ? "已评价" : "待评价" }}</span>
                  </div>
                  <h3>{{ meeting.title }}</h3>
                  <p class="meeting-time">{{ formatMeetingDate(meeting.startTime) }}</p>
                  <p class="meeting-sub">{{ meeting.roomName }} · {{ meeting.organizerName }} · {{ formatRole(meeting.role) }}</p>
                </article>
              </div>
              <el-empty v-else description="暂无已结束会议" />
            </div>
          </transition>
          <div v-if="endedTotal > endedPageSize" class="ended-pagination">
            <el-pagination v-model:current-page="endedPageNum" :page-size="endedPageSize" :total="endedTotal" layout="prev, pager, next" background />
          </div>
        </section>

        <template v-else>
          <section class="panel spotlight-panel">
            <div class="section-head">
              <div>
                <p class="panel-kicker">Day Agenda</p>
                <h2>{{ dayjs(selectedDate).format("M月D日 dddd") }}</h2>
              </div>
              <span class="section-meta">{{ selectedDateReservations.length }} 场</span>
            </div>
            <div v-if="selectedDateReservations.length > 0" class="meeting-list">
              <article v-for="meeting in selectedDateReservations" :key="meeting.id" class="meeting-card" :class="`tone-${meeting.status.toLowerCase()}`" @click="openDetail(meeting)">
                <div class="meeting-top">
                  <span class="role-pill" :class="meeting.role === 'ORGANIZER' ? 'is-organizer' : 'is-participant'">{{ formatRole(meeting.role) }}</span>
                  <span class="status-pill-inline" :class="`is-${meeting.status.toLowerCase()}`">{{ formatStatus(meeting.status) }}</span>
                </div>
                <h3>{{ meeting.title }}</h3>
                <p class="meeting-time">{{ formatTimeRange(meeting.startTime, meeting.endTime) }}</p>
                <p class="meeting-sub">{{ meeting.roomName }} · {{ meeting.organizerName }} · {{ meeting.attendees }} 人</p>
              </article>
            </div>
            <el-empty v-else description="当日没有会议" />
          </section>

          <section class="panel timeline-panel">
            <div class="section-head">
              <div>
                <p class="panel-kicker">Time Center</p>
                <h2>时间中心</h2>
              </div>
              <span class="section-meta">{{ currentMonthReservations.length }} 场</span>
            </div>
            <div v-if="meetingGroups.length > 0" class="group-stack">
              <section v-for="group in meetingGroups" :key="group.key" class="day-group">
                <div class="day-label">{{ group.label }}</div>
                <div class="meeting-list">
                  <article v-for="meeting in group.meetings" :key="meeting.id" class="meeting-card rail" :class="`tone-${meeting.status.toLowerCase()}`" @click="openDetail(meeting)">
                    <div class="meeting-top">
                      <span class="meeting-hours">{{ formatDateTimeRange(meeting.startTime, meeting.endTime) }}</span>
                      <span class="role-pill" :class="meeting.role === 'ORGANIZER' ? 'is-organizer' : 'is-participant'">{{ formatRole(meeting.role) }}</span>
                    </div>
                    <h3>{{ meeting.title }}</h3>
                    <p class="meeting-sub">{{ meeting.roomName }} · {{ meeting.organizerName }}</p>
                  </article>
                </div>
              </section>
            </div>
            <el-empty v-else description="本月没有会议" />
          </section>
        </template>
      </main>

      <aside v-show="showSignalRail" class="signal-rail">
        <section class="panel signal-panel">
          <div class="section-head">
            <div>
              <p class="panel-kicker">Status Breakdown</p>
              <h2>状态分布</h2>
            </div>
          </div>
          <div class="status-chart-shell">
            <div ref="statusChartRef" class="status-chart" />
            <div class="status-legend">
              <div v-for="item in statusBreakdown" :key="item.label" class="status-legend-item">
                <span class="legend-dot" :class="`is-${item.tone}`" />
                <span>{{ item.label }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="panel signal-panel">
          <div class="section-head">
            <div>
              <p class="panel-kicker">Room Summary</p>
              <h2>空间摘要</h2>
            </div>
          </div>
          <div v-if="roomSummary.length > 0" class="room-summary-list">
            <div v-for="room in roomSummary" :key="room.id" class="room-item">
              <div>
                <div class="room-title">{{ room.name }}</div>
                <div class="room-sub">{{ room.location }} · {{ room.capacity }} 人</div>
              </div>
              <div class="room-badge">{{ room.count }} 场</div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </section>

        <section class="panel signal-panel">
          <div class="section-head">
            <div>
              <p class="panel-kicker">Recent Feed</p>
              <h2>最近 5 场摘要</h2>
            </div>
          </div>
          <div v-if="recentSummaries.length > 0" class="summary-list">
            <button v-for="meeting in recentSummaries" :key="meeting.id" type="button" class="summary-item" :class="`tone-${meeting.status.toLowerCase()}`" @click="openDetail(meeting)">
              <div class="summary-top">
                <span class="summary-time">{{ dayjs(meeting.startTime).format("M月D日 HH:mm") }}</span>
                <span class="summary-status" :class="`is-${meeting.status.toLowerCase()}`">{{ formatStatus(meeting.status) }}</span>
              </div>
              <strong>{{ meeting.title }}</strong>
              <div class="summary-bottom">
                <small>{{ meeting.roomName }}</small>
                <span class="summary-role" :class="meeting.role === 'ORGANIZER' ? 'is-organizer' : 'is-participant'">{{ formatRole(meeting.role) }}</span>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无数据" />
        </section>
      </aside>
    </section>

    <el-dialog v-model="detailDialogVisible" width="min(920px, calc(100vw - 32px))" class="reservation-detail-dialog" destroy-on-close align-center>
      <template #header>
        <div v-if="selectedReservation" class="dialog-header">
          <div>
            <p class="detail-code">{{ selectedReservation.reservationNo }}</p>
            <h3>{{ selectedReservation.title }}</h3>
          </div>
          <div class="dialog-header-side">
            <span class="role-pill" :class="selectedReservation.role === 'ORGANIZER' ? 'is-organizer' : 'is-participant'">{{ formatRole(selectedReservation.role) }}</span>
            <span class="status-pill-inline" :class="`is-${selectedReservation.status.toLowerCase()}`">{{ formatStatus(selectedReservation.status) }}</span>
          </div>
        </div>
      </template>
      <div v-if="selectedReservation" class="detail-card">
        <div class="detail-overview">
          <div class="detail-box"><span>会议日期</span><strong>{{ selectedDateText }}</strong></div>
          <div class="detail-box"><span>会议时段</span><strong>{{ formatTimeRange(selectedReservation.startTime, selectedReservation.endTime) }}</strong></div>
          <div class="detail-box"><span>预约时长</span><strong>{{ selectedDurationText }}</strong></div>
          <div class="detail-box"><span>设备数量</span><strong>{{ selectedDeviceCount }}</strong></div>
          <div class="detail-box"><span>我的身份</span><strong>{{ formatRole(selectedReservation.role) }}</strong></div>
          <div class="detail-box"><span>参会人数</span><strong>{{ selectedReservation.attendees }} 人</strong></div>
          <div class="detail-box"><span>会议室</span><strong>{{ selectedReservation.roomName }}</strong></div>
          <div class="detail-box"><span>房间编号</span><strong>{{ selectedRoomCode }}</strong></div>
          <div class="detail-box"><span>所在区域</span><strong>{{ selectedRoomLocation }}</strong></div>
          <div class="detail-box"><span>容量</span><strong>{{ selectedRoomCapacityText }}</strong></div>
        </div>
        <div class="detail-section">
          <div class="detail-title"><el-icon><OfficeBuilding /></el-icon>空间说明</div>
          <div class="detail-note">{{ selectedRoomDescription || "当前会议室没有补充说明。" }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-title"><el-icon><User /></el-icon>具体参会人</div>
          <div class="detail-note">{{ formatParticipantNames(selectedReservation) }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-title"><el-icon><Monitor /></el-icon>资源信息</div>
          <div v-if="selectedReservation.devices.length > 0" class="device-list">
            <div v-for="device in selectedReservation.devices" :key="device.id" class="device-item">
              <div>
                <div class="device-name">{{ device.name }}</div>
                <div class="device-code">{{ device.deviceCode }}</div>
              </div>
              <div class="device-side">
                <span class="device-qty">x {{ device.quantity }}</span>
                <span class="device-state" :class="{ disabled: device.status === 'DISABLED' }">{{ device.status === "DISABLED" ? "停用" : "可用" }}</span>
              </div>
            </div>
          </div>
          <div v-else class="detail-note">当前会议没有额外设备申请。</div>
        </div>
        <div v-if="selectedCancelReasonText" class="detail-section">
          <div class="detail-title"><el-icon><User /></el-icon>补充信息</div>
          <div class="detail-note-stack">
            <div class="detail-note-block">
              <span class="detail-note-label">取消原因</span>
              <div class="detail-note">{{ selectedCancelReasonText }}</div>
            </div>
          </div>
        </div>
        <div v-if="selectedReservation.status === 'ENDED'" class="detail-section">
          <div class="detail-title"><el-icon><Tickets /></el-icon>会后评价</div>
          <div v-if="selectedReview" class="review-preview">
            <div class="review-preview-top">
              <el-rate :model-value="selectedReview.rating" disabled />
              <span class="review-preview-time">{{ dayjs(selectedReview.createdAt).format("YYYY-MM-DD HH:mm") }}</span>
            </div>
            <div class="detail-note">{{ selectedReview.content || "已提交评价" }}</div>
          </div>
          <div v-else class="detail-note">当前会议尚未评价。</div>
        </div>
      </div>
      <template #footer>
        <div v-if="selectedReservation" class="dialog-footer">
          <div v-if="canEditSelectedReservation || canCancelSelectedReservation || selectedReservation.status === 'ENDED'" class="footer-actions">
            <el-button v-if="canEditSelectedReservation" class="action-button action-button-edit" @click="openEditDialog"><el-icon><EditPen /></el-icon><span>修改会议</span></el-button>
            <el-button v-if="canCancelSelectedReservation" class="action-button action-button-cancel" @click="openCancelDialog"><el-icon><CloseBold /></el-icon><span>取消会议</span></el-button>
            <el-button v-if="canReviewSelectedReservation" class="action-button action-button-review" @click="openReviewDialog"><el-icon><Tickets /></el-icon><span>去评价</span></el-button>
            <el-button v-else-if="selectedReservation.status === 'ENDED'" class="action-button action-button-reviewed" disabled><el-icon><Tickets /></el-icon><span>已评价</span></el-button>
          </div>
          <div v-else class="footer-tip">{{ detailFooterText }}</div>
        </div>
      </template>
    </el-dialog>
    <el-dialog v-model="editDialogVisible" width="min(680px, calc(100vw - 32px))" class="reservation-edit-dialog" append-to-body destroy-on-close align-center>
      <template #header>
        <div class="sub-dialog-header">
          <div>
            <p class="detail-code">Meeting Edit</p>
            <h3>修改会议</h3>
          </div>
          <span class="section-meta">仅调整当前预约信息</span>
        </div>
      </template>
      <div class="edit-form">
        <div class="edit-field edit-field-wide"><label>会议标题</label><el-input v-model="editForm.title" maxlength="40" placeholder="请输入会议标题" /></div>
        <div class="edit-field"><label>会议室</label><el-select v-model="editForm.roomId" placeholder="请选择会议室"><el-option v-for="room in roomOptions" :key="room.id" :label="room.name" :value="room.id" /></el-select></div>
        <div class="edit-field"><label>会议日期</label><el-date-picker v-model="editForm.meetingDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" /></div>
        <div class="edit-field"><label>开始时间</label><el-select v-model="editForm.startClock" placeholder="开始时间"><el-option v-for="time in timeOptions" :key="`start-${time}`" :label="time" :value="time" /></el-select></div>
        <div class="edit-field"><label>结束时间</label><el-select v-model="editForm.endClock" placeholder="结束时间"><el-option v-for="time in timeOptions" :key="`end-${time}`" :label="time" :value="time" /></el-select></div>
        <div class="edit-field edit-field-wide">
          <label>参会人</label>
          <el-select
            v-model="editForm.participantUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            collapse-tags
            collapse-tags-tooltip
            :remote-method="searchEditParticipantUsers"
            :loading="editParticipantSearching"
            placeholder="输入姓名搜索参会人"
          >
            <el-option
              v-for="user in editParticipantOptions"
              :key="user.id"
              :label="user.displayName"
              :value="user.id"
            />
          </el-select>
        </div>
        <div class="edit-field edit-field-wide">
          <label>设备需求</label>
          <div class="edit-device-builder">
            <div v-if="editForm.deviceRequirements.length === 0" class="edit-device-empty">设备为空</div>
            <div v-else class="edit-device-list">
              <div
                v-for="(item, index) in editForm.deviceRequirements"
                :key="`${item.deviceId ?? 'draft'}-${index}`"
                class="edit-device-row"
              >
                <el-select v-model="item.deviceId" placeholder="选择设备">
                  <el-option
                    v-for="device in getSelectableEditDeviceOptions(index)"
                    :key="device.id"
                    :label="device.label"
                    :value="device.id"
                  />
                </el-select>
                <el-input-number v-model="item.quantity" :min="1" :max="99" controls-position="right" />
                <el-button text @click="removeEditDeviceRequirement(index)">删除</el-button>
              </div>
            </div>
            <el-button class="edit-device-add" type="primary" plain :disabled="!canAddMoreEditDevices" @click="addEditDeviceRequirement">添加设备</el-button>
          </div>
        </div>
        <div class="edit-field edit-field-wide">
          <label>补充备注</label>
          <el-input v-model="editForm.remark" type="textarea" :rows="4" maxlength="120" show-word-limit placeholder="可以补充会议重点、参会提醒或备注信息" />
        </div>
      </div>
      <template #footer>
        <div class="sub-dialog-footer">
          <el-button class="subtle-button" @click="editDialogVisible = false">暂不修改</el-button>
          <el-button class="action-button action-button-edit" @click="submitEdit"><el-icon><EditPen /></el-icon><span>保存修改</span></el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialogVisible" width="min(560px, calc(100vw - 32px))" class="reservation-cancel-dialog" append-to-body destroy-on-close align-center>
      <template #header>
        <div class="sub-dialog-header">
          <div>
            <p class="detail-code">Meeting Cancel</p>
            <h3>取消会议</h3>
          </div>
          <span class="status-pill-inline is-cancelled">需要填写原因</span>
        </div>
      </template>
      <div class="cancel-form">
        <div class="cancel-reason-options">
          <button v-for="reason in cancelReasonOptions" :key="reason" type="button" class="reason-chip" @click="fillCancelReason(reason)">{{ reason }}</button>
        </div>
        <div class="edit-field edit-field-wide">
          <label>取消原因</label>
          <el-input v-model="cancelReasonInput" type="textarea" :rows="5" maxlength="120" show-word-limit placeholder="请填写取消原因，方便后续查看和同步。" />
        </div>
      </div>
      <template #footer>
        <div class="sub-dialog-footer">
          <el-button class="subtle-button" @click="cancelDialogVisible = false">暂不取消</el-button>
          <el-button class="action-button action-button-cancel" @click="submitCancel"><el-icon><CloseBold /></el-icon><span>确认取消</span></el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" width="min(560px, calc(100vw - 32px))" class="reservation-review-dialog" append-to-body destroy-on-close align-center>
      <template #header>
        <div class="sub-dialog-header">
          <div>
            <p class="detail-code">Meeting Review</p>
            <h3>会后评价</h3>
          </div>
          <span class="status-pill-inline is-ended">仅限已结束会议</span>
        </div>
      </template>
      <div class="review-form">
        <div class="review-rate-shell"><label>评分</label><el-rate v-model="reviewForm.rating" :max="5" /></div>
        <div class="edit-field edit-field-wide">
          <label>评价内容</label>
          <el-input v-model="reviewForm.content" type="textarea" :rows="5" maxlength="300" show-word-limit placeholder="写下你对本次会议空间、设备和整体体验的评价" />
        </div>
      </div>
      <template #footer>
        <div class="sub-dialog-footer">
          <el-button class="subtle-button" @click="reviewDialogVisible = false">暂不评价</el-button>
          <el-button class="action-button action-button-review" @click="submitReview"><el-icon><Tickets /></el-icon><span>提交评价</span></el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.my-reservations-page { --page-topbar-columns: minmax(0, 1.3fr) minmax(280px, .8fr); --page-topbar-columns-md: 1fr; --page-topbar-side-justify-sm: flex-start; min-height: 100%; padding: 24px; color: #18314a; background: radial-gradient(circle at top left, rgba(117, 173, 255, 0.18), transparent 26%), radial-gradient(circle at top right, rgba(99, 212, 187, 0.16), transparent 28%), linear-gradient(180deg, #fbfaf7 0%, #f3f5f8 100%); }
.metric-item, .command-panel, .panel, .detail-card { border: 1px solid rgba(255,255,255,.78); border-radius: 26px; background: rgba(255,255,255,.78); box-shadow: 0 24px 56px rgba(48,71,98,.12); backdrop-filter: blur(18px); }
.hero-focus { display: grid; align-content: end; justify-self: end; width: min(280px, 100%); padding: 16px; border-radius: 20px; background: linear-gradient(135deg, rgba(25,51,79,.94), rgba(56,87,123,.9)); color: #f5fbff; }
.hero-focus span { font-size: 12px; letter-spacing: .08em; text-transform: uppercase; color: rgba(245,251,255,.72); }
.hero-focus strong { margin-top: 8px; font-size: 20px; line-height: 1.35; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; margin-top: 14px; }
.metric-item { display: grid; grid-template-columns: 48px 1fr; gap: 12px; padding: 16px; }
.metric-icon { display: flex; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: 16px; background: linear-gradient(135deg, rgba(95,142,255,.12), rgba(88,203,179,.14)); color: #3c63da; font-size: 20px; }
.metric-label, .meeting-sub, .room-sub, .device-code, .detail-box span, .detail-code, .footer-tip { font-size: 12px; color: #7a8ea3; }
.metric-value { margin-top: 4px; font-size: 24px; font-weight: 700; }
.command-panel, .section-head, .meeting-top, .dialog-header, .dialog-header-side, .dialog-footer, .room-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.command-panel { margin-top: 16px; padding: 16px 18px; }
.command-group, .scope-switch { display: inline-flex; align-items: center; gap: 10px; }
.scope-switch, .status-switch { padding: 5px; border-radius: 999px; background: linear-gradient(135deg, rgba(255,255,255,.96), rgba(235,244,255,.88)); }
.scope-button, .status-button, .summary-item, .reason-chip, .subtle-button { border: none; cursor: pointer; }
.scope-button, .status-button { min-width: 96px; padding: 10px 18px; border-radius: 999px; background: transparent; color: #6d8199; font-size: 13px; font-weight: 600; }
.scope-button.active, .status-button.active { background: linear-gradient(135deg, #7aa8ff, #65d4bf); color: #fff; box-shadow: 0 10px 24px rgba(102,151,232,.24); }
.main-grid { display: grid; grid-template-columns: minmax(0, 1.56fr) minmax(320px, .84fr); gap: 16px; margin-top: 16px; align-items: start; }
.main-grid.is-ended-mode { grid-template-columns: minmax(0, 1fr); }
.timeline-stage, .signal-rail, .meeting-list, .group-stack, .room-summary-list, .summary-list, .device-list { display: grid; gap: 12px; }
.ended-page-shell { display: grid; gap: 16px; }
.panel { padding: 18px; }
.ended-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.ended-pagination { display: flex; justify-content: center; margin-top: 18px; }
.page-switch-enter-active, .page-switch-leave-active { transition: opacity .24s ease, transform .24s ease; }
.page-switch-enter-from { opacity: 0; transform: translateY(14px); }
.page-switch-leave-to { opacity: 0; transform: translateY(-10px); }
.status-chart-shell { display: grid; gap: 8px; }
.status-chart { width: 100%; height: 214px; }
.section-head { align-items: flex-start; margin-bottom: 14px; }
.panel-kicker { margin: 0 0 6px; color: #7c90a7; font-size: 11px; letter-spacing: .28em; text-transform: uppercase; }
.section-head h2 { margin: 0; font-size: 23px; line-height: 1.08; }
.section-meta, .role-pill, .status-pill-inline, .room-badge, .device-state, .review-pill, .summary-status { display: inline-flex; align-items: center; justify-content: center; min-height: 28px; padding: 0 12px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.section-meta { background: rgba(113,141,178,.12); color: #5c738e; }
.status-legend { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px 14px; }
.status-legend-item { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 600; color: #73869b; }
.legend-dot { width: 10px; height: 10px; border-radius: 999px; }
.legend-dot.is-pending { background: #e2a54b; }
.legend-dot.is-active { background: #59c3b0; }
.legend-dot.is-ended { background: #5f81ff; }
.legend-dot.is-cancelled { background: #f28dac; }
.day-group { display: grid; grid-template-columns: 132px minmax(0, 1fr); gap: 14px; align-items: start; }
.day-label { padding-top: 8px; font-size: 14px; font-weight: 700; color: #51667f; }
.meeting-card, .room-item, .summary-item, .detail-box, .detail-note, .device-item { padding: 14px 16px; border-radius: 20px; background: rgba(255,255,255,.68); border: 1px solid transparent; transition: transform .2s ease, box-shadow .2s ease; }
.meeting-card:hover, .summary-item:hover { transform: translateY(-2px); box-shadow: 0 18px 32px rgba(74,96,123,.12); }
.meeting-card h3, .dialog-header h3 { margin: 10px 0 8px; font-size: 20px; line-height: 1.2; }
.dialog-header h3 { font-size: 28px; }
.meeting-time, .meeting-hours, .device-qty { font-size: 13px; font-weight: 600; color: #4f6886; }
.review-pill { background: rgba(113,141,178,.14); color: #5d7187; }
.review-pill.done { background: rgba(88,203,179,.14); color: #15806b; }
.meeting-card.tone-pending, .status-pill-inline.is-pending { border-color: rgba(226,165,75,.24); background: linear-gradient(135deg, rgba(255,250,240,.98), rgba(255,244,222,.9)); color: #a76410; }
.meeting-card.tone-active, .status-pill-inline.is-active, .device-state { border-color: rgba(88,203,179,.24); background: linear-gradient(135deg, rgba(244,255,252,.96), rgba(238,248,255,.86)); color: #14836d; }
.meeting-card.tone-ended, .status-pill-inline.is-ended { border-color: rgba(94,136,255,.18); background: linear-gradient(135deg, rgba(245,248,255,.98), rgba(240,246,255,.86)); color: #4669da; }
.meeting-card.tone-cancelled, .status-pill-inline.is-cancelled { border-color: rgba(245,159,186,.22); background: linear-gradient(135deg, rgba(255,246,249,.98), rgba(255,243,238,.9)); color: #d5557d; }
.meeting-card.tone-rejected, .status-pill-inline.is-rejected { border-color: rgba(214,106,130,.22); background: linear-gradient(135deg, rgba(255,246,249,.98), rgba(255,240,244,.9)); color: #c64c68; }
.meeting-card.tone-exception, .status-pill-inline.is-exception { border-color: rgba(223,139,69,.24); background: linear-gradient(135deg, rgba(255,249,241,.98), rgba(255,238,222,.9)); color: #b45d18; }
.role-pill.is-organizer, .summary-role.is-organizer { background: rgba(96,140,255,.14); color: #476bdf; }
.role-pill.is-participant, .summary-role.is-participant { background: rgba(255,205,118,.16); color: #a86a10; }
.room-title, .device-name { font-size: 14px; font-weight: 600; }
.room-badge { background: rgba(88,203,179,.14); color: #15806b; }
.summary-item { display: grid; gap: 10px; text-align: left; align-items: start; }
.summary-top, .summary-bottom, .footer-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.summary-time { display: inline-flex; align-items: center; min-height: 28px; padding: 0 12px; border-radius: 999px; background: rgba(95,123,159,.12); color: #4f6783; font-size: 12px; font-weight: 600; }
.summary-status.is-active { background: rgba(88,203,179,.16); color: #14836d; }
.summary-status.is-ended { background: rgba(94,136,255,.14); color: #4669da; }
.summary-status.is-cancelled { background: rgba(245,159,186,.16); color: #d5557d; }
.summary-item strong, .detail-box strong { color: #18314a; }
.sub-dialog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.detail-card { padding-top: 6px; }
.detail-code { margin: 0; letter-spacing: .16em; text-transform: uppercase; }
.detail-overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.detail-box { padding: 10px 14px; }
.detail-box strong { display: block; margin-top: 4px; font-size: 15px; line-height: 1.35; }
.detail-section { margin-top: 10px; }
.detail-title { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 13px; color: #607287; }
.detail-note { font-size: 13px; line-height: 1.6; color: #51647b; }
.detail-note-stack, .detail-note-block, .review-preview, .review-form, .review-rate-shell, .cancel-form, .edit-field { display: grid; gap: 8px; }
.review-preview-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.review-preview-time, .summary-item small { font-size: 12px; color: #7a8ea3; }
.device-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.device-side { display: grid; justify-items: end; gap: 6px; }
.device-state.disabled { background: rgba(127,141,158,.16); color: #6a7b90; }
.dialog-footer { justify-content: center; }
.footer-actions { justify-content: center; width: 100%; }
.action-button { min-width: 148px; height: 44px; border: none; border-radius: 999px; font-weight: 700; }
.action-button :deep(.el-icon) { margin-right: 8px; }
.action-button-edit { background: linear-gradient(135deg, #7aa8ff, #67d4c1); color: #fff; }
.action-button-cancel { background: linear-gradient(135deg, #f6c2d3, #f5b398); color: #7d2d49; }
.action-button-review { background: linear-gradient(135deg, #7aa8ff, #67d4c1); color: #fff; }
.action-button-reviewed { background: rgba(113,141,178,.14); color: #6b7d92; }
.edit-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.edit-field-wide { grid-column: 1 / -1; }
.edit-field :deep(.el-select), .edit-field :deep(.el-date-editor), .edit-field :deep(.el-input-number), .edit-field :deep(.el-input-number .el-input__wrapper) { width: 100%; }
.edit-device-builder { display: grid; gap: 10px; }
.edit-device-empty { padding: 12px 14px; border-radius: 14px; background: rgba(118,136,160,.08); color: #7a8ea3; font-size: 13px; }
.edit-device-list { display: grid; gap: 10px; }
.edit-device-row { display: grid; grid-template-columns: minmax(0, 1fr) 140px 64px; gap: 10px; align-items: center; }
.edit-device-add { justify-self: start; border-radius: 999px; }
.cancel-reason-options { display: flex; flex-wrap: wrap; gap: 10px; }
.reason-chip { padding: 10px 14px; border-radius: 999px; background: rgba(94,136,255,.1); color: #4b68a9; font-size: 12px; font-weight: 600; }
.sub-dialog-footer { display: flex; align-items: center; justify-content: center; gap: 14px; }
.subtle-button { min-width: 132px; height: 42px; border-radius: 999px; background: rgba(118,136,160,.1); color: #5b718d; }
:deep(.reservation-detail-dialog .el-dialog), :deep(.reservation-edit-dialog .el-dialog), :deep(.reservation-cancel-dialog .el-dialog), :deep(.reservation-review-dialog .el-dialog) { border-radius: 28px; overflow: hidden; }
:deep(.reservation-detail-dialog .el-dialog__header), :deep(.reservation-edit-dialog .el-dialog__header), :deep(.reservation-cancel-dialog .el-dialog__header), :deep(.reservation-review-dialog .el-dialog__header) { margin-right: 0; padding: 22px 24px 0; }
:deep(.reservation-detail-dialog .el-dialog__body), :deep(.reservation-edit-dialog .el-dialog__body), :deep(.reservation-cancel-dialog .el-dialog__body), :deep(.reservation-review-dialog .el-dialog__body) { padding: 18px 24px 8px; }
:deep(.reservation-detail-dialog .el-dialog__footer), :deep(.reservation-edit-dialog .el-dialog__footer), :deep(.reservation-cancel-dialog .el-dialog__footer), :deep(.reservation-review-dialog .el-dialog__footer) { padding: 10px 24px 22px; }
@media screen and (max-width: 1480px) { .my-reservations-page { --page-topbar-columns: 1fr; } .metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .main-grid { grid-template-columns: 1fr; } }
@media screen and (max-width: 1180px) { .metric-grid, .detail-overview, .edit-form, .ended-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .day-group { grid-template-columns: 1fr; } }
@media screen and (max-width: 768px) { .my-reservations-page { padding: 16px; } .metric-grid, .detail-overview, .edit-form, .ended-grid, .edit-device-row { grid-template-columns: 1fr; } .command-panel, .dialog-header, .dialog-header-side, .dialog-footer, .sub-dialog-header, .sub-dialog-footer, .summary-top, .summary-bottom, .footer-actions { flex-direction: column; align-items: stretch; } .scope-switch, .status-switch, .command-group { width: 100%; } .scope-button, .status-button { flex: 1; } }
</style>
