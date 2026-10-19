package com.safemesh.model

enum class MessageType {
    EMERGENCY_SOS,
    STANDARD_MESSAGE,
    DELIVERY_ACK,
    RELAY_ACK
}

enum class DeliveryStatus(val displayLabel: String) {
    SAVED_LOCAL("Saved on this device"),
    SEARCHING("Searching for participating devices"),
    TRANSFERRING("Transferring to a nearby device"),
    RELAYED("Stored on a relay device"),
    FORWARDED("Forwarded to additional devices"),
    DELIVERED("Delivered to the intended recipient"),
    EXPIRED("Expired"),
    FAILED("Delivery failed or requires attention")
}

enum class PeerConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

enum class TransferDirection {
    OUTGOING,
    INCOMING
}
