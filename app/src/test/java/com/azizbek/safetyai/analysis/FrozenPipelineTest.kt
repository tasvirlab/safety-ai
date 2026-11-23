package com.azizbek.safetyai.analysis

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test

class FrozenPipelineTest {
    @Test fun configurationHashIgnoresMapInsertionOrderAndChangesWithAParameter() {
        val configuration = FrozenPipeline.configuration()
        val reversed = configuration.entries.reversed().associate { it.key to it.value }
        assertEquals(FrozenPipeline.configurationHash(configuration), FrozenPipeline.configurationHash(reversed))
        assertNotEquals(FrozenPipeline.configurationHash(configuration),
            FrozenPipeline.configurationHash(configuration + ("rapid_min_velocity_deg_s" to "45.0")))
    }

    @Test fun capturedProvenanceSurvivesResultStorageSerialization() {
        val config = FrozenPipeline.configuration()
        val expected = PipelineProvenance(FrozenPipeline.ID, config,
            FrozenPipeline.configurationHash(config), FrozenPipeline.modelHashes(), "a".repeat(64),
            mapOf("device_model" to "25032RP42C", "rtmpose_backend" to "CPU"),
            mapOf("rtmpose_2d" to 100L))
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(expected) }
        }.toByteArray()
        val actual = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as PipelineProvenance
        }
        assertEquals(expected, actual)
        assertEquals("60.0", actual.configuration["rapid_min_velocity_deg_s"])
        assertEquals("4", actual.configuration["repetition_min_cycles"])
        assertEquals("9313221f443d5f1f8b3169ef828e73da804d04b46941d629e8934671262d8c16",
            actual.modelSha256["posemamba"])
    }
}
