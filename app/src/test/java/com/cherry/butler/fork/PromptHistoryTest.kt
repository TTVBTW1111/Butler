package com.cherry.butler.fork

import com.cherry.butler.core.data.PromptHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptHistoryTest {

    @Test
    fun `delta counts what was added and removed between versions`() {
        assertEquals(0 to 0, PromptHistory.delta("same text", "same text"))
        assertEquals(7 to 0, PromptHistory.delta("Be dry.", "Be dry, terse."))
        assertEquals(0 to 7, PromptHistory.delta("Be dry, terse.", "Be dry."))
        assertEquals(4 to 3, PromptHistory.delta("tone: dry", "tone: calm"))
    }
}
