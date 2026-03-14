package com.echolibrium.kyokan

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
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private lateinit var profileSpinner: Spinner
    private lateinit var txtPreview: EditText
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnNew: Button
    private lateinit var seekPitch: SeekBar;  private lateinit var tvPitch: TextView
    private lateinit var seekSpeed: SeekBar;  private lateinit var tvSpeed: TextView
    private lateinit var profileGrid: LinearLayout
    private lateinit var voiceGrid: LinearLayout
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
        buildFilterButtons()
        renderVoiceGrid()
        setupButtons()
        loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles[0])
    }

    private fun setupCollapsibleSections(v: View) {
        setupCollapsibleSection(v, R.id.label_pitch_speed, R.id.section_pitch_speed, "// PITCH & SPEED")
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
        genderRow       = v.findViewById(R.id.gender_filter_row)
        nationRow       = v.findViewById(R.id.nation_filter_row)
        seekPitch       = v.findViewById(R.id.seek_pitch);  tvPitch = v.findViewById(R.id.tv_pitch)
        seekSpeed       = v.findViewById(R.id.seek_speed);  tvSpeed = v.findViewById(R.id.tv_speed)
    }

    private fun buildFilterButtons() {
        if (view == null) return
        genderRow.removeAllViews()
        nationRow.removeAllViews()

        val genders = VoiceRegistry.genders()
        genders.forEach { g ->
            genderRow.addView(filterBtn(g, genderFilter == g) {
                genderFilter = g; buildFilterButtons(); renderVoiceGrid()
            })
        }

        val languages = VoiceRegistry.languages()
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

        // ── Cloud voices (Orpheus) — primary ──────────────────────────────
        val cloudEnabled = CloudTtsEngine.isEnabled()
        val cloudSubtitle = if (cloudEnabled)
            "DeepInfra API  ·  ${VoiceRegistry.CLOUD_VOICES.size} voices"
        else
            "Requires API key — set in Settings"
        voiceGrid.addView(buildSectionHeader("ORPHEUS (CLOUD)", cloudSubtitle, 0xFFffaa44.toInt()))
        renderCloudVoices()

        // ── Kokoro voices (offline) ──────────────────────────────────────
        val kokoroReady = VoiceDownloadManager.isModelReady(ctx)
        val kokoroSubtitle = if (kokoroReady) "Offline  ·  Model ready" else "Offline  ·  Model not downloaded"
        val kokoroDownloadAction: (() -> Unit)? = if (!kokoroReady
            && VoiceDownloadManager.state != VoiceDownloadManager.State.DOWNLOADING) {
            { startKokoroDownload() }
        } else null
        voiceGrid.addView(buildSectionHeader("KOKORO (OFFLINE)", kokoroSubtitle, 0xFF00ccff.toInt(), kokoroDownloadAction))
        if (!kokoroReady) {
            voiceGrid.addView(TextView(ctx).apply {
                text = "Download the offline model (~${VoiceDownloadManager.MODEL_SIZE_MB}MB) so speech works without internet."
                textSize = 11f; setTextColor(0xFF666666.toInt())
                setPadding(14, 4, 14, 8)
            })
        } else {
            renderKokoroVoices()
        }

        // ── Piper voices (offline) ──────────────────────────────────────
        voiceGrid.addView(buildSectionHeader("PIPER (OFFLINE)", "Offline  ·  Per-voice download", 0xFF88ccff.toInt()))
        renderPiperVoices()
    }

    private fun renderCloudVoices() {
        val cloudEnabled = CloudTtsEngine.isEnabled()
        val filtered = VoiceRegistry.CLOUD_VOICES.filter { v ->
            val gOk = genderFilter == "All" || v.gender == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }
        if (filtered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
            return
        }
        addVoiceRows(filtered.map { v ->
            val active = currentProfile.voiceName == v.id
            buildVoiceCard(
                name = v.displayName,
                icon = if (v.gender == "Female") "♀" else "♂",
                iconColor = if (cloudEnabled) {
                    if (v.gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt()
                } else 0xFF444444.toInt(),
                badge = "☁ Orpheus",
                voiceId = v.id, active = active, accent = 0xFFffaa44.toInt(),
                enabled = cloudEnabled,
                onClick = if (cloudEnabled) {
                    { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
                } else null
            )
        })
    }

    private fun renderKokoroVoices() {
        val entries = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).filter { v ->
            val gOk = genderFilter == "All" || v.gender == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }
        if (entries.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
            return
        }
        addVoiceRows(entries.map { v ->
            val active = currentProfile.voiceName == v.id
            buildVoiceCard(
                name = v.displayName,
                icon = if (v.gender == "Female") "♀" else "♂",
                iconColor = if (v.gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt(),
                badge = "🔇 Kokoro",
                voiceId = v.id, active = active, accent = 0xFF00ccff.toInt(),
                enabled = true,
                onClick = { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
            )
        })
    }

    private fun renderPiperVoices() {
        val entries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER).filter { v ->
            val gOk = genderFilter == "All" || v.gender == genderFilter
            val lOk = languageFilter == "All" || v.language == languageFilter
            gOk && lOk
        }
        if (entries.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
            return
        }
        val ctx = requireContext()
        addVoiceRows(entries.map { v ->
            val active = currentProfile.voiceName == v.id
            val ready = VoiceRegistry.isReady(ctx, v.id)
            val downloading = PiperDownloadManager.isDownloading(v.id)
            val badge = when {
                ready -> "🔇 Piper"
                downloading -> "⬇ ..."
                else -> "⬇ tap"
            }
            buildVoiceCard(
                name = v.displayName,
                icon = if (v.gender == "Female") "♀" else "♂",
                iconColor = if (v.gender == "Female") 0xFFff88cc.toInt() else 0xFF88ccff.toInt(),
                badge = badge,
                voiceId = v.id, active = active, accent = 0xFF88ccff.toInt(),
                enabled = ready,
                onClick = if (ready) {
                    { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
                } else if (!downloading) {
                    { startPiperDownload(v.id) }
                } else null
            )
        })
    }

    private fun buildSectionHeader(title: String, subtitle: String, accent: Int, onDownloadAll: (() -> Unit)? = null): android.view.View {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 14, 0, 10); layoutParams = lp
        }

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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (onDownloadAll != null) {
            titleRow.addView(TextView(ctx).apply {
                text = "⬇"; textSize = 18f; setTextColor(accent)
                setPadding(16, 0, 8, 0)
                setOnClickListener { onDownloadAll() }
            })
        }
        container.addView(titleRow)

        container.addView(TextView(ctx).apply {
            text = subtitle; textSize = 10f; setTextColor(0xFF555555.toInt())
            setPadding(14, 2, 0, 0)
        })

        return container
    }

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

            addView(TextView(ctx).apply {
                text = icon; textSize = 22f; gravity = android.view.Gravity.CENTER
                setTextColor(iconColor)
            })
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
            repeat(cols - rowCards.size) {
                row.addView(android.view.View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            voiceGrid.addView(row)
        }
    }

    private fun emptyLabel(msg: String): android.view.View {
        return TextView(requireContext()).apply {
            text = msg; setTextColor(0xFF446644.toInt()); textSize = 12f
            setPadding(6, 8, 0, 8)
        }
    }

    private fun startKokoroDownload() {
        val ctx = requireContext()
        VoiceDownloadManager.onProgress { pct ->
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
        startDownloadRefresh()
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
            }
        }
        PiperDownloadManager.downloadVoice(ctx, voiceId)
        renderVoiceGrid()
        startDownloadRefresh()
    }

    private fun updateDownloadProgress(pct: Int) {
        val statusView = voiceGrid.findViewWithTag<TextView>("download_status") ?: return
        val barView = voiceGrid.findViewWithTag<android.widget.ProgressBar>("download_bar") ?: return
        statusView.text = if (pct < 0) "⏳ Extracting model files..." else "⬇ Downloading voices: $pct%"
        barView.isIndeterminate = pct < 0
        if (pct >= 0) barView.progress = pct
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

            setOnClickListener {
                activeProfileId = p.id
                prefs.edit().putString("active_profile_id", activeProfileId).apply()
                loadProfileToUI(p)
                renderProfileGrid()
                val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
                profileSpinner.setSelection(idx)
            }

            setOnLongClickListener {
                showRenameDialog(p)
                true
            }
        }

        card.addView(TextView(ctx).apply {
            text = p.emoji
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        })

        card.addView(TextView(ctx).apply {
            text = p.name
            textSize = 11f
            setTextColor(if (isActive) 0xFF00ff88.toInt() else 0xFFaaaaaa.toInt())
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val voiceEntry = VoiceRegistry.byId(p.voiceName)
        if (voiceEntry != null) {
            card.addView(TextView(ctx).apply {
                text = voiceEntry.displayName
                textSize = 9f
                setTextColor(if (isActive) 0xFF448844.toInt() else 0xFF555555.toInt())
                gravity = android.view.Gravity.CENTER
                maxLines = 1
            })
        }

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
                "${p.emoji} ${p.name}"
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
            val text = txtPreview.text.toString().ifBlank { "Hello! This is how I sound." }
            NotificationReaderService.instance?.testSpeak(text, p)
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

        attachSeek(seekPitch) { tvPitch.text = "Pitch: %.2f".format((it + 50) / 100f) }
        attachSeek(seekSpeed) { tvSpeed.text = "Speed: %.2f".format((it + 50) / 100f) }

        renderVoiceGrid()
    }

    private fun readProfileFromUI() = currentProfile.copy(
        pitch = (seekPitch.progress + 50) / 100f,
        speed = (seekSpeed.progress + 50) / 100f
    )

    private fun attachSeek(s: SeekBar, onChange: (Int) -> Unit) {
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) { if (fromUser) onChange(v) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        startDownloadRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopDownloadRefresh()
    }

    override fun onDestroyView() {
        stopDownloadRefresh()
        super.onDestroyView()
    }

    private fun startDownloadRefresh() {
        stopDownloadRefresh()
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val kokoroDownloading = VoiceDownloadManager.state == VoiceDownloadManager.State.DOWNLOADING
                val piperDownloading = PiperDownloadManager.isAnyDownloading()
                if (kokoroDownloading || piperDownloading) {
                    renderVoiceGrid()
                    refreshHandler.postDelayed(this, 2000)
                } else {
                    renderVoiceGrid()
                }
            }
        }
        val kokoroDownloading = VoiceDownloadManager.state == VoiceDownloadManager.State.DOWNLOADING
        val piperDownloading = PiperDownloadManager.isAnyDownloading()
        if (kokoroDownloading || piperDownloading) {
            refreshHandler.postDelayed(refreshRunnable!!, 2000)
        }
    }

    private fun stopDownloadRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
    }
}
