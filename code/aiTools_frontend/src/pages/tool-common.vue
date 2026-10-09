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

    <!-- 纯转换工具（文档转文本 / 录音转写）：单文件上传，无提示词 -->
    <block v-if="isPureConvert">
      <view class="upload-card" @click="onPureConvertPick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">{{ toolInfo.uploadTitle || '点击上传文件' }}</text>
        <text class="upload-card__desc">{{ toolInfo.uploadDesc || '' }}</text>
      </view>
      <view v-if="pureConvertFiles.length > 0" class="section-title">
        <text>已选 {{ pureConvertFiles.length }} 个文件</text>
      </view>
      <view v-if="pureConvertFiles.length > 0" class="file-list">
        <view v-for="(f, idx) in pureConvertFiles" :key="idx" class="file-item">
          <view class="file-item__icon">{{ (f.name || 'F').charAt(0).toUpperCase() }}</view>
          <view class="file-item__body">
            <text class="file-item__name">{{ f.name }}</text>
            <text class="file-item__meta">{{ formatFileSize(f.size) }}</text>
          </view>
          <view class="file-item__close" @click="removePureConvertFile(idx)">
            <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          </view>
        </view>
      </view>

      <!-- 录音转写：转写引擎选择（仅 audio-transcribe 且服务端已开启腾讯云通道时显示） -->
      <view v-if="engineOptions.length > 0" class="section-title">
        <text>转写引擎</text>
      </view>
      <view v-if="engineOptions.length > 0" class="engine-group">
        <view
          v-for="opt in engineOptions"
          :key="opt.value"
          class="engine-item"
          :class="{ 'engine-item--active': asrEngine === opt.value }"
          @click="asrEngine = opt.value"
        >
          <view class="engine-item__radio" :class="{ 'engine-item__radio--on': asrEngine === opt.value }" />
          <view class="engine-item__body">
            <text class="engine-item__label">{{ opt.label }}</text>
            <text v-if="opt.desc" class="engine-item__desc">{{ opt.desc }}</text>
          </view>
        </view>
      </view>
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
        <view class="input-card" @click="focusMeetingContent">
          <view class="input-card__icon">
            <svg viewBox="0 0 24 24"><path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V6h16v12z"/></svg>
          </view>
          <text class="input-card__label">输入会议内容</text>
          <text class="input-card__sub">{{ inputText ? inputText.length + ' 字' : '点击下方输入或粘贴文字' }}</text>
        </view>
      </view>

      <!-- 会议内容（与下方提示词展示框同款 textarea，转写成功后自动填入） -->
      <view class="prompt-card">
        <view class="prompt-card__header">
          <text class="prompt-card__label">会议内容</text>
        </view>
        <textarea class="prompt-card__textarea" v-model="inputText" ref="meetingContentRef" placeholder="请输入或粘贴会议内容；上传录音后 AI 会自动转写并填入此处…" :maxlength="5000"></textarea>
        <text class="prompt-card__counter">{{ inputText.length }}/5000</text>
      </view>

