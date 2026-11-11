package com.jarvis.app

/*
 * Model router ka "dimaag". Is file mein Android ka koi code nahi hai,
 * isliye ye JVM unit tests mein seedha test hota hai.
 */

enum class Task { CHAT, REASONING, CODE, SEARCH, SUMMARIZE, PHONE, VISION }

enum class ErrKind { NONE, RATE_LIMIT, QUOTA, AUTH, NOT_FOUND, BAD_REQUEST, SERVER, NETWORK, EMPTY, OTHER }

object RouteModes {
    const val AUTO = "auto"
    const val ONLINE = "online"
    const val LOCAL_FIRST = "local_first"
    const val LOCAL_ONLY = "local_only"
}

class Source(val title: String, val url: String)

/** history: ("user"|"model", text). Aakhri item abhi ka user message hota hai. */
class RouterRequest(
    val task: Task,
    val history: List<Pair<String, String>>,
    val systemOnline: String,
    val systemLocal: String,
    val needSearch: Boolean = false,
    val imageB64: String? = null,
    val searchQuery: String = ""
)

class Outcome(
    val ok: Boolean,
    val text: String,
    val kind: ErrKind = ErrKind.NONE,
    val retryAfterSec: Int = 0,
    val sources: List<Source> = emptyList(),
    val searchUsed: Boolean = false,
    val searchNote: String = ""
)

interface Backend {
    val id: String
    val label: String
    val isLocal: Boolean
    val supportsVision: Boolean
    val supportsSearch: Boolean
    val capability: Int
    fun call(req: RouterRequest): Outcome
}

class RouteResult(
    val ok: Boolean,
    val text: String,
    val backend: Backend?,
    val failedBefore: List<String>,
    val failKind: ErrKind,
    val failMsg: String,
    val sources: List<Source>,
    val searchUsed: Boolean,
    val searchNote: String,
    val notes: List<String>
) {
    val switched: Boolean get() = ok && failedBefore.isNotEmpty()
}

object ErrorClassifier {
    fun classify(code: Int, message: String): ErrKind {
        val m = message.lowercase()
        return when {
            code == 0 -> ErrKind.NETWORK
            code == 429 ->
                if (m.contains("quota") || m.contains("exhaust") || m.contains("billing") || m.contains("daily"))
                    ErrKind.QUOTA else ErrKind.RATE_LIMIT
            code == 401 || code == 403 -> ErrKind.AUTH
            code == 404 -> ErrKind.NOT_FOUND
            code == 400 ->
                if (m.contains("api key") || m.contains("api_key")) ErrKind.AUTH else ErrKind.BAD_REQUEST
            code in 500..599 -> ErrKind.SERVER
            else -> ErrKind.OTHER
        }
    }
}

/** Model ke naam se uski taaqat ka andaaza (bada number = zyada capable). */
object ModelRank {
    private val sizeRegex = Regex("(\\d+(?:\\.\\d+)?)b")

    fun score(modelId: String): Int {
        val m = modelId.lowercase()
        var s = 2
        val sz = sizeRegex.find(m)?.groupValues?.get(1)?.toDoubleOrNull()
        if (sz != null) {
            s = when {
                sz >= 100 -> 6
                sz >= 60 -> 5
                sz >= 25 -> 4
                sz >= 12 -> 3
                sz >= 6 -> 2
                else -> 1
            }
        }
        if (m.contains("pro") || m.contains("opus") || m.contains("large") || m.contains("ultra")) s += 2
        if (m.contains("flash")) s += 1
        // "gemini" ke andar bhi "mini" hota hai, isliye pehle wo hata do
        val noGem = m.replace("gemini", "gem")
        if (noGem.contains("lite") || noGem.contains("mini") || noGem.contains("small") ||
            m.contains("instant") || m.contains("nano") || m.contains("tiny")
        ) s -= 1
        return s.coerceAtLeast(0)
    }
}

/** Kaun sa model kitni der "rest" par hai (rate limit, galat key...). */
class HealthTracker(private val now: () -> Long = { System.currentTimeMillis() }) {
    private val until = HashMap<String, Long>()
    private val why = HashMap<String, String>()

    @Synchronized
    fun cooldown(id: String, ms: Long, reason: String) {
        until[id] = now() + ms
        why[id] = reason
    }

    @Synchronized
    fun inCooldown(id: String): Boolean {
        val u = until[id] ?: return false
        if (now() >= u) {
            until.remove(id)
            why.remove(id)
            return false
        }
        return true
    }

    @Synchronized
    fun remainingMs(id: String): Long {
        val u = until[id] ?: return 0L
        return (u - now()).coerceAtLeast(0L)
    }

    @Synchronized
    fun reasonOf(id: String): String = why[id] ?: ""

    @Synchronized
    fun clear(id: String) {
        until.remove(id)
        why.remove(id)
    }
}

