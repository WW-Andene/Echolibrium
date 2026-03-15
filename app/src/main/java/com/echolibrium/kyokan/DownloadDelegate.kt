package com.echolibrium.kyokan

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference

/**
 * Manages TTS voice downloads — Kokoro model + Piper individual voices.
 *
 * Extracted from ProfilesFragment (Phase 3.3) to reduce God Fragment size.
 * Handles: download triggers, progress listeners, periodic polling refresh,
 * confirmation dialogs, and error toasts.
 *
 * L-06: Uses WeakReference<Fragment> to prevent fragment leak if callbacks outlive lifecycle.
 */
class DownloadDelegate(
    fragment: Fragment,
    private val container: AppContainer,
    private val viewModel: ProfilesViewModel,
    private val onVoiceGridChanged: () -> Unit
) {
    private val fragmentRef = WeakReference(fragment)
    private val fragment: Fragment? get() = fragmentRef.get()
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
        val f = fragment ?: return
        val ctx = f.requireContext()
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
        val f = fragment ?: return
        val ctx = f.requireContext()
        VoiceRegistry.byEngine(VoiceRegistry.Engine.PIPER).forEach { v ->
            if (!VoiceRegistry.isReady(ctx, v.id) && !container.piperDownloadManager.isDownloading(v.id)) {
                container.piperDownloadManager.downloadVoice(ctx, v.id)
            }
        }
        onVoiceGridChanged()
        startRefresh()
    }

    // ── Download listeners (register once, clean up in release) ─────────────

    val kokoroStateListener: (DownloadState) -> Unit = listener@{ state ->
        val f = fragment ?: return@listener
        f.activity?.runOnUiThread {
            if (f.isAdded) {
                onVoiceGridChanged()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(f.context, f.getString(R.string.kokoro_download_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val kokoroProgressListener: (Int) -> Unit = listener@{ _ ->
        val f = fragment ?: return@listener
        f.activity?.runOnUiThread {
            if (f.isAdded) onVoiceGridChanged()
        }
    }

    val piperStateListener: (String, DownloadState) -> Unit = listener@{ vid, state ->
        val f = fragment ?: return@listener
        f.activity?.runOnUiThread {
            if (f.isAdded) {
                onVoiceGridChanged()
                if (state == DownloadState.ERROR) {
                    Toast.makeText(f.context, f.getString(R.string.download_failed, vid), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val piperProgressListener: (String, Int) -> Unit = listener@{ _, _ ->
        val f = fragment ?: return@listener
        f.activity?.runOnUiThread {
            if (f.isAdded) onVoiceGridChanged()
        }
    }

    // ── Periodic refresh during downloads ───────────────────────────────────

    fun startRefresh() {
        stopRefresh()
        refreshRunnable = object : Runnable {
            override fun run() {
                val f = fragment ?: return
                if (!f.isAdded) return
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
