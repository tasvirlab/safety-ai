package com.azizbek.safetyai.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFramingRuleTest {
    private val joints = FloatArray(42) { 500f }
    private val valid = BooleanArray(21) { true }

    private fun check(
        left: Float = 200f,
        top: Float = 120f,
        right: Float = 800f,
        bottom: Float = 800f,
        points: FloatArray = joints,
        visible: BooleanArray = valid
    ): FramingFeedback = LiveFramingRule.evaluate(
        1000, 1000, left, top, right, bottom, points, visible
    )

    @Test fun fullBodyWithVisibleJointsIsReady() {
        assertTrue(check().ready)
    }

    @Test fun oversizedBodyOrClippedEdgeIsRed() {
        assertFalse(check(top = 20f).ready)
        assertFalse(check(left = 50f).ready)
        assertFalse(check(top = 90f, bottom = 950f).ready)
    }

    @Test fun missingHandOrFootAndTinyBodyAreRed() {
        assertFalse(check(visible = valid.copyOf().apply { this[10] = false }).ready)
        assertFalse(check(visible = valid.copyOf().apply { this[18] = false }).ready)
        assertFalse(check(top = 300f, bottom = 650f).ready)
        assertFalse(check(points = joints.copyOf().apply { this[20] = 990f }).ready)
    }
}
