<template>
  <view class="page">
    <view class="topbar">
      <view class="topbar__back" hover-class="topbar__back--hover" @click="goBack">
        <text class="topbar__back-icon">‹</text>
      </view>
      <text class="topbar__title">智能体记录</text>
    </view>

    <scroll-view scroll-y class="page-content">
      <!-- 空态 -->
      <view v-if="!loading && runs.length === 0" class="empty">
        <view class="empty__icon">
          <svg viewBox="0 0 24 24" style="width:72rpx;height:72rpx;fill:none;stroke:var(--text-tertiary);stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round">
            <circle cx="12" cy="12" r="9"/>
            <path d="M12 7v5l3.2 1.9"/>
          </svg>
        </view>
        <text class="empty__title">还没有运行记录</text>
        <text class="empty__desc">去智能体页跑一次，这里会留下完整过程</text>
      </view>

      <!-- 列表 -->
      <view v-for="r in runs" :key="r.runId" class="run-card" @click="openDetail(r)">
        <view class="run-card__head">
          <text class="run-card__status" :class="'run-card__status--' + r.status">{{ r.statusLabel }}</text>
          <text class="run-card__time">{{ r.createTime }}</text>
        </view>
        <text class="run-card__goal">{{ r.goal }}</text>
        <view class="run-card__meta">
          <text class="run-card__meta-item">{{ r.stepCount || 0 }} 步</text>
          <text class="run-card__meta-item">{{ Math.round((r.duration || 0) / 100) / 10 }}s</text>
        </view>
      </view>

      <!-- 详情（弹层） -->
      <view v-if="detail" class="detail-mask" @click="detail = null">
        <view class="detail" @click.stop>
          <scroll-view scroll-y class="detail__body">
            <text class="detail__label">目标</text>
            <text class="detail__goal">{{ detail.goal }}</text>

            <text class="detail__label">过程</text>
            <view v-for="(s, i) in detail.steps || []" :key="i" class="step">
              <view class="step__head">
                <text class="step__seq">{{ s.seq }}</text>
                <text class="step__tool">{{ s.tool }}</text>
                <text class="step__cost">{{ s.costMs }}ms</text>
              </view>
              <text v-if="s.thought" class="step__thought">{{ s.thought }}</text>
              <text v-if="s.args" class="step__args">参数：{{ s.args }}</text>
              <text v-if="s.output" class="step__output">{{ s.output }}</text>
            </view>

            <template v-if="detail.finalOutput">
              <text class="detail__label">最终答案</text>
              <MarkdownView class="detail__md" :source="detail.finalOutput" />
            </template>

            <text v-if="detail.errorMsg" class="detail__label">错误</text>
            <text v-if="detail.errorMsg" class="detail__error">{{ detail.errorMsg }}</text>
          </scroll-view>
          <view class="detail__close" @click="detail = null">关闭</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { listAgentRuns, getAgentRun } from '@/api/agent'
import { safeBack } from '@/utils/pageTransition'

const runs = ref([])
const loading = ref(false)
const detail = ref(null)

const goBack = () => {
  safeBack('/pages/index')
}

const load = async () => {
  loading.value = true
  try {
    const res = await listAgentRuns(30)
    runs.value = (res && res.data) || []
  } catch (e) {
    runs.value = []
  } finally {
    loading.value = false
  }
}

const openDetail = async (r) => {
  try {
    const res = await getAgentRun(r.runId)
    if (res && res.data) {
      detail.value = res.data
    }
  } catch (e) {
    uni.showToast({ title: '读取详情失败', icon: 'none' })
  }
}

load()
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
}

.topbar {
  padding: var(--status-bar-height, 0px) 24rpx 0;
  height: 88rpx;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid var(--border-color);

  &__back {
    width: 56rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    &-icon { font-size: 44rpx; color: var(--text-secondary); line-height: 1; }
    &--hover { opacity: 0.6; }
  }
  &__title {
    flex: 1;
    text-align: center;
    font-size: 32rpx;
    font-weight: 600;
    color: var(--text-primary);
  }
  &::after { content: ''; width: 56rpx; }
}

.page-content { flex: 1; padding: 24rpx 32rpx 64rpx; }

.empty {
  text-align: center;
  padding: 120rpx 0;

  &__title { display: block; font-size: 30rpx; color: var(--text-secondary); }
  &__desc { display: block; font-size: 24rpx; color: var(--text-tertiary); margin-top: 12rpx; }
}

.run-card {
  background: var(--bg-card);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;
  }
  &__status {
    font-size: 22rpx;
    color: var(--brand-primary);
    background: var(--brand-primary-light);
    padding: 4rpx 14rpx;
    border-radius: 999rpx;

    &--2 { color: #059669; background: #ECFDF5; }
    &--3 { color: var(--color-danger); background: var(--color-danger-bg); }
    &--4 { color: var(--text-secondary); background: var(--bg-gray); }
  }
  &__time { font-size: 22rpx; color: var(--text-tertiary); }
  &__goal {
    display: block;
    font-size: 27rpx;
    color: var(--text-secondary);
    line-height: 1.6;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  &__meta { display: flex; margin-top: 12rpx; }
  &__meta-item { font-size: 22rpx; color: var(--text-tertiary); margin-right: 24rpx; }
}

/* ============ 详情弹层 ============ */
.detail-mask {
  position: fixed;
  left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(17, 24, 39, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}
.detail {
  width: 100%;
  max-height: 82vh;
  background: var(--bg-card);
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;

  &__body { flex: 1; padding: 32rpx; }
  &__label {
    display: block;
    font-size: 24rpx;
    color: var(--brand-primary);
    margin: 24rpx 0 12rpx;
  }
  &__goal { font-size: 27rpx; color: var(--text-secondary); line-height: 1.6; }
  &__md { font-size: 27rpx; }
  &__error { font-size: 25rpx; color: var(--color-danger); line-height: 1.6; }
  &__close {
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    font-size: 29rpx;
    color: var(--brand-primary);
    border-top: 1rpx solid var(--border-color);
  }
}

.step {
  background: var(--bg-page);
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 12rpx;

  &__head { display: flex; align-items: center; margin-bottom: 8rpx; }
  &__seq {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50%;
    background: var(--brand-primary);
    color: #FFFFFF;
    font-size: 20rpx;
    text-align: center;
    line-height: 36rpx;
    margin-right: 12rpx;
  }
  &__tool { flex: 1; font-size: 25rpx; color: var(--text-secondary); font-weight: 600; }
  &__cost { font-size: 22rpx; color: var(--text-tertiary); }
  &__thought { display: block; font-size: 23rpx; color: var(--text-secondary); line-height: 1.6; }
  &__args { display: block; font-size: 22rpx; color: var(--text-tertiary); margin-top: 6rpx; }
  &__output {
    display: block;
    font-size: 23rpx;
    color: var(--text-secondary);
    line-height: 1.6;
    margin-top: 8rpx;
    white-space: pre-wrap;
    max-height: 300rpx;
    overflow: hidden;
  }
}
.empty__icon {
  width: 176rpx;
  height: 176rpx;
  border-radius: 32rpx;
  background: var(--bg-gray);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 28rpx;
}
</style>
