package com.example.cloble.ui.screens.wardrobe

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun DeleteClothingConfirmDialog(
    clothingName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Hapus Pakaian?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
        },
        text = {
            Text(
                text = "Pakaian \"$clothingName\" akan dihapus dari Lemari digital Anda. Seluruh tawaran barter pending yang menargetkan pakaian ini akan otomatis dibatalkan.",
                style = MaterialTheme.typography.bodySmall,
                color = CloBleColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("btn_confirm_delete_clothing")
            ) {
                Text(
                    text = "Hapus",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFDC2626)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_delete_clothing")
            ) {
                Text(
                    text = "Batal",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = CloBleColors.TextSecondary
                )
            }
        },
        modifier = modifier.testTag("dialog_delete_clothing")
    )
}
