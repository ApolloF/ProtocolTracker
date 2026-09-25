package com.apollof.protocoltracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CompoundEntity::class, PhaseEntity::class, PlanItemEntity::class, DoseLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun compounds(): CompoundDao
    abstract fun phases(): PhaseDao
    abstract fun items(): PlanItemDao
    abstract fun logs(): DoseLogDao

    companion object {
        fun create(context: Context): TrackerDatabase =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "tracker.db").build()

        fun inMemory(context: Context): TrackerDatabase =
            Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).allowMainThreadQueries().build()
    }
}
