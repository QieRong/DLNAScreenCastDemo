# DLNAScreenCastDemo 最终测试报告

> PR7 目标：尽可能验证技术测试指标，保留真实证据，不把目标值写成已达成结果。
> 当前结论基于 PR6 已有证据与 PR7 真机复测。无法完成的项目必须写明阻塞原因。

## 1. 测试环境

| 项目 | 内容 |
|---|---|
| Android 发送端 | 小米 14，型号 `23127PN0CC` |
| Android 版本 | `16`，API `36` |
| 接收端 | Windows 电脑 + Kodi，作为 DLNA / UPnP Renderer |
| FFmpeg 工具 | 已找到：`C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin`；未加入当前 PATH，PR7 使用绝对路径 |
| 本地流验证 | PR7 使用 ADB forward + `curl` + `ffprobe`；Windows 直连手机 IP 失败 |
| 延迟辅助设备 | vivo X80 可作为外部摄像机；PR7 未完成严格延迟录像测量 |
| 网络 | Windows 电脑热点 / 同一 Wi-Fi |

## 2. 证据矩阵

| 验收项 | 状态 | 证据 / 边界 |
|---|---|---|
| App 构建与单测 | PASS | `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain` 成功，`BUILD SUCCESSFUL`，`51 actionable tasks: 5 executed, 46 up-to-date` |
| App 本机 HTTP 服务 | PASS | 手机 `ss -ltnp` 显示 `*:8080` 监听；ADB forward 后 `curl` 收到 `HTTP/1.1 200 OK` |
| TS / H.264 内容 | PASS | PR7 `ffprobe` 识别 `format_name=mpegts`、`codec_name=h264` |
| 小米 14 竖屏样本 1080P 画布 | PASS | PR7 样本识别为 `1080 x 1920`；仅代表当前小米 14 竖屏样本达到 1080P 目标画布 |
| Windows 局域网直连手机 `8080` | FAIL | `curl http://192.168.137.44:8080/live.ts` 和 `Test-NetConnection 192.168.137.44 -Port 8080` 超时 |
| Kodi DLNA 播放链路 | 部分通过 | PR6 证据显示 Kodi 曾通过 DLNA AVTransport 显示手机画面，但存在周期性缓冲 / 卡顿 |
| 8Mbps 稳定码率 | 部分通过 | 编码器配置 `8Mbps`；PR7 静态样本估算远低于 `8Mbps`，需要动态画面和更长时长复测 |
| AAC 128Kbps | 未实现 | 当前 video-only；PR7 `ffprobe` 未发现 audio stream |
| 严格 `<2 秒` 延迟 | 未实测 | 缺少外部录像和三次时间差读数，不能写达标 |
| 真实电视兼容 | 未实测 | Kodi 结果不能外推到真实电视；未覆盖电视兼容矩阵 |

## 3. 最终技术指标表

| 指标 | 目标值 | 当前验证方式 | 当前结果 | 状态 |
|---|---|---|---|---|
| DLNA 投屏 | 支持 DLNA Renderer | Kodi + AVTransport `SetAVTransportURI` / `Play` | PR6 Kodi 可显示手机画面，但存在周期性缓冲 / 卡顿 | 部分通过 |
| 投屏延迟 | `< 2 秒` | 秒表 / 外部摄像机对比 | 未完成严格延迟测试，缺少外部录像 / 三次读数 | 未实测 |
| 分辨率 | `1080P` | App 参数 + `ffprobe` | PR7 样本识别为 `1080 x 1920`；当前小米 14 竖屏样本达到 1080P 目标画布 | PASS |
| 视频码率 | `8Mbps` | MediaCodec 配置 + 样本估算 | 目标配置 `8Mbps`；PR7 静态样本按 10 秒估算约 `0.29 Mbps`，按 `ffprobe` `bit_rate` 约 `0.030 Mbps` | 部分通过 |
| 音频规格 | `AAC 128Kbps` | `ffprobe` 音频流检查 | 当前 video-only，未发现 audio stream，AAC 未实现 | 未实现 |
| 平台 | Android Demo | 小米 14 真机 APK | 可安装运行；Release APK 待 PR7 合并后发布 | PASS |

