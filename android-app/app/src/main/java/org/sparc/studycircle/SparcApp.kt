package org.sparc.studycircle

import android.app.Application
import android.content.Context
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.model.SparcVisualTheme

class SparcApp : Application() {
    lateinit var repository: SparcRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("sparc_ui_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("visual_theme", SparcVisualTheme.COBALT_SAAS.name)
        val initialTheme = runCatching {
            SparcVisualTheme.valueOf(savedTheme ?: SparcVisualTheme.COBALT_SAAS.name)
        }.getOrDefault(SparcVisualTheme.COBALT_SAAS)

        val isAuthenticated = prefs.getBoolean("is_authenticated", false)
        val savedName = prefs.getString("user_name", null)
        val savedDept = prefs.getString("user_dept", null)
        val savedLang = prefs.getString("app_language", "bn") ?: "bn"

        repository = SparcRepository(
            initialTheme = initialTheme,
            initialAuthenticated = isAuthenticated,
            initialUserName = savedName,
            initialUserDept = savedDept,
            initialLanguage = savedLang,
            onPersistTheme = { chosen ->
                prefs.edit().putString("visual_theme", chosen.name).apply()
            },
            onPersistAuth = { authed, name, dept ->
                prefs.edit()
                    .putBoolean("is_authenticated", authed)
                    .putString("user_name", name)
                    .putString("user_dept", dept)
                    .apply()
            },
            onPersistLanguage = { lang ->
                prefs.edit().putString("app_language", lang).apply()
            }
        )
    }
}
