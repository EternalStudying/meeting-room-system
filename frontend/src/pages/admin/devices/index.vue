<script lang="ts" setup>
import { computed, ref, watch } from "vue"
import { ElMessage } from "element-plus"
import { Box, CircleCheck, Connection, EditPen, OfficeBuilding, Plus, Search, SwitchButton, Warning } from "@element-plus/icons-vue"
import {
  createAdminDeviceApi,
  deleteAdminDeviceApi,
  getAdminDeviceListApi,
  updateAdminDeviceApi,
  updateAdminDeviceStatusApi
} from "@/common/apis/rooms"
import type { AdminDeviceListItem, AdminDeviceListStats, AdminDeviceUpsertRequestData, DeviceStatus } from "@/common/apis/rooms/type"
import { CARD_PAGE_SIZE_OPTIONS } from "@/common/constants/pagination"

type StatusFilter = DeviceStatus | "all"

interface DeviceForm extends AdminDeviceUpsertRequestData {
  id: number | null
}

const lowStockThreshold = 1
const pageSizeOptions = CARD_PAGE_SIZE_OPTIONS
const devicePageSize = ref(8)

const searchKeyword = ref("")
const statusFilter = ref<StatusFilter>("all")
const devicePage = ref(1)
const devicePageTransitionKey = ref(0)
const detailDialogVisible = ref(false)
const upsertDialogVisible = ref(false)
const statusDialogVisible = ref(false)
const statusSubmitting = ref(false)
const deleteDialogVisible = ref(false)
const deleteTargetDevice = ref<AdminDeviceListItem | null>(null)
const deleteSubmitting = ref(false)
const selectedDeviceId = ref<number | null>(null)
const selectedDeviceSnapshot = ref<AdminDeviceListItem | null>(null)
const statusTargetDevice = ref<AdminDeviceListItem | null>(null)
const deviceCollection = ref<AdminDeviceListItem[]>([])
const pageTotal = ref(0)
const inventoryStats = ref<AdminDeviceListStats>(createEmptyDeviceStats())
const deviceForm = ref<DeviceForm>(createEmptyDeviceForm())

const pagedDevices = computed(() => deviceCollection.value)

const statsCards = computed(() => [
  { label: "设备总数", value: inventoryStats.value.totalCount, icon: Box, tone: "steel" },
  { label: "启用设备", value: inventoryStats.value.enabledCount, icon: CircleCheck, tone: "mint" },
  { label: "停用设备", value: inventoryStats.value.disabledCount, icon: SwitchButton, tone: "rose" },
  { label: "库存预警", value: inventoryStats.value.warningCount, icon: Warning, tone: "amber" }
])

const selectedDevice = computed(() => {
  if (selectedDeviceId.value === null) return null
  return deviceCollection.value.find((item) => item.id === selectedDeviceId.value) ?? selectedDeviceSnapshot.value
})

const statusNextLabel = computed(() => {
  if (!statusTargetDevice.value) return ""
  return statusTargetDevice.value.status === "ENABLED" ? "停用" : "启用"
})

let hasHydratedDevicePage = false

watch(pageTotal, (value) => {
  const maxPage = Math.max(1, Math.ceil(Math.max(value, 1) / devicePageSize.value))
  if (devicePage.value > maxPage) devicePage.value = maxPage
})

watch(deviceCollection, (list) => {
  if (list.length === 0) {
    selectedDeviceId.value = null
    selectedDeviceSnapshot.value = null
    detailDialogVisible.value = false
    statusDialogVisible.value = false
    statusTargetDevice.value = null
    return
  }

  if (!list.some(item => item.id === selectedDeviceId.value)) {
    selectedDeviceId.value = list[0].id
    selectedDeviceSnapshot.value = list[0]
  }
}, { immediate: true })

watch([searchKeyword, statusFilter], () => {
  const shouldFetchImmediately = devicePage.value === 1
  devicePage.value = 1
  if (shouldFetchImmediately) {
    void fetchDeviceCollection()
  }
})

watch([devicePage, devicePageSize], () => {
  void fetchDeviceCollection()
})

function handleDevicePageSizeChange(size: number) {
  devicePageSize.value = size
  devicePage.value = 1
}

function createEmptyDeviceForm(): DeviceForm {
  return {
    id: null,
    deviceCode: "",
    name: "",
    total: 1,
    status: "ENABLED"
  }
}

