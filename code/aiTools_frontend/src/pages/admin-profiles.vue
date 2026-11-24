<template>
  <view class="page">
    <page-header title="用户画像词条" :showBack="true" />

    <view class="ap-tip">画像只用于调整 AI 的表达风格，不会影响对话内容。仅管理员可查看与修改。</view>

    <view class="ap-search">
      <input v-model="keyword" class="ap-search__input" placeholder="搜索账号或昵称" @confirm="load" />
      <view class="ap-search__btn" @click="load">搜索</view>
    </view>

    <scroll-view scroll-y class="ap-list">
      <view v-if="loading" class="ap-empty">加载中…</view>
      <view v-else-if="!rows.length" class="ap-empty">没有匹配的用户</view>

      <view v-for="u in rows" :key="u.userId" class="ap-card">
        <view class="ap-card__head" @click="toggle(u.userId)">
          <view class="ap-card__who">
            <text class="ap-card__name">{{ u.username || '未命名' }}</text>
            <text class="ap-card__acc">@{{ u.account }}</text>
            <text v-if="u.role === 'admin'" class="ap-card__badge">管理员</text>
          </view>
          <text class="ap-card__chev">{{ openId === u.userId ? '收起' : '展开' }}</text>
        </view>

        <view v-if="openId === u.userId" class="ap-card__body">
          <text class="ap-label">词条（{{ chipList(u).length }} 条）</text>
          <view v-if="chipList(u).length" class="ap-chips">
            <view v-for="c in chipList(u)" :key="c.k" class="ap-chip">
              <text class="ap-chip__k">{{ labelOf(c.k) }}</text>
              <input class="ap-chip__in" v-model="c.v" placeholder="空值表示删除" />
              <text class="ap-chip__x" @click="removeChip(u, c.k)">×</text>
            </view>
          </view>
          <text v-else class="ap-none">暂无词条</text>

          <view class="ap-add">
            <input v-model="drafts[u.userId]" class="ap-add__in" placeholder="新增词条，如：语气=简洁" />
            <view class="ap-add__btn" @click="addChip(u)">添加</view>
          </view>

          <text class="ap-label">画像摘要</text>
          <textarea v-model="texts[u.userId]" class="ap-text" placeholder="画像摘要（可留空）" />

          <view class="ap-save" @click="save(u)">保存</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import { listAdminProfiles, updateAdminProfile } from '@/api/admin'
import { isAdmin } from '@/utils/auth'

const KEY_LABELS = {
  output_style: '输出风格',
  answer_length: '回答长度',
  tone: '语气',
  language: '语言',
  format: '格式偏好',
  tool_pref: '工具习惯'
}
const labelOf = (k) => KEY_LABELS[k] || k

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const openId = ref(null)
/** 每个用户的编辑态（展开时初始化，避免直接改列表数据） */
const edit = reactive({})
const drafts = reactive({})
const texts = reactive({})

const chipList = (u) => (edit[u.userId] ? edit[u.userId].list : [])

const initEdit = (u) => {
  const prefs = u.preferences && typeof u.preferences === 'object' ? u.preferences : {}
  edit[u.userId] = { list: Object.keys(prefs).map((k) => ({ k, v: String(prefs[k]) })) }
  texts[u.userId] = u.profileText || ''
}

const toggle = (userId) => {
  if (openId.value === userId) {
    openId.value = null
    return
  }
  const u = rows.value.find((x) => x.userId === userId)
  if (u && !edit[userId]) initEdit(u)
  openId.value = userId
}

const removeChip = (u, k) => {
  edit[u.userId].list = edit[u.userId].list.filter((c) => c.k !== k)
}

const addChip = (u) => {
  const raw = (drafts[u.userId] || '').trim()
  if (!raw) return
  const i = raw.indexOf('=')
  if (i <= 0) {
    uni.showToast({ title: '格式：键=值', icon: 'none' })
    return
  }
  const k = raw.slice(0, i).trim()
  const v = raw.slice(i + 1).trim()
  const list = edit[u.userId].list.filter((c) => c.k !== k)
  list.push({ k, v })
  edit[u.userId].list = list
  drafts[u.userId] = ''
}

