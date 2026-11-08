package com.example.cloble.ui.screens.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cloble.ui.components.BarterEligibilitySwitchCard
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.components.CloBleChoiceChipRow
import com.example.cloble.ui.components.CloBleDropdownField
import com.example.cloble.ui.components.CloBleIcons
import com.example.cloble.ui.components.CloBleInfoBoxTone
import com.example.cloble.ui.components.CloBleLabeledTextField
import com.example.cloble.ui.components.CloBleOutlinedButton
import com.example.cloble.ui.components.CloBlePrimaryButton
import com.example.cloble.ui.components.CloBleStatusInfoBox
import com.example.cloble.ui.components.CloBleTopAppBar
import com.example.cloble.ui.components.CloBleTopBarAction
import com.example.cloble.ui.components.ClothingPhotoSlot
import com.example.cloble.ui.components.WardrobeBarterBadge
import com.example.cloble.ui.components.WardrobeItemCard
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTheme

@Composable
fun DesignSystemShowcaseScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(CloBleBottomTab.WARDROBE) }
    var isBarterAllowed by rememberSaveable { mutableStateOf(false) }
    var primaryClickCount by rememberSaveable { mutableIntStateOf(0) }
    var lastInteractionMessage by rememberSaveable {
        mutableStateOf("Status awal: Switch izin barter nonaktif (default koleksi pribadi).")
    }

    var itemName by rememberSaveable { mutableStateOf("Kemeja linen krem") }
    var itemDescription by rememberSaveable {
        mutableStateOf("Kemeja linen lengan panjang. Bahan adem, bersih tanpa noda.")
    }
    var selectedSize by rememberSaveable { mutableStateOf("M") }
    var selectedGender by rememberSaveable { mutableStateOf("Uniseks") }
    var selectedChipSizes by rememberSaveable { mutableStateOf(listOf("M")) }
    var isFirstCardSelected by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = CloBleColors.Background,
        topBar = {
            CloBleTopAppBar(
                title = "Lemari & Design System",
                actions = listOf(
                    CloBleTopBarAction(
                        icon = CloBleIcons.FilterFunnel,
                        contentDescription = "Filter pakaian",
                        tint = CloBleColors.Primary,
                        testTag = "top_bar_filter_action",
                        onClick = {
                            lastInteractionMessage = "Aksi Filter pakaian ditekan."
                        }
                    ),
                    CloBleTopBarAction(
                        icon = CloBleIcons.Bell,
                        contentDescription = "Notifikasi",
                        tint = CloBleColors.TextPrimary,
                        showBadgeDot = true,
                        testTag = "top_bar_notification_action",
                        onClick = {
                            lastInteractionMessage = "Aksi Notifikasi ditekan."
                        }
                    )
                )
            )
        },
        bottomBar = {
            CloBleBottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                    lastInteractionMessage = "Tab navigasi berpindah ke ${tab.label}."
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("showcase_scroll_container"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CloBle",
                        style = MaterialTheme.typography.headlineLarge,
                        color = CloBleColors.TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.titleLarge,
                            color = CloBleColors.StarRating
                        )
                        Text(
                            text = "4.9 · Bandung",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.TextSecondary
                        )
                    }
                }
                Text(
                    text = "Tukar pakaian, bukan beli.",
                    style = MaterialTheme.typography.titleMedium,
                    color = CloBleColors.TextPrimary
                )
                Text(
                    text = "Validasi Fase 1 Skenario B: pengguna bebas mengelola izin barter tiap pakaian di Lemari.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CloBleColors.TextSecondary
                )
            }

            BarterEligibilitySwitchCard(
                isBarterAllowed = isBarterAllowed,
                onBarterAllowedChange = { checked ->
                    isBarterAllowed = checked
                    lastInteractionMessage = if (checked) {
                        "Switch izin barter AKTIF: Diizinkan barter."
                    } else {
                        "Switch izin barter NONAKTIF: Tidak untuk barter."
                    }
                }
            )

            CloBleStatusInfoBox(
                title = "Status Interaksi & Skenario B",
                message = lastInteractionMessage,
                tone = if (isBarterAllowed) CloBleInfoBoxTone.HIGHLIGHT else CloBleInfoBoxTone.INFO,
                modifier = Modifier.testTag("interaction_status_box")
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CloBlePrimaryButton(
                    text = "Tambah pakaian (${primaryClickCount}x)",
                    leadingSymbol = "+",
                    accessibilityLabel = "Tombol Tambah pakaian",
                    modifier = Modifier.testTag("primary_add_clothing_button"),
                    onClick = {
                        primaryClickCount += 1
                        lastInteractionMessage = "Tombol utama ditekan $primaryClickCount kali."
                    }
                )
                CloBleOutlinedButton(
                    text = "Ubah / tambah foto",
                    accessibilityLabel = "Tombol Ubah atau tambah foto",
                    modifier = Modifier.testTag("outlined_manage_photo_button"),
                    onClick = {
                        lastInteractionMessage = "Tombol sekunder Ubah / tambah foto ditekan."
                    }
                )
            }

            HorizontalDivider(thickness = 1.dp, color = CloBleColors.Outline)

            Text(
                text = "Form & Kriteria Pakaian",
                style = MaterialTheme.typography.titleMedium,
                color = CloBleColors.TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClothingPhotoSlot(
                    label = "Foto utama",
                    isSavedPlaceholder = true,
                    modifier = Modifier.weight(1f),
                    onClick = { lastInteractionMessage = "Slot Foto utama dipilih." }
                )
                ClothingPhotoSlot(
                    label = "+ Foto detail",
                    isSavedPlaceholder = false,
                    modifier = Modifier.weight(1f),
                    onClick = { lastInteractionMessage = "Slot Foto detail dipilih." }
                )
                ClothingPhotoSlot(
                    label = "+ Foto lain",
                    isSavedPlaceholder = false,
                    modifier = Modifier.weight(1f),
                    onClick = { lastInteractionMessage = "Slot Foto lain dipilih." }
                )
            }

            CloBleLabeledTextField(
                label = "Nama pakaian",
                value = itemName,
                onValueChange = { itemName = it },
                placeholder = "Contoh: Sweater rajut abu"
            )

            CloBleLabeledTextField(
                label = "Deskripsi",
                value = itemDescription,
                onValueChange = { itemDescription = it },
                placeholder = "Ceritakan bahan, pemakaian, dan kekurangan jika ada.",
                singleLine = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CloBleDropdownField(
                    label = "Ukuran",
                    selectedValue = selectedSize,
                    options = listOf("XS", "S", "M", "L", "XL"),
                    onOptionSelected = { size ->
                        selectedSize = size
                        lastInteractionMessage = "Ukuran dipilih: $size."
                    },
                    modifier = Modifier.weight(1f)
                )
                CloBleDropdownField(
                    label = "Gender pakaian",
                    selectedValue = selectedGender,
                    options = listOf("Pria", "Wanita", "Uniseks"),
                    onOptionSelected = { gender ->
                        selectedGender = gender
                        lastInteractionMessage = "Gender pakaian dipilih: $gender."
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            CloBleChoiceChipRow(
                label = "Filter ukuran cepat",
                options = listOf("XS", "S", "M", "L", "XL"),
                selectedOptions = selectedChipSizes.toSet(),
                onOptionClick = { chip ->
                    selectedChipSizes = if (chip in selectedChipSizes) {
                        selectedChipSizes - chip
                    } else {
                        selectedChipSizes + chip
                    }
                    lastInteractionMessage = "Filter chip ukuran diperbarui: ${
                        selectedChipSizes.joinToString().ifBlank { "Kosong" }
                    }."
                }
            )

            HorizontalDivider(thickness = 1.dp, color = CloBleColors.Outline)

            Text(
                text = "Daftar pakaian · 2 item",
                style = MaterialTheme.typography.titleMedium,
                color = CloBleColors.TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                WardrobeItemCard(
                    title = itemName.ifBlank { "Kemeja linen krem" },
                    metadata = "$selectedSize · $selectedGender · Baik",
                    barterBadge = if (isBarterAllowed) {
                        WardrobeBarterBadge.ALLOWED
                    } else {
                        WardrobeBarterBadge.NOT_FOR_BARTER
                    },
                    statusNote = if (isBarterAllowed) {
                        "Tersedia · diminati Naya"
                    } else {
                        "Koleksi pribadi"
                    },
                    isSelected = isFirstCardSelected,
                    actionButtonText = "Detail / ubah",
                    onCardClick = {
                        isFirstCardSelected = !isFirstCardSelected
                    },
                    onActionClick = {
                        lastInteractionMessage = "Membuka detail Kemeja linen krem."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wardrobe_card_primary")
                )

                WardrobeItemCard(
                    title = "Kaos hitam",
                    metadata = "L · Uniseks · Baik",
                    barterBadge = WardrobeBarterBadge.NOT_FOR_BARTER,
                    statusNote = "Koleksi pribadi",
                    actionButtonText = "Detail / ubah",
                    onActionClick = {
                        lastInteractionMessage = "Membuka detail Kaos hitam."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wardrobe_card_secondary")
                )
            }

            CloBleStatusInfoBox(
                message = "Status \"tersedia\" tetap berlaku saat match. Pertukaran dibahas lewat chat; match belum berarti pakaian sudah ditukar."
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 392, heightDp = 852)
@Composable
private fun DesignSystemShowcaseScreenPreview() {
    CloBleTheme {
        DesignSystemShowcaseScreen()
    }
}