function createEmptyDeviceStats(): AdminDeviceListStats {
  return {
    totalCount: 0,
    enabledCount: 0,
    disabledCount: 0,
    warningCount: 0
  }
}

async function fetchDeviceCollection() {
  try {
    const response = await getAdminDeviceListApi({
      currentPage: devicePage.value,
      size: devicePageSize.value,
      keyword: searchKeyword.value.trim() || undefined,
      status: statusFilter.value === "all" ? undefined : statusFilter.value
    })
    deviceCollection.value = response.data.list
    pageTotal.value = response.data.total
    inventoryStats.value = response.data.stats
  } catch {
    deviceCollection.value = []
    pageTotal.value = 0
    inventoryStats.value = createEmptyDeviceStats()
    ElMessage.error("设备列表加载失败")
  }

  if (selectedDeviceId.value !== null) {
    const nextSelected = deviceCollection.value.find((item) => item.id === selectedDeviceId.value)
    if (nextSelected) {
      selectedDeviceSnapshot.value = nextSelected
    }
  }

  if (hasHydratedDevicePage) {
    devicePageTransitionKey.value += 1
  } else {
    hasHydratedDevicePage = true
  }
}

void fetchDeviceCollection()

function getStatusLabel(status: DeviceStatus) {
  return status === "ENABLED" ? "启用" : "停用"
}

function getStatusTone(status: DeviceStatus) {
  return status === "ENABLED" ? "is-enabled" : "is-disabled"
}

function getStockTone(device: AdminDeviceListItem) {
  return device.availableQuantity <= lowStockThreshold ? "is-warning" : "is-steady"
}

function openDetail(device: AdminDeviceListItem) {
  selectedDeviceId.value = device.id
  selectedDeviceSnapshot.value = device
  detailDialogVisible.value = true
}

function openCreateDialog() {
  deviceForm.value = createEmptyDeviceForm()
  upsertDialogVisible.value = true
}

function openEditDialog(device: AdminDeviceListItem) {
  selectedDeviceId.value = device.id
  selectedDeviceSnapshot.value = device
  deviceForm.value = {
    id: device.id,
    deviceCode: device.deviceCode,
    name: device.name,
    total: device.total,
    status: device.status
  }
  upsertDialogVisible.value = true
}

function openStatusDialog(device: AdminDeviceListItem) {
  statusTargetDevice.value = device
  statusDialogVisible.value = true
}

function closeStatusDialog() {
  if (statusSubmitting.value) return
  statusDialogVisible.value = false
  statusTargetDevice.value = null
}

function getDeviceSaveErrorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : ""
  const lowerMessage = message.toLowerCase()
  if (lowerMessage.includes("devicecode already exists") || lowerMessage.includes("device code already exists") || message.includes("设备编码已存在")) return "设备编码已存在，请更换编码"
  return /[\u4E00-\u9FFF]/.test(message) ? message : "设备保存失败，请稍后重试"
}

function getDeviceDeleteErrorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : ""
  const lowerMessage = message.toLowerCase()
  if (lowerMessage.includes("device is still bound to rooms") || message.includes("绑定会议室")) return "该设备仍绑定会议室，暂不允许删除"
  return /[\u4E00-\u9FFF]/.test(message) ? message : "设备删除失败，请稍后重试"
}

async function submitDeviceForm() {
  const payload: DeviceForm = {
    ...deviceForm.value,
    deviceCode: deviceForm.value.deviceCode.trim().toUpperCase(),
    name: deviceForm.value.name.trim()
  }

  if (!payload.deviceCode || !payload.name) {
    ElMessage.warning("请完整填写设备编码和设备名称")
    return
  }

  if (payload.id !== null) {
    const current = deviceCollection.value.find((item) => item.id === payload.id)
    if (current && payload.total < current.boundQuantity) {
      ElMessage.warning("库存总量不能小于已绑定数量")
      return
    }
  }

  try {
    if (payload.id === null) {
      await createAdminDeviceApi({
        deviceCode: payload.deviceCode,
        name: payload.name,
        total: payload.total,
        status: payload.status
      })
      await fetchDeviceCollection()
      upsertDialogVisible.value = false
      ElMessage.success("设备已新增")
      return
    }

    await updateAdminDeviceApi(payload.id, {
      deviceCode: payload.deviceCode,
      name: payload.name,
      total: payload.total,
      status: payload.status
    })
    await fetchDeviceCollection()
    upsertDialogVisible.value = false
    ElMessage.success("设备信息已更新")
    return
  } catch (error) {
    ElMessage.error(getDeviceSaveErrorMessage(error))
  }
}

