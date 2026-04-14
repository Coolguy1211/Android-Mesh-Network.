# Android Mesh Network App

A robust Android application that forms an offline mesh network using WiFi and Bluetooth to enable messaging, file sharing, and transparent internet sharing.

## 📊 Architecture Diagram

```text
       ┌────────────────────────┐
       │     "REAL" INTERNET    │
       └───────────┬────────────┘
                   │ (WiFi/Cellular)
                   ▼
       ┌────────────────────────┐
       │     GATEWAY NODE       │
       │ (Phone with Internet)  │◀──┐
       └───────────┬────────────┘   │
                   │ (Nearby API)   │ (Mesh Network)
         ┌─────────┴─────────┐      │
         ▼                   ▼      │
┌────────────────┐  ┌────────────────┐  │
│   CLIENT NODE  │  │   CLIENT NODE  │  │
│ (VpnService)   │  │ (VpnService)   │◀─┘
└────────┬───────┘  └────────┬───────┘
         │                   │
         └─────▶ MESH ◀──────┘
         (Text / Files / VPN)
```

### Data Flow Explanation
1.  **Mesh Formation**: Phones discover each other via **Bluetooth/BLE** and upgrade to high-speed **WiFi Direct** automatically using the Nearby Connections API.
2.  **Messaging/Files**: Data is sent as "Payloads" directly between nodes.
3.  **Internet Sharing**:
    -   **Client Node**: Runs a `VpnService` that captures all IP packets from the system.
    -   **Tunneling**: These packets are sent over the Mesh (Nearby API) to the **Gateway**.
    -   **Routing**: The Gateway forwards packets to the internet and sends responses back to the Client.


-   **P2P Mesh Networking**: Uses Google's Nearby Connections API (`STRATEGY_P2P_CLUSTER`) to form an M-to-N mesh network automatically over Bluetooth, BLE, and WiFi Direct.
-   **Offline Texting**: Send and receive real-time text messages across the mesh without cellular data or external WiFi.
-   **High-Speed File Sharing**: Optimized high-bandwidth file transfers using WiFi Direct.
-   **Internet Sharing (Gateway Mode)**: 
    -   One device connected to "real" WiFi acts as a **Gateway**.
    -   Other devices connect to the Gateway via the mesh and route their entire device-level traffic through a custom **VPN Tunnel** (`VpnService`).

## 🛠 Tech Stack

-   **Language**: Kotlin
-   **Minimum SDK**: Android 7.0 (API 24)
-   **Target SDK**: Android 14 (API 34)
-   **Core Libraries**: 
    -   `com.google.android.gms:play-services-nearby` (Nearby Connections)
    -   `androidx.core:core-ktx`
    -   `androidx.appcompat:appcompat`

## 🏗 Project Structure

-   `MainActivity.kt`: UI logic and permission handling.
-   `network/NearbyConnectionManager.kt`: Core mesh formation and payload (Bytes/Files) handling.
-   `network/MeshVpnService.kt`: Android VpnService implementation for capturing and routing device-wide network traffic.

## 📦 How to Build

This project is configured with **GitHub Actions** for easy building:
1.  Push code to your repository.
2.  Go to the **Actions** tab on GitHub.
3.  Download the `app-debug` artifact once the build is complete.

Alternatively, to build locally:
```bash
./gradlew assembleDebug
```

## 📱 Permissions Required

To function as a mesh node, the app requires:
-   Nearby Devices (Bluetooth/WiFi scanning)
-   Fine Location (Required by Android for hardware radio access)
-   VPN Service (For internet sharing)
-   Media/Storage access (For file sharing)

## 🤝 Contributing

This is a prototype mesh networking application. Feel free to fork and submit pull requests for features like multi-hop routing or user-space NAT optimization for the Gateway node.

## 📜 License

MIT License - feel free to use this for your own projects!
