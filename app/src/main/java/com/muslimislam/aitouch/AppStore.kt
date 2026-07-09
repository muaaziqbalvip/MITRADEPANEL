package com.muslimislam.aitouch

import android.content.Context
import org.json.JSONArray

/**
 * Simple persistent storage using SharedPreferences.
 * Keeps: list of dots, the ONE-TIME saved AI instruction prompt,
 * and the HF Space backend URL.
 */
object AppStore {
    private const val PREFS = "ai_touch_prefs"
    private const val KEY_DOTS = "dots"
    private const val KEY_PROMPT = "saved_prompt"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_GROQ_KEY = "groq_key"

    fun saveDots(ctx: Context, dots: List<TouchDot>) {
        val arr = JSONArray()
        dots.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_DOTS, arr.toString()).apply()
    }

    fun loadDots(ctx: Context): MutableList<TouchDot> {
        val raw = prefs(ctx).getString(KEY_DOTS, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<TouchDot>()
        for (i in 0 until arr.length()) {
            list.add(TouchDot.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun savePrompt(ctx: Context, prompt: String) {
        prefs(ctx).edit().putString(KEY_PROMPT, prompt).apply()
    }

    fun loadPrompt(ctx: Context): String {
        return prefs(ctx).getString(KEY_PROMPT, "") ?: ""
    }

    fun saveBackendUrl(ctx: Context, url: String) {
        prefs(ctx).edit().putString(KEY_BACKEND_URL, url).apply()
    }

    fun loadBackendUrl(ctx: Context): String {
        return prefs(ctx).getString(KEY_BACKEND_URL, "") ?: ""
    }

    fun saveGroqKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(KEY_GROQ_KEY, key).apply()
    }

    fun loadGroqKey(ctx: Context): String {
        return prefs(ctx).getString(KEY_GROQ_KEY, "") ?: ""
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
