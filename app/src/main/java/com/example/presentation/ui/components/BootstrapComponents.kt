package com.example.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BootstrapBtnType {
    PRIMARY,
    SECONDARY,
    SUCCESS,
    DANGER,
    WARNING,
    INFO,
    LIGHT,
    DARK,
    OUTLINE_PRIMARY,
    OUTLINE_SECONDARY
}

@Composable
fun BootstrapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: BootstrapBtnType = BootstrapBtnType.PRIMARY,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val containerColor = when (type) {
        BootstrapBtnType.PRIMARY -> Color(0xFF0D6EFD)
        BootstrapBtnType.SECONDARY -> Color(0xFF6C757D)
        BootstrapBtnType.SUCCESS -> Color(0xFF198754)
        BootstrapBtnType.DANGER -> Color(0xFFDC3545)
        BootstrapBtnType.WARNING -> Color(0xFFFFC107)
        BootstrapBtnType.INFO -> Color(0xFF0DCAF0)
        BootstrapBtnType.LIGHT -> Color(0xFFF8F9FA)
        BootstrapBtnType.DARK -> Color(0xFF212529)
        BootstrapBtnType.OUTLINE_PRIMARY -> Color.Transparent
        BootstrapBtnType.OUTLINE_SECONDARY -> Color.Transparent
    }

    val contentColor = when (type) {
        BootstrapBtnType.WARNING, BootstrapBtnType.INFO, BootstrapBtnType.LIGHT -> Color.Black
        BootstrapBtnType.OUTLINE_PRIMARY -> Color(0xFF0D6EFD)
        BootstrapBtnType.OUTLINE_SECONDARY -> Color(0xFF6C757D)
        else -> Color.White
    }

    val border = when (type) {
        BootstrapBtnType.OUTLINE_PRIMARY -> BorderStroke(1.dp, Color(0xFF0D6EFD))
        BootstrapBtnType.OUTLINE_SECONDARY -> BorderStroke(1.dp, Color(0xFF6C757D))
        else -> null
    }

    // Bootstrap small button styling
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        border = border,
        modifier = modifier.heightIn(min = 34.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
