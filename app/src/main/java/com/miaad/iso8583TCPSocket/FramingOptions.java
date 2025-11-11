package com.miaad.iso8583TCPSocket;

import java.nio.ByteOrder;

/**
 * Configurable message framing options for send/receive.
 * Defaults preserve existing behavior (length-prefixed with 2-byte header, BIG_ENDIAN).
 */
public class FramingOptions {
    private final boolean sendLengthHeader;
    private final boolean expectResponseHeader;
    private final int lengthHeaderSize;
    private final ByteOrder byteOrder;
    private final boolean autoDetect;
    private final int fixedResponseLength;
    private final byte[] responseTerminator;
    private final int idleGapMs;
    private final int maxMessageSizeBytes;

    private FramingOptions(Builder builder) {
        this.sendLengthHeader = builder.sendLengthHeader;
        this.expectResponseHeader = builder.expectResponseHeader;
        this.lengthHeaderSize = builder.lengthHeaderSize;
        this.byteOrder = builder.byteOrder;
        this.autoDetect = builder.autoDetect;
        this.fixedResponseLength = builder.fixedResponseLength;
        this.responseTerminator = builder.responseTerminator;
        this.idleGapMs = builder.idleGapMs;
        this.maxMessageSizeBytes = builder.maxMessageSizeBytes;
    }

    public static FramingOptions defaults(int lengthHeaderSize, ByteOrder order) {
        return FramingOptions.builder()
                .sendLengthHeader(true)
                .expectResponseHeader(true)
                .lengthHeaderSize(lengthHeaderSize)
                .byteOrder(order == null ? ByteOrder.BIG_ENDIAN : order)
                .autoDetect(false)
                .idleGapMs(150)
                .build();
    }

    public boolean isSendLengthHeader() { return sendLengthHeader; }
    public boolean isExpectResponseHeader() { return expectResponseHeader; }
    public int getLengthHeaderSize() { return lengthHeaderSize; }
    public ByteOrder getByteOrder() { return byteOrder; }
    public boolean isAutoDetect() { return autoDetect; }
    public int getFixedResponseLength() { return fixedResponseLength; }
    public byte[] getResponseTerminator() { return responseTerminator; }
    public int getIdleGapMs() { return idleGapMs; }
    public int getMaxMessageSizeBytes() { return maxMessageSizeBytes; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean sendLengthHeader = true;
        private boolean expectResponseHeader = true;
        private int lengthHeaderSize = 2;
        private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
        private boolean autoDetect = false;
        private int fixedResponseLength = 0;
        private byte[] responseTerminator = null;
        private int idleGapMs = 150;
        private int maxMessageSizeBytes = 0;

        public Builder sendLengthHeader(boolean value) {
            this.sendLengthHeader = value;
            return this;
        }

        public Builder expectResponseHeader(boolean value) {
            this.expectResponseHeader = value;
            return this;
        }

        public Builder lengthHeaderSize(int size) {
            this.lengthHeaderSize = size;
            return this;
        }

        public Builder byteOrder(ByteOrder order) {
            this.byteOrder = order;
            return this;
        }

        public Builder autoDetect(boolean value) {
            this.autoDetect = value;
            return this;
        }

        public Builder fixedResponseLength(int length) {
            this.fixedResponseLength = length;
            return this;
        }

        public Builder responseTerminator(byte[] terminator) {
            this.responseTerminator = terminator;
            return this;
        }

        public Builder idleGapMs(int ms) {
            this.idleGapMs = ms;
            return this;
        }

        public Builder maxMessageSizeBytes(int bytes) {
            this.maxMessageSizeBytes = bytes;
            return this;
        }

        public FramingOptions build() {
            int lhs = this.lengthHeaderSize;
            if (lhs != 2 && lhs != 4) {
                throw new IllegalArgumentException("Length header size must be 2 or 4 bytes.");
            }
            return new FramingOptions(this);
        }
    }
}


