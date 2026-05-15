<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { ElMessage } from "element-plus"
import { CircleCheck, Connection, Delete, EditPen, Location, Monitor, OfficeBuilding, Operation, Plus, SetUp, SwitchButton, Warning } from "@element-plus/icons-vue"
import RoomUpsertDialog from "./components/RoomUpsertDialog.vue"
import {
  createAdminRoomApi,
  deleteAdminRoomApi,
  getAdminDeviceListApi,
  getAdminRoomListApi,
  getRoomLocationsApi,
  updateAdminRoomApi,
  updateAdminRoomDevicesApi,
  updateAdminRoomStatusApi
} from "@/common/apis/rooms"
import type { AdminRoomListItem, AdminRoomListStats, CapacityType, DeviceInventoryData, RoomDeviceData, RoomStatus } from "@/common/apis/rooms/type"
import { CARD_PAGE_SIZE_OPTIONS } from "@/common/constants/pagination"
import type { RoomUpsertForm } from "./model"
import { createEmptyRoomForm } from "./model"

type StatusFilter = RoomStatus | "all"
type CapacityFilter = CapacityType | "all"

interface DeviceDraft extends DeviceInventoryData {
  quantity: number
}

const searchKeyword = ref("")
const statusFilter = ref<StatusFilter>("all")
const capacityFilter = ref<CapacityFilter>("all")
const locationFilter = ref("all")
const deviceFilterIds = ref<number[]>([])
const detailDrawerVisible = ref(false)
const upsertDialogVisible = ref(false)
const deviceDialogVisible = ref(false)
const maintenanceDialogVisible = ref(false)
const maintenanceTargetRoom = ref<AdminRoomListItem | null>(null)
const maintenanceRemarkDraft = ref("")
const maintenanceSubmitting = ref(false)
const deleteDialogVisible = ref(false)
const deleteTargetRoom = ref<AdminRoomListItem | null>(null)
const deleteSubmitting = ref(false)
const selectedRoomId = ref<number | null>(null)
const selectedRoomSnapshot = ref<AdminRoomListItem | null>(null)
const filteredRoomCollection = ref<AdminRoomListItem[]>([])
const roomCollection = ref<AdminRoomListItem[]>([])
const deviceInventory = ref<DeviceInventoryData[]>([])
const roomForm = ref<RoomUpsertForm>(createEmptyRoomForm())
const deviceDrafts = ref<DeviceDraft[]>([])
const roomPage = ref(1)
const watchPage = ref(1)
const roomPageTransitionKey = ref(0)
const pageSizeOptions = CARD_PAGE_SIZE_OPTIONS
const roomPageSize = ref(4)
const watchPageSize = ref(4)
const roomTotal = ref(0)
const roomStats = ref<AdminRoomListStats>(createEmptyRoomStats())
const locationCatalog = ref<string[]>([])
const radialMenuVisible = ref(false)
const radialMenuExpanded = ref(false)
const radialRippleActive = ref(false)
const radialMenuRoomId = ref<number | null>(null)
const radialRippleKey = ref(0)
const radialMenuPosition = ref({ x: 0, y: 0 })
let radialOpenTimer: ReturnType<typeof setTimeout> | null = null
let radialCloseTimer: ReturnType<typeof setTimeout> | null = null
let hasHydratedRoomPage = false

const locationOptions = computed(() => locationCatalog.value)

const pagedRooms = computed(() => roomCollection.value)

const globalStats = computed(() => roomStats.value)

const capacityDistribution = computed(() => {
  const summary = {
    small: filteredRoomCollection.value.filter(item => resolveCapacityType(item.capacity) === "small").length,
    medium: filteredRoomCollection.value.filter(item => resolveCapacityType(item.capacity) === "medium").length,
    large: filteredRoomCollection.value.filter(item => resolveCapacityType(item.capacity) === "large").length
  }
  return [
    { label: "轻会场", hint: "1-8 人", value: summary.small, tone: "small" },
    { label: "协作场", hint: "9-16 人", value: summary.medium, tone: "medium" },
    { label: "大型场", hint: "17+ 人", value: summary.large, tone: "large" }
  ]
})

const watchRooms = computed(() => filteredRoomCollection.value.filter(item => getHealthTone(item) !== "is-healthy"))
const watchTotal = computed(() => watchRooms.value.length)

const pagedWatchRooms = computed(() => {
  const start = (watchPage.value - 1) * watchPageSize.value
  return watchRooms.value.slice(start, start + watchPageSize.value)
})

const selectedRoom = computed(() => {
  if (selectedRoomId.value === null) return null
  return filteredRoomCollection.value.find(item => item.id === selectedRoomId.value) ?? selectedRoomSnapshot.value
})

const radialMenuRoom = computed(() => {
  if (radialMenuRoomId.value === null) return null
  return roomCollection.value.find(item => item.id === radialMenuRoomId.value) ?? null
})

const selectedRoomWarnings = computed(() => selectedRoom.value ? getRoomWarnings(selectedRoom.value) : [])

const statsCards = computed(() => [
  { label: "总房间数", value: globalStats.value.totalCount, icon: OfficeBuilding, tone: "steel" },
  { label: "可用房间", value: globalStats.value.availableCount, icon: CircleCheck, tone: "mint" },
  { label: "维护中", value: globalStats.value.maintenanceCount, icon: Warning, tone: "rose" },
  { label: "未绑定设备", value: globalStats.value.unboundCount, icon: Connection, tone: "amber" },
  { label: "大型空间", value: globalStats.value.largeRoomCount, icon: Operation, tone: "violet" }
])

const radialMenuStyle = computed(() => ({
  left: `${radialMenuPosition.value.x}px`,
  top: `${radialMenuPosition.value.y}px`
}))

const radialActions = computed(() => {
  if (!radialMenuRoom.value) return []
  return [
    { key: "edit", label: "编辑房间", icon: EditPen },
    { key: "status", label: radialMenuRoom.value.status === "AVAILABLE" ? "转维护" : "恢复可用", icon: SwitchButton },
    { key: "device", label: "绑定设备", icon: SetUp },
    { key: "delete", label: "删除会议室", icon: Delete }
  ] as const
})

function resolveCapacityType(capacity: number): CapacityType {
  if (capacity <= 8) return "small"
  if (capacity <= 16) return "medium"
  return "large"
}

function padNumber(value: number) {
  return String(value).padStart(2, "0")
}

function getStatusLabel(status: RoomStatus) {
  return status === "AVAILABLE" ? "可用" : "维护中"
}

function getStatusTone(status: RoomStatus) {
  return status === "AVAILABLE" ? "is-available" : "is-maintenance"
}

