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
        // Base collections
        val baseWeapons = listOf("Greatsword", "War-Staff", "Shadow Dagger", "Demon Cleaver", "Vortex Wand", "Excalibur", "Kunai", "Katana")
        val baseShields = listOf("Buckler", "Round Shield", "Kite Shield", "Tower Shield", "Aegis Barrier", "Chakra Mirror")
        val baseHelmets = listOf("Visor", "Leather Cap", "Iron Helm", "Chain Coif", "Crown of Sorcery", "Shinobi Cowl")
        val baseChests = listOf("Cloth Tunic", "Leather Armor", "Chainmail Plate", "Steel Breastplate", "Draconic Carapace", "Shinobi Cloak")

        val commonPrefixes = listOf("Rusty", "Dull", "Standard", "Polished", "Worn", "Simple", "Novice")
        val magicPrefixes = listOf("Glowing", "Enchanted", "Crystalline", "Arcane", "Pulsing", "Blessed", "Gleaming")
        val rarePrefixes = listOf("Vanquisher's", "Void-Touched", "Runic", "Gladiator's", "Elite", "Reinforced")
        val legendaryPrefixes = listOf("Mythic", "Aetherius", "Kagutsuchi's", "Demonic", "Godlike", "Cosmic", "Slayer's")

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

        // Determine Rarity weights (Common, Magic, Rare, Legendary)
        val rand = Random.nextDouble()
        val chanceFactor = floor + playerLevel
        val rarity = when {
            rand < 0.04 + (chanceFactor * 0.005) -> Rarity.LEGENDARY
            rand < 0.22 + (chanceFactor * 0.01) -> Rarity.RARE
            rand < 0.60 + (chanceFactor * 0.01) -> Rarity.MAGIC
            else -> Rarity.COMMON
        }

        val rarityMultiplier = when (rarity) {
            Rarity.COMMON -> 1.0f
            Rarity.MAGIC -> 1.4f
            Rarity.RARE -> 2.1f
            Rarity.LEGENDARY -> 4.5f
            else -> 1.0f
        }

        // Select prefix depending on rarity
        val prefixList = when (rarity) {
            Rarity.COMMON -> commonPrefixes
            Rarity.MAGIC -> magicPrefixes
            Rarity.RARE -> rarePrefixes
            Rarity.LEGENDARY -> legendaryPrefixes
            else -> commonPrefixes
        }
        val prefix = prefixList[Random.nextInt(prefixList.size)]

        // Pick base name
        val baseName = when (chosenType) {
            ItemType.WEAPON -> baseWeapons[Random.nextInt(baseWeapons.size)]
            ItemType.SHIELD -> baseShields[Random.nextInt(baseShields.size)]
            ItemType.HELMET -> baseHelmets[Random.nextInt(baseHelmets.size)]
            ItemType.CHEST -> baseChests[Random.nextInt(baseChests.size)]
        }

        // Suffix adds depending on tier
        val hasSuffix = (rarity == Rarity.RARE || rarity == Rarity.LEGENDARY || (rarity == Rarity.MAGIC && Random.nextFloat() <= 0.5f))
        val suffixPair = if (hasSuffix) suffixes[Random.nextInt(suffixes.size)] else null
        val suffix = suffixPair?.first ?: ""
        val itemDescription = suffixPair?.second ?: "A handy piece of equipment discovered deep in the dungeons."

        val finalName = if (suffix.isNotEmpty()) "$prefix $baseName $suffix" else "$prefix $baseName"

        // Scale Stats dynamically based on Level, Floor, and Rarity
        val baseScale = (playerLevel * 3 + floor * 4 + Random.nextInt(2, 7))

        var attack = 0
        var defense = 0
        var hpBonus = 0
        var critBonus = 0
        var speedVal = 1.0f
        var elemResist = 0
        var dodge = 0
        var uniqueItem = false

        // 6% chance for a Legendary item to also roll an ultra Ancient Unique variant!
        val uniqueRoll = Random.nextDouble() < 0.06
        if (uniqueRoll && rarity == Rarity.LEGENDARY) {
            uniqueItem = true
        }

        when (chosenType) {
            ItemType.WEAPON -> {
                attack = (baseScale * 1.5f * rarityMultiplier).toInt()
                if (rarity == Rarity.MAGIC) critBonus = Random.nextInt(3, 8)
                if (rarity == Rarity.RARE) critBonus = Random.nextInt(8, 15)
                if (rarity == Rarity.LEGENDARY) critBonus = Random.nextInt(15, 25)
                speedVal = 0.8f + Random.nextFloat() * 0.5f
            }
            ItemType.SHIELD -> {
                defense = (baseScale * 1.0f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 2f * rarityMultiplier).toInt()
                if (rarity >= Rarity.MAGIC) {
                    elemResist = Random.nextInt(2, 6) + floor
                    dodge = Random.nextInt(2, 6)
                }
                if (rarity >= Rarity.RARE) {
                    elemResist = Random.nextInt(6, 12) + (floor * 2)
                    dodge = Random.nextInt(6, 12)
                }
            }
            ItemType.HELMET -> {
                defense = (baseScale * 0.6f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 3.5f * rarityMultiplier).toInt()
                if (rarity >= Rarity.MAGIC) elemResist = Random.nextInt(2, 6) + floor
                if (rarity >= Rarity.RARE) elemResist = Random.nextInt(6, 12) + (floor * 2)
            }
            ItemType.CHEST -> {
                defense = (baseScale * 1.6f * rarityMultiplier).toInt()
                hpBonus = (baseScale * 5f * rarityMultiplier).toInt()
                if (rarity >= Rarity.MAGIC) {
                    elemResist = Random.nextInt(2, 6) + floor
                    dodge = Random.nextInt(1, 4)
                }
                if (rarity >= Rarity.RARE) {
                    elemResist = Random.nextInt(6, 15) + (floor * 2)
                    dodge = Random.nextInt(5, 10)
                }
            }
        }

        // Additional enhancements for Legendary items
        if (rarity == Rarity.LEGENDARY) {
            attack = (attack * 1.25f).toInt()
            defense = (defense * 1.25f).toInt()
            hpBonus = (hpBonus * 1.35f).toInt()
            critBonus += 10
            elemResist += 6
            dodge += 5
            if (chosenType == ItemType.WEAPON) speedVal = 0.7f + Random.nextFloat() * 0.35f
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
            descOverride = "⭐ ANCIENT UNIQUE GEAR ⭐ Imbued with pristine power, whispering forbidden secrets of the elements."
            attack = (attack * 1.5f).toInt()
            defense = (defense * 1.5f).toInt()
            hpBonus = (hpBonus * 1.5f).toInt()
            critBonus += 15
            elemResist += 10
            dodge += 8
        } else {
            // Provide flavorful tier indicator
            descOverride = when (rarity) {
                Rarity.LEGENDARY -> "✨ LEGENDARY ✨ $itemDescription"
                Rarity.RARE -> "🔷 RARE 🔷 $itemDescription"
                Rarity.MAGIC -> "🔮 MAGIC 🔮 $itemDescription"
                else -> "🔸 Common 🔸 $itemDescription"
            }
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
