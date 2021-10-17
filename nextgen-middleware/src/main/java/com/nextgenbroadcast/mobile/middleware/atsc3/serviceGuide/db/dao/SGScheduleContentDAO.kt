package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGScheduleContentEntity

@Dao
interface SGScheduleContentDAO : BaseDao<SGScheduleContentEntity> {
    @Query("SELECT * FROM sg_schedule_content")
    fun getAll(): List<SGScheduleContentEntity>

    @Query("DELETE FROM sg_schedule_content")
    fun deleteAll()
}