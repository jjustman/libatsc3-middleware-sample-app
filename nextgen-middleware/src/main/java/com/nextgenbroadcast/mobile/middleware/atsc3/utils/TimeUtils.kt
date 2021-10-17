package com.nextgenbroadcast.mobile.middleware.atsc3.utils

import kotlin.math.roundToLong

/**
 * based on https://github.com/Free-Software-for-Android/NTPSync/blob/master/NTPSync/src/main/java/org/apache/commons/net/ntp/TimeStamp.java
 * */
internal object TimeUtils {

    /**
     * baseline NTP time if bit-0=0 -> 7-Feb-2036 @ 06:28:16 UTC
     */
    private var msb0baseTime = 2085978496000L

    /**
     * baseline NTP time if bit-0=1 -> 1-Jan-1900 @ 01:00:00 UTC
     */
    private var msb1baseTime = -2208988800000L

    /*
    init {
        val utcZone = TimeZone.getTimeZone("UTC")
        val calendar = Calendar.getInstance(utcZone)
        calendar[1900, Calendar.JANUARY, 1, 0, 0] = 0
        calendar[Calendar.MILLISECOND] = 0
        msb1baseTime = calendar.time.time
        calendar[2036, Calendar.FEBRUARY, 7, 6, 28] = 16
        calendar[Calendar.MILLISECOND] = 0
        msb0baseTime = calendar.time.time
    }
     */

    /***
     * Convert 64-bit NTP timestamp to Java standard time.
     *
     * Note that java time (milliseconds) by definition has less precision
     * then NTP time (picoseconds) so converting NTP timestamp to java time and back
     * to NTP timestamp loses precision. For example, Tue, Dec 17 2002 09:07:24.810 EST
     * is represented by a single Java-based time value of f22cd1fc8a, but its
     * NTP equivalent are all values ranging from c1a9ae1c.cf5c28f5 to c1a9ae1c.cf9db22c.
     *
     * @param ntpTimeValue
     * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * represented by this NTP timestamp value.
     */
    fun ntpTimeToUtc(ntpTimeValue: Long): Long {
        val seconds = ntpTimeValue ushr 32 and 0xffffffffL // high-order 32-bits
        val fraction = ntpTimeValue and 0xffffffffL // low-order 32-bits

        return ntpToUtc(seconds, fraction)
    }

    fun ntpSecondsToUtc(ntpSeconds: Long) = ntpToUtc(ntpSeconds, 0)

    private fun ntpToUtc(ntpSeconds: Long, ntpFraction: Long): Long {
        // Use round-off on fractional part to preserve going to lower precision
        val fraction = (1000.0 * ntpFraction / 0x100000000L).roundToLong()

        /*
         * If the most significant bit (MSB) on the seconds field is set we use
         * a different time base. The following text is a quote from RFC-2030 (SNTP v4):
         *
         *  If bit 0 is set, the UTC time is in the range 1968-2036 and UTC time
         *  is reckoned from 0h 0m 0s UTC on 1 January 1900. If bit 0 is not set,
         *  the time is in the range 2036-2104 and UTC time is reckoned from
         *  6h 28m 16s UTC on 7 February 2036.
         */
        val msb = ntpSeconds and 0x80000000L
        return if (msb == 0L) {
            // use base: 7-Feb-2036 @ 06:28:16 UTC
            msb0baseTime + ntpSeconds * 1000 + fraction
        } else {
            // use base: 1-Jan-1900 @ 01:00:00 UTC
            msb1baseTime + ntpSeconds * 1000 + fraction
        }
    }
}