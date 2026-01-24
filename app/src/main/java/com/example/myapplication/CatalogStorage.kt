package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object CatalogStorage {
    private const val FILE_NAME = "catalog_metadata.json"

    private val gson: Gson = GsonBuilder().create()

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun load(context: Context): CatalogMetadata? {
        return try {
            val f = file(context)
            if (!f.exists()) return null
            val json = f.readText()
            gson.fromJson(json, CatalogMetadata::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, metadata: CatalogMetadata) {
        try {
            val f = file(context)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            val json = gson.toJson(metadata)
            tmp.writeText(json)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        } catch (_: Exception) {
            // MVP: nie zabijamy aplikacji, jeśli zapis się nie uda
        }
    }

    fun clear(context: Context) {
        try {
            val f = file(context)
            if (f.exists()) f.delete()
        } catch (_: Exception) {}
    }
}