function getHealthLabel(room: AdminRoomListItem) {
  if (room.status === "MAINTENANCE") return "维护中"
  if (room.devices.length === 0) return "待补设备"
  if (!room.description.trim()) return "待补说明"
  if (room.capacity >= 17 && room.deviceCount < 3) return "大房间需补强"
  return "配置完整"
}

function getHealthTone(room: AdminRoomListItem) {
  if (room.status === "MAINTENANCE") return "is-maintenance"
  if (room.devices.length === 0 || !room.description.trim() || (room.capacity >= 17 && room.deviceCount < 3)) return "is-warning"
  return "is-healthy"
}

function getRoomWarnings(room: AdminRoomListItem) {
  const warnings: string[] = []
  if (room.status === "MAINTENANCE" && room.maintenanceRemark) warnings.push(room.maintenanceRemark)
  if (room.devices.length === 0) warnings.push("未绑定设备")
  if (room.capacity >= 17 && room.deviceCount < 3) warnings.push("大型会议室设备偏少")
  if (!room.description.trim()) warnings.push("缺少空间说明")
  return warnings
}

function normalizeRoomStatusValue(status: unknown): RoomStatus {
  const value = String(status ?? "").trim()
  return value === "MAINTENANCE" || value === "2" ? "MAINTENANCE" : "AVAILABLE"
}

function normalizeRoomItem(room: AdminRoomListItem): AdminRoomListItem {
  const devices = Array.isArray(room.devices) ? room.devices : []
  return {
    ...room,
    status: normalizeRoomStatusValue(room.status),
    description: room.description ?? "",
    maintenanceRemark: room.maintenanceRemark ?? "",
    devices,
    deviceCount: room.deviceCount ?? devices.reduce((sum, device) => sum + (device.quantity ?? 0), 0),
    deviceBindingSummary: room.deviceBindingSummary ?? `${devices.length} 类设备`
  }
}

watch([searchKeyword, statusFilter, capacityFilter, locationFilter, () => deviceFilterIds.value.join(",")], () => {
  const shouldFetchImmediately = roomPage.value === 1
  roomPage.value = 1
  watchPage.value = 1
  if (shouldFetchImmediately) {
    void fetchRoomCollection()
  }
})

watch(roomTotal, (value) => {
  const maxPage = Math.max(1, Math.ceil(value / roomPageSize.value))
  if (roomPage.value > maxPage) roomPage.value = maxPage
})

watch(watchTotal, (value) => {
  const maxPage = Math.max(1, Math.ceil(value / watchPageSize.value))
  if (watchPage.value > maxPage) watchPage.value = maxPage
})

watch([roomPage, roomPageSize], () => {
  closeRadialMenu(true)
  void fetchRoomCollection()
})

function handleRoomPageSizeChange(size: number) {
  roomPageSize.value = size
  roomPage.value = 1
}

function handleWatchPageSizeChange(size: number) {
  watchPageSize.value = size
  watchPage.value = 1
}

function clearRadialTimers() {
  if (radialOpenTimer) {
    clearTimeout(radialOpenTimer)
    radialOpenTimer = null
  }
  if (radialCloseTimer) {
    clearTimeout(radialCloseTimer)
    radialCloseTimer = null
  }
}

function closeRadialMenu(immediate = false) {
  clearRadialTimers()
  radialRippleActive.value = false
  radialMenuExpanded.value = false

  if (immediate) {
    radialMenuVisible.value = false
    radialMenuRoomId.value = null
    return
  }

  radialCloseTimer = setTimeout(() => {
    radialMenuVisible.value = false
    radialMenuRoomId.value = null
  }, 180)
}

function clampMenuPosition(x: number, y: number) {
  const radius = 104
  const edge = 24
  return {
    x: Math.min(Math.max(x, radius + edge), window.innerWidth - radius - edge),
    y: Math.min(Math.max(y, radius + edge), window.innerHeight - radius - edge)
  }
}

function openRadialMenu(event: MouseEvent, room: AdminRoomListItem) {
  event.preventDefault()
  event.stopPropagation()

  closeRadialMenu(true)

  radialMenuRoomId.value = room.id
  radialMenuPosition.value = clampMenuPosition(event.clientX, event.clientY)
  radialRippleKey.value += 1
  radialMenuVisible.value = true

  nextTick(() => {
    radialRippleActive.value = true
  })

  radialOpenTimer = setTimeout(() => {
    radialMenuExpanded.value = true
  }, 170)
}

function handleGlobalPointerDown(event: Event) {
  if (!radialMenuVisible.value) return
  const target = event.target as HTMLElement | null
  if (target?.closest(".radial-menu")) return
  closeRadialMenu()
}

function handleEscape(event: KeyboardEvent) {
  if (event.key === "Escape") closeRadialMenu()
}

function runRadialAction(key: "edit" | "status" | "device" | "delete") {
  if (!radialMenuRoom.value) return
  const room = radialMenuRoom.value
  closeRadialMenu()

  if (key === "edit") {
    openEditDialog(room)
    return
  }

  if (key === "status") {
    toggleRoomStatus(room)
    return
  }

  if (key === "device") {
    openDeviceBindingDialog(room)
    return
  }

  void deleteRoom(room)
}

function createEmptyRoomStats(): AdminRoomListStats {
  return {
    totalCount: 0,
    availableCount: 0,
    maintenanceCount: 0,
    unboundCount: 0,
    largeRoomCount: 0
  }
}

async function fetchRoomLocations() {
  try {
    const response = await getRoomLocationsApi()
    locationCatalog.value = response.data
  } catch {
    locationCatalog.value = []
  }
}

async function fetchDeviceInventory() {
  try {
    const response = await getAdminDeviceListApi({
      currentPage: 1,
      size: 200
    })
    deviceInventory.value = response.data.list
  } catch {
    deviceInventory.value = []
  }
}

