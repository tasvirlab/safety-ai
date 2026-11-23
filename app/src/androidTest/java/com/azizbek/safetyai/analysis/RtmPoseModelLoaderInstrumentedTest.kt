package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtmPoseModelLoaderInstrumentedTest {

    @Test
    fun installsVerifiedProductionModels() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val first = RtmPoseModelLoader.prepare(context)
        val second = RtmPoseModelLoader.prepare(context)

        assertTrue(first.detector.isFile)
        assertTrue(first.pose.isFile)

        assertEquals(101_400_344L, first.detector.length())
        assertEquals(228_708_578L, first.pose.length())

        assertEquals(first.detector.absolutePath, second.detector.absolutePath)
        assertEquals(first.pose.absolutePath, second.pose.absolutePath)

        assertTrue(
            first.detector.absolutePath.startsWith(
                context.noBackupFilesDir.absolutePath
            )
        )
        assertTrue(
            first.pose.absolutePath.startsWith(
                context.noBackupFilesDir.absolutePath
            )
        )

        println("PRODUCTION RTMPOSE MODEL LOADER: PASS")
    }
}