package com.echolibrium.kyokan

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

class ProfilesFragment : Fragment() {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    private var currentProfile = VoiceProfile()
    private var genderFilter = "All"
    private var languageFilter = "All"
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var lastVoiceGridRender = 0L
    private var pendingVoiceGridRender = false

    private lateinit var profileSpinner: Spinner
    private lateinit var txtPreview: EditText
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var btnNew: Button
    private lateinit var seekPitch: SeekBar
    private lateinit var tvPitch: TextView
    private lateinit var seekSpeed: SeekBar
    private lateinit var tvSpeed: TextView
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

        // Restore state from config change / process death
        if (s != null) {
            genderFilter = s.getString("genderFilter", "All")
            languageFilter = s.getString("languageFilter", "All")
            activeProfileId = s.getString("activeProfileId", activeProfileId)
        }

        // Register download listeners once (cleaned up in onDestroyView)
        VoiceDownloadManager.addStateListener(kokoroStateListener)
        VoiceDownloadManager.addProgressListener(kokoroProgressListener)
        PiperDownloadManager.addStateListener(piperStateListener)
        PiperDownloadManager.addProgressListener(piperProgressListener)

        setupCollapsibleSections(v)
        setupProfileSpinner()
        renderProfileGrid()
        buildFilterButtons()
        renderVoiceGrid()
        setupButtons()
        loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles[0])
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("genderFilter", genderFilter)
        outState.putString("languageFilter", languageFilter)
        outState.putString("activeProfileId", activeProfileId)
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
            val parent = section.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
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
        seekPitch       = v.findViewById(R.id.seek_pitch)
        tvPitch         = v.findViewById(R.id.tv_pitch)
        seekSpeed       = v.findViewById(R.id.seek_speed)
        tvSpeed         = v.findViewById(R.id.tv_speed)
    }

    private fun buildFilterButtons() {
        if (view == null) return
        genderRow.removeAllViews()
        nationRow.removeAllViews()

        VoiceRegistry.genders().forEach { g ->
            genderRow.addView(filterBtn(g, genderFilter == g) {
                genderFilter = g; buildFilterButtons(); renderVoiceGrid()
            })
        }

        VoiceRegistry.languages().forEach { l ->
            nationRow.addView(filterBtn(l, languageFilter == l) {
                languageFilter = l; buildFilterButtons(); renderVoiceGrid()
            })
        }
    }

    private fun filterBtn(label: String, active: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label; textSize = 11f
            setBackgroundColor(if (active) 0xFF251840.toInt() else 0xFF181222.toInt())
            setTextColor(if (active) 0xFFb898d4.toInt() else 0xFF6e5f82.toInt())
            setPadding(20, 8, 20, 8)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 8, 0); layoutParams = lp
            contentDescription = "Filter: $label${if (active) ", selected" else ""}"
            setOnClickListener { onClick() }
        }
    }

    // ── Voice grid rendering ───────────────────────────────────────────────

    private fun renderVoiceGridThrottled() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastVoiceGridRender
        if (elapsed >= 500) {
            renderVoiceGrid()
        } else if (!pendingVoiceGridRender) {
            pendingVoiceGridRender = true
            refreshHandler.postDelayed({
                pendingVoiceGridRender = false
                if (isAdded) renderVoiceGrid()
            }, 500 - elapsed)
        }
    }

    private fun renderVoiceGrid() {
        lastVoiceGridRender = System.currentTimeMillis()
        voiceGrid.removeAllViews()
        val ctx = requireContext()

        // ── Orpheus ──────────────────────────────────────────────────────
        val cloudEnabled = CloudTtsEngine.isEnabled()
        val orpheusSubtitle = if (cloudEnabled)
            "Cloud  ·  DeepInfra API  ·  ${VoiceRegistry.CLOUD_VOICES.size} voices"
        else
            "Cloud  ·  Tap 🔑 to enter API key"
        val cloudKeyAction: (() -> Unit) = { showDeepInfraKeyDialog() }
        voiceGrid.addView(buildSectionHeader("ORPHEUS", orpheusSubtitle, 0xFFffaa44.toInt(), cloudKeyAction, "🔑"))

        val cloudFiltered = VoiceRegistry.CLOUD_VOICES.filter { v ->
            (genderFilter == "All" || v.gender == genderFilter) &&
            (languageFilter == "All" || v.language == languageFilter)
        }
        if (cloudFiltered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
        } else {
            addVoiceRows(cloudFiltered.map { v ->
                buildVoiceCard(
                    name = v.displayName,
                    icon = if (v.gender == "Female") "♀" else "♂",
                    iconColor = if (cloudEnabled) {
                        if (v.gender == "Female") 0xFFd4a0b8.toInt() else 0xFF88aad4.toInt()
                    } else {
                        if (v.gender == "Female") 0xFF4a2a3e.toInt() else 0xFF3a4058.toInt()
                    },
                    status = if (cloudEnabled) "cloud" else "no API key",
                    statusColor = if (cloudEnabled) 0xFF886633.toInt() else 0xFFff4444.toInt(),
                    voiceId = v.id,
                    active = currentProfile.voiceName == v.id,
                    accent = 0xFFffaa44.toInt(),
                    enabled = cloudEnabled,
                    onClick = if (cloudEnabled) {
                        { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
                    } else {
                        { showDeepInfraKeyDialog() }
                    }
                )
            })
        }

        // ── Kokoro ───────────────────────────────────────────────────────
        val kokoroReady = VoiceDownloadManager.isModelReady(ctx)
        val kokoroDownloading = VoiceDownloadManager.state == DownloadState.DOWNLOADING
        val kokoroCount = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).size
        val kokoroSubtitle = when {
            kokoroReady -> "Offline  ·  $kokoroCount voices  ·  Ready"
            kokoroDownloading -> "Offline  ·  $kokoroCount voices  ·  Downloading..."
            else -> "Offline  ·  $kokoroCount voices  ·  ~${VoiceDownloadManager.MODEL_SIZE_MB}MB shared model"
        }
        val kokoroDownloadAll: (() -> Unit)? = if (!kokoroReady && !kokoroDownloading) {
            { startKokoroDownload() }
        } else null
        voiceGrid.addView(buildSectionHeader("KOKORO", kokoroSubtitle, 0xFF00ccff.toInt(), kokoroDownloadAll))

        val kokoroFiltered = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).filter { v ->
            (genderFilter == "All" || v.gender == genderFilter) &&
            (languageFilter == "All" || v.language == languageFilter)
        }
        if (kokoroFiltered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
        } else {
            addVoiceRows(kokoroFiltered.map { v ->
                val status: String
                val statusColor: Int
                when {
                    kokoroReady -> {
                        status = "ready"
                        statusColor = 0xFF00cc66.toInt()
                    }
                    kokoroDownloading -> {
                        val pct = VoiceDownloadManager.progressPercent
                        status = if (pct < 0) "extracting..." else "$pct%"
                        statusColor = 0xFF00ccff.toInt()
                    }
                    else -> {
                        status = "tap to download"
                        statusColor = 0xFF6e5f82.toInt()
                    }
                }
                buildVoiceCard(
                    name = v.displayName,
                    icon = if (v.gender == "Female") "♀" else "♂",
                    iconColor = if (kokoroReady) {
                        if (v.gender == "Female") 0xFFd4a0b8.toInt() else 0xFF88aad4.toInt()
                    } else {
                        if (v.gender == "Female") 0xFF4a2a3e.toInt() else 0xFF3a4058.toInt()
                    },
                    status = status,
                    statusColor = statusColor,
                    voiceId = v.id,
                    active = currentProfile.voiceName == v.id,
                    accent = 0xFF00ccff.toInt(),
                    enabled = kokoroReady,
                    onClick = when {
                        kokoroReady -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                        !kokoroDownloading -> {{ startKokoroDownload() }}
                        else -> null
                    }
                )
            })
        }

        // ── Piper ────────────────────────────────────────────────────────
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val piperReadyCount = piperEntries.count { VoiceRegistry.isReady(ctx, it.id) }
        val piperSubtitle = "Offline  ·  $piperReadyCount/${piperEntries.size} voices downloaded  ·  Per-voice download"
        val piperHasUndownloaded = piperEntries.any { !VoiceRegistry.isReady(ctx, it.id) && !PiperDownloadManager.isDownloading(it.id) }
        val piperDownloadAll: (() -> Unit)? = if (piperHasUndownloaded) {
            { confirmDownloadAllPiper() }
        } else null
        voiceGrid.addView(buildSectionHeader("PIPER", piperSubtitle, 0xFF88ccff.toInt(), piperDownloadAll))

        val piperFiltered = piperEntries.filter { v ->
            (genderFilter == "All" || v.gender == genderFilter) &&
            (languageFilter == "All" || v.language == languageFilter)
        }
        if (piperFiltered.isEmpty()) {
            voiceGrid.addView(emptyLabel("No voices match filter."))
        } else {
            addVoiceRows(piperFiltered.map { v ->
                val ready = VoiceRegistry.isReady(ctx, v.id)
                val downloading = PiperDownloadManager.isDownloading(v.id)
                val status: String
                val statusColor: Int
                when {
                    ready -> {
                        status = "ready"
                        statusColor = 0xFF00cc66.toInt()
                    }
                    downloading -> {
                        val pct = PiperDownloadManager.getProgress(v.id)
                        status = if (pct < 0) "extracting..." else "$pct%"
                        statusColor = 0xFF88ccff.toInt()
                    }
                    else -> {
                        status = "tap to download"
                        statusColor = 0xFF6e5f82.toInt()
                    }
                }
                buildVoiceCard(
                    name = v.displayName,
                    icon = if (v.gender == "Female") "♀" else if (v.gender == "Male") "♂" else "◆",
                    iconColor = if (ready) {
                        when (v.gender) {
                            "Female" -> 0xFFd4a0b8.toInt()
                            "Male" -> 0xFF88aad4.toInt()
                            else -> 0xFFb0a4c0.toInt()
                        }
                    } else {
                        when (v.gender) {
                            "Female" -> 0xFF4a2a3e.toInt()
                            "Male" -> 0xFF3a4058.toInt()
                            else -> 0xFF7e6e98.toInt()
                        }
                    },
                    status = status,
                    statusColor = statusColor,
                    voiceId = v.id,
                    active = currentProfile.voiceName == v.id,
                    accent = 0xFF88ccff.toInt(),
                    enabled = ready,
                    onClick = when {
                        ready -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                        !downloading -> {{ startPiperDownload(v.id) }}
                        else -> null
                    }
                )
            })
        }
    }

    // ── UI builder delegation (extracted to VoiceCardBuilder) ────────────────

    private fun buildSectionHeader(title: String, subtitle: String, accent: Int, onDownloadAll: (() -> Unit)? = null, downloadIcon: String = "⬇ ALL") =
        VoiceCardBuilder.buildSectionHeader(requireContext(), title, subtitle, accent, onDownloadAll, downloadIcon)

    private fun buildVoiceCard(
        name: String, icon: String, iconColor: Int,
        status: String, statusColor: Int,
        voiceId: String, active: Boolean, accent: Int,
        enabled: Boolean, onClick: (() -> Unit)?
    ) = VoiceCardBuilder.buildVoiceCard(
        requireContext(), name, icon, iconColor, status, statusColor,
        voiceId, active, accent, enabled, onClick,
        onPreview = if (enabled) { vid, vname -> previewVoice(vid, vname) } else null
    )

    private fun previewVoice(voiceId: String, name: String) {
        val ctx = requireContext()
        val previewText = txtPreview.text.toString().ifBlank { "Hello! This is $name." }
        val tempProfile = currentProfile.copy(voiceName = voiceId)
        val service = NotificationReaderService.instance
        if (service != null) {
            service.testSpeak(previewText, tempProfile)
        } else {
            AudioPipeline.start(ctx)
            AudioPipeline.enqueue(AudioPipeline.Item(
                text = previewText,
                voiceId = voiceId,
                pitch = tempProfile.pitch,
                speed = tempProfile.speed
            ))
        }
    }

    private fun addVoiceRows(cards: List<android.view.View>) =
        VoiceCardBuilder.addVoiceRows(voiceGrid, cards)

    private fun emptyLabel(msg: String) =
        VoiceCardBuilder.emptyLabel(requireContext(), msg)

    // ── API key dialog ─────────────────────────────────────────────────────

    private fun showDeepInfraKeyDialog() {
        val ctx = requireContext()
        val currentKey = try { SecureKeyStore.getDeepInfraKey(ctx) } catch (_: Exception) { null }

        val input = EditText(ctx).apply {
            hint = "DeepInfra API key"
            setText(currentKey ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
            setTextColor(0xFFddd6e8.toInt())
            setHintTextColor(0xFF6e5f82.toInt())
            setBackgroundColor(0xFF201830.toInt())
        }

        AlertDialog.Builder(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("DeepInfra API Key")
            .setMessage("Enter your DeepInfra API key to enable cloud voices (Orpheus).\n\nPrivacy note: When using cloud voices, notification text is sent to DeepInfra servers for speech synthesis. This may include private message content. Local voices (Kokoro, Piper) keep all data on-device.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                SecureKeyStore.setDeepInfraKey(ctx, key)
                CloudTtsEngine.updateApiKey(key)
                renderVoiceGrid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Downloads ───────────────────────────────────────────────────────────

    private fun startKokoroDownload() {
        VoiceDownloadManager.downloadModel(requireContext())
        renderVoiceGrid()
        startDownloadRefresh()
    }

    private fun startPiperDownload(voiceId: String) {
        PiperDownloadManager.downloadVoice(requireContext(), voiceId)
        renderVoiceGrid()
        startDownloadRefresh()
    }

    private fun confirmDownloadAllPiper() {
        val ctx = requireContext()
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val remaining = piperEntries.count { !VoiceRegistry.isReady(ctx, it.id) && !PiperDownloadManager.isDownloading(it.id) }
        val estimatedMb = piperEntries.filter { !VoiceRegistry.isReady(ctx, it.id) }
            .sumOf { PiperVoices.byId(it.id)?.sizeMb ?: 40 }
        AlertDialog.Builder(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Download all Piper voices?")
            .setMessage("This will download $remaining voices (~${estimatedMb}MB total). A Wi-Fi connection is recommended.")
            .setPositiveButton("Download") { _, _ -> downloadAllPiper() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAllPiper() {
        val ctx = requireContext()
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        piperEntries.forEach { v ->
            if (!VoiceRegistry.isReady(ctx, v.id) && !PiperDownloadManager.isDownloading(v.id)) {
                PiperDownloadManager.downloadVoice(ctx, v.id)
            }
        }
        renderVoiceGrid()
        startDownloadRefresh()
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
        val dp = ctx.resources.displayMetrics.density
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding((8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                if (isActive) {
                    setColor(0xFF2a1828.toInt())
                    setStroke((2 * dp).toInt(), 0xFFc48da0.toInt())
                } else {
                    setColor(0xFF1a1428.toInt())
                    setStroke(1, 0xFF2a2040.toInt())
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
            val voiceEntry = VoiceRegistry.byId(p.voiceName)
            contentDescription = "Profile ${p.name}${if (voiceEntry != null) ", voice ${voiceEntry.displayName}" else ""}${if (isActive) ", active" else ""}. Long press to rename."

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
            setTextColor(if (isActive) 0xFFc48da0.toInt() else 0xFFb0a4c0.toInt())
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val voiceEntry = VoiceRegistry.byId(p.voiceName)
        if (voiceEntry != null) {
            card.addView(TextView(ctx).apply {
                text = voiceEntry.displayName
                textSize = 9f
                setTextColor(if (isActive) 0xFF9b7eb8.toInt() else 0xFF7e6e98.toInt())
                gravity = android.view.Gravity.CENTER
                maxLines = 1
            })
        }

        if (isActive) {
            card.addView(android.view.View(ctx).apply {
                setBackgroundColor(0xFFc48da0.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                ).also { it.setMargins((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), 0) }
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

    private var spinnerInitDone = false

    private fun setupProfileSpinner() {
        spinnerInitDone = false
        profileSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, profiles.map { p ->
                "${p.emoji} ${p.name}"
            })
        val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
        profileSpinner.setSelection(idx)
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!spinnerInitDone) { spinnerInitDone = true; return }
                loadProfileToUI(profiles[pos])
                activeProfileId = profiles[pos].id
                prefs.edit().putString("active_profile_id", activeProfileId).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private val synthesisErrorListener: (String, String) -> Unit = { _, reason ->
        activity?.runOnUiThread {
            if (isAdded) Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        }
    }

    // Download listeners — registered once, cleaned up in onDestroyView
    private val kokoroStateListener: (DownloadState) -> Unit = { state ->
        activity?.runOnUiThread {
            if (isAdded) {
                renderVoiceGridThrottled()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(context, "Kokoro download failed: ${VoiceDownloadManager.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private val kokoroProgressListener: (Int) -> Unit = { _ ->
        activity?.runOnUiThread { if (isAdded) renderVoiceGridThrottled() }
    }
    private val piperStateListener: (String, DownloadState) -> Unit = { vid, state ->
        activity?.runOnUiThread {
            if (isAdded) {
                renderVoiceGridThrottled()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(context, "Download failed for $vid: ${PiperDownloadManager.getError(vid)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private val piperProgressListener: (String, Int) -> Unit = { _, _ ->
        activity?.runOnUiThread { if (isAdded) renderVoiceGridThrottled() }
    }

    private fun setupButtons() {
        // Show toast when TTS synthesis fails silently (B3: use listener list, not single slot)
        AudioPipeline.addSynthesisErrorListener(synthesisErrorListener)

        btnTest.setOnClickListener {
            val p = readProfileFromUI()
            val text = txtPreview.text.toString().ifBlank { "Hello! This is how I sound." }
            val ctx = requireContext()

            // Use the service if running, otherwise start AudioPipeline directly
            val service = NotificationReaderService.instance
            if (service != null) {
                service.testSpeak(text, p)
            } else {
                AudioPipeline.start(ctx)
                AudioPipeline.enqueue(AudioPipeline.Item(
                    text = text,
                    voiceId = p.voiceName,
                    pitch = p.pitch,
                    speed = p.speed
                ))
            }
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
            AudioPipeline.stop()
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
        s.setOnSeekBarChangeListener(onSeekBarChange(onChange))
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
        AudioPipeline.removeSynthesisErrorListener(synthesisErrorListener)
        VoiceDownloadManager.removeStateListener(kokoroStateListener)
        VoiceDownloadManager.removeProgressListener(kokoroProgressListener)
        PiperDownloadManager.removeStateListener(piperStateListener)
        PiperDownloadManager.removeProgressListener(piperProgressListener)
        super.onDestroyView()
    }

    private fun startDownloadRefresh() {
        stopDownloadRefresh()
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val kokoroDownloading = VoiceDownloadManager.state == DownloadState.DOWNLOADING
                val piperDownloading = PiperDownloadManager.isAnyDownloading()
                if (kokoroDownloading || piperDownloading) {
                    renderVoiceGridThrottled()
                    refreshHandler.postDelayed(this, 1000)
                } else {
                    renderVoiceGrid()
                }
            }
        }
        val kokoroDownloading = VoiceDownloadManager.state == DownloadState.DOWNLOADING
        val piperDownloading = PiperDownloadManager.isAnyDownloading()
        if (kokoroDownloading || piperDownloading) {
            refreshHandler.postDelayed(refreshRunnable!!, 1000)
        }
    }

    private fun stopDownloadRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
    }
}
