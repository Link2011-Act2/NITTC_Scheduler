package jp.linkserver.nittcsc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolVersionTest {
    @Test
    fun currentProtocolVersionContractIsVersionEight() {
        assertEquals("syncProtocolVersion", SYNC_PROTOCOL_VERSION_KEY)
        assertEquals(8, CURRENT_SYNC_PROTOCOL_VERSION)
        assertTrue(CURRENT_SYNC_PROTOCOL_VERSION > LEGACY_SYNC_PROTOCOL_VERSION)
    }

    @Test
    fun payloadWithoutVersionUsesLegacyVersionZero() {
        assertEquals(0, LEGACY_SYNC_PROTOCOL_VERSION)
    }

    @Test
    fun mismatchedProtocolVersionsAreRejectedBeforeMerge() {
        listOf(0, 1, 2, 3, 4, 5, 6, 7).forEach { olderVersion ->
            assertThrows(IllegalArgumentException::class.java) {
                requireCompatibleSyncProtocolVersions(
                    localVersion = CURRENT_SYNC_PROTOCOL_VERSION,
                    remoteVersion = olderVersion
                )
            }
        }
    }
}