async function toggleDeviceStatus(device: AdminDeviceListItem) {
  const nextStatus: DeviceStatus = device.status === "ENABLED" ? "DISABLED" : "ENABLED"
  statusSubmitting.value = true

  try {
    await updateAdminDeviceStatusApi(device.id, {
      status: nextStatus
    })
    await fetchDeviceCollection()
    ElMessage.success(`设备已${getStatusLabel(nextStatus)}`)
    return
  } catch {
    ElMessage.error("设备状态更新失败，请稍后重试")
  } finally {
    statusSubmitting.value = false
    statusDialogVisible.value = false
    statusTargetDevice.value = null
  }
}

function deleteDevice(device: AdminDeviceListItem) {
  if (device.boundRoomCount > 0) {
    ElMessage.warning("该设备仍绑定会议室，暂不允许删除")
    return
  }

  deleteTargetDevice.value = device
  deleteDialogVisible.value = true
}

function closeDeleteDialog() {
  if (deleteSubmitting.value) return
  deleteDialogVisible.value = false
  deleteTargetDevice.value = null
}

async function confirmDeleteDevice() {
  if (!deleteTargetDevice.value) return
  const device = deleteTargetDevice.value
  deleteSubmitting.value = true
  deleteDialogVisible.value = false
  deleteTargetDevice.value = null

  try {
    await deleteAdminDeviceApi(device.id)
    await fetchDeviceCollection()
  } catch (error) {
    ElMessage.error(getDeviceDeleteErrorMessage(error))
    return
  } finally {
    deleteSubmitting.value = false
  }

  if (selectedDeviceId.value === device.id) {
    selectedDeviceId.value = deviceCollection.value[0]?.id ?? null
    selectedDeviceSnapshot.value = deviceCollection.value[0] ?? null
    detailDialogVisible.value = false
  }
  ElMessage.success("设备已删除")
}

function getRowClassName({ row }: { row: AdminDeviceListItem }) {
  return [
    row.status === "DISABLED" ? "is-disabled-row" : "",
    row.availableQuantity <= lowStockThreshold ? "is-warning-row" : ""
  ].filter(Boolean).join(" ")
}
</script>

