package com.cherry.butler.fork

import com.cherry.butler.core.data.ForYouScoring
import com.cherry.butler.core.data.ForYouScoring.Candidate
import com.cherry.butler.core.data.ForYouScoring.Seed
import com.cherry.butler.core.data.remote.dto.CharacterDto
import com.cherry.butler.core.data.remote.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouScoringTest {
    private fun dto(id: String, vararg tags: String) = CharacterDto(id = id, name = id, tags = tags.mapIndexed { i, t -> TagDto(id = i + 1, name = t) })

    @Test
    fun `point of view is read from Janitor's tags and the persona's pronouns`() {
        assertEquals("any", ForYouScoring.povOf(listOf("👤 AnyPOV", "Female")))
        assertEquals("male", ForYouScoring.povOf(listOf("MalePOV")))
        assertEquals("female", ForYouScoring.povOf(listOf("FemPOV")))
        assertNull(ForYouScoring.povOf(listOf("Fantasy")))
        assertEquals("male", ForYouScoring.povOfPronoun("He"))
        assertEquals("any", ForYouScoring.povOfPronoun("they"))
    }

    @Test
    fun `a bot written for someone else's point of view drops below one that fits`() {
        val fits = Candidate(dto("a", "MalePOV", "Slowburn"))
        val other = Candidate(dto("b", "FemPOV", "Slowburn"))
        val w = mapOf("slowburn" to 1.0)
        val sa = ForYouScoring.score(fits, w, personaPov = "male", dislikedTags = emptySet()).first
        val sb = ForYouScoring.score(other, w, personaPov = "male", dislikedTags = emptySet()).first
        assertTrue(sa > sb)
    }

    @Test
    fun `similar to what you play counts most, and says so`() {
        val c = Candidate(dto("c", "Romance"), similarTo = mutableListOf(Seed("s", "Satoru Gojo", 3.0)))
        val plain = Candidate(dto("d", "Romance"))
        val (score, reasons) = ForYouScoring.score(c, emptyMap(), null, emptySet())
        assertTrue(score > ForYouScoring.score(plain, emptyMap(), null, emptySet()).first)
        assertTrue(reasons.first().contains("Satoru Gojo"))
    }

    @Test
    fun `chats count more when recent, longer, and as the persona you play now`() {
        val now = 1_000L * 86_400_000L
        val recent = ForYouScoring.chatWeight(200, now, now, samePersona = false)
        val old = ForYouScoring.chatWeight(200, now - 200L * 86_400_000L, now, samePersona = false)
        val mine = ForYouScoring.chatWeight(200, now, now, samePersona = true)
        assertTrue(recent > old)
        assertTrue(mine > recent)
        assertEquals(0.0, ForYouScoring.chatWeight(0, now, now, false), 0.0)
    }

    @Test
    fun `tags you passed on count against a bot`() {
        val c = Candidate(dto("e", "Yandere"))
        val liked = ForYouScoring.score(c, mapOf("yandere" to 1.0), null, emptySet()).first
        val passed = ForYouScoring.score(c, mapOf("yandere" to 1.0), null, setOf("yandere")).first
        assertTrue(passed < liked)
    }
}
