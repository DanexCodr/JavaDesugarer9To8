package j9compat;

import java.io.*;
import java.util.Objects;

/**
 * Java 8-compatible backport of the {@link java.io.InputStream} methods added
 * in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code InputStream.transferTo(OutputStream)} – reads all bytes from
 *       the input stream and writes them to the output stream.</li>
 *   <li>{@code InputStream.readAllBytes()} – reads all remaining bytes from
 *       the input stream.</li>
 *   <li>{@code InputStream.readNBytes(byte[], int, int)} – reads up to
 *       {@code len} bytes from the input stream into an existing buffer.</li>
 *   <li>{@code InputStream.readNBytes(int)} – reads up to {@code n} bytes and
 *       returns them as a new byte array (this overload was added in Java 11
 *       but is also handled here for completeness).</li>
 * </ul>
 *
 * <p>The desugarer converts each instance-method call to an INVOKESTATIC with
 * the {@code InputStream} receiver prepended as the first argument.
 */
public final class IOBackport {

    private IOBackport() {}

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int MAX_BUFFER_SIZE     = Integer.MAX_VALUE - 8;

    /**
     * Backport of {@code InputStream.transferTo(OutputStream out)}.
     *
     * <p>Reads all bytes from {@code in} and writes them to {@code out}.
     *
     * @return the number of bytes transferred
     */
    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        Objects.requireNonNull(in,  "in");
        Objects.requireNonNull(out, "out");

        long transferred = 0;
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            transferred += n;
        }
        return transferred;
    }

    /**
     * Backport of {@code InputStream.readAllBytes()}.
     *
     * <p>Reads all remaining bytes from the stream.  This method blocks until
     * all remaining bytes have been read, the end of stream is detected, or an
     * exception is thrown.
     *
     * @return a byte array containing the bytes read from this input stream
     */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        Objects.requireNonNull(in, "in");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    /**
     * Backport of {@code InputStream.readNBytes(byte[] b, int off, int len)}.
     *
     * <p>Reads up to {@code len} bytes from the input stream into a byte
     * array.  This method blocks until {@code len} bytes have been read, the
     * end of stream is detected, or an exception is thrown.
     *
     * @return the actual number of bytes read into the buffer
     */
    public static int readNBytes(InputStream in, byte[] b, int off, int len)
            throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(b,  "b");
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException(
                    "off=" + off + ", len=" + len + ", b.length=" + b.length);
        }

        int total = 0;
        while (total < len) {
            int n = in.read(b, off + total, len - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    /**
     * Backport of {@code InputStream.readNBytes(int n)} (Java 11).
     *
     * <p>Reads up to {@code n} bytes from the input stream and returns them
     * as a new byte array.
     */
    public static byte[] readNBytes(InputStream in, int n) throws IOException {
        Objects.requireNonNull(in, "in");
        if (n < 0) throw new IllegalArgumentException("n < 0");

        byte[] result = new byte[0];
        int remaining = n;
        while (remaining > 0) {
            byte[] buf = new byte[Math.min(remaining, DEFAULT_BUFFER_SIZE)];
            int read = in.read(buf, 0, buf.length);
            if (read < 0) break;
            byte[] newResult = new byte[result.length + read];
            System.arraycopy(result, 0, newResult, 0, result.length);
            System.arraycopy(buf,    0, newResult, result.length, read);
            result    = newResult;
            remaining -= read;
        }
        return result;
    }
}
