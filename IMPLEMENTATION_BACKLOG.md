# Implementation Backlog (Next Actions)

## P0 (Harus)
- Stabilkan koneksi internet end-to-end pada sing-box config final.
- Tambah status detail tahap koneksi (setup/start/openTun/route ready).
- Validasi DNS strategy (direct DNS vs remote DNS behavior).
- Tambah proteksi reconnect jika core exit cepat.

## P1 (Penting)
- Tambah export log otomatis ke file untuk bug report.
- Tambah health-check endpoint internal (core running, tun fd valid).
- Tambah statistik realtime (rx/tx bytes, session count).

## P2 (Enhancement)
- Per-app routing include/exclude.
- Auto reconnect policy.
- Preset profile server (save multiple socks endpoints).

## Rilis
- `v0.1`: connect/disconnect stabil + internet jalan.
- `v0.2`: telemetry + diagnostics lengkap.
- `v0.3`: policy routing + UX polish.
