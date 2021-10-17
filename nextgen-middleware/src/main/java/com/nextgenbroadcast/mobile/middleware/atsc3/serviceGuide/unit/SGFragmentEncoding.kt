package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

internal object SGFragmentEncoding {
    const val XML_OMA = 0
    const val SDP =     1
    const val MBMS =    2
    const val XML_ADP = 3
}

/*
0 – XML encoded OMA BCAST Service Guide fragment
1 – SDP fragment
2 – MBMS User Service Bundle Description (USBD) as specified
in [26.346] (see 5.1.2.4 ‘SessionDescription’ element)
3 – XML encoded Associated Delivery Procedure as specified in
[BCAST10-Distribution] section 5.3.4.
4-127 – reserved for future BCAST extensions
128-255 – available for proprietary extensions
 */