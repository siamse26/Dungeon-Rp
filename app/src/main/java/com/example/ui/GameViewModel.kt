package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

// Sealed UI State representing different major visual screens
sealed interface GameUiState {
    object Loading : GameUiState
    data class MainMenu(
        val player: PlayerStateEntity,
        val equippedItems: List<ItemEntity>,
        val setBonuses: List<String>
    ) : GameUiState
}

// Representation of an active combat encounter
data class BattleState(
    val enemy: Enemy,
    val playerCurrentHp: Int,
    val playerMaxHp: Int,
    val enemyCurrentHp: Int,
    val enemyMaxHp: Int,
    val companionActionText: String = "",
    val playerSkillCooldowns: Map<String, Int> = emptyMap(), // skillName to ticks remaining
    val battleLogs: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val isVictory: Boolean = false,
    val lootedItem: ItemEntity? = null,
    val shieldActive: Boolean = false,
    val dodgeActive: Boolean = false,
    val sprintActive: Boolean = false,
    val currentComboCount: Int = 0,
    val chargeStrikePercent: Int = 0,
    val activeWeaponSlot: Int = 0, // 0 = Swift Daggers, 1 = Heavy Greatsword
    val bossActivePhase: Int = 1
)

// Skills represented in the customizable skill tree
data class SkillNode(
    val name: String,
    val id: String,
    val description: String,
    val isUnlocked: Boolean,
    val cost: Int = 1,
    val isActive: Boolean = false,
    val parentId: String? = null
)

