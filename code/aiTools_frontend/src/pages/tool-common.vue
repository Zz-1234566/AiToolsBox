<template>
  <!-- 顶部导航 -->
  <view class="topbar">
    <view class="topbar__back" hover-class="topbar__back--hover" aria-label="返回" @click="goBack">
      <svg viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
    </view>
  </view>

  <scroll-view scroll-y class="page-content">
    <!-- Hero（左右布局） -->
    <section class="hero-tool">
      <view class="hero-tool__icon">
        <view class="tool-icon tool-icon--lg" :class="'tool-icon--' + (toolInfo.iconType || 'image')">
          <text class="tool-icon__emoji">{{ toolInfo.emoji || '📄' }}</text>
        </view>
      </view>
      <view class="hero-tool__text">
        <text class="hero__title">{{ toolInfo.name || '工具详情' }}</text>
        <text class="hero__subtitle">{{ toolInfo.desc || '让 AI 帮你高效完成任务' }}</text>
      </view>
    </section>

    <!-- 步骤指示器（居左对齐） -->
    <view class="steps">
      <view class="step" :class="{ 'step--active': stepIndex === 0 }">
        <view class="step__num">1</view>
        <text class="step__label">{{ stepsArr[0] }}</text>
      </view>
      <view class="step" :class="{ 'step--active': stepIndex === 1 }">
        <view class="step__num">2</view>
        <text class="step__label">{{ stepsArr[1] }}</text>
      </view>
      <view class="step" :class="{ 'step--active': stepIndex === 2 }">
        <view class="step__num">3</view>
        <text class="step__label">{{ stepsArr[2] }}</text>
      </view>
    </view>

    <!-- ============ 工具特化 UI（按 toolId 分发） ============ -->

    <!-- 工作总结 -->
    <block v-if="toolId === 'work-summary'">
      <view class="date-card" @click="chooseDate">
        <view class="date-card__left">
          <view class="date-card__icon">
            <svg viewBox="0 0 24 24"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19a2 2 0 002 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z"/></svg>
          </view>
          <text class="date-card__value">{{ workDate }}</text>
        </view>
        <view class="date-card__chev">
          <svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
        </view>
      </view>
      <view class="content-card">
        <label class="content-card__label">工作内容</label>
        <textarea class="content-card__textarea" v-model="inputText" placeholder="请详细描述今天的工作内容、完成的任务、遇到的问题等…" :maxlength="2000"></textarea>
        <text class="content-card__counter">{{ inputText.length }}/2000</text>
      </view>
      <view class="example-section">
        <view class="example-section__title">快捷示例</view>
        <view class="example-grid">
          <view class="pill-btn" @click="applyQuickPrompt('今天的工作总结')">今天的工作总结</view>
          <view class="pill-btn" @click="applyQuickPrompt('本周工作总结')">本周工作总结</view>
          <view class="pill-btn" @click="applyQuickPrompt('项目进展汇报')">项目进展汇报</view>
          <view class="pill-btn" @click="applyQuickPrompt('自定义内容')">自定义内容</view>
        </view>
      </view>
    </block>

    <!-- 文档重点提取 / AI 文件解读：上传 + 文件列表（共用） -->
    <block v-if="toolId === 'doc-keypoint-extract' || toolId === 'ai-file-reader'">
      <view class="upload-card" @click="onFilePickerClick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">点击上传文件</text>
        <text class="upload-card__desc">{{ toolId === 'ai-file-reader' ? '支持 PDF、Word、TXT、Excel 等格式' : '支持 PDF、Word、TXT 格式' }}</text>
      </view>
      <view v-if="uploadedFiles.length > 0" class="section-title">已上传文件</view>
      <view v-if="uploadedFiles.length > 0" class="file-list">
        <view v-for="(f, idx) in uploadedFiles" :key="idx" class="file-item">
          <view class="file-item__icon">{{ (f.fileName || 'F').charAt(0).toUpperCase() }}</view>
          <view class="file-item__body">
            <text class="file-item__name">{{ f.fileName }}</text>
            <text class="file-item__meta">{{ f.size || '1.6 MB' }} · {{ f.date || '2025-09-22' }}</text>
          </view>
          <view class="file-item__close" @click="removeFile(idx)">
            <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          </view>
        </view>
      </view>
      <!-- AI 文件解读：提问卡 + 推荐问题 -->
      <block v-if="toolId === 'ai-file-reader'">
        <view class="ask-card">
          <label class="ask-card__label">向 AI 提问</label>
          <textarea class="ask-card__textarea" v-model="askText" placeholder="例如：这份文件的主要内容是什么？" :maxlength="500"></textarea>
          <text class="ask-card__counter">{{ askText.length }}/500</text>
          <view class="suggest-row">
            <text class="suggest-label">推荐问题</text>
            <view class="pill-btn" @click="applySuggest('主要内容总结')">主要内容总结</view>
            <view class="pill-btn" @click="applySuggest('核心观点是什么')">核心观点是什么</view>
            <view class="pill-btn" @click="applySuggest('有哪些关键条款')">有哪些关键条款</view>
            <view class="pill-btn" @click="applySuggest('请用简单语言解释')">请用简单语言解释</view>
          </view>
        </view>
      </block>
    </block>

    <!-- 周报生成：textarea + 3 列表 -->
    <block v-if="toolId === 'weekly-report'">
      <view class="content-card">
        <label class="content-card__label">本周工作内容</label>
        <textarea class="content-card__textarea" v-model="inputText" placeholder="请输入本周完成的工作内容、关键成果等…" :maxlength="2000"></textarea>
        <text class="content-card__counter">{{ inputText.length }}/2000</text>
      </view>
      <view class="add-item-card">
        <view class="add-item-card__title">完成事项</view>
        <view class="add-item-row">
          <input class="add-item-row__field" v-model="weeklyDoneText" placeholder="请输入本周完成的工作事项…" />
          <view class="add-item-row__action" @click="addWeeklyItem('done')">
            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <text>添加</text>
          </view>
        </view>
      </view>
      <view class="add-item-card">
        <view class="add-item-card__title">存在问题</view>
        <view class="add-item-row">
          <input class="add-item-row__field" v-model="weeklyProblemText" placeholder="请填写工作中遇到的问题…" />
          <view class="add-item-row__action" @click="addWeeklyItem('problem')">
            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <text>添加</text>
          </view>
        </view>
      </view>
      <view class="add-item-card">
        <view class="add-item-card__title">下周计划</view>
        <view class="add-item-row">
          <input class="add-item-row__field" v-model="weeklyPlanText" placeholder="请输入下周工作计划…" />
          <view class="add-item-row__action" @click="addWeeklyItem('plan')">
            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <text>添加</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 会议纪要：双输入 + 会议信息 + 存在问题 + 示例效果 + 下周计划 -->
    <block v-if="toolId === 'meeting-minutes' || toolId === 'mm'">
      <view class="input-grid">
        <view class="input-card" @click="onUploadRecording">
          <view class="input-card__icon">
            <svg viewBox="0 0 24 24"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>
          </view>
          <text class="input-card__label">上传录音</text>
          <text class="input-card__sub">支持 MP3、WAV 等</text>
        </view>
        <view class="input-card" @click="onInputMeetingContent">
          <view class="input-card__icon">
            <svg viewBox="0 0 24 24"><path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12z"/></svg>
          </view>
          <text class="input-card__label">输入会议内容</text>
          <text class="input-card__sub">粘贴或输入文字</text>
        </view>
      </view>
      <view class="form-card">
        <view class="form-card__title">会议信息（可编辑）</view>
        <view class="form-row" @click="onEditMeetingInfo('participants')">
          <text class="form-row__label">会议参与人</text>
          <text class="form-row__field">请输入参与人，支持多选</text>
          <view class="form-row__chev"><svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg></view>
        </view>
        <view class="form-row" @click="onEditMeetingInfo('time')">
          <text class="form-row__label">会议时间</text>
          <text class="form-row__field">请选择会议时间</text>
          <view class="form-row__chev"><svg viewBox="0 0 24 24"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19a2 2 0 002 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z"/></svg></view>
        </view>
      </view>
      <view class="add-item-card">
        <view class="add-item-row">
          <text class="add-item-row__label">存在问题</text>
          <input class="add-item-row__field" v-model="mmProblemText" placeholder="请填写会议中遇到的问题…" />
          <view class="add-item-row__action" @click="addMmItem('problem')">
            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <text>添加</text>
          </view>
        </view>
      </view>
      <view class="example-card">
        <view class="example-card__title">示例效果</view>
        <view class="example-list">
          <view class="example-item"><view class="example-item__icon"><svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/></svg></view><text class="example-item__label">会议主题</text></view>
          <view class="example-item"><view class="example-item__icon"><svg viewBox="0 0 24 24"><path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/></svg></view><text class="example-item__label">讨论要点</text></view>
          <view class="example-item"><view class="example-item__icon"><svg viewBox="0 0 24 24"><path d="M19 3h-4.18C14.4 1.84 13.3 1 12 1c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"/></svg></view><text class="example-item__label">待办事项</text></view>
          <view class="example-item"><view class="example-item__icon"><svg viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/></svg></view><text class="example-item__label">会议总结</text></view>
        </view>
      </view>
      <view class="add-item-card">
        <view class="add-item-row">
          <text class="add-item-row__label">下周计划</text>
          <input class="add-item-row__field" v-model="mmPlanText" placeholder="请输入下周工作计划…" />
          <view class="add-item-row__action" @click="addMmItem('plan')">
            <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
            <text>添加</text>
          </view>
        </view>
      </view>

      <!-- ============ 会议纪要结果渲染区（JSON 表格 / SSE Markdown） ============ -->
      <block v-if="meetingRoute === 'json'">
        <view class="section-title">生成结果（JSON 结构化）</view>
        <view class="mm-json-table">
          <view v-for="(item, idx) in meetingJsonResult" :key="item.key || idx" class="mm-json-row">
            <view class="mm-json-cell-title">
              <text class="mm-json-key">{{ item.key || '' }}</text>
              <text class="mm-json-title">{{ item.title || '' }}</text>
            </view>
            <view v-if="item.type === 'todo'" class="mm-json-cell-content">
              <text class="mm-json-checkbox">□</text>
              <text class="mm-json-content">{{ item.content || '' }}</text>
            </view>
            <view v-else class="mm-json-cell-content">
              <text class="mm-json-content">{{ item.content || '' }}</text>
            </view>
          </view>
          <view v-if="meetingJsonResult.length === 0" class="mm-json-empty">
            <text>暂无生成结果</text>
          </view>
        </view>
      </block>
      <block v-else-if="meetingRoute === 'sse'">
        <view class="section-title">生成结果（SSE 流式 Markdown）</view>
        <view class="mm-md-result">
          <rich-text :nodes="meetingMarkdownHtml"></rich-text>
        </view>
      </block>
    </block>

    <!-- 智能识别：上传 + 4 项识别类型列表 -->
    <block v-if="toolId === 'ocr-recognize' || toolId === 'ocr-recognition'">
      <view class="upload-card" @click="onFilePickerClick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">点击上传文件</text>
        <text class="upload-card__desc">支持 JPG、PNG、PDF 格式</text>
      </view>
      <view class="section-title">识别类型</view>
      <view class="recognize-list">
        <view v-for="(t, i) in ocrTypes" :key="i" class="recognize-item" @click="selectRecognizeType(t.code)">
          <view class="recognize-item__icon">
            <svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-1 7V3.5L18.5 9H13z"/></svg>
          </view>
          <view class="recognize-item__body">
            <text class="recognize-item__title">{{ t.title }}</text>
            <text class="recognize-item__sub">{{ t.sub }}</text>
          </view>
          <view class="recognize-item__chev">
            <svg viewBox="0 0 24 24"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
          </view>
        </view>
      </view>
    </block>

    <!-- 证件照换背景色：上传 + 3 色块 + 示例对比 -->
    <block v-if="toolId === 'id-photo-bg-change'">
      <view class="upload-card" @click="onFilePickerClick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">点击上传证件照</text>
        <text class="upload-card__desc">支持 JPG、PNG 格式</text>
      </view>
      <view class="section-title">选择背景色</view>
      <view class="color-picker">
        <view v-for="c in bgColors" :key="c.code" class="color-item" @click="selectBgColor(c.code)">
          <view class="color-item__chip" :class="[c.cls, { 'is-active': selectedBgColor === c.code }]"></view>
          <text class="color-item__label">{{ c.label }}</text>
        </view>
      </view>
      <view class="example-card">
        <view class="example-card__title">示例效果</view>
        <view class="example-compare">
          <view class="example-cell">
            <view class="example-cell__img">👤</view>
            <text class="example-cell__label">原图</text>
          </view>
          <text class="example-arrow">→</text>
          <view class="example-cell">
            <view class="example-cell__img example-cell__img--blue">👤</view>
            <text class="example-cell__label">换背景后</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 人像换背景图：上传 + tab + 4 缩略图 + 示例对比 -->
    <block v-if="toolId === 'portrait-bg-replace'">
      <view class="upload-card" @click="onFilePickerClick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">点击上传人物照片</text>
        <text class="upload-card__desc">支持 JPG、PNG 格式</text>
      </view>
      <view class="section-title">选择背景</view>
      <view class="tabs">
        <view class="tab-item" :class="{ 'is-active': bgTab === 'recommend' }" @click="bgTab = 'recommend'">推荐背景</view>
        <view class="tab-item" :class="{ 'is-active': bgTab === 'custom' }" @click="bgTab = 'custom'">自定义上传</view>
      </view>
      <view class="thumb-grid">
        <view v-for="(t, i) in bgThumbs" :key="i" class="thumb-item" :class="[t.cls, { 'is-active': selectedThumb === i }]" @click="selectedThumb = i"></view>
        <view class="thumb-item thumb-item--upload" v-if="bgTab === 'custom'">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
          <text>上传</text>
        </view>
      </view>
      <view class="example-card">
        <view class="example-card__title">示例效果</view>
        <view class="example-compare">
          <view class="example-cell">
            <view class="example-cell__img">👤</view>
            <text class="example-cell__label">原图</text>
          </view>
          <text class="example-arrow">→</text>
          <view class="example-cell">
            <view class="example-cell__img example-cell__img--with-bg">👤</view>
            <text class="example-cell__label">换背景后</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 图片压缩：上传 + 图片信息 + slider -->
    <block v-if="toolId === 'image-compress'">
      <view class="upload-card" @click="onFilePickerClick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">点击上传图片</text>
        <text class="upload-card__desc">支持 JPG、PNG 格式</text>
      </view>
      <view class="section-title">图片信息</view>
      <view class="image-info">
        <view class="image-info__thumb">🖼️</view>
        <view class="image-info__body">
          <text class="image-info__name">示例图片.jpg</text>
          <view class="image-info__meta">
            <text>大小 <text class="meta-strong">5.2 MB</text></text>
            <text>尺寸 <text class="meta-strong">1920×1080</text></text>
          </view>
        </view>
      </view>
      <view class="compress-settings">
        <view class="compress-settings__title">压缩设置</view>
        <view class="slider-row">
          <text class="slider-label">质量</text>
          <text class="slider-value">{{ compressQuality }}%</text>
        </view>
        <view class="slider-track" @click="onSliderTrackClick">
          <text class="slider-fill" :style="{ width: compressQuality + '%' }"></text>
          <text class="slider-thumb" :style="{ left: compressQuality + '%' }"></text>
        </view>
        <view class="estimate-row">
          <text class="estimate-label">预计压缩后大小</text>
          <text class="estimate-value">约 {{ (5.2 * compressQuality / 100).toFixed(1) }} MB</text>
        </view>
      </view>
    </block>

    <!-- 二维码生成：textarea + 样式/尺寸 + QR 码 -->
    <block v-if="toolId === 'qr-code-gen'">
      <view class="content-card">
        <label class="content-card__label">输入内容</label>
        <textarea class="content-card__textarea" v-model="qrContent" placeholder="请输入文本或 URL" :maxlength="200"></textarea>
        <text class="content-card__counter">{{ qrContent.length }}/200</text>
      </view>
      <view class="section-title">二维码样式</view>
      <view class="options-row">
        <view v-for="(s, i) in qrStyles" :key="i" class="option-item" :class="{ 'is-active': qrStyle === s }" @click="qrStyle = s">{{ s }}</view>
      </view>
      <view class="section-title">尺寸</view>
      <view class="options-row">
        <view v-for="(s, i) in qrSizes" :key="i" class="option-item" :class="{ 'is-active': qrSize === s.label }" @click="qrSize = s.label">{{ s.label }}</view>
      </view>
      <view class="qr-card">
        <view class="qr-code">
          <view v-for="(cell, i) in qrMatrix" :key="i" class="qr-cell" :class="{ 'is-dark': cell }"></view>
        </view>
        <view class="qr-info">
          <text class="qr-info__label">生成结果</text>
          <text class="qr-info__code">{{ qrContent || 'https://aitoolsbox.app' }}</text>
          <view class="qr-info__copy" @click="copyQrCode">
            <svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
            <text>复制链接</text>
          </view>
        </view>
      </view>
      <view class="tip">
        <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
        <text>请定期更换密码，避免使用相同密码</text>
      </view>
    </block>

    <!-- 密码生成：slider + 字符选项 + 密码框 + 强度 -->
    <block v-if="toolId === 'password-gen'">
      <view class="settings-card">
        <view class="settings-card__title">密码设置</view>
        <view class="slider-row">
          <text class="slider-label">长度</text>
          <text class="slider-value">{{ pwdLength }}</text>
        </view>
        <view class="slider-track" @click="onPwdLengthTrackClick">
          <text class="slider-fill" :style="{ width: ((pwdLength - 4) / 28 * 100) + '%' }"></text>
          <text class="slider-thumb" :style="{ left: ((pwdLength - 4) / 28 * 100) + '%' }"></text>
        </view>
        <view class="settings-card__title">包含字符</view>
        <view class="checkbox-row">
          <view v-for="(c, i) in pwdChars" :key="i" class="checkbox-item" @click="togglePwdChar(i)">
            <view class="checkbox-item__box" :class="{ 'is-checked': c.checked }">
              <svg v-if="c.checked" viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
            </view>
            <text>{{ c.label }}</text>
          </view>
        </view>
      </view>
      <view class="password-card">
        <view class="password-card__title">生成结果</view>
        <view class="password-display">
          <text class="password-display__text">{{ generatedPassword }}</text>
          <view class="password-display__copy" @click="copyPassword">
            <svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
          </view>
        </view>
        <view class="strength-row">
          <text class="strength-label">密码强度</text>
          <view class="strength-bar"><view class="strength-fill" :style="{ width: pwdStrength + '%' }"></view></view>
          <text class="strength-text">{{ pwdStrengthLabel }}</text>
        </view>
      </view>
      <view class="tip">
        <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
        <text>请定期更换密码，避免使用相同密码</text>
      </view>
    </block>

    <!-- 待办清单：输入新任务 + tab + 任务列表 -->
    <block v-if="toolId === 'todo-list'">
      <view class="add-task-card">
        <textarea class="add-task-card__textarea" v-model="newTaskText" placeholder="添加新任务…"></textarea>
        <view class="add-task-card__add" @click="addTask">
          <svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
        </view>
      </view>
      <view class="tabs">
        <view class="tab-item" :class="{ 'is-active': taskTab === 'all' }" @click="taskTab = 'all'">全部</view>
        <view class="tab-item" :class="{ 'is-active': taskTab === 'doing' }" @click="taskTab = 'doing'">进行中</view>
        <view class="tab-item" :class="{ 'is-active': taskTab === 'done' }" @click="taskTab = 'done'">已完成</view>
      </view>
      <view class="task-list">
        <view v-for="(t, i) in filteredTasks" :key="i" class="task-item" :class="{ 'is-done': t.done }">
          <view class="task-item__checkbox" :class="{ 'is-checked': t.done }" @click="toggleTask(i)">
            <svg v-if="t.done" viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
          </view>
          <view class="task-item__body">
            <text class="task-item__name">{{ t.name }}</text>
            <view class="task-item__meta">
              <text class="task-priority" :class="'task-priority--' + t.priority">{{ t.priorityLabel }}</text>
              <text>{{ t.date }}</text>
            </view>
          </view>
          <view class="task-item__delete" @click="deleteTask(i)">
            <svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
          </view>
        </view>
      </view>
    </block>

    <view class="safe-area-bottom"></view>
  </scroll-view>

  <!-- 底部按钮 -->
  <view class="bottom-action">
    <button class="btn btn--primary btn--block" :disabled="loading" @click="handleGenerate">{{ bottomActionText }}</button>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { requireLogin } from '@/utils/auth'
