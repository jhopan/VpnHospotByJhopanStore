# VPN Hospot

HTTP Proxy and SOCKS5 Proxy for Android Wi-Fi Hotspot and USB Tethering.

## Features

- **Dual Proxy** - HTTP Proxy (TCP) + SOCKS5 Proxy (TCP + UDP ASSOCIATE)
- **VPN-Aware Routing** - Automatic traffic routing through VPN tunnel (Dark Tunnel, HTTP Injector, etc.)
- **Zero Traffic Leaks** - All proxy traffic bound to VPN interface
- **Gaming Optimized** - Enhanced UDP relay with 60-second keep-alive
- **Real-Time Stats** - Download/upload monitoring
- **Multi-Interface** - Wi-Fi Hotspot and USB Tethering support

## Download

Latest release: [Releases](https://github.com/jhopan/VpnHospotByJhopanStore/releases/latest)

**APK Variants:**
- `arm64-v8a` - 64-bit ARM devices (modern phones)
- `armeabi-v7a` - 32-bit ARM devices
- `universal` - All architectures

## Quick Start

### Server Setup (HP with VPN Hospot)

1. Install VPN Hospot
2. Start your VPN app (Dark Tunnel, HTTP Injector, etc.)
3. Open VPN Hospot → **Start Proxy**
4. Enable Wi-Fi Hotspot or USB Tethering
5. Note the IP address shown in the app

### Client Setup

**Wi-Fi Connection:**
1. Connect to server's hotspot
2. Wi-Fi Settings → tap connected network → Modify Network
3. Proxy: **Manual**
4. Hostname: server IP from VPN Hospot
5. Port: `8080` (HTTP) or `1080` (SOCKS5)
6. Save

**USB Tethering:**
1. Connect client to server via USB cable
2. Enable USB Tethering on server
3. Configure proxy on client (use USB tethering IP)

### SOCKS5 Clients

Use port `1080` with SOCKS5-compatible apps:
- **Android**: Socks Client by JhopanStore
- **Telegram**: Settings → Data and Storage → Use Proxy → SOCKS5
- **Firefox**: Settings → Network Settings → Manual proxy → SOCKS Host
- **Desktop**: Proxifier, SocksCap64, or `proxychains4`

## Build from Source

### Requirements

- Android Studio Arctic Fox+
- JDK 17
- Android SDK 34

### Debug Build

```bash
git clone https://github.com/jhopan/VpnHospotByJhopanStore.git
cd VpnHospotByJhopanStore
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/`

### Release Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/` (3 APK variants)

### Automated Release

Push a tag to trigger GitHub Actions:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Developer

**JhopanStore**
- Telegram: https://t.me/jhopan_05
- Website: https://jhopanstore.my.id

## License

MIT License - see LICENSE file for details.