const save = async (u) => {
  const prefs = {}
  for (const c of edit[u.userId].list) {
    if (c.v !== '' && c.v != null) prefs[c.k] = c.v
  }
  try {
    await updateAdminProfile(u.userId, { profileText: texts[u.userId] || '', preferences: prefs })
    u.preferences = prefs
    u.profileText = texts[u.userId] || ''
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await listAdminProfiles(keyword.value.trim())
    rows.value = res.data || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

onLoad(() => {
  if (!isAdmin()) {
    uni.showToast({ title: '仅管理员可访问', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
    return
  }
  load()
})
</script>

<style scoped>
.page { min-height: 100vh; background: var(--bg-page); display: flex; flex-direction: column; }
.ap-tip { padding: 20rpx 32rpx; font-size: 22rpx; color: var(--text-tertiary); line-height: 1.6; }
.ap-search { display: flex; gap: 16rpx; padding: 0 32rpx 16rpx; }
.ap-search__input { flex: 1; height: 72rpx; padding: 0 24rpx; border-radius: 16rpx; background: var(--bg-card); font-size: 26rpx; color: var(--text-primary); }
.ap-search__btn { padding: 0 28rpx; height: 72rpx; line-height: 72rpx; border-radius: 16rpx; background: var(--brand-primary); color: #fff; font-size: 26rpx; }
.ap-list { flex: 1; padding: 0 32rpx 40rpx; box-sizing: border-box; }
.ap-empty { padding: 80rpx 0; text-align: center; color: var(--text-tertiary); font-size: 26rpx; }
.ap-card { background: var(--bg-card); border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.ap-card__head { display: flex; align-items: center; justify-content: space-between; }
.ap-card__who { display: flex; align-items: center; gap: 10rpx; }
.ap-card__name { font-size: 28rpx; color: var(--text-primary); font-weight: 600; }
.ap-card__acc { font-size: 22rpx; color: var(--text-tertiary); }
.ap-card__badge { font-size: 18rpx; padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--brand-primary); color: #fff; }
.ap-card__chev { font-size: 22rpx; color: var(--brand-primary); }
.ap-card__body { margin-top: 20rpx; }
.ap-label { display: block; margin: 18rpx 0 10rpx; font-size: 22rpx; color: var(--text-secondary); }
.ap-chips { display: flex; flex-direction: column; gap: 12rpx; }
.ap-chip { display: flex; align-items: center; gap: 12rpx; background: var(--bg-gray); border-radius: 12rpx; padding: 10rpx 16rpx; }
.ap-chip__k { font-size: 22rpx; color: var(--text-secondary); width: 150rpx; }
.ap-chip__in { flex: 1; font-size: 24rpx; color: var(--text-primary); }
.ap-chip__x { font-size: 28rpx; color: var(--text-tertiary); padding: 0 8rpx; }
.ap-none { font-size: 22rpx; color: var(--text-tertiary); }
.ap-add { display: flex; gap: 12rpx; margin-top: 16rpx; }
.ap-add__in { flex: 1; height: 68rpx; padding: 0 20rpx; border-radius: 12rpx; background: var(--bg-gray); font-size: 24rpx; }
.ap-add__btn { padding: 0 24rpx; height: 68rpx; line-height: 68rpx; border-radius: 12rpx; border: 1rpx solid var(--brand-primary); color: var(--brand-primary); font-size: 24rpx; }
.ap-text { width: 100%; min-height: 140rpx; box-sizing: border-box; padding: 18rpx; border-radius: 12rpx; background: var(--bg-gray); font-size: 24rpx; color: var(--text-primary); }
.ap-save { margin-top: 24rpx; height: 80rpx; line-height: 80rpx; text-align: center; border-radius: 16rpx; background: var(--brand-primary); color: #fff; font-size: 28rpx; }
</style>
