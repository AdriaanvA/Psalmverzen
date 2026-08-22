package nl.psalmbladmuziek.app

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class PlaybackBuildCoordinator<Request, Result>(
    private val builder: (Request) -> Result?,
    private val starter: (Result) -> Unit,
    private val resultDispatcher: (() -> Unit) -> Unit,
    private val isStartAllowed: () -> Boolean,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MelodyEventBuilder")
    }
) {
    private val lock = Any()
    private var generation = 0L
    private var activeBuild: Future<*>? = null
    private var closed = false

    fun request(request: Request) {
        val requestGeneration: Long
        synchronized(lock) {
            if (closed) return
            generation += 1
            requestGeneration = generation
            activeBuild?.cancel(true)
            activeBuild = executor.submit {
                val result = try {
                    builder(request)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    null
                }
                if (result != null) {
                    resultDispatcher {
                        val isCurrent = synchronized(lock) {
                            !closed && requestGeneration == generation
                        }
                        if (isCurrent && isStartAllowed()) {
                            starter(result)
                        }
                    }
                }
            }
        }
    }

    fun cancel() {
        synchronized(lock) {
            generation += 1
            activeBuild?.cancel(true)
            activeBuild = null
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            activeBuild?.cancel(true)
            activeBuild = null
        }
        executor.shutdownNow()
    }
}