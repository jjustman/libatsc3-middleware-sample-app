package com.nextgenbroadcast.mobile.player.exoplayer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

public class RouteDASHLoadControl extends DefaultLoadControl {

    public RouteDASHLoadControl() {
        super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                /*DEFAULT_MIN_BUFFER_MS*/ 100,

                //jjustman-2020-08-06 - reduce down to 5 from 50s?
                /*DEFAULT_MAX_BUFFER_MS*/ 100,

                //jjustman-2020-08-06 - reduce down to 5 from 50s?
                /*DEFAULT_MAX_BUFFER_MS*/ 100,

                /*DEFAULT_BUFFER_FOR_PLAYBACK_MS*/ 0,

                /*DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS*/ 0,

                //jjustman-2020-08-06 - set to 32KB from DEFAULT_TARGET_BUFFER_BYTES
                /*DEFAULT_TARGET_BUFFER_BYTES*/ 1024,

                DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,

                // ~6 frames?
                /*DEFAULT_BACK_BUFFER_DURATION_MS*/ 100,

                /*
                  jjustman-2020-08-06 - don't set to false, will stall playback after 2s at:
                          2020-08-06 00:58:15.763 10351-10487/org.ngbp.libatsc3 D/AudioTrack: getTimestamp_l(98): device stall time corrected using current time 843079210248962
                2020-08-06 00:58:15.774 10351-10487/org.ngbp.libatsc3 W/AudioTrack: getTimestamp_l(98): retrograde timestamp time corrected, 843079204046150 < 843079210248962
                2020-08-06 00:58:16.554 10351-10487/org.ngbp.libatsc3 I/DashMediaSource: getAdjustedWindowDefaultStartPositionU, retuning value: -551000, windowDefaultStartPositionUs: 1000000, snapIndex.getTimeUs(segmentNum: 797212956): 2002000, defaultStartPositionInPeriodUs: 3553000;
                2020-08-06 00:58:17.561 10351-10487/org.ngbp.libatsc3 I/DashMediaSource: getAdjustedWindowDefaultStartPositionU, retuning value: 444000, windowDefaultStartPositionUs: 1000000, snapIndex.getTimeUs(segmentNum: 797212957): 4004000, defaultStartPositionInPeriodUs: 4560000;
                2020-08-06 00:58:17.695 10351-10487/org.ngbp.libatsc3 W/AudioTrackPositionTracker: pause invoked
                2020-08-06 00:58:17.695 10351-10487/org.ngbp.libatsc3 W/AudioTimestampPoller: reset with updateState(STATE_INITIALZING)
                */
                /*DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME*/ true);
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        //return super.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering);
        //jjustman-2020-08-06 - HACK for forcing playback start without waiting for first period
        return true;
    }

    @Override
    protected int calculateTargetBufferSize(Renderer[] renderers, TrackSelectionArray trackSelectionArray) {
        //return super.calculateTargetBufferSize(renderers, trackSelectionArray);

        int targetBufferSize = 0;
        for (int i = 0; i < renderers.length; i++) {
            if (trackSelectionArray.get(i) != null) {
                targetBufferSize += getDefaultBufferSize(renderers[i].getTrackType());
            }
        }
        return targetBufferSize;
    }

    private static int getDefaultBufferSize(int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_DEFAULT:
                //return DEFAULT_MUXED_BUFFER_SIZE;
                return /*DEFAULT_VIDEO_BUFFER_SIZE*/ 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE
                        + /*DEFAULT_AUDIO_BUFFER_SIZE*/ 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE
                        + DEFAULT_TEXT_BUFFER_SIZE;
            case C.TRACK_TYPE_AUDIO:
                //return DEFAULT_AUDIO_BUFFER_SIZE;
            case C.TRACK_TYPE_VIDEO:
                //return DEFAULT_VIDEO_BUFFER_SIZE;
                /* jjustman - 2020-08-06 - set to 2 * default_buffer segment size */
                return 2 * C.DEFAULT_BUFFER_SEGMENT_SIZE;
            case C.TRACK_TYPE_TEXT:
                return DEFAULT_TEXT_BUFFER_SIZE;
            case C.TRACK_TYPE_METADATA:
                return DEFAULT_METADATA_BUFFER_SIZE;
            case C.TRACK_TYPE_CAMERA_MOTION:
                return DEFAULT_CAMERA_MOTION_BUFFER_SIZE;
            case C.TRACK_TYPE_NONE:
                return 0;
            default:
                throw new IllegalArgumentException();
        }
    }
}
