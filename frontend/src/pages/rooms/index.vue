<script lang="ts" setup>
import { ElMessage } from "element-plus"
import { computed, onMounted, ref, watch } from "vue"
import { Calendar, Clock, Location, Monitor, MoreFilled, Search, User } from "@element-plus/icons-vue"
import ReservationCreateDialog from "@/components/ReservationCreateDialog.vue"
import type { ReservationCreateDraft } from "@/components/reservation-create-dialog"
import { createReservationCreateDraft } from "@/components/reservation-create-dialog"
import { getRoomDeviceOptionsApi, getRoomListApi, getRoomLocationsApi } from "@/common/apis/rooms"
import type { CapacityType, RoomData, RoomDeviceOptionData, RoomListStats, RoomStatus } from "@/common/apis/rooms/type"
import { CARD_PAGE_SIZE_OPTIONS } from "@/common/constants/pagination"

const pageSizeOptions = CARD_PAGE_SIZE_OPTIONS
const pageSize = ref(6)

const search = ref("")
const statusFilter = ref<RoomStatus | "all">("all")
const capacityFilter = ref<CapacityType | "all">("all")
const locationFilter = ref("all")
const deviceFilter = ref<number[]>([])
const currentPage = ref(1)
const roomsPageTransitionKey = ref(0)
const dialogVisible = ref(false)
const reservationDialogVisible = ref(false)
const loading = ref(false)

const roomList = ref<RoomData[]>([])
const total = ref(0)
const locationOptions = ref<string[]>([])
const deviceOptions = ref<RoomDeviceOptionData[]>([])
const selectedRoom = ref<RoomData | null>(null)
const stats = ref<RoomListStats>({
  totalCount: 0,
  availableCount: 0,
  maintenanceCount: 0
})
const visibleDeviceLimit = 2
const reservationDialogPreset = ref<Partial<ReservationCreateDraft> | null>(null)
let hasHydratedRoomsPage = false

const selectedDeviceCount = computed(() => {
  return selectedRoom.value?.devices.reduce((sum, item) => sum + item.quantity, 0) ?? 0
})

onMounted(() => {
  fetchLocations()
  fetchDeviceOptions()
  fetchRooms()
})

watch(search, () => {
  currentPage.value = 1
  fetchRooms()
})

watch([statusFilter, capacityFilter, locationFilter, deviceFilter], () => {
  currentPage.value = 1
  fetchRooms()
})

