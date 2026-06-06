# DLNAScreenCastDemo 最终测试报告

> PR7 目标：尽可能验证技术测试指标，保留真实证据，不把目标值写成已达成结果。
> PR10 继续补强最终指标证据。PR13 禁用运行时默认 1kHz 测试音源；PR14 接入 AudioPlaybackCapture 真实系统播放音代码路径，并归档一份 `ffprobe` 可识别 AAC、解码 WAV 非静音的样本；但接收端听感、目标 App capture policy 和手机端自动静音仍必须按后续真机证据分层记录。无法完成的项目必须写明阻塞原因，不把配置目标写成实测达成。

## 1. 测试环境

| 项目 | 内容 |
|---|---|
| Android 发送端 | 小米 14，型号 `23127PN0CC` |
| Android 版本 | `16`，API `36` |
| 接收端 | Windows 电脑 + Kodi，作为 DLNA / UPnP Renderer |
| FFmpeg 工具 | 已找到：`C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin`；未加入当前 PATH，PR7 使用绝对路径 |
| 本地流验证 | PR7 使用 ADB forward + `curl` + `ffprobe` 成功读取 H.264；PR10 复测时 `/live.ts` 返回 `Empty reply from server`；PR14 已归档一份 H.264 + AAC 样本 |
| 延迟辅助设备 | vivo X80 可作为外部摄像机；PR10 未完成严格延迟录像测量 |
| 网络 | Windows 电脑热点 / 同一 Wi-Fi |

## 2. 证据矩阵（PR10 最终版）

| 指标 | 目标 | 配置层 | ffprobe 识别 | 实测达成 | 证据文件 |
|---|---|---|---|---|---|
| 视频分辨率 | 1080P | UI 显示 `1080 x 1920`、`c2.qti.avc.encoder` | PR10 未取得样本；PR7 样本曾识别 `1080 x 1920` | PR10 未复测通过 | `docs/evidence/pr10-http-stream-evidence.md` |
| 视频码率 | 8 Mbps | UI 显示 `8.0 Mbps`、`CBR` | PR10 未取得 30 秒样本 | 未实测 | `docs/evidence/pr10-http-stream-evidence.md` |
| 音频规格 | AAC 128Kbps | PR14 接入 AudioPlaybackCapture + AudioRecord + AAC-LC 128Kbps + MPEG-TS audio PID；PR9 测试音仅为历史封装验证 | PR14 样本识别 `aac`、`48000 Hz`、单声道、约 `130 Kbps`；解码 WAV 非静音 | 样本层通过；接收端听感待验证 | `docs/evidence/pr14-playback-audio-capture-evidence.md` |
| 投屏延迟 | < 2s | N/A | N/A | 未按外部录像三次读数实测 | `docs/evidence/pr10-http-stream-evidence.md` |

说明：

- “配置层”只代表 App / encoder / muxer 的目标参数或 UI 展示，不等于实际输出。
- “ffprobe 识别”必须来自可读取的 `.ts` 样本。
- “实测达成”必须来自真机端到端验收，不能由配置层或历史样本替代。

## 3. PR10 HTTP 流复测结论

PR10 复测记录：

```text
App 状态：采集中
本地流地址：http://192.168.137.138:8080/live.ts
编码器：c2.qti.avc.encoder
实际编码画布：1080 x 1920
视频码率配置：8.0 Mbps / CBR
音频：PR10 当时为 AAC 128Kbps App 内 1kHz 测试音轨；PR13 起默认禁用运行时测试音
手机端监听：*:8080 LISTEN
```

PC 局域网直连：

```text
Connected to 192.168.137.138:8080
GET /live.ts HTTP/1.1
Empty reply from server
curl: (52) Empty reply from server
```

手机本机访问：

```text
Connected to 127.0.0.1:8080
GET /live.ts HTTP/1.1
Empty reply from server
curl: (52) Empty reply from server
```

ADB forward 路径：

```text
adb forward tcp:18080 tcp:8080 -> 18080
adb forward --list -> 空
curl http://127.0.0.1:18080/live.ts -> Connection refused
```

结论：本轮不能写成 `live.ts` 可抓取、`ffprobe` 可识别 H.264 + AAC、动态码率可估算或延迟可测。PR12 已修复 HTTP 握手路径；后续复测必须保存新样本证据。

## 4. 历史证据矩阵（PR7）

