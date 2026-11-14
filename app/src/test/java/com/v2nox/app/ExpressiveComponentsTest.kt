package com.v2nox.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressiveComponentsTest {
    private val density = Density(1f)
    private val size = Size(200f, 200f)

    @Test fun singleItemHasFourLargeCorners() = assertCorners(0, 1, 24f, 24f)
    @Test fun firstItemHasLargeTopCorners() = assertCorners(0, 3, 24f, 6f)
    @Test fun middleItemHasFourSmallCorners() = assertCorners(1, 3, 6f, 6f)
    @Test fun lastItemHasLargeBottomCorners() = assertCorners(2, 3, 6f, 24f)
    @Test fun twoItemGroupHasNoMiddleItem() {
        assertCorners(0, 2, 24f, 6f)
        assertCorners(1, 2, 6f, 24f)
    }

    private fun assertCorners(index: Int, count: Int, top: Float, bottom: Float) {
        val shape = groupedShape(index, count)
        assertEquals(top, shape.topStart.toPx(size, density), 0f)
        assertEquals(top, shape.topEnd.toPx(size, density), 0f)
        assertEquals(bottom, shape.bottomStart.toPx(size, density), 0f)
        assertEquals(bottom, shape.bottomEnd.toPx(size, density), 0f)
    }
}
