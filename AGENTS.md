# AGENTS.md｜DLNAScreenCastDemo AI 开发协作规范

> 本文件用于约束 Codex / AI Agent 在本仓库中的开发行为。  
> 项目地址：https://github.com/QieRong/DLNAScreenCastDemo.git  
> 文档语言：中文。代码注释可使用中文，类名、函数名、包名使用英文。

---

## 1. 项目目标

本项目是一个 **Android 手机投屏 Demo**，目标是在 3 天左右完成一个可演示（录制视频给别人看）、可测试、可下载 APK 的原型项目。

核心目标：

1. 基于 DLNA / UPnP 思路，实现手机端发现局域网内支持 DLNA 的播放设备。
2. 使用 Android 原生能力采集手机屏幕画面。
3. 将采集画面编码为视频流，并通过本地网络服务提供给接收端播放。
4. 在 App 中展示投屏状态、目标参数、测试结果和已知限制。
5. 交付可以在 GitHub Releases 下载的 APK，并保证每个阶段版本可回溯。

技术指标目标：

| 指标 | 目标值 |
|---|---|
| 投屏延时 | 目标 < 2 秒 |
| 分辨率 | 目标支持 1080P |
| 视频码率 | 目标 8Mbps |
| 音频规格 | 目标 AAC 128Kbps |
| 平台 | Android Demo |
| 协议方向 | DLNA / UPnP 设备发现与控制 + 本地流媒体服务 |

重要说明：

- 本项目是实习技术测试 Demo，不做完整商用投屏 SDK。
- 不允许假装已经达成指标。没有实测数据时，必须写“未实测”。
- 若某些电视或播放器不兼容实时流，必须在 README 和测试报告中说明。
- 若音频采集受 Android 系统权限或应用捕获策略限制，必须如实说明，不得伪造系统音频采集结果。
- 每完成了一个部分要提醒我在codex中新开一个对话，避免上下文过长导致幻觉！
- 开始 PR 3 及后续新阶段前，必须先明确提醒我在 Codex 中新开一个对话；开发过程中遇到有用且结果正确的截图，应保存到仓库，并在适合时插入 `README.md` 作为验收或演示证据。
- `README.md` 中同一验收或演示场景存在多张竖屏截图时，必须并排展示；每行最多并排放置 3 张竖屏截图，超过 3 张时换行继续排列。只有一张竖屏截图和一张横屏截图时，可放在同一行紧凑展示；竖屏截图已成组并排时，横屏截图另起一行展示。截图缩略图应紧凑且可辨认，避免占用过大的纵向空间。
- 每次开始下一个 PR 前，必须先检查当前 PR 是否已合并；确认合并后同步 `main`，再从最新 `main` 新建下一个阶段的独立分支。不允许在前一个 PR 分支上继续开发，也不允许在当前 PR 尚未合并时提前叠加下一个阶段。PR 3 示例：

```bash
git checkout main
git pull origin main
git checkout -b feat/003-screen-capture
```

- 乐播投屏有一个开发者平台，你看里面的东西能不能用的，能沾点边的话更好，用不到也无所谓。https://cloud.lebo.cn/

---

## 2. 已安装 Skills，写代码前必须使用

本项目已经安装以下 skills。AI Agent 在每次写代码、改架构、修 bug、写测试前，必须先识别任务类型，并调用对应 skills。

### 2.1 必须遵守的调用规则

每次开始写代码前，AI Agent 必须先输出并执行以下检查：

```text
本次任务识别：
- 任务类型：
- 计划使用的 skills：
- 使用原因：
- 影响范围：
- 预计测试命令：
```

如果当前环境支持 skills 调用，必须实际调用相关 skills 后再修改代码。

如果当前环境不支持真实调用 skills，必须停止直接编码，并在回复中说明：

```text
当前环境无法调用 skills，因此不能直接执行代码修改。以下仅提供修改计划。
```

不得假装已经调用 skills。

### 2.2 Skill 使用矩阵

