<script lang="ts" setup>
import Notify from "@@/components/Notify/index.vue"
import { useDevice } from "@@/composables/useDevice"
import { useLayoutMode } from "@@/composables/useLayoutMode"
import { Setting, UserFilled } from "@element-plus/icons-vue"
import { useAppStore } from "@/pinia/stores/app"
import { useSettingsStore } from "@/pinia/stores/settings"
import { useUserStore } from "@/pinia/stores/user"
import { Breadcrumb, Hamburger, Sidebar } from "../index"

const { isMobile } = useDevice()
const { isTop } = useLayoutMode()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const { showNotify, showSettings } = storeToRefs(settingsStore)

function toggleSidebar() {
  appStore.toggleSidebar(false)
}

function logout() {
  userStore.logout()
  router.push("/login")
}

function openSettingsPanel() {
  appStore.settingsPanelOpen = true
}
</script>

<template>
  <div class="navigation-bar">
    <Hamburger
      v-if="!isTop || isMobile"
      :is-active="appStore.sidebar.opened"
      class="hamburger"
      @toggle-click="toggleSidebar"
    />
    <Breadcrumb v-if="!isTop || isMobile" class="breadcrumb" />
    <Sidebar v-if="isTop && !isMobile" class="sidebar" />
    <div class="right-menu">
      <el-tooltip v-if="showSettings" content="页面设置" placement="bottom">
        <button class="right-menu-item settings-trigger" type="button" @click="openSettingsPanel">
          <el-icon :size="18">
            <Setting />
          </el-icon>
        </button>
      </el-tooltip>
      <Notify v-if="showNotify" class="right-menu-item" />
      <el-dropdown>
        <div class="right-menu-item user">
          <el-avatar :icon="UserFilled" :size="30" />
          <span>{{ userStore.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="logout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.navigation-bar {
  height: var(--v3-navigationbar-height);
  overflow: hidden;
  color: var(--v3-navigationbar-text-color);
  display: flex;
  justify-content: space-between;

  .hamburger {
    display: flex;
    align-items: center;
    height: 100%;
    padding: 0 15px;
    cursor: pointer;
  }

  .breadcrumb {
    flex: 1;

    @media screen and (max-width: 576px) {
      display: none;
    }
  }

  .sidebar {
    flex: 1;
    min-width: 0;

    :deep(.el-menu) {
      background-color: transparent;
    }

    :deep(.el-sub-menu) {
      &.is-active {
        .el-sub-menu__title {
          color: var(--el-color-primary);
        }
      }
    }
  }

  .right-menu {
    margin-right: 10px;
    height: 100%;
    display: flex;
    align-items: center;

    &-item {
      margin: 0 10px;
      cursor: pointer;
      border: none;
      background: transparent;
      color: inherit;

      &:last-child {
        margin-left: 20px;
      }
    }

    .settings-trigger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 999px;
      transition: background-color 0.2s ease, color 0.2s ease;

      &:hover {
        background: rgba(64, 158, 255, 0.1);
        color: var(--el-color-primary);
      }
    }

    .user {
      display: flex;
      align-items: center;

      .el-avatar {
        margin-right: 10px;
      }

      span {
        font-size: 16px;
      }
    }
  }
}
</style>