async function fetchRoomCollection() {
  const query = {
    keyword: searchKeyword.value.trim() || undefined,
    status: statusFilter.value === "all" ? undefined : statusFilter.value,
    capacityType: capacityFilter.value === "all" ? undefined : capacityFilter.value,
    location: locationFilter.value === "all" ? undefined : locationFilter.value,
    deviceIds: deviceFilterIds.value.length > 0 ? deviceFilterIds.value.join(",") : undefined
  }

  try {
    const [pagedResponse, filteredResponse] = await Promise.all([
      getAdminRoomListApi({
        currentPage: roomPage.value,
        size: roomPageSize.value,
        ...query
      }),
      getAdminRoomListApi({
        currentPage: 1,
        size: 200,
        ...query
      })
    ])

    roomCollection.value = pagedResponse.data.list.map(normalizeRoomItem)
    filteredRoomCollection.value = filteredResponse.data.list.map(normalizeRoomItem)
    roomTotal.value = pagedResponse.data.total
    roomStats.value = pagedResponse.data.stats
  } catch {
    filteredRoomCollection.value = []
    roomCollection.value = []
    roomTotal.value = 0
    roomStats.value = createEmptyRoomStats()
    ElMessage.error("会议室列表加载失败")
  }

  if (selectedRoomId.value !== null) {
    const nextSelected = filteredRoomCollection.value.find((item) => item.id === selectedRoomId.value)
    if (nextSelected) {
      selectedRoomSnapshot.value = nextSelected
    }
  }

  if (hasHydratedRoomPage) {
    roomPageTransitionKey.value += 1
  } else {
    hasHydratedRoomPage = true
  }
}

onMounted(async () => {
  await Promise.all([fetchRoomLocations(), fetchDeviceInventory(), fetchRoomCollection()])
  window.addEventListener("pointerdown", handleGlobalPointerDown)
  window.addEventListener("keydown", handleEscape)
  window.addEventListener("resize", handleGlobalPointerDown)
  window.addEventListener("scroll", handleGlobalPointerDown, true)
})

onBeforeUnmount(() => {
  clearRadialTimers()
  window.removeEventListener("pointerdown", handleGlobalPointerDown)
  window.removeEventListener("keydown", handleEscape)
  window.removeEventListener("resize", handleGlobalPointerDown)
  window.removeEventListener("scroll", handleGlobalPointerDown, true)
})

function openDetail(room: AdminRoomListItem) {
  selectedRoomId.value = room.id
  selectedRoomSnapshot.value = room
  detailDrawerVisible.value = true
}

function openCreateDialog() {
  roomForm.value = createEmptyRoomForm()
  upsertDialogVisible.value = true
}

function openEditDialog(room: AdminRoomListItem) {
  selectedRoomId.value = room.id
  selectedRoomSnapshot.value = room
  roomForm.value = {
    id: room.id,
    roomCode: room.roomCode,
    name: room.name,
    location: room.location,
    capacity: room.capacity,
    status: room.status,
    description: room.description,
    maintenanceRemark: room.maintenanceRemark ?? "",
    deviceBindings: room.devices.map(device => ({
      deviceId: device.id,
      quantity: device.quantity
    }))
  }
  upsertDialogVisible.value = true
}

function getRoomSaveErrorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : ""
  const lowerMessage = message.toLowerCase()
  if (lowerMessage.includes("roomcode already exists") || message.includes("会议室编码已存在")) return "会议室编码已存在，请更换编码"
  return /[\u4E00-\u9FFF]/.test(message) ? message : "会议室保存失败，请稍后重试"
}

function getRoomDeleteErrorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : ""
  const lowerMessage = message.toLowerCase()
  if (
    lowerMessage.includes("room has related reservations")
    || (lowerMessage.includes("reservation") && lowerMessage.includes("cannot be deleted"))
    || message.includes("预约")
  ) {
    return "该会议室已有预约记录，暂不允许删除"
  }
  return /[\u4E00-\u9FFF]/.test(message) ? message : "会议室删除失败，请稍后重试"
}

async function submitRoomFormPayload(formData: RoomUpsertForm) {
  const payload = {
    ...formData,
    roomCode: formData.roomCode.trim(),
    name: formData.name.trim(),
    location: formData.location.trim(),
    description: formData.description.trim(),
    maintenanceRemark: formData.maintenanceRemark.trim(),
    deviceBindings: formData.deviceBindings
      .filter(item => item.quantity > 0)
      .map(item => ({ ...item }))
  }

  if (!payload.roomCode || !payload.name || !payload.location) {
    ElMessage.warning("请完善房间编码、名称和位置")
    return
  }
  if (payload.capacity <= 0) {
    ElMessage.warning("容量需要大于 0")
    return
  }
  if (payload.status === "MAINTENANCE" && !payload.maintenanceRemark) {
    ElMessage.warning("请填写维护备注")
    return
  }

  try {
    if (payload.id === null) {
      const response = await createAdminRoomApi({
        roomCode: payload.roomCode,
        name: payload.name,
        location: payload.location,
        capacity: payload.capacity,
        status: payload.status,
        description: payload.description,
        maintenanceRemark: payload.status === "MAINTENANCE" ? payload.maintenanceRemark : ""
      })
      const createdRoomId = Number(response.data)
      if (payload.deviceBindings.length > 0 && Number.isFinite(createdRoomId)) {
        await updateAdminRoomDevicesApi(createdRoomId, {
          devices: payload.deviceBindings
        })
      }
      await fetchRoomCollection()
      upsertDialogVisible.value = false
      ElMessage.success("会议室已新增")
      return
    }

    await updateAdminRoomApi(payload.id, {
      roomCode: payload.roomCode,
      name: payload.name,
      location: payload.location,
      capacity: payload.capacity,
      status: payload.status,
      description: payload.description,
      maintenanceRemark: payload.status === "MAINTENANCE" ? payload.maintenanceRemark : ""
    })
    await updateAdminRoomDevicesApi(payload.id, {
      devices: payload.deviceBindings
    })
    await fetchRoomCollection()
    upsertDialogVisible.value = false
    selectedRoomSnapshot.value = roomCollection.value.find((room) => room.id === payload.id) ?? selectedRoomSnapshot.value
    ElMessage.success("会议室已更新")
    return
  } catch (error) {
    ElMessage.error(getRoomSaveErrorMessage(error))
  }
}

async function toggleRoomStatus(room: AdminRoomListItem) {
  const nextStatus: RoomStatus = room.status === "AVAILABLE" ? "MAINTENANCE" : "AVAILABLE"

  if (nextStatus === "MAINTENANCE") {
    openMaintenanceDialog(room)
    return
  }

  try {
    await updateAdminRoomStatusApi(room.id, {
      status: nextStatus,
      maintenanceRemark: ""
    })
    await fetchRoomCollection()
    ElMessage.success("已恢复为可用")
    return
  } catch {
    ElMessage.error("会议室状态更新失败，请稍后重试")
  }
}

function openMaintenanceDialog(room: AdminRoomListItem) {
  maintenanceTargetRoom.value = room
  maintenanceRemarkDraft.value = room.maintenanceRemark?.trim() ?? ""
  maintenanceDialogVisible.value = true
}

function closeMaintenanceDialog() {
  if (maintenanceSubmitting.value) return
  maintenanceDialogVisible.value = false
  maintenanceTargetRoom.value = null
  maintenanceRemarkDraft.value = ""
}