function fetchRooms() {
  loading.value = true
  return getRoomListApi({
    currentPage: currentPage.value,
    size: pageSize.value,
    keyword: search.value.trim() || undefined,
    status: statusFilter.value === "all" ? undefined : statusFilter.value,
    capacityType: capacityFilter.value === "all" ? undefined : capacityFilter.value,
    location: locationFilter.value === "all" ? undefined : locationFilter.value,
    deviceIds: deviceFilter.value.length > 0 ? deviceFilter.value.join(",") : undefined
  })
    .then((response) => {
      roomList.value = response.data.list
      total.value = response.data.total
      stats.value = response.data.stats
      if (selectedRoom.value) {
        selectedRoom.value = response.data.list.find(room => room.id === selectedRoom.value?.id) ?? selectedRoom.value
      }
      if (hasHydratedRoomsPage) {
        roomsPageTransitionKey.value += 1
      } else {
        hasHydratedRoomsPage = true
      }
    })
    .catch(() => {
      roomList.value = []
      total.value = 0
      selectedRoom.value = null
      if (hasHydratedRoomsPage) {
        roomsPageTransitionKey.value += 1
      } else {
        hasHydratedRoomsPage = true
      }
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchLocations() {
  getRoomLocationsApi()
    .then((response) => {
      locationOptions.value = response.data
    })
    .catch(() => {
      locationOptions.value = []
    })
}

function fetchDeviceOptions() {
  getRoomDeviceOptionsApi()
    .then((response) => {
      deviceOptions.value = response.data
    })
    .catch(() => {
      deviceOptions.value = []
    })
}

function openRoomDialog(room: RoomData) {
  selectedRoom.value = room
  dialogVisible.value = true
}

function openReservationDialog(room: RoomData) {
  if (room.status !== "AVAILABLE") {
    ElMessage.warning("当前会议室不可预约")
    return
  }

  selectedRoom.value = room
  reservationDialogPreset.value = {
    roomId: room.id,
    attendees: Math.min(1, room.capacity)
  }
  reservationDialogVisible.value = true
}

function handleReservationSubmitted() {
  reservationDialogVisible.value = false
  reservationDialogPreset.value = createReservationCreateDraft()
  void fetchRooms()
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchRooms()
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchRooms()
}

function getStatusLabel(status: RoomStatus) {
  return status === "AVAILABLE" ? "可预约" : "维护中"
}

function getCapacityLabel(capacity: number) {
  if (capacity <= 8) return "轻会谈"
  if (capacity <= 16) return "协作型"
  return "多人型"
}

function getRoomScene(room: RoomData) {
  if (room.capacity >= 18) return "适合大型同步、培训与高层汇报"
  if (room.devices.some((device) => device.name.includes("双联显示屏"))) return "适合演示、路演与客户沟通"
  if (room.devices.some((device) => device.name.includes("电子白板"))) return "适合共创讨论与白板推演"
  return "适合高频日常会议与跨组协作"
}

function getStatusTone(status: RoomStatus) {
  return status === "AVAILABLE" ? "is-available" : "is-maintenance"
}

function getVisibleDevices(room: RoomData) {
  return room.devices.slice(0, visibleDeviceLimit)
}

function getHiddenDevices(room: RoomData) {
  return room.devices.slice(visibleDeviceLimit)
}

</script>

<template>
  <div class="rooms-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Meeting Room Workspace</p>
        <h1 class="page-hero-title">会议空间</h1>
      </div>

      <div class="hero-side">
        <div class="hero-metrics">
          <article class="metric-item">
            <div class="metric-icon is-room-total"><el-icon><Monitor /></el-icon></div>
            <div>
              <div class="metric-label">房间总数</div>
              <div class="metric-value">{{ stats.totalCount }}</div>
            </div>
          </article>
          <article class="metric-item">
            <div class="metric-icon is-available"><el-icon><Calendar /></el-icon></div>
            <div>
              <div class="metric-label">可预约</div>
              <div class="metric-value">{{ stats.availableCount }}</div>
            </div>
          </article>
          <article class="metric-item">
            <div class="metric-icon is-maintenance"><el-icon><Clock /></el-icon></div>
            <div>
              <div class="metric-label">维护中</div>
              <div class="metric-value">{{ stats.maintenanceCount }}</div>
            </div>
          </article>
          <article class="metric-item">
            <div class="metric-icon is-result"><el-icon><Search /></el-icon></div>
            <div>
              <div class="metric-label">检索结果</div>
              <div class="metric-value">{{ total }}</div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section v-loading="loading" class="rooms-list-panel">
      <div class="section-head">
        <div>
          <p class="panel-kicker">Room Directory</p>
          <h2>房间清单</h2>
        </div>

        <div class="panel-filters">
          <div class="filters filters-compact">
            <el-input
              v-model="search"
              class="search-box"
              placeholder="搜索会议室、编码、位置"
              :prefix-icon="Search"
              clearable
            />
            <el-select v-model="statusFilter" class="filter-item">
              <el-option label="全部状态" value="all" />
              <el-option label="可预约" value="AVAILABLE" />
              <el-option label="维护中" value="MAINTENANCE" />
            </el-select>
            <el-select v-model="capacityFilter" class="filter-item">
              <el-option label="全部容量" value="all" />
              <el-option label="1-8 人" value="small" />
              <el-option label="9-16 人" value="medium" />
              <el-option label="17 人以上" value="large" />
            </el-select>
            <el-select v-model="locationFilter" class="filter-item">
              <el-option label="全部区域" value="all" />
              <el-option
                v-for="location in locationOptions"
                :key="location"
                :label="location"
                :value="location"
              />
            </el-select>
            <el-select
              v-model="deviceFilter"
              class="filter-item"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              placeholder="设备条件"
            >
              <el-option
                v-for="device in deviceOptions"
                :key="device.id"
                :label="device.name"
                :value="device.id"
              />
            </el-select>
          </div>
        </div>
      </div>

      <Transition name="page-fade" mode="out-in">
        <div :key="roomsPageTransitionKey" class="rooms-grid">
          <button
            v-for="room in roomList"
            :key="room.id"
            type="button"
            class="room-card"
            @click="openRoomDialog(room)"
          >
            <div class="room-card-head">
              <div>
                <h3>{{ room.name }}</h3>
                <p>{{ room.roomCode }}</p>
              </div>
              <span
                class="room-status"
                :class="[getStatusTone(room.status), 'is-clickable']"
                @click.stop="openReservationDialog(room)"
              >
                {{ getStatusLabel(room.status) }}
              </span>
            </div>

            <div class="room-meta">
              <span>
                <el-icon><Location /></el-icon>
                {{ room.location }}
              </span>
              <span>
                <el-icon><User /></el-icon>
                {{ room.capacity }} 人
              </span>
              <span>
                <el-icon><Monitor /></el-icon>
                {{ getCapacityLabel(room.capacity) }}
              </span>
            </div>

            <p class="room-description">{{ room.description }}</p>

            <div class="room-devices">
              <span
                v-for="device in getVisibleDevices(room)"
                :key="device.id"
                class="device-chip"
                :class="{ 'is-disabled': device.status === 'DISABLED' }"
              >
                {{ device.name }} × {{ device.quantity }}
              </span>

              <el-tooltip
                v-if="getHiddenDevices(room).length > 0"
                placement="top"
                effect="light"
                :offset="14"
                popper-class="room-device-popover"
              >
                <template #content>
                  <div class="device-popover-panel">
                    <div class="device-popover-list">
                      <div
                        v-for="(device, index) in getHiddenDevices(room)"
                        :key="device.id"
                        class="device-popover-item"
                        :class="{ 'is-disabled': device.status === 'DISABLED' }"
                        :style="{ '--device-delay': `${index * 60}ms` }"
                      >
                        <div class="device-popover-copy">
                          <span class="device-popover-name">{{ device.name }}</span>
                          <span class="device-popover-code">{{ device.deviceCode }}</span>
                        </div>
                        <span class="device-popover-qty">{{ device.quantity }} 台</span>
                      </div>
                    </div>
                  </div>
                  <div v-if="false" class="device-tooltip-list">
                    <div v-for="device in room.devices" :key="device.id" class="device-tooltip-item">
                      <span>{{ device.name }}</span>
                      <span>{{ device.quantity }} 台</span>
                    </div>
                  </div>
                </template>
                <span class="device-overflow-trigger" @click.stop>
                  <el-icon><MoreFilled /></el-icon>
                </span>
              </el-tooltip>
            </div>
          </button>
        </div>
      </Transition>

      <el-empty v-if="!loading && roomList.length === 0" description="当前条件下没有会议室" />

      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :current-page="currentPage"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="min(860px, calc(100vw - 32px))"
      class="room-dialog"
      destroy-on-close
      align-center
    >
      <template #header>
        <div v-if="selectedRoom" class="dialog-header">
          <p class="detail-code">{{ selectedRoom.roomCode }}</p>
          <h2>{{ selectedRoom.name }}</h2>
          <p class="detail-description">{{ selectedRoom.description }}</p>
        </div>
      </template>

      <div v-if="selectedRoom" class="dialog-body">
        <div class="detail-grid">
          <div class="detail-metric">
            <span>所在区域</span>
            <strong>{{ selectedRoom.location }}</strong>
          </div>
          <div class="detail-metric">
            <span>可容纳人数</span>
            <strong>{{ selectedRoom.capacity }} 人</strong>
          </div>
          <div class="detail-metric">
            <span>绑定设备数</span>
            <strong>{{ selectedDeviceCount }}</strong>
          </div>
          <div class="detail-metric">
            <span>状态</span>
            <strong>{{ getStatusLabel(selectedRoom.status) }}</strong>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-head compact">
            <div>
              <h3>空间判断</h3>
              <p>{{ getRoomScene(selectedRoom) }}</p>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-head compact">
            <div>
              <h3>设备绑定</h3>
            </div>
          </div>

          <div class="device-list">
            <div v-for="device in selectedRoom.devices" :key="device.id" class="device-row">
              <div>
                <div class="device-name">{{ device.name }}</div>
                <div class="device-code">{{ device.deviceCode }}</div>
              </div>
              <div class="device-stats">
                <span>房间绑定 {{ device.quantity }}</span>
                <span>库存 {{ device.total }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <ReservationCreateDialog
      v-model="reservationDialogVisible"
      :room="selectedRoom"
      :rooms="roomList"
      :preset="reservationDialogPreset"
      @submitted="handleReservationSubmitted"
    />
  </div>
</template>

<style lang="scss" scoped>
.rooms-page {
  --page-topbar-columns: minmax(0, 1fr) minmax(520px, 1.12fr);
  --page-topbar-columns-md: 1fr;
  --page-topbar-metrics-columns: repeat(4, minmax(0, 1fr));
  --page-topbar-metrics-columns-md: repeat(2, minmax(0, 1fr));
  --page-topbar-metrics-columns-sm: 1fr;
  --page-topbar-side-justify-sm: flex-start;
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(41, 98, 255, 0.12), transparent 26%),
    linear-gradient(180deg, #f4f7fb 0%, #eef3f8 100%);
  color: #122033;
}

.metric-icon.is-available {
  background: linear-gradient(135deg, rgba(88, 203, 179, 0.18), rgba(127, 194, 255, 0.16));
}

.metric-icon.is-maintenance {
  background: linear-gradient(135deg, rgba(255, 194, 102, 0.2), rgba(255, 149, 149, 0.16));
  color: #d67a36;
}

.metric-icon.is-result {
  background: linear-gradient(135deg, rgba(124, 165, 255, 0.18), rgba(153, 208, 255, 0.18));
}

.filters {
  display: grid;
  grid-template-columns: minmax(220px, 1.25fr) repeat(4, minmax(110px, 0.8fr));
  gap: 10px;
  width: 100%;
  align-items: center;
}

.search-box,
.filter-item {
  width: 100%;
}

.search-box :deep(.el-input__wrapper),
.filter-item :deep(.el-select__wrapper) {
  min-height: 42px;
}

.rooms-list-panel {
  padding: 22px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(18, 32, 51, 0.06);
  border-radius: 24px;
  backdrop-filter: blur(12px);
  animation: rise-in 0.8s ease;
}

.panel-filters {
  display: flex;
  justify-content: flex-end;
  flex: 1;
}

.filters-compact {
  width: min(1080px, 100%);
}

.filters-compact :deep(.el-input__wrapper),
.filters-compact :deep(.el-select__wrapper) {
  min-height: 36px;
  padding-top: 0;
  padding-bottom: 0;
  font-size: 12px;
}

.filters-compact :deep(.el-input__inner),
.filters-compact :deep(.el-select__selected-item),
.filters-compact :deep(.el-select__placeholder) {
  font-size: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  > div:first-child {
    flex-shrink: 0;
  }

  h2,
  h3 {
    margin: 0;
    font-weight: 700;
  }

  h2 {
    font-size: 23px;
    line-height: 1.08;
    letter-spacing: -0.03em;
    color: #17324a;
  }

  h3 {
    font-size: 20px;
    line-height: 1.06;
  }

  p {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 1.7;
    color: #67778d;
  }

  &.compact h3 {
    font-size: 17px;
  }
}

.panel-kicker {
  margin: 0 0 6px;
  color: #7c90a7;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.rooms-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.room-card {
  width: 100%;
  padding: 18px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(245, 248, 252, 0.86), rgba(255, 255, 255, 0.96));
  text-align: left;
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    background-color 0.22s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 18px 36px rgba(18, 32, 51, 0.08);
  }
}

.room-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;

  h3 {
    margin: 0;
    font-size: 22px;
    line-height: 1.05;
    font-weight: 700;
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: #75849a;
  }
}

.room-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 74px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;

  &.is-available {
    background: #e9f7ef;
    color: #1f7a43;
  }

  &.is-maintenance {
    background: #fff1f2;
    color: #d43f5d;
  }
}

