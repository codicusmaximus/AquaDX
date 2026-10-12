package icu.samnyan.aqua.net.games.chu3

import ext.*
import icu.samnyan.aqua.net.LanProps
import icu.samnyan.aqua.net.db.AquaGameOptions
import icu.samnyan.aqua.net.games.GenericItemMeta
import icu.samnyan.aqua.net.games.GenericMusicMeta
import icu.samnyan.aqua.sega.chusan.model.userdata.Chu3UserItem
import org.springframework.stereotype.Service
import java.io.File

/**
 * "Unlock all" game options for CHUNITHM: songs and collectibles (nameplates, frames, trophies,
 * map icons, system voices, symbol chats, avatar parts and stages).
 *
 * Song ids come from the bundled song list. Collectible ids come from aquadx.net's chu3 all-items.json,
 * either bundled at build time (meta/chu3/all-items.json) or cached by LAN mode in its data folder.
 */
@Service
class ChusanUnlocks(val lan: LanProps) {
    companion object {
        val log = logger()

        const val MUSIC_KIND = 7

        /** Item kind -> category name in all-items.json (see docs/dev/chusan_dev_notes.md) */
        val COLLECTIBLE_KINDS = mapOf(
            1 to "namePlate", 2 to "frame", 3 to "trophy", 8 to "mapIcon",
            9 to "systemVoice", 10 to "symbolChat", 11 to "avatarAccessory", 13 to "stage",
        )

        /** "2.25.00" -> [2, 25, 0]; null when there is no leading version number */
        fun parseVersion(v: Str?) = v?.let { Regex("^\\d+(\\.\\d+)*").find(it.trim())?.value }
            ?.split('.')?.map { it.toInt() }

        fun versionAtMost(ver: Str?, max: Str?): Bool {
            val a = parseVersion(ver) ?: return true
            val b = parseVersion(max) ?: return true
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x < y
            }
            return true
        }
    }

    val musicMapping: Map<Str, GenericMusicMeta> = try {
        resJson<Map<Str, GenericMusicMeta>>("/meta/chu3/music.json") ?: emptyMap()
    } catch (e: Exception) {
        log.warn("Could not read the chu3 song list: ${e.message}")
        emptyMap()
    }

    private var itemsCache: Map<Str, Map<Str, GenericItemMeta>>? = null

    /** All collectibles by category. Empty until a copy of all-items.json is available. */
    fun allItems(): Map<Str, Map<Str, GenericItemMeta>> {
        itemsCache?.let { return it }
        val text = resStr("/meta/chu3/all-items.json")
            ?: File(lan.dataPath, "chu3/00/all-items.json").takeIf { it.isFile }?.readText()
            ?: return emptyMap()
        return try {
            JSON.decodeFromString<Map<Str, Map<Str, GenericItemMeta>>>(text).also { itemsCache = it }
        } catch (e: Exception) {
            log.warn("Could not read the chu3 item list: ${e.message}")
            emptyMap<Str, Map<Str, GenericItemMeta>>().also { itemsCache = it }
        }
    }

    fun isUnlockKind(opts: AquaGameOptions, kind: Int) =
        (kind == MUSIC_KIND && opts.chusanUnlockMusic) ||
        (kind in COLLECTIBLE_KINDS && opts.chusanUnlockCollectables)

    /** Ids to unlock for an item kind, skipping content newer than the player's game version */
    fun unlockIds(kind: Int, romVersion: Str?): List<Int> = when (kind) {
        MUSIC_KIND -> musicMapping.filter { (_, m) -> versionAtMost(m.ver, romVersion) }.keys.mapNotNull { it.toIntOrNull() }
        in COLLECTIBLE_KINDS -> allItems()[COLLECTIBLE_KINDS[kind]]
            ?.filter { (_, m) -> m.disable != true && versionAtMost(m.ver, romVersion) }
            ?.keys?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        else -> emptyList()
    }

    /** Add the unlocked items the player doesn't own yet to [items] */
    fun addUnlocked(items: MutableList<Chu3UserItem>, kind: Int, opts: AquaGameOptions?, romVersion: Str?) {
        if (opts == null || !isUnlockKind(opts, kind)) return
        val owned = items.filter { it.itemKind == kind }.map { it.itemId }.toHashSet()
        unlockIds(kind, romVersion).filterNot { it in owned }.mapTo(items) { Chu3UserItem(kind, it, 1, true) }
    }
}
