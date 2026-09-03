# Kcum Gallery

Aplikasi Gallery / Photo Viewer Android penuh (Kotlin) - **100% offline, tiada backup awan/sync**.

| Info | Nilai |
|------|-------|
| Package | `com.kcum.gallery` |
| Min SDK | 24 (Android 7.0) |
| Target/Compile SDK | 34 (Android 14) |
| Senibina | MVVM (ViewModel + Repository + Room) |
| Bahasa UI | Bahasa Melayu (default) + English |

## Cara Buka Projek

1. Buka **Android Studio** (Koala/lebih baru disyorkan).
2. `File > Open` dan pilih folder `KcumGallery` ini.
3. Android Studio akan memuat turun Gradle 8.7 secara automatik (wrapper properties disertakan).
   Jika diminta, benarkan **Gradle Sync**.
4. Jalankan pada peranti/emulator (Run > app).

> Nota: Wrapper Gradle kini **lengkap disertakan** (`gradlew`, `gradlew.bat`,
> `gradle/wrapper/gradle-wrapper.jar` untuk Gradle 8.7). Anda tidak perlu menjalankan
> `gradle wrapper` — Android Studio atau `./gradlew` terus berfungsi.

## Build APK dengan GitHub Actions

Fail `.github/workflows/build.yml` disertakan. Setiap kali kod didorong ke cabang `main`
(atau dicetus manual melalui tab **Actions → Build APK → Run workflow**), GitHub akan:

1. Setup JDK 17 (Temurin).
2. Jalankan `./gradlew assembleDebug` — wrapper memuat turun Gradle 8.7 secara automatik.
3. Muat naik APK hasil binaan sebagai artifak **`kcum-gallery-apk`**
   (muat turun dari halaman run Actions, bahagian *Artifacts*).

> Penting: pastikan `gradle/wrapper/gradle-wrapper.jar` (binari ~43KB) dimasukkan ke dalam
> git — ia sudah ada dalam projek ini dan `.gitignore` tidak mengecualikannya.
> APK debug adalah untuk ujian; untuk keluaran, bina APK release bertanda tangan secara
> berasingan (`Build > Generate Signed App Bundle / APK` dalam Android Studio).

## Struktur Projek

```
.github/workflows/build.yml      # CI: Build APK (JDK 17 + assembleDebug + artifak)
app/src/main/java/com/kcum/gallery/
├── KcumApp.kt                    # Application: tema + purge tong sampah 30 hari
├── data/                         # LAPISAN DATA (Repository pattern)
│   ├── MediaItem.kt              # Model media (Parcelable)
│   ├── Album.kt                  # Model album/folder
│   ├── MediaRepository.kt        # MediaStore queries, trash, hide, statistik
│   ├── PrefsRepository.kt        # Keutamaan pengguna (sort/view/grid/lock/lang)
│   ├── TrashItem.kt / TrashDao.kt
│   ├── HiddenItem.kt / HiddenDao.kt
│   └── AppDatabase.kt            # Room DB
├── util/
│   ├── MediaStoreUtils.kt        # Create folder/rename/move/copy (scoped storage)
│   ├── PermissionUtils.kt        # Kebenaran API 33+/partial/Settings redirect
│   ├── SecurityUtils.kt          # PIN hash + BiometricPrompt
│   ├── ExifUtils.kt              # Decode + orientasi EXIF
│   ├── FilterUtils.kt            # ColorMatrix (brightness/contrast/saturation)
│   └── Extensions.kt             # Format saiz/tarikh, share intents
├── viewmodel/
│   ├── GalleryViewModel.kt       # Media/album/timeline + sort/filter
│   ├── TrashViewModel.kt / HiddenViewModel.kt
│   ├── SearchViewModel.kt        # Carian nama/tarikh/jenis/saiz
│   └── SettingsViewModel.kt      # Statistik storan
├── view/
│   ├── MainActivity.kt           # 3 tab + App Lock (PIN timeout)
│   ├── GalleryFragment.kt        # Grid/Senarai/Timeline + pemilihan + operasi
│   ├── AlbumsFragment.kt         # Album auto ikut folder + rename/padam folder
│   ├── SettingsFragment.kt       # Tema/Bahasa/Grid/Lock/Storan/Full access
│   ├── ViewerActivity.kt         # Skrin penuh + zoom + video + slideshow + set-as
│   ├── EditorActivity.kt         # Crop/Rotate/Filter/Draw/Text + simpan MediaStore
│   ├── PinActivity.kt            # PIN setup/verify/change + biometrik
│   ├── HiddenActivity.kt         # Album peribadi
│   ├── TrashActivity.kt          # Tong sampah (restore/padam kekal)
│   ├── SearchActivity.kt         # Carian & tapisan
│   ├── ZoomableImageView.kt      # Pinch-zoom + pan + double-tap
│   ├── CropOverlayView.kt        # Overlay potong interaktif
│   └── DrawView.kt               # Lukis + teks overlay (koordinat normalized)
└── adapter/
    ├── MediaAdapter.kt           # Grid/list + multi-select
    ├── AlbumAdapter.kt / TimelineAdapter.kt
    ├── ViewerPagerAdapter.kt     # ViewPager2 (imej + ExoPlayer video)
    ├── TrashAdapter.kt / HiddenAdapter.kt
```

