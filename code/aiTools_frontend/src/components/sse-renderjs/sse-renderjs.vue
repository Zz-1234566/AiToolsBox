<template>
  <!--
    视图层 SSE 传输组件（App 端专用）。

    为什么需要它：App 端 uni-app 是「逻辑层(jscore) / 视图层(webview)」分离架构，
    逻辑层运行在 jscore 下、**没有 XMLHttpRequest**（官方明确：js 运行在 jscore 下，
    没有浏览器专有对象），所以 SSE 请求必须由视图层 webview 发起。

    本组件的职责**仅限于传输**：
    - 视图层：发起 XHR、读取增量 responseText、把**累计全文**回传逻辑层
    - 逻辑层：解析 SSE 事件（复用 api/stream.js 的 createSseEventParser，H5/App 同一份）

    为什么回传「累计全文」而不是「增量 chunk」：
    现有 createSseEventParser.feed(fullText) 内部用 lastIndex 做增量消费，
    语义与 XHR 的 responseText 一致。传累计全文 → 解析器零改动、两端行为一致；
    传增量 chunk → 解析器会把除首段外的内容全部丢弃。

    ⚠️ 官方约束（决定本组件的写法）：
    - renderjs 只能内联在组件里，不能放独立 js 文件
    - 视图层不能使用 uni API、不能直接访问逻辑层数据
    - callMethod 只能调用逻辑层 Options API 的 methods（故本组件用 Options API 承载）
  -->
  <view
    class="sse-renderjs-host"
    :renderjs="sseRenderjs.rs"
    :req="req"
    :change:req="sseRenderjs.onReqChange"
  ></view>
</template>

<!-- ==================== 视图层（renderjs） ==================== -->
<script module="sseRenderjs" lang="renderjs">
export default {
  data() {
    return {
      name: 'sse-renderjs'
    }
  },
  methods: {
    /** 组件创建时的入口（视图层） */
    rs() {
      // 视图层无需初始化：XHR 在收到 req 变更时才发起
    },

    /**
     * 逻辑层 → 视图层：观测到 req 变化 → 发起 XHR
     * 官方约定的 change 绑定签名：(newValue, oldValue, ownerVm, vm)
     *
     * ⚠️ newValue 为 null 表示「取消」（逻辑层把 req 置空）。
     *    此时必须真正 abort 掉底层 XHR，否则请求会继续跑到后端超时。
     */
    onReqChange(newValue, oldValue, ownerVm) {
      if (!newValue || !newValue.reqId) {
        // 取消信号：真正中止底层请求
        this._abortXhr()
        return
      }
      this._ownerVm = ownerVm
      this._start(newValue)
    },

    /** 真正发起请求（视图层） */
    _start(req) {
      // 同一时刻只允许一个流；新的请求会中止旧的
      this._abortXhr()
      const xhr = new XMLHttpRequest()
      // reqId 绑定到本次 XHR 实例上（不读可变的 this._reqId）：
      // 否则旧请求 abort 回调会拿到新请求的 reqId，把新请求的回调误清掉
      const reqId = req.reqId
      this._xhr = xhr
      xhr.open('POST', req.url, true)
      xhr.setRequestHeader('Content-Type', 'application/json')
      if (req.token) {
        xhr.setRequestHeader('Authorization', 'Bearer ' + req.token)
      }
      xhr.onprogress = () => {
        // 回传**累计全文**（与 xhr.responseText 语义一致）
        // ⚠️ 必须传**单个对象**：运行时的 callMethod 实现是 vm[method](args)，
        //    args 整个数组作为「一个参数」传入、不会展开。
        //    传数组会导致逻辑层拿不到 reqId，数据被静默丢弃。
        this._emit('onRawData', { reqId: reqId, text: xhr.responseText || '' })
      }
      xhr.onload = () => {
        if (xhr.status >= 400) {
          // 非 2xx：响应体是错误 JSON，不是 SSE 流
          // 把状态码一并回传，逻辑层据此区分 401（登录过期）与其他错误
          this._emit('onRawError', { reqId: reqId, message: '请求失败（' + xhr.status + '）', status: xhr.status })
          return
        }
        // 结束前再补一次全文，确保尾部事件不丢
        this._emit('onRawData', { reqId: reqId, text: xhr.responseText || '' })
        this._emit('onRawDone', { reqId: reqId })
      }
      xhr.onerror = () => {
        this._emit('onRawError', { reqId: reqId, message: '网络异常', status: 0 })
      }
      xhr.onabort = () => {
        // 主动中止（切页面 / 组件卸载 / 取消），不当作错误
        this._emit('onRawAbort', { reqId: reqId })
      }
      xhr.send(req.body || '{}')
    },

    /** 中止底层 XHR（视图层内部） */
    _abortXhr() {
      if (this._xhr) {
        try { this._xhr.abort() } catch (e) { /* ignore */ }
        this._xhr = null
      }
    },

    /** 视图层 → 逻辑层：callMethod 只能调 Options API 的 methods */
    _emit(methodName, payload) {
      try {
        if (this._ownerVm && typeof this._ownerVm.callMethod === 'function') {
          // ⚠️ 传单个对象（不是数组）：运行时实现为 vm[method](args)，数组不会展开
          this._ownerVm.callMethod(methodName, payload)
        }
      } catch (e) {
        // 通信失败不应影响视图层自身
      }
    }
  }
}
</script>

