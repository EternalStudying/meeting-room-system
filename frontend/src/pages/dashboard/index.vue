<script lang="ts" setup>
import { computed, onMounted, ref } from "vue"
import { Calendar, DataAnalysis, Flag, OfficeBuilding } from "@element-plus/icons-vue"
import { getOverviewApi, getQuoteApi } from "@/common/apis/dashboard"
import type { OverviewData, OverviewRoomStatusItem, OverviewScheduleItem, QuoteData } from "@/common/apis/dashboard/type"

type SummaryTone = "stable" | "focus" | "busy" | "open" | "mute"

interface SummaryCard {
  label: string
  value: string
  detail: string
  icon: typeof Calendar
}

interface ScheduleCard {
  id: number | string
  time: string
  title: string
  meta: string
  level: SummaryTone
}

interface RoomCard {
  name: string
  state: string
  detail: string
  tone: SummaryTone
}

const today = new Date()
const dateLabel = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long"
}).format(today)

const loading = ref(false)
const overview = ref<OverviewData | null>(null)
const quoteData = ref<QuoteData | null>(null)

const summary = computed<SummaryCard[]>(() => {
  const summaryData = overview.value?.summary
  return [
    {
      label: "今日会议",
      value: formatNumber(summaryData?.todayMeetingCount),
      detail: buildMeetingDetail(summaryData?.todayMeetingCount),
      icon: Calendar
    },
    {
      label: "使用率",
      value: formatPercent(summaryData?.utilizationRate),
      detail: overview.value?.peakWindow ? `高峰窗口 ${overview.value.peakWindow}` : "高峰窗口待返回",
      icon: DataAnalysis
    },
    {
      label: "待处理事项",
      value: formatNumber(summaryData?.pendingCount),
      detail: buildPendingDetail(summaryData?.pendingCount),
      icon: Flag
    },
    {
      label: "可用会议室",
      value: formatNumber(summaryData?.availableRoomCount),
      detail: buildRoomDetail(summaryData?.totalRoomCount),
      icon: OfficeBuilding
    }
  ]
})

const schedule = computed<ScheduleCard[]>(() => {
  return (overview.value?.todaySchedules ?? []).map((item) => ({
    id: item.id,
    time: formatTime(item.startTime),
    title: item.title,
    meta: buildScheduleMeta(item),
    level: getScheduleTone(item)
  }))
})

const rooms = computed<RoomCard[]>(() => {
  return (overview.value?.roomStatuses ?? []).map((item) => ({
    name: item.roomName,
    state: item.displayStatus,
    detail: item.detail || "状态待更新",
    tone: getRoomTone(item)
  }))
})

const tasks = computed(() => overview.value?.todoItems ?? [])

const quote = computed(() => quoteData.value?.quote || "")
const quoteAuthor = computed(() => quoteData.value?.quoteAuthor || "")
const peakWindow = computed(() => overview.value?.peakWindow || "--")

onMounted(async () => {
  loading.value = true
  const [overviewResult, quoteResult] = await Promise.allSettled([getOverviewApi(), getQuoteApi()])
  if (overviewResult.status === "fulfilled") {
    overview.value = overviewResult.value.data
  } else {
    overview.value = null
  }
  if (quoteResult.status === "fulfilled") {
    quoteData.value = quoteResult.value.data
  } else {
    quoteData.value = null
  }
  loading.value = false
})

function formatNumber(value?: number) {
  return value === undefined || value === null ? "--" : String(value)
}

function formatPercent(value?: number) {
  return value === undefined || value === null ? "--" : `${value}%`
}

function buildMeetingDetail(todayMeetingCount?: number) {
  if (todayMeetingCount === undefined || todayMeetingCount === null) return "今日会议数据待返回"
  if (todayMeetingCount === 0) return "今天暂无会议安排"
  if (todayMeetingCount >= 10) return "今天会议较密集"
  if (todayMeetingCount >= 5) return "今天会议节奏平稳"
  return "今天会议安排较轻"
}

function buildPendingDetail(pendingCount?: number) {
  if (pendingCount === undefined || pendingCount === null) return "待处理数据待返回"
  if (pendingCount === 0) return "当前没有待处理事项"
  if (pendingCount >= 5) return "需要优先处理现场事项"
  return "建议今天内完成"
}

