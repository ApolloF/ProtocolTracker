package com.apollof.protocoltracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CompoundEntity::class, PhaseEntity::class, PlanItemEntity::class, DoseLogEntity::class, JournalEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun compounds(): CompoundDao
    abstract fun phases(): PhaseDao
    abstract fun items(): PlanItemDao
    abstract fun logs(): DoseLogDao
    abstract fun journal(): JournalDao

    companion object {
        /** Symptom logs and bloodwork keep their extra fields as JSON in the journal. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal ADD COLUMN dataJson TEXT")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_2_3)

        fun create(context: Context): TrackerDatabase =
            Room.databaseBuilder(context, TrackerDatabase::class.java, "tracker.db")
                // Version 1 predates the redesign and is not migrated (decided with the user). Real migrations from 2 on.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): TrackerDatabase =
            Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).allowMainThreadQueries().build()
    }
}
