package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.data.ItemEntity
import com.example.data.PlayerStateEntity
import org.json.JSONArray
import org.json.JSONObject

// Represents the remote database state (the "Cloud Profile")
data class CloudProfile(
    val level: Int = 1,
    val xp: Int = 0,
    val gold: Int = 100,
    val skillPoints: Int = 0,
    val unlockedSkills: String = "",
    val characterClass: String = "Knight",
    val outfitStyle: String = "Vanguard Crimson",
    val selectedCompanion: String = "NONE",
    val unsyncedLootCount: Int = 0
)

// Summary of conflicts identified upon reconnection
data class SyncConflictInfo(
    val hasConflict: Boolean,
    val localLevel: Int,
    val remoteLevel: Int,
    val localXp: Int,
    val remoteXp: Int,
    val localGold: Int,
    val remoteGold: Int,
    val localSkills: Set<String>,
    val remoteSkills: Set<String>,
    val unsyncedItemsCount: Int
)

class DungeonSyncService(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dungeon_cloud_mock", Context.MODE_PRIVATE)

    init {
        // Initialize with typical "previous play session" if empty, to generate an interesting cloud state
        if (!prefs.contains("cloud_level")) {
            setCloudProfile(
                CloudProfile(
                    level = 3,
                    xp = 180,
                    gold = 450,
                    skillPoints = 1,
                    unlockedSkills = "shield_bash",
                    characterClass = "Knight",
                    outfitStyle = "Vanguard Crimson",
                    selectedCompanion = "NONE"
                )
            )
        }
    }

    // Fetches the simulated remote cloud backup profile
    fun getCloudProfile(): CloudProfile {
        return CloudProfile(
            level = prefs.getInt("cloud_level", 1),
            xp = prefs.getInt("cloud_xp", 0),
            gold = prefs.getInt("cloud_gold", 100),
            skillPoints = prefs.getInt("cloud_skill_points", 0),
            unlockedSkills = prefs.getString("cloud_unlocked_skills", "") ?: "",
            characterClass = prefs.getString("cloud_char_class", "Knight") ?: "Knight",
            outfitStyle = prefs.getString("cloud_outfit_style", "Vanguard Crimson") ?: "Vanguard Crimson",
            selectedCompanion = prefs.getString("cloud_companion", "NONE") ?: "NONE"
        )
    }

    // Overwrites or updates the simulated remote cloud backup state
    fun setCloudProfile(profile: CloudProfile) {
        prefs.edit().apply {
            putInt("cloud_level", profile.level)
            putInt("cloud_xp", profile.xp)
            putInt("cloud_gold", profile.gold)
            putInt("cloud_skill_points", profile.skillPoints)
            putString("cloud_unlocked_skills", profile.unlockedSkills)
            putString("cloud_char_class", profile.characterClass)
            putString("cloud_outfit_style", profile.outfitStyle)
            putString("cloud_companion", profile.selectedCompanion)
            apply()
        }
    }

    // Identifies conflicts between local state and the remote cloud backup
    fun checkConflicts(local: PlayerStateEntity, unsyncedLocalItems: List<ItemEntity>): SyncConflictInfo {
        val cloud = getCloudProfile()
        
        val localSkills = local.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
        val remoteSkills = cloud.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()

        val levelDiff = local.level != cloud.level
        val goldDiff = local.gold != cloud.gold
        val skillDiff = localSkills != remoteSkills

        val hasConflict = levelDiff || goldDiff || skillDiff || unsyncedLocalItems.isNotEmpty()

        return SyncConflictInfo(
            hasConflict = hasConflict,
            localLevel = local.level,
            remoteLevel = cloud.level,
            localXp = local.xp,
            remoteXp = cloud.xp,
            localGold = local.gold,
            remoteGold = cloud.gold,
            localSkills = localSkills,
            remoteSkills = remoteSkills,
            unsyncedItemsCount = unsyncedLocalItems.size
        )
    }

    /**
     * Strategic Strategy #1: Additive Merge
     * Safely combines local offline progress with remote cloud state:
     * - Experience (XP): Combine XP earned offline with cloud XP. Let's calculate:
     *   Offline earned XP = max(0, Local XP - Cloud XP, or fallback to an estimate based on local unsynced count * 45)
     * - Gold: Add offline earned gold specifically (Local Gold - Cloud Gold) to Cloud Gold.
     * - Skills: Combine both sets of unlocked skills! Refund unspent skill points.
     * - Items: Accept all offline non-synced items (marks them as synced).
     */
    fun performAdditiveMerge(
        local: PlayerStateEntity,
        unsyncedLocalItems: List<ItemEntity>
    ): Pair<PlayerStateEntity, List<ItemEntity>> {
        val cloud = getCloudProfile()
        
        // Calculate offline progress gains
        val estimatedOfflineGold = if (local.gold > cloud.gold) local.gold - cloud.gold else local.unsyncedLootCount * 120
        val estimatedOfflineXp = if (local.xp > cloud.xp) local.xp - cloud.xp else local.unsyncedLootCount * 45

        // Combine Gold and XP
        val mergedGold = cloud.gold + estimatedOfflineGold
        val mergedXpSum = cloud.xp + estimatedOfflineXp

        // Recalculate level after adding XP. Level up occurs at each level * 100
        var currentLevel = cloud.level
        var currentXp = mergedXpSum
        var xpNeeded = currentLevel * 100
        while (currentXp >= xpNeeded) {
            currentXp -= xpNeeded
            currentLevel++
            xpNeeded = currentLevel * 100
        }

        // Merge Unlocked Skills
        val localSkills = local.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
        val remoteSkills = cloud.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
        val combinedSkillsSet = localSkills + remoteSkills
        val mergedUnlockedSkills = combinedSkillsSet.joinToString(",")

        // Combine skill points (give player any unspent points they earned, keeping balance)
        val baseSkillPoints = cloud.skillPoints + (currentLevel - cloud.level) + local.skillPoints
        val finalSkillPoints = (baseSkillPoints - (combinedSkillsSet.size - remoteSkills.size)).coerceAtLeast(0)

        val mergedPlayer = local.copy(
            level = currentLevel,
            xp = currentXp,
            gold = mergedGold,
            skillPoints = finalSkillPoints,
            unlockedSkills = mergedUnlockedSkills,
            unsyncedLootCount = 0,
            isOnlineMode = true
        )

        // Upload merged player to cloud simulated state
        setCloudProfile(
            CloudProfile(
                level = mergedPlayer.level,
                xp = mergedPlayer.xp,
                gold = mergedPlayer.gold,
                skillPoints = mergedPlayer.skillPoints,
                unlockedSkills = mergedPlayer.unlockedSkills,
                characterClass = mergedPlayer.characterClass,
                outfitStyle = mergedPlayer.outfitStyle,
                selectedCompanion = mergedPlayer.selectedCompanion
            )
        )

        // All local offline items are synced now
        val syncedItems = unsyncedLocalItems.map { it.copy(isSynced = true) }

        return Pair(mergedPlayer, syncedItems)
    }

    /**
     * Strategic Strategy #2: Force Local Progress (Overwrite Cloud)
     * Treats the offline local progress as the true state, uploading and completely overwriting cloud profile.
     */
    fun performForceLocal(
        local: PlayerStateEntity,
        unsyncedLocalItems: List<ItemEntity>
    ): Pair<PlayerStateEntity, List<ItemEntity>> {
        val updatedLocal = local.copy(
            unsyncedLootCount = 0,
            isOnlineMode = true
        )

        // Overwrite the cloud profile with local stats
        setCloudProfile(
            CloudProfile(
                level = updatedLocal.level,
                xp = updatedLocal.xp,
                gold = updatedLocal.gold,
                skillPoints = updatedLocal.skillPoints,
                unlockedSkills = updatedLocal.unlockedSkills,
                characterClass = updatedLocal.characterClass,
                outfitStyle = updatedLocal.outfitStyle,
                selectedCompanion = updatedLocal.selectedCompanion
            )
        )

        // Mark items as synced
        val syncedItems = unsyncedLocalItems.map { it.copy(isSynced = true) }

        return Pair(updatedLocal, syncedItems)
    }

    /**
     * Strategic Strategy #3: Force Cloud Backup (Overwrite Local)
     * Abandons offline local gains, completely pulling current cloud parameters and wiping any unsynced local content.
     */
    fun performForceCloud(
        local: PlayerStateEntity,
        unsyncedLocalItems: List<ItemEntity>
    ): Pair<PlayerStateEntity, List<ItemEntity>> {
        val cloud = getCloudProfile()

        val cloudPlayer = local.copy(
            level = cloud.level,
            xp = cloud.xp,
            gold = cloud.gold,
            skillPoints = cloud.skillPoints,
            unlockedSkills = cloud.unlockedSkills,
            characterClass = cloud.characterClass,
            outfitStyle = cloud.outfitStyle,
            selectedCompanion = cloud.selectedCompanion,
            unsyncedLootCount = 0,
            isOnlineMode = true
        )

        // Return the cloud player structure, and ask to delete/wipe the unsynced items
        // We will represent deleted local items as an empty list (indicating they should be deleted from DB)
        return Pair(cloudPlayer, emptyList())
    }
}
