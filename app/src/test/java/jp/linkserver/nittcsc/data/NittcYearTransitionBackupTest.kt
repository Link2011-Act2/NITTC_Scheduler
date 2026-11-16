package jp.linkserver.nittcsc.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class NittcYearTransitionBackupTest {
    @Test fun allOutcomesRoundTripInYearOrder() {
        val items = NittcYearOutcome.entries.mapIndexed { index, outcome -> NittcYearTransitionEntity(2026 + index, outcome.name) }
        assertEquals(items, NittcYearTransitionBackup.decode(NittcYearTransitionBackup.encode(items.reversed())))
    }

    @Test fun invalidYearsOutcomesAndDuplicatesAreRejected() {
        for (json in listOf(
            "[{\"academicYear\":2026.5,\"outcome\":\"GRADUATED\"}]",
            "[{\"academicYear\":2026,\"outcome\":\"UNKNOWN\"}]",
            "[{\"academicYear\":2026,\"outcome\":\"DEFERRED\"},{\"academicYear\":2026,\"outcome\":\"GRADUATED\"}]"
        )) assertTrue(runCatching { NittcYearTransitionBackup.decode(JSONArray(json)) }.isFailure)
    }
}
