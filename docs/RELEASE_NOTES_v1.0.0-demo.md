# v1.0.0-demo Release 文案草稿

> 该 Release 必须等 PR7 合并到 `main` 后再创建，确保 tag 指向最终 `main`。

## Release 标题

```text
v1.0.0-demo
```

## Release 内容

本版本是 Android DLNA 手机投屏 Demo 的阶段性交付版本。

已完成：

- Android 真机屏幕采集：MediaProjection + VirtualDisplay。
- H.264 视频编码：优先选择 `1080P` 编码画布，目标配置 `8Mbps`、`30fps`、关键帧间隔 `1 秒`。
- 本地 HTTP 流：`http://<phone-ip>:8080/live.ts`，MPEG-TS + H.264 video-only。
- DLNA / UPnP Renderer 发现：可发现 Kodi。
- DLNA AVTransport 控制：`SetAVTransportURI`、`Play`、`Pause`、`Stop`。
- Kodi 无电视演示：PR6 证据显示 Kodi 曾通过 DLNA AVTransport 显示手机画面。
- PR7 流内容复测：ADB forward + `curl` + `ffprobe` 验证 `/live.ts` 为 MPEG-TS / H.264 / `1080 x 1920`。

证据边界：

- PR6 证据：Kodi 曾通过 DLNA AVTransport 显示手机画面，但存在周期性缓冲 / 卡顿。
- PR7 证据：ADB forward 下 `curl` 收到 `HTTP 200` 和 `Content-Type: video/mp2t`，`ffprobe` 识别 `mpegts` + `h264` + `1080 x 1920`，未发现 audio stream。
- PR7 失败项：Windows 直连 `192.168.137.44:8080` 超时。
- ADB forward 只证明 App 本机 HTTP 服务和 `live.ts` 内容可读，不等同于 Windows 局域网直连成功，也不等同于真实电视端可访问。

当前限制：

- Kodi 播放存在周期性缓冲 / 卡顿。
- AAC `128Kbps` 未实现，当前流为 video-only。
- 严格 `<2 秒` 延迟测试未完成，缺少外部录像和三次读数，不得写达成。
- 真实电视兼容矩阵未覆盖。
- Windows 直连手机热点 IP 的 `8080` 端口本次超时，需要后续继续排查网络路径 / 防火墙 / 热点隔离。
- 8Mbps 为编码器目标配置；PR7 静态样本按 10 秒估算约 `0.29 Mbps`，按 `ffprobe` `bit_rate` 约 `0.030 Mbps`，不能写稳定达到 `8Mbps`。
- `CurrentURIMetaData=""` 是最小实现，部分电视可能要求 DIDL-Lite metadata 或 DLNA contentFeatures。

未实现项：

- AAC `128Kbps` 音频。
- HLS。
- DIDL-Lite metadata / DLNA contentFeatures 兼容增强。
- 多电视兼容矩阵。
- 严格延迟测试自动化记录。

## APK 信息

```text
APK 文件名：DLNAScreenCastDemo-v1.0.0-demo.apk
构建类型：Debug Demo APK
构建命令：.\gradlew.bat assembleDebug --console=plain
对应 commit：PR7 合并后的 main commit
SHA256：PR7 合并后重新构建并计算
已知问题：Kodi 周期性缓冲、Windows 直连本次超时、AAC 未实现、严格延迟未实测、真实电视兼容未覆盖
未实现项：AAC 128Kbps、HLS、DIDL-Lite metadata / DLNA contentFeatures、多电视兼容矩阵
```

Windows SHA256 命令：

```powershell
Get-FileHash .\DLNAScreenCastDemo-v1.0.0-demo.apk -Algorithm SHA256
```

## 发布步骤

PR7 合并后执行：

```powershell
git checkout main
git pull --ff-only origin main
.\gradlew.bat assembleDebug --console=plain
Copy-Item app\build\outputs\apk\debug\app-debug.apk C:\tmp\DLNAScreenCastDemo-v1.0.0-demo.apk
Get-FileHash C:\tmp\DLNAScreenCastDemo-v1.0.0-demo.apk -Algorithm SHA256
git tag v1.0.0-demo
git push origin v1.0.0-demo
```

然后在 GitHub Releases 创建 `v1.0.0-demo`，上传 `DLNAScreenCastDemo-v1.0.0-demo.apk` 作为 Asset，并把 APK SHA256、构建命令、对应 commit、已知问题和未实现项写入 Release 页面。
