package com.example.musicapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class Track(val uri: Uri, val title: String, var favorite: Boolean = false)

class MainActivity : AppCompatActivity() {
    private val tracks = mutableListOf<Track>()
    private var currentIndex = -1
    private var player: MediaPlayer? = null
    private lateinit var list: LinearLayout
    private lateinit var nowPlaying: TextView
    private lateinit var playButton: Button
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
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
                text="Chưa có bài hát. Hãy bấm “Thêm nhạc từ máy”."
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
        player?.release()
        player=MediaPlayer().apply{
            setDataSource(this@MainActivity,track.uri)
            setOnCompletionListener{next()}
            prepare(); start()
        }
        nowPlaying.text="Đang phát: ${track.title}"
        playButton.text="⏸"
    }

    private fun togglePlay(){
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

    override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}
