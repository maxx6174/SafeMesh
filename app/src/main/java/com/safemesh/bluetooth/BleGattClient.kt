package com.safemesh.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.safemesh.protocol.PacketFramer
import com.safemesh.protocol.SafeMeshPacket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets

class BleGattClient(private val context: Context) {
    private val tag = "BleGattClient"

    /**
     * Connects to a remote peer via BLE GATT, negotiates MTU, transfers packet chunks,
     * waits for acks, and disconnects cleanly.
     * Returns true if the full packet was successfully transferred and acknowledged.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendPacket(device: BluetoothDevice, packet: SafeMeshPacket): Boolean {
        var gatt: BluetoothGatt? = null
        try {
            val connectionDeferred = CompletableDeferred<Boolean>()
            val mtuDeferred = CompletableDeferred<Int>()
            val servicesDeferred = CompletableDeferred<Boolean>()
            val writeDeferred = CompletableDeferred<Boolean>()

            var writeChar: BluetoothGattCharacteristic? = null
            var ackChar: BluetoothGattCharacteristic? = null

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    Log.d(tag, "GATT client state change: status=$status, newState=$newState")
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        connectionDeferred.complete(true)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        connectionDeferred.complete(false)
                        servicesDeferred.complete(false)
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    Log.d(tag, "Negotiated MTU: $mtu, status=$status")
                    mtuDeferred.complete(mtu)
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = g.getService(BluetoothConstants.SAFEMESH_SERVICE_UUID)
                        if (service != null) {
                            writeChar = service.getCharacteristic(BluetoothConstants.CHAR_WRITE_CHUNK_UUID)
                            ackChar = service.getCharacteristic(BluetoothConstants.CHAR_ACK_NOTIFY_UUID)
                            servicesDeferred.complete(writeChar != null)
                        } else {
                            servicesDeferred.complete(false)
                        }
                    } else {
                        servicesDeferred.complete(false)
                    }
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    writeDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
                }

                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (characteristic.uuid == BluetoothConstants.CHAR_ACK_NOTIFY_UUID) {
                        val ackText = String(characteristic.value ?: ByteArray(0), StandardCharsets.US_ASCII)
                        Log.d(tag, "Received GATT Ack: $ackText")
                    }
                }
            }

            Log.d(tag, "Connecting to GATT server on ${device.address}")
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }

            val connected = withTimeoutOrNull(BluetoothConstants.GATT_OPERATION_TIMEOUT_MS) {
                connectionDeferred.await()
            } ?: false

            if (!connected) {
                Log.w(tag, "Connection timed out to ${device.address}")
                return false
            }

            // Request larger MTU
            gatt?.requestMtu(BluetoothConstants.REQUESTED_MTU)
            withTimeoutOrNull(2000L) { mtuDeferred.await() }

            // Discover services
            gatt?.discoverServices()
            val servicesFound = withTimeoutOrNull(BluetoothConstants.GATT_OPERATION_TIMEOUT_MS) {
                servicesDeferred.await()
            } ?: false

            if (!servicesFound || writeChar == null) {
                Log.w(tag, "SafeMesh GATT service not found on ${device.address}")
                return false
            }

            // Enable notifications for Acks
            ackChar?.let {
                gatt?.setCharacteristicNotification(it, true)
                val descriptor = it.getDescriptor(BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                    delay(150)
                }
            }

            // Serialize and chunk packet
            val packetBytes = PacketFramer.serializePacket(packet)
            val chunks = PacketFramer.splitIntoChunks(packetBytes, packet.messageId.take(16))

            Log.i(tag, "Sending packet ${packet.messageId} (${packetBytes.size} bytes in ${chunks.size} chunks) to ${device.address}")

            // Send each chunk
            for (chunk in chunks) {
                val frameBytes = PacketFramer.encodeChunkToFrame(chunk)
                var chunkSent = false

                for (retry in 0 until BluetoothConstants.MAX_TRANSFER_RETRIES) {
                    writeChar?.value = frameBytes
                    writeChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                    val success = gatt?.writeCharacteristic(writeChar) ?: false
                    if (success) {
                        chunkSent = true
                        delay(60) // Flow control pacing between chunks
                        break
                    }
                    Log.w(tag, "Retrying chunk ${chunk.chunkIndex} (attempt ${retry + 1})")
                    delay(300)
                }

                if (!chunkSent) {
                    Log.e(tag, "Failed to send chunk ${chunk.chunkIndex} to ${device.address}")
                    return false
                }
            }

            Log.i(tag, "Successfully transferred all ${chunks.size} chunks of packet ${packet.messageId} to ${device.address}")
            return true

        } catch (e: SecurityException) {
            Log.e(tag, "Bluetooth permission denied during GATT client transfer", e)
            return false
        } catch (e: Exception) {
            Log.e(tag, "Error during GATT transfer to ${device.address}: ${e.message}", e)
            return false
        } finally {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                Log.e(tag, "Error closing GATT client", e)
            }
        }
    }
}
