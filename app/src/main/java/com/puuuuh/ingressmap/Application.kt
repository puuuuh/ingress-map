package com.puuuuh.ingressmap

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.puuuuh.ingressmap.model.*
import com.puuuuh.ingressmap.settings.Settings

// Not object class. AndroidManifest.xml error happen.
class MainApplication : Application() {

    init {
        instance = this
    }

    companion object {
        private var instance: MainApplication? = null
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(GameEntity::class.java, GameEntityDeserializer())
            .registerTypeAdapter(FieldData::class.java, FieldDeserializer())
            .registerTypeAdapter(LinkData::class.java, LinkDeserializer())
            .registerTypeAdapter(Mod::class.java, ModDeserializer())
            .registerTypeAdapter(Point::class.java, PointDeserializer())
            .registerTypeAdapter(PortalData::class.java, PortalDeserializer())
            .registerTypeAdapter(Resonator::class.java, ResonatorDeserializer())
            .registerTypeAdapter(PlayerInfo::class.java, PlayerInfoDeserializer())
            .create()

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext()
        Settings.init(context)
        AppCompatDelegate.setDefaultNightMode(Settings.theme)
    }
}