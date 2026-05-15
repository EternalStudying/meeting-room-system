<script lang="ts" setup>
import { computed, ref, watch } from "vue"
import dayjs from "dayjs"
import { ElMessage } from "element-plus"
import { Calendar, CircleCheck, Clock, CloseBold, DocumentChecked, Search, Tickets, Warning } from "@element-plus/icons-vue"
import {
  approveAdminReservationApi,
  getAdminReservationListApi,
  markAdminReservationExceptionApi,
  rejectAdminReservationApi
} from "@/common/apis/reservations"
import type { AdminReservationItem, AdminReservationStats, ReservationStatus } from "@/common/apis/reservations/type"
import { getRoomListApi } from "@/common/apis/rooms"
import type { RoomData } from "@/common/apis/rooms/type"
import ReservationCreateDialog from "@/components/ReservationCreateDialog.vue"
import { CARD_PAGE_SIZE_OPTIONS } from "@/common/constants/pagination"

type StatusFilter = ReservationStatus | "all"
type ActionMode = "approve" | "reject" | "exception"

const pageSizeOptions = CARD_PAGE_SIZE_OPTIONS

const searchKeyword = ref("")
const statusFilter = ref<StatusFilter>("PENDING")
const reservationPage = ref(1)
const reservationPageSize = ref(8)
const reservationPageTransitionKey = ref(0)
const reservationCollection = ref<AdminReservationItem[]>([])
const reservationTotal = ref(0)
const reservationStats = ref<AdminReservationStats>(createEmptyReservationStats())
const dataLoading = ref(false)
const actionDialogVisible = ref(false)
const actionSubmitting = ref(false)
const actionMode = ref<ActionMode>("approve")
const actionTargetReservation = ref<AdminReservationItem | null>(null)
const actionRemark = ref("")
const selectedReservationId = ref<number | null>(null)
const emergencyDialogVisible = ref(false)
const emergencyRooms = ref<RoomData[]>([])
let hasHydratedReservationPage = false

const statusOptions: Array<{ label: string, value: StatusFilter }> = [
  { label: "全部状态", value: "all" },
  { label: "待审核", value: "PENDING" },
  { label: "已通过", value: "ACTIVE" },
  { label: "已驳回", value: "REJECTED" },
  { label: "异常", value: "EXCEPTION" },
  { label: "已取消", value: "CANCELLED" },
  { label: "已结束", value: "ENDED" }
]

const statsCards = computed(() => [
  { label: "全部预约", value: padNumber(reservationStats.value.totalCount), icon: Tickets, tone: "steel" },
  { label: "待审核", value: padNumber(reservationStats.value.pendingCount), icon: Clock, tone: "amber" },
  { label: "已通过", value: padNumber(reservationStats.value.activeCount), icon: CircleCheck, tone: "mint" },
  { label: "已驳回", value: padNumber(reservationStats.value.rejectedCount), icon: CloseBold, tone: "rose" },
  { label: "异常处理", value: padNumber(reservationStats.value.exceptionCount), icon: Warning, tone: "orange" }
])

const actionTitle = computed(() => {
  const map: Record<ActionMode, string> = {
    approve: "通过预约",
    reject: "驳回预约",
    exception: "标记异常"
  }
  return map[actionMode.value]
})

const actionPlaceholder = computed(() => {
  if (actionMode.value === "approve") return "可填写审核备注，例如：安排合理"
  if (actionMode.value === "reject") return "请填写驳回原因，例如：时间冲突或资源不足"
  return "请填写异常说明，例如：参会人未到或会议室临时不可用"
})

const selectedReservation = computed(() => {
  if (selectedReservationId.value === null) return reservationCollection.value[0] ?? null
  return reservationCollection.value.find(item => item.id === selectedReservationId.value) ?? reservationCollection.value[0] ?? null
})

watch([searchKeyword, statusFilter], () => {
  const shouldFetchImmediately = reservationPage.value === 1
  reservationPage.value = 1
  if (shouldFetchImmediately) {
    void fetchReservationCollection()
  }
})

watch([reservationPage, reservationPageSize], () => {
  void fetchReservationCollection()
})

watch(reservationCollection, (list) => {
  if (list.length === 0) {
    selectedReservationId.value = null
    return
  }

  if (!list.some(item => item.id === selectedReservationId.value)) {
    selectedReservationId.value = list[0].id
  }
})

function handleReservationPageSizeChange(size: number) {
  reservationPageSize.value = size
  reservationPage.value = 1
}

