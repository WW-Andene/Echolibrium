package com.kokoro.reader

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

class RulesFragment : Fragment() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val rules = mutableListOf<Pair<String, String>>()
    private lateinit var container: LinearLayout
    private val saveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val saveRunnable = Runnable { saveRules() }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_rules, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        container = v.findViewById(R.id.rules_container)
        loadRules()
        if (rules.isEmpty()) {
            rules.addAll(listOf(
                "WhatsApp" to "Message", "Gmail" to "Email",
                "lol" to "laugh out loud", "tbh" to "to be honest",
                "idk" to "I don't know", "omg" to "oh my god",
                "brb" to "be right back", "ngl" to "not gonna lie",
                "https://" to "link", "http://" to "link"
            ))
            saveRules()
        }
        renderRules()
        v.findViewById<Button>(R.id.btn_add_rule).setOnClickListener {
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

    private fun renderRules() {
        val ctx = context ?: return
        container.removeAllViews()
        rules.forEachIndexed { idx, (find, replace) ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFF111111.toInt()); setPadding(12, 10, 12, 10)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 4); layoutParams = lp
            }

            fun editText(hint: String, value: String, onChanged: (String) -> Unit) =
                EditText(ctx).apply {
                    setText(value); this.hint = hint; textSize = 13f
                    setTextColor(0xFFcccccc.toInt()); setHintTextColor(0xFF444444.toInt())
                    setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(12, 8, 12, 8)
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) { onChanged(s.toString()); debounceSave() }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }

            val etFind = editText("Find…", find) { if (idx < rules.size) rules[idx] = it to rules[idx].second }
            etFind.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)

            val arrow = TextView(ctx).apply {
                text = "→"; textSize = 14f; setTextColor(0xFF00ff88.toInt()); setPadding(8, 0, 8, 0)
            }

            val etReplace = editText("Replace…", replace) { if (idx < rules.size) rules[idx] = rules[idx].first to it }
            etReplace.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)

            val btnDel = Button(ctx).apply {
                text = "✕"; textSize = 12f; setTextColor(0xFFff4444.toInt())
                setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(16, 8, 16, 8)
                setOnClickListener { if (idx < rules.size) { rules.removeAt(idx); saveRules(); renderRules() } }
            }

            row.addView(etFind); row.addView(arrow); row.addView(etReplace); row.addView(btnDel)
            container.addView(row)
        }
    }

    /** Debounce saves to avoid writing SharedPreferences on every keystroke */
    private fun debounceSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 500)
    }

    override fun onDestroyView() {
        saveHandler.removeCallbacks(saveRunnable)
        // Flush any pending save before destroying
        saveRules()
        super.onDestroyView()
    }
}
