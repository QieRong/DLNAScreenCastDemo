# feat(capture): add MediaProjection screen capture skeleton

## 本次改动

- 每次开始采集都重新请求系统录屏授权，不持久化或复用旧授权数据。
- 新增 `mediaProjection` 类型前台服务，在进入前台后再获取 `MediaProjection`。
- 创建单次使用的 `VirtualDisplay`，使用 `ImageReader(maxImages = 2)` 临时消费并立即关闭画面帧。
- 支持尺寸变化、系统停止回调、App 内停止和通知停止 action。
- 首页展示当前采集状态和实际采集尺寸。

## 使用的 Skills

- `android-architecture`：保持 `capture`、服务、ViewModel 和 UI 边界清晰。
- `android-viewmodel`：使用只读 `StateFlow` 暴露采集状态。
- `kotlin-concurrency-expert`：集中处理 callback、worker thread 和幂等释放。
- `android-testing`：覆盖状态门禁、通知权限续流、单次显示器创建和释放顺序。
- `android-emulator-skill`：准备 ADB 安装、启动、日志和截图路径。
- `test-android-apps:android-emulator-qa`：按 UI tree 和 logcat 进行真机冒烟验证。
- `compose-ui`：使用状态提升接入首页控制按钮和状态展示。

## 影响范围

- Android Manifest 前台服务与通知权限。
- `capture` 屏幕采集分层和 `feature/casting` ViewModel。
- 首页采集控制、状态和当前尺寸展示。
- README 屏幕采集边界与真机测试步骤。

## 测试结果

提交前运行：

```powershell
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

结果：

- `assembleDebug`：`PASS`，已生成 Debug APK。
- `testDebugUnitTest`：`PASS`，共通过 34 个测试。
- `lintDebug`：`PASS`。
- `connectedDebugAndroidTest`：`PASS`，在 `23127PN0CC - 16` 上实际执行 `3 tests`。

补充说明：第一次执行 `connectedDebugAndroidTest` 时，小米系统拦截 instrumentation APK 安装，返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，实际执行 `0 tests`。开启“USB 调试（安全设置）”和“通过 USB 安装”后重新执行，最终通过。本文只将重新执行后的真实结果记录为 `PASS`。

## 手动测试

- 设备：`23127PN0CC`
- Android 版本：`16`，API `36`
- 测试步骤：安装 Debug APK；点击“开始采集”；同意系统录屏授权；确认采集中状态；旋转横竖屏；使用 App 按钮停止；再次开始；锁屏触发系统停止回调；解锁后确认恢复“未采集”。
- 系统授权：Android 14+ 保留系统默认用户选择模式，当前小米 ROM 弹窗提供“共享整个屏幕”和“共享一个应用”选项。App 每次开始都会重新调用系统授权 Intent；同一进程内使用 App 按钮停止后再次开始时，ROM 可能直接返回新的授权结果，不保证每次肉眼可见弹窗。
- 单应用共享：选择“共享一个应用”后系统进入应用选择器。
- 整屏共享：选择“共享整个屏幕”后页面进入 `采集中：1200 x 2670 px`。
- 页面结果：竖屏显示 `采集中：1200 x 2670 px`，横屏更新为 `2670 x 1200`。
- 日志结果：确认采集启动、横竖屏各一次尺寸更新、App 停止释放、锁屏后的“系统停止屏幕采集”和资源释放。
- 通知权限：当前 ROM 出现通知权限弹窗时采集仍可继续，符合“不阻断系统录屏授权流程”的策略。
- 前台通知：确认通知栏显示“正在采集屏幕画面”。小米通知栏未在自动化 UI tree 中展开自定义停止 action，因此未将通知 action 点击写成已手动通过。
- 截图：`docs/screenshots/pr3-screen-capture-source-options.png`、`docs/screenshots/pr3-screen-capture-app-choice.png` 和 `docs/screenshots/pr3-screen-capture-active.png`，设备 `23127PN0CC`、Android `16` / API `36`；仅包含 App 页面、系统授权弹窗和系统录屏状态提示，已检查无聊天、账号或通知隐私。
- 结果：PR 3 屏幕采集骨架在当前真机完成授权、采集、旋转、App 停止、锁屏停止和资源释放验收。

## 本 PR 不实现

- H.264 编码
- AAC 音频
- 本地 HTTP 流
- `ffplay` 播放
- DLNA AVTransport 播放控制
- 延迟 `< 2 秒` 实测

## 开源参考

- 参考资料：Android 官方 MediaProjection、Foreground service types、VirtualDisplay 文档。
- 参考内容：单次授权 token、前台服务顺序、回调、尺寸变化和资源释放。
- 本项目自己的实现差异：PR 3 仅使用原生 `ImageReader` 临时丢弃帧，不引入商业 SDK 或第三方采集库。
- 是否复制代码：否。

## 已知问题

- Android 14+ 的整屏 / 单应用选择弹窗样式和单应用选择流程可能受厂商 ROM 影响。
- 小米 ROM 未在自动化 UI tree 中展开前台通知的自定义停止 action，该点击路径未记录为已手动通过。
- PR 3 尚无编码器和本地流，不能使用 `ffplay` 验证画面。