.room-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #4b5a70;
  }
}

.room-description {
  min-height: 44px;
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: #5f6d82;
}

.room-devices {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  margin-top: 10px;
  min-height: 32px;
  overflow: hidden;
}

.device-chip {
  display: inline-flex;
  align-items: center;
  padding: 7px 10px;
  border-radius: 999px;
  background: #eff4fb;
  color: #35506f;
  font-size: 12px;
  line-height: 1;
  min-width: 0;
  max-width: clamp(112px, 32%, 168px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.is-disabled {
    background: #f3f4f6;
    color: #8a96a8;
  }
}

.device-overflow-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(236, 242, 251, 0.98), rgba(224, 233, 244, 0.92));
  color: #48627e;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 18px rgba(72, 98, 126, 0.16);
  }
}

.device-popover-panel {
  min-width: 248px;
  padding: 10px;
  animation: device-popover-rise 0.24s ease;
}

.device-popover-list {
  display: grid;
  gap: 6px;
}

.device-popover-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(237, 244, 252, 0.88));
  border: 1px solid rgba(219, 231, 244, 0.9);
  opacity: 0;
  transform: translateY(10px);
  animation: device-line-rise 0.34s cubic-bezier(0.22, 1, 0.36, 1) forwards;
  animation-delay: var(--device-delay, 0ms);
}

