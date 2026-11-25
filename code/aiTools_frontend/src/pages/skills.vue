<template>
  <view class="page">
    <view class="topbar">
      <view class="topbar__row">
        <view class="topbar__back" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
        </view>
        <text class="topbar__title">技能管理</text>
      </view>
      <text class="topbar__desc">维护智能体的技能流程，发布后立即生效</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 列表 -->
      <block v-if="!detail">
        <view v-for="s in list" :key="s.code" class="card" @click="open(s.code)">
          <view class="card__ico">📄</view>
          <view class="card__body">
            <text class="card__t">{{ s.title }}</text>
            <text class="card__s">v{{ s.version }} · {{ s.code }}</text>
            <text class="card__w" v-if="s.when">{{ s.when }}</text>
          </view>
          <text class="card__chev">›</text>
        </view>
        <view v-if="!list.length" class="empty">暂无技能</view>
      </block>

      <!-- 详情：线上 / 草稿 -->
      <block v-else>
        <view class="detail-head">
          <text class="detail-title">{{ detail.title }}</text>
          <text class="detail-ver">v{{ detail.version }}</text>
          <text v-if="detail.dirty" class="badge">有未发布修改</text>
        </view>

        <text class="col-label">线上版本（智能体正在使用）</text>
        <view class="pane pane--read">
          <MarkdownView :source="detail.publishedBody || '（空）'" />
        </view>

        <text class="col-label">草稿（可编辑）</text>
        <textarea v-model="draft" class="pane pane--edit" maxlength="-1" />

        <view class="acts acts--wrap">
          <view class="btn btn--ghost" @click="doTry">试用草稿</view>
          <view class="btn btn--ghost" @click="doDiscard" v-if="detail.dirty">放弃修改</view>
          <view class="btn btn--ghost" @click="doReimport">从文件导入</view>
          <view class="btn btn--ghost" @click="saveDraft">保存草稿</view>
          <view class="btn btn--primary" @click="doPublish">发布</view>
        </view>

        <!-- 试用结果 -->
        <view v-if="tryResult" class="try-box">
          <text class="try-title">试用结果（未发布，不影响线上）</text>
          <text class="try-answer">{{ tryResult.answer }}</text>
          <view v-for="(f, i) in tryResult.files" :key="i" class="try-file" @click="downloadOutput({ url: f.url, name: f.name, kind: outputKindOf(f.name) })">
            <text class="try-file__n">{{ f.name }}</text>
            <text class="try-file__d">下载</text>
          </view>
        </view>
      </block>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import MarkdownView from '@/components/MarkdownView.vue'
import { listSkills, getSkill, saveSkillDraft, publishSkill, reimportSkill, discardSkill, trySkillDraft } from '@/api/skill'
import { downloadOutput, outputKindOf } from '@/utils/fileAction'
import { isAdmin } from '@/utils/auth'

const list = ref([])
const detail = ref(null)
const draft = ref('')
const tryResult = ref(null)

const goBack = () => (detail.value ? (detail.value = null) : uni.navigateBack())

const load = async () => {
  try {
    const res = await listSkills()
    list.value = res.data || []
  } catch (e) {
    list.value = []
  }
}

