<script lang="ts" setup>
import { useDevice } from "@@/composables/useDevice"
import { useLayoutMode } from "@@/composables/useLayoutMode"
import { setCssVar } from "@@/utils/css"
import { useSettingsStore } from "@/pinia/stores/settings"
import { RightPanel, Settings } from "./components"
import { useResize } from "./composables/useResize"
import LeftMode from "./modes/LeftMode.vue"
import LeftTopMode from "./modes/LeftTopMode.vue"
import TopMode from "./modes/TopMode.vue"

useResize()

const { isMobile } = useDevice()
const { isLeft, isTop, isLeftTop } = useLayoutMode()
const settingsStore = useSettingsStore()
const { showSettings } = storeToRefs(settingsStore)

watchEffect(() => {
  setCssVar("--v3-tagsview-height", "0px")
})
</script>

<template>
  <div>
    <LeftMode v-if="isLeft || isMobile" />
    <TopMode v-else-if="isTop" />
    <LeftTopMode v-else-if="isLeftTop" />
    <RightPanel v-if="showSettings">
      <Settings />
    </RightPanel>
  </div>
</template>
