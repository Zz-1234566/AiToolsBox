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
        <!-- 成果图：只展示去背景后的合成结果，不再并排展示原图 -->
        <view class="preview-box">
          <text class="preview-box__label">成果图（可拖动缩放）</text>
          <!-- 实时合成：背景层 + 物体层（可拖动 + 可缩放） -->
          <view
            ref="composeCanvas"
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

            <!-- ============ 对比滑块：一半原图 / 一半去背景（可选开启） ============ -->
            <!-- 覆盖在画布左侧，只露出 compareRatio 宽度；右侧自然透出下方编辑结果 -->
            <view
              v-if="compareEnabled && uploadedImage"
              class="compare-layer"
              :style="{ width: compareRatio * 100 + '%' }"
            >
              <view class="compare-layer__inner" :style="compareInnerStyle">
                <image
                  :src="uploadedImage"
                  mode="aspectFit"
                  class="compare-layer__img"
                  :style="objectTransformStyle"
                  :draggable="false"
                />
              </view>
            </view>
            <!-- 分界线 + 可拖动手柄
                 手势绑定在**分界线容器**而非手柄上：
                 ① 鼠标落在内部 <svg> 上时不会冒泡不到手柄，绑容器才稳；
                 ② 拖动过程中指针移出手柄，mousemove 仍能继续跟到画布范围内。 -->
            <view
              v-if="compareEnabled && uploadedImage"
              class="compare-divider"
              :class="{ 'compare-divider--active': isComparing }"
              :style="{ left: compareRatio * 100 + '%' }"
              @touchstart.stop="onCompareStart"
              @touchmove.stop.prevent="onCompareMove"
              @touchend.stop="onCompareEnd"
              @touchcancel.stop="onCompareEnd"
              @mousedown.stop="onCompareMouseDown"
              @mousemove.stop.prevent="onCompareMouseMove"
              @mouseup.stop="onCompareMouseUp"
            >
              <view class="compare-divider__handle">
                <svg viewBox="0 0 24 24"><path d="M9.5 7.5L5 12l4.5 4.5M14.5 7.5L19 12l-4.5 4.5"/></svg>
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
            <!-- 对比原图：开启后画布上出现分界线，可左右拖动 -->
            <button
              class="btn-mini"
              :class="{ 'btn-mini--active': compareEnabled }"
              @click="onToggleCompare"
            >{{ compareEnabled ? '关闭对比' : '对比原图' }}</button>
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

    <!-- ============ App 端合成画布（离屏） ============ -->
    <!--
      用途：App 端把「透明 PNG + 底色/背景图 + 缩放位移」合成一张图并存相册。
      为什么必须有：H5 走 DOM canvas，App 端逻辑层无 document，只能用 uni canvas。
      为什么 hidpi="false"：hidpi 默认按设备像素比放大 backing store，
      3000×2000 的画布在 3x 机上会变成 9000×6000（≈216MB）→ 极易 OOM。
      关掉后 backing store = 逻辑尺寸，配合 destWidth/destHeight 输出尺寸可控。
    -->
    <!-- #ifndef H5 -->
    <canvas
      canvas-id="composeCanvasApp"
      id="composeCanvasApp"
      class="compose-canvas"
      hidpi="false"
      :style="{ width: canvasW + 'px', height: canvasH + 'px' }"
    ></canvas>
    <!-- #endif -->
  </view>
</template>

<script>
import { imageBgRemover, pollBatchCompleted } from '@/api/image-bg-remover'
import { pickFiles } from '@/utils/filePicker'
import { safeBack } from '@/utils/pageTransition'
import { downloadOutput, saveLocalImageToAlbum, downloadToLocalPath } from '@/utils/fileAction'

// 与后端 SegmentationController.ALLOWED_IMAGE_EXTS / ALLOWED_IMAGE_TYPES 保持同步：
// jpg / jpeg / png / bmp / gif。webp 等不在白名单内的格式直接前端拦截，
// 不送往后端，避免走到 service 才抛异常。
const ALLOWED_EXTS = ['jpg', 'jpeg', 'png', 'bmp', 'gif']
// 单张图片大小上限 20MB（与后端 multipart max-file-size / Constants.MAX_FILE_SIZE 一致）
const MAX_FILE_SIZE = 20 * 1024 * 1024

// ==================== 物体调整参数常量 ====================
/** 缩放下限 / 上限（与滑条 min=10 / max=300 保持一致） */
const MIN_OBJECT_SCALE = 0.1
const MAX_OBJECT_SCALE = 3.0