function padNumber(value: number) {
  return String(value).padStart(2, "0")
}

function createEmptyReservationStats(): AdminReservationStats {
  return {
    totalCount: 0,
    pendingCount: 0,
    activeCount: 0,
    rejectedCount: 0,
    exceptionCount: 0
  }
}

async function fetchReservationCollection() {
  dataLoading.value = true
  try {
    const response = await getAdminReservationListApi({
      currentPage: reservationPage.value,
      size: reservationPageSize.value,
      keyword: searchKeyword.value.trim() || undefined,
      status: statusFilter.value === "all" ? undefined : statusFilter.value
    })
    reservationCollection.value = response.data.list
    reservationTotal.value = response.data.total
    reservationStats.value = response.data.stats
  } catch {
    reservationCollection.value = []
    reservationTotal.value = 0
    reservationStats.value = createEmptyReservationStats()
    ElMessage.error("预约审核列表加载失败")
  } finally {
    dataLoading.value = false
  }

  if (hasHydratedReservationPage) {
    reservationPageTransitionKey.value += 1
  } else {
    hasHydratedReservationPage = true
  }
}

void fetchReservationCollection()

function getStatusLabel(status: ReservationStatus) {
  const map: Record<ReservationStatus, string> = {
    PENDING: "待审核",
    ACTIVE: "已通过",
    ENDED: "已结束",
    CANCELLED: "已取消",
    REJECTED: "已驳回",
    EXCEPTION: "异常"
  }
  return map[status]
}

function getStatusTone(status: ReservationStatus) {
  return `is-${status.toLowerCase()}`
}

function selectReservation(reservation: AdminReservationItem) {
  selectedReservationId.value = reservation.id
}

function isSelectedReservation(reservation: AdminReservationItem) {
  return selectedReservation.value?.id === reservation.id
}

function canApprove(reservation: AdminReservationItem) {
  return reservation.status === "PENDING"
}

function canReject(reservation: AdminReservationItem) {
  return reservation.status === "PENDING"
}

function canMarkException(reservation: AdminReservationItem) {
  return reservation.status === "ACTIVE"
}

function formatDateTime(value: string) {
  return dayjs(value).format("M月D日 HH:mm")
}

function formatTimeRange(reservation: AdminReservationItem) {
  return `${formatDateTime(reservation.startTime)} - ${dayjs(reservation.endTime).format("HH:mm")}`
}

function formatParticipantNames(reservation: AdminReservationItem) {
  const names = reservation.participants?.map(item => item.displayName || item.username).filter(Boolean) ?? []
  return names.length > 0 ? names.join("、") : "仅组织者"
}

function formatDeviceSummary(reservation: AdminReservationItem) {
  if (!reservation.devices.length) return "无设备需求"
  return reservation.devices.map(item => `${item.name} x${item.quantity}`).join("、")
}

function getProcessNote(reservation: AdminReservationItem) {
  return reservation.rejectReason || reservation.exceptionReason || reservation.approvalRemark || reservation.cancelReason || reservation.remark || "暂无备注"
}

function formatCreatedTime(reservation: AdminReservationItem) {
  return reservation.createdAt ? dayjs(reservation.createdAt).format("M月D日 HH:mm") : "未记录"
}

function formatProcessedInfo(reservation: AdminReservationItem) {
  if (!reservation.processedAt && !reservation.processedByName) return "未处理"
  const operator = reservation.processedByName || "管理员"
  const time = reservation.processedAt ? dayjs(reservation.processedAt).format("M月D日 HH:mm") : "时间未记录"
  return `${operator} · ${time}`
}

function openActionDialog(mode: ActionMode, reservation: AdminReservationItem) {
  actionMode.value = mode
  actionTargetReservation.value = reservation
  actionRemark.value = ""
  actionDialogVisible.value = true
}

async function openEmergencyReservationDialog() {
  emergencyDialogVisible.value = true
  if (emergencyRooms.value.length > 0) return
  try {
    const response = await getRoomListApi({ currentPage: 1, size: 100 })
    emergencyRooms.value = response.data.list
  } catch {
    emergencyRooms.value = []
  }
}

function handleEmergencyReservationSubmitted() {
  emergencyDialogVisible.value = false
  void fetchReservationCollection()
}

function closeActionDialog() {
  if (actionSubmitting.value) return
  actionDialogVisible.value = false
  actionTargetReservation.value = null
  actionRemark.value = ""
}

