package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.dao.*
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities.*

@Database(entities = [
    SGServiceEntity::class,
    SGScheduleEntity::class,
    SGScheduleContentEntity::class,
    SGPresentationEntity::class,
    SGContentEntity::class,
    SGContentNameEntity::class,
    SGContentDescriptionEntity::class,
    SGContentServiceIdEntity::class
], version = 1)
abstract class SGDataBase: RoomDatabase() {

    abstract fun serviceDAO(): SGServiceDAO
    abstract fun scheduleDAO(): SGScheduleDAO
    abstract fun scheduleContentDAO(): SGScheduleContentDAO
    abstract fun presentationDAO(): SGPresentationDAO
    abstract fun contentDAO(): SGContentDAO
    abstract fun contentNameDAO(): SGContentNameDAO
    abstract fun contentDescriptionDAO(): SGContentDescriptionDAO
    abstract fun contentServiceIdDAO(): SGContentServiceIdDAO
    abstract fun sgScheduleMapDAO(): SGScheduleMapDAO

    companion object {
        @Volatile
        private var INSTANCE: SGDataBase? = null

        fun getDatabase(context: Context): SGDataBase {
            val instance = INSTANCE
            return instance ?: synchronized(this) {
                val instance2 = INSTANCE
                instance2 ?: Room.inMemoryDatabaseBuilder(
                        context,
                        SGDataBase::class.java
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}