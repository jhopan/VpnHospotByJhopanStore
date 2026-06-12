# Technical Design Document (TDD)

## 1. Scope Teknis
- Migrasi engine VPN client dari `hevtunnel` ke `sing-box libbox`.
- Menjaga UI existing dan workflow connect/disconnect.

## 2. Modul/Files
- `Socks Client/src/main/java/com/jhopanstore/socksclient/MainActivity.java`
- `Socks Client/src/main/java/com/jhopanstore/socksclient/SocksVpnService.java`
- `Socks Client/src/main/java/com/jhopanstore/socksclient/DebugLog.java`
- `Socks Client/build.gradle.kts`

## 3. Desain Lifecycle
- Connect:
  - Validasi input -> start foreground service -> setup libbox -> start command server -> start sing-box config.
- Disconnect:
  - set stopping flag -> closeService -> close server -> close tun fd -> stop foreground + stopSelf.

## 4. Threading
- Main thread hanya UI + intent dispatch.
- Worker thread untuk operasi start core.
- Dedicated thread untuk stop agar tidak deadlock di queue connect.

## 5. Logging Strategy
- `Log.i/e(TAG, ...)` pada tahap:
  - onStartCommand
  - setup/start core
  - openTun establish
  - stop/cleanup
- Internal mirror ke `DebugLog` untuk inspeksi cepat di UI.

## 6. Kontrak Status
- SharedPrefs key:
  - `connected: boolean`
  - `status: string`
- UI enable/disable tombol berdasarkan state ini.

## 7. Error Handling
- Catch semua exception pada boundary service.
- Emit status human-readable.
- Hindari crash fatal dari callback service.

## 8. Security/Permission
- `BIND_VPN_SERVICE` untuk service VPN.
- Foreground service permission untuk Android modern.
- Tidak expose service ke aplikasi lain (`exported=false`).
