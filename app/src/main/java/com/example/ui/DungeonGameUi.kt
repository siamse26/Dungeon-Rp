package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DungeonGameApp(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val equippedItems by viewModel.equippedItems.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val totalStats by viewModel.totalStats.collectAsStateWithLifecycle()
    val activeDungeon by viewModel.activeDungeon.collectAsStateWithLifecycle()
    val dungeonLogs by viewModel.dungeonLogs.collectAsStateWithLifecycle()
    val activeBattle by viewModel.activeBattle.collectAsStateWithLifecycle()
    val chestDrop by viewModel.chestDrop.collectAsStateWithLifecycle()
    val syncConflict by viewModel.syncConflictState.collectAsStateWithLifecycle()
    val travelCodexOpen by viewModel.travelCodexOpen.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (player != null) {
                GameHeaderToolbar(
                    player = player!!,
                    onReset = { viewModel.resetCharacter() },
                    currentScreen = currentScreen,
                    onBack = { viewModel.navigateTo("home") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            OnyxBackground
                        )
                    )
                )
        ) {
            if (player == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val p = player!!

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        "home" -> MainMenuScreen(
                            player = p,
                            equippedItems = equippedItems,
                            stats = totalStats,
                            onNavigateTo = { viewModel.navigateTo(it) },
                            onLaunchDungeon = {
                                if (activeDungeon != null) {
                                    viewModel.navigateTo("dungeon")
                                } else {
                                    viewModel.enterDungeonExploration()
                                }
                            },
                            hasActiveDungeon = activeDungeon != null,
                            viewModel = viewModel
                        )

                        "dungeon" -> DungeonExplorationScreen(
                            dungeon = activeDungeon,
                            logs = dungeonLogs,
                            playerHp = p.currentHp,
                            playerMaxHp = totalStats.maxHp,
                            onMove = { r, c -> viewModel.movePlayer(r, c) },
                            onDescend = { viewModel.descendFloor() },
                            onBackToMenu = { viewModel.navigateTo("home") },
                            onOpenBackpack = { viewModel.navigateTo("inventory") },
                            onOpenTravel = { viewModel.openTravelCodex() },
                            playerClass = p.characterClass
                        )

                        "inventory" -> InventoryScreen(
                            items = allItems,
                            equippedItems = equippedItems,
                            onEquip = { viewModel.equipItem(it) },
                            onUnequip = { viewModel.unequipItem(it) },
                            onSell = { viewModel.sellItem(it) },
                            onBack = { viewModel.navigateTo("home") }
                        )

                        "skills" -> SkillTreeScreen(
                            player = p,
                            availableSkills = viewModel.availableSkills,
                            onBuySkill = { viewModel.buySkill(it) },
                            onBack = { viewModel.navigateTo("home") }
                        )

                        "companions" -> CompanionTavernScreen(
                            activeCompanion = p.selectedCompanion,
                            onSelect = { viewModel.selectCompanion(it) },
                            onBack = { viewModel.navigateTo("home") }
                        )

                        "maps" -> MapSelectScreen(
                            player = p,
                            onSelectMap = { viewModel.selectMapAndLaunch(it) },
                            onBack = { viewModel.navigateTo("home") }
                        )
                    }
                }

                // Global overlay triggers
                // 1. Battle Overlay
                if (activeBattle != null) {
                    BattleOverlay(
                        battleState = activeBattle!!,
                        playerUnlockedSkills = p.unlockedSkills,
                        onTriggerSkill = { viewModel.triggerActiveSkill(it) },
                        onClose = { viewModel.closeBattleOverlay() },
                        onComboSlash = { viewModel.performStandardCombo() },
                        onChargedAttack = { viewModel.performChargedAttack() },
                        onDodge = { viewModel.performDodgeRoll() },
                        onShieldBlock = { viewModel.performShieldBlock() },
                        onWeaponSwitch = { viewModel.performWeaponSwitch() },
                        onFlee = { viewModel.fleeBattle() }
                    )
                }

                // 2. Chest Loot drop Modal
                if (chestDrop != null) {
                    ChestDropModal(
                        item = chestDrop!!,
                        onClaim = { viewModel.claimChestItem(it) },
                        onSell = { viewModel.sellChestItem(it) },
                        onClose = { viewModel.closeChestDrop() }
                    )
                }

                // 3. Cloud Database Sync Conflict Resolution overlay
                if (syncConflict != null) {
                    SyncResolutionOverlay(
                        conflict = syncConflict!!,
                        onResolveAdditive = { viewModel.resolveSyncAdditive() },
                        onResolveForceLocal = { viewModel.resolveSyncForceLocal() },
                        onResolveForceCloud = { viewModel.resolveSyncForceCloud() },
                        onClose = { viewModel.closeSyncOverlay() }
                    )
                }

                // 4. Waypoints and Fast Travel Codex Overlay
                if (travelCodexOpen) {
                    TravelCodexOverlay(
                        player = p,
                        activeDungeon = activeDungeon,
                        onTravelWorld = { stageId, floorNum -> viewModel.fastTravelToWorldWaypoint(stageId, floorNum) },
                        onTravelLocal = { r, c, name -> viewModel.teleportToLocalWaypoint(r, c, name) },
                        onClose = { viewModel.closeTravelCodex() },
                        onReturnToTown = {
                            viewModel.navigateTo("home")
                            viewModel.closeTravelCodex()
                        }
                    )
                }
            }
        }
    }
}

// Global Toolbar displaying Character overview & Stats
@Composable
fun GameHeaderToolbar(
    player: PlayerStateEntity,
    currentScreen: String,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentScreen != "home") {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("back_button")
                            .size(36.dp)
                            .background(Color(0x1AFFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Text(
                        text = "SOLO HERO",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lv. ${player.level}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Mini level-up point notifier
                        if (player.skillPoints > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "+${player.skillPoints} PT",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // XP and Gold HUD indicators
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Gold Icon & Text
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalButton)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = "Gold Coins",
                        tint = EmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${player.gold}g",
                        color = EmberGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Settings icon for character reset
                var showResetDialog by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .testTag("settings_button")
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Reset Character",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showResetDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetDialog = false },
                        title = { Text("Erase Progress?") },
                        text = { Text("Are you sure you want to completely wipe all levels, skills, and procedurally generated inventory gear stats? This works completely offline.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showResetDialog = false
                                    onReset()
                                },
                                modifier = Modifier.testTag("confirm_wipe_btn")
                            ) {
                                Text("Wipe Progress", color = CrimsonRed)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetDialog = false }) {
                                Text("Keep", color = Color.White)
                            }
                        },
                        containerColor = DarkSlate
                    )
                }
            }
        }
    }
}

