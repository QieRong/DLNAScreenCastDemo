# feat(dlna): add SSDP renderer discovery

## 本次改动

- 将应用包名统一修正为 `com.qierong.dlnascreencastdemo`。
- 使用原生 UDP 实现 SSDP `M-SEARCH`，仅搜索 `MediaRenderer:1` 和 `ssdp:all`。
- 安全拉取并解析 Renderer 描述 XML，展示设备名称、厂商、型号、IP、描述地址和 AVTransport 状态。
- 兼容 Android 不支持部分 JAXP XML 附加防护配置的情况，同时保留 `DOCTYPE` 和外部实体拒绝策略。
- 增加 Wi-Fi 状态判断、Android 13+ `NEARBY_WIFI_DEVICES` 权限请求和中文空状态排查说明。

## 使用的 Skills

- `android-architecture`：保持 `dlna`、repository、ViewModel、UI 边界清晰。
- `android-viewmodel`：使用只读 `StateFlow` 暴露设备列表状态。
- `kotlin-concurrency-expert`：使用结构化协程、IO dispatcher、取消传播和 `MulticastLock` 释放。
- `android-testing`：覆盖解析器、HTTP 安全限制、repository 和 ViewModel 状态。
- `android-emulator-skill`：检查 ADB 设备状态，准备手动冒烟测试路径。
- `compose-ui`：使用状态提升和中文空状态页面。

## 影响范围

- 包名和 Android Manifest 网络权限。
- DLNA / UPnP SSDP Renderer 发现。
- 首页设备列表和搜索状态。
- README 无电视测试说明。

## 测试结果

提交前运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

结果：`PASS`。`assembleDebug` 已生成 Debug APK，`testDebugUnitTest` 已通过 24 个测试，`lintDebug` 已通过。

已尝试运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

结果：`FAIL`。小米系统拦截测试 APK 安装，返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，实际执行 `0 tests`。本 PR 不将其记录为已通过。

## 手动测试

- 设备：`23127PN0CC`
- Android 版本：`16`，API `36`
- 网络环境：Windows 电脑热点，手机连接热点。
- 接收端软件：Kodi，运行于热点电脑。
- 页面结果：显示 `Kodi (SK-20220818ZFPP)`，IP 为 `192.168.137.1`，并解析出 AVTransport 控制地址。
- logcat 结果：`设备搜索结束：发现 1 个 Renderer`
- 截图：`docs/screenshots/pr2-kodi-renderer-redacted.jpg`
- 结论：**局域网 Renderer 发现真机验收通过**。

## 本 PR 不实现

- `MediaProjection`
- H.264 编码
- 本地流服务
- DLNA 播放控制

`1080P`、`8 Mbps`、`AAC 128 Kbps`、延迟 `< 2 秒` 均为目标指标，当前未实测。

## 开源参考

- 参考资料：UPnP Device Architecture、Android 官方附近 Wi-Fi 设备权限和本地网络权限文档。
- 参考内容：SSDP 搜索、设备描述解析、局域网权限迁移边界。
- 本项目自己的实现差异：使用最小原生实现，不接入第三方 DLNA 库或乐播 SDK。
- 是否复制代码：否。

## 已知问题

- 防火墙、路由器 AP 隔离和不同 Renderer 实现可能影响发现结果。
- 小米系统拦截 instrumentation 测试 APK 安装，`connectedDebugAndroidTest` 尚未通过。
- Android 17、`targetSdk 37+` 的 `ACCESS_LOCAL_NETWORK` 迁移留给后续兼容性 PR。