async function submitMaintenanceStatus() {
  if (!maintenanceTargetRoom.value) return
  const remark = maintenanceRemarkDraft.value.trim()

  if (!remark) {
    ElMessage.warning("请填写维护备注")
    return
  }

  const room = maintenanceTargetRoom.value
  maintenanceSubmitting.value = true
  try {
    await updateAdminRoomStatusApi(room.id, {
      status: "MAINTENANCE",
      maintenanceRemark: remark
    })
    await fetchRoomCollection()
    maintenanceDialogVisible.value = false
    maintenanceTargetRoom.value = null
    maintenanceRemarkDraft.value = ""
    ElMessage.success("已切换为维护中")
    return
  } catch {
    ElMessage.error("会议室状态更新失败，请稍后重试")
  } finally {
    maintenanceSubmitting.value = false
  }
}

function deleteRoom(room: AdminRoomListItem) {
  closeRadialMenu(true)
  deleteTargetRoom.value = room
  deleteDialogVisible.value = true
}

function closeDeleteDialog() {
  if (deleteSubmitting.value) return
  deleteDialogVisible.value = false
  deleteTargetRoom.value = null
}

async function confirmDeleteRoom() {
  if (!deleteTargetRoom.value) return
  const room = deleteTargetRoom.value
  deleteSubmitting.value = true
  deleteDialogVisible.value = false
  deleteTargetRoom.value = null

  try {
    await deleteAdminRoomApi(room.id)
    await fetchRoomCollection()
  } catch (error) {
    ElMessage.error(getRoomDeleteErrorMessage(error))
    return
  } finally {
    deleteSubmitting.value = false
  }

  if (selectedRoomId.value === room.id) {
    selectedRoomId.value = null
    selectedRoomSnapshot.value = null
    detailDrawerVisible.value = false
    deviceDialogVisible.value = false
  }

  ElMessage.success("会议室已删除")
}

function openDeviceBindingDialog(room: AdminRoomListItem) {
  selectedRoomId.value = room.id
  selectedRoomSnapshot.value = room
  deviceDrafts.value = deviceInventory.value.map((device) => {
    const bound = room.devices.find(item => item.deviceCode === device.deviceCode)
    return {
      ...device,
      quantity: bound?.quantity ?? 0
    }
  })
  deviceDialogVisible.value = true
}

async function submitDeviceBinding() {
  if (!selectedRoom.value) return

  const devices = deviceDrafts.value
    .filter((item) => item.quantity > 0)
    .map<RoomDeviceData>((item) => ({
      id: item.id,
      deviceCode: item.deviceCode,
      name: item.name,
      quantity: item.quantity,
      total: item.total,
      status: item.status
    }))

  try {
    await updateAdminRoomDevicesApi(selectedRoom.value.id, {
      devices: devices.map((item) => ({
        deviceId: item.id,
        quantity: item.quantity
      }))
    })
    await fetchRoomCollection()
    deviceDialogVisible.value = false
    ElMessage.success("设备绑定已更新")
    return
  } catch {
    ElMessage.error("设备绑定更新失败，请稍后重试")
  }
}
</script>

