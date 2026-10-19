package com.safemesh.data.database

import androidx.room.TypeConverter
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.MessageType
import com.safemesh.model.PeerConnectionState
import com.safemesh.model.TransferDirection

class Converters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)

    @TypeConverter
    fun fromPeerConnectionState(value: PeerConnectionState): String = value.name

    @TypeConverter
    fun toPeerConnectionState(value: String): PeerConnectionState = PeerConnectionState.valueOf(value)

    @TypeConverter
    fun fromTransferDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toTransferDirection(value: String): TransferDirection = TransferDirection.valueOf(value)
}
