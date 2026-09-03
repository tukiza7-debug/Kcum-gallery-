package com.kcum.gallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Pangkalan data Room untuk metadata Kcum Gallery.
 * - trash_items  : Recycle Bin (pulihkan sebelum 30 hari)
 * - hidden_items : album peribadi (fail disimpan dalam storan dalaman app)
 */
@Database(
    entities = [TrashItem::class, HiddenItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trashDao(): TrashDao
    abstract fun hiddenDao(): HiddenDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kcum_gallery.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
