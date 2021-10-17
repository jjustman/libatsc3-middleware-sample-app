package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGContentServiceIdEntity


@Dao
interface SGContentServiceIdDAO : BaseDao<SGContentServiceIdEntity> {
    @Query("SELECT * FROM sg_content_serviceId")
    fun getAll(): List<SGContentServiceIdEntity>

    @Query("DELETE FROM sg_content_serviceId")
    fun deleteAll()
}