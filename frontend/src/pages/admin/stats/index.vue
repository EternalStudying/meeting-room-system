<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import dayjs from "dayjs"
import * as echarts from "echarts"
import { Calendar, CircleCheck, Connection, DataAnalysis, OfficeBuilding, Warning } from "@element-plus/icons-vue"
import { getAdminStatsApi } from "@/common/apis/stats"
import type {
  AdminStatsAlertItem,
  AdminStatsKpi,
  AdminStatsTone,
  AdminStatsOverview,
  AdminStatsReservationItem
} from "@/common/apis/stats/type"
import { TABLE_PAGE_SIZE_OPTIONS } from "@/common/constants/pagination"

type WindowOption = { label: string, value: 1 | 7 | 30 }

interface KpiCard extends AdminStatsKpi {
  icon: typeof Calendar
}

const windowOptions: WindowOption[] = [
  { label: "近 1 天", value: 1 },
  { label: "近 7 天", value: 7 },
  { label: "近 30 天", value: 30 }
]

const selectedWindow = ref<1 | 7 | 30>(30)
const alertsPage = ref(1)
const reservationsPage = ref(1)
const pageSizeOptions = TABLE_PAGE_SIZE_OPTIONS
const alertsPageSize = ref(5)
const reservationsPageSize = ref(5)
const detailTableHeight = 312

const trendChartRef = ref<HTMLDivElement | null>(null)
const heatmapChartRef = ref<HTMLDivElement | null>(null)
const roomRankChartRef = ref<HTMLDivElement | null>(null)
const deviceRankChartRef = ref<HTMLDivElement | null>(null)
const coverageChartRef = ref<HTMLDivElement | null>(null)

let trendChart: echarts.ECharts | null = null
let heatmapChart: echarts.ECharts | null = null
let roomRankChart: echarts.ECharts | null = null
let deviceRankChart: echarts.ECharts | null = null
let coverageChart: echarts.ECharts | null = null
let mounted = false

function createEmptyKpi(key: string, label: string, unit = "场", tone: AdminStatsTone = "steel"): AdminStatsKpi {
  return {
    key,
    label,
    value: 0,
    unit,
    detail: "",
    tone
  }
}

function createEmptyOverview(days: 1 | 7 | 30): AdminStatsOverview {
  return {
    recentDays: days,
    kpis: {
      totalReservations: createEmptyKpi("totalReservations", "预约总数"),
      activeReservations: createEmptyKpi("activeReservations", "进行中预约", "场", "mint"),
      cancelledReservations: createEmptyKpi("cancelledReservations", "已取消预约", "场", "rose"),
      roomCoverageRate: createEmptyKpi("roomCoverageRate", "会议室配置覆盖率", "%", "amber"),
      deviceCoverageRate: createEmptyKpi("deviceCoverageRate", "设备绑定覆盖率", "%"),
      maintenanceRooms: createEmptyKpi("maintenanceRooms", "维护中会议室", "间", "rose")
    },
    trend: [],
    heatmap: [],
    roomUsageRanks: [],
    deviceUsageRanks: [],
    coverage: {
      configuredRoomCount: 0,
      unconfiguredRoomCount: 0
    },
    alerts: [],
    reservations: [],
    staticSummary: {
      totalRooms: 0,
      availableRooms: 0,
      maintenanceRooms: 0,
      totalDevices: 0,
      enabledDevices: 0,
      disabledDevices: 0
    }
  }
}

const overview = ref<AdminStatsOverview>(createEmptyOverview(selectedWindow.value))

const kpiCards = computed<KpiCard[]>(() => [
  { ...overview.value.kpis.totalReservations, icon: Calendar },
  { ...overview.value.kpis.activeReservations, icon: CircleCheck },
  { ...overview.value.kpis.cancelledReservations, icon: Warning },
  { ...overview.value.kpis.roomCoverageRate, icon: OfficeBuilding },
  { ...overview.value.kpis.deviceCoverageRate, icon: Connection },
  { ...overview.value.kpis.maintenanceRooms, icon: DataAnalysis }
])

const pagedAlerts = computed(() => {
  const start = (alertsPage.value - 1) * alertsPageSize.value
  return overview.value.alerts.slice(start, start + alertsPageSize.value)
})

const pagedReservations = computed(() => {
  const start = (reservationsPage.value - 1) * reservationsPageSize.value
  return overview.value.reservations.slice(start, start + reservationsPageSize.value)
})

