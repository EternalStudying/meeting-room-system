<script lang="ts" setup>
import { computed, onMounted, ref, watch } from "vue"
import { ElMessage } from "element-plus"
import type { DateSelectArg, DatesSetArg, EventClickArg, EventDropArg, EventInput } from "@fullcalendar/core"
import zhCnLocale from "@fullcalendar/core/locales/zh-cn"
import dayGridPlugin from "@fullcalendar/daygrid"
import interactionPlugin from "@fullcalendar/interaction"
import type { EventResizeDoneArg } from "@fullcalendar/interaction"
import timeGridPlugin from "@fullcalendar/timegrid"
import FullCalendar from "@fullcalendar/vue3"
import dayjs from "dayjs"
import "dayjs/locale/zh-cn"
import { ArrowLeft, ArrowRight, Calendar, Clock, MagicStick, Monitor, User } from "@element-plus/icons-vue"
import ReservationCreateDialog from "@/components/ReservationCreateDialog.vue"
import type { ReservationCreateDraft } from "@/components/reservation-create-dialog"
import { createReservationCreateDraft } from "@/components/reservation-create-dialog"
import { getMyReservationsApi, getReservationCalendarApi, updateMyReservationApi } from "@/common/apis/reservations"
import type { MyReservationItem, ReservationCalendarItem, ReservationStatus } from "@/common/apis/reservations/type"
import { getRoomListApi } from "@/common/apis/rooms"
import type { RoomData } from "@/common/apis/rooms/type"

type CalendarViewMode = "dayGridMonth" | "timeGridWeek"
type StatusFilter = ReservationStatus | "all"
type CalendarReservationItem = ReservationCalendarItem | MyReservationItem

interface CalendarEventChangePayload {
  event: {
    start: Date | null
    end: Date | null
    extendedProps: Record<string, unknown>
  }
  revert: () => void
}

interface MetricCard {
  label: string
  value: string
  icon: typeof Calendar
}

const calendarRef = ref()
const loading = ref(false)
const roomOptions = ref<RoomData[]>([])
const reservationList = ref<CalendarReservationItem[]>([])
const currentView = ref<CalendarViewMode>("timeGridWeek")
const currentTitle = ref("")
const selectedDate = ref(dayjs().format("YYYY-MM-DD"))
const selectedRoomId = ref<number | undefined>(undefined)
const selectedStatus = ref<StatusFilter>("all")
const viewMyReservationsOnly = ref(false)
const selectedReservationId = ref<number | null>(null)
const detailDialogVisible = ref(false)
const reservationDialogVisible = ref(false)
const reservationDialogPreset = ref<Partial<ReservationCreateDraft> | null>(null)
const visibleRange = ref({ startDate: "", endDate: "" })
const timeFormatter = { hour: "2-digit", minute: "2-digit", hour12: false } as const

dayjs.locale("zh-cn")

const statusOptions: Array<{ label: string, value: StatusFilter }> = [
  { label: "全部状态", value: "all" },
  { label: "待审核", value: "PENDING" },
  { label: "进行中", value: "ACTIVE" },
  { label: "已结束", value: "ENDED" },
  { label: "已取消", value: "CANCELLED" },
  { label: "已驳回", value: "REJECTED" },
  { label: "异常", value: "EXCEPTION" }
]

const viewOptions: Array<{ label: string, value: CalendarViewMode }> = [
  { label: "月", value: "dayGridMonth" },
  { label: "周", value: "timeGridWeek" }
]

const sortedRoomOptions = computed(() => {
  return [...roomOptions.value].sort((a, b) => a.name.localeCompare(b.name, "zh-CN", { numeric: true, sensitivity: "base" }))
})

const selectedDayReservations = computed(() => {
  const now = dayjs()
  return reservationList.value
    .filter(item => dayjs(item.startTime).format("YYYY-MM-DD") === selectedDate.value)
    .filter(item => dayjs(item.endTime).isAfter(now))
    .sort((a, b) => dayjs(a.startTime).valueOf() - dayjs(b.startTime).valueOf())
})

const selectedReservation = computed(() => {
  return reservationList.value.find(item => item.id === selectedReservationId.value) ?? null
})

const activeFilterRoom = computed(() => {
  return roomOptions.value.find(item => item.id === selectedRoomId.value) ?? null
})

const selectedRoom = computed(() => {
  if (!selectedReservation.value) return null
  const roomId = selectedReservation.value.roomId
  return roomOptions.value.find(item => item.id === roomId) ?? null
})

