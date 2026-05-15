<script lang="ts" setup>
import type {
  AdminNotificationPublishData,
  AdminNotificationPublishRequestData,
  AdminNotificationRecipientScope,
  AdminNotificationType
} from "@/common/apis/notifications/type"
import { publishAdminNotificationApi } from "@/common/apis/notifications"
import { Bell, ChatLineRound, Service, UserFilled } from "@element-plus/icons-vue"
import { ElMessage } from "element-plus"

interface PublishTypeOption {
  value: AdminNotificationType
  label: string
  extra: string
  tone: "blue" | "amber"
}

interface RecipientScopeOption {
  value: AdminNotificationRecipientScope
  label: string
}

const publishTypeOptions: PublishTypeOption[] = [
  { value: "ANNOUNCEMENT", label: "系统公告", extra: "系统公告", tone: "blue" },
  { value: "MAINTENANCE", label: "维护通知", extra: "维护通知", tone: "amber" }
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
const lastPublish = ref<AdminNotificationPublishData | null>(null)

const selectedType = computed(() => publishTypeOptions.find(item => item.value === publishForm.type) ?? publishTypeOptions[0])
const selectedScope = computed(() => recipientScopeOptions.find(item => item.value === publishForm.recipientScope) ?? recipientScopeOptions[0])
const previewTitle = computed(() => publishForm.title.trim() || selectedType.value.label)
const previewContent = computed(() => publishForm.content.trim() || "暂无内容")

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

async function submitPublish() {
  const payload = buildPayload()
  if (!payload) return

  publishing.value = true
  try {
    const { data } = await publishAdminNotificationApi(payload)
    lastPublish.value = data
    ElMessage.success(`已发布给 ${data.publishedCount} 个接收人`)
    publishForm.title = ""
    publishForm.content = ""
  } catch {
    ElMessage.error("通知发布失败，请稍后重试")
  } finally {
    publishing.value = false
  }
}

defineExpose({
  publishForm,
  submitPublish,
  lastPublish
})
</script>

<template>
  <div class="admin-notifications-page">
    <section class="hero-panel page-topbar-fixed">
      <div class="hero-copy">
        <p class="eyebrow">Notification Center</p>
        <h1 class="page-hero-title">通知发布</h1>
      </div>
      <div class="hero-actions">
        <el-button class="hero-primary-button" :loading="publishing" @click="submitPublish">
          <el-icon><Bell /></el-icon>
          <span>发布通知</span>
        </el-button>
      </div>
    </section>

    <section class="publish-shell">
      <section class="publish-panel">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">Publish</p>
            <h2>发布内容</h2>
          </div>
        </div>

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
              :rows="8"
              placeholder="填写公告或维护安排的具体内容"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button :loading="publishing" type="primary" @click="submitPublish">发布通知</el-button>
          </div>
        </el-form>
      </section>

      <aside class="preview-panel">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">Preview</p>
            <h2>通知预览</h2>
          </div>
          <span class="preview-badge" :class="`is-${selectedType.tone}`">{{ selectedType.extra }}</span>
        </div>

        <div class="notify-preview" :class="`is-${selectedType.tone}`">
          <div class="preview-icon">
            <el-icon>
              <Service v-if="publishForm.type === 'MAINTENANCE'" />
              <ChatLineRound v-else />
            </el-icon>
          </div>
          <div>
            <h3>{{ previewTitle }}</h3>
            <p>{{ previewContent }}</p>
          </div>
        </div>

        <div class="scope-card">
          <div class="scope-icon">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div>
            <span>接收范围</span>
            <strong>{{ selectedScope.label }}</strong>
          </div>
        </div>

        <div v-if="lastPublish" class="publish-result">
          <span>最近发布</span>
          <strong>{{ lastPublish.title }}</strong>
          <p>{{ lastPublish.publishedCount }} 个接收人</p>
        </div>
      </aside>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.admin-notifications-page {
  min-height: 100%;
  padding: 24px;
  color: #1b3149;
  background:
    radial-gradient(circle at 0% 0%, rgba(107, 144, 216, 0.18), transparent 26%),
    radial-gradient(circle at 100% 10%, rgba(80, 193, 170, 0.14), transparent 32%),
    linear-gradient(180deg, #f6f7fb 0%, #edf1f7 100%);
}

.hero-primary-button {
  min-height: 42px;
  padding-inline: 18px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #6d8de8, #57c8b3);
  color: #fff;
  font-weight: 700;
}

.publish-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(340px, 0.55fr);
  gap: 16px;
}

.publish-panel,
.preview-panel {
  border: 1px solid rgba(207, 220, 238, 0.88);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 22px 54px rgba(83, 111, 144, 0.1);
  backdrop-filter: blur(18px);
}

.publish-panel {
  padding: 22px;
}

.preview-panel {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 22px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-heading h2 {
  margin: 6px 0 0;
  color: #17324d;
  font-size: 24px;
}

.panel-kicker {
  margin: 0;
  color: #6f8299;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.publish-form {
  display: grid;
  gap: 4px;
}

.form-control {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.preview-badge {
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.preview-badge.is-blue {
  background: rgba(90, 137, 222, 0.14);
  color: #426eb8;
}

.preview-badge.is-amber {
  background: rgba(224, 157, 64, 0.16);
  color: #a96818;
}

.notify-preview {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(211, 224, 240, 0.9);
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(248, 252, 255, 0.88));
}

.notify-preview h3 {
  margin: 0 0 8px;
  color: #17324d;
  font-size: 20px;
}

.notify-preview p {
  margin: 0;
  color: #5f7288;
  line-height: 1.8;
}

.preview-icon,
.scope-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  color: #fff;
}

.notify-preview.is-blue .preview-icon {
  background: linear-gradient(135deg, #6d8de8, #8fb2ef);
}

.notify-preview.is-amber .preview-icon {
  background: linear-gradient(135deg, #df9f43, #f0c16d);
}

.scope-card {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(211, 224, 240, 0.9);
  border-radius: 22px;
  background: rgba(246, 250, 255, 0.84);
}

.scope-icon {
  background: linear-gradient(135deg, #5fc7b2, #8bdcc9);
}

.scope-card span,
.publish-result span {
  display: block;
  color: #7b8da2;
  font-size: 12px;
}

.scope-card strong,
.publish-result strong {
  display: block;
  margin-top: 4px;
  color: #17324d;
  font-size: 18px;
}

.publish-result {
  padding: 16px;
  border-radius: 20px;
  background: rgba(233, 243, 255, 0.76);
}

.publish-result p {
  margin: 6px 0 0;
  color: #607389;
}

@media (max-width: 1120px) {
  .publish-shell {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .admin-notifications-page {
    padding: 16px;
  }

  .panel-heading {
    flex-direction: column;
  }
}
</style>