| 场景 | 必须优先使用的 Skill |
|---|---|
| 初始化项目结构、拆模块、调整包结构 | android-architecture |
| 设计 capture / encoder / stream / dlna / ui 分层 | android-architecture |
| UI 状态管理、投屏状态流转、设备列表状态 | android-viewmodel |
| 协程、扫描设备、网络请求、后台任务、资源释放 | kotlin-concurrency-expert |
| 单元测试、UI 测试、模拟器测试、测试策略 | android-testing |
| ADB、模拟器、安装 APK、自动化冒烟测试 | android-emulator-skill |
| Gradle 构建慢、依赖冲突、缓存优化 | gradle-build-performance |
| Compose 页面、设备列表、控制面板、状态页 | compose-ui |

### 2.3 禁止行为

- 禁止不看现有代码就直接大范围重构。
- 禁止一次 PR 同时做多个大模块。
- 禁止为了让编译通过删除核心逻辑。
- 禁止把开源项目代码整段复制进本仓库。
- 禁止把不可运行的伪代码提交到 `main`。
- 禁止声称“已测试”但没有给出命令和结果。
- 禁止提交无法回滚、无法定位版本的代码。

---

## 3. 推荐技术栈

优先使用以下技术栈，不要随意换路线：

```text
语言：Kotlin
UI：Jetpack Compose
架构：MVVM + 清晰分层
异步：Kotlin Coroutines + Flow / StateFlow
构建：Gradle Kotlin DSL
最低 SDK：建议 minSdk 26+
目标 SDK：按当前 Android Gradle Plugin 支持版本设置
视频采集：MediaProjection + VirtualDisplay
视频编码：MediaCodec H.264 / AVC
音频编码：AAC 128Kbps，优先使用系统允许的采集方式
网络服务：手机本地 HTTP 服务 / 流媒体输出
DLNA 控制：UPnP SSDP 发现 + MediaRenderer 控制
测试：JUnit + AndroidX Test + ADB 冒烟测试
```

第三方库原则：

- 能不用就不用。
- 必须使用时，先说明用途、许可证、替代方案。
- DLNA / UPnP 可参考 Cling、YAACC、dlna-cast 等项目的思路，但不能照抄实现。
- 引入依赖前必须检查许可证，避免 GPL 代码污染本项目。
- Demo 阶段优先小而清晰，不追求功能堆砌。

---

## 4. 项目结构建议

初始化后建议按以下结构组织：

```text
app/
  src/main/java/com/qierong/dlnascreencastdemo/
    MainActivity.kt

    core/
      common/
      logging/
      permission/
      result/

    feature/
      home/
      device/
      casting/
      settings/

    capture/
      ScreenCaptureManager.kt
      CaptureConfig.kt
      CaptureState.kt

    encoder/
      VideoEncoder.kt
      AudioEncoder.kt
      EncoderConfig.kt
      EncodedFrame.kt

    stream/
      LocalStreamServer.kt
      StreamSession.kt
      StreamUrlProvider.kt

    dlna/
      SsdpDiscoveryClient.kt
      DlnaDevice.kt
      DlnaControlPoint.kt
      AvTransportClient.kt
      SoapRequestBuilder.kt

    ui/
      theme/
      components/

    testutil/
```

分层规则：

1. `ui` 只负责显示和交互，不直接写网络扫描、编码、投屏逻辑。
2. `ViewModel` 负责组合用例、暴露 UI 状态，不直接操作底层编码器细节。
3. `capture` 只负责屏幕采集权限、VirtualDisplay、Surface 连接和释放。
4. `encoder` 只负责编码配置、编码启动、停止、输出帧。
5. `stream` 只负责本地网络服务和流输出。
6. `dlna` 只负责 SSDP 搜索、设备解析、SOAP 控制命令。
7. 每个模块必须可以单独测试关键逻辑。

---

## 5. 功能范围与实现顺序

### 阶段 0：项目初始化

目标：

- 创建 Android 原生 Kotlin 项目。
- 配置包名：`com.qierong.dlnascreencastdemo`
- 配置 Compose、基础主题、README、`.gitignore`、基础 CI 或本地测试命令。
- 创建最小可运行 App。

验收：

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

---

