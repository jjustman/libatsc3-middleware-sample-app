package com.nextgenbroadcast.mobile.player.exoplayer;

import android.util.Log;
import android.util.SparseArray;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.nextgenbroadcast.mmt.exoplayer2.ext.MMTMediaTrackUtils;
import com.nextgenbroadcast.mobile.player.MMTConstants;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Atsc3MMTExtractor implements Extractor {
    public static final String TAG = Atsc3MMTExtractor.class.getSimpleName();

    private static final int DEFAULT_SAMPLE_BUFFER_SIZE = 1024;

    public static int ReadSample_TrackIsNull_counter = 0;
    public static int ReadSample_ExtractSampleHeader_counter = 0;

    private ExtractorOutput extractorOutput;

    private int currentSampleBytesRemaining;
    private int currentSampleSize;
    private int currentSampleId;
    private long currentSampleTimeUs;
    private byte currentSampleType;
    private boolean currentSampleIsKey;
    private boolean hasOutputFormat;
    private boolean hasOutputSeekMap;

    private final ParsableByteArray sampleBuffer = new ParsableByteArray(DEFAULT_SAMPLE_BUFFER_SIZE);
    private final SparseArray<MmtTrack> tracks = new SparseArray<>();

    public Atsc3MMTExtractor() {
        sampleBuffer.setPosition(sampleBuffer.limit());
    }

    @Override
    public boolean sniff(ExtractorInput input) {
        return true;
    }

    @Override
    public void init(ExtractorOutput output) {
        extractorOutput = output;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        //Log.d("Atsc3MMTExtractor", String.format("read: enter: with input: %s, position: %d", input, input.getPosition()));

        if (input.getPosition() == 0) {
            if (!readMMTHeader(input)) {
                throw new ParserException("Could not find MMT header.");
            }
        }
        maybeOutputFormat(input);
        int sampleReadResult = readSample(input);
        maybeOutputSeekMap();

        //Log.d("Atsc3MMTExtractor",String.format("read: exit: sampleReadResult: %d", sampleReadResult));

        return sampleReadResult;
    }

    @Override
    public void seek(long position, long timeUs) {
        currentSampleTimeUs = 0;
        currentSampleSize = 0;
        currentSampleBytesRemaining = 0;
    }

    @Override
    public void release() {
        // Do nothing
    }

    private boolean readMMTHeader(ExtractorInput input) throws IOException, InterruptedException {
        ByteBuffer buffer = ByteBuffer.allocate(MMTConstants.mmtSignature.length);
        input.readFully(buffer.array(), /* offset= */ 0, /* length= */ MMTConstants.mmtSignature.length);
        buffer.rewind();

        return Arrays.equals(buffer.array(), MMTConstants.mmtSignature);
    }

    private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
        try {
            if (currentSampleBytesRemaining == 0) {
                if (sampleBuffer.bytesLeft() < MMTConstants.SIZE_SAMPLE_HEADER) {
                    int offset = 0;
                    if (sampleBuffer.bytesLeft() > 0) {
                        offset = sampleBuffer.bytesLeft();
                        System.arraycopy(sampleBuffer.data, sampleBuffer.getPosition(), sampleBuffer.data, 0, sampleBuffer.bytesLeft());
                    }

                    extractorInput.readFully(sampleBuffer.data, /* offset= */ offset, /* length= */ sampleBuffer.limit() - offset);
                    sampleBuffer.setPosition(0);
                }

                currentSampleType = (byte) sampleBuffer.readUnsignedByte();
                currentSampleSize = sampleBuffer.readInt();
                currentSampleId = sampleBuffer.readInt();
                currentSampleTimeUs = sampleBuffer.readLong();
                currentSampleIsKey = sampleBuffer.readUnsignedByte() == 1;

                currentSampleBytesRemaining = currentSampleSize;

                if((ReadSample_ExtractSampleHeader_counter++ % 1000) == 0) {
                    Log.d(TAG, String.format("sampleType: %d, packet_id: %d, sampleTimeUs: %d, count: %d", currentSampleType, currentSampleId, currentSampleTimeUs, ReadSample_ExtractSampleHeader_counter));
                }

            } else if (sampleBuffer.bytesLeft() == 0) {
                extractorInput.readFully(sampleBuffer.data, /* offset= */ 0, /* length= */ sampleBuffer.limit());
                sampleBuffer.setPosition(0);
            }
        } catch (Exception ex) {
            Log.w(TAG, "readSample - packet_id: " + currentSampleId + ", Exception, returning END_OF_INPUT - causing ExoPlayer DataSource teardown/unwind, ex: " + ex + ", messgae: " + ex.getMessage() + ",  Type: " + currentSampleType + ", sample TimeUs: " + currentSampleTimeUs + ",  sample size: " + currentSampleSize, ex);

            return Extractor.RESULT_END_OF_INPUT;
        }

        MmtTrack track = tracks.get(currentSampleId);
        if (track == null) {
            int skipped = 0;
            if (currentSampleBytesRemaining > 0) {
                if (sampleBuffer.bytesLeft() > 0) {
                    skipped = Math.min(currentSampleBytesRemaining, sampleBuffer.bytesLeft());
                    sampleBuffer.skipBytes(skipped);
                }
                currentSampleBytesRemaining -= skipped;
            }

            if(((ReadSample_TrackIsNull_counter++) % 1000) == 0) {
                Log.w(TAG, String.format("readSample - packet_id: %d, track is NULL, skipped: %d, returning Extrator.RESULT_CONTINUE, count: %d", currentSampleId, skipped, ReadSample_TrackIsNull_counter));
            }

            return Extractor.RESULT_CONTINUE;
        }

        TrackOutput trackOutput = track.trackOutput;

        int bytesAppended = 0;
        if (sampleBuffer.bytesLeft() > 0) {
            int read = Math.min(sampleBuffer.bytesLeft(), currentSampleBytesRemaining);
            trackOutput.sampleData(sampleBuffer, read);
            bytesAppended += read;
        }

        currentSampleBytesRemaining -= bytesAppended;
        if (currentSampleBytesRemaining > 0) {
            //Log.w(TAG, String.format("readSample - packet_id: %d, currentSampleBytesRemaining: %d, returning Extractor.RESULT_CONTINUE", currentSampleId, currentSampleBytesRemaining));
            return Extractor.RESULT_CONTINUE;
        }

        @C.BufferFlags int sampleFlags = 0;
        if (currentSampleIsKey) {
            sampleFlags = C.BUFFER_FLAG_KEY_FRAME;
        }

        trackOutput.sampleMetadata(
                currentSampleTimeUs,
                sampleFlags,
                currentSampleSize,
                /* offset= */ 0,
                /* encryptionData= */ null);

        if(false) {
            Log.d(TAG, String.format("readSample: packet_id: %d, returning after trackOutput.sampleMetadata, currentSampleTimeUs: %d, currentSampleSize: %d", currentSampleId, currentSampleTimeUs, currentSampleSize));
        }

        return Extractor.RESULT_CONTINUE;
    }

    private void maybeOutputFormat(ExtractorInput input) throws IOException, InterruptedException {
        if (!hasOutputFormat) {
            hasOutputFormat = true;

            ByteBuffer buffer = ByteBuffer.allocate(MMTConstants.HEADER_SIZE);
            input.readFully(buffer.array(), /* offset= */ 0, /* length= */ MMTConstants.HEADER_SIZE);
            buffer.rewind();

            int mediaHeaderSize = buffer.getInt() - MMTConstants.HEADER_SIZE;

            buffer = ByteBuffer.allocate(mediaHeaderSize);
            input.readFully(buffer.array(), /* offset= */ 0, /* length= */ mediaHeaderSize);
            buffer.rewind();

            while (buffer.remaining() > 0) {
                int headerSize = buffer.getInt();
                int trackType = buffer.get();

                switch (trackType) {
                    case MMTConstants.TRACK_TYPE_VIDEO: {
                        int videoType = buffer.getInt();
                        int packetId = buffer.getInt();
                        int videoWidth = buffer.getInt();
                        int videoHeight = buffer.getInt();
                        float videoFrameRate = buffer.getFloat();
                        int initialDataSize = buffer.getInt();

                        byte[] data = new byte[initialDataSize];
                        buffer.get(data, 0, initialDataSize);

                        TrackOutput videoOutput = MMTMediaTrackUtils.createVideoOutput(extractorOutput, packetId, videoType, videoWidth, videoHeight, videoFrameRate, data);
                        if (videoOutput != null) {
                            tracks.put(packetId, new MmtTrack(videoOutput));
                        }
                    }
                    break;

                    case MMTConstants.TRACK_TYPE_AUDIO: {
                        int audioType = buffer.getInt();
                        int packetId = buffer.getInt();
                        int audioChannelCount = buffer.getInt();
                        int audioSampleRate = buffer.getInt();

                        Format audioFormat = MMTMediaTrackUtils.createAudioFormat(Integer.toString(packetId),
                                audioType, audioChannelCount, audioSampleRate, 0, null);

                        if (audioFormat != null) {
                            Log.d(TAG, String.format("maybeOutputFormat: TRACK_TYPE_AUDIO: created audioFormat for packet_id: %d, (int)audioType: %d, audioFormat: %s", packetId, audioType, audioFormat));

                            TrackOutput audioOutput = extractorOutput.track(packetId, C.TRACK_TYPE_AUDIO);
                            audioOutput.format(audioFormat);
                            tracks.put(packetId, new MmtTrack(audioOutput));
                        } else {
                            Log.w(TAG, String.format("maybeOutputFormat: TRACK_TYPE_AUDIO: unable to instantiate audioFormat for packet_id: %d, (int)audioType: %d", packetId, audioType));
                        }
                    }
                    break;

                    case MMTConstants.TRACK_TYPE_TEXT: {
                        int textType = buffer.getInt();
                        int packetId = buffer.getInt();

                        TrackOutput textOutput = MMTMediaTrackUtils.createTextOutput(extractorOutput, packetId, textType, "us");
                        if (textOutput != null) {
                            tracks.put(packetId, new MmtTrack(textOutput));
                        }
                    }
                    break;
                }
            }

            extractorOutput.endTracks();
        }
    }

    private void maybeOutputSeekMap() {
        if (hasOutputSeekMap) {
            return;
        }

        extractorOutput.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
        hasOutputSeekMap = true;
    }

    private static final class MmtTrack {
        public final TrackOutput trackOutput;

        public MmtTrack(TrackOutput trackOutput) {
            this.trackOutput = trackOutput;
        }
    }
}
