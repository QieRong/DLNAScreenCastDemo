# PR15 Kodi 无响应 / 黑屏 / 不来拉流 — 诊断证据

> 本文档仅记录 PR15 范围内的诊断证据：网络前置检查、分层结论、代码修复说明。
> 不做 PR14 音频听感验收、AudioPlaybackCapture 或音画同步结论。

## 测试时间

2026-06-07

## 设备

| 项目 | 内容 |
|---|---|
| Android 发送端 | 小米 14，型号 23127PN0CC，ADB 序列号 a630f3ff |
| Android 版本 | 16，API 36 |
| PC 接收端 | Windows 电脑（热点宿主，IP 192.168.137.1）|
| Kodi | Windows 端 Kodi，UPnP/DLNA 已开启 |
| 手机 Wi-Fi IP | 192.168.137.182 |
| 网络环境 | Windows 电脑热点，手机连接热点 |

## 网络前置检查

### 1. 获取手机 Wi-Fi IP

```bash
adb -s a630f3ff shell ip addr show wlan0
```

结果：

```text
inet 192.168.137.182/24 brd 192.168.137.255 scope global wlan0
```

手机 Wi-Fi IP = **192.168.137.182**

### 2. PC ping 手机 IP

```powershell
ping 192.168.137.182 -n 4
```

结果：

```text
来自 192.168.137.182 的回复: 字节=32 时间=4ms TTL=64
来自 192.168.137.182 的回复: 字节=32 时间=20ms TTL=64
来自 192.168.137.182 的回复: 字节=32 时间=35ms TTL=64
来自 192.168.137.182 的回复: 字节=32 时间=44ms TTL=64
已发送 = 4，已接收 = 4，丢失 = 0 (0% 丢失)
```

**结论：PC ↔ 手机 ICMP 可达，局域网基础连通性正常。**

### 3. PC 检查手机 8080 端口

```powershell
Test-NetConnection 192.168.137.182 -Port 8080
```

结果：

```text
ComputerName    RemotePort TcpTestSucceeded PingSucceeded
------------    ---------- ---------------- -------------
192.168.137.182        8080            False          True
```

**结论：TCP 8080 不通。**

原因分析：测试时 App 未在投屏/推流状态，8080 端口未监听。需要用户启动 App 采集后重测。

> 注意：此结论等同于 PR7 历史记录的热点直连失败问题，可能还涉及 Windows 防火墙或热点网卡隔离。使用 ADB forward 作为对照：仅证明 App 本机 HTTP 服务可读，不证明局域网可达。

### 4. curl 前置检查

尚未执行（需 App 先启动投屏）。等 App 开始投屏后，执行：

```powershell
mkdir C:\tmp -ErrorAction SilentlyContinue
curl.exe -v http://192.168.137.182:8080/live.ts --max-time 5 --output C:\tmp\pr15-live-precheck.ts
```

预期结果等待补充。

### 5. ADB forward 对照

```powershell
adb forward tcp:18080 tcp:8080
curl.exe -v http://127.0.0.1:18080/live.ts --output C:\tmp\pr15-adb-forward.ts --max-time 10
```

预期结果等待补充。

> ADB forward 只证明 App 本机 HTTP 服务可读，不证明局域网可达。

## Kodi 复现要求

需要同时抓取的 logcat：

```bash
adb -s a630f3ff logcat -s DlnaControl StreamServer StreamSession ScreenCapture Encoder
```

关键检查点：

- DlnaControl 是否出现 SetURI 成功
- DlnaControl 是否出现 Play 成功
- StreamSession 是否出现"握手成功，开始推流"
- Kodi 是否发 HEAD /live.ts（StreamSession 日志中的 HEAD 探测）
- Kodi 是否发 GET /live.ts
- GET 后 Kodi 是否黑屏

**Kodi logcat 和 PC 前置检查结果待真机复现后补充。**

## PR15 代码修改说明

### 修改范围

| 文件 | 修改类型 | 说明 |
|---|---|---|
| `dlna/control/SoapRequestBuilder.kt` | 功能增强 | 新增 `buildDidlLiteMetadata()` 方法 |
| `dlna/control/SoapRequestBuilderTest.kt` | 测试补充 | 新增 8 个 DIDL-Lite 相关测试 |
| `stream/StreamSession.kt` | 无修改 | HEAD 行为已正确，仅补测试 |
| `stream/StreamSessionTest.kt` | 测试补充 | 新增 2 个 HEAD 行为验证测试 |
| `stream/MpegTsStreamPipelineTest.kt` | 测试补充 | 新增 3 个 bootstrap/replay 行为测试 |
| `docs/evidence/pr15-kodi-no-response-evidence.md` | 新增 | 本文件 |

### StreamSession HEAD 行为（已符合规范）

审查结论：`StreamSession.kt` 中 HEAD 行为已正确：

