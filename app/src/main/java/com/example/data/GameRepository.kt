package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.random.Random

class GameRepository(private val gameDao: GameDao) {

    val playerState: Flow<PlayerStateEntity?> = gameDao.getPlayerState()
    val allItems: Flow<List<ItemEntity>> = gameDao.getAllItems()
    val equippedItems: Flow<List<ItemEntity>> = gameDao.getEquippedItems()

    suspend fun getPlayerStateDirect(): PlayerStateEntity {
        val current = gameDao.getPlayerStateDirect()
        if (current == null) {
            val defaultState = PlayerStateEntity()
            gameDao.savePlayerState(defaultState)
            return defaultState
        }
        return current
    }

    suspend fun updatePlayerState(state: PlayerStateEntity) {
        gameDao.savePlayerState(state)
    }

    suspend fun getUnsyncedItems(): List<ItemEntity> {
        return gameDao.getUnsyncedItems()
    }

    suspend fun insertItem(item: ItemEntity): Long {
        return gameDao.insertItem(item)
    }

    suspend fun updateItem(item: ItemEntity) {
        gameDao.updateItem(item)
    }

    suspend fun deleteItem(item: ItemEntity) {
        gameDao.deleteItem(item)
    }

    suspend fun clearLoot() {
        gameDao.clearUnequippedLoot()
    }

    // Procedural Gear Generator
    fun generateProceduralGear(playerLevel: Int, floor: Int): ItemEntity {
        val prefixes = listOf("Rusty", "Dull", "Standard", "Polished", "Steel", "Reinforced", "Gleaming", "Vanguard", "Runic", "Void", "Eldritch", "Mythic")
        val baseWeapons = listOf("Greatsword", "War-Staff", "Shadow Dagger", "Demon Cleaver", "Vortex Wand", "Excalibur")
        val baseShields = listOf("Buckler", "Round Shield", "Kite Shield", "Tower Shield", "Aegis Barrier")
        val baseHelmets = listOf("Visor", "Leather Cap", "Iron Helm", "Chain Coif", "Crown of Sorcery")
        val baseChests = listOf("Cloth Tunic", "Leather Armor", "Chainmail Plate", "Steel Breastplate", "Draconic Carapace")

        val suffixes = listOf(
            "of Flame" to "Infused with dancing cinders.",
            "of Frost" to "Chill aura emanates from within.",
            "of Shadows" to "Cloaked in deep whispers.",
            "of Resilience" to "Fortified by ancestral energy.",
            "of the Behemoth" to "Imbued with core titanic power.",
            "of Doom" to "Cursed but immensely lethal.",
            "of Judgement" to "Blessed by high guardians.",
            "of the Phoenix" to "Always radiating warm life-force."
        )

        // Determine item type
        val itemTypes = ItemType.values()
        val chosenType = itemTypes[Random.nextInt(itemTypes.size)]

        // Determine Rarity weights
        val rand = Random.nextDouble()
        // stage scales rarity chances slightly
        val rarityChanceCount = floor + playerLevel
        val rarity = when {
            rand < 0.03 + (rarityChanceCount * 0.005) -> Rarity.LEGENDARY
            rand < 0.12 + (rarityChanceCount * 0.01) -> Rarity.EPIC
            rand < 0.32 + (rarityChanceCount * 0.01) -> Rarity.RARE
            rand < 0.65 -> Rarity.UNCOMMON
            else -> Rarity.COMMON
        }

        val rarityMultiplier = when (rarity) {
            Rarity.COMMON -> 1.0f
            Rarity.UNCOMMON -> 1.3f
            Rarity.RARE -> 1.8f
            Rarity.EPIC -> 2.5f
            Rarity.LEGENDARY -> 4.2f
        }

        // Pick prefix based on player level/floor
        val maxPrefixIdx = (playerLevel / 2 + floor / 2 + 1).coerceAtMost(prefixes.size - 1)
        val prefixIdx = Random.nextInt(0, maxPrefixIdx + 1)
        val prefix = prefixes[prefixIdx]

        // Pick base name and item slot fields
        val baseName = when (chosenType) {
            ItemType.WEAPON -> baseWeapons[Random.nextInt(baseWeapons.size)]
            ItemType.SHIELD -> baseShields[Random.nextInt(baseShields.size)]
            ItemType.HELMET -> baseHelmets[Random.nextInt(baseHelmets.size)]
            ItemType.CHEST -> baseChests[Random.nextInt(baseChests.size)]
        }

        // Suffix add chance
        val hasSuffix = (rarity == Rarity.RARE || rarity == Rarity.EPIC || rarity == Rarity.LEGENDARY)
        val suffixPair = if (hasSuffix) suffixes[Random.nextInt(suffixes.size)] else null
        val suffix = suffixPair?.first ?: ""
        val itemDescription = suffixPair?.second ?: "A standard dungeon equipment explorer piece."

        val finalName = if (suffix.isNotEmpty()) "$prefix $baseName $suffix" else "$prefix $baseName"

        // Scale Stats dynamically based on Level and Floor and Rarity
        val baseScale = (playerLevel * 3 + floor * 4 + Random.nextInt(1, 6))

        var attack = 0
        var defense = 0
        var hpBonus = 0
        var critBonus = 0
        var speedVal = 1.0f
        var elemResist = 0
        var dodge = 0
        var uniqueItem = false

        // 3% standard chance to roll an ultra unique item, or 100% if we explicitly command it or roll very lucky
        val uniqueRoll = Random.nextDouble() < 0.05
        if (uniqueRoll && rarity >= Rarity.EPIC) {
            uniqueItem = true
        }

        when (chosenType) {
            ItemType.WEAPON -> {
                attack = (baseScale * 1.5f * rarityMultiplier).toInt()
                if (rarity >= Rarity.RARE) critBonus = Random.nextInt(5, 16)
                // Random attacks speed: 0.8f is lightsabre, 1.4f is heavy warhammer
                speedVal = 0.8f + Random.nextFloat() * 0.6f
            }
            ItemType.SHIELD -> {
                defense = (baseScale * 1.0f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 2f * rarityMultiplier).toInt()
                elemResist = Random.nextInt(2, 10) + (floor * 2)
                dodge = Random.nextInt(3, 12)
            }
            ItemType.HELMET -> {
                defense = (baseScale * 0.6f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 3.5f * rarityMultiplier).toInt()
                elemResist = Random.nextInt(1, 6) + floor
            }
            ItemType.CHEST -> {
                defense = (baseScale * 1.6f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 5f * rarityMultiplier).toInt()
                elemResist = Random.nextInt(3, 12) + floor
                dodge = Random.nextInt(1, 8)
            }
        }

        // Additional stats for Legendary items
        if (rarity == Rarity.LEGENDARY) {
            attack = (attack * 1.2f).toInt()
            defense = (defense * 1.2f).toInt()
            hpBonus = (hpBonus * 1.3f).toInt()
            critBonus += 8
            if (chosenType == ItemType.WEAPON) speedVal = 0.7f + Random.nextFloat() * 0.4f
        }

        var nameOverride = finalName
        var descOverride = itemDescription

        if (uniqueItem) {
            nameOverride = when (chosenType) {
                ItemType.WEAPON -> "Aetherius Void Sunderer"
                ItemType.SHIELD -> "Aegis of the Soul Overlord"
                ItemType.HELMET -> "Visage of Balrog's Crown"
                ItemType.CHEST -> "Draconic Void Heartmesh"
            }
            descOverride = "⭐ ANCIENT UNIQUE GEAR ⭐ Imbued with pristine power, whispering forbidden secrets."
            attack = (attack * 1.5f).toInt()
            defense = (defense * 1.5f).toInt()
            hpBonus = (hpBonus * 1.5f).toInt()
            critBonus += 15
            elemResist += 10
            dodge += 8
        }

        return ItemEntity(
            name = nameOverride,
            type = chosenType.name,
            rarity = if (uniqueItem) Rarity.LEGENDARY.name else rarity.name,
            bonusAttack = attack,
            bonusDefense = defense,
            bonusHp = hpBonus,
            bonusCrit = critBonus,
            description = descOverride,
            isEquipped = false,
            purchaseGoldValue = (15 * rarityMultiplier * (playerLevel + floor)).toInt() * (if (uniqueItem) 2 else 1),
            attackSpeed = speedVal,
            elementalResist = elemResist,
            dodgeChance = dodge,
            isUnique = uniqueItem
        )
    }

