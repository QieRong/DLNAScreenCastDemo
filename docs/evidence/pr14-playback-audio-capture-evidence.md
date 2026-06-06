# PR14 AudioPlaybackCapture 证据记录

> 本文记录 PR14 当前可归档的音频证据。结论必须分层使用：样本层证据不能直接写成 Kodi 已听到真实目标 App 声音。

## 证据结论

| 层级 | 当前结果 | 说明 |
|---|---|---|
| 代码路径 | 通过 | 当前代码已接入 `MediaProjection + AudioPlaybackCaptureConfiguration + AudioRecord -> AAC-LC -> MPEG-TS audio PID`。 |
| TS 样本 | 通过 | `ffprobe` 可识别 `mpegts`、`h264` 视频和 `aac` 音频。 |
| 非静音音频 | 通过 | 从样本解码出的 WAV 存在非静音能量。 |
| 接收端听感 | 未实测归档 | 尚未归档 PC / Kodi / ffplay 听到目标媒体声音的证据。 |
| 目标 App 策略 | 未逐项验证 | 不能外推到抖音、B 站、微信等具体 App。 |
| 手机端自动静音 | 未实测 | 不能写成手机端已自动静音或远端独占出声。 |

## 归档样本

| 文件 | 用途 |
|---|---|
| `docs/evidence/artifacts/pr14-audio-live-aac.ts` | PR14 `/live.ts` 抓取样本，包含视频和 AAC 音轨。 |
| `docs/evidence/artifacts/pr14-audio-live-decoded.wav` | 从样本提取 / 解码出的音频，用于确认非静音能量。 |

## ffprobe 摘要

命令：

```powershell
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe `
  -v error `
  -show_entries format=format_name,duration,size,bit_rate `
  -show_entries stream=index,codec_type,codec_name,width,height,sample_rate,channels,bit_rate `
  -of default=noprint_wrappers=1 `
  docs\evidence\artifacts\pr14-audio-live-aac.ts
```

结果摘要：

```text
format_name=mpegts
size=1069720
index=0
codec_name=h264
codec_type=video
width=1080
height=1920
index=1
codec_name=aac
codec_type=audio
sample_rate=48000
channels=1
bit_rate=130633
```

说明：`duration` 和 `format bit_rate` 对实时 TS 截片不稳定，不作为达标证据。

## 非静音检查

命令：

```powershell
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe `
  -hide_banner `
  -i docs\evidence\artifacts\pr14-audio-live-decoded.wav `
  -af astats=metadata=1:reset=0 `
  -f null NUL
```

结果摘要：

```text
Peak level dB: -18.183257
RMS level dB: -21.376643
Zero crossings rate: 0.041667
```

这证明归档 WAV 不是全零静音。它不能单独证明 Kodi 或目标 Renderer 已经听到声音。

## 仍需补齐

- 录制 / 归档 PC、Kodi 或 ffplay 端能听到目标媒体声音的证据。
- 记录目标媒体 App 名称、版本、Android capture policy 表现。
- 如测试抖音、B 站、微信等 App，必须单独记录结果，不能从本样本外推。
- 使用外部录像观察声音节奏点和画面节奏点，才能写“人工观察级基本音画同步”。
