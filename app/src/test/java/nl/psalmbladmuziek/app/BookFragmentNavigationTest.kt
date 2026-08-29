package nl.psalmbladmuziek.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFragmentNavigationTest {
    @Test
    fun singleVerseOpensDirectlyEvenWhenSongHasAboutText() {
        assertTrue(BookFragment.shouldOpenDirectly(1))
    }

    @Test
    fun multipleVersesOpenTheVerseOverview() {
        assertFalse(BookFragment.shouldOpenDirectly(2))
    }
}