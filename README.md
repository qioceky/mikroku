# MikroKu

Panel admin MikroTik **RouterOS v6** untuk Android — Bahasa Indonesia, tanpa iklan,
tanpa analytics, tanpa server tengah. HP tersambung **langsung** ke router lewat
protokol API biner MikroTik (port 8728 / api-ssl 8729).

Dibangun lengkap secara bertahap: mulai dari monitoring (v0.1), menuju dashboard
admin penuh setara pekerjaan harian di WinBox. Rancangan lengkap ada di
[`RANCANGAN.md`](RANCANGAN.md).

## Fitur v0.1.1 — Monitoring + layar awal model aplikasi resmi

- **Layar awal 3 tab** (sejak v0.1.1, meniru pola aplikasi resmi atas
  permintaan pengguna): **MASUK** (form login langsung: Address, Username,
  Password, centang simpan password, tombol SIMPAN / SAMBUNGKAN, pemilih
  API 8728 / API-SSL 8729), **TERSIMPAN** (daftar router), **DITEMUKAN**
  (penemuan router di LAN lewat MNDP — protokol yang sama dengan WinBox,
  UDP 5678; ketuk hasil untuk mengisi form masuk).
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
- Izin aplikasi hanya 4: `INTERNET`, `ACCESS_NETWORK_STATE`, dan (sejak
  v0.1.1, khusus tab Ditemukan) `ACCESS_WIFI_STATE` +
  `CHANGE_WIFI_MULTICAST_STATE` untuk menerima broadcast MNDP.

## Build

Build hanya lewat GitHub Actions (`.github/workflows/build.yml`): keystore
tetap dipulihkan dari secret repo, APK release dibangun lalu diverifikasi
dengan `apksigner` resmi — sidik jari penanda tangan harus sama dengan
keystore yang di-pin:

- Signer SHA-256: `835D38CA48E11DAF66AE74CDAEF68EC017AB5EDE9689AE07D0AE2C5031B96CBB`
- Package: `id.mikroku`
