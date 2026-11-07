package com.mpp.integratedscripting.item;

/**
 * The outcome of one use of the network refresher.
 *
 * <p>This class is intentionally free of Minecraft and Integrated Dynamics
 * types: it only describes <em>what</em> was refreshed and which language key
 * reports it, so the decision logic can be unit-tested without a world
 * (docs/NETWORK-REFRESHER.md §5). The item turns the result into a localized
 * status message and a sound.</p>
 *
 * @author rubensworks
 */
public final class RefreshResult {

    /**
     * What the refresher touched.
     */
    public enum Kind {
        /** The whole Integrated Dynamics network was refreshed. */
        NETWORK,
        /** Nothing was refreshed; {@link #getFailureKey()} explains why. */
        FAILURE
    }

    public static final String KEY_NETWORK_REFRESHED = "networkrefresher.integratedscripting.network_refreshed";
    public static final String KEY_NETWORK_REFRESHED_CRASHED =
            "networkrefresher.integratedscripting.network_refreshed_crashed";
    public static final String KEY_ERROR_NO_NETWORK = "networkrefresher.integratedscripting.error.no_network";
    public static final String KEY_ERROR_FAILED = "networkrefresher.integratedscripting.error.failed";

    private final Kind kind;
    private final int networkPartCount;
    private final int networkDisplayCount;
    private final boolean crashCleared;
    private final String failureKey;

    private RefreshResult(Kind kind, int networkPartCount, int networkDisplayCount,
                          boolean crashCleared, String failureKey) {
        this.kind = kind;
        this.networkPartCount = networkPartCount;
        this.networkDisplayCount = networkDisplayCount;
        this.crashCleared = crashCleared;
        this.failureKey = failureKey;
    }

    public static RefreshResult network(int partCount, int displayCount, boolean crashCleared) {
        return new RefreshResult(Kind.NETWORK, partCount, displayCount, crashCleared, null);
    }

    public static RefreshResult failure(String key) {
        return new RefreshResult(Kind.FAILURE, 0, 0, false, key);
    }

    public Kind getKind() {
        return this.kind;
    }

    public boolean isFailure() {
        return this.kind == Kind.FAILURE;
    }

    /**
     * @return The count of part elements that were visited in a network refresh.
     */
    public int getNetworkPartCount() {
        return this.networkPartCount;
    }

    /**
     * @return The count of Display Panels that were re-evaluated in a network refresh.
     */
    public int getNetworkDisplayCount() {
        return this.networkDisplayCount;
    }

    /**
     * @return If the network was in its crashed state and the refresher cleared it.
     */
    public boolean isCrashCleared() {
        return this.crashCleared;
    }

    /**
     * @return The language key describing why nothing was refreshed, or null.
     */
    public String getFailureKey() {
        return this.failureKey;
    }

    /**
     * @return The language key that describes this result to the player.
     */
    public String getMessageKey() {
        if (this.kind == Kind.FAILURE) {
            return this.failureKey != null ? this.failureKey : KEY_ERROR_FAILED;
        }
        return this.crashCleared ? KEY_NETWORK_REFRESHED_CRASHED : KEY_NETWORK_REFRESHED;
    }

}