function buildRoomDetail(totalRoomCount?: number) {
  if (totalRoomCount === undefined || totalRoomCount === null) return "会议室总数待返回"
  return `总计 ${totalRoomCount} 间会议室`
}

function formatTime(value?: string) {
  if (!value) return "--:--"
  const matched = value.match(/(\d{2}):(\d{2})/)
  return matched ? `${matched[1]}:${matched[2]}` : value
}

function buildScheduleMeta(item: OverviewScheduleItem) {
  const metaParts = [item.roomName]
  if (item.attendees !== undefined && item.attendees !== null) metaParts.push(`${item.attendees} 人`)
  if (item.deviceSummary) metaParts.push(item.deviceSummary)
  return metaParts.join(" · ")
}

function getScheduleTone(item: OverviewScheduleItem): SummaryTone {
  if ((item.attendees ?? 0) >= 15) return "busy"
  if ((item.attendees ?? 0) >= 8) return "focus"
  return "stable"
}

function getRoomTone(item: OverviewRoomStatusItem): SummaryTone {
  if (item.status === "MAINTENANCE") return "mute"
  if (item.status === "FREE") return "open"
  if (item.displayStatus.includes("即将")) return "focus"
  return "busy"
}

function toneClass(level: SummaryTone) {
  return `tone-${level}`
}
</script>

<template>
  <div v-loading="loading" class="overview-page">
    <section class="hero">
      <div class="hero-copy">
        <div class="eyebrow">{{ dateLabel }}</div>
        <h1 class="headline page-hero-title">
          {{ quote || " " }}
          <span v-if="quoteAuthor" class="quote-author">—— {{ quoteAuthor }}</span>
        </h1>
      </div>
      <div class="hero-side">
        <div class="hero-metric">
          <span>高峰窗口</span>
          <strong>{{ peakWindow }}</strong>
        </div>
      </div>
    </section>

    <section class="summary-grid">
      <article v-for="item in summary" :key="item.label" class="summary-item">
        <div class="summary-icon">
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
        </div>
        <div class="summary-body">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <div class="summary-detail">{{ item.detail }}</div>
        </div>
      </article>
    </section>

    <section class="workspace">
      <div class="schedule-panel">
        <div class="section-head">
          <div>
            <p class="panel-kicker">Daily Agenda</p>
            <h2>今日排期</h2>
          </div>
        </div>
        <div v-if="schedule.length > 0" class="schedule-list">
          <article
            v-for="item in schedule"
            :key="item.id"
            class="schedule-item"
            :class="toneClass(item.level)"
          >
            <div class="schedule-time">{{ item.time }}</div>
            <div class="schedule-content">
              <div class="schedule-title">{{ item.title }}</div>
              <div class="schedule-meta">{{ item.meta }}</div>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无排期数据" />
      </div>

      <div class="sidebar-panel">
        <section class="room-panel">
          <div class="section-head compact">
            <div>
              <p class="panel-kicker">Room Status</p>
              <h2>空间状态</h2>
            </div>
          </div>
          <div v-if="rooms.length > 0" class="room-list">
            <div v-for="room in rooms" :key="room.name" class="room-item">
              <div>
                <div class="room-name">{{ room.name }}</div>
                <div class="room-detail">{{ room.detail }}</div>
              </div>
              <span class="room-badge" :class="toneClass(room.tone)">
                {{ room.state }}
              </span>
            </div>
          </div>
          <el-empty v-else description="暂无空间状态数据" />
        </section>

        <section class="task-panel">
          <div class="section-head compact">
            <div>
              <p class="panel-kicker">Action Queue</p>
              <h2>待处理</h2>
            </div>
          </div>
          <ul v-if="tasks.length > 0" class="task-list">
            <li v-for="task in tasks" :key="task">{{ task }}</li>
          </ul>
          <el-empty v-else description="暂无待处理事项" />
        </section>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.overview-page {
  min-height: 100%;
  padding: 24px 24px 32px;
  background:
    radial-gradient(circle at top left, rgba(31, 111, 235, 0.10), transparent 28%),
    linear-gradient(180deg, #f7f9fc 0%, #f3f6fb 100%);
  color: #122033;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.8fr);
  gap: 20px;
  align-items: center;
  padding: 28px 0 24px;
  border-bottom: 1px solid rgba(18, 32, 51, 0.08);
  animation: rise-in 0.5s ease;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6b7a90;
}

.headline {
  margin: 10px 0 12px;
  max-width: 30ch;
  min-height: calc(1.1em * 2);
}

.quote-author {
  display: inline;
  margin-left: 8px;
  font-size: 15px;
  line-height: 1.8;
  color: #5e6c82;
  font-weight: 400;
  white-space: nowrap;
  vertical-align: baseline;
}

.hero-side {
  display: flex;
  align-items: center;
  justify-content: center;
  justify-self: end;
}

.hero-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 188px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #122033;
  color: #f7fbff;
  box-shadow: 0 18px 40px rgba(18, 32, 51, 0.12);

  span {
    font-size: 12px;
    color: rgba(247, 251, 255, 0.7);
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  strong {
    font-size: 22px;
    line-height: 1.1;
    font-weight: 700;
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 22px;
  animation: rise-in 0.65s ease;
}

.summary-item {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 14px;
  padding: 18px 18px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(18, 32, 51, 0.06);
  backdrop-filter: blur(10px);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 14px 30px rgba(18, 32, 51, 0.08);
    border-color: rgba(31, 111, 235, 0.16);
  }
}

.summary-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: #e9f1ff;
  color: #1f6feb;
  font-size: 20px;
}

