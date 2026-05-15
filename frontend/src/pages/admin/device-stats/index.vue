<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import * as echarts from "echarts"
import { Connection, DataAnalysis, OfficeBuilding, RefreshRight, Search, Warning } from "@element-plus/icons-vue"
import { getAdminDeviceBindingStatsApi } from "@/common/apis/rooms"
import type {
  AdminDeviceBindingOverview,
  AdminDeviceBindingRoomItem,
  AdminDeviceBindingStatsItem,
  AdminRoomBindingDeviceItem,
  AdminRoomBindingStatsItem,
  BindingLevel,
  DeviceStatus,
  RoomStatus
} from "@/common/apis/rooms/type"
import { formatBindingLegendLabel, getRankingBarColor } from "./chart"

type DeviceStatusFilter = DeviceStatus | "all"
type RoomStatusFilter = RoomStatus | "all"
type LinkState =
  | { type: null, value: "" }
  | { type: "device", value: string }
  | { type: "room", value: string }
  | { type: "level", value: BindingLevel }

type RawAdminDeviceBindingRoomItem = Omit<AdminDeviceBindingRoomItem, "roomStatus"> & {
  roomStatus?: RoomStatus
}

type RawAdminRoomBindingDeviceItem = Omit<AdminRoomBindingDeviceItem, "deviceName" | "deviceStatus"> & Partial<Pick<AdminRoomBindingDeviceItem, "deviceName" | "deviceStatus">>

type RawAdminDeviceBindingOverview = Omit<AdminDeviceBindingOverview, "devices" | "rooms"> & {
  devices: Array<Omit<AdminDeviceBindingStatsItem, "rooms"> & { rooms: RawAdminDeviceBindingRoomItem[] }>
  rooms: Array<Omit<AdminRoomBindingStatsItem, "boundDevices"> & { boundDevices: RawAdminRoomBindingDeviceItem[] }>
}

function createEmptyOverview(): AdminDeviceBindingOverview {
  return {
    totalBindingCount: 0,
    boundDeviceTypeCount: 0,
    boundRoomCount: 0,
    unboundRoomCount: 0,
    devices: [],
    rooms: []
  }
}

function normalizeBindingOverview(raw: RawAdminDeviceBindingOverview): AdminDeviceBindingOverview {
  const roomStatusByCode = new Map(raw.rooms.map(room => [room.roomCode, room.roomStatus]))
  const deviceMetaByCode = new Map(raw.devices.map(device => [device.deviceCode, {
    name: device.name,
    status: device.status
  }]))

  return {
    ...raw,
    devices: raw.devices.map(device => ({
      ...device,
      rooms: device.rooms.map(room => ({
        ...room,
        roomStatus: room.roomStatus ?? roomStatusByCode.get(room.roomCode) ?? "AVAILABLE"
      }))
    })),
    rooms: raw.rooms.map(room => ({
      ...room,
      boundDevices: room.boundDevices.map((device) => {
        const matchedDevice = deviceMetaByCode.get(device.deviceCode)
        return {
          ...device,
          deviceName: device.deviceName ?? device.name ?? matchedDevice?.name ?? device.deviceCode,
          deviceStatus: device.deviceStatus ?? device.status ?? matchedDevice?.status ?? "ENABLED"
        }
      })
    }))
  }
}

const overview = ref<AdminDeviceBindingOverview>(createEmptyOverview())
const searchKeyword = ref("")
const deviceStatusFilter = ref<DeviceStatusFilter>("all")
const roomStatusFilter = ref<RoomStatusFilter>("all")
const activeLink = ref<LinkState>({ type: null, value: "" })
const clearBurstActive = ref(false)

const rankingChartRef = ref<HTMLDivElement | null>(null)
const distributionChartRef = ref<HTMLDivElement | null>(null)

let rankingChart: echarts.ECharts | null = null
let distributionChart: echarts.ECharts | null = null
let clearBurstTimer: ReturnType<typeof setTimeout> | null = null

const bindingLevelLabelMap: Record<BindingLevel, string> = {
  none: "未绑定",
  light: "轻绑定",
  medium: "中绑定",
  heavy: "重绑定"
}

