package de.l3s.icrawl.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// based on http://stackoverflow.com/a/6603018/83151
public class ByteBufferBackedInputStream extends InputStream {

    ByteBuffer buf;

    public ByteBufferBackedInputStream(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public int read() throws IOException {
        if (!buf.hasRemaining()) {
            return -1;
        }
        return buf.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        if (!buf.hasRemaining()) {
            return -1;
        }

        int readLen = Math.min(len, buf.remaining());
        buf.get(bytes, off, readLen);
        return readLen;
    }
}