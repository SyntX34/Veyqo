<p align="center">
  <img src="assets/logo.svg" alt="Veyqo Logo" width="128" height="128" />
</p>

<h1 align="center">Veyqo</h1>

<p align="center">
  <strong>High-performance, local-first remote device bridge for Windows, Linux, and Android.</strong>
</p>

<p align="center">
  <a href="https://github.com/SyntX34/Veyqo/actions"><img src="https://github.com/SyntX34/Veyqo/workflows/Build%20and%20Release/badge.svg" alt="Build Status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <a href="https://github.com/SyntX34"><img src="https://img.shields.io/badge/Author-Samir%20Neupane%20(SyntX34)-indigo.svg" alt="Author"></a>
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/Contributions-Welcome-brightgreen.svg" alt="Contributions Welcome"></a>
</p>

---

## Overview

**Veyqo** provides direct peer-to-peer remote device control over your local area network (LAN/Wi-Fi) without third-party cloud relays, subscription walls, or internet dependencies.

- **Desktop Controller (C# / Avalonia UI):** Cross-platform desktop application for Windows and Linux x64 with native rendering, QR code generation, and low-latency input streaming.
- **Mobile Server (Android / Java):** Background service that listens on local port `55443`, accepts direct connections, scans desktop pairing QR codes, and injects touch actions via the Android Accessibility Service (supports Android 8.0 Oreo and above).

---

## Features

- **Bi-directional Discovery:**
  - **Scan from Mobile:** Desktop displays a live QR pairing payload (`veyqo://connect?host=...&port=...`); scan with the mobile app camera to connect.
  - **Open Port on Mobile:** Toggle the background server on Android, enter the phone's IP and port into the desktop client, and connect directly.
- **Ultra-Low Latency:** Direct TCP byte-stream socket connection across LAN.
- **Cross-Platform Support:** C# Avalonia desktop client (Windows & Linux) and native Android Java client (`minSdk 26`).
- **Automated CI/CD:** GitHub Actions workflow builds signed official release APKs and desktop single-file binaries on every commit/PR, with automated releases on manual trigger.

---

## Project Structure

```
Veyqo/
├── .github/workflows/
│   └── build-and-release.yml   # Multi-platform CI/CD and manual release pipeline
├── .vscode/
│   └── settings.json           # Safe editor workspace configuration
├── assets/
│   └── logo.svg                # Geometric vector branding logo
├── desktop/
│   ├── Network/
│   │   ├── ControlPacket.cs    # Network protocol message definitions
│   │   ├── NetworkDiscovery.cs # Local IP and pairing payload utilities
│   │   └── RemoteClient.cs     # Asynchronous TCP client & packet stream
│   ├── Views/
│   │   ├── MainWindow.axaml    # Desktop GUI layout
│   │   └── MainWindow.axaml.cs # Event handlers and input capture
│   ├── App.axaml               # Fluent dark theme configuration
│   ├── Program.cs              # Application entry point
│   └── Veyqo.Desktop.csproj    # .NET 8 Avalonia cross-platform project
├── mobile/
│   ├── app/
│   │   ├── src/main/java/com/veyqo/remote/
│   │   │   ├── network/ControlPacket.java
│   │   │   ├── service/InputInjectionService.java
│   │   │   ├── service/RemoteServerService.java
│   │   │   └── MainActivity.java
│   │   ├── src/main/res/
│   │   ├── build.gradle
│   │   └── src/main/AndroidManifest.xml
│   ├── gradle/wrapper/
│   ├── build.gradle
│   ├── gradle.properties
│   ├── gradlew
│   └── settings.gradle
├── CONTRIBUTING.md             # Contribution standards and guidelines
├── LICENSE                     # MIT License (Samir Neupane / SyntX34)
└── README.md
```

---

## Roadmap

### Phase 1: Core Networking & Protocol (Current)
- [x] Initial Avalonia C# cross-platform desktop UI (Windows & Linux x64).
- [x] Native Android Java background server on port `55443` with Android 8+ (`minSdk 26`) support.
- [x] QR code pairing and direct IP/Port handshake.
- [x] Touch input injection via Android Accessibility Service.
- [x] GitHub Actions automated multi-platform compilation and signed release packaging.

### Phase 2: Screen Streaming & Performance
- [ ] Hardware-accelerated screen capture via Android `MediaProjection` API.
- [ ] H.264/H.265 video encoding pipeline using `MediaCodec`.
- [ ] Low-latency video decoder integration in the desktop client using FFmpeg / SkiaSharp.
- [ ] Dynamic bitrate and frame-rate adjustment based on LAN network congestion.

### Phase 3: Enhanced Input & Device Features
- [ ] Full hardware keyboard synchronization (IME input, function keys, hotkeys).
- [ ] Bi-directional shared clipboard sync (copy text on PC, paste on mobile and vice-versa).
- [ ] Multi-touch gesture emulation (pinch-to-zoom, two-finger scroll).
- [ ] Drag-and-drop local file transfer over TCP channel.

### Phase 4: Security & Ecosystem
- [ ] End-to-end TLS encryption with ephemeral certificate exchange during QR handshake.
- [ ] Device authorization whitelist (prompt to approve or reject unknown desktop connections).
- [ ] macOS desktop client support via Avalonia.
- [ ] Audio streaming from mobile to desktop.

---

## Building Locally

### Desktop Client (Linux & Windows)

Requirements: [.NET 8.0 SDK](https://dotnet.microsoft.com/download)

```bash
cd desktop
dotnet build -c Release
dotnet run
```

To build a standalone single-file binary:

```bash
# Windows
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true

# Linux
dotnet publish -c Release -r linux-x64 --self-contained true -p:PublishSingleFile=true
```

### Mobile App (Android)

Requirements: JDK 17 & Android SDK (API 34)

```bash
cd mobile
./gradlew assembleRelease
```

Install the signed release APK to a connected device:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Usage

1. **Connect both devices to the same Wi-Fi/LAN.**
2. **Method A (QR Code):**
   - Launch `Veyqo` on your PC. A pairing QR code is generated with your local IP.
   - Open Veyqo on Android, tap **Scan PC QR Code**, and point your camera at the screen.
3. **Method B (Direct Port):**
   - Open Veyqo on Android and tap **Start Remote Server** (listens on port `55443`). Note the displayed IP address.
   - On Desktop, type the mobile IP and port into **Direct Port Connection** and click **Connect to Mobile**.
4. Enable the **Veyqo Input Service** in Android Accessibility Settings when prompted to allow touch input control.

---

## Contributing

Contributions are welcomed. Please read the [Contributing Guidelines](CONTRIBUTING.md) for branch management, commit styles, and local testing instructions.

---

## Author

- **Samir Neupane ([@SyntX34](https://github.com/SyntX34))**
- Repository: [SyntX34/Veyqo](https://github.com/SyntX34/Veyqo)

---

## License

This project is licensed under the [MIT License](LICENSE) &copy; 2026 Samir Neupane (SyntX34).