const bindingLevelColorMap: Record<BindingLevel, string> = {
  none: "rgba(148, 163, 184, 0.48)",
  light: "#7c9bf3",
  medium: "#58c3b2",
  heavy: "#d9a247"
}

const keyword = computed(() => searchKeyword.value.trim().toLowerCase())

const baseDevices = computed(() => overview.value.devices.filter((device) => {
  const hitKeyword = !keyword.value
    || [device.name, device.deviceCode, ...device.rooms.map(room => `${room.roomName} ${room.roomCode}`)]
      .some(item => item.toLowerCase().includes(keyword.value))

  const hitDeviceStatus = deviceStatusFilter.value === "all" || device.status === deviceStatusFilter.value
  const hitRoomStatus = roomStatusFilter.value === "all"
    || device.rooms.some(room => room.roomStatus === roomStatusFilter.value)

  return hitKeyword && hitDeviceStatus && hitRoomStatus
}))

const baseRooms = computed(() => overview.value.rooms.filter((room) => {
  const hitKeyword = !keyword.value
    || [room.roomName, room.roomCode, ...room.boundDevices.map(device => `${device.deviceName} ${device.deviceCode}`)]
      .some(item => item.toLowerCase().includes(keyword.value))

  const hitRoomStatus = roomStatusFilter.value === "all" || room.roomStatus === roomStatusFilter.value
  const hitDeviceStatus = deviceStatusFilter.value === "all"
    || room.boundDevices.some(device => device.deviceStatus === deviceStatusFilter.value)

  return hitKeyword && hitRoomStatus && hitDeviceStatus
}))

const visibleDevices = computed(() => {
  if (activeLink.value.type === "device") {
    return baseDevices.value.filter(item => item.deviceCode === activeLink.value.value)
  }

  if (activeLink.value.type === "room") {
    const room = baseRooms.value.find(item => item.roomCode === activeLink.value.value)
    if (!room) return []
    const deviceCodes = new Set(room.boundDevices.map(item => item.deviceCode))
    return baseDevices.value.filter(item => deviceCodes.has(item.deviceCode))
  }

  if (activeLink.value.type === "level") {
    const roomCodes = new Set(
      baseRooms.value
        .filter(item => item.bindingLevel === activeLink.value.value)
        .map(item => item.roomCode)
    )
    return baseDevices.value.filter(device => device.rooms.some(room => roomCodes.has(room.roomCode)))
  }

  return baseDevices.value
})

const visibleRooms = computed(() => {
  if (activeLink.value.type === "device") {
    return baseRooms.value.filter(item => item.boundDevices.some(device => device.deviceCode === activeLink.value.value))
  }

  if (activeLink.value.type === "room") {
    return baseRooms.value.filter(item => item.roomCode === activeLink.value.value)
  }

  if (activeLink.value.type === "level") {
    return baseRooms.value.filter(item => item.bindingLevel === activeLink.value.value)
  }

  return baseRooms.value
})

const kpis = computed(() => {
  const boundDeviceTypeCount = visibleDevices.value.filter(item => item.boundRoomCount > 0).length
  const boundRoomCount = visibleRooms.value.filter(item => item.deviceTypeCount > 0).length
  const totalBindingCount = visibleRooms.value.reduce((sum, room) => sum + room.deviceTypeCount, 0)
  const unboundRoomCount = visibleRooms.value.filter(item => item.deviceTypeCount === 0).length

  return [
    { label: "已绑定设备类型", value: boundDeviceTypeCount, icon: Connection, tone: "steel" },
    { label: "发生绑定的会议室", value: boundRoomCount, icon: OfficeBuilding, tone: "mint" },
    { label: "绑定关系总数", value: totalBindingCount, icon: DataAnalysis, tone: "amber" },
    { label: "未绑定会议室", value: unboundRoomCount, icon: Warning, tone: "rose" }
  ]
})

const rankedDevices = computed(() => [...visibleDevices.value]
  .sort((a, b) => b.boundRoomCount - a.boundRoomCount || a.deviceCode.localeCompare(b.deviceCode, "zh-CN"))
  .slice(0, 6))

