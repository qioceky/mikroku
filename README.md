# MikroKu

Panel admin MikroTik **RouterOS v6** untuk Android — Bahasa Indonesia, tanpa iklan,
tanpa analytics, tanpa server tengah. HP tersambung **langsung** ke router lewat
protokol API biner MikroTik (port 8728 / api-ssl 8729).

Dibangun lengkap secara bertahap: mulai dari monitoring (v0.1), menuju dashboard
admin penuh setara pekerjaan harian di WinBox. Rancangan lengkap ada di
[`RANCANGAN.md`](RANCANGAN.md).

## Fitur v0.1.0 — Monitoring

- Daftar router (banyak router), status online/offline, tambah/ubah/hapus.
- Dashboard per router: identitas, board, versi RouterOS, CPU, RAM, uptime,
  jam router, suhu/tegangan (bila board mendukung) — auto-refresh.
- Interface & trafik: semua interface + alamat IP, laju Rx/Tx real-time
  (dihitung dari selisih counter byte, polling 2 detik) + grafik batang mini.
- Reboot router dengan konfirmasi dua langkah.
- Panduan koneksi di dalam aplikasi (mengaktifkan service API + user khusus).
- Login API dua mode otomatis: post-6.43 dan challenge-response MD5 pre-6.43.

## Keamanan

- Kredensial router disimpan **terenkripsi AES-256-GCM** (kunci di Android
  Keystore), tidak pernah ditulis plaintext dan tidak pernah masuk log.
- Mode api-ssl: sertifikat self-signed diverifikasi dengan pola TOFU
  (sidik jari disimpan saat koneksi pertama; perubahan sidik jari ditolak).
- Mode api polos (8728): sejak RouterOS 6.43 password login terkirim tanpa
  enkripsi di dalam koneksi — aplikasi menampilkan peringatan; gunakan hanya
  di LAN tepercaya atau lewat VPN, jangan buka port API ke internet.
- Izin aplikasi hanya 2: `INTERNET` dan `ACCESS_NETWORK_STATE`.

## Build

Build hanya lewat GitHub Actions (`.github/workflows/build.yml`): keystore
tetap dipulihkan dari secret repo, APK release dibangun lalu diverifikasi
dengan `apksigner` resmi — sidik jari penanda tangan harus sama dengan
keystore yang di-pin:

- Signer SHA-256: `835D38CA48E11DAF66AE74CDAEF68EC017AB5EDE9689AE07D0AE2C5031B96CBB`
- Package: `id.mikroku`