const selectedRoomLocation = computed(() => {
  return selectedRoom.value?.location ?? ""
})

const selectedDurationText = computed(() => {
  if (!selectedReservation.value) return ""
  const minutes = dayjs(selectedReservation.value.endTime).diff(dayjs(selectedReservation.value.startTime), "minute")
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  if (hours > 0 && remainMinutes > 0) return `${hours} 小时 ${remainMinutes} 分钟`
  if (hours > 0) return `${hours} 小时`
  return `${remainMinutes} 分钟`
})

const selectedDateText = computed(() => {
  if (!selectedReservation.value) return ""
  return dayjs(selectedReservation.value.startTime).format("YYYY 年 M 月 D 日 dddd")
})

const selectedDeviceCount = computed(() => {
  return selectedReservation.value?.devices.reduce((sum, item) => sum + item.quantity, 0) ?? 0
})

const selectedRoomCapacityText = computed(() => {
  if (!selectedRoom.value?.capacity) return "--"
  return `${selectedRoom.value.capacity} 人`
})

const selectedRoomCode = computed(() => {
  return selectedRoom.value?.roomCode ?? "--"
})

const selectedRoomDescription = computed(() => {
  return selectedRoom.value?.description?.trim() || ""
})

const isDragEditEnabled = computed(() => {
  return viewMyReservationsOnly.value && currentView.value === "timeGridWeek"
})

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;")
}

const calendarEvents = computed<EventInput[]>(() => {
  return reservationList.value.map(item => ({
    id: String(item.id),
    title: item.title,
    start: item.startTime,
    end: item.endTime,
    classNames: [`event-tone-${item.status.toLowerCase()}`],
    editable: isDragEditEnabled.value && item.status === "ACTIVE",
    startEditable: isDragEditEnabled.value && item.status === "ACTIVE",
    durationEditable: isDragEditEnabled.value && item.status === "ACTIVE",
    extendedProps: { reservation: item }
  }))
})

const metrics = computed<MetricCard[]>(() => {
  const activeCount = reservationList.value.filter(item => item.status === "ACTIVE").length
  const roomCoverageCount = new Set(reservationList.value.map(item => item.roomId)).size
  return [
    {
      label: "当前视图预约",
      value: padNumber(reservationList.value.length),
      icon: Calendar
    },
    {
      label: "进行中",
      value: padNumber(activeCount),
      icon: Clock
    },
    {
      label: "房间覆盖",
      value: padNumber(roomCoverageCount),
      icon: Monitor
    },
    {
      label: "当日明细",
      value: padNumber(selectedDayReservations.value.length),
      icon: MagicStick
    }
  ]
})

const roomInsights = computed(() => {
  return sortedRoomOptions.value.slice(0, 10).map(room => ({
    id: room.id,
    name: room.name,
    location: room.location,
    status: room.status,
    count: reservationList.value.filter(item => item.roomId === room.id && item.status === "ACTIVE").length
  }))
})

const calendarOptions = computed(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  initialView: currentView.value,
  locale: zhCnLocale,
  headerToolbar: false as const,
  firstDay: 1,
  allDaySlot: false,
  expandRows: true,
  nowIndicator: true,
  selectable: true,
  editable: isDragEditEnabled.value,
  eventStartEditable: isDragEditEnabled.value,
  eventDurationEditable: isDragEditEnabled.value,
  height: "auto",
  contentHeight: "auto",
  dayMaxEventRows: 3,
  eventMaxStack: 3,
  slotMinTime: "08:00:00",
  slotMaxTime: "19:00:00",
  eventTimeFormat: timeFormatter,
  slotLabelFormat: timeFormatter,
  events: calendarEvents.value,
  eventContent: renderCalendarEventContent,
  datesSet: handleDatesSet,
  select: handleDateSelect,
  eventDrop: handleEventDrop,
  eventResize: handleEventResize,
  dateClick: ({ dateStr }: { dateStr: string }) => {
    selectedDate.value = dateStr.slice(0, 10)
  },
  eventClick: handleEventClick
}))

onMounted(() => {
  fetchRooms()
})

watch(reservationList, (list) => {
  if (list.length === 0) {
    selectedReservationId.value = null
    detailDialogVisible.value = false
    return
  }
  if (selectedReservationId.value !== null && !list.some(item => item.id === selectedReservationId.value)) {
    selectedReservationId.value = null
    detailDialogVisible.value = false
  }
})

watch([selectedRoomId, selectedStatus, viewMyReservationsOnly], () => {
  fetchReservations()
})

