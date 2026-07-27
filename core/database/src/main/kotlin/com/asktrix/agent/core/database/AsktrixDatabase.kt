package com.asktrix.agent.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asktrix.agent.core.database.dao.CallRecordDao
import com.asktrix.agent.core.database.dao.ClientDao
import com.asktrix.agent.core.database.dao.OutboxDao
import com.asktrix.agent.core.database.dao.TimelineDao
import com.asktrix.agent.core.database.entity.CachedCallRecordEntity
import com.asktrix.agent.core.database.entity.CachedClientEntity
import com.asktrix.agent.core.database.entity.CachedTimelineEntity
import com.asktrix.agent.core.database.entity.OutboxEntity

@Database(
    entities = [
        CachedClientEntity::class,
        CachedTimelineEntity::class,
        CachedCallRecordEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AsktrixDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun timelineDao(): TimelineDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val NAME = "asktrix-cache.db"
    }
}