class GameViewModel(
    application: Application,
    private val repository: GameRepository
) : AndroidViewModel(application) {

    val syncService = com.example.data.sync.DungeonSyncService(application.applicationContext)
    
    private val _syncConflictState = MutableStateFlow<com.example.data.sync.SyncConflictInfo?>(null)
    val syncConflictState: StateFlow<com.example.data.sync.SyncConflictInfo?> = _syncConflictState.asStateFlow()

    fun closeSyncOverlay() {
        _syncConflictState.value = null
    }

    // Active Screen selection
    private val _currentScreen = MutableStateFlow("home") // "home", "dungeon", "skills", "inventory", "companions", "maps"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _travelCodexOpen = MutableStateFlow(false)
    val travelCodexOpen: StateFlow<Boolean> = _travelCodexOpen.asStateFlow()

    fun openTravelCodex() {
        _travelCodexOpen.value = true
    }

    fun closeTravelCodex() {
        _travelCodexOpen.value = false
    }

    // Navigation flow back stack tracker
    fun navigateTo(screen: String) {
        _currentScreen.value = screen
        if (screen == "home") {
            // Cancel active dungeon loop if necessary, or preserve state
        }
    }

    // Player database state stream
    val playerState: StateFlow<PlayerStateEntity?> = repository.playerState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Items list stream
    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val equippedItems: StateFlow<List<ItemEntity>> = repository.equippedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calculated derived stats from base + items + set bonuses
    val totalStats = combine(playerState, equippedItems) { player, items ->
        if (player == null) return@combine CalculatedStats()
        
        var bonusAtk = 0
        var bonusDef = 0
        var bonusHp = 0
        var bonusCrit = 0
        var bonusSpeed = 0f
        var bonusResist = 0
        var bonusDodge = 0
        var hasUnique = false

        for (item in items) {
            bonusAtk += item.bonusAttack
            bonusDef += item.bonusDefense
            bonusHp += item.bonusHp
            bonusCrit += item.bonusCrit
            bonusResist += item.elementalResist
            bonusDodge += item.dodgeChance
            if (item.isUnique) hasUnique = true
            if (item.type == ItemType.WEAPON.name) {
                bonusSpeed = item.attackSpeed
            }
        }

        // Apply Character Class special passive parameters
        var classBaseAtk = player.baseAttack
        var classBaseDef = player.baseDefense
        var classBaseCrit = 5
        var classBaseDodge = 0
        var classBaseResist = 0

        when (player.characterClass) {
            "Knight" -> {
                classBaseDef += 5
                classBaseDodge += 3
            }
            "Mage" -> {
                classBaseAtk += 7
            }
            "Rogue" -> {
                classBaseCrit += 15
                if (bonusSpeed == 0f || bonusSpeed > 0.9f) bonusSpeed = 0.82f
            }
        }

        // Evaluate Sets
        val setBons = mutableListOf<String>()
        val itemNames = items.map { it.name.lowercase() }
        
        // 1. Paladin's Bastion Set: Helmet/Chest with "Vanguard" or "Kite" or "Aegis"
        val hasDefSet = items.count { it.type == ItemType.SHIELD.name || it.type == ItemType.HELMET.name } >= 2
        if (hasDefSet) {
            bonusHp += 30
            bonusDef += 5
            setBons.add("Shield Wall Set (Restores +5 HP per battle tick)")
        }

        // 2. Slayer of Doom Set: Weapons with "Eldritch", "Slashing", "Void" or high rarities
        val weaponCount = items.count { it.type == ItemType.WEAPON.name && (it.rarity == Rarity.EPIC.name || it.rarity == Rarity.LEGENDARY.name) }
        if (weaponCount >= 1) {
            bonusAtk += 10
            bonusCrit += 12
            setBons.add("Hero's Call Set (Increases Critical chance and +10 Attack)")
        }

        // 3. Apply Unlocked Passive Skill Bonuses from Skill Tree
        val skillSet = player.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
        if (skillSet.contains("iron_will")) {
            bonusDef += 10
            setBons.add("Iron Will Passive (+10 Def)")
        }
        if (skillSet.contains("arcane_barrier")) {
            bonusResist += 15
            setBons.add("Arcane Barrier Passive (+15 Elemental Resist)")
        }
        if (skillSet.contains("shadow_step")) {
            bonusDodge += 12
            setBons.add("Shadow Step Passive (+12% Dodge Chance)")
        }

        CalculatedStats(
            attack = classBaseAtk + bonusAtk,
            defense = classBaseDef + bonusDef,
            maxHp = player.maxHp + bonusHp,
            critChance = classBaseCrit + bonusCrit,
            setBonuses = setBons,
            attackSpeed = if (bonusSpeed == 0f) 1.0f else bonusSpeed,
            elementalResist = classBaseResist + bonusResist,
            dodgeChance = classBaseDodge + bonusDodge,
            containsUnique = hasUnique
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatedStats())

    // Active dungeon level state
    private val _activeDungeon = MutableStateFlow<DungeonLevel?>(null)
    val activeDungeon: StateFlow<DungeonLevel?> = _activeDungeon.asStateFlow()

    // Screen log tracking dungeon events
    private val _dungeonLogs = MutableStateFlow<List<String>>(listOf("Welcome to the dungeon floor. Use controls to explore."))
    val dungeonLogs: StateFlow<List<String>> = _dungeonLogs.asStateFlow()

    // Active combat state
    private val _activeBattle = MutableStateFlow<BattleState?>(null)
    val activeBattle: StateFlow<BattleState?> = _activeBattle.asStateFlow()

    // Skill nodes definitions
    val availableSkills = listAvailableSkills()

    // Current chest award loop variable
    private val _chestDrop = MutableStateFlow<ItemEntity?>(null)
    val chestDrop: StateFlow<ItemEntity?> = _chestDrop.asStateFlow()

    init {
        viewModelScope.launch {
            repository.provisionStarterItems()
            // Make sure player state is initialized
            repository.getPlayerStateDirect()
        }
    }

    // Reset chest award overlay
    fun closeChestDrop() {
        _chestDrop.value = null
    }

    // Accept and keep chest item
    fun claimChestItem(item: ItemEntity) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val finalItem = if (!player.isOnlineMode) item.copy(isSynced = false) else item
            repository.insertItem(finalItem)
            _chestDrop.value = null
            addDungeonLog("You added ${finalItem.name} to inventory!")
        }
    }

    // Sell chest item on the spot
    fun sellChestItem(item: ItemEntity) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val sellValue = (item.purchaseGoldValue * 0.4).toInt().coerceAtLeast(10)
            repository.updatePlayerState(player.copy(gold = player.gold + sellValue))
            _chestDrop.value = null
            addDungeonLog("Discarded and sold ${item.name} for $sellValue Gold.")
        }
    }

    // Start explore maps
    fun selectMapAndLaunch(stageId: Int) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val wpKey = "${stageId}_1"
            val wpSet = player.discoveredWaypoints.split(",").filter { it.isNotEmpty() }.toSet()
            val nextWps = if (!wpSet.contains(wpKey)) (wpSet + wpKey).joinToString(",") else player.discoveredWaypoints

            repository.updatePlayerState(player.copy(currentStageId = stageId, currentFloor = 1, discoveredWaypoints = nextWps))
            val generator = DungeonGenerator()
            val level = generator.generateDungeon(stageId, 1)
            _activeDungeon.value = level
            _dungeonLogs.value = listOf("Selected Map Stage $stageId: Floor 1 procedural generation complete.")
            _currentScreen.value = "dungeon"
        }
    }

    // Continue current floor
    fun enterDungeonExploration() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val wpKey = "${player.currentStageId}_${player.currentFloor}"
            val wpSet = player.discoveredWaypoints.split(",").filter { it.isNotEmpty() }.toSet()
            val nextWps = if (!wpSet.contains(wpKey)) (wpSet + wpKey).joinToString(",") else player.discoveredWaypoints

            repository.updatePlayerState(player.copy(discoveredWaypoints = nextWps))
            val generator = DungeonGenerator()
            val level = generator.generateDungeon(player.currentStageId, player.currentFloor)
            _activeDungeon.value = level
            _dungeonLogs.value = listOf("Dungeon layout generated. Floor ${player.currentFloor}. Map ready for solo exploration!")
            _currentScreen.value = "dungeon"
        }
    }

    private fun addDungeonLog(log: String) {
        val current = _dungeonLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 22) current.removeAt(current.size - 1)
        _dungeonLogs.value = current
    }

    // Active movement logic
    fun movePlayer(rowDelta: Int, colDelta: Int) {
        val dungeon = _activeDungeon.value ?: return
        val player = playerState.value ?: return
        val newRow = dungeon.playerRow + rowDelta
        val newCol = dungeon.playerCol + colDelta

        // Bound check
        if (newRow !in 0 until dungeon.height || newCol !in 0 until dungeon.width) return

        val tile = dungeon.grid[newRow][newCol]
        if (tile.type == TileType.WALL) {
            addDungeonLog("A heavy stonewall blocks your path.")
            return
        }

        // Apply shift
        dungeon.playerRow = newRow
        dungeon.playerCol = newCol

        // Reveal map area (Fog of war)
        val generator = DungeonGenerator()
        generator.revealArea(dungeon.grid, newRow, newCol, dungeon.width, dungeon.height)

        // Force recompose trigger
        _activeDungeon.value = dungeon.copy(playerRow = newRow, playerCol = newCol)

        // Evaluate Tile trigger
        triggerTileAction(tile, newRow, newCol)
    }

    private fun triggerTileAction(tile: DungeonTile, r: Int, c: Int) {
        val dungeon = _activeDungeon.value ?: return
        when (tile.type) {
            TileType.CHEST -> {
                tile.type = TileType.FLOOR
                val bonusGold = Random.nextInt(20, 55)
                viewModelScope.launch {
                    val p = repository.getPlayerStateDirect()
                    val loot = repository.generateProceduralGear(p.level, dungeon.floor)
                    repository.updatePlayerState(p.copy(gold = p.gold + bonusGold))
                    addDungeonLog("Opened Chest: Found $bonusGold gold!")
                    
                    // Trigger popup
                    _chestDrop.value = loot
                }
            }
            TileType.TRAP -> {
                tile.type = TileType.FLOOR
                val dmg = Random.nextInt(10, 25)
                viewModelScope.launch {
                    val p = repository.getPlayerStateDirect()
                    val nextHp = (p.currentHp - dmg).coerceAtLeast(1)
                    repository.updatePlayerState(p.copy(currentHp = nextHp))
                    addDungeonLog("TRAP TRIGGERED! Spikes dealt $dmg piercing damage!")
                }
            }
            TileType.ENEMY, TileType.BOSS -> {
                // Discover matching enemy
                val enemyObj = dungeon.enemies.find { it.row == r && it.col == c && it.hp > 0 }
                if (enemyObj != null) {
                    launchCombat(enemyObj)
                } else {
                    // fall back
                    tile.type = TileType.FLOOR
                }
            }
            TileType.STAIRS_DOWN -> {
                addDungeonLog("You reached the staircase down. Ready to descend!")
            }
            TileType.WAYPOINT -> {
                viewModelScope.launch {
                    val p = repository.getPlayerStateDirect()
                    val wpString = p.discoveredWaypoints
                    val currentWpKey = "${p.currentStageId}_${p.currentFloor}"
                    val currentWpSet = wpString.split(",").filter { it.isNotEmpty() }.toSet()
                    
                    val stats = totalStats.value
                    
                    if (!currentWpSet.contains(currentWpKey)) {
                        val nextWpSet = currentWpSet + currentWpKey
                        val updatedWpString = nextWpSet.joinToString(",")
                        repository.updatePlayerState(p.copy(discoveredWaypoints = updatedWpString, currentHp = stats.maxHp))
                        addDungeonLog("🌌 WAYPOINT SYNCHRONIZED: Restored full HP and locked in Floor ${p.currentFloor}!")
                    } else {
                        repository.updatePlayerState(p.copy(currentHp = stats.maxHp))
                        addDungeonLog("🌌 Safe Beacon active. Restored full HP on this safe zone.")
                    }
                }
            }
            else -> {}
        }
    }

    // Fast Travel World Waypoint
    fun fastTravelToWorldWaypoint(stageId: Int, floorNum: Int) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            
            // Mark new floor and stage in player state
            val updated = player.copy(currentStageId = stageId, currentFloor = floorNum)
            repository.updatePlayerState(updated)
            
            // Generate the dungeon map for this stage and floor
            val generator = DungeonGenerator()
            val level = generator.generateDungeon(stageId, floorNum)
            _activeDungeon.value = level
            _dungeonLogs.value = listOf("🌌 FAST TRAVELED: Teleported safely to Stage $stageId, Floor $floorNum!")
            _currentScreen.value = "dungeon"
            _travelCodexOpen.value = false
        }
    }

    // Fast Travel Local Coordinates
    fun teleportToLocalWaypoint(row: Int, col: Int, name: String) {
        val dungeon = _activeDungeon.value ?: return
        
        // Update player coordinates
        dungeon.playerRow = row
        dungeon.playerCol = col
        
        // Reveal area around new spot
        val generator = DungeonGenerator()
        generator.revealArea(dungeon.grid, row, col, dungeon.width, dungeon.height)
        
        _activeDungeon.value = dungeon.copy(playerRow = row, playerCol = col)
        addDungeonLog("🌌 TELEPORTED: Traveled to $name.")
        _travelCodexOpen.value = false
    }

    // Move to next floor or complete stage
    fun descendFloor() {
        val dungeon = _activeDungeon.value ?: return
        viewModelScope.launch {
            val p = repository.getPlayerStateDirect()
            if (p.currentFloor >= 3) {
                // Completed Stage!
                val completedGoldBonus = p.currentStageId * 150
                val playerXpBonus = p.currentStageId * 200
                val nextStage = if (p.currentStageId < 3) p.currentStageId + 1 else p.currentStageId
                
                // Roll guaranteed Epic/Legendary Item
                val bonusLegendary = repository.generateProceduralGear(p.level + 2, 4)
                val finalLegendary = if (!p.isOnlineMode) bonusLegendary.copy(isSynced = false) else bonusLegendary
                repository.insertItem(finalLegendary)

                val updated = p.copy(
                    gold = p.gold + completedGoldBonus,
                    xp = p.xp + playerXpBonus,
                    currentFloor = 1,
                    currentStageId = nextStage
                )
                repository.updatePlayerState(updated)
                checkLevelUp(updated)

                _activeDungeon.value = null
                _currentScreen.value = "home"
                
                // Mock a drop screen
                _chestDrop.value = bonusLegendary
                addDungeonLog("EPIC VICTORY! Completed State ${p.currentStageId}. Awarded $completedGoldBonus Gold, $playerXpBonus XP, and local legend: ${bonusLegendary.name}!")
            } else {
                val nextF = p.currentFloor + 1
                val wpKey = "${p.currentStageId}_$nextF"
                val wpSet = p.discoveredWaypoints.split(",").filter { it.isNotEmpty() }.toSet()
                val nextWps = if (!wpSet.contains(wpKey)) (wpSet + wpKey).joinToString(",") else p.discoveredWaypoints

                repository.updatePlayerState(p.copy(currentFloor = nextF, discoveredWaypoints = nextWps))
                val generator = DungeonGenerator()
                val nextLevel = generator.generateDungeon(p.currentStageId, nextF)
                _activeDungeon.value = nextLevel
                _dungeonLogs.value = listOf("Descended into Deep Chambers. Floor $nextF starts.")
            }
        }
    }

    // Combat Engine
    private var combatJob: Job? = null

    private fun launchCombat(enemy: Enemy) {
        val stats = totalStats.value
        val player = playerState.value ?: return

        _activeBattle.value = BattleState(
            enemy = enemy,
            playerCurrentHp = player.currentHp,
            playerMaxHp = stats.maxHp,
            enemyCurrentHp = enemy.hp,
            enemyMaxHp = enemy.maxHp,
            battleLogs = listOf("An angry ${enemy.name} roars into real-time combat battle!"),
            shieldActive = false,
            dodgeActive = false,
            sprintActive = false,
            currentComboCount = 0,
            chargeStrikePercent = 0,
            activeWeaponSlot = 1, // Start with normal broadsword
            bossActivePhase = 1
        )

        // Begin the real-time Active Combat Tick Loop!
        combatJob?.cancel()
        combatJob = viewModelScope.launch {
            var tick = 0
            while (_activeBattle.value?.isFinished == false) {
                delay(1200) // combat ticks every 1.2s
                tick++
                runActiveCombatTick(tick)
            }
        }
    }

    private suspend fun runActiveCombatTick(tickNum: Int) {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return

        val mutLogs = battle.battleLogs.toMutableList()
        var nextEnemyHp = battle.enemyCurrentHp
        var nextPlayerHp = battle.playerCurrentHp
        var bossPhase = battle.bossActivePhase
        val stats = totalStats.value
        val player = playerState.value ?: return

        // --- 1. SPECIAL BOSS ENCOUNTER PHASES ---
        val isBoss = battle.enemy.isBoss
        if (isBoss) {
            val currentHpPercent = (nextEnemyHp.toFloat() / battle.enemyMaxHp.toFloat())
            if (currentHpPercent <= 0.50f && bossPhase == 1) {
                bossPhase = 2
                mutLogs.add(0, "⚠️ BOSS PHASE TRANSITION! ${battle.enemy.name} triggers Phase 2 rage state!")
                when {
                    battle.enemy.name.contains("Necromancer") -> {
                        mutLogs.add(0, "🔮 LORD NECROMANCER raises a Bone Shield absorbing 50% physical hit points!")
                    }
                    battle.enemy.name.contains("Balrog") -> {
                        mutLogs.add(0, "🔥 KAZAR unleashes METEOR RAIN! Prepare to DEFEND on warnings!")
                    }
                    battle.enemy.name.contains("Sovereign") -> {
                        mutLogs.add(0, "🌌 AETHERIUS enters VOID HORIZON. 50% dodge rate activated!")
                    }
                }
            }
        }

        // --- 2. UNIQUE BOSS COMBAT MECHANICS ---
        if (isBoss && nextEnemyHp > 0) {
            when {
                battle.enemy.name.contains("Necromancer") -> {
                    // Necrotic decay drains health every single tick
                    val drain = 4 + player.level
                    nextPlayerHp = (nextPlayerHp - drain).coerceAtLeast(1)
                    mutLogs.add(0, "💀 Necrotic Decay drains -$drain HP from your soul.")
                }
                battle.enemy.name.contains("Balrog") -> {
                    if (bossPhase == 2 && tickNum % 4 == 0) {
                        // Meteor strike: must be shielded or dodged!
                        if (battle.dodgeActive) {
                            mutLogs.add(0, "🏃 Evaded Kazar's Meteor Rain with a clean tumble!")
                        } else if (battle.shieldActive) {
                            val mitDmg = 12
                            nextPlayerHp = (nextPlayerHp - mitDmg).coerceAtLeast(0)
                            mutLogs.add(0, "🛡️ Blocked Kazar's Meteor! Absorption reduced impact to $mitDmg fire damage.")
                        } else {
                            val heavyDmg = 38 + (player.level * 2)
                            nextPlayerHp = (nextPlayerHp - heavyDmg).coerceAtLeast(0)
                            mutLogs.add(0, "💥 METEOR CRASHED! Kazar deals $heavyDmg catastrophic fire damage to you!")
                        }
                    }
                }
                battle.enemy.name.contains("Sovereign") -> {
                    if (bossPhase == 2 && tickNum % 3 == 0) {
                        val voidSiphon = (stats.maxHp * 0.12).toInt()
                        nextPlayerHp = (nextPlayerHp - voidSiphon).coerceAtLeast(1)
                        nextEnemyHp = (nextEnemyHp + voidSiphon).coerceAtMost(battle.enemyMaxHp)
                        mutLogs.add(0, "🌌 Void Singularity siphons $voidSiphon HP from you to regenerate Aetherius!")
                    }
                }
            }
        }

        // --- 3. COOPERATIVE COMPANION COMBAT TICK ---
        val companion = CompanionType.valueOf(player.selectedCompanion)
        var companionText = ""
        if (companion != CompanionType.NONE) {
            when (companion) {
                CompanionType.CLERIC -> {
                    if (tickNum % 3 == 0) {
                        val healVal = 10 + player.level * 2
                        nextPlayerHp = (nextPlayerHp + healVal).coerceAtMost(stats.maxHp)
                        companionText = "Cleric Companion cast Divine Prayer (+ $healVal HP)!"
                        mutLogs.add(0, "Cleric Companion healed you for $healVal HP!")
                    }
                }
                CompanionType.MAGE -> {
                    if (tickNum % 3 == 0) {
                        val fireballDmg = 18 + player.level * 4
                        // Boss Necromancer shield weakness
                        val actualFireball = if (battle.enemy.name.contains("Necromancer") && bossPhase == 2) {
                            mutLogs.add(0, "🔥 MAGE FIRES WEAKNESS! Mage Firebolt breaks Necromancer bone barrier!")
                            fireballDmg * 2
                        } else fireballDmg

                        nextEnemyHp = (nextEnemyHp - actualFireball).coerceAtLeast(0)
                        companionText = "Mage Companion shot Fireball (- $actualFireball HP)!"
                        mutLogs.add(0, "Mage Companion cast Fireball at ${battle.enemy.name} for $actualFireball damage.")
                    }
                }
                CompanionType.ROGUE -> {
                    if (tickNum % 2 == 0) {
                        val rogueDmg = 14 + player.level * 2
                        nextEnemyHp = (nextEnemyHp - rogueDmg).coerceAtLeast(0)
                        companionText = "Rogue Companion stabbed target (- $rogueDmg HP)!"
                        mutLogs.add(0, "Rogue Companion stealth-slashed enemy for $rogueDmg strike.")
                    }
                }
                CompanionType.NONE -> {}
            }
        }

        // Apply continuous active regen set bonuses if active
        if (stats.setBonuses.any { it.contains("Shield Wall") }) {
            nextPlayerHp = (nextPlayerHp + 5).coerceAtMost(stats.maxHp)
        }

        // --- 4. PLAYER AUTOMATIC ATTACK TICK (incorporates weapons & class) ---
        val daggerActive = battle.activeWeaponSlot == 0
        val isAttackTick = if (daggerActive) true else (tickNum % 2 == 0) // Daggers attack every tick

        if (isAttackTick && nextEnemyHp > 0) {
            // Evaluates Sovereign Phase 1 Vapor dodge chance
            val bossDodged = isBoss && battle.enemy.name.contains("Sovereign") && bossPhase == 1 && Random.nextDouble() < 0.50

            if (bossDodged) {
                mutLogs.add(0, "🌌 MISS! Aetherius's Vapor Form dodged your attack entirely!")
            } else {
                val isCrit = Random.nextInt(100) < stats.critChance
                var baseWeaponDmg = if (daggerActive) (stats.attack * 0.55).toInt() else (stats.attack * 1.4).toInt()
                
                // Balrog phase 2 weakness: defense goes to 0
                val targetDef = if (isBoss && battle.enemy.name.contains("Balrog") && bossPhase == 2) 0 else battle.enemy.defense
                var finalDmg = baseWeaponDmg - (targetDef / 2)
                
                // Necromancer bone barrier block
                if (isBoss && battle.enemy.name.contains("Necromancer") && bossPhase == 2) {
                    finalDmg = (finalDmg * 0.5f).toInt()
                }
                
                finalDmg = finalDmg.coerceAtLeast(5)
                if (isCrit) {
                    finalDmg = (finalDmg * 1.8f).toInt()
                    mutLogs.add(0, "🔥 CRITICAL CRUSH! Slashed ${battle.enemy.name} for $finalDmg damage!")
                } else {
                    mutLogs.add(0, "You hit ${battle.enemy.name} for $finalDmg damage.")
                }
                nextEnemyHp = (nextEnemyHp - finalDmg).coerceAtLeast(0)
            }
        }

        // --- 5. ENEMY STANDARD ATTACK TICK ---
        val isEnemyTick = tickNum % 2 == 1
        if (isEnemyTick && nextEnemyHp > 0) {
            // Check if player active dodge / block was readied via buttons
            if (battle.dodgeActive) {
                mutLogs.add(0, "💨 DODGED! You cleanly tumbles rolled away from enemy strike!")
            } else {
                var rawDmg = battle.enemy.attack
                if (isBoss && battle.enemy.name.contains("Balrog") && bossPhase == 2) {
                    rawDmg = (rawDmg * 1.8).toInt() // boss phase 2 rage
                }
                var finalEnemyDmg = rawDmg - (stats.defense / 2)
                finalEnemyDmg = finalEnemyDmg.coerceAtLeast(4)

                // Apply active shield blocks
                if (battle.shieldActive) {
                    finalEnemyDmg = (finalEnemyDmg * 0.25f).toInt().coerceAtLeast(1)
                    mutLogs.add(0, "🛡️ GUARD BLOCKED: Shield absorbs 75% damage, taking only $finalEnemyDmg!")
                } else {
                    mutLogs.add(0, "${battle.enemy.name} lunges at you, dealing $finalEnemyDmg damage!")
                }
                nextPlayerHp = (nextPlayerHp - finalEnemyDmg).coerceAtLeast(0)
            }
        }

        // Clear player action block/dodge charges for the next tick
        val clearDodge = false
        val clearShield = false

        // Check if battle resolved
        if (nextEnemyHp <= 0) {
            // Victory
            mutLogs.add(0, "VICTORY! You defeated ${battle.enemy.name}!")
            val goldReward = battle.enemy.goldReward
            val xpReward = battle.enemy.xpReward
            
            // DROP SYSTEM: Bosses drop guaranteed Unique Legendary gear! Commoners have a standard roll chance.
            val lootResult = if (battle.enemy.isBoss) {
                repository.generateGuaranteedBossUnique(battle.enemy.name, player.level, _activeDungeon.value?.floor ?: 3)
            } else {
                if (Random.nextFloat() <= 0.40f) {
                    repository.generateProceduralGear(player.level, _activeDungeon.value?.floor ?: 1)
                } else null
            }

            val nextGold = player.gold + goldReward
            val nextXp = player.xp + xpReward
            
            // Increments offline unsynced crawler sessions if connection mode is OFFLINE!
            val holdsUnsynced = if (!player.isOnlineMode) player.unsyncedLootCount + 1 else player.unsyncedLootCount

            var updatedP = player.copy(
                currentHp = nextPlayerHp, 
                gold = nextGold, 
                xp = nextXp,
                unsyncedLootCount = holdsUnsynced
            )

            // Save player state immediately
            repository.updatePlayerState(updatedP)
            checkLevelUp(updatedP)

            // Modify map tile to normal floor
            val dungeon = _activeDungeon.value
            if (dungeon != null) {
                dungeon.grid[battle.enemy.row][battle.enemy.col].type = TileType.FLOOR
            }

            _activeBattle.value = battle.copy(
                playerCurrentHp = nextPlayerHp,
                enemyCurrentHp = nextEnemyHp,
                companionActionText = "Victory!",
                battleLogs = mutLogs,
                isFinished = true,
                isVictory = true,
                lootedItem = lootResult,
                dodgeActive = clearDodge,
                shieldActive = clearShield
            )

            // Save looted item
            if (lootResult != null) {
                val finalLoot = if (!player.isOnlineMode) lootResult.copy(isSynced = false) else lootResult
                repository.insertItem(finalLoot)
            }

            addDungeonLog("Defeated ${battle.enemy.name}. Gained $xpReward XP and $goldReward Gold.")
        } else if (nextPlayerHp <= 0) {
            // Defeat
            mutLogs.add(0, "DEFEAT! You were crushed by ${battle.enemy.name}...")
            
            val lostGold = (player.gold * 0.25).toInt()
            val nextGold = (player.gold - lostGold).coerceAtLeast(0)
            
            val updatedP = player.copy(currentHp = stats.maxHp, gold = nextGold) // fully revive
            repository.updatePlayerState(updatedP)

            _activeBattle.value = battle.copy(
                playerCurrentHp = 0,
                enemyCurrentHp = nextEnemyHp,
                companionActionText = "Fled",
                battleLogs = mutLogs,
                isFinished = true,
                isVictory = false,
                dodgeActive = clearDodge,
                shieldActive = clearShield
            )

            _activeDungeon.value = null // escape dungeon
            _currentScreen.value = "home"
            addDungeonLog("Revived at Sanctuary. Lost $lostGold Gold in defeat.")
        } else {
            // Continue Combat with recalculated HP values
            _activeBattle.value = battle.copy(
                playerCurrentHp = nextPlayerHp,
                enemyCurrentHp = nextEnemyHp,
                companionActionText = companionText,
                battleLogs = mutLogs,
                dodgeActive = clearDodge,
                shieldActive = clearShield,
                bossActivePhase = bossPhase
            )
        }
    }

    // --- 6. STANDARD TACTICAL REAL-TIME RPG CONTROLS ---

    fun performStandardCombo() {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return
        val stats = totalStats.value
        val logs = battle.battleLogs.toMutableList()
        
        val daggerActive = (battle.activeWeaponSlot == 0)
        var damage = if (daggerActive) {
            (stats.attack * 0.65).toInt()
        } else {
            (stats.attack * 1.5).toInt()
        }
        
        var combo = battle.currentComboCount + 1
        var suffixText = ""
        
        // Block effect for Necromancer Phase 2 shield
        if (battle.enemy.name.contains("Necromancer") && battle.bossActivePhase == 2) {
            damage = (damage * 0.50).toInt()
            suffixText = " (Bone shield absorbed part of hit!)"
        }
        
        if (combo >= 3) {
            damage = (damage * 2.3).toInt()
            combo = 0
            logs.add(0, "💥 COMBO FINISHER SLAM! Executed blazing triple strikes for $damage damage on ${battle.enemy.name}!")
        } else {
            logs.add(0, "⚔️ Pressed ATTACK: Swapped strikes dealing $damage damage.$suffixText")
        }
        
        val nextEnemyHp = (battle.enemyCurrentHp - damage).coerceAtLeast(0)
        _activeBattle.value = battle.copy(
            enemyCurrentHp = nextEnemyHp,
            currentComboCount = combo,
            battleLogs = logs
        )
        
        if (nextEnemyHp <= 0) {
            viewModelScope.launch { resolveVictoryAndAward() }
        }
    }

    fun performChargedAttack() {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return
        val stats = totalStats.value
        val logs = battle.battleLogs.toMutableList()
        
        var charge = battle.chargeStrikePercent + 25
        if (charge >= 100) {
            val damage = (stats.attack * 3.6).toInt()
            val nextEnemyHp = (battle.enemyCurrentHp - damage).coerceAtLeast(0)
            logs.add(0, "🔮 OVERCHARGE CRUSH FLOW! Released high energy burst for $damage crushing damage!")
            _activeBattle.value = battle.copy(
                enemyCurrentHp = nextEnemyHp,
                chargeStrikePercent = 0,
                battleLogs = logs
            )
            if (nextEnemyHp <= 0) {
                viewModelScope.launch { resolveVictoryAndAward() }
            }
        } else {
            logs.add(0, "🔮 Channelling internal energy: Charged Strike loaded at $charge%")
            _activeBattle.value = battle.copy(
                chargeStrikePercent = charge,
                battleLogs = logs
            )
        }
    }

    fun performDodgeRoll() {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return
        val logs = battle.battleLogs.toMutableList()
        logs.add(0, "🏃 Active Dodge Readied! Evading next opponent hit.")
        _activeBattle.value = battle.copy(
            dodgeActive = true,
            battleLogs = logs
        )
    }

    fun performShieldBlock() {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return
        val logs = battle.battleLogs.toMutableList()
        logs.add(0, "🛡️ Raising Heavy Shield Guard! Reducing next damage by 75%.")
        _activeBattle.value = battle.copy(
            shieldActive = true,
            battleLogs = logs
        )
    }

    fun performWeaponSwitch() {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished) return
        val logs = battle.battleLogs.toMutableList()
        val nextSlot = if (battle.activeWeaponSlot == 0) 1 else 0
        val weaponName = if (nextSlot == 0) "Swift Dual-Daggers (Dmg 55%, High-Speed Ticks)" else "Heavy Colossal Broadsword (Dmg 140%, Low-Speed Ticks)"
        logs.add(0, "🔄 Swapped weapons on hotbar to $weaponName!")
        _activeBattle.value = battle.copy(
            activeWeaponSlot = nextSlot,
            battleLogs = logs
        )
    }

    // Resolve manual victories instantly
    private suspend fun resolveVictoryAndAward() {
        val battle = _activeBattle.value ?: return
        val player = playerState.value ?: return
        val stats = totalStats.value
        val mutLogs = battle.battleLogs.toMutableList()

        mutLogs.add(0, "VICTORY! Slashed down ${battle.enemy.name}!")
        val goldReward = battle.enemy.goldReward
        val xpReward = battle.enemy.xpReward
        
        val lootResult = if (battle.enemy.isBoss) {
            repository.generateGuaranteedBossUnique(battle.enemy.name, player.level, _activeDungeon.value?.floor ?: 3)
        } else {
            if (Random.nextFloat() <= 0.40f) {
                repository.generateProceduralGear(player.level, _activeDungeon.value?.floor ?: 1)
            } else null
        }

        val holdsUnsynced = if (!player.isOnlineMode) player.unsyncedLootCount + 1 else player.unsyncedLootCount
        val updated = player.copy(
            gold = player.gold + goldReward,
            xp = player.xp + xpReward,
            unsyncedLootCount = holdsUnsynced
        )
        repository.updatePlayerState(updated)
        checkLevelUp(updated)

        val dungeon = _activeDungeon.value
        if (dungeon != null) {
            dungeon.grid[battle.enemy.row][battle.enemy.col].type = TileType.FLOOR
        }

        _activeBattle.value = battle.copy(
            enemyCurrentHp = 0,
            companionActionText = "Victory!",
            battleLogs = mutLogs,
            isFinished = true,
            isVictory = true,
            lootedItem = lootResult
        )

        if (lootResult != null) {
            val finalLoot = if (!player.isOnlineMode) lootResult.copy(isSynced = false) else lootResult
            repository.insertItem(finalLoot)
        }
        addDungeonLog("Defeated ${battle.enemy.name} via active tactics. XP +$xpReward, Gold +$goldReward.")
    }

    // Escape combat safely
    fun fleeBattle() {
        val battle = _activeBattle.value ?: return
        val player = playerState.value ?: return
        val lostGold = (player.gold * 0.15).toInt()
        viewModelScope.launch {
            repository.updatePlayerState(player.copy(gold = (player.gold - lostGold).coerceAtLeast(0)))
            _activeBattle.value = null
            _activeDungeon.value = null
            _currentScreen.value = "home"
            addDungeonLog("Fled combat encounter! Escaped town safe but dropped $lostGold gold.")
        }
    }

    // --- 7. OFFLINE EXPLORATION & SYNC SUPPORT ---

    fun toggleConnectionMode() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val nextOnline = !player.isOnlineMode
            if (nextOnline) {
                val unsynced = repository.getUnsyncedItems()
                val conflict = syncService.checkConflicts(player, unsynced)
                if (conflict.hasConflict) {
                    _syncConflictState.value = conflict
                    addDungeonLog("📡 Reconnecting... Detected sync conflict with the Cloud Database! Resolution Required.")
                } else {
                    repository.updatePlayerState(player.copy(isOnlineMode = true))
                    addDungeonLog("📡 ONLINE MODE ACTIVE. Connected to cloud servers cleanly with zero conflicts!")
                }
            } else {
                repository.updatePlayerState(player.copy(isOnlineMode = false))
                addDungeonLog("📴 OFFLINE MODE ACTIVE. Local-First Room database is fully saving sessions.")
            }
        }
    }

    fun syncOfflineData() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            if (!player.isOnlineMode) {
                addDungeonLog("⚠️ Cannot sync while OFFLINE! Toggle Online Mode first in Town Campfire.")
                return@launch
            }
            val unsynced = repository.getUnsyncedItems()
            val conflict = syncService.checkConflicts(player, unsynced)
            if (conflict.hasConflict) {
                _syncConflictState.value = conflict
                addDungeonLog("📡 Opened Cloud Database synchronization hub to resolve conflicts.")
            } else {
                addDungeonLog("📡 Sync complete! Your local storage perfectly matches remote Cloud database.")
            }
        }
    }

    fun resolveSyncAdditive() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val unsynced = repository.getUnsyncedItems()
            val (mergedPlayer, syncedItems) = syncService.performAdditiveMerge(player, unsynced)
            
            repository.updatePlayerState(mergedPlayer)
            syncedItems.forEach { item ->
                repository.updateItem(item)
            }
            _syncConflictState.value = null
            addDungeonLog("✅ Merged Additive! Combined local gold, XP, and inventory items with cloud backup.")
        }
    }

    fun resolveSyncForceLocal() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val unsynced = repository.getUnsyncedItems()
            val (mergedPlayer, syncedItems) = syncService.performForceLocal(player, unsynced)
            
            repository.updatePlayerState(mergedPlayer)
            syncedItems.forEach { item ->
                repository.updateItem(item)
            }
            _syncConflictState.value = null
            addDungeonLog("✅ Forced Overwrite Local! Uploaded local offline stats and gear to overwrite cloud profile.")
        }
    }

    fun resolveSyncForceCloud() {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val unsynced = repository.getUnsyncedItems()
            val (mergedPlayer, syncedItems) = syncService.performForceCloud(player, unsynced)
            
            repository.updatePlayerState(mergedPlayer)
            unsynced.forEach { item ->
                repository.deleteItem(item)
            }
            _syncConflictState.value = null
            addDungeonLog("✅ Forced Override Cloud! Wiped local progress and downloaded cloud backup snapshot.")
        }
    }

    // --- 8. CHARACTER CLASS & OUTIFT CUSTOMIZATION ---

    fun chooseCharacterClass(className: String) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            val baseAtk = when (className) {
                "Knight" -> 14
                "Mage" -> 8
                "Rogue" -> 10
                else -> 12
            }
            val baseDef = when (className) {
                "Knight" -> 8
                "Mage" -> 2
                "Rogue" -> 4
                else -> 4
            }
            val baseHp = when (className) {
                "Knight" -> 120
                "Mage" -> 85
                "Rogue" -> 95
                else -> 100
            }
            repository.updatePlayerState(player.copy(
                characterClass = className,
                baseAttack = baseAtk,
                baseDefense = baseDef,
                maxHp = baseHp,
                currentHp = baseHp
            ))
            addDungeonLog("Active Class transformed to $className! Playstyle features adapted.")
        }
    }

    fun chooseOutfitStyle(styleName: String) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            repository.updatePlayerState(player.copy(outfitStyle = styleName))
            addDungeonLog("Outfit altered: Selected thematic cloak/skin [$styleName]!")
        }
    }

    // Trigger player active custom unlocked skill instant combat benefit!
    fun triggerActiveSkill(skillId: String) {
        val battle = _activeBattle.value ?: return
        if (battle.isFinished || battle.enemyCurrentHp <= 0) return

        val stats = totalStats.value
        val player = playerState.value ?: return
        val logs = battle.battleLogs.toMutableList()

        var nextEnemyHp = battle.enemyCurrentHp
        var nextPlayerHp = battle.playerCurrentHp

        when (skillId) {
            "shield_bash" -> {
                val dmg = (stats.attack * 1.8).toInt()
                nextEnemyHp = (nextEnemyHp - dmg).coerceAtLeast(0)
                logs.add(0, "🛡️ SHIELD BASH! Grunted shield charge hit for $dmg damage and dazed the foe!")
            }
            "fireball" -> {
                val dmg = (stats.attack * 2.5).toInt()
                nextEnemyHp = (nextEnemyHp - dmg).coerceAtLeast(0)
                logs.add(0, "🔥 FLAME STRIKE! Engulfed target in dragon fire for $dmg explosive damage!")
            }
            "heal" -> {
                val healAmt = (stats.maxHp * 0.35).toInt()
                nextPlayerHp = (nextPlayerHp + healAmt).coerceAtMost(stats.maxHp)
                logs.add(0, "✨ HEALING WIND! Divine winds restore $healAmt health points!")
            }
            "berserk" -> {
                val dmg = (stats.attack * 3.0).toInt()
                nextEnemyHp = (nextEnemyHp - dmg).coerceAtLeast(0)
                nextPlayerHp = (nextPlayerHp - 15).coerceAtLeast(1) // Cost: 15 HP, but don't commit suicide!
                logs.add(0, "🌋 BERSERK RAMPAGE! Screaming a high battle cry, you deal $dmg massive damage at the cost of 15 HP!")
            }
            "meteor" -> {
                val dmg = (stats.attack * 3.8).toInt()
                nextEnemyHp = (nextEnemyHp - dmg).coerceAtLeast(0)
                logs.add(0, "☄️ METEOR SHOWER! Summoned cosmic fireballs falling from the sky to incinerate the enemy for $dmg damage!")
            }
            "assassinate" -> {
                val dmg = (stats.attack * 4.2).toInt()
                nextEnemyHp = (nextEnemyHp - dmg).coerceAtLeast(0)
                logs.add(0, "🗡️ ASSASSINATE! Vanished into shadows and backstabbed the foe for $dmg lethal critical strike damage!")
            }
        }

        // Instantly update active battle status
        _activeBattle.value = battle.copy(
            playerCurrentHp = nextPlayerHp,
            enemyCurrentHp = nextEnemyHp,
            battleLogs = logs
        )

        // Save hp to player table
        viewModelScope.launch {
            val p = repository.getPlayerStateDirect()
            repository.updatePlayerState(p.copy(currentHp = nextPlayerHp))
        }

        // Trigger finish check instantly
        if (nextEnemyHp <= 0) {
            viewModelScope.launch { runActiveCombatTick(1) }
        }
    }

    fun closeBattleOverlay() {
        _activeBattle.value = null
    }

    // Item management: Equip / Sell unequipped
    fun equipItem(item: ItemEntity) {
        viewModelScope.launch {
            // First unequip existing item of same slot type
            val list = repository.allItems.firstOrNull() ?: emptyList()
            for (curr in list) {
                if (curr.type == item.type && curr.isEquipped) {
                    repository.updateItem(curr.copy(isEquipped = false))
                }
            }
            // Equip new
            repository.updateItem(item.copy(isEquipped = true))
        }
    }

    fun unequipItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isEquipped = false))
        }
    }

    fun sellItem(item: ItemEntity) {
        viewModelScope.launch {
            val p = repository.getPlayerStateDirect()
            repository.updatePlayerState(p.copy(gold = p.gold + item.purchaseGoldValue))
            repository.deleteItem(item)
        }
    }

    // Equip companion
    fun selectCompanion(companionType: CompanionType) {
        viewModelScope.launch {
            val p = repository.getPlayerStateDirect()
            repository.updatePlayerState(p.copy(selectedCompanion = companionType.name))
        }
    }

    // Level-up checks
    private fun checkLevelUp(player: PlayerStateEntity) {
        val xpThreshold = player.level * 100
        if (player.xp >= xpThreshold) {
            val nextLevel = player.level + 1
            val leftOverXp = player.xp - xpThreshold
            val updated = player.copy(
                level = nextLevel,
                xp = leftOverXp,
                skillPoints = player.skillPoints + 1,
                maxHp = player.maxHp + 15,
                currentHp = player.maxHp + 15,
                baseAttack = player.baseAttack + 3,
                baseDefense = player.baseDefense + 1
            )
            viewModelScope.launch {
                repository.updatePlayerState(updated)
                addDungeonLog("🎉 LEVEL UP! You reached Level $nextLevel. Gained HP, Attack, Defense, and 1 Skill Point.")
            }
            // re-check just in case they gained enough for another level
            checkLevelUp(updated)
        }
    }

    // Skill tree custom building with parent dependency checking
    fun buySkill(skillId: String) {
        viewModelScope.launch {
            val player = repository.getPlayerStateDirect()
            
            val skillNode = listAvailableSkills().find { it.id == skillId } ?: return@launch
            if (player.skillPoints < skillNode.cost) {
                addDungeonLog("⚠️ Insufficient Skill Points to learn ${skillNode.name}! (Need ${skillNode.cost} points)")
                return@launch
            }

            val currentUnlocked = player.unlockedSkills.split(",").filter { it.isNotEmpty() }.toMutableSet()
            if (currentUnlocked.contains(skillId)) return@launch

            // Check parent dependency requirement
            if (skillNode.parentId != null && !currentUnlocked.contains(skillNode.parentId)) {
                val parentName = listAvailableSkills().find { it.id == skillNode.parentId }?.name ?: "Prerequisite"
                addDungeonLog("⚠️ Cannot learn ${skillNode.name}! Requires prerequisite: $parentName")
                return@launch
            }

            currentUnlocked.add(skillId)
            val updated = player.copy(
                unlockedSkills = currentUnlocked.joinToString(","),
                skillPoints = player.skillPoints - skillNode.cost
            )
            repository.updatePlayerState(updated)
            addDungeonLog("✨ UNLOCKED ABILITY: Learned ${skillNode.name}!")
        }
    }

    // Quick reset option if user wants to play again or clear DB progress
    fun resetCharacter() {
        viewModelScope.launch {
            repository.clearLoot()
            val resetState = PlayerStateEntity()
            repository.updatePlayerState(resetState)
            repository.provisionStarterItems()
            _activeDungeon.value = null
            _currentScreen.value = "home"
        }
    }

    private fun listAvailableSkills(): List<SkillNode> {
        return listOf(
            // --- WARRIOR / COMBAT BRANCH ---
            SkillNode(
                name = "Shield Bash",
                id = "shield_bash",
                description = "Active: Smashes target dealing 180% physical attack power value in damage.",
                isUnlocked = false,
                cost = 1,
                isActive = true,
                parentId = null
            ),
            SkillNode(
                name = "Iron Will",
                id = "iron_will",
                description = "Passive: Hardens skin to permanently increase Base Physical Defense by +10.",
                isUnlocked = false,
                cost = 1,
                isActive = false,
                parentId = "shield_bash"
            ),
            SkillNode(
                name = "Berserk Rampage",
                id = "berserk",
                description = "Active: Unleashes extreme adrenaline dealing 300% massive physical attack damage at the cost of 15 HP.",
                isUnlocked = false,
                cost = 2,
                isActive = true,
                parentId = "iron_will"
            ),

            // --- MAGE / MAGIC BRANCH ---
            SkillNode(
                name = "Flame Strike",
                id = "fireball",
                description = "Active: Swipes a line of burning fire dealing 250% spell base damage to foes.",
                isUnlocked = false,
                cost = 1,
                isActive = true,
                parentId = null
            ),
            SkillNode(
                name = "Arcane Barrier",
                id = "arcane_barrier",
                description = "Passive: Infuses armor with magical runes giving +15 to permanent Elemental Resistance.",
                isUnlocked = false,
                cost = 1,
                isActive = false,
                parentId = "fireball"
            ),
            SkillNode(
                name = "Meteor Shower",
                id = "meteor",
                description = "Active: Summons explosive astral meteorites dealing 380% massive magical spell damage.",
                isUnlocked = false,
                cost = 2,
                isActive = true,
                parentId = "arcane_barrier"
            ),

            // --- ROGUE / AGILITY BRANCH ---
            SkillNode(
                name = "Healing Wind",
                id = "heal",
                description = "Active: Recovers 35% of total Max Health points in emergency critical combat situations.",
                isUnlocked = false,
                cost = 1,
                isActive = true,
                parentId = null
            ),
            SkillNode(
                name = "Shadow Step",
                id = "shadow_step",
                description = "Passive: Attunes reflexes to gain +12% permanent Dodge and Avoidance rate.",
                isUnlocked = false,
                cost = 1,
                isActive = false,
                parentId = "heal"
            ),
            SkillNode(
                name = "Assassinate",
                id = "assassinate",
                description = "Active: Teleports behind target in shadows to deal 420% lethal critical backstab strike damage.",
                isUnlocked = false,
                cost = 2,
                isActive = true,
                parentId = "shadow_step"
            )
        )
    }
}

data class CalculatedStats(
    val attack: Int = 12,
    val defense: Int = 4,
    val maxHp: Int = 100,
    val critChance: Int = 5,
    val setBonuses: List<String> = emptyList(),
    val attackSpeed: Float = 1.0f,
    val elementalResist: Int = 0,
    val dodgeChance: Int = 0,
    val containsUnique: Boolean = false
)