async function fetchRooms() {
  try {
    const response = await getRoomListApi({ currentPage: 1, size: 50 })
    roomOptions.value = response.data.list
    if (roomOptions.value.length > 0 && selectedRoomId.value === undefined) {
      selectedRoomId.value = roomOptions.value[0].id
    }
  } catch {
    roomOptions.value = []
    selectedRoomId.value = undefined
  }
}

async function fetchReservations() {
  if (!visibleRange.value.startDate || !visibleRange.value.endDate) return
  if (!viewMyReservationsOnly.value && selectedRoomId.value === undefined) return
  loading.value = true
  try {
    if (viewMyReservationsOnly.value) {
      const response = await getMyReservationsApi({
        startDate: visibleRange.value.startDate,
        endDate: visibleRange.value.endDate,
        scope: "organizer",
        status: selectedStatus.value === "all" ? undefined : selectedStatus.value
      })
      reservationList.value = Array.isArray(response.data) ? response.data : response.data.list
      return
    }

    const response = await getReservationCalendarApi({
      startDate: visibleRange.value.startDate,
      endDate: visibleRange.value.endDate,
      roomId: selectedRoomId.value,
      status: selectedStatus.value === "all" ? undefined : selectedStatus.value
    })
    reservationList.value = response.data
  } catch {
    reservationList.value = []
    selectedReservationId.value = null
  } finally {
    loading.value = false
  }
}

function handleDatesSet(arg: DatesSetArg) {
  currentTitle.value = arg.view.title
  currentView.value = arg.view.type as CalendarViewMode
  visibleRange.value = {
    startDate: dayjs(arg.start).format("YYYY-MM-DD HH:mm:ss"),
    endDate: dayjs(arg.end).format("YYYY-MM-DD HH:mm:ss")
  }
  fetchReservations()
}

function handleEventClick(arg: EventClickArg) {
  const reservation = arg.event.extendedProps.reservation as CalendarReservationItem
  selectedDate.value = dayjs(reservation.startTime).format("YYYY-MM-DD")
  selectedReservationId.value = reservation.id
  detailDialogVisible.value = true
}

function handleDateSelect(arg: DateSelectArg) {
  arg.view.calendar.unselect()

  if (arg.view.type !== "timeGridWeek") return

  const room = activeFilterRoom.value
  if (!room) {
    ElMessage.warning("请先选择会议室")
    return
  }
  if (room.status !== "AVAILABLE") {
    ElMessage.warning("当前会议室不可预约")
    return
  }

  const meetingDate = dayjs(arg.start).format("YYYY-MM-DD")
  selectedDate.value = meetingDate
  reservationDialogPreset.value = {
    roomId: room.id,
    meetingDate,
    startClock: dayjs(arg.start).format("HH:mm"),
    endClock: dayjs(arg.end).format("HH:mm"),
    attendees: Math.min(1, room.capacity)
  }
  reservationDialogVisible.value = true
}

function changeView(view: CalendarViewMode) {
  currentView.value = view
  calendarRef.value?.getApi().changeView(view)
}

function navigateCalendar(action: "prev" | "next" | "today") {
  calendarRef.value?.getApi()[action]()
}

function handleReservationSubmitted() {
  reservationDialogVisible.value = false
  reservationDialogPreset.value = createReservationCreateDraft()
  void fetchReservations()
}

function handleEventDrop(arg: EventDropArg) {
  void handleEventChange(arg)
}

function handleEventResize(arg: EventResizeDoneArg) {
  void handleEventChange(arg)
}

async function handleEventChange(arg: CalendarEventChangePayload) {
  const reservation = arg.event.extendedProps.reservation as CalendarReservationItem | undefined

  if (!reservation) {
    arg.revert()
    return
  }

  if (!isDragEditEnabled.value || reservation.status !== "ACTIVE") {
    arg.revert()
    return
  }

  if (!arg.event.start || !arg.event.end) {
    arg.revert()
    return
  }

  try {
    await updateMyReservationApi(reservation.id, {
      roomId: reservation.roomId,
      title: reservation.title,
      meetingDate: dayjs(arg.event.start).format("YYYY-MM-DD"),
      startClock: dayjs(arg.event.start).format("HH:mm"),
      endClock: dayjs(arg.event.end).format("HH:mm"),
      attendees: reservation.attendees,
      deviceRequirements: reservation.devices.map(item => ({
        deviceId: item.deviceId,
        quantity: item.quantity
      })),
      remark: reservation.remark
    })

    await fetchReservations()
    ElMessage.success("预约时间已更新")
  } catch {
    arg.revert()
    ElMessage.error("修改预约时间失败")
  }
}