import PageHeader from '@/components/PageHeader.vue'
import InputSwitcher from '@/components/InputSwitcher.vue'
import TextInputArea from '@/components/TextInputArea.vue'
import FileInputArea from '@/components/FileInputArea.vue'
import AudioInputArea from '@/components/AudioInputArea.vue'
import PromptInputArea from '@/components/PromptInputArea.vue'
import ResultArea from '@/components/ResultArea.vue'
import BatchFilePicker from '@/components/BatchFilePicker.vue'
import { uploadFileApi, batchUpload, ocrBatchUpload, aiFileReaderBatchUpload, batchCompleted, meetingMinutesDecideRoute, meetingMinutesJson } from '@/api/ai.js'
import { streamRequest, streamUpload } from '../api/stream'
import { formatAiResult } from '@/utils/format'
import { request } from '@/api/request'
import { marked } from 'marked'
import { promptListApi, systemPromptListApi, generatePromptApi, promptAddApi } from '@/api/prompt'
import { getTool, validate } from '@/config/tools'
import BatchResultCards from '@/components/BatchResultCards.vue'

const toolId = ref('')
const currentInputType = ref('text')
const inputText = ref('')
const fileName = ref('')
const filePath = ref('')
const fileObj = ref(null)          // 原生 File/Blob 对象（H5 端用于 multipart 上传且保留真实文件名）
const uploading = ref(false)
const batchPickerRef = ref(null)   // BatchFilePicker 组件引用：通过 getFiles() 拿当前文件列表
const fileUrl = ref('')
const loading = ref(false)
const resultContent = ref('')
const batchCards = ref([])        // 批量结果卡片数据：[{ index, fileName, status, costMs, errorMsg, output }]
const batchTotal = ref(0)          // 批量任务总文件数（用于卡片显示 i/N）
const batchProgress = ref(0)       // 批量任务已处理数（用于 loading 提示）
const promptFormatText = ref('')    // 用户自定义格式提示词
const promptGenerateText = ref('')  // 用户自定义生成内容提示词
const formatPromptDisplay = ref('')  // 系统统一管理的格式提示词（只读）
const promptPickerTarget = ref('generate') // 选择弹窗当前填充的目标输入框（format/generate）
const selectedPromptId = ref('')      // 最近选中的提示词 id（可随 document-summary 一并提交）
const userPromptList = ref([])    // 用户自定义提示词（按用途过滤）
const systemPromptList = ref([])  // 系统提示词（按用途过滤）
const showPromptPicker = ref(false) // 是否显示选择弹窗
const currentTab = ref('system')    // 弹窗当前 tab：system / user / ai
const aiRequirement = ref('')        // AI 生成 tab 的需求输入
const aiGeneratedText = ref('')      // AI 生成的提示词预览
const aiLoading = ref(false)         // AI 生成中（防重复点）
const showSaveNameModal = ref(false) // 应用并保存时弹出的命名小窗
const saveNameInput = ref('')        // 命名小窗的输入
const saveNameSaving = ref(false)    // 命名保存中（防重复点）


