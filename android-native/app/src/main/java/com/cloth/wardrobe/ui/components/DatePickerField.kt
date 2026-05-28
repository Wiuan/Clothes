package com.cloth.wardrobe.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value.ifBlank { "选填，点击选择" },
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.clickable {
            val cal = Calendar.getInstance()
            if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val p = value.split("-").mapNotNull { it.toIntOrNull() }
                if (p.size >= 3) {
                    cal.set(p[0], p[1] - 1, p[2])
                }
            }
            DatePickerDialog(
                context,
                { _, y, m, d -> onValueChange(String.format("%04d-%02d-%02d", y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    )
}
