<script lang="ts" setup>
import { ref, watch } from "vue"
import type { AdminRoomDeviceBindingItem, DeviceInventoryData } from "@/common/apis/rooms/type"
import type { RoomUpsertForm } from "../model"
import { createEmptyRoomForm } from "../model"

defineOptions({
  name: "RoomUpsertDialog"
})

const props = defineProps<{
  modelValue: boolean
  formData: RoomUpsertForm
  deviceOptions: DeviceInventoryData[]
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
  submit: [payload: RoomUpsertForm]
}>()

const localForm = ref<RoomUpsertForm>(createEmptyRoomForm())
const localDeviceBindings = ref<AdminRoomDeviceBindingItem[]>([])

watch(
  () => [props.modelValue, props.formData] as const,
  ([visible]) => {
    if (!visible) return
    localForm.value = {
      ...props.formData,
      deviceBindings: [...props.formData.deviceBindings]
    }
    localDeviceBindings.value = props.formData.deviceBindings.map(item => ({ ...item }))
  },
  { immediate: true, deep: true }
)

function closeDialog() {
  emit("update:modelValue", false)
}

function getDeviceQuantity(deviceId: number) {
  return localDeviceBindings.value.find(item => item.deviceId === deviceId)?.quantity ?? 0
}

function setDeviceQuantity(deviceId: number, value: number | undefined) {
  const quantity = Math.max(0, Number(value) || 0)
  const index = localDeviceBindings.value.findIndex(item => item.deviceId === deviceId)

  if (quantity === 0) {
    if (index >= 0) localDeviceBindings.value.splice(index, 1)
    return
  }

  if (index >= 0) {
    localDeviceBindings.value[index] = { deviceId, quantity }
    return
  }

  localDeviceBindings.value.push({ deviceId, quantity })
}

function submitForm() {
  emit("submit", {
    ...localForm.value,
    deviceBindings: localDeviceBindings.value
      .filter(item => item.quantity > 0)
      .map(item => ({ ...item }))
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="min(760px, calc(100vw - 32px))"
    class="room-upsert-dialog"
    destroy-on-close
    align-center
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <p class="eyebrow">Room Editor</p>
          <h3>{{ localForm.id === null ? "新增会议室" : "编辑会议室" }}</h3>
        </div>
      </div>
    </template>
    <div class="form-grid">
      <div class="form-field"><label>会议室编码</label><el-input v-model="localForm.roomCode" placeholder="例如 A401" /></div>
      <div class="form-field"><label>会议室名称</label><el-input v-model="localForm.name" placeholder="请输入会议室名称" /></div>
      <div class="form-field"><label>所在区域</label><el-input v-model="localForm.location" placeholder="例如 A楼 4层" /></div>
      <div class="form-field"><label>可容纳人数</label><el-input-number v-model="localForm.capacity" :min="1" :max="100" controls-position="right" /></div>
      <div class="form-field">
        <label>房间状态</label>
        <el-select v-model="localForm.status">
          <el-option label="可用" value="AVAILABLE" />
          <el-option label="维护中" value="MAINTENANCE" />
        </el-select>
      </div>
      <div v-if="localForm.status === 'MAINTENANCE'" class="form-field form-field-wide">
        <label>维护备注</label>
        <el-input v-model="localForm.maintenanceRemark" placeholder="补充当前维护原因或恢复时间" />
      </div>
      <div class="form-field form-field-wide">
        <label>空间说明</label>
        <el-input
          v-model="localForm.description"
          type="textarea"
          :rows="4"
          maxlength="120"
          show-word-limit
          placeholder="补充适用场景、空间说明和管理备注"
        />
      </div>
      <div class="form-field form-field-wide">
        <label>会议室设备</label>
        <div class="upsert-device-list">
          <div v-for="device in deviceOptions" :key="device.id" class="upsert-device-item" :class="{ disabled: device.status === 'DISABLED' && getDeviceQuantity(device.id) === 0 }">
            <div>
              <div class="device-title">{{ device.name }}</div>
              <div class="device-meta">{{ device.deviceCode }} · 库存 {{ device.total }}</div>
            </div>
            <div class="device-side">
              <span class="device-state" :class="{ disabled: device.status === 'DISABLED' }">{{ device.status === "DISABLED" ? "停用" : "可用" }}</span>
              <el-input-number
                :model-value="getDeviceQuantity(device.id)"
                :min="0"
                :max="device.total"
                controls-position="right"
                :disabled="device.status === 'DISABLED' && getDeviceQuantity(device.id) === 0"
                @update:model-value="setDeviceQuantity(device.id, $event)"
              />
            </div>
          </div>
          <div v-if="deviceOptions.length === 0" class="device-empty">暂无可选设备</div>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button class="ghost-button" @click="closeDialog">暂不处理</el-button>
        <el-button class="primary-button small" @click="submitForm">保存房间</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-header,
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dialog-header h3 {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.12;
  color: #1a3046;
}

.eyebrow {
  margin: 0;
  color: #7c8ca0;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-field {
  display: grid;
  gap: 8px;
}

.form-field-wide {
  grid-column: 1 / -1;
}

.form-field label {
  font-size: 12px;
  font-weight: 700;
  color: #6d8094;
}

.upsert-device-list {
  display: grid;
  gap: 10px;
}

.upsert-device-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(218, 226, 238, 0.9);
}

.upsert-device-item.disabled {
  opacity: 0.58;
}

.device-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a3046;
}

.device-meta,
.device-empty {
  margin-top: 4px;
  font-size: 12px;
  color: #7c8ca0;
}

.device-side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.device-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(77, 168, 132, 0.12);
  color: #408a70;
  font-size: 12px;
  font-weight: 700;
}

.device-state.disabled {
  background: rgba(113, 131, 154, 0.12);
  color: #7d8ca0;
}

.form-field :deep(.el-select),
.form-field :deep(.el-input-number),
.form-field :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}

.dialog-footer {
  justify-content: center;
  margin-top: 10px;
}

:deep(.room-upsert-dialog .el-dialog) {
  border-radius: 30px;
  overflow: hidden;
  background: rgba(247, 249, 253, 0.98);
}

:deep(.room-upsert-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 0;
}

:deep(.room-upsert-dialog .el-dialog__body) {
  padding: 18px 24px 8px;
}

:deep(.room-upsert-dialog .el-dialog__footer) {
  padding: 10px 24px 22px;
}

@media screen and (max-width: 980px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .dialog-header,
  .dialog-footer,
  .upsert-device-item,
  .device-side {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
