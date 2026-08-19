import { request } from './request'

// 查询最近历史记录
export const historyListApi = () => request({ url: '/api/history/list', method: 'GET' })

// 删除历史记录
export const historyDeleteApi = (id) => request({ url: `/api/history/${id}`, method: 'DELETE' })

// 清空历史记录（全部）
export const historyClearAllApi = () => request({ url: '/api/history/clear', method: 'DELETE' })