<!-- 格式提示词（告诉 AI 输出什么结构） -->
      <view class="prompt-card">
        <view class="prompt-card__header">
          <text class="prompt-card__label">格式提示词</text>
          <view class="prompt-card__pick" @click="openPromptPicker('format')">
            <svg viewBox="0 0 24 24"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19a2 2 0 002 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z"/></svg>
            <text>选择系统提示词</text>
          </view>
        </view>
        <textarea class="prompt-card__textarea" v-model="promptFormatText" placeholder="例如：按四部分输出（已完成/进行中/问题/下一步），每部分用编号列表…" :maxlength="1000"></textarea>
        <text class="prompt-card__counter">{{ promptFormatText.length }}/1000</text>
      </view>

      <!-- 生成提示词（告诉 AI 你是谁、怎么说话） -->
      <view class="prompt-card">
        <view class="prompt-card__header">
          <text class="prompt-card__label">生成提示词</text>
          <view class="prompt-card__pick" @click="openPromptPicker('generate')">
            <svg viewBox="0 0 24 24"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19a2 2 0 002 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z"/></svg>
            <text>选择系统提示词</text>
          </view>
        </view>
        <textarea class="prompt-card__textarea" v-model="promptGenerateText" placeholder="例如：你是严谨的会议整理助手，语气正式、结构清晰…" :maxlength="1000"></textarea>
        <text class="prompt-card__counter">{{ promptGenerateText.length }}/1000</text>
      </view>

      <!-- ============ 会议纪要结果渲染区（JSON 树 / SSE Markdown） ============ -->
      <!-- 使用 vue-json-pretty 通用渲染任意 JSON 结构（不限字段名/嵌套深度） -->
      <block v-if="meetingRoute === 'json'">
        <view class="section-title-row">
          <text class="section-title">生成结果（JSON 结构化）</text>
          <view class="result-actions">
            <view class="result-action-btn" @click="copyRaw(JSON.stringify(meetingJsonResult, null, 2), 'JSON')">
              <svg viewBox="0 0 24 24" class="result-action-icon"><path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8C6.9 5 6 5.9 6 7v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
              <text>复制 JSON</text>
            </view>
          </view>
        </view>
        <view v-if="meetingJsonResult.length > 0" class="mm-json-pretty">
          <vue-json-pretty :data="meetingJsonResult" :show-length="true" :show-line-number="true" />
        </view>
        <view v-else class="mm-json-empty">
          <text>暂无生成结果</text>
        </view>
      </block>
      <!-- ============ 会议纪要结果渲染区（JSON 树 / SSE Markdown） ============ -->
      <!-- SSE Markdown 统一由 MarkdownView 渲染：H5 走 v-html + DOMPurify 清洗，App 走 mp-html -->
      <block v-else-if="meetingRoute === 'sse'">
        <view class="section-title-row">
          <text class="section-title">生成结果（SSE 流式 Markdown）</text>
          <view class="result-actions">
            <view class="result-action-btn" @click="copyRaw(meetingMarkdownText, 'Markdown')">
              <svg viewBox="0 0 24 24" class="result-action-icon"><path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8C6.9 5 6 5.9 6 7v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
              <text>复制 Markdown</text>
            </view>
            <view class="result-action-btn" @click="copyPlain(meetingMarkdownText, '纯文本')">
              <svg viewBox="0 0 24 24" class="result-action-icon"><path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8C6.9 5 6 5.9 6 7v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
              <text>复制纯文本</text>
            </view>
          </view>
        </view>
        <view class="mm-md-result" :ref="el => setMeetingResultRef(el)" @scroll="onMeetingScroll">
          <!-- 渲染统一交给 MarkdownView（组件内部用 markdown-it + DOMPurify / mp-html）：
               streaming=true（推字中）只显示转义纯文本，推字结束(meetingTypingDone)后渲染富文本 -->
          <!-- 渲染模式的整块重挂载由组件内部处理，外部无需再传 renderKey -->
          <MarkdownView :source="meetingMarkdownText" :streaming="!meetingTypingDone" />
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

    <!-- 证件照换背景色：上传 + 3 色块 + 结果区（真实接口 /api/ai-office/id-photo-bg-change） -->
    <block v-if="toolId === 'id-photo-bg-change'">
      <!-- 已选图：展示缩略图 + 文件名，可重新选择 -->
      <view v-if="idPhotoPicked" class="idphoto-picked" @click="onIdPhotoPick">
        <image class="idphoto-picked__img" :src="idPhotoPicked.path" mode="aspectFill"></image>
        <view class="idphoto-picked__body">
          <text class="idphoto-picked__name">{{ idPhotoPicked.name }}</text>
          <text class="idphoto-picked__meta">{{ idPhotoPicked.sizeText }}</text>
        </view>
        <view class="idphoto-picked__change">
          <svg viewBox="0 0 24 24"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0020 13c0-4.42-3.58-8-8-8zm0 12c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 6.74A7.93 7.93 0 004 11c0 4.42 3.58 8 8 8v4l5-5-5-5v4z"/></svg>
          <text>重新选择</text>
        </view>
      </view>
      <!-- 未选图：上传卡片 -->
      <view v-else class="upload-card" @click="onIdPhotoPick">
        <view class="upload-card__icon">
          <svg viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        </view>
        <text class="upload-card__title">{{ toolInfo.uploadTitle || '点击上传证件照' }}</text>
        <text class="upload-card__desc">{{ toolInfo.uploadDesc || '支持 JPG、PNG 格式' }}</text>
      </view>

      <!-- 底色选择：红 / 蓝 / 白 单选 -->
      <view class="section-title">选择背景色</view>
      <view class="color-picker">
        <view v-for="c in bgColorChips" :key="c.code" class="color-item" @click="selectBgColor(c.code)">
          <view class="color-item__chip" :class="[c.cls, { 'is-active': selectedBgColor === c.code }]"></view>
          <text class="color-item__label">{{ c.label }}</text>
        </view>
      </view>
      <view class="tip">
        <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
        <text>建议上传正面免冠、背景干净的半身照，换底色效果更佳</text>
      </view>

      <!-- 结果区：loading → 错误 → 图片预览 -->
      <block v-if="idPhotoLoading">
        <view class="idphoto-loading">
          <view class="idphoto-loading__spinner"></view>
          <text class="idphoto-loading__text">AI 抠图中，请稍候…</text>
        </view>
      </block>
      <view v-else-if="idPhotoError" class="idphoto-error">
        <text class="idphoto-error__text">{{ idPhotoError }}</text>
        <view class="idphoto-error__retry" @click="handleGenerate">
          <text>重试</text>
        </view>
      </view>
      <block v-else-if="idPhotoResult">
        <view class="section-title-row">
          <text class="section-title">{{ toolInfo.resultTitle || '换背景结果' }}</text>
          <view class="result-actions">
            <view class="result-action-btn" @click="previewIdPhoto">
              <svg viewBox="0 0 24 24" class="result-action-icon"><path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zm0 12.5a5 5 0 110-10 5 5 0 010 10zm0-8a3 3 0 100 6 3 3 0 000-6z"/></svg>
              <text>放大预览</text>
            </view>
            <view class="result-action-btn" @click="saveIdPhoto">
              <svg viewBox="0 0 24 24" class="result-action-icon"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
              <text>保存图片</text>
            </view>
          </view>
        </view>
        <view class="idphoto-result">
          <image
            class="idphoto-result__img"
            :src="idPhotoResult.fileUrl"
            mode="widthFix"
            @click="previewIdPhoto"
          ></image>
          <view class="idphoto-result__meta">
            <view class="idphoto-result__row">
              <text class="idphoto-result__k">文件名</text>
              <text class="idphoto-result__v">{{ idPhotoResult.fileName || '—' }}</text>
            </view>
            <view class="idphoto-result__row">
              <text class="idphoto-result__k">文件大小</text>
              <text class="idphoto-result__v">{{ idPhotoResult.fileSizeText || formatFileSize(idPhotoResult.fileSize) }}</text>
            </view>
            <view class="idphoto-result__row">
              <text class="idphoto-result__k">图片尺寸</text>
              <text class="idphoto-result__v">{{ idPhotoResult.width || '—' }} × {{ idPhotoResult.height || '—' }}</text>
            </view>
            <view class="idphoto-result__row">
              <text class="idphoto-result__k">底色</text>
              <text class="idphoto-result__v">{{ selectedBgColorLabel }}</text>
            </view>
          </view>
        </view>
      </block>
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

    <!-- ===== 工具内嵌历史记录面板 ===== -->
    <view class="history-panel" :class="{ 'is-open': historyPanelOpen }">
      <view class="history-panel__header" @click="toggleHistoryPanel">
        <svg viewBox="0 0 24 24" class="history-panel__icon"><path d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/></svg>
        <text class="history-panel__title">{{ historyPanelOpen ? '收起历史记录' : '查看历史记录' + (historyList.length ? ' (' + historyList.length + ')' : '') }}</text>
        <svg viewBox="0 0 24 24" class="history-panel__chevron"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" v-if="!historyPanelOpen"/><path d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z" v-else/></svg>
      </view>
      <view v-if="historyPanelOpen" class="history-panel__body">
        <view v-if="historyLoading" class="history-panel__loading">
          <text>加载中…</text>
        </view>
        <view v-else-if="historyList.length === 0" class="history-panel__empty">
          <text>暂无历史记录</text>
        </view>
        <view v-else class="history-panel__list">
          <view v-for="item in historyList" :key="item.id" class="history-item" @click="applyHistory(item)">
            <view class="history-item__left">
              <text class="history-item__status" :class="item.status === 1 ? 'is-ok' : 'is-fail'">{{ item.status === 1 ? '成功' : '失败' }}</text>
              <text class="history-item__input">{{ (item.inputContent || '（无输入）').slice(0, 60) }}{{ (item.inputContent || '').length > 60 ? '…' : '' }}</text>
            </view>
            <view class="history-item__right">
              <text class="history-item__time">{{ formatHistoryTime(item.createTime) }}</text>
              <svg viewBox="0 0 24 24" class="history-item__chev"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
            </view>
          </view>
        </view>
      </view>
    </view>
  </scroll-view>

  <!-- 底部按钮 -->
  <view class="bottom-action">
    <button class="btn btn--primary btn--block" :disabled="loading" @click="handleGenerate">{{ bottomActionText }}</button>
  </view>

    
    <!-- 提示词选择抽屉弹窗（独立组件） -->
    <PromptPickerDrawer
      v-model:show="showPromptPicker"
      :tool-name="toolInfo.name"
      :tool-desc="toolInfo.desc"
      :system-prompts="systemPromptList"
      :user-prompts="userPromptList"
      @confirm="onPromptPickerConfirm"
    />

</template>



<script setup>
import { ref, computed, watch } from 'vue'
import { BASE_URL } from '@/config/env'
import { onLoad } from '@dcloudio/uni-app'
import { safeBack } from '@/utils/pageTransition'
import { requireLogin } from '@/utils/auth'
import VueJsonPretty from 'vue-json-pretty'
import 'vue-json-pretty/lib/styles.css'
import { uploadFileApi, batchUpload, audioBatchUpload, batchCompleted, meetingMinutesDecideRoute, meetingMinutesJson, transcribeMeeting, idPhotoBgChange } from '@/api/ai.js'
import { historyListByToolApi } from '@/api/history.js'
import { streamRequest, streamUpload } from '../api/stream'
import { formatAiResult } from '@/utils/format'
import { copyRaw, copyPlain } from '@/utils/clipboard'
import { request } from '@/api/request'
import MarkdownView from '@/components/MarkdownView.vue'
import { promptListApi, systemPromptListApi, generatePromptApi, promptAddApi } from '@/api/prompt'
import { getTool, validate } from '@/config/tools'
import PromptPickerDrawer from '@/components/PromptPickerDrawer.vue'

const toolId = ref('')
const currentInputType = ref('text')
const inputText = ref('')
const fileName = ref('')
const filePath = ref('')
const fileObj = ref(null)          // 原生 File/Blob 对象（H5 端用于 multipart 上传且保留真实文件名）
const uploading = ref(false)
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

// ===== 工具内嵌历史记录（按 aiCode 过滤）=====
const historyPanelOpen = ref(false)  // 历史面板是否展开
const historyList = ref([])          // 当前工具的历史列表（HistoryVO[]）
const historyLoading = ref(false)    // 拉历史时的 loading 状态
const HISTORY_DEFAULT_LIMIT = 10    // 拉取条数上限（与后端一致）
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

const goBack = () => safeBack('/pages/index')

// ===== 各工具特化状态 =====
const workDate = ref('2025-09-23')
const chooseDate = () => { uni.showToast({ title: '日期选择开发中', icon: 'none' }) }
const applyQuickPrompt = (text) => { inputText.value = text }

