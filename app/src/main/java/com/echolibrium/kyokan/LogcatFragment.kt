package com.echolibrium.kyokan

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Live logcat viewer — streams device logcat output in real-time with
 * color-coded log levels, text/tag filtering, and auto-scroll.
 *
 * Runs `logcat -v brief` in a background thread and posts colored lines
 * to the UI. Supports level filtering (V/D/I/W/E/F) and text search.
 *
 * Pipeline-only mode filters to Kyōkan-related tags for focused debugging.
 */
class LogcatFragment : Fragment() {

    private lateinit var tvLog: TextView
    private lateinit var tvLineCount: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etFilter: EditText
    private lateinit var spinnerLevel: Spinner
    private lateinit var switchAutoScroll: SwitchCompat
    private lateinit var chipAppOnly: TextView
    private lateinit var chipPipeline: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var logProcess: Process? = null
    private var readerThread: Thread? = null
    @Volatile private var running = false

    private val logBuffer = ArrayList<LogLine>(MAX_LINES)
    private var autoScroll = true
    private var appOnlyMode = false
    private var pipelineMode = false
    private var minLevel = 'V'
    private var filterText = ""
    private var lineCount = 0

    companion object {
        private const val MAX_LINES = 5000
        private const val TRIM_TO = 4000
        private val APP_PACKAGE = "com.echolibrium.kyokan"

        // Kyōkan pipeline tags for focused debugging
        private val PIPELINE_TAGS = setOf(
            "AudioPipeline", "AudioDsp", "SherpaEngine", "CloudTtsEngine",
            "NotifReader", "VoiceDownload", "PiperDownload", "DownloadUtil",
            "CrashLogger", "VoiceCommandListener", "VoiceCommandHandler",
            "NotifTranslator", "TtsAliveService"
        )

        private val LEVEL_ORDER = "VDIWEF"

        private val COLOR_VERBOSE = Color.parseColor("#5a4a6e")
        private val COLOR_DEBUG   = Color.parseColor("#b0a4c0")
        private val COLOR_INFO    = Color.parseColor("#9b7eb8")
        private val COLOR_WARN    = Color.parseColor("#ffcc00")
        private val COLOR_ERROR   = Color.parseColor("#ff4444")
        private val COLOR_FATAL   = Color.parseColor("#ff0000")
        private val COLOR_TAG     = Color.parseColor("#c48da0")
    }

    data class LogLine(val raw: String, val level: Char, val tag: String, val message: String, val pid: String)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_logcat, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        tvLog = v.findViewById(R.id.tv_log)
        tvLineCount = v.findViewById(R.id.tv_line_count)
        scrollView = v.findViewById(R.id.scroll_log)
        etFilter = v.findViewById(R.id.et_filter)
        spinnerLevel = v.findViewById(R.id.spinner_level)
        switchAutoScroll = v.findViewById(R.id.switch_autoscroll)
        chipAppOnly = v.findViewById(R.id.chip_app_only)
        chipPipeline = v.findViewById(R.id.chip_pipeline)