function renderCalendarEventContent(arg: { timeText: string, event: { extendedProps: Record<string, unknown> } }) {
  const reservation = arg.event.extendedProps.reservation as CalendarReservationItem | undefined
  if (!reservation) {
    return {
      html: [
        '<div class="calendar-event-rich">',
        `  <div class="calendar-event-rich__time">${escapeHtml(arg.timeText)}</div>`,
        '  <div class="calendar-event-rich__title"></div>',
        "</div>"
      ].join("")
    }
  }

  if (!viewMyReservationsOnly.value) {
    return {
      html: [
        '<div class="calendar-event-rich">',
        `  <div class="calendar-event-rich__time">${escapeHtml(arg.timeText)}</div>`,
        `  <div class="calendar-event-rich__title">${escapeHtml(reservation.title)}</div>`,
        "</div>"
      ].join("")
    }
  }

  return {
    html: [
      '<div class="calendar-event-rich">',
      `  <div class="calendar-event-rich__time">${escapeHtml(arg.timeText)}</div>`,
      `  <div class="calendar-event-rich__title">${escapeHtml(reservation.title)}</div>`,
      `  <div class="calendar-event-rich__room">${escapeHtml(reservation.roomName)}</div>`,
      "</div>"
    ].join("")
  }
}

function padNumber(value: number) {
  return String(value).padStart(2, "0")
}

function formatStatus(status: ReservationStatus) {
  const map: Record<ReservationStatus, string> = {
    PENDING: "待审核",
    ACTIVE: "进行中",
    ENDED: "已结束",
    CANCELLED: "已取消",
    REJECTED: "已驳回",
    EXCEPTION: "异常"
  }
  return map[status]
}