const askText = ref('')
const applySuggested = (text) => { askText.value = text }
const uploadedFiles = ref([])   // 批量工具已选文件（mockup 阶段的示例数据已移除）
/** 批量工具选文件（文档重点提取 / 智能识别 / AI 文件解读）：H5 原生多选，App 用 chooseMessageFile */
const onFilePickerClick = () => {
  const rule = toolInfo.value.fileRule || {}
  const maxCount = rule.maxCount || 10
  const accept = rule.accept || '.pdf,.docx,.txt'
  const isImage = toolInfo.value.fileType === 'image'
  const isAudio = toolInfo.value.fileType === 'audio'

  const addPicked = (items) => {
    if (!items.length) return
    const merged = uploadedFiles.value.filter(f => f && f.fileName).concat(items)
    if (merged.length > maxCount) {
      uni.showToast({ title: `最多 ${maxCount} 个文件，已截断`, icon: 'none' })
      merged.splice(maxCount)
    }
    uploadedFiles.value = merged
  }

  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = maxCount > 1
  input.accept = accept
  input.onchange = () => {
    const picked = Array.from(input.files || []).map(f => ({
      fileName: f.name, file: f, size: f.size || 0
    }))
    addPicked(picked)
  }
  input.click()
  // #endif
  // #ifndef H5
  if (isImage && typeof uni.chooseImage === 'function') {
    uni.chooseImage({
      count: maxCount,
      success: (res) => {
        const paths = res.tempFilePaths || []
        const files = res.tempFiles || []
        addPicked(paths.map((p, i) => ({
          fileName: (files[i] && files[i].name) || p.split('/').pop() || ('图片' + (i + 1)),
          path: p, size: (files[i] && files[i].size) || 0
        })))
      }
    })
    return
  }
  // 文档/音频：chooseMessageFile
  uni.chooseMessageFile({
    count: maxCount,
    type: isAudio ? 'audio' : 'file',
    extension: accept.split(',').map(s => s.trim()).filter(Boolean),
    success: (res) => {
      const files = res.tempFiles || []
      addPicked(files.map((f, i) => ({
        fileName: f.name || f.path.split('/').pop() || ('文件' + (i + 1)),
        path: f.path || f,
        size: f.size || 0
      })))
    }
  })
  // #endif
}
const removeFile = (idx) => { uploadedFiles.value.splice(idx, 1) }

/** 文件大小格式化（0 或未知显示「—」） */
const formatFileSize = (size) => {
  const n = Number(size || 0)
  if (!n) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

/** 点击文件：已上传（有 url）则跳下载；否则提示先运行（运行前才上传） */
const onFileItemClick = (f) => {
  if (!f || !f.url) {
    uni.showToast({ title: '文件将在运行时上传', icon: 'none' })
    return
  }
  // #ifdef H5
  window.open(f.url, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url: f.url,
    success: (res) => {
      if (res.statusCode === 200) uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' })
  })
  // #endif
}

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

// 打字机是否已完成:false=推字中(只显示纯文本,不渲染 markdown),true=已结束(渲染 markdown)
// 原因:打字过程中 markdown 是半截的,实时解析会频繁闪成错乱格式;
//       改为推字时纯文本 + 换行,结束后再一次性渲染完整 markdown
const meetingTypingDone = ref(false)

// 流式输出时的自动滚动:打字机每帧推完后,如果用户处于"接近底部"则跟着滚到底
// 用户主动上滑查看历史时暂停,直到再次滚到底才恢复
const meetingResultEl = ref(null)
const meetingUserScrolledUp = ref(false) // 用户是否已上滑
const MEETING_SCROLL_BOTTOM_THRESHOLD = 60 // 距底多少 px 视为"还在底部"
const setMeetingResultRef = (el) => {
  // 函数式 ref:vue 3 支持,el 可能为 null(组件卸载)
  meetingResultEl.value = el
}
const onMeetingScroll = () => {
  const el = meetingResultEl.value
  if (!el) return
  const distanceToBottom = el.scrollHeight - (el.scrollTop + el.clientHeight)
  // 距底 > 阈值视为用户上滑
  meetingUserScrolledUp.value = distanceToBottom > MEETING_SCROLL_BOTTOM_THRESHOLD
}
const scrollMeetingToBottom = () => {
  const el = meetingResultEl.value
  if (!el || meetingUserScrolledUp.value) return
  // 滚到底:用 scrollTop 直接赋值,uni-app H5 兼容;App 端用 uni.pageScrollTo
  try {
    if (typeof el.scrollTop === 'number') {
      el.scrollTop = el.scrollHeight
    } else if (typeof uni !== 'undefined' && uni.pageScrollTo) {
      uni.pageScrollTo({ scrollTop: el.scrollHeight || 999999, duration: 0 })
    }
  } catch (e) { /* 静默吞滚动异常,不影响主流程 */ }
}
const resetMeetingScroll = () => {
  // 重新生成前重置
  meetingUserScrolledUp.value = false
}
// 说明：原先的 normalizeMarkdown（用正则给 markdown 补空行）与 marked 解析已废弃——
// markdown-it（CommonMark）本身就支持"标题/列表/引用可打断段落"，无需正则预处理；
// 解析与渲染统一走 @/utils/markdown + MarkdownView 组件。

// 纯文本转义 / H5 渲染 / App 节点转换原先都在这里，现已全部收敛到
// src/components/MarkdownView.vue（内部复用 src/utils/markdown 管线）
const mmPlanText = ref('')
// H5 端用隐藏 input 选文件，返回 base64 dataURL 数组（支持多选）
const pickAudioByInput = () => {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'audio/*'
    // 多选：可一次选中多个录音（按住 Ctrl / Command，或重复点选累加）
    input.multiple = true
    input.style.display = 'none'
    input.onchange = async (e) => {
      const files = Array.from((e.target && e.target.files) || [])
      if (document.body.contains(input)) document.body.removeChild(input)
      if (!files.length) return resolve([])
      try {
        const results = await Promise.all(files.map((file) => new Promise((res, rej) => {
          const reader = new FileReader()
          reader.onload = () => res({ name: file.name, dataUrl: reader.result })
          reader.onerror = () => rej(new Error('读取文件失败'))
          reader.readAsDataURL(file)
        })))
        resolve(results)
      } catch (err) {
        reject(err)
      }
    }
    document.body.appendChild(input)
    input.click()
  })
}

// 上传录音（支持多选）→ 后端 ASR 逐个转写 → 分别拼接进会议内容输入框
const isTranscribing = ref(false)
const onUploadRecording = async () => {
  try {
    // 兼容 H5 + App：
    //   H5 端用原生 <input type=file>（uni.chooseMessageFile 仅微信小程序支持）
    //   App 端用 uni.chooseMedia（mediaType:audio）
    // 多选：选中 N 个录音 → 逐个调用 ASR → 每段结果单独拼进输入框
    let picked = []   // [{ name, path }]
    // #ifdef H5
    picked = await pickAudioByInput()
    // #endif
    // #ifndef H5
    const chooseRes = await uni.chooseMedia({
      count: 10,                       // 最多 10 个录音
      mediaType: ['audio'],
      sourceType: ['album', 'file']
    })
    const tempFiles = (chooseRes && (chooseRes.tempFiles
      || (chooseRes[1] && chooseRes[1].tempFiles))) || []
    picked = tempFiles.map((f, i) => ({
      name: f.name || ('录音' + (i + 1)),
      path: f.tempFilePath || f.path
    })).filter(x => !!x.path)
    // #endif
    if (!picked.length) {
      uni.showToast({ title: '未选择文件', icon: 'none' })
      return
    }

    isTranscribing.value = true
    uni.showLoading({ title: picked.length > 1 ? ('AI 转写中 0/' + picked.length) : 'AI 转写中...' })

    const texts = []
    const failures = []
    for (let i = 0; i < picked.length; i++) {
      const item = picked[i]
      const filePath = item.path || item.dataUrl
      if (picked.length > 1) {
        uni.showLoading({ title: 'AI 转写中 ' + (i + 1) + '/' + picked.length })
      }
      try {
        const res = await transcribeMeeting(filePath)
        const text = res && res.data && res.data.text
        if (text && String(text).trim()) {
          texts.push(String(text).trim())
        } else {
          failures.push(item.name || ('第' + (i + 1) + '个'))
        }
      } catch (e) {
        failures.push(item.name || ('第' + (i + 1) + '个'))
        console.error('录音转写失败:', item.name, e)
      }
    }
    uni.hideLoading()

    if (texts.length) {
      // 每段转写结果单独拼接（换行分隔，空两行），追加到已有内容之后
      const joined = texts.join(String.fromCharCode(10, 10))
      const oldText = inputText.value
      inputText.value = oldText ? oldText + String.fromCharCode(10, 10) + joined : joined
      if (failures.length) {
        uni.showToast({ title: '成功 ' + texts.length + ' 个，失败 ' + failures.length + ' 个', icon: 'none', duration: 3000 })
      } else {
        uni.showToast({ title: '转写完成' + (texts.length > 1 ? '（' + texts.length + ' 个）' : ''), icon: 'success' })
      }
    } else {
      uni.showToast({ title: '转写失败，请重试', icon: 'none' })
    }
  } catch (e) {
    uni.hideLoading()
    console.error('录音转写失败:', e)
    // 优先展示后端返回的 msg（ResultCode.message 或 BusinessException）
    const serverMsg = e && e.data && e.data.msg
    const errMsg = (serverMsg || (e && e.errMsg) || (e && e.message) || '录音转写失败').toString()
    uni.showToast({ title: errMsg.length > 18 ? errMsg.slice(0, 18) + '…' : errMsg, icon: 'none', duration: 3000 })
  } finally {
    isTranscribing.value = false
  }
}
// 点击"输入会议内容"卡 → 滚动聚焦到下方 textarea
const meetingContentRef = ref(null)
const focusMeetingContent = () => {
  // #ifdef H5
  const el = document.querySelector('.prompt-card__textarea')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  // #endif
  // #ifndef H5
  uni.pageScrollTo({ selector: '.prompt-card__textarea', duration: 300 })
  // #endif
  setTimeout(() => {
    const ref = meetingContentRef.value
    if (ref && ref.focus) ref.focus()
  }, 350)
}
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

