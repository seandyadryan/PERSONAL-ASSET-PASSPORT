package com.personalassetpassport.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalassetpassport.app.ui.PassportApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        val language = base.getSharedPreferences("passport-settings", 0).getString("language", null)
        if (language in listOf("en", "id")) {
            val locale = Locale.forLanguageTag(language!!)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            super.attachBaseContext(base.createConfigurationContext(config))
        } else super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PassportApp(viewModel(), intent.getStringExtra("assetId")) }
    }
}
