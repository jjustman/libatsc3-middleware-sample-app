package com.nextgenbroadcast.mobile.player;

import java.nio.charset.StandardCharsets;

public final class MMTConstants {
    public static final String MIME_MMT_VIDEO = "video/mmt";
    public static final String MIME_MMT_AUDIO = "audio/mmt";

    public static final int SIZE_SAMPLE_HEADER =
            Byte.BYTES          /* sample type */
            + Integer.BYTES     /* sample size */
            + Integer.BYTES     /* sample id */
            + Long.BYTES        /* sample presentation time */
            + Byte.BYTES;        /* is key frame */

    public static final int HEADER_SIZE =
            Integer.BYTES;       /* full header size */

    public static final int VIDEO_TRACK_HEADER_SIZE =
            Integer.BYTES       /* header size */
            + Byte.BYTES        /* track type*/
            + Integer.BYTES     /* video type - see Atom.java */
            + Integer.BYTES     /* video packet_id */
            + Integer.BYTES     /* video width */
            + Integer.BYTES     /* video height */
            + Float.BYTES       /* video frame rate */
            + Integer.BYTES;    /* video initial data length */

    public static final int AUDIO_TRACK_HEADER_SIZE =
            Integer.BYTES       /* header size */
            + Byte.BYTES        /* track type*/
            + Integer.BYTES     /* audio type - see Atom.java - Util.getIntegerCodeForString("ac-4");*/
            + Integer.BYTES     /* audio packet_id */
            + Integer.BYTES     /* audio channel count */
            + Integer.BYTES;    /* audio sample rate */

    public static final int CC_TRACK_HEADER_SIZE =
            Integer.BYTES       /* header size */
            + Byte.BYTES        /* track type*/
            + Integer.BYTES     /* text packet_id */
            + Integer.BYTES;    /* text type - see Atom.java - Util.getIntegerCodeForString("ac-4"); */

    public static final byte[] mmtSignature = "#!MMT\n".getBytes(StandardCharsets.UTF_8);

    public static final int TRACK_TYPE_EMPTY = -2;
    public static final int TRACK_TYPE_UNKNOWN = -1;
    public static final int TRACK_TYPE_AUDIO = 1;
    public static final int TRACK_TYPE_VIDEO = 2;
    public static final int TRACK_TYPE_TEXT = 3;
}
