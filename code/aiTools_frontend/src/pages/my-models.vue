<template>
  <view class="page page--no-tabbar">
    <!-- 顶部导航：复用 my.vue / favorites.vue 的 topbar 风格 -->
    <view class="topbar">
      <view class="topbar__back" hover-class="topbar__back--hover" @click="goBack">
        <WfIcon name="back" :size="36" />
      </view>
      <text class="topbar__title">我的 AI 模型</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 顶部说明：复用 redesign-card -->
      <view class="redesign-card notice">
        <text class="notice__title">BYOK（Bring Your Own Key）</text>
        <text class="notice__desc">你添加的 Key 仅你自己可见，后端 AES-256-GCM 加密落库。列表只展示前 4 位 prefix，不会泄露完整 Key。</text>
      </view>

      <!-- 列表 -->
      <view class="section-title">
        <text>已添加（{{ models.length }}）</text>
        <text class="section-title__action" @click="loadList">刷新</text>
      </view>
      <view v-if="loading" class="redesign-empty"><text>加载中…</text></view>
      <view v-else-if="models.length === 0" class="redesign-empty">
        <view class="redesign-empty__icon"><text class="redesign-empty__emoji">🧠</text></view>
        <text class="redesign-empty__text">还没有添加任何模型，填写下方表单加一个</text>
      </view>
      <view v-else class="me-list">
        <view v-for="m in models" :key="m.id" class="model-row">
          <view class="tool-avatar" :class="'tool-avatar--' + avatarType(m.capability)">
            <text class="ta-emoji">{{ avatarEmoji(m.capability) }}</text>
          </view>
          <view class="model-row__body">
            <view class="model-row__head">
              <text class="model-row__name">{{ m.nickname || m.modelCode }}</text>
              <text v-if="m.isDefault === 1" class="redesign-tag model-row__default">默认</text>
            </view>
            <text class="model-row__meta">
              {{ m.modelCode }} · {{ capLabel(m.capability) }}
              <text v-if="m.apiKeyPrefix" class="model-row__prefix"> · {{ m.apiKeyPrefix }}…</text>
            </text>
          </view>
          <view class="model-row__actions">
            <view v-if="m.isDefault !== 1" class="btn-xs" @click="onSetDefault(m)">设为默认</view>
            <view class="btn-xs btn-xs--danger" @click="onDelete(m)">删除</view>
          </view>
        </view>
      </view>

      <!-- 添加表单：复用 redesign-card + me-item 输入行 -->
      <view class="section-title"><text>添加新模型</text></view>
      <view class="redesign-card form-card">
        <view class="form-row">
          <text class="form-row__label">模型编码 *</text>
          <input class="form-row__input" v-model="form.modelCode" placeholder="如 glm-4-flash / deepseek-chat" />
        </view>
        <view class="form-row">
          <text class="form-row__label">API Key *</text>
          <input class="form-row__input" v-model="form.apiKey" password placeholder="只在这里输入，仅写入本次保存" />
        </view>
        <view class="form-row">
          <text class="form-row__label">能力 *</text>
          <picker mode="selector" :range="capPickerLabels" @change="onCapChange">
            <view class="form-row__picker">
              <text>{{ capLabel(form.capability) || '请选择' }}</text>
              <WfIcon name="chevron-down" :size="28" />
            </view>
          </picker>
        </view>
        <view class="form-row">
          <text class="form-row__label">别名</text>
          <input class="form-row__input" v-model="form.nickname" placeholder="如 我的 GLM / 公司 DeepSeek" />
        </view>
        <view class="form-row">
          <text class="form-row__label">API URL（可选）</text>
          <input class="form-row__input" v-model="form.apiUrl" placeholder="留空走系统默认" />
        </view>
        <view class="form-row form-row--inline">
          <text class="form-row__label">设为默认</text>
          <switch :checked="form.isDefault === 1" @change="(e) => form.isDefault = e.detail.value ? 1 : 0" />
        </view>
        <button class="btn-solid" :disabled="saving" @click="onSave">{{ saving ? '保存中…' : '保存' }}</button>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import WfIcon from '@/components/WfIcon.vue'
