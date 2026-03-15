package com.echolibrium.kyokan

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.fragment.app.viewModels

/** I-07: Uses SettingsRepository instead of direct SharedPreferences access. */
class ProfilesFragment : Fragment(), UnsavedChangesCheck {

    private val c by lazy { requireContext().container }
    private val repo by lazy { c.repo }
    private val viewModel: ProfilesViewModel by viewModels()
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    /** B-01: Single source of truth — always reads from ViewModel. Survives rotation. */
    private val currentProfile: VoiceProfile
        get() = viewModel.currentProfile.value ?: VoiceProfile()
    private var genderFilter = "All"
    private var languageFilter = "All"
    private var lastVoiceGridRender = 0L
    private var pendingVoiceGridRender = false
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var initialProfileLoaded = false
    /** F-01: Snapshot of profile at last save/load — used to detect unsaved changes. */
    private var lastSavedProfile: VoiceProfile? = null

    override fun hasUnsavedChanges(): Boolean {
        val saved = lastSavedProfile ?: return false
        return try {
            val current = readProfileFromUI()
            current != saved
        } catch (_: Exception) {
            false
        }
    }

    private lateinit var downloadDelegate: DownloadDelegate
    private val profileGridCallbacks: ProfileGridBuilder.Callbacks by lazy {
        object : ProfileGridBuilder.Callbacks {
            override fun onProfileSelected(profile: VoiceProfile) {
                viewModel.setActiveProfile(profile.id)
                loadProfileToUI(profile)
                ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
                val idx = profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0)
                profileSpinner.setSelection(idx)
            }
            override fun onProfileRenamed(profileId: String, newName: String) {
                viewModel.renameProfile(profileId, newName)
                setupProfileSpinner()
                ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
            }
        }
    }

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
    private lateinit var voiceGrid: androidx.recyclerview.widget.RecyclerView
    private lateinit var voiceGridAdapter: VoiceGridAdapter
    private lateinit var genderRow: LinearLayout
    private lateinit var nationRow: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_profiles, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        bindViews(v)

        // Restore filter state from config change / process death
        if (s != null) {
            genderFilter = s.getString("genderFilter", "All")
            languageFilter = s.getString("languageFilter", "All")
            // B-05: Restore in-progress profile edits after process death
            val savedProfileJson = s.getString("currentProfileJson", null)
            if (savedProfileJson != null) {
                try {
                    val restored = VoiceProfile.fromJson(org.json.JSONObject(savedProfileJson))
                    viewModel.updateCurrentProfile(restored)
                } catch (_: Exception) { /* ignore corrupt JSON */ }
            }
        }

        // Observe ViewModel state (M28) — keeps local state in sync
        viewModel.profiles.observe(viewLifecycleOwner) { list ->
            profiles = list.toMutableList()
            setupProfileSpinner()
            ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
            if (!initialProfileLoaded && profiles.isNotEmpty()) {
                initialProfileLoaded = true
                loadProfileToUI(profiles.find { it.id == activeProfileId } ?: profiles[0])
            }
        }
        viewModel.activeProfileId.observe(viewLifecycleOwner) { id ->
            activeProfileId = id
        }

        // Initialize download delegate
        downloadDelegate = DownloadDelegate(this, c, viewModel) { renderVoiceGridThrottled() }

        // Register download listeners once (cleaned up in onDestroyView)
        c.voiceDownloadManager.addStateListener(downloadDelegate.kokoroStateListener)
        c.voiceDownloadManager.addProgressListener(downloadDelegate.kokoroProgressListener)
        c.piperDownloadManager.addStateListener(downloadDelegate.piperStateListener)
        c.piperDownloadManager.addProgressListener(downloadDelegate.piperProgressListener)

        setupCollapsibleSections(v)
        buildFilterButtons()
        renderVoiceGrid()
        setupButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("genderFilter", genderFilter)
        outState.putString("languageFilter", languageFilter)
        outState.putString("activeProfileId", activeProfileId)
        // B-05: Save in-progress profile edits so they survive process death
        outState.putString("currentProfileJson", currentProfile.toJson().toString())
    }

    private fun setupCollapsibleSections(v: View) {
        CollapsibleSectionHelper.setup(v, R.id.label_pitch_speed, R.id.section_pitch_speed, "Pitch & speed")
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

        // Set up voice grid RecyclerView with 3-column grid
        voiceGridAdapter = VoiceGridAdapter(VoiceCardBuilder) { vid, vname -> previewVoice(vid, vname) }
        val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3)
        gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (voiceGridAdapter.getItemViewType(position)) {
                    VoiceGridAdapter.TYPE_HEADER, VoiceGridAdapter.TYPE_EMPTY -> 3
                    else -> 1
                }
            }
        }
        voiceGrid.layoutManager = gridLayoutManager
        voiceGrid.adapter = voiceGridAdapter
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
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        return Button(android.view.ContextThemeWrapper(ctx, R.style.KyokanFilterChip), null, 0).apply {
            text = label; textSize = 11f
            setBackgroundColor(if (active) AppColors.filterActiveBg(ctx) else AppColors.surface(ctx))
            setTextColor(if (active) AppColors.primary(ctx) else AppColors.textDisabled(ctx))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = (6 * dp).toInt(); layoutParams = lp
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
        val ctx = requireContext()
        val items = mutableListOf<VoiceGridItem>()

        // ── Orpheus ──────────────────────────────────────────────────────
        val cloudEnabled = c.cloudTtsEngine.isEnabled()
        items.add(VoiceGridItem.Header(
            title = getString(R.string.engine_orpheus),
            subtitle = if (cloudEnabled) getString(R.string.orpheus_subtitle_enabled, VoiceRegistry.CLOUD_VOICES.size)
                       else getString(R.string.orpheus_subtitle_disabled),
            accent = AppColors.engineOrpheus(ctx),
            actionLabel = getString(R.string.key_btn),
            onAction = { showDeepInfraKeyDialog() }
        ))
        val filteredCloud = filterVoices(VoiceRegistry.cloudEntries)
        if (filteredCloud.isEmpty()) {
            items.add(VoiceGridItem.Empty("orpheus", getString(R.string.no_voices_match)))
        } else {
            filteredCloud.forEach { v ->
                items.add(VoiceGridItem.Card(
                    voiceId = v.id, name = v.displayName,
                    icon = genderIcon(v.gender), iconColor = genderColor(v.gender, cloudEnabled),
                    status = if (cloudEnabled) getString(R.string.cloud_status) else getString(R.string.no_api_key),
                    statusColor = if (cloudEnabled) AppColors.cloudStatus(ctx) else AppColors.accentRed(ctx),
                    active = currentProfile.voiceName == v.id,
                    accent = AppColors.engineOrpheus(ctx), enabled = cloudEnabled,
                    onClick = if (cloudEnabled) {{ selectCloudVoice(v.id) }} else {{ showDeepInfraKeyDialog() }}
                ))
            }
        }

        // ── Kokoro ───────────────────────────────────────────────────────
        val kokoroReady = c.voiceDownloadManager.isModelReady(ctx)
        val kokoroDownloading = c.voiceDownloadManager.state == DownloadState.DOWNLOADING
        val kokoroCount = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).size
        items.add(VoiceGridItem.Header(
            title = getString(R.string.engine_kokoro),
            subtitle = when {
                kokoroReady -> getString(R.string.kokoro_subtitle_ready, kokoroCount)
                kokoroDownloading -> getString(R.string.kokoro_subtitle_downloading, kokoroCount)
                else -> getString(R.string.kokoro_subtitle_size, kokoroCount, VoiceDownloadManager.MODEL_SIZE_MB)
            },
            accent = AppColors.engineKokoro(ctx),
            actionLabel = getString(R.string.download_all_btn),
            onAction = if (!kokoroReady && !kokoroDownloading) {{ downloadDelegate.startKokoroDownload() }} else null
        ))
        val filteredKokoro = filterVoices(VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO))
        if (filteredKokoro.isEmpty()) {
            items.add(VoiceGridItem.Empty("kokoro", getString(R.string.no_voices_match)))
        } else {
            filteredKokoro.forEach { v ->
                val status: String; val statusColor: Int
                when {
                    kokoroReady -> { status = getString(R.string.ready_status); statusColor = AppColors.statusReady(ctx) }
                    kokoroDownloading -> {
                        val pct = c.voiceDownloadManager.progressPercent
                        status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                        statusColor = AppColors.engineKokoro(ctx)
                    }
                    else -> { status = getString(R.string.tap_to_download); statusColor = AppColors.textDisabled(ctx) }
                }
                items.add(VoiceGridItem.Card(
                    voiceId = v.id, name = v.displayName,
                    icon = genderIcon(v.gender), iconColor = genderColor(v.gender, kokoroReady),
                    status = status, statusColor = statusColor,
                    active = currentProfile.voiceName == v.id,
                    accent = AppColors.engineKokoro(ctx), enabled = kokoroReady,
                    onClick = when {
                        kokoroReady -> {{ viewModel.updateCurrentProfile(currentProfile.copy(voiceName = v.id)); renderVoiceGrid() }}
                        !kokoroDownloading -> {{ downloadDelegate.startKokoroDownload() }}
                        else -> null
                    }
                ))
            }
        }

        // ── Piper ────────────────────────────────────────────────────────
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val piperReadyCount = piperEntries.count { VoiceRegistry.isReady(ctx, it.id) }
        val piperHasUndownloaded = piperEntries.any { !VoiceRegistry.isReady(ctx, it.id) && !c.piperDownloadManager.isDownloading(it.id) }
        items.add(VoiceGridItem.Header(
            title = getString(R.string.engine_piper),
            subtitle = getString(R.string.piper_subtitle, piperReadyCount, piperEntries.size),
            accent = AppColors.enginePiper(ctx),
            actionLabel = getString(R.string.download_all_btn),
            onAction = if (piperHasUndownloaded) {{ downloadDelegate.confirmDownloadAllPiper() }} else null
        ))
        val filteredPiper = filterVoices(piperEntries)
        if (filteredPiper.isEmpty()) {
            items.add(VoiceGridItem.Empty("piper", getString(R.string.no_voices_match)))
        } else {
            filteredPiper.forEach { v ->
                val ready = VoiceRegistry.isReady(ctx, v.id)
                val downloading = c.piperDownloadManager.isDownloading(v.id)
                val status: String; val statusColor: Int
                when {
                    ready -> { status = getString(R.string.ready_status); statusColor = AppColors.statusReady(ctx) }
                    downloading -> {
                        val pct = c.piperDownloadManager.getProgress(v.id)
                        status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                        statusColor = AppColors.enginePiper(ctx)
                    }
                    else -> { status = getString(R.string.tap_to_download); statusColor = AppColors.textDisabled(ctx) }
                }
                items.add(VoiceGridItem.Card(
                    voiceId = v.id, name = v.displayName,
                    icon = genderIcon(v.gender), iconColor = genderColor(v.gender, ready),
                    status = status, statusColor = statusColor,
                    active = currentProfile.voiceName == v.id,
                    accent = AppColors.enginePiper(ctx), enabled = ready,
                    onClick = when {
                        ready -> {{ viewModel.updateCurrentProfile(currentProfile.copy(voiceName = v.id)); renderVoiceGrid() }}
                        !downloading -> {{ downloadDelegate.startPiperDownload(v.id) }}
                        else -> null
                    }
                ))
            }
        }

        // Force the parent ScrollView to re-measure after DiffUtil async diff completes.
        // Without this, the ScrollView measures the RecyclerView's height BEFORE items
        // arrive (height=0 or stale), then never re-measures — clipping Piper cards.
        voiceGridAdapter.submitList(items) {
            voiceGrid.post { voiceGrid.requestLayout() }
        }
    }

    // ── Voice grid helpers ──────────────────────────────────────────────────

    private fun genderIcon(gender: String) = when (gender) {
        "Female" -> "♀"; "Male" -> "♂"; else -> "◆"
    }

    private fun genderColor(gender: String, active: Boolean) = if (active) {
        when (gender) { "Female" -> AppColors.genderFemale(requireContext()); "Male" -> AppColors.genderMale(requireContext()); else -> AppColors.textSecondary(requireContext()) }
    } else {
        when (gender) { "Female" -> AppColors.genderFemaleDim(requireContext()); "Male" -> AppColors.genderMaleDim(requireContext()); else -> AppColors.textDisabled(requireContext()) }
    }

    private fun filterVoices(voices: List<VoiceRegistry.VoiceEntry>) = voices.filter { v ->
        (genderFilter == "All" || v.gender == genderFilter) &&
        (languageFilter == "All" || v.language == languageFilter)
    }

    private fun playPreview(text: String, profile: VoiceProfile) {
        val ctx = requireContext()
        val service = NotificationReaderService.instance
        if (service != null) {
            service.speakDirect(text, profile)
        } else {
            c.audioPipeline.start(ctx)
            c.audioPipeline.enqueue(AudioPipeline.Item(
                text = text,
                voiceId = profile.voiceName,
                pitch = profile.pitch,
                speed = profile.speed
            ))
        }
    }

    private fun previewVoice(voiceId: String, name: String) {
        val previewText = txtPreview.text.toString().ifBlank { getString(R.string.preview_default, name) }
        playPreview(previewText, currentProfile.copy(voiceName = voiceId))
    }

    // ── Cloud voice privacy consent ────────────────────────────────────────

    /**
     * Select a cloud voice with privacy consent gate (Phase 3.1).
     * First time a cloud voice is selected, shows a dialog explaining that
     * notification text will be sent to DeepInfra. Consent is persisted.
     */
    private fun selectCloudVoice(voiceId: String) {
        if (repo.getBoolean("cloud_privacy_acknowledged", false)) {
            viewModel.updateCurrentProfile(currentProfile.copy(voiceName = voiceId))
            renderVoiceGrid()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cloud_privacy_title))
            .setMessage(getString(R.string.privacy_disclosure))
            .setPositiveButton(getString(R.string.cloud_privacy_accept)) { _, _ ->
                repo.putBoolean("cloud_privacy_acknowledged", true)
                viewModel.updateCurrentProfile(currentProfile.copy(voiceName = voiceId))
                renderVoiceGrid()
            }
            .setNegativeButton(getString(R.string.cloud_privacy_decline), null)
            .show()
    }

    // ── API key dialog ─────────────────────────────────────────────────────

    private fun showDeepInfraKeyDialog() {
        val ctx = requireContext()
        val currentKey = try { SecureKeyStore.getDeepInfraKey(ctx) } catch (_: Exception) { null }

        val input = EditText(ctx).apply {
            hint = getString(R.string.deepinfra_key_hint)
            setText(currentKey ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
            setTextColor(AppColors.textBright(requireContext()))
            setHintTextColor(AppColors.textDisabled(requireContext()))
            setBackgroundColor(AppColors.inputBg(requireContext()))
        }

        AlertDialog.Builder(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(getString(R.string.deepinfra_key_title))
            .setMessage(getString(R.string.deepinfra_key_full_message, getString(R.string.privacy_disclosure)))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val key = input.text.toString().trim()
                SecureKeyStore.setDeepInfraKey(ctx, key)
                c.cloudTtsEngine.updateApiKey(key)
                renderVoiceGrid()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupProfileSpinner() {
        profileSpinner.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, profiles.map { p ->
                "${p.emoji} ${p.name}"
            })
        val idx = profiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
        profileSpinner.setSelection(idx)
        profileSpinner.onItemSelectedSkipFirst { pos ->
            viewModel.setActiveProfile(profiles[pos].id)
            loadProfileToUI(profiles[pos])
        }
    }

    private val synthesisErrorListener: (String, String) -> Unit = { _, reason ->
        activity?.runOnUiThread {
            if (isAdded) Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupButtons() {
        // Show toast when TTS synthesis fails silently (B3: use listener list, not single slot)
        c.audioPipeline.addSynthesisErrorListener(synthesisErrorListener)

        btnTest.setOnClickListener {
            val p = readProfileFromUI()
            val text = txtPreview.text.toString().ifBlank { getString(R.string.preview_text) }
            playPreview(text, p)
        }
        btnSave.setOnClickListener {
            val p = readProfileFromUI()
            viewModel.saveProfile(p)
            lastSavedProfile = p  // F-01: update snapshot after save
            ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
            Toast.makeText(context, getString(R.string.saved_profile, p.name), Toast.LENGTH_SHORT).show()
        }
        btnNew.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = getString(R.string.profile_name_hint)
                filters = arrayOf(android.text.InputFilter.LengthFilter(40)) // L6
            }
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.new_profile)).setView(et)
                .setPositiveButton(getString(R.string.create)) { _, _ ->
                    val name = et.text.toString().ifBlank { "Profile ${profiles.size + 1}" }
                    val p = viewModel.createProfile(name)
                    loadProfileToUI(p)
                    setupProfileSpinner(); profileSpinner.setSelection(profiles.size - 1)
                    ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        btnDelete.setOnClickListener {
            if (profiles.size <= 1) { Toast.makeText(context, getString(R.string.cant_delete_last), Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(requireContext()).setTitle("${getString(R.string.delete)} ${currentProfile.name}?")
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.deleteCurrentProfile()
                    loadProfileToUI(profiles[0])
                    setupProfileSpinner()
                    ProfileGridBuilder.renderGrid(profileGrid, profiles, activeProfileId, profileGridCallbacks)
                }.setNegativeButton(getString(R.string.cancel), null).show()
        }
        view?.findViewById<Button>(R.id.btn_stop)?.setOnClickListener {
            NotificationReaderService.instance?.stopSpeaking()
            c.audioPipeline.stop()
        }
    }

    private fun loadProfileToUI(p: VoiceProfile) {
        viewModel.updateCurrentProfile(p)
        lastSavedProfile = p  // F-01: snapshot for unsaved changes detection
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
        downloadDelegate.startRefresh()
    }

    override fun onPause() {
        super.onPause()
        downloadDelegate.stopRefresh()
    }

    override fun onDestroyView() {
        downloadDelegate.stopRefresh()
        c.audioPipeline.removeSynthesisErrorListener(synthesisErrorListener)
        c.voiceDownloadManager.removeStateListener(downloadDelegate.kokoroStateListener)
        c.voiceDownloadManager.removeProgressListener(downloadDelegate.kokoroProgressListener)
        c.piperDownloadManager.removeStateListener(downloadDelegate.piperStateListener)
        c.piperDownloadManager.removeProgressListener(downloadDelegate.piperProgressListener)
        super.onDestroyView()
    }
}
