package com.mycompany.synapse.game.logic

import kotlin.random.Random

/** Würfelposition im Gitter. */
data class Cube(val x: Int, val y: Int, val z: Int)

data class RotationTask(val left: List<Cube>, val right: List<Cube>, val mirrored: Boolean)

/** 3×3-Rotationsmatrix (ganzzahlig), zeilenweise. */
class Rot(val m: IntArray) {
    fun apply(c: Cube) = Cube(
        m[0] * c.x + m[1] * c.y + m[2] * c.z,
        m[3] * c.x + m[4] * c.y + m[5] * c.z,
        m[6] * c.x + m[7] * c.y + m[8] * c.z,
    )

    operator fun times(o: Rot): Rot {
        val r = IntArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0
            for (k in 0 until 3) s += m[i * 3 + k] * o.m[k * 3 + j]
            r[i * 3 + j] = s
        }
        return Rot(r)
    }

    override fun equals(other: Any?) = other is Rot && other.m.contentEquals(m)
    override fun hashCode() = m.contentHashCode()
}

object RotationGen {
    private val identity = Rot(intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1))
    val rotX = Rot(intArrayOf(1, 0, 0, 0, 0, -1, 0, 1, 0))
    val rotY = Rot(intArrayOf(0, 0, 1, 0, 1, 0, -1, 0, 0))
    val rotZ = Rot(intArrayOf(0, -1, 0, 1, 0, 0, 0, 0, 1))

    /** Alle 24 Drehungen eines Würfels. */
    val all: List<Rot> by lazy {
        val set = linkedSetOf(identity)
        var frontier = listOf(identity)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Rot>()
            for (r in frontier) for (g in listOf(rotX, rotY, rotZ)) {
                val p = g * r
                if (set.add(p)) next += p
            }
            frontier = next
        }
        set.toList()
    }

    private fun powers(g: Rot) = listOf(g, g * g, g * g * g)

    fun cubeCount(level: Int) = when {
        level <= 10 -> 4
        level <= 20 -> 5
        level <= 32 -> 6
        level <= 42 -> 7
        else -> 8
    }

    fun normalize(cubes: Collection<Cube>): List<Cube> {
        val mx = cubes.minOf { it.x }
        val my = cubes.minOf { it.y }
        val mz = cubes.minOf { it.z }
        return cubes.map { Cube(it.x - mx, it.y - my, it.z - mz) }
            .sortedWith(compareBy({ it.x }, { it.y }, { it.z }))
    }

    private fun mirror(cubes: List<Cube>) = normalize(cubes.map { Cube(-it.x, it.y, it.z) })

    fun sameShape(a: List<Cube>, b: List<Cube>): Boolean {
        val nb = normalize(b)
        return all.any { r -> normalize(a.map(r::apply)) == nb }
    }

    private val dirs = listOf(
        Cube(1, 0, 0), Cube(-1, 0, 0), Cube(0, 1, 0), Cube(0, -1, 0), Cube(0, 0, 1), Cube(0, 0, -1),
    )

    private fun randomShape(count: Int, rng: Random): List<Cube> {
        val cubes = mutableListOf(Cube(0, 0, 0))
        var last = cubes[0]
        var dir = dirs[rng.nextInt(dirs.size)]
        while (cubes.size < count) {
            // armartige Figuren (Shepard-Metzler): meist am letzten Würfel weiterbauen
            val base = if (rng.nextDouble() < 0.75) last else cubes[rng.nextInt(cubes.size)]
            if (rng.nextDouble() < 0.45) dir = dirs[rng.nextInt(dirs.size)]
            val c = Cube(base.x + dir.x, base.y + dir.y, base.z + dir.z)
            if (c !in cubes) {
                cubes += c
                last = c
            }
        }
        return normalize(cubes)
    }

    /** In der Isometrie-Ansicht darf kein Würfel komplett verdeckt sein. */
    fun clearlyVisible(cubes: List<Cube>): Boolean {
        val set = cubes.toSet()
        return cubes.none { c ->
            (1..8).any { k -> Cube(c.x + k, c.y + k, c.z + k) in set } ||
                (Cube(c.x + 1, c.y, c.z) in set && Cube(c.x, c.y + 1, c.z) in set && Cube(c.x, c.y, c.z + 1) in set)
        }
    }

    fun generate(level: Int, rng: Random): RotationTask {
        val l = level.coerceIn(1, 50)
        val count = cubeCount(l)
        repeat(400) {
            val shape = randomShape(count, rng)
            val mirrorShape = mirror(shape)
            if (sameShape(shape, mirrorShape)) return@repeat // achirale Figur: nicht unterscheidbar
            val present = all[rng.nextInt(all.size)]
            val left = normalize(shape.map(present::apply))
            if (!clearlyVisible(left)) return@repeat
            val extraPool = when {
                l <= 12 -> powers(rotY)
                l <= 26 -> powers(rotX) + powers(rotY) + powers(rotZ)
                else -> all.drop(1)
            }
            val mirrored = rng.nextBoolean()
            val source = if (mirrored) mirror(left) else left
            repeat(20) {
                val extra = extraPool[rng.nextInt(extraPool.size)]
                val right = normalize(source.map(extra::apply))
                if (clearlyVisible(right) && right != left) return RotationTask(left, right, mirrored)
            }
        }
        val fallback = listOf(Cube(0, 0, 0), Cube(1, 0, 0), Cube(1, 1, 0), Cube(1, 1, 1))
        return RotationTask(fallback, normalize(fallback.map(rotY::apply)), false)
    }

    fun label(level: Int): String {
        val axes = when {
            level <= 12 -> "Drehung um die Hochachse"
            level <= 26 -> "eine Achse"
            else -> "beliebige Drehung"
        }
        return "${cubeCount(level)} Würfel · $axes"
    }
}
