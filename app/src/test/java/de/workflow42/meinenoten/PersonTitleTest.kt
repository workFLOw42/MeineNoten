package de.workflow42.meinenoten

import de.workflow42.meinenoten.ui.util.MAX_TITLE_NAME_LENGTH
import de.workflow42.meinenoten.ui.util.PersonPalette
import de.workflow42.meinenoten.ui.util.PossessiveRule
import de.workflow42.meinenoten.ui.util.personColorIndex
import de.workflow42.meinenoten.ui.util.personalTitle
import de.workflow42.meinenoten.ui.util.possessive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonTitleTest {

    private fun german(name: String) =
        personalTitle(name, PossessiveRule.GERMAN, "%1\$s Noten", "Meine Noten")

    @Test
    fun `german genitive adds s`() {
        assertEquals("Annas Noten", german("Anna"))
        assertEquals("Florians Noten", german("Florian"))
    }

    @Test
    fun `german genitive uses apostrophe after s sounds`() {
        assertEquals("Hans’ Noten", german("Hans"))
        assertEquals("Max’ Noten", german("Max"))
        assertEquals("Fritz’ Noten", german("Fritz"))
        assertEquals("Anna S.’ Noten", german("Anna S."))
    }

    @Test
    fun `english always uses apostrophe s`() {
        assertEquals("Anna's", possessive("Anna", PossessiveRule.ENGLISH))
        assertEquals("Hans's", possessive("Hans", PossessiveRule.ENGLISH))
    }

    @Test
    fun `blank or overly long name falls back to app name`() {
        assertEquals("Meine Noten", german(""))
        assertEquals("Meine Noten", german("   "))
        assertEquals("Meine Noten", german("x".repeat(MAX_TITLE_NAME_LENGTH + 1)))
    }

    @Test
    fun `name is trimmed`() {
        assertEquals("Annas Noten", german("  Anna "))
    }

    @Test
    fun `colour depends on id only and is stable`() {
        val index = personColorIndex("3f2a-anna")
        assertEquals(index, personColorIndex("3f2a-anna"))
        assertTrue(index in PersonPalette.indices)
        // Fixed expectation: the colour of an id must never change between versions.
        assertEquals(Math.floorMod("3f2a-anna".hashCode(), PersonPalette.size), index)
    }

    @Test
    fun `palette covers every slot`() {
        val used = (0 until 500).map { personColorIndex("id-$it") }.toSet()
        assertEquals(PersonPalette.size, used.size)
        assertNotEquals(0, PersonPalette.size)
    }
}
