package com.kachat.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * A string resource in the language chosen in Settings > Language, from anywhere: a composable, a
 * click handler or a toast. Settings > Language recreates the activity, so what's on screen re-reads
 * it. The KaPay text ported from iOS (`strings_kapay.xml`, iOS 953b255's translations) goes through
 * this, the way iOS's goes through `AppLocalization`.
 */
object AppText {
    private lateinit var app: Context
    private var cachedTag: String? = null
    private var cachedContext: Context? = null

    fun init(context: Context) {
        app = context.applicationContext ?: context
    }

    @Synchronized
    fun context(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = locales.toLanguageTags()
        // No choice made: the app's own resources already follow the phone's language.
        if (tag.isEmpty()) return app
        cachedContext?.let { if (cachedTag == tag) return it }
        val config = Configuration(app.resources.configuration)
        config.setLocales(locales.unwrap() as LocaleList)
        return app.createConfigurationContext(config).also {
            cachedTag = tag
            cachedContext = it
        }
    }
}

/** See [AppText]. */
fun tr(@StringRes id: Int, vararg args: Any?): String = AppText.context().getString(id, *args)
