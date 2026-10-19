package com.safemesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onDeviceDiscovered: (device: BluetoothDevice, nodeId: String, rssi: Int) -> Unit
) {
    private val tag = "BleScanner"
    private var scanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanningError = MutableStateFlow<String?>(null)
    val scanningError: StateFlow<String?> = _scanningError.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val record = result.scanRecord

            // Extract Node ID from service data if available, or derive from address
            var nodeId = device.address
            val serviceData = record?.getServiceData(ParcelUuid(BluetoothConstants.SAFEMESH_SERVICE_UUID))
            if (serviceData != null && serviceData.isNotEmpty()) {
                nodeId = String(serviceData, StandardCharsets.US_ASCII).trim()
            }

            onDeviceDiscovered(device, nodeId, result.rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration for scan failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal BLE scan error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported"
                else -> "Scan failed with code $errorCode"
            }
            Log.e(tag, "Scan failed: $errorMsg")
            _isScanning.value = false
            _scanningError.value = errorMsg
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(lowPowerMode: Boolean = false) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _scanningError.value = "Bluetooth is disabled"
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _scanningError.value = "BLE Scanner unavailable"
            return
        }

        val scanMode = if (lowPowerMode) {
            ScanSettings.SCAN_MODE_LOW_POWER
        } else {
            ScanSettings.SCAN_MODE_BALANCED
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BluetoothConstants.SAFEMESH_SERVICE_UUID))
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            _scanningError.value = null
            Log.d(tag, "Started BLE scan for SafeMesh service")
        } catch (e: SecurityException) {
            _scanningError.value = "Bluetooth scan permission denied"
            Log.e(tag, "SecurityException starting BLE scan", e)
        } catch (e: Exception) {
            _scanningError.value = "Failed to start BLE scan: ${e.message}"
            Log.e(tag, "Error starting scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            if (_isScanning.value) {
                scanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping scan", e)
        } finally {
            _isScanning.value = false
        }
    }
}