<template>
  <div class="admin-rooms-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Room Ops Console</p>
        <h1 class="page-hero-title">会议室管理</h1>
      </div>
      <div class="hero-actions">
        <el-button class="primary-button" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          <span>新增会议室</span>
        </el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="item in statsCards" :key="item.label" class="stats-card" :class="`tone-${item.tone}`">
        <div class="stats-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div>
          <div class="stats-label">{{ item.label }}</div>
          <div class="stats-value">{{ padNumber(item.value) }}</div>
        </div>
      </article>
    </section>

    <section class="workspace-grid">
      <main class="control-stage">
        <section class="filter-panel">
          <el-input v-model="searchKeyword" class="search-box" placeholder="搜索会议室、编码、位置" clearable />
          <el-select v-model="statusFilter" class="filter-select">
            <el-option label="全部状态" value="all" />
            <el-option label="可用" value="AVAILABLE" />
            <el-option label="维护中" value="MAINTENANCE" />
          </el-select>
          <el-select v-model="capacityFilter" class="filter-select">
            <el-option label="全部容量" value="all" />
            <el-option label="1-8 人" value="small" />
            <el-option label="9-16 人" value="medium" />
            <el-option label="17 人以上" value="large" />
          </el-select>
          <el-select v-model="locationFilter" class="filter-select">
            <el-option label="全部区域" value="all" />
            <el-option v-for="location in locationOptions" :key="location" :label="location" :value="location" />
          </el-select>
          <el-select v-model="deviceFilterIds" class="filter-select" multiple clearable collapse-tags collapse-tags-tooltip placeholder="全部设备">
            <el-option v-for="device in deviceInventory" :key="device.id" :label="device.name" :value="device.id" />
          </el-select>
        </section>

        <section class="room-list-panel">
          <div class="section-head">
            <div>
              <p class="section-kicker">Control List</p>
              <h2>房间控制清单</h2>
            </div>
            <span class="section-meta">{{ roomTotal }} 间</span>
          </div>

          <div v-if="roomTotal > 0" class="panel-body">
            <div class="room-list-scroll">
              <Transition name="pagination-switch" mode="out-in">
                <div :key="`room-page-${roomPageTransitionKey}`" class="room-control-list">
              <article v-for="room in pagedRooms" :key="room.id" class="room-control-card is-clickable" @click="openDetail(room)" @contextmenu="openRadialMenu($event, room)">
              <div class="card-main">
                <div class="card-head">
                  <div>
                    <div class="card-title-line">
                      <h3>{{ room.name }}</h3>
                      <span class="room-code">{{ room.roomCode }}</span>
                    </div>
                    <p v-if="room.description" class="card-description">{{ room.description }}</p>
                  </div>
                  <div class="card-badges">
                    <span class="status-pill" :class="getStatusTone(room.status)">{{ getStatusLabel(room.status) }}</span>
                    <span v-if="room.status !== 'MAINTENANCE'" class="health-pill" :class="getHealthTone(room)">{{ getHealthLabel(room) }}</span>
                  </div>
                </div>
                <div class="card-metrics">
                  <div class="metric-chip"><el-icon><Location /></el-icon><span>{{ room.location }}</span></div>
                  <div class="metric-chip"><el-icon><OfficeBuilding /></el-icon><span>{{ room.capacity }} 人</span></div>
                </div>
                <div class="device-preview">
                  <span v-if="room.devices.length === 0" class="device-tag muted">未绑定设备</span>
                  <span v-for="device in room.devices.slice(0, 3)" :key="`${room.id}-${device.id}`" class="device-tag" :class="{ disabled: device.status === 'DISABLED' }">{{ device.name }} × {{ device.quantity }}</span>
                  <span v-if="room.devices.length > 3" class="device-tag more">+{{ room.devices.length - 3 }}</span>
                </div>
              </div>

              </article>
                </div>
              </Transition>
            </div>
            <el-pagination
              class="panel-pagination"
              layout="sizes, prev, pager, next"
              :current-page="roomPage"
              :page-size="roomPageSize"
              :page-sizes="pageSizeOptions"
              :total="roomTotal"
              @current-change="roomPage = $event"
              @size-change="handleRoomPageSizeChange"
            />
          </div>
          <div v-else class="panel-empty">--</div>
        </section>
      </main>

      <aside class="signal-rail">
        <section class="panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">Capacity</p>
              <h2>容量分布</h2>
            </div>
          </div>
          <div class="capacity-stack">
            <div v-for="item in capacityDistribution" :key="item.label" class="capacity-row">
              <div>
                <div class="capacity-label">{{ item.label }}</div>
                <div class="capacity-hint">{{ item.hint }}</div>
              </div>
              <strong>{{ padNumber(item.value) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="section-head compact">
            <div>
              <p class="section-kicker">Watch List</p>
              <h2>运维清单</h2>
            </div>
          </div>
          <div v-if="watchTotal > 0" class="panel-body">
            <Transition name="pagination-switch" mode="out-in">
              <div :key="`watch-page-${watchPage}-${watchPageSize}`" class="watch-list">
              <div v-for="room in pagedWatchRooms" :key="room.id" class="watch-item">
                <div>
                  <div class="watch-title">{{ room.name }}</div>
                  <div class="watch-sub">{{ getHealthLabel(room) }}</div>
                </div>
                <el-button text @click="openDetail(room)">查看</el-button>
              </div>
              </div>
            </Transition>
            <el-pagination
              class="panel-pagination"
              layout="sizes, prev, pager, next"
              :current-page="watchPage"
              :page-size="watchPageSize"
              :page-sizes="pageSizeOptions"
              :total="watchTotal"
              @current-change="watchPage = $event"
              @size-change="handleWatchPageSizeChange"
            />
          </div>
          <div v-else class="panel-empty">--</div>
        </section>
      </aside>
    </section>

    <div v-if="radialMenuVisible" class="radial-layer" @click="closeRadialMenu()" @contextmenu.prevent>
      <div class="radial-ripple" :key="radialRippleKey" :class="{ 'is-active': radialRippleActive }" :style="radialMenuStyle" />
      <div class="radial-menu" :class="{ 'is-expanded': radialMenuExpanded }" :style="radialMenuStyle" @click.stop @contextmenu.prevent>
        <button
          v-for="action in radialActions"
          :key="action.key"
          class="radial-sector"
          :class="`sector-${action.key}`"
          type="button"
          @click.stop="runRadialAction(action.key)"
        >
          <el-icon><component :is="action.icon" /></el-icon>
          <span>{{ action.label }}</span>
        </button>
        <div class="radial-core" />
      </div>
    </div>

    <el-drawer v-model="detailDrawerVisible" size="480px" class="room-detail-drawer" :with-header="false">
      <div v-if="selectedRoom" class="drawer-body">
        <div class="drawer-hero">
          <div>
            <p class="eyebrow">Room File</p>
            <h2>{{ selectedRoom.name }}</h2>
            <p class="drawer-sub">{{ selectedRoom.roomCode }} · {{ selectedRoom.location }}</p>
          </div>
          <span class="status-pill" :class="getStatusTone(selectedRoom.status)">{{ getStatusLabel(selectedRoom.status) }}</span>
        </div>

        <div class="detail-grid">
          <div class="detail-card"><span>可容纳人数</span><strong>{{ selectedRoom.capacity }} 人</strong></div>
          <div class="detail-card"><span>绑定设备总数</span><strong>{{ selectedRoom.deviceCount }}</strong></div>
          <div class="detail-card"><span>设备概况</span><strong>{{ selectedRoom.deviceBindingSummary }}</strong></div>
          <div class="detail-card"><span>配置状态</span><strong>{{ getHealthLabel(selectedRoom) }}</strong></div>
        </div>

        <section class="detail-section">
          <div class="detail-title"><el-icon><OfficeBuilding /></el-icon>空间说明</div>
          <div class="detail-note">{{ selectedRoom.description || "--" }}</div>
        </section>

        <section class="detail-section">
          <div class="detail-title"><el-icon><Monitor /></el-icon>设备绑定</div>
          <div v-if="selectedRoom.devices.length > 0" class="detail-device-list">
            <div v-for="device in selectedRoom.devices" :key="`${selectedRoom.id}-${device.id}`" class="detail-device-item">
              <div>
                <div class="device-title">{{ device.name }}</div>
                <div class="device-meta">{{ device.deviceCode }} · 库存 {{ device.total }}</div>
              </div>
              <div class="device-side">
                <span class="device-qty">× {{ device.quantity }}</span>
                <span class="device-state" :class="{ disabled: device.status === 'DISABLED' }">{{ device.status === "DISABLED" ? "停用" : "可用" }}</span>
              </div>
            </div>
          </div>
          <div v-else class="detail-note">--</div>
        </section>

        <section class="detail-section">
          <div class="detail-title"><el-icon><Warning /></el-icon>运维提示</div>
          <div v-if="selectedRoomWarnings.length > 0" class="warning-list">
            <div v-for="warning in selectedRoomWarnings" :key="warning" class="warning-item">{{ warning }}</div>
          </div>
          <div v-else class="detail-note">--</div>
        </section>

        <div class="drawer-actions">
          <el-button class="ghost-button" @click="openEditDialog(selectedRoom)">编辑房间</el-button>
          <el-button class="ghost-button" @click="openDeviceBindingDialog(selectedRoom)">绑定设备</el-button>
          <el-button class="primary-button small" @click="toggleRoomStatus(selectedRoom)">{{ selectedRoom.status === "AVAILABLE" ? "转维护" : "恢复可用" }}</el-button>
          <el-button class="ghost-button is-danger" @click="deleteRoom(selectedRoom)">删除会议室</el-button>
        </div>
      </div>
    </el-drawer>

    <RoomUpsertDialog
      v-model="upsertDialogVisible"
      :form-data="roomForm"
      :device-options="deviceInventory"
      @submit="submitRoomFormPayload"
    />

    <el-dialog v-model="deviceDialogVisible" width="min(760px, calc(100vw - 32px))" class="room-device-dialog" destroy-on-close align-center>
      <template #header>
        <div class="dialog-header">
          <div>
            <p class="eyebrow">Device Binding</p>
            <h3>设备绑定</h3>
          </div>
        </div>
      </template>
      <div class="binding-list">
        <div v-for="device in deviceDrafts" :key="device.id" class="binding-item" :class="{ disabled: device.status === 'DISABLED' && device.quantity === 0 }">
          <div>
            <div class="device-title">{{ device.name }}</div>
            <div class="device-meta">{{ device.deviceCode }} · 库存 {{ device.total }}</div>
          </div>
          <div class="binding-side">
            <span class="device-state" :class="{ disabled: device.status === 'DISABLED' }">{{ device.status === "DISABLED" ? "停用" : "可用" }}</span>
            <el-input-number v-model="device.quantity" :min="0" :max="device.total" controls-position="right" :disabled="device.status === 'DISABLED' && device.quantity === 0" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer device-dialog-footer">
          <el-button class="ghost-button" @click="deviceDialogVisible = false">暂不修改</el-button>
          <el-button class="primary-button small" @click="submitDeviceBinding">保存绑定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="maintenanceDialogVisible"
      width="min(520px, calc(100vw - 32px))"
      class="room-maintenance-dialog"
      destroy-on-close
      align-center
      :show-close="false"
      :close-on-click-modal="!maintenanceSubmitting"
      :close-on-press-escape="!maintenanceSubmitting"
      @closed="closeMaintenanceDialog"
    >
      <template #header>
        <div class="dialog-header">
          <div>
            <p class="eyebrow">Maintenance Note</p>
            <h3>转维护</h3>
          </div>
        </div>
      </template>
      <div class="maintenance-dialog-panel">
        <div v-if="maintenanceTargetRoom" class="dialog-room-title">{{ maintenanceTargetRoom.name }}</div>
        <p class="dialog-copy">转入维护状态前，请填写维护原因或预计恢复时间，方便预约人员识别房间状态。</p>
        <el-input
          v-model="maintenanceRemarkDraft"
          type="textarea"
          :rows="4"
          maxlength="120"
          show-word-limit
          placeholder="例如：投影线路检修，预计今天 18:00 前恢复"
        />
      </div>
      <template #footer>
        <div class="dialog-footer action-dialog-footer">
          <el-button class="ghost-button" :disabled="maintenanceSubmitting" @click="closeMaintenanceDialog">取消</el-button>
          <el-button class="primary-button small" :loading="maintenanceSubmitting" @click="submitMaintenanceStatus">确认转维护</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      width="min(460px, calc(100vw - 32px))"
      class="room-delete-dialog"
      destroy-on-close
      align-center
      :show-close="false"
      :close-on-click-modal="!deleteSubmitting"
      :close-on-press-escape="!deleteSubmitting"
      @closed="closeDeleteDialog"
    >
      <template #header>
        <div class="dialog-header">
          <div>
            <p class="eyebrow">Delete Room</p>
            <h3>删除会议室</h3>
          </div>
        </div>
      </template>
      <div class="delete-dialog-panel">
        <div v-if="deleteTargetRoom" class="dialog-room-title">{{ deleteTargetRoom.name }}</div>
        <p class="dialog-copy">删除后该会议室将从管理列表移除。若会议室已有预约记录，系统会阻止删除。</p>
      </div>
      <template #footer>
        <div class="dialog-footer action-dialog-footer">
          <el-button class="ghost-button" :disabled="deleteSubmitting" @click="closeDeleteDialog">取消</el-button>
          <el-button class="ghost-button is-danger" :loading="deleteSubmitting" @click="confirmDeleteRoom">确认删除</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.admin-rooms-page {
  --panel-bg: rgba(255, 255, 255, 0.78);
  --panel-border: rgba(255, 255, 255, 0.88);
  --text-main: #1a3046;
  --text-sub: #71839a;
  --steel: #6c8de8;
  --mint: #52c5b1;
  --rose: #ec8aa4;
  --amber: #d6a64b;
  --violet: #8d8ce9;
  min-height: 100%;
  padding: 24px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 0% 0%, rgba(112, 149, 219, 0.2), transparent 25%),
    radial-gradient(circle at 100% 10%, rgba(83, 197, 177, 0.18), transparent 30%),
    linear-gradient(180deg, #f6f7fb 0%, #eef1f7 100%);
}

.stats-card,
.signal-card,
.filter-panel,
.room-list-panel,
.panel,
.detail-card,
.detail-note,
.detail-device-item,
.warning-item,
.binding-item {
  border: 1px solid var(--panel-border);
  background: var(--panel-bg);
  box-shadow: 0 24px 60px rgba(61, 83, 109, 0.11);
  backdrop-filter: blur(18px);
}

.filter-panel,
.room-list-panel,
.panel {
  border-radius: 30px;
}

.eyebrow,
.section-kicker,
.stats-label,
.signal-detail,
.room-code,
.card-description,
.capacity-hint,
.device-meta,
.detail-card span,
.detail-note,
.warning-item,
.watch-sub {
  color: var(--text-sub);
}

.section-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.dialog-header h3,
.drawer-hero h2 {
  margin: 8px 0 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
  animation: rise-in 0.62s ease;
}

.stats-card {
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: 24px;
}

.stats-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 18px;
  font-size: 22px;
}

.stats-card.tone-steel .stats-icon { background: rgba(108, 141, 232, 0.14); color: var(--steel); }
.stats-card.tone-mint .stats-icon { background: rgba(82, 197, 177, 0.16); color: var(--mint); }
.stats-card.tone-rose .stats-icon { background: rgba(236, 138, 164, 0.16); color: var(--rose); }
.stats-card.tone-amber .stats-icon { background: rgba(214, 166, 75, 0.16); color: var(--amber); }
.stats-card.tone-violet .stats-icon { background: rgba(141, 140, 233, 0.16); color: var(--violet); }

.stats-label { font-size: 12px; }
.stats-value { margin-top: 5px; font-size: 28px; font-weight: 700; }

.signal-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 16px;
  animation: rise-in 0.72s ease;
}