const open = async (code) => {
  try {
    const res = await getSkill(code)
    detail.value = res.data
    draft.value = res.data.draftBody || res.data.publishedBody || ''
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
}

const saveDraft = async () => {
  try {
    await saveSkillDraft(detail.value.code, draft.value)
    detail.value.dirty = true
    uni.showToast({ title: '草稿已保存', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  }
}

const doPublish = () => {
  uni.showModal({
    title: '发布技能',
    content: '发布后智能体立即使用新内容，确定？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await publishSkill(detail.value.code)
        detail.value.version = res.data.version
        detail.value.publishedBody = draft.value
        detail.value.dirty = false
        uni.showToast({ title: '已发布 v' + res.data.version, icon: 'none' })
        load()
      } catch (e) {
        uni.showToast({ title: '发布失败', icon: 'none' })
      }
    }
  })
}

const doTry = async () => {
  // 试用前先把当前编辑内容存成草稿，保证测的就是眼前这份
  try {
    await saveSkillDraft(detail.value.code, draft.value)
  } catch (e) { /* 保存失败也允许用已存草稿继续 */ }
  uni.showModal({
    title: '试用草稿',
    editable: true,
    placeholderText: '输入一句任务，例如：写一份项目介绍书',
    content: '写一份项目介绍书',
    success: async (r) => {
      if (!r.confirm) return
      uni.showLoading({ title: '试用运行中', mask: true })
      try {
        const res = await trySkillDraft(detail.value.code, r.content || '')
        tryResult.value = res.data
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '试用失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

const doDiscard = () => {
  uni.showModal({
    title: '放弃修改',
    content: '草稿将回滚为线上版本，未发布的修改会丢失。',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await discardSkill(detail.value.code)
        await open(detail.value.code)
        tryResult.value = null
        uni.showToast({ title: '已回滚', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '回滚失败', icon: 'none' })
      }
    }
  })
}

const doReimport = () => {
  uni.showModal({
    title: '从文件重新导入',
    content: '将用代码仓库里的 SKILL.md 覆盖当前草稿，确定？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await reimportSkill(detail.value.code)
        await open(detail.value.code)
        uni.showToast({ title: '已导入到草稿', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '导入失败', icon: 'none' })
      }
    }
  })
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
.topbar { padding: calc(var(--status-bar-height, 0px) + 22rpx) 24rpx 0; background: var(--bg-card, #fff); }
.topbar__row { display: flex; align-items: center; gap: 12rpx; }
.topbar__back { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; color: var(--text-primary); }
.topbar__back svg { width: 40rpx; height: 40rpx; }
.topbar__title { font-size: 36rpx; font-weight: 700; color: var(--text-primary); line-height: 44rpx; }
.topbar__desc { display: block; margin: 8rpx 0 22rpx 62rpx; font-size: 23rpx; color: var(--text-tertiary); }
.content { flex: 1; padding: 24rpx 0 60rpx; }

.card { margin: 0 32rpx 20rpx; padding: 24rpx; border-radius: 20rpx; background: var(--bg-card, #fff); display: flex; align-items: center; gap: 18rpx; }
.card__ico { width: 64rpx; height: 64rpx; border-radius: 16rpx; background: rgba(245,103,29,.1); display: flex; align-items: center; justify-content: center; font-size: 30rpx; }
.card__body { flex: 1; min-width: 0; }
.card__t { display: block; font-size: 28rpx; font-weight: 600; color: var(--text-primary); }
.card__s { display: block; margin-top: 4rpx; font-size: 21rpx; color: var(--text-tertiary); }
.card__w { display: block; margin-top: 8rpx; font-size: 22rpx; color: var(--text-secondary); }
.card__chev { color: var(--text-tertiary); font-size: 30rpx; }
.empty { padding: 80rpx 0; text-align: center; color: var(--text-tertiary); font-size: 26rpx; }

.detail-head { display: flex; align-items: center; gap: 14rpx; margin: 0 32rpx 20rpx; }
.detail-title { font-size: 32rpx; font-weight: 700; color: var(--text-primary); }
.detail-ver { font-size: 22rpx; color: var(--text-tertiary); }
.badge { margin-left: auto; font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 8rpx; background: rgba(245,103,29,.12); color: var(--brand-primary); }

.col-label { display: block; margin: 20rpx 32rpx 10rpx; font-size: 22rpx; color: var(--text-tertiary); }
.pane { margin: 0 32rpx; padding: 20rpx; border-radius: 16rpx; background: var(--bg-card, #fff); }
.pane--edit { width: auto; height: 520rpx; font-size: 24rpx; line-height: 1.6; color: var(--text-primary); font-family: Consolas, monospace; }

.acts { display: flex; gap: 14rpx; margin: 28rpx 32rpx 0; }
.btn { flex: 1; height: 84rpx; line-height: 84rpx; text-align: center; border-radius: 42rpx; font-size: 26rpx; }
.btn--ghost { background: var(--bg-card, #fff); border: 1rpx solid var(--border-color); color: var(--text-secondary); }
.btn--primary { background: var(--brand-primary); color: #fff; font-weight: 600; }
.acts--wrap { flex-wrap: wrap; }
.acts--wrap .btn { flex: 1 1 30%; font-size: 24rpx; }
.try-box { margin: 24rpx 32rpx 0; padding: 20rpx; border-radius: 16rpx; background: rgba(245,103,29,.06); }
.try-title { display: block; font-size: 22rpx; color: var(--brand-primary); margin-bottom: 10rpx; }
.try-answer { display: block; font-size: 23rpx; line-height: 1.7; color: var(--text-primary); white-space: pre-wrap; }
.try-file { display: flex; justify-content: space-between; margin-top: 12rpx; padding: 12rpx 16rpx; border-radius: 12rpx; background: #fff; }
.try-file__n { font-size: 23rpx; color: var(--text-primary); }
.try-file__d { font-size: 23rpx; color: var(--brand-primary); }
</style>
