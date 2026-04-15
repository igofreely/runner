package com.heartrunner.app.tts

/**
 * 心率区间警报逻辑
 * 参考原项目：基于最大心率和区间百分比判断当前心率是否过高/过低
 */
class HeartRateAlertLogic {

    data class AlertConfig(
        val maxHeartRate: Int = 190,
        val zoneLowPercent: Int = 70,
        val zoneHighPercent: Int = 80,
        // 心率播报间隔（秒）
        val heartRateReportInterval: Int = 30,
        // 区间报警间隔（秒）
        val zoneAlertInterval: Int = 5
    ) {
        val zoneLowBpm: Int get() = maxHeartRate * zoneLowPercent / 100
        val zoneHighBpm: Int get() = maxHeartRate * zoneHighPercent / 100

        fun isValid(): Boolean {
            return maxHeartRate in 140..220 &&
                    zoneLowPercent in 40..100 &&
                    zoneHighPercent in 40..100 &&
                    zoneLowPercent < zoneHighPercent
        }
    }

    sealed class AlertResult {
        data class HeartRateReport(val bpm: Int) : AlertResult()
        data class ZoneTooLow(val bpm: Int, val threshold: Int) : AlertResult()
        data class ZoneTooHigh(val bpm: Int, val threshold: Int) : AlertResult()
        data class ZoneChanged(val zone: Int) : AlertResult()
        object None : AlertResult()
    }

    private var lastHeartRateReportTime = 0L
    private var lastZoneAlertTime = 0L
    private var currentZone = -1

    fun evaluate(bpm: Int, config: AlertConfig, currentTimeMillis: Long): List<AlertResult> {
        if (bpm <= 0) return listOf(AlertResult.None)

        val results = mutableListOf<AlertResult>()
        val now = currentTimeMillis

        // 定时心率播报
        if (now - lastHeartRateReportTime >= config.heartRateReportInterval * 1000L) {
            results.add(AlertResult.HeartRateReport(bpm))
            lastHeartRateReportTime = now
        }

        // 区间告警
        if (now - lastZoneAlertTime >= config.zoneAlertInterval * 1000L) {
            if (config.isValid()) {
                // 有有效配置时，检查是否超出区间
                when {
                    bpm < config.zoneLowBpm -> {
                        results.add(AlertResult.ZoneTooLow(bpm, config.zoneLowBpm))
                    }
                    bpm > config.zoneHighBpm -> {
                        results.add(AlertResult.ZoneTooHigh(bpm, config.zoneHighBpm))
                    }
                }
            } else {
                // 无有效配置时，播报心率区间变化
                val zone = bpm * 10 / 190 - 2
                if (zone != currentZone) {
                    currentZone = zone
                    results.add(AlertResult.ZoneChanged(zone))
                }
            }
            lastZoneAlertTime = now
        }

        return results.ifEmpty { listOf(AlertResult.None) }
    }

    fun reset() {
        lastHeartRateReportTime = 0L
        lastZoneAlertTime = 0L
        currentZone = -1
    }
}
