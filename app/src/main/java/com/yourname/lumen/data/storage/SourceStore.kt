package com.yourname.lumen.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.yourname.lumen.domain.model.Source
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Stores sources on the device only. Credentials are encrypted with the Android Keystore. */
class SourceStore(context: Context) {

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "lumen_sources",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Some TV boxes have a broken Keystore. Fall back to private app storage.
        context.getSharedPreferences("lumen_sources_plain", Context.MODE_PRIVATE)
    }

    fun load(): List<Source> {
        val arr = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Source(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                baseUrl = o.optString("url"),
                username = o.optString("user"),
                password = o.optString("pass"),
            )
        }
    }

    fun add(source: Source) = save(load() + source)

    fun remove(id: String) = save(load().filterNot { it.id == id })

    private fun save(list: List<Source>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.baseUrl)
                    .put("user", it.username)
                    .put("pass", it.password),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "sources"
    }
}
