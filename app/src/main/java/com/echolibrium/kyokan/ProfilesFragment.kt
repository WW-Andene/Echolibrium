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

    companion object {
        // Shared UI colors (M14)
        private const val COLOR_ORPHEUS      = 0xFFffaa44.toInt()
        private const val COLOR_KOKORO       = 0xFF00ccff.toInt()
        private const val COLOR_PIPER        = 0xFF88ccff.toInt()
        private const val COLOR_READY        = 0xFF00cc66.toInt()
        private const val COLOR_DIMMED       = 0xFF6e5f82.toInt()
        private const val COLOR_BG_DARK      = 0xFF181222.toInt()
        private const val COLOR_BG_ACTIVE    = 0xFF251840.toInt()
        private const val COLOR_TEXT_LIGHT    = 0xFFddd6e8.toInt()
        private const val COLOR_TEXT_MUTED    = 0xFFb0a4c0.toInt()
        private const val COLOR_LAVENDER      = 0xFF9b7eb8.toInt()
        private const val COLOR_ROSE          = 0xFFc48da0.toInt()
        private const val COLOR_FILTER_ACTIVE = 0xFFb898d4.toInt()
        private const val COLOR_FEMALE_ACTIVE = 0xFFd4a0b8.toInt()
        private const val COLOR_MALE_ACTIVE   = 0xFF88aad4.toInt()
        private const val COLOR_FEMALE_DIM    = 0xFF4a2a3e.toInt()
        private const val COLOR_MALE_DIM      = 0xFF3a4058.toInt()
        private const val COLOR_CARD_ACTIVE_BG = 0xFF2a1828.toInt()
        private const val COLOR_CARD_BORDER    = 0xFF2a2040.toInt()
        private const val COLOR_INPUT_BG       = 0xFF201830.toInt()
        private const val COLOR_ERROR          = 0xFFff4444.toInt()
        private const val COLOR_CLOUD_STATUS   = 0xFF886633.toInt()
    }

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
        val dp = requireContext().resources.displayMetrics.density
        return Button(requireContext()).apply {
            text = label; textSize = 11f
            setBackgroundColor(if (active) COLOR_BG_ACTIVE else COLOR_BG_DARK)
            setTextColor(if (active) COLOR_FILTER_ACTIVE else COLOR_DIMMED)
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            minHeight = (48 * dp).toInt(); minimumHeight = (48 * dp).toInt()
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, (6 * dp).toInt(), 0); layoutParams = lp
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
            getString(R.string.orpheus_subtitle_enabled, VoiceRegistry.CLOUD_VOICES.size)
        else
            getString(R.string.orpheus_subtitle_disabled)
        val cloudKeyAction: (() -> Unit) = { showDeepInfraKeyDialog() }
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_orpheus), orpheusSubtitle, COLOR_ORPHEUS, cloudKeyAction, getString(R.string.key_btn)))

        addFilteredVoiceCards(VoiceRegistry.CLOUD_VOICES) { v ->
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, cloudEnabled),
                status = if (cloudEnabled) getString(R.string.cloud_status) else getString(R.string.no_api_key),
                statusColor = if (cloudEnabled) COLOR_CLOUD_STATUS else COLOR_ERROR,
                voiceId = v.id,
                active = currentProfile.voiceName == v.id,
                accent = COLOR_ORPHEUS,
                enabled = cloudEnabled,
                onClick = if (cloudEnabled) {
                    { currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }
                } else {
                    { showDeepInfraKeyDialog() }
                }
            )
        }

        // ── Kokoro ───────────────────────────────────────────────────────
        val kokoroReady = VoiceDownloadManager.isModelReady(ctx)
        val kokoroDownloading = VoiceDownloadManager.state == DownloadState.DOWNLOADING
        val kokoroCount = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).size
        val kokoroSubtitle = when {
            kokoroReady -> getString(R.string.kokoro_subtitle_ready, kokoroCount)
            kokoroDownloading -> getString(R.string.kokoro_subtitle_downloading, kokoroCount)
            else -> getString(R.string.kokoro_subtitle_size, kokoroCount, VoiceDownloadManager.MODEL_SIZE_MB)
        }
        val kokoroDownloadAll: (() -> Unit)? = if (!kokoroReady && !kokoroDownloading) {
            { startKokoroDownload() }
        } else null
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_kokoro), kokoroSubtitle, COLOR_KOKORO, kokoroDownloadAll, getString(R.string.download_all_btn)))

        addFilteredVoiceCards(VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO)) { v ->
            val status: String
            val statusColor: Int
            when {
                kokoroReady -> { status = getString(R.string.ready_status); statusColor = COLOR_READY }
                kokoroDownloading -> {
                    val pct = VoiceDownloadManager.progressPercent
                    status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                    statusColor = COLOR_KOKORO
                }
                else -> { status = getString(R.string.tap_to_download); statusColor = COLOR_DIMMED }
            }
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, kokoroReady),
                status = status, statusColor = statusColor,
                voiceId = v.id, active = currentProfile.voiceName == v.id,
                accent = COLOR_KOKORO, enabled = kokoroReady,
                onClick = when {
                    kokoroReady -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                    !kokoroDownloading -> {{ startKokoroDownload() }}
                    else -> null
                }
            )
        }

        // ── Piper ────────────────────────────────────────────────────────
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val piperReadyCount = piperEntries.count { VoiceRegistry.isReady(ctx, it.id) }
        val piperSubtitle = getString(R.string.piper_subtitle, piperReadyCount, piperEntries.size)
        val piperHasUndownloaded = piperEntries.any { !VoiceRegistry.isReady(ctx, it.id) && !PiperDownloadManager.isDownloading(it.id) }
        val piperDownloadAll: (() -> Unit)? = if (piperHasUndownloaded) {
            { confirmDownloadAllPiper() }
        } else null
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_piper), piperSubtitle, COLOR_PIPER, piperDownloadAll, getString(R.string.download_all_btn)))

        addFilteredVoiceCards(piperEntries) { v ->
            val ready = VoiceRegistry.isReady(ctx, v.id)
            val downloading = PiperDownloadManager.isDownloading(v.id)
            val status: String
            val statusColor: Int
            when {
                ready -> { status = getString(R.string.ready_status); statusColor = COLOR_READY }
                downloading -> {
                    val pct = PiperDownloadManager.getProgress(v.id)
                    status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                    statusColor = COLOR_PIPER
                }
                else -> { status = getString(R.string.tap_to_download); statusColor = COLOR_DIMMED }
            }
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, ready),
                status = status, statusColor = statusColor,
                voiceId = v.id, active = currentProfile.voiceName == v.id,
                accent = COLOR_PIPER, enabled = ready,
                onClick = when {
                    ready -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                    !downloading -> {{ startPiperDownload(v.id) }}
                    else -> null
                }
            )
        }
    }

    // ── Voice grid helpers (M27: reduce repeated patterns) ──────────────────

    private fun genderIcon(gender: String) = when (gender) {
        "Female" -> "♀"; "Male" -> "♂"; else -> "◆"
    }

    private fun genderColor(gender: String, active: Boolean) = if (active) {
        when (gender) { "Female" -> COLOR_FEMALE_ACTIVE; "Male" -> COLOR_MALE_ACTIVE; else -> COLOR_TEXT_MUTED }
    } else {
        when (gender) { "Female" -> COLOR_FEMALE_DIM; "Male" -> COLOR_MALE_DIM; else -> COLOR_DIMMED }
    }

    private fun filterVoices(voices: List<VoiceRegistry.VoiceEntry>) = voices.filter { v ->
        (genderFilter == "All" || v.gender == genderFilter) &&
        (languageFilter == "All" || v.language == languageFilter)
    }

    private fun addFilteredVoiceCards(
        voices: List<VoiceRegistry.VoiceEntry>,
        cardBuilder: (VoiceRegistry.VoiceEntry) -> android.view.View
    ) {
        val filtered = filterVoices(voices)
        if (filtered.isEmpty()) {
            voiceGrid.addView(emptyLabel(getString(R.string.no_voices_match)))
        } else {
            addVoiceRows(filtered.map(cardBuilder))
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
        val previewText = txtPreview.text.toString().ifBlank { getString(R.string.preview_default, name) }
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
            hint = getString(R.string.deepinfra_key_hint)
            setText(currentKey ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
            setTextColor(COLOR_TEXT_LIGHT)
            setHintTextColor(COLOR_DIMMED)
            setBackgroundColor(COLOR_INPUT_BG)
        }

        AlertDialog.Builder(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(getString(R.string.deepinfra_key_title))
            .setMessage(getString(R.string.deepinfra_key_full_message, getString(R.string.privacy_disclosure)))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val key = input.text.toString().trim()
                SecureKeyStore.setDeepInfraKey(ctx, key)
                CloudTtsEngine.updateApiKey(key)
                renderVoiceGrid()
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
            .setTitle(getString(R.string.download_all_piper_title))
            .setMessage(getString(R.string.download_all_piper_msg, remaining, estimatedMb))
            .setPositiveButton(getString(R.string.download_btn)) { _, _ -> downloadAllPiper() }
            .setNegativeButton(getString(R.string.cancel), null)
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
                    setColor(COLOR_CARD_ACTIVE_BG)
                    setStroke((2 * dp).toInt(), COLOR_ROSE)
                } else {
                    setColor(0xFF1a1428.toInt())
                    setStroke(1, COLOR_CARD_BORDER)
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
            setTextColor(if (isActive) COLOR_ROSE else COLOR_TEXT_MUTED)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val voiceEntry = VoiceRegistry.byId(p.voiceName)
        if (voiceEntry != null) {
            card.addView(TextView(ctx).apply {
                text = voiceEntry.displayName
                textSize = 9f
                setTextColor(if (isActive) COLOR_LAVENDER else COLOR_DIMMED)
                gravity = android.view.Gravity.CENTER
                maxLines = 1
            })
        }

        // Personality hint based on pitch/speed (M13)
        val hint = personalityHint(p.pitch, p.speed)
        if (hint.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = hint
                textSize = 8f
                setTextColor(if (isActive) 0xFF7e6e98.toInt() else 0xFF5c3d7a.toInt())
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
        }

        if (isActive) {
            card.addView(android.view.View(ctx).apply {
                setBackgroundColor(COLOR_ROSE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                ).also { it.setMargins((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), 0) }
            })
        }

        return card
    }

    private fun personalityHint(pitch: Float, speed: Float): String {
        val pitchDesc = when {
            pitch >= 1.5f -> "high"
            pitch <= 0.7f -> "deep"
            else -> null
        }
        val speedDesc = when {
            speed >= 1.8f -> "fast"
            speed <= 0.7f -> "slow"
            else -> null
        }
        return listOfNotNull(pitchDesc, speedDesc).joinToString(" · ")
    }

    private fun showRenameDialog(p: VoiceProfile) {
        val ctx = context ?: return
        val et = EditText(ctx).apply {
            setText(p.name)
            hint = getString(R.string.profile_name_hint)
            selectAll()
        }
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.rename_profile))
            .setView(et)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = et.text.toString().trim().ifBlank { p.name }
                val updated = p.copy(name = newName)
                val idx = profiles.indexOfFirst { it.id == p.id }
                if (idx >= 0) profiles[idx] = updated
                if (p.id == currentProfile.id) currentProfile = updated
                VoiceProfile.saveAll(profiles, prefs)
                setupProfileSpinner()
                renderProfileGrid()
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
                    Toast.makeText(context, getString(R.string.kokoro_download_failed), Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, getString(R.string.download_failed, vid), Toast.LENGTH_LONG).show()
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
            val text = txtPreview.text.toString().ifBlank { getString(R.string.preview_text) }
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
            Toast.makeText(context, getString(R.string.saved_profile, p.name), Toast.LENGTH_SHORT).show()
        }
        btnNew.setOnClickListener {
            val et = EditText(requireContext()).apply { hint = getString(R.string.profile_name_hint) }
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.new_profile)).setView(et)
                .setPositiveButton(getString(R.string.create)) { _, _ ->
                    val name = et.text.toString().ifBlank { "Profile ${profiles.size + 1}" }
                    val p = VoiceProfile(name = name)
                    profiles.add(p); VoiceProfile.saveAll(profiles, prefs)
                    activeProfileId = p.id
                    prefs.edit().putString("active_profile_id", activeProfileId).apply()
                    loadProfileToUI(p)
                    setupProfileSpinner(); profileSpinner.setSelection(profiles.size - 1)
                    renderProfileGrid()
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        btnDelete.setOnClickListener {
            if (profiles.size <= 1) { Toast.makeText(context, getString(R.string.cant_delete_last), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(requireContext()).setTitle("${getString(R.string.delete)} ${currentProfile.name}?")
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    profiles.removeAll { it.id == currentProfile.id }
                    VoiceProfile.saveAll(profiles, prefs)
                    activeProfileId = profiles[0].id
                    prefs.edit().putString("active_profile_id", activeProfileId).apply()
                    loadProfileToUI(profiles[0])
                    setupProfileSpinner()
                    renderProfileGrid()
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        view?.findViewById<Button>(R.id.btn_stop)?.setOnClickListener {
            NotificationReaderService.instance?.stopSpeaking()
            AudioPipeline.stop()
        }
    }

    private fun loadProfileToUI(p: VoiceProfile) {
        currentProfile = p
        seekPitch.max = 150; seekPitch.progress = ((p.pitch * 100).toInt() - 50).coerceIn(0, 150)
        tvPitch.text = getString(R.string.pitch_label, p.pitch)
        seekSpeed.max = 250; seekSpeed.progress = ((p.speed * 100).toInt() - 50).coerceIn(0, 250)
        tvSpeed.text = getString(R.string.speed_label, p.speed)

        attachSeek(seekPitch) { tvPitch.text = getString(R.string.pitch_label, (it + 50) / 100f) }
        attachSeek(seekSpeed) { tvSpeed.text = getString(R.string.speed_label, (it + 50) / 100f) }

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