const activeWindowIndex = computed(() => windowOptions.findIndex(item => item.value === selectedWindow.value))

const windowThumbStyle = computed(() => ({
  width: `calc((100% - 8px) / ${windowOptions.length})`,
  transform: `translateX(calc(${activeWindowIndex.value} * 100%))`
}))

watch(selectedWindow, () => {
  alertsPage.value = 1
  reservationsPage.value = 1
  void fetchOverview()
})

watch(() => overview.value.alerts.length, (total) => {
  const maxPage = Math.max(1, Math.ceil(total / alertsPageSize.value))
  if (alertsPage.value > maxPage) alertsPage.value = maxPage
})

watch(() => overview.value.reservations.length, (total) => {
  const maxPage = Math.max(1, Math.ceil(total / reservationsPageSize.value))
  if (reservationsPage.value > maxPage) reservationsPage.value = maxPage
})

function handleAlertsPageSizeChange(size: number) {
  alertsPageSize.value = size
  alertsPage.value = 1
}

function handleReservationsPageSizeChange(size: number) {
  reservationsPageSize.value = size
  reservationsPage.value = 1
}

watch(overview, async () => {
  if (!mounted) return
  await nextTick()
  renderCharts()
})

async function fetchOverview() {
  try {
    const response = await getAdminStatsApi(selectedWindow.value)
    overview.value = response.data
  } catch {
    overview.value = createEmptyOverview(selectedWindow.value)
  }
}

function getToneClass(tone: AdminStatsTone) {
  return `tone-${tone}`
}

function getAlertTypeLabel(type: AdminStatsAlertItem["type"]) {
  switch (type) {
    case "room_maintenance":
      return "会议室维护"
    case "room_unbound":
      return "未配置设备"
    case "device_disabled_bound":
      return "停用设备仍绑定"
    case "room_high_cancel":
      return "取消率偏高"
    default:
      return "异常"
  }
}

function getAlertLevelLabel(level: AdminStatsAlertItem["level"]) {
  return level === "danger" ? "高优先" : "注意"
}

function getAlertLevelClass(level: AdminStatsAlertItem["level"]) {
  return level === "danger" ? "is-danger" : "is-warning"
}

function getReservationStatusLabel(status: AdminStatsReservationItem["status"]) {
  switch (status) {
    case "PENDING":
      return "待审核"
    case "ACTIVE":
      return "进行中"
    case "ENDED":
      return "已结束"
    case "CANCELLED":
      return "已取消"
    case "REJECTED":
      return "已驳回"
    case "EXCEPTION":
      return "异常"
    default:
      return status
  }
}

function getReservationStatusClass(status: AdminStatsReservationItem["status"]) {
  if (status === "PENDING") return "is-warning"
  if (status === "ACTIVE") return "is-active"
  if (status === "ENDED") return "is-ended"
  if (status === "EXCEPTION" || status === "REJECTED") return "is-danger"
  return "is-cancelled"
}

function formatRange(startTime: string, endTime: string) {
  const start = dayjs(startTime)
  const end = dayjs(endTime)
  return `${start.format("MM-DD HH:mm")} - ${end.format("HH:mm")}`
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  trendChart ??= echarts.init(trendChartRef.value)

  trendChart.setOption({
    animationDuration: 520,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "axis",
      formatter: (params: Array<{ axisValueLabel: string, data: number }>) => {
        const item = params[0]
        return `${item.axisValueLabel}<br/>预约 ${item.data} 场`
      }
    },
    grid: { top: 28, right: 24, bottom: 24, left: 42 },
    xAxis: {
      type: "category",
      data: overview.value.trend.map(item => item.label),
      axisLine: { lineStyle: { color: "rgba(141, 160, 183, 0.24)" } },
      axisTick: { show: false }
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "rgba(141, 160, 183, 0.14)" } }
    },
    series: [
      {
        type: "line",
        smooth: true,
        symbol: "circle",
        symbolSize: 8,
        lineStyle: { width: 3, color: "#4f7cff" },
        itemStyle: { color: "#4f7cff" },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "rgba(79, 124, 255, 0.34)" },
            { offset: 1, color: "rgba(79, 124, 255, 0.04)" }
          ])
        },
        data: overview.value.trend.map(item => item.reservationCount)
      }
    ]
  })
}

