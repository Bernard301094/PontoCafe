package com.pontocafe.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {

    @Test
    fun `width breakpoints follow compact medium and expanded policy`() {
        assertEquals(PontoCafeWindowSizeClass.COMPACT, pontoCafeWindowSizeClass(599.dp))
        assertEquals(PontoCafeWindowSizeClass.MEDIUM, pontoCafeWindowSizeClass(600.dp))
        assertEquals(PontoCafeWindowSizeClass.MEDIUM, pontoCafeWindowSizeClass(839.dp))
        assertEquals(PontoCafeWindowSizeClass.EXPANDED, pontoCafeWindowSizeClass(840.dp))
    }

    @Test
    fun `height breakpoints distinguish short landscape from regular screens`() {
        assertEquals(PontoCafeWindowHeightClass.COMPACT, pontoCafeWindowHeightClass(479.dp))
        assertEquals(PontoCafeWindowHeightClass.MEDIUM, pontoCafeWindowHeightClass(480.dp))
        assertEquals(PontoCafeWindowHeightClass.MEDIUM, pontoCafeWindowHeightClass(899.dp))
        assertEquals(PontoCafeWindowHeightClass.EXPANDED, pontoCafeWindowHeightClass(900.dp))
    }

    @Test
    fun `very large text disables narrow multi pane layouts`() {
        assertTrue(responsiveInfo(widthClass = PontoCafeWindowSizeClass.MEDIUM).supportsTwoColumns)
        assertFalse(
            responsiveInfo(
                widthClass = PontoCafeWindowSizeClass.MEDIUM,
                fontScale = 1.6f,
            ).supportsTwoColumns,
        )
        assertFalse(responsiveInfo(widthClass = PontoCafeWindowSizeClass.COMPACT).supportsTwoColumns)
    }

    @Test
    fun `short landscape selects compact vertical layout`() {
        val info = responsiveInfo(
            widthClass = PontoCafeWindowSizeClass.MEDIUM,
            heightClass = PontoCafeWindowHeightClass.MEDIUM,
            width = 800,
            height = 500,
        )

        assertTrue(info.isShortLandscape)
        assertTrue(info.useCompactVerticalLayout)
    }

    private fun responsiveInfo(
        widthClass: PontoCafeWindowSizeClass,
        heightClass: PontoCafeWindowHeightClass = PontoCafeWindowHeightClass.MEDIUM,
        fontScale: Float = 1f,
        width: Int = 700,
        height: Int = 700,
    ) = PontoCafeResponsiveInfo(
        availableWidth = width.dp,
        availableHeight = height.dp,
        pagePadding = 16.dp,
        windowSizeClass = widthClass,
        windowHeightClass = heightClass,
        isNarrow = width < 480,
        isCompact = widthClass == PontoCafeWindowSizeClass.COMPACT,
        isMedium = widthClass == PontoCafeWindowSizeClass.MEDIUM,
        isExpanded = widthClass == PontoCafeWindowSizeClass.EXPANDED,
        isCompactHeight = heightClass == PontoCafeWindowHeightClass.COMPACT,
        isLandscape = width > height,
        fontScale = fontScale,
        isLargeScreen = width >= 1_200,
        isExtraLargeScreen = width >= 1_600,
    )
}
