# App 端文件选择接入说明（离线打包 / iOS / Android）

## 现状

| 能力 | H5 | App |
|---|---|---|
| 图片选择（相册/相机） | ✅ | ✅ `uni.chooseImage` |
| 文档选择（PDF/Word/PPT/音频） | ✅ `uni.chooseFile` | ⚠️ 需原生插件 |

uni-app 没有提供 App 端的文档选择 API（`chooseFile` 仅 H5，`chooseMessageFile` 仅微信小程序），
所以文档类工具在 App 端必须走原生插件。

代码里已预留接入点：`src/utils/filePicker.js` → `tryNativeFilePicker()`。
插件装好并把名字加进 `candidates` 数组即可生效，无需改动业务代码。

## 一、Android（离线打包）

1. **导入插件**
   - HBuilderX 插件市场搜索「文件选择」，选择支持 App 端 + 离线打包的插件
   - 导入后会生成 `uni_modules/xxx-file-picker/`
2. **配置**
   - `manifest.json` → App原生插件配置 → 勾选该插件
   - `pages.json` 无需改动（插件按需调用）
3. **集成到离线工程**
   - HBuilderX → 发行 → 原生App-本地打包 → 生成本地打包资源
   - 打开官方 Android 离线 SDK 工程（Android Studio）
   - 把 `uni_modules/xxx-file-picker/android/*.aar` 放进 `libs/`，`build.gradle` 里 `implementation fileTree(dir: 'libs', include: ['*.aar'])`
   - `dcloud_uniplugins.json` 里补插件声明（插件文档会给 JSON 片段）
   - 放入新生成的 `assets/apps/<appid>/www` 资源，重新编译 APK
4. **验证**
   - 安装 APK → 文档提取 → 点击上传 → 应弹出系统文件选择器

## 二、iOS（必须 Xcode 打包）

1. 插件需提供 iOS framework（`.framework` 或 `.a` + 头文件）
2. HBuilderX → 生成本地打包资源（iOS 版）
3. Xcode 打开官方 iOS 离线 SDK 工程：
   - 把插件 framework 拖进工程，General → Frameworks 勾选 Embed
   - `Info.plist` 增加所需权限（如 `NSPhotoLibraryUsageDescription`）
   - 在 `dcloud_uniplugins.json` 补插件声明
4. 配置签名证书 + Provisioning Profile（真机测试需要开发者账号）
5. 编译安装到设备

## 三、不装插件的临时方案（仅图片）

图片类工具已经全平台可用：

```js
uni.chooseImage({ count: 1, success: (res) => upload(res.tempFilePaths[0]) })
```

覆盖场景：图片去背景、证件照换底色、图片格式转换。

## 四、必须接受的事实

- **iOS 绕不开原生插件**：`UIDocumentPickerViewController` 无法用 Native.js 调起后取回结果
- **Android 的 Native.js 取不到结果**：`startActivityForResult` 的回调在 uni-app 运行时不暴露，
  社区所谓"Native.js 方案"依赖反射 hook，兼容性差，不建议用于答辩演示

## 五、验收清单

- [ ] Android 离线包：选 PDF → 能上传 → 能转换
- [ ] iOS 真机：选 PDF → 能上传 → 能转换
- [ ] 未装插件时：图片类可用，文档类给出明确提示（不白屏、不静默失败）
