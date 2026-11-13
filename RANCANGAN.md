# RANCANGAN — MikroKu (aplikasi mobile MikroTik)

Tanggal: 2026-11-13. Status: v0.1.0 RILIS 2026-11-13 (repo `qioceky/mikroku`,
CI run `43364795785` hijau, APK 6.537.968 byte, signer SHA-256
`835D38CA…6CBB` terverifikasi apksigner). Menunggu uji lapangan user.
Keputusan user (2026-11-13): **lengkap bertahap, mulai dari monitoring.**
REVISI 2026-11-13 (09:05 WIB): user mengonfirmasi routernya **RouterOS v6** —
jalur konek DIBALIK: API biner 8728/8729 jadi jalur utama v0.1, REST v7 turun
jadi jalur kedua (v0.2+). UI/mockup tidak berubah konsep, hanya contoh
versi di mockup disesuaikan ke 6.x.
REVISI 2026-11-13 (09:10 WIB): user menegaskan target akhirnya **dashboard
admin PENUH** (bukan monitoring saja) — roadmap diperluas sampai v0.6 di
bawah; v0.1 tetap monitoring sebagai fondasi, sesuai urutan bertahap yang
sudah dipilih user.

## Identitas

- Nama: **MikroKu**
- Package: `id.mikroku`
- Repo: PUBLIK `qioceky/mikroku` (direncanakan, belum dibuat)
- Bahasa UI: Indonesia
- Gaya visual: soft neobrutalism (kertas #FFFDF7, tinta #161616, kuning #FFD02E,
  aksen hijau #2FBF71 untuk status online, merah #FF5A5A untuk offline/error) —
  sama dengan RDPfread Mobile & RuangKlon. Mockup menyertai rancangan ini.
- Tanpa iklan, tanpa analytics, tanpa backend sendiri. Semua data hanya di HP
  dan router milik user.

## Cara konek ke router (keputusan teknis inti)

### Jalur utama: API biner RouterOS (port 8728 / 8729) — untuk v0.1
REST API **tidak ada di RouterOS v6** (baru muncul di v7.1), jadi untuk router
user satu-satunya jalur programatik adalah API biner klasik MikroTik.
Terverifikasi dari dokumentasi resmi MikroTik (help.mikrotik.com/docs — API,
dicek 2026-11-13):

- Transport: TCP **8728** (api, plaintext) atau **8729** (api-ssl, TLS).
  Service `api`/`api-ssl` harus **diaktifkan dulu** di router (IP → Services);
  secara default sering nonaktif.
- Protokol: komunikasi berbasis **sentence** — rangkaian **word** yang
  diakhiri word kosong (panjang 0). Tiap word diawali panjang berkode
  variabel: 1 byte bila ≤0x7F, 2 byte ≤0x3FFF (bit penanda 0x8000),
  3 byte ≤0x1FFFFF (0xC00000), 4 byte ≤0xFFFFFFF (0xE0000000),
  5 byte untuk sisanya (0xF0 + 4 byte panjang). Ini diimplementasikan
  sendiri dalam Kotlin murni (±200 baris, tanpa library eksternal).
- Balasan router: `!re` (baris data), `!done` (selesai), `!trap` (error
  perintah), `!fatal` (error koneksi/login).
- Login: untuk ROS **≥6.43** satu sentence `/login` + `=name=` +
  `=password=` langsung dibalas `!done`. Untuk ROS **<6.43** ada metode
  lama challenge-response MD5 (`=response=00<md5>`) — klien MikroKu
  mendeteksi otomatis dari balasan `=ret=` dan memakai metode yang cocok,
  jadi seluruh rentang v6 tertangani.
- Perintah = menu console apa adanya: `/system/resource/print`,
  `/system/identity/print`, `/system/clock/print`, `/system/health/print`,
  `/interface/print`, `/ip/address/print`, `/system/reboot`, dst.
  Argumen filter memakai word `=.proplist=` dan `?` query bila perlu.

- Syarat di sisi router (akan jadi panduan langkah-demi-langkah di aplikasi):
  1. Service `api` (8728) atau `api-ssl` (8729) aktif; kolom
     *Available From* dibatasi ke subnet LAN user.
  2. User khusus dengan group ber-policy `read,api`
     (tambah `write` hanya bila user mau fitur ubah di fase berikutnya).
  3. HP dan router satu jaringan (LAN/WiFi), atau via VPN/Tailscale bila
     dari luar — port API **tidak pernah** diekspos langsung ke internet.

- **Peringatan keamanan yang harus tampil di aplikasi:** sejak login
  post-6.43, password terkirim plaintext di dalam koneksi. Di port 8728
  berarti password terbaca siapa pun yang bisa menyadap LAN. Urutan
  anjuran: (1) api-ssl 8729 bila sertifikat tersedia, (2) 8728 hanya di
  LAN tepercaya / lewat VPN. Aplikasi menampilkan banner peringatan saat
  profil router memakai 8728, tanpa memblokir pemakaiannya.

### Jalur kedua: REST API RouterOS v7 (v0.2+, bila ada router v7)
- Tersedia sejak RouterOS **v7.1** lewat service `www-ssl`
  (`https://<ip>/rest/...`, HTTP Basic Auth, group policy `read,api,rest-api`).
- Klien REST berbagi antarmuka `RouterOsClient` yang sama, jadi dashboard
  dan layar lain tidak berubah — hanya lapisan transport yang bertambah.

### Trafik real-time — catatan jujur
- Perintah `monitor-traffic` bersifat streaming terus-menerus; lewat API
  biner sebenarnya bisa (balasan `!re` beruntun sampai `/cancel`), tapi
  untuk v0.1 MikroKu menghitung laju trafik dari **selisih counter**
  `rx-byte`/`tx-byte` pada `/interface/print` antar polling (default
  2 detik, bisa diatur). Lebih sederhana, hemat baterai, dan hasilnya
  setara untuk monitoring. Streaming monitor-traffic jadi kandidat v0.4.

## Arsitektur aplikasi

- Kotlin native + Jetpack Compose, arsitektur lapis sederhana:
  UI (Compose) → ViewModel → Repository → `RouterOsClient`
  (implementasi v0.1: `BinaryApiClient`; v0.2+: `RestRouterOsClient`).
- Transport v0.1: `Socket`/`SSLSocket` Kotlin murni + codec sentence/word
  sendiri, satu koneksi persisten per router yang sedang dibuka, perintah
  dijalankan serial dengan `.tag` untuk mencocokkan balasan. Timeout
  konek/baca eksplisit + batas ukuran word dari server (anti hang/OOM).
  Polling dengan coroutine Flow, berhenti otomatis saat layar tidak tampil
  / aplikasi di background; koneksi ditutup saat keluar dari layar router.
- Penyimpanan: daftar router di DataStore; password terenkripsi dengan
  Android Keystore (AES-GCM), tidak pernah ditulis plaintext, tidak pernah
  masuk log. Ada penangkap crash lokal (pola RuangApung: log bisa disalin user).
- Sertifikat api-ssl self-signed: diterima hanya setelah user menyetujui
  sidik jari sertifikat saat pertama kali konek (TOFU — trust on first
  use); perubahan sidik jari setelahnya memicu peringatan, bukan
  diam-diam diterima. api-ssl juga bisa jalan tanpa sertifikat di router
  (mode tanpa verifikasi) — dalam mode itu aplikasi memberi tahu user
  bahwa koneksi tidak terverifikasi, bukan mengklaim aman.
- Izin Android target: hanya `INTERNET` + `ACCESS_NETWORK_STATE` (2 izin).
- Toolchain (disamakan dengan RuangKlon): Gradle 8.14.5, AGP 8.13.2,
  JDK 21, compileSdk/targetSdk 35, minSdk 26, arm64 (+armv7 bila ringan).
- Build **hanya** via GitHub Actions CI (Gradle tidak bisa jalan di VM Muse —
  loopback JVM rusak, lihat AGENTS.md). Keystore di-pin sejak rilis pertama
  (secret repo, pola skill android_ci_apk_signing): rilis via GitHub Release
  dengan ukuran byte + SHA-256, verifikasi signer pakai `apksigner` resmi.

## Target akhir: dashboard admin penuh

Target akhirnya **admin penuh setara pekerjaan harian di WinBox**, bukan
sekadar monitoring — karena API biner membuka (hampir) semua menu console,
cakupan akhirnya ditentukan oleh policy user API di router, bukan oleh
protokolnya. Cakupan menu yang dikejar sampai v0.6:

- **System:** identity, clock, resource, health, reboot/shutdown, scheduler,
  script, logging, users & groups, packages, backup/export, file manager.
- **Interfaces:** ethernet, wireless (+ registration table), bridge + port,
  VLAN, bonding, PPP — lihat, enable/disable, edit, tambah/hapus.
- **IP:** addresses, DHCP client/server/leases, DNS, pool, ARP, routes,
  services, cloud DDNS, neighbors.
- **Firewall:** filter, NAT, mangle, address list — CRUD penuh + geser
  urutan rule.
- **Hotspot:** server, user, profile, user profile, sesi aktif, walled garden.
- **PPP:** profile, secret, sesi aktif.
- **Queues:** simple queue + queue tree (lihat + edit dasar).
- **Tools:** ping, traceroute, torch, bandwidth test, netwatch, profile.
- **Konsol API:** kirim perintah API mentah untuk hal yang belum ada
  layarnya (jaring pengaman supaya tidak ada menu yang benar-benar buntu).

Dua profil user API yang didukung panduan in-app:
1. **Monitoring** — group policy `read,api` (cukup untuk v0.1, paling aman).
2. **Admin penuh** — group policy `read,write,api,test,policy` (dibutuhkan
   mulai fase CRUD; aplikasi menandai aksi yang butuh policy lebih tinggi).

Pengaman aksi admin: konfirmasi dua langkah untuk aksi destruktif
(hapus rule, reboot, shutdown, reset), saklar "Mode Admin" di profil
router (default mati di v0.1), dan semua aksi tulis tercatat di log
aktivitas lokal aplikasi.

Batas jujur: bukan klon WinBox 100% — hal yang memang tidak diekspos API
(mis. tampilan jendela WinBox itu sendiri, drag-and-drop visual) tidak
dikejar. Semua yang bisa dilakukan console/API adalah target realistis.

## Roadmap bertahap (dikunci dari pilihan user)

### v0.1 — Monitoring (rilis pertama)
- Daftar router: tambah/edit/hapus, nama, host/IP, port, user, password,
  label lokasi; status online/offline per router (tes koneksi).
- Dashboard per router: identitas, board, versi ROS, CPU load, RAM
  (total/free), uptime, jam router — auto-refresh.
  Perintah: `/system/resource/print`, `/system/identity/print`,
  `/system/clock/print`, `/system/health/print` (bila board mendukung —
  tidak semua RB v6 punya sensor suhu/tegangan; field hilang = kartu
  disembunyikan, bukan error).
- Interface: daftar semua interface + status up/down, IP, counter byte,
  laju Rx/Tx hasil hitung selisih, grafik batang mini per interface.
  Perintah: `/interface/print`, `/ip/address/print`.
- Aksi aman pertama: **Reboot** (dengan konfirmasi dua langkah).
  Perintah: `/system/reboot`.
- Panduan koneksi di dalam aplikasi (langkah mengaktifkan service
  api/api-ssl + membuat user khusus ber-policy api).
- Tolok ukur go/no-go: dashboard + trafik tampil benar dari router asli
  user di HP Infinix-nya.

### v0.2 — Inventaris & jaringan (baca + ubah ringan)
- IP address, DHCP server + leases, ARP, routes, DNS static/cache ringkas.
- Firewall filter/NAT/mangle: lihat dulu; enable/disable per rule.
- Dukungan REST untuk router v7 (bila user mulai memegang router v7).

### v0.3 — Kelola pengguna (hotspot & PPPoE)
- Hotspot: user, profile, sesi aktif (putuskan sesi).
- PPPoE: secret + sesi aktif.
- Simple queue: lihat + batasi/ubah limit dasar.
- Tools: ping, traceroute, torch (sekali jalan, hasil ringkas).

### v0.4 — Firewall & IP penuh
- Firewall filter/NAT/mangle/address list: CRUD penuh + geser urutan.
- IP address, routes, DNS, pool: CRUD penuh.
- Grafik historis trafik (data lokal di HP), log router + filter.

### v0.5 — System admin
- Users & groups, scheduler, script (jalankan/edit), logging.
- Backup/export konfigurasi ke file, file manager dasar.
- Notifikasi router down (pemeriksaan berkala opsional, hemat baterai).
- Multi-router dashboard gabungan (semua router dalam satu layar ringkas).

### v0.6 — Genap admin penuh
- Wireless (registration table, access list), bridge/VLAN/bonding edit.
- Tools lengkap: bandwidth test, torch streaming, netwatch.
- Konsol API mentah. Routing dinamis (OSPF/RIP) sebatas lihat + edit dasar.

## Yang TIDAK masuk (sengaja)
- Klon WinBox 100%: hal yang tidak diekspos API/console tidak dikejar
  (lihat batas jujur di atas). Konfigurasi sangat mendalam (BGP/MPLS
  kompleks, CAPsMAN detail) cukup lewat Konsol API, bukan layar khusus.
- Tidak ada server relay/cloud: bila router tidak terjangkau dari HP,
  itu urusan jaringan user (LAN/VPN), bukan dilewatkan server kita.
- Tidak menyimpan kredensial di luar HP, tidak ada akun/login aplikasi.

## Risiko & pertanyaan terbuka
- Versi RouterOS user TERJAWAB 2026-11-13: **v6** (detail minor-nya belum
  diketahui — klien harus menangani login pre/post 6.43 dan parser harus
  toleran field hilang antar minor/board).
- Service api/api-ssl di banyak router lapangan nonaktif secara default —
  panduan in-app harus benar-benar langkah-demi-langkah, dan pesan error
  "tidak bisa konek" harus membedakan: port tertutup (service mati),
  login ditolak (!trap/!fatal), dan jaringan tidak terjangkau.
- Password plaintext di port 8728 (lihat peringatan di atas) — risiko
  diterima user secara sadar untuk LAN, dimitigasi banner + anjuran VPN.
- Tidak semua board v6 melaporkan suhu/tegangan di `/system/health/print`.
- Uji lapangan butuh 1 router v6 asli yang bisa diakses dari HP user —
  inilah tolok ukur go/no-go v0.1; alternatif lab: CHR tidak cocok
  (CHR hanya v7), jadi uji harus ke router fisik user.
