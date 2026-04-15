# HeartRunner

一款基于 BLE（低功耗蓝牙）的实时心率监测 Android 应用，专为跑步运动设计。连接蓝牙心率带，实时追踪心率数据，并通过语音播报提醒你保持在目标心率区间内。支持无心率带独立记录 GPS 轨迹。

## 功能特性

- **BLE 心率设备连接** — 自动扫描并连接符合标准蓝牙心率协议（UUID 180D）的心率带
- **实时心率监测** — 实时显示当前心率，支持 UINT8/UINT16 数据格式
- **心率区间管理** — 可配置最大心率与目标区间百分比，心率超出区间时自动提醒
- **语音播报** — 基于 TTS 的中文语音提醒，包括定时心率播报与区间越界警告
- **运动播报** — 可配置按距离和/或时间间隔播报速度、里程、时间、心率、平均心率、平均速度，与心率区间告警独立且告警优先级更高
- **地图浮层操作** — 将开始/停止记录与心率带扫描按钮直接叠加在地图上，跑步中无需离开地图视图
- **高德地图** — 使用 osmdroid + 高德瓦片源，中文地图标注，实时显示位置和运动轨迹
- **地图全屏** — 点击全屏按钮可展开地图至全屏查看轨迹详情，并保留地图上的核心操作按钮
- **WGS-84/GCJ-02 坐标转换** — GPS 坐标自动转换为 GCJ-02，确保在高德地图上精确显示
- **独立运动记录** — 无需连接心率带即可开始记录 GPS 轨迹、速度、里程等数据
- **数据导出** — 支持导出 GPX 和 TCX 格式的运动文件，可分享到第三方平台
- **前台服务** — 录制时自动启动前台服务，息屏后保持 GPS 和蓝牙连接
- **Material Design 3** — 支持动态取色（Android 12+）和深色模式

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 地图 | osmdroid + 高德瓦片源 |
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
│   ├── CoordinateConverter.kt     # WGS-84 ↔ GCJ-02 坐标转换
│   └── LocationTracker.kt         # GPS + 网络定位与轨迹跟踪
├── service/
│   └── HeartRateService.kt        # 前台服务，息屏保活
├── tts/
│   ├── BroadcastConfig.kt         # 运动播报配置（距离/时间触发、播报内容）
│   ├── HeartRateAlertLogic.kt     # 心率区间判断与警报逻辑
│   └── TtsAlertManager.kt         # TTS 语音播报管理
└── ui/
    ├── MainActivity.kt            # Activity 入口
    ├── MainViewModel.kt           # 状态管理与业务编排
    ├── HeartRunnerScreen.kt       # Compose UI 界面（含高德地图）
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
- `ACCESS_FINE_LOCATION`（用于 BLE 扫描和 GPS 轨迹记录）
- `INTERNET` / `ACCESS_NETWORK_STATE`（加载高德地图瓦片）
- `POST_NOTIFICATIONS`（Android 13+，前台服务通知）

## 使用方式

1. 打开应用，授予蓝牙和位置权限
2. 在地图底部浮层点击「开始记录」直接开始 GPS 轨迹记录（无需心率带）
3. 如需心率监测，在地图底部浮层点击「扫描心率带」搜索并连接蓝牙心率设备
4. 地图实时显示当前位置和运动轨迹，点击全屏按钮可放大查看，核心操作按钮会继续固定在地图上
5. 点击设置图标，配置最大心率和目标区间
6. 开启语音播报，应用将自动提醒心率状态

## 许可证

MIT License
