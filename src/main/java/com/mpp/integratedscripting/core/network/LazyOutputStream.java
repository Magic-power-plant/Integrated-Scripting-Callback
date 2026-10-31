package com.mpp.integratedscripting.core.network;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that delays inner output stream creation until the first byte was written.
 *
 * <p>Direct port of the 1.20 reference implementation (level A in
 * docs/PORTING-1.12.2.md §6.24).</p>
 *
 * @author rubensworks
 */
public class LazyOutputStream extends OutputStream {

    private final OutputStreamSupplier outputStreamSupplier;
    @Nullable
    private OutputStream outputStream;

    public LazyOutputStream(OutputStreamSupplier outputStreamSupplier) {
        this.outputStreamSupplier = outputStreamSupplier;
    }

    @Override
    public void write(int b) throws IOException {
        if (outputStream == null) {
            outputStream = outputStreamSupplier.get();
        }
        outputStream.write(b);
    }

    @Override
    public void close() throws IOException {
        if (outputStream != null) {
            outputStream.close();
        }
    }

    public interface OutputStreamSupplier {
        public OutputStream get() throws FileNotFoundException;
    }

}