const selectedBgColor = ref('red')   // 默认底色（与 tools.js 的 defaultBgColor 一致）
const selectBgColor = (code) => { selectedBgColor.value = code }

// ===== 证件照换背景色（id-photo-bg-change）=====
// 底色选项以 tools.js 配置为准（value 与后端 bgColor 入参一致），配置缺失时退回本地兜底
const ID_PHOTO_BG_FALLBACK = [
  { value: 'red',   label: '红色', hex: '#EF4444' },
  { value: 'blue',  label: '蓝色', hex: '#3B82F6' },
  { value: 'white', label: '白色', hex: '#FFFFFF' }
]
// 色块样式类映射（设计稿的 3 个色块）
const BG_COLOR_CLS = {
  red: 'color-item__chip--red',
  blue: 'color-item__chip--blue',
  white: 'color-item__chip--white'
}
const idPhotoBgOptions = computed(() => toolInfo.value.bgColorOptions || ID_PHOTO_BG_FALLBACK)
// 模板用的色块列表：由配置派生，避免配置与 UI 各写一份
const bgColorChips = computed(() => idPhotoBgOptions.value.map((c) => ({
  code: c.value, label: c.label, cls: BG_COLOR_CLS[c.value] || ''
})))
const selectedBgColorLabel = computed(() => {
  const hit = idPhotoBgOptions.value.find((c) => c.value === selectedBgColor.value)
  return hit ? hit.label : ''
})
/** 已选证件照：{ path, name, sizeText, file }，file 为 H5 原生 File/Blob（保留真实文件名） */
const idPhotoPicked = ref(null)
/** 换背景结果：后端返回的 ToolOutputVO（fileUrl / fileName / width / height …） */
const idPhotoResult = ref(null)
const idPhotoLoading = ref(false)
const idPhotoError = ref('')

/** 选择证件照：H5 用原生 input 拿 File 对象，App/小程序用 uni.chooseImage 拿临时路径 */
const onIdPhotoPick = () => {
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/jpeg,image/png,image/bmp'
  input.onchange = () => {
    const f = (input.files && input.files[0]) || null
    if (!f) return
    // 上一次选图的 objectURL 及时释放，避免内存泄漏
    const prev = idPhotoPicked.value
    if (prev && prev.isObjectUrl) URL.revokeObjectURL(prev.path)
    idPhotoPicked.value = {
      path: URL.createObjectURL(f),
      isObjectUrl: true,
      name: f.name,
      sizeText: formatFileSize(f.size),
      size: f.size || 0,
      file: f
    }
    idPhotoResult.value = null
    idPhotoError.value = ''
  }
  input.click()
  return
  // #endif
  // #ifndef H5
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const path = (res.tempFilePaths || [])[0]
      if (!path) return
      const temp = (res.tempFiles || [])[0]
      idPhotoPicked.value = {
        path,
        name: (temp && temp.name) || path.split('/').pop() || '证件照',
        sizeText: formatFileSize((temp && temp.size) || 0),
        size: (temp && temp.size) || 0,
        file: null
      }
      idPhotoResult.value = null
      idPhotoError.value = ''
    },
    fail: () => uni.showToast({ title: '未选择图片', icon: 'none' })
  })
  // #endif
}

/** 调 /api/ai-office/id-photo-bg-change：上传选中的证件照 → 返回合成好底色的新图 */
const runIdPhotoBgChange = async () => {
  const picked = idPhotoPicked.value
  if (!picked) {
    idPhotoError.value = '请先上传证件照'
    uni.showToast({ title: '请先上传证件照', icon: 'none' })
    return
  }
  idPhotoLoading.value = true
  idPhotoError.value = ''
  idPhotoResult.value = null
  try {
    const res = await idPhotoBgChange(picked.file || picked.path, { bgColor: selectedBgColor.value })
    const data = (res && res.data) || null
    if (!res || res.code !== 200 || !data || !data.fileUrl) {
      throw new Error((res && (res.message || res.msg)) || '换背景色失败，请稍后重试')
    }
    idPhotoResult.value = data
  } catch (err) {
    console.error('id-photo-bg-change error:', err)
    idPhotoError.value = (err && err.message) || '换背景色失败，请稍后重试'
  } finally {
    idPhotoLoading.value = false
  }
}

/** 放大预览结果图（uni.previewImage 跨端通用） */
const previewIdPhoto = () => {
  const url = idPhotoResult.value && idPhotoResult.value.fileUrl
  if (!url) return
  uni.previewImage({ urls: [url], current: url })
}

/** 保存/下载结果图：H5 走 a[download]，App 端 uni.downloadFile + openDocument 交系统保存 */
const saveIdPhoto = () => {
  const out = idPhotoResult.value
  if (!out || !out.fileUrl) return
  // #ifdef H5
  const a = document.createElement('a')
  a.href = out.fileUrl
  a.download = out.fileName || 'id-photo-bg-change.png'
  a.target = '_blank'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url: out.fileUrl,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
      } else {
        uni.showToast({ title: '保存失败', icon: 'none' })
      }
    },
    fail: () => uni.showToast({ title: '保存失败', icon: 'none' })
  })
  // #endif
}

const pwdLength = ref(16)
const pwdChars = ref([{ label: '大小写字母', checked: true }, { label: '数字', checked: true }, { label: '特殊符号', checked: true }])
const generatedPassword = ref('7Fq9ik2P@8m3Z#6t')
const pwdStrength = ref(80)
const pwdStrengthLabel = ref('强')
const onPwdLengthTrackClick = () => { pwdLength.value = 12 }
const togglePwdChar = (i) => { pwdChars.value[i].checked = !pwdChars.value[i].checked }
const copyPassword = () => copyRaw(generatedPassword.value, '密码')

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

// 纯转换工具（文档转文本 / 录音转写）：单文件上传，无提示词（提示词后端内置或纯解析）
const isPureConvert = computed(() => !!toolInfo.value.pureConvert)

// 纯转换工具选中的单个文件（{ name, file } —— file 为 H5 File 对象，App 端为 null 走路径）
const pureConvertFile = ref(null)
/** 多选文件列表（录音转写等多文件场景；单文件工具时长度为 1） */
const pureConvertFiles = ref([])

/**
 * 录音转写引擎（minimax / tencent）。
 * 仅 audio-transcribe 有意义；其他纯转换工具（doc-to-text）不发该参数。
 */
const asrEngine = ref('minimax')

/**
 * 引擎选项列表。
 * 只有当 tools.js 配了 engineOptions，且当前确实是录音转写工具时才展示；
 * 服务端 asr.tencent.enabled=false 时即使前端选了 tencent 也会自动回落到 minimax，
 * 因此这里不做额外过滤，保持前端与后端职责分离。
 */