function getStatusClass(status: ReservationStatus) {
  return `is-${status.toLowerCase()}`
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

function formatParticipantNames(reservation: CalendarReservationItem | null | undefined) {
  const names = reservation?.participants?.map(item => item.displayName || item.username).filter(Boolean) ?? []
  return names.length > 0 ? names.join("、") : "暂无额外参会人"
}
</script>

<template>
  <div class="calendar-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Reservation Atelier</p>
        <h1 class="page-hero-title">预约日历</h1>
      </div>
      <div class="hero-side">
        <div class="hero-metrics">
          <article v-for="item in metrics" :key="item.label" class="metric-item">
            <div class="metric-icon"><el-icon><component :is="item.icon" /></el-icon></div>
            <div>
              <div class="metric-label">{{ item.label }}</div>
              <div class="metric-value">{{ item.value }}</div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="workspace">
      <aside class="sidebar">
        <div class="filter-shell">
          <div class="panel-head">
            <div>
              <p class="panel-kicker">Filters</p>
              <h2>筛选</h2>
            </div>
          </div>

          <div class="filter-stack">
            <el-select
              v-model="selectedRoomId"
              class="filter-field"
              placeholder="选择会议室"
              :disabled="viewMyReservationsOnly"
            >
              <el-option v-for="room in sortedRoomOptions" :key="room.id" :label="room.name" :value="room.id" />
            </el-select>
            <el-select v-model="selectedStatus" class="filter-field">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>

        <div class="insight-block">
          <div class="insight-head">
            <div>
              <p class="panel-kicker">Room Insights</p>
              <h3>空间速览</h3>
            </div>
          </div>
          <div class="room-insight-list">
            <div v-for="room in roomInsights" :key="room.id" class="room-insight-item">
              <div>
                <div class="room-name">{{ room.name }}</div>
                <div class="room-meta">{{ room.location }}</div>
              </div>
              <div class="room-count" :class="{ muted: room.status !== 'AVAILABLE' }">{{ room.count }} 场</div>
            </div>
          </div>
        </div>
      </aside>

      <main class="calendar-panel">
        <div class="calendar-toolbar">
          <div class="toolbar-top">
            <div class="toolbar-row">
              <button type="button" class="ghost-button is-icon" aria-label="上一段" @click="navigateCalendar('prev')">
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <button type="button" class="ghost-button is-icon" aria-label="今天" @click="navigateCalendar('today')">
                <span class="toolbar-dot"></span>
              </button>
              <button type="button" class="ghost-button is-icon" aria-label="下一段" @click="navigateCalendar('next')">
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <div class="toolbar-legend">
              <label class="toolbar-toggle">
                <span>只看我的预约</span>
                <el-switch v-model="viewMyReservationsOnly" />
              </label>
              <span class="toolbar-legend-item"><span class="legend-dot is-pending" />待审核</span>
              <span class="toolbar-legend-item"><span class="legend-dot is-active" />进行中</span>
              <span class="toolbar-legend-item"><span class="legend-dot is-ended" />已结束</span>
              <span class="toolbar-legend-item"><span class="legend-dot is-cancelled" />已取消</span>
              <span class="toolbar-legend-item"><span class="legend-dot is-rejected" />已驳回</span>
              <span class="toolbar-legend-item"><span class="legend-dot is-exception" />异常</span>
            </div>
          </div>
          <div class="toolbar-title">{{ currentTitle }}</div>
          <div class="view-switcher">
            <button
              v-for="item in viewOptions"
              :key="item.value"
              type="button"
              class="view-button"
              :class="{ active: currentView === item.value }"
              @click="changeView(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="calendar-surface" v-loading="loading">
          <FullCalendar ref="calendarRef" :options="calendarOptions" />
        </div>
      </main>
    </section>

    <el-dialog
      v-model="detailDialogVisible"
      width="min(860px, calc(100vw - 32px))"
      class="reservation-detail-dialog"
      destroy-on-close
      align-center
    >
      <template #header>
        <div v-if="selectedReservation" class="dialog-header">
          <div>
            <p class="detail-code">{{ selectedReservation.reservationNo }}</p>
            <h3>{{ selectedReservation.title }}</h3>
          </div>
          <span class="status-pill" :class="getStatusClass(selectedReservation.status)">
            {{ formatStatus(selectedReservation.status) }}
          </span>
        </div>
      </template>

      <div v-if="selectedReservation" class="detail-card dialog-card">
        <div class="detail-overview">
          <div class="overview-pill">
            <span>预约日期</span>
            <strong>{{ selectedDateText }}</strong>
          </div>
          <div class="overview-pill">
            <span>会议时段</span>
            <strong>{{ formatTimeRange(selectedReservation.startTime, selectedReservation.endTime) }}</strong>
          </div>
          <div class="overview-pill">
            <span>预约时长</span>
            <strong>{{ selectedDurationText }}</strong>
          </div>
          <div class="overview-pill">
            <span>设备数量</span>
            <strong>{{ selectedDeviceCount }}</strong>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-cell"><span>预约编号</span><strong>{{ selectedReservation.reservationNo }}</strong></div>
          <div class="detail-cell"><span>会议室</span><strong>{{ selectedReservation.roomName }}</strong></div>
          <div class="detail-cell"><span>房间编号</span><strong>{{ selectedRoomCode }}</strong></div>
          <div class="detail-cell"><span>所在区域</span><strong>{{ selectedRoomLocation || "--" }}</strong></div>
          <div class="detail-cell"><span>可容纳人数</span><strong>{{ selectedRoomCapacityText }}</strong></div>
          <div class="detail-cell"><span>组织人</span><strong>{{ selectedReservation.organizerName }}</strong></div>
          <div class="detail-cell"><span>参会人数</span><strong>{{ selectedReservation.attendees }} 人</strong></div>
          <div class="detail-cell"><span>状态</span><strong>{{ formatStatus(selectedReservation.status) }}</strong></div>
        </div>

        <div v-if="selectedRoomDescription" class="detail-section">
          <div class="detail-title">空间说明</div>
          <div class="detail-note-card">{{ selectedRoomDescription }}</div>
        </div>

        <div class="detail-section">
          <div class="detail-title"><el-icon><User /></el-icon>具体参会人</div>
          <div class="detail-note-card">{{ formatParticipantNames(selectedReservation) }}</div>
        </div>

        <div class="detail-section">
          <div class="detail-title"><el-icon><Monitor /></el-icon>设备需求</div>
          <div v-if="selectedReservation.devices.length > 0" class="device-detail-list">
            <div
              v-for="device in selectedReservation.devices"
              :key="device.id"
              class="device-detail-item"
            >
              <div>
                <div class="device-name">{{ device.name }}</div>
                <div class="device-code">{{ device.deviceCode }}</div>
              </div>
              <div class="device-side">
                <span class="device-qty">x {{ device.quantity }}</span>
                <span class="device-state" :class="{ disabled: device.status === 'DISABLED' }">
                  {{ device.status === "DISABLED" ? "停用" : "可用" }}
                </span>
              </div>
            </div>
          </div>
          <div v-else class="detail-note-card is-muted">当前预约没有申请额外设备。</div>
        </div>

        <div class="detail-section">
          <div class="detail-title"><el-icon><User /></el-icon>{{ selectedReservation.status === "CANCELLED" ? "取消原因" : "补充说明" }}</div>
          <div class="detail-note-card" :class="{ 'is-muted': !selectedReservation.cancelReason }">
            {{ selectedReservation.cancelReason || "当前预约没有补充说明。" }}
          </div>
        </div>
      </div>
    </el-dialog>

    <ReservationCreateDialog
      v-model="reservationDialogVisible"
      :room="activeFilterRoom"
      :rooms="roomOptions"
      :preset="reservationDialogPreset"
      @submitted="handleReservationSubmitted"
    />
  </div>
</template>

<style lang="scss" scoped>
.calendar-page {
  --page-topbar-columns: minmax(0, 1fr) minmax(560px, 1.18fr);
  --page-topbar-columns-md: 1fr;
  --page-topbar-metrics-columns: repeat(4, minmax(0, 1fr));
  --page-topbar-metrics-columns-md: repeat(2, minmax(0, 1fr));
  --page-topbar-metrics-columns-sm: 1fr;
  --text-main: #17324d;
  --text-soft: #6f8094;
  --panel-bg: rgba(255, 255, 255, 0.68);
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at 0% 10%, rgba(144, 192, 255, 0.26), transparent 24%),
    radial-gradient(circle at 100% 0%, rgba(134, 231, 214, 0.22), transparent 24%),
    radial-gradient(circle at 82% 72%, rgba(255, 193, 218, 0.18), transparent 18%),
    linear-gradient(180deg, #fbfaf7 0%, #f8f7f3 100%);
  color: var(--text-main);
}

.metric-item,
.sidebar,
.calendar-panel {
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 28px;
  background: var(--panel-bg);
  box-shadow: 0 24px 60px rgba(61, 83, 109, 0.12);
  backdrop-filter: blur(20px);
}

.workspace {
  display: grid;
  grid-template-columns: minmax(280px, 0.72fr) minmax(0, 2.28fr);
  gap: 16px;
  margin-top: 16px;
  align-items: start;
  animation: rise-in 0.72s ease;
}

.sidebar,
.calendar-panel {
  padding: 16px;
}

.sidebar {
  display: grid;
  gap: 14px;
  align-content: start;
}

.calendar-panel {
  display: flex;
  flex-direction: column;
}

.filter-shell,
.insight-block {
  border-radius: 24px;
  border: 1px solid rgba(219, 229, 241, 0.72);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(244, 248, 253, 0.7));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 16px 32px rgba(91, 117, 154, 0.08);
}