// ===== 工具详情元数据 =====
const TOOL_META = {
  'work-summary':        { iconType: 'image',  emoji: '📝', action: 'AI 生成总结', steps: ['选日期', '填写内容', '生成总结'] },
  'doc-keypoint-extract':{ iconType: 'image',  emoji: '📄', action: '开始处理', steps: ['上传文件', 'AI 提取', '查看结果'] },
  'ai-file-reader':      { iconType: 'image',  emoji: '📄', action: '开始解读', steps: ['上传文件', '提问', '获取答案'] },
  'weekly-report':       { iconType: 'image',  emoji: '📝', action: '生成周报', steps: ['填写内容', 'AI 生成', '预览下载'] },
  'meeting-minutes':     { iconType: 'meeting',emoji: '🎯', action: '生成周报', steps: ['上传会议内容', 'AI 整理', '生成纪要'] },
  'mm':                  { iconType: 'meeting',emoji: '🎯', action: '生成周报', steps: ['上传会议内容', 'AI 整理', '生成纪要'] },
  'ocr-recognize':       { iconType: 'ocr',    emoji: '🔍', action: '开始识别', steps: ['上传文件', '选择类型', '获取结果'] },
  'ocr-recognition':     { iconType: 'ocr',    emoji: '🔍', action: '开始识别', steps: ['上传文件', '选择类型', '获取结果'] },
  'id-photo-bg-change':  { iconType: 'image',  emoji: '🖼️', action: '生成证件照', steps: ['上传照片', '选择背景', '生成下载'] },
  'portrait-bg-replace': { iconType: 'image',  emoji: '🎨', action: '开始处理', steps: ['上传照片', '选择背景', '生成效果'] },
  'image-compress':      { iconType: 'dev',    emoji: '🗜️', action: '开始压缩', steps: ['上传图片', '设置参数', '开始压缩'] },
  'qr-code-gen':         { iconType: 'image',  emoji: '🔳', action: '保存二维码', steps: ['输入内容', '选择样式', '生成下载'] },
  'password-gen':        { iconType: 'image',  emoji: '🔐', action: '生成新密码', steps: ['设置参数', '生成密码', '复制使用'] },
  'todo-list':           { iconType: 'image',  emoji: '✅', action: '保存清单', steps: ['添加任务', '完成任务', '保存清单'] }
}

const toolInfo = computed(() => {
  const base = getTool(toolId.value)
  const meta = TOOL_META[toolId.value] || {}
  return {
    ...base,
    iconType: meta.iconType || 'image',
    emoji: meta.emoji || '📄'
  }
})

const stepsArr = computed(() => {
  const meta = TOOL_META[toolId.value]
  return (meta && meta.steps) || ['步骤 1', '步骤 2', '步骤 3']
})

const stepIndex = ref(0)

const bottomActionText = computed(() => {
  const meta = TOOL_META[toolId.value]
  return (meta && meta.action) || '开始处理'
})

const goBack = () => {
  uni.navigateBack({ delta: 1 })
}

// ===== 各工具特化状态 =====
const workDate = ref('2025-09-23')
const chooseDate = () => { uni.showToast({ title: '日期选择开发中', icon: 'none' }) }
const applyQuickPrompt = (text) => { inputText.value = text }

const askText = ref('')
const applySuggested = (text) => { askText.value = text }
const uploadedFiles = ref([{ fileName: '示例文件.pdf', size: '1.6 MB', date: '2025-09-22' }])
const onFilePickerClick = () => { uni.showToast({ title: '文件选择开发中', icon: 'none' }) }
const removeFile = (idx) => { uploadedFiles.value.splice(idx, 1) }

const weeklyDoneText = ref('')
const weeklyProblemText = ref('')
const weeklyPlanText = ref('')
const addWeeklyItem = (kind) => {
  const map = { done: weeklyDoneText, problem: weeklyProblemText, plan: weeklyPlanText }
  const labels = { done: '完成事项', problem: '存在问题', plan: '下周计划' }
  const text = map[kind].value.trim()
  if (!text) { uni.showToast({ title: '请输入内容', icon: 'none' }); return }
  uni.showToast({ title: '已添加' + labels[kind], icon: 'success' })
  map[kind].value = ''
}

const mmProblemText = ref('')
// ===== 会议纪要渲染状态（按 decide-route 分 JSON/SSE） =====
const meetingRoute = ref('')  // '' | 'sse' | 'json'
const meetingJsonResult = ref([])  // JSON 数组：[{ key, title, type, content }]
const meetingMarkdownText = ref('')  // SSE markdown 累积文本
const meetingMarkdownHtml = computed(() => {
  try {
    return meetingMarkdownText.value ? marked.parse(meetingMarkdownText.value) : ''
  } catch (e) {
    return ''
  }
})

const mmPlanText = ref('')
const onUploadRecording = () => { uni.showToast({ title: '录音上传开发中', icon: 'none' }) }
const onInputMeetingContent = () => { uni.showToast({ title: '请在下方输入会议内容', icon: 'none' }) }
const onEditMeetingInfo = (kind) => { uni.showToast({ title: '会议信息编辑开发中', icon: 'none' }) }
const addMmItem = (kind) => {
  const map = { problem: mmProblemText, plan: mmPlanText }
  const labels = { problem: '存在问题', plan: '下周计划' }
  const text = map[kind].value.trim()
  if (!text) { uni.showToast({ title: '请输入内容', icon: 'none' }); return }
  uni.showToast({ title: '已添加' + labels[kind], icon: 'success' })
  map[kind].value = ''
}