const roomLevelDistribution = computed(() => {
  const counts: Record<BindingLevel, number> = { none: 0, light: 0, medium: 0, heavy: 0 }
  visibleRooms.value.forEach((room) => { counts[room.bindingLevel] += 1 })

  return (Object.keys(counts) as BindingLevel[]).map(level => ({
    level,
    label: bindingLevelLabelMap[level],
    value: counts[level],
    color: bindingLevelColorMap[level]
  }))
})

function getRoomStatusLabel(status: RoomStatus) {
  return status === "AVAILABLE" ? "可用" : "维护中"
}

function getRoomStatusTone(status: RoomStatus) {
  return status === "AVAILABLE" ? "is-available" : "is-maintenance"
}

function getDeviceStatusLabel(status: DeviceStatus) {
  return status === "ENABLED" ? "启用" : "停用"
}

function getDeviceStatusTone(status: DeviceStatus) {
  return status === "ENABLED" ? "is-enabled" : "is-disabled"
}

function formatRate(value: number) {
  return `${Math.round(value * 100)}%`
}

function getDeviceRoomSummary(device: AdminDeviceBindingStatsItem) {
  return device.rooms.length > 0 ? device.rooms.map(room => room.roomName).join(" / ") : "未绑定会议室"
}

function getRoomDeviceSummary(room: AdminRoomBindingStatsItem) {
  return room.boundDevices.length > 0 ? room.boundDevices.map(device => device.deviceName).join(" / ") : "未绑定设备"
}

function getBindingLevelLabel(level: BindingLevel) {
  return bindingLevelLabelMap[level]
}

function activateDevice(deviceCode: string) {
  activeLink.value = { type: "device", value: deviceCode }
}

function activateRoom(roomCode: string) {
  activeLink.value = { type: "room", value: roomCode }
}

function activateBindingLevel(level: BindingLevel) {
  activeLink.value = { type: "level", value: level }
}

function clearLinkage() {
  activeLink.value = { type: null, value: "" }
  if (clearBurstTimer) clearTimeout(clearBurstTimer)
  clearBurstActive.value = false
  void nextTick(() => {
    clearBurstActive.value = true
    clearBurstTimer = setTimeout(() => {
      clearBurstActive.value = false
      clearBurstTimer = null
    }, 820)
  })
}

async function fetchBindingOverview() {
  try {
    const response = await getAdminDeviceBindingStatsApi()
    overview.value = normalizeBindingOverview(response.data as RawAdminDeviceBindingOverview)
  } catch {
    overview.value = createEmptyOverview()
  }
}

function renderRankingChart() {
  if (!rankingChartRef.value) return

  if (!rankingChart) {
    rankingChart = echarts.init(rankingChartRef.value)
    rankingChart.on("click", (params: { name?: string }) => {
      if (params.name) activateDevice(params.name)
    })
  }

  rankingChart.setOption({
    animationDuration: 420,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: Array<{ value: number, name: string, axisValueLabel: string }>) => {
        const item = params[0]
        return `${item.axisValueLabel}<br/>已绑定到 ${item.value} 间会议室`
      }
    },
    grid: { top: 18, right: 24, bottom: 8, left: 112 },
    xAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "rgba(123, 145, 170, 0.12)" } }
    },
    yAxis: {
      type: "category",
      data: rankedDevices.value.map(item => item.name),
      axisTick: { show: false },
      axisLine: { show: false }
    },
    series: [
      {
        type: "bar",
        barWidth: 16,
        data: rankedDevices.value.map((item, index) => ({
          value: item.boundRoomCount,
          name: item.deviceCode,
          itemStyle: {
            color: activeLink.value.type === "device" && activeLink.value.value === item.deviceCode
              ? "#4fbea9"
              : getRankingBarColor(index),
            borderRadius: [10, 10, 10, 10],
            opacity: activeLink.value.type && activeLink.value.type !== "device" ? 0.74 : 1,
            shadowBlur: activeLink.value.type === "device" && activeLink.value.value === item.deviceCode ? 18 : 10,
            shadowColor: activeLink.value.type === "device" && activeLink.value.value === item.deviceCode
              ? "rgba(79, 190, 169, 0.42)"
              : "rgba(108, 141, 232, 0.18)"
          }
        })),
        label: {
          show: true,
          position: "right",
          color: "#20374c"
        }
      }
    ]
  })
}

