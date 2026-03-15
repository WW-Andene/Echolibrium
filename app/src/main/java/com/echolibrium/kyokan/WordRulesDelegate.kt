package com.echolibrium.kyokan

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Delegate handling word find/replace rules (M20: decomposed from RulesFragment).
 */
class WordRulesDelegate(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val container: LinearLayout
) {
    private val rules = mutableListOf<Pair<String, String>>()
    /** B-09: Debounce handler — saves at most once per 500ms instead of every keystroke. */
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null

    fun setup(rootView: View) {
        loadRules()
        if (rules.isEmpty()) {
            // L-03: Load default rules from string-array resources (localizable)
            val finds = context.resources.getStringArray(R.array.default_word_rules_find)
            val replaces = context.resources.getStringArray(R.array.default_word_rules_replace)
            finds.zip(replaces).forEach { (f, r) -> rules.add(f to r) }
            saveRules()
        }
        renderRules()
        rootView.findViewById<Button>(R.id.btn_add_rule).setOnClickListener {
            rules.add("" to ""); saveRules(); renderRules()
        }
    }

    private fun loadRules() {
        rules.clear()
        val json = prefs.getString("wording_rules", null) ?: return
        try {
            val arr = JSONArray(json)
            repeat(arr.length()) { rules.add(arr.getJSONObject(it).optString("find") to arr.getJSONObject(it).optString("replace")) }
        } catch (_: Exception) {}
    }

    private fun saveRules() {
        val arr = JSONArray()
        rules.forEach { (f, r) -> arr.put(JSONObject().apply { put("find", f); put("replace", r) }) }
        prefs.edit().putString("wording_rules", arr.toString()).apply()
    }

    /** B-09: Debounced save — cancels pending save and posts new one after 500ms. */
    private fun debounceSave() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        saveRunnable = Runnable { saveRules() }
        saveHandler.postDelayed(saveRunnable!!, 500)
    }

    private fun renderRules() {
        container.removeAllViews()
        rules.forEachIndexed { idx, (find, replace) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(AppColors.inputBg(context)); setPaddingRelative(12, 10, 12, 10)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 4); layoutParams = lp
            }

            fun editText(hint: String, value: String, onChanged: (String) -> Unit) =
                EditText(context).apply {
                    setText(value); this.hint = hint; textSize = 13f
                    setTextColor(AppColors.textPrimary(context)); setHintTextColor(AppColors.textHint(context))
                    setBackgroundColor(AppColors.cardBorder(context)); setPaddingRelative(12, 8, 12, 8)
                    filters = arrayOf(android.text.InputFilter.LengthFilter(200))
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) { onChanged(s.toString()); debounceSave() }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }

            val ruleNum = idx + 1
            val etFind = editText(context.getString(R.string.find_hint), find) { rules[idx] = it to rules[idx].second }
            etFind.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            etFind.contentDescription = "Rule $ruleNum find text"

            val arrow = TextView(context).apply {
                text = "→"; textSize = 14f; setTextColor(AppColors.primary(context)); setPaddingRelative(8, 0, 8, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            val etReplace = editText(context.getString(R.string.replace_hint), replace) { rules[idx] = rules[idx].first to it }
            etReplace.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            etReplace.contentDescription = "Rule $ruleNum replace text"

            val dp = context.resources.displayMetrics.density
            val btnDel = Button(context).apply {
                text = "✕"; textSize = 12f; setTextColor(AppColors.accentRed(context))
                setBackgroundColor(AppColors.inputBg(context)); setPaddingRelative(16, 8, 16, 8)
                minHeight = (48 * dp).toInt(); minimumHeight = (48 * dp).toInt()
                minWidth = (48 * dp).toInt(); minimumWidth = (48 * dp).toInt()
                contentDescription = "Delete rule $ruleNum"
                setOnClickListener { rules.removeAt(idx); saveRules(); renderRules() }
            }

            row.addView(etFind); row.addView(arrow); row.addView(etReplace); row.addView(btnDel)
            container.addView(row)
        }
    }
}