const ocrTypes = ref([
  { code: 'text', title: '文字识别 OCR', sub: '识别图片中的文字内容' },
  { code: 'table', title: '表格识别', sub: '识别图片中的表格结构' },
  { code: 'receipt', title: '票据识别', sub: '识别发票、收据等票据' },
  { code: 'idcard', title: '证件识别', sub: '识别身份证、护照等证件' }
])
const selectRecognizeType = (code) => { uni.showToast({ title: '识别类型：' + code + '（开发中）', icon: 'none' }) }

const bgColors = ref([
  { code: 'white', cls: 'color-item__chip--white', label: '白色' },
  { code: 'blue',  cls: 'color-item__chip--blue',  label: '蓝色' },
  { code: 'red',   cls: 'color-item__chip--red',   label: '红色' }
])
const selectedBgColor = ref('blue')
const selectBgColor = (code) => { selectedBgColor.value = code }

const bgTab = ref('recommend')
const selectedThumb = ref(0)
const bgThumbs = ref([{ cls: 'thumb-item--grass' }, { cls: 'thumb-item--mountain' }, { cls: 'thumb-item--sea' }])

const compressQuality = ref(70)
const onSliderTrackClick = () => { compressQuality.value = 50 }

const qrContent = ref('')
const qrStyles = ref(['默认', '艺术', '个性化'])
const qrStyle = ref('默认')
const qrSizes = ref([{ label: '128×128', val: 128 }, { label: '256×256', val: 256 }, { label: '512×512', val: 512 }])
const qrSize = ref('256×256')
const qrMatrix = ref(Array.from({ length: 49 }, () => Math.random() > 0.5))
const copyQrCode = () => {
  uni.setClipboardData({ data: qrContent.value || 'https://aitoolsbox.app' })
}

const pwdLength = ref(16)
const pwdChars = ref([{ label: '大小写字母', checked: true }, { label: '数字', checked: true }, { label: '特殊符号', checked: true }])
const generatedPassword = ref('7Fq9ik2P@8m3Z#6t')
const pwdStrength = ref(80)
const pwdStrengthLabel = ref('强')
const onPwdLengthTrackClick = () => { pwdLength.value = 12 }
const togglePwdChar = (i) => { pwdChars.value[i].checked = !pwdChars.value[i].checked }
const copyPassword = () => {
  uni.setClipboardData({ data: generatedPassword.value })
  uni.showToast({ title: '已复制密码', icon: 'success' })
}

const taskTab = ref('all')
const newTaskText = ref('')
const tasks = ref([
  { name: '完成项目需求文档', priority: 'high', priorityLabel: '高', date: '2025-09-22', done: true },
  { name: '整理本月财务数据', priority: 'medium', priorityLabel: '中', date: '2025-09-23', done: false },
  { name: '设计新工具图标', priority: 'low', priorityLabel: '低', date: '2025-09-24', done: false }
])
const filteredTasks = computed(() => {
  if (taskTab.value === 'doing') return tasks.value.filter(t => !t.done)
  if (taskTab.value === 'done') return tasks.value.filter(t => t.done)
  return tasks.value
})
const addTask = () => {
  const text = newTaskText.value.trim()
  if (!text) { uni.showToast({ title: '请输入任务内容', icon: 'none' }); return }
  tasks.value.unshift({ name: text, priority: 'medium', priorityLabel: '中', date: new Date().toISOString().slice(0, 10), done: false })
  newTaskText.value = ''
}
const toggleTask = (i) => { tasks.value[i].done = !tasks.value[i].done }
const deleteTask = (i) => { tasks.value.splice(i, 1) }

const pickerTabs = [
  { key: 'system', label: '系统提示词' },
  { key: 'user', label: '我的提示词' },
  { key: 'ai', label: 'AI 生成' }
]

// AI 生成 tab 的需求占位提示（按 target 给场景化示例）
const aiRequirementPlaceholder = computed(() => {
  if (promptPickerTarget.value === 'format') {
    return '例如：按四段式输出：已完成/进行中/问题/下一步'
  }
  return '例如：语气正式，结构化，使用项目符号'
})


// 是否是支持多文件批量上传的工具（tools.js 中定义了 fileRule）
const isBatchTool = computed(() => !!toolInfo.value.fileRule)