.summary-label {
  font-size: 13px;
  color: #64748b;
}

.summary-value {
  margin-top: 4px;
  font-size: 30px;
  line-height: 1.1;
  font-weight: 700;
}

.summary-detail {
  margin-top: 8px;
  font-size: 13px;
  color: #66758b;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.9fr);
  gap: 18px;
  margin-top: 22px;
  animation: rise-in 0.8s ease;
}

.schedule-panel,
.room-panel,
.task-panel {
  padding: 20px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(18, 32, 51, 0.06);
  border-radius: 22px;
  backdrop-filter: blur(12px);
}

.sidebar-panel {
  display: grid;
  grid-template-rows: auto auto;
  gap: 18px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 23px;
    line-height: 1.08;
    letter-spacing: -0.03em;
    font-weight: 700;
    color: #17324a;
  }
}

.panel-kicker {
  margin: 0 0 6px;
  color: #7c90a7;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.schedule-list {
  display: grid;
  gap: 10px;
}

.schedule-item {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 14px;
  align-items: center;
  padding: 14px 14px 14px 0;
  border-bottom: 1px solid rgba(18, 32, 51, 0.06);
  transition: transform 0.2s ease;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  &:hover {
    transform: translateX(4px);
  }
}

.schedule-time {
  font-size: 26px;
  line-height: 1;
  font-weight: 700;
  color: #1d3557;
}

.schedule-title {
  font-size: 16px;
  font-weight: 600;
}

.schedule-meta {
  margin-top: 4px;
  font-size: 13px;
  color: #6a788d;
}

.room-list,
.task-list {
  display: grid;
  gap: 10px;
}

.room-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(18, 32, 51, 0.06);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.room-name {
  font-size: 15px;
  font-weight: 600;
}

.room-detail {
  margin-top: 4px;
  font-size: 12px;
  color: #6a788d;
}

.room-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 78px;
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.task-list {
  margin: 0;
  padding-left: 18px;

  li {
    color: #415066;
    line-height: 1.75;
    padding-left: 4px;
  }
}

.tone-stable {
  color: #1d3557;
}

.tone-focus {
  color: #1565c0;
}

.tone-busy {
  color: #d9485f;
}

.room-badge.tone-open {
  background: #eaf7ef;
  color: #237a45;
}

.room-badge.tone-busy {
  background: #fff0f2;
  color: #c83752;
}

.room-badge.tone-focus {
  background: #edf4ff;
  color: #1968d2;
}

.room-badge.tone-mute {
  background: #f1f4f8;
  color: #63748a;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media screen and (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .overview-page {
    padding: 18px 16px 26px;
  }

  .hero {
    grid-template-columns: 1fr;
  }

  .hero-side {
    justify-self: start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .schedule-item {
    grid-template-columns: 64px 1fr;
  }

  .schedule-time {
    font-size: 22px;
  }
}
</style>
