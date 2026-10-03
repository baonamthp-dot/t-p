package com.example.musicapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

data class Track(
    val uri: Uri?,
    val title: String,
    var favorite: Boolean = false,
    val melody: List<Pair<Double, Int>>? = null,
    val youtubeUrl: String? = null
)

class MainActivity : AppCompatActivity() {
    private val tracks = mutableListOf<Track>()
    private var currentIndex = -1
    private var player: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private val playbackGeneration = AtomicInteger(0)
    private val audioLock = Any()
    @Volatile private var stopBuiltin = false

    private lateinit var list: LinearLayout
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: Button
    private lateinit var search: EditText
    private lateinit var favoriteFilter: Button
    private var showingFavorites = false

    private val builtInTracks = listOf(
        Track(null, "🌙 Ru ngủ - Đêm yên bình", melody = melody(261.63, 293.66, 329.63, 392.00, 329.63, 293.66, 261.63)),
        Track(null, "🌙 Ru ngủ - Mây mềm", melody = melody(220.00, 261.63, 293.66, 349.23, 293.66, 261.63, 220.00)),
        Track(null, "🌙 Ru ngủ - Ánh trăng", melody = melody(196.00, 246.94, 293.66, 246.94, 220.00, 196.00, 174.61)),
        Track(null, "🌙 Ru ngủ - Giấc mơ", melody = melody(174.61, 220.00, 261.63, 293.66, 261.63, 220.00, 174.61)),
        Track(null, "🌙 Ru ngủ - Sao đêm", melody = melody(196.00, 220.00, 246.94, 293.66, 246.94, 220.00, 196.00)),
        Track(null, "☁️ Chill - Cà phê chiều", melody = melody(261.63, 329.63, 392.00, 493.88, 392.00, 329.63, 293.66)),
        Track(null, "☁️ Chill - Hoàng hôn", melody = melody(293.66, 349.23, 440.00, 523.25, 440.00, 349.23, 293.66)),
        Track(null, "☁️ Chill - Mưa nhẹ", melody = melody(220.00, 277.18, 329.63, 369.99, 329.63, 277.18, 220.00)),
        Track(null, "☁️ Chill - Gió biển", melody = melody(246.94, 329.63, 392.00, 440.00, 392.00, 329.63, 246.94)),
        Track(null, "☁️ Chill - Thư giãn", melody = melody(196.00, 246.94, 329.63, 392.00, 329.63, 246.94, 196.00)),
        Track(null, "▶️ Bài YouTube mới", youtubeUrl = "https://youtu.be/liTfD88dbCo?si=eSPMiLH_CE1-a4Rp")
    )

    private fun melody(vararg notes: Double): List<Pair<Double, Int>> =
        notes.map { it to 650 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracks.addAll(builtInTracks.map { it.copy() })
        buildUi()
        renderList("")
        requestAudioPermission()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun styleButton(button: Button) {
        button.setTextColor(Color.WHITE)
        button.textSize = 14f
        button.isAllCaps = false
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.rgb(10, 12, 18))
        }

        root.addView(TextView(this).apply {
            text = "🎵  Music"
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Nghe nhạc • Ru ngủ • Chill"
            textSize = 14f
            setTextColor(Color.rgb(155, 165, 185))
            setPadding(dp(4), 0, 0, dp(14))
        })

