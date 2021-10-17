package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

internal object SGFragmentType {
    const val UNSPECIFIED = 0
    const val SERVICE = 1
    const val CONTENT = 2
    const val SCHEDULE = 3
}

/*
Guide fragment, with the following values:
0 – unspecified
1 – ‘Service’ Fragment
2 – ‘Content’ fragment
3 – ‘Schedule’ Fragment
4 – ‘Access’ Fragment
5 – ‘PurchaseItem’ Fragment
6 – ‘PurchaseData’ Fragment
7– ‘PurchaseChannel’ Fragment
8 – ‘PreviewData’ Fragment
9 – ‘InteractivityData’ Fragment
10-127 – reserved for BCAST extensions
128-255 – available for proprietary extensions
 */