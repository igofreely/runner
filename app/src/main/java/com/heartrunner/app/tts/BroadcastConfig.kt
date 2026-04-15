package com.heartrunner.app.tts

/**
 * 运动播报配置
 * 独立于心率区间告警，支持按距离或时间间隔播报
 */
data class BroadcastConfig(
    // 播报开关
    val enabled: Boolean = true,
    // 触发方式（可同时启用）
    val triggerByDistance: Boolean = true,
    val triggerByTime: Boolean = true,
    // 距离间隔（公里）
    val distanceIntervalKm: Double = 1.0,
    // 时间间隔（分钟）
    val timeIntervalMin: Int = 5,
    // 各项播报开关
    val announceSpeed: Boolean = true,
    val announceDistance: Boolean = true,
    val announceTime: Boolean = true,
    val announceHeartRate: Boolean = true,
    val announceAvgHeartRate: Boolean = false,
    val announceAvgSpeed: Boolean = false
)