.device-popover-item.is-disabled {
  background: linear-gradient(135deg, rgba(246, 248, 251, 0.94), rgba(238, 242, 247, 0.9));
}

.device-popover-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.device-popover-name {
  color: #1d344d;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
}

.device-popover-code {
  color: #7b8da2;
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.device-popover-qty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(73, 109, 162, 0.1);
  color: #3d5d85;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.device-popover-item.is-disabled .device-popover-name,
.device-popover-item.is-disabled .device-popover-code,
.device-popover-item.is-disabled .device-popover-qty {
  color: #8a97a7;
}

.device-popover-item.is-disabled .device-popover-qty {
  background: rgba(138, 151, 167, 0.12);
}

.device-tooltip-list {
  display: grid;
  gap: 8px;
  min-width: 180px;
}

.device-tooltip-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  line-height: 1.4;
}

:deep(.room-device-popover.el-popper) {
  padding: 0;
  border: 1px solid rgba(210, 223, 239, 0.92);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(251, 253, 255, 0.96), rgba(242, 247, 253, 0.92));
  box-shadow: 0 20px 34px rgba(32, 53, 78, 0.14);
  backdrop-filter: blur(18px);
  overflow: hidden;
}

:deep(.room-device-popover.el-popper .el-popper__arrow::before) {
  border-color: rgba(238, 244, 251, 0.96);
  background: rgba(248, 251, 255, 0.98);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

.dialog-header {
  padding-right: 28px;
}

.dialog-body {
  animation: dialog-content-in 0.28s ease;
}

.detail-code {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6f8097;
}

.dialog-header h2 {
  margin: 8px 0 10px;
  font-size: 34px;
  line-height: 0.96;
  letter-spacing: -0.04em;
}

.detail-description {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #607087;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-metric {
  padding: 14px 0;
  border-top: 1px solid rgba(18, 32, 51, 0.08);

  span {
    display: block;
    font-size: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #6c7d92;
  }

  strong {
    display: block;
    margin-top: 8px;
    font-size: 18px;
    line-height: 1.2;
    color: #15243a;
  }
}

.detail-section {
  margin-top: 20px;
}

.device-list {
  display: grid;
  gap: 10px;
}

.device-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(18, 32, 51, 0.06);
}

