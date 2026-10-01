package com.example.tsvkeyboard

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R

/**
 * Android InputMethodService that provides TSV Category selection and insertion.
 * Designed with defensive null-checks and lifecycle isolation to prevent crashes
 * during keyboard switching, opening from external apps, or data corruption.
 */
class TsvKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "TSVKeyboard/IME"
    }

    private var repository: CategoryRepository? = null

    // UI View References (only valid between onCreateInputView and onDestroy)
    private var rootView: View? = null
    private var layoutCategoryTabs: LinearLayout? = null
    private var hsvCategories: HorizontalScrollView? = null
    private var rvValues: RecyclerView? = null
    private var etSearch: EditText? = null
    private var btnClearSearch: TextView? = null
    private var btnInsertAll: TextView? = null
    private var layoutEmptyState: View? = null
    private var tvEmptyStateText: TextView? = null
    private var tvKeyboardTitle: TextView? = null

    // Data State
    private var categories: List<Category> = emptyList()
    private var selectedCategoryIndex: Int = 0
    private var currentSearchQuery: String = ""
    private var valuesAdapter: ValuesAdapter? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        try {
            repository = CategoryRepository(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing repository in onCreate", e)
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView called")
        return try {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.keyboard, null)
            rootView = view

            initViews(view)
            loadDataAndSetupUI()

            view
    } catch (e: Exception) {
            Log.e(TAG, "Fatal fallback prevented crash in onCreateInputView", e)
            // Emergency fallback view so the system never crashes
            val fallback = View(this)
            rootView = fallback
            fallback
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput restarting=$restarting")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView restarting=$restarting")
        try {
            // Reload categories in case user edited them in the app
            loadDataAndSetupUI()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartInputView refresh", e)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView finishingInput=$finishingInput")
        try {
            // Clear search focus if open
            etSearch?.clearFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onFinishInputView", e)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput called")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        // Clear all view references to avoid memory leaks
        rootView = null
        layoutCategoryTabs = null
        hsvCategories = null
        rvValues = null
        etSearch = null
        btnClearSearch = null
        btnInsertAll = null
        layoutEmptyState = null
        tvEmptyStateText = null
        tvKeyboardTitle = null
        valuesAdapter = null
        super.onDestroy()
    }

    private fun initViews(view: View) {
        layoutCategoryTabs = view.findViewById(R.id.layoutCategoryTabs)
        hsvCategories = view.findViewById(R.id.hsvCategories)
        rvValues = view.findViewById(R.id.rvValues)
        etSearch = view.findViewById(R.id.etSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        btnInsertAll = view.findViewById(R.id.btnInsertAll)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyStateText = view.findViewById(R.id.tvEmptyStateText)
        tvKeyboardTitle = view.findViewById(R.id.tvKeyboardTitle)

        // Setup RecyclerView
        rvValues?.layoutManager = LinearLayoutManager(this)
        valuesAdapter = ValuesAdapter(
            onItemClick = { value -> safeCommitText(value) },
            onInsertClick = { value -> safeCommitText(value) }
        )
        rvValues?.adapter = valuesAdapter

        // Setup Search Listener
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch?.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                updateValuesList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch?.setOnClickListener {
            etSearch?.setText("")
        }

        // INSERT ALL Button
        btnInsertAll?.setOnClickListener {
            insertAllValues()
        }

        // Top bar buttons
        view.findViewById<View>(R.id.btnOpenApp)?.setOnClickListener {
            launchMainActivity()
        }

        view.findViewById<View>(R.id.btnSwitchIme)?.setOnClickListener {
            safeSwitchInputMethod()
        }

        view.findViewById<View>(R.id.btnHideKeyboard)?.setOnClickListener {
            requestHideSelf(0)
        }

        view.findViewById<View>(R.id.btnEmptyOpenApp)?.setOnClickListener {
            launchMainActivity()
        }

        // Bottom control keys
        view.findViewById<View>(R.id.btnTabKey)?.setOnClickListener {
            safeCommitText("\t")
        }

        view.findViewById<View>(R.id.btnSpaceKey)?.setOnClickListener {
            safeCommitText(" ")
        }

        view.findViewById<View>(R.id.btnBackspaceKey)?.setOnClickListener {
            safeDeleteText()
        }

        view.findViewById<View>(R.id.btnEnterKey)?.setOnClickListener {
            safeSendEnter()
        }
    }

    private fun loadDataAndSetupUI() {
        try {
            categories = repository?.loadCategories() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories from repository", e)
            categories = emptyList()
        }

        if (categories.isEmpty()) {
            layoutCategoryTabs?.removeAllViews()
            layoutEmptyState?.visibility = View.VISIBLE
            rvValues?.visibility = View.GONE
            btnInsertAll?.isEnabled = false
            btnInsertAll?.alpha = 0.5f
            tvKeyboardTitle?.text = getString(R.string.kb_header_title)
            etSearch?.hint = getString(R.string.kb_hint_search)
            return
        }

        layoutEmptyState?.visibility = View.GONE
        rvValues?.visibility = View.VISIBLE
        btnInsertAll?.isEnabled = true
        btnInsertAll?.alpha = 1.0f

        // Clamp selected category index safely
        if (selectedCategoryIndex >= categories.size || selectedCategoryIndex < 0) {
            selectedCategoryIndex = 0
        }

        buildCategoryTabs()
        updateValuesList()
    }

    private fun buildCategoryTabs() {
        val container = layoutCategoryTabs ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (6 * density).toInt()
        val marginEnd = (6 * density).toInt()

        for (i in categories.indices) {
            val cat = categories[i]
            val tabView = TextView(this).apply {
                text = "${cat.name} (${cat.values.size})"
                textSize = 12f
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true

                val isSelected = (i == selectedCategoryIndex)
                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_keyboard_tab_active)
                    setTextColor(getColor(R.color.kb_text_white))
                } else {
                    setBackgroundResource(R.drawable.bg_keyboard_tab_normal)
                    setTextColor(getColor(R.color.kb_text_dim))
                }

                setOnClickListener {
                    if (selectedCategoryIndex != i) {
                        selectedCategoryIndex = i
                        buildCategoryTabs()
                        updateValuesList()
                    }
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.marginEnd = marginEnd
            }
            container.addView(tabView, params)
        }

        // Update Title and Search Hint
        val activeCat = categories.getOrNull(selectedCategoryIndex)
        if (activeCat != null) {
            tvKeyboardTitle?.text = "TSV KEYBOARD • ${activeCat.name.uppercase()}"
            etSearch?.hint = "Search in ${activeCat.name}…"
        }
    }

    private fun updateValuesList() {
        val activeCat = categories.getOrNull(selectedCategoryIndex)
        if (activeCat == null || activeCat.values.isEmpty()) {
            valuesAdapter?.submitList(emptyList())
            btnInsertAll?.isEnabled = false
            btnInsertAll?.alpha = 0.5f
            return
        }

        val filtered = if (currentSearchQuery.isBlank()) {
            activeCat.values
        } else {
            activeCat.values.filter { it.contains(currentSearchQuery, ignoreCase = true) }
        }

        valuesAdapter?.submitList(filtered)
        btnInsertAll?.isEnabled = filtered.isNotEmpty()
        btnInsertAll?.alpha = if (filtered.isNotEmpty()) 1.0f else 0.5f
    }

    private fun insertAllValues() {
        val activeCat = categories.getOrNull(selectedCategoryIndex) ?: return
        val itemsToInsert = if (currentSearchQuery.isBlank()) {
            activeCat.values
        } else {
            activeCat.values.filter { it.contains(currentSearchQuery, ignoreCase = true) }
        }

        if (itemsToInsert.isEmpty()) return
        val joined = itemsToInsert.joinToString(separator = "\n")
        safeCommitText(joined)
    }

    /**
     * Commits text safely to the active external input connection.
     */
    private fun safeCommitText(text: String) {
        if (text.isEmpty()) return
        try {
            val ic = currentInputConnection
            if (ic != null) {
                ic.commitText(text, 1)
            } else {
                Log.w(TAG, "InputConnection was null when attempting to commit text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during commitText", e)
        }
    }

    /**
     * Safely deletes text backwards.
     */
    private fun safeDeleteText() {
        try {
            val ic = currentInputConnection ?: return
            val selected = ic.getSelectedText(0)
            if (selected.isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            } else {
                ic.commitText("", 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during deleteSurroundingText", e)
        }
    }

    /**
     * Safely sends enter or triggers the action specified by IME options.
     */
    private fun safeSendEnter() {
        try {
            val ic = currentInputConnection ?: return
            val editorInfo = currentInputEditorInfo
            val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sendEnter", e)
        }
    }

    /**
     * Safely switches to the previous or next system input method.
     */
    private fun safeSwitchInputMethod() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                val token = window?.window?.attributes?.token
                if (token != null) {
                    imm.switchToNextInputMethod(token, false)
                } else {
                    imm.showInputMethodPicker()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching input method, falling back to picker", e)
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            } catch (_: Exception) {}
        }
    }

    /**
     * Launches the main app activity from outside safely.
     */
    private fun launchMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching MainActivity from keyboard", e)
        }
    }

    /**
     * Lightweight RecyclerView Adapter for values.
     */
    private class ValuesAdapter(
        private val onItemClick: (String) -> Unit,
        private val onInsertClick: (String) -> Unit
    ) : RecyclerView.Adapter<ValuesAdapter.ViewHolder>() {

        private var items: List<String> = emptyList()

        fun submitList(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_keyboard_value, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvValue.text = item

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }

            holder.btnInsert.setOnClickListener {
                onInsertClick(item)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvValue: TextView = itemView.findViewById(R.id.tvValueText)
            val btnInsert: TextView = itemView.findViewById(R.id.btnInsertValue)
        }
    }
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("TSVKeyboard/IME", "color fix failed", t)
        }
    }

}
