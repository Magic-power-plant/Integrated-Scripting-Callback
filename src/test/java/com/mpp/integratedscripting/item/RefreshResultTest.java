package com.mpp.integratedscripting.item;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the refresher's result/message selection.
 *
 * <p>These tests run without a Minecraft world: {@link RefreshResult} only
 * carries what was refreshed plus the language key that reports it
 * (docs/NETWORK-REFRESHER.md §5).</p>
 */
public class RefreshResultTest {

    @Test
    public void networkResultKeepsCounts() {
        RefreshResult result = RefreshResult.network(12, 3, false);

        assertEquals(RefreshResult.Kind.NETWORK, result.getKind());
        assertFalse(result.isFailure());
        assertEquals(12, result.getNetworkPartCount());
        assertEquals(3, result.getNetworkDisplayCount());
        assertFalse(result.isCrashCleared());
        assertEquals(RefreshResult.KEY_NETWORK_REFRESHED, result.getMessageKey());
    }

    @Test
    public void crashedNetworkUsesRecoveryMessage() {
        RefreshResult result = RefreshResult.network(5, 1, true);

        assertTrue(result.isCrashCleared());
        assertEquals(RefreshResult.KEY_NETWORK_REFRESHED_CRASHED, result.getMessageKey());
    }

    @Test
    public void failureKeepsItsOwnReason() {
        RefreshResult noNetwork = RefreshResult.failure(RefreshResult.KEY_ERROR_NO_NETWORK);
        RefreshResult failed = RefreshResult.failure(RefreshResult.KEY_ERROR_FAILED);

        assertTrue(noNetwork.isFailure());
        assertEquals(RefreshResult.KEY_ERROR_NO_NETWORK, noNetwork.getMessageKey());
        assertTrue(failed.isFailure());
        assertEquals(RefreshResult.KEY_ERROR_FAILED, failed.getMessageKey());
    }

}