object RoutePlanner {

    /**
     * Task, user ke mode aur halat ke hisaab se models ki line banao (pehla = sabse pehle try hoga).
     * online=false ho to online models list mein aate hi nahi.
     */
    fun order(
        task: Task,
        mode: String,
        online: Boolean,
        onlines: List<Backend>,
        local: Backend?,
        avoidLocal: Boolean,
        needsVision: Boolean
    ): List<Backend> {
        var ons: List<Backend> = if (online) onlines else emptyList()
        if (needsVision) ons = ons.filter { it.supportsVision }
        val loc: Backend? = if (needsVision) null else local

        val strong = task == Task.REASONING || task == Task.CODE
        val ordered: List<Backend> = when {
            task == Task.SEARCH -> ons.sortedWith(
                compareByDescending<Backend> { it.supportsSearch }.thenByDescending { it.capability }
            )
            strong -> ons.sortedByDescending { it.capability }
            else -> ons
        }

        val out = ArrayList<Backend>()
        when (mode) {
            RouteModes.LOCAL_ONLY -> {
                if (loc != null) out.add(loc)
            }
            RouteModes.ONLINE -> out.addAll(ordered)
            RouteModes.LOCAL_FIRST -> {
                val localFirst = loc != null && !avoidLocal &&
                    task != Task.SEARCH && task != Task.PHONE
                if (localFirst && loc != null) {
                    out.add(loc)
                    out.addAll(ordered)
                } else {
                    out.addAll(ordered)
                    if (loc != null) out.add(loc)
                }
            }
            else -> {
                out.addAll(ordered)
                if (loc != null) out.add(loc)
            }
        }
        return out
    }
}

class RouterCore(
    private val health: HealthTracker,
    private val sleep: (Long) -> Unit = { ms ->
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
        }
    }
) {

    private fun cooldownFor(o: Outcome): Pair<Long, String> {
        return when (o.kind) {
            ErrKind.RATE_LIMIT -> {
                val ms = (o.retryAfterSec * 1000L).coerceIn(30_000L, 900_000L)
                Pair(ms, "rate limit")
            }
            ErrKind.QUOTA -> Pair(30 * 60_000L, "quota khatam")
            ErrKind.AUTH -> Pair(10 * 60_000L, "API key galat ya expire")
            ErrKind.NOT_FOUND -> Pair(10 * 60_000L, "model nahi mila")
            ErrKind.SERVER, ErrKind.EMPTY, ErrKind.NETWORK -> Pair(30_000L, "connection/server dikkat")
            else -> Pair(15_000L, "error")
        }
    }

    private fun shortReason(k: ErrKind): String = when (k) {
        ErrKind.RATE_LIMIT -> "rate limit"
        ErrKind.QUOTA -> "quota khatam"
        ErrKind.AUTH -> "key galat"
        ErrKind.NOT_FOUND -> "model nahi mila"
        ErrKind.SERVER -> "server error"
        ErrKind.NETWORK -> "internet"
        ErrKind.EMPTY -> "khali jawab"
        ErrKind.BAD_REQUEST -> "request galat"
        else -> "error"
    }

    /**
     * order ke models ek-ek karke try karo.
     * - 429/quota/auth: seedha agle model par (retry bekaar hai)
     * - 5xx/khali/internet: maxRetries tak dobara, phir agla
     */
    fun run(
        order: List<Backend>,
        req: RouterRequest,
        maxRetries: Int = 2,
        stop: () -> Boolean = { false }
    ): RouteResult {
        val failed = ArrayList<String>()
        val notes = ArrayList<String>()
        var last: Outcome? = null

        for (b in order) {
            if (stop()) break
            if (health.inCooldown(b.id)) {
                notes.add(b.label + " abhi rest par hai (" + health.reasonOf(b.id) + ")")
                continue
            }
            var attempt = 0
            while (true) {
                attempt++
                val o = b.call(req)
                if (o.ok) {
                    health.clear(b.id)
                    return RouteResult(
                        true, o.text, b, failed, ErrKind.NONE, "",
                        o.sources, o.searchUsed, o.searchNote, notes
                    )
                }
                last = o
                val retryable = o.kind == ErrKind.SERVER || o.kind == ErrKind.EMPTY ||
                    o.kind == ErrKind.NETWORK
                if (retryable && attempt <= maxRetries && !stop()) {
                    sleep(500L * attempt)
                    continue
                }
                val cd = cooldownFor(o)
                health.cooldown(b.id, cd.first, cd.second)
                failed.add(b.label + " (" + shortReason(o.kind) + ")")
                break
            }
        }
        val l = last
        return RouteResult(
            false, "", null, failed,
            l?.kind ?: ErrKind.NONE, l?.text ?: "", emptyList(), false, "", notes
        )
    }
}
