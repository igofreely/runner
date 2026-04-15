# HeartRunner

一款基于 BLE（低功耗蓝牙）的实时心率监测 Android 应用，专为跑步运动设计。连接蓝牙心率带，实时追踪心率数据，并通过语音播报提醒你保持在目标心率区间内。

## 功能特性

- **BLE 心率设备连接** — 自动扫描并连接符合标准蓝牙心率协议（UUID 180D）的心率带
- **实时心率监测** — 实时显示当前心率，支持 UINT8/UINT16 数据格式
- **心率区间管理** — 可配置最大心率与目标区间百分比，心率超出区间时自动提醒
- **语音播报** — 基于 TTS 的中文语音提醒，包括定时心率播报与区间越界警告
- **心率趋势图** — Canvas 绘制的折线图，展示最近 60 次心率数据走势
- **运动记录** — 记录心率、GPS 轨迹、速度、海拔等运动数据
- **数据导出** — 支持导出 GPX 和 TCX 格式的运动文件，可分享到第三方平台
- **前台服务** — 息屏后保持蓝牙连接与心率监测，通知栏实时显示当前心率
- **Material Design 3** — 支持动态取色（Android 12+）和深色模式

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 蓝牙 | Android BLE API |
| 异步 | Kotlin Coroutines & Flow |
| 语音 | Android TextToSpeech |
| 权限 | Accompanist Permissions |
| 最低版本 | Android 8.0（API 26） |
| 目标版本 | Android 16（API 36） |

## 项目结构

```
com.heartrunner.app
├── HeartRunnerApp.kt              # Application 初始化，创建通知渠道
├── ble/
│   └── BleHeartRateManager.kt     # BLE 扫描、连接、心率数据解析
├── data/
│   └── WorkoutData.kt             # 运动数据模型（轨迹点）
├── export/
│   └── WorkoutExporter.kt         # GPX / TCX 格式导出
├── location/
│   └── LocationTracker.kt         # GPS 定位与轨迹跟踪
├── service/
│   └── HeartRateService.kt        # 前台服务，息屏保活
├── tts/
│   ├── HeartRateAlertLogic.kt     # 心率区间判断与警报逻辑
│   └── TtsAlertManager.kt         # TTS 语音播报管理
└── ui/
    ├── MainActivity.kt            # Activity 入口
    ├── MainViewModel.kt           # 状态管理与业务编排
    ├── HeartRunnerScreen.kt       # Compose UI 界面
    └── theme/
        └── Theme.kt               # Material 3 主题配置
```

## 构建与运行

### 环境要求

- Android Studio Ladybug 或更高版本
- JDK 21
- Android SDK 36

### 构建

```bash
./gradlew assembleDebug
```

### 安装到设备

```bash
./gradlew installDebug
```

## 运行时权限

应用需要以下权限：

- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`（Android 12+）
- `ACCESS_FINE_LOCATION`（用于 BLE 扫描）
- `POST_NOTIFICATIONS`（Android 13+，前台服务通知）

## 使用方式

1. 打开应用，授予蓝牙和位置权限
2. 点击扫描，选择你的心率带设备
3. 连接成功后，实时心率将显示在主界面
4. 点击设置图标，配置最大心率和目标区间
5. 开启语音播报，应用将自动提醒心率状态

## 许可证

MIT License