<!-- ==================== 逻辑层 ==================== -->
<script>
import { registerSseEmitter, unregisterSseEmitter, emitSseEvent, clearSseCallbacks } from '@/utils/sseBridge'

export default {
  data() {
    return {
      // 逻辑层 → 视图层的唯一通道：req 变化会触发视图层的 :change:req
      req: null
    }
  },
  mounted() {
    // 注册为当前 SSE 传输实现（api/stream.js 通过它发起请求）
    registerSseEmitter(this)
  },
  beforeUnmount() {
    // 传入自身实例：从注册表栈中精确移除自己，
    // 上一位（仍在页面栈中的页面）会自动恢复为当前实现
    unregisterSseEmitter(this)
    if (this.req && this.req.reqId) {
      clearSseCallbacks(this.req.reqId)
    }
    // 置空 req → 触发视图层 :change:req → 真正 abort 掉底层 XHR。
    // 组件被销毁时若不中止，请求会继续跑到后端超时。
    this.req = null
  },
  methods: {
    /**
     * 逻辑层发起一次流式请求。
     * @param {Object} p { reqId, url, token, body }
     */
    start(p) {
      this.req = Object.assign({}, p)
    },

    /** 中止进行中的请求 */
    abort() {
      if (this.req && this.req.reqId) {
        clearSseCallbacks(this.req.reqId)
      }
      this.req = null
    },

    // ---- 以下四个由视图层 callMethod 调起（必须是 Options API 的 methods）----
    // ⚠️ 运行时实现是 vm[method](args)，args 整个作为「一个参数」传入、不会展开。
    //    所以视图层传单个对象，这里用单参解构接收 —— 不能写成 (reqId, text)。

    /** 收到增量数据（累计全文） */
    onRawData(p) {
      if (!p) return
      emitSseEvent(p.reqId, 'data', p.text)
    },

    /** 流正常结束 */
    onRawDone(p) {
      if (!p) return
      emitSseEvent(p.reqId, 'done', null)
    },

    /** 流出错（status 为 HTTP 状态码，可能为 0） */
    onRawError(p) {
      if (!p) return
      emitSseEvent(p.reqId, 'error', { message: p.message || '请求失败', status: p.status || 0 })
    },

    /** 请求被主动中止（不算错误） */
    onRawAbort(p) {
      if (!p) return
      clearSseCallbacks(p.reqId)
    }
  }
}
</script>

<style scoped>
/* 视图层需要一个真实存在的 DOM 节点承载 renderjs 绑定，
   但它没有任何可见内容 —— 尺寸置 0 且不拦截触摸 */
.sse-renderjs-host {
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
</style>
