package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGContentEntity

@Dao
interface SGContentDAO : BaseDao<SGContentEntity> {
    @Query("SELECT * FROM sg_content")
    fun getAll(): List<SGContentEntity>

    @Query("DELETE FROM sg_content")
    fun deleteAll()
}