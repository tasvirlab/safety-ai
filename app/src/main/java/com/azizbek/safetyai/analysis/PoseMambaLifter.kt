package com.azizbek.safetyai.analysis

import android.content.Context
import com.azizbek.safetyai.ondevice.PoseMambaModelLoader
import com.azizbek.safetyai.ondevice.PoseMambaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PoseMambaLifter : AutoCloseable {
    /**
     * Input: normalized 243 x 21 x 2 FP32 window.
     * Output: 243 x 21 x 3 model-unit 3D pose.
     */
    suspend fun lift(normalized2DWindow: FloatArray): FloatArray
}

class OnDevicePoseMambaLifter(
    context: Context
) : PoseMambaLifter {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var session: PoseMambaSession? = null

    override suspend fun lift(
        normalized2DWindow: FloatArray
    ): FloatArray = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val activeSession =
                session ?: PoseMambaModelLoader.open(appContext, FrozenPipeline.POSEMAMBA_THREADS).also {
                    session = it
                }

            activeSession.lift(normalized2DWindow)
        }
    }

    override fun close() {
        synchronized(lock) {
            session?.close()
            session = null
        }
    }
}
