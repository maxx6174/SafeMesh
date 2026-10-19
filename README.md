# SafeMesh: Decentralized Offline Emergency SOS Mesh for Android

**SafeMesh** is an offline-first emergency SOS communication application designed for catastrophic scenarios where cellular towers, power grids, and internet backends are inoperable. SafeMesh enables participating Android devices to establish opportunistic peer-to-peer Bluetooth Low Energy (BLE) meshes, discover nearby survivors and first responders, and relay encrypted emergency SOS messages using **store-carry-forward epidemic networking**.

---

## 1. Key Architectural Features

1. **Zero Cloud / Backend Dependency**:
   - No servers, no accounts, no Firebase, no APIs, and no internet connection required.
   - Operates 100% offline using decentralized Bluetooth Low Energy (BLE) and local device storage.
2. **True Bluetooth Mesh Data Transfer**:
   - Uses BLE Advertising & Scanning for nearby node discovery.
   - Implements BLE GATT Server & Client connections with MTU negotiation (up to 512 bytes), binary packet chunk framing (`SMSH` magic bytes), CRC32 integrity verification, chunk acknowledgements, and automatic retry mechanisms.
3. **Store-Carry-Forward Opportunistic Relaying**:
   - Packets that cannot immediately reach an intended recipient or gateway are saved in Room SQLite database.
   - When a node moves and discovers another participating device, it opportunistically transfers pending packets.
   - Prevents looping with a persistent duplicate cache and hop-limit decrements (`hopCount < hopLimit`).
4. **Emergency Prioritization**:
   - Emergency SOS packets are strictly prioritized over standard messages in the transmission queue.
5. **Robust Cryptography**:
   - **NIST P-256 (secp256r1) Elliptic Curve Key Pairs**: Self-sovereign cryptographic identity generated locally without central certificate authorities.
   - **ECDH + AES-256-GCM (AEAD)**: Directed peer messages are end-to-end encrypted. Intermediary relays cannot read payload contents.
   - **HMAC-SHA256**: Authenticated transport envelope prevents packet tampering while permitting intermediate nodes to inspect routing metadata (Hop Count, Expiry, Message ID).
6. **Pure Offline GPS**:
   - Uses Android `LocationManager` (GPS provider) with zero Google Play Services / cloud dependencies.
   - Accurately reports accuracy, timestamp, and distinguishes live GPS fixes from last-known locations. Never fabricates coordinates.
7. **Emergency-Focused Accessible UI**:
   - Material 3 dark navy theme with high-contrast text.
   - 2-second press-and-hold Emergency SOS button with animated progress ring to prevent accidental activation.
   - 3-second cancellation window before transmission.
   - Visual multi-hop delivery stepper distinguishing intermediate relays from confirmed recipient delivery.
   - Clearly labeled Demonstration Mode for testing without a second physical phone.

---

## 2. Technology Stack

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose & Material 3
- **Architecture**: Clean MVVM (Model-View-ViewModel) with Service Locator / Dependency Container
- **Persistence**: Room Database with SQLite & TypeConverters
- **Reactive Streams**: Kotlin Coroutines & StateFlow
- **Networking**: Android Bluetooth APIs (BLE Scanner, BLE Advertiser, GATT Server, GATT Client)
- **Location**: Android LocationManager (GPS & Network Provider)
- **Cryptography**: Standard Java Cryptography Architecture (JCA) NIST P-256 EC, ECDH, AES-256-GCM, HMAC-SHA256
- **Testing**: JUnit 4, Kotlinx Coroutines Test

---

## 3. Project Structure

