package com.kcum.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenDao {

    @Query("SELECT * FROM hidden_items ORDER BY hiddenAt DESC")
    fun getAll(): Flow<List<HiddenItem>>

    @Insert
    suspend fun insert(item: HiddenItem): Long

    @Delete
    suspend fun delete(item: HiddenItem)

    @Query("DELETE FROM hidden_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM hidden_items")
    suspend fun count(): Int
}
