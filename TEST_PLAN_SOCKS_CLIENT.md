# Test Plan - Socks Client

## 1. Prasyarat
- Device Android terhubung ADB.
- HP server menjalankan SOCKS5 di hotspot/USB tether.
- APK debug terbaru terpasang.

## 2. Perintah Dasar
```bash
adb devices
adb install -r "C:\Users\ACER\Documents\Project\Aplikasi\VPN Hospot\Socks Client\build\outputs\apk\debug\socksclient-debug.apk"
adb logcat -c
adb logcat -v time SocksClientMain:I SocksVpnService:I *:S
```

## 3. Test Case Wajib
- TC1 Connect valid host/port:
  - Expected: status connected, log start core sukses.
- TC2 Disconnect setelah connected:
  - Expected: status disconnected, tidak force close.
- TC3 Connect spam-click:
  - Expected: tidak crash, tidak duplicate runner.
- TC4 Invalid host/port:
  - Expected: fail fast dengan pesan jelas.
- TC5 Internet check:
  - Expected: browser/app client bisa akses web.

## 4. Verifikasi Koneksi
```bash
adb shell dumpsys connectivity | findstr /i vpn
adb shell ping -c 3 8.8.8.8
adb shell ping -c 3 google.com
```

## 5. Exit Criteria
- 20x cycle connect/disconnect tanpa crash.
- Internet reachable pada minimal 3 aplikasi umum.
