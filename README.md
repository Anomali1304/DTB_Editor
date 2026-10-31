# DTB Editor

Aplikasi Android untuk membuka, mengedit topologi CPU dan sumber DTS, serta menyimpan kembali file vendor_boot.img atau DTB / DT Table.

## Cara Build

1. Pastikan lingkungan Android SDK sudah terkonfigurasi.
2. Jalankan perintah Gradle:
   `gradle :app:assembleDebug`

## Cara Pakai

1. Buka aplikasi, lalu pilih file `vendor_boot.img` atau file Device Tree (`.dtb`).
2. Gunakan tab **CPU** untuk mengatur topologi core dan slot domain cpufreq, atau tab **DTS** untuk mengedit kode sumber langsung.
3. Tekan tombol **Simpan** di bilah atas untuk menulis hasil modifikasi ke file baru.
