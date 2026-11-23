<div align="center">

# 👕 CloBle — Clothing Barter App

**Aplikasi Android berbasis Kotlin dan Supabase (PostgreSQL) untuk barter pakaian preloved dengan sistem swipe & match berbasis lokasi.**
</div>

---

## 📖 Tentang CloBle
**CloBle** adalah platform *mobile* berbasis Android yang mempermudah pengguna untuk bertukar pakaian (*barter*) secara langsung dengan pengguna lain di sekitar mereka. Alih-alih menjual atau membeli, pengguna dapat mengelola lemari digital (*wardrobe*), menemukan pakaian sesuai preferensi ukuran dan jarak lokasi, mengajukan ketertarikan (*interest*), berdiskusi lewat *real-time chat*, hingga menyelesaikan pertukaran dan saling memberikan ulasan.

Aplikasi ini sendiri mengambil ide yang terinspirasi dari Kelompok "Sidoarjo Hebat Team" dari Institut Teknologi Sepuluh Nopember, yang memenangkan Juara 1 di UI/UX National Competition Prog{r}amming 8.2 diselenggarakan oleh Universitas Prasetiya Mulya. 
---

## Fitur Utama

- **🔐 Autentikasi & Keamanan Sesi**
- **👗 Manajemen Wardrobe**
- **📍 Discovery & Filter Berbasis Lokasi**
- **🤝 Sistem Minat & Explicit Match Barter**
- **💬 Real-Time Chat + Offline Cache**
- **🔔 Notifikasi Real-Time & Push Notification**
- **⭐ Profil, Riwayat Aktivitas & Ulasan**
---

## 🛠️ Tech Stack & Arsitektur

Aplikasi ini dibangun menggunakan arsitektur berlapis (**Data – Domain – UI**) dengan pola **MVVM** (`ViewModel` + `StateFlow`) dan *manual dependency injection* (`AppContainer`).

| Lapisan | Teknologi / Library |
| :--- | :--- |
| **UI & Navigasi** | Jetpack Compose (Material 3), Navigation Compose, Coil, Custom Font (Inter) |
| **State & DI** | AndroidX Lifecycle ViewModel, Kotlin Coroutines & Flow, Manual DI (`AppContainer`) |
| **Local Storage** | Room Database (KSP), Jetpack DataStore Preferences, Encrypted Session Cipher |
| **Networking & Backend** | OkHttp, Supabase (REST/RPC, Realtime WebSocket, PostgreSQL RLS, Edge Functions) |
| **Peta & Lokasi** | OSMDroid (`osmdroid-android`), Nominatim Geocoder |
| **Notifikasi** | Firebase Cloud Messaging (`firebase-messaging`) |
| **Testing** | JUnit 4, Kotlinx Coroutines Test, Espresso, Compose UI Test, Python (Supabase integration tests) |

### 📂 Struktur Direktori

```text
kelompok_2_PAPB/
├── app/src/main/java/com/example/cloble/
│   ├── data/
│   │   ├── local/           # Room DB, DAO, Entities, TypeConverters, DataStore & Cipher
│   │   ├── remote/          # Supabase Client, Realtime, Repositories, Mappers, Nominatim
│   │   └── repository/      # Offline cache mappers
│   ├── di/                  # AppContainer (Dependency Injection)
│   ├── domain/
│   │   ├── model/           # Domain models & Enums
│   │   ├── repository/      # Repository interfaces
│   │   ├── result/          # Domain result wrappers
│   │   └── util/            # Business utilities (e.g., ChatThreadDeduplicator)
│   ├── service/             # CloBleFirebaseMessagingService
│   ├── ui/
│   │   ├── components/      # Reusable Compose components (Bars, Cards, Dialogs, Map Picker)
│   │   ├── navigation/      # CloBleDestinations & CloBleNavGraph
│   │   ├── screens/         # auth, chat, discover, history, matched, notifications, preferences, profile, review, wardrobe
│   │   └── theme/           # Material 3 Color, Typography, Theme
│   └── util/                # SystemNotificationHelper
└── supabase/
    ├── functions/           # Edge Functions (push-notification, send-password-reset-otp)
    ├── migrations/          # SQL migrations (RLS, RPC-first hardening, parallel barter, optimization)
    └── tests/               # Integration tests (parallel_barter, explicit_match_confirmation)
```

---

## ⚙️ Prasyarat & Konfigurasi Proyek

### Prasyarat
- **Android Studio** versi terbaru yang mendukung **AGP 9.3.3** & **Kotlin 2.2.10**
- **JDK 11** atau lebih baru
- **Android SDK** (`compileSdk` & `targetSdk` 37, `minSdk` 24)
- Project **Supabase** & Project **Firebase** (untuk FCM)

### Langkah Instalasi

1. **Clone repositori**
   ```bash
   git clone https://github.com/<username>/kelompok_2_PAPB.git
   cd kelompok_2_PAPB
   ```

2. **Konfigurasi Supabase di `local.properties`**
   Tambahkan kredensial Supabase pada file `local.properties` di *root* proyek (atau set melalui *environment variables* `SUPABASE_URL` & `SUPABASE_KEY`):
   ```properties
   supabase.url=https://<your-project-ref>.supabase.co
   supabase.key=<your-supabase-anon-key>
   ```
   > **Catatan:** Build Gradle akan gagal jika `supabase.url` dan `supabase.key` belum dikonfigurasi.

3. **Konfigurasi Firebase (FCM)**
   Letakkan file `google-services.json` dari Firebase Console ke dalam direktori `app/`.

4. **Setup Backend Supabase (Opsional jika menggunakan instance baru)**
   - Jalankan seluruh migrasi SQL di folder `supabase/migrations/` ke database Supabase.
   - Deploy Edge Functions di `supabase/functions/push-notification` dan `supabase/functions/send-password-reset-otp`.

5. **Build & Jalankan Aplikasi**
   Jalankan langsung melalui Android Studio atau via terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   *(Di Windows gunakan `gradlew.bat assembleDebug`)*

---
<div align="center">



</div>