async function submitAction() {
  const target = actionTargetReservation.value
  if (!target) return

  const note = actionRemark.value.trim()
  if ((actionMode.value === "reject" || actionMode.value === "exception") && !note) {
    ElMessage.warning(actionMode.value === "reject" ? "请填写驳回原因" : "请填写异常说明")
    return
  }

  actionSubmitting.value = true
  try {
    if (actionMode.value === "approve") {
      await approveAdminReservationApi(target.id, { remark: note || undefined })
      ElMessage.success("预约已通过")
    } else if (actionMode.value === "reject") {
      await rejectAdminReservationApi(target.id, { reason: note })
      ElMessage.success("预约已驳回")
    } else {
      await markAdminReservationExceptionApi(target.id, { reason: note })
      ElMessage.success("预约已标记异常")
    }
    actionDialogVisible.value = false
    actionTargetReservation.value = null
    actionRemark.value = ""
    await fetchReservationCollection()
  } catch {
    ElMessage.error("操作失败，请稍后重试")
  } finally {
    actionSubmitting.value = false
  }
}

defineExpose({
  reservationCollection,
  actionDialogVisible,
  actionMode,
  actionTargetReservation,
  openActionDialog,
  emergencyDialogVisible,
  openEmergencyReservationDialog
})
</script>

<template>
  <div class="admin-reservations-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Reservation Review</p>
        <h1 class="page-hero-title">预约审核</h1>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="item in statsCards" :key="item.label" class="stat-card" :class="`is-${item.tone}`">
        <div class="stat-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
        </div>
      </article>
    </section>

    <section class="review-panel">
      <div class="panel-heading">
        <div>
          <p class="panel-kicker">Review Queue</p>
          <h2>预约申请队列</h2>
        </div>
        <div class="panel-filters">
          <el-button class="emergency-admin-button" type="primary" @click="openEmergencyReservationDialog">创建紧急会议</el-button>
          <el-input v-model="searchKeyword" class="filter-field" clearable placeholder="搜索编号、主题、会议室或发起人">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="statusFilter" class="filter-field">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>

      <transition name="pagination-switch" mode="out-in">
        <div :key="reservationPageTransitionKey" v-loading="dataLoading" class="review-workbench">
          <div class="reservation-list">
            <article
              v-for="item in reservationCollection"
              :key="item.id"
              class="reservation-card"
              :class="{ 'is-selected': isSelectedReservation(item) }"
              @click="selectReservation(item)"
            >
              <div class="reservation-card__title-row">
                <div>
                  <p class="reservation-no">{{ item.reservationNo }}</p>
                  <h3>{{ item.title }}</h3>
                </div>
                <span class="status-pill" :class="getStatusTone(item.status)">{{ getStatusLabel(item.status) }}</span>
              </div>

              <div class="reservation-card__meta">
                <span><el-icon><Calendar /></el-icon>{{ formatTimeRange(item) }}</span>
                <span><el-icon><Tickets /></el-icon>{{ item.roomName }}</span>
                <span><el-icon><DocumentChecked /></el-icon>{{ item.organizerName }} · {{ item.attendees }} 人</span>
              </div>

              <div class="reservation-card__note">
                <span>{{ item.roomLocation }}</span>
                <span>{{ formatDeviceSummary(item) }}</span>
              </div>
            </article>

            <el-empty v-if="reservationCollection.length === 0" description="暂无预约申请" />
          </div>

          <aside class="review-inspector">
            <template v-if="selectedReservation">
              <div class="inspector-head">
                <div>
                  <p class="panel-kicker">Selected Request</p>
                  <h3>{{ selectedReservation.title }}</h3>
                  <span>{{ selectedReservation.reservationNo }}</span>
                </div>
                <span class="status-pill" :class="getStatusTone(selectedReservation.status)">
                  {{ getStatusLabel(selectedReservation.status) }}
                </span>
              </div>

              <div class="inspector-actions">
                <el-button type="primary" :disabled="!canApprove(selectedReservation)" @click="openActionDialog('approve', selectedReservation)">
                  通过预约
                </el-button>
                <el-button type="danger" plain :disabled="!canReject(selectedReservation)" @click="openActionDialog('reject', selectedReservation)">
                  驳回
                </el-button>
                <el-button plain :disabled="!canMarkException(selectedReservation)" @click="openActionDialog('exception', selectedReservation)">
                  标记异常
                </el-button>
              </div>

              <div class="inspector-grid">
                <div>
                  <span>会议时间</span>
                  <strong>{{ formatTimeRange(selectedReservation) }}</strong>
                </div>
                <div>
                  <span>会议空间</span>
                  <strong>{{ selectedReservation.roomName }}</strong>
                </div>
                <div>
                  <span>组织者</span>
                  <strong>{{ selectedReservation.organizerName }}</strong>
                </div>
                <div>
                  <span>参会规模</span>
                  <strong>{{ selectedReservation.attendees }} 人</strong>
                </div>
              </div>

              <section class="inspector-section">
                <p>参会人</p>
                <div class="chip-line">{{ formatParticipantNames(selectedReservation) }}</div>
              </section>

              <section class="inspector-section">
                <p>设备需求</p>
                <div class="chip-line">{{ formatDeviceSummary(selectedReservation) }}</div>
              </section>

              <section class="inspector-section">
                <p>处理记录</p>
                <div class="process-card">
                  <span>{{ formatProcessedInfo(selectedReservation) }}</span>
                  <strong>{{ getProcessNote(selectedReservation) }}</strong>
                </div>
              </section>

              <div class="inspector-foot">
                提交于 {{ formatCreatedTime(selectedReservation) }}
              </div>
            </template>

            <el-empty v-else description="请选择预约申请" />
          </aside>
        </div>
      </transition>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="reservationPage"
          v-model:page-size="reservationPageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="pageSizeOptions"
          :total="reservationTotal"
          @size-change="handleReservationPageSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="actionDialogVisible"
      width="520px"
      class="review-action-dialog"
      :title="actionTitle"
      destroy-on-close
      @close="closeActionDialog"
    >
      <div v-if="actionTargetReservation" class="action-summary">
        <p>{{ actionTargetReservation.title }}</p>
        <span>{{ actionTargetReservation.roomName }} · {{ formatTimeRange(actionTargetReservation) }}</span>
      </div>
      <el-input
        v-model="actionRemark"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        :placeholder="actionPlaceholder"
      />
      <template #footer>
        <el-button @click="closeActionDialog">取消</el-button>
        <el-button type="primary" :loading="actionSubmitting" @click="submitAction">确认{{ actionTitle }}</el-button>
      </template>
    </el-dialog>

    <ReservationCreateDialog
      v-model="emergencyDialogVisible"
      :rooms="emergencyRooms"
      emergency
      @submitted="handleEmergencyReservationSubmitted"
    />
  </div>
