package com.example.musicapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

data class Track(val uri:Uri?,val title:String,var favorite:Boolean=false,val melody:List<Pair<Double,Int>>?=null,val youtubeUrl:String?=null)

class MainActivity:AppCompatActivity(){
 private val tracks=mutableListOf<Track>();private var currentIndex=-1;private var player:MediaPlayer?=null;private var audioTrack:AudioTrack?=null
 private val generation=AtomicInteger(0);private val audioLock=Any();@Volatile private var stopBuiltin=false
 @Volatile private var builtinPaused=false
 @Volatile private var destroyed=false
 private lateinit var list:LinearLayout;private lateinit var nowPlaying:TextView;private lateinit var playButton:Button;private lateinit var search:EditText;private var favoritesOnly=false
 private val builtIn=listOf(
  Track(null,"🌙 Ru ngủ - Đêm yên bình",melody=melody(261.63,293.66,329.63,392.0,329.63,293.66,261.63)),
  Track(null,"🌙 Ru ngủ - Mây mềm",melody=melody(220.0,261.63,293.66,349.23,293.66,261.63,220.0)),
  Track(null,"🌙 Ru ngủ - Ánh trăng",melody=melody(196.0,246.94,293.66,246.94,220.0,196.0,174.61)),
  Track(null,"🌙 Ru ngủ - Giấc mơ",melody=melody(174.61,220.0,261.63,293.66,261.63,220.0,174.61)),
  Track(null,"🌙 Ru ngủ - Sao đêm",melody=melody(196.0,220.0,246.94,293.66,246.94,220.0,196.0)),
  Track(null,"☁️ Chill - Cà phê chiều",melody=melody(261.63,329.63,392.0,493.88,392.0,329.63,293.66)),
  Track(null,"☁️ Chill - Hoàng hôn",melody=melody(293.66,349.23,440.0,523.25,440.0,349.23,293.66)),
  Track(null,"☁️ Chill - Mưa nhẹ",melody=melody(220.0,277.18,329.63,369.99,329.63,277.18,220.0)),
  Track(null,"☁️ Chill - Gió biển",melody=melody(246.94,329.63,392.0,440.0,392.0,329.63,246.94)),
  Track(null,"☁️ Chill - Thư giãn",melody=melody(196.0,246.94,329.63,392.0,329.63,246.94,196.0)),
  Track(null,"▶️ Bài YouTube mới",youtubeUrl="https://youtu.be/liTfD88dbCo?si=eSPMiLH_CE1-a4Rp"))
 private fun melody(vararg n:Double)=n.map{it to 650}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun rounded(c:Int,r:Int=18)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
 private fun button(t:String)=Button(this).apply{text=t;isAllCaps=false;textSize=14f;setTextColor(Color.WHITE);background=rounded(Color.rgb(38,44,58),16)}
 override fun onCreate(b:Bundle?){super.onCreate(b);tracks.addAll(builtIn.map{it.copy()});buildUi();renderList("");requestAudioPermission()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(10));setBackgroundColor(Color.rgb(8,10,16))}
  root.addView(TextView(this).apply{text="Music";textSize=31f;setTypeface(typeface,1);setTextColor(Color.WHITE)})
  root.addView(TextView(this).apply{text="Your music • Chill • Sleep";textSize=13f;setTextColor(Color.rgb(145,155,175));setPadding(0,0,0,dp(14))})
  search=EditText(this).apply{hint="🔎  Tìm bài hát";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(Color.rgb(125,135,155));setPadding(dp(15),0,dp(15),0);background=rounded(Color.rgb(25,29,40),18)}
  root.addView(search,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(10)})
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val add=button("＋  Thêm nhạc");add.setOnClickListener{chooseAudio()};actions.addView(add,LinearLayout.LayoutParams(0,dp(48),1f))
  val fav=button("♡  Yêu thích");fav.setOnClickListener{favoritesOnly=!favoritesOnly;fav.text=if(favoritesOnly)"♥  Yêu thích" else "♡  Yêu thích";renderList(search.text.toString())};actions.addView(fav,LinearLayout.LayoutParams(0,dp(48),1f).apply{leftMargin=dp(8)});root.addView(actions)
  val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(18),dp(12),dp(18),dp(12));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(29,43,70),Color.rgb(47,30,62))).apply{cornerRadius=dp(24).toFloat()}}
  card.addView(TextView(this).apply{text="♫";textSize=38f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)})
  nowPlaying=TextView(this).apply{text="Chưa phát bài nào";textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);maxLines=2};card.addView(nowPlaying)
  card.addView(TextView(this).apply{text="READY TO PLAY";textSize=10f;gravity=Gravity.CENTER;setTextColor(Color.rgb(150,190,255));setPadding(0,dp(5),0,0)})
  root.addView(card,LinearLayout.LayoutParams(-1,dp(132)).apply{topMargin=dp(12);bottomMargin=dp(10)})
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER}
  val prev=button("⏮");prev.textSize=19f;controls.addView(prev,LinearLayout.LayoutParams(dp(62),dp(52)))
  playButton=button("▶");playButton.textSize=24f;playButton.background=rounded(Color.rgb(70,105,180),28);playButton.setOnClickListener{togglePlay()};controls.addView(playButton,LinearLayout.LayoutParams(dp(86),dp(58)).apply{leftMargin=dp(10);rightMargin=dp(10)})
  val nextBtn=button("⏭");nextBtn.textSize=19f;controls.addView(nextBtn,LinearLayout.LayoutParams(dp(62),dp(52)))
  var lastNav=0L
  prev.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;previous()}}
  nextBtn.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;next()}}
  root.addView(controls)
  root.addView(TextView(this).apply{text="  BÀI HÁT";textSize=12f;setTextColor(Color.rgb(120,135,165));setTypeface(typeface,1);setPadding(0,dp(12),0,dp(6))})
  list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(ScrollView(this).apply{isFillViewport=true;addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){renderList(s?.toString().orEmpty())};override fun afterTextChanged(e:android.text.Editable?){}})
  setContentView(root)
 }
 private fun requestAudioPermission(){val p=if(android.os.Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE;if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,arrayOf(p),10)}
 private fun chooseAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
 @Deprecated("Compatibility callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=100||c!=Activity.RESULT_OK||d==null)return;val us=mutableListOf<Uri>();d.data?.let{us.add(it)};d.clipData?.let{x->for(i in 0 until x.itemCount)us.add(x.getItemAt(i).uri)};us.distinct().forEach{u->if(tracks.none{it.uri==u}){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};tracks.add(Track(u,getFileName(u)))}};renderList(search.text.toString())}
 private fun getFileName(u:Uri):String{var n:String?=null;contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)};return n?:"Bài hát"}
 private fun renderList(qs:String){
  list.removeAllViews();val q=qs.trim().lowercase(Locale.getDefault());val visible=tracks.filter{(!favoritesOnly||it.favorite)&&it.title.lowercase(Locale.getDefault()).contains(q)}
  if(visible.isEmpty()){list.addView(TextView(this).apply{text="Chưa có bài phù hợp";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.GRAY);setPadding(0,dp(30),0,dp(30))});return}
  visible.forEach{tr->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(9),dp(7),dp(7),dp(7));background=rounded(Color.rgb(18,22,31),18)}
   val icon=TextView(this).apply{text=when{tr.title.startsWith("🌙")->"🌕";tr.title.startsWith("☁️")->"☁️";tr.youtubeUrl!=null->"▶️";else->"🎵"};textSize=24f;gravity=Gravity.CENTER;background=rounded(Color.rgb(30,36,50),15)}
   row.addView(icon,LinearLayout.LayoutParams(dp(52),dp(52)).apply{rightMargin=dp(10)});icon.setOnClickListener{playTrack(tr)}
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(66),1f)};box.addView(TextView(this).apply{text=tr.title;textSize=15f;setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END});box.addView(TextView(this).apply{text=when{tr.youtubeUrl!=null->"YouTube";tr.melody!=null->"Nhạc tích hợp";else->"Từ thiết bị"};textSize=11f;setTextColor(Color.rgb(125,140,165))});row.addView(box);box.setOnClickListener{playTrack(tr)}
   val f=button(if(tr.favorite)"♥" else "♡");f.textSize=20f;f.setOnClickListener{tr.favorite=!tr.favorite;renderList(search.text.toString())};row.addView(f,LinearLayout.LayoutParams(dp(48),dp(52)));list.addView(row,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(7)})
  }
 }
 private fun stopCurrentPlayback(){
  generation.incrementAndGet(); stopBuiltin=true; builtinPaused=false
  synchronized(audioLock){
   val a=audioTrack; audioTrack=null
   if(a!=null){try{a.stop()}catch(_:Exception){};try{a.release()}catch(_:Exception){}}
  }
  val p=player; player=null
  if(p!=null){try{p.setOnPreparedListener(null)}catch(_:Exception){};try{p.setOnCompletionListener(null)}catch(_:Exception){};try{p.setOnErrorListener(null)}catch(_:Exception){};try{p.stop()}catch(_:Exception){};try{p.release()}catch(_:Exception){}}
  if(!destroyed&&::playButton.isInitialized)playButton.text="▶"
 }
 private fun playTrack(t:Track){currentIndex=tracks.indexOf(t);stopCurrentPlayback();if(t.youtubeUrl!=null){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(t.youtubeUrl)));nowPlaying.text="Đã mở: "+t.title}catch(_:Exception){nowPlaying.text="Không mở được YouTube"};return};if(t.melody!=null){playBuiltin(t);return};val u=t.uri?:return;try{val mp=MediaPlayer();mp.setDataSource(this,u);mp.setOnPreparedListener{if(player!==mp){try{mp.release()}catch(_:Exception){};return@setOnPreparedListener};mp.start();nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"};mp.setOnCompletionListener{if(player===mp)next()};mp.setOnErrorListener{_,_,_->if(player===mp){player=null;nowPlaying.text="Không phát được tệp này";playButton.text="▶";try{mp.release()}catch(_:Exception){}};true};player=mp;mp.prepareAsync();nowPlaying.text="Đang tải: "+t.title}catch(_:Exception){player=null;playButton.text="▶"}}
 private fun playBuiltin(t:Track){
  val g=generation.get(); stopBuiltin=false; builtinPaused=false
  nowPlaying.text="Đang phát: "+t.title; playButton.text="⏸"
  Thread{
   val sr=22050; val mb=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
   if(g!=generation.get()||stopBuiltin)return@Thread
   val at=try{AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,mb.coerceAtLeast(4096),AudioTrack.MODE_STREAM)}catch(_:Exception){return@Thread}
   synchronized(audioLock){if(g!=generation.get()||stopBuiltin){try{at.release()}catch(_:Exception){};return@Thread};audioTrack=at}
   var completed=false
   try{
    at.play()
    repeat(3){for((freq,dur)in t.melody?:emptyList()){
     val count=sr*dur/1000; val s=ShortArray(count); var i=0
     while(i<count){
      if(stopBuiltin||g!=generation.get())return@Thread
      while(builtinPaused&&!stopBuiltin&&g==generation.get()){try{Thread.sleep(50)}catch(_:Exception){}}
      if(stopBuiltin||g!=generation.get())return@Thread
      val x=i.toDouble()/sr; val fi=(i/(sr*.04)).coerceAtMost(1.0); val fo=((count-i)/(sr*.04)).coerceAtMost(1.0)
      s[i]=(sin(2*PI*freq*x)*.18*minOf(fi,fo)*Short.MAX_VALUE).toInt().toShort(); i++
     }
     synchronized(audioLock){if(audioTrack!==at)return@Thread;try{at.write(s,0,s.size)}catch(_:Exception){return@Thread}}
    }}
    completed=true
   }catch(_:Exception){}finally{
    var owner=false; synchronized(audioLock){if(audioTrack===at){audioTrack=null;owner=true}}
    if(owner){try{at.stop()}catch(_:Exception){};try{at.release()}catch(_:Exception){}}
    runOnUiThread{if(g==generation.get()&&!stopBuiltin&&completed&&!destroyed){playButton.text="▶";next()}}
   }
  }.start()
 }
 private fun togglePlay(){
  synchronized(audioLock){
   audioTrack?.let{a->
    try{
     if(a.playState==AudioTrack.PLAYSTATE_PLAYING){builtinPaused=true;a.pause();playButton.text="▶"}
     else{builtinPaused=false;a.play();playButton.text="⏸"}
    }catch(_:Exception){playButton.text="▶"}
    return
   }
  }
  val p=player
  if(p!=null){
   try{if(p.isPlaying){p.pause();playButton.text="▶"}else{p.start();playButton.text="⏸"}}
   catch(_:Exception){player=null;playButton.text="▶";nowPlaying.text="Không thể tiếp tục bài hát"}
  }
 }
 private fun previous(){if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<=0)tracks.lastIndex else currentIndex-1])}
 private fun next(){if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<0||currentIndex>=tracks.lastIndex)0 else currentIndex+1])}
 override fun onDestroy(){destroyed=true;stopCurrentPlayback();super.onDestroy()}
}