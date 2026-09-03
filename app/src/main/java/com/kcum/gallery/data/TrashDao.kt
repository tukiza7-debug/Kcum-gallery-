package com.kcum.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    @Query("SELECT * FROM trash_items ORDER BY trashedAt DESC")
    fun getAll(): Flow<List<TrashItem>>

    @Query("SELECT * FROM trash_items")
    suspend fun getAllOnce(): List<TrashItem>

    /** Item lebih tua daripada `cutoff` (milisaat) - untuk auto-purge 30 hari */
    @Query("SELECT * FROM trash_items WHERE trashedAt < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<TrashItem>

    @Insert
    suspend fun insert(item: TrashItem): Long

    @Delete
    suspend fun delete(item: TrashItem)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM trash_items")
    suspend fun count(): Int
}
