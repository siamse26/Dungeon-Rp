package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Enums for standard RPG variables
enum class Rarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
}

enum class ItemType {
    WEAPON, SHIELD, HELMET, CHEST
}

enum class CompanionType {
    NONE, CLERIC, MAGE, ROGUE
}

@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = 1,
    val level: Int = 1,
    val xp: Int = 0,
    val gold: Int = 100,
    val maxHp: Int = 100,
    val currentHp: Int = 100,
    val baseAttack: Int = 12,
    val baseDefense: Int = 4,
    val skillPoints: Int = 0,
    val selectedCompanion: String = CompanionType.NONE.name,
    val currentStageId: Int = 1,
    val currentFloor: Int = 1,
    val unlockedSkills: String = "", // comma separated list
    val equippedSkills: String = "",  // comma separated list
    val characterClass: String = "Knight",
    val outfitStyle: String = "Vanguard Crimson",
    val isOnlineMode: Boolean = false,
    val unsyncedLootCount: Int = 0,
    val discoveredWaypoints: String = "1_1" // comma separated list like "1_1,1_2"
)

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // WEAPON, SHIELD, HELMET, CHEST
    val rarity: String, // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    val bonusAttack: Int,
    val bonusDefense: Int,
    val bonusHp: Int,
    val bonusCrit: Int, // critical strike chance percentage
    val description: String,
    val isEquipped: Boolean = false,
    val purchaseGoldValue: Int = 30,
    val attackSpeed: Float = 1.0f,
    val elementalResist: Int = 0,
    val dodgeChance: Int = 0,
    val isUnique: Boolean = false,
    val isSynced: Boolean = true // Track local modifications that need cloud sync
)

@Dao
interface GameDao {
    @Query("SELECT * FROM player_state WHERE id = 1 LIMIT 1")
    fun getPlayerState(): Flow<PlayerStateEntity?>

    @Query("SELECT * FROM player_state WHERE id = 1 LIMIT 1")
    suspend fun getPlayerStateDirect(): PlayerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayerState(state: PlayerStateEntity)

    @Query("SELECT * FROM items")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isEquipped = 1")
    fun getEquippedItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isSynced = 0")
    suspend fun getUnsyncedItems(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE isEquipped = 0")
    suspend fun clearUnequippedLoot()
}

@Database(entities = [PlayerStateEntity::class, ItemEntity::class], version = 4, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
