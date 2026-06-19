# VPN Hospot by JhopanStore

Aplikasi Android untuk sharing internet via HTTP Proxy dan SOCKS5 Proxy melalui Wi-Fi Hotspot dan USB Tethering.

## Fitur

- **HTTP Proxy** - Proxy HTTP untuk browsing dan aplikasi yang support HTTP proxy
- **SOCKS5 Proxy** - Proxy SOCKS5 dengan dukungan TCP + UDP
- **Wi-Fi Hotspot** - Share koneksi internet via hotspot
- **USB Tethering** - Share koneksi internet via USB
- **Traffic Counter** - Monitoring download/upload real-time
- **VPN-aware Routing** - Otomatis route traffic melalui VPN/TUN interface (compatible dengan Dark Tunnel, HTTP Injector, dll)

## Download

Download APK terbaru di [Releases](https://github.com/jhopan/VpnHospotByJhopanStore/releases/latest).

Tersedia 3 varian APK:
- **arm64-v8a** - Untuk HP 64-bit modern (95% HP saat ini)
- **armeabi-v7a** - Untuk HP 32-bit lama
- **universal** - Kompatibel dengan semua arsitektur (ukuran lebih besar)

## Cara Pakai

### 1. Setup di HP Server (yang jalankan VPN Hospot)

1. Install dan buka VPN Hospot
2. (Opsional) Jalankan aplikasi tunneling seperti Dark Tunnel atau HTTP Injector
3. Buka VPN Hospot dan klik **Start Proxy**
4. Nyalakan Wi-Fi Hotspot atau USB Tethering dari pengaturan HP
5. Catat IP address yang muncul di aplikasi

### 2. Setup di HP Client

**Via Wi-Fi:**
1. Connect ke hotspot HP server
2. Buka Wi-Fi Settings → tap jaringan yang terhubung → Modify Network
3. Proxy: **Manual**
4. Hostname: isi IP server dari app VPN Hospot
5. Port: `8080` (default HTTP Proxy)
6. Save

**Via USB Tethering:**
1. Hubungkan HP client ke HP server via kabel USB
2. Aktifkan USB Tethering di HP server
3. Set proxy di HP client seperti cara di atas (gunakan IP USB tethering)

### 3. SOCKS5 Proxy

Untuk SOCKS5, gunakan port `1080` (default) dan aplikasi yang support SOCKS5 seperti:
- **Android**: Socks Client by JhopanStore
- **Telegram**: Settings → Data and Storage → Use Proxy → Add SOCKS5
- **Firefox**: Settings → Network Settings → Manual proxy → SOCKS Host
- **PC/Laptop**: Proxifier, SocksCap64, atau `proxychains4`

## Build dari Source

### Requirements

- Android Studio Arctic Fox atau lebih baru
- JDK 17
- Android SDK 34

### Build

```bash
git clone https://github.com/jhopan/VpnHospotByJhopanStore.git
cd VpnHospotByJhopanStore
./gradlew assembleDebug
```

APK akan ada di `app/build/outputs/apk/debug/`

### Build Release

```bash
./gradlew assembleRelease
```

APK release akan ada di `app/build/outputs/apk/release/` dengan 3 varian (arm64-v8a, armeabi-v7a, universal).

## Release Otomatis

Push tag untuk trigger GitHub Actions build dan create release otomatis:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions akan build 3 APK dan create release dengan release notes.

## Developer

**JhopanStore**

- Telegram: https://t.me/jhopan_05
- Website: https://jhopanstore.my.id

## License

MIT License - bebas digunakan dan dimodifikasi.
