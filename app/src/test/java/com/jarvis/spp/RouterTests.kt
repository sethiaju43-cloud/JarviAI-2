package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBackend(
    override val id: String,
    override val isLocal: Boolean = false,
    override val supportsVision: Boolean = false,
    override val supportsSearch: Boolean = false,
    override val capability: Int = 2,
    private val script: List<Outcome> = listOf(Outcome(true, "ok-$id"))
) : Backend {
    var calls = 0
    override val label: String get() = "L-$id"
    override fun call(req: RouterRequest): Outcome {
        val o = script[minOf(calls, script.size - 1)]
        calls++
        return o
    }
}

private fun req(task: Task = Task.CHAT) =
    RouterRequest(task, listOf(Pair("user", "hi")), "s", "s")

class RoutePlannerTest {
    private val gemini = FakeBackend("gemini", supportsSearch = true, supportsVision = true, capability = 2)
    private val groq = FakeBackend("groq", capability = 5)
    private val local = FakeBackend("local", isLocal = true)
    private val ons = listOf<Backend>(gemini, groq)

    private fun ids(l: List<Backend>) = l.map { it.id }

    @Test fun autoOnline() {
        assertEquals(listOf("groq", "gemini", "local"),
            ids(RoutePlanner.order(Task.REASONING, RouteModes.AUTO, true, ons, local, false, false)))
        assertEquals(listOf("gemini", "groq", "local"),
            ids(RoutePlanner.order(Task.CHAT, RouteModes.AUTO, true, ons, local, false, false)))
        assertEquals(listOf("gemini", "groq", "local"),
            ids(RoutePlanner.order(Task.SEARCH, RouteModes.AUTO, true, ons, local, false, false)))
    }

    @Test fun offlineUsesLocal() {
        assertEquals(listOf("local"), ids(RoutePlanner.order(Task.CHAT, RouteModes.AUTO, false, ons, local, false, false)))
        assertTrue(RoutePlanner.order(Task.CHAT, RouteModes.AUTO, false, ons, null, false, false).isEmpty())
    }

    @Test fun modes() {
        assertEquals(listOf("local", "gemini", "groq"),
            ids(RoutePlanner.order(Task.CHAT, RouteModes.LOCAL_FIRST, true, ons, local, false, false)))
        assertEquals(listOf("gemini", "groq", "local"),
            ids(RoutePlanner.order(Task.CHAT, RouteModes.LOCAL_FIRST, true, ons, local, true, false)))
        assertEquals(listOf("gemini", "groq", "local"),
            ids(RoutePlanner.order(Task.SEARCH, RouteModes.LOCAL_FIRST, true, ons, local, false, false)))
        assertEquals(listOf("local"), ids(RoutePlanner.order(Task.CHAT, RouteModes.LOCAL_ONLY, true, ons, local, false, false)))
        assertTrue(RoutePlanner.order(Task.CHAT, RouteModes.ONLINE, false, ons, local, false, false).isEmpty())
    }

    @Test fun visionOnlyCapableOnline() {
        assertEquals(listOf("gemini"), ids(RoutePlanner.order(Task.VISION, RouteModes.AUTO, true, ons, local, false, true)))
    }
}

class RouterCoreTest {
    private var now = 0L
    private val health = HealthTracker { now }
    private var slept = 0
    private val core = RouterCore(health) { slept++ }

    @Test fun firstSuccess() {
        val a = FakeBackend("a")
        val r = core.run(listOf(a), req())
        assertTrue(r.ok)
        assertEquals("ok-a", r.text)
        assertFalse(r.switched)
    }

    @Test fun rateLimitSwitchesAndCoolsDown() {
        val a = FakeBackend("a", script = listOf(Outcome(false, "429", ErrKind.RATE_LIMIT, 60)))
        val b = FakeBackend("b")
        val r = core.run(listOf(a, b), req())
        assertTrue(r.ok)
        assertTrue(r.switched)
        assertEquals(1, a.calls)
        assertTrue(health.inCooldown("a"))
        // cooldown ke dauran a ko chhua nahi jata
        core.run(listOf(a, b), req())
        assertEquals(1, a.calls)
        now += 120_000
        core.run(listOf(a, b), req())
        assertEquals(2, a.calls)
    }

    @Test fun serverErrorRetriesThenMovesOn() {
        val a = FakeBackend("a", script = listOf(Outcome(false, "boom", ErrKind.SERVER)))
        val b = FakeBackend("b")
        val r = core.run(listOf(a, b), req())
        assertTrue(r.ok)
        assertEquals(3, a.calls)
        assertEquals(2, slept)
    }

