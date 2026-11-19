package com.example.cloble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun CloBleLabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = if (singleLine) 48.dp else 82.dp,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    inputTestTag: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        !enabled -> CloBleColors.Outline
        isFocused -> CloBleColors.Primary
        else -> CloBleColors.Outline
    }
    val containerColor = if (enabled) CloBleColors.Surface else CloBleColors.SecondarySurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = CloBleColors.TextPrimary
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = CloBleColors.TextPrimary),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(CloBleColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .then(if (inputTestTag != null) Modifier.testTag(inputTestTag) else Modifier)
                .semantics {
                    contentDescription = if (label.isNotBlank()) "Input $label" else placeholder.ifBlank { "Input teks" }
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = minHeight)
                        .clip(shape)
                        .background(containerColor)
                        .border(width = if (isFocused) 1.5.dp else 1.dp, color = borderColor, shape = shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = CloBleColors.TextSecondary
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        trailingIcon()
                    }
                }
            }
        )
    }
}

@Composable
fun CloBleDropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih",
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (expanded) CloBleColors.Primary else CloBleColors.Outline
    val displayText = selectedValue.ifBlank { placeholder }
    val displayTextColor = if (selectedValue.isBlank()) {
        CloBleColors.TextSecondary
    } else {
        CloBleColors.TextPrimary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CloBleColors.TextPrimary
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(shape)
                    .background(if (enabled) CloBleColors.Surface else CloBleColors.SecondarySurface)
                    .border(width = 1.dp, color = borderColor, shape = shape)
                    .clickable(
                        enabled = enabled,
                        role = Role.DropdownList,
                        onClick = { expanded = !expanded }
                    )
                    .semantics {
                        contentDescription = "Dropdown $label"
                        stateDescription = displayText
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = displayTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = CloBleIcons.ChevronDown,
                    contentDescription = null,
                    tint = CloBleColors.TextPrimary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = CloBleColors.Surface
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = if (option == selectedValue) {
                                    MaterialTheme.typography.labelLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (option == selectedValue) {
                                    CloBleColors.Primary
                                } else {
                                    CloBleColors.TextPrimary
                                }
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloBleChoiceChipRow(
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    showCheckmarkOnSelected: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = CloBleColors.TextPrimary
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option in selectedOptions
                val bgColor = if (isSelected) CloBleColors.Primary else CloBleColors.Surface
                val borderColor = if (isSelected) CloBleColors.Primary else CloBleColors.Outline
                val textColor = if (isSelected) CloBleColors.OnPrimary else CloBleColors.TextPrimary

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Checkbox,
                            onClick = { onOptionClick(option) }
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (label != null) "$label: $option" else "Opsi $option"
                            selected = isSelected
                            stateDescription = if (isSelected) "Aktif" else "Tidak aktif"
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isSelected && showCheckmarkOnSelected) {
                            Icon(
                                imageVector = CloBleIcons.Check,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = option,
                            style = if (isSelected) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