| 验收项 | 状态 | 证据 / 边界 |
|---|---|---|
| App 构建与单测 | PASS | `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain` 成功，`BUILD SUCCESSFUL`，`51 actionable tasks: 5 executed, 46 up-to-date` |
| App 本机 HTTP 服务 | PASS | 手机 `ss -ltnp` 显示 `*:8080` 监听；ADB forward 后 `curl` 收到 `HTTP/1.1 200 OK` |
| TS / H.264 内容 | PASS | PR7 `ffprobe` 识别 `format_name=mpegts`、`codec_name=h264` |
| 小米 14 竖屏样本 1080P 画布 | PASS | PR7 样本识别为 `1080 x 1920`；仅代表当前小米 14 竖屏样本达到 1080P 目标画布 |
| Windows 局域网直连手机 `8080` | FAIL | `curl http://192.168.137.44:8080/live.ts` 和 `Test-NetConnection 192.168.137.44 -Port 8080` 超时 |
| Kodi DLNA 播放链路 | 部分通过 | PR6 证据显示 Kodi 曾通过 DLNA AVTransport 显示手机画面，但存在周期性缓冲 / 卡顿 |
| 8Mbps 稳定码率 | 部分通过 | 编码器配置 `8Mbps`；PR7 静态样本估算远低于 `8Mbps`，需要动态画面和更长时长复测 |
| AAC 128Kbps | 样本层通过 | PR14 已接入真实系统播放音代码路径；归档样本可识别 AAC 且解码 WAV 非静音；接收端听感仍待验证 |
| 严格 `<2 秒` 延迟 | 未实测 | 缺少外部录像和三次时间差读数，不能写达标 |
| 真实电视兼容 | 未实测 | Kodi 结果不能外推到真实电视；未覆盖电视兼容矩阵 |

## 5. 最终技术指标表

| 指标 | 目标值 | 当前验证方式 | 当前结果 | 状态 |
|---|---|---|---|---|
| DLNA 投屏 | 支持 DLNA Renderer | Kodi + AVTransport `SetAVTransportURI` / `Play` | PR6 Kodi 可显示手机画面，但存在周期性缓冲 / 卡顿 | 部分通过 |
| 投屏延迟 | `< 2 秒` | 秒表 / 外部摄像机对比 | 未完成严格延迟测试，缺少外部录像 / 三次读数 | 未实测 |
| 分辨率 | `1080P` | App 参数 + `ffprobe` | PR7 样本识别为 `1080 x 1920`；当前小米 14 竖屏样本达到 1080P 目标画布 | PASS |
| 视频码率 | `8Mbps` | MediaCodec 配置 + 样本估算 | 目标配置 `8Mbps`；PR10 未取得 30 秒动态样本，无法估算当前实际码率 | 未实测 |
| 音频规格 | `AAC 128Kbps` | `ffprobe` 音频流检查 + 真机听感验证 | PR14 归档样本识别 `aac`、`48000 Hz`、单声道、约 `130 Kbps`；解码 WAV 非静音；接收端听感未归档 | 样本层通过 / 接收端待验证 |
| 平台 | Android Demo | 小米 14 真机 APK | 可安装运行；Release APK 待 PR7 合并后发布 | PASS |

## 6. DLNA / Kodi 证据分层

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

## 7. 分辨率验证

目标：`1080P`。

PR7 样本识别为：

```text
codec_name=h264
width=1080
height=1920
format_name=mpegts
```

结论：PR7 当前小米 14 竖屏样本达到 1080P 目标画布。PR10 UI 显示当前配置仍为 `1080 x 1920`，但未取得新样本，因此不新增 ffprobe 通过结论。

## 8. 视频码率验证

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

说明：两个结果都是 PR7 静态画面短样本估算，不代表稳定吞吐。PR10 因 `/live.ts` 空响应未取得 30 秒动态样本，不能写“视频码率已达 `8Mbps`”。

## 9. 音频规格验证

目标：`AAC 128Kbps`。

PR9 已接入：

```text
AudioEncoderConfig：AAC-LC / 128000 bps / 48000 Hz / mono
音频来源：App 内 1kHz 正弦波测试音
未实现：系统内录、麦克风采集
```

PR13 当前状态：

```text
运行时默认测试音：已禁用
默认 MPEG-TS：video-only，不声明 audio PID
保留能力：AAC 编码参数、ADTS 封装、MPEG-TS 音频封装单元测试
真实系统播放音频：PR14 代码路径已接入，样本层已识别 AAC 且解码非静音；接收端听感待验证
```

PR14 当前边界：

```text
代码路径：MediaProjection + AudioPlaybackCaptureConfiguration + AudioRecord -> AAC-LC 128Kbps -> MPEG-TS audio PID
权限：只新增 RECORD_AUDIO；拒绝时继续录屏并降级 video-only
捕获限制：Android 10+、同一用户资料、usage 为 MEDIA/GAME/UNKNOWN、目标 App capture policy 允许
声音路由：期望接收端出声、手机端不双重外放；手机端自动静音策略未实测
```