// 1. MAIN MENU SCREEN (Home)
@Composable
fun MainMenuScreen(
    player: PlayerStateEntity,
    equippedItems: List<ItemEntity>,
    stats: CalculatedStats,
    onNavigateTo: (String) -> Unit,
    onLaunchDungeon: () -> Unit,
    hasActiveDungeon: Boolean,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Card Showcase
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, SlateBorder),
            tonalElevation = 8.dp
        ) {
            Box {
                // Background artistic gradient
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF251F35), GunmetalSurface)
                            )
                        )
                ) {
                    RetroAtmosphereSparks(
                        modifier = Modifier.fillMaxSize(),
                        particleColor = EmberGold
                    )
                }

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Adaptive Character Avatar / Crest
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF402821))
                            .border(3.dp, EmberGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Character Crest",
                            tint = EmberGold,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "DUNGEON RAIDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmberGold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Hero Siam Level ${player.level}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CrimsonRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(player.characterClass.uppercase(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(player.outfitStyle, fontSize = 12.sp, color = EmberGold, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Companion Subtext Indicator
                    val comp = CompanionType.valueOf(player.selectedCompanion)
                    val compText = if (comp == CompanionType.NONE) {
                        "Fighting Solo (No active companion)"
                    } else {
                        "With loyal companion: ${comp.name} 👥"
                    }
                    Text(
                        text = compText,
                        fontSize = 13.sp,
                        color = if (comp == CompanionType.NONE) Color.LightGray else CosmicTeal,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Experience Progress bar
                    val currentXpNeeded = player.level * 100
                    val xpPercent = (player.xp.toFloat() / currentXpNeeded.toFloat()).coerceIn(0f, 1f)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("EXPERIENCE XP", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${player.xp} / $currentXpNeeded", fontSize = 11.sp, color = Color.LightGray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { xpPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmberGold,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                }
            }
        }

        // Action Buttons: Dungeon Play
        Button(
            onClick = onLaunchDungeon,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("launch_dungeon_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasActiveDungeon) "CONTINUE RUN (Floor ${player.currentFloor})" else "ENTER FOREST DUNGEON",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cosmic Fast Travel Button
        Button(
            onClick = { viewModel.openTravelCodex() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_travel_codex_home_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CosmicTeal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Explore, contentDescription = "Fast Travel", tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "FAST TRAVEL CODEX 🌌",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Navigate other stages map selection
        OutlinedButton(
            onClick = { onNavigateTo("maps") },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("select_map_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmberGold),
            border = BorderStroke(1.5.dp, EmberGold),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SWITCH OFFLINE MAP (Multi-Map)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid Menu Options (Inventory, Skill Tree, Companions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MenuIconCard(
                title = "Inventory",
                subtitle = "Equip rarity gear",
                icon = Icons.Default.Backpack,
                color = SpectralViolet,
                modifier = Modifier
                    .weight(1f)
                    .testTag("menu_inventory"),
                onClick = { onNavigateTo("inventory") }
            )
            MenuIconCard(
                title = "Skill Tree",
                subtitle = "${player.skillPoints} points left",
                icon = Icons.Default.Bolt,
                color = EmberGold,
                modifier = Modifier
                    .weight(1f)
                    .testTag("menu_skills"),
                onClick = { onNavigateTo("skills") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            MenuIconCard(
                title = "Companion Tavern",
                subtitle = "Co-op active support",
                icon = Icons.Default.People,
                color = CosmicTeal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("menu_companions"),
                onClick = { onNavigateTo("companions") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CHARACTER SELECT & CUSTOM OUTFIT SECTION
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF382A25))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT CHARACTER CLASS & SKIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmberGold,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text("Select Hero Profession Type:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val classes = listOf("Knight", "Mage", "Rogue")
                    classes.forEach { cls ->
                        val isSelected = (player.characterClass == cls)
                        Button(
                            onClick = { viewModel.chooseCharacterClass(cls) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CrimsonRed else DarkSlate
                            ),
                            border = if (isSelected) BorderStroke(1.dp, EmberGold) else null,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(cls.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.LightGray)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text("Customized Outfit Style Armor Theme:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val styles = listOf("Vanguard Crimson", "Midnight Onyx", "Royal Gold", "Jade Ranger")
                    styles.forEach { sty ->
                        val isSelected = (player.outfitStyle == sty)
                        Button(
                            onClick = { viewModel.chooseOutfitStyle(sty) },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) SpectralViolet else Color(0xFF1E2129)
                            ),
                            border = if (isSelected) BorderStroke(1.dp, EmberGold) else null,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(sty.substringBefore(" ").uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NETWORK SYNC & OFFLINE COOP MODULE
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF232D28))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NETWORK COOP & OUTPOST SYNC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CosmicTeal,
                        letterSpacing = 1.sp
                    )
                    
                    val modeLabel = if (player.isOnlineMode) "📡 ONLINE" else "📴 OFFLINE"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (player.isOnlineMode) Color(0xFF102A1F) else Color(0xFF2B2020))
                            .clickable { viewModel.toggleConnectionMode() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (player.isOnlineMode) Color.Green else Color.Red))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(modeLabel, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A343A))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Unsynchronized local runs: ",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = "${player.unsyncedLootCount} runs locally cached",
                        fontSize = 12.sp,
                        color = if (player.unsyncedLootCount > 0) LegendOrange else Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { viewModel.syncOfflineData() },
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("sync_offline_progress"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicTeal)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESYNC CAMPFIRE DATA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RPG Statistics Detailed Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF382A25))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HERO STATS SUMMARY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmberGold,
                    letterSpacing = 1.sp
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF382A25))

                StatRowItem(label = "Attack Power ⚔️", value = "${stats.attack}", subText = "Base ${player.baseAttack}")
                StatRowItem(label = "Physical Defense 🛡️", value = "${stats.defense}", subText = "Base ${player.baseDefense}")
                StatRowItem(label = "Attack Recoil Cooldown 🕒", value = "${String.format("%.2f", stats.attackSpeed)}s per hit", subText = "Weapon item")
                StatRowItem(label = "Elemental Resistance 🌀", value = "${stats.elementalResist}%", subText = "Shield + items")
                StatRowItem(label = "Dodge Chance % 💨", value = "${stats.dodgeChance}%", subText = "Professions bonus")
                StatRowItem(label = "Max Health HP ❤️", value = "${stats.maxHp}", subText = "Base ${player.maxHp}")
                StatRowItem(label = "Strike Critical Chance % ✨", value = "${stats.critChance}%", subText = "Base 5%")

                // Armor set bonuses indicator
                if (stats.setBonuses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ACTIVE UNIQUE ARMOR SET EFFECTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LegendOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    for (bonus in stats.setBonuses) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = LegendOrange, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(bonus, fontSize = 12.sp, color = LegendOrange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatRowItem(label: String, value: String, subText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subText, color = Color.Gray, fontSize = 11.sp)
        }
        Text(value, color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MenuIconCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(115.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFF382A25)),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 2. DUNGEON EXPLORATION SCREEN (Grid-based walk!)
@Composable
fun DungeonExplorationScreen(
    dungeon: DungeonLevel?,
    logs: List<String>,
    playerHp: Int,
    playerMaxHp: Int,
    onMove: (Int, Int) -> Unit,
    onDescend: () -> Unit,
    onBackToMenu: () -> Unit,
    onOpenBackpack: () -> Unit,
    onOpenTravel: () -> Unit,
    playerClass: String = "Knight"
) {
    if (dungeon == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Explore, contentDescription = null, tint = EmberGold, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No Active Exploration Run", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Launch are procedurally generated dungeon first from the sanctuary campfire.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBackToMenu, colors = ButtonDefaults.buttonColors(containerColor = EmberGold)) {
                Text("Return Campfire", color = ShadowBlack)
            }
        }
        return
    }

    var activeTexturePack by remember { mutableStateOf(TexturePack.CLASSIC_CRYPT) }

    val infiniteTransition = rememberInfiniteTransition(label = "dungeon_anims")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "player_bob"
    )
    val flickerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "torch_flicker"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "enemy_pulse"
    )

    // Identify if player stands on Stairs down
    val isPlayerOnStairsDown = dungeon.grid[dungeon.playerRow][dungeon.playerCol].type == TileType.STAIRS_DOWN
    val isPlayerOnWaypoint = dungeon.grid[dungeon.playerRow][dungeon.playerCol].type == TileType.WAYPOINT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floor header tracker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val stageName = when (dungeon.stageId) {
                    1 -> "Forgotten Grotto Crypt"
                    2 -> "Sulfurous Inferno Abyss"
                    else -> "Celestial Aether Citadel"
                }
                Text(stageName.uppercase(), color = activeTexturePack.primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Chamber Floor ${dungeon.floor} / 3", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                Text("STYLE: ${activeTexturePack.displayName}", color = activeTexturePack.primaryColor.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            // Real-time Health Counter in HUD
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // CYCLE VISUAL TEXTURE PACKS (DYNAMIC CUSTOM STYLES)
                IconButton(
                    onClick = {
                        val packs = TexturePack.values()
                        val nextIndex = (packs.indexOf(activeTexturePack) + 1) % packs.size
                        activeTexturePack = packs[nextIndex]
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(activeTexturePack.primaryColor.copy(0.15f))
                        .size(34.dp)
                        .testTag("cycle_texture_pack")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Cycle Texture Pack",
                        tint = activeTexturePack.primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // TRAVEL CODEX BUTTON
                IconButton(
                    onClick = onOpenTravel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicTeal.copy(0.15f))
                        .size(34.dp)
                        .testTag("open_travel_codex_dungeon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Fast Travel Codex",
                        tint = CosmicTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$playerHp/$playerMaxHp HP", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Procedural Dungeon Grid View
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            color = Color(0xFF0F0B09),
            border = BorderStroke(2.dp, activeTexturePack.primaryColor)
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    for (r in 0 until dungeon.height) {
                        Row(modifier = Modifier.weight(1f)) {
                            for (c in 0 until dungeon.width) {
                                val isPlayer = (r == dungeon.playerRow && c == dungeon.playerCol)
                                val cell = dungeon.grid[r][c]

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    DungeonTileContent(
                                        cell = cell,
                                        isPlayer = isPlayer,
                                        texturePack = activeTexturePack,
                                        bobOffset = bobOffset,
                                        flickerAlpha = flickerAlpha,
                                        pulseScale = pulseScale,
                                        playerClass = playerClass,
                                        enemies = dungeon.enemies
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // QUICK COMBAT HOTBAR & BACKPACK SHORTCUT
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1AFFFFFF),
            border = BorderStroke(1.dp, Color(0xFF382A25))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Hotbar slots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HOTBAR:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmberGold)
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1715))
                            .border(1.dp, CrimsonRed, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚔️", fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF15191E))
                            .border(1.dp, CosmicTeal, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🛡️", fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E1D15))
                            .border(1.dp, EmberGold, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧪", fontSize = 14.sp)
                    }
                }

                // Right: Backpack Button
                Button(
                    onClick = onOpenBackpack,
                    colors = ButtonDefaults.buttonColors(containerColor = SpectralViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp).testTag("open_backpack_inside_dungeon"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Backpack, contentDescription = "Inventory Backpack", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BACKPACK (INV)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Display Waypoint Fast-Travel banner if standing on one
        AnimatedVisibility(
            visible = isPlayerOnWaypoint,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onOpenTravel() }
                    .testTag("waypoint_banner_button"),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F262E),
                border = BorderStroke(1.5.dp, CosmicTeal)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🌌", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("COSMIC WAYPOINT ACTIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Safe zone. Fully restored HP. Tap here to travel!", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                    Button(
                        onClick = onOpenTravel,
                        colors = ButtonDefaults.buttonColors(containerColor = CosmicTeal),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("FAST TRAVEL", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }

        // Descend or Ascend Button if standing on Exit Portal
        AnimatedVisibility(
            visible = isPlayerOnStairsDown,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Button(
                onClick = onDescend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("descend_stairs_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmberGold)
            ) {
                Text(
                    text = if (dungeon.floor >= 3) "CLAIM STAGE CLEAR GRAND LOOT! 🏆" else "DESCEND STAIRS DEEPER (Chamber ${dungeon.floor + 1})",
                    color = ShadowBlack,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Tactical Movement D-Pad Layout and Log console
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Log Console (Left Side)
            Surface(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x99000000),
                border = BorderStroke(1.dp, Color(0xFF382A25))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    reverseLayout = false
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("TRAP") -> CrimsonRed
                                log.contains("VICTORY") -> EmberGold
                                log.contains("Level") -> LegendOrange
                                else -> Color.LightGray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Directional Pad Walking Controls (Right Side)
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // North Button
                Button(
                    onClick = { onMove(-1, 0) },
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("move_north"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSlate),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move North", tint = EmberGold)
                }

                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // West
                    Button(
                        onClick = { onMove(0, -1) },
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("move_west"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSlate),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move West", tint = EmberGold)
                    }

                    // Spacer/Center Center label
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFB300)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmberGold))
                    }

                    // East
                    Button(
                        onClick = { onMove(0, 1) },
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("move_east"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSlate),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move East", tint = EmberGold)
                    }
                }

                // South
                Button(
                    onClick = { onMove(1, 0) },
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("move_south"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSlate),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move South", tint = EmberGold)
                }
            }
        }
    }
}

// 3. INVENTORY & EQUIPMENT SCREEN
@Composable
fun InventoryScreen(
    items: List<ItemEntity>,
    equippedItems: List<ItemEntity>,
    onEquip: (ItemEntity) -> Unit,
    onUnequip: (ItemEntity) -> Unit,
    onSell: (ItemEntity) -> Unit,
    onBack: () -> Unit
) {
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Loot Inventory Chest", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("${items.size} / 25 items", color = Color.Gray, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compare Card Pane (if weapon selected)
        if (selectedItem != null) {
            val compareItem = selectedItem!!
            val currentEquipped = equippedItems.find { it.type == compareItem.type }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkSlate,
                border = BorderStroke(1.5.dp, getRarityColor(compareItem.rarity))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                compareItem.rarity,
                                color = getRarityColor(compareItem.rarity),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(compareItem.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { selectedItem = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Compare", tint = Color.LightGray)
                        }
                    }

                    Text(
                        text = compareItem.description,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF382A25))

                    // Comparative Stats layout
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SELECTED ITEM STATS:", fontSize = 11.sp, color = EmberGold, fontWeight = FontWeight.Bold)
                            if (compareItem.bonusAttack > 0) Text("+${compareItem.bonusAttack} Attack ⚔️", color = Color.White)
                            if (compareItem.bonusDefense > 0) Text("+${compareItem.bonusDefense} Defense 🛡️", color = Color.White)
                            if (compareItem.bonusHp > 0) Text("+${compareItem.bonusHp} Max HP ❤️", color = Color.White)
                            if (compareItem.bonusCrit > 0) Text("+${compareItem.bonusCrit}% Critical Strike ✨", color = Color.White)
                        }

                        if (currentEquipped != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("COMPARE TO EQUIPPED:", fontSize = 11.sp, color = Color.MediumGray(), fontWeight = FontWeight.Bold)
                                Text(currentEquipped.name, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                
                                val atkDiff = compareItem.bonusAttack - currentEquipped.bonusAttack
                                if (atkDiff != 0) {
                                    Text(
                                        text = "${if (atkDiff > 0) "+" else ""}$atkDiff Attack",
                                        color = if (atkDiff > 0) CosmicTeal else CrimsonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val defDiff = compareItem.bonusDefense - currentEquipped.bonusDefense
                                if (defDiff != 0) {
                                    Text(
                                        text = "${if (defDiff > 0) "+" else ""}$defDiff Defense",
                                        color = if (defDiff > 0) CosmicTeal else CrimsonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text("No existing gear equipped in this slot.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (compareItem.isEquipped) {
                                    onUnequip(compareItem)
                                } else {
                                    onEquip(compareItem)
                                }
                                selectedItem = null
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("compare_action_equip"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmberGold)
                        ) {
                            Text(if (compareItem.isEquipped) "UNEQUIP" else "EQUIP PIECE", color = ShadowBlack)
                        }

                        Button(
                            onClick = {
                                onSell(compareItem)
                                selectedItem = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compare_action_sell"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF382A25))
                        ) {
                            Text("SELL (+${compareItem.purchaseGoldValue}g)", color = EmberGold)
                        }
                    }
                }
            }
        }

        // Inventory list
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("Inventory is Empty", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Slay dungeon creatures to drop rare procedural artifacts.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    val color = getRarityColor(item.rarity)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItem = item },
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.isEquipped) Color(0xFF1E261A) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (item.isEquipped) CosmicTeal else Color(0xFF382A25))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.rarity,
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    if (item.isEquipped) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CosmicTeal)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("EQUIPPED", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    item.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Text(
                                    text = "Slot: ${item.type} | Value: ${item.purchaseGoldValue}g",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            // Dynamic stats display helper
                            val statText = when {
                                item.bonusAttack > 0 -> "+${item.bonusAttack} Atk ⚔️"
                                item.bonusDefense > 0 -> "+${item.bonusDefense} Def 🛡️"
                                else -> "+${item.bonusHp} HP ❤️"
                            }
                            Text(statText, color = EmberGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// 4. CUSTOM SKILL TREE SYSTEM
@Composable
fun SkillTreeScreen(
    player: PlayerStateEntity,
    availableSkills: List<SkillNode>,
    onBuySkill: (String) -> Unit,
    onBack: () -> Unit
) {
    val unlockedSet = remember(player.unlockedSkills) {
        player.unlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("skills_back_button")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.LightGray)
            }
            Text("Citadel Codex Skill Tree", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EmberGold.copy(0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = EmberGold, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Available Skill Points: ${player.skillPoints}", color = EmberGold, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val branches = listOf(
            Triple("Warrior Combat Tree ⚔️", listOf("shield_bash", "iron_will", "berserk"), Color(0xFFC62828)),
            Triple("Mage Magic Tree 🔮", listOf("fireball", "arcane_barrier", "meteor"), Color(0xFF6A1B9A)),
            Triple("Rogue Agility Tree 🍃", listOf("heal", "shadow_step", "assassinate"), Color(0xFF2E7D32))
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            branches.forEach { (branchTitle, skillIds, accentColor) ->
                item {
                    Text(
                        text = branchTitle.uppercase(),
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                val branchSkills = availableSkills.filter { skillIds.contains(it.id) }
                items(branchSkills) { skill ->
                    val isUnlocked = unlockedSet.contains(skill.id)
                    val parentIsUnlocked = skill.parentId == null || unlockedSet.contains(skill.parentId)
                    val parentNode = if (skill.parentId != null) availableSkills.find { it.id == skill.parentId } else null

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSlate,
                        border = BorderStroke(1.dp, if (isUnlocked) EmberGold else if (parentIsUnlocked) accentColor.copy(alpha = 0.5f) else Color(0xFF2D2420)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(skill.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    val skillTypeName = if (skill.id == "shield_bash" || skill.id == "berserk" || skill.id == "fireball" || skill.id == "meteor" || skill.id == "heal" || skill.id == "assassinate") "ACTIVE COMBAT" else "PERMANENT PASSIVE"
                                    Text(skillTypeName, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (isUnlocked) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EmberGold.copy(0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("UNLOCKED", color = EmberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = EmberGold, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("${skill.cost} Cost", color = EmberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(skill.description, color = Color.LightGray, fontSize = 13.sp)

                            // Prerequisite indicator
                            if (!isUnlocked && parentNode != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val isMet = unlockedSet.contains(parentNode.id)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isMet) Color(0x224CAF50) else Color(0x22F44336))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isMet) "✅ Prerequisite Met: ${parentNode.name}" else "🔒 Requires: ${parentNode.name}",
                                        color = if (isMet) Color(0xFF81C784) else Color(0xFFE57373),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (!isUnlocked) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val canLearn = player.skillPoints >= skill.cost && parentIsUnlocked
                                Button(
                                    onClick = { onBuySkill(skill.id) },
                                    enabled = canLearn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("buy_skill_${skill.id}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        disabledContainerColor = Color(0xFF231E1E)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (!parentIsUnlocked) "PREREQUISITE REQ" else if (player.skillPoints < skill.cost) "NEED MORE POINTS" else "UNLOCK FOR ${skill.cost} SP",
                                        color = if (canLearn) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. TACTICAL COMPANION RECRUITMENT TAVERN SCREEN
@Composable
fun CompanionTavernScreen(
    activeCompanion: String,
    onSelect: (CompanionType) -> Unit,
    onBack: () -> Unit
) {
    val activeType = try {
        CompanionType.valueOf(activeCompanion)
    } catch (e: Exception) {
        CompanionType.NONE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Companion Recruitment Tavern", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Assemble supportive AI companions fighting side-by-side in real-time combat.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(20.dp))

        // Tavern options
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cleric Companion
            CompanionCard(
                name = "Aria the Divine Cleric",
                desc = "Class: Mystic Supporter\n+ Active Spell: Restores hefty health points to you every 3 ticks during active battle overlay.",
                isActive = activeType == CompanionType.CLERIC,
                iconType = Icons.Default.Favorite,
                tint = CosmicTeal,
                onRecruit = { onSelect(CompanionType.CLERIC) }
            )

            // Mage Companion
            CompanionCard(
                name = "Zephyr the Fire Archmage",
                desc = "Class: Magical Ranged DPS\n+ Active Offense: Conjures massive explosive Fireball dealing 3x standard damage to mythical creatures every 3 ticks.",
                isActive = activeType == CompanionType.MAGE,
                iconType = Icons.Default.LocalFireDepartment,
                tint = CrimsonRed,
                onRecruit = { onSelect(CompanionType.MAGE) }
            )

            // Rogue Companion
            CompanionCard(
                name = "Kage the Shadow Rogue",
                desc = "Class: Stealth Striker\n+ Combat buff: Passive +12% strike Critical chance bonus + slashes enemies dynamically.",
                isActive = activeType == CompanionType.ROGUE,
                iconType = Icons.Default.Bolt,
                tint = EmberGold,
                onRecruit = { onSelect(CompanionType.ROGUE) }
            )

            // Solo Run
            CompanionCard(
                name = "Explore Solo (Wanderer)",
                desc = "Wander through chambers without companions. True hardcore rogue-like solo challenge.",
                isActive = activeType == CompanionType.NONE,
                iconType = Icons.Default.Person,
                tint = Color.LightGray,
                onRecruit = { onSelect(CompanionType.NONE) }
            )
        }
    }
}

@Composable
fun CompanionCard(
    name: String,
    desc: String,
    isActive: Boolean,
    iconType: ImageVector,
    tint: Color,
    onRecruit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFF1F232B) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (isActive) CosmicTeal else Color(0xFF382A25)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tint.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = iconType, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = Color.Gray, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRecruit,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isActive) CosmicTeal else EmberGold),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(if (isActive) "ACTIVE PARTNER" else "RECRUIT TACTICAL CO-OP", fontSize = 11.sp, color = ShadowBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 6. MULTI-MAP OFFLINE SELECTION SCREEN
@Composable
fun MapSelectScreen(
    player: PlayerStateEntity,
    onSelectMap: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dungeon Map Selection", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Select biomes with procedurally scaled layouts, monsters, and bosses.", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stage 1
            MapZoneCard(
                stageId = 1,
                title = "1. Grotto Crypt Entrance",
                unlocked = true, // stage 1 always free
                bgGradient = listOf(Color(0xFF263238), Color(0xFF101618)),
                currentStageId = player.currentStageId,
                bossName = "Lord Necromancer Overlord",
                onSelect = { onSelectMap(1) }
            )

            // Stage 2
            MapZoneCard(
                stageId = 2,
                title = "2. Sulfurous Inferno Abyss",
                unlocked = player.currentStageId >= 2,
                bgGradient = listOf(Color(0xFF3E2723), Color(0xFF1B0C0A)),
                currentStageId = player.currentStageId,
                bossName = "Kazar, Balrog Fire-Bringer",
                onSelect = { onSelectMap(2) }
            )

            // Stage 3
            MapZoneCard(
                stageId = 3,
                title = "3. Celestial Aether Citadel",
                unlocked = player.currentStageId >= 3,
                bgGradient = listOf(Color(0xFF4A148C), Color(0xFF1A0A30)),
                currentStageId = player.currentStageId,
                bossName = "Ultimate Void Sovereign God",
                onSelect = { onSelectMap(3) }
            )
        }
    }
}

@Composable
fun MapZoneCard(
    stageId: Int,
    title: String,
    unlocked: Boolean,
    bgGradient: List<Color>,
    currentStageId: Int,
    bossName: String,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(enabled = unlocked, onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, if (currentStageId == stageId) EmberGold else Color(0x33FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(bgGradient))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = if (unlocked) Color.White else Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (currentStageId == stageId) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmberGold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CURRENT TARGET", color = ShadowBlack, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Epic Boss: $bossName", color = Color.LightGray, fontSize = 12.sp)
                }

                if (!unlocked) {
                    Text("🔒 LOCKED: Complete preceding stage to unlock map", color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Procedural: 13x13 grid, Fog of war active. Fully Offline.", color = CosmicTeal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// 7. ACTIVE COMBAT BATTLE DIALOG (Real-time active interface overlays)
@Composable
fun BattleOverlay(
    battleState: BattleState,
    playerUnlockedSkills: String,
    onTriggerSkill: (String) -> Unit,
    onClose: () -> Unit,
    onComboSlash: () -> Unit,
    onChargedAttack: () -> Unit,
    onDodge: () -> Unit,
    onShieldBlock: () -> Unit,
    onWeaponSwitch: () -> Unit,
    onFlee: () -> Unit
) {
    val skillSet = remember(playerUnlockedSkills) {
        playerUnlockedSkills.split(",").filter { it.isNotEmpty() }.toSet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60D0908))
            .clickable(enabled = false) {}, // absorb touch events
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, EmberGold),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Battle header title
                Text(
                    text = "SWORDS CLASHING - ACTIVE ENGAGEMENT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = EmberGold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // HP COMPARISON HUD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Player COLUMN
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("HERO SIAM", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (battleState.playerCurrentHp.toFloat() / battleState.playerMaxHp.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = CosmicTeal,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Text("${battleState.playerCurrentHp} / ${battleState.playerMaxHp} HP", color = CosmicTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // VS Emblem
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .size(32.dp)
                            .background(Color(0xFF3E2D28), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("VS", color = EmberGold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    // Enemy COLUMN
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(battleState.enemy.name.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (battleState.enemyCurrentHp.toFloat() / battleState.enemyMaxHp.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = CrimsonRed,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Text("${battleState.enemyCurrentHp} / ${battleState.enemyMaxHp} HP", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time custom clashing graphics and animations!
                BattleClashGraphic(
                    enemyEmoji = enemyEmojiForName(battleState.enemy.name),
                    playerCurrentHp = battleState.playerCurrentHp,
                    enemyCurrentHp = battleState.enemyCurrentHp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Track boss phase visual markers
                if (battleState.enemy.isBoss) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2C1919))
                            .border(1.dp, CrimsonRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("👿 ACTIVE BOSS PHASE: ${battleState.bossActivePhase} / 2", color = EmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Real-time supportive Companion active indicator text
                if (battleState.companionActionText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E2631))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = battleState.companionActionText,
                            color = CosmicTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // BATTLE SCROLL CONSOLE LOGS
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0x33000000),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF382A25))
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(battleState.battleLogs) { log ->
                            Text(
                                text = log,
                                color = when {
                                    log.contains("CRITICAL") -> LegendOrange
                                    log.contains("lunges") -> CrimsonRed
                                    log.contains("healed") -> CosmicTeal
                                    log.contains("COMBO") -> EmberGold
                                    log.contains("DODGE") || log.contains("DODGED") -> Color.Cyan
                                    log.contains("GUARD") || log.contains("Shield") -> Color.Green
                                    else -> Color.LightGray
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ACTIVE ACTION BUTTONS
                if (!battleState.isFinished) {
                    // Tactically Interactive Combat buttons
                    Text(
                        "MANUAL ATTACK & MITIGATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmberGold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onComboSlash,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("action_combo"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("SLASH [x${battleState.currentComboCount}]", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onChargedAttack,
                            colors = ButtonDefaults.buttonColors(containerColor = SpectralViolet),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("action_charged"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("CHARGE [${battleState.chargeStrikePercent}%]", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onWeaponSwitch,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSlate),
                            modifier = Modifier.weight(1.2f).height(44.dp).testTag("action_weapon_switch"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = BorderStroke(1.dp, EmberGold)
                        ) {
                            val name = if (battleState.activeWeaponSlot == 0) "🗡️ DAGGERS" else "⚔️ GREATSWORD"
                            Text(name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = EmberGold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onShieldBlock,
                            colors = ButtonDefaults.buttonColors(containerColor = if (battleState.shieldActive) LegendOrange else CrimsonRed),
                            modifier = Modifier.weight(1.2f).height(40.dp).testTag("action_shield"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (battleState.shieldActive) "SLOT LOCKED" else "GUARD BLOCK 🛡️", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDodge,
                            colors = ButtonDefaults.buttonColors(containerColor = if (battleState.dodgeActive) LegendOrange else CosmicTeal),
                            modifier = Modifier.weight(1.2f).height(40.dp).testTag("action_dodge"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (battleState.dodgeActive) "DODGE READY" else "DODGE ROLL 💨", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onFlee,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.weight(0.8f).height(40.dp).testTag("action_flee"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("RUN/FLEE 🏃", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "TAP INSTANT SPECIAL SKILLS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeCombatSkills = listOf(
                            Triple("shield_bash", "Shield Bash 🛡️", "use_skill_shield_bash"),
                            Triple("berserk", "Berserk 🌋", "use_skill_berserk"),
                            Triple("heal", "Heal Wind ✨", "use_skill_heal"),
                            Triple("assassinate", "Assassinate 🗡️", "use_skill_assassinate"),
                            Triple("fireball", "Flame Strike 🔥", "use_skill_fireball"),
                            Triple("meteor", "Meteor ☄️", "use_skill_meteor")
                        )

                        activeCombatSkills.forEach { (id, label, tag) ->
                            ActiveSkillButton(
                                label = label,
                                unlocked = skillSet.contains(id),
                                modifier = Modifier
                                    .width(115.dp)
                                    .testTag(tag),
                                onClick = { onTriggerSkill(id) }
                            )
                        }
                    }
                } else {
                    // Result section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (battleState.isVictory) "🏆 VICTORY!" else "💀 DEFEAF / FLEE",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (battleState.isVictory) EmberGold else CrimsonRed
                        )

                        // Legendary drop notifier
                        if (battleState.isVictory && battleState.lootedItem != null) {
                            val item = battleState.lootedItem
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x1AFF5722),
                                border = BorderStroke(1.5.dp, getRarityColor(item.rarity)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "MONSTER DROP UNLOCKED!",
                                            color = LegendOrange,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(item.description, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Text(
                                        item.rarity,
                                        color = getRarityColor(item.rarity),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("close_battle_overlay"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmberGold)
                        ) {
                            Text("CLOSE BATTLE JOURNAL", color = ShadowBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSkillButton(
    label: String,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = unlocked,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EmberGold,
            disabledContainerColor = Color(0xFF241C1A)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = if (unlocked) label else "🔒",
            fontSize = if (unlocked) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (unlocked) ShadowBlack else Color.Gray
        )
    }
}

// 8. LOOT DROP CHEST MODAL (Renders whenever player steps on chest)
@Composable
fun ChestDropModal(
    item: ItemEntity,
    onClaim: (ItemEntity) -> Unit,
    onSell: (ItemEntity) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD90D0908))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSlate,
            border = BorderStroke(2.5.dp, getRarityColor(item.rarity))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glitter shine illustration placeholder
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(getRarityColor(item.rarity).copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = null,
                        tint = getRarityColor(item.rarity),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${item.rarity} PROCEDURAL GEAR".uppercase(),
                    color = getRarityColor(item.rarity),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stats list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (item.bonusAttack > 0) Text("Attack Power: +${item.bonusAttack} ⚔️", color = Color.White, fontWeight = FontWeight.Bold)
                    if (item.bonusDefense > 0) Text("Defense Protection: +${item.bonusDefense} 🛡️", color = Color.White, fontWeight = FontWeight.Bold)
                    if (item.bonusHp > 0) Text("Structure Health: +${item.bonusHp} HP ❤️", color = Color.White, fontWeight = FontWeight.Bold)
                    if (item.bonusCrit > 0) Text("Critical Strike: +${item.bonusCrit}% ✨", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(item.description, color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onClaim(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("claim_chest_item"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberGold)
                    ) {
                        Text("ADD KEY PIECE TO INVENTORY", color = ShadowBlack, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onSell(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sell_chest_instant"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmberGold),
                        border = BorderStroke(1.dp, EmberGold)
                    ) {
                        Text("DISCARD & QUICK SELL (+${(item.purchaseGoldValue * 0.4).toInt()}g)")
                    }
                }
            }
        }
    }
}

// Helpers
fun getRarityColor(rarity: String): Color {
    return when (rarity) {
        Rarity.LEGENDARY.name -> LegendOrange
        Rarity.EPIC.name -> SpectralViolet
        Rarity.RARE.name -> RareBlue
        Rarity.UNCOMMON.name -> CosmicTeal
        else -> NormalGray
    }
}

fun Color.Companion.MediumGray(): Color = Color(0xFF90A4AE)

// 6. CLOUD SYNCHRONIZATION CONFLICT RESOLUTION MODAL
@Composable
fun SyncResolutionOverlay(
    conflict: com.example.data.sync.SyncConflictInfo,
    onResolveAdditive: () -> Unit,
    onResolveForceLocal: () -> Unit,
    onResolveForceCloud: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF60B0807))
            .clickable(enabled = false) {}, // devour click events
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, EmberGold),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with glowing cloud synchronization icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(EmberGold.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = EmberGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Citadel Cloud Database Sync",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Conflict identified upon network reconnection. Select a merge resolution rule below.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Comparison grid card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF130E0D)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C221F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PROFILE DISCREPANCIES DETECTED",
                            color = EmberGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Discrepancy details rows
                        ConflictComparisonRow("Character Level", "${conflict.localLevel}", "Lv. ${conflict.remoteLevel}", conflict.localLevel != conflict.remoteLevel)
                        ConflictComparisonRow("Total Gold Accumulated", "${conflict.localGold}g", "${conflict.remoteGold}g", conflict.localGold != conflict.remoteGold)
                        ConflictComparisonRow("Current Experience Points", "${conflict.localXp} XP", "${conflict.remoteXp} XP", conflict.localXp != conflict.remoteXp)
                        ConflictComparisonRow("Unlocked Skills Count", "${conflict.localSkills.size}", "${conflict.remoteSkills.size}", conflict.localSkills != conflict.remoteSkills)
                        ConflictComparisonRow("Unsynced Offline Gear Loot", "${conflict.unsyncedItemsCount} Item(s)", "None (Cloud)", conflict.unsyncedItemsCount > 0)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELECT MERGE STRATEGY",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // --- Resolution Pathway #1: Safe Additive Merge ---
                ResolutionStrategyBlock(
                    title = "Option 1: Safe Additive Merge (RECOMMENDED)",
                    description = "Combines both profiles: Keeps gold & XP earned offline, preserves all newly collected dungeon gear, and unlocks both sets of skill codices.",
                    buttonLabel = "SAFE COMBINE MERGE",
                    buttonColor = EmberGold,
                    textColor = ShadowBlack,
                    onClick = onResolveAdditive,
                    tag = "btn_resolve_additive"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // --- Resolution Pathway #2: Force Local State ---
                ResolutionStrategyBlock(
                    title = "Option 2: Force Local Exploration Progress",
                    description = "Treats this device's local state as correct: Uploads your stats, levels, gold and items, completely overwriting the older remote Cloud snapshot.",
                    buttonLabel = "FORCE UPLOAD LOCAL",
                    buttonColor = CosmicTeal,
                    textColor = Color.White,
                    onClick = onResolveForceLocal,
                    tag = "btn_resolve_force_local"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // --- Resolution Pathway #3: Force Cloud Overwrite ---
                ResolutionStrategyBlock(
                    title = "Option 3: Download Cloud Backup Snapshot",
                    description = "Reverts progress: Purges all unsynced offline levels, gold boosts, and wipes any gear items gained offline to match the remote cloud profile precisely.",
                    buttonLabel = "FORCE REVERT TO CLOUD",
                    buttonColor = Color(0xFFC62828),
                    textColor = Color.White,
                    onClick = onResolveForceCloud,
                    tag = "btn_resolve_force_cloud"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Cancel/Close option to let user stay offline and resolve later
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("btn_close_sync")
                ) {
                    Text("Continue Exploring Offline for Now", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConflictComparisonRow(
    label: String,
    localVal: String,
    cloudVal: String,
    hasDiff: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localVal, color = if (hasDiff) CrimsonRed else Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Text(cloudVal, color = if (hasDiff) CosmicTeal else Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ResolutionStrategyBlock(
    title: String,
    description: String,
    buttonLabel: String,
    buttonColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E1614),
        border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = buttonColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = Color.LightGray, fontSize = 10.sp, lineHeight = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag(tag),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(buttonLabel, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun TravelCodexOverlay(
    player: PlayerStateEntity,
    activeDungeon: DungeonLevel?,
    onTravelWorld: (Int, Int) -> Unit,
    onTravelLocal: (Int, Int, String) -> Unit,
    onClose: () -> Unit,
    onReturnToTown: () -> Unit
) {
    val scrollState = rememberScrollState()
    val discoveredKeys = remember(player.discoveredWaypoints) {
        player.discoveredWaypoints.split(",").filter { it.isNotEmpty() }.toSet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0808))
            .clickable(enabled = false) {}, // consume clicks
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, CosmicTeal),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = CosmicTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Travel Codex",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_travel_codex_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Text(
                    text = "Teleport across cosmic channels to safe zones and discovered floors.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // --- SECTION 1: LOCAL SAFE ZONES (MAP TACTICAL TRAVEL) ---
                    if (activeDungeon != null) {
                        Text(
                            text = "LOCAL FLOOR SAFE BEACONS",
                            color = CosmicTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Let's filter local grid for revealed waypoints
                        val localWaypoints = remember(activeDungeon) {
                            val list = mutableListOf<Triple<Int, Int, String>>()
                            for (r in 0 until activeDungeon.height) {
                                for (c in 0 until activeDungeon.width) {
                                    val cell = activeDungeon.grid[r][c]
                                    if (cell.type == TileType.WAYPOINT && cell.isRevealed) {
                                        // Give them friendly names based on position
                                        val isSpawn = (r == 3 || r == 2 || r == 4) && (c == 3 || c == 2 || c == 4)
                                        val label = if (isSpawn) "Citadel Arrival Beacon 🗼" else "Deep Crypt Sanctum 🔮"
                                        list.add(Triple(r, c, "$label at ($r, $c)"))
                                    }
                                }
                            }
                            list
                        }

                        if (localWaypoints.isEmpty()) {
                            Text(
                                text = "⚠️ No local safe beacons discovered on this floor yet. Discover a 🌌 tile on the map first!",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
                            localWaypoints.forEach { (row, col, label) ->
                                val isCurrent = (row == activeDungeon.playerRow && col == activeDungeon.playerCol)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(!isCurrent) { onTravelLocal(row, col, label) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrent) Color(0xFF132F3A) else Color(0xFF1B1615),
                                    border = BorderStroke(1.dp, if (isCurrent) CosmicTeal else Color(0xFF2C221F))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (isCurrent) "STATIONED HERE" else "TAP TO TELEPORT INSTANTLY",
                                                color = if (isCurrent) CosmicTeal else Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (!isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = CosmicTeal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- SECTION 2: WORLD OVERVIEW WAYPOINTS (FLOOR LEVELS) ---
                    Text(
                        text = "WORLD CODEX CHANNELS",
                        color = EmberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    val worldLevels = listOf(
                        Triple(1, 1, "Forgotten Grotto Crypt - Floor 1"),
                        Triple(1, 2, "Forgotten Grotto Crypt - Floor 2"),
                        Triple(1, 3, "Forgotten Grotto Crypt - Floor 3 👹"),
                        Triple(2, 1, "Sulfurous Inferno Abyss - Floor 1"),
                        Triple(2, 2, "Sulfurous Inferno Abyss - Floor 2"),
                        Triple(2, 3, "Sulfurous Inferno Abyss - Floor 3 👹"),
                        Triple(3, 1, "Celestial Aether Citadel - Floor 1"),
                        Triple(3, 2, "Celestial Aether Citadel - Floor 2"),
                        Triple(3, 3, "Celestial Aether Citadel - Floor 3 👹")
                    )

                    worldLevels.forEach { (stage, floorNum, name) ->
                        val key = "${stage}_$floorNum"
                        val isDiscovered = discoveredKeys.contains(key)
                        val isCurrent = activeDungeon != null && activeDungeon.stageId == stage && activeDungeon.floor == floorNum

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(isDiscovered && !isCurrent) { onTravelWorld(stage, floorNum) },
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isCurrent -> Color(0xFF132F3A)
                                !isDiscovered -> Color(0xFF0F0B09)
                                else -> Color(0xFF1B1615)
                            },
                            border = BorderStroke(1.dp, when {
                                isCurrent -> CosmicTeal
                                !isDiscovered -> Color(0xFF171311)
                                else -> EmberGold.copy(0.4f)
                            })
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(if (isDiscovered) "🌌" else "🔒", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = name,
                                            color = if (isDiscovered) Color.White else Color.Gray,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = when {
                                                isCurrent -> "CURRENT STICKY CHAMBER"
                                                isDiscovered -> "DISCOVERED AND ACTIVE"
                                                else -> "REACH THIS CHAMBER TO UNLOCK"
                                            },
                                            color = when {
                                                isCurrent -> CosmicTeal
                                                isDiscovered -> EmberGold
                                                else -> Color.DarkGray
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (isDiscovered && !isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = EmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- SECTION 3: RETURN TO SANCTUARY CAMPFIRE ---
                if (activeDungeon != null) {
                    Button(
                        onClick = onReturnToTown,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3430)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("travel_return_to_town_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RETURN TO SANCTUARY TOWN SAFELY 🏕️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// GRAPHICS, VISUAL TEXTURES & PIXEL PACKS
// ==========================================

enum class TexturePack(
    val id: String,
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val wallColor: Color,
    val floorColor: Color,
    val waypointColor: Color,
    val playerColor: Color,
    val exitColor: Color,
    val playerIcon: String = ""
) {
    CLASSIC_CRYPT(
        id = "classic_crypt",
        displayName = "Classic Crypt 🧱",
        primaryColor = Color(0xFFC5A059), // EmberGold
        secondaryColor = Color(0xFFC62828), // CrimsonRed
        wallColor = Color(0xFF1E1715),
        floorColor = Color(0xFF2E2421),
        waypointColor = Color(0xFF0F262E),
        playerColor = Color(0xFF33FFB3),
        exitColor = Color(0xFF381F1A)
    ),
    NEON_SYNTH(
        id = "neon_synth",
        displayName = "Neon Cyber 👾",
        primaryColor = Color(0xFFFF007F), // Neon Pink
        secondaryColor = Color(0xFF00FFCC), // Cosmic Teal
        wallColor = Color(0xFF100020),
        floorColor = Color(0xFF03001E),
        waypointColor = Color(0xFF1C003D),
        playerColor = Color(0xFF00FFCC),
        exitColor = Color(0xFF2C003D)
    ),
    ICE_SANCTUM(
        id = "ice_sanctum",
        displayName = "Frozen Glace ❄️",
        primaryColor = Color(0xFF00B0FF), // Ice blue
        secondaryColor = Color(0xFF80D8FF), // Accent white-blue
        wallColor = Color(0xFF0D2530),
        floorColor = Color(0xFF1B3B48),
        waypointColor = Color(0xFF0E3140),
        playerColor = Color(0xFFE0F7FA),
        exitColor = Color(0xFF0C2B3A)
    ),
    TOXIC_WASTELAND(
        id = "toxic_wasteland",
        displayName = "Bio Sludge ☣️",
        primaryColor = Color(0xFF76FF03), // Poison green
        secondaryColor = Color(0xFFFFD600), // Radioactive yellow
        wallColor = Color(0xFF1A2415),
        floorColor = Color(0xFF2E3D25),
        waypointColor = Color(0xFF1D2F1B),
        playerColor = Color(0xFFB2FF59),
        exitColor = Color(0xFF2A3A22)
    ),
    COBBLESTONE_CASTLE(
        id = "cobble_castle",
        displayName = "Cobble Castle 🏰",
        primaryColor = Color(0xFF90A4AE),
        secondaryColor = Color(0xFF37474F),
        wallColor = Color(0xFF263238),
        floorColor = Color(0xFF455A64),
        waypointColor = Color(0xFF1C2D37),
        playerColor = Color(0xFFECEFF1),
        exitColor = Color(0xFF1A2327)
    ),
    VOLCANIC_MAGMA(
        id = "volcanic_magma",
        displayName = "Volcanic Magma 🌋",
        primaryColor = Color(0xFFFF3D00),
        secondaryColor = Color(0xFFFFEB3B),
        wallColor = Color(0xFF120502),
        floorColor = Color(0xFF210C07),
        waypointColor = Color(0xFF3A1108),
        playerColor = Color(0xFFFF9100),
        exitColor = Color(0xFF1B0703)
    ),
    WOODEN_KEEP(
        id = "wooden_keep",
        displayName = "Wooden Keep 🪵",
        primaryColor = Color(0xFFD7CCC8),
        secondaryColor = Color(0xFF5D4037),
        wallColor = Color(0xFF2D1B10),
        floorColor = Color(0xFF4E342E),
        waypointColor = Color(0xFF3E2723),
        playerColor = Color(0xFFA1887F),
        exitColor = Color(0xFF27130A)
    ),
    DEEP_SEA(
        id = "deep_sea",
        displayName = "Deep Sea Bubbles 🫧",
        primaryColor = Color(0xFF00E5FF),
        secondaryColor = Color(0xFF1565C0),
        wallColor = Color(0xFF010A1B),
        floorColor = Color(0xFF002244),
        waypointColor = Color(0xFF001133),
        playerColor = Color(0xFFE0F7FA),
        exitColor = Color(0xFF001F3F)
    ),
    OVERGROWN_FOLLY(
        id = "overgrown_ruins",
        displayName = "Overgrown Ruins 🌿",
        primaryColor = Color(0xFF66BB6A),
        secondaryColor = Color(0xFF1B5E20),
        wallColor = Color(0xFF0D2211),
        floorColor = Color(0xFF1C3A21),
        waypointColor = Color(0xFF122C17),
        playerColor = Color(0xFFA5D6A7),
        exitColor = Color(0xFF0E2010)
    ),
    DESERT_DUNES(
        id = "desert_dunes",
        displayName = "Desert Dunes 🏜️",
        primaryColor = Color(0xFFFFCA28),
        secondaryColor = Color(0xFF6D4C41),
        wallColor = Color(0xFF3E2723),
        floorColor = Color(0xFF8D6E63),
        waypointColor = Color(0xFF5D4037),
        playerColor = Color(0xFFFFF9C4),
        exitColor = Color(0xFF3E221A)
    )
}

// Render dynamic, textured retro cells with custom animations
@Composable
fun DungeonTileContent(
    cell: DungeonTile,
    isPlayer: Boolean,
    texturePack: TexturePack,
    bobOffset: Float,
    flickerAlpha: Float,
    pulseScale: Float,
    playerClass: String,
    enemies: List<Enemy>
) {
    val contextColor = when {
        !cell.isRevealed -> Color(0xFF050404)
        isPlayer -> texturePack.playerColor.copy(alpha = 0.25f)
        cell.type == TileType.WALL -> texturePack.wallColor
        cell.type == TileType.WAYPOINT -> texturePack.waypointColor
        cell.type == TileType.STAIRS_DOWN -> texturePack.exitColor
        else -> texturePack.floorColor
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(contextColor),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isRevealed) {
            // Draw visual textures inside wall and floor cells using Canvas!
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Draw solid wood crate structure if it's a Trap or Chest (matching image #7wood crate exactly)!
                if (cell.type == TileType.CHEST || cell.type == TileType.TRAP) {
                    val woodDark = Color(0xFF2E1C12)
                    val woodMedium = Color(0xFF7A583A)
                    val woodLight = Color(0xFFB18F64)
                    val darkBorder = Color(0xFF1B0E07)

                    // Base background
                    drawRect(color = darkBorder)
                    drawRect(
                        color = woodMedium,
                        topLeft = Offset(1.5f.dp.toPx(), 1.5f.dp.toPx()),
                        size = size.copy(width = size.width - 3.dp.toPx(), height = size.height - 3.dp.toPx())
                    )
                    // Diagonal brace (cross slat)
                    drawLine(
                        color = woodDark,
                        start = Offset(2.dp.toPx(), 2.dp.toPx()),
                        end = Offset(w - 2.dp.toPx(), h - 2.dp.toPx()),
                        strokeWidth = 3.2f.dp.toPx()
                    )
                    drawLine(
                        color = woodLight,
                        start = Offset(3.5f.dp.toPx(), 2.dp.toPx()),
                        end = Offset(w - 2.dp.toPx(), h - 3.5f.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Outer square inside frame lines
                    drawRect(
                        color = woodDark,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = size.copy(width = size.width - 6.dp.toPx(), height = size.height - 6.dp.toPx()),
                        style = Stroke(width = 0.8f.dp.toPx())
                    )
                    // Nails
                    drawCircle(Color.Black, radius = 0.7f.dp.toPx(), center = Offset(3.5f.dp.toPx(), 3.5f.dp.toPx()))
                    drawCircle(Color.Black, radius = 0.7f.dp.toPx(), center = Offset(w - 3.5f.dp.toPx(), 3.5f.dp.toPx()))
                    drawCircle(Color.Black, radius = 0.7f.dp.toPx(), center = Offset(3.5f.dp.toPx(), h - 3.5f.dp.toPx()))
                    drawCircle(Color.Black, radius = 0.7f.dp.toPx(), center = Offset(w - 3.5f.dp.toPx(), h - 3.5f.dp.toPx()))
                } else {
                    when (cell.type) {
                        TileType.WALL -> {
                            when (texturePack) {
                                TexturePack.CLASSIC_CRYPT -> {
                                    // Reddish brick rows with mortar joints
                                    drawLine(Color(0xFF2C1F1C), start = Offset(0f, h * 0.33f), end = Offset(w, h * 0.33f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF2C1F1C), start = Offset(0f, h * 0.66f), end = Offset(w, h * 0.66f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF2C1F1C), start = Offset(w * 0.5f, 0f), end = Offset(w * 0.5f, h * 0.33f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF2C1F1C), start = Offset(w * 0.25f, h * 0.33f), end = Offset(w * 0.25f, h * 0.66f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF2C1F1C), start = Offset(w * 0.75f, h * 0.33f), end = Offset(w * 0.75f, h * 0.66f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF2C1F1C), start = Offset(w * 0.5f, h * 0.66f), end = Offset(w * 0.5f, h), strokeWidth = 1.dp.toPx())
                                }
                                TexturePack.NEON_SYNTH -> {
                                    // Glowing neon circuit borders
                                    drawRect(
                                        color = texturePack.primaryColor.copy(alpha = 0.5f),
                                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                        size = size.copy(width = size.width - 2.dp.toPx(), height = size.height - 2.dp.toPx()),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                    drawCircle(texturePack.primaryColor, radius = 2.dp.toPx(), center = Offset(w / 2, h / 2))
                                }
                                TexturePack.ICE_SANCTUM -> {
                                    // Ice chiseled cracks
                                    drawLine(Color(0xFF3A6D80), start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF3A6D80), start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = 0.5.dp.toPx())
                                }
                                TexturePack.TOXIC_WASTELAND -> {
                                    // Diagonal radioactive warning caution lines
                                    drawLine(Color(0xFF3D2D1B), start = Offset(0f, h * 0.5f), end = Offset(w * 0.5f, 0f), strokeWidth = 2.dp.toPx())
                                    drawLine(Color(0xFF3D2D1B), start = Offset(0f, h), end = Offset(w, 0f), strokeWidth = 2.dp.toPx())
                                }
                                TexturePack.COBBLESTONE_CASTLE -> {
                                    // Natural grey cobblestone joints (matching image cobblestones)
                                    drawLine(Color(0xFF1F2B30), start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 1.5f.dp.toPx())
                                    drawLine(Color(0xFF1F2B30), start = Offset(w * 0.5f, 0f), end = Offset(w * 0.5f, h * 0.5f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF1F2B30), start = Offset(w * 0.25f, h * 0.5f), end = Offset(w * 0.25f, h), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF1F2B30), start = Offset(w * 0.75f, h * 0.5f), end = Offset(w * 0.75f, h), strokeWidth = 1.dp.toPx())
                                }
                                TexturePack.VOLCANIC_MAGMA -> {
                                    // High-intensity flowing orange magma cracks
                                    val lavaCol = Color(0xFFFF4500).copy(alpha = 0.7f + flickerAlpha * 0.3f)
                                    drawRect(Color(0xFF120502))
                                    drawLine(lavaCol, start = Offset(w * 0.2f, 0f), end = Offset(w * 0.2f, h), strokeWidth = 2.dp.toPx())
                                    drawLine(lavaCol, start = Offset(w * 0.8f, 0f), end = Offset(w * 0.8f, h), strokeWidth = 2.dp.toPx())
                                    drawLine(lavaCol, start = Offset(w * 0.2f, h * 0.5f), end = Offset(w * 0.8f, h * 0.5f), strokeWidth = 1.5f.dp.toPx())
                                }
                                TexturePack.WOODEN_KEEP -> {
                                    // Wood wall plank lines (horizontal paneling block)
                                    drawLine(Color(0xFF1A100B), start = Offset(w * 0.33f, 0f), end = Offset(w * 0.33f, h), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF1A100B), start = Offset(w * 0.66f, 0f), end = Offset(w * 0.66f, h), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF6F4E37), start = Offset(w * 0.15f, h * 0.2f), end = Offset(w * 0.15f, h * 0.7f), strokeWidth = 0.5f.dp.toPx())
                                    drawLine(Color(0xFF6F4E37), start = Offset(w * 0.8f, h * 0.3f), end = Offset(w * 0.8f, h * 0.8f), strokeWidth = 0.5f.dp.toPx())
                                }
                                TexturePack.DEEP_SEA -> {
                                    // Water currents on subsea walls
                                    drawLine(Color(0xFF0F325C).copy(0.4f), start = Offset(0f, h * 0.2f), end = Offset(w, h * 0.5f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF0F325C).copy(0.4f), start = Offset(0f, h * 0.7f), end = Offset(w, h * 0.9f), strokeWidth = 1.dp.toPx())
                                }
                                TexturePack.OVERGROWN_FOLLY -> {
                                    // Overhanging leaf structures
                                    drawLine(Color(0xFF13321B), start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = 1.5f.dp.toPx())
                                    drawCircle(Color(0xFF2E7D32), radius = 2.dp.toPx(), center = Offset(w * 0.3f, h * 0.4f))
                                    drawCircle(Color(0xFF4CAF50), radius = 1.5f.dp.toPx(), center = Offset(w * 0.7f, h * 0.6f))
                                }
                                TexturePack.DESERT_DUNES -> {
                                    // Sand lines
                                    drawLine(Color(0xFF795548), start = Offset(0f, h * 0.3f), end = Offset(w, h * 0.4f), strokeWidth = 1.dp.toPx())
                                    drawLine(Color(0xFF795548), start = Offset(0f, h * 0.7f), end = Offset(w, h * 0.8f), strokeWidth = 1.dp.toPx())
                                }
                            }
                        }
                        TileType.FLOOR -> {
                            when (texturePack) {
                                TexturePack.CLASSIC_CRYPT -> {
                                    // Subtle earthy stone tile specks
                                    drawCircle(Color(0xFF3A2E2A), radius = 1.dp.toPx(), center = Offset(w * 0.35f, h * 0.4f))
                                    drawCircle(Color(0xFF3A2E2A), radius = 0.8f.dp.toPx(), center = Offset(w * 0.7f, h * 0.75f))
                                }
                                TexturePack.NEON_SYNTH -> {
                                    // Digital grids
                                    drawLine(Color(0xFF0F0028), start = Offset(w * 0.5f, 0f), end = Offset(w * 0.5f, h), strokeWidth = 0.5.dp.toPx())
                                    drawLine(Color(0xFF0F0028), start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 0.5.dp.toPx())
                                }
                                TexturePack.ICE_SANCTUM -> {
                                    // Glittering snow specular dots
                                    drawCircle(Color(0xFFACF3FF).copy(alpha = 0.25f), radius = 1.2.dp.toPx(), center = Offset(w * 0.5f, h * 0.5f))
                                }
                                TexturePack.TOXIC_WASTELAND -> {
                                    // Effervescent bubbles of acid slime
                                    drawCircle(Color(0xFF558B2F).copy(alpha = flickerAlpha * 0.45f), radius = 2.dp.toPx(), center = Offset(w * 0.4f, h * 0.6f))
                                }
                                TexturePack.COBBLESTONE_CASTLE -> {
                                    // Small natural grey paving stone circles
                                    drawCircle(Color(0xFF62727B), radius = 1.5f.dp.toPx(), center = Offset(w * 0.3f, h * 0.3f))
                                    drawCircle(Color(0xFF4F5B62), radius = 2.dp.toPx(), center = Offset(w * 0.7f, h * 0.65f))
                                    drawCircle(Color(0xFF37474F), radius = 1.2f.dp.toPx(), center = Offset(w * 0.25f, h * 0.75f))
                                }
                                TexturePack.VOLCANIC_MAGMA -> {
                                    // Cooled lava embers
                                    drawCircle(Color(0xFFFF5722).copy(alpha = flickerAlpha * 0.6f), radius = 1.5f.dp.toPx(), center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color(0xFFFFEB3B).copy(alpha = flickerAlpha * 0.4f), radius = 1.dp.toPx(), center = Offset(w * 0.75f, h * 0.3f))
                                }
                                TexturePack.WOODEN_KEEP -> {
                                    // Parquet floor panels (wood board parquet)
                                    drawLine(Color(0xFF2E1C14), start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 0.5f.dp.toPx())
                                    drawLine(Color(0xFF2E1C14), start = Offset(w * 0.5f, 0f), end = Offset(w * 0.5f, h * 0.5f), strokeWidth = 0.5f.dp.toPx())
                                    drawLine(Color(0xFF2E1C14), start = Offset(w * 0.5f, h * 0.5f), end = Offset(w * 0.5f, h), strokeWidth = 0.5f.dp.toPx())
                                }
                                TexturePack.DEEP_SEA -> {
                                    // Water bubbles rising up dynamically (bubble tile pattern #4/5)
                                    val bubY1 = h * ((0.35f + flickerAlpha * 0.15f).coerceIn(0f, 1f))
                                    val bubY2 = h * ((0.7f - flickerAlpha * 0.15f).coerceIn(0f, 1f))
                                    drawCircle(Color(0xFFE0F7FA).copy(0.4f), radius = 2.2f.dp.toPx(), center = Offset(w * 0.4f, bubY1))
                                    drawCircle(Color.White.copy(0.7f), radius = 0.6f.dp.toPx(), center = Offset(w * 0.35f, bubY1 - 1.dp.toPx()))
                                    drawCircle(Color(0xFFE0F7FA).copy(0.3f), radius = 1.6f.dp.toPx(), center = Offset(w * 0.75f, bubY2))
                                }
                                TexturePack.OVERGROWN_FOLLY -> {
                                    // Green grass leaves structures
                                    drawCircle(Color(0xFF4CAF50).copy(0.5f), radius = 1.5f.dp.toPx(), center = Offset(w * 0.3f, h * 0.5f))
                                    drawCircle(Color(0xFF81C784).copy(0.3f), radius = 2.5f.dp.toPx(), center = Offset(w * 0.7f, h * 0.4f))
                                }
                                TexturePack.DESERT_DUNES -> {
                                    // Fine quicksand sand grains
                                    drawCircle(Color(0xFFD7CCC8).copy(0.6f), radius = 0.8f.dp.toPx(), center = Offset(w * 0.2f, h * 0.3f))
                                    drawCircle(Color(0xFFD7CCC8).copy(0.6f), radius = 0.8f.dp.toPx(), center = Offset(w * 0.8f, h * 0.7f))
                                    drawCircle(Color(0xFFD7CCC8).copy(0.6f), radius = 0.8f.dp.toPx(), center = Offset(w * 0.5f, h * 0.8f))
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
 
            // Bind tile icons with beautiful visual bobs
            val cellEmoji = when {
                isPlayer -> playerIconForClass(playerClass)
                cell.type == TileType.STAIRS_UP -> "🪜"
                cell.type == TileType.STAIRS_DOWN -> "🌀"
                cell.type == TileType.WAYPOINT -> "🌌"
                cell.type == TileType.CHEST -> "🎁"
                cell.type == TileType.TRAP -> "⚠️"
                cell.type == TileType.ENEMY -> {
                    val mName = enemies.find { it.row == cell.row && it.col == cell.col }?.name ?: ""
                    enemyEmojiForName(mName)
                }
                cell.type == TileType.BOSS -> "👹"
                else -> ""
            }

            if (cellEmoji.isNotEmpty()) {
                val animModifier = when {
                    isPlayer -> Modifier.offset(y = bobOffset.dp)
                    cell.type == TileType.WAYPOINT -> Modifier
                        .scale(0.85f + flickerAlpha * 0.2f)
                        .rotate(flickerAlpha * 35f)
                    cell.type == TileType.STAIRS_DOWN -> Modifier.scale(0.95f + flickerAlpha * 0.1f)
                    cell.type == TileType.ENEMY || cell.type == TileType.BOSS -> Modifier.scale(pulseScale)
                    else -> Modifier
                }

                Box(
                    modifier = animModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cellEmoji,
                        fontSize = when {
                            isPlayer -> 14.sp
                            cell.type == TileType.BOSS -> 16.sp
                            else -> 13.sp
                        }
                    )
                }
            }
        }
    }
}

// Particle effect simulating atmospheric ember particles
@Composable
fun RetroAtmosphereSparks(
    modifier: Modifier = Modifier,
    particleColor: Color = Color(0xFFC5A059)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sparks")
    
    val animations = (0..12).map { index ->
        val duration = remember { (2200..4500).random() }
        val startDelay = remember { (0..1500).random() }
        
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, delayMillis = startDelay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "p_$index"
        )
        progress
    }
    
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas
        
        animations.forEachIndexed { i, progress ->
            val xSeed = (i * 73) % 100
            val xPos = (w * (xSeed / 100f) + (progress * 40f - 20f)).coerceIn(0f, w)
            val yPos = h - (progress * h)
            
            val alpha = when {
                progress < 0.2f -> progress / 0.2f
                progress > 0.8f -> (1f - progress) / 0.2f
                else -> 1f
            }
            
            val radius = (1.5.dp + (i % 3).dp).toPx() * (0.5f + (1f - progress) * 0.5f)
            
            drawCircle(
                color = particleColor.copy(alpha = alpha * 0.65f),
                radius = radius,
                center = Offset(xPos, yPos)
            )
        }
    }
}

// Resolves customized emojis for procedurally generated monsters
fun enemyEmojiForName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("skeleton") -> "💀"
        lower.contains("priest") -> "🧙"
        lower.contains("gargoyle") -> "🦇"
        lower.contains("hound") -> "🐺"
        lower.contains("fiend") -> "😈"
        lower.contains("imp") -> "👺"
        lower.contains("golem") -> "🤖"
        lower.contains("chimera") -> "🦁"
        lower.contains("sentinel") -> "👼"
        lower.contains("griffin") -> "🦅"
        lower.contains("drake") -> "🐉"
        lower.contains("valkyrie") -> "⚔️"
        lower.contains("necromancer") -> "🧙‍♂️"
        lower.contains("balrog") -> "🔥"
        lower.contains("aetherius") -> "👾"
        lower.contains("void") -> "👁️"
        else -> "💀"
    }
}

// Maps unique classes to custom action icons
fun playerIconForClass(characterClass: String): String {
    return when (characterClass.lowercase()) {
        "mage" -> "🧙"
        "ranger" -> "🏹"
        "rogue" -> "🗡️"
        else -> "⚔️" // Default Knight
    }
}

// Combat clash visual graphics animation panel
@Composable
fun BattleClashGraphic(
    enemyEmoji: String,
    playerCurrentHp: Int,
    enemyCurrentHp: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clash")
    
    val playerOffset by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "p_clash"
    )

    val enemyOffset by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "e_clash"
    )

    val sparksRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparks_rotate"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F0A09))
            .border(1.dp, Color(0xFF382520)),
        contentAlignment = Alignment.Center
    ) {
        RetroAtmosphereSparks(
            modifier = Modifier.fillMaxSize(),
            particleColor = Color(0xFFFF5722)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player side
            Box(
                modifier = Modifier
                    .offset(x = playerOffset.dp)
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (playerCurrentHp <= 0) Color.DarkGray else Color(0x3300FFCC))
                    .border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (playerCurrentHp <= 0) "🛡️" else "⚔️", fontSize = 26.sp)
            }

            // Contact Spark
            Box(
                modifier = Modifier
                    .rotate(sparksRotate)
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("💥", fontSize = 28.sp)
            }

            // Enemy side
            Box(
                modifier = Modifier
                    .offset(x = enemyOffset.dp)
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enemyCurrentHp <= 0) Color.DarkGray else Color(0x33FF3D00))
                    .border(1.dp, Color(0xFFFF3D00), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (enemyCurrentHp <= 0) "🪦" else enemyEmoji, fontSize = 26.sp)
            }
        }
    }
}

