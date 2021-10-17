package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGPresentationEntity

@Dao
interface SGPresentationDAO : BaseDao<SGPresentationEntity> {
    @Query("SELECT * FROM sg_presentation")
    fun getAll(): List<SGPresentationEntity>

    @Query("DELETE FROM sg_presentation")
    fun deleteAll()
}