</template>

<style lang="scss" scoped>
.admin-reservations-page {
  min-height: 100%;
  padding: 24px;
  color: #1b3149;
  background:
    radial-gradient(circle at 0% 0%, rgba(107, 144, 216, 0.18), transparent 26%),
    radial-gradient(circle at 100% 10%, rgba(80, 193, 170, 0.14), transparent 32%),
    linear-gradient(180deg, #f6f7fb 0%, #edf1f7 100%);
}

.panel-kicker {
  margin: 0;
  color: #6f8299;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 104px;
  padding: 18px;
  border: 1px solid rgba(207, 220, 238, 0.88);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 16px 34px rgba(91, 119, 153, 0.1);
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 16px;
  color: #fff;
}

.stat-card.is-steel .stat-icon {
  background: linear-gradient(135deg, #6d8db3, #99b7d7);
}

.stat-card.is-amber .stat-icon {
  background: linear-gradient(135deg, #df9f43, #f0c16d);
}

.stat-card.is-mint .stat-icon {
  background: linear-gradient(135deg, #39b894, #77d9bd);
}

.stat-card.is-rose .stat-icon {
  background: linear-gradient(135deg, #e16d86, #f2a0b0);
}

.stat-card.is-orange .stat-icon {
  background: linear-gradient(135deg, #d6763a, #f0ad70);
}

.stat-card p {
  margin: 0 0 6px;
  color: #6f8196;
  font-size: 13px;
}

.stat-card strong {
  color: #1b334f;
  font-size: 26px;
}

.review-panel {
  margin-top: 16px;
  padding: 22px;
  border: 1px solid rgba(205, 219, 237, 0.9);
  border-radius: 28px;
  background:
    radial-gradient(circle at left top, rgba(116, 169, 255, 0.1), transparent 30%),
    radial-gradient(circle at right top, rgba(82, 192, 180, 0.1), transparent 28%),
    rgba(255, 255, 255, 0.94);
  box-shadow: 0 22px 54px rgba(83, 111, 144, 0.12);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.panel-heading h2 {
  margin: 6px 0 0;
  color: #1a334f;
  font-size: 22px;
}

.panel-filters {
  display: flex;
  gap: 12px;
}

.filter-field {
  width: 220px;
}

.filter-field:first-child {
  width: 320px;
}

.review-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(360px, 0.72fr);
  gap: 18px;
  align-items: start;
  min-height: 410px;
}

.reservation-list {
  display: grid;
  gap: 12px;
}

.reservation-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 18px 18px 16px 20px;
  border: 1px solid rgba(211, 224, 240, 0.9);
  border-radius: 22px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.98), rgba(248, 252, 255, 0.95));
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.reservation-card::before {
  position: absolute;
  top: 18px;
  bottom: 18px;
  left: 0;
  width: 4px;
  border-radius: 0 999px 999px 0;
  background: transparent;
  content: "";
  transition: background 0.2s ease;
}

.reservation-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(86, 119, 160, 0.12);
}

.reservation-card.is-selected {
  border-color: rgba(91, 151, 235, 0.72);
  box-shadow: 0 18px 38px rgba(67, 117, 181, 0.14);
}

.reservation-card.is-selected::before {
  background: linear-gradient(180deg, #72a7ff, #62d0bd);
}

.reservation-card__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.reservation-no {
  margin: 0 0 6px;
  color: #8091a5;
  font-size: 12px;
}

.reservation-card h3 {
  margin: 0;
  color: #16324e;
  font-size: 20px;
}

.reservation-card__meta,
.reservation-card__note {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  color: #607389;
  font-size: 13px;
}

.reservation-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.reservation-card__note span {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(238, 245, 255, 0.92);
}

.review-inspector {
  position: sticky;
  top: 72px;
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid rgba(205, 219, 237, 0.9);
  border-radius: 26px;
  background:
    radial-gradient(circle at right top, rgba(118, 178, 255, 0.16), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.94));
  box-shadow: 0 18px 38px rgba(78, 111, 150, 0.12);
}

.inspector-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.inspector-head h3 {
  margin: 6px 0 6px;
  color: #17324e;
  font-size: 24px;
  line-height: 1.16;
}

.inspector-head span:not(.status-pill) {
  color: #75879c;
  font-size: 12px;
}

.inspector-actions {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: 10px;
}

.inspector-actions .el-button {
  width: 100%;
  margin-left: 0;
}

.inspector-actions .el-button:first-child {
  grid-column: 1 / -1;
}

.inspector-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.inspector-grid div,
.process-card,
.chip-line {
  padding: 12px;
  border: 1px solid rgba(216, 226, 240, 0.78);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.inspector-grid span,
.process-card span,
.inspector-section p,
.inspector-foot {
  color: #7a8da3;
  font-size: 12px;
}

.inspector-grid strong,
.process-card strong {
  display: block;
  margin-top: 7px;
  color: #203a56;
  font-size: 14px;
  line-height: 1.5;
}

.inspector-section {
  display: grid;
  gap: 8px;
}

.inspector-section p {
  margin: 0;
}

.chip-line {
  color: #50667f;
  font-size: 13px;
  line-height: 1.7;
}

.process-card strong {
  font-weight: 600;
}

.inspector-foot {
  padding-top: 2px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 7px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-pill.is-pending {
  background: rgba(255, 241, 211, 0.95);
  color: #ac6a11;
}

.status-pill.is-active {
  background: rgba(224, 247, 239, 0.95);
  color: #168064;
}

.status-pill.is-rejected,
.status-pill.is-cancelled {
  background: rgba(255, 231, 237, 0.95);
  color: #c94d6f;
}

.status-pill.is-exception {
  background: rgba(255, 236, 218, 0.95);
  color: #ba5c16;
}

.status-pill.is-ended {
  background: rgba(231, 237, 255, 0.95);
  color: #526dd8;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.action-summary {
  display: grid;
  gap: 6px;
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(244, 248, 253, 0.94);
}

.action-summary p {
  margin: 0;
  color: #19334f;
  font-weight: 700;
}

.action-summary span {
  color: #697d94;
  font-size: 13px;
}

.pagination-switch-enter-active,
.pagination-switch-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.pagination-switch-enter-from,
.pagination-switch-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 1120px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .review-workbench {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-inspector {
    position: static;
  }

  .panel-heading,
  .panel-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-field,
  .filter-field:first-child {
    width: 100%;
  }
}

@media (max-width: 720px) {
  .admin-reservations-page {
    padding: 16px;
  }

  .stats-grid,
  .inspector-grid,
  .inspector-actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-panel {
    border-radius: 22px;
  }
}
</style>
