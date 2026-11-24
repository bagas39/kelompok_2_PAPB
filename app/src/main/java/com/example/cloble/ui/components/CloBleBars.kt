package com.example.cloble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles

data class CloBleTopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val tint: Color? = null,
    val showBadgeDot: Boolean = false,
    val testTag: String? = null
)

@Composable
fun CloBleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    backContentDescription: String = "Kembali",
    leadingContent: (@Composable () -> Unit)? = null,
    actions: List<CloBleTopBarAction> = emptyList()
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CloBleColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onNavigateBack != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = backContentDescription,
                                onClick = onNavigateBack
                            )
                            .semantics {
                                contentDescription = backContentDescription
                            }
                            .testTag("top_bar_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CloBleIcons.ArrowLeft,
                            contentDescription = null,
                            tint = CloBleColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (leadingContent != null) {
                    leadingContent()
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CloBleColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("top_bar_title")
                )

                actions.forEach { action ->
                    IconButton(
                        onClick = action.onClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .cloBleFocusRing(CircleShape)
                            .semantics {
                                contentDescription = action.contentDescription
                            }
                            .let { base ->
                                if (action.testTag != null) base.testTag(action.testTag) else base
                            }
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = action.tint ?: CloBleColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            if (action.showBadgeDot) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CloBleColors.Primary)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = CloBleColors.Outline
            )
        }
    }
}

enum class CloBleBottomTab(
    val label: String,
    val contentDescription: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector
) {
    DISCOVER(
        label = "Jelajah",
        contentDescription = "Tab Jelajah",
        outlineIcon = CloBleIcons.LayersOutline,
        filledIcon = CloBleIcons.LayersFilled
    ),
    WARDROBE(
        label = "Lemari",
        contentDescription = "Tab Lemari",
        outlineIcon = CloBleIcons.ShirtOutline,
        filledIcon = CloBleIcons.ShirtFilled
    ),
    CHAT(
        label = "Chat",
        contentDescription = "Tab Chat",
        outlineIcon = CloBleIcons.ChatOutline,
        filledIcon = CloBleIcons.ChatFilled
    ),
    PROFILE(
        label = "Profil",
        contentDescription = "Tab Profil",
        outlineIcon = CloBleIcons.UserOutline,
        filledIcon = CloBleIcons.UserFilled
    )
}

@Composable
fun CloBleBottomNavBar(
    selectedTab: CloBleBottomTab,
    onTabSelected: (CloBleBottomTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<CloBleBottomTab> = CloBleBottomTab.entries
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CloBleColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = CloBleColors.Outline
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val cellBg = if (isSelected) CloBleColors.PrimaryContainer else CloBleColors.Surface
                    val contentTint = if (isSelected) CloBleColors.OnPrimaryContainer else CloBleColors.TextSecondary
                    val textStyle = if (isSelected) {
                        CloBleTextStyles.BottomNavSelected
                    } else {
                        CloBleTextStyles.BottomNavUnselected
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(cellBg)
                            .cloBleFocusRing(MaterialTheme.shapes.extraSmall)
                            .clickable(
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) }
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = tab.contentDescription
                                selected = isSelected
                                stateDescription = if (isSelected) "Aktif" else "Tidak aktif"
                            }
                            .testTag("bottom_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .width(36.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(CloBleColors.Primary)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlineIcon,
                                contentDescription = null,
                                tint = contentTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tab.label,
                                style = textStyle,
                                color = contentTint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
