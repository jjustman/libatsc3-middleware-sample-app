package com.nextgenbroadcast.mobile.middleware.settings

import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Profile
import com.nextgenbroadcast.mobile.middleware.location.FrequencyLocation

interface IReceiverSettings {
    var frequencyLocation: FrequencyLocation?
    var lastFrequency: List<Int>
    var receiverProfile: Atsc3Profile?
}