### 阶段 1：DLNA / UPnP 设备发现

目标：

- 实现 SSDP M-SEARCH 搜索。
- 发现局域网内 MediaRenderer 设备。
- 解析设备名称、IP、服务地址、AVTransport 控制地址。
- 在 UI 中展示设备列表。
- 没有设备时显示清晰的空状态和测试说明。

建议实现：

```text
SsdpDiscoveryClient
DlnaDevice
DeviceRepository
DeviceListViewModel
DeviceListScreen
```

验收：

- 手机和电脑在同一 Wi-Fi。
- 电脑端开启 Kodi 或其他可作为 UPnP/DLNA Renderer 的软件。
- App 点击“搜索设备”后能看到设备。
- 日志中能看到 M-SEARCH 请求和响应解析结果。

---

### 阶段 2：屏幕采集权限与采集骨架

目标：

- 接入 MediaProjection 权限申请。
- 创建 VirtualDisplay。
- 将采集画面输出到编码器 Surface。
- 处理横竖屏、停止采集、权限拒绝、资源释放。

要求：

- 必须有前台服务或符合 Android 当前版本要求的投屏服务设计。
- 必须处理 `MediaProjection.Callback`。
- 用户拒绝权限时不能崩溃。
- 停止投屏时必须释放 VirtualDisplay、Surface、Encoder、StreamServer。

验收：

- 真机点击“开始采集”能弹出系统录屏授权。
- 同意后状态变为“采集中”。
- 停止后状态回到“未投屏”。
- `adb logcat` 无明显资源泄露和崩溃。

---

### 阶段 3：视频编码参数

目标：

- 使用 H.264 / AVC 编码。
- 支持目标 1080P。
- 目标视频码率 8Mbps。
- 关键参数必须集中在 `EncoderConfig`。

建议配置：

```text
width = 1920
height = 1080
videoBitrate = 8_000_000
frameRate = 30
iFrameInterval = 1
mimeType = video/avc
```

要求：

- 如果设备不支持 1080P 或 8Mbps，必须降级并在 UI 显示实际参数。
- 不允许只在 UI 写 1080P / 8Mbps，底层却没配置。
- 编码器启动失败必须给出错误状态。

验收：

- 单元测试覆盖参数校验。
- 日志输出实际编码参数。
- UI 展示目标参数和实际参数。

---

### 阶段 4：本地流媒体服务

目标：

- 在手机本机启动 HTTP 服务。
- 对外提供可播放的流 URL，例如：

```text
http://<phone-ip>:8080/live.ts
```

或：

```text
http://<phone-ip>:8080/live.m3u8
```

要求：

- App UI 必须展示当前流地址。
- PC 端可以使用 `ffplay` 或支持网络流的软件验证。
- 如果 DLNA Renderer 不支持某种实时格式，需要尝试 TS / HLS 等兼容方式，并记录结果。

验收命令示例：

```bash
ffplay -fflags nobuffer -flags low_delay -framedrop -probesize 32 -analyzeduration 0 http://<phone-ip>:8080/live.ts
```

也可以先抓取 10 秒文件：

```bash
curl -v http://<phone-ip>:8080/live.ts --output sample.ts --max-time 10
ffprobe sample.ts
```

---

### 阶段 5：DLNA 控制播放

目标：

- 对发现到的 MediaRenderer 执行 AVTransport 控制。
- 设置播放地址。
- 发送播放、暂停、停止命令。
- UI 展示目标设备、连接状态、播放状态。

建议实现：

```text
AvTransportClient.setAvTransportUri()
AvTransportClient.play()
AvTransportClient.pause()
AvTransportClient.stop()
```

要求：

- SOAP 请求必须可单元测试。
- 网络失败、设备拒绝、控制地址为空时必须有错误提示。
- 不允许隐藏异常。

验收：

- App 发现设备。
- 选择设备。
- 点击开始投屏。
- DLNA Renderer 尝试播放手机提供的流 URL。
- 失败时 UI 显示具体失败阶段：发现失败 / 设置 URL 失败 / 播放失败 / 流不可播放。

---