    // Generate an absolutely guaranteed boss drop item
    fun generateGuaranteedBossUnique(bossName: String, playerLevel: Int, floor: Int): ItemEntity {
        val finalName = when {
            bossName.contains("Necromancer") -> "Necromancer's Souldrainer Scythe"
            bossName.contains("Balrog") -> "Kazar's Crimson Fire-Cleaver"
            else -> "Aetherius Zenith Void Blade"
        }
        val bonusAtk = 30 + playerLevel * 6 + floor * 5
        val bonusCrit = 15 + floor * 2
        val speedVal = 0.75f // extremely snappy double attack

        return ItemEntity(
            name = finalName,
            type = ItemType.WEAPON.name,
            rarity = Rarity.LEGENDARY.name,
            bonusAttack = bonusAtk,
            bonusDefense = 0,
            bonusHp = 45,
            bonusCrit = bonusCrit,
            description = "🔥 GUARANTEED EPIC UNIQUE DROP 🔥 Carved from the slain remains of $bossName.",
            isEquipped = false,
            purchaseGoldValue = 1000,
            attackSpeed = speedVal,
            elementalResist = 15,
            dodgeChance = 5,
            isUnique = true
        )
    }

    // Initialize pre-equipped clean gear for brand new characters
    suspend fun provisionStarterItems() {
        val existingItems = gameDao.getAllItems().firstOrNull() ?: emptyList()
        if (existingItems.isEmpty()) {
            val starterWeapon = ItemEntity(
                name = "Novice Shortsword",
                type = ItemType.WEAPON.name,
                rarity = Rarity.COMMON.name,
                bonusAttack = 8,
                bonusDefense = 0,
                bonusHp = 0,
                bonusCrit = 2,
                description = "Standard bronze shortsword issued to novice trainees.",
                isEquipped = true,
                attackSpeed = 1.0f,
                elementalResist = 0,
                dodgeChance = 0,
                isUnique = false
            )
            val starterChest = ItemEntity(
                name = "Explorer's Leather Garb",
                type = ItemType.CHEST.name,
                rarity = Rarity.COMMON.name,
                bonusAttack = 0,
                bonusDefense = 3,
                bonusHp = 20,
                bonusCrit = 0,
                description = "Worn leather tunic suited for solo dungeon exploration.",
                isEquipped = true,
                attackSpeed = 1.0f,
                elementalResist = 2,
                dodgeChance = 1,
                isUnique = false
            )
            gameDao.insertItem(starterWeapon)
            gameDao.insertItem(starterChest)
        }
    }
}
