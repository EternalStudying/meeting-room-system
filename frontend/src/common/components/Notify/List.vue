<script lang="ts" setup>
import type { NotifyItem } from "./type"
import { formatDateTime } from "@/common/utils/datetime"

interface Props {
  data: NotifyItem[]
  loading?: boolean
}

interface Emits {
  (event: "item-click", item: NotifyItem): void
  (event: "mark-read", item: NotifyItem): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})
const emit = defineEmits<Emits>()

function handleItemClick(item: NotifyItem) {
  if (!item.route) return
  emit("item-click", item)
}

function handleMarkRead(item: NotifyItem) {
  emit("mark-read", item)
}
</script>

<template>
  <div class="notify-list">
    <div v-if="props.loading" class="loading-state">
      <el-skeleton v-for="index in 2" :key="index" :rows="3" animated class="loading-card" />
    </div>
    <el-empty v-else-if="props.data.length === 0" description="暂无消息" />
    <el-card
      v-else
      v-for="item in props.data"
      :key="item.id"
      shadow="never"
      class="card-container"
      :class="{
        'is-unread': !item.read,
        'is-actionable': Boolean(item.route)
      }"
      @click="handleItemClick(item)"
    >
      <template #header>
        <div class="card-header">
          <div class="card-header-main">
            <span v-if="!item.read" class="unread-dot" />
            <span class="card-title">{{ item.title }}</span>
            <el-tag v-if="item.extra" :type="item.status" effect="plain" size="small">{{ item.extra }}</el-tag>
          </div>
          <div class="card-time">
            {{ formatDateTime(item.createdAt, "MM-DD HH:mm") }}
          </div>
        </div>
      </template>
      <div class="card-body">
        {{ item.content || "暂无详情" }}
      </div>
      <div class="card-actions">
        <el-button v-if="item.route" link type="primary" @click.stop="handleItemClick(item)">
          去查看
        </el-button>
        <el-button v-if="!item.read" link @click.stop="handleMarkRead(item)">
          标记已读
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.notify-list {
  min-height: 136px;
}

.loading-state {
  display: grid;
  gap: 10px;
}

.loading-card {
  :deep(.el-skeleton__item) {
    border-radius: 12px;
  }
}

.card-container {
  margin-bottom: 10px;
  border: 1px solid rgba(213, 224, 238, 0.9);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;

  &.is-unread {
    border-color: rgba(103, 151, 255, 0.35);
    box-shadow: 0 10px 24px rgba(84, 128, 214, 0.08);
  }

  &.is-actionable {
    cursor: pointer;

    &:hover {
      border-color: rgba(88, 145, 255, 0.55);
      transform: translateY(-1px);
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;

    .card-header-main {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      min-width: 0;
    }

    .unread-dot {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: var(--el-color-primary);
      flex-shrink: 0;
    }

    .card-title {
      font-weight: bold;
      color: #233549;
    }

    .card-time {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      white-space: nowrap;
    }
  }

  .card-body {
    font-size: 12px;
    line-height: 1.7;
    color: #5f7288;
  }

  .card-actions {
    display: flex;
    justify-content: flex-end;
    gap: 14px;
    margin-top: 12px;
  }
}
</style>
