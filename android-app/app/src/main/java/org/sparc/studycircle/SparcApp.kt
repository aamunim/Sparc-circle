package org.sparc.studycircle

import android.app.Application
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.data.SupabaseAuthSession
import org.sparc.studycircle.core.media.SparcNotificationHelper
import org.sparc.studycircle.core.model.ApiKeyVaultConfig
import org.sparc.studycircle.core.model.AppCustomizationConfig
import org.sparc.studycircle.core.model.SparcVisualTheme
import org.sparc.studycircle.core.model.UserUiCustomization

class SparcApp : Application() {
    lateinit var repository: SparcRepository
        private set

    override fun onCreate() {
        super.onCreate()
        SparcNotificationHelper.ensureChannel(this)
        val json = Json { ignoreUnknownKeys = true }
        val prefs = getSharedPreferences("sparc_ui_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("visual_theme", SparcVisualTheme.COBALT_SAAS.name)
        val initialTheme = runCatching {
            SparcVisualTheme.valueOf(savedTheme ?: SparcVisualTheme.COBALT_SAAS.name)
        }.getOrDefault(SparcVisualTheme.COBALT_SAAS)

        val isAuthenticated = prefs.getBoolean("is_authenticated", false)
        val savedName = prefs.getString("user_name", null)
        val savedDept = prefs.getString("user_dept", null)
        val savedLang = prefs.getString("app_language", "bn") ?: "bn"
        // Purge any legacy client-side API key vault from device storage (all keys now live in Admin Server Vault)
        if (prefs.contains("api_key_vault")) {
            prefs.edit().remove("api_key_vault").apply()
        }
        val initialVault = ApiKeyVaultConfig()

        val savedSessionJson = prefs.getString("supabase_auth_session", null)
        val initialSession = savedSessionJson?.let {
            runCatching { json.decodeFromString<SupabaseAuthSession>(it) }.getOrNull()
        }

        val savedAppCustomJson = prefs.getString("app_customization_config", null)
        val initialAppCustom = savedAppCustomJson?.let {
            runCatching { json.decodeFromString<AppCustomizationConfig>(it) }.getOrNull()
        } ?: AppCustomizationConfig()

        val savedUiCustomJson = prefs.getString("user_ui_customization", null)
        val initialUiCustom = savedUiCustomJson?.let {
            runCatching { json.decodeFromString<UserUiCustomization>(it) }.getOrNull()
        } ?: UserUiCustomization()

        repository = SparcRepository(
            initialTheme = initialTheme,
            initialAuthenticated = isAuthenticated,
            initialUserName = savedName,
            initialUserDept = savedDept,
            initialLanguage = savedLang,
            initialApiVault = initialVault,
            initialSession = initialSession,
            initialAppCustomization = initialAppCustom,
            initialUiCustomization = initialUiCustom,
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
            },
            onPersistApiVault = { _ ->
                prefs.edit().remove("api_key_vault").apply()
            },
            onPersistSession = { sess ->
                val ed = prefs.edit()
                if (sess == null) {
                    ed.remove("supabase_auth_session")
                } else {
                    ed.putString("supabase_auth_session", json.encodeToString(sess))
                }
                ed.apply()
            },
            onPersistAppCustomization = { cfg ->
                prefs.edit().putString("app_customization_config", json.encodeToString(cfg)).apply()
            },
            onPersistUiCustomization = { uiCfg ->
                prefs.edit().putString("user_ui_customization", json.encodeToString(uiCfg)).apply()
            },
            onSystemAlert = { title, body ->
                val enforceQuiet = runCatching { repository.state.value.uiCustomization.quietHoursEnabled }.getOrDefault(true)
                SparcNotificationHelper.dispatchAlert(this@SparcApp, title, body, enforceQuietHours = enforceQuiet)
            }
        )
    }
}
