# 📱 Android Mesh Network - Deep Technical Documentation

An advanced Android application that forms a high-performance, decentralized mesh network using **WiFi Direct**, **Bluetooth Low Energy (BLE)**, and **Classic Bluetooth**. This project enables offline communication and transparent, device-wide internet sharing through a mesh-routed VPN tunnel.

---

## 🚀 Recent Updates
-   **Modern Chat UI**: Full-screen messaging with WhatsApp-style bubbles, timestamps, and global sync.
-   **Desktop Control Center**: Cross-platform (Win/Mac/Linux) tray app for mesh management.
-   **Stability & Ghost Node Fixes**: Aggressive AODV routing cleanup and auto-detect compatibility mode for older devices (Pixel 3).
-   **Full TCP/UDP NAT**: Optimized userspace routing for reliable HTTPS browsing and streaming.

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
The app uses the `STRATEGY_P2P_CLUSTER` topology, supporting a many-to-many connection.
*   **Discovery**: Devices scan via BLE/Bluetooth to find peers.
*   **Bandwidth Upgrade**: Automatically negotiates a high-speed WiFi Direct (P2P) link.
*   **Compatibility Mode**: Older devices (like Pixel 3) auto-switch to "Leaf Node" mode to prevent radio hardware crashes.

### 2. Modern Chat System
Messaging is no longer a simple text blob. It now features:
*   **Bubble UI**: Colored messaging bubbles with sender IDs and timestamps.
*   **Global Persistence**: Messages are stored in the application context, staying visible even when switching activities or roles.
*   **Multi-Node Broadcast**: Messages reach every node in the mesh via multi-hop routing.

### 3. Transparent Internet Sharing (VPN Tunneling)
*   **The Client**: Runs an Android `VpnService` to capture all system traffic as raw IP packets.
*   **The Tunnel**: Packets are encapsulated into `MeshPacket.VPN_IP_PACKET` and routed to the closest Gateway.
*   **The Gateway**: Performs **User-space NAT** for both **UDP** and **TCP** using a non-blocking NIO loop. It synthesizes TCP handshakes (`SYN-ACK`) to trick the client OS into a seamless connection.

---

## 💻 Desktop Control Center (Windows / macOS / Linux)

Located in `desktop_app/`, this Python-based application allows laptops to join the mesh.
*   **System Tray Integration**: Lives in your taskbar for quick access.
*   **Web API Interface**: Connects to the Android Gateway Phone's Hotspot to provide a full desktop dashboard.
*   **Features**: View the live topology graph, chat with phones in the mesh, and monitor latency.

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
3.  Download the `app-debug` artifact.

### Desktop App Setup
```bash
cd desktop_app
pip install requests pystray Pillow
python desktop_control_center.py
```

---

## 🌐 Reticulum Network Stack (RNS) Integration

This branch (`reticulum-integration`) upgrades the mesh from a custom protocol to the **Reticulum Network Stack**.

### 🚀 Key Advantages
- **Built-in Cryptography**: Every packet is encrypted and signed using Reticulum's identity system.
- **Interoperability**: Works with standard Reticulum apps like **Sideband** and **Nomad Network**.
- **Self-Healing**: Native AODV-style routing that is much more stable than our prototype.

### 📱 How it works
1. **Nearby Connections** acts as the "Physical Interface".
2. **RRN (Reticulum Node)** runs in the background via **Chaquopy (Python)**.
3. The app opens a **Local Interface** on `localhost:4242`.
4. Other apps on your phone (like Sideband) will automatically "see" our mesh node and can use it to reach the internet or other users.

### 💻 Desktop Interop
The Desktop Control Center now includes a Reticulum helper that allows your PC to act as a native Reticulum node when connected to your phone's hotspot.


---

## 📜 License & Credits

Distributed under the MIT License. Created by **Coolguy1211**.
Special thanks to the Google Open Source team for the Nearby Connections API.