function renderDistributionChart() {
  if (!distributionChartRef.value) return

  if (!distributionChart) {
    distributionChart = echarts.init(distributionChartRef.value)
    distributionChart.on("click", (params: { name?: string }) => {
      const matched = roomLevelDistribution.value.find(item => item.label === params.name)
      if (matched) activateBindingLevel(matched.level)
    })
  }

  distributionChart.setOption({
    animationDuration: 460,
    animationEasing: "cubicOut",
    tooltip: {
      trigger: "item",
      formatter: (params: { name: string, value: number }) => `${params.name}<br/>当前共有 ${params.value} 间会议室`
    },
    legend: {
      bottom: 0,
      left: "center",
      icon: "circle",
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 18,
      textStyle: {
        color: "#6f839a",
        fontSize: 12
      },
      formatter: (label: string) => formatBindingLegendLabel(label, roomLevelDistribution.value)
    },
    series: [
      {
        type: "pie",
        radius: ["54%", "78%"],
        center: ["50%", "42%"],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderColor: "rgba(255,255,255,0.95)",
          borderWidth: 4
        },
        data: roomLevelDistribution.value.map(item => ({
          name: item.label,
          value: item.value,
          itemStyle: {
            color: item.level === activeLink.value.value ? "#e58aa1" : item.color,
            opacity: activeLink.value.type && activeLink.value.type !== "level" ? 0.78 : 1
          }
        }))
      }
    ],
    graphic: [
      {
        type: "text",
        left: "center",
        top: "30%",
        style: {
          text: String(visibleRooms.value.length),
          fill: "#18314a",
          fontSize: 30,
          fontWeight: 700
        }
      },
      {
        type: "text",
        left: "center",
        top: "44%",
        style: {
          text: "会议室样本",
          fill: "#7a8ea3",
          fontSize: 12
        }
      }
    ]
  })
}

function handleResize() {
  rankingChart?.resize()
  distributionChart?.resize()
}

function disposeCharts() {
  rankingChart?.dispose()
  distributionChart?.dispose()
  rankingChart = null
  distributionChart = null
}

watch([rankedDevices, roomLevelDistribution, visibleRooms, activeLink], async () => {
  await nextTick()
  renderRankingChart()
  renderDistributionChart()
}, { immediate: true, deep: true })

onMounted(async () => {
  await fetchBindingOverview()
  await nextTick()
  renderRankingChart()
  renderDistributionChart()
  window.addEventListener("resize", handleResize)
})

onBeforeUnmount(() => {
  if (clearBurstTimer) clearTimeout(clearBurstTimer)
  window.removeEventListener("resize", handleResize)
  disposeCharts()
})
</script>

