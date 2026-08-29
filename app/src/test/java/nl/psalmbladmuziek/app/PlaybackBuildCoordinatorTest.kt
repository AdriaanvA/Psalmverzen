package nl.psalmbladmuziek.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlaybackBuildCoordinatorTest {
    @Test
    fun cancelPreventsCompletedBuildFromStarting() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val started = CountDownLatch(1)
        val coordinator = PlaybackBuildCoordinator<String, String>(
            builder = { request ->
                entered.countDown()
                awaitIgnoringInterrupt(release)
                completed.countDown()
                request
            },
            starter = { started.countDown() },
            resultDispatcher = { it() },
            isStartAllowed = { true },
            executor = Executors.newSingleThreadExecutor()
        )

        coordinator.request("old")
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        coordinator.cancel()
        release.countDown()

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertFalse(started.await(200, TimeUnit.MILLISECONDS))
        coordinator.close()
    }

    @Test
    fun newestRequestIsTheOnlyOneStarted() {
        val oldEntered = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val newStarted = CountDownLatch(1)
        val started = Collections.synchronizedList(mutableListOf<String>())
        val coordinator = PlaybackBuildCoordinator<String, String>(
            builder = { request ->
                if (request == "old") {
                    oldEntered.countDown()
                    awaitIgnoringInterrupt(releaseOld)
                }
                request
            },
            starter = { result ->
                started += result
                if (result == "new") newStarted.countDown()
            },
            resultDispatcher = { it() },
            isStartAllowed = { true },
            executor = Executors.newSingleThreadExecutor()
        )

        coordinator.request("old")
        assertTrue(oldEntered.await(2, TimeUnit.SECONDS))
        coordinator.request("new")
        releaseOld.countDown()

        assertTrue(newStarted.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("new"), started)
        coordinator.close()
    }

    @Test
    fun disallowedLifecycleDoesNotStartResult() {
        var started = false
        val dispatched = CountDownLatch(1)
        val coordinator = PlaybackBuildCoordinator<String, String>(
            builder = { it },
            starter = { started = true },
            resultDispatcher = { action ->
                action()
                dispatched.countDown()
            },
            isStartAllowed = { false }
        )

        coordinator.request("result")

        assertTrue(dispatched.await(2, TimeUnit.SECONDS))
        assertFalse(started)
        coordinator.close()
    }

    @Test
    fun nullBuildDoesNotStart() {
        val built = CountDownLatch(1)
        var started = false
        val coordinator = PlaybackBuildCoordinator<String, String>(
            builder = {
                built.countDown()
                null
            },
            starter = { started = true },
            resultDispatcher = { it() },
            isStartAllowed = { true }
        )

        coordinator.request("empty")

        assertTrue(built.await(2, TimeUnit.SECONDS))
        assertFalse(started)
        coordinator.close()
    }

    private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
        while (latch.count > 0) {
            try {
                latch.await()
            } catch (_: InterruptedException) {
                // The generation token must reject builders that ignore cancellation.
            }
        }
    }
}
