package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.*
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGServiceEntity

@Dao
interface SGServiceDAO : BaseDao<SGServiceEntity> {
    @Query("SELECT * FROM sg_service")
    fun getAll(): List<SGServiceEntity>

    @Query("DELETE FROM sg_service")
    fun deleteAll()
}