<template>
  <div class="admin-device-stats-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Binding Intelligence</p>
        <h1 class="page-hero-title">设备绑定统计</h1>
      </div>
      <div class="hero-actions">
        <el-button class="clear-link-button" :class="{ 'is-bursting': clearBurstActive }" @click="clearLinkage()">
          <span class="clear-link-orbit clear-link-orbit-left" />
          <span class="clear-link-orbit clear-link-orbit-right" />
          <span class="clear-link-content">
            <el-icon class="clear-link-icon"><RefreshRight /></el-icon>
            <span>清空联动</span>
          </span>
        </el-button>
      </div>
    </section>

    <section class="stats-panel">
      <div class="section-head">
        <div>
          <p class="section-kicker">Binding Overview</p>
          <h2>绑定总览</h2>
        </div>
      </div>
      <div class="stats-grid">
        <article v-for="item in kpis" :key="item.label" class="stats-card" :class="`tone-${item.tone}`">
          <div class="stats-icon"><el-icon><component :is="item.icon" /></el-icon></div>
          <div>
            <div class="stats-label">{{ item.label }}</div>
            <strong class="stats-value">{{ item.value }}</strong>
          </div>
        </article>
      </div>
    </section>

    <section class="filter-panel">
      <el-input v-model="searchKeyword" class="search-box" placeholder="搜索设备名称/编码或会议室名称/编码" clearable>
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="deviceStatusFilter" class="filter-select">
        <el-option label="全部设备状态" value="all" />
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-select v-model="roomStatusFilter" class="filter-select">
        <el-option label="全部房间状态" value="all" />
        <el-option label="可用" value="AVAILABLE" />
        <el-option label="维护中" value="MAINTENANCE" />
      </el-select>
    </section>

    <section class="chart-grid">
      <article class="chart-panel">
        <div class="section-head compact">
          <div>
            <p class="section-kicker">Device Ranking</p>
            <h2>设备绑定排行</h2>
          </div>
        </div>
        <div ref="rankingChartRef" class="chart-canvas" />
      </article>

      <article class="chart-panel">
        <div class="section-head compact">
          <div>
            <p class="section-kicker">Room Distribution</p>
            <h2>会议室绑定强度</h2>
          </div>
        </div>
        <div ref="distributionChartRef" class="chart-canvas" />
      </article>
    </section>

    <section class="table-stack">
      <article class="table-panel">
        <div class="section-head compact">
          <div>
            <p class="section-kicker">Device Detail</p>
            <h2>设备绑定明细</h2>
          </div>
          <span class="section-meta">{{ visibleDevices.length }} 项</span>
        </div>

        <el-table
          :data="visibleDevices"
          class="stats-table"
          :row-class-name="({ row }) => activeLink.type === 'device' && activeLink.value === row.deviceCode ? 'is-active-row' : ''"
          @row-click="(row) => activateDevice(row.deviceCode)"
        >
          <el-table-column label="设备名称" min-width="180">
            <template #default="{ row }">
              <div class="cell-title">
                <div class="cell-main">{{ row.name }}</div>
                <div class="cell-sub">{{ row.deviceCode }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96" align="center">
            <template #default="{ row }">
              <span class="status-pill" :class="getDeviceStatusTone(row.status)">{{ getDeviceStatusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存总量" prop="total" width="96" align="center" />
          <el-table-column label="绑定会议室数" prop="boundRoomCount" width="120" align="center" />
          <el-table-column label="覆盖率" width="100" align="center">
            <template #default="{ row }">{{ formatRate(row.bindingRate) }}</template>
          </el-table-column>
          <el-table-column label="绑定会议室摘要" min-width="220">
            <template #default="{ row }">
              <div class="summary-text">{{ getDeviceRoomSummary(row) }}</div>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="table-panel">
        <div class="section-head compact">
          <div>
            <p class="section-kicker">Room Detail</p>
            <h2>会议室绑定明细</h2>
          </div>
          <span class="section-meta">{{ visibleRooms.length }} 间</span>
        </div>

        <el-table
          :data="visibleRooms"
          class="stats-table"
          :row-class-name="({ row }) => activeLink.type === 'room' && activeLink.value === row.roomCode ? 'is-active-row' : ''"
          @row-click="(row) => activateRoom(row.roomCode)"
        >
          <el-table-column label="会议室" min-width="200">
            <template #default="{ row }">
              <div class="cell-title">
                <div class="cell-main">{{ row.roomName }}</div>
                <div class="cell-sub">{{ row.roomCode }} · {{ row.location }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="房间状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-pill" :class="getRoomStatusTone(row.roomStatus)">{{ getRoomStatusLabel(row.roomStatus) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="设备类型数" prop="deviceTypeCount" width="108" align="center" />
          <el-table-column label="绑定强度" width="100" align="center">
            <template #default="{ row }">
              <span class="level-pill" :class="`level-${row.bindingLevel}`">{{ getBindingLevelLabel(row.bindingLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主要绑定设备摘要" min-width="240">
            <template #default="{ row }">
              <div class="summary-text">{{ getRoomDeviceSummary(row) }}</div>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.admin-device-stats-page {
  --panel-bg: rgba(255, 255, 255, 0.8);
  --panel-border: rgba(255, 255, 255, 0.92);
  --text-main: #18314a;
  --text-sub: #72859a;
  --steel: #6c8de8;
  --mint: #53c5b1;
  --amber: #d6a64b;
  --rose: #ec8aa4;
  --line: rgba(123, 145, 170, 0.14);
  min-height: 100%;
  padding: 24px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 0% 0%, rgba(112, 149, 219, 0.18), transparent 25%),
    radial-gradient(circle at 100% 8%, rgba(83, 197, 177, 0.15), transparent 30%),
    linear-gradient(180deg, #f6f7fb 0%, #eef2f8 100%);
}

.stats-panel,
.filter-panel,
.chart-panel,
.table-panel,
.stats-card {
  border: 1px solid var(--panel-border);
  background: var(--panel-bg);
  box-shadow: 0 22px 60px rgba(61, 83, 109, 0.1);
  backdrop-filter: blur(18px);
}

.stats-panel,
.filter-panel,
.chart-panel,
.table-panel {
  border-radius: 28px;
}

.stats-panel,
.filter-panel,
.chart-panel,
.table-panel {
  animation: float-in 0.46s ease both;
}

.section-head h2 {
  margin: 0;
}

.section-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--text-sub);
}

.section-head,
.cell-title {
  display: flex;
  gap: 12px;
}

.clear-link-button {
  position: relative;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background:
    linear-gradient(135deg, rgba(108, 141, 232, 0.18), rgba(83, 197, 177, 0.22)),
    linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0.72));
  box-shadow:
    0 12px 30px rgba(108, 141, 232, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.clear-link-button::before {
  content: "";
  position: absolute;
  inset: 1px;
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.76));
}

.clear-link-button::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: -45%;
  width: 34%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.92), transparent);
  transform: skewX(-24deg);
  animation: clear-shimmer 4.2s ease-in-out infinite;
}

.clear-link-button:hover {
  transform: translateY(-2px);
  box-shadow:
    0 16px 34px rgba(108, 141, 232, 0.22),
    0 0 0 1px rgba(108, 141, 232, 0.08);
}

.clear-link-content {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  color: #35506b;
  font-size: 13px;
  font-weight: 700;
}

.clear-link-icon {
  font-size: 15px;
  color: #5379c8;
  transition: transform 0.24s ease;
}

.clear-link-button:hover .clear-link-icon {
  transform: rotate(18deg);
}

.clear-link-orbit {
  position: absolute;
  top: 50%;
  width: 86px;
  height: 86px;
  border-radius: 50%;
  border: 1px solid rgba(108, 141, 232, 0.18);
  pointer-events: none;
  opacity: 0.48;
}

.clear-link-orbit-left {
  left: -26px;
  transform: translateY(-50%);
  animation: clear-orbit-left 5.6s linear infinite;
}

.clear-link-orbit-right {
  right: -26px;
  transform: translateY(-50%);
  border-color: rgba(83, 197, 177, 0.22);
  animation: clear-orbit-right 6.2s linear infinite;
}

.clear-link-button.is-bursting {
  animation: clear-burst 0.82s ease;
}

.clear-link-button.is-bursting .clear-link-icon {
  animation: clear-spin 0.82s ease;
}

.clear-link-button.is-bursting .clear-link-orbit-left,
.clear-link-button.is-bursting .clear-link-orbit-right {
  animation-duration: 0.82s;
  opacity: 0.72;
}

.stats-panel,
.filter-panel,
.chart-panel,
.table-panel {
  margin-top: 16px;
  padding: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 12px;
}

.stats-card {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: 22px;
}

.stats-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  font-size: 20px;
}

.tone-steel .stats-icon { background: rgba(108, 141, 232, 0.14); color: var(--steel); }
.tone-mint .stats-icon { background: rgba(83, 197, 177, 0.16); color: var(--mint); }
.tone-amber .stats-icon { background: rgba(214, 166, 75, 0.18); color: var(--amber); }
.tone-rose .stats-icon { background: rgba(236, 138, 164, 0.16); color: var(--rose); }

.stats-label,
.cell-sub,
.summary-text {
  color: var(--text-sub);
}

.stats-value {
  display: block;
  margin-top: 5px;
  font-size: 28px;
  font-weight: 700;
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) repeat(2, minmax(0, 0.6fr));
  gap: 12px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.chart-canvas {
  height: 328px;
}

.table-stack {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.section-head {
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-head.compact {
  margin-bottom: 12px;
}

.section-head h2 {
  font-size: 23px;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.section-meta,
.status-pill,
.level-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.section-meta {
  color: #5570b3;
  background: rgba(108, 141, 232, 0.12);
}

.status-pill.is-enabled,
.status-pill.is-available {
  color: #1d7f6b;
  background: rgba(83, 197, 177, 0.16);
}

.status-pill.is-disabled,
.status-pill.is-maintenance {
  color: #a24c65;
  background: rgba(236, 138, 164, 0.16);
}

.level-pill.level-none {
  color: #71839a;
  background: rgba(148, 163, 184, 0.18);
}

.level-pill.level-light {
  color: #4c66a6;
  background: rgba(108, 141, 232, 0.14);
}

.level-pill.level-medium {
  color: #1f7b6a;
  background: rgba(83, 197, 177, 0.16);
}

.level-pill.level-heavy {
  color: #9b6c18;
  background: rgba(214, 166, 75, 0.18);
}

.cell-title {
  flex-direction: column;
  gap: 4px;
}

.cell-main {
  font-weight: 700;
}

.summary-text {
  line-height: 1.5;
}

:deep(.stats-table) {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: rgba(244, 247, 252, 0.92);
  --el-table-row-hover-bg-color: rgba(108, 141, 232, 0.06);
  border-radius: 22px;
  overflow: hidden;
}

:deep(.stats-table .el-table__header-wrapper th.el-table__cell) {
  color: var(--text-sub);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

:deep(.stats-table .el-table__body-wrapper tr) {
  transition: transform 0.22s ease, background-color 0.22s ease, box-shadow 0.22s ease;
}

:deep(.stats-table .el-table__body-wrapper tr:hover) {
  transform: translateX(3px);
}

:deep(.stats-table .el-table__body-wrapper tr.is-active-row td.el-table__cell) {
  background: rgba(108, 141, 232, 0.08);
}

:deep(.stats-table .el-table__body-wrapper td.el-table__cell) {
  border-bottom: 1px solid var(--line);
}

@keyframes float-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes clear-shimmer {
  0%, 100% {
    transform: translateX(0) skewX(-24deg);
    opacity: 0;
  }
  18%, 38% {
    opacity: 1;
  }
  60% {
    transform: translateX(360%) skewX(-24deg);
    opacity: 0;
  }
}

@keyframes clear-orbit-left {
  from {
    transform: translateY(-50%) rotate(0deg);
  }
  to {
    transform: translateY(-50%) rotate(360deg);
  }
}

@keyframes clear-orbit-right {
  from {
    transform: translateY(-50%) rotate(360deg);
  }
  to {
    transform: translateY(-50%) rotate(0deg);
  }
}

@keyframes clear-spin {
  0% {
    transform: rotate(0deg) scale(1);
  }
  50% {
    transform: rotate(160deg) scale(1.16);
  }
  100% {
    transform: rotate(360deg) scale(1);
  }
}

@keyframes clear-burst {
  0% {
    transform: scale(1);
    box-shadow:
      0 12px 30px rgba(108, 141, 232, 0.18),
      inset 0 1px 0 rgba(255, 255, 255, 0.85);
  }
  40% {
    transform: scale(1.04);
    box-shadow:
      0 0 0 10px rgba(108, 141, 232, 0.08),
      0 18px 38px rgba(83, 197, 177, 0.24),
      inset 0 1px 0 rgba(255, 255, 255, 0.92);
  }
  100% {
    transform: scale(1);
    box-shadow:
      0 12px 30px rgba(108, 141, 232, 0.18),
      inset 0 1px 0 rgba(255, 255, 255, 0.85);
  }
}

@media (max-width: 1080px) {
  .chart-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .filter-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .admin-device-stats-page {
    --page-topbar-actions-justify: flex-start;
    padding: 16px;
  }

  .chart-canvas {
    height: 292px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-panel,
  .stats-panel,
  .filter-panel,
  .chart-panel,
  .table-panel,
  .clear-link-button,
  .clear-link-icon,
  .clear-link-orbit,
  :deep(.stats-table .el-table__body-wrapper tr) {
    animation: none;
    transition: none;
  }
}
</style>
