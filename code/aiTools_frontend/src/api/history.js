import { request } from './request'

// 从 storage / token 拿当前用户 id（不依赖后端 token 解析，后端 /api/history/list 直接收 userId 入参）
//   - 优先取登录态 userInfo.id（后端 login/register 接口返回的 userId 字段）
//   - 兜底从 storage 取
function _resolveUserId() {
  try {
    const u = uni.getStorageSync('userInfo')
    if (u && u.id) return u.id
    if (u && u.userId) return u.userId
  } catch (e) { /* 静默 */ }
  return null
}

// 查询最近历史记录（全部工具）
export const historyListApi = () => {
  const userId = _resolveUserId()
  return request({ url: '/api/history/list', method: 'GET', data: { userId } })
}

// 查询指定工具的历史记录（按 aiCode 过滤，limit 可选）
// @param aiCode  工具编码（work-summary / meeting-minutes / ...）
// @param limit   返回条数上限（1 <= limit <= 50）
export const historyListByToolApi = (aiCode, limit = 10) => {
  const userId = _resolveUserId()
  return request({
    url: '/api/history/list',
    method: 'GET',
    data: { userId, aiCode, limit }
  })
}

// 删除历史记录
export const historyDeleteApi = (id) => request({ url: `/api/history/${id}`, method: 'DELETE' })

// 清空历史记录（全部）
export const historyClearAllApi = () => request({ url: '/api/history/clear', method: 'DELETE' })
