// 后端服务地址
// P2-B9: 不再硬编码开发机内网 IP。规则：
//   1. 默认 localhost（本机调试用，无需改源码）
//   2. 演示 / 答辩时需要同局域网手机访问：手动改 BASE_URL 为电脑 IP（HBuilderX 实时生效）
//   3. 生产部署：改成 https 公网域名
// 切换环境时只需修改此处，api/request.js 与 api/stream.js 统一引用。
export const BASE_URL = 'http://localhost:8080'  // 同局域网手机访问时改成电脑 IP，如 'http://192.168.1.100:8080'