<template>
  <div class="admin-devices-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Inventory Console</p>
        <h1 class="page-hero-title">设备管理</h1>
      </div>
      <div class="hero-actions">
        <el-button class="hero-primary-button" @click="openCreateDialog()">
          <el-icon><Plus /></el-icon>
          <span>新增设备</span>
        </el-button>
      </div>
    </section>

    <section class="stats-section">
      <div class="section-head stats-head">
        <div>
          <p class="section-kicker">Inventory Snapshot</p>
          <h2>库存概览</h2>
        </div>
      </div>

      <div class="stats-grid">
        <article v-for="item in statsCards" :key="item.label" class="stats-card" :class="`tone-${item.tone}`">
          <div class="stats-icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="stats-label">{{ item.label }}</div>
            <strong class="stats-value">{{ item.value }}</strong>
          </div>
        </article>
      </div>
    </section>

    <section class="workspace-grid">
      <main class="main-panel">
        <section class="filter-panel">
          <el-input v-model="searchKeyword" class="search-box" placeholder="搜索设备名称或编码" clearable>
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="statusFilter" class="filter-select">
            <el-option label="全部状态" value="all" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </section>

        <section class="table-panel">
          <div class="section-head">
            <div>
              <p class="section-kicker">Device Registry</p>
              <h2>库存设备清单</h2>
            </div>
            <span class="section-meta">{{ pageTotal }} 台</span>
          </div>

          <div v-if="pageTotal > 0" class="table-body">
            <Transition name="pagination-switch" mode="out-in">
              <div :key="`device-page-${devicePageTransitionKey}`" class="table-switch-stage">
                <el-table
                  :data="pagedDevices"
                  class="device-table"
                  height="100%"
                  :row-class-name="getRowClassName"
                  @row-click="openDetail"
                >
                  <el-table-column label="设备信息" min-width="220">
                    <template #default="{ row }">
                      <div class="device-title-cell">
                        <div class="device-name">{{ row.name }}</div>
                        <div class="device-code">{{ row.deviceCode }}</div>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100" align="center">
                    <template #default="{ row }">
                      <span class="status-pill" :class="getStatusTone(row.status)">{{ getStatusLabel(row.status) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="库存总量" prop="total" width="100" align="center" />
                  <el-table-column label="已绑定" prop="boundQuantity" width="100" align="center" />
                  <el-table-column label="可用库存" width="120" align="center">
                    <template #default="{ row }">
                      <span class="stock-pill" :class="getStockTone(row)">{{ row.availableQuantity }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="绑定会议室" width="120" align="center">
                    <template #default="{ row }">
                      {{ row.boundRoomCount }}
                    </template>
                  </el-table-column>
                  <el-table-column fixed="right" label="操作" width="212" align="center">
                    <template #default="{ row }">
                      <div class="table-actions">
                        <el-button text @click.stop="openEditDialog(row)">编辑</el-button>
                        <el-button text @click.stop="openStatusDialog(row)">
                          {{ row.status === "ENABLED" ? "停用" : "启用" }}
                        </el-button>
                        <el-button text type="danger" @click.stop="deleteDevice(row)">删除</el-button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </Transition>

            <el-pagination
              class="panel-pagination"
              layout="sizes, prev, pager, next"
              :current-page="devicePage"
              :page-size="devicePageSize"
              :page-sizes="pageSizeOptions"
              :total="pageTotal"
              @current-change="devicePage = $event"
              @size-change="handleDevicePageSizeChange"
            />
          </div>
          <div v-else class="panel-empty">
            <el-empty description="当前筛选条件下暂无设备">
              <el-button type="primary" @click="openCreateDialog()">新增第一台设备</el-button>
            </el-empty>
          </div>
        </section>
      </main>
    </section>

    <el-dialog
      v-model="detailDialogVisible"
      width="min(860px, calc(100vw - 32px))"
      class="device-detail-dialog"
      :with-header="false"
      destroy-on-close
      align-center
    >
      <div v-if="selectedDevice" class="device-archive">
        <section class="archive-hero">
          <div class="archive-hero-copy">
            <p class="eyebrow">Device Archive</p>
            <h2>{{ selectedDevice.name }}</h2>
            <div class="archive-chip-row">
              <span class="archive-chip">
                <el-icon><EditPen /></el-icon>
                {{ selectedDevice.deviceCode }}
              </span>
              <span class="archive-chip">
                <el-icon><Box /></el-icon>
                总库存 {{ selectedDevice.total }}
              </span>
              <span class="archive-chip" :class="selectedDevice.availableQuantity <= lowStockThreshold ? 'is-alert' : 'is-calm'">
                <el-icon><Warning /></el-icon>
                {{ selectedDevice.availableQuantity <= lowStockThreshold ? "库存偏紧" : "库存稳定" }}
              </span>
            </div>
          </div>
          <div class="archive-hero-aside">
            <span class="status-pill archive-status-pill" :class="getStatusTone(selectedDevice.status)">
              {{ getStatusLabel(selectedDevice.status) }}
            </span>
            <div class="archive-orb">
              <el-icon><Connection /></el-icon>
            </div>
          </div>
        </section>

        <section class="archive-kpi-grid">
          <article class="archive-kpi-card">
            <span>库存总量</span>
            <strong>{{ selectedDevice.total }}</strong>
          </article>
          <article class="archive-kpi-card">
            <span>已绑定数量</span>
            <strong>{{ selectedDevice.boundQuantity }}</strong>
          </article>
          <article class="archive-kpi-card is-primary">
            <span>可用库存</span>
            <strong>{{ selectedDevice.availableQuantity }}</strong>
          </article>
          <article class="archive-kpi-card">
            <span>绑定会议室</span>
            <strong>{{ selectedDevice.boundRoomCount }}</strong>
          </article>
        </section>

        <section class="archive-section">
          <div class="archive-section-head">
            <div class="detail-title"><el-icon><OfficeBuilding /></el-icon>绑定会议室</div>
            <span class="archive-section-meta">{{ selectedDevice.boundRoomCount }} 间</span>
          </div>

          <div v-if="selectedDevice.boundRooms.length > 0" class="archive-room-grid">
            <article v-for="room in selectedDevice.boundRooms" :key="room.roomId" class="archive-room-card">
              <div class="archive-room-topline">
                <div>
                  <div class="bound-room-title">{{ room.roomName }}</div>
                  <div class="bound-room-meta">{{ room.roomCode }} · {{ room.location }}</div>
                </div>
                <span class="archive-room-badge">×{{ room.quantity }}</span>
              </div>
            </article>
          </div>
          <div v-else class="archive-empty-card">暂无绑定会议室</div>
        </section>

        <section class="archive-section archive-insight-panel">
          <div class="archive-section-head">
            <div class="detail-title"><el-icon><Connection /></el-icon>状态洞察</div>
          </div>
          <div class="archive-insight-grid">
            <article class="archive-insight-card" :class="selectedDevice.availableQuantity <= lowStockThreshold ? 'is-warning' : 'is-healthy'">
              <span>库存信号</span>
              <strong>{{ selectedDevice.availableQuantity <= lowStockThreshold ? "建议补货" : "供应稳定" }}</strong>
            </article>
            <article class="archive-insight-card" :class="selectedDevice.status === 'DISABLED' ? 'is-warning' : 'is-healthy'">
              <span>使用状态</span>
              <strong>{{ selectedDevice.status === "DISABLED" ? "已停用" : "正常启用" }}</strong>
            </article>
          </div>
        </section>
      </div>
    </el-dialog>

    <el-dialog
      v-model="upsertDialogVisible"
      width="min(560px, calc(100vw - 32px))"
      class="device-upsert-dialog"
      destroy-on-close
      align-center
    >
      <template #header>
        <div class="dialog-header">
          <div>
            <p class="eyebrow">Device Editor</p>
            <h3>{{ deviceForm.id === null ? "新增设备" : "编辑设备" }}</h3>
          </div>
        </div>
      </template>

      <div class="form-grid">
        <div class="form-field">
          <label>设备编码</label>
          <el-input v-model="deviceForm.deviceCode" placeholder="例如 PROJ-08" />
        </div>
        <div class="form-field">
          <label>设备名称</label>
          <el-input v-model="deviceForm.name" placeholder="请输入设备名称" />
        </div>
        <div class="form-field">
          <label>库存总量</label>
          <el-input-number v-model="deviceForm.total" :min="1" :max="999" controls-position="right" />
        </div>
        <div class="form-field">
          <label>使用状态</label>
          <el-select v-model="deviceForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button class="ghost-button" @click="upsertDialogVisible = false">暂不处理</el-button>
          <el-button class="primary-button" type="primary" @click="submitDeviceForm()">保存设备</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="statusDialogVisible"
      width="min(360px, calc(100vw - 32px))"
      class="device-status-dialog"
      destroy-on-close
      align-center
      :close-on-click-modal="!statusSubmitting"
      :close-on-press-escape="!statusSubmitting"
      @closed="statusTargetDevice = null"
    >
      <div v-if="statusTargetDevice" class="status-dialog-shell">
        <div class="status-dialog-panel">
          <div class="status-dialog-device">{{ statusTargetDevice.name }}</div>
          <div class="status-dialog-code">{{ statusTargetDevice.deviceCode }}</div>
          <div class="status-dialog-flow">
            <span class="status-pill" :class="getStatusTone(statusTargetDevice.status)">{{ getStatusLabel(statusTargetDevice.status) }}</span>
            <span class="status-dialog-arrow">→</span>
            <span class="status-pill" :class="statusNextLabel === '启用' ? 'is-enabled' : 'is-disabled'">{{ statusNextLabel }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="status-dialog-footer">
          <el-button class="ghost-button" :disabled="statusSubmitting" @click="closeStatusDialog()">取消</el-button>
          <el-button
            class="primary-button"
            :type="statusNextLabel === '停用' ? 'danger' : 'primary'"
            :loading="statusSubmitting"
            @click="statusTargetDevice && toggleDeviceStatus(statusTargetDevice)"
          >
            确认{{ statusNextLabel }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      width="min(460px, calc(100vw - 32px))"
      class="device-delete-dialog"
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
            <p class="eyebrow">Delete Device</p>
            <h3>删除设备</h3>
          </div>
        </div>
      </template>
      <div class="delete-dialog-panel">
        <div v-if="deleteTargetDevice" class="dialog-device-title">{{ deleteTargetDevice.name }}</div>
        <p class="dialog-copy">删除后该设备将从库存清单移除。若设备仍绑定会议室，系统会阻止删除。</p>
      </div>
      <template #footer>
        <div class="dialog-footer delete-dialog-footer">
          <el-button class="ghost-button" :disabled="deleteSubmitting" @click="closeDeleteDialog">取消</el-button>
          <el-button class="ghost-button is-danger" :loading="deleteSubmitting" @click="confirmDeleteDevice">确认删除</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
.admin-devices-page {
  --panel-bg: rgba(255, 255, 255, 0.82);
  --panel-border: rgba(255, 255, 255, 0.92);
  --text-main: #193047;
  --text-sub: #6f8398;
  --steel: #6787df;
  --mint: #4fbea9;
  --rose: #e37f9a;
  --amber: #d7a34a;
  --line: rgba(145, 164, 190, 0.18);
  min-height: 100%;
  padding: 24px;
  color: var(--text-main);
  background:
    radial-gradient(circle at 0% 0%, rgba(107, 144, 216, 0.2), transparent 26%),
    radial-gradient(circle at 100% 10%, rgba(80, 193, 170, 0.16), transparent 32%),
    linear-gradient(180deg, #f6f7fb 0%, #edf1f7 100%);
}

.stats-card,
.filter-panel,
.table-panel,
.archive-hero,
.archive-section,
.archive-kpi-card,
.archive-room-card,
.archive-insight-card,
.archive-empty-card {
  border: 1px solid var(--panel-border);
  background: var(--panel-bg);
  box-shadow: 0 22px 58px rgba(61, 83, 109, 0.1);
  backdrop-filter: blur(18px);
}

.filter-panel,
.table-panel,
.archive-hero,
.archive-section {
  border-radius: 28px;
}

.stats-card,
.filter-panel,
.table-panel {
  animation: float-in 0.45s ease both;
}

.hero-primary-button {
  min-height: 42px;
  padding-inline: 18px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #6d8de8, #57c8b3);
  color: #fff;
  font-weight: 700;
  letter-spacing: 0.01em;
  box-shadow: 0 18px 38px rgba(91, 126, 205, 0.24);
  transition: transform 0.22s ease, box-shadow 0.22s ease, filter 0.22s ease;
}

.hero-primary-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 22px 42px rgba(91, 126, 205, 0.3);
  filter: saturate(1.03);
}

.hero-primary-button:active {
  transform: translateY(0);
  box-shadow: 0 12px 24px rgba(91, 126, 205, 0.22);
}

.hero-primary-button :deep(.el-icon) {
  margin-right: 6px;
  font-size: 15px;
}

.eyebrow,
.section-kicker,
.stats-label,
.device-code,
.bound-room-meta {
  color: var(--text-sub);
}

.section-kicker {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.stats-section {
  margin-top: 16px;
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

.tone-steel .stats-icon { background: rgba(103, 135, 223, 0.14); color: var(--steel); }
.tone-mint .stats-icon { background: rgba(79, 190, 169, 0.16); color: var(--mint); }
.tone-rose .stats-icon { background: rgba(227, 127, 154, 0.16); color: var(--rose); }
.tone-amber .stats-icon { background: rgba(215, 163, 74, 0.18); color: var(--amber); }

.stats-value {
  display: block;
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.main-panel {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.filter-panel,
.table-panel {
  padding: 18px;
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(180px, 0.42fr);
  gap: 12px;
}

.section-head,
.dialog-header,
.dialog-footer,
.table-actions,
.archive-section-head,
.archive-room-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-head {
  margin-bottom: 16px;
  align-items: flex-start;
}

.section-head h2 {
  margin: 0;
  font-size: 23px;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.section-meta,
.status-pill,
.stock-pill,
.archive-section-meta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.section-meta,
.archive-section-meta {
  background: rgba(103, 135, 223, 0.12);
  color: #5370b2;
}

.status-pill.is-enabled {
  color: #1d7f6b;
  background: rgba(79, 190, 169, 0.16);
}

.status-pill.is-disabled {
  color: #a34860;
  background: rgba(227, 127, 154, 0.16);
}

.stock-pill.is-steady {
  color: #4c678a;
  background: rgba(103, 135, 223, 0.12);
}

.stock-pill.is-warning {
  color: #9e6b18;
  background: rgba(215, 163, 74, 0.18);
}

.table-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 680px;
}

.table-body {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 12px;
  min-height: 0;
}

.table-switch-stage {
  min-height: 0;
  height: 100%;
}

.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
}

.device-title-cell {
  display: grid;
  gap: 4px;
}

.device-name,
.bound-room-title {
  font-weight: 700;
}

.table-actions {
  justify-content: center;
  gap: 10px;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.device-archive,
.archive-chip-row,
.archive-kpi-grid,
.archive-room-grid,
.archive-insight-grid {
  display: grid;
}

.device-archive {
  gap: 18px;
}

.archive-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  padding: 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(103, 135, 223, 0.22), transparent 34%),
    radial-gradient(circle at bottom left, rgba(79, 190, 169, 0.18), transparent 30%),
    linear-gradient(145deg, rgba(244, 248, 255, 0.96), rgba(255, 255, 255, 0.82));
}

.archive-hero::after {
  content: "";
  position: absolute;
  inset: auto -32px -48px auto;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(103, 135, 223, 0.18), transparent 68%);
}

.archive-hero-copy,
.archive-hero-aside {
  position: relative;
  z-index: 1;
}

.archive-hero-copy h2 {
  margin: 10px 0 0;
  font-size: 34px;
  line-height: 1;
  letter-spacing: -0.04em;
}

.archive-chip-row {
  grid-template-columns: repeat(3, max-content);
  gap: 10px;
  margin-top: 18px;
}

.archive-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(126, 148, 177, 0.18);
  background: rgba(255, 255, 255, 0.72);
  color: #30475f;
  font-size: 12px;
  font-weight: 700;
}

.archive-chip.is-alert {
  color: #9f6713;
  background: rgba(245, 232, 204, 0.92);
}

.archive-chip.is-calm {
  color: #2c7b68;
  background: rgba(225, 244, 239, 0.92);
}

.archive-hero-aside {
  display: grid;
  justify-items: end;
  align-content: space-between;
  gap: 16px;
}

.archive-status-pill {
  min-height: 34px;
  padding-inline: 16px;
  font-size: 13px;
}

.archive-orb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(24, 49, 74, 0.96), rgba(52, 83, 116, 0.82));
  color: #f5fbff;
  font-size: 36px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.14), 0 16px 36px rgba(25, 48, 71, 0.22);
}
.archive-kpi-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.archive-kpi-card,
.archive-room-card,
.archive-insight-card,
.archive-empty-card {
  border-radius: 24px;
  padding: 18px;
  overflow: hidden;
}

.archive-kpi-card span,
.archive-insight-card span {
  color: #7387a0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.archive-kpi-card strong {
  display: block;
  margin-top: 10px;
  font-size: 34px;
  line-height: 1;
  letter-spacing: -0.05em;
}

.archive-kpi-card.is-primary {
  background: linear-gradient(160deg, rgba(28, 51, 76, 0.98), rgba(78, 103, 142, 0.92));
  color: #f7fbff;
}

.archive-kpi-card.is-primary span {
  color: rgba(239, 246, 255, 0.78);
}

.archive-section {
  padding: 18px;
}

.archive-room-grid,
.archive-insight-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.archive-room-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(24, 49, 74, 0.1);
  color: #193047;
  font-size: 13px;
  font-weight: 700;
}

.archive-insight-panel {
  background: linear-gradient(180deg, rgba(249, 251, 255, 0.92), rgba(245, 248, 252, 0.88));
}

.archive-insight-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.1;
}

