<template>
  <view class="page page--no-tabbar">
    <!-- ============ 顶栏 ============ -->
    <view class="topbar">
      <view class="topbar__back" hover-class="topbar__back--hover" @click="goBack">
        <text class="topbar__back-icon">‹</text>
      </view>
      <text class="topbar__title">图片背景去除</text>
    </view>

    <scroll-view scroll-y class="page-content" @touchmove.stop.prevent="onNoop">

      <!-- ============ 状态 A：上传 + 去背景按钮（仅去背景） ============ -->
      <view v-if="state === 'A'" class="state-a">
        <!-- 上传卡片 -->
        <view v-if="!uploadedImage" class="upload-card" @click="onPickImage">
          <view class="upload-card__icon">
            <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
          </view>
          <text class="upload-card__title">点击上传图片</text>
          <text class="upload-card__desc">支持 JPG、PNG 格式</text>
        </view>

        <!-- 已上传预览 -->
        <view v-else class="uploaded-preview">
          <image
            class="uploaded-preview__img"
            :src="uploadedImage"
            mode="aspectFit"
            @click="previewOriginal"
          />
          <view class="uploaded-preview__bar">
            <text class="uploaded-preview__name">{{ uploadedFileName }}</text>
            <view class="uploaded-preview__reupload" @click="onPickImage">重新上传</view>
          </view>
        </view>

        <!-- 唯一按钮：去背景 -->
        <button
          class="btn-primary"
          :disabled="!uploadedImage"
          @click="onStartRemove"
        >去背景</button>
        <text class="btn-hint">一键抠出透明 PNG</text>
      </view>

      <!-- ============ 状态 B：处理中（进度条） ============ -->
      <view v-else-if="state === 'B'" class="state-b">
        <view class="processing-card">
          <view class="processing-card__thumb">
            <image
              v-if="uploadedImage"
              :src="uploadedImage"
              mode="aspectFit"
              class="processing-card__img"
            />
          </view>

          <view class="processing-card__progress">
            <view class="progress-track">
              <view class="progress-fill" :style="{ width: progress + '%' }"></view>
            </view>
            <text class="progress-text">{{ progress }}%</text>
          </view>

          <text class="processing-card__title">正在去背景</text>
          <text class="processing-card__desc">图片较大时需要一些时间，请耐心等待</text>
        </view>
      </view>

      <!-- ============ 状态 C：处理完成 + 实时预览（换底色 / 换背景图） ============ -->
      <view v-else class="state-c">
        <!-- 实时预览：左原图 / 右合成结果 -->
        <view class="compare-preview">
          <view class="preview-box">
            <text class="preview-box__label">原图</text>
            <view class="preview-box__canvas">
              <image :src="uploadedImage" mode="aspectFit" class="preview-box__img" />
            </view>
          </view>

          <view class="preview-box">
            <text class="preview-box__label">结果（实时预览）</text>
            <!-- 实时合成：背景层 + 物体层（可拖动 + 可缩放） -->
            <view
              class="preview-box__canvas preview-box__canvas--compose"
              :class="{ 'preview-box__canvas--checker': !currentBgColor && !bgImage }"
            >
              <!-- 背景层（不动） -->
              <view class="bg-layer" :style="composeStyle"></view>
              <!-- 物体层（可拖动 + 可缩放） -->
              <view
                class="object-layer"
                :style="objectTransformStyle"
                @touchstart.stop="onTouchStart"
                @touchmove.stop.prevent="onTouchMove"
                @touchend.stop="onTouchEnd"
                @touchcancel.stop="onTouchEnd"
                @mousedown.stop="onMouseDown"
                @mousemove.stop.prevent="onMouseMove"
                @mouseup.stop="onMouseEnd"
                @mouseleave.stop="onMouseEnd"
              >
                <image
                  v-if="transparentPng"
                  :src="transparentPng"
                  mode="aspectFit"
                  class="object-layer__img"
                  :draggable="false"
                />
                <view v-else class="preview-box__placeholder">
                  <text>暂无合成结果</text>
                  <text class="preview-box__placeholder-sub">后端产物暂未回传</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 物体调整面板（仅当有透明 PNG 时） -->
        <view v-if="transparentPng" class="object-controls">
          <view class="object-controls__row">
            <text class="control-label">调整大小</text>
            <text class="control-value">{{ Math.round(objectScale * 100) }}%</text>
          </view>
          <slider
            :value="objectScale * 100"
            min="10"
            max="300"
            :step="1"
            :block-size="20"
            activeColor="#3B82F6"
            backgroundColor="#E5E7EB"
            @changing="onScaleSliderChange"
            @change="onScaleSliderChange"
            @input="onScaleSliderChange"
            class="object-controls__slider"
          />
          <view class="object-controls__btns">
            <button class="btn-mini" @click="resetObject">重置</button>
            <button class="btn-mini btn-mini--active" @click="centerObject">居中</button>
          </view>
        </view>

        <!-- 当前状态指示 -->
        <view class="status-bar">
          <view class="status-dot"></view>
          <text class="status-text">
            <template v-if="bgImage">已叠加自定义背景图</template>
            <template v-else-if="currentBgColor">已切换底色</template>
            <template v-else>透明 PNG（未叠加）</template>
          </text>
          <text v-if="bgImage || currentBgColor" class="status-clear" @click="onClearOverlay">清除</text>
        </view>

        <!-- 换底色：12 色，CSS 叠图层，实时切换 -->
        <view class="section">
          <text class="section-title">换底色</text>
          <view class="color-palette">
            <view
              v-for="c in bgColors"
              :key="c.value"
              class="color-chip"
              :class="{ 'color-chip--active': currentBgColor === c.value }"
              :style="{ backgroundColor: c.value }"
              @click="onSelectBgColor(c.value)"
            >
              <text v-if="c.value === 'transparent'" class="color-chip__label">无</text>
            </view>
          </view>
        </view>

        <!-- 换背景图：上传图片，实时叠加 -->
        <view class="section">
          <text class="section-title">换背景图</text>
          <view class="bg-actions">
            <view class="bg-upload-btn" @click="onUploadBg">
              <text class="bg-upload-btn__plus">+</text>
              <text class="bg-upload-btn__text">{{ bgImage ? '更换背景图' : '上传背景图' }}</text>
            </view>
            <view v-if="bgImage" class="bg-thumb" @click="onUploadBg">
              <image :src="bgImage" mode="aspectFill" class="bg-thumb__img" />
            </view>
          </view>
        </view>

        <!-- 操作按钮 -->
        <view class="bottom-actions">
          <button class="btn-secondary" @click="onDownload">下载图片</button>
          <button class="btn-primary" @click="onReset">重新处理</button>
        </view>
      </view>

    </scroll-view>
  </view>
