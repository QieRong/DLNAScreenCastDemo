# PR13 video-only 真机回归证据

测试时间：2026-06-05 18:18-18:27（Asia/Shanghai）

分支：`fix/013-disable-runtime-test-tone`

基线提交：`124fd49`

设备：`a630f3ff` / `23127PN0CC`

## 结论

PR13 默认 video-only；真实系统播放音频待 PR14 实现和验证。

本轮真机回归确认：

- App 可安装并启动。
- 录屏授权后进入采集中状态。
- UI 显示实际编码画布为 `1080 x 1920`，视频码率 `8.0 Mbps`，格式为 `MPEG-TS + H.264 视频（video-only）`。
- 默认运行时未发现 App 内 1kHz 测试音、AAC 首帧或音频 mux 日志。
- 通过 ADB forward 辅助抓到 `sample-pr13-video-only.ts`，样本大小 `389912` bytes。
- 样本结构解析为 MPEG-TS，PMT 仅声明 H.264 视频流，无 AAC audio stream。
- 提交附件路径：`docs/evidence/artifacts/sample-pr13-video-only.ts`。

## 构建门禁

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

结果：`BUILD SUCCESSFUL`

```powershell
git diff --check
```

结果：通过。仅有 CRLF 提示，无 whitespace error。

## 真机安装与启动

```powershell
adb devices
adb -s a630f3ff install -r app\build\outputs\apk\debug\app-debug.apk
adb -s a630f3ff logcat -c
adb -s a630f3ff shell am start -n com.qierong.dlnascreencastdemo/.MainActivity
```

结果：

- `adb devices` 显示 `a630f3ff device`。
- APK 安装结果：`Success`。
- App 启动结果：`Starting: Intent { cmp=com.qierong.dlnascreencastdemo/.MainActivity }`。
- 点击“开始采集”并授权后，UI 显示：`采集中：源画面 1200 x 2670 px`。

## 流地址与 curl

App UI 显示：

```text
当前视频流：http://192.168.137.134:8080/live.ts
格式：MPEG-TS + H.264 视频（video-only）
说明：PR13 起默认不再输出 App 内 1kHz 测试音；AAC/ADTS/MPEG-TS 音频封装能力保留给显式音频路径。真实系统播放音待 PR14 实现和验证，延迟仍未实测。
```

PC 直连手机 Wi-Fi IP 抓流：

```powershell
curl.exe -v http://192.168.137.134:8080/live.ts --output sample-pr13-video-only.ts --max-time 10
```

结果：失败，错误为 `Bad access` / `Could not connect to server`。

失败后按 PR13 要求检查手机端监听：

```powershell
adb -s a630f3ff shell "ss -ltnp | grep 8080"
```

结果：

```text
LISTEN      0      0            *:8080                     *:*
Cannot open netlink socket: Permission denied
```

StreamServer / StreamSession 日志：

```powershell
adb -s a630f3ff logcat -d -s StreamSession StreamServer
```

结果：

```text
I StreamServer: 本地流地址：http://192.168.137.134:8080/live.ts
```

因此，本轮不能把 PC 直连失败直接归因到 PR13 去测试音改动；手机端服务已监听，失败点更像电脑到手机 IP 的网络访问限制。

ADB forward 辅助抓流：

```powershell
adb start-server
adb -s a630f3ff forward tcp:18080 tcp:8080
adb -s a630f3ff forward --list
curl.exe -v http://127.0.0.1:18080/live.ts --output sample-pr13-video-only.ts --max-time 10
```

结果：

```text
a630f3ff tcp:18080 tcp:8080
HTTP/1.1 200 OK
Content-Type: video/mp2t
Operation timed out after 10013 milliseconds with 389912 bytes received
```

说明：`curl` exit code 为 28，原因是 `--max-time 10` 到时中断；样本已成功保存。

## ffprobe 结果

PR13 要求执行：

```powershell
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe sample-pr13-video-only.ts
C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin\ffprobe.exe -select_streams a sample-pr13-video-only.ts
```

视频 / 格式检查：

```text
codec_name=h264
codec_type=video
width=1080
height=1920
codec_name=h264
codec_type=video
width=1080
height=1920
format_name=mpegts
```

音频流检查：

```text
<empty output>
```

结论：`ffprobe` 识别 `format_name=mpegts`、`codec_type=video`、`codec_name=h264`、`width=1080`、`height=1920`；`-select_streams a` 无输出，未识别到 `audio` / `aac` 音频流。

## 样本结构复核

同时使用本地二进制解析脚本复核样本结构：

```text
bytes=389912 packets=2074 sync_all=True
pat_pmt_pid=0x1000
pcr_pid=0x100
streams=type=0x1b,pid=0x100
has_h264=True
has_aac=False
nal_types=7,8,5,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1
has_sps=True
has_idr=True
sps_width=1080 sps_height=1920
```

复核结果：

- MPEG-TS sync byte 检查通过。
- PAT 指向 PMT PID `0x1000`。
- PMT 仅声明 `stream_type=0x1b` H.264 视频流。
- 未声明 `stream_type=0x0f` AAC audio stream。
- H.264 SPS 解析为 `1080 x 1920`。

## logcat 关键词检查

命令：

```powershell
adb -s a630f3ff logcat -d -s DLNA-Demo ScreenCapture Encoder StreamServer StreamSession DlnaControl AudioEncoder
```

关键日志：

```text
D StreamSession: [/127.0.0.1:60675] 请求行：GET /live.ts HTTP/1.1
I StreamSession: [/127.0.0.1:60675] 握手成功，开始推流
I ScreenCapture: 系统停止屏幕采集
I Encoder : H.264 编码器已释放：csd-0=true，csd-1=true，codecConfigBuffers=1，encodedFrames=603，firstMediaFrame=true，firstKeyFrame=true
I StreamServer: 本地流服务已停止
I ScreenCapture: 屏幕采集资源释放完成
```

关键词扫描：

```text
NO_MATCH AudioEncoder
NO_MATCH AudioCapture
NO_MATCH first audio
NO_MATCH muxAudio
NO_MATCH AAC
NO_MATCH 1kHz
NO_MATCH sine
NO_MATCH test tone
NO_MATCH 测试音
NO_MATCH first AAC frame
NO_MATCH muxAudioAccessUnit
```

结论：运行时未观察到测试音启动、first AAC frame 或 `muxAudioAccessUnit` 音频发布日志。

## 未完成 / 待补

- PC 直连 `http://192.168.137.134:8080/live.ts` 失败，需要后续排查电脑网络、防火墙、共享网络或 USB/热点网段限制；本轮已用 ADB forward 证明手机端服务和 video-only 样本输出存在。
