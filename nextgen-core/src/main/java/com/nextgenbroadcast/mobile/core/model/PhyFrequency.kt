package com.nextgenbroadcast.mobile.core.model

class PhyFrequency(
        val list: List<Int>,
        val source: Source
) {
    enum class Source {
        USER, AUTO
    }

    companion object {
        fun auto(frequency: List<Int>) = PhyFrequency(frequency, Source.AUTO)
        fun user(frequency: List<Int>) = PhyFrequency(frequency, Source.USER)
        fun default(source: Source) = PhyFrequency(emptyList(), source)
    }
}