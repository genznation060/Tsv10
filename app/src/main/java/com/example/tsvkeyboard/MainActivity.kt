package com.example.tsvkeyboard

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CategoryRepository
    private lateinit var categoryAdapter: CategoryAdapter

    private var currentCategories: MutableList<Category> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CategoryRepository(this)

        setupRecyclerView()
        setupListeners()
        loadCategories()
    }

    override fun onResume() {
        super.onResume()
        // Refresh categories on resume
        loadCategories()
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(
            onCategoryClick = { category -> showCategoryDetailDialog(category) },
            onEditClick = { category -> showRenameCategoryDialog(category) },
            onDeleteClick = { category -> showDeleteCategoryDialog(category) }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = categoryAdapter
    }

    private fun setupListeners() {
        // IMPORT TSV
        binding.btnImportTsv.setOnClickListener {
            val tsvText = binding.etTsvInput.text.toString()
            if (tsvText.isBlank()) {
                Toast.makeText(this, "Please paste TSV data first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val parseResult = TsvParser.parse(tsvText)
            if (parseResult.categories.isEmpty()) {
                Toast.makeText(this, "Could not extract valid categories from TSV", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save to repository
            repository.saveCategories(parseResult.categories)
            loadCategories()

            // Show summary
            binding.layoutImportSummary.visibility = View.VISIBLE
            binding.tvSummaryDetails.text = "Rows: ${parseResult.rowCount}  |  Columns: ${parseResult.columnCount}\nCategories created: ${parseResult.categories.size} (${parseResult.totalValuesCount} unique values)"
            Toast.makeText(this, "Imported ${parseResult.categories.size} categories!", Toast.LENGTH_SHORT).show()
        }

        // CLEAR INPUT
        binding.btnClearInput.setOnClickListener {
            binding.etTsvInput.setText("")
            binding.layoutImportSummary.visibility = View.GONE
        }

        // LOAD SAMPLE TSV
        binding.btnLoadSample.setOnClickListener {
            val sampleTsv = buildString {
                append("Phone\tModel\tDisplay\tProcessor\tCamera\tBattery\n")
                append("Galaxy S24\tSM-S921B\t6.2 AMOLED 120Hz\tSnapdragon 8 Gen 3\t50MP Dual-Pixel\t4000mAh 25W\n")
                append("Galaxy S24 Ultra\tSM-S928B\t6.8 Dynamic AMOLED 2X\tSnapdragon 8 Gen 3\t200MP Quad Telephoto\t5000mAh 45W\n")
                append("Pixel 9 Pro\tG1D60\t6.3 Super Actua LTPO\tGoogle Tensor G4\t50MP Triple-Cam\t4700mAh 27W\n")
                append("iPhone 16 Pro\tA3293\t6.3 Super Retina XDR\tApple A18 Pro\t48MP Fusion Telephoto\t3582mAh 25W\n")
                append("OnePlus 12\tCPH2581\t6.82 LTPO AMOLED\tSnapdragon 8 Gen 3\t50MP Hasselblad 4th Gen\t5400mAh 100W\n")
                append("Xiaomi 14\t23127PN0CG\t6.36 OLED 120Hz\tSnapdragon 8 Gen 3\t50MP Leica Optics\t4610mAh 90W\n")
            }
            binding.etTsvInput.setText(sampleTsv)
            Toast.makeText(this, "Sample TSV loaded! Tap 'IMPORT TSV' to import.", Toast.LENGTH_SHORT).show()
        }

        // ADD CATEGORY
        binding.btnAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        // CLEAR ALL CATEGORIES
        binding.btnClearAllCategories.setOnClickListener {
            showClearAllDialog()
        }

        // OPEN KEYBOARD SETTINGS
        binding.btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open keyboard settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // SWITCH INPUT METHOD
        binding.btnSwitchKeyboard.setOnClickListener {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open keyboard picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCategories() {
        currentCategories = repository.loadCategories().toMutableList()
        categoryAdapter.submitList(currentCategories)

        binding.tvCategoriesTitle.text = "Categories (${currentCategories.size})"

        if (currentCategories.isEmpty()) {
            binding.layoutEmptyCategories.visibility = View.VISIBLE
            binding.rvCategories.visibility = View.GONE
            binding.btnClearAllCategories.visibility = View.GONE
        } else {
            binding.layoutEmptyCategories.visibility = View.GONE
            binding.rvCategories.visibility = View.VISIBLE
            binding.btnClearAllCategories.visibility = View.VISIBLE
        }
    }

    // --- Dialogs ---

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Category name (e.g. Storage, Colors)"
            setPadding(40, 30, 40, 30)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_create_category)
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repository.addCategory(name)
                    loadCategories()
                    Toast.makeText(this, "Category created: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showRenameCategoryDialog(category: Category) {
        val input = EditText(this).apply {
            setText(category.name)
            setSelection(category.name.length)
            setPadding(40, 30, 40, 30)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rename_category)
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != category.name) {
                    repository.renameCategory(category.name, newName)
                    loadCategories()
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_category)
            .setMessage("Delete category \"${category.name}\" and all its ${category.values.size} values?")
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                repository.deleteCategory(category.name)
                loadCategories()
                Toast.makeText(this, "Deleted ${category.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_confirm_clear)
            .setMessage(R.string.dialog_confirm_clear_msg)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                repository.clearCategories()
                loadCategories()
                binding.layoutImportSummary.visibility = View.GONE
                Toast.makeText(this, "All categories cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showCategoryDetailDialog(category: Category) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_detail, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogCategoryTitle)
        val etNewValue = dialogView.findViewById<EditText>(R.id.etNewValue)
        val btnAddValue = dialogView.findViewById<Button>(R.id.btnAddValue)
        val etFilter = dialogView.findViewById<EditText>(R.id.etFilterValues)
        val btnClearAll = dialogView.findViewById<Button>(R.id.btnClearAllValues)
        val rvValues = dialogView.findViewById<RecyclerView>(R.id.rvCategoryValues)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmptyValues)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDialog)

        var activeCat = repository.loadCategories().find { it.name == category.name } ?: category
        var currentFilter = ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val adapter = ValueRowAdapter(
            onDeleteClick = { valToDelete ->
                repository.deleteValueFromCategory(activeCat.name, valToDelete)
                activeCat = repository.loadCategories().find { it.name == activeCat.name }
                    ?: activeCat.copy(values = activeCat.values - valToDelete)
                refreshDetailValues(activeCat, currentFilter, rvValues, tvEmpty, tvTitle)
                loadCategories()
            }
        )

        rvValues.layoutManager = LinearLayoutManager(this)
        rvValues.adapter = adapter

        fun refresh() {
            refreshDetailValues(activeCat, currentFilter, rvValues, tvEmpty, tvTitle)
            adapter.submitList(filterList(activeCat.values, currentFilter))
        }

        refresh()

        btnAddValue.setOnClickListener {
            val newVal = etNewValue.text.toString().trim()
            if (newVal.isNotEmpty()) {
                repository.addValueToCategory(activeCat.name, newVal)
                activeCat = repository.loadCategories().find { it.name == activeCat.name }
                    ?: activeCat.copy(values = activeCat.values + newVal)
                etNewValue.setText("")
                refresh()
                loadCategories()
            }
        }

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilter = s?.toString()?.trim() ?: ""
                refresh()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Values?")
                .setMessage("Clear all values in \"${activeCat.name}\"?")
                .setPositiveButton("Clear") { _, _ ->
                    repository.clearCategoryValues(activeCat.name)
                    activeCat = activeCat.copy(values = emptyList())
                    refresh()
                    loadCategories()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun filterList(values: List<String>, query: String): List<String> {
        if (query.isBlank()) return values
        return values.filter { it.contains(query, ignoreCase = true) }
    }

    private fun refreshDetailValues(
        category: Category,
        query: String,
        rv: RecyclerView,
        tvEmpty: TextView,
        tvTitle: TextView
    ) {
        tvTitle.text = "${category.name} (${category.values.size} values)"
        val filtered = filterList(category.values, query)
        val adapter = rv.adapter as? ValueRowAdapter
        adapter?.submitList(filtered)

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    // --- Category Adapter for Main List ---

    private class CategoryAdapter(
        private val onCategoryClick: (Category) -> Unit,
        private val onEditClick: (Category) -> Unit,
        private val onDeleteClick: (Category) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private var items: List<Category> = emptyList()

        fun submitList(newItems: List<Category>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = items[position]
            holder.tvName.text = cat.name
            holder.tvCount.text = "${cat.values.size} values"

            holder.itemView.setOnClickListener {
                onCategoryClick(cat)
            }

            holder.btnEdit.setOnClickListener {
                onEditClick(cat)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(cat)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
            val tvCount: TextView = itemView.findViewById(R.id.tvCategoryCount)
            val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditCategory)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteCategory)
        }
    }

    // --- Value Row Adapter for Dialog List ---

    private class ValueRowAdapter(
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<ValueRowAdapter.ViewHolder>() {

        private var items: List<String> = emptyList()

        fun submitList(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_value_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvValue.text = item

            holder.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvValue: TextView = itemView.findViewById(R.id.tvValueText)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteValue)
        }
    }
}
