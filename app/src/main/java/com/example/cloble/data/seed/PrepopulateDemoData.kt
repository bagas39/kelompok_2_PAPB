package com.example.cloble.data.seed

import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.entity.ChatMessageEntity
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.InterestEntity
import com.example.cloble.data.local.entity.MatchEntity
import com.example.cloble.data.local.entity.NotificationEntity
import com.example.cloble.data.local.entity.ReviewEntity
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationType
import com.example.cloble.domain.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PrepopulateDemoData {

    private val seedMutex = Mutex()

    suspend fun seedDatabase(database: CloBleDatabase, force: Boolean = false) = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            val userCount = database.userDao().countUsers()
            if (userCount > 0 && !force) {
                return@withContext
            }

            val baseTime = System.currentTimeMillis() - 86400000L * 7
            val demoPassword = "CloBle123!"

            val rakaId = database.userDao().insertUser(
                UserEntity(
                    name = "Raka Pratama",
                    email = "raka@cloble.demo",
                    passwordHash = PasswordHasher.hash(demoPassword),
                    bio = "Penggemar kemeja linen, streetwear & casual vintage.",
                    city = "Bandung, Jawa Barat",
                    latitude = -6.9175,
                    longitude = 107.6191,
                    preferredSize = "M",
                    preferredGender = "Uniseks",
                    preferredRadiusKm = 25,
                    createdAt = baseTime
                )
            )

            val nayaId = database.userDao().insertUser(
                UserEntity(
                    name = "Naya",
                    email = "naya@cloble.demo",
                    passwordHash = PasswordHasher.hash(demoPassword),
                    bio = "Minimalist closet curator. Suka thrifting bahan katun & linen.",
                    city = "Bandung, Jawa Barat",
                    latitude = -6.8915,
                    longitude = 107.6107,
                    preferredSize = "L",
                    preferredGender = "Uniseks",
                    preferredRadiusKm = 10,
                    createdAt = baseTime + 1000L
                )
            )

            val bagasId = database.userDao().insertUser(
                UserEntity(
                    name = "Bagas",
                    email = "bagas@cloble.demo",
                    passwordHash = PasswordHasher.hash(demoPassword),
                    bio = "Suka kemeja flannel & jaket denim casual.",
                    city = "Cimahi, Jawa Barat",
                    latitude = -6.8722,
                    longitude = 107.5425,
                    preferredSize = "M",
                    preferredGender = "Pria",
                    preferredRadiusKm = 25,
                    createdAt = baseTime + 2000L
                )
            )

            val dindaId = database.userDao().insertUser(
                UserEntity(
                    name = "Dinda",
                    email = "dinda@cloble.demo",
                    passwordHash = PasswordHasher.hash(demoPassword),
                    bio = "Preloved outfit berkualitas, jarang dipakai.",
                    city = "Jakarta",
                    latitude = -6.2088,
                    longitude = 106.8456,
                    preferredSize = "M",
                    preferredGender = "Wanita",
                    preferredRadiusKm = 50,
                    createdAt = baseTime + 3000L
                )
            )

            val kemejaLinenKremId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = rakaId,
                    name = "Kemeja linen krem",
                    description = "Kemeja linen premium warna krem santai, potongan reguler fit nyaman.",
                    category = "Atasan",
                    size = "L",
                    gender = "Uniseks",
                    color = "Krem",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1596755094514-f87e34085b2c",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 10000L,
                    updatedAt = baseTime + 10000L
                )
            )

            val jaketDenimBiruId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = rakaId,
                    name = "Jaket denim biru",
                    description = "Jaket denim tebal 14oz wash medium blue.",
                    category = "Luaran",
                    size = "L",
                    gender = "Pria",
                    color = "Biru",
                    condition = "Baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1576871337622-98d48d1cf531",
                    isBarterAllowed = false,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 11000L,
                    updatedAt = baseTime + 11000L
                )
            )

            val kaosGarisNavyId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = rakaId,
                    name = "Kaos garis navy",
                    description = "Kaos motif garis navy katun combed adem.",
                    category = "Atasan",
                    size = "L",
                    gender = "Uniseks",
                    color = "Biru",
                    condition = "Baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1521572267360-ee0c2909d518",
                    isBarterAllowed = false,
                    status = ItemStatus.EXCHANGED,
                    createdAt = baseTime + 12000L,
                    updatedAt = baseTime + 20000L
                )
            )

            val hoodieHitamId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = nayaId,
                    name = "Hoodie hitam",
                    description = "Hoodie hitam fleece tebal dan lembut, fit oversized.",
                    category = "Luaran",
                    size = "M",
                    gender = "Uniseks",
                    color = "Hitam",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1556905055-8f358a7a47b2",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 13000L,
                    updatedAt = baseTime + 13000L
                )
            )

            val celanaJeansBiruId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = nayaId,
                    name = "Celana jeans biru",
                    description = "Celana jeans model lurus klasik warna biru wash.",
                    category = "Bawahan",
                    size = "M",
                    gender = "Uniseks",
                    color = "Biru",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1541099649105-f69ad21f3246",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 14000L,
                    updatedAt = baseTime + 14000L
                )
            )

            val kemejaPutihId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = nayaId,
                    name = "Kemeja putih",
                    description = "Kemeja putih katun clean look untuk kantor atau kuliah.",
                    category = "Atasan",
                    size = "M",
                    gender = "Wanita",
                    color = "Putih",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1551028719-00167b16eac5",
                    isBarterAllowed = false,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 15000L,
                    updatedAt = baseTime + 15000L
                )
            )

            val cardiganRajutAbuId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = nayaId,
                    name = "Cardigan rajut abu",
                    description = "Cardigan rajut abu lembut hangat.",
                    category = "Luaran",
                    size = "M",
                    gender = "Uniseks",
                    color = "Abu-abu",
                    condition = "Baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1434389677669-e08b4cac3105",
                    isBarterAllowed = false,
                    status = ItemStatus.EXCHANGED,
                    createdAt = baseTime + 16000L,
                    updatedAt = baseTime + 20000L
                )
            )

            val outerKatunHijauId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = nayaId,
                    name = "Outer katun hijau",
                    description = "Outer santai katun warna sage green adem.",
                    category = "Luaran",
                    size = "M",
                    gender = "Uniseks",
                    color = "Hijau",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1572804013309-59a88b7e92f1",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 17000L,
                    updatedAt = baseTime + 17000L
                )
            )

            val sweaterRajutKremId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = bagasId,
                    name = "Sweater rajut krem",
                    description = "Sweater rajut warna krem tebal hangat.",
                    category = "Luaran",
                    size = "L",
                    gender = "Pria",
                    color = "Krem",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1548883354-7622d03aca27",
                    isBarterAllowed = false,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 18000L,
                    updatedAt = baseTime + 18000L
                )
            )

            val kemejaOxfordBiruId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = bagasId,
                    name = "Kemeja oxford biru",
                    description = "Kemeja oxford warna biru muda formal kasual.",
                    category = "Atasan",
                    size = "L",
                    gender = "Pria",
                    color = "Biru",
                    condition = "Baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf",
                    isBarterAllowed = false,
                    status = ItemStatus.EXCHANGED,
                    createdAt = baseTime + 19000L,
                    updatedAt = baseTime + 25000L
                )
            )

            val kemejaFlanelHijauId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = dindaId,
                    name = "Kemeja flanel hijau",
                    description = "Kemeja flanel kotak hijau tua bahan lembut.",
                    category = "Atasan",
                    size = "M",
                    gender = "Uniseks",
                    color = "Hijau",
                    condition = "Baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 20000L,
                    updatedAt = baseTime + 20000L
                )
            )

            val blazerHitamKasualId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = dindaId,
                    name = "Blazer hitam kasual",
                    description = "Blazer hitam kasual fit semi crop elegan.",
                    category = "Luaran",
                    size = "M",
                    gender = "Wanita",
                    color = "Hitam",
                    condition = "Sangat baik",
                    mainPhotoUri = "https://images.unsplash.com/photo-1591047139829-d91aecb6caea",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE,
                    createdAt = baseTime + 21000L,
                    updatedAt = baseTime + 21000L
                )
            )

            val rokPlisketCokelatId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = dindaId,
                    name = "Rok plisket cokelat",
                    description = "Rok plisket panjang warna cokelat muda manis.",
                    category = "Bawahan",
                    size = "M",
                    gender = "Wanita",
                    color = "Krem",
                    condition = "Layak pakai",
                    mainPhotoUri = "https://images.unsplash.com/photo-1583496661160-fb5886a0aaaa",
                    isBarterAllowed = false,
                    status = ItemStatus.EXCHANGED,
                    createdAt = baseTime + 22000L,
                    updatedAt = baseTime + 25000L
                )
            )

            val interest1Id = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = rakaId,
                    receiverUserId = nayaId,
                    targetItemId = hoodieHitamId,
                    status = InterestStatus.PENDING,
                    createdAt = baseTime + 30000L,
                    respondedAt = null
                )
            )

            val interestCrossId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = nayaId,
                    receiverUserId = rakaId,
                    targetItemId = kemejaLinenKremId,
                    status = InterestStatus.PENDING,
                    createdAt = baseTime + 31000L,
                    respondedAt = null
                )
            )

            val interestDindaId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = rakaId,
                    receiverUserId = dindaId,
                    targetItemId = blazerHitamKasualId,
                    status = InterestStatus.PENDING,
                    createdAt = baseTime + 32000L,
                    respondedAt = null
                )
            )

            val interestDemoActiveId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = dindaId,
                    receiverUserId = nayaId,
                    targetItemId = outerKatunHijauId,
                    status = InterestStatus.ACCEPTED_MATCHED,
                    createdAt = baseTime + 33000L,
                    respondedAt = baseTime + 34000L
                )
            )

            val interestHistCompletedId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = nayaId,
                    receiverUserId = rakaId,
                    targetItemId = kaosGarisNavyId,
                    status = InterestStatus.ACCEPTED_MATCHED,
                    createdAt = baseTime + 35000L,
                    respondedAt = baseTime + 36000L
                )
            )

            val interestHistBagasDindaId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = bagasId,
                    receiverUserId = dindaId,
                    targetItemId = rokPlisketCokelatId,
                    status = InterestStatus.ACCEPTED_MATCHED,
                    createdAt = baseTime + 37000L,
                    respondedAt = baseTime + 38000L
                )
            )

            val interestHistCancelledId = database.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = bagasId,
                    receiverUserId = dindaId,
                    targetItemId = blazerHitamKasualId,
                    status = InterestStatus.ACCEPTED_MATCHED,
                    createdAt = baseTime + 39000L,
                    respondedAt = baseTime + 40000L
                )
            )

            val matchDemoActiveId = database.interestMatchDao().insertMatch(
                MatchEntity(
                    sourceInterestId = interestDemoActiveId,
                    user1Id = nayaId,
                    user1ItemId = outerKatunHijauId,
                    user2Id = dindaId,
                    user2ItemId = kemejaFlanelHijauId,
                    status = MatchStatus.ACTIVE,
                    meetingLocation = null,
                    createdAt = baseTime + 34000L,
                    updatedAt = baseTime + 34000L
                )
            )

            val matchHistCompletedId = database.interestMatchDao().insertMatch(
                MatchEntity(
                    sourceInterestId = interestHistCompletedId,
                    user1Id = rakaId,
                    user1ItemId = kaosGarisNavyId,
                    user2Id = nayaId,
                    user2ItemId = cardiganRajutAbuId,
                    status = MatchStatus.COMPLETED,
                    meetingLocation = "Taman Balai Kota Bandung",
                    createdAt = baseTime + 36000L,
                    updatedAt = baseTime + 50000L
                )
            )

            val matchHistDindaBagasId = database.interestMatchDao().insertMatch(
                MatchEntity(
                    sourceInterestId = interestHistBagasDindaId,
                    user1Id = dindaId,
                    user1ItemId = rokPlisketCokelatId,
                    user2Id = bagasId,
                    user2ItemId = kemejaOxfordBiruId,
                    status = MatchStatus.COMPLETED,
                    meetingLocation = "Stasiun Bandung",
                    createdAt = baseTime + 38000L,
                    updatedAt = baseTime + 55000L
                )
            )

            val matchHistCancelledId = database.interestMatchDao().insertMatch(
                MatchEntity(
                    sourceInterestId = interestHistCancelledId,
                    user1Id = dindaId,
                    user1ItemId = blazerHitamKasualId,
                    user2Id = bagasId,
                    user2ItemId = sweaterRajutKremId,
                    status = MatchStatus.CANCELLED,
                    meetingLocation = null,
                    createdAt = baseTime + 40000L,
                    updatedAt = baseTime + 42000L
                )
            )

            database.chatDao().insertAllMessages(
                listOf(
                    ChatMessageEntity(
                        matchId = matchDemoActiveId,
                        senderId = dindaId,
                        messageText = "Halo Naya! Boleh tahu detail bahan outernya?",
                        isRead = true,
                        sentAt = baseTime + 34100L
                    ),
                    ChatMessageEntity(
                        matchId = matchDemoActiveId,
                        senderId = nayaId,
                        messageText = "Halo Dinda! Bahannya katun adem, warnanya sage green lembut ya.",
                        isRead = true,
                        sentAt = baseTime + 34200L
                    ),
                    ChatMessageEntity(
                        matchId = matchHistCompletedId,
                        senderId = nayaId,
                        messageText = "Halo Raka, barter kaos dan cardigan sudah cocok ya.",
                        isRead = true,
                        sentAt = baseTime + 36100L
                    ),
                    ChatMessageEntity(
                        matchId = matchHistCompletedId,
                        senderId = rakaId,
                        messageText = "Iya Naya, terima kasih banyak! Sampai ketemu di Balai Kota.",
                        isRead = true,
                        sentAt = baseTime + 36200L
                    ),
                    ChatMessageEntity(
                        matchId = matchHistDindaBagasId,
                        senderId = bagasId,
                        messageText = "COD di Stasiun Bandung jam 4 sore ya.",
                        isRead = true,
                        sentAt = baseTime + 38100L
                    ),
                    ChatMessageEntity(
                        matchId = matchHistDindaBagasId,
                        senderId = dindaId,
                        messageText = "Oke siap Bagas.",
                        isRead = true,
                        sentAt = baseTime + 38200L
                    ),
                    ChatMessageEntity(
                        matchId = matchHistCancelledId,
                        senderId = bagasId,
                        messageText = "Maaf Dinda, saya ada urusan mendadak jadi batalkan barter ini dulu ya.",
                        isRead = true,
                        sentAt = baseTime + 41000L
                    )
                )
            )

            database.reviewDao().insertReview(
                ReviewEntity(
                    matchId = matchHistCompletedId,
                    reviewerId = nayaId,
                    revieweeId = rakaId,
                    rating = 5,
                    comment = "Kaos sangat bersih dan sesuai deskripsi, komunikasi lancar.",
                    createdAt = baseTime + 51000L
                )
            )

            database.reviewDao().insertReview(
                ReviewEntity(
                    matchId = matchHistDindaBagasId,
                    reviewerId = bagasId,
                    revieweeId = dindaId,
                    rating = 2,
                    comment = "Ukuran rok sedikit berbeda dari deskripsi dan datang terlambat 30 menit.",
                    createdAt = baseTime + 56000L
                )
            )

            database.notificationDao().insertAll(
                listOf(
                    NotificationEntity(
                        userId = nayaId,
                        type = NotificationType.INCOMING_INTEREST,
                        title = "Raka Pratama tertarik pada Hoodie hitam",
                        body = "Buka lemari Raka Pratama dan pilih tepat satu pakaian berizin barter.",
                        relatedInterestId = interest1Id,
                        relatedMatchId = null,
                        isRead = false,
                        createdAt = baseTime + 30100L
                    ),
                    NotificationEntity(
                        userId = rakaId,
                        type = NotificationType.INCOMING_INTEREST,
                        title = "Naya tertarik pada Kemeja linen krem",
                        body = "Buka lemari Naya dan pilih tepat satu pakaian berizin barter.",
                        relatedInterestId = interestCrossId,
                        relatedMatchId = null,
                        isRead = false,
                        createdAt = baseTime + 31100L
                    ),
                    NotificationEntity(
                        userId = dindaId,
                        type = NotificationType.INCOMING_INTEREST,
                        title = "Raka Pratama tertarik pada Blazer hitam kasual",
                        body = "Buka lemari Raka Pratama dan pilih tepat satu pakaian berizin barter.",
                        relatedInterestId = interestDindaId,
                        relatedMatchId = null,
                        isRead = false,
                        createdAt = baseTime + 32100L
                    ),
                    NotificationEntity(
                        userId = nayaId,
                        type = NotificationType.MATCH_VERIFIED,
                        title = "Match terverifikasi dengan Dinda",
                        body = "Outer katun hijau ↔ Kemeja flanel hijau siap didiskusikan di ruang Chat.",
                        relatedInterestId = interestDemoActiveId,
                        relatedMatchId = matchDemoActiveId,
                        isRead = true,
                        createdAt = baseTime + 34100L
                    ),
                    NotificationEntity(
                        userId = dindaId,
                        type = NotificationType.MATCH_VERIFIED,
                        title = "Match terverifikasi dengan Naya",
                        body = "Outer katun hijau ↔ Kemeja flanel hijau siap didiskusikan di ruang Chat.",
                        relatedInterestId = interestDemoActiveId,
                        relatedMatchId = matchDemoActiveId,
                        isRead = true,
                        createdAt = baseTime + 34100L
                    )
                )
            )
        }
    }
}