## Senarai Fungsi

**Paparan & Navigasi**: grid/list toggle, timeline (bulan/tahun), susun tarikh/saiz/nama/jenis,
album auto, penonton skrin penuh (swipe + pinch zoom + double tap), video play/pause/seek/mute
(Media3 ExoPlayer), slideshow (3/5/10 saat).

**Pengurusan Fail**: cipta folder (MediaStore RELATIVE_PATH + SAF custom), padam (tong sampah
30 hari + auto purge), restore, padam kekal, pindah, salin/duplicate, rename fail & folder,
sembunyi/unhide (album peribadi dalaman + PIN/biometrik).

**Edit Asas**: putar 90°, crop (bebas/1:1/4:3/16:9), kecerahan/kontras/ketepuan, lukis/annotasi,
teks overlay boleh diseret. Simpan sebagai SALINAN ke `Pictures/Kcum Gallery` (bukan merosakkan
asal).

**Share & Export**: ACTION_SEND (+MULTIPLE), wallpaper (WallpaperManager), foto kenalan
(ACTION_ATTACH_DATA), eksport ke mana-mana melalui SAF (ACTION_CREATE_DOCUMENT).

**Search & Filter**: nama fail, julat tarikh (DatePicker), jenis (gambar/video), saiz fail.

**Keselamatan**: PIN (SHA-256 + salt), biometrik (BiometricPrompt), album peribadi berasingan,
timeout kunci app (segera/1min/5min/tidak pernah), backup Android dihalang untuk folder
`hidden/` dan `trash/` (lihat `backup_rules.xml`).

**Tetapan**: tema gelap/terahang/sistem, bahasa BM/EN (per-app locale), grid 2/3/4 lajur,
tempoh slideshow, statistik storan, akses penuh storan (opsyenal).

## Nota Teknikal Penting

### Cipta folder pada Android 10+ (Scoped Storage)
MediaStore tiada API "mkdir" terus. Teknik yang digunakan (lihat `MediaStoreUtils.createFolderViaMediaStore`):
masukkan entri sementara dengan `RELATIVE_PATH = "Pictures/<nama>"` + `IS_PENDING=1`, tulis bait
placeholder, tandakan `IS_PENDING=0`, kemudian padam entri tersebut. Direktori kekal pada
kebanyakan peranti (MediaProvider tidak memadam direktori kosong serta-merta), dan walaupun
dibersihkan ia akan diwujudkan semula apabila fail pertama dialih masuk. Pilihan kedua: lokasi
custom melalui **SAF** (`ACTION_OPEN_DOCUMENT_TREE` + `DocumentsContract.createDocument`).

### MANAGE_EXTERNAL_STORAGE
Dinyatakan dalam manifest tetapi **TIDAK diminta secara lalai**. Boleh diaktifkan manual dari
Tetapan dalam app. **Risiko** (diterangkan juga dalam kod): privasi (akses semua fail), dasar
Google Play (app galeri biasa biasanya ditolak), kepercayaan pengguna. Kebanyakan fungsi tidak
memerlukannya - MediaStore + SAF mencukupi.

### Kebenaran media
- Android 13+ : `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` (+ `READ_MEDIA_VISUAL_USER_SELECTED`
  untuk akses separa Android 14).
- Android 12 ke bawah: `READ_EXTERNAL_STORAGE`.
- Ditolak kekal → dialog → redirect ke Settings app (`ACTION_APPLICATION_DETAILS_SETTINGS`).

### Tong sampah & album peribadi
Kedua-duanya menggunakan storan dalaman app (`filesDir/trash`, `filesDir/hidden`) + metadata
Room. Ini mengekalkan keserasian scoped storage sepenuhnya: app ini "memiliki" fail tersebut,
jadi tiada kebenaran tambahan diperlukan untuk restore/unhide.

### PIN
Hash SHA-256 dengan salt rawak disimpan dalam SharedPreferences. Untuk produksi kelas
tinggi, pertimbangkan Android Keystore / `EncryptedSharedPreferences`.

## Dependensi Utama

AndroidX Core/AppCompat/Material 3, Room 2.6.1 (kapt), Glide 4.16, Media3 ExoPlayer 1.4.1,
Biometric 1.1.0, ExifInterface, Coroutines, ViewPager2, DocumentFile (SAF).

Tiada dependensi awan/sync/iklan.
