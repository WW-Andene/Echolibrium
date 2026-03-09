package com.kokoro.reader

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import java.util.Locale

class ProfilesFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    private var currentProfile = VoiceProfile()
    private var allSysVoices = listOf<android.speech.tts.Voice>()
    private var filteredVoices = listOf<android.speech.tts.Voice>()
    private var ttsInit: TextToSpeech? = null
    private var genderFilter = "All"
    private var nationFilter = "All"

    private lateinit var profileSpinner: Spinner
    private lateinit var txtPreview: EditText
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnNew: Button
    private lateinit var seekPitch: SeekBar;  private lateinit var tvPitch: TextView
    private lateinit var seekSpeed: SeekBar;  private lateinit var tvSpeed: TextView
    private lateinit var seekBreathInt: SeekBar;   private lateinit var tvBreathInt: TextView
    private lateinit var seekBreathCurve: SeekBar; private lateinit var tvBreathCurve: TextView
    private lateinit var seekBreathPause: SeekBar; private lateinit var tvBreathPause: TextView
    private lateinit var seekStutterInt: SeekBar;  private lateinit var tvStutterInt: TextView
    private lateinit var seekStutterPos: SeekBar;  private lateinit var tvStutterPos: TextView
    private lateinit var seekStutterFreq: SeekBar; private lateinit var tvStutterFreq: TextView
    private lateinit var seekStutterPause: SeekBar;private lateinit var tvStutterPause: TextView
    private lateinit var seekIntonInt: SeekBar;    private lateinit var tvIntonInt: TextView
    private lateinit var seekIntonVar: SeekBar;    private lateinit var tvIntonVar: TextView
    private lateinit var voiceGrid: LinearLayout
    private lateinit var presetsScroll: LinearLayout
    private lateinit var gimmicksContainer: LinearLayout
    private lateinit var genderRow: LinearLayout
    private lateinit var nationRow: LinearLayout

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_profiles, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        bindViews(v)
        profiles = VoiceProfile.loadAll(prefs)
        activeProfileId = prefs.getString("active_profile_id", "") ?: ""
        if (profiles.isEmpty()) { profiles.add(VoiceProfile(name = "Default")); VoiceProfile.saveAll(profiles, prefs) }

        ttsInit = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                allSysVoices = ttsInit?.voices?.filter { it.locale.language == "en" }?.sortedBy { it.name } ?: emptyList()
                activity?.runOnUiThread { buildFilterButtons(); renderVoiceGrid() }
            }
        }

        setupProfileSpinner()
        buildPresets()
        setupButtons()
        buildGimmicksEditor()
        loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles[0])
    }

    private fun bindViews(v: View) {
        profileSpinner  = v.findViewById(R.id.spinner_profile)
        txtPreview      = v.findViewById(R.id.txt_preview)
        btnTest         = v.findViewById(R.id.btn_test)
        btnSave         = v.findViewById(R.id.btn_save)
        btnDelete       = v.findViewById(R.id.btn_delete)
        btnNew          = v.findViewById(R.id.btn_new_profile)
        voiceGrid       = v.findViewById(R.id.voice_grid)
        presetsScroll   = v.findViewById(R.id.layout_presets)
        gimmicksContainer = v.findViewById(R.id.gimmicks_container)
        genderRow       = v.findViewById(R.id.gender_filter_row)
        nationRow       = v.findViewById(R.id.nation_filter_row)
        seekPitch       = v.findViewById(R.id.seek_pitch);         tvPitch       = v.findViewById(R.id.tv_pitch)
        seekSpeed       = v.findViewById(R.id.seek_speed);         tvSpeed       = v.findViewById(R.id.tv_speed)
        seekBreathInt   = v.findViewById(R.id.seek_breath_int);    tvBreathInt   = v.findViewById(R.id.tv_breath_int)
        seekBreathCurve = v.findViewById(R.id.seek_breath_curve);  tvBreathCurve = v.findViewById(R.id.tv_breath_curve)
        seekBreathPause = v.findViewById(R.id.seek_breath_pause);  tvBreathPause = v.findViewById(R.id.tv_breath_pause)
        seekStutterInt  = v.findViewById(R.id.seek_stutter_int);   tvStutterInt  = v.findViewById(R.id.tv_stutter_int)
        seekStutterPos  = v.findViewById(R.id.seek_stutter_pos);   tvStutterPos  = v.findViewById(R.id.tv_stutter_pos)
        seekStutterFreq = v.findViewById(R.id.seek_stutter_freq);  tvStutterFreq = v.findViewById(R.id.tv_stutter_freq)
        seekStutterPause= v.findViewById(R.id.seek_stutter_pause); tvStutterPause= v.findViewById(R.id.tv_stutter_pause)
        seekIntonInt    = v.findViewById(R.id.seek_intonation_int);tvIntonInt    = v.findViewById(R.id.tv_intonation_int)
        seekIntonVar    = v.findViewById(R.id.seek_intonation_var);tvIntonVar    = v.findViewById(R.id.tv_intonation_var)
    }

    private fun buildFilterButtons() {
        // Gender filter
        genderRow.removeAllViews()
        listOf("All", "Female", "Male", "Unknown").forEach { g ->
            genderRow.addView(filterBtn(g, genderFilter == g) {
                genderFilter = g; buildFilterButtons(); renderVoiceGrid()
            })
        }
        // Nationality filter
        nationRow.removeAllViews()
        listOf("All", "American", "British", "Unknown").forEach { n ->
            nationRow.addView(filterBtn(n, nationFilter == n) {
                nationFilter = n; buildFilterButtons(); renderVoiceGrid()
            })
        }
    }

    private fun filterBtn(label: String, active: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label; textSize = 11f
            setBackgroundColor(if (active) 0xFF1a3a1a.toInt() else 0xFF111111.toInt())
            setTextColor(if (active) 0xFF00ff88.toInt() else 0xFF666666.toInt())
            setPadding(20, 8, 20, 8)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 8, 0); layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun renderVoiceGrid() {
        filteredVoices = allSysVoices.filter { v ->
            val info = VoiceInfo.from(v.name)
            val gOk = genderFilter == "All" || info.gender == genderFilter
            val nOk = nationFilter == "All" || info.nationality == nationFilter
            gOk && nOk
        }
        voiceGrid.removeAllViews()
        if (filteredVoices.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No voices match. Open SherpaTTS to download more."
                setTextColor(0xFF446644.toInt()); textSize = 12f; setPadding(0, 8, 0, 8)
            }
            voiceGrid.addView(tv); return
        }
        filteredVoices.forEach { v ->
            val info = VoiceInfo.from(v.name)
            val active = currentProfile.voiceName == v.name
            val btn = Button(requireContext()).apply {
                text = buildString {
                    append(if (info.gender == "Female") "♀ " else if (info.gender == "Male") "♂ " else "? ")
                    val shortName = v.name.substringAfter("_").replaceFirstChar { it.uppercase() }
                    append(shortName)
                    if (info.nationality != "Unknown") append("\n${info.nationality}")
                }
                textSize = 11f; setPadding(16, 10, 16, 10)
                setBackgroundColor(if (active) 0xFF1a3a1a.toInt() else 0xFF111111.toInt())
                setTextColor(if (active) 0xFF00ff88.toInt() else 0xFF888888.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 8, 8); layoutParams = lp
                setOnClickListener {
                    currentProfile = currentProfile.copy(voiceName = v.name)
                    renderVoiceGrid()
                }
            }
            voiceGrid.addView(btn)
        }
    }

    private fun buildPresets() {
        presetsScroll.removeAllViews()
        VoiceProfile.PRESETS.forEach { preset ->
            val btn = Button(requireContext()).apply {
                text = "${preset.emoji}\n${preset.name}"; textSize = 11f
                setBackgroundColor(0xFF1a2a1a.toInt()); setTextColor(0xFF00ff88.toInt())
                setPadding(20, 12, 20, 12)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 10, 8); layoutParams = lp
                setOnClickListener {
                    loadProfileToUI(preset.copy(id = currentProfile.id, name = currentProfile.name, voiceName = currentProfile.voiceName))
                }
            }
            presetsScroll.addView(btn)
        }
    }

    private fun buildCommentaryEditor() {
        val v = view ?: return
        val container = v.findViewById<LinearLayout>(R.id.commentary_pools_container)
        container.removeAllViews()

        currentProfile.commentaryPools.forEachIndexed { idx, pool ->
            container.addView(buildPoolCard(pool, idx))
        }

        v.findViewById<Button>(R.id.btn_add_pre_comment).setOnClickListener {
            showAddPoolDialog("pre")
        }
        v.findViewById<Button>(R.id.btn_add_post_comment).setOnClickListener {
            showAddPoolDialog("post")
        }
    }

    private fun buildPoolCard(pool: CommentaryPool, idx: Int): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111111.toInt()); setPadding(14, 12, 14, 12)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 6); layoutParams = lp
        }

        // Header: position badge + condition label + delete
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val posBadge = TextView(requireContext()).apply {
            text = if (pool.position == "pre") "BEFORE" else "AFTER"
            textSize = 10f; setPadding(10, 4, 10, 4)
            setBackgroundColor(if (pool.position == "pre") 0xFF1a3a2a.toInt() else 0xFF1a2a3a.toInt())
            setTextColor(if (pool.position == "pre") 0xFF00ff88.toInt() else 0xFF00ccff.toInt())
        }
        val condLabel = TextView(requireContext()).apply {
            text = "  ${pool.condition.label()}"
            textSize = 12f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val freqLabel = TextView(requireContext()).apply {
            text = "${pool.frequency}%"; textSize = 11f; setTextColor(0xFFffaa44.toInt())
            setPadding(0, 0, 10, 0)
        }
        val btnDel = Button(requireContext()).apply {
            text = "✕"; textSize = 11f; setTextColor(0xFFff4444.toInt())
            setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(12, 4, 12, 4)
            setOnClickListener {
                val updated = currentProfile.commentaryPools.toMutableList().also { it.removeAt(idx) }
                currentProfile = currentProfile.copy(commentaryPools = updated)
                buildCommentaryEditor()
            }
        }
        header.addView(posBadge); header.addView(condLabel); header.addView(freqLabel); header.addView(btnDel)
        card.addView(header)

        // Frequency slider
        val seekFreq = SeekBar(requireContext()).apply {
            max = 100; progress = pool.frequency
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFffaa44.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFffaa44.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8); layoutParams = lp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser) {
                        freqLabel.text = "$pv%"
                        val updated = currentProfile.commentaryPools.toMutableList()
                        updated[idx] = pool.copy(frequency = pv)
                        currentProfile = currentProfile.copy(commentaryPools = updated)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(seekFreq)

        // Lines
        val linesContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val lines = pool.lines.toMutableList()

        fun renderLines() {
            linesContainer.removeAllViews()
            lines.forEachIndexed { li, line ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, 3, 0, 3)
                }
                val et = android.widget.EditText(requireContext()).apply {
                    setText(line); hint = "Say something..."; textSize = 12f
                    setTextColor(0xFFcccccc.toInt()); setHintTextColor(0xFF444444.toInt())
                    setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(10, 6, 10, 6)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) {
                            lines[li] = s.toString()
                            val updatedPools = currentProfile.commentaryPools.toMutableList()
                            updatedPools[idx] = pool.copy(lines = lines.toList())
                            currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                        }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }
                val del = Button(requireContext()).apply {
                    text = "✕"; textSize = 10f; setTextColor(0xFFff4444.toInt())
                    setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(10, 4, 10, 4)
                    setOnClickListener {
                        lines.removeAt(li)
                        val updatedPools = currentProfile.commentaryPools.toMutableList()
                        updatedPools[idx] = pool.copy(lines = lines.toList())
                        currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                        renderLines()
                    }
                }
                row.addView(et); row.addView(del); linesContainer.addView(row)
            }
        }
        renderLines()
        card.addView(linesContainer)

        val btnAddLine = Button(requireContext()).apply {
            text = "+ line"; textSize = 11f; setTextColor(0xFF00ff88.toInt())
            setBackgroundColor(0xFF1a2a1a.toInt()); setPadding(14, 4, 14, 4)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 6, 0, 0); layoutParams = lp
            setOnClickListener {
                lines.add("")
                val updatedPools = currentProfile.commentaryPools.toMutableList()
                updatedPools[idx] = pool.copy(lines = lines.toList())
                currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                renderLines()
            }
        }
        card.addView(btnAddLine)
        return card
    }

    private fun showAddPoolDialog(position: String) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10)
        }

        val condTypes = arrayOf("always", "app", "sender", "keyword", "time", "flood")
        val condLabels = arrayOf("Always", "App (e.g. whatsapp)", "Sender name", "Text keyword (e.g. ?)", "Time range (e.g. 22-06)", "After N notifications")
        var selectedType = "always"

        val typeSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, condLabels)
        }
        val valueInput = android.widget.EditText(ctx).apply {
            hint = "Condition value (if needed)"; textSize = 13f
            visibility = View.GONE
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedType = condTypes[pos]
                valueInput.visibility = if (selectedType == "always") View.GONE else View.VISIBLE
                valueInput.hint = condLabels[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        layout.addView(typeSpinner)
        layout.addView(valueInput)

        AlertDialog.Builder(ctx)
            .setTitle("Add ${if (position == "pre") "before" else "after"} commentary pool")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val condition = CommentaryCondition(selectedType, valueInput.text.toString().trim())
                val newPool = CommentaryPool(position = position, condition = condition, lines = listOf(""), frequency = 40)
                val updated = currentProfile.commentaryPools.toMutableList().also { it.add(newPool) }
                currentProfile = currentProfile.copy(commentaryPools = updated)
                buildCommentaryEditor()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildGimmicksEditor() {
        gimmicksContainer.removeAllViews()
        val allTypes = listOf("giggle","sigh","huh","mmm","woah","ugh","aww","gasp","yawn","hmm","laugh","tsk")
        allTypes.forEach { type ->
            val existing = currentProfile.gimmicks.find { it.type == type }
            val freq = existing?.frequency ?: 0
            val pos = existing?.position ?: "RANDOM"

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF111111.toInt())
                setPadding(16, 12, 16, 12)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 4); layoutParams = lp
            }

            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val label = TextView(requireContext()).apply {
                text = type.replaceFirstChar { it.uppercase() }
                textSize = 13f; setTextColor(0xFFcccccc.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val freqLabel = TextView(requireContext()).apply {
                text = "$freq%"; textSize = 12f; setTextColor(0xFF00ff88.toInt())
                setPadding(0, 0, 8, 0); minWidth = 44
            }

            val seekFreq = SeekBar(requireContext()).apply {
                max = 100; progress = freq
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF00ff88.toInt())
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00ff88.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                        if (fromUser) {
                            freqLabel.text = "$v%"
                            updateGimmick(type, v, pos)
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            topRow.addView(label); topRow.addView(freqLabel); topRow.addView(seekFreq)
            row.addView(topRow)

            // Position selector
            val posRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 0)
            }
            listOf("START", "MID", "END", "RANDOM").forEach { p ->
                val pb = Button(requireContext()).apply {
                    text = p.lowercase(); textSize = 9f
                    setBackgroundColor(if (p == pos) 0xFF223322.toInt() else 0xFF1a1a1a.toInt())
                    setTextColor(if (p == pos) 0xFF00ff88.toInt() else 0xFF444444.toInt())
                    setPadding(12, 4, 12, 4)
                    val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp2.setMargins(0, 0, 6, 0); layoutParams = lp2
                    setOnClickListener {
                        updateGimmick(type, seekFreq.progress, p)
                        buildGimmicksEditor()
                    }
                }
                posRow.addView(pb)
            }
            row.addView(posRow)
            gimmicksContainer.addView(row)
        }
    }

    private fun updateGimmick(type: String, frequency: Int, position: String) {
        val updated = currentProfile.gimmicks.toMutableList()
        updated.removeAll { it.type == type }
        if (frequency > 0) updated.add(GimmickConfig(type, frequency, position))
        currentProfile = currentProfile.copy(gimmicks = updated)
    }

    private fun setupProfileSpinner() {
        profileSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, profiles.map { "${it.emoji} ${it.name}" })
        val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
        profileSpinner.setSelection(idx)
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                loadProfileToUI(profiles[pos])
                activeProfileId = profiles[pos].id
                prefs.edit().putString("active_profile_id", activeProfileId).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        btnTest.setOnClickListener {
            val p = readProfileFromUI()
            val rules = loadWordingRules()
            val text = txtPreview.text.toString().ifBlank { "Hello! This is how I sound." }
            NotificationReaderService.instance?.testSpeak(text, p, rules)
                ?: Toast.makeText(context, "Service not running — grant permission first.", Toast.LENGTH_SHORT).show()
        }
        btnSave.setOnClickListener {
            val p = readProfileFromUI()
            val idx = profiles.indexOfFirst { it.id == p.id }
            if (idx >= 0) profiles[idx] = p else profiles.add(p)
            VoiceProfile.saveAll(profiles, prefs)
            Toast.makeText(context, "Saved: ${p.name}", Toast.LENGTH_SHORT).show()
        }
        btnNew.setOnClickListener {
            val et = EditText(requireContext()).apply { hint = "Profile name" }
            AlertDialog.Builder(requireContext()).setTitle("New Profile").setView(et)
                .setPositiveButton("Create") { _, _ ->
                    val name = et.text.toString().ifBlank { "Profile ${profiles.size + 1}" }
                    val p = VoiceProfile(name = name)
                    profiles.add(p); VoiceProfile.saveAll(profiles, prefs)
                    setupProfileSpinner(); profileSpinner.setSelection(profiles.size - 1)
                }.setNegativeButton("Cancel", null).show()
        }
        btnDelete.setOnClickListener {
            if (profiles.size <= 1) { Toast.makeText(context, "Can't delete last profile", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(requireContext()).setTitle("Delete ${currentProfile.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    profiles.removeAll { it.id == currentProfile.id }
                    VoiceProfile.saveAll(profiles, prefs); setupProfileSpinner()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun loadProfileToUI(p: VoiceProfile) {
        currentProfile = p
        seekPitch.max = 150; seekPitch.progress = ((p.pitch * 100).toInt() - 50).coerceIn(0, 150)
        tvPitch.text = "Pitch: %.2f".format(p.pitch)
        seekSpeed.max = 250; seekSpeed.progress = ((p.speed * 100).toInt() - 50).coerceIn(0, 250)
        tvSpeed.text = "Speed: %.2f".format(p.speed)
        seekBreathInt.max = 100; seekBreathInt.progress = p.breathIntensity; tvBreathInt.text = "Intensity: ${p.breathIntensity}"
        seekBreathCurve.max = 100; seekBreathCurve.progress = (p.breathCurvePosition * 100).toInt(); tvBreathCurve.text = "Curve: ${(p.breathCurvePosition*100).toInt()}%"
        seekBreathPause.max = 100; seekBreathPause.progress = p.breathPause; tvBreathPause.text = "Pause: ${p.breathPause}"
        seekStutterInt.max = 100; seekStutterInt.progress = p.stutterIntensity; tvStutterInt.text = "Intensity: ${p.stutterIntensity}"
        seekStutterPos.max = 100; seekStutterPos.progress = (p.stutterPosition*100).toInt(); tvStutterPos.text = "Position: ${(p.stutterPosition*100).toInt()}%"
        seekStutterFreq.max = 100; seekStutterFreq.progress = p.stutterFrequency; tvStutterFreq.text = "Frequency: ${p.stutterFrequency}%"
        seekStutterPause.max = 100; seekStutterPause.progress = p.stutterPause; tvStutterPause.text = "Pause: ${p.stutterPause}"
        seekIntonInt.max = 100; seekIntonInt.progress = p.intonationIntensity; tvIntonInt.text = "Intensity: ${p.intonationIntensity}"
        seekIntonVar.max = 100; seekIntonVar.progress = (p.intonationVariation*100).toInt(); tvIntonVar.text = "Variation: ${(p.intonationVariation*100).toInt()}%"

        attachSeek(seekPitch)      { tvPitch.text = "Pitch: %.2f".format((it+50)/100f) }
        attachSeek(seekSpeed)      { tvSpeed.text = "Speed: %.2f".format((it+50)/100f) }
        attachSeek(seekBreathInt)  { tvBreathInt.text = "Intensity: $it" }
        attachSeek(seekBreathCurve){ tvBreathCurve.text = "Curve: $it%" }
        attachSeek(seekBreathPause){ tvBreathPause.text = "Pause: $it" }
        attachSeek(seekStutterInt) { tvStutterInt.text = "Intensity: $it" }
        attachSeek(seekStutterPos) { tvStutterPos.text = "Position: $it%" }
        attachSeek(seekStutterFreq){ tvStutterFreq.text = "Frequency: $it%" }
        attachSeek(seekStutterPause){tvStutterPause.text = "Pause: $it" }
        attachSeek(seekIntonInt)   { tvIntonInt.text = "Intensity: $it" }
        attachSeek(seekIntonVar)   { tvIntonVar.text = "Variation: $it%" }

        buildCommentaryEditor()
        buildGimmicksEditor()
        renderVoiceGrid()
    }

    private fun readProfileFromUI() = currentProfile.copy(
        pitch               = (seekPitch.progress + 50) / 100f,
        speed               = (seekSpeed.progress + 50) / 100f,
        breathIntensity     = seekBreathInt.progress,
        breathCurvePosition = seekBreathCurve.progress / 100f,
        breathPause         = seekBreathPause.progress,
        stutterIntensity    = seekStutterInt.progress,
        stutterPosition     = seekStutterPos.progress / 100f,
        stutterFrequency    = seekStutterFreq.progress,
        stutterPause        = seekStutterPause.progress,
        intonationIntensity = seekIntonInt.progress,
        intonationVariation = seekIntonVar.progress / 100f
    )

    private fun loadWordingRules(): List<Pair<String, String>> {
        val json = prefs.getString("wording_rules", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { val o = arr.getJSONObject(it); Pair(o.optString("find"), o.optString("replace")) }
        } catch (e: Exception) { emptyList() }
    }

    private fun attachSeek(s: SeekBar, onChange: (Int) -> Unit) {
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onDestroyView() { ttsInit?.shutdown(); super.onDestroyView() }
}