.filter-shell {
  padding: 14px;
}

.panel-head {
  margin-bottom: 10px;
}

.panel-head h2 {
  margin: 0;
  font-size: 23px;
  line-height: 1.08;
  letter-spacing: -0.03em;
  color: var(--text-main);
}

.panel-kicker {
  margin: 0 0 6px;
  color: #7c90a7;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.filter-stack,
.room-insight-list {
  display: grid;
  gap: 8px;
}

.filter-field {
  width: 100%;
}

.filter-field :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 14px;
  box-shadow: none;
  background: rgba(255, 255, 255, 0.8);
}

.detail-cell,
.room-insight-item {
  background: rgba(255, 255, 255, 0.56);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex: none;
}

.legend-dot.is-pending {
  background: #e2a54b;
}

.legend-dot.is-active {
  background: #58cbb3;
}

.legend-dot.is-ended {
  background: #5e88ff;
}

.legend-dot.is-cancelled {
  background: #f59fba;
}

.legend-dot.is-rejected {
  background: #d66a82;
}

.legend-dot.is-exception {
  background: #df8b45;
}

.insight-block {
  padding: 12px;
}

.insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
  color: #69798e;
}

.insight-head h3 {
  margin: 0;
  font-size: 23px;
  line-height: 1.08;
  letter-spacing: -0.03em;
  color: var(--text-main);
}

.room-insight-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px;
  border-radius: 16px;
}

.room-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.room-meta {
  margin-top: 2px;
  font-size: 11px;
  color: #7a8aa0;
}

.room-count {
  min-width: 56px;
  padding: 7px 8px;
  border-radius: 999px;
  background: rgba(90, 201, 180, 0.14);
  color: #0f8b73;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
}

.room-count.muted {
  background: rgba(130, 145, 164, 0.14);
  color: #708297;
}

