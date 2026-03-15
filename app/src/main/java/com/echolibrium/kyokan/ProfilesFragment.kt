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
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

class ProfilesFragment : Fragment() {


    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val c by lazy { requireContext().container }
    private val viewModel: ProfilesViewModel by viewModels()
    private var profiles = mutableListOf<VoiceProfile>()
    private var activeProfileId = ""
    private var currentProfile = VoiceProfile()
    private var genderFilter = "All"
    private var languageFilter = "All"
    private var lastVoiceGridRender = 0L
    private var pendingVoiceGridRender = false
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var initialProfileLoaded = false

    private lateinit var downloadDelegate: DownloadDelegate
    private val profileGridCallbacks = object : ProfileGridBuilder.Callbacks {
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

        // Restore filter state from config change / process death
        if (s != null) {
            genderFilter = s.getString("genderFilter", "All")
            languageFilter = s.getString("languageFilter", "All")
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
        viewModel.currentProfile.observe(viewLifecycleOwner) { profile ->
            currentProfile = profile
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
    }

    private fun setupCollapsibleSections(v: View) {
        setupCollapsibleSection(v, R.id.label_pitch_speed, R.id.section_pitch_speed, "Pitch & speed")
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
            setBackgroundColor(if (active) AppColors.filterActiveBg(requireContext()) else AppColors.surface(requireContext()))
            setTextColor(if (active) AppColors.primary(requireContext()) else AppColors.textDisabled(requireContext()))
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
        val cloudEnabled = c.cloudTtsEngine.isEnabled()
        val orpheusSubtitle = if (cloudEnabled)
            getString(R.string.orpheus_subtitle_enabled, VoiceRegistry.CLOUD_VOICES.size)
        else
            getString(R.string.orpheus_subtitle_disabled)
        val cloudKeyAction: (() -> Unit) = { showDeepInfraKeyDialog() }
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_orpheus), orpheusSubtitle, AppColors.engineOrpheus(requireContext()), cloudKeyAction, getString(R.string.key_btn)))

