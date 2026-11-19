package nl.hannahsten.texifyidea.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EvinceVersionTest {

    private fun assertParsed(output: String?, major: Int, minor: Int) {
        val version = SystemEnvironment.parseEvinceVersion(output)
        assertEquals("major of '$output'", major, version.majorVersion)
        assertEquals("minor of '$output'", minor, version.minorVersion)
    }

    @Test
    fun `legacy GNOME Document Viewer output`() = assertParsed("GNOME Document Viewer 3.34.2", 3, 34)

    @Test
    fun `release output`() = assertParsed("Evince 48.1", 48, 1)

    @Test
    fun `pre-release output`() = assertParsed("Evince 49.alpha", 49, 0)

    @Test
    fun `pre-release with minor`() = assertParsed("Evince 46.rc.1", 46, 0)

    @Test
    fun `missing evince`() = assertParsed(null, 0, 0)
}
