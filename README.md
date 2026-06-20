<div align="center">

# VPN Hospot

**HTTP Proxy & SOCKS5 Proxy for Android**  
*Share your VPN connection via Wi-Fi Hotspot and USB Tethering*

[![Release](https://img.shields.io/github/v/release/jhopan/VpnHospotByJhopanStore?style=for-the-badge&color=blue)](https://github.com/jhopan/VpnHospotByJhopanStore/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/java-17-orange?style=for-the-badge&logo=openjdk)](#requirements)
[![Android](https://img.shields.io/badge/android-5.0+-green?style=for-the-badge&logo=android)](#requirements)

</div>

---

## Overview

VPN Hospot transforms your Android device into a proxy server, allowing you to share your VPN connection with other devices via Wi-Fi Hotspot or USB Tethering. Perfect for gaming, streaming, and secure browsing on multiple devices.

## Features

**Dual Proxy Support**
- HTTP Proxy with TCP CONNECT tunneling
- SOCKS5 Proxy with full TCP + UDP ASSOCIATE support
- Real-time traffic monitoring and statistics

**VPN Integration**
- Automatic VPN network detection and binding
- Zero traffic leaks - all proxy traffic routed through VPN tunnel
- Compatible with Dark Tunnel, HTTP Injector, WireGuard, and more

**Gaming Optimized**
- Enhanced UDP relay with 60-second keep-alive mechanism
- 4-layer fallback session mapping for stability
- Optimized for online gaming over VPN

**User Experience**
- Clean, minimal interface with live status display
- Configurable ports with instant preview
- Multi-interface support (Wi-Fi Hotspot & USB Tethering)
- Foreground service with persistent notification

## Download

Get the latest release from [GitHub Releases](https://github.com/jhopan/VpnHospotByJhopanStore/releases/latest).

| Variant | Architecture | Description |
|---------|-------------|-------------|
| `arm64-v8a` | 64-bit ARM | Modern phones (recommended) |
| `armeabi-v7a` | 32-bit ARM | Older devices |
| `universal` | All | Universal package |

## Quick Start

### 1. Server Setup

**On the device running VPN Hospot:**

```
1. Install VPN Hospot
2. Start your VPN app (Dark Tunnel, HTTP Injector, etc.)
3. Open VPN Hospot → Tap "Start Proxy"
4. Enable Wi-Fi Hotspot or USB Tethering
5. Note the IP address displayed in the app
```

### 2. Client Setup

**Connect via Wi-Fi Hotspot:**

```
1. Connect to server's Wi-Fi hotspot
2. Open Wi-Fi Settings → Tap connected network → Modify Network
3. Set Proxy to "Manual"
4. Hostname: [Server IP from VPN Hospot]
5. Port: 8080 (HTTP) or 1080 (SOCKS5)
6. Save and connect
```

**Connect via USB Tethering:**

```
1. Connect client device to server via USB cable
2. Enable USB Tethering on server device
3. Configure proxy on client using USB tethering IP
4. Use same port settings as Wi-Fi setup
```

### 3. SOCKS5 Clients

For SOCKS5 proxy (port `1080`), use compatible applications:

**Mobile:**
- Socks Client by JhopanStore (Android)

**Desktop:**
- Proxifier (Windows/macOS)
- SocksCap64 (Windows)
- `proxychains4` (Linux)

**Browsers:**
- Firefox: Settings → Network Settings → Manual proxy → SOCKS Host
- Telegram: Settings → Data and Storage → Use Proxy → SOCKS5

## Technical Details

### Network Binding

VPN Hospot intelligently routes all proxy traffic through your VPN tunnel:

```
Priority: VPN Interface > Active Network > Cellular Data
```

This ensures:
- No traffic leaks to cellular data
- All connections use VPN exit IP
- Consistent IP for gaming sessions

### UDP Relay

Enhanced UDP ASSOCIATE with advanced session management:

- **Keep-Alive:** UDP sessions remain active for 60 seconds after TCP control closes
- **Fallback Mapping:** 4-layer mapping (IP:port → port → IP-only → single client)
- **Statistics:** Real-time forward/backward packet tracking

### Proxy Protocols

| Protocol | TCP | UDP | IPv4 | IPv6 |
|----------|-----|-----|------|------|
| HTTP Proxy | ✓ CONNECT | ✗ | ✓ | ✓ |
| SOCKS5 Proxy | ✓ CONNECT | ✓ ASSOCIATE | ✓ | ✓ |

## Build from Source

### Requirements

- Android Studio Arctic Fox or newer
- JDK 17
- Android SDK 34

### Clone and Build

```bash
git clone https://github.com/jhopan/VpnHospotByJhopanStore.git
cd VpnHospotByJhopanStore
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/`

### Release Build

```bash
./gradlew assembleRelease
```

Release APKs: `app/build/outputs/apk/release/` (3 variants)

### Automated Release

Create a new release via GitHub Actions:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Configuration

### Default Ports

- HTTP Proxy: `8080`
- SOCKS5 Proxy: `1080`

Both ports are configurable in the app interface.

### Traffic Counting

Enable/disable real-time traffic monitoring via the toggle switch in the app.

## Known Limitations

- **HTTP Proxy:** TCP only, no UDP relay support
- **UDP Stability:** Depends on VPN provider and remote server response
- **Gaming Servers:** Some servers may block VPN exit IPs (not an app limitation)

## Troubleshooting

**Proxy not working:**
- Ensure VPN is connected before starting proxy
- Check that hotspot/tethering is enabled
- Verify client proxy settings match server IP and port

**UDP not stable:**
- Try different VPN servers
- Some game servers block VPN IPs
- Check VPN provider's UDP support

## Developer

**JhopanStore**
- **Telegram:** [@jhopan_05](https://t.me/jhopan_05)
- **Website:** [jhopanstore.my.id](https://jhopanstore.my.id)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ by JhopanStore**

[Report Bug](https://github.com/jhopan/VpnHospotByJhopanStore/issues) · [Request Feature](https://github.com/jhopan/VpnHospotByJhopanStore/issues)

</div>
