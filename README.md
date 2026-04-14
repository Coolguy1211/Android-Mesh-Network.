# 📱 Android Mesh Network - Deep Technical Documentation

An advanced Android application that forms a high-performance, decentralized mesh network using **WiFi Direct**, **Bluetooth Low Energy (BLE)**, and **Classic Bluetooth**. This project enables offline communication and transparent, device-wide internet sharing through a mesh-routed VPN tunnel.

---

## 📊 Architecture & Data Flow

```text
                                  ┌────────────────────────┐
                                  │      THE INTERNET      │
                                  └───────────┬────────────┘
                                              │ (WiFi/Cellular)
                                              ▼
                                  ┌────────────────────────┐
                                  │      GATEWAY NODE      │
                                  │ (Internet Connection)  │◀─────┐
                                  └───────────┬────────────┘      │
                                              │                   │
                     ┌────────────────────────┴───────────────────┴────────────────────────┐
                     │                 NEARBY CONNECTIONS (P2P_CLUSTER)                     │
                     └─────────────┬───────────────────────────────┬────────────────────────┘
                                   │                               │
                      ┌────────────▼────────────┐     ┌────────────▼────────────┐
                      │      CLIENT NODE A      │     │      CLIENT NODE B      │
                      │   (Active VpnService)   │     │   (Active VpnService)   │
                      └────────────┬────────────┘     └────────────┬────────────┘
                                   │                               │
                                   └───────────────┬───────────────┘
                                                   ▼
                                         DECENTRALIZED MESH
                                     (Text / Files / IP Packets)
```

---

## 🛠 Deep Dive: Core Technologies

### 1. Mesh Formation (Google Nearby Connections)
The app uses the `STRATEGY_P2P_CLUSTER` topology, which is a peer-to-peer strategy that supports a many-to-many connection.
*   **Discovery**: Devices scan via BLE and Bluetooth to find peers.
*   **Bandwidth Upgrade**: Once a connection is established over Bluetooth, the API automatically negotiates a high-speed WiFi Direct (P2P) or WiFi Hotspot link for data transfer.
*   **Decentralization**: There is no single master. Any device can connect to any other device, forming a web of connections.

### 2. Transparent Internet Sharing (VPN Tunneling)
This is the most complex part of the application. It bypasses the need for tethering/hotspotting which is often blocked by carriers.
*   **The Client**: Runs an Android `VpnService`. This creates a virtual network interface (TUN) on the device. All system traffic (Chrome, YouTube, Spotify) is intercepted by our app as raw IP packets.
*   **The Tunnel**: The app reads these packets from the TUN interface, encapsulates them into `Payload.BYTES`, and sends them across the mesh to the Gateway.
*   **The Gateway**: Receives the raw IP packets. It must perform **User-space NAT (Network Address Translation)**. It opens standard TCP/UDP sockets to the real internet, forwards the data, and then encapsulates the responses to send back to the client.

### 3. Messaging & File System
*   **Texting**: Uses small byte payloads. Messages are broadcast to all connected endpoints in the cluster.
*   **Files**: Uses the `Payload.Type.FILE` system. This is extremely efficient because the Nearby API handles the streaming and avoids loading the entire file into memory.

---

## 📡 Networking Protocols

| Feature | Protocol | Hardware |
| :--- | :--- | :--- |
| **Discovery** | BLE / mDNS | Bluetooth / WiFi |
| **Mesh Link** | WiFi Direct / P2P | WiFi Radio |
| **VPN Tunnel** | Raw IPv4/IPv6 Packets | Virtual TUN Interface |
| **Control Plane** | Nearby Connections API | System Service |

---

## ⚙️ Detailed Setup & Build Instructions

### Prerequisites
*   Android Studio Ladybug (or newer)
*   JDK 17
*   At least two physical Android devices (Emulators do not support Bluetooth/WiFi Direct)

### GitHub Actions Build (Recommended)
This project is optimized for cloud builds to save local resources:
1.  Fork this repository.
2.  Navigate to **Actions**.
3.  The workflow `Build Android APK` runs on every push.
4.  Download the result from the **Artifacts** section of the latest run.

### Local CLI Build
If you have at least 8GB of RAM, you can build locally:
```powershell
./gradlew assembleDebug
```

---

## 🔒 Security Considerations

*   **Encryption**: Nearby Connections uses TLS encryption for all payloads.
*   **Sandboxing**: The `VpnService` ensures that traffic is isolated to the virtual interface.
*   **Privacy**: No data is stored on our servers; the mesh is entirely peer-to-peer.

---

## 🚧 Future Roadmap (Phases 6-7)

-   [*] **Multi-hop Routing**: Currently, nodes must be in direct range of the Gateway. We plan to implement a distance-vector routing protocol (like AODV) to allow nodes to act as relays.
-   [*] **User-space NAT (Tun2Socks)**: Integrating a robust NAT engine on the Gateway to handle thousands of concurrent connections.
-   [*] **Mesh Status Dashboard**: A real-time visual map of the mesh topology within the app.

---

## 📜 License & Credits

Distributed under the MIT License. Created by **Coolguy1211**.
Special thanks to the Google Open Source team for the Nearby Connections API.
