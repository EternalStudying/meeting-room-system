<script lang="ts" setup>
import { computed, reactive, ref } from "vue"
import { ElMessage } from "element-plus"
import type {
  AdminNotificationPublishData,
  AdminNotificationPublishRequestData,
  AdminNotificationRecipientScope,
  AdminNotificationType
} from "@/common/apis/notifications/type"
import { publishAdminNotificationApi } from "@/common/apis/notifications"

defineOptions({
  name: "AdminNotificationPublishDialog"
})

interface Props {
  modelValue: boolean
}

interface Emits {
  (event: "update:modelValue", value: boolean): void
  (event: "published", value: AdminNotificationPublishData): void
}

interface PublishTypeOption {
  value: AdminNotificationType
  label: string
}

interface RecipientScopeOption {
  value: AdminNotificationRecipientScope
  label: string
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const publishTypeOptions: PublishTypeOption[] = [
  { value: "ANNOUNCEMENT", label: "系统公告" },
  { value: "MAINTENANCE", label: "维护通知" }
]

const recipientScopeOptions: RecipientScopeOption[] = [
  { value: "ALL", label: "全体成员" },
  { value: "USERS", label: "普通用户" },
  { value: "ADMINS", label: "管理员" }
]

const publishForm = reactive<AdminNotificationPublishRequestData>({
  type: "ANNOUNCEMENT",
  recipientScope: "ALL",
  title: "",
  content: ""
})
const publishing = ref(false)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit("update:modelValue", value)
})

function resetPublishForm() {
  publishForm.type = "ANNOUNCEMENT"
  publishForm.recipientScope = "ALL"
  publishForm.title = ""
  publishForm.content = ""
}

function buildPayload(): AdminNotificationPublishRequestData | null {
  const title = publishForm.title.trim()
  const content = publishForm.content.trim()

  if (!title) {
    ElMessage.warning("请输入通知标题")
    return null
  }
  if (!content) {
    ElMessage.warning("请输入通知内容")
    return null
  }

  return {
    type: publishForm.type,
    recipientScope: publishForm.recipientScope,
    title,
    content
  }
}

function closeDialog() {
  if (publishing.value) return
  dialogVisible.value = false
}

async function submitPublish() {
  const payload = buildPayload()
  if (!payload) return

  publishing.value = true
  try {
    const { data } = await publishAdminNotificationApi(payload)
    ElMessage.success(`已发布给 ${data.publishedCount} 个接收人`)
    resetPublishForm()
    dialogVisible.value = false
    emit("published", data)
  } catch {
    ElMessage.error("通知发布失败，请稍后重试")
  } finally {
    publishing.value = false
  }
}

defineExpose({
  publishForm,
  publishing,
  submitPublish
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    width="520px"
    class="admin-notification-publish-dialog"
    title="发布通知"
    append-to-body
    destroy-on-close
  >
    <el-form label-position="top" class="publish-form" @submit.prevent>
      <el-form-item label="发布类型">
        <el-radio-group v-model="publishForm.type">
          <el-radio-button v-for="item in publishTypeOptions" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="接收范围">
        <el-select v-model="publishForm.recipientScope" class="form-control">
          <el-option
            v-for="item in recipientScopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="通知标题">
        <el-input v-model="publishForm.title" maxlength="128" show-word-limit placeholder="例如：系统升级通知" />
      </el-form-item>

      <el-form-item label="通知内容">
        <el-input
          v-model="publishForm.content"
          type="textarea"
          maxlength="500"
          show-word-limit
          :rows="6"
          placeholder="填写公告或维护安排的具体内容"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="publishing" @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">发布</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.publish-form {
  display: grid;
  gap: 2px;
}

.form-control {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
