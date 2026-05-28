package com.cloth.wardrobe.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants
import java.util.Calendar

private val FieldBg = Color(0xFFF7F7F8)
private val DividerColor = Color(0xFFF0F0F0)
private val HintColor = Color(0xFFAAAAAA)

@Composable
fun EditFormField(
    label: String,
    inlineHint: String? = null,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            if (inlineHint != null) {
                Text(
                    inlineHint,
                    fontSize = 12.sp,
                    color = HintColor,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
    if (showDivider) {
        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
    }
}

@Composable
fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontSize = 14.sp, color = WardrobeConstants.TextPrimary),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(WardrobeConstants.Accent),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FieldBg)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = HintColor)
                }
                inner()
            }
        }
    )
}

@Composable
fun CompactTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "选填",
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontSize = 14.sp, color = WardrobeConstants.TextPrimary),
        cursorBrush = SolidColor(WardrobeConstants.Accent),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FieldBg)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = HintColor)
                }
                inner()
            }
        }
    )
}

@Composable
fun CompactDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "选填，点击选择",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(FieldBg)
            .clickable {
                val cal = Calendar.getInstance()
                if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    val p = value.split("-").mapNotNull { it.toIntOrNull() }
                    if (p.size >= 3) cal.set(p[0], p[1] - 1, p[2])
                }
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onValueChange(String.format("%04d-%02d-%02d", y, m + 1, d)) },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            value.ifBlank { placeholder },
            fontSize = 14.sp,
            color = if (value.isBlank()) HintColor else WardrobeConstants.TextPrimary
        )
    }
}

@Composable
fun CompactSizeRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = Color(0xFF666666),
            modifier = Modifier
                .padding(end = 8.dp)
                .weight(0.42f)
        )
        CompactInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = "选填",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.58f)
        )
    }
}