function renderHeatmapChart() {
  if (!heatmapChartRef.value) return
  heatmapChart ??= echarts.init(heatmapChartRef.value)

  const weekdays = [...new Set(overview.value.heatmap.map(item => item.weekday))]
  const hours = [...new Set(overview.value.heatmap.map(item => item.hour))]
  const maxValue = Math.max(...overview.value.heatmap.map(item => item.reservationCount), 1)

  heatmapChart.setOption({
    animationDuration: 520,
    animationEasing: "cubicOut",
    tooltip: {
      formatter: (params: { data: [number, number, number] }) => {
        const [weekdayIndex, hourIndex, count] = params.data
        return `${weekdays[weekdayIndex]} ${hours[hourIndex]}<br/>预约 ${count} 场`
      }
    },
    grid: { top: 18, right: 18, bottom: 32, left: 54 },
    xAxis: {
      type: "category",
      data: weekdays,
      axisTick: { show: false },
      axisLine: { show: false }
    },
    yAxis: {
      type: "category",
      data: hours,
      axisTick: { show: false },
      axisLine: { show: false }
    },
    visualMap: {
      min: 0,
      max: maxValue,
      show: false,
      inRange: {
        color: ["#edf4ff", "#b5d1ff", "#76a8ff", "#4f7cff"]
      }
    },
    series: [
      {
        type: "heatmap",
        data: overview.value.heatmap.map(item => [item.weekdayIndex, item.hourIndex, item.reservationCount]),
        label: {
          show: true,
          color: "#17324a",
          formatter: ({ data }: { data: [number, number, number] }) => (data[2] > 0 ? `${data[2]}` : "")
        },
        itemStyle: {
          borderRadius: 10,
          borderColor: "rgba(255,255,255,0.82)",
          borderWidth: 2
        },
        progressive: 0
      }
    ]
  })
}

function renderRoomRankChart() {
  if (!roomRankChartRef.value) return
  roomRankChart ??= echarts.init(roomRankChartRef.value)

  roomRankChart.setOption({
    animationDuration: 520,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: Array<{
        axisValueLabel: string
        data: {
          roomName: string
          location: string
          reservationCount: number
          activeCount: number
          cancelledCount: number
        }
      }>) => {
        const item = params[0]
        return [
          `<strong>${item.data.roomName}</strong>`,
          item.axisValueLabel,
          item.data.location,
          `预约次数：${item.data.reservationCount}`,
          `进行中：${item.data.activeCount}`,
          `已取消：${item.data.cancelledCount}`
        ].join("<br/>")
      }
    },
    grid: { top: 16, right: 22, bottom: 8, left: 86 },
    xAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "rgba(141, 160, 183, 0.12)" } }
    },
    yAxis: {
      type: "category",
      data: overview.value.roomUsageRanks.map(item => item.roomCode),
      axisTick: { show: false },
      axisLine: { show: false }
    },
    series: [
      {
        type: "bar",
        barWidth: 14,
        data: overview.value.roomUsageRanks.map((item, index) => ({
          value: item.reservationCount,
          itemStyle: {
            color: ["#5f87ff", "#6c9cff", "#7caeff", "#8bbfff", "#9ed0ff", "#b3ddff"][index] ?? "#5f87ff",
            borderRadius: [999, 999, 999, 999]
          },
          ...item
        })),
        label: { show: true, position: "right", color: "#1b3551" }
      }
    ]
  })
}

function renderDeviceRankChart() {
  if (!deviceRankChartRef.value) return
  deviceRankChart ??= echarts.init(deviceRankChartRef.value)

  deviceRankChart.setOption({
    animationDuration: 520,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: Array<{
        axisValueLabel: string
        data: {
          deviceName: string
          deviceCode: string
          usageQuantity: number
          reservationCount: number
          status: "ENABLED" | "DISABLED"
        }
      }>) => {
        const item = params[0]
        return [
          `<strong>${item.data.deviceName}</strong>`,
          `设备编码：${item.data.deviceCode}`,
          `设备状态：${item.data.status === "ENABLED" ? "启用" : "停用"}`,
          `调用数量：${item.data.usageQuantity}`,
          `关联预约：${item.data.reservationCount}`
        ].join("<br/>")
      }
    },
    grid: { top: 16, right: 22, bottom: 8, left: 110 },
    xAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "rgba(141, 160, 183, 0.12)" } }
    },
    yAxis: {
      type: "category",
      data: overview.value.deviceUsageRanks.map(item => item.deviceName),
      axisTick: { show: false },
      axisLine: { show: false }
    },
    series: [
      {
        type: "bar",
        barWidth: 14,
        data: overview.value.deviceUsageRanks.map((item, index) => ({
          value: item.usageQuantity,
          itemStyle: {
            color: ["#4fb9a8", "#5ac7b4", "#66d2be", "#78dcc8", "#8ce6d2", "#a7f0df"][index] ?? "#4fb9a8",
            borderRadius: [999, 999, 999, 999]
          },
          ...item
        })),
        label: { show: true, position: "right", color: "#1b3551" }
      }
    ]
  })
}