import {
  listUserAiModels,
  saveUserAiModel,
  deleteUserAiModel,
  setDefaultUserAiModel,
} from '@/api/aiModel.js'
import { safeBack } from '@/utils/pageTransition'

const models = ref([])
const loading = ref(false)
const saving = ref(false)

const capOptions = [
  { value: 'text',   label: '文本生成' },
  { value: 'vision', label: '视觉理解' },
  { value: 'asr',    label: '语音转写' },
  { value: 'ocr',    label: 'OCR' },
]
const capPickerLabels = capOptions.map((c) => c.label)

const form = reactive({
  modelCode: '',
  apiKey: '',
  capability: 'text',
  nickname: '',
  apiUrl: '',
  isDefault: 0,
})

const capLabel = (v) => capOptions.find((c) => c.value === v)?.label || v || ''
// 复用 redesign.scss 的 tool-avatar 渐变色（text/doc/audio/image/ocr/code）
const avatarType = (cap) => ({ text: 'text', vision: 'image', asr: 'audio', ocr: 'doc' }[cap] || 'code')
const avatarEmoji = (cap) => ({ text: '📝', vision: '🖼️', asr: '🎤', ocr: '📄' }[cap] || '🧠')

const onCapChange = (e) => {
  form.capability = capOptions[e.detail.value].value
}

/**
 * 返回：用项目的 safeBack。
 * H5 端 navigateBack 走 history.back()，页面栈为空时（刷新本页 / 热更新重载后）
 * 既不报错也不触发 fail，点返回会完全没反应；safeBack 主动判断栈深度并 reLaunch 兜底。
 */