.signal-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
}

.signal-card strong { font-size: 22px; }
.signal-title { font-size: 14px; font-weight: 700; }
.signal-card.tone-warning strong { color: var(--rose); }
.signal-card.tone-risk strong { color: var(--amber); }
.signal-card.tone-accent strong { color: var(--steel); }
.signal-card.tone-muted strong { color: #7d8ea2; }

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.78fr);
  gap: 16px;
  margin-top: 16px;
  height: min(1120px, calc(100vh + 560px));
  align-items: stretch;
  animation: rise-in 0.84s ease;
}

.control-stage,
.signal-rail,
.panel-body,
.room-list-scroll,
.capacity-stack,
.watch-list,
.room-control-list,
.detail-grid,
.detail-device-list,
.warning-list,
.binding-list {
  display: grid;
  gap: 12px;
}

.control-stage,
.signal-rail,
.room-list-panel,
.panel {
  min-height: 0;
}

.control-stage {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
}

.signal-rail {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.filter-panel,
.room-list-panel,
.panel {
  padding: 18px;
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) repeat(4, minmax(0, 0.8fr));
  gap: 12px;
}

.search-box,
.filter-select {
  width: 100%;
}

.room-list-panel,
.panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.panel-body {
  grid-template-rows: minmax(0, 1fr) auto;
  min-height: 0;
}

