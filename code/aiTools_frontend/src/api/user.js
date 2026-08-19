import { request } from './request'

// ==================== 用户账号 ====================

/**
 * 退出登录
 * @returns {Promise}
 */
export const logoutApi = () => {
  return request({ url: '/api/user/logout', method: 'POST' })
}

/**
 * 通过邮箱找回账号
 * @param {String} email - 注册邮箱
 * @returns {Promise} { account, username }
 */
export const findAccountApi = (email) => {
  return request({ url: '/api/user/find-account', method: 'POST', data: { email } })
}

/**
 * 发送邮箱验证码
 * @param {Object} data - { email, type }，type: register | reset-password
 * @returns {Promise}
 */
export const sendCodeApi = (data) => {
  return request({ url: '/api/mail/send-code', method: 'POST', data })
}

/**
 * 重置密码（通过邮箱验证码）
 * @param {Object} data - { account, code, newPassword, confirmPassword }
 * @returns {Promise}
 */
export const resetPasswordApi = (data) => {
  return request({ url: '/api/user/reset-password', method: 'POST', data })
}

/**
 * 修改密码
 * @param {String} oldPassword - 旧密码
 * @param {String} newPassword - 新密码（6-20位）
 * @returns {Promise}
 */
export const changePasswordApi = (oldPassword, newPassword) => {
  return request({
    url: '/api/user/change-password',
    method: 'POST',
    data: { oldPassword, newPassword }
  })
}

/**
 * 更新个人资料
 * @param {Object} data - { username, avatar }
 * @returns {Promise} 更新后的用户信息
 */
export const updateProfileApi = (data) => {
  return request({ url: '/api/user/update-profile', method: 'POST', data })
}
