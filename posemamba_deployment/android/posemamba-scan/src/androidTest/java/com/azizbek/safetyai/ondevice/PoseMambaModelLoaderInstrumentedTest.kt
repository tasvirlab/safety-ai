package com.azizbek.safetyai.ondevice

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMambaModelLoaderInstrumentedTest {
    @Test
    fun productionAssetLoadsAndRuns() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        PoseMambaModelLoader.open(context).use { session ->
            val input = FloatArray(243 * 21 * 2)
            val output = session.lift(input)

            assertEquals(243 * 21 * 3, output.size)
            assertTrue(output.all { it.isFinite() })
        }
    }
}