## 4. DLNA / Kodi 证据分层

### PR6 证据

PR6 已完成最小 DLNA AVTransport 演示链路：

```text
Renderer：Kodi (SK-20220818ZFPP)，IP 192.168.137.1
controlURL：http://192.168.137.1:1932/AVTransport/b3eaf005-844b-07e1-086d-e914aaff4b63/control.xml
streamUrl：http://192.168.137.183:8080/live.ts
SetAVTransportURI：成功
Play：成功
Kodi 画面：可显示手机画面
播放质量：存在周期性缓冲 / 卡顿
```

PR6 结论只能写：“Kodi 曾通过 DLNA AVTransport 显示手机画面，但存在周期性缓冲 / 卡顿。”不能写成真实电视兼容已通过。

### PR7 证据

PR7 复测记录了 DLNA 控制命令和本机流内容：

```text
Renderer：Kodi (SK-20220818ZFPP)，IP 192.168.137.1
SetAVTransportURI：HTTP 200，成功
Play：HTTP 200，成功
Pause：HTTP 200，成功
Stop：HTTP 200，成功
停止采集释放：成功，UI 回到未采集，本地流停止
```

PR7 新增流内容验证使用 ADB forward：

```powershell
adb forward tcp:18080 tcp:8080
curl.exe -v http://127.0.0.1:18080/live.ts --output app\build\tmp\DLNAScreenCastDemo-pr7-sample.ts --max-time 10
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe -v error -show_entries format=format_name,duration,size,bit_rate -show_entries stream=index,codec_type,codec_name,width,height,avg_frame_rate,bit_rate -of default=noprint_wrappers=1 app\build\tmp\DLNAScreenCastDemo-pr7-sample.ts
```

`curl` 摘要：

```text
HTTP/1.1 200 OK
Content-Type: video/mp2t
Operation timed out after 10003 milliseconds with 357388 bytes received
```

`ffprobe` 摘要：

```text
index=0
codec_name=h264
codec_type=video
width=1080
height=1920
avg_frame_rate=0/0
bit_rate=N/A
format_name=mpegts
duration=94.160533
size=357388
bit_rate=30364
```

音频检查：

```powershell
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe -v error -select_streams a -show_entries stream=index,codec_type,codec_name,bit_rate -of default=noprint_wrappers=1 app\build\tmp\DLNAScreenCastDemo-pr7-sample.ts
```

结果为空，表示未发现 audio stream。

### PR7 失败项

Windows 直连手机热点 IP 失败：

```text
curl http://192.168.137.44:8080/live.ts：连接超时
Test-NetConnection 192.168.137.44 -Port 8080：TCP connect failed，Ping TimedOut
手机本机 ss：*:8080 正在监听
```

ADB forward 证据只能证明 App 本机 HTTP 服务和 `live.ts` 内容可读，不能等同于 Windows 局域网直连成功，也不能等同于真实电视端可访问。

## 5. 分辨率验证

目标：`1080P`。

PR7 样本识别为：

```text
codec_name=h264
width=1080
height=1920
format_name=mpegts
```

结论：当前小米 14 竖屏样本达到 1080P 目标画布。该结论不扩大为所有设备、所有方向或所有场景稳定 `1080P`。

## 6. 视频码率验证

目标配置码率：`8Mbps`。

App / logcat 配置记录：

```text
Encoder: 启动 H.264 编码器：codec=c2.qti.avc.encoder，request=1080x1920，bitrate=8000000，fps=30，iFrame=1s，mode=CBR
Encoder: H.264 actual output format：mime=video/avc，width=1080，height=1920，bitrate=8000000，fps=30，bitrateMode=2
```

PR7 静态样本：

```text
sample size：357,388 bytes
curl wall time：10 seconds
ffprobe duration：94.160533
ffprobe size：357388
ffprobe bit_rate：30364 bps
```

按 `curl --max-time 10` 墙钟时间估算：

```text
357,388 * 8 / 10 = 285,910 bps ≈ 0.29 Mbps
```

