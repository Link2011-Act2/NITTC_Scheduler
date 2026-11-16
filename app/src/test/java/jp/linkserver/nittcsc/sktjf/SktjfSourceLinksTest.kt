package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test

class SktjfSourceLinksTest {
    @Test fun linksRoundTripWithoutDeviceRowIds() {
        val links = listOf(SktjfSourceLink("a-lesson", 1, 2, SktjfCycle.A), SktjfSourceLink("b-lesson", 1, 2, SktjfCycle.B))
        assertEquals(links, SktjfSourceLinks.decode(SktjfSourceLinks.encode(links)))
    }

    @Test fun refusesAmbiguousOrMalformedLinks() {
        val a = SktjfSourceLink("one", 1, 2, SktjfCycle.A)
        assertTrue(runCatching { SktjfSourceLinks.encode(listOf(a, a.copy(sourceLessonId = "two"))) }.isFailure)
        assertTrue(runCatching { SktjfSourceLinks.encode(listOf(a, a.copy(sourceLessonId = "two", cycle = SktjfCycle.WEEKLY))) }.isFailure)
        assertTrue(runCatching { SktjfSourceLinks.decode("[{\"id\":\"a\",\"id\":\"b\",\"day\":1,\"period\":2,\"cycle\":\"A\"}]") }.isFailure)
        assertTrue(runCatching { SktjfSourceLinks.decode("[],\"other\":1") }.isFailure)
    }
}
