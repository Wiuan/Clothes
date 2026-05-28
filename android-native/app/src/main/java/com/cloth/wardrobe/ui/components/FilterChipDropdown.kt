package com.cloth.wardrobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloth.wardrobe.ui.WardrobeConstants

@Composable
fun FilterChipDropdown(
    label: String?,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    sortStyle: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (sortStyle) value else value.ifEmpty { WardrobeConstants.ALL }
    val wrapChip = sortStyle || label == null
    val active = when {
        sortStyle -> value != "默认"
        label == null -> display != WardrobeConstants.ALL
        else -> value.isNotEmpty()
    }
    val chipShape = RoundedCornerShape(50)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(
                    if (wrapChip) Modifier.wrapContentWidth()
                    else Modifier.fillMaxWidth()
                )
                .background(
                    if (active) WardrobeConstants.ChipActiveBg else WardrobeConstants.CardBg,
                    chipShape
                )
                .then(
                    if (active) Modifier.border(1.dp, WardrobeConstants.ChipActiveBorder, chipShape)
                    else Modifier
                )
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (label != null) {
                Text(
                    label,
                    fontSize = 10.sp,
                    color = WardrobeConstants.TextSecondary,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    display,
                    fontSize = 11.sp,
                    color = if (active) WardrobeConstants.Accent else WardrobeConstants.TextPrimary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            } else {
                Text(
                    display,
                    fontSize = 11.sp,
                    color = if (active) WardrobeConstants.Accent else WardrobeConstants.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
            Text(
                "▾",
                fontSize = 9.sp,
                color = WardrobeConstants.TextHint,
                modifier = Modifier.padding(start = 2.dp),
                softWrap = false
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            opt,
                            color = WardrobeConstants.TextPrimary,
                            fontSize = 12.sp
                        )
                    },
                    onClick = {
                        if (sortStyle) {
                            onSelect(opt)
                        } else {
                            onSelect(if (opt == WardrobeConstants.ALL) "" else opt)
                        }
                        expanded = false
                    }
                )
            }
        }
    }
}
