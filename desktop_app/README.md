# Mesh Control Center (Desktop)

A cross-platform desktop application for Windows, macOS, and Linux that allows you to interact with the Android Mesh Network.

## 🚀 Features
- **System Tray Icon**: Lives in your taskbar for quick access.
- **Live Mesh Chat**: Send and receive messages to the mesh from your PC.
- **Topology Viewer**: See all connected phones and routing paths in real-time.
- **Settings**: Configure the Gateway IP to connect to different mesh nodes.

## 🛠 Setup Instructions

### 1. Install Python
Ensure you have Python 3.x installed on your computer.

### 2. Install Dependencies
Open your terminal/command prompt and run:
```bash
pip install requests pystray Pillow
```

### 3. Run the App
```bash
python desktop_control_center.py
```

## 💻 How to Connect to Mesh
1. On your Android phone, enable **Personal Hotspot**.
2. Connect your Laptop to the phone's WiFi.
3. Open this Desktop App.
4. It will automatically try to connect to the phone at `192.168.43.1` (the default Android Hotspot IP).
5. You can now chat with other phones in the mesh!
