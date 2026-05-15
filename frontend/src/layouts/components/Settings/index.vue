<script lang="ts" setup>
import { useLayoutMode } from "@@/composables/useLayoutMode"
import { removeLayoutsConfig } from "@@/utils/local-storage"
import { Refresh } from "@element-plus/icons-vue"
import { useSettingsStore } from "@/pinia/stores/settings"
import SelectLayoutMode from "./SelectLayoutMode.vue"

const { isLeft } = useLayoutMode()
const settingsStore = useSettingsStore()

const {
  showLogo,
  fixedHeader,
  showNotify,
  showGreyMode,
  showColorWeakness
} = storeToRefs(settingsStore)

const switchSettings = {
  "显示 Logo": showLogo,
  "固定 Header": fixedHeader,
  "显示消息通知": showNotify,
  "显示灰色模式": showGreyMode,
  "显示色弱模式": showColorWeakness
}

watchEffect(() => {
  !isLeft.value && (fixedHeader.value = true)
})

function resetLayoutsConfig() {
  removeLayoutsConfig()
  location.reload()
}
</script>

<template>
  <div class="setting-container">
    <h4>布局配置</h4>
    <SelectLayoutMode />
    <el-divider />
    <h4>功能配置</h4>
    <div v-for="(settingValue, settingName, index) in switchSettings" :key="index" class="setting-item">
      <span class="setting-name">{{ settingName }}</span>
      <el-switch v-model="settingValue.value" :disabled="!isLeft && settingName === '固定 Header'" />
    </div>
    <el-button type="danger" :icon="Refresh" @click="resetLayoutsConfig">
      重置
    </el-button>
  </div>
</template>

<style lang="scss" scoped>
@import "@@/assets/styles/mixins.scss";

.setting-container {
  padding: 20px;

  .setting-item {
    font-size: 14px;
    color: var(--el-text-color-regular);
    padding: 5px 0;
    display: flex;
    justify-content: space-between;
    align-items: center;

    .setting-name {
      @extend %ellipsis;
    }
  }

  .el-button {
    margin-top: 40px;
    width: 100%;
  }
}
</style>
