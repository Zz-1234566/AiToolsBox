package uts.sdk.modules.srFilePicker

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.dcloud.uts.UTSAndroid
import io.dcloud.uts.console

object NativeCode {

    private const val REQUEST_CODE_PICK_FILE = 1001

    // 定义一个回调接口，用于返回文件路径
    private var filePickerCallback: ((String?) -> Unit)? = null

    /**
     * 打开文件选择器
     * @param callback 回调函数，用于返回选中的文件路径
     */
    fun openFilePicker(callback: (String?) -> Unit) {
		console.log("open filepicker")
        // 保存回调函数
        filePickerCallback = callback

        // 创建文件选择 Intent
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // 选择所有类型的文件
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        // 启动文件选择器
        val activity = UTSAndroid.getUniActivity()
        activity?.startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
    }

    /**
     * 处理文件选择结果
     */
	
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		console.log("Selected file")
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                // 获取文件路径
                val filePath = getFilePathFromUri(uri)
                console.log("Selected file path: ", filePath)

                // 通过回调返回文件路径
                filePickerCallback?.invoke(filePath)
            } else {
                // 如果没有选中文件，返回 null
                filePickerCallback?.invoke(null)
            }
        } else {
            // 用户取消选择或选择失败，返回 null
            filePickerCallback?.invoke(null)
        }

        // 清空回调，避免内存泄漏
        filePickerCallback = null
    }

    /**
     * 从 Uri 中获取文件路径
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        val context = UTSAndroid.getUniActivity()
        val cursor = context?.contentResolver?.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                // 获取文件名
                val displayName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                // 获取文件路径（实际路径可能无法直接获取，返回 Uri 的字符串形式）
                uri.toString()
            } else {
                null
            }
        }
    }
}