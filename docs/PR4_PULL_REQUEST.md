# feat(encoder): add H.264 encoder configuration and runtime drain

## 本次改动

- 使用原生 `MediaCodec` H.264 encoder Surface 接替 PR 3 的临时丢弃帧 Surface。
- 按设备 encoder capabilities 选择标准编码画布，检查尺寸帧率支持、宽高对齐和码率范围。
- 优先配置 `CBR`；不支持时沿用默认码率模式，并在 UI 显示“默认 / 非 CBR”。
- 持续排空并丢弃编码输出，分别记录 codec config buffer、first media frame 和 first key frame，不输出完整二进制内容。
- 横竖屏变化时进行 `250ms` 防抖，只处理最后一次目标尺寸；编码画布变化时进入重配置状态并替换 encoder Surface。
- 停止路径优先尝试 EOS 和短时间 drain，再依次释放 codec、输入 Surface、worker thread、`VirtualDisplay` 和 `MediaProjection`。
- 首页展示 codec 名称、实际编码画布、已配置码率、码率模式、帧率、关键帧间隔和是否降级。

## 使用的 Skills

- `android-architecture`：保持 `encoder`、`capture`、服务和 UI 边界清晰。
- `android-viewmodel`：使用只读 `StateFlow` 暴露采集和重配置状态。
- `kotlin-concurrency-expert`：集中处理 drain worker、resize 防抖和幂等释放。
- `android-testing`：覆盖参数校验、能力降级、输出分类、防抖和释放顺序。
- `android-emulator-skill`：执行 ADB 安装、启动、旋转、锁屏、日志和截图流程。
- `test-android-apps:android-emulator-qa`：使用 UI tree 推导按钮坐标并进行真机冒烟验证。
- `compose-ui`：通过状态提升增加编码参数卡片。
- `superpowers:test-driven-development`：按 RED / GREEN 顺序实现纯 Kotlin 策略和生命周期协调器。
- `lint-and-validate`：运行构建、单测和 Android lint。

## 影响范围

- 新增 `encoder` H.264 编码分层。
- `capture` 模块由临时 `ImageReader` Surface 切换为 encoder Surface。
- 首页采集状态和 H.264 实际配置展示。
- README PR 4 范围、测试方法、真机证据和截图。

## 测试结果

提交前运行：

```powershell
.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

结果：

- `assembleDebug`：`PASS`，已生成 Debug APK。
- `testDebugUnitTest`：`PASS`。
- `lintDebug`：`PASS`。
- `connectedDebugAndroidTest`：`PASS`，在 `23127PN0CC - 16` 上实际执行 `3 tests`。

## 手动测试

- 设备：`23127PN0CC`
- Android 版本：`16`，API `36`
- 实际 codec：`c2.qti.avc.encoder`
- 竖屏：源画面 `1200 x 2670`，实际编码画布 `1080 x 1920`
- 横屏：源画面 `2670 x 1200`，实际编码画布 `1920 x 1080`
- 已配置参数：`8 Mbps`、`30 fps`、关键帧间隔 `1 秒`、`CBR`
- 输出日志：记录 `csd-0=true`、`csd-1=true`、codec config buffer、first media frame、first key frame 和停止时 encoded frame count
- 旋转重建：通过；编码画布变化时自动重建，相同画布的重复 resize 被忽略
- App 内停止：通过；页面恢复“未采集”，日志记录 encoder 和屏幕采集资源释放
- 锁屏停止：通过；日志出现系统停止回调、encoder 释放统计和屏幕采集资源释放
- 截图：`docs/screenshots/pr4-h264-portrait.png` 和 `docs/screenshots/pr4-h264-landscape.png`，已检查无聊天、账号或通知隐私

第一次执行 `adb install -r` 时，小米系统拦截安装并返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`；再次执行后安装成功。本文只将重试成功后的真实结果记录为安装通过。

## 本 PR 不实现

- AAC 音频
- 本地 HTTP 流
- `ffplay` 播放
- DLNA AVTransport 播放控制
- 延迟 `< 2 秒` 实测
- 乐播云商业 SDK 接入

优先选择 `1080P` 编码画布，实际配置取决于设备 H.264 encoder capabilities；性能仍未实测。已配置 `8 Mbps` 不代表实测吞吐量。

## 开源参考

- 参考资料：Android 官方 `MediaCodec`、`MediaFormat`、`MediaCodecInfo.VideoCapabilities` 文档。
- 参考内容：Surface 输入编码、output buffer 排空、码率模式、尺寸帧率能力和对齐要求。
- 本项目自己的实现差异：仅使用 Android 原生 API，自行实现小型 H.264 配置选择、输出观察和生命周期协调，不引入商业 SDK 或第三方编码库。
- 乐播云：仅作为商业兼容方案参考，没有接入 SDK。
- 是否复制代码：否。

## 已知问题

- PR 4 只排空并丢弃 H.264 输出，不提供可播放流。
- H.264 编码配置不等于性能实测。
- 音频、推流、DLNA 播控和延迟测量留给后续 PR。
