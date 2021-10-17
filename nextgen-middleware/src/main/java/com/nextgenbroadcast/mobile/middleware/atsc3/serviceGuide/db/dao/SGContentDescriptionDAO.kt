package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGContentDescriptionEntity


@Dao
interface SGContentDescriptionDAO : BaseDao<SGContentDescriptionEntity> {
    @Query("SELECT * FROM sg_content_description")
    fun getAll(): List<SGContentDescriptionEntity>

    @Query("DELETE FROM sg_content_description")
    fun deleteAll()
}