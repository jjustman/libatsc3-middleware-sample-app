package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide

import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGFragmentEncoding
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.IndexOutOfBoundsException

/*
Service Guide Delivery Unit structure
https://www.openmobilealliance.org/release/BCAST/V1_0_1-20130109-A/OMA-TS-BCAST_Service_Guide-V1_0_1-20130109-A.pdf

Service_Guide_Delivery_Unit {
    Unit_Header {
        extension_offset uimsbf32
        reserved 16 bits
        n_o_service_guide_fragments uimsbf24
        for(i=0; i< n_o_service_guide_fragments; i++) {
            fragmentTransportID[i] uimsbf32
            fragmentVersion[i] uimsbf32
            offset[i] uimsbf32
        }
    }
    Unit_Payload {
        for(i=0; i< n_o_service_guide_fragments; i++) {
            fragmentEncoding[i] uimsbf8
            if(fragmentEncoding[i]=0) {
                fragmentType uimsbf8
                XMLFragment bytestring
            }
            else if(fragmentEncoding[i]=1) {
                validFrom uimsbf32
                validTo uimsbf32
                fragmentID bytestring
                SDPfragment bytestring
            }
            else if(fragmentEncoding[i]=2) {
                validFrom uimsbf32
                validTo uimsbf32
                fragmentID bytestring
                USBDfragment bytestring
            }
            else if(fragmentEncoding[i]=3) {
                validFrom uimsbf32
                validTo uimsbf32
                fragmentID bytestring
                ADPfragment bytestring
            }
        }
    }
    if(extension_offset>0) {
        extension_type uimsbf8
        next_extension_offset uimsbf32
        extension_data bitstring
    }
}
*/

internal class SGDUFile private constructor() : Closeable {
    private val readBuffer = ByteArray(8)

    private var file: RandomAccessFile? = null
    private var unitPayloadStart: Long = 0
    private var offsets: LongArray? = null
    private var extensionOffset: Long = 0
    private var fragmentsCount: Int = 0
    private var nextIndex: Int = 0
    private var buffer: ByteArray = ByteArray(0)

    private fun RandomAccessFile.readUnsignedInt(): Long {
        readFully(readBuffer, 0, 4)
        return ((readBuffer[0].toLong() and 0xFF) shl 24) or
                ((readBuffer[1].toLong() and 0xFF) shl 16) or
                ((readBuffer[2].toLong() and 0xFF) shl 8) or
                (readBuffer[3].toLong() and 0xFF)
    }

    private fun RandomAccessFile.readUnsignedInt24(): Int {
        readFully(readBuffer, 0, 3)
        return ((readBuffer[0].toInt() and 0xFF) shl 16) or
                ((readBuffer[1].toInt() and 0xFF) shl 8) or
                (readBuffer[2].toInt() and 0xFF)
    }

    override fun close() {
        file?.close()
    }

    @Throws(IOException::class)
    fun open(file: File) {
        this.file = RandomAccessFile(file, "r").also { randomFile ->
            // Unit_Header
            extensionOffset = randomFile.readUnsignedInt()
            randomFile.skipBytes(2) // reserved
            fragmentsCount = randomFile.readUnsignedInt24()

            val offsets = LongArray(fragmentsCount).also {
                offsets = it
            }
            for (i in 0 until fragmentsCount) {
                // skip fragmentTransportID[i] + fragmentVersion[i]
                randomFile.skipBytes(Int.SIZE_BYTES + Int.SIZE_BYTES)
                offsets[i] = randomFile.readUnsignedInt()
            }

            unitPayloadStart = randomFile.filePointer
        }
    }

    fun hasNext() = nextIndex < fragmentsCount

    fun next(): Pair<Int, String>? {
        if (nextIndex >= fragmentsCount) return null

        val fragmentEncoding = readUnitType()
        if (fragmentEncoding != SGFragmentEncoding.XML_OMA) return null

        return readOMAXml(nextIndex++)
    }

    fun seekTo(index: Int): Boolean {
        if (index in 0 until fragmentsCount) {
            file?.let { randomFile ->
                randomFile.seek(unitPayloadStart)
                randomFile.skipBytes(getUnitOffset(index).toInt())
                nextIndex = index

                return true
            }
        }

        return false
    }

    private fun readUnitType(): Int {
        return file?.readUnsignedByte() ?: -1
    }

    private fun readOMAXml(index: Int): Pair<Int, String>? {
        return file?.let { randomFile ->
            val fragmentType = randomFile.readUnsignedByte()

            val size = getUnitSize(randomFile, index) - OMA_XML_OFFSET

            val buff = allocBuffer(size)
            buff.fill(0)
            randomFile.read(buff, 0, size)
            val xml = buff.toByteString().utf8()

            return Pair(fragmentType, xml)
        }
    }

    private fun allocBuffer(size: Int): ByteArray {
        if (buffer.size < size) {
            buffer = ByteArray(size)
        }

        return buffer
    }

    private fun getUnitOffset(index: Int): Long = offsets?.let { offsets ->
        offsets[index]
    } ?: throw IndexOutOfBoundsException("Service Guide Delivery Unit offsets are empty")

    private fun getUnitSize(randomFile: RandomAccessFile, index: Int): Int {
        return offsets?.let { offsets ->
            if (index + 1 < offsets.size) {
                offsets[index + 1] - offsets[index]
            } else if (extensionOffset > 0) {
                extensionOffset - offsets[index]
            } else {
                randomFile.length() - offsets[index]
            }
        }?.toInt() ?: 0
    }

    companion object {
        private val GENERAL_OFFSET = Byte.SIZE_BYTES /* fragmentEncoding */
        private val OMA_XML_OFFSET = GENERAL_OFFSET + Byte.SIZE_BYTES  /* fragmentEncoding + fragmentType */

        fun open(file: File) = SGDUFile().apply {
            open(file)
        }
    }
}