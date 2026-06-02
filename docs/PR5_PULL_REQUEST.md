# feat(stream): add local MPEG-TS HTTP streaming

## 本次改动

- 将 `MediaCodec` H.264 输出从仅排空扩展为可注入的 `EncodedVideoOutputSink`，读取实际 codec config、关键帧和普通访问单元。
- 使用项目内自研的小型 Annex-B 规范化与 MPEG-TS 封装链路，将 H.264 视频封装为连续 TS 数据。
- 使用原生 `ServerSocket` 提供 `http://<phone-wifi-ip>:8080/live.ts`，支持多个客户端、异步写入和幂等停止。
- 为新连接缓存最近 GOP；横竖屏重建 encoder 时清空旧 GOP，避免客户端收到跨会话数据。
- 只展示 `wlan*` 网卡的局域网 IPv4，避免 Wi-Fi 未连接时错误展示蜂窝网络地址。
- 将本地流服务纳入屏幕采集生命周期：启动采集时启动服务，停止采集时释放 encoder、HTTP 服务和 `MediaProjection` 资源。
- 首页展示当前流地址、MPEG-TS + H.264 格式，以及 AAC 音频和延迟尚未完成的边界。

## 使用的 Skills

- `android-architecture`：保持 `encoder`、`stream`、`capture` 和 UI 边界清晰。
- `android-viewmodel`：沿用只读 `StateFlow` 暴露采集会话和流地址。
- `kotlin-concurrency-expert`：处理 HTTP accept、握手、写队列、GOP 缓存和幂等释放。
- `android-testing`：覆盖编码输出读取、TS 封装、流服务、URL 选择和释放顺序。
- `android-emulator-skill`：执行 ADB 安装、启动、旋转、日志和停止验证。
- `test-android-apps:android-emulator-qa`：使用真机进行 UI 冒烟验证。
- `compose-ui`：增加本地流状态卡片。
- `superpowers:test-driven-development`：按 RED / GREEN 顺序补充服务重启、Wi-Fi URL 和 GOP 回放边界。
- `lint-and-validate`：运行构建、单测和 Android lint。

## 影响范围

- 新增 `stream` 本地 MPEG-TS HTTP 服务。
- `encoder` 模块开始向 `stream` 模块输出 H.264 数据。
- `capture` 模块负责本地流服务的启动、停止和横竖屏重建协调。
- 首页增加当前流 URL 和格式说明。
- README 增加 PC 抓流、FFprobe、实时解码和真机验收记录。

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

## 真机与 PC 手动测试

- 设备：`23127PN0CC`
- Android 版本：`16`，API `36`
- 电脑网络：Windows 移动热点，手机 Wi-Fi 地址 `192.168.137.155`
- 本地流地址：`http://192.168.137.155:8080/live.ts`
- 实际 codec：`c2.qti.avc.encoder`
- 竖屏：源画面 `1200 x 2670`，实际编码画布 `1080 x 1920`
- 横屏：源画面 `2670 x 1200`，实际编码画布 `1920 x 1080`
- 已配置参数：`8 Mbps`、`30 fps`、关键帧间隔 `1 秒`、`CBR`
- PC 抓流：`curl --max-time 10` 收到 `159988 bytes`，共 `851` 个 TS packet，`BAD_SYNC_PACKETS=0`
- PC 样本探测：`ffprobe` 识别 `mpegts`、`h264` 和竖屏 `1080 x 1920`
- PC 样本解码：FFmpeg 从真实 TS 样本成功解码截图 `docs/screenshots/pr5-pc-decoded-frame.png`
- PC 实时解码：FFmpeg 对 `/live.ts` 进行 `5` 秒实时解码，竖屏和横屏均返回 `exit=0`
- 横竖屏重建：通过；URL 保持不变，encoder 根据画布变化重建
- App 内停止：通过；日志记录 encoder、本地流服务和屏幕采集资源释放
- 停止后端口：PC 再次请求 `/live.ts` 返回连接拒绝

PC 验收命令：

```powershell
curl.exe -v http://192.168.137.155:8080/live.ts --output C:\tmp\pr5-sample.ts --max-time 10
ffprobe C:\tmp\pr5-sample.ts
ffmpeg -hide_banner -loglevel warning -t 5 -i http://192.168.137.155:8080/live.ts -f null NUL
```

## 本 PR 不实现

- AAC 音频
- DLNA AVTransport 播放控制
- 延迟 `< 2 秒` 可复现实测
- 乐播云商业 SDK 接入

`8 Mbps` 是 encoder 配置值，不代表实际持续吞吐量。当前只验证 H.264 MPEG-TS 本地视频流可访问、可识别并可由 PC 实时解码。

## 开源参考

- 参考资料：MPEG-TS、PES、PAT / PMT 基础结构和 FFmpeg 验证方式。
- 参考内容：TS packet、PAT / PMT、H.264 PES 和 PC 播放验证流程。
- 本项目自己的实现差异：仅使用 Android 原生 API，自行实现小型视频-only MPEG-TS 封装和 HTTP 服务，不引入第三方流媒体库。
- 乐播云：仅作为商业兼容方案参考，没有接入 SDK。
- 是否复制代码：否。

## 已知问题

- MPEG-TS 当前只包含 H.264 视频，不包含 AAC 音频。
- 尚未接入 DLNA AVTransport，不能从 App 主动要求 Renderer 播放本地流。
- 延迟 `< 2 秒` 尚未按可复现方法测量。
- 当前小米 ROM 在采集期间执行 `adb screencap` 会触发系统停止采集，因此 README 使用 PC 从真实 TS 样本解码出的画面作为 PR 5 证据。