</template>

<script>
import { imageBgRemover, pollBatchCompleted } from '@/api/image-bg-remover'

export default {
  data() {
    return {
      // ===== 3 状态机 =====
      // A: 上传 + 「去背景」按钮（换底色/换背景图 不显示）
      // B: 处理中（进度条 + 「正在去背景」+ 「请稍候」）
      // C: 处理完成 + 实时预览（换底色/换背景图 显示）
      state: 'A',

      // 状态 A 数据
      uploadedImage: null,    // 原图本地路径（H5：blob URL；小程序/APP：tempFilePaths[0]）
      uploadedFileName: '',   // 文件名

      // 状态 B 数据
      progress: 0,
      pollingTimer: null,
      progressTimer: null,

      // 状态 C 数据
      batchId: null,
      transparentPng: null,   // 后端回传的透明 PNG URL（暂未在 batchCompleted 里回传，前端做空态处理）
      currentBgColor: '',     // 当前选中的底色
      bgImage: null,          // 当前上传的背景图本地路径

      // 物体调整（D2：拖动 + 缩放）
      objectX: 0,             // 水平偏移 (rpx)
      objectY: 0,             // 垂直偏移 (rpx)
      objectScale: 1,         // 缩放比例 (0.1 - 3.0)

      // 手势状态
      isDragging: false,
      dragStartX: 0,
      dragStartY: 0,
      objectStartX: 0,
      objectStartY: 0,

      // 12 色 + 透明选项
      bgColors: [
        { value: 'transparent' },
        { value: '#FFFFFF' },
        { value: '#000000' },
        { value: '#7C3AED' },
        { value: '#3B82F6' },
        { value: '#10B981' },
        { value: '#F59E0B' },
        { value: '#EC4899' },
        { value: '#6B7280' },
        { value: '#EF4444' },
        { value: '#14B8A6' },
        { value: '#A855F7' }
      ]
    }
  },

  computed: {
    /**
     * 实时合成样式：底色 + 背景图（CSS 叠图层，**只叠图层不上传**）
     * 用户原话：「最好是提供实时预览的功能，这个只是叠图层」
     */
    composeStyle() {
      const style = {}
      if (this.currentBgColor && this.currentBgColor !== 'transparent') {
        style.backgroundColor = this.currentBgColor
      } else {
        style.backgroundColor = 'transparent'
      }
      if (this.bgImage) {
        style.backgroundImage = `url(${this.bgImage})`
        style.backgroundSize = 'cover'
        style.backgroundPosition = 'center'
        style.backgroundRepeat = 'no-repeat'
      }
      return style
    },
    /**
     * 物体层 transform：translate + scale
     * ⚠️ H5 端 CSS transform 只接受合法长度单位（px/em/rem/vw/vh 等），
     *    `rpx` 是 uni-app 扩展单位，浏览器不认识，rpx 写在 CSS 字符串里
     *    会导致整条 transform 被忽略（拖动无反应）。
     * 解决：data 仍以 rpx 记录（与画布合成保持一致），但输出 CSS 时用
     *    uni.upx2px() 转为 px。
     */
    objectTransformStyle() {
      const xPx = uni.upx2px ? uni.upx2px(this.objectX) : this.objectX
      const yPx = uni.upx2px ? uni.upx2px(this.objectY) : this.objectY
      return `transform: translate(${xPx}px, ${yPx}px) scale(${this.objectScale});`
    }
  },

  onUnload() {
    this._clearTimers()
  },

  methods: {
    goBack() {
      uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/index' }) })
    },

    // ==================== 状态 A：上传 ====================
    onPickImage() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          const path = (res.tempFilePaths || [])[0]
          if (!path) return
          const temp = (res.tempFiles || [])[0]
          this.uploadedImage = path
          this.uploadedFileName = (temp && temp.name) || path.split('/').pop() || 'image.png'
        },
        fail: () => {
          uni.showToast({ title: '未选择图片', icon: 'none' })
        }
      })
    },

    previewOriginal() {
      if (!this.uploadedImage) return
      uni.previewImage({ urls: [this.uploadedImage], current: this.uploadedImage })
    },

    // ==================== 状态 A → B：触发去背景 ====================
    async onStartRemove() {
      if (!this.uploadedImage) {
        uni.showToast({ title: '请先上传图片', icon: 'none' })
        return
      }

      this.state = 'B'
      this.progress = 0

      // 视觉进度（丝滑增长到 90%，给用户「处理中」的真实感）
      this.progressTimer = setInterval(() => {
        if (this.progress < 90) {
          this.progress += 5
        }
      }, 400)

      try {
        // 1) 提交任务：mode=transparent（一键去背景 → 透明 PNG）
        const res = await imageBgRemover(this.uploadedImage, { mode: 'transparent' })
        const data = (res && res.data) || null
        if (!res || res.code !== 200 || !data || !data.batchId) {
          throw new Error((res && res.message) || '提交任务失败')
        }
        this.batchId = data.batchId

        // 2) 轮询 batch 状态
        await this._pollBatch(this.batchId)
      } catch (err) {
        console.error('[image-bg-remover] 去背景失败:', err)
        uni.showToast({ title: (err && err.message) || '处理失败', icon: 'none' })
        this._clearTimers()
        this.state = 'A'
        this.progress = 0
      }
    },

    /**
     * 轮询 /api/ai-office/batch/{batchId}/completed
     * 状态码：0=待处理 1=处理中 2=已完成 3=部分失败 4=全部失败
     */
    _pollBatch(batchId) {
      return new Promise((resolve, reject) => {
        let since = 0
        const tick = async () => {
          try {
            const res = await pollBatchCompleted(batchId, since)
            const data = (res && res.data) || null
            if (!res || res.code !== 200 || !data) {
              throw new Error((res && res.message) || '查询任务状态失败')
            }

            // 累计进度：已处理数 / 总数 * 90
            if (data.fileCount && data.processedIndex != null) {
              const ratio = Math.min(data.processedIndex / data.fileCount, 1)
              this.progress = Math.max(this.progress, Math.floor(ratio * 90))
            }

            // 推进 since（增量拉取）
            if (Array.isArray(data.results)) {
              since += data.results.length
            }

            // 终态判断
            if (data.status === 2) {
              this._onBatchCompleted(data)
              resolve()
            } else if (data.status === 3 || data.status === 4) {
              const ok = data.successCount || 0
              const total = data.fileCount || 1
              if (ok > 0) {
                // 部分成功：仍按完成处理
                this._onBatchCompleted(data)
                resolve()
              } else {
                throw new Error('处理失败，请重试')
              }
            } else {
              // 0 / 1：继续轮询
              this.pollingTimer = setTimeout(tick, 1200)
            }
          } catch (err) {
            reject(err)
          }
        }
        tick()
      })
    },

    _onBatchCompleted(data) {
      this._clearTimers()
      this.progress = 100

      // image-bg-remover 当前 resultSummary 写死为 "[]"，fileUrl 不会出现在 results 里；
      // 产物落库到 sys_tool_output（toolCode='image-bg-remover'）但暂无按 batchId 查 fileUrl 的接口。
      // 兼容解析：若 results 里有 fileUrl / output 字段则使用；否则 transparentPng 留空，
      // 状态 C 仍可进入（实时预览显示「暂无合成结果」+ 提示后端补全接口）。
      const results = (data && data.results) || []
      const first = results[0] || null

      let url = ''
      if (first) {
        url = first.fileUrl || first.output || first.url || ''
      }

      this.transparentPng = url

      if (!url) {
        console.warn('[image-bg-remover] batch 已完成但 fileUrl 为空')
      }

      // 给一个短暂的 100% 视觉停留，再切到状态 C
      setTimeout(() => {
        this.state = 'C'
        uni.showToast({ title: '去背景完成', icon: 'success' })
      }, 300)
    },

    _clearTimers() {
      if (this.pollingTimer) {
        clearTimeout(this.pollingTimer)
        this.pollingTimer = null
      }
      if (this.progressTimer) {
        clearInterval(this.progressTimer)
        this.progressTimer = null
      }
    },

    // 空操作：scroll-view 上拦截 touchmove，让拖动手势不被滚动抢走
    onNoop() {},

    // ==================== 状态 C：实时预览 ====================
    onSelectBgColor(color) {
      // 选「无（transparent）」表示清除底色
      if (color === 'transparent') {
        this.currentBgColor = ''
      } else {
        this.currentBgColor = color
      }
      // 切底色时清掉背景图（同时只能一个生效，UX 更清晰）
      this.bgImage = null
    },

    onUploadBg() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          const path = (res.tempFilePaths || [])[0]
          if (!path) return
          this.bgImage = path
          this.currentBgColor = ''
        },
        fail: () => {
          uni.showToast({ title: '未选择图片', icon: 'none' })
        }
      })
    },

    onClearOverlay() {
      this.currentBgColor = ''
      this.bgImage = null
    },

    // ==================== 状态 C：物体调整（D2） ====================
    onTouchStart(e) {
      if (!e || !e.touches || e.touches.length !== 1) return
      this.isDragging = true
      const touch = e.touches[0]
      this.dragStartX = touch.clientX
      this.dragStartY = touch.clientY
      this.objectStartX = this.objectX
      this.objectStartY = this.objectY
    },

    onTouchMove(e) {
      if (!this.isDragging || !e || !e.touches || e.touches.length !== 1) return
      const touch = e.touches[0]
      const dx = touch.clientX - this.dragStartX
      const dy = touch.clientY - this.dragStartY
      // rpx ≈ px × 2（uni-app 750rpx 设计稿下）
      this.objectX = this.objectStartX + dx * 2
      this.objectY = this.objectStartY + dy * 2
    },

    onTouchEnd() {
      this.isDragging = false
    },

    // H5 桌面端 fallback：uni-app 编译到 H5 时，鼠标拖动不会触发 touch* 事件
    onMouseDown(e) {
      this.isDragging = true
      this.dragStartX = e.clientX
      this.dragStartY = e.clientY
      this.objectStartX = this.objectX
      this.objectStartY = this.objectY
    },

    onMouseMove(e) {
      if (!this.isDragging) return
      const dx = e.clientX - this.dragStartX
      const dy = e.clientY - this.dragStartY
      this.objectX = this.objectStartX + dx * 2
      this.objectY = this.objectStartY + dy * 2
    },

    onMouseEnd() {
      this.isDragging = false
    },

    onScaleSliderChange(e) {
      // uni-app slider @change/@changing 事件 detail 形如 { value: number }
      // H5 端偶尔会回传原生事件（e.target.value），做一次兜底
      const raw = (e && e.detail && e.detail.value != null)
        ? e.detail.value
        : (e && e.target && e.target.value != null ? e.target.value : null)
      const v = raw != null ? Number(raw) : 100
      this.objectScale = Math.max(0.1, Math.min(3.0, v / 100))
    },

    resetObject() {
      this.objectX = 0
      this.objectY = 0
      this.objectScale = 1
    },

    centerObject() {
      this.objectX = 0
      this.objectY = 0
    },

    onDownload() {
      if (!this.transparentPng) {
        uni.showToast({
          title: '后端暂未回传产物 URL，请等待接口补全',
          icon: 'none',
          duration: 2500
        })
        return
      }
      uni.showLoading({ title: '正在合成...', mask: true })
      // D3：用 canvas 把 transparentPng + 底色/背景图 合成一张图，再下载
      this.compositeOnCanvas()
        .then((dataUrl) => {
          // #ifdef H5
          const a = document.createElement('a')
          a.href = dataUrl
          a.download = (this.uploadedFileName && this.uploadedFileName.replace(/\.[^.]+$/, '')) || 'image-bg-remover'
          a.download += `-${Date.now()}.png`
          a.target = '_blank'
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          // #endif
          // #ifndef H5
          // 非 H5 端：直接把 transparentPng 走 uni.downloadFile 触发系统下载/保存
          uni.downloadFile({
            url: this.transparentPng,
            success: (res) => {
              if (res.statusCode === 200) {
                uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
              } else {
                uni.showToast({ title: '下载失败', icon: 'none' })
              }
            },
            fail: () => uni.showToast({ title: '下载失败', icon: 'none' })
          })
          // #endif
          uni.hideLoading()
          uni.showToast({ title: '下载完成', icon: 'success' })
        })
        .catch((err) => {
          console.error('[image-bg-remover] canvas 合成失败:', err)
          uni.hideLoading()
          // 关键：catch 不能静默调用 _fallbackDownload 下载 transparentPng，
          // 否则用户拿到的是「去背景后的透明 PNG」而非「合成后的工作图」，
          // 与本次需求直接冲突。改为提示后由用户决定是否保存原图。
          uni.showModal({
            title: '合成失败',
            content: '图片合成失败：' + ((err && err.message) || '未知错误') + '。是否仍下载原透明 PNG？',
            confirmText: '下载原图',
            cancelText: '取消',
            success: (res) => {
              if (res.confirm) this._fallbackDownload()
            }
          })
        })
    },

    /**
     * D3：把 transparentPng + 底色/背景图 在 canvas 上合成一张 PNG dataURL
     * 仅 H5 走完整合成（H5 是主要预览目标）；小程序/APP 兜底走原图下载。
     */
    compositeOnCanvas() {
      // #ifdef H5
      return (async () => {
        // 1) 把 transparentPng 先拉成同源 blob，绕开 OSS CORS（tainted canvas）
        const localImgUrl = await this._toLocalImageUrl(this.transparentPng)
        const objImg = await this._loadImage(localImgUrl)

        // 2) 画布尺寸 = transparentPng 原始尺寸（保证输出与原图比例一致，
        //    不随 bgImage 变化）。这是修复「下载图高度不一致」的核心：
        //    之前取 bgImage 尺寸 → 输出比例被 bgImage 决定 → 跨端不一致。
        const objNatW = objImg.naturalWidth || objImg.width || 800
        const objNatH = objImg.naturalHeight || objImg.height || 800
        const W = objNatW
        const H = objNatH

        // 3) 背景图（如有）也拉成本地 URL（仅用于叠加，不影响画布尺寸）
        let bgImgEl = null
        if (this.bgImage) {
          const localBgUrl = await this._toLocalImageUrl(this.bgImage)
          bgImgEl = await this._loadImage(localBgUrl)
        }

        const canvas = document.createElement('canvas')
        canvas.width = W
        canvas.height = H
        const ctx = canvas.getContext('2d')
        if (!ctx) throw new Error('canvas 2d 上下文创建失败')

        // 4) 画背景
        if (this.currentBgColor && this.currentBgColor !== 'transparent') {
          ctx.fillStyle = this.currentBgColor
          ctx.fillRect(0, 0, W, H)
        } else if (bgImgEl && bgImgEl.naturalWidth && bgImgEl.naturalHeight) {
          // 背景图 aspectFit (contain) 居中绘制，保持原比例（之前是
          // drawImage(bgImgEl, 0, 0, W, H) 直接拉伸 → 与预览 backgroundSize:cover 表现不一致）
          const bgW = bgImgEl.naturalWidth
          const bgH = bgImgEl.naturalHeight
          const bgFit = Math.min(W / bgW, H / bgH)
          const drawW = bgW * bgFit
          const drawH = bgH * bgFit
          const drawX = (W - drawW) / 2
          const drawY = (H - drawH) / 2
          ctx.drawImage(bgImgEl, drawX, drawY, drawW, drawH)
        } else {
          // 完全透明 → 不填充，让 PNG 透明区域保留
        }

        // 5) 画前景：因为画布尺寸 = 物体原始尺寸，物体本身就是 1:1，
        //    不用再 aspectFit。最终尺寸直接 = objectScale。
        const finalW = objNatW * this.objectScale
        const finalH = objNatH * this.objectScale
        // rpx → px：uni-app 750rpx 设计稿下，1rpx = 画布宽度 / 750。
        // 注意：拖动时 onTouchMove 用 dx*2（即 1 屏 px = 2 rpx），与 750rpx 体系一致。
        // 之前用 W/375 会让 rpx→px 系数翻倍，跨端表现不一致。
        const rpxToPx = W / 750
        const finalX = (W - finalW) / 2 + this.objectX * rpxToPx
        const finalY = (H - finalH) / 2 + this.objectY * rpxToPx
        ctx.drawImage(objImg, finalX, finalY, finalW, finalH)

        return canvas.toDataURL('image/png')
      })()
      // #endif
      // #ifndef H5
      // 非 H5 端：uni.createCanvasContext + toTempFilePath 可合成，但 uni-app 跨端兼容复杂；
      // 此处不合成，return transparentPng 字符串，onDownload 的 #ifndef H5 分支会
      // 走 uni.downloadFile 保存原透明 PNG（用户可后续自行合成）。
      // 注意：必须 resolve 而非 reject，否则外层 catch 会再次触发 _fallbackDownload。
      return Promise.resolve(this.transparentPng || '')
      // #endif
    },

    /**
     * 把任意 URL（H5 OSS / blob URL）转成「同源 / 可被 canvas 读取」的图片对象
     * - blob URL：直接用
     * - 远程 URL：fetch → blob → objectURL，绕开 OSS CORS
     * - fetch 失败（CORS / 网络）：回退返回原 URL，由 Image(crossOrigin) 自行尝试，
     *   避免直接 reject 导致 _loadImage 拿不到 img
     */
    _toLocalImageUrl(url) {
      // #ifdef H5
      return new Promise((resolve) => {
        try {
          if (!url) return resolve(url)
          if (/^blob:|^data:/.test(url)) return resolve(url)
          fetch(url, { mode: 'cors' })
            .then((r) => {
              if (!r.ok) throw new Error('HTTP ' + r.status)
              return r.blob()
            })
            .then((blob) => resolve(URL.createObjectURL(blob)))
            .catch(() => resolve(url)) // 回退：让 _loadImage 用 crossOrigin 直接试
        } catch (err) {
          resolve(url)
        }
      })
      // #endif
      // #ifndef H5
      return Promise.resolve(url)
      // #endif
    },

    /**
     * 加载图片（H5 only）
     * 设置 crossOrigin="anonymous"：服务器若支持 CORS，canvas 不会被污染，
     *   toDataURL 即可正常导出；若不支持 CORS，图片仍能渲染（onerror 才报错），
     *   但 drawImage 后 toDataURL 会抛 SecurityError，由外层 catch 兜底。
     */
    _loadImage(url) {
      // #ifdef H5
      return new Promise((resolve, reject) => {
        const img = new Image()
        // 仅对 http(s) 远程 URL 加 crossOrigin；blob:/data: 不需要（也禁止设置）
        if (/^https?:\/\//i.test(url)) {
          img.crossOrigin = 'anonymous'
        }
        img.onload = () => resolve(img)
        img.onerror = () => reject(new Error('图片加载失败：' + (url || '')))
        img.src = url
      })
      // #endif
      // #ifndef H5
      return Promise.reject(new Error('仅 H5 支持 canvas 合成'))
      // #endif
    },

    /**
     * 兜底：合成失败时退回直接下载原图
     */
    _fallbackDownload() {
      // #ifdef H5
      const a = document.createElement('a')
      a.href = this.transparentPng
      a.download = this.uploadedFileName || 'image-bg-remover.png'
      a.target = '_blank'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      // #endif
      // #ifndef H5
      uni.downloadFile({
        url: this.transparentPng,
        success: (res) => {
          if (res.statusCode === 200) {
            uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
          }
        }
      })
      // #endif
    },

    onReset() {
      this._clearTimers()
      this.state = 'A'
      this.uploadedImage = null
      this.uploadedFileName = ''
      this.transparentPng = null
      this.currentBgColor = ''
      this.bgImage = null
      this.batchId = null
      this.progress = 0
      // 重置物体调整
      this.objectX = 0
      this.objectY = 0
      this.objectScale = 1
      this.isDragging = false
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F9FAFB;
  display: flex;
  flex-direction: column;
}