        // Level spinner
        val levels = arrayOf("Verbose", "Debug", "Info", "Warn", "Error", "Fatal")
        spinnerLevel.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, levels)
        spinnerLevel.setSelection(0)
        spinnerLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                minLevel = LEVEL_ORDER[pos]
                refreshDisplay()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Filter text
        etFilter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterText = s?.toString()?.lowercase() ?: ""
                refreshDisplay()
            }
        })

        // Auto-scroll toggle
        switchAutoScroll.isChecked = true
        switchAutoScroll.setOnCheckedChangeListener { _, checked -> autoScroll = checked }

        // App-only chip
        chipAppOnly.setOnClickListener {
            appOnlyMode = !appOnlyMode
            chipAppOnly.setTextColor(if (appOnlyMode) Color.parseColor("#b898d4") else Color.parseColor("#5a4a6e"))
            if (appOnlyMode) {
                pipelineMode = false
                chipPipeline.setTextColor(Color.parseColor("#5a4a6e"))
            }
            refreshDisplay()
        }

        // Pipeline chip
        chipPipeline.setOnClickListener {
            pipelineMode = !pipelineMode
            chipPipeline.setTextColor(if (pipelineMode) Color.parseColor("#9b7eb8") else Color.parseColor("#5a4a6e"))
            if (pipelineMode) {
                appOnlyMode = false
                chipAppOnly.setTextColor(Color.parseColor("#5a4a6e"))
            }
            refreshDisplay()
        }

        // Clear button
        v.findViewById<Button>(R.id.btn_clear).setOnClickListener {
            synchronized(logBuffer) { logBuffer.clear() }
            lineCount = 0
            refreshDisplay()
        }

        startLogcat()
    }

    override fun onDestroyView() {
        stopLogcat()
        super.onDestroyView()
    }

    private fun startLogcat() {
        if (running) return
        running = true

        readerThread = Thread {
            try {
                // Clear logcat buffer first, then stream
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat -v brief")
                logProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val batch = ArrayList<LogLine>(50)

                while (running) {
                    val line = reader.readLine() ?: break
                    val parsed = parseLine(line) ?: continue
                    batch.add(parsed)

                    // Batch lines for efficiency
                    if (!reader.ready() || batch.size >= 50) {
                        synchronized(logBuffer) {
                            logBuffer.addAll(batch)
                            if (logBuffer.size > MAX_LINES) {
                                val excess = logBuffer.size - TRIM_TO
                                logBuffer.subList(0, excess).clear()
                            }
                        }
                        batch.clear()
                        handler.post { refreshDisplay() }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    handler.post {
                        tvLog.text = "Failed to start logcat: ${e.message}"
                    }
                }
            }
        }.apply {
            name = "LogcatReader"
            isDaemon = true
            start()
        }
    }

    private fun stopLogcat() {
        running = false
        logProcess?.destroy()
        logProcess = null
        readerThread?.interrupt()
        readerThread = null
    }

    // Parse "D/TagName( 1234): message" format (brief)
    private fun parseLine(line: String): LogLine? {
        if (line.length < 3) return null
        val level = line[0]
        if (level !in LEVEL_ORDER) return null

        val slashIdx = line.indexOf('/')
        if (slashIdx != 1) return null

        val parenOpen = line.indexOf('(', slashIdx)
        val parenClose = line.indexOf(')', parenOpen.coerceAtLeast(0))
        if (parenOpen < 0 || parenClose < 0) return null

        val tag = line.substring(2, parenOpen).trim()
        val pid = line.substring(parenOpen + 1, parenClose).trim()
        val colonIdx = line.indexOf(':', parenClose)
        val message = if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""

        return LogLine(line, level, tag, message, pid)
    }

    private fun refreshDisplay() {
        val filtered: List<LogLine>
        synchronized(logBuffer) {
            filtered = logBuffer.filter { passesFilter(it) }
        }

        lineCount = filtered.size
        tvLineCount.text = "$lineCount lines"

        val ssb = SpannableStringBuilder()
        val displayLines = if (filtered.size > 1000) filtered.takeLast(1000) else filtered

        for (logLine in displayLines) {
            val start = ssb.length
            val levelColor = levelColor(logLine.level)

            // Level indicator
            ssb.append("${logLine.level} ")
            ssb.setSpan(ForegroundColorSpan(levelColor), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Tag
            val tagStart = ssb.length
            ssb.append("${logLine.tag}: ")
            ssb.setSpan(ForegroundColorSpan(COLOR_TAG), tagStart, tagStart + logLine.tag.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Message
            val msgStart = ssb.length
            ssb.append(logLine.message)
            ssb.setSpan(ForegroundColorSpan(levelColor), msgStart, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            ssb.append("\n")
        }

        tvLog.text = ssb

        if (autoScroll) {
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun passesFilter(line: LogLine): Boolean {
        // Level filter
        if (LEVEL_ORDER.indexOf(line.level) < LEVEL_ORDER.indexOf(minLevel)) return false

        // App-only mode
        if (appOnlyMode && !line.raw.contains(APP_PACKAGE)) return false

        // Pipeline mode
        if (pipelineMode && line.tag !in PIPELINE_TAGS) return false

        // Text filter
        if (filterText.isNotEmpty()) {
            val lower = line.raw.lowercase()
            if (!lower.contains(filterText)) return false
        }

        return true
    }

    private fun levelColor(level: Char) = when (level) {
        'V' -> COLOR_VERBOSE
        'D' -> COLOR_DEBUG
        'I' -> COLOR_INFO
        'W' -> COLOR_WARN
        'E' -> COLOR_ERROR
        'F' -> COLOR_FATAL
        else -> COLOR_DEBUG
    }
}
