package icu.samnyan.aqua.net

import ext.*
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaNetUserRepo
import icu.samnyan.aqua.net.games.GenericUserDataRepo
import icu.samnyan.aqua.net.games.IUserData
import icu.samnyan.aqua.sega.chusan.model.Chu3UserDataRepo
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserDataRepo
import icu.samnyan.aqua.sega.ongeki.OgkUserDataRepo
import icu.samnyan.aqua.sega.wacca.model.db.WcUserRepo
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.text.Normalizer
import java.util.*
import kotlin.io.path.*

/**
 * LAN mode: for servers that only serve cabinets and people on a home network.
 *
 * When enabled, the web UI is served by this server, players log in by picking their card
 * (no email or password), song data is served locally, and keychip checks are skipped.
 */
@Configuration
@ConfigurationProperties(prefix = "aqua-net.lan")
class LanProps {
    var enabled: Boolean = false

    /** Local folder for web data (song lists, jackets). Files here override the ones bundled in the jar. */
    var dataPath: String = "data/web-data"

    /** When a data file is missing locally, download it from here once and keep a copy. Leave blank to stay fully offline. */
    var dataMirror: String = "https://aquadx.net"
}

@RestController
@API("/api/v2/lan")
class LanApi(
    val props: LanProps,
    val jwt: JWT,
    val userRepo: AquaNetUserRepo,
    val cardRepo: CardRepository,
    val cardService: CardService,
    val hasher: PasswordEncoder,
    val chu3: Chu3UserDataRepo,
    val mai2: Mai2UserDataRepo,
    val ongeki: OgkUserDataRepo,
    val wacca: WcUserRepo,
    val tx: TransactionTemplate,
) {
    companion object {
        val log = logger()
        private val random = SecureRandom()
    }

    // Chunithm first, so its player name wins when a card has played several games
    val repos: Map<Str, GenericUserDataRepo<out IUserData>> by lazy {
        linkedMapOf("chu3" to chu3, "mai2" to mai2, "ongeki" to ongeki, "wacca" to wacca)
    }

    fun ensureEnabled() { if (!props.enabled) 403 - "LAN mode is disabled" }

    @API("/info")
    @Doc("Whether LAN mode is enabled on this server", "{ enabled }")
    fun info() = mapOf("enabled" to props.enabled)

    @API("/players")
    @Doc("List everyone who has played on this server, most recent first", "List of players")
    suspend fun players(): List<Map<Str, Any?>> {
        ensureEnabled()
        val byCard = linkedMapOf<Long, MutableMap<Str, Any?>>()
        // Cards are loaded lazily, so read everything in one transaction
        async { tx.execute {
            repos.forEach { (game, repo) ->
                repo.findAll().forEach { data ->
                    val card = data.card ?: return@forEach
                    if (!isRealCard(card)) return@forEach
                    val entry = byCard.getOrPut(card.id) {
                        mutableMapOf(
                            "cardId" to card.id,
                            "username" to (card.aquaUser?.username ?: "user${card.id}"),
                            "name" to data.userName,
                            "games" to mutableListOf<Str>(),
                            "lastPlayed" to "",
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    (entry["games"] as MutableList<Str>).add(game)
                    val last = data.lastPlayDate.toString().replace('T', ' ')
                    if (last > (entry["lastPlayed"] as Str)) entry["lastPlayed"] = last
                }
            }
        } }
        return byCard.values.sortedByDescending { it["lastPlayed"] as Str }
    }

    @API("/login")
    @Doc("Log in as a player by their card (database id from /players) or by the access code printed on the card", "JWT token")
    suspend fun login(@RP cardId: Long?, @RP accessCode: Str?): Any {
        ensureEnabled()
        val user = async { loginUser(cardId, accessCode) }
        val token = jwt.gen(user)
        async { userRepo.save(user.apply { lastLogin = millis() }) }
        log.info("LAN login: ${user.username} (${user.auId})")
        return mapOf("token" to token, "username" to user.username)
    }

    fun loginUser(cardId: Long?, accessCode: Str?): AquaNetUser = tx.execute {
        val card = when {
            cardId != null -> cardRepo.findById(cardId).orElse(null) ?: (404 - "Card not found")
            !accessCode.isNullOrBlank() -> cardService.tryLookup(accessCode) ?: run {
                // A card that has never been scanned on a cabinet: register it now
                val digits = accessCode.filter { it.isDigit() }
                if (digits.length != 20) 400 - "Access codes are 20 digits"
                cardService.registerByAccessCode(digits)
            }
            else -> 400 - "Pick a player or type an access code"
        }
        if (!isRealCard(card)) 404 - "Card not found"
        card.aquaUser ?: adopt(card)
    }!!

    /**
     * Create a web account for a card that has never logged into the web UI.
     *
     * The card itself becomes the account's main card, so no game data moves and the
     * cabinet keeps seeing the same player.
     */
    fun adopt(card: Card): AquaNetUser {
        val gameName = repos.values.firstNotNullOfOrNull { it.findByCard(card)?.userName }
        val username = uniqueUsername(gameName)
        val user = AquaNetUser(
            username = username,
            email = "card${card.id}@lan.local",
            pwHash = hasher.encode(UUID.randomUUID().toString() + random.nextLong())!!,
            regTime = millis(), lastLogin = millis(), country = "",
            emailConfirmed = true,
        ).apply {
            displayName = gameName?.let { normalize(it) } ?: ""
            ghostCard = card
        }
        userRepo.save(user)
        card.aquaUser = user
        cardRepo.save(card)
        log.info("LAN: created account $username for card ${card.id}")
        return user
    }

    /** Turn a full-width in-game name like "ＭＥＥＭＺ" into a valid username like "meemz" */
    fun uniqueUsername(gameName: Str?): Str {
        val base = gameName?.let { normalize(it) }
            ?.filter { it.isLetterOrDigit() || it in "_-~." }
            ?.lowercase()
            ?.takeIf { it.length >= 2 && !(it.startsWith("user") && it.substring(4).toLongOrNull() != null) }
            ?.take(28)
            ?: "player"
        if (userRepo.findByUsernameIgnoreCase(base) == null) return base
        return (2..9999).asSequence().map { "$base$it" }.first { userRepo.findByUsernameIgnoreCase(it) == null }
    }

    fun normalize(s: Str) = Normalizer.normalize(s, Normalizer.Form.NFKC).trim()

    /** Placeholder cards created when data is migrated or orphaned have text in their luid */
    fun isRealCard(card: Card) = card.luid.none { it == ' ' }
}

/**
 * Serves the web UI from this server in LAN mode, so page refreshes on any page work.
 */
@Controller
class LanWebUi(val props: LanProps) {
    @GetMapping(
        "/home", "/cards", "/setup", "/support", "/pictures", "/transfer", "/verify", "/reset-password",
        "/ranking", "/ranking/{game}", "/u/{username}", "/u/{username}/{game}", "/settings", "/settings/{page}"
    )
    fun page(response: HttpServletResponse): String? {
        if (!props.enabled) { response.sendError(404); return null }
        return "forward:/index.html"
    }
}

/**
 * Serves song lists, items and jackets under /d/ (the same layout as aquadx.net/d/).
 *
 * Lookup order: the local data folder, then files bundled at build time, then (if configured) the mirror,
 * whose response is saved locally so it works offline next time.
 */
@RestController
class LanData(val props: LanProps) {
    companion object {
        val log = logger()
        val allowed = Regex("^[A-Za-z0-9_\\-./]+$")
    }

    val root: Path by lazy { Path(props.dataPath).toAbsolutePath().normalize().apply { createDirectories() } }

    @GetMapping("/d/**")
    fun data(request: HttpServletRequest, response: HttpServletResponse) {
        if (!props.enabled) return response.sendError(404)
        val rel = request.requestURI.removePrefix("/d/")
        if (!allowed.matches(rel) || ".." in rel) return response.sendError(400)

        val bytes = local(rel) ?: bundled(rel) ?: mirror(rel) ?: return response.sendError(404)
        response.contentType = when (rel.substringAfterLast('.').lowercase()) {
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        response.setHeader("Cache-Control", "public, max-age=86400")
        response.outputStream.write(bytes)
    }

    fun local(rel: Str) = root.resolve(rel).normalize().takeIf { it.startsWith(root) && it.isRegularFile() }?.readBytes()

    /** Song and item lists downloaded into the jar at build time (src/main/resources/meta/update.sh) */
    fun bundled(rel: Str): ByteArray? {
        val m = Regex("^(\\w+)/00/all-(music|items)\\.json$").find(rel) ?: return null
        val (game, kind) = m.destructured
        return (javaClass.getResourceAsStream("/meta/$game/$kind.json")
            ?: javaClass.getResourceAsStream("/meta/$game/all-$kind.json"))?.use { it.readBytes() }
    }

    fun mirror(rel: Str): ByteArray? {
        if (props.dataMirror.isBlank()) return null
        return try {
            val bytes = URI("${props.dataMirror.trimEnd('/')}/d/$rel").toURL().openConnection().apply {
                connectTimeout = 3000; readTimeout = 10000
            }.getInputStream().use { it.readBytes() }
            root.resolve(rel).normalize().takeIf { it.startsWith(root) }?.let {
                it.parent.createDirectories()
                Files.write(it, bytes)
            }
            bytes
        } catch (e: Exception) {
            log.debug("LAN data mirror miss for $rel: ${e.message}")
            null
        }
    }
}
