package com.nextgenbroadcast.mobile.middleware.provider.esg

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.SGDataBase
import java.lang.Integer.parseInt
import java.util.*


class ESGContentProvider : ContentProvider() {
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    private lateinit var AUTHORITY: String
    private lateinit var SERVICE_CONTENT_URI: Uri
    private lateinit var PROGRAM_CONTENT_URI: Uri

    private lateinit var db: SGDataBase

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false

        AUTHORITY = ESGContentAuthority.getAuthority(appContext)
        SERVICE_CONTENT_URI = ESGContentAuthority.getServiceContentUri(appContext)
        PROGRAM_CONTENT_URI = ESGContentAuthority.getProgramContentUri(appContext)

        uriMatcher.addURI(AUTHORITY, "$SERVICE_CONTENT_PATH/#", URI_SERVICE_BY_ID)
        uriMatcher.addURI(AUTHORITY, SERVICE_CONTENT_PATH, URI_ALL_SERVICES)
        uriMatcher.addURI(AUTHORITY, "$PROGRAM_CONTENT_PATH/#", URI_PROGRAM_BY_ID)
        uriMatcher.addURI(AUTHORITY, PROGRAM_CONTENT_PATH, URI_ALL_PROGRAMS)

        db = SGDataBase.getDatabase(appContext)

        return true
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            URI_ALL_SERVICES -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$SERVICE_CONTENT_PATH"
            URI_SERVICE_BY_ID -> ".$AUTHORITY.$SERVICE_CONTENT_PATH"
            URI_ALL_PROGRAMS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$PROGRAM_CONTENT_PATH"
            URI_PROGRAM_BY_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PROGRAM_CONTENT_PATH"
            else -> throw IllegalArgumentException("Wrong URI: $uri")
        }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val context = context ?: throw IllegalArgumentException("Wrong context")
        val contentResolver = context.contentResolver

        val cursor = fillCursor(uri, selectionArgs, selection, sortOrder)

        cursor.setNotificationUri(contentResolver, SERVICE_CONTENT_URI)

        return cursor
    }

    private fun fillCursor(uri: Uri, selectionArgs: Array<String>?, selection: String?, sortOrder: String?): Cursor {
        when (uriMatcher.match(uri)) {
            URI_ALL_SERVICES -> {
                return db.sgScheduleMapDAO().getAllService(1)
            }

            URI_SERVICE_BY_ID -> {
                var id = -1
                uri.lastPathSegment?.let {
                    id = parseInt(it)
                }
                return db.sgScheduleMapDAO().getServiceById(id, 1)
            }

            URI_ALL_PROGRAMS -> {
                if (selectionArgs.isNullOrEmpty()) throw IllegalArgumentException("Wrong selectionArgs: $selectionArgs")

                //TODO: use language from settings
                val lang = Locale.getDefault().language
                val args = arrayListOf<String>(lang, lang)
                args.addAll(selectionArgs)

                return db.sgScheduleMapDAO().getContentBy(selection, args.toTypedArray(), sortOrder)
            }

            URI_PROGRAM_BY_ID -> {
                //TODO: add implementation
            }

            else -> throw IllegalArgumentException("Wrong URI: $uri")
        }
        throw throw IllegalArgumentException("URI not matched")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        throw UnsupportedOperationException()
    }

    companion object {
        const val SERVICE_CONTENT_PATH = "services_data"
        const val PROGRAM_CONTENT_PATH = "programs_data"

        const val SERVICE_COLUMN_BSID = "bsid"
        const val SERVICE_COLUMN_ID = "serviceId"
        const val SERVICE_COLUMN_SHORT_NAME = "shortServiceName"
        const val SERVICE_COLUMN_GLOBAL_ID = "globalServiceId"
        const val SERVICE_COLUMN_MAJOR_CHANNEL_NO = "majorChannelNo"
        const val SERVICE_COLUMN_MINOR_CHANNEL_NO = "minorChannelNo"
        const val SERVICE_COLUMN_CATEGORY = "category"

        const val PROGRAM_COLUMN_START_TIME = "startTime"
        const val PROGRAM_COLUMN_END_TIME = "endTime"
        const val PROGRAM_COLUMN_DURATION = "duration"

        const val PROGRAM_COLUMN_CONTENT_ID = "id"
        const val PROGRAM_COLUMN_CONTENT_VERSION = "version"
        const val PROGRAM_COLUMN_CONTENT_ICON = "icon"
        const val PROGRAM_COLUMN_CONTENT_NAME = "name"
        const val PROGRAM_COLUMN_CONTENT_DESCRIPTION = "description"

        const val URI_ALL_SERVICES = 1
        const val URI_SERVICE_BY_ID = 2
        const val URI_ALL_PROGRAMS = 3
        const val URI_PROGRAM_BY_ID = 4
    }
}