package com.nextgenbroadcast.mobile.middleware.atsc3.entities.held

object DeviceCapabilityCodes {
    const val FORBIDDEN = 0x0000 // Forbidden

    // Capability Category: FEC Algorithms
    const val REPAIR_ONLY = 0x0200 // RFC 6330 Repair-only A/332 Section 5.3.3

    // Capability Category: Media Types
    const val AVC = 0x0500 // AVC standard definition video A/332 Section A.2.8 [16]
    const val AVC_H = 0x0501 // AVC high definition video A/332 Section A.2.9 [16]
    const val AC3 = 0x0502 // AC-3 audio A/332 Section A.2.10 [16]
    const val EAC3 = 0x0503 // E-AC-3 audio A/332 Section A.2.11 [16]
    const val DTS_HD = 0x0504 // DTS-HD audio A/332 Section A.2.18 [16]
    const val HE_AAC_MPEG = 0x0505 // HE AAC v2 with MPEG Surround A/332 Section A.2.21 [16]
    const val HE_AAC_L6 = 0x0506 // HE AAC v2 Level 6 audio A/332 Section A.2.22 [16]
    const val VIDEO_3D_SS = 0x0507 // Frame-compatible 3D video (Side-by-Side) A/332 Section A.2.23 [16]
    const val VIDEO_3D_TB = 0x0508 // Frame-compatible 3D video (Top-and-Bottom) A/332 Section A.2.24 [16]
    const val SHVC = 0x0509 // ATSC 3.0 SHVC Video Section 5.3.1
    const val HDR = 0x050A // ATSC 3.0 HDR Video Section 5.3.2
    const val AC4 = 0x050B // Dolby® AC4 Audio A/342 Part 2 [14]
    const val MPEG_H = 0x050C // MPEG-H Audio A/342 Part 3 [15]
    const val IMSC1_TEXT = 0x050D // IMSC1 Text Profile A/343 [18]
    const val IMSC1_IMAGE = 0x050E // IMSC1 Image Profile A/343 [18]

    // Capability Category: Internet Link
    const val LINK_56KBPS = 0x0600 // Internet link, downward rate 56,000 bps or better A/332 Section A.2.25 [16]
    const val LINK_512KBPS = 0x0601 // Internet link, downward rate 512,000 bps or better A/332 Section A.2.26 [16]
    const val LINK_2MBPS = 0x0602 // Internet link, downward rate 2,000,000 bps or better A/332 Section A.2.27 [16]
    const val LINK_10MBPS = 0x0603 // Internet link, downward rate 10,000,000 bps or better A/332 Section A.2.28 [16]
}