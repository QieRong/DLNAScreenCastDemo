# PR17 Kodi 慢动作与严重延迟积压诊断记录

> 本文只记录 PR17 的分层诊断入口和待真机填写的证据模板。当前不能写任何低延迟达标、Kodi 慢动作已完成修复或音画同步已完成闭环的结论。

## 本阶段目标

- 判断 video-only 是否也存在慢动作和秒表严重落后。
- 判断开启 audio 后是否加重慢动作或秒表落后。
- 用 10 秒 curl 样本和 ffprobe 判断 TS duration、帧率和音频轨声明是否合理。
- 用限频日志判断视频 PTS delta、音频 PTS delta、音频初始 offset、发布/丢弃计数和 StreamSession 队列积压。

## 新增诊断日志

`StreamPipeline` 每 5 秒输出一次摘要：

```text
encodedVideoFrameCount
publishedVideoFrameCount
droppedVideoFrameCount
receivedAudioFrameCount
publishedAudioFrameCount
droppedAudioFrameCount
audioOffsetRejectedCount
videoPtsDeltaUsMin / Max / Avg
audioPtsDeltaUsMin / Max / Avg
lastVideoPtsUs
lastAudioPtsUs
```

`StreamServer` 每 5 秒输出一次摘要：

```text
publishedChunkCount
currentSessionCount
pendingSocketCount
pendingPacketCount
maxSessionPendingPacketCount
sessionWriteFailureCount
replayChunkCount
replayBytes
```

说明：这些日志只用于定位，不作为“已修复”证据。

## 真机验证模板

```text
测试模式：video-only / video+audio
接收端：Kodi / ffplay
测试画面：系统秒表 / App 延迟测试页

10s：
- 接收端显示：__ 秒
- 手机端实际：__ 秒
- 差值：__ 秒

30s：
- 接收端显示：__ 秒
- 手机端实际：__ 秒
- 差值：__ 秒

60s：
- 接收端显示：__ 秒
- 手机端实际：__ 秒
- 差值：__ 秒

观察：
- 是否慢动作：是 / 否
- 是否缓冲转圈：是 / 否
- 是否播放旧画面：是 / 否
- 是否延迟持续扩大：是 / 否
```

如果出现“接收端显示 10s，手机端已经 43s”，必须写：

```text
当前 Kodi 端已落后约 33s，不满足 <2s 延迟目标，也不能写成优化成功。
```

## 10 秒样本命令

```powershell
adb -s a630f3ff forward tcp:18080 tcp:8080
curl.exe -v http://127.0.0.1:18080/live.ts --output C:\tmp\slow-motion-test.ts --max-time 10

C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe `
  -v error `
  -show_entries format=format_name,duration,size,bit_rate `
  -show_entries stream=index,codec_type,codec_name,width,height,avg_frame_rate,r_frame_rate,start_time,duration,bit_rate `
  -of default=noprint_wrappers=1 `
  C:\tmp\slow-motion-test.ts
```

判断口径：

- curl 10 秒但 ffprobe duration 明显大于 10 秒：优先排查 TS 时间戳 / PCR / PTS。
- duration 接近 10 秒但 Kodi 仍慢动作：优先怀疑 Kodi 缓冲策略或 DLNA 兼容。
- `avg_frame_rate` 很低：优先排查编码输出帧率或 pipeline 发布帧率。
- ffplay 低延迟正常但 Kodi 慢动作：不要继续盲改核心流逻辑，应记录 Kodi 侧兼容性差异。

## 当前状态

- 已新增限频诊断日志和单元测试入口。
- 已增加音频初始 offset 过大时的拒绝计数与保护，避免明显跨时间域的 AAC 帧进入 TS。
- 尚未完成 video-only / video+audio 的 Kodi 真机对照。
- 尚未完成 ffplay 对照。
- 尚未完成 10 秒样本 ffprobe 复核。
