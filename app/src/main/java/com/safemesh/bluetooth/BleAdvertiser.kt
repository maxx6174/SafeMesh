package com.safemesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

class BleAdvertiser(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val localNodeId: String
) {
    private val tag = "BleAdvertiser"
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _advertisingError = MutableStateFlow<String?>(null)
    val advertisingError: StateFlow<String?> = _advertisingError.asStateFlow()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(tag, "BLE Advertisement successfully started: $settingsInEffect")
            _isAdvertising.value = true
            _advertisingError.value = null
        }

        override fun onStartFailure(errorCode: Int) {
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertisement already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertisement data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE Advertising unsupported on this device"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal BLE advertising error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many active BLE advertisers"
                else -> "BLE Advertising failed with code $errorCode"
            }
            Log.e(tag, "BLE Advertising failed: $errorMsg")
            _isAdvertising.value = false
            _advertisingError.value = errorMsg
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(lowPowerMode: Boolean = false) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _advertisingError.value = "Bluetooth is disabled"
            return
        }

        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            _advertisingError.value = "Multiple advertisement unsupported on this hardware"
            Log.w(tag, "Multiple advertisement unsupported")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _advertisingError.value = "BLE Advertiser unavailable"
            return
        }

        val advertiseMode = if (lowPowerMode) {
            AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        } else {
            AdvertiseSettings.ADVERTISE_MODE_BALANCED
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(advertiseMode)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely until explicitly stopped
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Service data includes compact 8-char node identifier
        val nodeBytes = localNodeId.take(8).toByteArray(StandardCharsets.US_ASCII)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BluetoothConstants.SAFEMESH_SERVICE_UUID))
            .addServiceData(ParcelUuid(BluetoothConstants.SAFEMESH_SERVICE_UUID), nodeBytes)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            _advertisingError.value = "Bluetooth advertise permission denied"
            Log.e(tag, "SecurityException starting advertisement", e)
        } catch (e: Exception) {
            _advertisingError.value = "Failed to start BLE advertisement: ${e.message}"
            Log.e(tag, "Error starting advertisement", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            if (_isAdvertising.value) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping advertisement", e)
        } finally {
            _isAdvertising.value = false
        }
    }
}
