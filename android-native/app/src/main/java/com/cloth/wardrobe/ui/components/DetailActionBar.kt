package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

enum class DetailActionStyle { Primary, Outline }

data class DetailAction(
    val label: String,
    val onClick: () -> Unit,
    val style: DetailActionStyle = DetailActionStyle.Outline,
    val contentColor: Color? = null,
    val borderColor: Color? = null
)

@Composable
fun DetailActionBar(
    actions: List<DetailAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            val shape = RoundedCornerShape(8.dp)
            val textColor = action.contentColor ?: when (action.style) {
                DetailActionStyle.Primary -> Color.White
                DetailActionStyle.Outline -> WardrobeConstants.Accent
            }
            val modifierBtn = Modifier
                .weight(1f)
                .height(34.dp)
            when (action.style) {
                DetailActionStyle.Primary -> Button(
                    onClick = action.onClick,
                    modifier = modifierBtn,
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(containerColor = WardrobeConstants.Accent),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    ActionLabel(action.label, textColor)
                }
                DetailActionStyle.Outline -> OutlinedButton(
                    onClick = action.onClick,
                    modifier = modifierBtn,
                    shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    border = BorderStroke(1.dp, action.borderColor ?: textColor),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    ActionLabel(action.label, textColor)
                }
            }
        }
    }
}

@Composable
private fun ActionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}
