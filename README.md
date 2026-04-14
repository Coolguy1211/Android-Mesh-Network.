# 📱 Android Mesh Network - Deep Technical Documentation

An advanced Android application that forms a high-performance, decentralized mesh network using **WiFi Direct**, **Bluetooth Low Energy (BLE)**, and **Classic Bluetooth**. This project enables offline communication and transparent, device-wide internet sharing through a mesh-routed VPN tunnel.

---

## 🚀 Recent Updates
-   **TCP NAT Relay**: Added support for device-wide TCP traffic (Web browsing, HTTPS).
-   **Multi-hop AODV**: Packets can now traverse multiple nodes to reach the Gateway.
-   **CI/CD Fix**: Increased GitHub Actions memory limits to 4GB for stable builds.
-   **Persistence**: Added Device Admin and Launcher mode to prevent OS task killing.

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
*   **The Tunnel**: The app reads these packets from the TUN interface, encapsulates them into `MeshPacket.VPN_IP_PACKET`, and routes them through the mesh.
*   **The Gateway**: Receives the raw IP packets. It performs **User-space NAT (Network Address Translation)** for both **UDP** and **TCP**. It uses a non-blocking NIO Selector loop to manage multiple concurrent internet connections.

### 3. Multi-hop AODV Routing
Nodes maintain dynamic routing tables. If Phone A is too far from the Gateway but can see Phone B, it will use Phone B as a relay node.
*   **Learning**: Nodes update routes based on the origin field of passing packets.
*   **Forwarding**: Packets are automatically relayed to the neighbor with the lowest hop count to the destination.

---

## 📡 Networking Protocols

| Feature | Protocol | Hardware |
| :--- | :--- | :--- |
| **Discovery** | BLE / mDNS | Bluetooth / WiFi |
| **Mesh Link** | WiFi Direct / P2P | WiFi Radio |
| **VPN Tunnel** | Raw IPv4/IPv6 Packets | Virtual TUN Interface |
| **NAT Relay** | User-space TCP/UDP Proxy | Standard Sockets |

---

## ⚙️ Detailed Setup & Build Instructions

### Prerequisites
*   Android Studio Ladybug (or newer)
*   JDK 17
*   At least two physical Android devices

### GitHub Actions Build (Recommended)
This project is optimized for cloud builds:
1.  Navigate to **Actions** in your repository.
2.  Select the **Build Android APK** workflow.
3.  Download the `app-debug` artifact from the latest successful run.

### Local CLI Build
```powershell
./gradlew assembleDebug
```

---

## 🚧 Future Roadmap

-   [x] **TCP Support**: Implemented via NIO SocketChannels.
-   [x] **Persistence**: Implemented via Device Admin & Launcher mode.
-   [ ] **Full IP Wrapping**: Wrapping internet responses back into IPv4 headers for seamless client-side injection.
-   [ ] **Mesh Status Dashboard**: A real-time visual map of the mesh topology.

---

## 📜 License & Credits

Distributed under the MIT License. Created by **Coolguy1211**.
Special thanks to the Google Open Source team for the Nearby Connections API.
