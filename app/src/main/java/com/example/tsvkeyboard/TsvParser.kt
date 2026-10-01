package com.example.tsvkeyboard

import android.util.Log

/**
 * Robust TSV (Tab-Separated Values) parser designed specifically to safely
 * extract column categories and row values without risking crashes on malformed data.
 */
object TsvParser {

    private const val TAG = "TSVKeyboard/Parser"

    data class ParseResult(
        val categories: List<Category>,
        val rowCount: Int,
        val columnCount: Int,
        val totalValuesCount: Int
    )

    /**
     * Parses TSV text into categories based on the first row's column headers.
     * Safe against:
     * - Empty input
     * - Windows (\r\n) and Unix (\n) line breaks
     * - Trailing or consecutive tabs
     * - Rows with fewer or more columns than headers
     * - Null/blank inputs
     */
    fun parse(tsvContent: String?): ParseResult {
        if (tsvContent.isNullOrBlank()) {
            return ParseResult(emptyList(), 0, 0, 0)
        }

        try {
            // Split into lines preserving line boundary types
            val lines = tsvContent.split(Regex("\r\n|\r|\n"))
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }

            if (lines.isEmpty()) {
                return ParseResult(emptyList(), 0, 0, 0)
            }

            // First non-blank row is the column headers
            val headerTokens: List<String> = lines[0].split('\t').map { it.trim() }
            if (headerTokens.isEmpty()) {
                return ParseResult(emptyList(), 0, 0, 0)
            }

            // Ensure every header has a non-blank name
            val columnHeaders: List<String> = headerTokens.mapIndexed { index: Int, name: String ->
                if (name.isBlank()) "Column ${index + 1}" else name
            }

            val columnCount = columnHeaders.size
            val columnValues = List(columnCount) { mutableListOf<String>() }

            var dataRows = 0
            for (lineIdx in 1 until lines.size) {
                val line = lines[lineIdx]
                if (line.isBlank()) continue
                dataRows++

                val tokens: List<String> = line.split('\t')

                for (colIdx in 0 until columnCount) {
                    val rawValue = if (colIdx < tokens.size) tokens[colIdx].trim() else ""
                    if (rawValue.isNotEmpty()) {
                        columnValues[colIdx].add(rawValue)
                    }
                }
            }

            var totalValues = 0
            val categories = columnHeaders.mapIndexed { index: Int, header: String ->
                val uniqueVals = columnValues[index].distinct()
                totalValues += uniqueVals.size
                Category(name = header, values = uniqueVals)
            }

            return ParseResult(
                categories = categories,
                rowCount = dataRows,
                columnCount = columnCount,
                totalValuesCount = totalValues
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TSV content", e)
            return ParseResult(emptyList(), 0, 0, 0)
        }
    }
}
