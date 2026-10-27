package com.cherry.butler.fork

import androidx.compose.ui.graphics.Color
import com.cherry.butler.core.design.SearchMarks
import com.cherry.butler.core.design.searchStrokes
import com.cherry.butler.feature.chat.ChatSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchTest {

    @Test
    fun `terms split on spaces, drop blanks and repeats`() {
        assertEquals(listOf("jab", "lazy"), ChatSearch.terms("  jab   lazy  JAB "))
        assertEquals(emptyList<String>(), ChatSearch.terms("   "))
    }

    @Test
    fun `a turn matches when every word is in it, any order, case aside`() {
        val text = "*He tosses you the wraps.* \"Southpaw stance today. Your jab is lazy.\""
        assertTrue(ChatSearch.matches(text, listOf("LAZY", "jab")))
        assertFalse(ChatSearch.matches(text, listOf("jab", "hook")))
        assertFalse(ChatSearch.matches(text, emptyList()))
    }

    @Test
    fun `stepping wraps at both ends and starts from the newest`() {
        assertEquals(2, ChatSearch.step(-1, -1, 3)) // nothing chosen yet: older from the newest
        assertEquals(0, ChatSearch.step(2, 1, 3))   // newer past the newest wraps to the oldest
        assertEquals(2, ChatSearch.step(0, -1, 3))  // older past the oldest wraps to the newest
        assertEquals(-1, ChatSearch.step(0, 1, 0))
    }

    @Test
    fun `every occurrence of every term gets a stroke`() {
        val marks = SearchMarks(listOf("jab", "ROPE"), Color.Red)
        val strokes = searchStrokes("Jab, jab, then the ropes.", marks)
        assertEquals(listOf(0 to 3, 5 to 8, 19 to 23), strokes.map { it.first to it.second }.sortedBy { it.first })
        assertEquals(emptyList<Any>(), searchStrokes("anything", null))
    }
}