```
SafeMesh/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── java/com/safemesh/
│   │   │       ├── SafeMeshApplication.kt        // Application container & singletons
│   │   │       ├── MainActivity.kt               // Runtime permissions & Compose entry
│   │   │       ├── model/
│   │   │       │   └── Enums.kt                  // MessageType, DeliveryStatus, etc.
│   │   │       ├── crypto/
│   │   │       │   └── CryptoManager.kt          // ECDH, AES-GCM, HMAC, KeyGen
│   │   │       ├── protocol/
│   │   │       │   ├── ProtocolModels.kt         // SafeMeshPacket, MeshChunk, ChunkAck
│   │   │       │   └── PacketFramer.kt           // Framing, CRC32, chunking, serialization
│   │   │       ├── data/
│   │   │       │   ├── database/
│   │   │       │   │   ├── Entities.kt           // MessageEntity, PeerEntity, TransferRecord
│   │   │       │   │   ├── Converters.kt         // Room TypeConverters
│   │   │       │   │   ├── SafeMeshDaos.kt       // DAOs for messages, peers, transfers
│   │   │       │   │   └── SafeMeshDatabase.kt   // Room database
│   │   │       │   └── repository/
│   │   │       │       ├── MessageRepository.kt  // Message store-carry-forward logic
│   │   │       │       └── PeerRepository.kt     // Discovered peers & demo simulation
│   │   │       ├── bluetooth/
│   │   │       │   ├── BluetoothConstants.kt     // GATT Service & Characteristic UUIDs
│   │   │       │   ├── BleAdvertiser.kt          // BLE Advertising
│   │   │       │   ├── BleScanner.kt             // BLE Scanning
│   │   │       │   ├── BleGattServer.kt          // GATT Server for receiving packets
│   │   │       │   ├── BleGattClient.kt          // GATT Client for sending packets
│   │   │       │   └── BluetoothMeshManager.kt   // Bluetooth orchestration & state
│   │   │       ├── location/
│   │   │       │   └── SafeMeshLocationProvider.kt // Android LocationManager
│   │   │       ├── routing/
│   │   │       │   ├── RoutingPolicy.kt          // Priority & eligibility rules
│   │   │       │   ├── DuplicateDetector.kt      // Cache for preventing loops
│   │   │       │   └── StoreCarryForwardEngine.kt// Periodic maintenance & queue order
│   │   │       ├── service/
│   │   │       │   ├── NotificationHelper.kt     // Foreground & emergency notifications
│   │   │       │   └── SafeMeshForegroundService.kt // connectedDevice service
│   │   │       └── ui/
│   │   │           ├── theme/                    // Color, Theme, Type
│   │   │           ├── navigation/               // Screen routes & NavGraph
│   │   │           ├── components/               // SOS Hold Button, StatusPill, Cards
│   │   │           └── screens/
│   │   │               ├── HomeScreen.kt
│   │   │               ├── EmergencySosScreen.kt
│   │   │               ├── EmergencyStatusScreen.kt
│   │   │               ├── NearbyDevicesScreen.kt
│   │   │               ├── MessagesScreen.kt
│   │   │               ├── MessageDetailScreen.kt
│   │   │               ├── ReceivedEmergencyScreen.kt
│   │   │               ├── LocationScreen.kt
│   │   │               └── SettingsScreen.kt
│   │   └── test/java/com/safemesh/
│   │       ├── SafeMeshPacketTest.kt             // Packet parsing, expiry, hop limit tests
│   │       ├── CryptoManagerTest.kt              // ECDH, AES-GCM, HMAC unit tests
│   │       ├── PacketFramerTest.kt               // Framing, CRC32, chunking tests
│   │       ├── RoutingPolicyTest.kt              // Priority & eligibility tests
│   │       └── DuplicateDetectorTest.kt          // Deduplication & cache purge tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 4. Physical Device Testing Procedures

### Test 1: Local SOS Creation
1. Turn off Wi-Fi and Mobile Data on Phone A.
2. Launch SafeMesh.
3. On the Home Screen, press and hold the **SOS** button for 2 seconds.
4. Select an emergency category (e.g. "Medical Emergency"), attach coordinates, and tap **Transmit Emergency SOS**.
5. Observe the 3-second cancellation countdown.
6. Verify the packet appears in the local database marked with status **Saved on this device** (`SAVED_LOCAL`).

### Test 2: Two-Device Direct Bluetooth Transfer
1. Install SafeMesh on Phone A and Phone B. Keep cellular data and Wi-Fi disabled on both phones.
2. Enable Bluetooth on both phones.
3. Phone A creates an Emergency SOS packet.
4. On Phone B, observe Phone A appears in **Nearby Devices**.
5. Phone A discovers Phone B and automatically initiates BLE GATT connection.
6. Phone A chunks the framed packet and writes to Phone B's GATT server.
7. Phone B receives all chunks, verifies the CRC32 checksum, reassembles the packet, decrypts the payload, and vibrates with an **Emergency SOS Alert**.
8. Phone B sends an acknowledgement; Phone A updates message status to **Stored on a relay device** (`RELAYED`).

### Test 3: Interrupted Transfer Handling
1. Start transferring an emergency message from Phone A to Phone B.
2. While transmitting, walk Phone B out of Bluetooth range (or toggle Bluetooth off briefly on Phone B).
3. Verify Phone A detects the GATT write failure, executes retries up to 3 times, reverts the packet state to `SAVED_LOCAL`, and remains in queue without corrupting data.
4. Bring Phone B back into range; observe transfer automatically resumes when Phone B is rediscovered.

### Test 4: Store-Carry-Forward (3-Phone Relay Test)
1. Set up Phone A, Phone B, and Phone C.
2. Phone A and Phone C are placed far apart (out of Bluetooth range, e.g. 100 meters).
3. Phone A transmits an Emergency SOS. Phone B (carrier node) receives and persists the message (`RELAYED`).
4. Carry Phone B away from Phone A toward Phone C.
5. When Phone B discovers Phone C, Phone B's store-carry-forward engine inspects pending packets and opportunistically transfers the SOS packet to Phone C.
6. Phone C receives, verifies, and stores the packet (`hopCount` incremented from 1 to 2).

### Test 5: End-to-End Recipient Delivery
1. Set up a directed message from Phone A targeted to Phone C's Node ID.
2. Relay message through Phone B.
3. Phone B stores the encrypted ciphertext without being able to decrypt it (end-to-end encrypted with Phone C's public key).
4. Phone C receives the packet from Phone B, decrypts the plaintext using Phone C's private key, and returns a `DELIVERY_ACK` packet through the mesh back to Phone A.
5. Phone A marks delivery status as **Delivered to the intended recipient** (`DELIVERED`).

### Test 6: Duplicate Prevention & Loop Immunity
1. Phone A sends Packet X to Phone B.
2. Phone B attempts to relay Packet X back to Phone A.
3. Phone A checks `DuplicateDetector` (dual memory and Room cache) and routing rules (`senderId == targetPeerId`), immediately rejecting the packet.
4. No infinite forwarding loops occur.

### Test 7: Message Expiration
1. Create a packet with an expiration time set to a past timestamp.
2. Attempt to parse or forward the packet.
3. Packet is immediately rejected by `PacketFramer` with `IllegalArgumentException: Packet has expired`, and marked as `EXPIRED` in Room.

### Test 8: No GPS Available
1. Disable Location Services in Android Settings.
2. Open SafeMesh and activate SOS.
3. SafeMesh gracefully falls back to creating the emergency packet without coordinates (`latitude = null`, `longitude = null`), correctly marking coordinates unavailable without failing.

### Test 9: Offline Operation (No Internet)
1. Turn on Airplane Mode with Bluetooth enabled.
2. Verify all operations (key generation, encryption, packet serialization, GATT transfer, Room persistence) function with zero network errors.

### Test 10: App Restart & Persistence
1. Create an undelivered emergency SOS message on Phone A.
2. Force close the SafeMesh application and restart the device.
3. Re-open SafeMesh; observe the message remains intact in the database with status `SAVED_LOCAL` and automatically resumes scanning.

---

## 5. Security & Privacy Model

- **No Public Identifiers in Advertisements**: Broadcast advertisements contain only the SafeMesh 128-bit Service UUID and an 8-character ephemeral node ID. No GPS coordinates, names, or emergency contents are included in discovery packets.
- **Transport Envelope vs. Payload Separation**: Intermediary relays only inspect the transport envelope metadata (Message ID, Hop Count, Expiration, Auth Signature) needed to make routing decisions.
- **Cryptographic Authenticity**: Every packet envelope is signed using HMAC-SHA256 with constant-time verification to prevent replay tampering.
- **Key Storage**: Private keys are held in memory and generated using Android Keystore / standard secure random seeds, never logged to logcat or stored in plain text files.
