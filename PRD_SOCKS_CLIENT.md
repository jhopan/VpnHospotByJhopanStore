# Product Requirements Document (PRD)

## 1. Produk
- Nama: `Socks Client`
- Platform: Android
- Tujuan: Menjadikan HP client sebagai VPN lokal berbasis TUN yang meneruskan trafik ke SOCKS5 server (HP VPN Hotspot) melalui Wi-Fi hotspot / USB tethering.

## 2. Problem Statement
- User butuh internet sharing yang lebih fleksibel melalui SOCKS5.
- Implementasi sebelumnya tidak stabil (connect/disconnect, core exit cepat, force close).

## 3. Goals
- `Connect` stabil ke server SOCKS5 lokal (IP hotspot/tether).
- `Disconnect` selalu responsif.
- Trafik aplikasi client keluar melalui SOCKS server.
- Debugging jelas via logcat dan status UI.

## 4. Non-Goals
- Belum fokus multi-protocol (VLESS/Trojan/VMess).
- Belum fokus policy routing kompleks per-app/per-domain advanced.

## 5. User Flow
1. User konek ke hotspot/USB tether dari HP server.
2. Buka app `Socks Client`.
3. Isi Host/IP + Port SOCKS5.
4. Klik Connect dan grant VPN permission.
5. Status jadi connected.
6. Klik Disconnect kapan pun untuk stop tunnel.

## 6. Functional Requirements
- FR1: Input host, port, username/password opsional.
- FR2: Start tunnel mode TUN + SOCKS5 outbound.
- FR3: Stop tunnel bersih tanpa force close.
- FR4: Persist status `connected/status` di SharedPreferences.
- FR5: Logging event penting ke logcat (`SocksClientMain`, `SocksVpnService`).

## 7. Non-Functional Requirements
- NFR1: Start/stop < 3 detik pada device normal.
- NFR2: Tidak ANR saat connect/disconnect.
- NFR3: Kompatibel Android SDK 24+.
- NFR4: Stabil di MIUI (graceful handling service lifecycle).

## 8. Success Metrics
- >95% connect success di 20 percobaan.
- 0 crash di sesi connect/disconnect standar.
- Internet reachable (DNS + HTTP/HTTPS) setelah connect.

## 9. Risiko Utama
- Device/vendor behavior (MIUI appops noise).
- Konfigurasi core tidak cocok pada runtime.
- DNS/routing mismatch menyebabkan “connected tapi no internet”.