.device-name {
  font-size: 15px;
  font-weight: 600;
}

.device-code {
  margin-top: 4px;
  font-size: 12px;
  color: #73839a;
}

.device-stats {
  display: grid;
  gap: 4px;
  text-align: right;
  font-size: 12px;
  color: #5d6d83;
}

.room-status.is-clickable {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 18px rgba(31, 122, 67, 0.16);
  }
}

.reservation-dialog-header {
  display: grid;
  gap: 6px;
}

.reservation-dialog-eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #7c90a7;
}

.reservation-dialog-header h3 {
  margin: 0;
  font-size: 24px;
  color: #15243a;
}

.reservation-dialog-room {
  margin: 0;
  color: #5e7087;
}

.reservation-form {
  padding-top: 4px;
}

.reservation-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.reservation-form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reservation-form-tip {
  font-size: 13px;
  color: #5e7087;
}

.reservation-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.room-dialog) {
  border-radius: 24px;
}

:deep(.room-dialog .el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  transform-origin: center top;
}

:deep(.reservation-dialog .el-dialog) {
  border-radius: 24px;
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

@keyframes dialog-content-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes device-popover-rise {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes device-line-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

@media screen and (max-width: 1280px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-filters {
    width: 100%;
  }

  .rooms-grid,
  .detail-grid,
  .reservation-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .rooms-page {
    padding: 16px;
  }

  .filters,
  .rooms-grid,
  .detail-grid,
  .reservation-form-grid {
    grid-template-columns: 1fr;
  }

  .reservation-form-foot {
    flex-direction: column;
    align-items: stretch;
  }

  .reservation-form-actions {
    justify-content: stretch;
  }

  .section-head {
    align-items: flex-start;
  }

  .room-card-head,
  .device-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .device-stats {
    text-align: left;
  }

  .room-devices {
    flex-wrap: wrap;
  }

  .device-chip {
    max-width: 100%;
  }

  .panel-filters {
    justify-content: stretch;
  }

  .filters-compact {
    width: 100%;
  }
}
</style>
