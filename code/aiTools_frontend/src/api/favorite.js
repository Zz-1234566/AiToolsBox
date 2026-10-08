import { request } from './request'

/**
 * 收藏 API（工具 / 提示词），对应后端 /api/favorite。
 * @param targetType 'tool' 工具 | 'prompt' 提示词
 * @param targetId   工具传 toolCode；提示词传 promptId
 */

/** 收藏列表（可不传 targetType 取全部） */
export const favoriteListApi = (targetType) =>
  request({ url: '/api/favorite/list', method: 'GET', data: targetType ? { targetType } : {} })

/** 添加收藏 */
export const favoriteAddApi = (targetType, targetId) =>
  request({ url: '/api/favorite/add', method: 'POST', data: { targetType, targetId } })

/** 取消收藏 */
export const favoriteRemoveApi = (targetType, targetId) =>
  request({ url: '/api/favorite/remove', method: 'DELETE', data: { targetType, targetId } })