    @Test fun transientThenSuccessNoSwitch() {
        val a = FakeBackend("a", script = listOf(Outcome(false, "x", ErrKind.EMPTY), Outcome(true, "fine")))
        val r = core.run(listOf(a), req())
        assertTrue(r.ok)
        assertFalse(r.switched)
        assertEquals(2, a.calls)
    }

    @Test fun authErrorNoRetry() {
        val a = FakeBackend("a", script = listOf(Outcome(false, "bad key", ErrKind.AUTH)))
        val r = core.run(listOf(a), req())
        assertFalse(r.ok)
        assertEquals(1, a.calls)
        assertEquals(ErrKind.AUTH, r.failKind)
    }

    @Test fun onlineFailsFallsBackToLocal() {
        val a = FakeBackend("a", script = listOf(Outcome(false, "net", ErrKind.NETWORK)))
        val l = FakeBackend("local", isLocal = true)
        val r = core.run(listOf(a, l), req())
        assertTrue(r.ok)
        assertTrue(r.backend!!.isLocal)
    }
}

class ParsersTest {
    @Test fun classifyErrors() {
        assertEquals(ErrKind.RATE_LIMIT, ErrorClassifier.classify(429, "Too many requests"))
        assertEquals(ErrKind.QUOTA, ErrorClassifier.classify(429, "You exceeded your current quota"))
        assertEquals(ErrKind.AUTH, ErrorClassifier.classify(401, ""))
        assertEquals(ErrKind.AUTH, ErrorClassifier.classify(400, "API key not valid"))
        assertEquals(ErrKind.SERVER, ErrorClassifier.classify(503, ""))
        assertEquals(ErrKind.NETWORK, ErrorClassifier.classify(0, ""))
    }

    @Test fun modelRank() {
        assertTrue(ModelRank.score("llama-3.3-70b-versatile") > ModelRank.score("llama-3.1-8b-instant"))
        assertTrue(ModelRank.score("gemini-3.8-flash") > ModelRank.score("gemini-3.5-flash-lite"))
    }

    @Test fun openAiParse() {
        val raw = """{"choices":[{"message":{"role":"assistant","content":"<think>x</think>Namaste"}}]}"""
        assertEquals("Namaste", OnlineParsers.parseOpenAi(raw))
        assertEquals("", OnlineParsers.parseOpenAi("{}"))
    }

    @Test fun geminiParseWithSources() {
        val raw = """{"candidates":[{"content":{"parts":[{"text":"Hello "},{"text":"world"}]},
            "groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://a.com","title":"A"}}]}}]}"""
        val p = OnlineParsers.parseGemini(raw)
        assertEquals("Hello world", p.first)
        assertEquals(1, p.second.size)
        assertEquals("https://a.com", p.second[0].url)
    }

    @Test fun errorMessageRedactsKeys() {
        val raw = """{"error":{"message":"Bad key sk-abcdefghijklmnopqrstuvwxyz123"}}"""
        val m = OnlineParsers.errorMessage(raw, 401)
        assertTrue(m.contains("***"))
        assertFalse(m.contains("abcdefghijkl"))
        assertEquals("Error 500", OnlineParsers.errorMessage("not json", 500))
    }

    @Test fun modelLists() {
        assertEquals(listOf("a", "b"), OnlineParsers.parseOpenAiModels("""{"data":[{"id":"a"},{"id":"b"}]}"""))
        val g = """{"models":[{"name":"models/g1","supportedGenerationMethods":["generateContent"]},
            {"name":"models/emb","supportedGenerationMethods":["embedContent"]}]}"""
        assertEquals(listOf("g1"), OnlineParsers.parseGeminiModels(g))
    }

    @Test fun openAiMessagesWithImage() {
        val m = OnlineParsers.buildOpenAiMessages("sys", listOf(Pair("user", "hi"), Pair("model", "yo"), Pair("user", "pic")), "B64")
        assertEquals(4, m.length())
        assertEquals("assistant", m.getJSONObject(2).getString("role"))
        assertTrue(m.getJSONObject(3).getJSONArray("content").length() == 2)
    }

    @Test fun searchParsers() {
        val t = SearchFormat.parseTavily("""{"results":[{"title":"T","url":"https://t.com","content":"c"}]}""")
        assertEquals("https://t.com", t[0].url)
        val br = SearchFormat.parseBrave("""{"web":{"results":[{"title":"<b>B</b>","url":"https://b.com","description":"d &amp; e"}]}}""")
        assertEquals("B", br[0].title)
        assertEquals("d & e", br[0].snippet)
        val w = SearchFormat.parseWikipedia("""{"query":{"search":[{"title":"New Delhi","snippet":"<span>cap</span>"}]}}""", "en")
        assertEquals("https://en.wikipedia.org/wiki/New_Delhi", w[0].url)
        assertTrue(SearchFormat.promptBlock(t).startsWith("[1] T"))
    }
}
