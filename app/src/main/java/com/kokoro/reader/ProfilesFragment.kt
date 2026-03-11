package com.kokoro.reader

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class ProfilesFragment : Fragment() {

    private lateinit var prefs: android.content.SharedPreferences
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    private var currentProfile = VoiceProfile()
    private var genderFilter = "All"
    private var languageFilter = "All"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var viewsBound = false

    private var profileSpinner: Spinner? = null
    private var txtPreview: EditText? = null
    private var btnTest: Button? = null
    private var btnStop: Button? = null
    private var btnSave: Button? = null
    private var btnDelete: Button? = null
    private var btnNew: Button? = null
    private var btnResetSettings: Button? = null
    private var btnRenameVoice: Button? = null
    private var seekPitch: SeekBar? = null;  private var tvPitch: TextView? = null
    private var seekSpeed: SeekBar? = null;  private var tvSpeed: TextView? = null
    private var seekBreathInt: SeekBar? = null;   private var tvBreathInt: TextView? = null
    private var seekBreathCurve: SeekBar? = null; private var tvBreathCurve: TextView? = null
    private var seekBreathPause: SeekBar? = null; private var tvBreathPause: TextView? = null
    private var seekStutterInt: SeekBar? = null;  private var tvStutterInt: TextView? = null
    private var seekStutterPos: SeekBar? = null;  private var tvStutterPos: TextView? = null
    private var seekStutterFreq: SeekBar? = null; private var tvStutterFreq: TextView? = null
    private var seekStutterPause: SeekBar? = null; private var tvStutterPause: TextView? = null
    private var seekIntonInt: SeekBar? = null;    private var tvIntonInt: TextView? = null
    private var seekIntonVar: SeekBar? = null;    private var tvIntonVar: TextView? = null
    private var voiceGrid: LinearLayout? = null
    private var presetsScroll: LinearLayout? = null
    private var gimmicksContainer: LinearLayout? = null
    private var genderRow: LinearLayout? = null
    private var nationRow: LinearLayout? = null
    private var tvEngineStatus: TextView? = null
    private var selectedVoiceBanner: LinearLayout? = null
    private var tvSelectedVoiceIcon: TextView? = null
    private var tvSelectedVoiceName: TextView? = null
    private var tvSelectedVoiceDetail: TextView? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View? =
        try { i.inflate(R.layout.fragment_profiles, c, false) }
        catch (e: Exception) { Log.e("ProfilesFragment", "Layout inflation failed", e); null }

    override fun onViewCreated(v: View, s: Bundle?) {
        try {
            val ctx = context ?: return
            prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            bindViews(v)
            profiles = VoiceProfile.loadAll(prefs)
            activeProfileId = prefs.getString("active_profile_id", "") ?: ""
            if (profiles.isEmpty()) { profiles.add(VoiceProfile(name = "Default")); VoiceProfile.saveAll(profiles, prefs) }

            setupCollapsibleSections(v)
            setupProfileSpinner()
            buildPresets()
            buildFilterButtons()
            renderVoiceGrid()
            setupButtons()
            buildGimmicksEditor()
            loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: VoiceProfile(name = "Default"))

            // Start engine warm-up and show status
            setupEngineStatus()
        } catch (e: Exception) {
            Log.e("ProfilesFragment", "Error initializing voice profiles view", e)
            showErrorFallback(v, "Voice profiles failed to load: ${e.message}")
        }
    }

    private fun showErrorFallback(v: View, message: String) {
        try {
            val ctx = context ?: return
            val container = v.findViewById<ViewGroup>(R.id.voice_grid) ?: (v as? ViewGroup) ?: return
            container.removeAllViews()
            container.addView(TextView(ctx).apply {
                text = "⚠ $message\n\nTry restarting the app."
                setTextColor(0xFFff4444.toInt())
                textSize = 14f
                setPadding(20, 40, 20, 40)
            })
        } catch (e2: Exception) {
            Log.e("ProfilesFragment", "Error showing fallback UI", e2)
        }
    }

    private fun setupEngineStatus() {
        updateEngineStatusUI()
        SherpaEngine.onReadyCallback = {
            mainHandler.post {
                if (isAdded && view != null) {
                    try { updateEngineStatusUI() }
                    catch (e: Exception) { android.util.Log.w("ProfilesFragment", "Engine status update failed", e) }
                }
            }
        }
        // Refresh voice grid when a Piper voice finishes downloading
        PiperVoiceManager.downloadCallback = { _, _ ->
            mainHandler.post {
                if (isAdded && view != null) {
                    try { renderVoiceGrid() }
                    catch (e: Exception) { android.util.Log.w("ProfilesFragment", "Voice grid refresh failed", e) }
                }
            }
        }
        // Show status — engine initializes lazily on first synthesis
        if (!SherpaEngine.isReady) {
            tvEngineStatus?.text = "Engine will initialize on first use"
            tvEngineStatus?.setTextColor(0xFFffaa00.toInt())
        }
    }

    private fun updateEngineStatusUI() {
        if (!viewsBound || !isAdded || view == null) return
        val error = SherpaEngine.errorMessage
        when {
            SherpaEngine.isReady -> {
                tvEngineStatus?.text = "✓ Voice engine ready"
                tvEngineStatus?.setTextColor(0xFF00ff88.toInt())
            }
            error != null -> {
                tvEngineStatus?.text = "✗ Engine error: $error"
                tvEngineStatus?.setTextColor(0xFFff4444.toInt())
            }
            else -> {
                tvEngineStatus?.text = "⏳ Initializing voice engine…"
                tvEngineStatus?.setTextColor(0xFFffaa00.toInt())
            }
        }
    }

    private fun setupCollapsibleSections(v: View) {
        setupCollapsibleSection(v, R.id.label_pitch_speed, R.id.section_pitch_speed, "// PITCH & SPEED")
        setupCollapsibleSection(v, R.id.label_breathiness, R.id.section_breathiness, "// BREATHINESS")
        setupCollapsibleSection(v, R.id.label_stuttering, R.id.section_stuttering, "// STUTTERING")
        setupCollapsibleSection(v, R.id.label_intonation, R.id.section_intonation, "// INTONATION")
        setupCollapsibleSection(v, R.id.label_gimmicks, R.id.gimmicks_container,
            "// GIMMICKS  (giggle · sigh · huh · mmm · woah · ugh · aww · gasp · yawn · hmm · laugh · tsk)")
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
        txtPreview      = v.findViewById(R.id.txt_preview)
        btnTest         = v.findViewById(R.id.btn_test)
        btnStop         = v.findViewById(R.id.btn_stop)
        btnSave         = v.findViewById(R.id.btn_save)
        btnDelete       = v.findViewById(R.id.btn_delete)
        btnNew          = v.findViewById(R.id.btn_new_profile)
        btnResetSettings = v.findViewById(R.id.btn_reset_settings)
        btnRenameVoice  = v.findViewById(R.id.btn_rename_voice)
        voiceGrid       = v.findViewById(R.id.voice_grid)
        presetsScroll   = v.findViewById(R.id.layout_presets)
        gimmicksContainer = v.findViewById(R.id.gimmicks_container)
        genderRow       = v.findViewById(R.id.gender_filter_row)
        nationRow       = v.findViewById(R.id.nation_filter_row)
        tvEngineStatus  = v.findViewById(R.id.tv_engine_status)
        selectedVoiceBanner = v.findViewById(R.id.selected_voice_banner)
        tvSelectedVoiceIcon = v.findViewById(R.id.tv_selected_voice_icon)
        tvSelectedVoiceName = v.findViewById(R.id.tv_selected_voice_name)
        tvSelectedVoiceDetail = v.findViewById(R.id.tv_selected_voice_detail)
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
        viewsBound = true
    }

    private fun buildFilterButtons() {
        if (!viewsBound || !isAdded || view == null) return
        val ctx = context ?: return
        genderRow?.removeAllViews()
        nationRow?.removeAllViews()

        KokoroVoices.genders().forEach { g ->
            genderRow?.addView(filterBtn(ctx, g, genderFilter == g) {
                genderFilter = g; buildFilterButtons(); renderVoiceGrid()
            })
        }
        KokoroVoices.languages().forEach { l ->
            nationRow?.addView(filterBtn(ctx, l, languageFilter == l) {
                languageFilter = l; buildFilterButtons(); renderVoiceGrid()
            })
        }
    }

    private fun filterBtn(ctx: Context, label: String, active: Boolean, onClick: () -> Unit): Button {
        return Button(ctx).apply {
            text = label; textSize = 11f
            setBackgroundColor(if (active) 0xFF1a3a1a.toInt() else 0xFF111111.toInt())
            setTextColor(if (active) 0xFF00ff88.toInt() else 0xFF666666.toInt())
            setPadding(24, 10, 24, 10)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 8, 0); layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun updateSelectedVoiceBanner() {
        if (!viewsBound || !isAdded || view == null) return
        val voiceId = currentProfile.voiceName
        val alias = currentProfile.voiceAlias
        val kokoroVoice = KokoroVoices.byId(voiceId)
        val piperVoice = PiperVoiceCatalog.byId(voiceId)
        when {
            kokoroVoice != null -> {
                tvSelectedVoiceIcon?.text = kokoroVoice.genderIcon
                tvSelectedVoiceIcon?.setTextColor(kokoroVoice.genderColor)
                tvSelectedVoiceName?.text = if (alias.isNotBlank()) "$alias (${kokoroVoice.displayName})" else kokoroVoice.displayName
                tvSelectedVoiceDetail?.text = "${kokoroVoice.flagEmoji} ${kokoroVoice.nationality} · ${kokoroVoice.gender} · Kokoro"
                selectedVoiceBanner?.setBackgroundColor(0xFF0d2a0d.toInt())
            }
            piperVoice != null -> {
                tvSelectedVoiceIcon?.text = piperVoice.genderIcon
                tvSelectedVoiceIcon?.setTextColor(piperVoice.genderColor)
                tvSelectedVoiceName?.text = if (alias.isNotBlank()) "$alias (${piperVoice.displayName})" else piperVoice.displayName
                val status = if (piperVoice.bundled) "bundled" else {
                    val appCtx = context?.applicationContext
                    if (appCtx != null && VoiceDownloadManager.isDownloaded(appCtx, piperVoice.id)) "downloaded" else "not downloaded"
                }
                tvSelectedVoiceDetail?.text = "${piperVoice.flagEmoji} ${piperVoice.nationality} · ${piperVoice.quality} · Piper ($status)"
                selectedVoiceBanner?.setBackgroundColor(0xFF0d1a2a.toInt())
            }
            else -> {
                tvSelectedVoiceIcon?.text = "?"
                tvSelectedVoiceIcon?.setTextColor(0xFF666666.toInt())
                tvSelectedVoiceName?.text = if (alias.isNotBlank()) alias else voiceId.ifEmpty { "None selected" }
                tvSelectedVoiceDetail?.text = "Tap a voice below to select"
                selectedVoiceBanner?.setBackgroundColor(0xFF1a1a1a.toInt())
            }
        }
    }

    private fun renderVoiceGrid() {
        if (!viewsBound || !isAdded || view == null) return
        val ctx = context ?: return
        voiceGrid?.removeAllViews()

        // ── Filter Kokoro voices ──────────────────────────────────────────
        val kokoroFiltered = KokoroVoices.ALL.filter { v ->
            val gOk = genderFilter   == "All" || v.gender   == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }

        // ── Filter Piper voices ───────────────────────────────────────────
        val piperFiltered = PiperVoiceCatalog.ALL.filter { v ->
            val gOk = genderFilter   == "All" || v.gender   == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }

        if (kokoroFiltered.isEmpty() && piperFiltered.isEmpty()) {
            voiceGrid?.addView(TextView(ctx).apply {
                text = "No voices match filter."; setTextColor(0xFF446644.toInt()); textSize = 12f
            }); return
        }

        // ── Kokoro voices (grouped by language) ──────────────────────────
        if (kokoroFiltered.isNotEmpty()) {
            kokoroFiltered.groupBy { it.language }.entries.sortedBy { it.key }.forEach { (lang, voices) ->
                voiceGrid?.addView(TextView(ctx).apply {
                    text = "KOKORO · ${lang.uppercase()}  (${voices.size} bundled)"
                    textSize = 10f; setTextColor(0xFF446644.toInt())
                    setPadding(4, 14, 0, 6)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                renderVoiceCardRows(voices.map { v ->
                    VoiceCardData(v.id, v.genderIcon, v.genderColor, v.displayName,
                        "${v.flagEmoji} ${v.nationality}", "bundled", 0xFF00ff88.toInt(), true)
                })
            }
        }

        // ── Piper voices (grouped by language, bundled + downloadable) ──
        if (piperFiltered.isNotEmpty()) {
            piperFiltered.groupBy { it.language }.entries.sortedBy { it.key }.forEach { (lang, voices) ->
                // De-duplicate by name (show only recommended quality per voice name)
                val deduped = voices.groupBy { it.name }.map { (_, variants) ->
                    variants.find { it.quality == "medium" } ?: variants.firstOrNull()
                }.filterNotNull()

                val bundledCount = deduped.count { it.bundled || VoiceDownloadManager.isDownloaded(ctx, it.id) }
                val totalCount = deduped.size
                voiceGrid?.addView(TextView(ctx).apply {
                    text = "PIPER · ${lang.uppercase()}  ($bundledCount/$totalCount ready)"
                    textSize = 10f; setTextColor(0xFF446644.toInt())
                    setPadding(4, 14, 0, 6)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                renderVoiceCardRows(deduped.map { v ->
                    val state = VoiceDownloadManager.getState(ctx, v.id)
                    val isReady = state == VoiceDownloadManager.State.DOWNLOADED
                    val statusText = when (state) {
                        VoiceDownloadManager.State.DOWNLOADED -> if (v.bundled) "bundled" else "downloaded"
                        VoiceDownloadManager.State.DOWNLOADING -> "downloading…"
                        else -> "tap to download"
                    }
                    val accentColor = when (state) {
                        VoiceDownloadManager.State.DOWNLOADED -> 0xFF00ccff.toInt()
                        VoiceDownloadManager.State.DOWNLOADING -> 0xFFffaa00.toInt()
                        else -> 0xFF555555.toInt()
                    }
                    VoiceCardData(v.id, v.genderIcon, v.genderColor, v.displayName,
                        "${v.flagEmoji} ${v.nationality}", statusText, accentColor, isReady)
                })
            }
        }

        updateSelectedVoiceBanner()
    }

    private data class VoiceCardData(
        val voiceId: String, val genderIcon: String, val genderColor: Int,
        val displayName: String, val subtitle: String, val statusText: String,
        val accentColor: Int, val isReady: Boolean
    )

    private fun renderVoiceCardRows(cards: List<VoiceCardData>) {
        val ctx = context ?: return
        val chunkSize = 3
        cards.chunked(chunkSize).forEach { rowCards ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            rowCards.forEach { c ->
                val active = currentProfile.voiceName == c.voiceId
                val card = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(12, 16, 12, 16)
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(0, 0, 6, 6); layoutParams = lp
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (active) 0xFF0d2a0d.toInt() else 0xFF111111.toInt())
                        setStroke(if (active) 2 else 1, if (active) c.accentColor else 0xFF222222.toInt())
                        cornerRadius = 8f
                    }
                    setOnClickListener {
                        if (c.isReady) {
                            // Voice is available — select it
                            currentProfile = currentProfile.copy(voiceName = c.voiceId)
                            renderVoiceGrid()
                            // Pre-warm Piper engine for the selected voice to reduce test lag
                            if (PiperVoiceCatalog.byId(c.voiceId) != null) {
                                val appCtx = context?.applicationContext ?: return@setOnClickListener
                                Thread {
                                    SherpaEngine.preloadPiperVoice(appCtx, c.voiceId)
                                }.apply {
                                    name = "PiperPreload-${c.voiceId}"
                                    isDaemon = true
                                    start()
                                }
                            }
                        } else {
                            // Voice needs downloading — start download
                            val appCtx = context?.applicationContext ?: return@setOnClickListener
                            Thread {
                                val success = VoiceDownloadManager.download(appCtx, c.voiceId)
                                if (success) {
                                    // Auto-select the voice after download
                                    activity?.runOnUiThread {
                                        currentProfile = currentProfile.copy(voiceName = c.voiceId)
                                        renderVoiceGrid()
                                    }
                                } else {
                                    activity?.runOnUiThread { renderVoiceGrid() }
                                }
                            }.apply {
                                name = "VoiceDownload-${c.voiceId}"
                                isDaemon = true
                                start()
                            }
                            // Immediately refresh to show "downloading…" state
                            renderVoiceGrid()
                        }
                    }
                }
                card.addView(TextView(ctx).apply {
                    text = c.genderIcon; textSize = 22f; gravity = android.view.Gravity.CENTER
                    setTextColor(c.genderColor)
                })
                card.addView(TextView(ctx).apply {
                    text = c.displayName; textSize = 13f; gravity = android.view.Gravity.CENTER
                    setTextColor(if (active) c.accentColor else 0xFFcccccc.toInt())
                    typeface = android.graphics.Typeface.create("monospace",
                        if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                })
                card.addView(TextView(ctx).apply {
                    text = c.subtitle; textSize = 10f; gravity = android.view.Gravity.CENTER
                    setTextColor(if (active) c.accentColor else 0xFF446644.toInt())
                })
                card.addView(TextView(ctx).apply {
                    text = c.statusText; textSize = 8f; gravity = android.view.Gravity.CENTER
                    setTextColor(if (c.isReady) 0xFF336633.toInt() else 0xFF333333.toInt())
                })
                row.addView(card)
            }
            // Fill remaining slots with empty space
            repeat(chunkSize - rowCards.size) {
                row.addView(android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            voiceGrid?.addView(row)
        }
    }

    private fun buildPresets() {
        if (!viewsBound || !isAdded || view == null) return
        val ctx = context ?: return
        presetsScroll?.removeAllViews()
        VoiceProfile.PRESETS.forEach { preset ->
            val btn = Button(ctx).apply {
                text = "${preset.emoji}\n${preset.name}"; textSize = 11f
                setBackgroundColor(0xFF1a2a1a.toInt()); setTextColor(0xFF00ff88.toInt())
                setPadding(20, 12, 20, 12)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 10, 8); layoutParams = lp
                setOnClickListener {
                    loadProfileToUI(preset.copy(id = currentProfile.id, name = currentProfile.name, voiceName = currentProfile.voiceName, voiceAlias = currentProfile.voiceAlias))
                }
            }
            presetsScroll?.addView(btn)
        }
    }

    private fun buildCommentaryEditor() {
        val v = view ?: return
        val ctx = context ?: return
        if (!isAdded) return
        val container = v.findViewById<LinearLayout>(R.id.commentary_pools_container)
        container.removeAllViews()

        currentProfile.commentaryPools.forEachIndexed { idx, pool ->
            container.addView(buildPoolCard(ctx, pool, idx))
        }

        v.findViewById<Button>(R.id.btn_add_pre_comment).setOnClickListener {
            showAddPoolDialog("pre")
        }
        v.findViewById<Button>(R.id.btn_add_post_comment).setOnClickListener {
            showAddPoolDialog("post")
        }
    }

    private fun buildPoolCard(ctx: Context, pool: CommentaryPool, idx: Int): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111111.toInt()); setPadding(14, 12, 14, 12)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 6); layoutParams = lp
        }

        // Header: position badge + condition label + delete
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val posBadge = TextView(ctx).apply {
            text = if (pool.position == "pre") "BEFORE" else "AFTER"
            textSize = 10f; setPadding(10, 4, 10, 4)
            setBackgroundColor(if (pool.position == "pre") 0xFF1a3a2a.toInt() else 0xFF1a2a3a.toInt())
            setTextColor(if (pool.position == "pre") 0xFF00ff88.toInt() else 0xFF00ccff.toInt())
        }
        val condLabel = TextView(ctx).apply {
            text = "  ${pool.condition.label()}"
            textSize = 12f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val freqLabel = TextView(ctx).apply {
            text = "${pool.frequency}%"; textSize = 11f; setTextColor(0xFFffaa44.toInt())
            setPadding(0, 0, 10, 0)
        }
        val btnDel = Button(ctx).apply {
            text = "✕"; textSize = 11f; setTextColor(0xFFff4444.toInt())
            setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(12, 4, 12, 4)
            setOnClickListener {
                val pools = currentProfile.commentaryPools
                if (idx >= 0 && idx < pools.size) {
                    val updated = pools.toMutableList()
                    updated.removeAt(idx)
                    currentProfile = currentProfile.copy(commentaryPools = updated)
                    buildCommentaryEditor()
                }
            }
        }
        header.addView(posBadge); header.addView(condLabel); header.addView(freqLabel); header.addView(btnDel)
        card.addView(header)

        // Frequency slider
        val seekFreq = SeekBar(ctx).apply {
            max = 100; progress = pool.frequency
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFffaa44.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFffaa44.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8); layoutParams = lp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser && idx < currentProfile.commentaryPools.size) {
                        freqLabel.text = "$pv%"
                        val updated = currentProfile.commentaryPools.toMutableList()
                        if (idx < updated.size) {
                            updated[idx] = pool.copy(frequency = pv)
                            currentProfile = currentProfile.copy(commentaryPools = updated)
                        }
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(seekFreq)

        // Lines
        val linesContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val lines = pool.lines.toMutableList()

        fun renderLines() {
            linesContainer.removeAllViews()
            lines.forEachIndexed { li, line ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, 3, 0, 3)
                }
                val et = android.widget.EditText(ctx).apply {
                    setText(line); hint = "Say something..."; textSize = 12f
                    setTextColor(0xFFcccccc.toInt()); setHintTextColor(0xFF444444.toInt())
                    setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(10, 6, 10, 6)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) {
                            if (li >= 0 && li < lines.size) lines[li] = s.toString()
                            val updatedPools = currentProfile.commentaryPools.toMutableList()
                            if (idx >= 0 && idx < updatedPools.size) {
                                updatedPools[idx] = pool.copy(lines = lines.toList())
                                currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                            }
                        }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }
                val del = Button(ctx).apply {
                    text = "✕"; textSize = 10f; setTextColor(0xFFff4444.toInt())
                    setBackgroundColor(0xFF1a1a1a.toInt()); setPadding(10, 4, 10, 4)
                    setOnClickListener {
                        if (li >= 0 && li < lines.size) lines.removeAt(li)
                        val updatedPools = currentProfile.commentaryPools.toMutableList()
                        if (idx >= 0 && idx < updatedPools.size) {
                            updatedPools[idx] = pool.copy(lines = lines.toList())
                            currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                        }
                        renderLines()
                    }
                }
                row.addView(et); row.addView(del); linesContainer.addView(row)
            }
        }
        renderLines()
        card.addView(linesContainer)

        val btnAddLine = Button(ctx).apply {
            text = "+ line"; textSize = 11f; setTextColor(0xFF00ff88.toInt())
            setBackgroundColor(0xFF1a2a1a.toInt()); setPadding(14, 4, 14, 4)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 6, 0, 0); layoutParams = lp
            setOnClickListener {
                lines.add("")
                val updatedPools = currentProfile.commentaryPools.toMutableList()
                if (idx >= 0 && idx < updatedPools.size) {
                    updatedPools[idx] = pool.copy(lines = lines.toList())
                    currentProfile = currentProfile.copy(commentaryPools = updatedPools)
                }
                renderLines()
            }
        }
        card.addView(btnAddLine)
        return card
    }

    private fun showAddPoolDialog(position: String) {
        val ctx = context ?: return
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
                selectedType = conditions.getOrNull(pos)?.first ?: return
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        layout.addView(typeSpinner)

        try {
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
        } catch (e: Exception) {
            Log.w("ProfilesFragment", "Failed to show add pool dialog", e)
        }
    }

    private fun buildGimmicksEditor() {
        if (!viewsBound || !isAdded || view == null) return
        val ctx = context ?: return
        gimmicksContainer?.removeAllViews()
        val allTypes = listOf("giggle","sigh","huh","mmm","woah","ugh","aww","gasp","yawn","hmm","laugh","tsk")
        allTypes.forEach { type ->
            val existing = currentProfile.gimmicks.find { it.type == type }
            val freq = existing?.frequency ?: 0
            val pos = existing?.position ?: "RANDOM"

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF111111.toInt())
                setPadding(16, 12, 16, 12)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 4); layoutParams = lp
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val label = TextView(ctx).apply {
                text = type.replaceFirstChar { it.uppercase() }
                textSize = 13f; setTextColor(0xFFcccccc.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val freqLabel = TextView(ctx).apply {
                text = "$freq%"; textSize = 12f; setTextColor(0xFF00ff88.toInt())
                setPadding(0, 0, 8, 0); minWidth = 44
            }

            val seekFreq = SeekBar(ctx).apply {
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
            val posRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 0)
            }
            listOf("START", "MID", "END", "RANDOM").forEach { p ->
                val pb = Button(ctx).apply {
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
            gimmicksContainer?.addView(row)
        }
    }

    private fun updateGimmick(type: String, frequency: Int, position: String) {
        val updated = currentProfile.gimmicks.toMutableList()
        updated.removeAll { it.type == type }
        if (frequency > 0) updated.add(GimmickConfig(type, frequency, position))
        currentProfile = currentProfile.copy(gimmicks = updated)
    }

    private fun setupProfileSpinner() {
        val ctx = context ?: return
        if (profiles.isEmpty()) return
        profileSpinner?.adapter = ArrayAdapter(ctx,
            android.R.layout.simple_spinner_dropdown_item, profiles.map { "${it.emoji} ${it.name}" })
        val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
        profileSpinner?.setSelection(idx)
        profileSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!isAdded || view == null) return
                val selected = profiles.getOrNull(pos) ?: return
                loadProfileToUI(selected)
                activeProfileId = selected.id
                prefs.edit().putString("active_profile_id", activeProfileId).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        btnTest?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (!SherpaEngine.isReady) {
                Toast.makeText(ctx, "Voice engine is loading — it will be ready in a few seconds.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val p = readProfileFromUI()
                val rules = loadWordingRules()
                val text = txtPreview?.text?.toString()?.ifBlank { "Hello! This is how I sound." } ?: "Hello! This is how I sound."
                // Use AudioPipeline directly — works without notification service
                val appCtx = context?.applicationContext ?: return@setOnClickListener
                AudioPipeline.testSpeak(appCtx, text, p, rules)
            } catch (e: Exception) {
                Log.w("ProfilesFragment", "Error starting test speech", e)
                Toast.makeText(ctx, "Error testing voice", Toast.LENGTH_SHORT).show()
            }
        }
        btnStop?.setOnClickListener {
            AudioPipeline.stop()
            Toast.makeText(context ?: return@setOnClickListener, "Stopped", Toast.LENGTH_SHORT).show()
        }
        btnResetSettings?.setOnClickListener {
            loadProfileToUI(currentProfile.copy(
                pitch = 1.0f, speed = 1.0f,
                breathIntensity = 0, breathCurvePosition = 0f, breathPause = 0,
                stutterIntensity = 0, stutterPosition = 0f, stutterFrequency = 0, stutterPause = 30,
                intonationIntensity = 0, intonationVariation = 0.5f,
                gimmicks = emptyList()
            ))
            Toast.makeText(context ?: return@setOnClickListener, "Voice settings reset to defaults", Toast.LENGTH_SHORT).show()
        }
        btnSave?.setOnClickListener {
            val p = readProfileFromUI()
            val idx = profiles.indexOfFirst { it.id == p.id }
            if (idx >= 0) profiles[idx] = p else profiles.add(p)
            VoiceProfile.saveAll(profiles, prefs)
            Toast.makeText(context ?: return@setOnClickListener, "Saved: ${p.name}", Toast.LENGTH_SHORT).show()
        }
        btnNew?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val et = EditText(ctx).apply { hint = "Profile name" }
            try {
                AlertDialog.Builder(ctx).setTitle("New Profile").setView(et)
                    .setPositiveButton("Create") { _, _ ->
                        val name = et.text.toString().ifBlank { "Profile ${profiles.size + 1}" }
                        val p = VoiceProfile(name = name)
                        profiles.add(p); VoiceProfile.saveAll(profiles, prefs)
                        setupProfileSpinner(); profileSpinner?.setSelection(profiles.size - 1)
                    }.setNegativeButton("Cancel", null).show()
            } catch (e: Exception) {
                Log.w("ProfilesFragment", "Failed to show new profile dialog", e)
            }
        }
        btnDelete?.setOnClickListener {
            if (profiles.size <= 1) { Toast.makeText(context ?: return@setOnClickListener, "Can't delete last profile", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val ctx = context ?: return@setOnClickListener
            try {
                AlertDialog.Builder(ctx).setTitle("Delete ${currentProfile.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        profiles.removeAll { it.id == currentProfile.id }
                        VoiceProfile.saveAll(profiles, prefs); setupProfileSpinner()
                    }.setNegativeButton("Cancel", null).show()
            } catch (e: Exception) {
                Log.w("ProfilesFragment", "Failed to show delete dialog", e)
            }
        }
        btnRenameVoice?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val et = EditText(ctx).apply {
                hint = "Voice nickname (leave empty to clear)"
                setText(currentProfile.voiceAlias)
            }
            try {
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
                        updateSelectedVoiceBanner()
                    }
                    .setNeutralButton("Clear") { _, _ ->
                        currentProfile = currentProfile.copy(voiceAlias = "")
                        val idx = profiles.indexOfFirst { it.id == currentProfile.id }
                        if (idx >= 0) profiles[idx] = currentProfile
                        VoiceProfile.saveAll(profiles, prefs)
                        updateSelectedVoiceBanner()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Log.w("ProfilesFragment", "Failed to show rename dialog", e)
            }
        }
    }

    private fun loadProfileToUI(p: VoiceProfile) {
        if (!viewsBound || !isAdded || view == null) return
        currentProfile = p
        seekPitch?.max = 200; seekPitch?.progress = ((p.pitch - 0.50f) / 1.50f * 200f).toInt().coerceIn(0, 200)
        tvPitch?.text = "Pitch: %.2f".format(p.pitch)
        seekSpeed?.max = 200; seekSpeed?.progress = ((p.speed - 0.50f) / 2.50f * 200f).toInt().coerceIn(0, 200)
        tvSpeed?.text = "Speed: %.2f".format(p.speed)
        seekBreathInt?.max = 200; seekBreathInt?.progress = progressiveToSlider(p.breathIntensity, 100); tvBreathInt?.text = "Intensity: ${p.breathIntensity}"
        seekBreathCurve?.max = 200; seekBreathCurve?.progress = progressiveToSlider((p.breathCurvePosition * 100).toInt(), 100); tvBreathCurve?.text = "Curve: ${(p.breathCurvePosition*100).toInt()}%"
        seekBreathPause?.max = 200; seekBreathPause?.progress = progressiveToSlider(p.breathPause, 100); tvBreathPause?.text = "Pause: ${p.breathPause}"
        seekStutterInt?.max = 200; seekStutterInt?.progress = progressiveToSlider(p.stutterIntensity, 100); tvStutterInt?.text = "Intensity: ${p.stutterIntensity}"
        seekStutterPos?.max = 200; seekStutterPos?.progress = progressiveToSlider((p.stutterPosition*100).toInt(), 100); tvStutterPos?.text = "Position: ${(p.stutterPosition*100).toInt()}%"
        seekStutterFreq?.max = 200; seekStutterFreq?.progress = progressiveToSlider(p.stutterFrequency, 100); tvStutterFreq?.text = "Frequency: ${p.stutterFrequency}%"
        seekStutterPause?.max = 200; seekStutterPause?.progress = progressiveToSlider(p.stutterPause, 100); tvStutterPause?.text = "Pause: ${p.stutterPause}"
        seekIntonInt?.max = 200; seekIntonInt?.progress = progressiveToSlider(p.intonationIntensity, 100); tvIntonInt?.text = "Intensity: ${p.intonationIntensity}"
        seekIntonVar?.max = 200; seekIntonVar?.progress = progressiveToSlider((p.intonationVariation*100).toInt(), 100); tvIntonVar?.text = "Variation: ${(p.intonationVariation*100).toInt()}%"

        seekPitch?.let { s -> attachSeek(s) { tvPitch?.text = "Pitch: %.2f".format(0.50f + it / 200f * 1.50f) } }
        seekSpeed?.let { s -> attachSeek(s) { tvSpeed?.text = "Speed: %.2f".format(0.50f + it / 200f * 2.50f) } }
        seekBreathInt?.let { s -> attachSeek(s) { tvBreathInt?.text = "Intensity: ${progressiveFromSlider(it, 200, 100)}" } }
        seekBreathCurve?.let { s -> attachSeek(s) { tvBreathCurve?.text = "Curve: ${progressiveFromSlider(it, 200, 100)}%" } }
        seekBreathPause?.let { s -> attachSeek(s) { tvBreathPause?.text = "Pause: ${progressiveFromSlider(it, 200, 100)}" } }
        seekStutterInt?.let { s -> attachSeek(s) { tvStutterInt?.text = "Intensity: ${progressiveFromSlider(it, 200, 100)}" } }
        seekStutterPos?.let { s -> attachSeek(s) { tvStutterPos?.text = "Position: ${progressiveFromSlider(it, 200, 100)}%" } }
        seekStutterFreq?.let { s -> attachSeek(s) { tvStutterFreq?.text = "Frequency: ${progressiveFromSlider(it, 200, 100)}%" } }
        seekStutterPause?.let { s -> attachSeek(s) { tvStutterPause?.text = "Pause: ${progressiveFromSlider(it, 200, 100)}" } }
        seekIntonInt?.let { s -> attachSeek(s) { tvIntonInt?.text = "Intensity: ${progressiveFromSlider(it, 200, 100)}" } }
        seekIntonVar?.let { s -> attachSeek(s) { tvIntonVar?.text = "Variation: ${progressiveFromSlider(it, 200, 100)}%" } }

        buildCommentaryEditor()
        buildGimmicksEditor()
        renderVoiceGrid()
    }

    /** Convert slider position (0..sliderMax) to actual value (0..valueMax) using quadratic curve */
    private fun progressiveFromSlider(sliderPos: Int, sliderMax: Int, valueMax: Int): Int {
        val t = sliderPos.toFloat() / sliderMax
        return (t * t * valueMax).toInt().coerceIn(0, valueMax)
    }

    /** Convert actual value (0..valueMax) to slider position (0..sliderMax) using square-root curve */
    private fun progressiveToSlider(value: Int, valueMax: Int): Int {
        val t = (value.toFloat() / valueMax).coerceIn(0f, 1f)
        return (kotlin.math.sqrt(t) * 200f).toInt().coerceIn(0, 200)
    }

    private fun readProfileFromUI() = currentProfile.copy(
        pitch               = 0.50f + (seekPitch?.progress ?: 0) / 200f * 1.50f,
        speed               = 0.50f + (seekSpeed?.progress ?: 0) / 200f * 2.50f,
        breathIntensity     = progressiveFromSlider(seekBreathInt?.progress ?: 0, 200, 100),
        breathCurvePosition = progressiveFromSlider(seekBreathCurve?.progress ?: 0, 200, 100) / 100f,
        breathPause         = progressiveFromSlider(seekBreathPause?.progress ?: 0, 200, 100),
        stutterIntensity    = progressiveFromSlider(seekStutterInt?.progress ?: 0, 200, 100),
        stutterPosition     = progressiveFromSlider(seekStutterPos?.progress ?: 0, 200, 100) / 100f,
        stutterFrequency    = progressiveFromSlider(seekStutterFreq?.progress ?: 0, 200, 100),
        stutterPause        = progressiveFromSlider(seekStutterPause?.progress ?: 0, 200, 100),
        intonationIntensity = progressiveFromSlider(seekIntonInt?.progress ?: 0, 200, 100),
        intonationVariation = progressiveFromSlider(seekIntonVar?.progress ?: 0, 200, 100) / 100f
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

    override fun onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null)
        SherpaEngine.onReadyCallback = null
        PiperVoiceManager.downloadCallback = null
        profileSpinner = null; txtPreview = null; btnTest = null; btnStop = null
        btnSave = null; btnDelete = null; btnNew = null; btnResetSettings = null; btnRenameVoice = null
        seekPitch = null; tvPitch = null; seekSpeed = null; tvSpeed = null
        seekBreathInt = null; tvBreathInt = null; seekBreathCurve = null; tvBreathCurve = null
        seekBreathPause = null; tvBreathPause = null; seekStutterInt = null; tvStutterInt = null
        seekStutterPos = null; tvStutterPos = null; seekStutterFreq = null; tvStutterFreq = null
        seekStutterPause = null; tvStutterPause = null; seekIntonInt = null; tvIntonInt = null
        seekIntonVar = null; tvIntonVar = null; voiceGrid = null; presetsScroll = null
        gimmicksContainer = null; genderRow = null; nationRow = null; tvEngineStatus = null
        selectedVoiceBanner = null; tvSelectedVoiceIcon = null; tvSelectedVoiceName = null; tvSelectedVoiceDetail = null
        viewsBound = false
        super.onDestroyView()
    }
}
