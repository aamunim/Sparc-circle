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
        val savedName = prefs.getString("visual_theme", SparcVisualTheme.COBALT_SAAS.name)
        val initialTheme = runCatching {
            SparcVisualTheme.valueOf(savedName ?: SparcVisualTheme.COBALT_SAAS.name)
        }.getOrDefault(SparcVisualTheme.COBALT_SAAS)

        repository = SparcRepository(
            initialTheme = initialTheme,
            onPersistTheme = { chosen ->
                prefs.edit().putString("visual_theme", chosen.name).apply()
            }
        )
    }
}
