package com.example.musicapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
    @Volatile private var stopBuiltin = false
    private lateinit var list: LinearLayout
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: Button
    private lateinit var search: EditText

    private val builtInTracks = listOf(
        Track(null, "🌙 Ru ngủ - Đêm yên bình", false, melody(261.63, 293.66, 329.63, 392.00, 329.63, 293.66, 261.63)),
        Track(null, "🌙 Ru ngủ - Mây mềm", false, melody(220.00, 261.63, 293.66, 349.23, 293.66, 261.63, 220.00)),
        Track(null, "🌙 Ru ngủ - Ánh trăng", false, melody(196.00, 246.94, 293.66, 246.94, 220.00, 196.00, 174.61)),
        Track(null, "🌙 Ru ngủ - Giấc mơ", false, melody(174.61, 220.00, 261.63, 293.66, 261.63, 220.00, 174.61)),
        Track(null, "🌙 Ru ngủ - Sao đêm", false, melody(196.00, 220.00, 246.94, 293.66, 246.94, 220.00, 196.00)),
        Track(null, "☁️ Chill - Cà phê chiều", false, melody(261.63, 329.63, 392.00, 493.88, 392.00, 329.63, 293.66)),
        Track(null, "☁️ Chill - Hoàng hôn", false, melody(293.66, 349.23, 440.00, 523.25, 440.00, 349.23, 293.66)),
        Track(null, "☁️ Chill - Mưa nhẹ", false, melody(220.00, 277.18, 329.63, 369.99, 329.63, 277.18, 220.00)),
        Track(null, "☁️ Chill - Gió biển", false, melody(246.94, 329.63, 392.00, 440.00, 392.00, 329.63, 246.94)),
        Track(null, "☁️ Chill - Thư giãn", false, melody(196.00, 246.94, 329.63, 392.00, 329.63, 246.94, 196.00)),
        Track(null, "▶️ Bài YouTube mới", false, null, "https://youtu.be/liTfD88dbCo?si=eSPMiLH_CE1-a4Rp")
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

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,24,24,16)
            setBackgroundColor(0xFF101114.toInt())
        }
        root.addView(TextView(this).apply {
            text = "🎵  Music"; textSize = 30f; setTextColor(0xFFFFFFFF.toInt())
        })
        search = EditText(this).apply {
            hint = "Tìm bài hát..."; setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFFAAAAAA.toInt())
        }
        root.addView(search, LinearLayout.LayoutParams(-1,56))
        root.addView(Button(this).apply {
            text = "＋ Thêm nhạc từ máy"; setOnClickListener { chooseAudio() }
        })
        nowPlaying = TextView(this).apply {
            text = "Chưa phát bài nào"; textSize = 17f; gravity = Gravity.CENTER
            setTextColor(0xFFDDDDDD.toInt()); setPadding(8,20,8,10)
        }
        root.addView(nowPlaying)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply { text="⏮"; setOnClickListener{ previous() } })
        playButton = Button(this).apply { text="▶"; setOnClickListener{ togglePlay() } }
        controls.addView(playButton)
        controls.addView(Button(this).apply { text="⏭"; setOnClickListener{ next() } })
        root.addView(controls)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}
            override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){ renderList(s?.toString().orEmpty()) }
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        setContentView(root)
    }

    private fun requestAudioPermission() {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this,p) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,arrayOf(p),10)
    }

    private fun chooseAudio() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type="audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addCategory(Intent.CATEGORY_OPENABLE)
        },100)
    }

    @Deprecated("Compatibility callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=100 || resultCode!=Activity.RESULT_OK || data==null) return
        val uris=mutableListOf<Uri>()
        data.data?.let{uris.add(it)}
        data.clipData?.let{c->for(i in 0 until c.itemCount) uris.add(c.getItemAt(i).uri)}
        uris.forEach{u->if(tracks.none{it.uri==u}) tracks.add(Track(u,getFileName(u)))}
        renderList(search.text.toString())
    }

    private fun getFileName(uri:Uri):String {
        var name:String?=null
        contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
            if(it.moveToFirst()) name=it.getString(0)
        }
        return name ?: "Bài hát"
    }

    private fun renderList(query:String) {
        list.removeAllViews()
        val q=query.trim().lowercase()
        val visible=tracks.filter{it.title.lowercase().contains(q)}
        if(visible.isEmpty()){
            list.addView(TextView(this).apply{
                text="Không tìm thấy bài hát."
                setTextColor(0xFFAAAAAA.toInt()); setPadding(8,30,8,30)
            }); return
        }
        visible.forEach{track->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val name=TextView(this).apply{
                text=track.title; textSize=16f; setTextColor(0xFFFFFFFF.toInt())
                layoutParams=LinearLayout.LayoutParams(0,64,1f); gravity=Gravity.CENTER_VERTICAL
                setOnClickListener{playTrack(track)}
            }
            row.addView(name)
            row.addView(Button(this).apply{
                text=if(track.favorite)"♥" else "♡"
                setOnClickListener{track.favorite=!track.favorite;text=if(track.favorite)"♥" else "♡"}
            })
            list.addView(row)
        }
    }

    private fun playTrack(track:Track){
        currentIndex=tracks.indexOf(track)
        stopBuiltin = true
        player?.release()
        player=null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack=null

        if (track.melody != null) {
            playBuiltin(track)
        } else if (track.uri != null) {
            player=MediaPlayer().apply{
                setDataSource(this@MainActivity,track.uri)
                setOnCompletionListener{next()}
                prepare(); start()
            }
            nowPlaying.text="Đang phát: ${track.title}"
            playButton.text="⏸"
        }
    }

    private fun playBuiltin(track: Track) {
        stopBuiltin = false
        nowPlaying.text = "Đang phát: ${track.title}"
        playButton.text = "⏸"
        Thread {
            val sampleRate = 22050
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val at = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                minBuffer.coerceAtLeast(4096), AudioTrack.MODE_STREAM
            )
            audioTrack = at
            at.play()
            try {
                repeat(3) {
                    for ((frequency, duration) in (track.melody ?: emptyList())) {
                        if (stopBuiltin) return@Thread
                        val count = sampleRate * duration / 1000
                        val samples = ShortArray(count)
                        for (i in 0 until count) {
                            val t = i.toDouble() / sampleRate
                            val fade = minOf(1.0, i / (sampleRate * 0.04).coerceAtLeast(1.0),
                                (count - i) / (sampleRate * 0.04).coerceAtLeast(1.0))
                            samples[i] = (sin(2.0 * PI * frequency * t) * 0.20 * fade * Short.MAX_VALUE).toInt().toShort()
                        }
                        at.write(samples, 0, samples.size)
                    }
                }
            } finally {
                at.stop()
                at.release()
                runOnUiThread {
                    if (!stopBuiltin && currentIndex >= 0) next()
                }
            }
        }.start()
    }

    private fun togglePlay(){
        if (audioTrack != null) {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause(); playButton.text="▶"
            } else {
                audioTrack?.play(); playButton.text="⏸"
            }
            return
        }
        val p=player ?: return
        if(p.isPlaying){p.pause();playButton.text="▶"} else {p.start();playButton.text="⏸"}
    }

    private fun previous(){
        if(tracks.isEmpty())return
        playTrack(tracks[if(currentIndex<=0) tracks.lastIndex else currentIndex-1])
    }

    private fun next(){
        if(tracks.isEmpty())return
        playTrack(tracks[if(currentIndex<0 || currentIndex>=tracks.lastIndex)0 else currentIndex+1])
    }

    override fun onDestroy(){
        stopBuiltin=true
        player?.release()
        audioTrack?.release()
        player=null
        audioTrack=null
        super.onDestroy()
    }
}
