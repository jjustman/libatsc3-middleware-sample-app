package com.nextgenbroadcast.mobile.middleware.atsc3.buffer;

import com.nextgenbroadcast.mobile.core.LOG;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Atsc3RingBuffer {
    /*
        typedef struct {
            // page header
            int8_t  page_lock;
            int32_t page_num;
            int32_t payload_length;
            // payload header
            int8_t  type;
            int32_t service_id;
            int32_t packet_id;
            int32_t sample_number;
            int64_t presentationUs;
            // reserved for feature use
            int8_t  reserved[7];
        } RingBufferPageHeader;

     */

    public static final String TAG = Atsc3RingBuffer.class.getSimpleName();

    public static final int RING_BUFFER_PAGE_HEADER_SIZE = 37; // sizeof(RingBufferPageHeader)
    public static final int RING_BUFFER_PAYLOAD_HEADER_OFFSET = Byte.BYTES /* page lock */ + Integer.BYTES /* page number */ + Integer.BYTES /* payload length */;

    private final int pageSize;
    private final byte[] data = new byte[Long.BYTES];
    private final ByteBuffer buffer;
    private final boolean invertByteOrder;
    private final ByteBuffer zeroBuffer = ByteBuffer.allocate(0);

    private int currentPageNumber = 0;
    private int lastPageNumber;
    private int lastBufferPosition;

    public Atsc3RingBuffer(ByteBuffer buffer, int pageSize) {
        this.buffer = buffer;
        this.pageSize = pageSize;

        invertByteOrder = buffer.order() != ByteOrder.nativeOrder();
    }

    public int readNextPage(ByteBuffer outBuffer) {
        outBuffer.clear();
        int pageSize = readNextPage(0, outBuffer.capacity(), outBuffer.array(), zeroBuffer);
        if (pageSize > 0) {
            outBuffer.position(RING_BUFFER_PAYLOAD_HEADER_OFFSET);
        }
        return pageSize;
    }

    public int readNextPage(int offset, int readLength, byte[] outBuffer, ByteBuffer tailBuffer) {
        // Go to the head if we at the end of buffer
        if (buffer.remaining() < pageSize) {
            buffer.position(0);
        }

        int ringPosition = buffer.position();
        int saveRingPosition = ringPosition;

        // Peek page lock state and sequence number
        boolean earlyIsLocked = buffer.get() != 0;
        int earlyPageNum = getInt(buffer);
        if (earlyIsLocked || earlyPageNum <= currentPageNumber) {
            buffer.position(ringPosition);
            return -1;
        }

        // calculate payload size
        int earlyBufferLen = getInt(buffer);
        int fullPacketSize = RING_BUFFER_PAGE_HEADER_SIZE + earlyBufferLen;
        int bytesRemaining = fullPacketSize;
        int packetPageCount = (int) Math.ceil((float) fullPacketSize / pageSize);

        // skip page if payload bigger than out buffer
        if (bytesRemaining > readLength + tailBuffer.capacity()) {
            LOG.d(TAG, "Skip extra large fragment: " + bytesRemaining);
            buffer.position(ringPosition + pageSize * packetPageCount);
            return -2;
        }

        tailBuffer.clear();
        int outBufferBytesRemaining = readLength;

        // read payload from page
        buffer.position(ringPosition);
        int bytesToRead = Math.min(bytesRemaining, pageSize);
        if (bytesToRead > readLength) {
            buffer.get(outBuffer, offset, readLength);
            outBufferBytesRemaining -= readLength;
            buffer.get(tailBuffer.array(), 0, bytesToRead - readLength);
        } else {
            buffer.get(outBuffer, offset, bytesToRead);
            outBufferBytesRemaining -= bytesToRead;
        }

        bytesRemaining -= bytesToRead;

        // If payload is bigger then one page then read all related pages or skip all pages if something went wrong
        int segmentNum = 1;
        while (bytesRemaining > 0) {
            // Position to next page
            buffer.position(ringPosition + pageSize);
            if (buffer.remaining() < pageSize) {
                buffer.position(0);
            }

            ringPosition = buffer.position();

            // Peek next page lock state and sequence page number, drop if page is locked or sequency number is wrong
            boolean segmentIsLocked;
            // short await loop
            int retryCount = 5;
            while ((segmentIsLocked = (buffer.get() != 0)) && (retryCount > 0)) {
                buffer.position(buffer.position() - 1);
                retryCount--;
            }

            int segmentPageNum = getInt(buffer);
            if (segmentIsLocked || earlyPageNum != segmentPageNum) {
                int bytesToSkip = pageSize * (packetPageCount - segmentNum);
                if (buffer.remaining() < bytesToSkip) {
                    buffer.position(bytesToSkip - (buffer.limit() - ringPosition));
                } else {
                    buffer.position(ringPosition + bytesToSkip);
                }

                tailBuffer.limit(0);
                return -1;
            }

            // Check next page segment number
            byte nextSegmentNum = buffer.get(ringPosition + RING_BUFFER_PAGE_HEADER_SIZE  - 7 /* reserved part 7 bytes */);
            if (segmentNum != nextSegmentNum) {
                buffer.position(ringPosition + pageSize * (packetPageCount - segmentNum));
                tailBuffer.limit(0);
                return -1;
            }

            // Skip next page header
            buffer.position(ringPosition + RING_BUFFER_PAGE_HEADER_SIZE);
            int pageBytesToRead = Math.min(bytesRemaining, pageSize - RING_BUFFER_PAGE_HEADER_SIZE);

            // Read payload from next page payload
            if (outBufferBytesRemaining > 0) {
                int outBytesToRead = Math.min(outBufferBytesRemaining, pageBytesToRead);
                buffer.get(outBuffer, offset + fullPacketSize - bytesRemaining, outBytesToRead);
                outBufferBytesRemaining -= outBytesToRead;
                if (outBytesToRead < pageBytesToRead) {
                    buffer.get(tailBuffer.array(), fullPacketSize - bytesRemaining - readLength + outBytesToRead, pageBytesToRead - outBytesToRead);
                }
            } else {
                buffer.get(tailBuffer.array(), fullPacketSize - bytesRemaining - readLength, pageBytesToRead);
            }

            segmentNum++;
            bytesRemaining -= pageBytesToRead;
        }

        // Check page lock state and page sequence number
        // Skip page if it's locked or page number differs from peeked out earlier
        int outPosition = offset;
        boolean isLocked = outBuffer[outPosition] != 0;
        outPosition++;
        int currentPageNum = getInt(outPosition, outBuffer);
        outPosition += Integer.BYTES;
        int payloadLength = getInt(outPosition, outBuffer);
        if (!isLocked && currentPageNum == earlyPageNum) {
            lastPageNumber = currentPageNumber;
            lastBufferPosition = saveRingPosition;
            buffer.position(ringPosition + pageSize);
            currentPageNumber = currentPageNum;

            tailBuffer.limit(fullPacketSize > readLength ? fullPacketSize - readLength : 0);

            return payloadLength + RING_BUFFER_PAGE_HEADER_SIZE;
        } else {
            buffer.position(ringPosition);
            tailBuffer.limit(0);

            return -1;
        }
    }

    public void gotoPreviousPage() {
        buffer.position(lastBufferPosition);
        currentPageNumber = lastPageNumber;
    }

    public int getInt(ByteBuffer buffer) {
        buffer.get(data, 0, Integer.BYTES);

        if (invertByteOrder) {
            return (this.data[3] & 0xff) << 24
                    | (this.data[2] & 0xff) << 16
                    | (this.data[1] & 0xff) << 8
                    | this.data[0] & 0xff;
        } else {
            return (this.data[0] & 0xff) << 24
                    | (this.data[1] & 0xff) << 16
                    | (this.data[2] & 0xff) << 8
                    | this.data[3] & 0xff;
        }
    }

    public long getLong(ByteBuffer buffer) {
        buffer.get(data, 0, Long.BYTES);

        if (invertByteOrder) {
            return ((long) this.data[7] & 0xff) << 56
                    | ((long) this.data[6] & 0xff) << 48
                    | ((long) this.data[5] & 0xff) << 40
                    | ((long) this.data[4] & 0xff) << 32
                    | ((long) this.data[3] & 0xff) << 24
                    | ((long) this.data[2] & 0xff) << 16
                    | ((long) this.data[1] & 0xff) << 8
                    | (long) this.data[0] & 0xff;
        } else {
            return ((long) this.data[0] & 0xff) << 56
                    | ((long) this.data[1] & 0xff) << 48
                    | ((long) this.data[2] & 0xff) << 40
                    | ((long) this.data[3] & 0xff) << 32
                    | ((long) this.data[4] & 0xff) << 24
                    | ((long) this.data[5] & 0xff) << 16
                    | ((long) this.data[6] & 0xff) << 8
                    | (long) this.data[7] & 0xff;
        }
    }

    public int getInt(int offset, byte[] buffer) {
        if (invertByteOrder) {
            return (buffer[offset + 3] & 0xff) << 24
                    | (buffer[offset + 2] & 0xff) << 16
                    | (buffer[offset + 1] & 0xff) << 8
                    | buffer[offset] & 0xff;
        } else {
            return (buffer[offset] & 0xff) << 24
                    | (buffer[offset + 1] & 0xff) << 16
                    | (buffer[offset + 2] & 0xff) << 8
                    | buffer[offset + 3] & 0xff;
        }
    }

    public long getLong(int offset, byte[] buffer) {
        if (invertByteOrder) {
            return ((long) buffer[offset + 7] & 0xff) << 56
                    | ((long) buffer[offset + 6] & 0xff) << 48
                    | ((long) buffer[offset + 5] & 0xff) << 40
                    | ((long) buffer[offset + 4] & 0xff) << 32
                    | ((long) buffer[offset + 3] & 0xff) << 24
                    | ((long) buffer[offset + 2] & 0xff) << 16
                    | ((long) buffer[offset + 1] & 0xff) << 8
                    | (long) buffer[offset] & 0xff;
        } else {
            return ((long) buffer[offset] & 0xff) << 56
                    | ((long) buffer[offset + 1] & 0xff) << 48
                    | ((long) buffer[offset + 2] & 0xff) << 40
                    | ((long) buffer[offset + 3] & 0xff) << 32
                    | ((long) buffer[offset + 4] & 0xff) << 24
                    | ((long) buffer[offset + 5] & 0xff) << 16
                    | ((long) buffer[offset + 6] & 0xff) << 8
                    | (long) buffer[offset + 7] & 0xff;
        }
    }

    public void setInt(int offset, byte[] buffer, int value) {
        buffer[offset] = (byte) (value >> 24);
        buffer[offset + 1] = (byte) (value >> 16);
        buffer[offset + 2] = (byte) (value >> 8);
        buffer[offset + 3] = (byte) (value);
    }

    public void setLong(int offset, byte[] buffer, long value) {
        buffer[offset] = (byte) (value >> 56);
        buffer[offset + 1] = (byte) (value >> 48);
        buffer[offset + 2] = (byte) (value >> 40);
        buffer[offset + 3] = (byte) (value >> 32);
        buffer[offset + 4] = (byte) (value >> 24);
        buffer[offset + 5] = (byte) (value >> 16);
        buffer[offset + 6] = (byte) (value >> 8);
        buffer[offset + 7] = (byte) (value);
    }
}