.room-list-scroll {
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.section-head,
.card-head,
.card-title-line,
.card-badges,
.card-actions,
.dialog-header,
.dialog-footer,
.drawer-actions,
.detail-device-item,
.device-side,
.binding-item,
.binding-side,
.capacity-row,
.watch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-head { margin-bottom: 16px; align-items: flex-start; }
.section-head.compact { margin-bottom: 12px; }
.section-head h2 { margin: 0; font-size: 23px; line-height: 1.08; letter-spacing: -0.03em; }
.dialog-header h3,
.drawer-hero h2 { font-size: 28px; line-height: 1.06; }

.section-meta,
.status-pill,
.health-pill,
.device-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.section-meta { background: rgba(108, 141, 232, 0.12); color: #5570b3; }

.room-control-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding: 18px;
  border-radius: 26px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.82));
  border: 1px solid rgba(255, 255, 255, 0.88);
  box-shadow: 0 22px 54px rgba(61, 83, 109, 0.1);
}

.room-control-card.is-clickable {
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.room-control-card.is-clickable:hover {
  transform: translateY(-2px);
  border-color: rgba(108, 141, 232, 0.26);
  box-shadow: 0 26px 60px rgba(61, 83, 109, 0.14);
}

.radial-layer {
  position: fixed;
  inset: 0;
  z-index: 90;
}

.radial-ripple,
.radial-menu {
  position: fixed;
  left: 0;
  top: 0;
  transform: translate(-50%, -50%);
}

.radial-ripple {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(109, 141, 232, 0.26), rgba(109, 141, 232, 0.08) 58%, rgba(109, 141, 232, 0) 72%);
  opacity: 0;
  pointer-events: none;
}

.radial-ripple.is-active {
  animation: radial-ripple 0.42s ease-out forwards;
}

.radial-menu {
  width: 208px;
  height: 208px;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.radial-menu.is-expanded {
  opacity: 1;
  pointer-events: auto;
  transform: translate(-50%, -50%) scale(1);
}

.radial-sector,
.radial-core {
  position: absolute;
  border: none;
  transition:
    transform 0.42s cubic-bezier(0.22, 0.84, 0.28, 1),
    opacity 0.32s ease,
    background 0.42s cubic-bezier(0.22, 0.84, 0.28, 1),
    box-shadow 0.42s cubic-bezier(0.22, 0.84, 0.28, 1),
    filter 0.42s cubic-bezier(0.22, 0.84, 0.28, 1);
}

.radial-sector {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 108px;
  height: 108px;
  padding: 16px 12px 12px;
  color: #35506d;
  opacity: 0;
  cursor: pointer;
  box-shadow: 0 20px 40px rgba(56, 77, 101, 0.12);
  backdrop-filter: blur(16px);
}

.radial-sector span {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.radial-sector .el-icon {
  font-size: 18px;
}

.radial-menu.is-expanded .radial-sector {
  opacity: 1;
}

.sector-edit {
  left: 0;
  top: 0;
  border-radius: 999px 22px 18px 22px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(231, 240, 255, 0.92));
  transform: translate(46px, 46px) scale(0.68);
}

.sector-status {
  right: 0;
  top: 0;
  border-radius: 22px 999px 22px 18px;
  background: linear-gradient(135deg, rgba(239, 250, 246, 0.96), rgba(216, 243, 235, 0.94));
  transform: translate(-46px, 46px) scale(0.68);
}

.sector-device {
  left: 0;
  bottom: 0;
  border-radius: 22px 18px 22px 999px;
  background: linear-gradient(135deg, rgba(236, 244, 255, 0.96), rgba(220, 232, 251, 0.94));
  transform: translate(46px, -46px) scale(0.68);
}

.sector-delete {
  right: 0;
  bottom: 0;
  border-radius: 18px 22px 999px 22px;
  background: linear-gradient(135deg, rgba(255, 243, 247, 0.96), rgba(255, 226, 235, 0.94));
  color: #bb5071;
  transform: translate(-46px, -46px) scale(0.68);
}

.radial-menu.is-expanded .sector-edit,
.radial-menu.is-expanded .sector-status,
.radial-menu.is-expanded .sector-device,
.radial-menu.is-expanded .sector-delete {
  transform: translate(0, 0) scale(1);
}

.radial-sector:hover {
  z-index: 2;
  box-shadow: 0 32px 72px rgba(56, 77, 101, 0.16);
  filter: saturate(1.04) brightness(1.02);
}

.radial-menu.is-expanded .sector-edit:hover {
  transform: translate(-12px, -12px) scale(1.03);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(236, 243, 255, 0.96));
}

.radial-menu.is-expanded .sector-status:hover {
  transform: translate(12px, -12px) scale(1.03);
  background: linear-gradient(135deg, rgba(244, 253, 249, 0.98), rgba(224, 246, 238, 0.96));
}

.radial-menu.is-expanded .sector-device:hover {
  transform: translate(-12px, 12px) scale(1.03);
  background: linear-gradient(135deg, rgba(241, 247, 255, 0.98), rgba(226, 236, 252, 0.96));
}

.radial-menu.is-expanded .sector-delete:hover {
  transform: translate(12px, 12px) scale(1.03);
  background: linear-gradient(135deg, rgba(255, 247, 249, 0.98), rgba(255, 232, 239, 0.96));
}

.radial-core {
  left: 50%;
  top: 50%;
  width: 58px;
  height: 58px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow:
    0 12px 24px rgba(75, 98, 124, 0.14),
    inset 0 0 0 1px rgba(255, 255, 255, 0.86);
  transform: translate(-50%, -50%) scale(0.82);
}

.radial-menu.is-expanded .radial-core {
  transform: translate(-50%, -50%) scale(1);
}

