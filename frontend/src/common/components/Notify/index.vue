<script lang="ts" setup>
import type { NotificationCategory, NotificationSummaryCount } from "@/common/apis/notifications/type"
import { getNotificationPageApi, getNotificationSummaryApi, readAllNotificationsApi, readNotificationApi } from "@/common/apis/notifications"
import { Bell } from "@element-plus/icons-vue"
import { useUserStore } from "@/pinia/stores/user"
import AdminNotificationPublishDialog from "./AdminNotificationPublishDialog.vue"
import List from "./List.vue"
import type { NotifyItem } from "./type"

type TabName = "通知" | "消息" | "待办"

interface TabItem {
  name: TabName
  category: NotificationCategory
  type: "primary" | "success" | "warning" | "danger" | "info"
}

const router = useRouter()
const userStore = useUserStore()
const badgeMax = 99
const pageSize = 20
const popoverWidth = 376
const tabs: TabItem[] = [
  { name: "通知", category: "NOTICE", type: "primary" },
  { name: "消息", category: "MESSAGE", type: "danger" },
  { name: "待办", category: "TODO", type: "warning" }
]

const emptySummary = (): NotificationSummaryCount => ({
  NOTICE: 0,
  MESSAGE: 0,
  TODO: 0
})

const badgeValue = computed(() => summary.value.totalUnread)
const activeName = ref<TabName>("通知")
const summary = ref({
  totalUnread: 0,
  unreadByCategory: emptySummary()
})
const publishDialogVisible = ref(false)
const items = reactive<Record<NotificationCategory, NotifyItem[]>>({
  NOTICE: [],
  MESSAGE: [],
  TODO: []
})
const loading = reactive<Record<NotificationCategory, boolean>>({
  NOTICE: false,
  MESSAGE: false,
  TODO: false
})
const loaded = reactive<Record<NotificationCategory, boolean>>({
  NOTICE: false,
  MESSAGE: false,
  TODO: false
})

const activeTab = computed(() => tabs.find(item => item.name === activeName.value) ?? tabs[0])
const currentUnread = computed(() => summary.value.unreadByCategory[activeTab.value.category])
const isAdminUser = computed(() => userStore.roles.some(role => role.toLowerCase() === "admin"))

function normalizeRouteQuery(routeQuery?: NotifyItem["routeQuery"]) {
  if (!routeQuery) return undefined

  return Object.fromEntries(
    Object.entries(routeQuery).map(([key, value]) => [key, value == null ? "" : String(value)])
  )
}

async function refreshSummary() {
  try {
    const { data } = await getNotificationSummaryApi()
    summary.value = data
  } catch {
    summary.value = {
      totalUnread: 0,
      unreadByCategory: emptySummary()
    }
  }
}

async function loadCategory(category: NotificationCategory, force = false) {
  if (loading[category]) return
  if (!force && loaded[category]) return

  loading[category] = true
  try {
    const { data } = await getNotificationPageApi({
      category,
      pageNum: 1,
      pageSize
    })
    items[category] = data.list
    loaded[category] = true
  } catch {
    items[category] = []
    loaded[category] = false
  } finally {
    loading[category] = false
  }
}

async function initialize(force = false) {
  await Promise.all([
    refreshSummary(),
    loadCategory(activeTab.value.category, force)
  ])
}

async function handlePopoverShow() {
  await initialize(true)
}

async function handleMarkRead(item: NotifyItem) {
  if (item.read) return

  try {
    await readNotificationApi(item.id)
    await Promise.all([
      refreshSummary(),
      loadCategory(item.category, true)
    ])
  } catch {
    await refreshSummary()
  }
}

async function handleItemClick(item: NotifyItem) {
  if (!item.route) return

  await router.push({
    path: item.route,
    query: normalizeRouteQuery(item.routeQuery)
  })

  if (!item.read) {
    try {
      await readNotificationApi(item.id)
    } catch {}
  }

  await Promise.all([
    refreshSummary(),
    loadCategory(item.category, true)
  ])
}

async function handleReadAll() {
  if (currentUnread.value === 0) return

  try {
    await readAllNotificationsApi({
      category: activeTab.value.category
    })
    await Promise.all([
      refreshSummary(),
      loadCategory(activeTab.value.category, true)
    ])
  } catch {
    await refreshSummary()
  }
}

function openPublishDialog() {
  if (!isAdminUser.value) return
  publishDialogVisible.value = true
}

async function handleNotificationPublished() {
  await Promise.all([
    refreshSummary(),
    loadCategory(activeTab.value.category, true)
  ])
}

onMounted(() => {
  void initialize(true)
})

watch(activeName, () => {
  void loadCategory(activeTab.value.category)
})
</script>

<template>
  <div class="notify">
    <el-popover placement="bottom" :width="popoverWidth" trigger="click" @show="handlePopoverShow">
      <template #reference>
        <el-badge :value="badgeValue" :max="badgeMax" :hidden="badgeValue === 0">
          <el-tooltip effect="dark" content="消息通知" placement="bottom">
            <el-icon :size="20">
              <Bell />
            </el-icon>
          </el-tooltip>
        </el-badge>
      </template>
      <template #default>
        <el-tabs v-model="activeName" class="notify-tabs" stretch>
          <el-tab-pane v-for="item in tabs" :key="item.category" :name="item.name">
            <template #label>
              {{ item.name }}
              <el-badge :value="summary.unreadByCategory[item.category]" :max="badgeMax" :type="item.type" />
            </template>
            <el-scrollbar height="400px">
              <List
                :data="items[item.category]"
                :loading="loading[item.category]"
                @item-click="handleItemClick"
                @mark-read="handleMarkRead"
              />
            </el-scrollbar>
          </el-tab-pane>
        </el-tabs>
        <div class="notify-footer">
          <span class="notify-footer__text">
            {{ currentUnread === 0 ? `${activeName}已全部读完` : `${activeName}还有 ${currentUnread} 条未读` }}
          </span>
          <div class="notify-footer__actions">
            <el-button
              v-if="isAdminUser"
              class="notify-publish-button"
              link
              type="primary"
              @click="openPublishDialog"
            >
              发布通知
            </el-button>
            <el-button class="notify-read-all-button" link type="primary" :disabled="currentUnread === 0" @click="handleReadAll">
              全部标记已读
            </el-button>
          </div>
        </div>
      </template>
    </el-popover>
    <AdminNotificationPublishDialog v-model="publishDialogVisible" @published="handleNotificationPublished" />
  </div>
</template>

<style lang="scss" scoped>
.notify-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);

  &__text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
</style>
