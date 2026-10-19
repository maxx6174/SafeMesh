package com.safemesh.bluetooth

import java.util.UUID

object BluetoothConstants {
    // SafeMesh 128-bit UUIDs for BLE GATT Service & Characteristics
    val SAFEMESH_SERVICE_UUID: UUID = UUID.fromString("0000fe60-0000-1000-8000-00805f9b34fb")
    val CHAR_WRITE_CHUNK_UUID: UUID = UUID.fromString("0000fe61-0000-1000-8000-00805f9b34fb")
    val CHAR_ACK_NOTIFY_UUID: UUID = UUID.fromString("0000fe62-0000-1000-8000-00805f9b34fb")
    val CHAR_NODE_INFO_UUID: UUID = UUID.fromString("0000fe63-0000-1000-8000-00805f9b34fb")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val REQUESTED_MTU = 512
    const val DEFAULT_BLE_MTU = 23
    const val GATT_OPERATION_TIMEOUT_MS = 6000L
    const val MAX_TRANSFER_RETRIES = 3
}
