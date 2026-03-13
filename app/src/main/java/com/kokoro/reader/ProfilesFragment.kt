package com.kokoro.reader

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class ProfilesFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    private var currentProfile = VoiceProfile()
    private var genderFilter = "All"
    private var languageFilter = "All"

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
    private lateinit var profileGrid: LinearLayout
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

        setupCollapsibleSections(v)
        setupProfileSpinner()
        renderProfileGrid()
        buildPresets()
        buildFilterButtons()
        renderVoiceGrid()
        setupButtons()
        buildGimmicksEditor()
        loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles[0])
    }

    private fun setupCollapsibleSections(v: View) {
        setupCollapsibleSection(v, R.id.label_pitch_speed, R.id.section_pitch_speed, "// PITCH & SPEED")
        setupCollapsibleSection(v, R.id.label_breathiness, R.id.section_breathiness, "// BREATHINESS")
        setupCollapsibleSection(v, R.id.label_stuttering, R.id.section_stuttering, "// STUTTERING")
        setupCollapsibleSection(v, R.id.label_intonation, R.id.section_intonation, "// INTONATION")
    }

    private fun setupCollapsibleSection(v: View, labelId: Int, sectionId: Int, title: String) {
        val label = v.findViewById<TextView>(labelId)
        val section = v.findViewById<View>(sectionId)
        label.contentDescription = "$title, collapsed. Tap to expand."
        label.setOnClickListener {
            val expanded = section.visibility == View.VISIBLE
            section.visibility = if (expanded) View.GONE else View.VISIBLE
            label.text = "${if (expanded) "▸" else "▾"} $title"
            label.contentDescription = if (expanded) "$title, collapsed. Tap to expand."
                else "$title, expanded. Tap to collapse."
        }
    }

    private fun bindViews(v: View) {
        profileSpinner  = v.findViewById(R.id.spinner_profile)
        profileGrid     = v.findViewById(R.id.profile_grid)
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
        if (view == null) return
        genderRow.removeAllViews()
        nationRow.removeAllViews()

        // Merge genders from both engines
        val genders = (KokoroVoices.genders() + PiperVoices.genders()).distinct()
        genders.forEach { g ->
            genderRow.addView(filterBtn(g, genderFilter == g) {
                genderFilter = g; buildFilterButtons(); renderVoiceGrid()
            })
        }

        // Merge languages from both engines
        val languages = (KokoroVoices.languages() + PiperVoices.languages()).distinct()
        languages.forEach { l ->
            nationRow.addView(filterBtn(l, languageFilter == l) {
                languageFilter = l; buildFilterButtons(); renderVoiceGrid()
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

    // ── Voice grid rendering ───────────────────────────────────────────────

    private fun renderVoiceGrid() {
        voiceGrid.removeAllViews()
        val ctx = requireContext()

        // ── KOKORO section ──────────────────────────────────────────────────
        voiceGrid.addView(buildSectionHeader("KOKORO", "Multi-voice model  ·  11 speakers", 0xFF00ff88.toInt()))

        if (!VoiceDownloadManager.isModelReady(ctx)) {
            voiceGrid.addView(buildDownloadBanner())
        } else {
            renderKokoroVoices()
        }

        // ── Spacing ─────────────────────────────────────────────────────────
        voiceGrid.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).also { it.setMargins(0, 20, 0, 0) }
        })

        // ── PIPER section ───────────────────────────────────────────────────
        voiceGrid.addView(buildSectionHeader("PIPER", "Per-voice download  ·  ~40MB each", 0xFF88ccff.toInt()))
        renderPiperVoices()
    }

    private fun buildSectionHeader(title: String, subtitle: String, accent: Int): android.view.View {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 10); layoutParams = lp
        }

        // Accent bar + title row
        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        titleRow.addView(android.view.View(ctx).apply {
            setBackgroundColor(accent)
            layoutParams = LinearLayout.LayoutParams(4, 36).also { it.setMargins(0, 0, 10, 0) }
        })
        titleRow.addView(TextView(ctx).apply {
            text = title; textSize = 14f; setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        container.addView(titleRow)

        container.addView(TextView(ctx).apply {
            text = subtitle; textSize = 10f; setTextColor(0xFF555555.toInt())
            setPadding(14, 2, 0, 0)
        })

        return container
    }

    private fun renderKokoroVoices() {
        val ctx = requireContext()
        val filtered = KokoroVoices.ALL.filter { v ->
            val gOk = genderFilter == "All" || v.gender == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }

        if (filtered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No Kokoro voices match filter."))
            return
        }

        filtered.groupBy { it.language }.entries.sortedBy { it.key }.forEach { (lang, voices) ->
            voiceGrid.addView(langHeader(lang, voices.size, 0xFF446644.toInt()))
            addVoiceRows(voices.map { v ->
                buildVoiceCard(
                    name = v.displayName, icon = v.genderIcon, iconColor = v.genderColor,
                    badge = "${v.flagEmoji} ${v.nationality}", voiceId = v.id,
                    active = currentProfile.voiceName == v.id,
                    accent = 0xFF00ff88.toInt(), enabled = true,
                    onClick = { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
                )
            })
        }
    }

    private fun renderPiperVoices() {
        val ctx = requireContext()
        val filtered = PiperVoices.ALL.filter { v ->
            val gOk = genderFilter == "All" || v.gender == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }

        if (filtered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No Piper voices match filter."))
            return
        }

        filtered.groupBy { it.language }.entries.sortedBy { it.key }.forEach { (lang, voices) ->
            voiceGrid.addView(langHeader(lang, voices.size, 0xFF446688.toInt()))
            addVoiceRows(voices.map { v -> buildPiperCardNew(v) })
        }
    }

    private fun buildPiperCardNew(v: PiperVoice): android.view.View {
        val ctx = requireContext()
        val state = PiperDownloadManager.getState(ctx, v.id)
        val ready = state == PiperDownloadManager.State.READY
        val active = currentProfile.voiceName == v.id

        val statusText: String
        val statusColor: Int
        val statusClickable: Boolean

        when (state) {
            PiperDownloadManager.State.READY -> {
                statusText = "ready"
                statusColor = 0xFF446644.toInt()
                statusClickable = false
            }
            PiperDownloadManager.State.DOWNLOADING -> {
                val pct = PiperDownloadManager.getProgress(v.id)
                statusText = if (pct < 0) "extracting..." else "$pct%"
                statusColor = 0xFFffcc00.toInt()
                statusClickable = false
            }
            PiperDownloadManager.State.ERROR -> {
                statusText = "retry"
                statusColor = 0xFFff4444.toInt()
                statusClickable = true
            }
            PiperDownloadManager.State.NOT_DOWNLOADED -> {
                statusText = "~${v.sizeMb}MB"
                statusColor = 0xFF88ccff.toInt()
                statusClickable = true
            }
        }

        val card = buildVoiceCard(
            name = v.displayName, icon = v.genderIcon,
            iconColor = if (ready) v.genderColor else 0xFF444444.toInt(),
            badge = "${v.flagEmoji} ${v.quality}", voiceId = v.id,
            active = active, accent = 0xFF88ccff.toInt(), enabled = ready,
            onClick = if (ready) {
                { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
            } else null
        )

        // Add status / download indicator
        val statusView = TextView(ctx).apply {
            tag = "piper_status_${v.id}"
            text = if (!ready && state != PiperDownloadManager.State.DOWNLOADING
                && state != PiperDownloadManager.State.ERROR) "⬇ $statusText" else statusText
            textSize = 10f; gravity = android.view.Gravity.CENTER
            setTextColor(statusColor)
            setPadding(0, 4, 0, 0)
            if (statusClickable) {
                setOnClickListener { startPiperDownload(v.id) }
            }
        }
        (card as LinearLayout).addView(statusView)
        return card
    }

    /**
     * Shared card builder for both Kokoro and Piper voices.
     */
    private fun buildVoiceCard(
        name: String, icon: String, iconColor: Int, badge: String, voiceId: String,
        active: Boolean, accent: Int, enabled: Boolean, onClick: (() -> Unit)?
    ): android.view.View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding((10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            layoutParams = lp

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                if (active) {
                    setColor(0xFF0d1a1a.toInt())
                    setStroke((2 * dp).toInt(), accent)
                } else {
                    setColor(if (enabled) 0xFF151515.toInt() else 0xFF0e0e0e.toInt())
                    setStroke(1, 0xFF222222.toInt())
                }
            }

            if (onClick != null) setOnClickListener { onClick() }

            // Gender icon
            addView(TextView(ctx).apply {
                text = icon; textSize = 22f; gravity = android.view.Gravity.CENTER
                setTextColor(iconColor)
            })

            // Name
            addView(TextView(ctx).apply {
                text = name; textSize = 13f; gravity = android.view.Gravity.CENTER
                setTextColor(when {
                    active  -> accent
                    enabled -> 0xFFdddddd.toInt()
                    else    -> 0xFF666666.toInt()
                })
                typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD
                           else android.graphics.Typeface.DEFAULT
                setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
            })

            // Badge (flag + nationality/quality)
            addView(TextView(ctx).apply {
                text = badge; textSize = 9f; gravity = android.view.Gravity.CENTER
                setTextColor(if (active) accent.and(0x00FFFFFF).or(0x99000000.toInt())
                             else 0xFF555555.toInt())
            })
        }
    }

    private fun addVoiceRows(cards: List<android.view.View>) {
        val ctx = requireContext()
        val cols = 3
        cards.chunked(cols).forEach { rowCards ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowCards.forEach { row.addView(it) }
            // Fill remaining space in incomplete rows
            repeat(cols - rowCards.size) {
                row.addView(android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            voiceGrid.addView(row)
        }
    }

    private fun langHeader(lang: String, count: Int, color: Int): android.view.View {
        return TextView(requireContext()).apply {
            text = "${lang.uppercase()}  ($count)"
            textSize = 10f; setTextColor(color)
            setPadding(6, 14, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun emptyLabel(msg: String): android.view.View {
        return TextView(requireContext()).apply {
            text = msg; setTextColor(0xFF446644.toInt()); textSize = 12f
            setPadding(6, 8, 0, 8)
        }
    }

    private fun startPiperDownload(voiceId: String) {
        val ctx = requireContext()
        PiperDownloadManager.onStateChange = { _, _ ->
            activity?.runOnUiThread {
                if (isAdded) renderVoiceGrid()
            }
        }
        PiperDownloadManager.onProgress = { id, pct ->
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val tv = voiceGrid.findViewWithTag<TextView>("piper_status_$id")
                tv?.text = if (pct < 0) "extracting..." else "$pct%"
            }
        }
        PiperDownloadManager.downloadVoice(ctx, voiceId)
        renderVoiceGrid()
    }

    private fun buildDownloadBanner(): android.view.View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1a1a00.toInt()); setPadding(20, 16, 20, 16)
        }
        val state = VoiceDownloadManager.state

        when (state) {
            VoiceDownloadManager.State.NOT_DOWNLOADED, VoiceDownloadManager.State.ERROR -> {
                card.addView(TextView(ctx).apply {
                    text = if (state == VoiceDownloadManager.State.ERROR)
                        "⚠ Download failed: ${VoiceDownloadManager.errorMessage}"
                    else "🎙 No voice model installed"
                    textSize = 13f; setTextColor(0xFFffcc00.toInt()); setPadding(0, 0, 0, 8)
                })
                card.addView(TextView(ctx).apply {
                    text = "The Kokoro voice model (~${VoiceDownloadManager.MODEL_SIZE_MB}MB) needs to be downloaded once.\nWi-Fi recommended."
                    textSize = 12f; setTextColor(0xFF888888.toInt()); setPadding(0, 0, 0, 12)
                })
                card.addView(Button(ctx).apply {
                    text = "⬇ DOWNLOAD KOKORO VOICES"
                    setBackgroundColor(0xFF1a3a1a.toInt()); setTextColor(0xFF00ff88.toInt())
                    setOnClickListener {
                        VoiceDownloadManager.onProgress { pct ->
                            // Update only the progress text and bar, not the whole grid
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                updateDownloadProgress(pct)
                            }
                        }
                        VoiceDownloadManager.onStateChange { _ ->
                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread
                                buildFilterButtons(); renderVoiceGrid()
                            }
                        }
                        VoiceDownloadManager.downloadModel(ctx)
                        renderVoiceGrid()
                    }
                })
            }
            VoiceDownloadManager.State.DOWNLOADING -> {
                val pct = VoiceDownloadManager.progressPercent
                val statusText = TextView(ctx).apply {
                    tag = "download_status"
                    text = if (pct < 0) "⏳ Extracting model files..." else "⬇ Downloading voices: $pct%"
                    textSize = 13f; setTextColor(0xFF00ff88.toInt())
                }
                card.addView(statusText)
                val bar = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                    tag = "download_bar"
                    max = 100; progress = if (pct >= 0) pct else 100
                    isIndeterminate = pct < 0
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
                    lp.setMargins(0, 12, 0, 0); layoutParams = lp
                }
                card.addView(bar)
            }
            VoiceDownloadManager.State.READY -> { /* won't reach here */ }
        }
        return card
    }

    private fun updateDownloadProgress(pct: Int) {
        val statusView = voiceGrid.findViewWithTag<TextView>("download_status") ?: return
        val barView = voiceGrid.findViewWithTag<android.widget.ProgressBar>("download_bar") ?: return
        statusView.text = if (pct < 0) "⏳ Extracting model files..." else "⬇ Downloading voices: $pct%"
        barView.isIndeterminate = pct < 0
        if (pct >= 0) barView.progress = pct
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

        // type → display label pairs — both arrays derived from the same source to stay in sync
        val conditions = listOf(
            "always"            to "Always",
            "sender_human"      to "From a human",
            "source_personal"   to "Personal message",
            "source_game"       to "Game notification",
            "source_financial"  to "Financial / bank",
            "source_service"    to "Delivery / service",
            "intent_request"    to "Contains question or request",
            "intent_alert"      to "Urgent alert",
            "intent_greeting"   to "Greeting",
            "intent_plea"       to "Pleading",
            "urgency_expiring"  to "Phone call / expiring",
            "urgency_real"      to "Genuinely urgent",
            "stakes_emotional"  to "Emotional stakes",
            "stakes_financial"  to "Financial stakes",
            "stakes_high"       to "High stakes",
            "stakes_low"        to "Low / no stakes",
            "warmth_high"       to "Warm message",
            "warmth_distressed" to "Distressed sender",
            "intensity_high"    to "High intensity",
            "intensity_low"     to "Low intensity",
            "time_night"        to "Night time (10 pm–6 am)",
            "time_morning"      to "Morning (7 am–10 am)",
            "flooded"           to "Many notifications today"
        )
        var selectedType = conditions[0].first

        val typeSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item,
                conditions.map { it.second })
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedType = conditions[pos].first
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        layout.addView(typeSpinner)

        AlertDialog.Builder(ctx)
            .setTitle("Add ${if (position == "pre") "before" else "after"} commentary pool")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val condition = CommentaryCondition(selectedType)
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

    // ── Profile grid (cards) ───────────────────────────────────────────────

    private fun renderProfileGrid() {
        profileGrid.removeAllViews()
        val ctx = requireContext()
        val columns = 3
        var row: LinearLayout? = null

        profiles.forEachIndexed { index, p ->
            if (index % columns == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 0, 0, 8) }
                }
                profileGrid.addView(row)
            }

            val isActive = p.id == activeProfileId
            val card = buildProfileCard(p, isActive)
            row!!.addView(card)
        }

        // Fill remaining cells in last row with spacers
        val remainder = profiles.size % columns
        if (remainder != 0) {
            for (i in remainder until columns) {
                row!!.addView(android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
        }
    }

    private fun buildProfileCard(p: VoiceProfile, isActive: Boolean): android.view.View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(8, 16, 8, 16)
            setBackgroundColor(if (isActive) 0xFF1a3a1a.toInt() else 0xFF151515.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(4, 0, 4, 0)
            }

            // Tap to select
            setOnClickListener {
                activeProfileId = p.id
                prefs.edit().putString("active_profile_id", activeProfileId).apply()
                loadProfileToUI(p)
                renderProfileGrid()
                val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
                profileSpinner.setSelection(idx)
            }

            // Long-press to rename
            setOnLongClickListener {
                showRenameDialog(p)
                true
            }
        }

        // Emoji
        card.addView(TextView(ctx).apply {
            text = p.emoji
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        })

        // Profile name (main label)
        card.addView(TextView(ctx).apply {
            text = p.name
            textSize = 11f
            setTextColor(if (isActive) 0xFF00ff88.toInt() else 0xFFaaaaaa.toInt())
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        // Voice name (small, below)
        val voiceLabel = p.voiceAlias.ifBlank { p.voiceName }
        if (voiceLabel.isNotBlank()) {
            card.addView(TextView(ctx).apply {
                text = voiceLabel
                textSize = 9f
                setTextColor(if (isActive) 0xFF448844.toInt() else 0xFF555555.toInt())
                gravity = android.view.Gravity.CENTER
                maxLines = 1
            })
        }

        // Active indicator
        if (isActive) {
            card.addView(android.view.View(ctx).apply {
                setBackgroundColor(0xFF00ff88.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 3
                ).also { it.setMargins(12, 8, 12, 0) }
            })
        }

        return card
    }

    private fun showRenameDialog(p: VoiceProfile) {
        val ctx = context ?: return
        val et = EditText(ctx).apply {
            setText(p.name)
            hint = "Profile name"
            selectAll()
        }
        AlertDialog.Builder(ctx)
            .setTitle("Rename Profile")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val newName = et.text.toString().trim().ifBlank { p.name }
                val updated = p.copy(name = newName)
                val idx = profiles.indexOfFirst { it.id == p.id }
                if (idx >= 0) profiles[idx] = updated
                if (p.id == currentProfile.id) currentProfile = updated
                VoiceProfile.saveAll(profiles, prefs)
                setupProfileSpinner()
                renderProfileGrid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupProfileSpinner() {
        profileSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, profiles.map { p ->
                val alias = p.voiceAlias
                if (alias.isNotBlank()) "${p.emoji} ${p.name} ($alias)" else "${p.emoji} ${p.name}"
            })
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
            renderProfileGrid()
            Toast.makeText(context, "Saved: ${p.name}", Toast.LENGTH_SHORT).show()
        }
        btnNew.setOnClickListener {
            val et = EditText(requireContext()).apply { hint = "Profile name" }
            AlertDialog.Builder(requireContext()).setTitle("New Profile").setView(et)
                .setPositiveButton("Create") { _, _ ->
                    val name = et.text.toString().ifBlank { "Profile ${profiles.size + 1}" }
                    val p = VoiceProfile(name = name)
                    profiles.add(p); VoiceProfile.saveAll(profiles, prefs)
                    activeProfileId = p.id
                    prefs.edit().putString("active_profile_id", activeProfileId).apply()
                    loadProfileToUI(p)
                    setupProfileSpinner(); profileSpinner.setSelection(profiles.size - 1)
                    renderProfileGrid()
                }.setNegativeButton("Cancel", null).show()
        }
        btnDelete.setOnClickListener {
            if (profiles.size <= 1) { Toast.makeText(context, "Can't delete last profile", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(requireContext()).setTitle("Delete ${currentProfile.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    profiles.removeAll { it.id == currentProfile.id }
                    VoiceProfile.saveAll(profiles, prefs)
                    activeProfileId = profiles[0].id
                    prefs.edit().putString("active_profile_id", activeProfileId).apply()
                    loadProfileToUI(profiles[0])
                    setupProfileSpinner()
                    renderProfileGrid()
                }.setNegativeButton("Cancel", null).show()
        }
        view?.findViewById<Button>(R.id.btn_rename_voice)?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val et = EditText(ctx).apply {
                hint = "Voice nickname (leave empty to clear)"
                setText(currentProfile.voiceAlias)
            }
            AlertDialog.Builder(ctx)
                .setTitle("Rename Voice")
                .setMessage("Set a nickname for this voice in the current profile. This won't change the original voice name.")
                .setView(et)
                .setPositiveButton("Save") { _, _ ->
                    val alias = et.text.toString().trim()
                    currentProfile = currentProfile.copy(voiceAlias = alias)
                    val idx = profiles.indexOfFirst { it.id == currentProfile.id }
                    if (idx >= 0) profiles[idx] = currentProfile
                    VoiceProfile.saveAll(profiles, prefs)
                    setupProfileSpinner()
                    renderProfileGrid()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        view?.findViewById<Button>(R.id.btn_stop)?.setOnClickListener {
            NotificationReaderService.instance?.stopSpeaking()
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

    override fun onDestroyView() { super.onDestroyView() }
}