- `HEAD /live.ts` 返回 `200 OK` + `Content-Type: video/mp2t`
- HEAD 不创建 live session（返回 null）
- HEAD 不启动 TS 推流
- HEAD 后 socket 关闭（客户端收到 EOF）
- `GET /live.ts` 才注册 session 并持续输出 TS

PR15 为 HEAD 行为新增了 2 个专项测试用例：
- `open_headResponseContainsVideoMp2tContentType`：验证 Content-Type 头
- `open_headClosesSocketAfterResponseWithoutTsData`：验证 HEAD 后 socket 关闭

### DIDL-Lite metadata（按需使用）

新增 `SoapRequestBuilder.buildDidlLiteMetadata(streamUrl, title)` 方法，生成最小 DLNA 兼容的 DIDL-Lite XML。

**触发条件**（必须同时满足才启用）：
1. SetURI 成功
2. Play 成功
3. PC 网络可达手机 IP 和 8080
4. Kodi 不发 GET /live.ts

**DIDL-Lite 结构**：

```xml
<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" ...>
  <item id="1" parentID="0" restricted="1">
    <dc:title>Live Screen Cast</dc:title>
    <upnp:class>object.item.videoItem</upnp:class>
    <res protocolInfo="http-get:*:video/mp2t:*">http://ip:8080/live.ts</res>
  </item>
</DIDL-Lite>
```

该方法生成原始 XML 字符串；传入 `setAvTransportUri(metadata=...)` 时会自动通过 `xmlEscaped()` 二次转义，保证 SOAP 结构不被破坏。

### TS bootstrap/replay 验证

PR15 新增 3 个 `MpegTsStreamPipelineTest` 测试，验证：

1. **P-frame 不进 replay**：`onAccessUnit_pFrameIsNotMarkedAsBootstrap` — P-frame 的 `isBootstrap=false`，`LocalStreamServer.updateReplayChunks` 只接收 `replayOnConnect=true` 的 chunk，P-frame 不会加入 replay 缓存。
2. **reset 后 P-frame 被丢弃**：`reset_pFrameAfterResetIsDroppedUntilNewKeyFrame` — encoder 重建 `reset()` 后，`waitingForKeyFrame=true`，旧 GOP 的 P-frame 不会发布。
3. **reset 后新 IDR 是 bootstrap**：`reset_newKeyFrameAfterResetIsPublishedAndMarkedBootstrap` — 旧 encoder 的 SPS/PPS 不会泄露到新 GOP 的 replay。

## PR15 分层结论（当前）

| 层次 | 问题描述 | 当前结论 |
|---|---|---|
| 1. SetURI / Play 失败 | DLNA 控制层问题 | 待真机复现 |
| 2. SetURI / Play 成功，Kodi 不发 HEAD/GET | Renderer 兼容性或 metadata 问题 | 已备好 DIDL-Lite 修复，待触发条件确认后启用 |
| 3. Kodi 发 HEAD 不发 GET | HEAD 响应或 metadata 兼容问题 | HEAD 行为已审查符合规范（200 OK + Content-Type: video/mp2t） |
| 4. Kodi 发 GET 但黑屏 | TS bootstrap / keyframe / SPS/PPS / PMT 问题 | Pipeline 行为已验证：关键帧前含 SPS/PPS，P-frame 不进 replay |
| 5. curl 可读但 Kodi 不行 | Kodi 兼容性问题 | 待 curl 前置检查完成后判断 |
| 6. ADB forward 可读但局域网不可读 | 网络 / 热点隔离 / 防火墙问题 | 当前 TCP 8080 不通（App 未推流），需启动推流后重测 |

## 未完成项

- [ ] App 启动投屏后重测 TCP 8080 连通性
- [ ] 执行 `curl.exe -v http://192.168.137.182:8080/live.ts --max-time 5 --output C:\tmp\pr15-live-precheck.ts`
- [ ] 执行 ADB forward + curl 对照验证
- [ ] 真机 Kodi 复现：抓取 logcat 并分层判断 SetURI/Play/HEAD/GET/黑屏
- [ ] 根据分层结论决定是否启用 DIDL-Lite（当前代码已备好，仅需修改调用处传入 metadata）
- [ ] 如果 Kodi 发 GET 但黑屏：用 ffprobe 验证 TS bootstrap 包含 SPS/PPS + IDR

## 后续建议

- PR15 主线（当前）：代码审查 + 单元测试补充已完成；真机 Kodi 复现待进行
- PR16：补 PR14 真实播放音接收端听感证据
- PR17：严格 < 2 秒延迟测试与 8 Mbps 动态码率复测
- PR18：DIDL-Lite / DLNA contentFeatures / 真实电视兼容矩阵增强

## 参考资料

- UPnP ContentDirectory specification（DIDL-Lite 结构）
- DLNA Networked Device Interoperability Guidelines（protocolInfo 格式）
- 本项目自己的实现差异：DIDL-Lite 构建为最小 videoItem，不使用第三方 DLNA 库
- 是否复制代码：否
