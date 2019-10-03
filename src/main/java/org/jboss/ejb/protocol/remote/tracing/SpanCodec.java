package org.jboss.ejb.protocol.remote.tracing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

import io.jaegertracing.internal.JaegerObjectFactory;
import io.jaegertracing.internal.JaegerSpanContext;
import io.opentracing.propagation.Binary;

public class SpanCodec {
    /**
     * Explicitly define the charset we will use.
     */
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    /**
     * Object factory used to construct JaegerSpanContext subclass instances.
     */
    private final JaegerObjectFactory objectFactory;

    /**
     * Constructor for a Binary Codec.
     */
    public SpanCodec() {
        this(builder());
    }

    private SpanCodec(Builder builder) {
        this.objectFactory = builder.objectFactory;
    }

    /**
     * Write a long into a stream in network order
     *
     * @param stream Stream to write the integer into
     * @param value  long to write
     * @param buf    buffer to use to write to the stream.
     */
    private static void writeLong(ByteArrayOutputStream stream, long value) {
        stream.write((byte) (value >> 56));
        stream.write((byte) (value >> 48));
        stream.write((byte) (value >> 40));
        stream.write((byte) (value >> 32));
        stream.write((byte) (value >> 24));
        stream.write((byte) (value >> 16));
        stream.write((byte) (value >> 8));
        stream.write((byte) (value));
    }

    /**
     * Write an integer into a stream in network order
     *
     * @param stream Stream to write the integer into
     * @param value  integer to write
     */
    private static void writeInt(ByteArrayOutputStream stream, int value) {
        stream.write((byte) (value >> 24));
        stream.write((byte) (value >> 16));
        stream.write((byte) (value >> 8));
        stream.write((byte) (value));
    }

    /**
     * Writes a String Key/Value pair into a ByteArrayOutputStream
     *
     * @param stream Stream to write the integer into
     * @param key    key of the KV pair
     * @param value  value of the KV pair
     */
    private void writeKvPair(ByteArrayOutputStream stream, String key, String value) {
        byte[] buf;

        int keyLen;
        buf = key.getBytes(DEFAULT_CHARSET);
        keyLen = buf.length;
        writeInt(stream, keyLen);
        stream.write(buf, 0, keyLen);

        int valLen;
        buf = value.getBytes(DEFAULT_CHARSET);
        valLen = value.length();
        writeInt(stream, valLen);
        stream.write(buf, 0, valLen);
    }

    /**
     * Convenience method to check a buffer for size and reallocate if necessary.
     *
     * @param len   the length required
     * @param bytes the buffer of bytes to be used
     * @return a byte array of the correct size.
     */
    private static byte[] checkBuf(int len, byte[] bytes) {
        return len <= bytes.length ? bytes : new byte[len];
    }

    public void inject(JaegerSpanContext spanContext, MessageOutputStream carrier) throws IOException {

        // Because we need to know the size of a ByteBuffer a priori, we'll
        // use a stream to serialize and then copy the stream into the
        // ByteBuffer of the carrier. The double allocation isn't ideal, but
        // these should be small and the GC will return this memory very fast.
        ByteArrayOutputStream stream = new ByteArrayOutputStream(64);

        // Write the IDs
        writeLong(stream, spanContext.getTraceIdHigh());
        writeLong(stream, spanContext.getTraceIdLow());
        writeLong(stream, spanContext.getSpanId());
        writeLong(stream, spanContext.getParentId());

        // Write the flags (byte), write one to indicate that we wish to report this span
        stream.write(spanContext.getFlags());

        // write the baggage count.
        writeInt(stream, spanContext.baggageCount());

        // write the kv/pars into the stream
        for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
            writeKvPair(stream, entry.getKey(), entry.getValue());
        }

        // Now we have a stream and a size, and we'll copy it into the byte
        // buffer.
        int size = stream.size();
        
        carrier.write(stream.toByteArray(), 0, size);
    }

    public JaegerSpanContext extract(MessageInputStream buf) throws IOException {
        Map<String, String> baggage = null;
        // drain the seven bytes (should be eight so we need to start from the next reachable
        // data
        byte[] bu = new byte[7];
        int in = 0;
        while(in < 7) {
            byte b = (byte) buf.read();
            bu[in++] = b;
        }
        
        // this piece of data is missing (the MSB is not read and lost somewhere)
        //long traceIdHigh = buf.readLong();
        long traceIdLow = buf.readLong();
        long spanId = buf.readLong();
        long parentId = buf.readLong();
        // TODO this is broken and only zero is read back, force the 1 to indicate we wish
        // to report the span
        byte flags = buf.readByte();
        //flags = 1;
        int count = buf.readInt();

        // This is optimized to reduce allocations. A decent
        // buffer is allocated to read strings, and reused for
        // keys and values. It will be expanded as necessary.
        if (count > 0) {
            baggage = new HashMap<String, String>(count);
            // Choose a size that we guess would fit most baggage k/v lengths.
            byte[] tmp = new byte[32];

            for (int i = 0; i < count; i++) {
                int len = buf.readInt();
                tmp = checkBuf(len, tmp);
                buf.read(tmp, 0, len);
                final String key = new String(tmp, 0, len, DEFAULT_CHARSET);

                len = buf.readInt();
                tmp = checkBuf(len, tmp);
                buf.read(tmp, 0, len);
                final String value = new String(tmp, 0, len, DEFAULT_CHARSET);

                baggage.put(key, value);
            }
        }

        return objectFactory.createSpanContext(0, traceIdLow, spanId, parentId, flags, baggage, null);
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("BinaryCodec{").append("ObjectFactory=" + objectFactory.getClass().getName()).append('}');
        return buffer.toString();
    }

    /**
     * Returns a builder for BinaryCodec.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This class is the builder for the BinaryCodec.
     */
    public static class Builder {

        private JaegerObjectFactory objectFactory = new JaegerObjectFactory();

        /**
         * Set object factory to use for construction of JaegerSpanContext subclass
         * instances.
         *
         * @param objectFactory JaegerObjectFactory subclass instance.
         */
        public Builder withObjectFactory(JaegerObjectFactory objectFactory) {
            this.objectFactory = objectFactory;
            return this;
        }

        /**
         * Builds a BinaryCodec object.
         */
        public SpanCodec build() {
            return new SpanCodec(this);
        }
    }
}
