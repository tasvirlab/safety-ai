package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDevicePoseMambaLifterInstrumentedTest {
    @Test
    fun runsFromSafetyAiAppModule() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val lifter = OnDevicePoseMambaLifter(context)

        try {
            val input = FloatArray(243 * 21 * 2)
            val output = lifter.lift(input)

            assertEquals(243 * 21 * 3, output.size)
            assertTrue(output.all { it.isFinite() })
        } finally {
            lifter.close()
        }
    }
}