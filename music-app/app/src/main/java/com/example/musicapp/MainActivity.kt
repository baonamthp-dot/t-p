package com.example.musicapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin

data class Track(var uri:Uri?,val title:String,var favorite:Boolean=false,val melody:List<Pair<Double,Int>>?=null,val youtubeUrl:String?=null,val streamUrl:String?=null)

class MainActivity:AppCompatActivity(){
 private val tracks=mutableListOf<Track>();private var currentIndex=-1;private var player:MediaPlayer?=null;private var audioTrack:AudioTrack?=null
 private val generation=AtomicInteger(0);private val audioLock=Any();@Volatile private var stopBuiltin=false
 @Volatile private var builtinPaused=false
 @Volatile private var destroyed=false
 private lateinit var list:LinearLayout;private lateinit var nowPlaying:TextView;private lateinit var playButton:Button;private lateinit var search:EditText;private var favoritesOnly=false
 private var shuffle=false
 private var repeatOne=false
 private var pendingExportText=""
 private var pendingYoutubeAudio:Track?=null
 private var sleepTimer: android.os.CountDownTimer?=null
 private val prefs by lazy{getSharedPreferences("music_app",MODE_PRIVATE)}
 private var isOwner=false
 private var aiUnlocked=false
 private var openAiApiKey=""
 private val openAiModel="gpt-4.1-mini"
 private val aiCodeHash="9418c2e92ded7aa45a0d568d4c773be7bb71f233c9c7707f8a0c09dae9f8ceaf"
 private var tts:TextToSpeech?=null
 private val ownerCodeHash="0bc685edb692c7d408cd670c5b73c29a577188b66dd3a7b8f75e357a5b9b00e8"
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
  Track(null,"▶️ Bài YouTube mới",youtubeUrl="https://music.youtube.com/watch?v=liTfD88dbCo"),
  Track(null,"▶️ YouTube Music - Bài bạn gửi",youtubeUrl="https://music.youtube.com/watch?v=tu9KGLE7zLs&si=rqYnO5JnnTml768F"),
  Track(null,"▶️ YouTube Music - Bài 2",youtubeUrl="https://music.youtube.com/watch?v=0GVExpdmoDs&si=nGyRCBfO_XpkVmsi"),
  Track(null,"▶️ YouTube Music - Bài 3",youtubeUrl="https://music.youtube.com/watch?v=AYr6emQJnM0&si=vDkbqfP1BPYdnCIL"),
  Track(null,"▶️ YouTube Music - Bài 4",youtubeUrl="https://music.youtube.com/watch?v=mY9fNwGE7YA&si=CUMjMXKy0nh45fVB"),
  Track(null,"🎤 Thanh niên làm theo lời Bác",streamUrl="https://commons.wikimedia.org/wiki/Special:Redirect/file/Thanh_ni%C3%AAn_l%C3%A0m_theo_l%E1%BB%9Di_B%C3%A1c_(c%C3%B3_l%E1%BB%9Di).ogg"),
  Track(null,"🎤 Tiến Quân Ca (bản ghi CC0)",streamUrl="https://commons.wikimedia.org/wiki/Special:Redirect/file/TienQuanCa_PAVN_records.ogg")
 )
 private fun melody(vararg n:Double)=n.map{it to 650}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun rounded(c:Int,r:Int=18)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
 private fun button(t:String)=Button(this).apply{text=t;isAllCaps=false;textSize=14f;setTextColor(Color.WHITE);background=rounded(Color.rgb(38,44,58),16)}
 override fun onCreate(b:Bundle?){super.onCreate(b);tracks.addAll(builtIn.map{it.copy()});tracks.filter{it.youtubeUrl!=null}.forEach{t->prefs.getString(audioPreferenceKey(t),null)?.let{saved->t.uri=Uri.parse(saved)}};loadFavorites();tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val vi=Locale("vi","VN");val result=tts?.setLanguage(vi)?:TextToSpeech.LANG_NOT_SUPPORTED;if(result==TextToSpeech.LANG_NOT_SUPPORTED||result==TextToSpeech.LANG_MISSING_DATA){runOnUiThread{Toast.makeText(this,"⚠️ Máy chưa có dữ liệu giọng nói tiếng Việt. Hãy cài Google Text-to-Speech.",Toast.LENGTH_LONG).show()}};tts?.setSpeechRate(0.95f)}else{runOnUiThread{Toast.makeText(this,"⚠️ Không khởi tạo được giọng nói.",Toast.LENGTH_LONG).show()}}};buildUi();renderList("");showWelcomeIfNeeded()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(10));setBackgroundResource(com.example.musicapp.R.drawable.moon_background)}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  top.addView(TextView(this).apply{text="♫";textSize=28f;setTextColor(Color.rgb(90,190,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
  val titleBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(52),1f)}
  titleBox.addView(TextView(this).apply{text="Music";textSize=28f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE)})
  titleBox.addView(TextView(this).apply{text="Âm nhạc cho tâm hồn";textSize=12f;setTextColor(Color.rgb(145,170,205))})
  top.addView(titleBox)
  top.addView(TextView(this).apply{text="⌕";textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setOnClickListener{search.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)}},LinearLayout.LayoutParams(dp(42),dp(48)))
  top.addView(TextView(this).apply{text="⚙";textSize=23f;setTextColor(Color.rgb(180,195,220));gravity=Gravity.CENTER;setOnClickListener{showSettings()}},LinearLayout.LayoutParams(dp(42),dp(48)))
  top.addView(TextView(this).apply{text="👑";textSize=23f;setTextColor(Color.rgb(255,215,80));gravity=Gravity.CENTER;setOnClickListener{showOwnerLogin()}},LinearLayout.LayoutParams(dp(42),dp(48)))
  root.addView(top)
  search=EditText(this).apply{hint="🔎  Tìm bài hát";setSingleLine();textSize=14f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(120,140,175));setPadding(dp(15),0,dp(15),0);background=GradientDrawable().apply{setColor(Color.rgb(13,22,46));cornerRadius=dp(22).toFloat();setStroke(dp(1),Color.rgb(35,65,110))}}
  root.addView(search,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(6);bottomMargin=dp(10)})
  val hero=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   gravity=Gravity.CENTER
   setPadding(dp(16),dp(12),dp(16),dp(12))
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(7,35,75),Color.rgb(28,12,60),Color.rgb(8,18,42))).apply{
    cornerRadius=dp(25).toFloat()
    setStroke(dp(1),Color.rgb(47,102,175))
   }
  }
  hero.addView(TextView(this).apply{text="✦  ·  ✧   ☾   ✧  ·  ✦";textSize=12f;setTextColor(Color.rgb(100,180,255));gravity=Gravity.CENTER})
  hero.addView(TextView(this).apply{text="🌕";textSize=58f;gravity=Gravity.CENTER;setShadowLayer(dp(14).toFloat(),0f,0f,Color.rgb(80,160,255))})
  hero.addView(TextView(this).apply{text="Những bản nhạc đưa bạn đến bình yên";textSize=18f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER})
  hero.addView(TextView(this).apply{text="Chill • Ngủ ngon • Thư giãn";textSize=12f;setTextColor(Color.rgb(155,190,235));gravity=Gravity.CENTER;setPadding(0,dp(4),0,0)})
  root.addView(hero,LinearLayout.LayoutParams(-1,dp(164)).apply{bottomMargin=dp(10)})
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun action(label:String,icon:String,click:()->Unit)=TextView(this).apply{text="$icon\n$label";textSize=12f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(13,27,57),20);setPadding(0,dp(6),0,dp(4));setOnClickListener{click()}}
  actions.addView(action("Thêm nhạc","♫"){chooseAudio()},LinearLayout.LayoutParams(0,dp(62),1f))
  actions.addView(action("Yêu thích","♥"){favoritesOnly=!favoritesOnly;renderList(search.text.toString())},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Nhạc ngủ","☾"){showSleepTimer()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("YouTube","▶"){tracks.firstOrNull{it.youtubeUrl!=null}?.youtubeUrl?.let{openYoutube(it)}},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Bản đồ","🗺️"){openMap()},LinearLayout.LayoutParams(dp(92),dp(62)).apply{leftMargin=dp(7)})
  val aiAction=action("AI","🤖"){showAiAssistantGate()}
  val aiParams=LinearLayout.LayoutParams(dp(82),dp(62))
  aiParams.leftMargin=dp(7)
  actions.addView(aiAction,aiParams)
  actions.addView(action("Lịch sử","🕘"){showHistory()},LinearLayout.LayoutParams(dp(86),dp(62)).apply{leftMargin=dp(7)})
  actions.addView(action("Âm lượng","🔊"){showVolume()},LinearLayout.LayoutParams(dp(86),dp(62)).apply{leftMargin=dp(7)})
  actions.addView(action("Tiện ích","🧰"){showToolsPanel()},LinearLayout.LayoutParams(dp(86),dp(62)).apply{leftMargin=dp(7)})
  root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(actions)},LinearLayout.LayoutParams(-1,dp(70)))
  root.addView(TextView(this).apply{text="DANH SÁCH NỔI BẬT";textSize=13f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);setPadding(dp(4),dp(12),0,dp(6))})
  val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=rounded(Color.rgb(10,18,38),20)}
  card.addView(TextView(this).apply{text="🌙  Nhạc ngủ";textSize=15f;setTextColor(Color.WHITE)})
  card.addView(TextView(this).apply{text="Thư giãn - Ngủ ngon - Sống chậm";textSize=11f;setTextColor(Color.rgb(130,155,195));setPadding(0,dp(3),0,0)})
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
  val shuffleBtn=button("🔀");shuffleBtn.textSize=16f;controls.addView(shuffleBtn,LinearLayout.LayoutParams(dp(58),dp(46)).apply{leftMargin=dp(8)});shuffleBtn.setOnClickListener{shuffle=!shuffle;shuffleBtn.text=if(shuffle)"🔀✓" else "🔀";Toast.makeText(this,if(shuffle)"Đã bật phát ngẫu nhiên" else "Đã tắt phát ngẫu nhiên",Toast.LENGTH_SHORT).show()}
  val repeatBtn=button("🔁");repeatBtn.textSize=16f;controls.addView(repeatBtn,LinearLayout.LayoutParams(dp(58),dp(46)).apply{leftMargin=dp(8)});repeatBtn.setOnClickListener{repeatOne=!repeatOne;repeatBtn.text=if(repeatOne)"🔂✓" else "🔁";Toast.makeText(this,if(repeatOne)"Đang lặp bài hiện tại" else "Đã tắt lặp bài",Toast.LENGTH_SHORT).show()}
  var lastNav=0L
  prev.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;previous()}}
  nextBtn.setOnClickListener{val now=android.os.SystemClock.uptimeMillis();if(now-lastNav>180){lastNav=now;next()}}
  root.addView(controls)
  root.addView(TextView(this).apply{text="BÀI HÁT";textSize=12f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.rgb(120,150,195));setPadding(dp(4),dp(6),0,dp(6))})
  list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  root.addView(ScrollView(this).apply{isFillViewport=true;addView(list)},LinearLayout.LayoutParams(-1,0,1f))
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){renderList(s?.toString().orEmpty())};override fun afterTextChanged(e:android.text.Editable?) {}})
  setContentView(root)
 }
 private fun showToolsPanel(){
  val items=arrayOf("📊 Thống kê thư viện","📤 Chia sẻ danh sách bài hát","🔤 Sắp xếp tên A–Z","🎲 Trộn danh sách phát","💔 Bỏ yêu thích tất cả","📥 Xuất danh sách dạng TXT","🧹 Xóa nhạc đã thêm từ thiết bị","🔎 Tìm bài ngẫu nhiên","🌙 Hẹn giờ ngủ","🗣️ Kiểm tra giọng nói","📋 Sao chép tên bài hát")
  AlertDialog.Builder(this).setTitle("🧰 Tiện ích Music App").setItems(items){_,which->
   when(which){
    0->{
     val builtInCount=tracks.count{it.melody!=null}
     val deviceCount=tracks.count{it.uri!=null}
     val youtubeCount=tracks.count{it.youtubeUrl!=null}
     val favoriteCount=tracks.count{it.favorite}
     val historyCount=prefs.getString("play_history","").orEmpty().split("|").count{it.isNotBlank()}
     val msg="📚 Tổng số bài: ${tracks.size}\n\n🌙 Nhạc tích hợp: $builtInCount\n📱 Nhạc từ thiết bị: $deviceCount\n▶️ Liên kết YouTube: $youtubeCount\n♥ Bài yêu thích: $favoriteCount\n🕘 Lịch sử gần đây: $historyCount/20"
     AlertDialog.Builder(this).setTitle("📊 Thống kê thư viện").setMessage(msg).setPositiveButton("OK",null).show()
    }
    1->{
     val content=tracks.joinToString("\n"){t->(if(t.favorite)"♥ " else "")+t.title}
     val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Danh sách nhạc Music App");putExtra(Intent.EXTRA_TEXT,if(content.isBlank())"Thư viện nhạc đang trống." else "🎵 Danh sách nhạc Music App\n\n$content")}
     startActivity(Intent.createChooser(send,"Chia sẻ danh sách nhạc"))
    }
    2->{tracks.sortBy{it.title.lowercase(Locale.ROOT)};currentIndex=-1;renderList(search.text.toString());Toast.makeText(this,"Đã sắp xếp thư viện theo tên A–Z.",Toast.LENGTH_SHORT).show()}
    3->{shuffle=!shuffle;Toast.makeText(this,if(shuffle)"🎲 Đã bật phát ngẫu nhiên." else "🎲 Đã tắt phát ngẫu nhiên.",Toast.LENGTH_SHORT).show()}
    4->{
     AlertDialog.Builder(this).setTitle("Bỏ yêu thích tất cả?").setMessage("Thao tác này sẽ xóa dấu yêu thích của các bài hát, không xóa tệp nhạc.").setNegativeButton("Hủy",null).setPositiveButton("Bỏ yêu thích"){_,_->
      tracks.forEach{it.favorite=false;saveFavorite(it)}
      favoritesOnly=false;renderList(search.text.toString());Toast.makeText(this,"Đã cập nhật danh sách yêu thích.",Toast.LENGTH_SHORT).show()
     }.show()
    }
    5->{
     val body=tracks.joinToString("\\n"){(if(it.favorite)"♥ " else "")+it.title}
     val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"music-app-playlist.txt")}
     pendingExportText=if(body.isBlank())"Thư viện nhạc đang trống." else "Danh sách nhạc Music App\\n\\n$body"
     startActivityForResult(intent,102)
    }
    6->{
     val count=tracks.count{it.uri!=null}
     if(count==0)Toast.makeText(this,"Không có bài nhạc từ thiết bị để xóa khỏi thư viện.",Toast.LENGTH_SHORT).show()
     else AlertDialog.Builder(this).setTitle("Xóa nhạc khỏi thư viện?").setMessage("Sẽ gỡ $count bài đã thêm từ thiết bị khỏi danh sách trong app. Tệp gốc trên máy không bị xóa.").setNegativeButton("Hủy",null).setPositiveButton("Gỡ khỏi thư viện"){_,_->
      tracks.removeAll{it.uri!=null};currentIndex=-1;renderList(search.text.toString());Toast.makeText(this,"Đã gỡ nhạc khỏi thư viện.",Toast.LENGTH_SHORT).show()
     }.show()
    }
    7->{if(tracks.isEmpty())Toast.makeText(this,"Thư viện đang trống.",Toast.LENGTH_SHORT).show() else playTrack(tracks[java.util.Random().nextInt(tracks.size)])}
    8->showSleepTimerDialog()
    9->{
     if(tts==null)Toast.makeText(this,"Giọng nói chưa sẵn sàng trên thiết bị.",Toast.LENGTH_SHORT).show()
     else{tts?.language=Locale.getDefault();tts?.speak("Xin chào! Đây là kiểm tra giọng nói của Music App.",TextToSpeech.QUEUE_FLUSH,null,"music_voice_test")}
    }
    10->{
     val names=tracks.joinToString("\\n"){it.title}
     val clip=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
     clip.setPrimaryClip(android.content.ClipData.newPlainText("Tên bài hát",names))
     Toast.makeText(this,"Đã sao chép tên bài hát.",Toast.LENGTH_SHORT).show()
    }
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showHistory(){
  val history=prefs.getString("play_history","").orEmpty().split("|").filter{it.isNotBlank()}
  if(history.isEmpty()){
   AlertDialog.Builder(this).setTitle("🕘 Lịch sử nghe").setMessage("Bạn chưa phát bài nào trong lịch sử được lưu.").setPositiveButton("OK",null).show()
   return
  }
  AlertDialog.Builder(this).setTitle("🕘 Bài nghe gần đây").setItems(history.toTypedArray()){_,which->
   val t=tracks.firstOrNull{it.title==history[which]}
   if(t!=null)playTrack(t) else Toast.makeText(this,"Bài này không còn trong thư viện hiện tại.",Toast.LENGTH_SHORT).show()
  }.setNeutralButton("Xóa lịch sử"){_,_->prefs.edit().remove("play_history").apply();Toast.makeText(this,"Đã xóa lịch sử.",Toast.LENGTH_SHORT).show()}.setNegativeButton("Đóng",null).show()
 }
 private fun saveHistory(title:String){
  val old=prefs.getString("play_history","").orEmpty().split("|").filter{it.isNotBlank()&&it!=title}
  prefs.edit().putString("play_history",(listOf(title)+old).take(20).joinToString("|")).apply()
 }
 private fun showSleepTimerDialog(){
  val options=arrayOf("5 phút","10 phút","15 phút","30 phút","45 phút","60 phút","Hủy hẹn giờ")
  AlertDialog.Builder(this).setTitle("🌙 Hẹn giờ tắt nhạc").setItems(options){_,which->
   if(which==6){sleepTimer?.cancel();sleepTimer=null;Toast.makeText(this,"Đã hủy hẹn giờ.",Toast.LENGTH_SHORT).show()}
   else{
    val mins=intArrayOf(5,10,15,30,45,60)[which]
    sleepTimer?.cancel()
    sleepTimer=object:android.os.CountDownTimer(mins*60_000L,1000L){
     override fun onTick(ms:Long){}
     override fun onFinish(){stopCurrentPlayback();nowPlaying.text="🌙 Hẹn giờ kết thúc — nhạc đã dừng";sleepTimer=null}
    }.start()
    Toast.makeText(this,"Nhạc sẽ dừng sau $mins phút.",Toast.LENGTH_SHORT).show()
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showVolume(){
  val audio=getSystemService(AUDIO_SERVICE) as AudioManager
  val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  val current=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
  val seek=SeekBar(this).apply{this.max=max;progress=current}
  val label=TextView(this).apply{text="Âm lượng: $current / $max";setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(dp(8),dp(8),dp(8),dp(8))}
  seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean){audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);label.text="Âm lượng: $value / $max"}
   override fun onStartTrackingTouch(bar:SeekBar?){}
   override fun onStopTrackingTouch(bar:SeekBar?){}
  })
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(4));addView(label);addView(seek)}
  AlertDialog.Builder(this).setTitle("🔊 Điều chỉnh âm lượng").setView(panel).setPositiveButton("Xong",null).show()
 }
 private fun showDiagnostics(){
  val audio=getSystemService(AUDIO_SERVICE) as AudioManager
  val details="Music App — Kiểm tra nhanh\\n\\n"+
   "• Bài hát trong thư viện: ${tracks.size}\\n"+
   "• Bài yêu thích: ${tracks.count{it.favorite}}\\n"+
   "• Trạng thái phát: ${if(player!=null||audioTrack!=null)"Có thiết bị phát" else "Đang chờ / phát ngoài ứng dụng"}\\n"+
   "• Giọng nói: ${if(tts!=null)"Đã khởi tạo" else "Chưa sẵn sàng"}\\n"+
    "• Mạng: cần Internet để dùng AI/YouTube\\n\\n"+
   "Nếu ứng dụng tự thoát, hãy gửi ảnh lỗi hoặc log để xác định nguyên nhân."
  AlertDialog.Builder(this).setTitle("🛠️ Chẩn đoán ứng dụng").setMessage(details).setPositiveButton("OK",null).show()
 }
 private fun loadFavorites(){tracks.forEach{it.favorite=prefs.getBoolean("fav_"+it.title,false)}}
 private fun saveFavorite(t:Track){prefs.edit().putBoolean("fav_"+t.title,t.favorite).apply()}
 private fun showSleepTimer(){
  val choices=arrayOf("15 phút","30 phút","60 phút","90 phút","Tắt hẹn giờ")
  AlertDialog.Builder(this).setTitle("☾ Hẹn giờ ngủ").setItems(choices){_,which->
   sleepTimer?.cancel()
   if(which==4){Toast.makeText(this,"Đã tắt hẹn giờ ngủ",Toast.LENGTH_SHORT).show();return@setItems}
   val mins=intArrayOf(15,30,60,90)[which]
   sleepTimer=object:android.os.CountDownTimer(mins*60_000L,1000L){
    override fun onTick(ms:Long){nowPlaying.text="☾ Tắt nhạc sau "+((ms+59999)/60000)+" phút"}
    override fun onFinish(){stopCurrentPlayback();nowPlaying.text="☾ Đã tắt nhạc theo hẹn giờ";Toast.makeText(this@MainActivity,"☾ Đã tắt nhạc",Toast.LENGTH_SHORT).show()}
   }.start()
   Toast.makeText(this,"☾ Hẹn giờ "+mins+" phút",Toast.LENGTH_SHORT).show()
  }.setNegativeButton("Hủy",null).show()
 }
 private fun showSettings(){
  val items=arrayOf("🌍 Chọn quốc gia","🔐 Bảo mật","ℹ️ Thông tin ứng dụng","🛠️ Chẩn đoán ứng dụng","🕘 Lịch sử nghe")
  AlertDialog.Builder(this).setTitle("⚙ Cài đặt").setItems(items){_,which->
   when(which){
    0->showCountryPicker()
    1->AlertDialog.Builder(this).setTitle("🔐 Bảo mật").setMessage("AI Bảo Nam không lưu API key vào mã nguồn.\n\nMã chủ và mã AI chỉ là lớp mở khóa cục bộ.").setPositiveButton("OK",null).show()
    2->AlertDialog.Builder(this).setTitle("ℹ️ Music App").setMessage("Music App 1.2\n\n🎵 Nhạc tích hợp và nhạc từ thiết bị\n♥ Yêu thích, tìm kiếm, phát ngẫu nhiên và lặp bài\n☾ Hẹn giờ ngủ\n🕘 Lịch sử nghe\n🔊 Điều chỉnh âm lượng\n🤖 AI Bảo Nam và đọc câu trả lời\n🌍 Tùy chọn quốc gia\n🛠️ Chẩn đoán ứng dụng").setPositiveButton("OK",null).show()
    3->showDiagnostics()
    4->showHistory()
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showCountryPicker(){
  val countries=arrayOf("🇻🇳 Việt Nam","🇺🇸 Hoa Kỳ","🇬🇧 Vương quốc Anh","🇯🇵 Nhật Bản","🇰🇷 Hàn Quốc","🇨🇳 Trung Quốc","🇹🇭 Thái Lan","🇫🇷 Pháp","🇩🇪 Đức","🇪🇸 Tây Ban Nha","🇮🇹 Ý","🇷🇺 Nga","🇮🇩 Indonesia","🇲🇾 Malaysia","🇵🇭 Philippines","🇮🇳 Ấn Độ","🇧🇷 Brazil","🇵🇹 Bồ Đào Nha","🇹🇷 Thổ Nhĩ Kỳ","🇸🇦 Ả Rập Xê Út")
  val selected=prefs.getInt("country_index",0)
  AlertDialog.Builder(this).setTitle("🌍 Chọn quốc gia").setSingleChoiceItems(countries,selected){dialog,which->
   prefs.edit().putInt("country_index",which).apply()
   Toast.makeText(this,"✅ Đã chọn "+countries[which],Toast.LENGTH_SHORT).show()
   dialog.dismiss()
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showWelcomeIfNeeded(){
  val prefs=getSharedPreferences("music_app",MODE_PRIVATE)
  if(prefs.getBoolean("welcome_seen",false))return
  AlertDialog.Builder(this)
   .setTitle("🌙 Chào mừng bạn đến với Music")
   .setMessage("Đây là ứng dụng nghe nhạc chill, ngủ ngon và thư giãn.\\n\\n• Chạm vào bài hát để phát\\n• ♡ để thêm vào yêu thích\\n• ♫ Thêm nhạc để chọn nhạc từ thiết bị\\n• ▶ YouTube để mở video\\n• 🗺️ Bản đồ để mở bản đồ và tìm địa điểm\\n\\nChúc bạn nghe nhạc thật vui! ✨")
   .setPositiveButton("Bắt đầu"){_,_->prefs.edit().putBoolean("welcome_seen",true).apply()}
   .setCancelable(false)
   .show()
 }
 private fun showOwnerLogin(){
  if(isOwner){Toast.makeText(this,"👑 Chủ app: Bảo Nam",Toast.LENGTH_SHORT).show();return}
  val input=EditText(this).apply{hint="Nhập mã chủ app";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)}
  AlertDialog.Builder(this)
   .setTitle("👑 Xác nhận chủ app")
   .setMessage("Nhập đúng mã để trở thành chủ app.")
   .setView(input)
   .setNegativeButton("Hủy",null)
   .setPositiveButton("Xác nhận"){_,_->
    if(sha256(input.text.toString())==ownerCodeHash){
     isOwner=true
     Toast.makeText(this,"👑 Bảo Nam đã trở thành chủ app!",Toast.LENGTH_LONG).show()
     showOwnerPanel()
    }else Toast.makeText(this,"❌ Sai mã chủ app!",Toast.LENGTH_SHORT).show()
   }.show()
 }
 private fun showOwnerPanel(){
  AlertDialog.Builder(this)
   .setTitle("👑 Chủ app: Bảo Nam")
   .setMessage("Bạn đã xác nhận là chủ app.\n\nTên chủ app: Bảo Nam\nTrạng thái: Đã xác nhận")
   .setPositiveButton("OK",null).show()
 }
 private fun sha256(value:String):String=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
 private fun speakAi(text:String){val engine=tts;if(engine==null){Toast.makeText(this,"⚠️ Giọng nói AI chưa sẵn sàng.",Toast.LENGTH_SHORT).show();return};val vi=Locale("vi","VN");val result=engine.setLanguage(vi);if(result==TextToSpeech.LANG_NOT_SUPPORTED||result==TextToSpeech.LANG_MISSING_DATA){Toast.makeText(this,"⚠️ Chưa có giọng tiếng Việt trên máy.",Toast.LENGTH_LONG).show();return};engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"music_ai_answer")}
 private fun showAiAssistantGate(){
  if(aiUnlocked){showAiTutor();return}
  val input=EditText(this).apply{hint="Nhập mã trợ lý";setSingleLine();inputType=android.text.InputType.TYPE_CLASS_NUMBER;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)}
  AlertDialog.Builder(this)
   .setTitle("🔐 Mở trợ lý AI")
   .setMessage("Nhập mã để sử dụng trợ lý AI.")
   .setView(input)
   .setNegativeButton("Hủy",null)
   .setPositiveButton("Xác nhận"){_,_->
    if(sha256(input.text.toString())==aiCodeHash){aiUnlocked=true;Toast.makeText(this,"✅ Đã mở trợ lý AI!",Toast.LENGTH_SHORT).show();showAiTutor()}
    else Toast.makeText(this,"❌ Sai mã trợ lý!",Toast.LENGTH_SHORT).show()
   }.show()
 }
 private fun showAiTutor(){
  if(openAiApiKey.isBlank()){showApiKeyDialog();return}
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(2),dp(4),dp(2))}
  val chat=TextView(this).apply{text="🤖 AI Bảo Nam\n\nXin chào! Mình đang kết nối với OpenAI. Hãy nhập câu hỏi bên dưới.\n";textSize=14f;setTextColor(Color.WHITE);setPadding(dp(10),dp(10),dp(10),dp(10));background=rounded(Color.rgb(10,18,38),16)}
  val scroll=ScrollView(this).apply{addView(chat)};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val input=EditText(this).apply{hint="Nhắn tin với ChatGPT...";setSingleLine(false);maxLines=4;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);background=rounded(Color.rgb(16,27,52),16);setPadding(dp(12),dp(8),dp(12),dp(8))}
  val send=button("Gửi").apply{background=rounded(Color.rgb(36,118,225),18)}
  val speak=button("🔊 Nói").apply{background=rounded(Color.rgb(24,75,110),18)}
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(input,LinearLayout.LayoutParams(0,dp(58),1f));row.addView(send,LinearLayout.LayoutParams(dp(70),dp(58)).apply{leftMargin=dp(7)});row.addView(speak,LinearLayout.LayoutParams(dp(75),dp(58)).apply{leftMargin=dp(7)})
  panel.addView(row,LinearLayout.LayoutParams(-1,dp(64)).apply{topMargin=dp(7)})
  val dialog=AlertDialog.Builder(this).setTitle("🤖 AI Bảo Nam").setView(panel).setNegativeButton("Đóng",null).create()
  val history=JSONArray()
  var lastAnswer="Xin chào! Bạn hãy nhập câu hỏi."
  fun append(role:String,text:String){chat.append((if(role=="user")"\n👤 Bạn: " else "\n🤖 AI Bảo Nam: ")+text+"\n");scroll.post{scroll.fullScroll(ScrollView.FOCUS_DOWN)}}
  fun ask(){
   val q=input.text.toString().trim();if(q.isEmpty())return
   input.setText("");append("user",q);send.isEnabled=false;send.text="…"
   history.put(JSONObject().put("role","user").put("content",q))
   Thread{
    val answer=callOpenAi(history)
    runOnUiThread{
     if(answer.startsWith("⚠️")){append("assistant",answer);history.remove(history.length()-1)}
     else{history.put(JSONObject().put("role","assistant").put("content",answer));lastAnswer=answer;append("assistant",answer);speakAi(answer)}
     send.isEnabled=true;send.text="Gửi"
    }
   }.start()
  }
  send.setOnClickListener{ask()}
  speak.setOnClickListener{if(lastAnswer.isNotBlank())speakAi(lastAnswer)}
  input.setOnEditorActionListener{_,_,_->ask();true}
  dialog.setOnDismissListener{tts?.stop()}
  dialog.show()
 }
 private fun showApiKeyDialog(){
  val input=EditText(this).apply{
   hint="sk-..."
   setSingleLine(true)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
   setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)
  }
  AlertDialog.Builder(this)
   .setTitle("🔑 Kết nối AI Bảo Nam")
   .setMessage("Nhập OpenAI API key do bạn hoặc phụ huynh quản lý tạo. Key chỉ giữ trong bộ nhớ của app ở phiên hiện tại, không ghi vào mã nguồn. Không gửi key cho người khác. API có thể tính phí; hãy nhờ phụ huynh quản lý tài khoản và chi phí.")
   .setView(input)
   .setNegativeButton("Hủy",null)
   .setPositiveButton("Kết nối"){_,_->
    val key=input.text.toString().trim()
    if(key.isBlank()){Toast.makeText(this,"⚠️ Chưa nhập API key.",Toast.LENGTH_SHORT).show()}
    else{openAiApiKey=key;Toast.makeText(this,"Đã lưu key trong bộ nhớ phiên này. Hãy gửi câu hỏi để kiểm tra kết nối.",Toast.LENGTH_LONG).show();showAiTutor()}
   }.show()
 }
 private fun callOpenAi(history:JSONArray):String{
  var conn:HttpURLConnection?=null
  return try{
   val body=JSONObject().put("model",openAiModel).put("instructions","Bạn là AI Bảo Nam, trợ lý học tập thân thiện. Trả lời rõ ràng bằng tiếng Việt, giải thích bài tập từng bước, không bịa khi không chắc và nhắc người dùng không chia sẻ thông tin riêng tư.").put("max_output_tokens",1200).put("input",history)
   conn=(URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply{
    requestMethod="POST";connectTimeout=20000;readTimeout=60000;doOutput=true
    setRequestProperty("Authorization","Bearer "+openAiApiKey)
    setRequestProperty("Content-Type","application/json")
   }
   conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
   val code=conn.responseCode
   val stream=if(code in 200..299)conn.inputStream else conn.errorStream
   val raw=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
    if(code !in 200..299){
     val msg=try{JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()}catch(_:Exception){""}
     val hint=when(code){
      401,403->"API key không hợp lệ hoặc tài khoản chưa có quyền."
      404->"Không tìm thấy model hoặc tài khoản chưa được cấp quyền dùng model."
      429->"Đã chạm giới hạn yêu cầu hoặc hạn mức API. Hãy kiểm tra quota/billing."
      else->"Yêu cầu tới OpenAI thất bại."
     }
     return "⚠️ $hint (HTTP $code)"+if(msg.isNotBlank()){"\\nChi tiết: $msg"}else{""}
    }
   val json=JSONObject(raw)
   val output=json.optJSONArray("output")?:return "⚠️ Không nhận được nội dung trả lời từ ChatGPT."
   val result=StringBuilder()
   for(i in 0 until output.length()){
    val item=output.optJSONObject(i)?:continue
    val content=item.optJSONArray("content")?:continue
    for(j in 0 until content.length()){
     val part=content.optJSONObject(j)?:continue
     if(part.optString("type")=="output_text"){result.append(part.optString("text"))}
    }
   }
   if(result.isBlank())"⚠️ ChatGPT trả về dữ liệu nhưng không có văn bản." else result.toString().trim()
  }catch(e:Exception){"⚠️ Không kết nối được ChatGPT: "+(e.message?:"lỗi mạng")}
  finally{conn?.disconnect()}
 }
 private fun openMap(){
  val uri=Uri.parse("geo:0,0?q=bản đồ")
  try{startActivity(Intent(Intent.ACTION_VIEW,uri))}
  catch(_:Exception){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps")))}catch(_:Exception){nowPlaying.text="Không mở được Bản đồ"}}
 }
 private fun chooseAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
 private fun chooseAudioForTrack(t:Track){pendingYoutubeAudio=t;Toast.makeText(this,"Chọn tệp âm thanh của bài này để phát trực tiếp trong Music App.",Toast.LENGTH_LONG).show();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";addCategory(Intent.CATEGORY_OPENABLE)},103)}
 private fun audioPreferenceKey(t:Track):String="youtube_audio_"+(Uri.parse(t.youtubeUrl).getQueryParameter("v")?:t.title.hashCode().toString())
 @Deprecated("Compatibility callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==102&&c==Activity.RESULT_OK&&d?.data!=null){try{contentResolver.openOutputStream(d.data!!)?.use{it.write(pendingExportText.toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Đã xuất danh sách nhạc.",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"Không thể xuất danh sách: "+(e.message?:"lỗi"),Toast.LENGTH_LONG).show()};return};if(r==103){val target=pendingYoutubeAudio;pendingYoutubeAudio=null;if(c==Activity.RESULT_OK&&d?.data!=null&&target!=null){val u=d.data!!;try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};target.uri=u;prefs.edit().putString(audioPreferenceKey(target),u.toString()).apply();renderList(search.text.toString());Toast.makeText(this,"Đã gắn âm thanh vào “"+target.title+"”. Bài sẽ phát trong app, không mở YouTube Music.",Toast.LENGTH_LONG).show();playTrack(target)};return};if(r!=100||c!=Activity.RESULT_OK||d==null)return;val us=mutableListOf<Uri>();d.data?.let{us.add(it)};d.clipData?.let{x->for(i in 0 until x.itemCount)us.add(x.getItemAt(i).uri)};us.distinct().forEach{u->if(tracks.none{it.uri==u}){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};tracks.add(Track(u,getFileName(u)))}};renderList(search.text.toString())}
 private fun getFileName(u:Uri):String{var n:String?=null;contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)};return n?:"Bài hát"}
 private fun renderList(qs:String){
  list.removeAllViews();val q=qs.trim().lowercase(Locale.getDefault());val visible=tracks.filter{(!favoritesOnly||it.favorite)&&it.title.lowercase(Locale.getDefault()).contains(q)}
  if(visible.isEmpty()){list.addView(TextView(this).apply{text="🌙\n\nChưa có bài phù hợp";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.rgb(125,145,180));setPadding(0,dp(28),0,dp(28))});return}
  visible.forEach{tr->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(7),dp(6));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(11,22,43),Color.rgb(17,17,36))).apply{cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(25,52,90))}}
   val icon=TextView(this).apply{text=when{tr.title.startsWith("🌙")->"🌕";tr.title.startsWith("☁️")->"☁";tr.streamUrl!=null->"🎤";tr.youtubeUrl!=null->"▶";else->"♫"};textSize=23f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(22,36,66),15)}
   row.addView(icon,LinearLayout.LayoutParams(dp(50),dp(50)).apply{rightMargin=dp(9)});icon.setOnClickListener{playTrack(tr)}
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(62),1f)}
   box.addView(TextView(this).apply{text=tr.title;textSize=14f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   box.addView(TextView(this).apply{text=when{tr.streamUrl!=null->"Nhạc tiếng Việt • Phát trực tuyến miễn phí";tr.youtubeUrl!=null&&tr.uri!=null->"Âm thanh từ thiết bị • Phát trong app";tr.youtubeUrl!=null->"Chọn tệp âm thanh để nghe trong app";tr.melody!=null->"Nhạc ngủ / Chill • Tích hợp";else->"Từ thiết bị"};textSize=10f;setTextColor(Color.rgb(110,145,190));setPadding(0,dp(3),0,0)})
   row.addView(box);box.setOnClickListener{playTrack(tr)};box.setOnLongClickListener{showTrackDetails(tr);true}
   val f=button(if(tr.favorite)"♥" else "♡");f.textSize=19f;f.background=rounded(Color.rgb(18,30,56),18);f.setOnClickListener{tr.favorite=!tr.favorite;saveFavorite(tr);renderList(search.text.toString())};row.addView(f,LinearLayout.LayoutParams(dp(46),dp(50)))
   list.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(7)})
  }
 }
 private fun stopCurrentPlayback(){
  generation.incrementAndGet();stopBuiltin=true;builtinPaused=false
  synchronized(audioLock){val a=audioTrack;audioTrack=null;if(a!=null){try{a.stop()}catch(_:Exception){};try{a.release()}catch(_:Exception){}}}
  val p=player;player=null;if(p!=null){try{p.setOnPreparedListener(null)}catch(_:Exception){};try{p.setOnCompletionListener(null)}catch(_:Exception){};try{p.setOnErrorListener(null)}catch(_:Exception){};try{p.stop()}catch(_:Exception){};try{p.release()}catch(_:Exception){}}
  if(!destroyed&&::playButton.isInitialized)playButton.text="▶"
 }
 private fun showTrackDetails(t:Track){
  val message=when{
   t.melody!=null -> "🌙 "+t.title+"\n\n• Không gian: nhạc nền nhẹ, thiên về thư giãn và đi vào trạng thái chậm.\n• Cấu trúc: giai điệu ngắn lặp lại với nhịp ổn định để tạo cảm giác quen thuộc.\n• Chi tiết đáng nghe: hãy để ý lúc nốt cao xuất hiện rồi trở về nốt thấp; sự lên xuống đó tạo cảm giác như một hơi thở.\n• Cách nghe sâu: giảm âm lượng vừa đủ nghe, nhắm mắt vài phút và tập trung vào khoảng lặng giữa các nốt.\n• Phù hợp: nghỉ ngơi, đọc sách hoặc chuẩn bị ngủ."
   t.streamUrl!=null && t.title.contains("Thanh niên") -> "🎤 "+t.title+"\n\n• Nguồn: Wikimedia Commons.\n• Giấy phép bản ghi: Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0).\n• Khi chia sẻ lại, cần ghi công và giữ điều kiện giấy phép tương thích.\n• Cần Internet để phát trực tuyến."
   t.streamUrl!=null -> "🎤 "+t.title+"\n\n• Nguồn: Wikimedia Commons.\n• Bản ghi được đánh dấu CC0.\n• Cần Internet để phát trực tuyến."
   t.youtubeUrl!=null -> "🎧 "+t.title+"\n\n• Nguồn: YouTube Music.\n• Vai trò của Music App: chỉ mở nguồn nghe bên ngoài, không tải hay nhúng bản thu.\n• Chi tiết đáng chú ý: khi nghe, thử tách riêng nhịp, bass, giai điệu và không gian phía sau thay vì chỉ nghe toàn bộ cùng lúc.\n• Gợi ý: dùng tai nghe ở âm lượng vừa và nghỉ tai nếu nghe lâu."
   t.uri!=null -> "🎵 "+t.title+"\n\n• Nguồn: tệp nhạc bạn đã chọn từ thiết bị.\n• Có thể phát trực tiếp trong thư viện và lưu trạng thái yêu thích.\n• Chi tiết đáng nghe: thử nhận ra 3 lớp — nhịp nền, giai điệu chính và âm thanh phụ.\n• Cách nghe sâu: chọn một đoạn ngắn, nghe lại và xem cảm xúc của bạn thay đổi thế nào theo nhịp và cao độ."
   else -> "🎵 "+t.title+"\n\nĐây là một bài trong thư viện Music. Hãy nghe ở âm lượng vừa và chú ý đến nhịp, giai điệu, khoảng lặng và cảm xúc mà bài tạo ra."
  }
  AlertDialog.Builder(this).setTitle("✦ Chi tiết sâu hơn").setMessage(message).setPositiveButton("▶ Nghe ngay"){_,_->playTrack(t)}.setNegativeButton("Đóng",null).show()
 }
 private fun openYoutube(url:String){
  val parsed=Uri.parse(url)
  val host=parsed.host?.lowercase(Locale.ROOT)
  val safeHost=host=="music.youtube.com"
  if(parsed.scheme!="https"||!safeHost){nowPlaying.text="Liên kết YouTube không an toàn";return}
  try{
   val appIntent=Intent(Intent.ACTION_VIEW,parsed).apply{setPackage("com.google.android.apps.youtube.music")}
   startActivity(appIntent)
   nowPlaying.text="🎧 Đang mở YouTube Music: "+(parsed.getQueryParameter("v")?:"bài hát")
  }catch(_:Exception){
   try{
    startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW,parsed),"Mở bằng"))
    nowPlaying.text="🎧 Đang mở YouTube Music"
   }catch(_:Exception){
    nowPlaying.text="Không tìm thấy ứng dụng mở YouTube"
   }
  }
 }
 private fun playTrack(t:Track){
  currentIndex=tracks.indexOf(t);saveHistory(t.title);stopCurrentPlayback()
  if(t.youtubeUrl!=null&&t.uri==null){chooseAudioForTrack(t);return}
  if(t.melody!=null){playBuiltin(t);return}
  val u=t.uri
   if(u==null&&t.streamUrl==null)return
   try{val mp=MediaPlayer();if(t.streamUrl!=null)mp.setDataSource(t.streamUrl) else mp.setDataSource(this,u!!);mp.setOnPreparedListener{if(player!==mp){try{mp.release()}catch(_:Exception){};return@setOnPreparedListener};mp.start();nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"};mp.setOnCompletionListener{if(player===mp){player=null;try{mp.release()}catch(_:Exception){};if(repeatOne)playTrack(t) else next()}};mp.setOnErrorListener{_,_,_->if(player===mp){player=null;nowPlaying.text="Không phát được tệp này";playButton.text="▶";try{mp.release()}catch(_:Exception){}};true};player=mp;mp.prepareAsync();nowPlaying.text="Đang tải: "+t.title}catch(_:Exception){player=null;playButton.text="▶"}
 }
 private fun playBuiltin(t:Track){
  val g=generation.get();stopBuiltin=false;builtinPaused=false;nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"
  Thread{
   val sr=22050;val mb=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
   if(g!=generation.get()||stopBuiltin)return@Thread
   val at=try{AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,mb.coerceAtLeast(4096),AudioTrack.MODE_STREAM)}catch(_:Exception){return@Thread}
   synchronized(audioLock){if(g!=generation.get()||stopBuiltin){try{at.release()}catch(_:Exception){};return@Thread};audioTrack=at}
   var completed=false
   try{at.play();repeat(3){for((freq,dur)in t.melody?:emptyList()){val count=sr*dur/1000;val s=ShortArray(count);var i=0;while(i<count){if(stopBuiltin||g!=generation.get())return@Thread;while(builtinPaused&&!stopBuiltin&&g==generation.get()){try{Thread.sleep(50)}catch(_:Exception){}};if(stopBuiltin||g!=generation.get())return@Thread;val x=i.toDouble()/sr;val fi=(i/(sr*.04)).coerceAtMost(1.0);val fo=((count-i)/(sr*.04)).coerceAtMost(1.0);s[i]=(sin(2*PI*freq*x)*.18*minOf(fi,fo)*Short.MAX_VALUE).toInt().toShort();i++};synchronized(audioLock){if(audioTrack!==at)return@Thread;try{at.write(s,0,s.size)}catch(_:Exception){return@Thread}}}};completed=true}catch(_:Exception){}finally{var owner=false;synchronized(audioLock){if(audioTrack===at){audioTrack=null;owner=true}};if(owner){try{at.stop()}catch(_:Exception){};try{at.release()}catch(_:Exception){}};runOnUiThread{if(g==generation.get()&&!stopBuiltin&&completed&&!destroyed&&!isFinishing){playButton.text="▶";if(repeatOne)playTrack(t) else next()}}}
  }.start()
 }
 private fun togglePlay(){
  synchronized(audioLock){audioTrack?.let{a->try{if(a.playState==AudioTrack.PLAYSTATE_PLAYING){builtinPaused=true;a.pause();playButton.text="▶"}else{builtinPaused=false;a.play();playButton.text="⏸"}}catch(_:Exception){playButton.text="▶"};return}}
  val p=player;if(p!=null){try{if(p.isPlaying){p.pause();playButton.text="▶"}else{p.start();playButton.text="⏸"}}catch(_:Exception){player=null;playButton.text="▶";nowPlaying.text="Không thể tiếp tục bài hát"}}
 }
 private fun previous(){if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<=0)tracks.lastIndex else currentIndex-1])}
 private fun next(){if(tracks.isNotEmpty()){val idx:Int;if(shuffle){val choices=tracks.indices.filter{it!=currentIndex};idx=if(choices.isNotEmpty())choices[java.util.Random().nextInt(choices.size)] else 0}else{idx=if(currentIndex<0||currentIndex>=tracks.lastIndex)0 else currentIndex+1};playTrack(tracks[idx])}}
 override fun onDestroy(){destroyed=true;sleepTimer?.cancel();sleepTimer=null;stopCurrentPlayback();tts?.stop();tts?.shutdown();tts=null;super.onDestroy()}
}