package com.jarvis.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phone par chalne wala local (offline) AI. Ek samay mein sirf EK model memory mein.
 * Engine LiteRT-LM (Google) ka hai. Kabhi bhi Throwable pakadte hain taaki app na gire.
 */
object LocalLlm {

    /** stopped = user ne "Ruko" bola aur jawab beech mein roka gaya (text mein ab tak ka hissa) */
    class Result(val ok: Boolean, val text: String, val error: String = "", val stopped: Boolean = false)

    private val lock = Any()
    private var engine: Engine? = null
    private var loadedId: String? = null

    @Volatile
    private var lastUse = 0L

    @Volatile
    private var busy: CountDownLatch? = null

    private val main = Handler(Looper.getMainLooper())

    fun loadedModelId(): String? = loadedId
    fun isLoaded(): Boolean = engine != null
    fun isBusy(): Boolean = busy != null

    /** Settings mein chuna hua aur installed model (nahi to koi bhi installed model) */
    fun activeModel(ctx: Context): LocalModel? {
        val all = LocalModelCatalog.all(ctx)
        val chosen = Prefs.localModelId(ctx)
        val m = all.firstOrNull { it.id == chosen }
        if (m != null && ModelDownloader.isInstalled(ctx, m)) return m
        return all.firstOrNull { ModelDownloader.isInstalled(ctx, it) }
    }

    fun unload(reason: String) {
        synchronized(lock) {
            if (busy != null) return
            try {
                engine?.close()
            } catch (t: Throwable) {
            }
            engine = null
            loadedId = null
        }
    }

    /** Memory kam ho to model hata do (JarvisApp.onTrimMemory se) */
    fun onTrim(level: Int) {
        if (level == 15 || level >= 40) unload("memory kam")
    }

    private fun scheduleIdle(ctx: Context) {
        val min = Prefs.idleUnloadMin(ctx)
        main.removeCallbacksAndMessages(null)
        if (min <= 0) return
        main.postDelayed({
            if (System.currentTimeMillis() - lastUse >= min * 60_000L - 500) unload("idle")
            else scheduleIdle(ctx)
        }, min * 60_000L)
    }

