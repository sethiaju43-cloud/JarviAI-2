package com.jarvis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local (offline) models ki list. Sab .litertlm format ke hain (LiteRT-LM runtime).
 * Size/URL Hugging Face litert-community ke pages se liye gaye hain.
 * RAM ke aankde andaaze hain (HF benchmarks par based), asli phone par alag ho sakte hain.
 */
object LocalModelCatalog {

    val builtIn: List<LocalModel> = listOf(
        LocalModel(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B (sabse halka)",
            params = "0.6B", sizeMb = 586, peakRamMb = 1000, minTotalRamMb = 3000,
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            fileName = "Qwen3-0.6B.litertlm",
            languages = "English achhi; Hindi/Hinglish basic (galtiyan ho sakti hain)",
            strengths = "Tez, kam RAM. Chhoti baat-cheet, rewrite, saral sawaal",
            license = "Apache-2.0", experimental = false, gated = false,
            note = "Isme 'sochne' wala text hota hai jo app khud hata deta hai."
        ),
        LocalModel(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B Instruct",
            params = "1.5B", sizeMb = 1600, peakRamMb = 2300, minTotalRamMb = 4000,
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Qwen2.5-1.5B-Instruct_q8_ekv4096.litertlm",
            languages = "English aur Chinese mazboot; Hindi/Hinglish basic",
            strengths = "Balanced: summary, rewrite, simple reasoning aur coding",
            license = "Apache-2.0", experimental = false, gated = false,
            note = "int8 quantized, 4096 context."
        ),
        LocalModel(
            id = "qwen2.5-coder-1.5b",
            name = "Qwen2.5 Coder 1.5B (coding)",
            params = "1.5B", sizeMb = 1120, peakRamMb = 1900, minTotalRamMb = 4000,
            url = "https://huggingface.co/litert-community/Qwen2.5-Coder-1.5B-Instruct/resolve/main/Qwen2.5-Coder-1.5B-Instruct_int4.litertlm",
            fileName = "Qwen2.5-Coder-1.5B-Instruct_int4.litertlm",
            languages = "Code + English; Hindi kamzor",
            strengths = "Chhote code snippets, error samjhana. Bada code bharosemand nahi",
            license = "Apache-2.0", experimental = false, gated = false,
            note = "int4 quantized. LiteRT-LM 0.16+ chahiye (app mein hai)."
        ),
        LocalModel(
            id = "gemma-4-e2b",
            name = "Gemma 4 E2B (best quality, bhaari)",
            params = "~2B effective", sizeMb = 2590, peakRamMb = 2600, minTotalRamMb = 5500,
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            languages = "Multilingual family (Hindi samet). Asli quality khud test karo",
            strengths = "In sab mein sabse behtar reasoning/baat-cheet; 6 GB par thoda tight",
            license = "Apache-2.0", experimental = false, gated = false,
            note = "Weights memory-map hote hain, isliye RAM file size se kam lagti hai. CPU par 6 GB phone par dheema ho sakta hai."
        ),
        LocalModel(
            id = "gemma-4-e4b",
            name = "Gemma 4 E4B (EXPERIMENTAL)",
            params = "~4B effective", sizeMb = 3650, peakRamMb = 3500, minTotalRamMb = 6000,
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            languages = "Multilingual family",
            strengths = "Sabse capable, par 6 GB RAM par chalne ki guarantee NAHI hai",
            license = "Apache-2.0", experimental = true, gated = false,
            note = "File ka naam E2B ke pattern par liya hai (verify nahi hua). Download fail ho to 'Custom model' se sahi URL daalo."
        )
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("jarvis_models", Context.MODE_PRIVATE)

    fun customModels(ctx: Context): List<LocalModel> {
        val out = ArrayList<LocalModel>()
        try {
            val arr = JSONArray(prefs(ctx).getString("custom", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    LocalModel(
                        id = o.getString("id"), name = o.getString("name"), params = "?",
                        sizeMb = 0, peakRamMb = o.optInt("ram", 2500), minTotalRamMb = 4000,
                        url = o.getString("url"), fileName = o.getString("file"),
                        languages = "Pata nahi (khud test karo)", strengths = "Aapka apna model",
                        license = "Khud check karo", experimental = true, gated = o.optBoolean("gated", false),
                        note = "Custom model. Size download ke waqt pata chalega.", custom = true
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun addCustom(ctx: Context, name: String, url: String, gated: Boolean): String? {
        if (!Net.isHttps(url)) return "URL https:// se shuru hona chahiye"
        var file = url.substringBefore("?").substringAfterLast("/")
        if (!file.endsWith(".litertlm")) return "Sirf .litertlm file chalti hai"
        file = file.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val list = ArrayList(customModels(ctx))
        if (list.any { it.fileName == file }) return "Ye file pehle se list mein hai"
        val arr = JSONArray()
        for (m in list) {
            arr.put(JSONObject().put("id", m.id).put("name", m.name).put("url", m.url)
                .put("file", m.fileName).put("gated", m.gated).put("ram", m.peakRamMb))
        }
        arr.put(JSONObject().put("id", "custom-" + file).put("name", name.ifBlank { file })
            .put("url", url.trim()).put("file", file).put("gated", gated).put("ram", 2500))
        prefs(ctx).edit().putString("custom", arr.toString()).apply()
        return null
    }

    fun removeCustom(ctx: Context, id: String) {
        val arr = JSONArray()
        for (m in customModels(ctx)) {
            if (m.id == id) continue
            arr.put(JSONObject().put("id", m.id).put("name", m.name).put("url", m.url)
                .put("file", m.fileName).put("gated", m.gated).put("ram", m.peakRamMb))
        }
        prefs(ctx).edit().putString("custom", arr.toString()).apply()
    }

    fun all(ctx: Context): List<LocalModel> = builtIn + customModels(ctx)

    fun find(ctx: Context, id: String): LocalModel? = all(ctx).firstOrNull { it.id == id }
}
