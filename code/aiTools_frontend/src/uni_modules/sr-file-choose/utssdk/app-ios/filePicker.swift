import Foundation
import UIKit
import MobileCoreServices
// UTS内置对象的引用
import DCloudUTSFoundation

@objc public class NativeCode: NSObject {
    
    /// 当前视图控制器，用于弹出文件选择器
    @objc static var currentViewController: UIViewController?
    /// 文件选择器代理对象
    private static var filePickerDelegate: FilePickerDelegate?
    /// 打开文件选择器
    @objc static func openFilePicker(_ callback: @escaping (_ fileURL: String?, _ size: Int) -> Void) {
        // 确保当前视图控制器存在
        guard let viewController = currentViewController else {
            callback(nil, 0)
            return
        }
		
        // 创建文件选择器
        let documentPicker = UIDocumentPickerViewController(documentTypes: [String(kUTTypeData)], in: .open)
		filePickerDelegate = FilePickerDelegate(callback: callback) // 强引用持有 delegate
        documentPicker.delegate = filePickerDelegate
        documentPicker.modalPresentationStyle = .formSheet
		
        // 在主线程中执行 UI 操作
        DispatchQueue.main.async {                        
            // 弹出文件选择器
            viewController.present(documentPicker, animated: true, completion: nil)
        }
    }
}

// MARK: - 文件选择器代理
class FilePickerDelegate: NSObject, UIDocumentPickerDelegate {
    private var callback: (_ fileURL: String?, _ size: Int) -> Void
    
    init(callback: @escaping (_ fileURL: String?, _ size: Int) -> Void) {
        self.callback = callback
    }
    
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
		// 用户选择了文件
		console.log(urls);
		if let fileURL = urls.first {
			// 1. 获取安全访问权限
			let accessing = fileURL.startAccessingSecurityScopedResource()
			
			do {
				// 2. 将文件复制到应用的临时目录（获取可用的文件路径）
				let tempDirectoryURL = FileManager.default.temporaryDirectory
				let destinationURL = tempDirectoryURL.appendingPathComponent(fileURL.lastPathComponent)
				
				// 删除已存在的文件（如果有）
				if FileManager.default.fileExists(atPath: destinationURL.path) {
					try FileManager.default.removeItem(at: destinationURL)
				}
				
				// 复制文件到临时目录
				try FileManager.default.copyItem(at: fileURL, to: destinationURL)
				
				// 3. 获取文件属性
				let fileAttributes = try destinationURL.resourceValues(forKeys: [.fileSizeKey])
				let fileSize = fileAttributes.fileSize ?? 0
						
				// 在主线程中执行回调
				DispatchQueue.main.async {
					console.log(destinationURL.relativePath);
					self.callback(destinationURL.path, fileSize) // 返回文件路径 path relativePath absoluteString
				}
			} catch {
				console.log("Error getting file size: \(error.localizedDescription)")
				// DispatchQueue.main.async {
				// 	self.callback(nil, 0)
				// }
			}
			// 4. 释放访问权限
			if accessing {
				fileURL.stopAccessingSecurityScopedResource()
			}
		} else {
		    DispatchQueue.main.async {
		        self.callback(nil, 0)
		    }
		}    
    }
    
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        // 用户取消了文件选择
        // self.callback(nil, 0)
    }
}