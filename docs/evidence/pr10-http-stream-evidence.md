# PR10 HTTP / MPEG-TS 证据记录

测试时间：2026-06-04

## 环境

```text
分支：feat/010-final-metrics-evidence
main 基线：bb3f9ea94eebd71f146fe33a3f1ed54aca210e52
发送端：小米 14，型号 23127PN0CC
Android：16 / API 36
FFmpeg 工具：C:\tmp\ffmpeg-pr5\ffmpeg-8.1.1-essentials_build\bin
```

## 构建与安装

```powershell
.\gradlew.bat assembleDebug --console=plain
```

结果：

```text
BUILD SUCCESSFUL in 10s
36 actionable tasks: 36 up-to-date
```

完整门禁：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

结果：

```text
未执行完成。Codex 提权请求因当前使用额度限制被系统拒绝，因此本轮只确认 assembleDebug。
```

安装：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

结果：

```text
Performing Streamed Install
Success
```

## App 侧状态

UI 层级显示：

```text
当前阶段：PR 10：最终指标证据补强
性能指标：PR10 补强 ffprobe、动态码率和延迟证据；AAC 测试音轨已接入，是否被识别以真机样本为准；延迟仍需外部录像实测。
采集中：源画面 1200 x 2670 px
编码器：c2.qti.avc.encoder
实际编码画布：1080 x 1920
已配置视频码率：8.0 Mbps
码率模式：CBR
当前视频流：http://192.168.137.138:8080/live.ts
格式：MPEG-TS + H.264 视频 + AAC 测试音轨
```

手机端监听状态：

```powershell
adb shell ss -ltnp
```

摘要：

```text
LISTEN      0      0            *:8080                     *:*
Cannot open netlink socket: Permission denied
```

## ADB forward 路径

清理残留：

```powershell
adb forward --remove tcp:18080
```

结果：

```text
adb.exe: error: listener 'tcp:18080' not found
```

建立 forward：

```powershell
adb forward tcp:18080 tcp:8080
```

结果：

```text
18080
```

但随后：

```powershell
adb forward --list
```

结果为空；当前环境中 ADB daemon 多次在命令间重启，forward 未稳定保留。

PC 访问 forward：

```powershell
curl.exe -v http://127.0.0.1:18080/live.ts --output app\build\tmp\DLNAScreenCastDemo-pr10-av-aac-10s.ts --max-time 10
```

结果：

```text
connect to 127.0.0.1 port 18080 failed: Connection refused
curl: (7) Failed to connect to 127.0.0.1 port 18080
```

## PC 局域网直连路径

```powershell
curl.exe -v http://192.168.137.138:8080/live.ts --output app\build\tmp\DLNAScreenCastDemo-pr10-av-aac-10s.ts --max-time 10
```

提权后结果：

```text
Connected to 192.168.137.138 (192.168.137.138) port 8080
GET /live.ts HTTP/1.1
Empty reply from server
curl: (52) Empty reply from server
```

## 手机本机访问路径

```powershell
adb shell "curl -v http://127.0.0.1:8080/live.ts -o /sdcard/DLNAScreenCastDemo-pr10-av-aac-10s.ts --max-time 10"
```

重启采集前后均复现：

```text
Connected to 127.0.0.1 (127.0.0.1) port 8080
GET /live.ts HTTP/1.1
Empty reply from server
curl: (52) Empty reply from server
```

## PR10 结论

- App 能启动采集，UI 显示 H.264 1080 x 1920 / 8 Mbps / CBR，手机端 `*:8080` 处于监听状态。
- 当前 `/live.ts` 在 PC 直连和手机本机 curl 下均返回 `Empty reply from server`。
- 因未取得 `.ts` 样本，本轮无法执行有效 `ffprobe` 音视频流识别，不能写 `audio=aac` 已通过。
- 30 秒动态码率样本和延迟样本均依赖可读取的 `/live.ts`，本轮保持未实测。
- 后续应优先另开修复 PR 排查 `LocalStreamServer` / `StreamSession.open()` 静默关闭连接的问题，再复测 AAC 与动态码率。
