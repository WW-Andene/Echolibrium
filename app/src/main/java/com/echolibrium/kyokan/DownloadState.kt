package com.echolibrium.kyokan

/**
 * Shared download state enum used by both VoiceDownloadManager and PiperDownloadManager (H2).
 */
enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}