.archive-insight-card.is-warning strong {
  color: #af6d0a;
}

.archive-insight-card.is-healthy strong {
  color: #2c7b68;
}

.archive-empty-card {
  color: #62778f;
  line-height: 1.7;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-field {
  display: grid;
  gap: 8px;
}

.form-field label {
  font-size: 13px;
  font-weight: 700;
}

.status-dialog-shell {
  display: grid;
  place-items: center;
  min-height: 124px;
}

.status-dialog-panel {
  display: grid;
  gap: 8px;
  justify-items: center;
  text-align: center;
  width: 100%;
  padding: 2px 0;
}

.status-dialog-device {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--text-main);
}

.status-dialog-code {
  color: var(--text-sub);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.status-dialog-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 2px;
}

.status-dialog-arrow {
  color: #7c8fa6;
  font-size: 18px;
  font-weight: 700;
}

.status-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.delete-dialog-panel {
  display: grid;
  gap: 12px;
}

.dialog-device-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.18;
}

.dialog-copy {
  margin: 0;
  color: var(--text-sub);
  line-height: 1.7;
}

.delete-dialog-footer {
  justify-content: flex-end;
}

.delete-dialog-footer .ghost-button,
.delete-dialog-footer .primary-button {
  width: 132px;
  min-width: 132px;
  min-height: 40px;
  padding: 0 18px;
}

