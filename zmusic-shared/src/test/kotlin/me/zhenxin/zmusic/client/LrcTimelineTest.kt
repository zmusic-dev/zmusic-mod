package me.zhenxin.zmusic.client

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcTimelineTest {
    @Test
    fun `selects the latest line at current position`() {
        val timeline = LrcTimeline.parse("[00:01.00]first\n[00:02.50][00:03.00]second")

        assertEquals(LrcTimeline.Match(-1, ""), timeline.lineAt(500))
        assertEquals(LrcTimeline.Match(0, "first"), timeline.lineAt(1000))
        assertEquals("second", timeline.lineAt(3500).text)
    }
}