PR14 样本层结果：

```text
归档 TS：docs/evidence/artifacts/pr14-audio-live-aac.ts
ffprobe：mpegts + h264 + aac，音频 48000 Hz / mono / bit_rate≈130633
解码 WAV：docs/evidence/artifacts/pr14-audio-live-decoded.wav
astats：Peak level dB=-18.183257，RMS level dB=-21.376643，非全零静音
```

结论：PR14 合并前后都必须分层写证据。`ffprobe audio=aac` 只能写“AAC 音轨存在”；logcat 有 PCM peak/RMS + first AAC frame 才能写“已捕获到有效播放音并完成编码”；PC/Kodi/ffplay 能听到目标 App 声音才可以写“真实播放音频链路跑通”。不能提前写“抖音声音已实现”“真实系统音频已达成”或“手机端已自动静音”。

## 10. 投屏延迟验证

目标：`<2 秒`。

严格测试要求：

1. 小米 14 打开秒表或时间显示页面。
2. App 开始采集并发送到 Kodi 或 `ffplay`。
3. vivo X80 同时拍摄小米 14 原始屏幕和电脑播放画面。
4. 通过视频暂停观察两边时间差。
5. 至少记录 3 次并计算平均值。

PR10 记录：

```text
第 1 次：未测
第 2 次：未测
第 3 次：未测
平均：未测
```

结论：严格 `<2 秒` 测试未完成，缺少可读取 `/live.ts` 和外部录像三次读数；不能只凭肉眼判断，不能写达标。

## 11. Release 准备

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

## 12. 最终结论

本 Demo 已完成一个可演示的 Android DLNA 投屏原型。PR6 证据显示 Kodi 曾通过 DLNA AVTransport 显示手机画面，但有周期性缓冲 / 卡顿。PR7 证据显示 App 本机 HTTP 服务可通过 ADB forward 读取，`live.ts` 内容为 MPEG-TS + H.264，当前小米 14 竖屏样本为 `1080 x 1920`。

当前不能写成“全部指标达成”：PR12 已修复 PR10 记录的 HTTP 握手空响应问题，PR14 已补充一份 H.264 + AAC 且非静音的样本层证据；但 PC/Kodi/ffplay 接收端听到目标媒体声音仍未归档，严格 `<2 秒` 延迟读数、真实电视兼容、8Mbps 稳定吞吐和手机端自动静音仍未证明。

## 13. 本地门禁状态

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

PR10 当前完成：

```text
.\gradlew.bat assembleDebug --console=plain：PASS，BUILD SUCCESSFUL
adb install -r app\build\outputs\apk\debug\app-debug.apk：PASS
UI 文案：已显示 PR10 和 AAC 测试音轨边界
手机端 8080 监听：PASS
```

PR10 当前未完成 / 阻塞：

```text
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain：未执行完成，Codex 提权请求因当前使用额度限制被系统拒绝
ADB forward：当前环境中 forward 未稳定保留，127.0.0.1:18080 连接拒绝
PC 局域网直连 live.ts：TCP 连接成功，但 Empty reply from server
手机本机 curl live.ts：Empty reply from server
ffprobe H.264 + AAC：PR14 归档样本已验证
30 秒动态码率：未取得样本，无法估算
严格 <2 秒延迟测试：未实测
```

PR14 当前完成：

```text
UI 文案：显示 AudioPlaybackCapture 阶段状态和 capture policy 限制
代码路径：真实播放音 PCM 可送入 AAC 编码和 MPEG-TS audio PID
证据边界：样本层可识别 AAC 且解码 WAV 非静音；logcat peak/RMS、接收端听感、手机端自动静音仍待补充
```

## 14. 截图证据

PR7 新增截图：

- `docs/screenshots/pr7-kodi-renderer-selected-before.png`：Kodi Renderer 发现、IP、描述地址和 AVTransport controlURL。
- `docs/screenshots/pr7-kodi-renderer-selected.png`：Kodi Renderer 已选择，DLNA 播放控制按钮可用。

不提交 `.ts` 样本、抓包、外部摄像机原始视频或大体积视频。

## 15. 后续独立工作

PR7 合并后如继续优化，不在 PR7 分支叠加，需从最新 `main` 新建独立分支：

- `fix/low-latency-buffering`
- `feat/aac-audio-encoding`
- `test/latency-measurement`
- `fix/stream-session-empty-reply`
- `fix/audio-encoder-cleanup`
