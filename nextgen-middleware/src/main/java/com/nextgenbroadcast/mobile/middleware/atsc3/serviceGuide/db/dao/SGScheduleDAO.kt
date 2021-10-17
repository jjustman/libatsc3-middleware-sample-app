package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGScheduleEntity

@Dao
interface SGScheduleDAO : BaseDao<SGScheduleEntity> {
    @Query("SELECT * FROM sg_schedule")
    fun getAll(): List<SGScheduleEntity>

    @Query("DELETE FROM sg_schedule")
    fun deleteAll()
}