const engineOptions = computed(() => {
  if (toolId.value !== 'audio-transcribe') return []
  return toolInfo.value.engineOptions || []
})

// 切换工具时把引擎重置为该工具的默认值，避免上一个工具的选择残留
watch(toolId, () => {
  asrEngine.value = toolInfo.value.defaultEngine || 'minimax'
}, { immediate: true })

/** 选择文件（纯转换工具）：H5 用原生 input，App 用 chooseMessageFile */
const onPureConvertPick = () => {
  const isAudio = toolId.value === 'audio-transcribe'
  const maxCount = (toolInfo.value.fileRule && toolInfo.value.fileRule.maxCount) || 1
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  // 方案 B：保留现有 mockup 外观，仅把选择逻辑改为支持多选（不引入新组件/样式）
  input.multiple = maxCount > 1
  input.accept = isAudio ? '.mp3,.wav,.m4a,.aac,.flac,.ogg,.amr' : '.pdf,.docx,.txt'
  input.onchange = () => {
    const picked = Array.from(input.files || [])
    if (!picked.length) return
    if (maxCount > 1) {
      // 多选：累加到 pureConvertFiles
      const merged = pureConvertFiles.value.concat(
        picked.map(f => ({ name: f.name, file: f, size: f.size || 0 }))
      )
      if (merged.length > maxCount) {
        uni.showToast({ title: `最多 ${maxCount} 个文件，已截断`, icon: 'none' })
        merged.splice(maxCount)
      }
      pureConvertFiles.value = merged
      // 兼容旧逻辑：pureConvertFile 始终指向第一个
      pureConvertFile.value = merged[0] || null
    } else {
      // 单文件：保持原行为
      pureConvertFile.value = { name: picked[0].name, file: picked[0] }
      pureConvertFiles.value = [pureConvertFile.value]
    }
  }
  input.click()
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: maxCount > 1 ? maxCount : 1,
    type: isAudio ? 'audio' : 'file',
    success: (res) => {
      const picked = (res.tempFiles || []).map(f => ({ name: f.name || f.path, file: null, path: f.path, size: f.size || 0 }))
      if (!picked.length) return
      if (maxCount > 1) {
        const merged = pureConvertFiles.value.concat(picked)
        if (merged.length > maxCount) {
          uni.showToast({ title: `最多 ${maxCount} 个文件，已截断`, icon: 'none' })
          merged.splice(maxCount)
        }
        pureConvertFiles.value = merged
        pureConvertFile.value = merged[0] || null
      } else {
        pureConvertFile.value = picked[0]
        pureConvertFiles.value = [picked[0]]
      }
    }
  })
  // #endif
}

/** 从多选列表中移除一个 */
const removePureConvertFile = (idx) => {
  pureConvertFiles.value.splice(idx, 1)
  pureConvertFile.value = pureConvertFiles.value[0] || null
}

// 通用校验（按工具 + 输入方式）：返回第一个失败的错误文案，null = 通过
// 在 handleGenerate 入口前置校验，不通过直接 return + toast，不进 if-else 分支
const runValidation = () => validate(toolId.value, currentInputType.value, {
  // 证件照换背景色自己维护已选文件（idPhotoPicked），把它映射成通用的 filePath 参与校验
  filePath: isPureConvert.value
    ? (pureConvertFiles.value.length ? 'selected' : '')
    : (idPhotoPicked.value ? 'selected' : filePath.value),
  batchFiles: isPureConvert.value ? pureConvertFiles.value : uploadedFiles.value,
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
  // 从历史详情页「再次使用」进入：自动回填该条历史记录
  if (option.historyId) {
    applyHistoryById(option.historyId)
  }
})

/** 按 id 拉取指定历史记录并回填（供「再次使用」进入时调用） */
const applyHistoryById = async (historyId) => {
  try {
    const res = await historyListByToolApi(toolId.value, 50)
    const list = (res && res.data) || []
    const found = list.find((h) => String(h.id) === String(historyId))
    if (!found) {
      uni.showToast({ title: '历史记录不存在或已删除', icon: 'none' })
      return
    }
    applyHistory(found)
  } catch (e) {
    uni.showToast({ title: '回填历史失败', icon: 'none' })
  }
}