.calendar-toolbar {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  color: #6f8094;
  font-size: 11px;
  line-height: 1;
}

.toolbar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #58708d;
  font-size: 12px;
  line-height: 1;
}

.toolbar-toggle :deep(.el-switch) {
  --el-switch-on-color: #6f9bff;
  --el-switch-off-color: rgba(151, 170, 197, 0.62);
}

.toolbar-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.toolbar-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  text-align: center;
}

.ghost-button,
.view-button {
  border: none;
  cursor: pointer;
}

.ghost-button {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  color: #44607f;
}

.ghost-button.is-icon {
  width: 34px;
  height: 34px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(182, 200, 225, 0.4);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    0 8px 18px rgba(112, 138, 177, 0.12);
}

.ghost-button.is-icon :deep(.el-icon) {
  font-size: 15px;
}

.toolbar-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 0 0 4px rgba(68, 96, 127, 0.12);
}

.view-switcher {
  display: inline-flex;
  width: fit-content;
  margin: 0 auto;
  padding: 5px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(236, 244, 255, 0.9));
  border: 1px solid rgba(141, 170, 214, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 10px 24px rgba(126, 154, 196, 0.14);
}

.view-button {
  min-width: 58px;
  padding: 9px 16px;
  border-radius: 999px;
  background: transparent;
  color: #70839a;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  transition:
    color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.view-button.active {
  background: linear-gradient(135deg, #7aa8ff, #65d4bf);
  color: #fff;
  box-shadow: 0 8px 18px rgba(102, 151, 232, 0.26);
}

.view-button:not(.active):hover {
  color: #496784;
  background: rgba(124, 165, 255, 0.1);
  transform: translateY(-1px);
}

.calendar-surface {
  padding: 14px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.58);
}

.detail-card {
  margin-top: 14px;
  padding: 14px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(243, 248, 252, 0.66));
}

.dialog-card {
  margin-top: 0;
  animation: dialog-rise-in 0.26s ease;
}

.detail-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.overview-pill {
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
}

.overview-pill span {
  display: block;
  font-size: 12px;
  color: #7f90a4;
}

.overview-pill strong {
  display: block;
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.5;
}

.detail-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-top h3 {
  margin: 8px 0 0;
  font-size: 22px;
}

.dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-right: 24px;
}

.dialog-header h3 {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.12;
}

.detail-code {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #8593a6;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 78px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-pill.is-pending {
  background: rgba(226, 165, 75, 0.16);
  color: #a76410;
}

.status-pill.is-active {
  background: rgba(88, 203, 179, 0.16);
  color: #14836d;
}

.status-pill.is-ended {
  background: rgba(94, 136, 255, 0.14);
  color: #4669da;
}

.status-pill.is-cancelled {
  background: rgba(245, 159, 186, 0.16);
  color: #d5557d;
}

.status-pill.is-rejected {
  background: rgba(214, 106, 130, 0.16);
  color: #c64c68;
}

.status-pill.is-exception {
  background: rgba(223, 139, 69, 0.16);
  color: #b45d18;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.detail-cell {
  padding: 12px;
  border-radius: 18px;
}

.detail-cell span {
  display: block;
  font-size: 12px;
  color: #8190a4;
}

.detail-cell strong {
  display: block;
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.55;
}

.detail-section {
  margin-top: 12px;
}

.detail-note-card {
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.62);
  font-size: 13px;
  line-height: 1.8;
  color: #4f6177;
}

.detail-note-card.is-muted {
  color: #7c8da1;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #627389;
}

.device-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.device-detail-list {
  display: grid;
  gap: 10px;
}

.device-detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
}

.device-name {
  font-size: 14px;
  font-weight: 600;
}

.device-code {
  margin-top: 4px;
  font-size: 12px;
  color: #7f90a4;
  letter-spacing: 0.06em;
}

.device-side {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.device-qty {
  font-size: 13px;
  font-weight: 600;
  color: #27415f;
}

.device-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(88, 203, 179, 0.16);
  color: #14836d;
  font-size: 12px;
}

.device-state.disabled {
  background: rgba(127, 141, 158, 0.16);
  color: #6a7b90;
}

.device-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(94, 136, 255, 0.14);
  color: #3f63d8;
  font-size: 12px;
}

.device-chip.disabled {
  background: rgba(127, 141, 158, 0.14);
  color: #6a7b90;
}

.detail-empty {
  font-size: 13px;
  line-height: 1.8;
  color: #6f8094;
}