@keyframes radial-ripple {
  0% {
    opacity: 0.72;
    transform: translate(-50%, -50%) scale(0.4);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(12);
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

.card-title-line { justify-content: flex-start; }
.card-title-line h3 { margin: 0; font-size: 24px; line-height: 1.08; }

.room-code {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(26, 48, 70, 0.07);
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.card-description { margin: 10px 0 0; line-height: 1.7; }

.status-pill.is-available { background: rgba(82, 197, 177, 0.14); color: #14856f; }
.status-pill.is-maintenance,
.health-pill.is-maintenance { background: rgba(236, 138, 164, 0.14); color: #cc5377; }
.health-pill.is-warning { background: rgba(214, 166, 75, 0.14); color: #a77716; }
.health-pill.is-healthy { background: rgba(108, 141, 232, 0.14); color: #5873c5; }

.card-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.metric-chip,
.device-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(245, 248, 253, 0.96);
  color: #557086;
  font-size: 12px;
  font-weight: 600;
}

.device-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.device-tag.disabled,
.device-state.disabled,
.binding-item.disabled {
  opacity: 0.65;
}

.device-tag.muted,
.device-tag.more { background: rgba(113, 131, 154, 0.12); }

.card-actions {
  align-items: stretch;
  flex-direction: column;
  min-width: 142px;
}

.ghost-button,
.primary-button {
  border-radius: 999px;
  font-weight: 700;
}

.ghost-button {
  width: 100%;
  min-height: 38px;
  color: #526b86;
  background: rgba(255, 255, 255, 0.74);
  border-color: rgba(148, 164, 188, 0.18);
}

.ghost-button.is-danger {
  color: #c14d70;
  background: rgba(255, 242, 246, 0.92);
  border-color: rgba(228, 146, 171, 0.24);
}

.primary-button {
  min-height: 42px;
  border: none;
  background: linear-gradient(135deg, #6d8de8, #57c8b3);
  color: #fff;
  box-shadow: 0 18px 38px rgba(91, 126, 205, 0.24);
}

.primary-button.small { min-height: 40px; padding: 0 18px; }

.capacity-row,
.watch-item,
.detail-card,
.detail-note,
.detail-device-item,
.warning-item,
.binding-item {
  padding: 14px 16px;
  border-radius: 20px;
}

.capacity-label,
.watch-title,
.device-title { font-size: 14px; font-weight: 700; }

.room-control-list,
.watch-list {
  align-content: start;
}

.room-control-list {
  min-height: 0;
}

.panel-pagination {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  color: var(--text-sub);
  font-weight: 700;
}

.drawer-body { display: grid; gap: 14px; }
.drawer-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.drawer-sub { margin: 10px 0 0; color: var(--text-sub); }

.detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.detail-card strong { display: block; margin-top: 6px; line-height: 1.5; }
.detail-section { display: grid; gap: 6px; }
.detail-title { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #607287; }

.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.form-field,
.binding-side { display: grid; gap: 8px; }
.form-field-wide { grid-column: 1 / -1; }
.form-field label { font-size: 12px; font-weight: 700; color: #6d8094; }

.maintenance-dialog-panel,
.delete-dialog-panel {
  display: grid;
  gap: 12px;
}

.dialog-room-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.18;
}

.dialog-copy {
  margin: 0;
  color: var(--text-sub);
  line-height: 1.7;
}

.form-field :deep(.el-select),
.form-field :deep(.el-input-number),
.form-field :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}

.dialog-footer,
.drawer-actions { justify-content: center; margin-top: 10px; }

.device-dialog-footer,
.action-dialog-footer {
  justify-content: flex-end;
}

.device-dialog-footer .ghost-button,
.device-dialog-footer .primary-button,
.action-dialog-footer .ghost-button,
.action-dialog-footer .primary-button {
  width: 132px;
  min-width: 132px;
  min-height: 40px;
  padding: 0 18px;
}

.device-dialog-footer :deep(.el-button + .el-button),
.action-dialog-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

:deep(.room-detail-drawer .el-drawer),
:deep(.room-upsert-dialog.el-dialog),
:deep(.room-device-dialog.el-dialog),
:deep(.room-maintenance-dialog.el-dialog),
:deep(.room-delete-dialog.el-dialog),
:deep(.room-upsert-dialog .el-dialog),
:deep(.room-device-dialog .el-dialog),
:deep(.room-maintenance-dialog .el-dialog),
:deep(.room-delete-dialog .el-dialog) {
  background: rgba(247, 249, 253, 0.98);
}

:deep(.room-upsert-dialog.el-dialog),
:deep(.room-device-dialog.el-dialog),
:deep(.room-maintenance-dialog.el-dialog),
:deep(.room-delete-dialog.el-dialog),
:deep(.room-upsert-dialog .el-dialog),
:deep(.room-device-dialog .el-dialog),
:deep(.room-maintenance-dialog .el-dialog),
:deep(.room-delete-dialog .el-dialog) {
  border-radius: 30px;
  overflow: hidden;
}

:deep(.room-detail-drawer .el-drawer__body) { padding: 22px; }
:deep(.room-upsert-dialog .el-dialog__header),
:deep(.room-device-dialog .el-dialog__header),
:deep(.room-maintenance-dialog .el-dialog__header),
:deep(.room-delete-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 0;
}
:deep(.room-upsert-dialog .el-dialog__body),
:deep(.room-device-dialog .el-dialog__body),
:deep(.room-maintenance-dialog .el-dialog__body),
:deep(.room-delete-dialog .el-dialog__body) { padding: 18px 24px 8px; }
:deep(.room-upsert-dialog .el-dialog__footer),
:deep(.room-device-dialog .el-dialog__footer),
:deep(.room-maintenance-dialog .el-dialog__footer),
:deep(.room-delete-dialog .el-dialog__footer) { padding: 10px 24px 22px; }

@media screen and (max-width: 1480px) {
  .admin-rooms-page {
    --page-topbar-columns: 1fr;
    --page-topbar-actions-justify: flex-start;
  }

  .stats-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .workspace-grid { grid-template-columns: 1fr; }
  .workspace-grid { height: auto; }
}

@media screen and (max-width: 980px) {
  .filter-panel,
  .form-grid,
  .detail-grid { grid-template-columns: 1fr; }
  .room-control-card { grid-template-columns: 1fr; }
  .card-actions { min-width: auto; }
}

@media screen and (max-width: 768px) {
  .admin-rooms-page { padding: 16px; }
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .card-head,
  .card-badges,
  .drawer-hero,
  .dialog-header,
  .dialog-footer,
  .drawer-actions,
  .binding-item,
  .binding-side {
    flex-direction: column;
    align-items: stretch;
  }

  .device-dialog-footer .ghost-button,
  .device-dialog-footer .primary-button,
  .action-dialog-footer .ghost-button,
  .action-dialog-footer .primary-button {
    width: 100%;
    min-width: 0;
  }
}
</style>
