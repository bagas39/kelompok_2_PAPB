package com.example.cloble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
    NEUTRAL,
    WARNING,
    SUCCESS
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
        CloBleInfoBoxTone.WARNING -> CloBleColors.WarningContainer
        CloBleInfoBoxTone.SUCCESS -> CloBleColors.SuccessContainer
    }
    val borderColor = when (tone) {
        CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.Primary
        CloBleInfoBoxTone.WARNING -> CloBleColors.WarningText
        CloBleInfoBoxTone.SUCCESS -> CloBleColors.SuccessText
        CloBleInfoBoxTone.INFO, CloBleInfoBoxTone.NEUTRAL -> CloBleColors.Outline
    }
    val titleColor = when (tone) {
        CloBleInfoBoxTone.WARNING -> CloBleColors.WarningText
        CloBleInfoBoxTone.SUCCESS -> CloBleColors.SuccessText
        CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.OnPrimaryContainer
        else -> CloBleColors.TextPrimary
    }
    val messageColor = when (tone) {
        CloBleInfoBoxTone.NEUTRAL -> CloBleColors.TextSecondary
        CloBleInfoBoxTone.WARNING -> CloBleColors.TextPrimary
        CloBleInfoBoxTone.SUCCESS -> CloBleColors.SuccessText
        CloBleInfoBoxTone.INFO, CloBleInfoBoxTone.HIGHLIGHT -> CloBleColors.TextPrimary
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
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
                    color = titleColor
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

    val containerBg = if (isBarterAllowed) CloBleColors.PrimaryContainer else CloBleColors.Surface
    val borderColor = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.Outline
    val badgeBg = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.SecondarySurface
    val badgeBorder = if (isBarterAllowed) CloBleColors.Primary else CloBleColors.Outline
    val badgeTextColor = if (isBarterAllowed) CloBleColors.OnPrimary else CloBleColors.TextSecondary

    val cardShape = MaterialTheme.shapes.medium
    Surface(
        shape = cardShape,
        color = containerBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .cloBleFocusRing(cardShape)
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
    val shape = MaterialTheme.shapes.medium
    val bgColor = if (isSavedPlaceholder) CloBleColors.PrimaryContainer else CloBleColors.Surface

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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CloBleColors.DarkScrim.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CloBleIcons.ZoomIn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
            if (onRemoveClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .clip(CircleShape)
                        .cloBleFocusRing(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Hapus foto $label",
                            onClick = onRemoveClick
                        )
                        .semantics { contentDescription = "Hapus foto $label" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CloBleColors.DarkScrim.copy(alpha = 0.72f)),
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
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = label,
                    style = if (isSavedPlaceholder) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = CloBleColors.OnPrimaryContainer,
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
    IN_ACTIVE_MATCH("Sedang dalam Match"),
    EXCHANGED("Sudah Ditukar")
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
    compactGridMode: Boolean = false,
    isSelected: Boolean = false,
    actionButtonText: String? = null,
    actionButtonEnabled: Boolean = true,
    onActionClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    onPhotoClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(16.dp)
    val photoShape = RoundedCornerShape(12.dp)
    val cardBorderWidth = if (isSelected) 2.dp else 1.dp
    val cardBorderColor = if (isSelected) CloBleColors.Primary else CloBleColors.Outline

    val (badgeBg, badgeBorder, badgeTextColor) = when (barterBadge) {
        WardrobeBarterBadge.ALLOWED -> Triple(
            CloBleColors.PrimaryContainer,
            CloBleColors.PrimarySoft,
            CloBleColors.OnPrimaryContainer
        )
        WardrobeBarterBadge.NOT_FOR_BARTER -> Triple(
            CloBleColors.SecondarySurface,
            CloBleColors.Outline,
            CloBleColors.TextSecondary
        )
        WardrobeBarterBadge.IN_ACTIVE_MATCH -> Triple(
            CloBleColors.WarningContainer,
            CloBleColors.WarningText,
            CloBleColors.WarningText
        )
        WardrobeBarterBadge.EXCHANGED -> Triple(
            CloBleColors.SuccessContainer,
            CloBleColors.SuccessText,
            CloBleColors.SuccessText
        )
    }

    val photoRatioModifier = if (compactGridMode) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
    } else {
        Modifier
            .fillMaxWidth()
            .height(photoHeight)
    }

    Surface(
        shape = cardShape,
        color = CloBleColors.Surface,
        border = BorderStroke(cardBorderWidth, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Kartu pakaian $title, $metadata, ${barterBadge.label}"
                if (isSelected) {
                    stateDescription = "Terpilih untuk barter"
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactGridMode) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactGridMode) 6.dp else 8.dp)
        ) {
            Box(
                modifier = photoRatioModifier
                    .clip(photoShape)
                    .background(CloBleColors.SecondarySurface)
                    .border(width = 1.dp, color = CloBleColors.Outline, shape = photoShape)
                    .let { base ->
                        val effectivePhotoClick = if (imageModel != null && onPhotoClick != null) onPhotoClick else onCardClick
                        if (effectivePhotoClick != null) {
                            base.clickable(
                                role = Role.Button,
                                onClickLabel = if (imageModel != null && onPhotoClick != null) "Lihat foto penuh $title" else title,
                                onClick = effectivePhotoClick
                            )
                        } else {
                            base
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (onPhotoClick != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CloBleColors.DarkScrim.copy(alpha = 0.68f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CloBleIcons.ZoomIn,
                                contentDescription = "Perbesar foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = CloBleIcons.ShirtOutline,
                            contentDescription = null,
                            tint = CloBleColors.Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = photoPlaceholderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = CloBleColors.OnPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (onCardClick != null) {
                            base
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClick = onCardClick)
                                .padding(vertical = 2.dp)
                        } else {
                            base
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                    maxLines = if (compactGridMode) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeBorder),
                    modifier = Modifier.padding(top = 2.dp)
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
            }

            if (!statusNote.isNullOrBlank()) {
                CloBleStatusInfoBox(message = statusNote)
            }

            if (!actionButtonText.isNullOrBlank() && onActionClick != null) {
                CloBleOutlinedButton(
                    text = actionButtonText,
                    onClick = onActionClick,
                    enabled = actionButtonEnabled,
                    accessibilityLabel = "$actionButtonText $title"
                )
            }
        }
    }
}
