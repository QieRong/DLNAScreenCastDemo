# 指标演示与动态测试页测试指南

> PR8 只新增指标演示辅助页面和测试文档，不修改 PR7 / `v1.0.0-demo` 的历史测试结论，不移动已发布 tag，不覆盖已发布 APK 说明。PR8 的目标是提供“可复现测试入口”，不是宣称指标达成。PR9 已接入 AAC 测试音轨；PR10 使用本指南补强 ffprobe、动态码率和延迟证据，但若 `/live.ts` 不能读取，所有相关指标都必须继续写未实测或未验证。

## 1. 页面入口

安装并打开 App 后，在首页找到“指标演示 / 验收辅助”卡片：

1. 点击“打开延迟测试页”，进入延迟测试页。
2. 点击“打开动态码率测试页”，进入动态码率测试页。

进入延迟测试页或动态码率测试页时，App 会保持屏幕常亮；退出页面后恢复系统默认亮屏策略。

## 2. Android 14+ 录屏授权选择

测试延迟页或动态码率页时，如果系统录屏弹窗提供“共享整个屏幕 / 共享一个应用”，请选择包含当前 App 测试页的共享方式。

如果只共享其他应用，当前测试页不会被采集，后续 `ffplay`、Kodi 或样本文件中的画面就不能代表 PR8 测试页。

## 3. 延迟测试页读数

延迟测试页会显示：

```text
毫秒时间戳：当前 wall clock 毫秒数
秒表格式：mm:ss.SSS
帧跳变编号：Frame N
快速变化色块
跳变数字 / 进度条
```

推荐测试方法：

1. 小米 14 打开 App，进入“延迟测试页”。
2. 开始采集并将 `/live.ts` 发送到 Kodi 或使用 `ffplay` 播放。
3. 使用 vivo X80 同时拍摄小米 14 原始屏幕和电脑播放画面。
4. 回放 vivo X80 视频，暂停在同一时刻，分别读取两边的毫秒时间戳或秒表格式。
5. 至少记录 3 次时间差并计算平均值。

计算方式：

```text
延迟秒数 = 接收端画面时间 - 发送端原始画面时间
平均延迟 = 3 次延迟秒数之和 / 3
```

如果用 60fps 视频估算，也可以记录帧差：

```text
延迟秒数 = 相差帧数 / 60
```

没有 3 次外部录像读数前，README 和 PR 描述只能写“未实测”，不能写“延迟已小于 2 秒”。

## 4. 动态码率测试页

动态码率测试页用于让 H.264 编码器输入更接近高运动画面，避免只采静态页面导致码率很低。页面包含：

```text
1. 横向高速滚动文字
2. 大面积移动棋盘格
3. 每 250ms 切换颜色块
4. 多个不同速度移动的小方块
5. 大号毫秒时间戳
6. 当前测试时长
```

测试时保持 App 停留在动态码率测试页，然后开始采集并抓取 30 秒样本。

## 5. 抓取 30 秒动态样本

连接手机并确认 App 已经开始采集后，在 Windows PowerShell 执行：

```powershell
adb forward tcp:18080 tcp:8080

adb forward --list

curl.exe -v http://127.0.0.1:18080/live.ts `
  --output app\build\tmp\DLNAScreenCastDemo-dynamic-30s.ts `
  --max-time 30
```

`curl` 对连续流达到 `--max-time` 后返回超时是预期行为，只要文件持续写入并收到 `HTTP 200` / `Content-Type: video/mp2t` 即可继续分析。

## 6. PowerShell 估算动态码率

动态码率估算优先使用 `curl` 实际抓取墙钟时间计算：

```text
码率 Mbps = 文件字节数 x 8 / 实际秒数 / 1,000,000
```

`ffprobe` 的 `duration` 对实时 TS 样本可能不稳定，只作为辅助信息。

PowerShell 示例：

```powershell
$path="app\build\tmp\DLNAScreenCastDemo-dynamic-30s.ts"
$seconds=30
$bytes=(Get-Item $path).Length
$mbps=$bytes*8/$seconds/1000000
"Estimated bitrate: {0:N2} Mbps" -f $mbps
```

也可以使用简短写法：

```powershell
$path="app\build\tmp\DLNAScreenCastDemo-dynamic-30s.ts"
$seconds=30
$size=(Get-Item $path).Length
[math]::Round(($size*8/$seconds)/1000000,2)
```

只有动态样本估算结果接近目标并保留命令证据时，才可以写“视频码率接近 8Mbps”。否则保持“动态样本待测”或记录实际 Mbps。

## 7. ffprobe 查看分辨率和流信息

```powershell
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe `
  -v error `
  -show_entries format=format_name,duration,size,bit_rate `
  -show_entries stream=index,codec_type,codec_name,width,height,avg_frame_rate,bit_rate `
  -of default=noprint_wrappers=1 `
  app\build\tmp\DLNAScreenCastDemo-dynamic-30s.ts
```

重点查看：

```text
format_name=mpegts
codec_name=h264
width=1080
height=1920
codec_type=audio 是否存在
```

PR9 已接入 App 内 1kHz AAC 测试音轨。若 `ffprobe` 未发现 audio stream，或像 PR10 当前复测一样未取得有效 `.ts` 样本，应继续记录“音频：测试音轨已接入，但 ffprobe 未验证 / 未识别”，不能写成音频规格已达成。

## 8. 测试记录模板

| 测试项 | 第 1 次 | 第 2 次 | 第 3 次 | 平均值 | 结论 |
|---|---:|---:|---:|---:|---|
| 延迟秒数 | 未测 | 未测 | 未测 | 未测 | 未实测 |
| 动态样本码率 Mbps | 未测 | 未测 | 未测 | 未测 | 待测 |

补充记录：

```text
测试时间：
发送端手机：
接收端软件：
网络环境：
流地址：
样本文件：
ffprobe 摘要：
已知问题：
```

## 9. PR8 边界

- 本 PR 不修改 MediaProjection、MediaCodec、MPEG-TS、StreamServer、AVTransport 核心链路。
- 本 PR 不实现 AAC 128Kbps。
- 本 PR 不接入乐播云或其他商业 SDK。
- 页面只用于辅助录像和动态样本测试，不代表指标自动达成。
- 若 PR8 合并后需要发布新版本，建议使用 `v1.1.0-metrics-demo`，不要重写 `v1.0.0-demo`。
- PR10 复测若遇到 `/live.ts` 返回 `Empty reply from server`，应先记录证据并另开修复 PR，不应在指标文档中写 ffprobe、动态码率或延迟已通过。