.delete-dialog-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

.panel-pagination {
  justify-self: end;
}

:deep(.device-table) {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: rgba(244, 247, 252, 0.9);
  --el-table-row-hover-bg-color: rgba(103, 135, 223, 0.06);
  border-radius: 22px;
  overflow: hidden;
}

:deep(.device-table .el-table__header-wrapper th.el-table__cell) {
  color: var(--text-sub);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

:deep(.device-table .el-table__body-wrapper tr) {
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

:deep(.device-table .el-table__body-wrapper tr:hover) {
  transform: translateX(3px);
}

:deep(.device-table .el-table__body-wrapper td.el-table__cell) {
  border-bottom: 1px solid var(--line);
}

:deep(.device-table .el-table__body-wrapper tr.is-warning-row td.el-table__cell) {
  background: rgba(215, 163, 74, 0.06);
}

:deep(.device-table .el-table__body-wrapper tr.is-disabled-row td.el-table__cell) {
  color: #7f8ca0;
}

:deep(.device-detail-dialog .el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  background: rgba(249, 250, 253, 0.96);
  box-shadow: 0 24px 72px rgba(61, 83, 109, 0.18);
}

:deep(.device-detail-dialog .el-dialog__body) {
  padding: 22px 24px 24px;
  max-height: min(78vh, 820px);
  overflow: auto;
}

:deep(.device-detail-dialog .el-overlay) {
  backdrop-filter: blur(10px);
}

:deep(.device-upsert-dialog .el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  background: rgba(249, 250, 253, 0.95);
  box-shadow: 0 24px 72px rgba(61, 83, 109, 0.16);
}

:deep(.device-delete-dialog.el-dialog),
:deep(.device-delete-dialog .el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  background: rgba(249, 250, 253, 0.98);
  box-shadow: 0 24px 72px rgba(24, 49, 74, 0.22);
}

:deep(.device-upsert-dialog .el-dialog__header) {
  padding: 22px 24px 0;
}

:deep(.device-delete-dialog.el-dialog .el-dialog__header),
:deep(.device-delete-dialog .el-dialog__header) {
  padding: 22px 24px 0;
}

:deep(.device-upsert-dialog .el-dialog__body) {
  padding: 18px 24px 8px;
}

:deep(.device-delete-dialog.el-dialog .el-dialog__body),
:deep(.device-delete-dialog .el-dialog__body) {
  padding: 18px 24px 8px;
}

:deep(.device-upsert-dialog .el-dialog__footer) {
  padding: 8px 24px 22px;
}

:deep(.device-delete-dialog.el-dialog .el-dialog__footer),
:deep(.device-delete-dialog .el-dialog__footer) {
  padding: 10px 24px 22px;
}

:deep(.device-status-dialog .el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  background: rgba(249, 250, 253, 0.98);
  box-shadow: 0 24px 72px rgba(24, 49, 74, 0.22);
}

:deep(.device-status-dialog .el-dialog__header) {
  display: none;
}

:deep(.device-status-dialog .el-dialog__body) {
  padding: 18px 20px 6px;
}

:deep(.device-status-dialog .el-dialog__footer) {
  padding: 8px 20px 16px;
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

@media (max-width: 900px) {
  .admin-devices-page {
    --page-topbar-columns: 1fr;
    --page-topbar-actions-justify: flex-start;
  }

  .stats-grid,
  .form-grid,
  .archive-kpi-grid,
  .archive-room-grid,
  .archive-insight-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .archive-hero,
  .filter-panel {
    grid-template-columns: 1fr;
  }

  .archive-hero-aside {
    justify-items: start;
  }
}

@media (max-width: 640px) {
  .admin-devices-page {
    padding: 16px;
  }

  .stats-grid,
  .form-grid,
  .archive-kpi-grid,
  .archive-room-grid,
  .archive-insight-grid {
    grid-template-columns: 1fr;
  }

  .table-panel {
    min-height: 580px;
  }

  .archive-chip-row {
    grid-template-columns: 1fr;
  }

  .archive-hero-copy h2 {
    font-size: 28px;
  }

  .archive-orb {
    width: 76px;
    height: 76px;
    border-radius: 22px;
    font-size: 28px;
  }

  .delete-dialog-footer .ghost-button,
  .delete-dialog-footer .primary-button {
    width: 100%;
    min-width: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-panel,
  .stats-card,
  .filter-panel,
  .table-panel,
  :deep(.device-table .el-table__body-wrapper tr) {
    animation: none;
    transition: none;
  }
}
</style>
