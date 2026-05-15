import { DeviceEnum, SIDEBAR_CLOSED, SIDEBAR_OPENED } from "@@/constants/app-key"
import { getSidebarStatus, setSidebarStatus } from "@@/utils/local-storage"
import { pinia } from "@/pinia"

interface Sidebar {
  opened: boolean
  withoutAnimation: boolean
}

/** 设置侧边栏状态本地缓存 */
function handleSidebarStatus(opened: boolean) {
  opened ? setSidebarStatus(SIDEBAR_OPENED) : setSidebarStatus(SIDEBAR_CLOSED)
}

export const useAppStore = defineStore("app", () => {
  const sidebar: Sidebar = reactive({
    opened: getSidebarStatus() !== SIDEBAR_CLOSED,
    withoutAnimation: false
  })

  const device = ref<DeviceEnum>(DeviceEnum.Desktop)
  const settingsPanelOpen = ref(false)

  watch(
    () => sidebar.opened,
    (opened) => {
      handleSidebarStatus(opened)
    }
  )

  const toggleSidebar = (withoutAnimation: boolean) => {
    sidebar.opened = !sidebar.opened
    sidebar.withoutAnimation = withoutAnimation
  }

  const closeSidebar = (withoutAnimation: boolean) => {
    sidebar.opened = false
    sidebar.withoutAnimation = withoutAnimation
  }

  const toggleDevice = (value: DeviceEnum) => {
    device.value = value
  }

  return { device, sidebar, settingsPanelOpen, toggleSidebar, closeSidebar, toggleDevice }
})

/**
 * @description 在 SPA 应用中可用于在 pinia 实例被激活前使用 store
 * @description 在 SSR 应用中可用于在 setup 外使用 store
 */
export function useAppStoreOutside() {
  return useAppStore(pinia)
}
