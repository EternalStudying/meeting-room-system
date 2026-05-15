<script lang="ts" setup>
import { useAppStore } from "@/pinia/stores/app"
import { useSettingsStore } from "@/pinia/stores/settings"
import { AppMain, Logo, NavigationBar, Sidebar } from "../components"

const appStore = useAppStore()
const settingsStore = useSettingsStore()
const { showLogo } = storeToRefs(settingsStore)

const layoutClasses = computed(() => ({
  hideSidebar: !appStore.sidebar.opened
}))
</script>

<template>
  <div :class="layoutClasses" class="app-wrapper">
    <div class="fixed-header layout-header">
      <Logo v-if="showLogo" :collapse="false" class="logo" />
      <div class="content">
        <NavigationBar />
      </div>
    </div>
    <div class="main-container">
      <Sidebar class="sidebar-container" />
      <AppMain class="app-main" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@import "@@/assets/styles/mixins.scss";
$transition-time: 0.35s;

.app-wrapper {
  @extend %clearfix;
  width: 100%;
}

.fixed-header {
  position: fixed;
  top: 0;
  z-index: 1002;
  width: 100%;
  display: flex;

  .logo {
    flex: none;
    width: var(--v3-sidebar-width);
  }

  .content {
    flex: 1;
    overflow: hidden;
  }
}

.layout-header {
  background-color: var(--v3-header-bg-color);
  box-shadow: var(--v3-header-box-shadow);
  border-bottom: var(--v3-header-border-bottom);
}

.main-container {
  min-height: 100%;
}

.sidebar-container {
  background-color: var(--el-menu-bg-color);
  transition: width $transition-time;
  width: var(--v3-sidebar-width);
  height: 100%;
  position: fixed;
  left: 0;
  z-index: 1001;
  overflow: hidden;
  border-right: var(--v3-sidebar-border-right);
  padding-top: var(--v3-navigationbar-height);
}

.app-main {
  transition: padding-left $transition-time;
  padding-top: var(--v3-navigationbar-height);
  padding-left: var(--v3-sidebar-width);
  height: 100vh;
  overflow: auto;
}

.hideSidebar {
  .sidebar-container {
    width: var(--v3-sidebar-hide-width);
  }

  .app-main {
    padding-left: var(--v3-sidebar-hide-width);
  }
}
</style>
