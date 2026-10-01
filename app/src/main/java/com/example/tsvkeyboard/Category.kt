package com.example.tsvkeyboard

/**
 * Represents a category extracted from a TSV column, containing its column name
 * and all parsed row values.
 */
data class Category(
    val name: String,
    val values: List<String> = emptyList()
)
