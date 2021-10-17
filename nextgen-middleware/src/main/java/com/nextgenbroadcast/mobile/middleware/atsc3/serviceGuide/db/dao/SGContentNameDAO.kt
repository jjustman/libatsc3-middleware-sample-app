package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.SGContentNameEntity

@Dao
interface SGContentNameDAO : BaseDao<SGContentNameEntity>  {
    @Query("SELECT * FROM sg_content_name")
    fun getAll(): List<SGContentNameEntity>

    @Query("DELETE FROM sg_content_name")
    fun deleteAll()
}