        addFilteredVoiceCards(VoiceRegistry.cloudEntries) { v ->
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, cloudEnabled),
                status = if (cloudEnabled) getString(R.string.cloud_status) else getString(R.string.no_api_key),
                statusColor = if (cloudEnabled) AppColors.cloudStatus(requireContext()) else AppColors.accentRed(requireContext()),
                voiceId = v.id,
                active = currentProfile.voiceName == v.id,
                accent = AppColors.engineOrpheus(requireContext()),
                enabled = cloudEnabled,
                onClick = if (cloudEnabled) {
                    { selectCloudVoice(v.id) }
                } else {
                    { showDeepInfraKeyDialog() }
                }
            )
        }

        // ── Kokoro ───────────────────────────────────────────────────────
        val kokoroReady = c.voiceDownloadManager.isModelReady(ctx)
        val kokoroDownloading = c.voiceDownloadManager.state == DownloadState.DOWNLOADING
        val kokoroCount = VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO).size
        val kokoroSubtitle = when {
            kokoroReady -> getString(R.string.kokoro_subtitle_ready, kokoroCount)
            kokoroDownloading -> getString(R.string.kokoro_subtitle_downloading, kokoroCount)
            else -> getString(R.string.kokoro_subtitle_size, kokoroCount, VoiceDownloadManager.MODEL_SIZE_MB)
        }
        val kokoroDownloadAll: (() -> Unit)? = if (!kokoroReady && !kokoroDownloading) {
            { downloadDelegate.startKokoroDownload() }
        } else null
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_kokoro), kokoroSubtitle, AppColors.engineKokoro(requireContext()), kokoroDownloadAll, getString(R.string.download_all_btn)))

        addFilteredVoiceCards(VoiceRegistry.byEngine(VoiceRegistry.Engine.KOKORO)) { v ->
            val status: String
            val statusColor: Int
            when {
                kokoroReady -> { status = getString(R.string.ready_status); statusColor = AppColors.statusReady(requireContext()) }
                kokoroDownloading -> {
                    val pct = c.voiceDownloadManager.progressPercent
                    status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                    statusColor = AppColors.engineKokoro(requireContext())
                }
                else -> { status = getString(R.string.tap_to_download); statusColor = AppColors.textDisabled(requireContext()) }
            }
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, kokoroReady),
                status = status, statusColor = statusColor,
                voiceId = v.id, active = currentProfile.voiceName == v.id,
                accent = AppColors.engineKokoro(requireContext()), enabled = kokoroReady,
                onClick = when {
                    kokoroReady -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                    !kokoroDownloading -> {{ downloadDelegate.startKokoroDownload() }}
                    else -> null
                }
            )
        }

        // ── Piper ────────────────────────────────────────────────────────
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val piperReadyCount = piperEntries.count { VoiceRegistry.isReady(ctx, it.id) }
        val piperSubtitle = getString(R.string.piper_subtitle, piperReadyCount, piperEntries.size)
        val piperHasUndownloaded = piperEntries.any { !VoiceRegistry.isReady(ctx, it.id) && !c.piperDownloadManager.isDownloading(it.id) }
        val piperDownloadAll: (() -> Unit)? = if (piperHasUndownloaded) {
            { downloadDelegate.confirmDownloadAllPiper() }
        } else null
        voiceGrid.addView(buildSectionHeader(getString(R.string.engine_piper), piperSubtitle, AppColors.enginePiper(requireContext()), piperDownloadAll, getString(R.string.download_all_btn)))

        addFilteredVoiceCards(piperEntries) { v ->
            val ready = VoiceRegistry.isReady(ctx, v.id)
            val downloading = c.piperDownloadManager.isDownloading(v.id)
            val status: String
            val statusColor: Int
            when {
                ready -> { status = getString(R.string.ready_status); statusColor = AppColors.statusReady(requireContext()) }
                downloading -> {
                    val pct = c.piperDownloadManager.getProgress(v.id)
                    status = if (pct < 0) getString(R.string.extracting_status) else "$pct%"
                    statusColor = AppColors.enginePiper(requireContext())
                }
                else -> { status = getString(R.string.tap_to_download); statusColor = AppColors.textDisabled(requireContext()) }
            }
            buildVoiceCard(
                name = v.displayName,
                icon = genderIcon(v.gender),
                iconColor = genderColor(v.gender, ready),
                status = status, statusColor = statusColor,
                voiceId = v.id, active = currentProfile.voiceName == v.id,
                accent = AppColors.enginePiper(requireContext()), enabled = ready,
                onClick = when {
                    ready -> {{ currentProfile = currentProfile.copy(voiceName = v.id); renderVoiceGrid() }}
                    !downloading -> {{ downloadDelegate.startPiperDownload(v.id) }}
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
        when (gender) { "Female" -> AppColors.genderFemale(requireContext()); "Male" -> AppColors.genderMale(requireContext()); else -> AppColors.textSecondary(requireContext()) }
    } else {
        when (gender) { "Female" -> AppColors.genderFemaleDim(requireContext()); "Male" -> AppColors.genderMaleDim(requireContext()); else -> AppColors.textDisabled(requireContext()) }
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
            service.speakDirect(previewText, tempProfile)
        } else {
            c.audioPipeline.start(ctx)
            c.audioPipeline.enqueue(AudioPipeline.Item(
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

    // ── Cloud voice privacy consent ────────────────────────────────────────

    /**
     * Select a cloud voice with privacy consent gate (Phase 3.1).
     * First time a cloud voice is selected, shows a dialog explaining that
     * notification text will be sent to DeepInfra. Consent is persisted.
     */
    private fun selectCloudVoice(voiceId: String) {
        if (prefs.getBoolean("cloud_privacy_acknowledged", false)) {
            currentProfile = currentProfile.copy(voiceName = voiceId)
            renderVoiceGrid()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cloud_privacy_title))
            .setMessage(getString(R.string.privacy_disclosure))
            .setPositiveButton(getString(R.string.cloud_privacy_accept)) { _, _ ->
                prefs.edit().putBoolean("cloud_privacy_acknowledged", true).apply()
                currentProfile = currentProfile.copy(voiceName = voiceId)
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
                viewModel.setActiveProfile(profiles[pos].id)
                loadProfileToUI(profiles[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
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
            val ctx = requireContext()

            // Use the service if running, otherwise start AudioPipeline directly
            val service = NotificationReaderService.instance
            if (service != null) {
                service.speakDirect(text, p)
            } else {
                c.audioPipeline.start(ctx)
                c.audioPipeline.enqueue(AudioPipeline.Item(
                    text = text,
                    voiceId = p.voiceName,
                    pitch = p.pitch,
                    speed = p.speed
                ))
            }
        }
        btnSave.setOnClickListener {
            val p = readProfileFromUI()
            viewModel.saveProfile(p)
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