### 阶段 6：演示页与测试报告

目标：

- App 内增加“测试信息”区域。
- README 中写清楚如何测试。
- 保存测试截图或录屏。
- GitHub Release 附带 APK。

测试信息至少包括：

```text
目标分辨率：
实际分辨率：
目标视频码率：
实际视频码率：
目标音频码率：
实际音频码率：
目标延迟：
实测延迟：
测试设备：
接收端软件：
测试时间：
已知问题：
```

---

## 6. 没有电视时的具体测试方案

用户目前只有电脑和手机，因此测试必须支持“无电视验证”。测试方案必须详细告知用户，因为用户没有经验！

### 6.1 基础环境

准备：

1. Android 手机一台。
2. Windows / macOS / Linux 电脑一台。
3. 手机和电脑连接同一个 Wi-Fi。
4. 电脑关闭或放行防火墙，允许局域网访问。
5. 手机安装 Debug APK。

查看手机 IP：

```bash
adb shell ip addr show wlan0
```

或者在 App 内显示当前 Wi-Fi IP。

---

### 6.2 测试一：App 是否能启动

命令：

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.qierong.dlnascreencastdemo/.MainActivity
```

通过标准：

- App 正常打开。
- 没有闪退。
- 首页能看到“搜索设备 / 开始投屏 / 测试信息”等入口。

---

### 6.3 测试二：DLNA 设备发现

电脑端建议安装 Kodi，作为 DLNA / UPnP Renderer 测试。

Kodi 设置路径：

```text
Settings → Services → UPnP / DLNA
```

打开以下选项：

```text
Enable UPnP support
Allow remote control via UPnP
Look for remote UPnP players
```

然后手机端点击“搜索设备”。

通过标准：

- App 设备列表能显示 Kodi 或其他 UPnP Renderer。
- 日志能看到设备 IP、friendlyName、controlURL。
- 如果搜索不到，README 必须说明防火墙、同 Wi-Fi、路由器 AP 隔离等排查方法。

辅助抓包：

```text
Wireshark 过滤条件：
udp.port == 1900 || http
```

---

### 6.4 测试三：本地流 URL 是否可访问

App 开始推流后，UI 应显示类似地址：

```text
http://<phone-ip>:8080/live.ts
```

电脑端执行：

```bash
curl -v http://<phone-ip>:8080/live.ts --output sample.ts --max-time 10
```

如果生成了 `sample.ts`，继续执行：

```bash
ffprobe sample.ts
```

通过标准：

- HTTP 能连接。
- 能收到连续数据。
- `ffprobe` 能识别视频流。
- 如果音频已实现，`ffprobe` 能识别 AAC 音频流。

---

### 6.5 测试四：电脑端播放手机流

电脑安装 FFmpeg 后执行：

```bash
ffplay -fflags nobuffer -flags low_delay -framedrop -probesize 32 -analyzeduration 0 http://<phone-ip>:8080/live.ts
```

通过标准：

- 电脑端能看到手机画面。
- 画面延迟目标小于 2 秒。
- 画面不应长时间黑屏、花屏、卡死。
- 停止投屏后，电脑播放中断，App 状态恢复正常。

如果使用 HLS：

```bash
ffplay -fflags nobuffer -flags low_delay http://<phone-ip>:8080/live.m3u8
```

---

### 6.6 测试五：延迟测量

必须提供可复现的延迟测试方式，不允许只凭肉眼说“差不多”。

建议方式：

1. App 开启“时间戳浮层”。
2. 在手机屏幕显示毫秒级或秒级时间。
3. 电脑播放投屏画面。
4. 用另一台设备同时拍摄手机屏幕和电脑屏幕。
5. 根据视频帧差计算延迟。

计算方式：

```text
延迟秒数 = 两个画面时间差
```

如果用 60fps 视频估算：

```text
延迟秒数 = 相差帧数 / 60
```

每次测试至少记录 3 组：

```text
第 1 次：
第 2 次：
第 3 次：
平均值：
```

通过标准：

- 平均延迟 < 2 秒才可以写“达到目标”。
- 如果没有达到，必须写实际结果和优化方向。

---

### 6.7 测试六：日志检查

命令：

```bash
adb logcat -s DLNA-Demo ScreenCapture Encoder StreamServer DlnaControl
```

日志必须包含：

```text
设备搜索开始 / 结束
发现设备数量
选择设备
申请屏幕采集权限
编码器配置
本地流地址
DLNA setAVTransportURI 结果
DLNA play 结果
停止投屏
资源释放完成
```

---

## 7. 开源参考规则

允许参考：

- Android 官方文档中的 MediaProjection / MediaCodec / AudioPlaybackCapture 设计。
- UPnP / DLNA 协议文档中的设备发现和 AVTransport 概念。
- Cling 的 UPnP 抽象思路。
- YAACC 的功能边界和测试思路。
- dlna-cast 的命令行投屏流程思路。

严格禁止：

- 复制开源项目中的完整类、完整函数、完整协议封装。
- 删除对方版权声明后挪进本项目。
- 用 GPL 代码改名后提交。
- 只改变量名但保留原实现结构。
- 让 AI 根据某个仓库“照着写一样的”。

如果参考了开源资料，PR 中必须写：

```text
参考资料：
- 项目 / 文档名称：
- 参考内容：
- 本项目自己的实现差异：
- 是否复制代码：否
```

---

## 8. 代码质量要求

### 8.1 Kotlin 代码

- 类名、函数名必须语义清晰。
- 复杂逻辑必须拆小函数。
- 不要写超过 300 行的单个 Kotlin 文件，确实需要时必须说明原因。
- 不要在 Activity 中堆业务逻辑。
- 所有可释放资源必须有明确释放路径。
- 协程必须绑定生命周期或明确取消。
- 网络、编码、DLNA 控制失败必须返回明确错误。

### 8.2 UI 要求

页面至少包含：

```text
首页 / 投屏控制页：
- 当前状态
- 搜索设备按钮
- 设备列表
- 开始投屏按钮
- 停止投屏按钮
- 当前流地址
- 参数展示
- 错误提示

