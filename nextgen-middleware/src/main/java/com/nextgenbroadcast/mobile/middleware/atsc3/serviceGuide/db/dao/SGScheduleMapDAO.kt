package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface SGScheduleMapDAO {

    @Query("SELECT *, :category AS category FROM sg_service")
    fun getAllService(category: Int): Cursor

    @Query("SELECT *, :category AS category FROM sg_service WHERE serviceId = :id")
    fun getServiceById(id: Int, category: Int): Cursor

    @RawQuery
    fun getContentBy(query: SupportSQLiteQuery): Cursor

    @Transaction
    fun getContentBy(selection: String?, args: Array<String>, sortOrder: String?): Cursor {
        val query = "SELECT sch.*, schc.contentId, schp.startTime, schp.endTime, schp.duration, cnt.icon, cntn.name, cntd.description\n" +
                "FROM sg_schedule sch, sg_schedule_content schc, sg_presentation schp\n" +
                "LEFT JOIN sg_content cnt ON cnt.id = schc.contentId\n" +
                "LEFT JOIN (SELECT * FROM (SELECT * FROM sg_content_name WHERE language = ? UNION ALL SELECT * FROM sg_content_name WHERE language NOTNULL) GROUP BY contentId) cntn ON cntn.contentId = schc.contentId\n" +
                "LEFT JOIN (SELECT * FROM (SELECT * FROM sg_content_description WHERE language = ? UNION ALL SELECT * FROM sg_content_description WHERE language NOTNULL) GROUP BY contentId) cntd ON cntd.contentId = schc.contentId\n" +
                "WHERE schc.scheduleId = sch.id AND schp.content_Id = schc.contentId AND $selection\n" +
                "ORDER BY $sortOrder"

        return getContentBy(SimpleSQLiteQuery(query, args))
    }
}