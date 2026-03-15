package com.echolibrium.kyokan

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * Manages TTS voice downloads — Kokoro model + Piper individual voices.
 *
 * Extracted from ProfilesFragment (Phase 3.3) to reduce God Fragment size.
 * Handles: download triggers, progress listeners, periodic polling refresh,
 * confirmation dialogs, and error toasts.
 */
class DownloadDelegate(
    private val fragment: Fragment,
    private val container: AppContainer,
    private val viewModel: ProfilesViewModel,
    private val onVoiceGridChanged: () -> Unit
) {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // ── Download triggers ───────────────────────────────────────────────────

    fun startKokoroDownload() {
        viewModel.startKokoroDownload()
        onVoiceGridChanged()
        startRefresh()
    }

    fun startPiperDownload(voiceId: String) {
        viewModel.startPiperDownload(voiceId)
        onVoiceGridChanged()
        startRefresh()
    }

    fun confirmDownloadAllPiper() {
        val ctx = fragment.requireContext()
        val piperEntries = VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER)
        val remaining = piperEntries.count {
            !VoiceRegistry.isReady(ctx, it.id) && !container.piperDownloadManager.isDownloading(it.id)
        }
        val estimatedMb = piperEntries
            .filter { !VoiceRegistry.isReady(ctx, it.id) }
            .sumOf { PiperVoices.byId(it.id)?.sizeMb ?: 40 }
        AlertDialog.Builder(ctx, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(ctx.getString(R.string.download_all_piper_title))
            .setMessage(ctx.getString(R.string.download_all_piper_msg, remaining, estimatedMb))
            .setPositiveButton(ctx.getString(R.string.download_btn)) { _, _ -> downloadAllPiper() }
            .setNegativeButton(ctx.getString(R.string.cancel), null)
            .show()
    }

    private fun downloadAllPiper() {
        val ctx = fragment.requireContext()
        VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER).forEach { v ->
            if (!VoiceRegistry.isReady(ctx, v.id) && !container.piperDownloadManager.isDownloading(v.id)) {
                container.piperDownloadManager.downloadVoice(ctx, v.id)
            }
        }
        onVoiceGridChanged()
        startRefresh()
    }

    // ── Download listeners (register once, clean up in release) ─────────────

    val kokoroStateListener: (DownloadState) -> Unit = { state ->
        fragment.activity?.runOnUiThread {
            if (fragment.isAdded) {
                onVoiceGridChanged()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(
                        fragment.context,
                        fragment.getString(R.string.kokoro_download_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val kokoroProgressListener: (Int) -> Unit = { _ ->
        fragment.activity?.runOnUiThread {
            if (fragment.isAdded) onVoiceGridChanged()
        }
    }

    val piperStateListener: (String, DownloadState) -> Unit = { vid, state ->
        fragment.activity?.runOnUiThread {
            if (fragment.isAdded) {
                onVoiceGridChanged()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(
                        fragment.context,
                        fragment.getString(R.string.download_failed, vid),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val piperProgressListener: (String, Int) -> Unit = { _, _ ->
        fragment.activity?.runOnUiThread {
            if (fragment.isAdded) onVoiceGridChanged()
        }
    }

    // ── Periodic refresh during downloads ───────────────────────────────────

    fun startRefresh() {
        stopRefresh()
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!fragment.isAdded) return
                val kokoroDownloading = container.voiceDownloadManager.state == DownloadState.DOWNLOADING
                val piperDownloading = container.piperDownloadManager.isAnyDownloading()
                onVoiceGridChanged()
                if (kokoroDownloading || piperDownloading) {
                    refreshHandler.postDelayed(this, 1000)
                }
            }
        }
        val kokoroDownloading = container.voiceDownloadManager.state == DownloadState.DOWNLOADING
        val piperDownloading = container.piperDownloadManager.isAnyDownloading()
        if (kokoroDownloading || piperDownloading) {
            refreshHandler.postDelayed(refreshRunnable!!, 1000)
        }
    }

    fun stopRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
    }
}
