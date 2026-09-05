package jp.linkserver.nittcsc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolVersionTest {
    @Test
    fun currentProtocolVersionContractIsVersionThree() {
        assertEquals("syncProtocolVersion", SYNC_PROTOCOL_VERSION_KEY)
        assertEquals(3, CURRENT_SYNC_PROTOCOL_VERSION)
        assertTrue(CURRENT_SYNC_PROTOCOL_VERSION > LEGACY_SYNC_PROTOCOL_VERSION)
    }

    @Test
    fun payloadWithoutVersionUsesLegacyVersionZero() {
        assertEquals(0, LEGACY_SYNC_PROTOCOL_VERSION)
    }

    @Test
    fun mismatchedProtocolVersionsAreRejectedBeforeMerge() {
        assertThrows(IllegalArgumentException::class.java) {
            requireCompatibleSyncProtocolVersions(
                localVersion = CURRENT_SYNC_PROTOCOL_VERSION,
                remoteVersion = 2
            )
        }
    }
}
