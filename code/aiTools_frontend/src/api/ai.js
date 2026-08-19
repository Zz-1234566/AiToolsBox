import { uploadFile } from './request'

// ==================== 文件上传 ====================

/**
 * 上传文件
 * @param {String} filePath - 本地文件路径
 * @param {String} prefix - 存储分区前缀（默认 avatar，工具文件传 'file'）
 */
export const uploadFileApi = (filePath, prefix = 'avatar') => {
  return uploadFile('/api/file/upload', filePath, 'file', { prefix })
}