按 `ffprobe` 结果记录：

```text
bit_rate=30364 bps ≈ 0.030 Mbps
```

说明：两个结果都是静态画面短样本估算，不代表稳定吞吐。PR7 只能写“编码器配置 `8Mbps`，PR7 静态样本实测吞吐远低于 `8Mbps`，需动态画面和更长时长复测”，不能写“视频码率已达 `8Mbps`”。

## 7. 音频规格验证

目标：`AAC 128Kbps`。

PR7 结果：

```text
当前流为 video-only。
ffprobe 未发现 audio stream。
AAC 128Kbps 未实现。
```

结论：音频规格未实现，不能写“音频规格已达成”。

## 8. 投屏延迟验证

目标：`<2 秒`。

严格测试要求：

1. 小米 14 打开秒表或时间显示页面。
2. App 开始采集并发送到 Kodi 或 `ffplay`。
3. vivo X80 同时拍摄小米 14 原始屏幕和电脑播放画面。
4. 通过视频暂停观察两边时间差。
5. 至少记录 3 次并计算平均值。

PR7 记录：

```text
第 1 次：未测
第 2 次：未测
第 3 次：未测
平均：未测
```

结论：严格 `<2 秒` 测试未完成，缺少外部录像和三次读数；不能只凭肉眼判断，不能写达标。

## 9. Release 准备

PR7 分支只准备 Release 信息；真正 tag 和 GitHub Release 必须等 PR7 合并到 `main` 后创建。APK 不提交到仓库。

Release 信息：

```text
APK 文件名：DLNAScreenCastDemo-v1.0.0-demo.apk
构建类型：Debug Demo APK
构建命令：.\gradlew.bat assembleDebug --console=plain
对应 commit：PR7 合并后的 main commit
SHA256：PR7 合并后重新构建并计算
```

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

然后在 GitHub Release 上传 `DLNAScreenCastDemo-v1.0.0-demo.apk`，并写入 APK SHA256、构建命令、对应 commit、已知问题和未实现项。

## 10. 最终结论

本 Demo 已完成一个可演示的 Android DLNA 投屏原型。PR6 证据显示 Kodi 曾通过 DLNA AVTransport 显示手机画面，但有周期性缓冲 / 卡顿。PR7 证据显示 App 本机 HTTP 服务可通过 ADB forward 读取，`live.ts` 内容为 MPEG-TS + H.264，当前小米 14 竖屏样本为 `1080 x 1920`。

当前不能写成“全部指标达成”：Windows 局域网直连 `192.168.137.44:8080` 本次超时，真实电视兼容未实测，AAC 128Kbps 未实现，严格 `<2 秒` 延迟未实测，8Mbps 稳定吞吐未证明。

## 11. 本地门禁状态

PR7 当前已完成：

```text
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain：PASS，BUILD SUCCESSFUL
git diff --check：待最终文档修改后复跑
大体积样本检查：待最终文档修改后复跑
ADB 真机链路：PASS，设备 a630f3ff 在线，App 可启动并完成 PR7 手动链路复测
```

PR7 当前未完成 / 阻塞：

```text
Windows 局域网直连 live.ts：FAIL，192.168.137.44:8080 超时
严格 <2 秒延迟测试：未实测，缺少外部录像和三次读数
APK SHA256：待 PR7 合并 main 后重新构建 Release APK 并计算
```

## 12. 截图证据

PR7 新增截图：

- `docs/screenshots/pr7-kodi-renderer-selected-before.png`：Kodi Renderer 发现、IP、描述地址和 AVTransport controlURL。
- `docs/screenshots/pr7-kodi-renderer-selected.png`：Kodi Renderer 已选择，DLNA 播放控制按钮可用。

不提交 `.ts` 样本、抓包、外部摄像机原始视频或大体积视频。

## 13. 后续独立工作

PR7 合并后如继续优化，不在 PR7 分支叠加，需从最新 `main` 新建独立分支：

- `fix/low-latency-buffering`
- `feat/aac-audio-encoding`
- `test/latency-measurement`
