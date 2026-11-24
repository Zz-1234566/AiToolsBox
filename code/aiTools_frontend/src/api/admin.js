import { request } from './request'

/**
 * 管理员接口（仅 role=admin 可用，后端二次校验）。
 *
 *   GET /api/admin/profiles?keyword=      所有用户的画像摘要 + 词条
 *   PUT /api/admin/profiles/{userId}      修改某用户的画像摘要 / 词条
 */

/** 用户画像词条列表 */
export const listAdminProfiles = (keyword = '') =>
  request({ url: '/api/admin/profiles', method: 'GET', data: keyword ? { keyword } : {} })

/** 修改某用户的画像（profileText / preferences） */
export const updateAdminProfile = (userId, body) =>
  request({ url: `/api/admin/profiles/${userId}`, method: 'PUT', data: body })
