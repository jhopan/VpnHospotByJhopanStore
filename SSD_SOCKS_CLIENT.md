# System Design Document (SSD)

## 1. Arsitektur Tingkat Tinggi
- `MainActivity`:
  - Form input server SOCKS.
  - Trigger connect/disconnect.
  - Render status + debug log.
- `SocksVpnService`:
  - Orkestrasi VPNService lifecycle.
  - Bridge Android TUN ke core sing-box (`libbox`).
  - Start/stop command server.
- `libbox.aar`:
  - Core sing-box (Go runtime + networking).
  - Menjalankan tun inbound + socks outbound.

## 2. Data Flow
1. User klik Connect di `MainActivity`.
2. `MainActivity` kirim intent `ACTION_CONNECT`.
3. `SocksVpnService` inisialisasi `Libbox.setup(...)`.
4. `CommandServer.startOrReloadService(config)` dipanggil.
5. libbox minta open TUN via `PlatformInterface.openTun(...)`.
6. Android `VpnService.Builder.establish()` return fd.
7. Trafik app -> TUN -> sing-box -> SOCKS5 server -> internet.

## 3. Komponen Kunci
- State storage:
  - `socks_client_status` (`connected`, `status`).
- Logging:
  - logcat tag: `SocksClientMain`, `SocksVpnService`.
  - internal debug buffer: `DebugLog`.
- Notification:
  - foreground channel `socks_client`.

## 4. Konfigurasi Core (Current)
- Inbound: `tun`
- Outbound utama: `socks` (`version:5`, `server`, `server_port`, optional auth)
- Route final: `socks-out`

## 5. Failure Handling
- Invalid host/port -> fail fast.
- Core start error -> status `Gagal connect: ...`.
- Revoke VPN -> stop service.
- Disconnect -> force close command server + close tun fd.

## 6. Known Gaps
- Belum ada telemetry statistik koneksi (latency/retry per tahap).
- Belum ada fallback DNS strategy berlapis.
- Belum ada integration test on-device otomatis.
