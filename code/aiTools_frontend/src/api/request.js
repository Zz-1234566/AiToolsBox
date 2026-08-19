import { ERROR_CODE } from './errorCode'
import { BASE_URL } from '../config/env'

/**
 * 通用请求封装
 * @param {Object} options - { url, method, data, header, responseType }
 * @returns {Promise}
 */
export const request = (options) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const headers = {
      'Content-Type': 'application/json',
      ...options.header
    }
    if (token) {
      headers['Authorization'] = 'Bearer ' + token
    }

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: headers,
      responseType: options.responseType || 'text',
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const data = res.data
          // 检查业务错误码
          if (data.code === ERROR_CODE.SUCCESS) {
            resolve(data)
          } else if (data.code === 401) {
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.showToast({ title: data.message || '登录已过期', icon: 'none' })
            reject(new Error(data.message))
          } else {
            // 其他业务错误，显示后端返回的消息
            uni.showToast({ title: data.message || '操作失败', icon: 'none' })
            reject(new Error(data.message))
          }
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          reject(new Error('未登录'))
        } else {
          uni.showToast({ title: '请求失败', icon: 'none' })
          reject(new Error('请求失败'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络请求失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 文件上传请求
 * @param {String} url - 接口路径
 * @param {String} filePath - 本地文件路径
 * @param {String} name - 文件字段名
 * @param {Object} formData - 额外的表单字段（如 prefix），非空时才随请求携带
 * @returns {Promise}
 */
export const uploadFile = (url, filePath, name = 'file', formData = {}) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const headers = {}
    if (token) {
      headers['Authorization'] = 'Bearer ' + token
    }

    const options = {
      url: BASE_URL + url,
      filePath,
      name,
      header: headers
    }
    // 仅当 formData 非空时才附带，保证现有调用（如头像上传）行为不变
    if (formData && Object.keys(formData).length > 0) {
      options.formData = formData
    }

    uni.uploadFile({
      ...options,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          resolve(data)
        } else {
          reject(new Error('上传失败'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '文件上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

/**
 * 文件上传（带 formData，用于 multipart/form-data 且需要额外字段的场景）
 */
export const uploadWithFormData = (url, filePath, formData = {}, name = 'image') => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const header = {}
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }

    uni.uploadFile({
      url: BASE_URL + url,
      filePath,
      name,
      formData,
      header,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          // remove-bg 返回二进制，不解析 JSON
          if (res.data instanceof ArrayBuffer || typeof res.data === 'object') {
            resolve(res.data)
          } else {
            try {
              resolve(JSON.parse(res.data))
            } catch {
              resolve(res.data)
            }
          }
        } else {
          reject(new Error('上传失败'))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}
