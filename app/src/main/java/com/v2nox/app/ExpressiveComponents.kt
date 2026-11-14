package com.v2nox.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared by profile and settings groups; logical corners also support RTL. */
internal fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 24.dp else 6.dp
    val bottom = if (index == count - 1) 24.dp else 6.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