const goBack = () => {
  safeBack('/pages/my')
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await listUserAiModels()
    const list = (res && res.data) || res || []
    models.value = Array.isArray(list) ? list : []
  } catch (e) {
    models.value = []
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const onSave = async () => {
  if (!form.modelCode || !form.apiKey || !form.capability) {
    uni.showToast({ title: '请填写必填项', icon: 'none' }); return
  }
  saving.value = true
  try {
    await saveUserAiModel({
      modelCode: form.modelCode.trim(),
      apiKey: form.apiKey,
      apiUrl: form.apiUrl || undefined,
      apiModel: form.modelCode.trim(),
      nickname: form.nickname || undefined,
      capability: form.capability,
      isDefault: form.isDefault,
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    form.apiKey = ''
    await loadList()
  } catch (e) {
    uni.showToast({ title: e.message || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

const onDelete = async (m) => {
  const ok = await new Promise((resolve) => {
    uni.showModal({ title: '确认删除', content: `删除「${m.nickname || m.modelCode}」？`, success: (r) => resolve(r.confirm) })
  })
  if (!ok) return
  try {
    await deleteUserAiModel(m.id)
    uni.showToast({ title: '已删除', icon: 'success' })
    await loadList()
  } catch (e) {
    uni.showToast({ title: '删除失败', icon: 'none' })
  }
}

const onSetDefault = async (m) => {
  try {
    await setDefaultUserAiModel(m.id)
    uni.showToast({ title: '已设为默认', icon: 'success' })
    await loadList()
  } catch (e) {
    uni.showToast({ title: '设置失败', icon: 'none' })
  }
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
@import '@/styles/buttons.scss';

.page { min-height: 100vh; background: #F9FAFB; padding-bottom: 0; }

.topbar {
  /* App 端自绘导航栏需避开状态栏/刘海（uni 注入 --status-bar-height，H5 端为 0） */
  padding-top: var(--status-bar-height, 0px);
  display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: #fff;
  border-bottom: 1rpx solid #F3F4F6;
  &__back { width: 60rpx; height: 60rpx; @include btn-base; color: #1F2937; }
  &__title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #0f172a; }
}
.content { padding-bottom: 60rpx; }

// 复用 redesign-card（背景白 + 圆角 24rpx + 轻阴影）—— 不 import，全局类在 scoped 不生效
.redesign-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.redesign-tag {
  display: inline-flex; align-items: center; height: 44rpx;
  padding: 0 16rpx; border-radius: 8rpx;
  background: #F9FAFB; color: #4B5563; font-size: 24rpx;
}
.redesign-empty {
  display: flex; flex-direction: column; align-items: center; padding: 64rpx 32rpx;
  &__icon {
    width: 192rpx; height: 192rpx; border-radius: 24rpx;
    background: #EFF6FF;
    display: flex; align-items: center; justify-content: center;
    margin-bottom: 32rpx;
  }
  &__emoji { font-size: 88rpx; }
  &__text { font-size: 28rpx; color: #9CA3AF; }
}
// 复用 tool-avatar 渐变 + emoji（与首页工具卡同款）
.tool-avatar {
  width: 88rpx; height: 88rpx; border-radius: 24rpx;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  &--text  { background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%); }
  &--doc   { background: linear-gradient(135deg, #93C5FD 0%, #3B82F6 100%); }
  &--audio { background: linear-gradient(135deg, #FCD34D 0%, #F59E0B 100%); }
  &--image { background: linear-gradient(135deg, #C7D2FE 0%, #6366F1 100%); }
}
.ta-emoji { font-size: 40rpx; line-height: 1; }

.notice {
  margin: 24rpx 32rpx 0;
  &__title { display: block; font-size: 28rpx; color: #1D4ED8; font-weight: 600; margin-bottom: 8rpx; }
  &__desc { font-size: 24rpx; color: #1E40AF; line-height: 1.5; }
}

.section-title {
  display: flex; justify-content: space-between; align-items: center;
  padding: 32rpx 32rpx 8rpx; font-size: 24rpx; color: #6B7280; font-weight: 600;
  &__action { font-size: 24rpx; color: #3B82F6; }
}

.me-list {
  margin: 0 32rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.model-row {
  display: flex; align-items: center; gap: 24rpx;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #F3F4F6;
  &:last-child { border-bottom: 0; }
  &__body { flex: 1; min-width: 0; }
  &__head { display: flex; align-items: center; gap: 12rpx; }
  &__name { font-size: 30rpx; font-weight: 600; color: #1F2937; }
  &__default { flex-shrink: 0; }
  &__meta { display: block; margin-top: 6rpx; font-size: 22rpx; color: #6B7280; }
  &__prefix { font-family: monospace; color: #9CA3AF; }
  &__actions { display: flex; gap: 12rpx; flex-shrink: 0; }
}

// 复用 buttons.scss 的 btn-xs（次按钮），并扩展一个红色变体
.btn-xs {
  @include btn(xs, secondary);
  &--danger {
    @include btn-base;
    height: 52rpx; padding: 0 20rpx; font-size: 23rpx; font-weight: 600;
    border-radius: 8rpx; line-height: 1;
    background: #FEF2F2; color: #DC2626; border: 1rpx solid #FECACA;
    &:active { background: #FEE2E2; }
  }
}
// 复用 redesign 的主按钮（圆形主色）
.btn-solid {
  height: 96rpx; border-radius: 9999rpx; background: #3B82F6; color: #fff;
  font-size: 32rpx; font-weight: 600;
  display: flex; align-items: center; justify-content: center; width: 100%;
  border: 0;
  &:active { background: #1D4ED8; }
}

.form-card { margin: 0 32rpx 24rpx; }
.form-row {
  margin-bottom: 24rpx;
  &__label { display: block; font-size: 24rpx; color: #4B5563; margin-bottom: 8rpx; }
  &__input {
    width: 100%; box-sizing: border-box; height: 80rpx; padding: 0 24rpx;
    background: #F9FAFB; border: 1rpx solid #E5E7EB; border-radius: 12rpx;
    font-size: 28rpx; color: #1F2937;
  }
  &__picker {
    height: 80rpx; padding: 0 24rpx; background: #F9FAFB; border: 1rpx solid #E5E7EB; border-radius: 12rpx;
    display: flex; justify-content: space-between; align-items: center;
    font-size: 28rpx; color: #1F2937;
  }
  &--inline { display: flex; align-items: center; justify-content: space-between; }
}
.safe-area-bottom { height: 60rpx; }
</style>
