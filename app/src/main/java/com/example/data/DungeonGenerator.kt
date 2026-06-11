package com.example.data

import kotlin.random.Random

enum class TileType {
    VOID, WALL, FLOOR, STAIRS_UP, STAIRS_DOWN, CHEST, TRAP, ENEMY, BOSS, WAYPOINT
}

data class DungeonTile(
    val row: Int,
    val col: Int,
    var type: TileType,
    var isRevealed: Boolean = false
)

data class Enemy(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val row: Int,
    val col: Int,
    var hp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val isBoss: Boolean = false,
    val xpReward: Int,
    val goldReward: Int
)

class DungeonGenerator {

    fun generateDungeon(stageId: Int, floorNum: Int, width: Int = 13, height: Int = 13): DungeonLevel {
        val grid = Array(height) { r ->
            Array(width) { c ->
                DungeonTile(r, c, TileType.WALL, isRevealed = false)
            }
        }

        // Room representation
        data class Room(val x: Int, val y: Int, val w: Int, val h: Int)

        val rooms = mutableListOf<Room>()
        val minRoomSize = 3
        val maxRoomSize = 5
        val roomCount = Random.nextInt(4, 7)

        // Attempt to place rooms
        for (i in 0 until 15) {
            val w = Random.nextInt(minRoomSize, maxRoomSize + 1)
            val h = Random.nextInt(minRoomSize, maxRoomSize + 1)
            val x = Random.nextInt(1, width - w - 1)
            val y = Random.nextInt(1, height - h - 1)

            val overlaps = rooms.any { r ->
                !(x + w < r.x || x > r.x + r.w || y + h < r.y || y > r.y + r.h)
            }

            if (!overlaps) {
                rooms.add(Room(x, y, w, h))
                // Carve room
                for (rIdx in y until y + h) {
                    for (cIdx in x until x + w) {
                        grid[rIdx][cIdx].type = TileType.FLOOR
                    }
                }
            }
            if (rooms.size >= roomCount) break
        }

        // If no rooms could be created (highly unlikely), make a default fallback
        if (rooms.isEmpty()) {
            rooms.add(Room(2, 2, 4, 4))
            for (r in 2..5) {
                for (c in 2..5) {
                    grid[r][c].type = TileType.FLOOR
                }
            }
        }

        // Connect rooms with corridors
        for (i in 0 until rooms.size - 1) {
            val r1 = rooms[i]
            val r2 = rooms[i + 1]

            val startX = r1.x + r1.w / 2
            val startY = r1.y + r1.h / 2
            val endX = r2.x + r2.w / 2
            val endY = r2.y + r2.h / 2

            // Carve horizontal then vertical
            var curX = startX
            var curY = startY

            while (curX != endX) {
                grid[curY][curX].type = TileType.FLOOR
                curX += if (endX > curX) 1 else -1
            }
            while (curY != endY) {
                grid[curY][curX].type = TileType.FLOOR
                curY += if (endY > curY) 1 else -1
            }
        }

        // Place stairs
        val firstRoom = rooms.first()
        val lastRoom = rooms.last()

        val startX = firstRoom.x + Random.nextInt(0, firstRoom.w)
        val startY = firstRoom.y + Random.nextInt(0, firstRoom.h)
        grid[startY][startX].type = TileType.STAIRS_UP
        grid[startY][startX].isRevealed = true // reveal start tile

        // Reveal cells immediately around starting position
        revealArea(grid, startY, startX, width, height)

        // Generate Entry Map Waypoint in the first room
        val wpX = (firstRoom.x + firstRoom.w / 2).coerceIn(1, width - 2)
        val wpY = (firstRoom.y + firstRoom.h / 2).coerceIn(1, height - 2)
        if (wpX != startX || wpY != startY) {
            grid[wpY][wpX].type = TileType.WAYPOINT
            grid[wpY][wpX].isRevealed = true
        } else {
            val altX = if (startX + 1 < firstRoom.x + firstRoom.w) startX + 1 else startX - 1
            if (altX in 1 until width - 1) {
                grid[startY][altX].type = TileType.WAYPOINT
                grid[startY][altX].isRevealed = true
            }
        }

        // Generate Deep Sanctum Waypoint in a middle room (if size > 2)
        if (rooms.size > 2) {
            val midRoom = rooms[rooms.size / 2]
            val midWpX = (midRoom.x + midRoom.w / 2).coerceIn(1, width - 2)
            val midWpY = (midRoom.y + midRoom.h / 2).coerceIn(1, height - 2)
            if (grid[midWpY][midWpX].type == TileType.FLOOR) {
                grid[midWpY][midWpX].type = TileType.WAYPOINT
            }
        }

        val exitX = lastRoom.x + Random.nextInt(0, lastRoom.w)
        val exitY = lastRoom.y + Random.nextInt(0, lastRoom.h)
        if (startY != exitY || startX != exitX) {
            grid[exitY][exitX].type = TileType.STAIRS_DOWN
        } else {
            // Find another spot in last room
            val safeX = (lastRoom.x + 1).coerceAtMost(width - 1)
            val safeY = (lastRoom.y + 1).coerceAtMost(height - 1)
            grid[safeY][safeX].type = TileType.STAIRS_DOWN
        }

        // Place non-boss enemies and boss
        val enemies = mutableListOf<Enemy>()
        val isBossFloor = (floorNum == 3)

        val stageScale = stageId * 25 + floorNum * 12
        val enemyPool = when (stageId) {
            1 -> listOf(
                "Crypt Skeleton" to Triple(35, 12, 3),
                "Zombie Priest" to Triple(45, 10, 5),
                "Goyle Gargoyle" to Triple(50, 15, 6),
                "Crypt Hound" to Triple(30, 14, 2)
            )
            2 -> listOf(
                "Hell Fiend" to Triple(65, 18, 8),
                "Flame Imp" to Triple(50, 22, 5),
                "Fire Golem" to Triple(90, 16, 12),
                "Chimera Spawn" to Triple(80, 20, 10)
            )
            else -> listOf(
                "Archangel Sentinel" to Triple(100, 24, 15),
                "Celestial Griffin" to Triple(90, 28, 12),
                "Golden Drake" to Triple(120, 30, 18),
                "Rift Valkyrie" to Triple(110, 26, 16)
            )
        }

        // Place boss at stairs down if boss floor, otherwise standard monsters
        if (isBossFloor) {
            val bossName = when (stageId) {
                1 -> "Lord Necromancer Overlord"
                2 -> "Kazar, Balrog Fire-Bringer"
                else -> "Aetherius, Void Sovereign God"
            }
            val bossHp = 180 + stageScale * 4
            val bossAtk = 25 + stageScale / 2
            val bossDef = 12 + stageScale / 3
            val bossX = lastRoom.x + lastRoom.w / 2
            val bossY = lastRoom.y + lastRoom.h / 2

            grid[bossY][bossX].type = TileType.BOSS
            enemies.add(
                Enemy(
                    name = bossName,
                    row = bossY,
                    col = bossX,
                    hp = bossHp,
                    maxHp = bossHp,
                    attack = bossAtk,
                    defense = bossDef,
                    isBoss = true,
                    xpReward = 80 + stageScale * 3,
                    goldReward = 100 + stageScale * 4
                )
            )
        }

        // Place normal monsters, chests, and traps in other rooms
        for (roomIdx in rooms.indices) {
            val room = rooms[roomIdx]
            // Skip first room for peace, skip last room of boss floor for clarity
            if (roomIdx == 0) continue
            if (isBossFloor && roomIdx == rooms.size - 1) continue

            // Place chest
            if (Random.nextDouble() < 0.6) {
                val chestX = room.x + Random.nextInt(0, room.w)
                val chestY = room.y + Random.nextInt(0, room.h)
                if (grid[chestY][chestX].type == TileType.FLOOR) {
                    grid[chestY][chestX].type = TileType.CHEST
                }
            }

            // Place Trap
            if (Random.nextDouble() < 0.35) {
                val trapX = room.x + Random.nextInt(0, room.w)
                val trapY = room.y + Random.nextInt(0, room.h)
                if (grid[trapY][trapX].type == TileType.FLOOR) {
                    grid[trapY][trapX].type = TileType.TRAP
                }
            }

            // Place enemies
            val density = Random.nextInt(1, 3)
            for (d in 0 until density) {
                val emX = room.x + Random.nextInt(0, room.w)
                val emY = room.y + Random.nextInt(0, room.h)
                if (grid[emY][emX].type == TileType.FLOOR) {
                    grid[emY][emX].type = TileType.ENEMY

                    val proto = enemyPool[Random.nextInt(enemyPool.size)]
                    val levelMultiplier = 1.0f + (floorNum * 0.15f)
                    val hp = (proto.second.first * levelMultiplier).toInt()
                    val atk = (proto.second.second * levelMultiplier).toInt()
                    val def = (proto.second.third * levelMultiplier).toInt()

                    enemies.add(
                        Enemy(
                            name = proto.first,
                            row = emY,
                            col = emX,
                            hp = hp,
                            maxHp = hp,
                            attack = atk,
                            defense = def,
                            isBoss = false,
                            xpReward = 20 + floorNum * 5,
                            goldReward = 15 + floorNum * 4
                        )
                    )
                }
            }
        }

        return DungeonLevel(
            stageId = stageId,
            floor = floorNum,
            grid = grid,
            width = width,
            height = height,
            enemies = enemies,
            playerRow = startY,
            playerCol = startX
        )
    }

    fun revealArea(grid: Array<Array<DungeonTile>>, r: Int, c: Int, w: Int, h: Int) {
        val range = 2
        for (dr in -range..range) {
            for (dc in -range..range) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until h && nc in 0 until w) {
                    grid[nr][nc].isRevealed = true
                }
            }
        }
    }
}

data class DungeonLevel(
    val stageId: Int,
    val floor: Int,
    val grid: Array<Array<DungeonTile>>,
    val width: Int,
    val height: Int,
    val enemies: List<Enemy>,
    var playerRow: Int,
    var playerCol: Int
)
