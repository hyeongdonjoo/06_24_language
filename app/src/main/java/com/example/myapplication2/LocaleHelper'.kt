package com.example.myapplication2

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * 간단한 언어 설정 유틸리티. 앱 어디서든 호출 가능.
 */
object LocaleHelper {
    private const val PREFS_NAME = "Settings"
    private const val KEY_LANG = "language"

    /** 저장된 언어 코드 가져오기 (기본 "ko") */
    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "ko") ?: "ko"
    }

    /** 언어 코드 저장 */
    fun saveLanguage(context: Context, lang: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
    }

    /** 런타임에서 Locale 적용 */
    fun setLocale(context: Context, lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
