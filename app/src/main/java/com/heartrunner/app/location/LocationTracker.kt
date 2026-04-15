package com.heartrunner.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTracker(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun startTracking() {
        // 获取最后已知位置作为初始值
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            _currentLocation.value = lastKnown
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _currentLocation.value = location
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // GPS 定位
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            1f,
            locationListener!!,
            Looper.getMainLooper()
        )

        // 网络定位作为补充（模拟器和室内）
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener!!,
                    Looper.getMainLooper()
                )
            }
        } catch (_: Exception) {}
    }

    fun stopTracking() {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
        _currentLocation.value = null
    }
}
