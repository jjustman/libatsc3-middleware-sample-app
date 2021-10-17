package com.nextgenbroadcast.mobile.middleware.provider.mmt;

import android.util.ArrayMap;
import android.util.Log;

import com.nextgenbroadcast.mmt.exoplayer2.ext.MMTClockAnchor;
import com.nextgenbroadcast.mobile.middleware.atsc3.buffer.Atsc3RingBuffer;
import com.nextgenbroadcast.mobile.player.MMTConstants;

import org.ngbp.libatsc3.middleware.android.mmt.MmtPacketIdContext;
import org.ngbp.libatsc3.middleware.android.mmt.MpuMetadata_HEVC_NAL_Payload;
import org.ngbp.libatsc3.middleware.android.mmt.models.MMTAudioDecoderConfigurationRecord;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MMTFragmentWriter {
    public static final String TAG = MMTFragmentWriter.class.getSimpleName();

    private static final int MAX_FIRST_MFU_WAIT_TIME = 5000;
    private static final byte RING_BUFFER_PAGE_INIT = 1;
    private static final byte RING_BUFFER_PAGE_FRAGMENT = 2;
    private static final int FRAGMENT_PACKET_HEADER = Integer.BYTES /* packet_id */ + Integer.BYTES /* sample_number */ + Long.BYTES /* mpu_presentation_time_uS_from_SI */ + 7 /* reserved */;

    private static final int AC_4_CODE = getIntegerCodeForString(MMTAudioDecoderConfigurationRecord.AC_4_ID);

    //ac-4 sync frame header
    private final byte[] ac4header = {(byte) 0xAC, 0x40, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00};

    // SIZE_SAMPLE_HEADER
    private final byte[] emptyFragmentHeader = {(byte) MMTConstants.TRACK_TYPE_EMPTY, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    private final Boolean audioOnly;
    private final int serviceId;
    private final Atsc3RingBuffer ringBuffer;
    private final ByteBuffer fragmentBuffer = ByteBuffer.allocate(1024 * 1024);
    private final ArrayMap<Integer, MMTAudioDecoderConfigurationRecord> audioConfigurationMap = new ArrayMap<>();

    private ByteBuffer InitMpuMetadata_HEVC_NAL_Payload = null;
    private ByteBuffer headerBuffer;

    private long videoMfuPresentationTimestampUs = Long.MAX_VALUE;
    private final ArrayMap<Integer, Long> audioMfuPresentationTimestampMap = new ArrayMap<>();
    private long stppMfuPresentationTimestampUs = Long.MAX_VALUE;

    private volatile boolean isActive = true;
    private boolean sendFileHeader = true;
    private boolean firstKeyFrameReceived = false;

    private long mpuWaitingStartTime;

    public MMTFragmentWriter(int serviceId, Atsc3RingBuffer fragmentBuffer, boolean audioOnly) {
        this.serviceId = serviceId;
        this.ringBuffer = fragmentBuffer;
        this.audioOnly = audioOnly;

        this.fragmentBuffer.limit(0);
    }

    public int getServiceId() {
        return serviceId;
    }

    public boolean isActive() {
        return isActive;
    }

    public int write(FileOutputStream out) throws IOException {
        if (!isActive) throw new IOException("Reader is not active");

        int bytesRead = 0;
        if (sendFileHeader) {
            if (!audioOnly && !hasMpuMetadata()) {
                if (mpuWaitingStartTime == 0) {
                    mpuWaitingStartTime = System.currentTimeMillis();
                }

                scanMpuMetadata(fragmentBuffer);

                if (!hasMpuMetadata()) {
                    if ((System.currentTimeMillis() - mpuWaitingStartTime) < MAX_FIRST_MFU_WAIT_TIME) {
                        return 0;
                    } else {
                        throw new IOException("Can't get MPU Metadata");
                    }
                }
            }

            //TODO: we need better criteria
            if (audioConfigurationMap.isEmpty()) {
                return 0;
            }

            if (headerBuffer == null) {
                headerBuffer = createFileHeader();
            }

            bytesRead += writeBuffer(out, headerBuffer);
            out.flush();

            sendFileHeader = false;
        }

        bytesRead += writeQueue(out);

        return bytesRead;
    }

    public void close() {
        isActive = false;
    }

    private ByteBuffer createFileHeader() {
        byte videoTrackCount = (byte) (audioOnly ? 0 : 1);
        byte audioTrackCount = (byte) audioConfigurationMap.size();
        byte textTrackCount = (byte) (audioOnly ? 0 : 1);

        int mpuMetadataSize = getMpuMetadataSize();
        int videoHeaderSize = MMTConstants.VIDEO_TRACK_HEADER_SIZE * videoTrackCount + mpuMetadataSize;
        int audioHeaderSize = MMTConstants.AUDIO_TRACK_HEADER_SIZE * audioTrackCount;
        int textHeaderSize = MMTConstants.CC_TRACK_HEADER_SIZE * textTrackCount;
        int headerSize = MMTConstants.HEADER_SIZE + videoHeaderSize + audioHeaderSize + textHeaderSize;

        ByteBuffer fileHeaderBuffer = ByteBuffer.allocate(MMTConstants.mmtSignature.length + headerSize);

        // Read identification header
        fileHeaderBuffer.put(MMTConstants.mmtSignature);

        // write stream Header data
        fileHeaderBuffer.putInt(headerSize);

        if (videoTrackCount > 0) {
            int videoFormat = getIntegerCodeForString("hev1");

            // write initial MFU Metadata
            InitMpuMetadata_HEVC_NAL_Payload.rewind();

            fileHeaderBuffer
                    .putInt(MMTConstants.VIDEO_TRACK_HEADER_SIZE + mpuMetadataSize)
                    .put((byte) MMTConstants.TRACK_TYPE_VIDEO)
                    .putInt(videoFormat)
                    .putInt(MmtPacketIdContext.video_packet_id) //TODO: replace MmtPacketIdContext.video_packet_id
                    .putInt(getVideoWidth())
                    .putInt(getVideoHeight())
                    .putFloat(getVideoFrameRate())
                    .putInt(mpuMetadataSize)
                    .put(InitMpuMetadata_HEVC_NAL_Payload);
        }

        if (audioTrackCount > 0) {
            for (MMTAudioDecoderConfigurationRecord info : audioConfigurationMap.values()) {
                //jjustman-2021-09-08 - set audioFormat from our asset_type fourcc code
                int audioFormat = getIntegerCodeForString(info.asset_type);

                fileHeaderBuffer
                        .putInt(MMTConstants.AUDIO_TRACK_HEADER_SIZE)
                        .put((byte) MMTConstants.TRACK_TYPE_AUDIO)
                        .putInt(audioFormat)
                        .putInt(info.packet_id)
                        .putInt(info.channel_count)
                        .putInt(info.sample_rate);
            }
        }

        if (textTrackCount > 0) {
            int ttmlFormat = getIntegerCodeForString("stpp");

            fileHeaderBuffer
                    .putInt(MMTConstants.CC_TRACK_HEADER_SIZE)
                    .put((byte) MMTConstants.TRACK_TYPE_TEXT)
                    .putInt(ttmlFormat)
                    .putInt(MmtPacketIdContext.stpp_packet_id); //TODO: replace MmtPacketIdContext.stpp_packet_id
        }

        fileHeaderBuffer.rewind();
        return fileHeaderBuffer;
    }

    // write empty fragment to buffer to check stream is still alive
    private void testOutStream(FileOutputStream out) throws IOException {
        out.write(emptyFragmentHeader,0 , emptyFragmentHeader.length);
        out.flush();
    }

    private int writeQueue(FileOutputStream out) throws IOException {
        testOutStream(out);

        int bytesRead = 0;
        while (isActive) {
            if (fragmentBuffer.remaining() == 0) {
                readFragment(fragmentBuffer);
            }

            // read the sample buffer
            int bytesToRead = fragmentBuffer.remaining();
            if (bytesToRead == 0) {
                break;
            }

            bytesRead += writeBuffer(out, fragmentBuffer);

            out.flush();
        }

        return bytesRead;
    }

    private int writeBuffer(FileOutputStream out, ByteBuffer buffer) throws IOException {
        int bytesToWrite = buffer.remaining();
        //jjustman-2021-09-01-do we need to .sync() out?
        out.write(buffer.array(), buffer.position(), bytesToWrite);
        buffer.limit(0);
        return bytesToWrite;
    }

    private void readFragment(ByteBuffer buffer) {
        int retryCount = 5;
        while (true) {
            int pageSize = ringBuffer.readNextPage(buffer);

            if (pageSize == -2) {
                if (--retryCount >= 0) {
                    continue; // we skipped page in some reason, let's try again
                } else {
                    return;
                }
            }

            if (pageSize <= 0) {
                buffer.limit(0);
                return;
            }

            int pageType = buffer.get();
            if (pageType != RING_BUFFER_PAGE_FRAGMENT) {
                if (--retryCount >= 0) {
                    continue;
                } else {
                    return;
                }
            }

            int service_id = ringBuffer.getInt(buffer);
            if (service_id != serviceId) {
                // it's a bad sign, probably receiver switched to another Service or we read a fragment from the previous session
                buffer.limit(0);
                return;
            }

            int packet_id = ringBuffer.getInt(buffer);
            int sample_number = ringBuffer.getInt(buffer);
            long mpu_presentation_time_uS_from_SI = ringBuffer.getLong(buffer);

            byte sampleType = MMTConstants.TRACK_TYPE_UNKNOWN;
            if (isVideoSample(packet_id)) {
                sampleType = MMTConstants.TRACK_TYPE_VIDEO;
            } else if (isAudioSample(packet_id)) {
                sampleType = MMTConstants.TRACK_TYPE_AUDIO;
            } else if (isTextSample(packet_id)) {
                sampleType = MMTConstants.TRACK_TYPE_TEXT;
            }

            // If it's AV stream skip all fragments except audio till video key frame received
            if (!audioOnly && !firstKeyFrameReceived) {
                if (sampleType == MMTConstants.TRACK_TYPE_VIDEO && isKeySample(sample_number)) {
                    firstKeyFrameReceived = true;
                } else if (sampleType != MMTConstants.TRACK_TYPE_AUDIO) {
                    buffer.limit(0);
                    continue;
                }
            }

            long computedPresentationTimestampUs = getPresentationTimestampUs(packet_id, sample_number, mpu_presentation_time_uS_from_SI);

            int headerDiff = Atsc3RingBuffer.RING_BUFFER_PAGE_HEADER_SIZE - MMTConstants.SIZE_SAMPLE_HEADER;

            if (sampleType == MMTConstants.TRACK_TYPE_AUDIO) {
                MMTAudioDecoderConfigurationRecord mmtAudioDecoderConfigurationRecord = audioConfigurationMap.get(packet_id);

                //check if our packet_id flow is ac-4, and prepend sync frame header as needed
                if (mmtAudioDecoderConfigurationRecord != null && getIntegerCodeForString(mmtAudioDecoderConfigurationRecord.asset_type) == AC_4_CODE) {
                    headerDiff -= ac4header.length;

                    ac4header[4] = (byte) (pageSize >> 16 & 0xFF);
                    ac4header[5] = (byte) (pageSize >> 8 & 0xFF);
                    ac4header[6] = (byte) (pageSize & 0xFF);

                    buffer.position(headerDiff + MMTConstants.SIZE_SAMPLE_HEADER);
                    buffer.put(ac4header);

                    pageSize += ac4header.length;
                }
            }

            buffer.position(headerDiff);
            buffer.put(sampleType)
                    .putInt(pageSize)
                    .putInt(packet_id)
                    .putLong(computedPresentationTimestampUs)
                    .put(isKeySample(sample_number) ? (byte) 1 : (byte) 0);

            int sampleRemaining = pageSize + MMTConstants.SIZE_SAMPLE_HEADER + headerDiff;

            if(false) {
                Log.d(TAG, String.format("readFragment: sampleType: %d, packetId: %d, sampleNumber: %d, presentationTimeUs: %d, isKey: %s, fragmentBuffer.position: %d, len: %d",
                        sampleType, packet_id, sample_number, computedPresentationTimestampUs, isKeySample(sample_number), headerDiff, sampleRemaining));
            }

            int limit = Math.max(sampleRemaining, 0);
            buffer.limit(limit);
            buffer.position(headerDiff);

            return;
        }
    }

    private boolean hasMpuMetadata() {
        return InitMpuMetadata_HEVC_NAL_Payload != null;
    }

    private void scanMpuMetadata(ByteBuffer buffer) {
        while (true) {
            int bufferLen = ringBuffer.readNextPage(buffer);
            if (bufferLen <= 0) {
                buffer.limit(0);
                return;
            }

            int pageType = buffer.get();
            if (pageType != RING_BUFFER_PAGE_INIT) continue;

            int service_id = ringBuffer.getInt(buffer);
            // we read a fragment from the previous session, skip it
            if (service_id != serviceId) continue;

            if (InitMpuMetadata_HEVC_NAL_Payload == null) {
                ByteBuffer init = ByteBuffer.allocate(bufferLen);
                init.put(buffer.array(), buffer.position() + FRAGMENT_PACKET_HEADER, bufferLen);
                InitMpuMetadata_HEVC_NAL_Payload = init;
            }

            buffer.limit(0);

            return;
        }
    }

    private int getVideoWidth() {
        return MmtPacketIdContext.video_packet_statistics.width > 0 ? MmtPacketIdContext.video_packet_statistics.width : MmtPacketIdContext.MmtMfuStatistics.FALLBACK_WIDTH;
    }

    //TODO: refactor to get rid of MmtPacketIdContext
    private int getVideoHeight() {
        return MmtPacketIdContext.video_packet_statistics.height > 0 ? MmtPacketIdContext.video_packet_statistics.height : MmtPacketIdContext.MmtMfuStatistics.FALLBACK_HEIGHT;
    }

    private float getVideoFrameRate() {
        return (float) 1000000.0 / MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us;
    }

    private boolean isVideoSample(int packet_id) {
        return MmtPacketIdContext.video_packet_id == packet_id;
    }

    private boolean isAudioSample(int packet_id) {
        return MmtPacketIdContext.selected_audio_packet_id == packet_id && audioConfigurationMap.containsKey(packet_id);
    }


    private boolean isTextSample(int packet_id) {
        return MmtPacketIdContext.stpp_packet_id == packet_id;
    }

    private boolean isKeySample(int sample_number) {
        return sample_number == 1;
    }

    private int getMpuMetadataSize() {
        if (InitMpuMetadata_HEVC_NAL_Payload == null) return 0;
        return InitMpuMetadata_HEVC_NAL_Payload.limit();
    }

    //jjustman-2020-12-22 - TODO: handle when mfu_presentation_time_uS_computed - push last value?

    //moved

    //jjustman-2021-09-02 - TODO: fix isAudioSample() to only check -> audioConfigurationMap.containsKey(packet_id); its checking if MmtPacketIdContext.selected_audio_packet_id == packet_id

    private long getPresentationTimestampUs_no_anchor(int packet_id, int sample_number, long mpu_presentation_time_uS_from_SI) {
        long mpuPresentationTimestampDeltaUs = 0;

        if (mpu_presentation_time_uS_from_SI > 0) {
            long mfu_presentation_time_uS_computed = 0;
            long extracted_sample_duration_us;
            if (isVideoSample(packet_id)) {
                if (MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us;
                    Log.d(TAG, String.format("getPresentationTimestampUs: Video: packet_id: %d, sampleNumber: %d, mpu_presentation_time_uS_from_SI: %d, extracted_sample_duration_us: %d",
                            packet_id, sample_number, mpu_presentation_time_uS_from_SI, MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us));
                }
            } else if (isAudioSample(packet_id)) {
                if ((extracted_sample_duration_us = MmtPacketIdContext.getAudioPacketStatistic(packet_id).extracted_sample_duration_us) > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * extracted_sample_duration_us;
                    Log.d(TAG, String.format("getPresentationTimestampUs: Audio: packet_id: %d, sample_number: %d, mpu_presentation_time_uS_from_SI: %d, extracted_sample_duration_us: %d",
                            packet_id, sample_number, mpu_presentation_time_uS_from_SI, MmtPacketIdContext.getAudioPacketStatistic(packet_id).extracted_sample_duration_us));

                }
            } else if (isTextSample(packet_id)) {
                if (MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us;
                    Log.d(TAG, String.format("getPresentationTimestampUs: Stpp: packet_id: %d, sample_number: %d, mpu_presentation_time_uS_from_SI: %d, extracted_sample_duration_us: %d",
                            packet_id, sample_number, mpu_presentation_time_uS_from_SI, MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us));

                }
            }

            if(mfu_presentation_time_uS_computed == 0) {
                Log.w(TAG, String.format("getPresentationTimestampUs: mfu_presentation_time_uS_computed is 0! packet_id: %d, sample_number: %d, mfu_presentation_time_uS_computed: %d, mpu_presentation_time_uS_from_SI: %d, extracted_sample_duration_us: %d",
                        packet_id, sample_number, mfu_presentation_time_uS_computed, mpu_presentation_time_uS_from_SI, MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us));
            }

            if (mfu_presentation_time_uS_computed > 0) {
                //todo: jjustman-2021-09-01 - also convert video and stpp to Map for anchor timestampUs
                if (isVideoSample(packet_id)) {
                    if (videoMfuPresentationTimestampUs == Long.MAX_VALUE) {
                        videoMfuPresentationTimestampUs = mfu_presentation_time_uS_computed;
                    }
                    mpuPresentationTimestampDeltaUs = mfu_presentation_time_uS_computed - videoMfuPresentationTimestampUs;
                } else if (isAudioSample(packet_id)) {
                    if (!audioMfuPresentationTimestampMap.containsKey(packet_id)) {
                        audioMfuPresentationTimestampMap.put(packet_id, mfu_presentation_time_uS_computed);
                    }
                    mpuPresentationTimestampDeltaUs = mfu_presentation_time_uS_computed - audioMfuPresentationTimestampMap.get(packet_id);
                } else if (isTextSample(packet_id)) {
                    if (stppMfuPresentationTimestampUs == Long.MAX_VALUE) {
                        stppMfuPresentationTimestampUs = mfu_presentation_time_uS_computed;
                    }
                    mpuPresentationTimestampDeltaUs = mfu_presentation_time_uS_computed - stppMfuPresentationTimestampUs;
                }
                return mpuPresentationTimestampDeltaUs;
            }
        }

        return 0;
    }

    //jjustman-2020-12-22 - TODO: handle when mfu_presentation_time_uS_computed - push last value?
    private long getPresentationTimestampUs(int packet_id, int sample_number, long mpu_presentation_time_uS_from_SI) {
        if (mpu_presentation_time_uS_from_SI > 0) {
            long mfu_presentation_time_uS_computed = 0;
            long extracted_sample_duration_us;

            long track_anchor_timestamp_us = 0;
            if (isVideoSample(packet_id)) {
                if (MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * MmtPacketIdContext.video_packet_statistics.extracted_sample_duration_us;
                }
            } else if (isAudioSample(packet_id)) {
                if ((extracted_sample_duration_us = MmtPacketIdContext.getAudioPacketStatistic(packet_id).extracted_sample_duration_us) > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * extracted_sample_duration_us;
                }
            } else if (isTextSample(packet_id)) {
                if (MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us > 0) {
                    mfu_presentation_time_uS_computed = mpu_presentation_time_uS_from_SI + (sample_number - 1) * MmtPacketIdContext.stpp_packet_statistics.extracted_sample_duration_us;
                }
            }

            if (mfu_presentation_time_uS_computed > 0) {
                //todo: expand size as needed, every ~ mfu_presentation_time_uS_computed 1000000uS
                if (isVideoSample(packet_id)) {
                    if (videoMfuPresentationTimestampUs == Long.MAX_VALUE) {
                        videoMfuPresentationTimestampUs = mfu_presentation_time_uS_computed;
                        MMTClockAnchor.SystemClockAnchor = System.currentTimeMillis() + MMTClockAnchor.SYSTEM_CLOCK_ANCHOR_PTS_OFFSET_MS;

                    }
                    track_anchor_timestamp_us = videoMfuPresentationTimestampUs;
                } else if (isAudioSample(packet_id)) {
                    if (!audioMfuPresentationTimestampMap.containsKey(packet_id)) {
                        audioMfuPresentationTimestampMap.put(packet_id, mfu_presentation_time_uS_computed);
                    }
                    track_anchor_timestamp_us = audioMfuPresentationTimestampMap.get(packet_id);

                } else if (isTextSample(packet_id)) {
                    if (stppMfuPresentationTimestampUs == Long.MAX_VALUE) {
                        stppMfuPresentationTimestampUs = mfu_presentation_time_uS_computed;
                    }
                    track_anchor_timestamp_us = stppMfuPresentationTimestampUs;

                }
                long anchorMfuPresentationTimestampUs = getMinNonZeroMfuPresentationTimestampForAnchor(packet_id);
                long mpuPresentationTimestampDeltaUs = mfu_presentation_time_uS_computed - anchorMfuPresentationTimestampUs;

                return mpuPresentationTimestampDeltaUs + MMTClockAnchor.MPU_PRESENTATION_DELTA_PTS_OFFSET_US;
            }
        }

        return 0;
    }

    private long getMinNonZeroMfuPresentationTimestampForAnchor(int packet_id) {
        long minNonZeroMfuPresentationTimestampForAnchor = Long.MAX_VALUE;

        Long audioMfuPresentationTimestampUs = audioMfuPresentationTimestampMap.get(packet_id);
        if (videoMfuPresentationTimestampUs != Long.MAX_VALUE) {
            minNonZeroMfuPresentationTimestampForAnchor = videoMfuPresentationTimestampUs;
            if (audioMfuPresentationTimestampUs != null) {
                minNonZeroMfuPresentationTimestampForAnchor = Math.min(minNonZeroMfuPresentationTimestampForAnchor, audioMfuPresentationTimestampUs);
                if (stppMfuPresentationTimestampUs != Long.MAX_VALUE) {
                    minNonZeroMfuPresentationTimestampForAnchor = Math.min(minNonZeroMfuPresentationTimestampForAnchor, stppMfuPresentationTimestampUs);
                }
            }
        } else if (audioMfuPresentationTimestampUs != null) {
            minNonZeroMfuPresentationTimestampForAnchor = audioMfuPresentationTimestampUs;
            if (stppMfuPresentationTimestampUs != Long.MAX_VALUE) {
                minNonZeroMfuPresentationTimestampForAnchor = Math.min(minNonZeroMfuPresentationTimestampForAnchor, stppMfuPresentationTimestampUs);
            }
        } else if (stppMfuPresentationTimestampUs != Long.MAX_VALUE) {
            minNonZeroMfuPresentationTimestampForAnchor = stppMfuPresentationTimestampUs;
        }

        if (MMTClockAnchor.SystemClockAnchor == 0) {
            //MMTClockAnchor.SystemClockAnchor = System.currentTimeMillis() + MMTClockAnchor.SYSTEM_CLOCK_ANCHOR_PTS_OFFSET_MS;
        }

        if((MMTClockAnchor.MfuClockAnchor == 0) || (minNonZeroMfuPresentationTimestampForAnchor < MMTClockAnchor.MfuClockAnchor)) {
//            MMTClockAnchor.MfuClockAnchor = minNonZeroMfuPresentationTimestampForAnchor;
//            long lastSystemClockAnchor = MMTClockAnchor.SystemClockAnchor;
//            MMTClockAnchor.SystemClockAnchor = System.currentTimeMillis() + MMTClockAnchor.SYSTEM_CLOCK_ANCHOR_PTS_OFFSET_MS;
//            Log.i(TAG, String.format("old systemClockAnchor: %d, new systemClockAnchor: %d, diff: %d", lastSystemClockAnchor, MMTClockAnchor.SystemClockAnchor, (lastSystemClockAnchor - MMTClockAnchor.SystemClockAnchor)));
//
        }

        return minNonZeroMfuPresentationTimestampForAnchor;
    }

    public void InitMpuMetadata_HEVC_NAL_Payload(MpuMetadata_HEVC_NAL_Payload payload) {
        if (!isActive) return;

        if (InitMpuMetadata_HEVC_NAL_Payload == null) {
            InitMpuMetadata_HEVC_NAL_Payload = payload.myByteBuffer.duplicate();
        } /*else {
            //Do not free it here payload.releaseByteBuffer();
        }*/
    }

    public void pushAudioDecoderConfigurationRecord(MMTAudioDecoderConfigurationRecord mmtAudioDecoderConfigurationRecord) {
        audioConfigurationMap.put(mmtAudioDecoderConfigurationRecord.packet_id, mmtAudioDecoderConfigurationRecord);
    }

    /**
     * Copied from ExoPlayer/Utils.java
     */
    private static int getIntegerCodeForString(String string) {
        int length = string.length();
        if (length > 4) throw new IllegalArgumentException();
        int result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= string.charAt(i);
        }
        return result;
    }
}