// ===== 历史记录（按当前 toolId 拉后端） =====
// 打开/折叠历史面板：首次打开时拉一次，后续切换走缓存
const toggleHistoryPanel = async () => {
  historyPanelOpen.value = !historyPanelOpen.value
  if (historyPanelOpen.value && historyList.value.length === 0 && !historyLoading.value) {
    await loadHistory()
  }
}
const loadHistory = async () => {
  if (!toolId.value) return
  historyLoading.value = true
  try {
    const res = await historyListByToolApi(toolId.value, HISTORY_DEFAULT_LIMIT)
    const list = (res && res.data) || []
    historyList.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('loadHistory error:', e)
    uni.showToast({ title: '加载历史失败', icon: 'none' })
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}
// 格式化历史时间：把 '2026-09-28T10:00:00' 切成 '09-28 10:00'
const formatHistoryTime = (s) => {
  if (!s) return ''
  // 兼容 'YYYY-MM-DDTHH:mm:ss' 与 'YYYY-MM-DD HH:mm:ss'
  const t = String(s).replace('T', ' ').slice(0, 16)
  // 只取月-日 时:分，去掉年份缩短显示
  return t.slice(5) || t
}
// 说明：自研的 parseInline / markdownToRichTextNodes（markdown → rich-text 节点数组）已删除，
// App 端改由 MarkdownView 内部使用 mp-html 渲染同一份 markdown-it HTML。
// 复制能力已统一到 utils/clipboard.js 的 copyRaw / copyPlain（见文件顶部 import）。
// 点击某条历史：回填文本输入 + 把历史结果写到结果区（不自动调 AI，用户需手动点生成重跑）
//   - 文本类工具(work-summary/weekly-report/meeting-minutes)：inputText + 提示词 + resultContent
//   - meeting-minutes：inputText + 提示词 + meetingMarkdownText + meetingRoute（不重跑，只展示）
//   - 文件类工具(doc-keypoint/ocr-recognize/ai-file-reader)：本次不回填文件，只展示输出
const applyHistory = (item) => {
  if (!item) return
  const input = item.inputContent || ''
  const output = item.outputContent || ''
  // 文件类工具：仅展示输出，不回填 inputText（避免误导——历史 inputContent 可能含文件名）
  if (toolId.value === 'doc-keypoint-extract'
      || toolId.value === 'ocr-recognize'
      || toolId.value === 'ai-file-reader') {
    resultContent.value = output
    uni.showToast({ title: '已展示历史结果，文件请重新上传', icon: 'none', duration: 2000 })
    historyPanelOpen.value = false
    return
  }
  // 文本类：回填 input + 提示词（用户当时 textarea 的原值，让点生成时是"原参数重发"）
  inputText.value = input
  promptFormatText.value = item.promptFormat || ''
  promptGenerateText.value = item.promptGenerate || ''
  // meeting-minutes：还要回填 markdown 输出 + 切到对应渲染模式
  if (toolId.value === 'meeting-minutes') {
    // 优先尝试解析 JSON（meeting-minutes 历史上可能是 JSON 或纯 markdown）
    if (output && (output.trim().startsWith('[') || output.trim().startsWith('{'))) {
      try {
        const parsed = JSON.parse(output)
        if (Array.isArray(parsed)) {
          meetingRoute.value = 'json'
          meetingJsonResult.value = parsed
          meetingMarkdownText.value = ''
          resultContent.value = ''
          historyPanelOpen.value = false
          uni.showToast({ title: '已回填，请点生成重新发送', icon: 'none', duration: 2000 })
          return
        }
      } catch (e) { /* 不是 JSON，按 markdown 走 */ }
    }
    meetingRoute.value = 'sse'
    meetingMarkdownText.value = output
    // 历史回填的是完整结果（无打字过程），直接按"推字已结束"渲染 markdown
    meetingTypingDone.value = true
    meetingJsonResult.value = []
    resultContent.value = ''
  } else {
    // work-summary / weekly-report
    resultContent.value = output
  }
  historyPanelOpen.value = false
  uni.showToast({ title: '已回填，请点生成重新发送', icon: 'none', duration: 2000 })
}

// 切换输入方式时重置状态
const switchInputType = (type) => {
  currentInputType.value = type
  // 切换时清空之前的输入
  inputText.value = ''
  filePath.value = ''
  fileName.value = ''
  fileObj.value = null
  // 重置纯转换工具的多选列表（避免切换后残留上一个工具的文件）
  pureConvertFiles.value = []
  pureConvertFile.value = null
  fileUrl.value = ''
  // 清空批量工具已选文件（原 BatchFilePicker 组件未渲染，改由 uploadedFiles 维护）
  uploadedFiles.value = []
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

// 抽屉弹窗：选中列表项 → 设置预览（不直接应用，让用户点"确定"再应用）
// 抽屉弹窗"确定"事件回调：组件 emit confirm 后调用
const onPromptPickerConfirm = (item) => {
  if (!item) return
  selectedPromptId.value = item.id
  if (promptPickerTarget.value === 'format') {
    promptFormatText.value = item.promptText
  } else {
    promptGenerateText.value = item.promptText
  }
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

// 通用 SSE 文本流式输出（会议纪要：推字中纯文本、打完再渲染 Markdown）：
//   - meeting-minutes：走 createTypewriter（rAF + 自适应速度），逐字推进 meetingMarkdownText
//     推字期间 meetingTypingDone=false（只显示纯文本），结束后再置 true 渲染 markdown
//   - 其他工具：保留旧行为，直接 resultContent.value += chunk
// 入参 url 为后端流式接口地址；返回拼接后的完整文本
//
// 打字机句柄：保存上一轮结果，每次请求时先清掉旧的（防止用户中途取消 / 重新生成时残留）
let currentTypewriter = null
const runTextStream = (url) => {
  return new Promise((resolve, reject) => {
    // 防御：上一轮未清理的句柄先 dispose
    if (currentTypewriter) {
      currentTypewriter.dispose()
      currentTypewriter = null
    }

    const isMeeting = url.includes('meeting-minutes')
    const initialContent = isMeeting ? meetingMarkdownText.value : ''
    let fullText = initialContent
    if (isMeeting) {
      meetingMarkdownText.value = ''
      // 进入推字阶段：先切回纯文本模式，整个推字过程不渲染 markdown
      meetingTypingDone.value = false
      // 为本轮 SSE 创建一个新的打字机
      // onUpdate 每帧推完后回调 → 自动滚动到底(用户未上滑时)
      currentTypewriter = createTypewriter({
        targetRef: meetingMarkdownText,
        onUpdate: () => scrollMeetingToBottom()
      })
    }

    const onChunk = (chunk) => {
      fullText += chunk
      if (isMeeting) {
        // 走打字机：chunk 进队列,rAF 每帧推一批到 meetingMarkdownText
        currentTypewriter?.push(chunk)
      } else {
        resultContent.value += chunk
      }
    }
    const onDone = () => {
      if (isMeeting) {
        // 排空队列 → 唤醒 onComplete → 销毁
        currentTypewriter?.flush()
        currentTypewriter?.dispose()
        currentTypewriter = null
        // 推字结束：切到 markdown 渲染模式（MarkdownView 会由纯文本切到富文本）
        // 必须放在 flush 之后：尾段字符此刻才真正写入 meetingMarkdownText，
        // 否则会拿半截文本去渲染 markdown
        meetingTypingDone.value = true
      }
      resolve(fullText)
    }
    const onError = (err) => {
      if (isMeeting) {
        currentTypewriter?.dispose()
        currentTypewriter = null
        // 出错同样标记推字结束，避免残留内容卡在纯文本模式
        meetingTypingDone.value = true
      }
      reject(err)
    }
    streamRequest({ url, data: {
      content: inputText.value,
      promptFormat: promptFormatText.value,
      promptGenerate: promptGenerateText.value
    }, onChunk, onDone, onError })
  })
}

// 老的打字机实现（onChunk 进 charQueue + setInterval 20ms 推 1 字符）已废弃——
// 现在 runTextStream 把 chunk 交给 createTypewriter 推进 meetingMarkdownText，
// 渲染由 <MarkdownView> 按 streaming 模式自动切换。
/* 旧的实现代码已被上面的新版替换，下方为残留修复占位 */

/**
 * 打字机调度器（rAF 驱动 + 自适应速度 + 队列缓冲）
 *
 * 设计目标:
 *  - 把 SSE chunk 累积到字符队列(不立即 ref 触发渲染)
 *  - 用 requestAnimationFrame 每帧从队列里推 N 个字符 → 改目标 ref
 *  - 速度自适应:队列越长(AI 写入快) → 每帧推得越多;队列清空 → 减速到 BASE_STEP
 *  - 单调性:FIFO,绝不重复消费绝不丢字符
 *  - flush() 用于流结束时强制排空队列
 *  - dispose() 用于组件卸载 / 用户切走 / 取消时停止 rAF
 *
 * @param {Object} options
 *   targetRef     - 要写入的目标 ref(必须是 .value 可写的)
 *   onUpdate?     - 可选:每帧推完后回调(text, isFinal),用于标记 / 光标
 *   onComplete?   - 可选:flush 完最后一次回调
 *   baseStep?     - 基础每帧推多少字(默认 2)
 *   maxStep?      - 最大每帧推多少字(默认 8)
 *   stepRule?     - 自定义调速函数(queuedLen, baseStep, maxStep) -> step
 * @returns {{ push: (str) => void, flush: () => void, dispose: () => void }}
 */
const createTypewriter = (options) => {
  const targetRef = options.targetRef
  const onUpdate = options.onUpdate
  const onComplete = options.onComplete
  const baseStep = options.baseStep || 2
  const maxStep = options.maxStep || 8
  const stepRule = options.stepRule || ((qLen, b, m) => {
    // 默认自适应:队列越长越快,呈线性提升到上限
    if (qLen <= 0) return 0
    if (qLen < 16) return b
    if (qLen < 64) return Math.min(b + 2, m)
    if (qLen < 256) return Math.min(b + 4, m)
    return m
  })

  const queue = []
  let rafId = null
  let disposed = false

  const tick = () => {
    if (disposed) return
    rafId = null
    const queued = queue.length
    if (queued === 0) {
      // 队列空,等下一次 push 或 flush 再唤醒
      return
    }
    const step = Math.min(stepRule(queued, baseStep, maxStep), queued)
    let popped = ''
    for (let i = 0; i < step; i++) {
      popped += queue.shift()
    }
    targetRef.value += popped
    if (onUpdate) onUpdate(targetRef.value, false)
    // 继续下一帧(若队列还有内容)
    scheduleNext()
  }

  const scheduleNext = () => {
    if (disposed || rafId !== null) return
    if (queue.length === 0) return
    rafId = requestAnimationFrame(tick)
  }

  return {
    push(str) {
      if (disposed || !str) return
      // 把字符串切成单个字符推入队列(支持中文 / emoji / surrogate pair)
      // 使用 Array.from 可正确按 Unicode code point 切分,避免 char-split 切坏中文
      const chars = Array.from(String(str))
      for (let i = 0; i < chars.length; i++) queue.push(chars[i])
      scheduleNext()
    },
    flush() {
      if (disposed) return
      if (queue.length === 0) {
        if (onUpdate) onUpdate(targetRef.value, true)
        if (onComplete) onComplete()
        return
      }
      // 排空:一次性把所有剩余字符推到 ref
      let rest = ''
      while (queue.length) rest += queue.shift()
      targetRef.value += rest
      if (onUpdate) onUpdate(targetRef.value, true)
      if (onComplete) onComplete()
    },
    dispose() {
      disposed = true
      queue.length = 0
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
    },
    get pending() { return queue.length }
  }
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
    uni.showToast({title: err, icon: 'none'})
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

    // 证件照换背景色：单图上传 + 底色 → 后端换底色 → 渲染结果图（自有 loading/error 状态，不走通用纯转换分支）
    if (id === 'id-photo-bg-change') {
      await runIdPhotoBgChange()
      return
    }

    // 录音转写若选中多个文件，走批量；单个则退回原有单文件纯转换路径
    const audioBatchFiles = pureConvertFiles.value.length > 1
      ? pureConvertFiles.value
      : uploadedFiles.value.slice()
    if (id === 'audio-transcribe' && audioBatchFiles.length > 0) {
      // 录音转写：B2 多文件批量（轮询方案）；整批共用同一引擎
      const { batchId, fileCount } = await audioBatchUpload({
        files: audioBatchFiles,
        fields: { engine: asrEngine.value }
      })
      batchTotal.value = fileCount
      await pollBatchCompleted(batchId, 'audio-transcribe')
      return
    }

    if (isPureConvert.value) {
      // 纯转换工具（文档转文本 / 录音转写）：单文件上传 → 调对应端点 → 纯文本结果
      const picked = pureConvertFile.value
      if (!picked) {
        uni.showToast({ title: '请先选择文件', icon: 'none' })
        return
      }
      const url = id === 'audio-transcribe'
        ? '/api/ai-office/meeting-minutes/transcribe'
        : '/api/ai-office/doc-to-text'
      const upRes = await new Promise((resolve, reject) => {
        uni.uploadFile({
          url: BASE_URL + url,
          filePath: picked.path || picked.file,
          name: 'file',
          // 录音转写透传引擎；文档转文本不带该字段
          formData: id === 'audio-transcribe' ? { engine: asrEngine.value } : {},
          header: { Authorization: 'Bearer ' + (uni.getStorageSync('token') || '') },
          success: resolve,
          fail: reject
        })
      })
      let body = {}
      try { body = JSON.parse(upRes.data || '{}') } catch (e) { /* ignore */ }
      if (body.code !== 200) {
        uni.showToast({ title: body.message || '转换失败', icon: 'none', duration: 2500 })
        resultContent.value = body.message || '转换失败'
        return
      }
      const text = (body.data && (body.data.text || '')) || ''
      resultContent.value = text
      if (!text) uni.showToast({ title: '未提取到内容', icon: 'none' })

    } else if (id === 'doc-keypoint-extract') {
      // 重点提取：纯文字输入 → SSE 流式（与会议纪要一致）
      await runTextStream('/api/ai-office/document-summary/text-stream')
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
      // 流式打字机开始前重置滚动状态(确保新一轮自动滚到底)
      resetMeetingScroll()
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
        // SSE 路径：调 /stream 端点流式 markdown 文本，由 MarkdownView 渲染
        // 注意：runTextStream 已经通过打字机把内容推到 meetingMarkdownText，不要再用 resultContent 覆盖
        await runTextStream('/api/ai-office/meeting-minutes/stream')
      }
    } else if (id === 'work-summary') {
      // 工作总结：纯文字输入 → SSE 流式
      await runTextStream('/api/ai-office/work-summary/stream')
    } else if (id === 'weekly-report') {
      // 周报生成：纯文字输入 → SSE 流式
      await runTextStream('/api/ai-office/weekly-report/stream')
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
/* 全局 SVG 兜底：默认尺寸 + 跟随父颜色（避免父容器缺 size 时显示异常） */
svg {
  width: 100%;
  height: 100%;
  fill: currentColor;
}
/* ============ 提示词输入卡（meeting-minutes 专用） ============ */
.prompt-card {
  margin: 0 32rpx 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 24rpx 32rpx;
}
.prompt-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.prompt-card__label {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
}
.prompt-card__pick {
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 48rpx;
  padding: 0 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  font-size: 24rpx;
  border-radius: 16rpx;
  cursor: pointer;
  flex-shrink: 0;
}
.prompt-card__pick svg {
  width: 28rpx;
  height: 28rpx;
  fill: currentColor;
  flex-shrink: 0;
}
.prompt-card__textarea {
  width: 100%;
  min-height: 140rpx;
  padding: 16rpx 0;
  border: none;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  background: transparent;
  resize: none;
  outline: none;
  font-family: inherit;
  line-height: 1.6;
}
.prompt-card__textarea::placeholder {
  color: var(--text-tertiary, #9CA3AF);
}
.prompt-card__counter {
  display: block;
  text-align: right;
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
  margin-top: 8rpx;
}

/* ============ 抽屉式 prompt-picker 弹窗样式（按图 4：左列表 + 右预览） ============ */
.prompt-drawer-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  justify-content: flex-end;
}
.prompt-drawer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 90vh;
  background: var(--bg-page, #F9FAFB);
  display: flex;
  flex-direction: column;
  border-top-left-radius: 32rpx;
  border-top-right-radius: 32rpx;
  overflow: hidden;
  animation: drawerSlideUp 0.3s ease-out;
  box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.12);
}
@keyframes drawerSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.prompt-drawer__header {
  padding: 24rpx 32rpx 32rpx;
  background: linear-gradient(135deg, #EEF2FF 0%, #F5F3FF 100%);
  border-bottom: 1rpx solid var(--border-color, #E5E7EB);
}
.prompt-drawer__status {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-bottom: 16rpx;
}
.prompt-drawer__title-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.prompt-drawer__icon-bg {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-drawer__icon-bg svg {
  width: 40rpx;
  height: 40rpx;
  fill: #FFFFFF;
}
.prompt-drawer__title-text {
  flex: 1;
  min-width: 0;
}
.prompt-drawer__name {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
}
.prompt-drawer__desc {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-top: 4rpx;
}
.prompt-drawer__close {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #4B5563);
  cursor: pointer;
  z-index: 10;
}
.prompt-drawer__close svg {
  width: 36rpx;
  height: 36rpx;
  fill: currentColor;
}
.prompt-drawer__body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.prompt-drawer__left {
  width: 50%;
  display: flex;
  flex-direction: column;
  border-right: 1rpx solid var(--divider-color, #F0F0F0);
  background: var(--bg-card, #FFFFFF);
}
.prompt-drawer__title-bar {
  padding: 24rpx 32rpx 16rpx;
}
.prompt-drawer__modal-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
}
.prompt-drawer__tab-bar {
  display: flex;
  gap: 16rpx;
  padding: 0 32rpx 16rpx;
}
.prompt-drawer__tab-item {
  padding: 12rpx 28rpx;
  font-size: 26rpx;
  color: var(--text-secondary, #4B5563);
  font-weight: 500;
  border-radius: 999rpx;
  background: var(--bg-card, #FFFFFF);
  border: 1rpx solid var(--border-color, #E5E7EB);
  cursor: pointer;
}
.prompt-drawer__tab-item.is-active {
  color: #FFFFFF;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  border-color: transparent;
}
.prompt-drawer__search {
  margin: 0 32rpx 16rpx;
  padding: 16rpx 20rpx;
  background: var(--bg-page, #F3F4F6);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.prompt-drawer__search svg {
  width: 28rpx;
  height: 28rpx;
  fill: var(--text-tertiary, #9CA3AF);
  flex-shrink: 0;
}
.prompt-drawer__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  font-family: inherit;
}
.prompt-drawer__list {
  flex: 1;
  padding: 0 32rpx 24rpx;
}
.prompt-list-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
  border-radius: 16rpx;
  cursor: pointer;
  transition: background 0.2s;
}
.prompt-list-item.is-active {
  background: var(--brand-primary-light, #EFF6FF);
}
.prompt-list-item__icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-list-item__icon svg {
  width: 28rpx;
  height: 28rpx;
  fill: currentColor;
}
.prompt-list-item__body {
  flex: 1;
  min-width: 0;
}
.prompt-list-item__name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.prompt-list-item__meta {
  display: block;
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.prompt-list-empty {
  text-align: center;
  padding: 80rpx 0;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 26rpx;
}
.prompt-drawer__right {
  width: 50%;
  background: var(--bg-page, #F9FAFB);
  overflow-y: auto;
}
.prompt-preview {
  padding: 32rpx;
}
.prompt-preview__header {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
  margin-bottom: 24rpx;
}
.prompt-preview__icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-preview__icon svg {
  width: 32rpx;
  height: 32rpx;
  fill: currentColor;
}
.prompt-preview__title-block {
  flex: 1;
  min-width: 0;
}
.prompt-preview__name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 12rpx;
  word-break: break-all;
}
.prompt-preview__tag-row {
  display: flex;
  gap: 8rpx;
}
.prompt-preview__tag {
  display: inline-block;
  padding: 4rpx 12rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  font-size: 22rpx;
  border-radius: 8rpx;
}
.prompt-preview__desc-title {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 16rpx;
}
.prompt-preview__desc-text {
  display: block;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
.prompt-preview--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 28rpx;
}
.prompt-drawer__bottom {
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 0rpx));
  background: var(--bg-card, #FFFFFF);
  border-top: 1rpx solid var(--divider-color, #F0F0F0);
}
.prompt-drawer__confirm-btn {
  width: 100%;
  height: 88rpx;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #FFFFFF;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 500;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.prompt-drawer__confirm-btn[disabled] {
  opacity: 0.4;
}

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
/* 带右侧操作按钮的 section title 行（用于结果区复制按钮） */
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 32rpx 16rpx;
  gap: 16rpx;
}
.section-title-row .section-title {
  margin: 0;
}
.result-actions {
  display: flex;
  gap: 12rpx;
  flex-shrink: 0;
}
.result-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 18rpx;
  background: var(--bg-card, #FFFFFF);
  border: 1rpx solid var(--border-color, #E5E7EB);
  border-radius: 12rpx;
  font-size: 22rpx;
  color: var(--text-secondary, #6B7280);
  transition: all 0.15s;
}
.result-action-btn:active {
  background: var(--bg-hover, rgba(59,130,246,0.06));
  color: var(--color-primary, #3B82F6);
}
.result-action-icon {
  width: 28rpx;
  height: 28rpx;
  fill: currentColor;
  flex-shrink: 0;
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

/* 转写引擎选择（录音转写） */
.engine-group {
  margin: 0 32rpx 24rpx;
}
.engine-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: var(--bg-card, #FFFFFF);
  border: 2rpx solid transparent;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  transition: border-color 0.2s, background 0.2s;
}
.engine-item--active {
  border-color: var(--brand-primary, #3B82F6);
  background: var(--brand-primary-light, #EFF6FF);
}
.engine-item__radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 3rpx solid var(--text-tertiary, #9CA3AF);
  flex-shrink: 0;
}
.engine-item__radio--on {
  border-color: var(--brand-primary, #3B82F6);
  background: var(--brand-primary, #3B82F6);
  box-shadow: inset 0 0 0 6rpx var(--bg-card, #FFFFFF);
}
.engine-item__body {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
}
.engine-item__label {
  font-size: 28rpx;
  color: var(--text-primary, #1F2937);
  font-weight: 500;
}
.engine-item__desc {
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
}

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
  width: 64rpx;
  height: 64rpx;
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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
  width: 56rpx;
  height: 56rpx;
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.input-card__icon svg { width: 52rpx; height: 52rpx; fill: currentColor; }
.input-card__label {
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
}
.input-card__sub { font-size: 22rpx; color: var(--text-tertiary, #9CA3AF); }

/* 表单卡（会议信息） */

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

/* ============ 证件照换背景色（id-photo-bg-change） ============ */
/* 已选证件照（缩略图 + 文件名 + 重新选择） */
.idphoto-picked {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
  padding: 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
}
.idphoto-picked__img {
  width: 112rpx;
  height: 112rpx;
  border-radius: 16rpx;
  background: var(--bg-gray, #F3F4F6);
  flex-shrink: 0;
}
.idphoto-picked__body { flex: 1; min-width: 0; }
.idphoto-picked__name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.idphoto-picked__meta { display: block; margin-top: 4rpx; font-size: 24rpx; color: var(--text-tertiary, #9CA3AF); }
.idphoto-picked__change {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 18rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  font-size: 22rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}
.idphoto-picked__change svg { width: 28rpx; height: 28rpx; fill: currentColor; }

/* loading */
.idphoto-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  margin: 0 32rpx 24rpx;
  padding: 64rpx 32rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
}
.idphoto-loading__spinner {
  width: 56rpx;
  height: 56rpx;
  border: 4rpx solid var(--brand-primary-light, #EFF6FF);
  border-top-color: var(--brand-primary, #3B82F6);
  border-radius: 50%;
  animation: idphotoSpin 0.9s linear infinite;
}
@keyframes idphotoSpin {
  to { transform: rotate(360deg); }
}
.idphoto-loading__text { font-size: 26rpx; color: var(--text-secondary, #6B7280); }

/* 错误提示（可重试） */
.idphoto-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin: 0 32rpx 24rpx;
  padding: 24rpx 32rpx;
  background: rgba(239, 68, 68, 0.08);
  border: 1rpx solid rgba(239, 68, 68, 0.2);
  border-radius: 24rpx;
}
.idphoto-error__text { flex: 1; min-width: 0; font-size: 26rpx; color: #EF4444; }
.idphoto-error__retry {
  padding: 10rpx 28rpx;
  background: #EF4444;
  color: #FFFFFF;
  font-size: 24rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
}

/* 结果区 */
.idphoto-result {
  margin: 0 32rpx 24rpx;
  padding: 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
}
.idphoto-result__img {
  width: 100%;
  border-radius: 16rpx;
  background: var(--bg-gray, #F3F4F6);
  display: block;
}
.idphoto-result__meta { margin-top: 24rpx; }
.idphoto-result__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid var(--border-color, #F3F4F6);
}
.idphoto-result__row:last-child { border-bottom: none; }
.idphoto-result__k {
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  flex-shrink: 0;
}
.idphoto-result__v {
  font-size: 24rpx;
  color: var(--text-primary, #111827);
  text-align: right;
  word-break: break-all;
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
/* ===== 会议纪要结果区 ===== */
/* markdown 正文（h1-h6 / p / ul / ol / table / pre / code …）的标签样式统一由全局主题
   src/styles/markdown.css 提供（作用域 .markdown-body），此处只保留滚动卡片外框 */
/* 注意：v-html 注入的子元素没有 data-v-xxx 属性，不要在这里写 scoped 的标签选择器 */
.mm-md-result {
  margin: 0 16px 12px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 12px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary, #111827);
  word-wrap: break-word;
  overflow-wrap: break-word;
}
/* 说明：原先针对 .mm-md-html / .mm-md-result 的 :deep() 标签样式已删除，
   统一收敛到全局主题 src/styles/markdown.css（.markdown-body） */

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

/* ===== 历史记录面板（工具详情页内嵌） ===== */
.history-panel {
  margin: 24rpx $spacing-md 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.history-panel__header {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  gap: 16rpx;
}
.history-panel__icon {
  width: 36rpx;
  height: 36rpx;
  fill: var(--color-primary, #3B82F6);
  flex-shrink: 0;
}
.history-panel__title {
  flex: 1;
  font-size: 28rpx;
  color: var(--text-primary, #111827);
  font-weight: 500;
}
.history-panel__chevron {
  width: 36rpx;
  height: 36rpx;
  fill: var(--text-secondary, #6B7280);
  flex-shrink: 0;
  transition: transform 0.2s;
}
.history-panel.is-open .history-panel__chevron {
  transform: rotate(0deg);
}
.history-panel__body {
  border-top: 1rpx solid var(--border-color, #E5E7EB);
  padding: 8rpx 0;
}
.history-panel__loading,
.history-panel__empty {
  padding: 48rpx 0;
  text-align: center;
  color: var(--text-secondary, #6B7280);
  font-size: 26rpx;
}
.history-panel__list {
  display: flex;
  flex-direction: column;
}
.history-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--border-color-light, #F3F4F6);
  transition: background-color 0.15s;
}
.history-item:last-child {
  border-bottom: none;
}
.history-item:active {
  background: var(--bg-hover, rgba(59,130,246,0.04));
}
.history-item__left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  overflow: hidden;
}
.history-item__status {
  font-size: 22rpx;
  font-weight: 500;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  align-self: flex-start;
}
.history-item__status.is-ok {
  color: #059669;
  background: rgba(16,185,129,0.1);
}
.history-item__status.is-fail {
  color: #DC2626;
  background: rgba(239,68,68,0.1);
}
.history-item__input {
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-item__right {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-left: 16rpx;
}
.history-item__time {
  font-size: 22rpx;
  color: var(--text-secondary, #6B7280);
  white-space: nowrap;
}
.history-item__chev {
  width: 32rpx;
  height: 32rpx;
  fill: var(--text-secondary, #6B7280);
}

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