测试信息页：
- 分辨率
- 码率
- 延迟
- 设备
- 日志入口说明
- 无电视测试步骤
```

UI 原则：

- 简洁、稳定、不要花哨。
- 重点突出“这是可演示技术 Demo”。
- 状态提示必须明确。
- 不能让用户不知道现在卡在哪一步。

---

## 9. Git 分支、PR 与版本发布规范

当前仓库是公开仓库，必须保证每个阶段都可以回退和下载。

### 9.1 分支命名

使用以下格式：

```text
feat/001-project-bootstrap
feat/002-dlna-discovery
feat/003-screen-capture
feat/004-video-encoder
feat/005-local-stream-server
feat/006-dlna-control
feat/007-test-report-release
fix/xxx-short-description
docs/xxx-short-description
```

### 9.2 Commit 规范

使用简洁的 Conventional Commits 风格：

```text
chore: initialize android project
feat(dlna): add ssdp discovery client
feat(capture): add media projection flow
feat(stream): expose local live stream url
test(dlna): add soap request builder tests
docs: add no-tv testing guide
fix(encoder): release codec safely on stop
```

一次 commit 只做一类事情。

### 9.3 PR 规范

每个 PR 必须小而清晰。

PR 标题格式：

```text
feat(dlna): add SSDP renderer discovery
```

PR 描述模板：

```markdown
## 本次改动

- 

## 使用的 Skills

- android-architecture：
- android-viewmodel：
- kotlin-concurrency-expert：
- android-testing：
- android-emulator-skill：
- gradle-build-performance：
- compose-ui：

## 影响范围

- 

## 测试结果

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

结果：

```text
PASS / FAIL
```

## 手动测试

- 设备：
- Android 版本：
- 电脑系统：
- 接收端软件：
- 测试步骤：
- 结果：

## 开源参考

- 参考资料：
- 参考内容：
- 是否复制代码：否

## 已知问题