        search = EditText(this).apply {
            hint = "🔎  Tìm bài hát..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(140, 150, 170))
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(28, 32, 42))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply {
            bottomMargin = dp(10)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val addButton = Button(this).apply {
            text = "＋ Thêm nhạc"
            styleButton(this)
            setOnClickListener { chooseAudio() }
        }
        actions.addView(addButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        favoriteFilter = Button(this).apply {
            text = "♡ Yêu thích"
            styleButton(this)
            setOnClickListener {
                showingFavorites = !showingFavorites
                text = if (showingFavorites) "♥ Yêu thích" else "♡ Yêu thích"
                renderList(search.text.toString())
            }
        }
        actions.addView(favoriteFilter, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            leftMargin = dp(8)
        })
        root.addView(actions)

        val nowCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(12))
            setBackgroundColor(Color.rgb(22, 26, 36))
        }

        nowCard.addView(TextView(this).apply {
            text = "ĐANG PHÁT"
            textSize = 11f
            setTextColor(Color.rgb(120, 180, 255))
            gravity = Gravity.CENTER
        })

        nowPlaying = TextView(this).apply {
            text = "Chưa phát bài nào"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, dp(7), 8, dp(8))
            maxLines = 2
        }
        nowCard.addView(nowPlaying)
        root.addView(nowCard, LinearLayout.LayoutParams(-1, dp(90)).apply {
            topMargin = dp(12)
            bottomMargin = dp(8)
        })

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply {
            text = "⏮"
            textSize = 20f
            styleButton(this)
            setOnClickListener { previous() }
        }, LinearLayout.LayoutParams(dp(68), dp(52)))

        playButton = Button(this).apply {
            text = "▶"
            textSize = 22f
            styleButton(this)
            setOnClickListener { togglePlay() }
        }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(82), dp(52)).apply {
            leftMargin = dp(8); rightMargin = dp(8)
        })

        controls.addView(Button(this).apply {
            text = "⏭"
            textSize = 20f
            styleButton(this)
            setOnClickListener { next() }
        }, LinearLayout.LayoutParams(dp(68), dp(52)))
        root.addView(controls)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        setContentView(root)
    }

    private fun requestAudioPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 10)
        }
    }

    private fun chooseAudio() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 100)
    }

    @Deprecated("Compatibility callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != Activity.RESULT_OK || data == null) return

        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }

        uris.distinct().forEach { uri ->
            if (tracks.none { it.uri == uri }) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                tracks.add(Track(uri, getFileName(uri)))
            }
        }
        renderList(search.text.toString())
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        return name ?: "Bài hát"
    }

    private fun renderList(query: String) {
        list.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        val visible = tracks.filter {
            (!showingFavorites || it.favorite) &&
            it.title.lowercase(Locale.getDefault()).contains(q)
        }

        if (visible.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (showingFavorites) "Chưa có bài yêu thích." else "Không tìm thấy bài hát."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(150, 160, 180))
                setPadding(8, dp(40), 8, dp(40))
            })
            return
        }

        visible.forEach { track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(6), dp(6))
                setBackgroundColor(Color.rgb(20, 24, 33))
            }

            row.addView(TextView(this).apply {
                text = when {
                    track.title.startsWith("🌙") -> "🌕"
                    track.title.startsWith("☁️") -> "☁️"
                    track.youtubeUrl != null -> "▶️"
                    else -> "🎵"
                }
                textSize = 25f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(34, 39, 52))
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(10) }
                setOnClickListener { playTrack(track) }
            })

            val textBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, dp(62), 1f)
                setOnClickListener { playTrack(track) }
            }
            textBox.addView(TextView(this).apply {
                text = track.title
                textSize = 15f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            textBox.addView(TextView(this).apply {
                text = when {
                    track.youtubeUrl != null -> "Mở video YouTube"
                    track.melody != null -> "Nhạc tích hợp"
                    else -> "Nhạc từ thiết bị"
                }
                textSize = 12f
                setTextColor(Color.rgb(135, 145, 165))
            })
            row.addView(textBox)

            row.addView(Button(this).apply {
                text = if (track.favorite) "♥" else "♡"
                textSize = 20f
                styleButton(this)
                setOnClickListener {
                    track.favorite = !track.favorite
                    renderList(search.text.toString())
                }
            }, LinearLayout.LayoutParams(dp(50), dp(54)))

            list.addView(row, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(7) })
        }
    }

    private fun stopCurrentPlayback() {
        playbackGeneration.incrementAndGet()
        stopBuiltin = true

        val oldPlayer = player
        player = null
        try { oldPlayer?.stop() } catch (_: Exception) {}
        try { oldPlayer?.release() } catch (_: Exception) {}

        synchronized(audioLock) {
            val oldAudio = audioTrack
            audioTrack = null
            try { oldAudio?.stop() } catch (_: Exception) {}
            try { oldAudio?.release() } catch (_: Exception) {}
        }
        playButton.text = "▶"
    }

    private fun playTrack(track: Track) {
        currentIndex = tracks.indexOf(track)
        stopCurrentPlayback()

        if (track.youtubeUrl != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.youtubeUrl)))
                nowPlaying.text = "Đã mở: " + track.title
            } catch (_: Exception) {
                nowPlaying.text = "Không mở được YouTube trên thiết bị."
            }
            return
        }

        if (track.melody != null) {
            playBuiltin(track)
            return
        }

        val uri = track.uri ?: return
        try {
            val mp = MediaPlayer()
            mp.setDataSource(this, uri)
            mp.setOnPreparedListener {
                if (player !== mp) {
                    try { mp.release() } catch (_: Exception) {}
                    return@setOnPreparedListener
                }
                mp.start()
                nowPlaying.text = "Đang phát: " + track.title
                playButton.text = "⏸"
            }
            mp.setOnCompletionListener {
                if (player === mp) next()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) {
                    player = null
                    nowPlaying.text = "Không phát được tệp này."
                    playButton.text = "▶"
                    try { mp.release() } catch (_: Exception) {}
                }
                true
            }
            player = mp
            mp.prepareAsync()
            nowPlaying.text = "Đang tải: " + track.title
        } catch (_: Exception) {
            player = null
            nowPlaying.text = "Không thể mở bài hát này."
            playButton.text = "▶"
        }
    }

    private fun playBuiltin(track: Track) {
        val generation = playbackGeneration.get()
        stopBuiltin = false
        nowPlaying.text = "Đang phát: " + track.title
        playButton.text = "⏸"

        Thread {
            val sampleRate = 22050
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (generation != playbackGeneration.get()) return@Thread

            val at = try {
                AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuffer.coerceAtLeast(4096), AudioTrack.MODE_STREAM)
            } catch (_: Exception) {
                runOnUiThread {
                    nowPlaying.text = "Không tạo được trình phát âm thanh."
                    playButton.text = "▶"
                }
                return@Thread
            }

            synchronized(audioLock) {
                if (generation != playbackGeneration.get() || stopBuiltin) {
                    try { at.release() } catch (_: Exception) {}
                    return@Thread
                }
                audioTrack = at
            }

            try {
                at.play()
                repeat(3) {
                    for ((frequency, duration) in track.melody ?: emptyList()) {
                        if (stopBuiltin || generation != playbackGeneration.get()) return@Thread
                        val count = sampleRate * duration / 1000
                        val samples = ShortArray(count)
                        for (i in 0 until count) {
                            if (stopBuiltin || generation != playbackGeneration.get()) return@Thread
                            val t = i.toDouble() / sampleRate
                            val fadeIn = (i / (sampleRate * 0.04).coerceAtLeast(1.0)).coerceAtMost(1.0)
                            val fadeOut = ((count - i) / (sampleRate * 0.04).coerceAtLeast(1.0)).coerceAtMost(1.0)
                            samples[i] = (sin(2.0 * PI * frequency * t) * 0.18 * minOf(fadeIn, fadeOut) * Short.MAX_VALUE).toInt().toShort()
                        }
                        at.write(samples, 0, samples.size)
                    }
                }
            } catch (_: Exception) {
            } finally {
                synchronized(audioLock) {
                    if (audioTrack === at) audioTrack = null
                }
                try { at.stop() } catch (_: Exception) {}
                try { at.release() } catch (_: Exception) {}
                runOnUiThread {
                    if (generation == playbackGeneration.get() && !stopBuiltin) {
                        playButton.text = "▶"
                        next()
                    }
                }
            }
        }.start()
    }

    private fun togglePlay() {
        synchronized(audioLock) {
            audioTrack?.let { at ->
                try {
                    if (at.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        at.pause()
                        playButton.text = "▶"
                    } else {
                        at.play()
                        playButton.text = "⏸"
                    }
                } catch (_: Exception) { playButton.text = "▶" }
                return
            }
        }

        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
                playButton.text = "▶"
            } else {
                p.start()
                playButton.text = "⏸"
            }
        } catch (_: Exception) {
            player = null
            playButton.text = "▶"
        }
    }

    private fun previous() {
        if (tracks.isEmpty()) return
        playTrack(tracks[if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1])
    }

    private fun next() {
        if (tracks.isEmpty()) return
        playTrack(tracks[if (currentIndex < 0 || currentIndex >= tracks.lastIndex) 0 else currentIndex + 1])
    }

    override fun onDestroy() {
        stopCurrentPlayback()
        super.onDestroy()
    }
}
