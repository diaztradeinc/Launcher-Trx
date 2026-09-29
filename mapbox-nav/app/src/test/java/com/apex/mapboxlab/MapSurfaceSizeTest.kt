package com.apex.mapboxlab

import org.junit.Assert.*
import org.junit.Test

class MapSurfaceSizeTest {
    @Test fun onePixelPlaceholderWouldBecomeEmptyAtPhoneDensity() {
        val ratio = 3.5f
        assertEquals(0, (1 / ratio).toInt())
        val safe = MapSurfaceSize.safe(1, 1, ratio)
        assertTrue((safe.first / ratio).toInt() >= 2)
        assertTrue((safe.second / ratio).toInt() >= 2)
    }
    @Test fun collapseAndResizeNeverProduceAnEmptyLogicalSurface() {
        for (ratio in listOf(.5f, 1f, 1.0083f, 2.625f, 3.5f, 6f)) {
            for ((w, h) in listOf(0 to 0, 1440 to 0, 0 to 2400, 1 to 1, 602 to 545, 1440 to 1800)) {
                val safe = MapSurfaceSize.safe(w, h, ratio)
                assertTrue(safe.first / ratio >= 2f)
                assertTrue(safe.second / ratio >= 2f)
            }
        }
    }
    @Test fun normalMapDimensionsAreNotRescaled() {
        assertEquals(1440 to 1800, MapSurfaceSize.safe(1440, 1800, 3.5f))
        assertEquals(602 to 545, MapSurfaceSize.safe(602, 545, 1.0083f))
    }
}
