package com.macrokey.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MacroBlock::class], version = 1, exportSchema = false)
abstract class MacroKeyDatabase : RoomDatabase() {

    abstract fun blockDao(): MacroBlockDao

    companion object {
        @Volatile
        private var INSTANCE: MacroKeyDatabase? = null

        fun getInstance(context: Context): MacroKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MacroKeyDatabase::class.java,
                    "macrokey_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