// 通用校验（按工具 + 输入方式）：返回第一个失败的错误文案，null = 通过
// 在 handleGenerate 入口前置校验，不通过直接 return + toast，不进 if-else 分支
const runValidation = () => validate(toolId.value, currentInputType.value, {
  filePath: filePath.value,
  batchFiles: (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : [],
  inputText: inputText.value,
  promptFormat: promptFormatText.value,
  promptGenerate: promptGenerateText.value,
  token: !!uni.getStorageSync('token')
})

// 拉取系统格式提示词（只读展示用）
const fetchSystemFormat = async () => {
  if (!toolId.value) return
  try {
    const res = await request({
      url: '/api/prompt/system/format',
      method: 'GET',
      data: { toolCode: toolId.value }
    })
    if (res && res.code === 200) {
      formatPromptDisplay.value = (res.data && typeof res.data === 'string') ? res.data : ''
    }
  } catch (e) {
    console.error('拉取系统格式提示词失败:', e)
  }
}

onLoad((option) => {
  if (!requireLogin()) return
  toolId.value = option.id || ''
  // 初始化默认输入方式
  currentInputType.value = toolInfo.value.defaultInput
    || (toolInfo.value.inputTypes && toolInfo.value.inputTypes[0])
    || 'text'
  // 拉取系统统一管理的格式提示词（只读展示）
  fetchSystemFormat()
})

// 切换输入方式时重置状态
const switchInputType = (type) => {
  currentInputType.value = type
  // 切换时清空之前的输入
  inputText.value = ''
  filePath.value = ''
  fileName.value = ''
  fileObj.value = null
  fileUrl.value = ''
  // 清空 BatchFilePicker 组件内部文件列表
  if (batchPickerRef.value && batchPickerRef.value.clear) {
    batchPickerRef.value.clear()
  }
}

const onFileChoose = async (info) => {
  // UploadArea 组件返回 { filePath, file }，兼容旧版字符串
  const localPath = (typeof info === 'string') ? info : (info && info.filePath)
  if (!localPath) return
  // 原生 File/Blob 对象（H5 端 tempFiles[0]，保留真实文件名，供 multipart+SSE 上传）
  fileObj.value = (info && info.file) || null
  // 优先用 File 的真实文件名，回退到路径文件名
  const rawName = (fileObj.value && fileObj.value.name) || localPath.split('/').pop()
  filePath.value = localPath
  fileName.value = rawName || '已选择文件'
  // 上传到后端通用用户文件区（prefix=file）
  uploading.value = true
  try {
    const res = await uploadFileApi(localPath, 'file')
    const url = (res && res.data && res.data.fileUrl) || ''
    fileUrl.value = url
    uni.showToast({ title: url ? '上传成功' : '上传失败', icon: 'none' })
  } catch (e) {
    fileUrl.value = ''
    uni.showToast({ title: '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

// 打开选择弹窗：同时拉取该用途下的用户自定义 + 系统提示词，target 指定填充哪个输入框（format/generate）
const openPromptPicker = async (target = 'generate') => {
  promptPickerTarget.value = target
  // 默认显示系统提示词 tab；重置 AI 生成 tab 的状态（避免上次残留）
  currentTab.value = 'system'
  aiRequirement.value = ''
  aiGeneratedText.value = ''
  aiLoading.value = false
  showSaveNameModal.value = false
  saveNameInput.value = ''
  saveNameSaving.value = false
  try {
    // 用户自定义提示词（过滤用途，按当前工具 toolCode 隔离）
    const userRes = await promptListApi(toolId.value)
    userPromptList.value = (userRes.data || []).filter(p => p.promptUse === target)
    // 系统提示词（过滤用途）
    const sysRes = await systemPromptListApi(toolId.value)
    systemPromptList.value = (sysRes.data || []).filter(p => p.promptUse === target)
  } catch (e) {
    userPromptList.value = []
    systemPromptList.value = []
  }
  showPromptPicker.value = true
}

// 关闭弹窗：点遮罩或关闭按钮
const closePromptPicker = () => {
  showPromptPicker.value = false
  showSaveNameModal.value = false
  saveNameInput.value = ''
  // 注意：textarea 已填内容（promptFormatText/promptGenerateText）保留，不清
}

// Tab 切换（不重新请求，只换显示）
const switchTab = (key) => {
  if (currentTab.value === key) return
  currentTab.value = key
}

// 选中一条提示词填入对应输入框（用户/系统提示词均带 promptText）
const selectPrompt = (item) => {
  selectedPromptId.value = (item && item.id != null) ? item.id : ''
  if (promptPickerTarget.value === 'format') {
    promptFormatText.value = item.promptText
  } else {
    promptGenerateText.value = item.promptText
  }
  showPromptPicker.value = false
}

// AI 生成：调后端文本模型，按用户需求生成提示词
const handleAiGenerate = async () => {
  const requirement = aiRequirement.value.trim()
  if (!requirement) {
    uni.showToast({ title: '请输入参考需求', icon: 'none' })
    return
  }
  if (aiLoading.value) return
  aiLoading.value = true
  try {
    const res = await generatePromptApi({
      toolCode: toolId.value,
      toolName: toolInfo.value.name || '',
      toolDesc: toolInfo.value.desc || '',
      promptUse: promptPickerTarget.value,
      requirement
    })
    const text = (res && res.data && res.data.promptText) || ''
    if (!text) {
      uni.showToast({ title: '生成结果为空，请重试', icon: 'none' })
      return
    }
    aiGeneratedText.value = text
  } catch (e) {
    // request.js 已经 uni.showToast 错误消息，这里仅记日志
    console.error('AI generate failed:', e)
  } finally {
    aiLoading.value = false
  }
}

// 应用生成结果（只填入，不保存）
const applyGenerated = () => {
  const text = aiGeneratedText.value
  if (!text) return
  // AI 生成的内容无 id，清空 selectedPromptId
  selectedPromptId.value = ''
  if (promptPickerTarget.value === 'format') {
    promptFormatText.value = text
  } else {
    promptGenerateText.value = text
  }
  showPromptPicker.value = false
}

// 打开"应用并保存"命名弹窗：默认值 = 工具名 + 用途 + 提示词 + 自增数字
const openSaveNameModal = () => {
  if (!aiGeneratedText.value) return
  const baseName = buildDefaultPromptName()
  saveNameInput.value = baseName
  showSaveNameModal.value = true
}

const cancelSaveName = () => {
  showSaveNameModal.value = false
  saveNameInput.value = ''
}

// 计算默认提示词名：{工具名}{用途}提示词{自增数字}
// 例：智能识别 + 格式 + 提示词1；下次新增 → 智能识别格式提示词2
// 自增范围：仅匹配同工具同用途的现有命名
const buildDefaultPromptName = () => {
  const toolName = (toolInfo.value && toolInfo.value.name) || '提示词'
  const useLabel = promptPickerTarget.value === 'format' ? '格式' : '生成内容'
  const prefix = toolName + useLabel + '提示词'
  const re = new RegExp('^' + escapeRegExp(prefix) + '(\d+)$')
  let maxNum = 0
  for (const p of userPromptList.value) {
    const n = (p.promptName || '').match(re)
    if (n) {
      const num = parseInt(n[1], 10)
      if (num > maxNum) maxNum = num
    }
  }
  return prefix + (maxNum + 1)
}

// RegExp 转义辅助
const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// 确认保存：填入 textarea + 调 promptAddApi
const confirmSaveName = async () => {
  if (saveNameSaving.value) return
  const text = aiGeneratedText.value
  const name = saveNameInput.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入名称', icon: 'none' })
    return
  }
  saveNameSaving.value = true
  try {
    await promptAddApi(text, promptPickerTarget.value, toolId.value, name)
    // 填入 textarea
    selectedPromptId.value = ''
    if (promptPickerTarget.value === 'format') {
      promptFormatText.value = text
    } else {
      promptGenerateText.value = text
    }
    // 刷新 userPromptList（让用户能看到新保存的）
    try {
      const userRes = await promptListApi(toolId.value)
      userPromptList.value = (userRes.data || []).filter(p => p.promptUse === promptPickerTarget.value)
    } catch (e) { /* 刷新失败不影响主流程 */ }
    uni.showToast({ title: '已保存到我的提示词', icon: 'success' })
    showSaveNameModal.value = false
    showPromptPicker.value = false
  } catch (e) {
    // request.js 已 toast 错误（同名会提示"该工具下已存在同名提示词"）
    console.error('Save generated prompt failed:', e)
  } finally {
    saveNameSaving.value = false
  }
}

// 通用 SSE 文本流式输出（打字机效果）：work-summary / weekly-report / meeting-minutes 共用
// 入参 url 为后端流式接口地址；返回拼接后的完整文本
const runTextStream = (url) => {
  return new Promise((resolve, reject) => {
    let fullText = ''
    resultContent.value = ''
    const charQueue = []
    let streamDone = false
    let typeTimer = null

    const flushChar = () => {
      if (charQueue.length > 0) {
        resultContent.value += charQueue.shift()
      }
      // 流结束且队列排空后收尾
      if (streamDone && charQueue.length === 0) {
        if (typeTimer) {
          clearInterval(typeTimer)
          typeTimer = null
        }
        resolve(fullText)
      }
    }

    streamRequest({
      url,
      data: {
        content: inputText.value,
        promptFormat: promptFormatText.value,
        promptGenerate: promptGenerateText.value
      },
      onChunk: (chunk) => {
        fullText += chunk
        for (const ch of chunk) {
          charQueue.push(ch)
        }
        if (!typeTimer) {
          typeTimer = setInterval(flushChar, 20)
        }
      },
      onDone: () => {
        streamDone = true
        // 队列已空则立即结束，否则等 flushChar 排空后 resolve
        if (charQueue.length === 0) {
          if (typeTimer) {
            clearInterval(typeTimer)
            typeTimer = null
          }
          resolve(fullText)
        }
      },
      onError: (err) => {
        if (typeTimer) {
          clearInterval(typeTimer)
          typeTimer = null
        }
        reject(err)
      }
    })
  })
}

/**
 * 轮询批量任务结果：前端驱动（自适应间隔）
 *   - 有新 items 时：立即追加渲染 + 立即再拉
 *   - 无新 items 时：sleep 1.5s 再拉
 *   - 终态（status >= 2）时：停
 * @param {String} batchId - 批量任务 ID
 * @param {String} toolCode - 当前工具编码（预留：以后按 toolCode 决定卡片渲染方式）
 */
const pollBatchCompleted = async (batchId, toolCode) => {
  let since = 0
  let aborted = false
  // 用户切走 / 重新生成时停止轮询
  const stop = () => { aborted = true }
  // 用 uni.$once 监听页面卸载（uniapp 无 onUnload composable，简单粗暴）
  const origUnload = uni.$once
  // 简易实现：跑 1000 次上限（每个文件 sleep 1.5s + AI 处理 5-30s 足够）
  const MAX_LOOPS = 200
  for (let i = 0; i < MAX_LOOPS && !aborted; i++) {
    let res
    try {
      res = await batchCompleted(batchId, since)
    } catch (e) {
      // 拉取失败：可能是网络抖动，继续重试
      await new Promise(r => setTimeout(r, 1500))
      continue
    }
    if (!res) {
      await new Promise(r => setTimeout(r, 1500))
      continue
    }
    // 追加新 items
    const newItems = res.results || []
    if (newItems.length) {
      batchCards.value = batchCards.value.concat(newItems)
      since += newItems.length
      batchProgress.value = res.processedIndex || since
    }
    // 终态：status 2=COMPLETED 3=PARTIAL 4=FAILED
    const status = res.status
    if (status === 2 || status === 3 || status === 4) {
      break
    }
    // 没新结果 + 还在进行中：等一下再拉
    if (newItems.length === 0) {
      await new Promise(r => setTimeout(r, 1500))
    }
  }
  return stop
}

const handleGenerate = async () => {
  // 通用前置校验（按 tools.js 的 validateRules；不通过直接 toast + return，不进 if-else 分支）
  const err = runValidation()
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }

  loading.value = true
  resultContent.value = ''
  // 批量场景：清空卡片数组（保证重新生成时刷新）
  batchCards.value = []
  batchTotal.value = 0
  batchProgress.value = 0

  try {
    const id = toolId.value

    if (id === 'doc-keypoint-extract') {
      // 文档重点提取：B2 多文件批量（轮询方案）
      // 流程：batchUpload 拿 batchId → 轮询 batchCompleted 拉增量 items → 渲染到 BatchResultCards
      const docBatchFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const { batchId, fileCount } = await batchUpload({
        files: docBatchFiles,
        fields: {
          promptFormat: promptFormatText.value,
          promptGenerate: promptGenerateText.value,
          promptId: selectedPromptId.value
        }
      })
      batchTotal.value = fileCount
      await pollBatchCompleted(batchId, 'doc-keypoint-extract')

    } else if (id === 'ai-file-reader') {
      // AI 文件解读：B2 多文件批量（轮询方案），后端只接单个 prompt 字符串
      // 格式提示词 + 生成内容提示词拼接后传入；两者皆空时后端用默认解读提示词
      const readerFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const promptParts = []
      if (promptFormatText.value.trim()) promptParts.push(promptFormatText.value.trim())
      if (promptGenerateText.value.trim()) promptParts.push(promptGenerateText.value.trim())
      const { batchId, fileCount } = await aiFileReaderBatchUpload({
        files: readerFiles,
        fields: {
          prompt: promptParts.join('\n\n')
        }
      })
      batchTotal.value = fileCount
      await pollBatchCompleted(batchId, 'ai-file-reader')
    } else if (id === 'weekly-report') {
      // 周报生成：SSE 流式输出（前置校验已统一处理）
      const fullText = await runTextStream('/api/ai-office/weekly-report/stream')
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'meeting-minutes') {
      // 会议纪要：先调 decide-route 决定 SSE 流式还是 JSON 同步，再分支处理
      const route = await meetingMinutesDecideRoute({
        content: inputText.value,
        promptFormat: promptFormatText.value,
        promptGenerate: promptGenerateText.value,
        promptId: selectedPromptId.value || undefined
      })
      meetingRoute.value = route
      meetingJsonResult.value = []
      meetingMarkdownText.value = ''
      resultContent.value = ''
      if (route === 'json') {
        // JSON 路径：同步调 /json 端点，等 AI 完全返回后一次性拿到结构化数据，渲染成表格
        const res = await meetingMinutesJson({
          content: inputText.value,
          promptFormat: promptFormatText.value,
          promptGenerate: promptGenerateText.value,
          promptId: selectedPromptId.value || undefined
        })
        try {
          const parsed = JSON.parse(res.data || '[]')
          meetingJsonResult.value = Array.isArray(parsed) ? parsed : []
        } catch (e) {
          console.error('meeting-minutes JSON parse error:', e)
          meetingJsonResult.value = []
        }
      } else {
        // SSE 路径：调 /stream 端点流式 markdown 文本，用 marked 渲染
        await runTextStream('/api/ai-office/meeting-minutes/stream')
        meetingMarkdownText.value = resultContent.value
      }

    } else if (id === 'ocr-recognize') {
      // OCR 智能识别：上传图片/PDF → 腾讯云 OCR → 调 AI 整理（前置校验已统一处理）
      // 兼容两种模式：老用户用单文件 filePath，新用户用组件多文件
      const ocrFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const useBatch = ocrFiles.length > 0

      if (useBatch) {
        // 多文件模式：轮询方案
        const { batchId, fileCount } = await ocrBatchUpload({
          files: ocrFiles,
          fields: {
            promptFormat: promptFormatText.value,
            promptGenerate: promptGenerateText.value
          }
        })
        batchTotal.value = fileCount
        await pollBatchCompleted(batchId, 'ocr-recognize')
      } else {
        // 单文件模式：保留原打字机效果
        let fullText = ''
        resultContent.value = ''
        const charQueue = []
        let streamDone = false
        let typeTimer = null
        await new Promise((resolve, reject) => {
          const flushChar = () => {
            if (charQueue.length > 0) resultContent.value += charQueue.shift()
            if (streamDone && charQueue.length === 0) {
              if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
              resolve()
            }
          }
          streamUpload({
            url: '/api/ai-office/ocr-recognize/stream',
            file: filePath.value,
            fields: {
              promptFormat: promptFormatText.value,
              promptGenerate: promptGenerateText.value
            },
            onChunk: (chunk) => {
              fullText += chunk
              for (const ch of chunk) charQueue.push(ch)
              if (!typeTimer) typeTimer = setInterval(flushChar, 20)
            },
            onDone: () => {
              streamDone = true
              if (charQueue.length === 0) {
                if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
                resolve()
              }
            },
            onError: (err) => {
              if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
              uni.showModal({ title: '请求失败', content: err && err.message ? err.message : '未知错误', showCancel: false })
              reject(err)
            }
          })
        })
        resultContent.value = formatAiResult(fullText)
      }

    } else if (id === 'work-summary') {
      // 工作总结：SSE 流式输出（前置校验已统一处理）
      const fullText = await runTextStream('/api/ai-office/work-summary/stream')
      // 流式完成后格式化（兜底分段，即使 AI 没换行也能分行展示）
      resultContent.value = formatAiResult(fullText)
    } else if (id === 'id-photo-bg-change' || id === 'portrait-bg-replace' || id === 'image-compress') {
      // 去背景 + 图片压缩：后端暂未实现
      uni.showToast({ title: '该工具开发中', icon: 'none' })
      return

    } else if (id === 'qr-code-gen' || id === 'password-gen' || id === 'todo-list') {
      // 二维码 + 密码生成 + 待办清单：后端暂未实现
      uni.showToast({ title: '该工具开发中', icon: 'none' })
      return
      // 去背景：后端暂未实现
      uni.showToast({ title: '该工具开发中', icon: 'none' })
      return
      
    } else {
      // 暂未接入后端的工具，使用模拟数据
      resultContent.value = `【${toolInfo.value.name}】\n\n这是模拟生成的结果。\n\n后续接入后端接口后会返回真实结果。`
    }
    
  } catch (err) {
    console.error('Generate error:', err)
    resultContent.value = (err && err.message) || '处理失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
  /* padding-bottom 移到 .page-content（scroll-view 内部），让滚动内容能滚到按钮上方 */
}

.page-content {
  flex: 1;
  padding: 0 $spacing-md 160rpx; /* 底部 160rpx 给 .bottom-action 留空间，避免遮挡滚动内容 */
}

.tool-desc {
  padding: $spacing-md 0;
  
  text {
    font-size: $font-size-md;
    color: $text-secondary;
    line-height: 1.6;
  }
}
.prompt-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: rgba(0,0,0,0.5);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  
  .prompt-picker {
    width: 80%;
    max-height: 70vh;
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-lg;
    display: flex;
    flex-direction: column;
    
    .prompt-picker-title {
      font-size: $font-size-lg;
      font-weight: 600;
      color: $text-primary;
      text-align: center;
      margin-bottom: $spacing-md;
    }

    // Tab 切换栏
    .prompt-tabs {
      display: flex;
      border-bottom: 1rpx solid $divider-color;
      margin-bottom: $spacing-sm;

      .prompt-tab {
        flex: 1;
        text-align: center;
        padding: $spacing-sm 0;
        font-size: $font-size-sm;
        color: $text-secondary;
        position: relative;

        &.active {
          color: $text-primary;
          font-weight: 600;

          &::after {
            content: '';
            position: absolute;
            left: 50%;
            bottom: 0;
            transform: translateX(-50%);
            width: 40rpx;
            height: 4rpx;
            background-color: $text-primary;
            border-radius: 2rpx;
          }
        }
      }
    }
    
    .prompt-picker-list {
      max-height: 50vh;
    }
    
    .prompt-group-title {
      font-size: $font-size-sm;
      color: $text-tertiary;
      padding: $spacing-sm $spacing-md;
    }
    
    .prompt-empty {
      text-align: center;
      padding: $spacing-xl 0;
      color: $text-tertiary;
      font-size: $font-size-sm;
    }
    
    .prompt-picker-item {
      padding: $spacing-md;
      border-bottom: 1rpx solid $divider-color;
      
      .prompt-picker-text {
        font-size: $font-size-sm;
        color: $text-primary;
        white-space: pre-wrap;
      }
    }

    // ============ AI 生成 tab 样式 ============
    .ai-gen-form {
      padding: $spacing-sm $spacing-md;

      .ai-gen-label {
        display: block;
        font-size: $font-size-sm;
        color: $text-secondary;
        margin-bottom: $spacing-xs;
      }

      .ai-gen-textarea {
        width: 100%;
        min-height: 160rpx;
        background-color: $bg-color;
        border-radius: $radius-md;
        padding: $spacing-sm $spacing-md;
        font-size: $font-size-sm;
        color: $text-primary;
        border: 2rpx solid $border-color;
        box-sizing: border-box;
      }

      .ai-gen-btn {
        width: 100%;
        height: 80rpx;
        margin-top: $spacing-md;
        border-radius: $radius-pill;
        background-color: $text-primary;
        color: $bg-white;
        font-size: $font-size-md;
        font-weight: 500;
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;

        &[disabled] {
          opacity: 0.5;
        }
      }

      .ai-gen-preview {
        margin-top: $spacing-md;

        .ai-gen-preview-box {
          background-color: $bg-color;
          border-radius: $radius-md;
          padding: $spacing-md;
          max-height: 400rpx;
          overflow-y: auto;
          border: 1rpx solid $border-color;

          .ai-gen-preview-text {
            font-size: $font-size-sm;
            color: $text-primary;
            white-space: pre-wrap;
            word-break: break-all;
            line-height: 1.6;
          }
        }

        .ai-gen-actions {
          display: flex;
          gap: $spacing-sm;
          margin-top: $spacing-md;

          .ai-gen-action-btn {
            flex: 1;
            height: 80rpx;
            border-radius: $radius-pill;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: $font-size-sm;

            &.secondary {
              background-color: $bg-color;
              color: $text-primary;
              border: 1rpx solid $border-color;
            }

            &.primary {
              background-color: $text-primary;
              color: $bg-white;
            }

            &:active {
              opacity: 0.8;
            }
          }
        }
      }
    }
    
    .prompt-picker-close {
      margin-top: $spacing-md;
      text-align: center;
      color: $text-secondary;
      font-size: $font-size-sm;
    }
  }

  // ============ "应用并保存"命名小窗样式 ============
  .save-name-mask {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;

    .save-name-modal {
      width: 80%;
      background-color: $bg-white;
      border-radius: $radius-lg;
      padding: $spacing-lg;
      display: flex;
      flex-direction: column;

      .save-name-title {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: $spacing-xs;
      }

      .save-name-hint {
        font-size: $font-size-xs;
        color: $text-tertiary;
        margin-bottom: $spacing-md;
      }

      .save-name-input {
        width: 100%;
        height: 80rpx;
        background-color: $bg-color;
        border-radius: $radius-md;
        padding: 0 $spacing-md;
        font-size: $font-size-sm;
        color: $text-primary;
        border: 1rpx solid $border-color;
        box-sizing: border-box;
        margin-bottom: $spacing-md;
      }

      .save-name-actions {
        display: flex;
        gap: $spacing-sm;

        .save-name-btn {
          flex: 1;
          height: 80rpx;
          border-radius: $radius-pill;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: $font-size-sm;

          &.secondary {
            background-color: $bg-color;
            color: $text-primary;
            border: 1rpx solid $border-color;
          }

          &.primary {
            background-color: $text-primary;
            color: $bg-white;
          }

          &:active {
            opacity: 0.8;
          }
        }
      }
    }
  }
}

.action-section {
  margin-bottom: $spacing-md;
  
  .primary-btn {
    width: 100%;
    height: 96rpx;
    border-radius: $radius-pill;
    background-color: $text-primary;
    color: $bg-white;
    font-size: $font-size-lg;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    
    &:active {
      opacity: 0.85;
    }
    
    &[disabled] {
      opacity: 0.6;
    }
  }
}

.result-section {
  margin-bottom: $spacing-md;
}

// ==================== doc-keypoint-extract / ocr-recognize 多文件批量（B2） ====================
// 样式已抽到 src/components/BatchFilePicker.vue，本页面不再维护

/* ============ 工具详情页新样式（一比一复刻 mockup） ============ */
.topbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
}
.topbar__back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-primary, #111827);
}
.topbar__back svg { width: 40rpx; height: 40rpx; fill: currentColor; }

.hero-tool {
  display: flex;
  align-items: center;
  gap: 32rpx;
  padding: 40rpx 32rpx 32rpx;
  text-align: left;
  min-height: 112rpx;
}
.hero-tool__icon { flex-shrink: 0; align-self: center; }
.hero-tool__text { flex: 1; min-width: 0; }
.hero__title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
  text-align: left;
}
.hero__subtitle {
  display: block;
  font-size: 26rpx;
  color: var(--text-secondary, #4B5563);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 8rpx;
}

.tool-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tool-icon--lg { width: 96rpx; height: 96rpx; border-radius: 24rpx; }
.tool-icon__emoji { font-size: 44rpx; }
.tool-icon--doc { background: linear-gradient(135deg, #FCA5A5 0%, #EF4444 100%); }
.tool-icon--image { background: linear-gradient(135deg, #C7D2FE 0%, #6366F1 100%); }
.tool-icon--dev { background: linear-gradient(135deg, #6EE7B7 0%, #10B981 100%); }
.tool-icon--audio { background: linear-gradient(135deg, #FCD34D 0%, #F59E0B 100%); }
.tool-icon--video { background: linear-gradient(135deg, #F9A8D4 0%, #EC4899 100%); }
.tool-icon--ocr { background: linear-gradient(135deg, #93C5FD 0%, #3B82F6 100%); }
.tool-icon--text { background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%); }
.tool-icon--code { background: linear-gradient(135deg, #5EEAD4 0%, #14B8A6 100%); }
.tool-icon--meeting { background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%); }

.steps {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  width: 100%;
  padding: 0 32rpx 32rpx;
  box-sizing: border-box;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  flex: 1;
  position: relative;
}
.step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 24rpx;
  left: calc(50% + 28rpx);
  width: calc(100% - 56rpx);
  height: 1px;
  background: var(--divider-color, #E5E7EB);
}
.step__num {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: var(--bg-page, #F3F4F6);
  color: var(--text-tertiary, #9CA3AF);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 600;
  border: 1rpx solid var(--border-color, #E5E7EB);
}
.step--active .step__num {
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  border-color: var(--brand-primary, #3B82F6);
}
.step__label {
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
  white-space: nowrap;
}
.step--active .step__label {
  color: var(--brand-primary, #3B82F6);
  font-weight: 500;
}

/* 上传卡 */
.upload-card {
  margin: 24rpx 32rpx;
  padding: 64rpx 32rpx;
  background: var(--bg-card, #FFFFFF);
  border: 2rpx dashed var(--border-color, #E5E7EB);
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}
.upload-card__icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.upload-card__icon svg { width: 48rpx; height: 48rpx; fill: currentColor; }
.upload-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 8rpx;
}
.upload-card__desc {
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}

/* section title + 文件列表 */
.section-title {
  margin: 0 32rpx 16rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
}
.file-list { margin: 0 32rpx 24rpx; }
.file-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}
.file-item__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 28rpx;
  flex-shrink: 0;
}
.file-item__body { flex: 1; min-width: 0; }
.file-item__name {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  margin-bottom: 4rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-item__meta { font-size: 24rpx; color: var(--text-tertiary, #9CA3AF); }
.file-item__close {
  width: 48rpx;
  height: 48rpx;
  color: var(--text-tertiary, #9CA3AF);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.file-item__close svg { width: 28rpx; height: 28rpx; fill: currentColor; }

/* 提问卡（AI 文件解读） */
.ask-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.ask-card__label {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 24rpx;
}
.ask-card__textarea {
  width: 100%;
  min-height: 160rpx;
  border: none;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  background: transparent;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.6;
}
.ask-card__counter {
  display: block;
  text-align: right;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  margin-top: 8rpx;
}
.suggest-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  align-items: center;
  margin-top: 24rpx;
}
.suggest-label {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-right: 4rpx;
  flex-shrink: 0;
}

/* Pill 按钮 */
.pill-btn {
  height: 56rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: var(--bg-page, #F3F4F6);
  color: var(--text-secondary, #4B5563);
  font-size: 24rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid var(--border-color, #E5E7EB);
  cursor: pointer;
}

/* 内容卡（textarea） */
.content-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.content-card__label {
  display: block;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  margin-bottom: 24rpx;
}
.content-card__textarea {
  width: 100%;
  min-height: 240rpx;
  border: none;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  background: transparent;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.6;
}
.content-card__counter {
  display: block;
  text-align: right;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  margin-top: 16rpx;
}

/* 日期选择器 */
.date-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}
.date-card__left { display: flex; align-items: center; gap: 24rpx; }
.date-card__icon {
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
}
.date-card__icon svg { width: 44rpx; height: 44rpx; fill: currentColor; }
.date-card__value {
  font-size: 32rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
}
.date-card__chev {
  color: var(--text-tertiary, #9CA3AF);
  display: flex;
  align-items: center;
}
.date-card__chev svg { width: 32rpx; height: 32rpx; fill: currentColor; }

/* 快捷示例 */
.example-section { margin: 0 32rpx 24rpx; }
.example-section__title {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  margin-bottom: 24rpx;
}
.example-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
}

/* 双输入卡网格（会议纪要） */
.input-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
}
.input-card {
  background: var(--bg-card, #FFFFFF);
  border: 1rpx solid var(--divider-color, #F0F0F0);
  border-radius: 24rpx;
  padding: 32rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  cursor: pointer;
}
.input-card__icon {
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
}
.input-card__icon svg { width: 52rpx; height: 52rpx; fill: currentColor; }
.input-card__label {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
}
.input-card__sub { font-size: 22rpx; color: var(--text-tertiary, #9CA3AF); }

/* 表单卡（会议信息） */
.form-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 0 32rpx;
}
.form-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  padding: 32rpx 0 24rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
}
.form-row {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
  font-size: 28rpx;
  min-height: 88rpx;
}
.form-row:last-child { border-bottom: none; }
.form-row__label {
  width: 192rpx;
  color: var(--text-secondary, #4B5563);
  flex-shrink: 0;
}
.form-row__field { flex: 1; color: var(--text-tertiary, #9CA3AF); }
.form-row__chev {
  color: var(--text-tertiary, #9CA3AF);
  margin-left: auto;
  display: flex;
  align-items: center;
}
.form-row__chev svg { width: 32rpx; height: 32rpx; fill: currentColor; }

/* 添加项卡 */
.add-item-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 0 32rpx;
}
.add-item-card__title {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  padding: 32rpx 0 24rpx;
}
.add-item-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 0;
  font-size: 28rpx;
  min-height: 88rpx;
  border-top: 1rpx solid var(--divider-color, #F0F0F0);
}
.add-item-row__field {
  flex: 1;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 28rpx;
  background: transparent;
  border: none;
  outline: none;
}
.add-item-row__action {
  color: var(--brand-primary, #3B82F6);
  font-size: 26rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  cursor: pointer;
}
.add-item-row__action svg { width: 28rpx; height: 28rpx; fill: currentColor; }

/* 示例效果卡 */
.example-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.example-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 24rpx;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.example-item { display: flex; align-items: center; gap: 24rpx; }
.example-item__icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.example-item__icon svg { width: 28rpx; height: 28rpx; fill: #FFFFFF; }
.example-item__label {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
}

/* 识别类型列表 */
.recognize-list {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  overflow: hidden;
}
.recognize-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
  cursor: pointer;
}
.recognize-item:last-child { border-bottom: none; }
.recognize-item__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.recognize-item__icon svg { width: 40rpx; height: 40rpx; fill: currentColor; }
.recognize-item__body { flex: 1; min-width: 0; }
.recognize-item__title {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  margin-bottom: 4rpx;
}
.recognize-item__sub {
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.recognize-item__chev {
  color: var(--text-tertiary, #9CA3AF);
  display: flex;
  align-items: center;
}
.recognize-item__chev svg { width: 32rpx; height: 32rpx; fill: currentColor; }

/* 颜色选择器 */
.color-picker {
  display: flex;
  gap: 32rpx;
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  justify-content: space-around;
}
.color-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  cursor: pointer;
}
.color-item__chip {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  border: 4rpx solid var(--border-color, #E5E7EB);
}
.color-item__chip--white { background: #FFFFFF; }
.color-item__chip--blue { background: #3B82F6; }
.color-item__chip--red { background: #EF4444; }
.color-item__chip.is-active {
  border-color: var(--brand-primary, #3B82F6);
  box-shadow: 0 0 0 4rpx var(--brand-primary-light, #EFF6FF);
}
.color-item__label {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
}

/* 示例对比 */
.example-compare {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}
.example-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.example-cell__img {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #F3F4F6, #E5E7EB);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 72rpx;
}
.example-cell__img--blue { background: #3B82F6; color: #FFFFFF; }
.example-cell__img--with-bg { background: linear-gradient(135deg, #93C5FD, #3B82F6); color: #FFFFFF; }
.example-cell__label {
  font-size: 24rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
}
.example-arrow {
  color: var(--text-tertiary, #9CA3AF);
  font-size: 48rpx;
  flex-shrink: 0;
}

/* Tab 切换 */
.tabs {
  display: flex;
  gap: 16rpx;
  margin: 0 32rpx 24rpx;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  color: var(--text-secondary, #4B5563);
  font-size: 26rpx;
  border-radius: 16rpx;
  border: 1rpx solid var(--border-color, #E5E7EB);
  cursor: pointer;
}
.tab-item.is-active {
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  border-color: var(--brand-primary, #3B82F6);
}

/* 缩略图网格 */
.thumb-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
}
.thumb-item {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 24rpx;
  background: var(--bg-disabled, #F3F4F6);
  border: 4rpx solid transparent;
  cursor: pointer;
}
.thumb-item.is-active { border-color: var(--brand-primary, #3B82F6); }
.thumb-item--grass { background: linear-gradient(135deg, #6EE7B7, #10B981); }
.thumb-item--mountain { background: linear-gradient(135deg, #C7D2FE, #818CF8); }
.thumb-item--sea { background: linear-gradient(135deg, #93C5FD, #3B82F6); }
.thumb-item--upload {
  border: 2rpx dashed var(--border-color, #E5E7EB);
  background: var(--bg-card, #FFFFFF);
  color: var(--text-tertiary, #9CA3AF);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8rpx;
}
.thumb-item--upload svg { width: 48rpx; height: 48rpx; fill: currentColor; }
.thumb-item--upload text { font-size: 24rpx; }

/* 图片信息（图片压缩） */
.image-info {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.image-info__thumb {
  width: 112rpx;
  height: 112rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #C7D2FE, #6366F1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  flex-shrink: 0;
}
.image-info__body { flex: 1; min-width: 0; }
.image-info__name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.image-info__meta {
  display: flex;
  gap: 24rpx;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.image-info__meta .meta-strong {
  color: var(--text-primary, #111827);
  font-weight: 500;
}

/* 压缩设置 */
.compress-settings {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.compress-settings__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 24rpx;
}

/* Slider */
.slider-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.slider-label {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
}
.slider-value {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--brand-primary, #3B82F6);
}
.slider-track {
  position: relative;
  height: 16rpx;
  background: var(--bg-disabled, #F3F4F6);
  border-radius: 999rpx;
  margin-bottom: 24rpx;
  cursor: pointer;
}
.slider-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--brand-primary, #3B82F6);
  border-radius: 999rpx;
}
.slider-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 40rpx;
  height: 40rpx;
  background: #FFFFFF;
  border: 6rpx solid var(--brand-primary, #3B82F6);
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 预计大小 */
.estimate-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--divider-color, #F0F0F0);
}
.estimate-label {
  font-size: 26rpx;
  color: var(--text-secondary, #4B5563);
}
.estimate-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-success, #10B981);
}

/* 选项行（QR 样式/尺寸） */
.options-row {
  display: flex;
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
}
.option-item {
  flex: 1;
  text-align: center;
  padding: 24rpx;
  background: var(--bg-card, #FFFFFF);
  border: 1rpx solid var(--border-color, #E5E7EB);
  border-radius: 16rpx;
  font-size: 26rpx;
  color: var(--text-secondary, #4B5563);
  cursor: pointer;
}
.option-item.is-active {
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  border-color: var(--brand-primary, #3B82F6);
}

/* QR 码 */
.qr-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 32rpx;
}
.qr-code {
  width: 200rpx;
  height: 200rpx;
  background: #FFFFFF;
  border: 1rpx solid var(--divider-color, #F0F0F0);
  border-radius: 16rpx;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(7, 1fr);
  padding: 12rpx;
  flex-shrink: 0;
}
.qr-cell { background: transparent; }
.qr-cell.is-dark { background: #111827; }
.qr-info { flex: 1; min-width: 0; }
.qr-info__label {
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  margin-bottom: 8rpx;
}
.qr-info__code {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 16rpx;
  word-break: break-all;
}
.qr-info__copy {
  font-size: 26rpx;
  color: var(--brand-primary, #3B82F6);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}
.qr-info__copy svg { width: 28rpx; height: 28rpx; fill: currentColor; }

/* 密码生成设置 */
.settings-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.settings-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 24rpx;
}

/* 字符选项 */
.checkbox-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
}
.checkbox-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  cursor: pointer;
}
.checkbox-item__box {
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  border: 1rpx solid var(--border-color, #E5E7EB);
  background: var(--bg-card, #FFFFFF);
  display: flex;
  align-items: center;
  justify-content: center;
}
.checkbox-item__box.is-checked {
  background: var(--brand-primary, #3B82F6);
  border-color: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
}
.checkbox-item__box svg { width: 24rpx; height: 24rpx; fill: currentColor; }

/* 密码展示 */
.password-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
}
.password-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 24rpx;
}
.password-display {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  background: var(--bg-page, #F3F4F6);
  border-radius: 16rpx;
  border: 1rpx dashed var(--border-color, #E5E7EB);
}
.password-display__text {
  flex: 1;
  font-family: monospace;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  word-break: break-all;
  letter-spacing: 2rpx;
}
.password-display__copy {
  width: 64rpx;
  height: 64rpx;
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}
.password-display__copy svg { width: 32rpx; height: 32rpx; fill: currentColor; }

/* 密码强度 */
.strength-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 24rpx;
}
.strength-label {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  flex-shrink: 0;
}
.strength-bar {
  flex: 1;
  height: 12rpx;
  background: var(--bg-disabled, #F3F4F6);
  border-radius: 999rpx;
  overflow: hidden;
}
.strength-fill {
  height: 100%;
  background: linear-gradient(90deg, #10B981, #34D399);
  border-radius: 999rpx;
}
.strength-text {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--color-success, #10B981);
  flex-shrink: 0;
}


/* ============ 会议纪要结果渲染（JSON 表格 + SSE Markdown） ============ */
.mm-json-table {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  overflow: hidden;
}
.mm-json-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
}
.mm-json-row:last-child {
  border-bottom: none;
}
.mm-json-cell-title {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.mm-json-key {
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
  font-family: monospace;
  background: var(--bg-page, #F3F4F6);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.mm-json-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.mm-json-cell-content {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding-left: 16rpx;
}
.mm-json-checkbox {
  font-size: 32rpx;
  color: var(--text-tertiary, #9CA3AF);
  flex-shrink: 0;
  line-height: 1.4;
}
.mm-json-content {
  flex: 1;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  line-height: 1.6;
}
.mm-json-empty {
  padding: 60rpx 0;
  text-align: center;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 26rpx;
}
.mm-md-result {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--text-primary, #111827);
}
.mm-md-result h1,
.mm-md-result h2,
.mm-md-result h3 {
  margin: 24rpx 0 16rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
}
.mm-md-result h1 { font-size: 36rpx; }
.mm-md-result h2 { font-size: 32rpx; }
.mm-md-result h3 { font-size: 28rpx; }
.mm-md-result p {
  margin: 12rpx 0;
}
.mm-md-result ul,
.mm-md-result ol {
  margin: 12rpx 0;
  padding-left: 40rpx;
}
.mm-md-result li {
  margin: 8rpx 0;
}
.mm-md-result strong {
  font-weight: 700;
  color: var(--brand-primary, #3B82F6);
}
.mm-md-result code {
  background: var(--bg-page, #F3F4F6);
  padding: 2rpx 8rpx;
  border-radius: 4rpx;
  font-family: monospace;
  font-size: 24rpx;
}

/* 提示卡 */
.tip {
  margin: 0 32rpx 24rpx;
  padding: 24rpx;
  background: var(--brand-primary-light, #EFF6FF);
  border-radius: 16rpx;
  color: var(--text-secondary, #4B5563);
  font-size: 26rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.tip svg { width: 32rpx; height: 32rpx; fill: var(--brand-primary, #3B82F6); flex-shrink: 0; }

/* 添加新任务卡 */
.add-task-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
}
.add-task-card__textarea {
  flex: 1;
  min-height: 120rpx;
  border: 1rpx solid var(--border-color, #E5E7EB);
  border-radius: 16rpx;
  padding: 24rpx;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  background: var(--bg-page, #F3F4F6);
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.5;
}
.add-task-card__add {
  width: 72rpx;
  height: 72rpx;
  background: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}
.add-task-card__add svg { width: 40rpx; height: 40rpx; fill: currentColor; }

/* 任务列表 */
.task-list {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  overflow: hidden;
}
.task-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
}
.task-item:last-child { border-bottom: none; }
.task-item.is-done .task-item__name {
  text-decoration: line-through;
  color: var(--text-tertiary, #9CA3AF);
}
.task-item__checkbox {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 1rpx solid var(--border-color, #E5E7EB);
  background: var(--bg-card, #FFFFFF);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
}
.task-item__checkbox.is-checked {
  background: var(--brand-primary, #3B82F6);
  border-color: var(--brand-primary, #3B82F6);
  color: #FFFFFF;
}
.task-item__checkbox svg { width: 24rpx; height: 24rpx; fill: currentColor; }
.task-item__body { flex: 1; min-width: 0; }
.task-item__name {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
  margin-bottom: 8rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-item__meta {
  display: flex;
  gap: 16rpx;
  align-items: center;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.task-priority {
  padding: 2rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 500;
}
.task-priority--high { background: rgba(239, 68, 68, 0.1); color: #EF4444; }
.task-priority--medium { background: rgba(245, 158, 11, 0.1); color: #F59E0B; }
.task-priority--low { background: rgba(16, 185, 129, 0.1); color: #10B981; }
.task-item__delete {
  color: var(--text-tertiary, #9CA3AF);
  cursor: pointer;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.task-item__delete svg { width: 32rpx; height: 32rpx; fill: currentColor; }

/* 底部按钮 */
.bottom-action {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 0rpx));
  background: var(--bg-card, #FFFFFF);
  border-top: 1rpx solid var(--divider-color, #F0F0F0);
  z-index: 100;
}
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 500;
  cursor: pointer;
  border: none;
}
.btn--primary {
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #FFFFFF;
}
.btn--primary[disabled] { opacity: 0.6; }
.btn--block { width: 100%; }

.safe-area-bottom { height: 60rpx; }
</style>