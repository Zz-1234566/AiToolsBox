import { request } from './request'

/** 技能列表 */
export const listSkills = () => request({ url: '/api/skill/list', method: 'GET' })
/** 技能详情（线上 + 草稿 + dirty） */
export const getSkill = (code) => request({ url: `/api/skill/${code}`, method: 'GET' })
/** 保存草稿 */
export const saveSkillDraft = (code, body) =>
  request({ url: `/api/skill/${code}/draft`, method: 'PUT', data: { body } })
/** 发布 */
export const publishSkill = (code) => request({ url: `/api/skill/${code}/publish`, method: 'POST' })
/** 从文件重新导入到草稿 */
export const reimportSkill = (code) => request({ url: `/api/skill/${code}/reimport`, method: 'POST' })

/** 放弃修改：草稿回滚为线上版本 */
export const discardSkill = (code) => request({ url: `/api/skill/${code}/discard`, method: 'POST' })
/** 试用草稿：用当前草稿跑一次，不影响线上 */
export const trySkillDraft = (code, goal) =>
  request({ url: `/api/skill/${code}/try`, method: 'POST', data: { goal } })
