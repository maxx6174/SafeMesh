package com.safemesh.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long,
    val isLastKnown: Boolean
)

class SafeMeshLocationProvider(private val context: Context) {
    private val tag = "LocationProvider"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<LocationInfo?>(null)
    val currentLocation: StateFlow<LocationInfo?> = _currentLocation.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    init {
        checkProviderStatus()
    }

    fun updatePermissionStatus(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            fetchLastKnownLocation()
        }
    }

    fun checkProviderStatus() {
        val gps = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val net = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        _isGpsEnabled.value = gps || net
    }

    @SuppressLint("MissingPermission")
    fun fetchLastKnownLocation(): LocationInfo? {
        if (!_hasPermission.value || locationManager == null) return null

        try {
            var bestLocation: Location? = null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            if (bestLocation != null) {
                val info = LocationInfo(
                    latitude = bestLocation.latitude,
                    longitude = bestLocation.longitude,
                    accuracyMeters = if (bestLocation.hasAccuracy()) bestLocation.accuracy else null,
                    timestamp = bestLocation.time,
                    isLastKnown = true
                )
                _currentLocation.value = info
                return info
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Location permission missing", e)
        } catch (e: Exception) {
            Log.e(tag, "Error obtaining last known location", e)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun requestSingleUpdate(onResult: (LocationInfo?) -> Unit) {
        if (!_hasPermission.value || locationManager == null) {
            onResult(fetchLastKnownLocation())
            return
        }

        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val info = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        timestamp = location.time,
                        isLastKnown = false
                    )
                    _currentLocation.value = info
                    locationManager.removeUpdates(this)
                    onResult(info)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                null
            }

            if (provider != null) {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } else {
                onResult(fetchLastKnownLocation())
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Location permission denied", e)
            onResult(null)
        } catch (e: Exception) {
            Log.e(tag, "Error requesting location update", e)
            onResult(fetchLastKnownLocation())
        }
    }
}