/**
 * 屏幕 px → rpx 的换算系数。
 * ⚠️ 原来各手势分支里散落着硬编码的 `dx * 2`，那是假定「750rpx = 375px」
 *    （即仅 iPhone 6/7/8 这类 375pt 宽机型）。在 414pt / 428pt 的机型上
 *    手指移动同样距离会被换算成更大的 rpx 位移，导致「拖过头」。
 * 统一按 uni.upx2px 的逆运算推导：1rpx 对应多少 px，375pt 机型下正好等于 2。
 */
const _upx2px = (rpx) => (uni && uni.upx2px ? uni.upx2px(rpx) : rpx)
const RPX_PER_PX = 750 / Math.max(1, _upx2px(750))

/** 成果图画布在 750rpx 设计稿下的宽度（= 100% - preview-box 左右各 16rpx padding） */
const PREVIEW_CANVAS_RPX = 750 - 32

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

      // 对比滑块：左原图 / 右去背景，默认关闭，避免干扰编辑
      compareEnabled: false,
      compareRatio: 0.5,      // 分界线位置 0~1
      isComparing: false,

      // App 端合成画布尺寸（px）：随透明 PNG 的原始尺寸设置，供 canvas 元素绑定
      canvasW: 0,
      canvasH: 0,
      // H5 window 监听句柄（非响应式，避免每次拖动触发重渲染）
      _compareBound: false,
      _compareMoveHandler: null,
      _compareUpHandler: null,

      // 手势状态
      isDragging: false,
      dragStartX: 0,
      dragStartY: 0,
      objectStartX: 0,
      objectStartY: 0,
      // 双指捏合状态（缩放锚点 = 两指中点）
      isPinching: false,
      pinchStartDist: 0,
      pinchStartMidX: 0,
      pinchStartMidY: 0,
      pinchStartScale: 1,
      // 画布在屏幕坐标系里的原点与边长（每次手势开始实测）
      canvasRect: null,

      // ⭐ 去背景调参：已移除（MODNet 官方推荐默认：mask-threshold=0 / feather-size=0，模型不可调）
      // yml 中 SegmentationConfig 是最佳默认值，前端不再传参。

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
    },

    /**
     * 对比层内部定位：宽度必须等于**整个画布**（而非被裁剪后的可见宽度），
     * 否则原图会跟着分界线一起被压缩，两侧比例就对不上了。
     * 用 100000rpx 的绝对宽度配合父级 left:0 + overflow 隐藏来实现裁剪。
     */
    compareInnerStyle() {
      return `width: ${_upx2px(PREVIEW_CANVAS_RPX)}px; height: 100%;`
    }
  },

  /**
   * Android 物理返回键拦截（uni-app 的 onBackPress，App 端可用；iOS 侧滑由系统接管）。
   *
   * <p>处理中（state='B'）返回会**中断正在跑的去背景任务**并丢掉已上传的图片，
   * 没有任何提示 —— 用户以为 App 卡了。这里给一次确认。
   * <p>其余状态（A 已选图未处理 / C 已出结果）直接放行，不给用户添堵。
   */
  onBackPress() {
    if (this.state !== 'B') {
      return false   // 不拦截，走默认返回
    }
    uni.showModal({
      title: '确认离开？',
      content: '正在去背景，离开后本次处理会中断',
      confirmText: '离开',
      cancelText: '继续处理',
      success: (r) => {
        if (r.confirm) {
          this._clearTimers()
          // 用项目统一的 safeBack（栈空时自动 reLaunch 兜底），不手写 navigateBack
          safeBack('/pages/index')
        }
      }
    })
    return true    // 阻止默认返回，由上面的 confirm 决定
  },

  onUnload() {
    this._clearTimers()
    // 拖动中途离开页面会漏掉 mouseup，必须在这里解绑 window 监听
    // #ifdef H5
    this._detachCompareWindowListeners()
    // #endif
  },

  methods: {
    /**
     * 成果图画布在**屏幕坐标系**里的原点与边长（px）。
     * 捏合缩放的锚点补偿必须用画布相对坐标，否则页面滚动后锚点会偏。
     * 读 DOM 而非 computed：无响应式依赖，computed 会被永久缓存，
     * 故在 onTouchStart（每次手势开始）实测一次。
     */
    _measureCanvas() {
      const fallback = _upx2px(Math.min(PREVIEW_CANVAS_RPX, 720))
      // #ifdef H5
      try {
        const el = this.$refs.composeCanvas
        if (el) {
          const rect = el.getBoundingClientRect()
          if (rect && rect.width > 0) {
            this.canvasRect = { left: rect.left, top: rect.top, size: rect.width }
            return
          }
        }
      } catch (err) {
        // 非 H5 端无 DOM API，走下面的兜底
      }
      // #endif
      this.canvasRect = { left: 0, top: 0, size: fallback }
    },

    /**
     * 返回：用项目的 safeBack，而不是 navigateBack + fail 兜底。
     * H5 端 navigateBack 走 history.back()，页面栈为空时（刷新本页 /
     * vite 热更新重载后）既不报错也不触发 fail，点击返回会完全没反应；
     * safeBack 主动判断 getCurrentPages().length，栈底时改走 reLaunch。
     */
    goBack() {
      safeBack('/pages/index')
    },

    // ==================== 状态 A：上传 ====================
    async onPickImage() {
      // 跨端选择统一走 utils/filePicker（平台分支只在那一处）
      const res = await pickFiles({
        count: 1,
        // extension 让文件选择器**先**过滤（H5 端会映射为 input.accept），与 ALLOWED_EXTS 保持一致
        accept: ALLOWED_EXTS.map(e => '.' + e).join(','),
        fileType: 'image',
        sourceType: ['album', 'camera'],
        fileLabel: '图片'
      })
      if (!res.ok) {
        if (res.reason === 'unsupported' || res.reason === 'empty' || res.reason === 'error') {
          uni.showToast({ title: res.message || '当前平台暂不支持选择图片', icon: 'none', duration: 3000 })
        }
        // cancelled：用户主动取消，不提示
        return
      }
      const picked = res.files[0]
      const fileName = picked.fileName || 'image.png'
      // 大小校验：提前拦下，避免传完 20MB 才被后端拒（提示费解）
      if ((picked.size || 0) > MAX_FILE_SIZE) {
        uni.showToast({
          title: '图片不能超过 20MB',
          icon: 'none',
          duration: 3000
        })
        return
      }
      this.uploadedImage = picked.path || picked.file
      this.uploadedFileName = fileName
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
        // 不再传调参，MODNet 官方推荐默认（mt=0/fs=0/matte=none）由后端 yml 控制
        const res = await imageBgRemover(this.uploadedImage, {
          mode: 'transparent'
        })
        const data = (res && res.data) || null
        if (!res || res.code !== 200 || !data || !data.batchId) {
          // 失败时把后端 code + message 一并塞进 Error，方便 catch 块做具体提示
          const code = (res && res.code) || 0
          const msg = (res && res.message) || '提交任务失败'
          const e = new Error(msg)
          e.code = code
          throw e
        }
        this.batchId = data.batchId

        // 2) 轮询 batch 状态
        await this._pollBatch(this.batchId)
      } catch (err) {
        console.error('[image-bg-remover] 去背景失败:', err)
        this._showErrorToast(err)
        this._clearTimers()
        this.state = 'A'
        this.progress = 0
      }
    },

    /**
     * 把 onStartRemove 流程中抛出的 err 翻译成用户能看懂的提示
     * - err.code 0  = 本地包装（如「提交任务失败」），原始 message 即提示
     * - err.code 在 30002/80002/90001/90004/90005 → 给出具体话术
     * - 其他 code → 显示「message（错误码 code）」
     * - err.errMsg（uni.request/uploadFile fail）→ 网络异常
     * - 其他 → 客户端错误
     */
    _showErrorToast(err) {
      const code = (err && err.code) || 0
      const msg = (err && err.message) || ''

      if (code === 90001) {
        uni.showModal({ title: '未登录', content: '请重新登录后再试', showCancel: false })
        return
      }
      if (code === 90004) {
        uni.showToast({ title: '请求太频繁，请稍后再试', icon: 'none' })
        return
      }
      if (code === 90005) {
        uni.showToast({ title: '系统错误：' + (msg || '请稍后再试'), icon: 'none', duration: 3000 })
        return
      }
      // 30002 = IMAGE_UNSUPPORTED「图片格式不支持」；80002 = FILE_TOO_LARGE「文件太大」
      // ⚠️ 原来这里写的是 10001 / 10002 —— 那两个码在 errorCode.js 里是
      //    USER_NOT_FOUND（用户不存在）/ PASSWORD_ERROR（密码错误），
      //    与本意完全不符（后端也从没发过这两个码），导致用户只能看到
      //    「xxx（错误码 10001）」这种没头没尾的提示。
      if (code === 30002) {
        uni.showToast({ title: '文件格式不支持：' + (msg || '请换一张图'), icon: 'none', duration: 3000 })
        return
      }
      if (code === 80002) {
        uni.showToast({ title: '文件太大：' + (msg || '请压缩后重试'), icon: 'none', duration: 3000 })
        return
      }
      if (code && code !== 0) {
        // 其他后端错误码：把后端 message + 错误码一起给用户
        uni.showToast({ title: (msg || '处理失败') + '（错误码 ' + code + '）', icon: 'none', duration: 3000 })
        return
      }
      if (err && err.errMsg) {
        // uni.request / uni.uploadFile fail 时的结构：{ errMsg: 'request:fail ...' }
        if (String(err.errMsg).includes('timeout')) {
          uni.showToast({ title: '网络超时，请检查后重试', icon: 'none' })
        } else {
          uni.showToast({ title: '网络异常，请检查后重试', icon: 'none' })
        }
        return
      }
      uni.showToast({ title: msg || '处理失败，请重试', icon: 'none' })
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

    // ==================== 对比滑块（左右分界） ====================
    /** 按屏幕 x 坐标更新分界位置 */
    _setCompareByX(clientX) {
      const rect = this.canvasRect
      const left = rect ? rect.left : 0
      const size = rect && rect.size > 0 ? rect.size : _upx2px(PREVIEW_CANVAS_RPX)
      if (!size) return
      const ratio = (clientX - left) / size
      // 留 2% 余量，手柄不会被裁掉，也便于贴边
      this.compareRatio = Math.max(0.02, Math.min(0.98, ratio))
    },

    onCompareStart(e) {
      this.isComparing = true
      this._measureCanvas()
      const t = (e && e.touches && e.touches[0]) || null
      if (t) this._setCompareByX(t.clientX)
    },

    onCompareMove(e) {
      if (!this.isComparing) return
      const t = (e && e.touches && e.touches[0]) || null
      if (t) this._setCompareByX(t.clientX)
    },

    onCompareEnd() {
      this.isComparing = false
    },

    /**
     * H5 桌面端 fallback：拖动期间把 move/up 挂到 window。
     * 指针一旦移出 2rpx 宽的分界线，元素上的 mousemove 就收不到了，
     * 会导致「按住后一离开细线就卡住」。window 监听才能全程跟手。
     */
    onCompareMouseDown(e) {
      this.isComparing = true
      this._measureCanvas()
      this._setCompareByX(e.clientX)
      // #ifdef H5
      this._attachCompareWindowListeners()
      // #endif
    },

    onCompareMouseMove(e) {
      if (!this.isComparing) return
      this._setCompareByX(e.clientX)
    },

    onCompareMouseUp() {
      if (!this.isComparing) return
      this.isComparing = false
      // #ifdef H5
      this._detachCompareWindowListeners()
      // #endif
    },

    // #ifdef H5
    _attachCompareWindowListeners() {
      if (this._compareBound) return
      this._compareBound = true
      this._compareMoveHandler = (e) => this._setCompareByX(e.clientX)
      this._compareUpHandler = () => {
        this.isComparing = false
        this._detachCompareWindowListeners()
      }
      window.addEventListener('mousemove', this._compareMoveHandler)
      window.addEventListener('mouseup', this._compareUpHandler)
    },

    _detachCompareWindowListeners() {
      if (!this._compareBound) return
      this._compareBound = false
      window.removeEventListener('mousemove', this._compareMoveHandler)
      window.removeEventListener('mouseup', this._compareUpHandler)
      this._compareMoveHandler = null
      this._compareUpHandler = null
    },
    // #endif

    /** 关闭对比时清理未结束的拖动，避免残留 window 监听 */
    onToggleCompare() {
      this.compareEnabled = !this.compareEnabled
      if (!this.compareEnabled) {
        this.isComparing = false
        // #ifdef H5
        this._detachCompareWindowListeners()
        // #endif
      }
    },

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

    async onUploadBg() {
      // 跨端选择统一走 utils/filePicker（平台分支只在那一处）
      const res = await pickFiles({
        count: 1,
        // extension 让文件选择器**先**过滤，与 ALLOWED_EXTS 保持一致
        accept: ALLOWED_EXTS.map(e => '.' + e).join(','),
        fileType: 'image',
        sourceType: ['album', 'camera'],
        fileLabel: '背景图'
      })
      if (!res.ok) {
        if (res.reason === 'unsupported' || res.reason === 'empty') {
          uni.showToast({ title: res.message || '未选择图片', icon: 'none' })
        }
        // cancelled：用户主动取消，不提示
        return
      }
      const picked = res.files[0]
      this.bgImage = picked.path || picked.file
      this.currentBgColor = ''
    },

    onClearOverlay() {
      this.currentBgColor = ''
      this.bgImage = null
    },

    // ==================== 状态 C：物体调整（D2） ====================
    /**
     * 两指中点（供捏合缩放锚点补偿用）。
     * 缩放时若不做补偿，物体只会以「画布中心」为锚点缩放，
     * 手指按在哪就必然漂移——这是图片编辑器最常见的体验缺陷。
     */
    _touchMid(touches) {
      const x = (touches[0].clientX + touches[1].clientX) / 2
      const y = (touches[0].clientY + touches[1].clientY) / 2
      return { x, y }
    },

    /** 两指间距（捏合基准距离） */
    _touchDist(touches) {
      const dx = touches[0].clientX - touches[1].clientX
      const dy = touches[0].clientY - touches[1].clientY
      return Math.sqrt(dx * dx + dy * dy)
    },

    onTouchStart(e) {
      const touches = (e && e.touches) || []

      // 双指：进入捏合缩放，记录初始距离 / 中点 / 当时的缩放与位移
      if (touches.length === 2) {
        this.isPinching = true
        this.isDragging = false
        this._measureCanvas()
        this.pinchStartDist = this._touchDist(touches)
        const mid = this._touchMid(touches)
        this.pinchStartMidX = mid.x
        this.pinchStartMidY = mid.y
        this.pinchStartScale = this.objectScale
        this.objectStartX = this.objectX
        this.objectStartY = this.objectY
        return
      }

      // 单指：仅在未处于捏合态时才开始拖动。
      // ⚠️ 捏合结束切回单指时 touches.length 会从 2 变 1，
      //    若不置 isPinching=false，单指拖动会一直不响应。
      if (touches.length === 1 && !this.isPinching) {
        this.isDragging = true
        const touch = touches[0]
        this.dragStartX = touch.clientX
        this.dragStartY = touch.clientY
        this.objectStartX = this.objectX
        this.objectStartY = this.objectY
      }
    },

    onTouchMove(e) {
      const touches = (e && e.touches) || []

      // 双指捏合：以中点为锚点缩放
      if (touches.length === 2 && this.isPinching) {
        const startDist = this.pinchStartDist || 0
        if (startDist <= 0) return
        const ratio = this._touchDist(touches) / startDist
        const newScale = Math.max(
          MIN_OBJECT_SCALE,
          Math.min(MAX_OBJECT_SCALE, this.pinchStartScale * ratio)
        )

        // 锚点补偿：让「起始中点下的那个像素」在缩放前后停在同一屏幕位置。
        // 推导（画布相对坐标，物体基准尺寸恰好等于画布，故物体中心 C 恒为 0）：
        //   屏幕映射  M = p*S + T        （p 为未缩放的物体坐标，T 为位移）
        //   起始      M0 = p0*S0 + T0     →  p0 = (M0 - T0)/S0
        //   缩放后要求 p0 仍在 M1 下      M1 = p0*S1 + T1
        //   代入化简（k = S1/S0）：  T1 = k*T0 + M1 - k*M0
        // 退化校验：k=1 时 T1 = T0 + (M1-M0)，即纯跟手平移，行为不变。
        // 全部量统一为 px（位移 data 存 rpx，进出各换算一次）
        const k = newScale / (this.pinchStartScale || 1)
        const t0x = this.objectStartX / RPX_PER_PX
        const t0y = this.objectStartY / RPX_PER_PX
        const rect = this.canvasRect || { left: 0, top: 0, size: _upx2px(Math.min(PREVIEW_CANVAS_RPX, 720)) }
        const m0x = this.pinchStartMidX - rect.left
        const m0y = this.pinchStartMidY - rect.top
        const mid = this._touchMid(touches)
        const m1x = mid.x - rect.left
        const m1y = mid.y - rect.top

        this.objectScale = newScale
        this.objectX = (k * t0x + m1x - k * m0x) * RPX_PER_PX
        this.objectY = (k * t0y + m1y - k * m0y) * RPX_PER_PX
        this._applyBounds()
        return
      }

      if (!this.isDragging || touches.length !== 1) return
      const touch = touches[0]
      const dx = touch.clientX - this.dragStartX
      const dy = touch.clientY - this.dragStartY
      this.objectX = this.objectStartX + dx * RPX_PER_PX
      this.objectY = this.objectStartY + dy * RPX_PER_PX
      this._applyBounds()
    },

    onTouchEnd(e) {
      const touches = (e && e.touches) || []
      // 还有手指留在屏幕上（2→1）时不要急着收尾，等最后一根抬起再重置
      if (touches.length > 0) return
      // ⚠️ 必须彻底清理手势状态，否则下一次单指拖动会被误判成缩放
      //    （Cropper.js #161 同类问题）
      this.isDragging = false
      this.isPinching = false
      this.pinchStartDist = 0
      this.pinchStartMidX = 0
      this.pinchStartMidY = 0
      this.pinchStartScale = 1
    },

    /**
     * 边界约束：物体小于画布时锁死居中（不可拖），大于画布时把位移夹在溢出范围内。
     * 不加这个约束，用户可以把主体拖出画布外且看不到「去哪了」。
     */
    _applyBounds() {
      const maxOffsetRpx = this._maxOffsetRpx()
      this.objectScale = Math.max(MIN_OBJECT_SCALE, Math.min(MAX_OBJECT_SCALE, this.objectScale))
      if (maxOffsetRpx <= 0) {
        // 物体未超出画布 → 无可拖动空间，锁死居中
        this.objectX = 0
        this.objectY = 0
        return
      }
      this.objectX = Math.max(-maxOffsetRpx, Math.min(maxOffsetRpx, this.objectX))
      this.objectY = Math.max(-maxOffsetRpx, Math.min(maxOffsetRpx, this.objectY))
    },

    /**
     * 当前缩放下允许的最大位移（rpx），返回 0 表示物体恰好等于画布、无可拖空间。
     *
     * 物体层基准尺寸 = 画布（object-layer__img 铺满画布），缩放后实际尺寸 = 画布 × scale：
     *   · scale < 1：物体小于画布 → 可在画布内自由摆放，范围 = (画布 - 物体)/2
     *   · scale = 1：物体等于画布 → 无空间，锁死居中
     *   · scale > 1：物体大于画布 → 只能平移，且不能拖到露出画布外空白，范围 = (物体 - 画布)/2
     * 两侧统一为 画布 × |1 - scale| / 2。
     *
     * ⚠️ 不可写成 max(0, scale-1)：那会在 scale<1 时直接锁死，
     *    导致「先缩小、再把主体挪到想要的位置」这条最自然的操作路径失效。
     */
    _maxOffsetRpx() {
      const scale = this.objectScale || 1
      return (PREVIEW_CANVAS_RPX * Math.abs(1 - scale)) / 2
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
      this.objectX = this.objectStartX + dx * RPX_PER_PX
      this.objectY = this.objectStartY + dy * RPX_PER_PX
      this._applyBounds()
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
      this.objectScale = Math.max(MIN_OBJECT_SCALE, Math.min(MAX_OBJECT_SCALE, v / 100))
      this._applyBounds()
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
          // ⚠️ H5 这里刻意不走 fileAction.downloadOutput：dataUrl 是 canvas 合成出的
          //    内存 data URL，不是网络地址；uni.downloadFile 只接受网络/本地路径。
          //    <a download> 是触发浏览器存盘 data URL 的唯一方式。
          const a = document.createElement('a')
          a.href = dataUrl
          a.download = (this.uploadedFileName && this.uploadedFileName.replace(/\.[^.]+$/, '')) || 'image-bg-remover'
          a.download += `-${Date.now()}.png`
          a.target = '_blank'
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          uni.hideLoading()
          uni.showToast({ title: '已开始下载', icon: 'success' })
          // #endif
          // #ifndef H5
          // App 端：compositeOnCanvas 返回的是**合成图的本地临时文件路径**
          // （canvasToTempFilePath 产出），直接存相册。
          // ⚠️ 不能在这里弹「下载完成」：存相册是异步的，提前弹会变成虚假成功提示。
          //    成功/失败提示由 fileAction.saveLocalImageToAlbum 的回调给出。
          uni.hideLoading()
          saveLocalImageToAlbum(dataUrl)
          // #endif
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
      // App 端：逻辑层没有 document，无法用 DOM canvas，改用 uni canvas
      // （createCanvasContext + canvasToTempFilePath，官方支持 App 端）。
      // 几何口径与 H5 分支**完全一致**：画布 = 透明 PNG 原始尺寸，
      // 前景按 objectScale 缩放、按 objectX/objectY（rpx）位移。
      return this._compositeOnAppCanvas()
      // #endif
    },

    /**
     * App 端 canvas 合成：透明 PNG + 底色/背景图 → 临时文件路径。
     *
     * 与 H5 的差异只在 API（uni canvas vs DOM canvas），几何口径刻意保持一致，
     * 保证「同一张图在 H5 与 App 下载出来的结果相同」。
     *
     * 关键约束（uni canvas 与 DOM canvas 的差异）：
     * - drawImage 只接受**本地路径**，网络图要先 downloadFile 到临时文件；
     * - 画布尺寸由 canvas 元素的 style 决定，而尺寸绑定的是响应式数据
     *   → 改完必须等 DOM 更新（$nextTick）才能 createCanvasContext 并绘制；
     * - canvasToTempFilePath 产出临时文件路径，再由调用方存相册。
     *
     * @returns {Promise<String>} 合成图的临时文件路径
     */
    async _compositeOnAppCanvas() {
      // #ifndef H5
      // 1) 前景（透明 PNG）：网络 URL → 本地临时文件（canvas 只认本地路径）
      const objPath = await this._downloadToLocalPath(this.transparentPng)
      if (!objPath) throw new Error('下载透明底图失败')

      // 2) 取原始尺寸：画布按原始尺寸，与 H5 分支一致（不按预览框尺寸）
      const objInfo = await this._getImageInfo(objPath)
      const W = objInfo.width || 800
      const H = objInfo.height || 800

      // 3) 背景图（可选）：网络地址同样先转本地；拉取失败降级为「只画底色/透明」
      let bgPath = ''
      let bgW = 0
      let bgH = 0
      if (this.bgImage) {
        const bgInfo = await this._getImageInfo(this.bgImage)
        bgW = bgInfo.width || 0
        bgH = bgInfo.height || 0
        const local = await this._downloadToLocalPath(this.bgImage)
        if (local) bgPath = local
      }

      // 4) 设置画布尺寸并等 DOM 生效（尺寸绑定的是响应式数据）
      this.canvasW = W
      this.canvasH = H
      await this.$nextTick()

      // 5) 绘制
      const ctx = uni.createCanvasContext('composeCanvasApp', this)
      // 5.1 画背景
      if (this.currentBgColor && this.currentBgColor !== 'transparent') {
        ctx.setFillStyle(this.currentBgColor)
        ctx.fillRect(0, 0, W, H)
      } else if (bgPath && bgW && bgH) {
        // aspectFit(contain) 居中，与 H5 分支一致
        const bgFit = Math.min(W / bgW, H / bgH)
        const drawW = bgW * bgFit
        const drawH = bgH * bgFit
        ctx.drawImage(bgPath, (W - drawW) / 2, (H - drawH) / 2, drawW, drawH)
      }
      // 5.2 画前景：画布 = 物体原始尺寸，物体本身 1:1，只受 objectScale 影响
      const finalW = W * this.objectScale
      const finalH = H * this.objectScale
      // rpx → px：与 H5 分支同一系数（画布宽度 / 750）
      const rpxToPx = W / 750
      const finalX = (W - finalW) / 2 + this.objectX * rpxToPx
      const finalY = (H - finalH) / 2 + this.objectY * rpxToPx
      ctx.drawImage(objPath, finalX, finalY, finalW, finalH)

      // 6) 落盘导出（必须在 draw 的回调里，绘制完成后才能取到像素）
      return new Promise((resolve, reject) => {
        ctx.draw(false, () => {
          uni.canvasToTempFilePath({
            canvasId: 'composeCanvasApp',
            x: 0,
            y: 0,
            width: W,
            height: H,
            destWidth: W,
            destHeight: H,
            success: (res) => resolve(res.tempFilePath),
            fail: (err) => reject(new Error('导出合成图失败：' + ((err && err.errMsg) || '未知错误')))
          }, this)
        })
      })
      // #endif
    },

    /**
     * 把网络地址取到本地路径（App 端 canvas 画网络图的前置步骤）。
     * 统一走 fileAction.downloadToLocalPath（公共能力唯一出口）。
     */
    _downloadToLocalPath(url) {
      // #ifndef H5
      return downloadToLocalPath(url)
      // #endif
    },

    /**
     * 取图片尺寸（App 端 canvas 需要原始像素尺寸来定画布大小）。
     * 失败返回空对象，由调用方走默认尺寸。
     */
    _getImageInfo(src) {
      // #ifndef H5
      return new Promise((resolve) => {
        if (!src) {
          resolve({})
          return
        }
        uni.getImageInfo({
          src,
          success: (info) => resolve(info || {}),
          fail: () => resolve({})
        })
      })
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
     * 兜底：合成失败时退回直接下载原图。
     * 走 fileAction 统一下载出口（跨端策略 + 403 过期提示都在那一处维护）。
     */
    _fallbackDownload() {
      downloadOutput({
        url: this.transparentPng,
        name: (this.uploadedFileName && this.uploadedFileName.replace(/\.[^.]+$/, '')) || 'image-bg-remover.png',
        kind: 'image'
      })
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
      // 重置对比滑块（新图没有可对比的旧状态）
      this.compareEnabled = false
      this.compareRatio = 0.5
      this.isComparing = false
      // #ifdef H5
      this._detachCompareWindowListeners()
      // #endif
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
  /* App 端自绘导航栏需避开状态栏/刘海（uni 注入 --status-bar-height，H5 端为 0）
     ⚠️ 必须用四值简写：`padding: 0 24rpx` 简写会重置 padding-top。 */
  padding: var(--status-bar-height, 0px) 24rpx 0;
  display: flex;
  align-items: center;
  height: 88rpx;
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

/* App 端合成用离屏画布：移出可视区但保留尺寸（display:none 会让画布不可绘制） */
.compose-canvas {
  position: fixed;
  left: -9999px;
  top: 0;
  pointer-events: none;
  opacity: 0;
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
  /* 单列成果图：不再并排展示原图，画布占满宽度并适当加高 */
  .preview-box {
    background: #FFFFFF;
    border-radius: 24rpx;
    padding: 16rpx;
    margin-bottom: 24rpx;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

    &__label {
      display: block;
      font-size: 24rpx;
      color: #6B7280;
      margin-bottom: 12rpx;
    }
    &__canvas {
      width: 100%;
      height: 720rpx;
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

  /* 去背景参数面板：已移除（MODNet 官方推荐默认由 yml 控制） */
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

  /* ============ 对比滑块：左原图 / 右去背景 ============ */
  /* 对比层盖在画布左侧，只露出 compareRatio 宽度；内部宽度等于整个画布，
     保证原图与右侧编辑结果的比例严格一致（不随分界线移动而被压缩）。 */
  .compare-layer {
    position: absolute;
    top: 0; left: 0; bottom: 0;
    overflow: hidden;
    pointer-events: none;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;

    &__inner {
      position: absolute;
      left: 0; top: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    &__img {
      width: 100%;
      height: 100%;
      /* 与 .object-layer__img 同步同一套 transform，原图与成果图始终对齐 */
    }
  }

  .compare-divider {
    /* 手柄用 absolute 定位到中心，分界线自身需作为定位上下文 */
    position: absolute;
    top: 0; bottom: 0;
    width: 2rpx;
    background: #FFFFFF;
    box-shadow: 0 0 0 1rpx rgba(0, 0, 0, 0.18);
    z-index: 3;
    display: flex;
    align-items: center;
    justify-content: center;
    /* ⚠️ 这里必须允许命中：手势绑在本容器上，pointer-events:none 会让手柄完全拖不动。
        视觉上仍是 2rpx 细线，但用不可见的宽命中区（::before）扩大可点范围。 */
    pointer-events: auto;
    touch-action: none;
    cursor: ew-resize;

    /* 扩大命中区：左右各 24rpx 透明可点区域，视觉无变化但好按 */
    &::before {
      content: '';
      position: absolute;
      top: 0; bottom: 0;
      left: -24rpx;
      width: 50rpx;
    }
    &--active { box-shadow: 0 0 0 2rpx rgba(59, 130, 246, 0.6); }

    &__handle {
      /* 分界线只有 2rpx 宽，手柄靠 absolute 定位到中心，
         否则 margin-left 会把手柄整体推到分界线左边 */
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%);
      width: 64rpx;
      height: 64rpx;
      border-radius: 50%;
      background: #FFFFFF;
      box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.25);
      display: flex;
      align-items: center;
      justify-content: center;
      pointer-events: auto;
      cursor: ew-resize;
      user-select: none;
      -webkit-user-drag: none;
      svg {
        width: 32rpx;
        height: 32rpx;
        fill: none;
        stroke: #374151;
        stroke-width: 2;
        stroke-linecap: round;
        stroke-linejoin: round;
      }
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