:deep(.fc) {
  --fc-border-color: rgba(23, 50, 77, 0.08);
  --fc-button-bg-color: transparent;
  --fc-button-border-color: transparent;
  --fc-today-bg-color: rgba(96, 140, 255, 0.07);
}

:deep(.fc-theme-standard td),
:deep(.fc-theme-standard th) {
  border-color: rgba(23, 50, 77, 0.08);
}

:deep(.fc-col-header-cell) {
  padding: 8px 0;
  background: rgba(255, 255, 255, 0.42);
  font-size: 12px;
}

:deep(.fc .fc-daygrid-day-number),
:deep(.fc .fc-col-header-cell-cushion) {
  color: #5c6f85;
  text-decoration: none;
}

:deep(.fc-timegrid-axis-cushion),
:deep(.fc-timegrid-slot-label-cushion) {
  font-size: 11px;
  color: #617489;
}

:deep(.fc .fc-scrollgrid) {
  border-radius: 18px;
  overflow: hidden;
}

:deep(.fc-timegrid-slot),
:deep(.fc-timegrid-axis) {
  height: 1.5em;
}

:deep(.fc-event) {
  border: none;
  border-radius: 16px;
  padding: 0;
  overflow: hidden;
  box-shadow: 0 12px 24px rgba(47, 86, 136, 0.14);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    filter 0.18s ease;
}

:deep(.fc-event:hover) {
  transform: translateY(-1px);
  box-shadow: 0 16px 28px rgba(47, 86, 136, 0.18);
  z-index: 4;
}

:deep(.fc-timegrid-event .fc-event-main) {
  display: flex;
  height: 100%;
  width: 100%;
  align-items: stretch;
  padding: 0;
}

:deep(.fc-timegrid-event .fc-event-main-frame) {
  display: flex;
  flex: 1;
  height: 100%;
  min-height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 52px;
  padding: 6px 10px;
  text-align: center;
}

:deep(.fc-timegrid-event .fc-event-time),
:deep(.fc-timegrid-event .fc-event-title) {
  width: 100%;
  text-align: center;
}

:deep(.fc-timegrid-event .fc-event-time) {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.15;
}

:deep(.fc-timegrid-event .fc-event-title) {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
  white-space: normal;
  word-break: break-word;
}

:deep(.calendar-event-rich) {
  display: grid;
  width: 100%;
  gap: 2px;
  text-align: center;
}

:deep(.calendar-event-rich__time) {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.1;
}

:deep(.calendar-event-rich__title) {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.15;
}

:deep(.calendar-event-rich__room) {
  font-size: 10px;
  line-height: 1.1;
  opacity: 0.86;
}

:deep(.fc-timegrid-event-harness) {
  margin-right: 3px;
}

:deep(.event-tone-pending) {
  background: linear-gradient(135deg, rgba(226, 165, 75, 0.94), rgba(250, 200, 103, 0.94));
}

:deep(.event-tone-active) {
  background: linear-gradient(135deg, rgba(72, 196, 169, 0.94), rgba(104, 181, 248, 0.94));
}

:deep(.event-tone-ended) {
  background: linear-gradient(135deg, rgba(116, 153, 248, 0.94), rgba(109, 195, 255, 0.94));
}

:deep(.event-tone-cancelled) {
  background: linear-gradient(135deg, rgba(241, 149, 185, 0.94), rgba(244, 186, 152, 0.94));
}

:deep(.event-tone-rejected) {
  background: linear-gradient(135deg, rgba(214, 106, 130, 0.94), rgba(241, 149, 185, 0.94));
}

:deep(.event-tone-exception) {
  background: linear-gradient(135deg, rgba(223, 139, 69, 0.94), rgba(244, 186, 152, 0.94));
}

:deep(.fc-daygrid-event-dot) {
  display: none;
}

:deep(.reservation-detail-dialog .el-dialog) {
  border-radius: 26px;
  overflow: hidden;
  transform-origin: center center;
  animation: dialog-pop-in 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}

:deep(.reservation-detail-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 20px 22px 0;
}

:deep(.reservation-detail-dialog .el-dialog__body) {
  padding: 18px 22px 22px;
}

@keyframes dialog-pop-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes dialog-rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media screen and (max-width: 1480px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .sidebar,
  .calendar-panel {
    height: auto;
  }
}

@media screen and (max-width: 1180px) {
  .detail-overview,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .calendar-page {
    padding: 16px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .view-switcher {
    width: 100%;
  }

  .view-button {
    flex: 1;
  }

  .toolbar-top {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-legend {
    justify-content: flex-start;
  }
}
</style>
