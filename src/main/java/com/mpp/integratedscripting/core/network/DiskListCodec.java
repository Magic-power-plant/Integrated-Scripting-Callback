package com.mpp.integratedscripting.core.network;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import javax.annotation.Nullable;

/**
 * Encodes the list of disk ids of a scripting network into the string payload of
 * a Cyclops value notifier.
 *
 * <p>1.12.2's {@code ValueNotifierHelpers} only supports {@code int} and
 * {@code String} values, so the disk list of the terminal container is sent as a
 * comma separated string (docs/PORTING-1.12.2.md §7.8.2).</p>
 *
 * @author rubensworks
 */
public final class DiskListCodec {

    private DiskListCodec() {
    }

    /**
     * Encode a disk list.
     * @param disks The disk ids, in ascending order.
     * @return A comma separated list, or an empty string for no disks.
     */
    public static String encode(IntList disks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < disks.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(disks.getInt(i));
        }
        return builder.toString();
    }

    /**
     * Decode a disk list, ignoring malformed entries.
     * @param encoded The payload produced by {@link #encode(IntList)}; may be null.
     * @return The decoded disk ids; empty when the payload is null or empty.
     */
    public static IntList decode(@Nullable String encoded) {
        IntList disks = new IntArrayList();
        if (encoded == null || encoded.trim().isEmpty()) {
            return disks;
        }
        for (String part : encoded.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                disks.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                // Ignore malformed entries instead of breaking the GUI.
            }
        }
        return disks;
    }

}
