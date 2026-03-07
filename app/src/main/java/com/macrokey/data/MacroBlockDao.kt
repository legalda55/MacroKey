package com.macrokey.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroBlockDao {

    @Query("SELECT * FROM macro_blocks ORDER BY sort_order ASC, id ASC")
    fun getAllBlocksFlow(): Flow<List<MacroBlock>>

    @Query("SELECT * FROM macro_blocks ORDER BY sort_order ASC, id ASC")
    suspend fun getAllBlocks(): List<MacroBlock>

    @Query("SELECT * FROM macro_blocks WHERE id = :id")
    suspend fun getBlockById(id: Long): MacroBlock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: MacroBlock): Long

    @Update
    suspend fun updateBlock(block: MacroBlock)

    @Delete
    suspend fun deleteBlock(block: MacroBlock)

    @Query("UPDATE macro_blocks SET usage_count = usage_count + 1 WHERE id = :id")
    suspend fun incrementUsageCount(id: Long)

    @Query("SELECT COUNT(*) FROM macro_blocks")
    suspend fun getBlockCount(): Int
}
