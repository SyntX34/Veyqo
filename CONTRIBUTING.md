# Contributing to Veyqo

Thank you for your interest in contributing to **Veyqo**! This document provides instructions and guidelines for opening issues, submitting code, and adhering to project standards.

---

## Code of Conduct

We are committed to providing a welcoming, constructive, and productive environment for all contributors. Please treat fellow community members with professional respect.

---

## Architecture Overview

Veyqo is structured into two core components:

1. **`desktop/` (Desktop Controller)**:
   - Built with **C# (.NET 8.0)** and **Avalonia UI**.
   - Runs natively on Windows and Linux x64.
   - Generates dynamic pairing QR codes, listens for incoming connections, and handles low-latency pointer/keyboard serialization over TCP.

2. **`mobile/` (Android Host & Remote Client)**:
   - Built with **Java / Android SDK** (`minSdk 26`, Android 8.0+).
   - Contains a background TCP socket server (`RemoteServerService`) on port `55443`.
   - Uses Android's `AccessibilityService` (`InputInjectionService`) for touch and gesture injection without root.
   - Integrates ZXing for QR pairing code scanning.

---

## Development Setup

### Prerequisites

- **Desktop**: [.NET 8.0 SDK](https://dotnet.microsoft.com/download)
- **Mobile**: JDK 17, Android SDK Platform 34, and Gradle 8.2+
- **Editor**: VS Code with C# Dev Kit and Android extensions, or JetBrains Rider / Android Studio

### Building Locally

#### 1. Desktop App
```bash
cd desktop
dotnet build -c Debug
dotnet run
```

#### 2. Mobile App
```bash
cd mobile
./gradlew assembleDebug
```

---

## Development Guidelines

### Code Style & Formatting
- **C# / .NET**: Follow standard C# naming conventions (PascalCase for methods and properties, camelCase with `_` prefix for private fields).
- **Java / Android**: Follow standard Android conventions with clear descriptive identifiers (e.g., `handleClient`, `injectTap`).
- **Formatting**: Preserve editor configuration rules in `.vscode/settings.json` (`"editor.formatOnSave": false`).

### Commit Messages
We enforce the **Conventional Commits** specification:
- `feat:` New features or public APIs
- `fix:` Bug fixes
- `refactor:` Code restructuring without functional changes
- `docs:` Documentation and guide changes
- `ci:` Workflow and build configuration updates
- `test:` Unit or integration test additions

---

## Submitting Pull Requests

1. Fork the repository on GitHub: `https://github.com/SyntX34/Veyqo`.
2. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. Commit your changes with a conventional commit message.
4. Verify local builds pass for both desktop and mobile:
   ```bash
   dotnet build desktop/Veyqo.Desktop.csproj
   cd mobile && ./gradlew assembleDebug
   ```
5. Push to your fork and submit a Pull Request targeting `main`.
6. Automated GitHub Actions will compile your code across Windows, Linux, and Android.
