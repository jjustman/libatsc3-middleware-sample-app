package com.nextgenbroadcast.mobile.core

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.Environment
import okio.ByteString.Companion.readByteString
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

object FileUtils {
    val TAG: String = FileUtils::class.java.simpleName

    fun writeExternalFile(context: Context, filename: String, content: String) {
        try {
            openExternalFileDescriptor(context, filename, "w")?.use { file ->
                file.createOutputStream().bufferedWriter().use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        } catch (e : IOException) {
            LOG.e(TAG, "Failed to write external file: $filename, source text: $content", e)
        }
    }

    fun writeExternalFile(context: Context, filename: String, uri: Uri) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { inputFile ->
                inputFile.createInputStream().use { fileInputStream ->
                    fileInputStream.readByteString(fileInputStream.available()).utf8().apply {
                        writeExternalFile(context, filename, this)
                    }
                }
            }
        } catch (e: IOException) {
            LOG.e(TAG, "Failed to write external file: $filename, source uri: $uri", e)
        }
    }

    fun readExternalFileAsString(context: Context, filename: String): String? {
        try {
            openExternalFileDescriptor(context, filename)?.use { file ->
                file.createInputStream().use { fileInputStream ->
                    return fileInputStream.readByteString(fileInputStream.available()).utf8()
                }
            }
        } catch (e: IOException) {
            LOG.e(TAG, "Failed to read external file: $filename", e)
        }

        return null
    }

    fun removeFile(context: Context, filename: String) {
        try {
            val externalFile: File = file(context, filename)
            if (externalFile.exists()) {
                externalFile.delete()
            }
        } catch (e: IOException) {
            LOG.e(TAG, "Failed to remove external file: $filename", e)
        }
    }

    @Throws(FileNotFoundException::class)
    fun openExternalFileDescriptor(context: Context, filename: String, mode: String = "r"): AssetFileDescriptor? {
        val externalFile: File = file(context, filename)

        if (externalFile.exists() || mode.contains("w", true)) {
            return context.contentResolver.openAssetFileDescriptor(Uri.fromFile(externalFile), mode)
        }

        return null
    }

    private fun file(context: Context, filename: String): File {
        var externalFile: File? = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            externalFile = File(Environment.getExternalStorageDirectory(), filename)
        }

        if (externalFile == null || !externalFile.exists()) {
            externalFile = File(context.getExternalFilesDir(null), filename)
        }
        return externalFile
    }

}