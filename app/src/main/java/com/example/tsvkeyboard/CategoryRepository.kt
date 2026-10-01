package com.example.tsvkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight and bulletproof storage for categories and values.
 * Uses local SharedPreferences + JSON.
 * Completely independent of Activities or Compose lifecycles so it can be called
 * safely from both MainActivity and TsvKeyboardService.
 */
class CategoryRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val lock = Any()

    companion object {
        private const val TAG = "TSVKeyboard/Repo"
        private const val PREFS_NAME = "tsv_keyboard_prefs"
        private const val KEY_CATEGORIES_JSON = "categories_json"
    }

    /**
     * Loads all categories and their values safely.
     * Guaranteed to never throw an uncaught exception.
     */
    fun loadCategories(): List<Category> {
        synchronized(lock) {
            val rawJson = prefs.getString(KEY_CATEGORIES_JSON, null) ?: return emptyList()
            if (rawJson.isBlank()) return emptyList()

            val result = mutableListOf<Category>()
            try {
                val trimmed = rawJson.trim()
                if (trimmed.startsWith("[")) {
                    // Array format: [ {"name": "Phone", "values": [...]}, ... ]
                    val jsonArray = JSONArray(trimmed)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val name = obj.optString("name", "").trim()
                        if (name.isEmpty()) continue

                        val valuesList = mutableListOf<String>()
                        val valuesArray = obj.optJSONArray("values")
                        if (valuesArray != null) {
                            for (v in 0 until valuesArray.length()) {
                                val item = valuesArray.optString(v, "")
                                if (item.isNotEmpty()) {
                                    valuesList.add(item)
                                }
                            }
                        }
                        result.add(Category(name = name, values = valuesList))
                    }
                } else if (trimmed.startsWith("{")) {
                    // Object format: { "Phone": [...], "Display": [...] }
                    val jsonObj = JSONObject(trimmed)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val catName = keys.next()
                        val valuesList = mutableListOf<String>()
                        val valuesArray = jsonObj.optJSONArray(catName)
                        if (valuesArray != null) {
                            for (v in 0 until valuesArray.length()) {
                                val item = valuesArray.optString(v, "")
                                if (item.isNotEmpty()) {
                                    valuesList.add(item)
                                }
                            }
                        }
                        result.add(Category(name = catName, values = valuesList))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Corrupted category JSON in SharedPreferences, recovering gracefully", e)
                return emptyList()
            }
            return result
        }
    }

    /**
     * Saves categories list to SharedPreferences as a JSON array.
     */
    fun saveCategories(categories: List<Category>) {
        synchronized(lock) {
            try {
                val jsonArray = JSONArray()
                for (cat in categories) {
                    val obj = JSONObject()
                    obj.put("name", cat.name)
                    val vals = JSONArray()
                    for (v in cat.values) {
                        vals.put(v)
                    }
                    obj.put("values", vals)
                    jsonArray.put(obj)
                }
                prefs.edit().putString(KEY_CATEGORIES_JSON, jsonArray.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save categories to SharedPreferences", e)
            }
        }
    }

    /**
     * Clears all stored categories and values.
     */
    fun clearCategories() {
        synchronized(lock) {
            prefs.edit().remove(KEY_CATEGORIES_JSON).apply()
        }
    }

    /**
     * Appends a new empty or pre-populated category.
     */
    fun addCategory(name: String, initialValues: List<String> = emptyList()) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val current = loadCategories().toMutableList()
        // If already exists, do not duplicate
        val existingIndex = current.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            val merged = (existing.values + initialValues).distinct()
            current[existingIndex] = existing.copy(values = merged)
        } else {
            current.add(Category(name = trimmedName, values = initialValues.distinct()))
        }
        saveCategories(current)
    }

    /**
     * Renames a category.
     */
    fun renameCategory(oldName: String, newName: String) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isEmpty()) return
        val current = loadCategories().toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index >= 0) {
            current[index] = current[index].copy(name = trimmedNew)
            saveCategories(current)
        }
    }

    /**
     * Deletes a category.
     */
    fun deleteCategory(categoryName: String) {
        val current = loadCategories().filterNot { it.name == categoryName }
        saveCategories(current)
    }

    /**
     * Adds an individual value to a category.
     */
    fun addValueToCategory(categoryName: String, value: String) {
        val trimmedVal = value.trim()
        if (trimmedVal.isEmpty()) return
        val current = loadCategories().toMutableList()
        val index = current.indexOfFirst { it.name == categoryName }
        if (index >= 0) {
            val existing = current[index]
            if (!existing.values.contains(trimmedVal)) {
                current[index] = existing.copy(values = existing.values + trimmedVal)
                saveCategories(current)
            }
        }
    }

    /**
     * Removes an individual value from a category.
     */
    fun deleteValueFromCategory(categoryName: String, value: String) {
        val current = loadCategories().toMutableList()
        val index = current.indexOfFirst { it.name == categoryName }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(values = existing.values.filterNot { it == value })
            saveCategories(current)
        }
    }

    /**
     * Clears all values inside a single category.
     */
    fun clearCategoryValues(categoryName: String) {
        val current = loadCategories().toMutableList()
        val index = current.indexOfFirst { it.name == categoryName }
        if (index >= 0) {
            current[index] = current[index].copy(values = emptyList())
            saveCategories(current)
        }
    }
}
