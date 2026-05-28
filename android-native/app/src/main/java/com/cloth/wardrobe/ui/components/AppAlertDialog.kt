package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "取消"
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = WardrobeConstants.CardBg,
        titleContentColor = Color(0xFF222222),
        textContentColor = Color(0xFF666666),
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(text, fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = WardrobeConstants.Accent, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(dismissText, color = WardrobeConstants.TextSecondary, fontSize = 14.sp)
            }
        }
    )
}
