package com.example.cloble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cloble.ui.theme.CloBleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserSummaryHeader(
    name: String,
    city: String,
    averageRating: Double,
    reviewCount: Int,
    isLowCredibility: Boolean,
    photoUri: String? = null,
    modifier: Modifier = Modifier,
    onProfileClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onProfileClick != null) Modifier.clickable(onClick = onProfileClick)
                else Modifier
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CloBleColors.SecondarySurface)
                    .border(1.dp, CloBleColors.Outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Avatar $name",
                        modifier = Modifier.size(52.dp)
                    )
                } else {
                    Icon(
                        imageVector = CloBleIcons.UserOutline,
                        contentDescription = "Avatar $name",
                        tint = CloBleColors.TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CloBleColors.TextPrimary
                )
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodySmall,
                    color = CloBleColors.TextSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = CloBleIcons.StarFilled,
                        contentDescription = "Rating",
                        tint = CloBleColors.StarRating,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (reviewCount > 0) {
                            String.format(Locale.US, "%.1f (%d ulasan)", averageRating, reviewCount)
                        } else {
                            "Belum ada ulasan"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = CloBleColors.TextSecondary
                    )
                }
            }
        }

        if (isLowCredibility) {
            Spacer(modifier = Modifier.height(8.dp))
            CloBleStatusInfoBox(
                tone = CloBleInfoBoxTone.HIGHLIGHT,
                title = "Peringatan Kredibilitas Rendah",
                message = "Pengguna ini memiliki rata-rata rating di bawah 3.0 bintang. Berhati-hatilah saat melakukan pertukaran.",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ChatBubbleItem(
    messageText: String,
    sentAt: Long,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(sentAt))

    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    val bubbleBackground = if (isFromMe) CloBleColors.Primary else CloBleColors.IncomingBubbleSurface
    val textColor = if (isFromMe) CloBleColors.OnPrimary else CloBleColors.TextPrimary
    val timeColor = if (isFromMe) CloBleColors.OnPrimaryMuted else CloBleColors.TextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = timeColor,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun StarRatingSelector(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isSelected = i <= rating
            val icon = if (isSelected) CloBleIcons.StarFilled else CloBleIcons.StarOutline
            val tint = if (isSelected) CloBleColors.StarRating else CloBleColors.Outline

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (enabled) {
                            Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onRatingSelected(i) }
                                )
                                .semantics { contentDescription = "Beri $i bintang" }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