/* ============ 顶栏 ============ */
.topbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #F3F4F6;
  &__back {
    width: 60rpx; height: 60rpx;
    display: flex; align-items: center; justify-content: center;
  }
  &__back-icon { font-size: 48rpx; color: #1F2937; }
  &__title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #0f172a; }
}

.page-content {
  flex: 1;
  padding: 24rpx 32rpx 64rpx;
}

/* ============ 状态 A：上传 + 去背景 ============ */
.state-a {
  .upload-card {
    height: 360rpx;
    border: 2rpx dashed #CBD5E1;
    border-radius: 24rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: #FFFFFF;
    margin-bottom: 24rpx;

    &__icon {
      width: 80rpx; height: 80rpx;
      color: #94A3B8;
      margin-bottom: 16rpx;
      svg { width: 100%; height: 100%; fill: currentColor; }
    }
    &__title { font-size: 30rpx; color: #1F2937; font-weight: 500; margin-bottom: 8rpx; }
    &__desc { font-size: 24rpx; color: #94A3B8; }
  }

  .uploaded-preview {
    background: #FFFFFF;
    border-radius: 24rpx;
    overflow: hidden;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
    margin-bottom: 24rpx;

    &__img {
      width: 100%;
      height: 480rpx;
      background: #F1F5F9;
      display: block;
    }
    &__bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20rpx 24rpx;
      border-top: 1rpx solid #F1F5F9;
    }
    &__name {
      font-size: 24rpx;
      color: #6B7280;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-right: 16rpx;
    }
    &__reupload {
      font-size: 24rpx;
      color: #3B82F6;
      padding: 8rpx 20rpx;
      border-radius: 999rpx;
      background: #EFF6FF;
    }
  }

  .btn-primary {
    width: 100%;
    height: 88rpx;
    background: #3B82F6;
    color: #FFFFFF;
    border-radius: 16rpx;
    font-size: 30rpx;
    font-weight: 600;
    margin-top: 24rpx;
    line-height: 88rpx;
    text-align: center;
    border: none;
    &[disabled] {
      background: #E5E7EB;
      color: #9CA3AF;
    }
  }
  .btn-hint {
    display: block;
    text-align: center;
    font-size: 24rpx;
    color: #94A3B8;
    margin-top: 16rpx;
  }
}

/* ============ 状态 B：处理中 ============ */
.state-b {
  padding: 64rpx 0;

  .processing-card {
    background: #FFFFFF;
    border-radius: 24rpx;
    padding: 48rpx 32rpx;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

    &__thumb {
      width: 200rpx;
      height: 200rpx;
      margin: 0 auto 32rpx;
      border-radius: 16rpx;
      overflow: hidden;
      background: #F1F5F9;
    }
    &__img { width: 100%; height: 100%; }

    &__progress { margin-bottom: 32rpx; }
    &__title {
      display: block;
      text-align: center;
      font-size: 32rpx;
      font-weight: 600;
      color: #1F2937;
      margin-bottom: 12rpx;
    }
    &__desc {
      display: block;
      text-align: center;
      font-size: 24rpx;
      color: #94A3B8;
      line-height: 1.5;
    }
  }

  .progress-track {
    width: 100%;
    height: 16rpx;
    background: #F1F5F9;
    border-radius: 8rpx;
    overflow: hidden;
    margin-bottom: 12rpx;
  }
  .progress-fill {
    height: 100%;
    background: linear-gradient(to right, #3B82F6, #60A5FA);
    border-radius: 8rpx;
    transition: width 0.3s ease;
  }
  .progress-text {
    display: block;
    text-align: center;
    font-size: 28rpx;
    font-weight: 600;
    color: #3B82F6;
  }
}

/* ============ 状态 C：实时预览 ============ */
.state-c {
  .compare-preview {
    display: flex;
    gap: 16rpx;
    margin-bottom: 24rpx;
  }

  .preview-box {
    flex: 1;
    background: #FFFFFF;
    border-radius: 24rpx;
    padding: 16rpx;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

    &__label {
      display: block;
      font-size: 24rpx;
      color: #6B7280;
      margin-bottom: 12rpx;
    }
    &__canvas {
      width: 100%;
      height: 300rpx;
      border-radius: 16rpx;
      overflow: hidden;
      background: #F1F5F9;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    &__canvas--compose {
      transition: background-color 0.2s ease;
    }
    /* 棋盘格背景：透明 PNG 的默认展示（看得出透明区域） */
    &__canvas--checker {
      background-image:
        linear-gradient(45deg, #E5E7EB 25%, transparent 25%),
        linear-gradient(-45deg, #E5E7EB 25%, transparent 25%),
        linear-gradient(45deg, transparent 75%, #E5E7EB 75%),
        linear-gradient(-45deg, transparent 75%, #E5E7EB 75%);
      background-size: 20rpx 20rpx;
      background-position: 0 0, 0 10rpx, 10rpx -10rpx, -10rpx 0;
    }
    &__img { width: 100%; height: 100%; }
    &__placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #9CA3AF;
      font-size: 24rpx;
      &-sub { font-size: 22rpx; margin-top: 8rpx; color: #CBD5E1; }
    }
  }

  .status-bar {
    display: flex;
    align-items: center;
    background: #FFFFFF;
    border-radius: 16rpx;
    padding: 20rpx 24rpx;
    margin-bottom: 16rpx;
  }
  .status-dot {
    width: 12rpx; height: 12rpx;
    border-radius: 50%;
    background: #10B981;
    margin-right: 12rpx;
  }
  .status-text {
    flex: 1;
    font-size: 26rpx;
    color: #1F2937;
  }
  .status-clear {
    font-size: 24rpx;
    color: #3B82F6;
    padding: 4rpx 16rpx;
    border-radius: 999rpx;
    background: #EFF6FF;
  }

  .section {
    background: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 16rpx;

    .section-title {
      display: block;
      font-size: 28rpx;
      font-weight: 600;
      color: #1F2937;
      margin-bottom: 20rpx;
    }
  }

  .color-palette {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 16rpx;
  }
  .color-chip {
    height: 72rpx;
    border-radius: 16rpx;
    border: 4rpx solid transparent;
    transition: transform 0.15s ease;
    position: relative;

    &--active {
      border-color: #3B82F6;
      transform: scale(1.05);
    }
    &__label {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: 22rpx;
      color: #6B7280;
    }
  }

  .bg-actions {
    display: flex;
    align-items: center;
    gap: 16rpx;
  }
  .bg-upload-btn {
    flex: 1;
    height: 120rpx;
    border: 2rpx dashed #CBD5E1;
    border-radius: 16rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: #F9FAFB;
    &__plus {
      font-size: 40rpx;
      color: #94A3B8;
      line-height: 1;
    }
    &__text {
      font-size: 24rpx;
      color: #6B7280;
      margin-top: 8rpx;
    }
  }
  .bg-thumb {
    width: 120rpx;
    height: 120rpx;
    border-radius: 16rpx;
    overflow: hidden;
    background: #F1F5F9;
    &__img { width: 100%; height: 100%; }
  }

  /* ============ 物体层 + 背景层（D2） ============ */
  .preview-box__canvas--compose {
    position: relative;
    overflow: hidden;
  }
  .bg-layer {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    pointer-events: none;
  }
  .object-layer {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    transform-origin: center center;
    touch-action: none;
    /* H5 阻止图像默认拖拽；移动端阻止滚动 */
    user-select: none;
    -webkit-user-drag: none;
    cursor: move;
    &__img {
      width: 100%;
      height: 100%;
      pointer-events: none;
    }
  }

  /* ============ 物体控制面板（D2） ============ */
  .object-controls {
    background: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 16rpx;
    &__row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12rpx;
    }
    &__slider {
      width: 100%;
      margin: 8rpx 0 16rpx;
    }
    &__btns {
      display: flex;
      gap: 12rpx;
      margin-top: 8rpx;
    }
  }
  .control-label {
    font-size: 26rpx;
    color: #1F2937;
    font-weight: 500;
  }
  .control-value {
    font-size: 24rpx;
    color: #3B82F6;
    font-weight: 600;
  }
  .btn-mini {
    flex: 1;
    height: 64rpx;
    line-height: 64rpx;
    background: #F3F4F6;
    color: #374151;
    border-radius: 12rpx;
    font-size: 26rpx;
    border: none;
    text-align: center;
    &--active {
      background: #EFF6FF;
      color: #3B82F6;
    }
  }

  .bottom-actions {
    display: flex;
    gap: 16rpx;
    margin-top: 32rpx;

    .btn-primary,
    .btn-secondary {
      flex: 1;
      height: 88rpx;
      border-radius: 16rpx;
      font-size: 28rpx;
      font-weight: 600;
      line-height: 88rpx;
      text-align: center;
      border: none;
    }
    .btn-primary {
      background: #3B82F6;
      color: #FFFFFF;
    }
    .btn-secondary {
      background: #FFFFFF;
      color: #3B82F6;
      border: 2rpx solid #3B82F6;
    }
  }
}
</style>
