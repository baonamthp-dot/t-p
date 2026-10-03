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
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(10));setBackgroundColor(Color.rgb(5,8,20))}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  top.addView(TextView(this).apply{text="♫";textSize=28f;setTextColor(Color.rgb(90,190,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
  val titleBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(52),1f)}
  titleBox.addView(TextView(this).apply{text="Music";textSize=28f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE)})
  titleBox.addView(TextView(this).apply{text="Âm nhạc cho tâm hồn";textSize=12f;setTextColor(Color.rgb(145,170,205))})
  top.addView(titleBox)
  top.addView(TextView(this).apply{text="⌕";textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(42),dp(48)))
  top.addView(TextView(this).apply{text="⚙";textSize=23f;setTextColor(Color.rgb(180,195,220));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(42),dp(48)))
  root.addView(top)
  search=EditText(this).apply{hint="🔎  Tìm bài hát";setSingleLine();textSize=14f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(120,140,175));setPadding(dp(15),0,dp(15),0);background=GradientDrawable().apply{setColor(Color.rgb(13,22,46));cornerRadius=dp(22).toFloat();setStroke(dp(1),Color.rgb(35,65,110))}}
  root.addView(search,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(6);bottomMargin=dp(10)})
  val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(16),dp(12),dp(16),dp(12));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(7,35,75),Color.rgb(28,12,60),Color.rgb(8,18,42))).apply{cornerRadius=dp(25).toFloat();setStroke(dp(1),Color.rgb(47,102,175))}}
  hero.addView(TextView(this).apply{text="✦  ·  ✧   ☾   ✧  ·  ✦";textSize=12f;setTextColor(Color.rgb(100,180,255));gravity=Gravity.CENTER})
  hero.addView(TextView(this).apply{text="🌕";textSize=58f;gravity=Gravity.CENTER;setShadowLayer(dp(14).toFloat(),0f,0f,Color.rgb(80,160,255))})
  hero.addView(TextView(this).apply{text="Những bản nhạc đưa bạn đến bình yên";textSize=18f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER})
  hero.addView(TextView(this).apply{text="Chill • Ngủ ngon • Thư giãn";textSize=12f;setTextColor(Color.rgb(155,190,235));gravity=Gravity.CENTER;setPadding(0,dp(4),0,0)})
  root.addView(hero,LinearLayout.LayoutParams(-1,dp(164)).apply{bottomMargin=dp(10)})
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun action(label:String,icon:String,click:()->Unit)=TextView(this).apply{text="$icon\n$label";textSize=12f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(13,27,57),20);setPadding(0,dp(6),0,dp(4));setOnClickListener{click()}}
  val add=action("Thêm nhạc","♫"){chooseAudio()};actions.addView(add,LinearLayout.LayoutParams(0,dp(62),1f))
  val fav=action("Yêu thích","♥"){favoritesOnly=!favoritesOnly;renderList(search.text.toString())};actions.addView(fav,LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  val sleep=action("Nhạc ngủ","☾"){search.setText("Ru ngủ")};actions.addView(sleep,LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  val yt=action("YouTube","▶"){val t=tracks.firstOrNull{it.youtubeUrl!=null};if(t!=null)playTrack(t)};actions.addView(yt,LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  root.addView(actions)
  root.addView(TextView(this).apply{text="DANH SÁCH NỔI BẬT";textSize=13f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);setPadding(dp(4),dp(12),0,dp(6))})
  val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=rounded(Color.rgb(10,18,38),20)}
  card.addView(TextView(this).apply{text="🌙  Nhạc ngủ";textSize=15f;setTextColor(Color.WHITE)})
  val cardSubtitle=TextView(this)
  cardSubtitle.text="Thư giãn - Ngủ ngon - Sống chậm"
  cardSubtitle.textSize=11f
  cardSubtitle.setTextColor(Color.rgb(130,155,195))
  cardSubtitle.setPadding(0,dp(3),0,0)
  card.addView(cardSubtitle)
  root.addView(card,LinearLayout.LayoutParams(-1,dp(64)).apply{bottomMargin=dp(8)})
  val nowCard=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(8),dp(8));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(17,31,63),Color.rgb(31,18,58))).apply{cornerRadius=dp(20).toFloat();setStroke(dp(1),Color.rgb(45,83,145))}}
  nowCard.addView(TextView(this).apply{text="🌕";textSize=34f;gravity=Gravity.CENTER;background=rounded(Color.rgb(23,37,67),17)},LinearLayout.LayoutParams(dp(58),dp(58)).apply{rightMargin=dp(10)})
  val np=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(60),1f)}
  nowPlaying=TextView(this).apply{text="Chưa phát bài nào";textSize=15f;setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};np.addView(nowPlaying)
  np.addView(TextView(this).apply{text="ĐANG CHỜ PHÁT";textSize=9f;setTextColor(Color.rgb(100,185,255));setPadding(0,dp(3),0,0)});nowCard.addView(np)
  playButton=button("▶").apply{textSize=21f;background=rounded(Color.rgb(36,118,225),28);setOnClickListener{togglePlay()}};nowCard.addView(playButton,LinearLayout.LayoutParams(dp(54),dp(54)))
  root.addView(nowCard,LinearLayout.LayoutParams(-1,dp(76)).apply{bottomMargin=dp(8)})
  val controls=LinearLayout(this).apply{gravity=Gravity.CENTER}
  val prev=button("⏮");prev.textSize=18f;controls.addView(prev,LinearLayout.LayoutParams(dp(58),dp(46)))
  val nextBtn=button("⏭");nextBtn.textSize=18f;controls.addView(nextBtn,LinearLayout.LayoutParams(dp(58),dp(46)).apply{leftMargin=dp(8)})
  var lastNav=0L
  prev.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;previous()}}
  nextBtn.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;next()}}
  root.addView(controls)
  root.addView(TextView(this).apply{text="BÀI HÁT";textSize=12f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.rgb(120,150,195));setPadding(dp(4),dp(6),0,dp(6))})
  list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  root.addView(ScrollView(this).apply{isFillViewport=true;addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){renderList(s?.toString().orEmpty())};override fun afterTextChanged(e:android.text.Editable?){}})
  setContentView(root)
 }
 private fun requestAudioPermission(){val p=if(android.os.Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE;if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,arrayOf(p),10)}
 private fun chooseAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
 @Deprecated("Compatibility callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=100||c!=Activity.RESULT_OK||d==null)return;val us=mutableListOf<Uri>();d.data?.let{us.add(it)};d.clipData?.let{x->for(i in 0 until x.itemCount)us.add(x.getItemAt(i).uri)};us.distinct().forEach{u->if(tracks.none{it.uri==u}){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};tracks.add(Track(u,getFileName(u)))}};renderList(search.text.toString())}
 private fun getFileName(u:Uri):String{var n:String?=null;contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)};return n?:"Bài hát"}
 private fun renderList(qs:String){
  list.removeAllViews();val q=qs.trim().lowercase(Locale.getDefault());val visible=tracks.filter{(!favoritesOnly||it.favorite)&&it.title.lowercase(Locale.getDefault()).contains(q)}
  if(visible.isEmpty()){list.addView(TextView(this).apply{text="🌙\n\nChưa có bài phù hợp";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.rgb(125,145,180));setPadding(0,dp(28),0,dp(28))});return}
  visible.forEach{tr->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(7),dp(6));background=GradientDrawable(GradientDrawable.Orientation.LT_BR,intArrayOf(Color.rgb(11,22,43),Color.rgb(17,17,36))).apply{cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(25,52,90))}}
   val icon=TextView(this).apply{text=when{tr.title.startsWith("🌙")->"🌕";tr.title.startsWith("☁️")->"☁";tr.youtubeUrl!=null->"▶";else->"♫"};textSize=23f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(22,36,66),15)}
   row.addView(icon,LinearLayout.LayoutParams(dp(50),dp(50)).apply{rightMargin=dp(9)});icon.setOnClickListener{playTrack(tr)}
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(62),1f)}
   box.addView(TextView(this).apply{text=tr.title;textSize=14f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   box.addView(TextView(this).apply{text=when{tr.youtubeUrl!=null->"YouTube • Mở video";tr.melody!=null->"Nhạc ngủ / Chill • Tích hợp";else->"Từ thiết bị"};textSize=10f;setTextColor(Color.rgb(110,145,190));setPadding(0,dp(3),0,0)})
   row.addView(box);box.setOnClickListener{playTrack(tr)}
   val f=button(if(tr.favorite)"♥" else "♡");f.textSize=19f;f.background=rounded(Color.rgb(18,30,56),18);f.setOnClickListener{tr.favorite=!tr.favorite;renderList(search.text.toString())};row.addView(f,LinearLayout.LayoutParams(dp(46),dp(50)))
   list.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(7)})
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