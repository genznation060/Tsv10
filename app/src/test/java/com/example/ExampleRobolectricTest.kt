package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tsvkeyboard.Category
import com.example.tsvkeyboard.CategoryRepository
import com.example.tsvkeyboard.TsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TSV Category Keyboard", appName)
    }

    @Test
    fun `tsv parser extracts columns and values correctly`() {
        val tsv = "Phone\tDisplay\tCamera\r\nGalaxy S24\t6.2 AMOLED\t50MP\r\nPixel 9\t6.3 OLED\t50MP"
        val result = TsvParser.parse(tsv)

        assertEquals(3, result.columnCount)
        assertEquals(2, result.rowCount)
        assertEquals(3, result.categories.size)

        val phoneCat = result.categories.find { it.name == "Phone" }
        assertNotNull(phoneCat)
        assertEquals(listOf("Galaxy S24", "Pixel 9"), phoneCat?.values)

        val cameraCat = result.categories.find { it.name == "Camera" }
        assertNotNull(cameraCat)
        // 50MP is distinct
        assertEquals(listOf("50MP"), cameraCat?.values)
    }

    @Test
    fun `tsv parser handles uneven rows and empty cells safely`() {
        val malformedTsv = "Col1\tCol2\tCol3\nVal1\t\nVal3\tVal4\tVal5\tExtra"
        val result = TsvParser.parse(malformedTsv)

        assertEquals(3, result.columnCount)
        assertEquals(2, result.rowCount)
        assertEquals(3, result.categories.size)
    }

    @Test
    fun `category repository saves and loads categories`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CategoryRepository(context)
        repo.clearCategories()

        val sample = listOf(
            Category("Phone", listOf("S24", "Pixel 9")),
            Category("OS", listOf("Android 14", "Android 15"))
        )
        repo.saveCategories(sample)

        val loaded = repo.loadCategories()
        assertEquals(2, loaded.size)
        assertEquals("Phone", loaded[0].name)
        assertEquals(2, loaded[0].values.size)

        // Test adding individual value
        repo.addValueToCategory("Phone", "iPhone 16")
        val updated = repo.loadCategories()
        val phoneCat = updated.find { it.name == "Phone" }
        assertTrue(phoneCat?.values?.contains("iPhone 16") == true)
    }
}