function renderCoverageChart() {
  if (!coverageChartRef.value) return
  coverageChart ??= echarts.init(coverageChartRef.value)

  const configured = overview.value.coverage.configuredRoomCount
  const unconfigured = overview.value.coverage.unconfiguredRoomCount
  const total = configured + unconfigured

  coverageChart.setOption({
    animationDuration: 520,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "item",
      formatter: (params: { name: string, value: number }) => `${params.name}<br/>${params.value} 间`
    },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: "#4e667d" }
    },
    graphic: {
      elements: [
        {
          type: "text",
          left: "center",
          top: "41%",
          style: {
            text: `${configured} / ${total}`,
            textAlign: "center",
            fill: "#19324c",
            fontSize: 28,
            fontWeight: 700
          }
        },
        {
          type: "text",
          left: "center",
          top: "53%",
          style: {
            text: "已配置",
            textAlign: "center",
            fill: "#6e8497",
            fontSize: 13,
            fontWeight: 500
          }
        }
      ]
    },
    series: [
      {
        type: "pie",
        radius: ["58%", "76%"],
        center: ["50%", "46%"],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: "#f8fbff", borderWidth: 6 },
        data: [
          { value: configured, name: "已配置会议室", itemStyle: { color: "#4f7cff" } },
          { value: unconfigured, name: "未配置会议室", itemStyle: { color: "#d8e2f2" } }
        ]
      }
    ]
  })
}

function renderCharts() {
  renderTrendChart()
  renderHeatmapChart()
  renderRoomRankChart()
  renderDeviceRankChart()
  renderCoverageChart()
}

function resizeCharts() {
  trendChart?.resize()
  heatmapChart?.resize()
  roomRankChart?.resize()
  deviceRankChart?.resize()
  coverageChart?.resize()
}

function disposeCharts() {
  trendChart?.dispose()
  heatmapChart?.dispose()
  roomRankChart?.dispose()
  deviceRankChart?.dispose()
  coverageChart?.dispose()
  trendChart = null
  heatmapChart = null
  roomRankChart = null
  deviceRankChart = null
  coverageChart = null
}

onMounted(async () => {
  mounted = true
  await fetchOverview()
  await nextTick()
  renderCharts()
  window.addEventListener("resize", resizeCharts)
})

onBeforeUnmount(() => {
  mounted = false
  window.removeEventListener("resize", resizeCharts)
  disposeCharts()
})
</script>

