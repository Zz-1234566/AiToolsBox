<template>
  <view class="page">
    <page-header title="我的偏好" :showBack="true" />

    <scroll-view scroll-y class="content">
      <text class="hint">这些偏好只作为 AI 的表达风格参考，不会影响对话内容记忆。</text>

      <!-- 已有词条 -->
      <view class="section">
        <view class="section__head">
          <text class="section__t">偏好词条</text>
          <text class="section__n">{{ keys.length }} 条</text>
        </view>

        <view v-if="keys.length" class="chips">
          <view v-for="k in keys" :key="k" class="chip" @click="editChip(k)">
            <text class="chip__k">{{ labelOf(k) }}</text>
            <text class="chip__v">{{ prefs[k] }}</text>
            <text class="chip__x" @click.stop="removeChip(k)">×</text>
          </view>
        </view>
        <text v-else class="empty">还没有偏好词条，可以在下面添加</text>
      </view>

      <!-- 添加 -->
      <view class="section">
        <text class="section__t">添加词条</text>
        <view class="add-row">
          <picker mode="selector" :range="keyLabels" :value="keyIndex" @change="onKeyChange">
            <view class="add-key">
              <text>{{ keyLabels[keyIndex] }}</text>
              <text class="add-key__chev">›</text>
            </view>
          </picker>
          <input v-model="newValue" class="add-input" placeholder="如：中文 / 简洁要点式" @confirm="addChip" />
          <view class="add-btn" @click="addChip">添加</view>
        </view>
      </view>

      <view class="save" @click="save">保存</view>
      <view class="safe-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAgentProfile, saveAgentProfile } from '@/api/agent'

/** 可选的偏好类别（与后端白名单一致） */
const KEY_OPTIONS = [
  { key: 'language', label: '语言' },
  { key: 'output_style', label: '输出风格' },
  { key: 'answer_length', label: '期望答复长度' },
  { key: 'tone', label: '语气' },
  { key: 'preferred_tools', label: '常用工具' },
  { key: 'industry', label: '所属行业' },
  { key: 'time_preference', label: '时间习惯' },
]

const prefs = ref({})
const keyIndex = ref(0)
const newValue = ref('')
const saving = ref(false)

const keyLabels = computed(() => KEY_OPTIONS.map(o => o.label))
const keys = computed(() => Object.keys(prefs.value))

const labelOf = (k) => {
  const hit = KEY_OPTIONS.find(o => o.key === k)
  return hit ? hit.label : k
}

const onKeyChange = (e) => {
  keyIndex.value = Number(e.detail.value) || 0
}

const addChip = () => {
  const v = String(newValue.value || '').trim()
  if (!v) {
    uni.showToast({ title: '请输入内容', icon: 'none' })
    return
  }
  const k = KEY_OPTIONS[keyIndex.value].key
  prefs.value = { ...prefs.value, [k]: v }
  newValue.value = ''
}

const editChip = (k) => {
  uni.showModal({
    title: labelOf(k),
    editable: true,
    placeholderText: '输入新的内容',
    content: prefs.value[k],
    success: (r) => {
      if (!r.confirm) return
      const v = String(r.content || '').trim()
      if (!v) return
      prefs.value = { ...prefs.value, [k]: v }
    }
  })
}

const removeChip = (k) => {
  const next = { ...prefs.value }
  delete next[k]
  prefs.value = next
}

const load = async () => {
  try {
    const res = await getAgentProfile()
    prefs.value = (res && res.data && res.data.preferences) || {}
  } catch (e) {
    prefs.value = {}
  }
}

const save = async () => {
  if (saving.value) return
  saving.value = true
  try {
    const res = await saveAgentProfile(prefs.value)
    if (res && res.code === 200) {
      uni.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 600)
    } else {
      uni.showToast({ title: (res && res.message) || '保存失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

load()
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}
.content { flex: 1; padding: 0 32rpx; }
.hint {
  display: block;
  margin: 8rpx 0 24rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.section {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.section__head { display: flex; align-items: center; justify-content: space-between; }
.section__t { font-size: 28rpx; font-weight: 700; color: var(--text-primary); }
.section__n { font-size: 22rpx; color: var(--text-tertiary); }
.chips { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 18rpx; }
.chip {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: var(--bg-gray);
  max-width: 100%;
}
.chip__k { font-size: 20rpx; color: var(--text-tertiary); }
.chip__v {
  font-size: 24rpx;
  color: var(--text-primary);
  max-width: 320rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.chip__x { font-size: 26rpx; line-height: 1; color: var(--text-tertiary); }
.empty { display: block; margin-top: 18rpx; font-size: 24rpx; color: var(--text-tertiary); }
.add-row { display: flex; align-items: center; gap: 14rpx; margin-top: 18rpx; }
.add-key {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 16rpx 20rpx;
  border-radius: 14rpx;
  background: var(--bg-gray);
  font-size: 24rpx;
  color: var(--text-primary);
  white-space: nowrap;
}
.add-key__chev { color: var(--text-tertiary); }
.add-input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: 14rpx;
  background: var(--bg-gray);
  font-size: 24rpx;
  color: var(--text-primary);
}
.add-btn {
  flex-shrink: 0;
  padding: 16rpx 28rpx;
  border-radius: 14rpx;
  background: var(--brand-primary);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}
.save {
  margin: 16rpx 0 24rpx;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  border-radius: 999rpx;
  background: var(--brand-primary);
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
}
.safe-bottom { height: 40rpx; }
</style>