- 
```

### 9.4 合并规则

- 不允许直接把大改动推到 `main`。
- 每个功能通过 PR 合并。
- 合并前至少完成本地构建和单元测试。
- `main` 分支必须长期保持可编译。
- 不允许 force push 到 `main`。
- 不允许删除已发布 tag。

### 9.5 版本号与 Release

采用阶段版本号：

```text
v0.1.0-bootstrap
v0.2.0-dlna-discovery
v0.3.0-screen-capture
v0.4.0-encoder-stream
v0.5.0-dlna-control
v1.0.0-demo
```

每次阶段完成后：

```bash
git tag v0.1.0-bootstrap
git push origin v0.1.0-bootstrap
```

然后在 GitHub Releases 创建发布，至少包含：

```text
Release 标题：
v0.1.0-bootstrap

Release 内容：
- 本版本完成内容
- APK 下载
- 测试命令
- 已知问题
- 下一步计划
```

每个 Release 必须上传对应 APK：

```text
app-debug.apk
```

如果是最终演示版，文件名建议：

```text
DLNAScreenCastDemo-v1.0.0-demo.apk
```

目标：任何人打开 GitHub Releases，都能下载对应阶段 APK。

---

## 10. 最小验收标准

最终 Demo 至少满足：

1. App 能安装和启动。
2. App 能搜索局域网 DLNA / UPnP Renderer。
3. App 能申请屏幕采集权限。
4. App 能展示目标编码参数：1080P、8Mbps、AAC 128Kbps。
5. App 能启动本地流服务并显示 URL。
6. 电脑端能用 ffplay 或其他播放器验证流可访问。
7. App 能向 DLNA Renderer 发送播放控制请求。
8. README 写清楚没有电视时怎么测试。
9. Release 中有 APK 可下载。
10. 已知问题必须如实说明。

---

## 11. README 必须包含的内容

README 至少包括：

```text
项目简介
技术目标
技术架构图
功能列表
运行环境
如何构建
如何安装 APK
如何无电视测试
如何使用 Kodi / ffplay 测试
技术指标测试方法
当前完成度
已知问题
开源参考声明
截图 / 录屏
Release 下载地址
```

---

## 12. AI Agent 每次任务完成前的自检清单

提交前必须检查：

```text
[ ] 是否调用了相关 skills？
[ ] 是否说明了使用哪些 skills？
[ ] 是否没有复制开源代码？
[ ] 是否能编译？
[ ] 是否补充或更新测试？
[ ] 是否更新 README 或测试说明？
[ ] 是否有清晰错误处理？
[ ] 是否释放 MediaProjection / Encoder / StreamServer 资源？
[ ] 是否没有把伪结果写成已完成？
[ ] 是否准备好 PR 描述？
```

---

## 13. 给 Codex 的执行原则

1. 先读 `AGENTS.md`，再读 README，再看代码。
2. 先计划，后编码。
3. 每次只做一个阶段。
4. 小步提交，不要一次性写完整投屏系统。
5. 遇到 Android 权限、DLNA 兼容、音频采集限制时，必须先解释限制，再给可执行替代方案。
6. 不要为了看起来完成而隐藏失败。
7. Demo 项目最重要的是：能跑、能演示、能解释、能测试、能下载。

---

## 14. 推荐开发顺序给 Codex

请按以下顺序创建 PR：

```text
PR 1：初始化 Android 项目和基础 README
PR 2：实现 DLNA / UPnP 设备发现
PR 3：实现 MediaProjection 权限和采集状态
PR 4：实现 H.264 编码配置和参数展示
PR 5：实现本地 HTTP 流服务和 PC 播放测试
PR 6：实现 DLNA AVTransport 控制
PR 7：补充测试、截图、README、Release APK
```

每个 PR 完成后，都必须能运行，不允许“等后面 PR 再修”。

---

## 15. 最终交付物

最终至少交付：

```text
1. GitHub 仓库源码
2. README.md
3. AGENTS.md
4. Debug APK 或 Demo APK
5. GitHub Release
6. 测试说明
7. 演示截图或录屏
8. 已知问题说明
```

如果某项没有完成，必须明确写：

```text
未完成项：
原因：
后续怎么补：
```
