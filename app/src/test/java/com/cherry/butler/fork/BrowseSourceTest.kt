package com.cherry.butler.fork

import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.SpecialMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseSourceTest {

    @Test
    fun `favorites and following list everything, not only what trends today`() {
        val trending = BrowseQuery(special = SpecialMode.Trending24)
        assertNull(trending.copy(source = BrowseSource.Favorites).server().special)
        assertNull(trending.copy(source = BrowseSource.Following).server().special)
    }

    @Test
    fun `the plain browse keeps its trending window`() {
        assertEquals(SpecialMode.Trending24, BrowseQuery(special = SpecialMode.Trending24).server().special)
    }
}
