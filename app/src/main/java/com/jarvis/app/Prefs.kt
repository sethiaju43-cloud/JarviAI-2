package com.jarvis.app

import android.content.Context
import android.content.SharedPreferences

/** Saari chhoti settings ek jagah ("jarvis" SharedPreferences) */
object Prefs {
    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    // Jinka default ON rakhna hai (user ne kabhi badla na ho to). Baat-cheet mode: Jarvi bolne ke baad khud sunti hai.
    private val keyDefaults = mapOf("voice_chat_mode" to true)

    fun bool(ctx: Context, key: String, def: Boolean) = sp(ctx).getBoolean(key, keyDefaults[key] ?: def)
    fun setBool(ctx: Context, key: String, v: Boolean) = sp(ctx).edit().putBoolean(key, v).apply()
    fun str(ctx: Context, key: String, def: String) = sp(ctx).getString(key, def) ?: def
    fun setStr(ctx: Context, key: String, v: String) = sp(ctx).edit().putString(key, v).apply()
    fun int(ctx: Context, key: String, def: Int) = sp(ctx).getInt(key, def)
    fun setInt(ctx: Context, key: String, v: Int) = sp(ctx).edit().putInt(key, v).apply()

    fun routeMode(ctx: Context) = str(ctx, "route_mode", RouteModes.AUTO)
    fun localModelId(ctx: Context) = str(ctx, "local_model_id", "")
    fun idleUnloadMin(ctx: Context) = int(ctx, "local_idle_min", 3)
    fun localGpu(ctx: Context) = bool(ctx, "local_gpu", false)
    fun memoryEnabled(ctx: Context) = bool(ctx, "memory_enabled", true)
    fun doubleCheck(ctx: Context) = bool(ctx, "double_check", false)
    fun speakReplies(ctx: Context) = bool(ctx, "speak_replies", true)
    fun voiceAutoSend(ctx: Context) = bool(ctx, "voice_autosend", false)
    fun voiceOffline(ctx: Context) = bool(ctx, "voice_offline", false)
    fun screenToAi(ctx: Context) = bool(ctx, "screen_to_ai", true)
    fun batterySaver(ctx: Context) = bool(ctx, "battery_saver", true)
    fun callJaan(ctx: Context) = bool(ctx, "call_jaan", true)
    fun searchProvider(ctx: Context) = str(ctx, "search_provider", "auto")

    /** AI ka naam (user badal sakta hai). Khali ho to Jarvi. */
    fun aiName(ctx: Context): String = str(ctx, "ai_name", "Jarvi").trim().ifBlank { "Jarvi" }
    fun setAiName(ctx: Context, v: String) = setStr(ctx, "ai_name", v.trim())

    fun style(ctx: Context): ReplyStyle = ReplyStyle(
        str(ctx, "style_len", "normal"), str(ctx, "style_lang", "auto"), bool(ctx, "style_simple", false)
    )

    fun saveStyle(ctx: Context, s: ReplyStyle) {
        sp(ctx).edit().putString("style_len", s.length).putString("style_lang", s.lang)
            .putBoolean("style_simple", s.simple).apply()
    }
}
