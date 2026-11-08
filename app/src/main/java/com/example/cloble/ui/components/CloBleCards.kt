package com.example.cloble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles

enum class CloBleInfoBoxTone {

    INFO,

    HIGHLIGHT,

    NEUTRAL
}

@Composable
fun CloBleStatusInfoBox(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: CloBleInfoBoxTone = CloBleInfoBoxTone.INFO
) {
    val bgColor = when (tone) {
        CloBleInfoBoxTone.INFO, CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.PrimaryContainer
        CloBleInfoBoxTone.NEUTRAL -> CloBleColors.SecondarySurface
    }
    val borderColor = when (tone) {
        CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.Primary
        CloBleInfoBoxTone.INFO, CloBleInfoBoxTone.NEUTRAL -> CloBleColors.Outline
    }
    val messageColor = when (tone) {
        CloBleInfoBoxTone.NEUTRAL -> CloBleColors.TextSecondary
        CloBleInfoBoxTone.INFO, CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.TextPrimary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = CloBleColors.TextPrimary
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = messageColor
            )
        }
    }
}

@Composable
fun BarterEligibilitySwitchCard(
    isBarterAllowed: Boolean,
    onBarterAllowedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String = "Izinkan dipilih untuk barter"
) {
    val badgeText = if (isBarterAllowed) "Diizinkan barter" else "Tidak untuk barter"
    val descriptionText = if (isBarterAllowed) {
        "Pakaian boleh tampil sebagai kandidat barter di Jelajah dan dipilih pengguna lain selama memenuhi syarat ketersediaan."
    } else {
        "Pakaian hanya menjadi koleksi pribadi di Lemari dan tidak ditampilkan sebagai kandidat barter."
    }

    val containerBg = if (isBarterAllowed) CloBleColors.PrimaryContainer else CloBleColors.Background
    val borderColor = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.Outline
    val badgeBg = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.SecondarySurface
    val badgeBorder = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.Outline
    val badgeTextColor = if (isBarterAllowed) CloBleColors.OnPrimary else CloBleColors.TextSecondary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = isBarterAllowed,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onBarterAllowedChange
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Switch $title"
                stateDescription = badgeText
            }
            .testTag("barter_eligibility_switch_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = CloBleColors.TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeBorder)
                    ) {
                        Text(
                            text = badgeText,
                            style = CloBleTextStyles.BadgeSemiBold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Switch(
                    checked = isBarterAllowed,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CloBleColors.OnPrimary,
                        checkedTrackColor = CloBleColors.Primary,
                        checkedBorderColor = CloBleColors.Primary,
                        uncheckedThumbColor = CloBleColors.TextSecondary,
                        uncheckedTrackColor = CloBleColors.SecondarySurface,
                        uncheckedBorderColor = CloBleColors.Outline
                    )
                )
            }

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyMedium,
                color = CloBleColors.TextPrimary
            )
        }
    }
}

@Composable
fun ClothingPhotoSlot(
    label: String,
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    isSavedPlaceholder: Boolean = false,
    height: Dp = 96.dp,
    onClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor = if (isSavedPlaceholder) CloBleColors.PrimaryContainer else CloBleColors.Background

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = label,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bgColor)
            .border(width = 1.dp, color = CloBleColors.Outline, shape = shape)
            .then(clickModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "Slot foto $label"
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (onRemoveClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(role = Role.Button, onClick = onRemoveClick)
                        .semantics { contentDescription = "Hapus foto $label" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CloBleIcons.XClose,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = CloBleIcons.ImagePlaceholder,
                    contentDescription = null,
                    tint = CloBleColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = label,
                    style = if (isSavedPlaceholder) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = CloBleColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class WardrobeBarterBadge(val label: String) {
    ALLOWED("Diizinkan barter"),
    NOT_FOR_BARTER("Tidak untuk barter"),
    IN_ACTIVE_MATCH("Sedang dalam Match")
}

@Composable
fun WardrobeItemCard(
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
    barterBadge: WardrobeBarterBadge = WardrobeBarterBadge.ALLOWED,
    statusNote: String? = null,
    imageModel: Any? = null,
    photoPlaceholderLabel: String = "Foto pakaian",
    photoHeight: Dp = 172.dp,
    isSelected: Boolean = false,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    val photoShape = RoundedCornerShape(12.dp)
    val photoBorderWidth = if (isSelected) 2.dp else 1.dp
    val photoBorderColor = if (isSelected) CloBleColors.Primary else CloBleColors.Outline

    val (badgeBg, badgeBorder, badgeTextColor) = when (barterBadge) {
        WardrobeBarterBadge.ALLOWED -> Triple(
            CloBleColors.PrimaryContainer,
            CloBleColors.Primary,
            CloBleColors.Primary
        )
        WardrobeBarterBadge.NOT_FOR_BARTER -> Triple(
            CloBleColors.SecondarySurface,
            CloBleColors.Outline,
            CloBleColors.TextSecondary
        )
        WardrobeBarterBadge.IN_ACTIVE_MATCH -> Triple(
            CloBleColors.SecondarySurface,
            CloBleColors.Outline,
            CloBleColors.TextPrimary
        )
    }

    val clickableModifier = if (onCardClick != null) {
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onCardClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .semantics {
                contentDescription = "Kartu pakaian $title, $metadata, ${barterBadge.label}"
                if (isSelected) {
                    stateDescription = "Terpilih untuk barter"
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(photoHeight)
                .clip(photoShape)
                .background(CloBleColors.PrimaryContainer)
                .border(width = photoBorderWidth, color = photoBorderColor, shape = photoShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = CloBleIcons.ImagePlaceholder,
                        contentDescription = null,
                        tint = CloBleColors.Primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = photoPlaceholderLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = CloBleColors.Primary
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = CloBleColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = metadata,
            style = MaterialTheme.typography.bodySmall,
            color = CloBleColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeBg,
            border = BorderStroke(1.dp, badgeBorder)
        ) {
            Text(
                text = barterBadge.label,
                style = CloBleTextStyles.BadgeSemiBold,
                color = badgeTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (!statusNote.isNullOrBlank()) {
            CloBleStatusInfoBox(message = statusNote)
        }

        if (!actionButtonText.isNullOrBlank() && onActionClick != null) {
            CloBleOutlinedButton(
                text = actionButtonText,
                onClick = onActionClick,
                accessibilityLabel = "$actionButtonText $title"
            )
        }
    }
}
