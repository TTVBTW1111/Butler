package com.cherry.butler.fork

import com.cherry.butler.core.model.ChatFilter
import com.cherry.butler.core.model.ChatLength
import com.cherry.butler.core.model.ChatListSpec
import com.cherry.butler.core.model.ChatPeriod
import com.cherry.butler.core.model.ChatSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListSpecTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @Test fun pastWeekSetsSinceOnly() {
        val s = ChatListSpec.of(ChatFilter(period = ChatPeriod.Week), " aya ", null, 0, now)
        assertEquals(now - 7 * day, s.since)
        assertEquals(0L, s.until)
        assertEquals("aya", s.query)
    }

    @Test fun olderThanSetsUntilOnly() {
        val s = ChatListSpec.of(ChatFilter(period = ChatPeriod.Older), "", null, 0, now)
        assertEquals(0L, s.since)
        assertEquals(now - 90 * day, s.until)
    }

    @Test fun defaultIsNotActive() {
        assertFalse(ChatFilter().active)
        assertFalse(ChatFilter(inMessages = true).active)
        assertTrue(ChatFilter(sort = ChatSort.NameAz).active)
        assertEquals(2, ChatFilter(length = ChatLength.Long, multiOnly = true).count)
        assertEquals(100, ChatListSpec.of(ChatFilter(length = ChatLength.Long), "", null, 0, now).minMessages)
    }
}
