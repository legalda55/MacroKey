package com.macrokey.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MacroBlock::class], version = 2, exportSchema = false)
abstract class MacroKeyDatabase : RoomDatabase() {

    abstract fun blockDao(): MacroBlockDao

    companion object {
        @Volatile
        private var INSTANCE: MacroKeyDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE macro_blocks ADD COLUMN image_path TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): MacroKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MacroKeyDatabase::class.java,
                    "macrokey_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
