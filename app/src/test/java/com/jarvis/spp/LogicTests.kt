package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArithmeticCheckerTest {
    @Test fun evalBasics() {
        assertEquals(14.0, ArithmeticChecker.eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, ArithmeticChecker.eval("(2+3)*4")!!, 1e-9)
        assertEquals(2.5, ArithmeticChecker.eval("10/4")!!, 1e-9)
        assertNull(ArithmeticChecker.eval("1/0"))
        assertNull(ArithmeticChecker.eval("2 3"))
    }

    @Test fun correctAnswersPass() {
        assertTrue(ArithmeticChecker.find("12 x 3 = 36").isEmpty())
        assertTrue(ArithmeticChecker.find("1,000 + 500 = 1500").isEmpty())
        assertTrue(ArithmeticChecker.find("10 / 3 = 3.33").isEmpty())
        assertTrue(ArithmeticChecker.find("Step 1. 2 + 3 = 5").isEmpty())
        assertTrue(ArithmeticChecker.find("2 + 3 = 5 + 1").isEmpty())
        assertTrue(ArithmeticChecker.find("hello world").isEmpty())
    }

    @Test fun wrongAnswersCaught() {
        val r = ArithmeticChecker.find("17 × 3 = 41")
        assertEquals(1, r.size)
        assertEquals(51.0, r[0].actual, 1e-9)
        assertEquals(41.0, r[0].claimed, 1e-9)
        assertEquals(1, ArithmeticChecker.find("7 / 2 = 3").size)
    }
}

class HonestyTest {
    @Test fun thinkStripped() {
        assertEquals("Namaste", ThinkStripper.strip("<think>soch raha hun</think>Namaste"))
        assertEquals("", ThinkStripper.strip("<think>abhi soch rahi hun"))
        assertEquals("Jawab", ThinkStripper.strip("khayal</think>Jawab"))
        assertEquals("Hi", ThinkStripper.strip("Hi<thi"))
    }

    @Test fun speechSkipsSourcesAndNotices() {
        val t = "Hello\n\nSources:\n1. x - https://a.com\n\n\u26A0\uFE0F note"
        assertEquals("Hello", Honesty.forSpeech(t))
        assertEquals("Dekho (code chat mein likha hai) bas", Honesty.forSpeech("Dekho ```print(1)``` bas").replace("  ", " "))
    }

    @Test fun markdownCleanedButCodeKept() {
        val t = "**Bold** baat\n# Heading\n* item\n```\n**keep**\n```"
        val c = Honesty.cleanMarkdown(t)
        assertTrue(c.contains("Bold baat"))
        assertFalse(c.contains("# Heading"))
        assertTrue(c.contains("\u2022 item"))
        assertTrue(c.contains("**keep**"))
    }

    @Test fun correctionDetected() {
        assertTrue(Honesty.isUserCorrection("ye galat hai"))
        assertTrue(Honesty.isUserCorrection("That is wrong"))
        assertFalse(Honesty.isUserCorrection("aaj mausam kaisa hai"))
    }

    @Test fun correctionDetectedMoreWays() {
        assertTrue(Honesty.isUserCorrection("Jarvi, tumne mujhe galat jawab kyon diya?"))
        assertTrue(Honesty.isUserCorrection("nahi nahi, aisa nahi hai"))
        assertFalse(Honesty.isUserCorrection("kal subah 6 baje alarm laga do"))
        // lamba message correction nahi maana jata
        assertFalse(Honesty.isUserCorrection("galat ".repeat(60)))
    }

    @Test fun lastExchangeFindsLatestPair() {
        val h = listOf(
            Pair("user", "q1"), Pair("model", "a1"),
            Pair("user", "q2"), Pair("model", "a2")
        )
        val ex = Honesty.lastExchange(h)
        assertEquals("q2", ex?.user)
        assertEquals("a2", ex?.answer)
        assertNull(Honesty.lastExchange(emptyList()))
        assertNull(Honesty.lastExchange(listOf(Pair("user", "sirf sawaal"))))
    }

    @Test fun correctionRequestCarriesPreviousAnswer() {
        val note = Honesty.correctionRequest(Honesty.Exchange("2+2 kitna?", "5 hota hai"))
        assertTrue(note.contains("2+2 kitna?"))
        assertTrue(note.contains("5 hota hai"))
        // pichla jawab na mile tab bhi note bane
        assertTrue(Honesty.correctionRequest(null).contains("NOTE"))
    }

    @Test fun spokenTextIsClean() {
        assertEquals("Dekho link bas", Honesty.spokenText("Dekho https://a.com/x bas"))
        assertEquals("Line one. Line two", Honesty.spokenText("Line one\nLine two"))
        assertEquals("Bold tag ok", Honesty.spokenText("**Bold** #tag \uD83D\uDE00 ok"))
        assertEquals("", Honesty.spokenText("\uD83D\uDE00"))
        assertEquals("Hello", Honesty.spokenText("Hello\n\nSources:\n1. A - https://a.com"))
    }

    @Test fun sourcesBlockDedupes() {
        val b = Honesty.sourcesBlock(listOf(Source("A", "https://a.com"), Source("A2", "https://a.com"), Source("B", "https://b.com")))
        assertTrue(b.startsWith("\n\nSources:"))
        assertEquals(2, b.lines().count { it.matches(Regex("^[0-9]+\\. .*")) })
        assertEquals("", Honesty.sourcesBlock(emptyList()))
    }
}