    /** Model load karo (background thread par bulao). Memory check pehle hota hai. */
    fun load(ctx: Context, m: LocalModel): Result {
        synchronized(lock) {
            if (engine != null && loadedId == m.id) return Result(true, "")
            if (busy != null) return Result(false, "", "Local model abhi kaam mein hai, thodi der baad try karo")
            try {
                engine?.close()
            } catch (t: Throwable) {
            }
            engine = null
            loadedId = null

            val file = ModelDownloader.fileFor(ctx, m)
            if (!file.exists()) return Result(false, "", "Model file nahi mili. Pehle download karo.")
            val snap = DeviceInfo.snapshot(ctx)
            val fit = ModelFit.evaluate(m, snap, true)
            if (fit.fit == Fit.UNSUPPORTED || fit.fit == Fit.TOO_BIG) return Result(false, "", fit.message)
            if (snap.availRamMb < m.peakRamMb * 0.8) {
                return Result(false, "", "RAM kam hai (" + snap.availRamMb + " MB khali, ~" + m.peakRamMb + " MB chahiye). Doosre apps band karo.")
            }
            try {
                val backend = if (Prefs.localGpu(ctx)) Backend.GPU() else Backend.CPU()
                val cfg = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = backend,
                    cacheDir = ctx.cacheDir.absolutePath
                )
                val e = Engine(cfg)
                e.initialize()
                engine = e
                loadedId = m.id
                lastUse = System.currentTimeMillis()
                scheduleIdle(ctx.applicationContext)
                return Result(true, "")
            } catch (t: Throwable) {
                engine = null
                loadedId = null
                val oom = t is OutOfMemoryError
                return Result(
                    false, "",
                    if (oom) "Memory khatam ho gayi. Chhota model chuno ya doosre apps band karo."
                    else "Model load nahi hua: " + OnlineParsers.redact(t.message ?: t.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * Chalte hue jawab ko rokne ki koshish. LiteRT-LM ke Conversation mein rokne ka tareeka
     * (cancelProcess) hota hai; naam reflection se dhundte hain taaki library ka version
     * badalne par app compile hona band na ho.
     */
    private fun cancelConversation(conv: Any?) {
        if (conv == null) return
        for (name in listOf("cancelProcess", "cancel")) {
            try {
                conv.javaClass.getMethod(name).invoke(conv)
                return
            } catch (t: Throwable) {
            }
        }
    }

    /**
     * Jawab banao. onPartial streaming ke liye (thinking hata kar). Background thread par bulao.
     * history: ("user"|"model", text) - abhi ka user message ALAG se `user` mein.
     * shouldStop: true lautaye to jawab beech mein rok diya jata hai (Result.stopped = true).
     */
    fun generate(
        ctx: Context,
        m: LocalModel,
        system: String,
        history: List<Pair<String, String>>,
        user: String,
        onPartial: ((String) -> Unit)?,
        shouldStop: () -> Boolean = { false }
    ): Result {
        val ld = load(ctx, m)
        if (!ld.ok) return ld
        val eng = synchronized(lock) { engine } ?: return Result(false, "", "Model load nahi hai")

        val latch = CountDownLatch(1)
        busy = latch
        lastUse = System.currentTimeMillis()
        var conv: com.google.ai.edge.litertlm.Conversation? = null
        try {
            val initial = ArrayList<Message>()
            for (h in history) {
                if (h.first == "user") initial.add(Message.user(h.second)) else initial.add(Message.model(h.second))
            }
            val cfg = ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = initial,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
            )
            conv = eng.createConversation(cfg)
            // Qwen3 pehle lamba "sochta" hai, chhote phone par isme minute lag jaate hain.
            // "/no_think" lagane se wo seedha jawab deta hai.
            val sendText = if (m.id.contains("qwen3", true) || m.name.contains("qwen3", true)) {
                user + " /no_think"
            } else user
            ModelRouter.onStatus?.invoke("Local model jawab bana raha hai (offline mein thoda dheema hota hai)...")
            val sb = StringBuilder()
            var err: Throwable? = null
            val done = CountDownLatch(1)
            val halted = AtomicBoolean(false)
            conv.sendMessageAsync(sendText, object : MessageCallback {
                override fun onMessage(message: Message) {
                    sb.append(message.toString())
                    if (!halted.get()) onPartial?.invoke(ThinkStripper.strip(sb.toString()))
                }

                override fun onDone() {
                    done.countDown()
                }

                override fun onError(throwable: Throwable) {
                    err = throwable
                    done.countDown()
                }
            })
            // Dheeme CPU par bhi 4 minute se zyada nahi rukenge.
            // Har 0.2 second par dekhte hain ki user ne "Ruko" to nahi bola.
            val deadline = System.currentTimeMillis() + 240_000L
            var finished = false
            var stopped = false
            while (true) {
                if (done.await(200, TimeUnit.MILLISECONDS)) {
                    finished = true
                    break
                }
                if (shouldStop()) {
                    stopped = true
                    break
                }
                if (System.currentTimeMillis() >= deadline) break
            }
            if (stopped) {
                halted.set(true)
                cancelConversation(conv)
                // Rokne ko thoda waqt do, tab tak conversation band mat karo
                try {
                    done.await(3, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                }
                val partial = ThinkStripper.strip(sb.toString())
                return Result(true, if (partial.isBlank()) "Ruk gayi 🛑" else partial, "", true)
            }
            val text = ThinkStripper.strip(sb.toString())
            val e = err
            return when {
                e != null && text.isBlank() ->
                    Result(false, "", "Local model error: " + OnlineParsers.redact(e.message ?: e.javaClass.simpleName))
                !finished && text.isBlank() -> Result(false, "", "Local model ne time se jawab nahi diya")
                text.isBlank() -> Result(false, "", "Local model ka jawab khali aaya")
                else -> Result(true, if (finished) text else text + " ...")
            }
        } catch (t: Throwable) {
            val oom = t is OutOfMemoryError
            if (oom) unloadForce()
            return Result(
                false, "",
                if (oom) "Memory khatam ho gayi (local model hata diya gaya)."
                else "Local model error: " + OnlineParsers.redact(t.message ?: t.javaClass.simpleName)
            )
        } finally {
            try {
                conv?.close()
            } catch (t: Throwable) {
            }
            busy = null
            latch.countDown()
            lastUse = System.currentTimeMillis()
        }
    }

    private fun unloadForce() {
        synchronized(lock) {
            try {
                engine?.close()
            } catch (t: Throwable) {
            }
            engine = null
            loadedId = null
        }
    }
}