<template>
  <div class="admin-stats-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Operations Report</p>
        <h1 class="page-hero-title">统计分析</h1>
      </div>

      <div class="window-switch-panel">
        <span class="switch-label">统计窗口</span>
        <div class="window-switch">
          <span class="window-thumb" :style="windowThumbStyle" />
          <button
            v-for="item in windowOptions"
            :key="item.value"
            type="button"
            class="window-switch__button"
            :class="{ 'is-active': selectedWindow === item.value }"
            @click="selectedWindow = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </section>

    <section class="kpi-grid">
      <article
        v-for="item in kpiCards"
        :key="item.key"
        class="kpi-card"
        :class="getToneClass(item.tone)"
      >
        <div class="kpi-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="kpi-content">
          <span class="kpi-label">{{ item.label }}</span>
          <strong class="kpi-value">
            {{ item.value }}
            <small v-if="item.unit">{{ item.unit }}</small>
          </strong>
        </div>
      </article>
    </section>

    <section class="chart-grid chart-grid--top">
      <article class="panel chart-panel chart-panel--wide">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Reservation Trend</p>
            <h2>预约趋势</h2>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-box chart-box--trend" />
      </article>

      <article class="panel chart-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Time Heat</p>
            <h2>时段热力</h2>
          </div>
        </div>
        <div ref="heatmapChartRef" class="chart-box chart-box--heatmap" />
      </article>
    </section>

    <section class="chart-grid chart-grid--bottom">
      <article class="panel chart-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Room Ranking</p>
            <h2>会议室使用排行</h2>
          </div>
        </div>
        <div ref="roomRankChartRef" class="chart-box chart-box--rank" />
      </article>

      <article class="panel chart-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Device Ranking</p>
            <h2>设备使用排行</h2>
          </div>
        </div>
        <div ref="deviceRankChartRef" class="chart-box chart-box--rank" />
      </article>

      <article class="panel chart-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Coverage</p>
            <h2>资源覆盖概览</h2>
          </div>
        </div>
        <div ref="coverageChartRef" class="chart-box chart-box--coverage" />
      </article>
    </section>

    <section class="detail-grid">
      <article class="panel detail-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Alerts</p>
            <h2>异常提醒</h2>
          </div>
        </div>

        <Transition name="pagination-switch" mode="out-in">
          <div :key="`alerts-page-${alertsPage}-${alertsPageSize}`" class="detail-table-stage">
            <el-table :data="pagedAlerts" :height="detailTableHeight" class="stats-table" stripe>
          <el-table-column label="对象" min-width="124">
            <template #default="{ row }">
              <div class="target-cell">
                <strong>{{ row.targetName }}</strong>
                <span class="target-type">{{ getAlertTypeLabel(row.type) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="180" prop="summary" show-overflow-tooltip />
          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <span class="inline-tag" :class="getAlertLevelClass(row.level)">{{ getAlertLevelLabel(row.level) }}</span>
            </template>
          </el-table-column>
            </el-table>
          </div>
        </Transition>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="alertsPage"
            :page-size="alertsPageSize"
            :page-sizes="pageSizeOptions"
            :total="overview.alerts.length"
            :hide-on-single-page="false"
            background
            layout="sizes, prev, pager, next"
            small
            @size-change="handleAlertsPageSizeChange"
          />
        </div>
      </article>

      <article class="panel detail-panel">
        <div class="panel-head">
          <div>
            <p class="panel-kicker">Reservation Feed</p>
            <h2>预约明细</h2>
          </div>
        </div>

        <Transition name="pagination-switch" mode="out-in">
          <div :key="`reservation-page-${reservationsPage}-${reservationsPageSize}`" class="detail-table-stage">
            <el-table :data="pagedReservations" :height="detailTableHeight" class="stats-table" stripe>
          <el-table-column label="预约单号" min-width="132" prop="reservationNo" show-overflow-tooltip />
          <el-table-column label="主题 / 空间" min-width="168">
            <template #default="{ row }">
              <div class="target-cell">
                <strong>{{ row.title }}</strong>
                <span class="target-type">{{ row.roomName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="组织人" width="92" prop="organizerName" />
          <el-table-column label="时间段" min-width="168">
            <template #default="{ row }">
              {{ formatRange(row.startTime, row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <span class="inline-tag" :class="getReservationStatusClass(row.status)">{{ getReservationStatusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="设备调用" min-width="180" prop="deviceSummary" show-overflow-tooltip />
            </el-table>
          </div>
        </Transition>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="reservationsPage"
            :page-size="reservationsPageSize"
            :page-sizes="pageSizeOptions"
            :total="overview.reservations.length"
            :hide-on-single-page="false"
            background
            layout="sizes, prev, pager, next"
            small
            @size-change="handleReservationsPageSizeChange"
          />
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.admin-stats-page {
  --page-topbar-gap: 20px;
  --steel: #4f7cff;
  --steel-soft: rgba(79, 124, 255, 0.14);
  --mint: #4fb9a8;
  --mint-soft: rgba(79, 185, 168, 0.15);
  --amber: #d9a247;
  --amber-soft: rgba(217, 162, 71, 0.16);
  --rose: #e07088;
  --rose-soft: rgba(224, 112, 136, 0.15);
  --panel-bg: rgba(255, 255, 255, 0.82);
  --panel-border: rgba(226, 236, 246, 0.9);
  --panel-shadow: 0 20px 48px rgba(28, 67, 105, 0.08);
  --text-main: #17324a;
  --text-secondary: #698096;
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(126, 194, 255, 0.18), transparent 32%),
    radial-gradient(circle at right 20%, rgba(97, 208, 189, 0.14), transparent 24%),
    linear-gradient(180deg, #f6f9fd 0%, #f3f7fb 100%);
}

.panel,
.kpi-card {
  animation: panel-rise 0.58s ease both;
}

.panel-kicker {
  margin: 0 0 6px;
  color: #7c90a7;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.panel-head h2 {
  margin: 0;
  color: var(--text-main);
}

.window-switch-panel {
  display: flex;
  align-items: center;
  gap: 14px;
}

.switch-label {
  display: inline-flex;
  align-items: center;
  height: 42px;
  white-space: nowrap;
  flex-shrink: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.window-switch {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: min(360px, 100%);
  padding: 4px;
  border: 1px solid rgba(220, 231, 243, 0.96);
  border-radius: 999px;
  background: rgba(244, 248, 253, 0.94);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.window-thumb {
  position: absolute;
  top: 4px;
  left: 4px;
  bottom: 4px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(79, 124, 255, 0.96), rgba(115, 157, 255, 0.88));
  box-shadow: 0 12px 22px rgba(79, 124, 255, 0.22);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.window-switch__button {
  position: relative;
  z-index: 1;
  height: 42px;
  border: 0;
  background: transparent;
  color: #60758b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.28s ease, transform 0.28s ease;
}

.window-switch__button:hover {
  color: #35516d;
}

.window-switch__button.is-active {
  color: #ffffff;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 116px;
  padding: 18px;
  border: 1px solid var(--panel-border);
  border-radius: 24px;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
  backdrop-filter: blur(16px);
}

.tone-steel {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(238, 244, 255, 0.92));
}

.tone-mint {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(238, 250, 247, 0.92));
}

.tone-amber {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 248, 235, 0.92));
}

.tone-rose {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 242, 246, 0.92));
}

.kpi-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  color: var(--steel);
  background: var(--steel-soft);
  font-size: 20px;
  flex-shrink: 0;
}

.tone-mint .kpi-icon {
  color: var(--mint);
  background: var(--mint-soft);
}

.tone-amber .kpi-icon {
  color: var(--amber);
  background: var(--amber-soft);
}

.tone-rose .kpi-icon {
  color: var(--rose);
  background: var(--rose-soft);
}

.kpi-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.kpi-label {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.kpi-value {
  color: var(--text-main);
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}

.kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.chart-grid,
.detail-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 18px;
}

.chart-grid--top {
  grid-template-columns: 1.4fr 1fr;
}

.chart-grid--bottom {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel {
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 28px;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
  backdrop-filter: blur(18px);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 22px 24px 0;
}

.panel-head h2 {
  font-size: 23px;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.chart-box {
  width: 100%;
}

.chart-box--trend,
.chart-box--heatmap {
  height: 320px;
  padding: 8px 10px 14px;
}

.chart-box--rank {
  height: 280px;
  padding: 8px 10px 10px;
}

.chart-box--coverage {
  height: 280px;
  padding: 8px 10px 10px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  height: 470px;
}

.detail-table-stage {
  min-height: 0;
}

:deep(.stats-table) {
  margin: 18px 18px 0;
  border-radius: 18px;
  overflow: hidden;
}

:deep(.stats-table .el-table__inner-wrapper) {
  min-height: 100%;
}

:deep(.stats-table .el-table__row) {
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

:deep(.stats-table .el-table__row:hover) {
  transform: translateX(3px);
  box-shadow: inset 3px 0 0 rgba(79, 124, 255, 0.28);
}

.target-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.target-cell strong {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 700;
}

.target-type {
  color: var(--text-secondary);
  font-size: 12px;
}

.inline-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.inline-tag.is-warning {
  color: #996926;
  background: rgba(217, 162, 71, 0.16);
}

.inline-tag.is-danger,
.inline-tag.is-cancelled {
  color: #bb4965;
  background: rgba(224, 112, 136, 0.16);
}

.inline-tag.is-active {
  color: #1b8b7a;
  background: rgba(79, 185, 168, 0.16);
}

.inline-tag.is-ended {
  color: #3760d8;
  background: rgba(79, 124, 255, 0.14);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  min-height: 54px;
  padding: 16px 18px 18px;
  margin-top: auto;
}

@keyframes panel-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1480px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .window-switch-panel {
    flex-direction: column;
    align-items: stretch;
  }

  .chart-grid--top,
  .chart-grid--bottom,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .admin-stats-page {
    padding: 16px;
  }

  .panel {
    border-radius: 22px;
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .window-switch {
    width: 100%;
  }

  .chart-box--trend,
  .chart-box--heatmap,
  .chart-box--rank,
  .chart-box--coverage {
    height: 260px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-panel,
  .panel,
  .kpi-card,
  .window-thumb,
  .window-switch__button,
  :deep(.stats-table .el-table__row) {
    animation: none;
    transition: none;
  }
}
</style>