class PastChatsTest {
    @Test fun picksRelatedPastExchange() {
        val chats = listOf(
            listOf(
                ChatMessage("mere dost se jhagda ho gaya office project par", true),
                ChatMessage("Kya hua tha? Bahas kis baat par hui?", false),
                ChatMessage("aaj mausam kaisa hai", true),
                ChatMessage("Dhoop khili hai", false)
            )
        )
        val r = PastChats.pick("office project dost ka jhagda kaise suljhaun", chats, 2)
        assertEquals(1, r.size)
        assertTrue(r[0].contains("office project"))
        assertTrue(r[0].contains("Bahas"))
    }

    @Test fun ignoresSensitiveAndUnrelated() {
        val chats = listOf(
            listOf(
                ChatMessage("mera OTP 1234 hai office project", true),
                ChatMessage("ok", false)
            )
        )
        assertTrue(PastChats.pick("office project kaisa chal raha hai", chats, 2).isEmpty())
        assertTrue(PastChats.pick("hello", chats, 2).isEmpty())
    }
}

class MemoryLogicTest {
    @Test fun sensitiveDetected() {
        assertTrue(SensitiveFilter.isSensitive("mera number 9876543210 hai"))
        assertTrue(SensitiveFilter.isSensitive("mera OTP 1234 hai"))
        assertTrue(SensitiveFilter.isSensitive("email me at a@b.com"))
        assertFalse(SensitiveFilter.isSensitive("main chai pasand karta hun"))
    }

    @Test fun rankerPicksRelevant() {
        val now = System.currentTimeMillis()
        val list = listOf(
            MemoryEntry("1", "User ko chhote jawab pasand hain", "pref", now, true),
            MemoryEntry("2", "User ka naam Rahul hai", "fact", now, true),
            MemoryEntry("3", "Android app Jarvi bana raha hai", "project", now, true)
        )
        val r = MemoryRanker.rank("mera android app kaisa chal raha hai", list, 2)
        assertEquals("project", r[0].kind)
        assertFalse(r.any { it.id == "2" })
    }

    @Test fun explicitMemoryExtract() {
        assertEquals("main Delhi mein rehta hun", ExplicitMemory.extract("yaad rakho ki main Delhi mein rehta hun"))
        assertEquals("I like tea", ExplicitMemory.extract("remember that I like tea"))
        assertNull(ExplicitMemory.extract("hello"))
    }
}

class TaskAndStyleTest {
    @Test fun classify() {
        assertEquals(Task.SEARCH, TaskClassifier.classify("aaj ka mausam batao"))
        assertEquals(Task.PHONE, TaskClassifier.classify("whatsapp kholo"))
        assertEquals(Task.CODE, TaskClassifier.classify("python code likho for sorting"))
        assertEquals(Task.CHAT, TaskClassifier.classify("hello"))
        assertEquals(Task.SUMMARIZE, TaskClassifier.classify("summarize this text"))
        assertEquals(Task.REASONING, TaskClassifier.classify("12 + 15 kitna hota hai"))
        assertEquals(Task.VISION, TaskClassifier.classify("ye kya hai", hasImage = true))
    }

    @Test fun styleLearns() {
        val s = StyleLogic.detect("chhota jawab do", ReplyStyle.DEFAULT)
        assertEquals("short", s.length)
        assertEquals("hi", StyleLogic.detect("hindi mein bolo", s).lang)
        assertSame(s, StyleLogic.detect("aaj mausam kaisa hai", s))
        assertEquals("normal", StyleLogic.detect("normal jawab", s).length)
    }

    @Test fun trimmerKeepsLastAndStartsWithUser() {
        val h = listOf(Pair("user", "aaaa"), Pair("model", "bbbb"), Pair("user", "cccc"))
        assertEquals(3, ContextTrimmer.trim(h, 1000).size)
        val t = ContextTrimmer.trim(h, 3)
        assertEquals(1, t.size)
        assertEquals("cccc", t[0].second)
        assertEquals("user", ContextTrimmer.trim(h.drop(1), 1000)[0].first)
    }

    @Test fun modelFit() {
        val m = LocalModel("x", "X", "1B", 1000, 2000, 4000, "https://x", "x.litertlm", "", "", "", false, false, "")
        fun snap(total: Long, avail: Long, free: Long, abis: List<String> = listOf("arm64-v8a")) =
            DeviceSnapshot(total, avail, false, free, 34, abis, 80, false, 30f, false)
        assertEquals(Fit.OK, ModelFit.evaluate(m, snap(6000, 3000, 5000), false).fit)
        assertEquals(Fit.TOO_BIG, ModelFit.evaluate(m, snap(3000, 2000, 5000), false).fit)
        assertEquals(Fit.TIGHT, ModelFit.evaluate(m, snap(6000, 1000, 5000), false).fit)
        assertEquals(Fit.NO_STORAGE, ModelFit.evaluate(m, snap(6000, 3000, 500), false).fit)
        assertEquals(Fit.UNSUPPORTED, ModelFit.evaluate(m, snap(6000, 3000, 5000, listOf("armeabi-v7a")), false).fit)
        assertTrue(ModelFit.shouldAvoidLocal(DeviceSnapshot(6000, 3000, false, 5000, 34, listOf("arm64-v8a"), 80, false, 45f, false)))
        assertFalse(ModelFit.shouldAvoidLocal(snap(6000, 3000, 5000)))
    }
}
