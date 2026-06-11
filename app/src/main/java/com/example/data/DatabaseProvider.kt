package com.example.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private var database: GameDatabase? = null
    private var repository: GameRepository? = null

    @Synchronized
    fun getDatabase(context: Context): GameDatabase {
        if (database == null) {
            database = Room.databaseBuilder(
                context.applicationContext,
                GameDatabase::class.java,
                "dungeon_crawler_db"
            )
            .fallbackToDestructiveMigration()
            .build()
        }
        return database!!
    }

    @Synchronized
    fun getRepository(context: Context): GameRepository {
        if (repository == null) {
            val db = getDatabase(context)
            repository = GameRepository(db.gameDao())
        }
        return repository!!
    }
}
