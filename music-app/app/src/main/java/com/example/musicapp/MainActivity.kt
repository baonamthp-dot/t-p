package com.example.musicapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
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
 private val tracks=mutableListOf<Track>();private var currentIndex=-1;private var activePlaylist:List<Track>?=null;private var playlistPosition=0;private var player:MediaPlayer?=null;@Volatile private var playerPreparing=false;private var audioTrack:AudioTrack?=null;private var equalizer:Equalizer?=null
 private val generation=AtomicInteger(0);private val audioLock=Any();@Volatile private var stopBuiltin=false
 @Volatile private var builtinPaused=false
 @Volatile private var destroyed=false
 private lateinit var list:LinearLayout;private lateinit var nowPlaying:TextView;private lateinit var coverIcon:TextView;private lateinit var playButton:Button;private lateinit var progressBar:SeekBar;private lateinit var elapsedLabel:TextView;private lateinit var durationLabel:TextView;private val progressHandler=android.os.Handler(android.os.Looper.getMainLooper());private lateinit var search:EditText;private var favoritesOnly=false
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
 private var activeAiInput:EditText?=null
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
  Track(null,"🎤 Thanh niên làm theo lời Bác",streamUrl="https://upload.wikimedia.org/wikipedia/commons/transcoded/d/db/Thanh_ni%C3%AAn_l%C3%A0m_theo_l%E1%BB%9Di_B%C3%A1c_%28c%C3%B3_l%E1%BB%9Di%29.ogg/Thanh_ni%C3%AAn_l%C3%A0m_theo_l%E1%BB%9Di_B%C3%A1c_%28c%C3%B3_l%E1%BB%9Di%29.ogg.mp3"),
  Track(null,"🎤 Tiến Quân Ca (bản ghi CC0)",streamUrl="https://upload.wikimedia.org/wikipedia/commons/a/ad/TienQuanCa_PAVN_records.ogg")
 )
 private fun melody(vararg n:Double)=n.map{it to 650}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun rounded(c:Int,r:Int=18)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
 private fun button(t:String)=Button(this).apply{text=t;isAllCaps=false;textSize=14f;setTextColor(Color.WHITE);background=rounded(Color.rgb(38,44,58),16)}
 override fun onCreate(b:Bundle?){super.onCreate(b);progressHandler.post(progressUpdate);tracks.addAll(builtIn.map{it.copy()});loadSavedYoutubeTracks();tracks.filter{it.youtubeUrl!=null}.forEach{t->prefs.getString(audioPreferenceKey(t),null)?.let{saved->t.uri=Uri.parse(saved)}};loadFavorites();tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val vi=Locale("vi","VN");val result=tts?.setLanguage(vi)?:TextToSpeech.LANG_NOT_SUPPORTED;if(result==TextToSpeech.LANG_NOT_SUPPORTED||result==TextToSpeech.LANG_MISSING_DATA){runOnUiThread{Toast.makeText(this,"⚠️ Máy chưa có dữ liệu giọng nói tiếng Việt. Hãy cài Google Text-to-Speech.",Toast.LENGTH_LONG).show()}};tts?.setSpeechRate(0.95f)}else{runOnUiThread{Toast.makeText(this,"⚠️ Không khởi tạo được giọng nói.",Toast.LENGTH_LONG).show()}}};buildUi();renderList("");showWelcomeIfNeeded()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(10));setBackgroundResource(com.example.musicapp.R.drawable.moon_background)}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  top.addView(TextView(this@MainActivity).apply{text="♫";textSize=28f;setTextColor(Color.rgb(90,190,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
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
  actions.addView(action("YouTube","▶"){addYoutubeMusic()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("YouTube Mix","♫"){showYoutubeMix()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Yêu thích","♥"){favoritesOnly=!favoritesOnly;renderList(search.text.toString())},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Hẹn giờ","☾"){showSleepTimerDialog()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Tiện ích","⚙"){showToolsPanel()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("AI Music","🤖"){showAppAssistant()},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(actions)},LinearLayout.LayoutParams(-1,dp(70)))
  root.addView(TextView(this).apply{text="DANH SÁCH NỔI BẬT";textSize=13f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);setPadding(dp(4),dp(12),0,dp(6))})
  val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=rounded(Color.rgb(10,18,38),20)}
  card.addView(TextView(this).apply{text="🌙  Nhạc ngủ";textSize=15f;setTextColor(Color.WHITE)})
  card.addView(TextView(this).apply{text="Thư giãn - Ngủ ngon - Sống chậm";textSize=11f;setTextColor(Color.rgb(130,155,195));setPadding(0,dp(3),0,0)})
  root.addView(card,LinearLayout.LayoutParams(-1,dp(64)).apply{bottomMargin=dp(8)})
  val nowCard=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(8),dp(8));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(17,31,63),Color.rgb(31,18,58))).apply{cornerRadius=dp(20).toFloat();setStroke(dp(1),Color.rgb(45,83,145))}}
  coverIcon=TextView(this).apply{text="🌕";textSize=34f;gravity=Gravity.CENTER;background=rounded(Color.rgb(23,37,67),17)};nowCard.addView(coverIcon,LinearLayout.LayoutParams(dp(58),dp(58)).apply{rightMargin=dp(10)})
  val np=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(60),1f)}
  nowPlaying=TextView(this).apply{text="Chưa phát bài nào";textSize=15f;setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};np.addView(nowPlaying)
  np.addView(TextView(this).apply{text="ĐANG CHỜ PHÁT";textSize=9f;setTextColor(Color.rgb(100,185,255));setPadding(0,dp(3),0,0)});nowCard.addView(np)
  playButton=button("▶").apply{textSize=21f;background=rounded(Color.rgb(36,118,225),28);setOnClickListener{togglePlay()}};nowCard.addView(playButton,LinearLayout.LayoutParams(dp(54),dp(54)))
  root.addView(nowCard,LinearLayout.LayoutParams(-1,dp(76)).apply{bottomMargin=dp(8)})
  val progressRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(6),0,dp(6),dp(2))}
  val times=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  elapsedLabel=TextView(this).apply{text="00:00";textSize=10f;setTextColor(Color.rgb(145,180,225))}
  durationLabel=TextView(this).apply{text="00:00";textSize=10f;setTextColor(Color.rgb(145,180,225))}
  times.addView(elapsedLabel,LinearLayout.LayoutParams(0,dp(18),1f))
  times.addView(durationLabel)
  progressRow.addView(times)
  progressBar=SeekBar(this).apply{max=1000;progress=0;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean){}
   override fun onStartTrackingTouch(bar:SeekBar?){}
   override fun onStopTrackingTouch(bar:SeekBar?){
    val mp=player
    if(mp!=null)try{val dur=mp.duration;if(dur>0)mp.seekTo((dur.toLong()*bar!!.progress/1000L).toInt())}catch(_:Exception){}
   }
  })}
  progressRow.addView(progressBar,LinearLayout.LayoutParams(-1,dp(28)))
  root.addView(progressRow,LinearLayout.LayoutParams(-1,dp(46)))
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
 private fun showAppAssistant(){
  if(destroyed||isFinishing)return
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(4))}
  val messages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4))}
  val scroll=ScrollView(this).apply{isFillViewport=false;addView(messages)}
  fun addBubble(who:String,text:String){
   val bubble=TextView(this).apply{
    this.text=(if(who=="Bạn")"🧑 Bạn\\n" else "🤖 AI Music\\n")+text
    textSize=14f;setTextColor(Color.WHITE);setPadding(dp(12),dp(9),dp(12),dp(9))
    background=rounded(if(who=="Bạn")Color.rgb(28,67,120) else Color.rgb(25,34,55),14)
   }
   messages.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
   scroll.post{scroll.fullScroll(ScrollView.FOCUS_DOWN)}
  }
  addBubble("AI","Chào bạn! Mình là AI Music 🎵. Bạn có thể ra lệnh bằng chữ như “phát bài tiếp theo”, “tạm dừng”, “tìm nhạc ngủ”, “bật phát ngẫu nhiên”, “mở cài đặt” hoặc “hẹn giờ tắt nhạc”. Mình chỉ điều khiển các chức năng có sẵn trong Music App.")
  val input=EditText(this).apply{hint="Nhập lệnh hoặc hỏi AI Music…";setSingleLine(false);maxLines=3;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND;textSize=14f;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);background=rounded(Color.rgb(16,25,45),12);setPadding(dp(10),dp(8),dp(10),dp(8))}
  val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val quickScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(quick)}
  listOf("▶ Bài tiếp","⏸ Tạm dừng","🔀 Ngẫu nhiên","🌙 Hẹn giờ").forEach{label->
   val chip=button(label).apply{textSize=11f;background=rounded(Color.rgb(24,42,70),20)}
   quick.addView(chip,LinearLayout.LayoutParams(-2,dp(38)).apply{rightMargin=dp(7)})
   chip.setOnClickListener{
    val command=when(label){"▶ Bài tiếp"->"phát bài tiếp theo";"⏸ Tạm dừng"->"tạm dừng nhạc";"🔀 Ngẫu nhiên"->"bật phát ngẫu nhiên";else->"hẹn giờ tắt nhạc"}
    addBubble("Bạn",command);addBubble("AI",appAiHandleCommand(command)?:appHelpReply(command))
   }
  }
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  row.addView(input,LinearLayout.LayoutParams(0,dp(52),1f))
  val mic=button("🎙").apply{textSize=17f;background=rounded(Color.rgb(28,45,70),24);setOnClickListener{
   activeAiInput=input
   try{startActivityForResult(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"vi-VN");putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Nói lệnh cho AI Music")},104)}
   catch(_:Exception){Toast.makeText(this@MainActivity,"Thiết bị chưa hỗ trợ nhập giọng nói.",Toast.LENGTH_SHORT).show()}
  }}
  row.addView(mic,LinearLayout.LayoutParams(dp(48),dp(48)).apply{rightMargin=dp(5)})
  val send=button("Gửi")
  row.addView(send,LinearLayout.LayoutParams(dp(64),dp(48)))
  panel.addView(scroll,LinearLayout.LayoutParams(-1,dp(270)))
  panel.addView(quickScroll,LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(5)})
  panel.addView(row,LinearLayout.LayoutParams(-1,dp(58)).apply{topMargin=dp(4)})
  activeAiInput=input
  val dialog=AlertDialog.Builder(this).setTitle("🤖 AI Music — Điều khiển ứng dụng").setView(panel).setPositiveButton("Đóng",null).create()
  val sendMessage={
   val question=input.text.toString().trim()
   if(question.isNotBlank()){
    addBubble("Bạn",question)
    addBubble("AI",appAiHandleCommand(question) ?: appHelpReply(question))
    input.setText("")
   }
  }
  send.setOnClickListener{sendMessage()}
  input.setOnEditorActionListener{_,actionId,_->
   if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendMessage();true}else false
  }
  dialog.setOnDismissListener{activeAiInput=null}
  dialog.show()
 }
 private fun appAiHandleCommand(question:String):String?{
  val q=question.lowercase(Locale.ROOT).trim()
  fun has(vararg words:String)=words.any{q.contains(it)}
  if(has("phát bài tiếp theo","bài tiếp theo","bài kế tiếp","chuyển bài tiếp","next")){next();return "Đã yêu cầu phát bài tiếp theo. 🎵"}
  if(has("bài trước","phát bài trước","quay lại bài","previous")){previous();return "Đã yêu cầu phát bài trước. 🎵"}
  if(has("tạm dừng","dừng nhạc","pause")){
   val playing=try{player?.isPlaying==true}catch(_:Exception){false} || try{audioTrack?.playState==AudioTrack.PLAYSTATE_PLAYING}catch(_:Exception){false}
   if(playing){togglePlay();return "Đã tạm dừng phát nhạc. ⏸"}
   return "Hiện tại mình không thấy bài nào đang phát. Bạn có thể ra lệnh “phát bài tiếp theo” hoặc chọn một bài trong danh sách."
  }
  if(has("tiếp tục","phát tiếp","resume","bật nhạc lại")){
   val paused=(player!=null&&!playerPreparing)||(audioTrack!=null)
   val playing=try{player?.isPlaying==true}catch(_:Exception){false} || try{audioTrack?.playState==AudioTrack.PLAYSTATE_PLAYING}catch(_:Exception){false}
   if(paused&&!playing){togglePlay();return "Đã yêu cầu tiếp tục phát nhạc. ▶"}
   return "Chưa có bài đang tạm dừng. Hãy chọn một bài hoặc ra lệnh “phát bài tiếp theo”."
  }
  if(has("bật ngẫu nhiên","bật phát ngẫu nhiên","bật shuffle")){shuffle=true;return "Đã bật phát ngẫu nhiên. 🔀"}
  if(has("tắt ngẫu nhiên","tắt phát ngẫu nhiên","tắt shuffle")){shuffle=false;return "Đã tắt phát ngẫu nhiên."}
  if(has("bật lặp bài","bật lặp","bật repeat")){repeatOne=true;return "Đã bật lặp bài hiện tại. 🔁"}
  if(has("tắt lặp bài","tắt lặp","tắt repeat")){repeatOne=false;return "Đã tắt lặp bài hiện tại."}
  if(has("mở cài đặt","vào cài đặt","mở settings")){showSettings();return "Đã mở Cài đặt của Music App. ⚙️"}
  if(has("mở tiện ích","mở bảng tiện ích","vào tiện ích")){showToolsPanel();return "Đã mở bảng Tiện ích. ⚙️"}
  if(has("mở bản đồ","đi đến bản đồ","open map")){openMap();return "Đã yêu cầu mở bản đồ trên thiết bị. 🗺️"}
  if(has("hiện yêu thích","lọc yêu thích","chỉ bài yêu thích","mở yêu thích")){favoritesOnly=true;renderList(search.text.toString());return "Đã lọc thư viện để chỉ hiện bài yêu thích. ♥"}
  if(has("hiện tất cả bài","bỏ lọc yêu thích","tắt lọc yêu thích")){favoritesOnly=false;renderList(search.text.toString());return "Đã hiển thị lại tất cả bài hát phù hợp với từ khóa tìm kiếm."}
  if(has("phát bài yêu thích","phát nhạc yêu thích","nghe bài yêu thích")){val target=tracks.firstOrNull{it.favorite};if(target!=null){playTrack(target);return "Đang yêu cầu phát bài yêu thích: “${target.title}”. 🎵"};return "Bạn chưa có bài hát nào được đánh dấu yêu thích."}
  if(has("hẹn giờ","hẹn giờ tắt","tắt nhạc sau","timer")){showSleepTimerDialog();return "Đã mở bảng hẹn giờ. Chọn thời gian trong bảng để đặt giờ tắt nhạc. 🌙"}
  if(has("điều chỉnh âm lượng","mở âm lượng","chỉnh âm lượng")){showVolume();return "Đã mở bảng điều chỉnh âm lượng. 🔊"}
  if(has("xem lịch sử","mở lịch sử","lịch sử nghe")){showHistory();return "Đã mở lịch sử nghe."}
  if(has("chẩn đoán","kiểm tra ứng dụng","kiểm tra lỗi")){showDiagnostics();return "Đã mở phần chẩn đoán ứng dụng."}
  if(has("phát bài ","mở bài ","nghe bài ","phát nhạc ","nghe ","mở ")){
   val cleaned=question.trim().replace(Regex("(?i)^(hãy\\s+)?(phát|mở|nghe)(\\s+nhạc)?(\\s+bài)?\\s*"),"").trim().trim('"','“','”','\'')
   val target=tracks.firstOrNull{it.title.equals(cleaned,true)}?:tracks.firstOrNull{it.title.contains(cleaned,true)&&cleaned.length>=2}?:tracks.firstOrNull{cleaned.length>=2&&cleaned.contains(it.title,true)}
   if(target!=null){playTrack(target);return "Đã tìm thấy “${target.title}” và gửi lệnh phát trong Music App. 🎵"}
   return "Mình chưa tìm thấy bài “$cleaned” trong thư viện. Bạn thử nhập một phần tên bài hát hoặc thêm nhạc trước nhé."
  }
  if(has("tìm nhạc ","tìm bài ","tìm kiếm ")){
   val term=question.trim().replace(Regex("(?i)^(hãy\\s+)?(tìm nhạc|tìm bài|tìm kiếm)\\s*"),"").trim()
   if(term.isNotBlank()){search.setText(term);return "Đã lọc danh sách theo từ khóa “$term”. 🔎"}
  }
  if(has("thích bài ","yêu thích bài ","đánh dấu yêu thích ")){
   val term=question.trim().replace(Regex("(?i)^(hãy\\s+)?(thích bài|yêu thích bài|đánh dấu yêu thích)\\s*"),"").trim().trim('"','“','”','\'')
   val target=tracks.firstOrNull{it.title.equals(term,true)}?:tracks.firstOrNull{term.length>=2&&it.title.contains(term,true)}
   if(target!=null){target.favorite=!target.favorite;saveFavorite(target);renderList(search.text.toString());return (if(target.favorite)"Đã thêm" else "Đã bỏ")+ " “${target.title}” "+(if(target.favorite)"vào" else "khỏi")+" danh sách yêu thích. ♥"}
   return "Mình chưa tìm thấy bài phù hợp để đổi trạng thái yêu thích."
  }
  if(has("bỏ lọc","hiện tất cả bài","xóa tìm kiếm")){search.setText("");favoritesOnly=false;renderList("");return "Đã xóa từ khóa tìm kiếm và hiển thị lại thư viện. 🔎"}
  return null
 }
 private fun appHelpReply(question:String):String{
  val q=question.lowercase(Locale.ROOT)
  return when{
   listOf("phát nhạc","mở bài","nghe nhạc","play","bài hát").any{q.contains(it)} ->
    "Để phát nhạc, chạm vào tên bài trong danh sách. Dùng ▶/⏸ để phát hoặc tạm dừng, ⏮ và ⏭ để chuyển bài. Nhạc trực tuyến cần Internet."
   listOf("thêm nhạc","nhập nhạc","nhạc của tôi","tệp nhạc","file nhạc").any{q.contains(it)} ->
    "Ở màn hình chính, chạm “♫ Thêm nhạc”, chọn tệp âm thanh trên thiết bị rồi xác nhận. Chỉ chọn tệp bạn có quyền sử dụng."
   listOf("yêu thích","trái tim","♥","fav").any{q.contains(it)} ->
    "Chạm biểu tượng ♥ cạnh bài hát để thêm hoặc bỏ yêu thích. Nút “Yêu thích” trên màn hình chính giúp lọc danh sách chỉ còn các bài đã đánh dấu."
   listOf("hẹn giờ","tắt nhạc","ngủ","timer").any{q.contains(it)} ->
    "Chạm “Hẹn giờ” ở màn hình chính, sau đó chọn khoảng thời gian như 15, 30, 60 hoặc 90 phút. Chọn “Tắt hẹn giờ” nếu muốn hủy."
   listOf("tìm","tìm kiếm","search").any{q.contains(it)} ->
    "Nhập tên bài hát vào ô “Tìm bài hát” phía trên danh sách. Xóa nội dung tìm kiếm để xem lại các bài."
   listOf("danh sách phát","playlist").any{q.contains(it)} ->
    "Mở “Tiện ích” → “🎵 Danh sách phát riêng”. Chọn tạo mới, đặt tên, đánh dấu các bài muốn thêm rồi lưu."
   listOf("quốc gia","ngôn ngữ","country").any{q.contains(it)} ->
    "Mở biểu tượng ⚙ → “🌍 Chọn quốc gia”, rồi chạm vào quốc gia bạn muốn. Tùy chọn này chưa chắc dịch toàn bộ mọi nội dung trong ứng dụng."
   listOf("cài đặt","bảo mật","riêng tư","settings").any{q.contains(it)} ->
    "Chạm biểu tượng ⚙ ở góc trên màn hình để mở Cài đặt. Tại đó có lựa chọn quốc gia, thông tin ứng dụng, bảo mật, chẩn đoán và lịch sử nghe."
   listOf("lỗi","không phát","không nghe","không chạy","mất tiếng","không mở","đơ","crash").any{q.contains(it)} ->
    "Bạn thử kiểm tra âm lượng thiết bị và Internet nếu đang nghe nhạc trực tuyến; chọn bài khác để thử. Mở “⚙ → Chẩn đoán ứng dụng” để xem thông tin cơ bản. Nếu vẫn lỗi, hãy gửi ảnh thông báo lỗi."
   listOf("âm lượng","to nhỏ","volume").any{q.contains(it)} ->
    "Mở “Tiện ích” → “🔊 Âm lượng” để điều chỉnh âm lượng phát nhạc của thiết bị."
   listOf("lịch sử","nghe gần đây").any{q.contains(it)} ->
    "Mở “Tiện ích” → “🕘 Lịch sử nghe” để xem bài nghe gần đây. Trong cửa sổ lịch sử, bạn cũng có thể xóa dữ liệu lịch sử."
   listOf("eq","equalizer","chỉnh âm","âm thanh").any{q.contains(it)} ->
    "Mở “Tiện ích” → “🎚️ Bộ chỉnh âm (EQ)”. Tính năng này phụ thuộc vào khả năng hỗ trợ hiệu ứng âm thanh của thiết bị."
   listOf("ngẫu nhiên","shuffle","lặp bài","repeat","chuyển bài").any{q.contains(it)} ->
    "Dùng nút 🔀 để bật/tắt phát ngẫu nhiên và 🔁 để bật/tắt lặp bài. ⏮ và ⏭ dùng để chuyển bài trước hoặc tiếp theo."
   listOf("bản đồ","map").any{q.contains(it)} ->
    "Nếu phiên bản của bạn có mục Bản đồ, hãy mở mục đó từ phần tiện ích để xem bản đồ. Bản đồ cần kết nối Internet."
   listOf("ai là bạn","bạn là ai","làm được gì","chức năng").any{q.contains(it)} ->
    "Mình là trợ lý hướng dẫn sử dụng Music App, không phải trợ lý đa năng. Mình có thể giúp về phát nhạc, thêm nhạc, yêu thích, tìm kiếm, hẹn giờ, playlist, cài đặt và xử lý lỗi cơ bản."
   else ->
    "Mình chỉ trả lời về cách dùng Music App nên chưa hỗ trợ câu hỏi này. Bạn có thể hỏi: “Làm sao thêm nhạc?”, “Cách hẹn giờ tắt nhạc?”, “Tạo danh sách phát thế nào?” hoặc “Sửa lỗi không có tiếng ra sao?”"
  }
 }
 private fun showToolsPanel(){
  val items=arrayOf(
   "📈 Thống kê nghe nhạc",
   "📤 Chia sẻ danh sách bài hát",
   "🔤 Sắp xếp tên A–Z",
   "♥ Chỉ xem bài yêu thích",
   "🧽 Xóa tìm kiếm và bộ lọc",
   "📥 Xuất danh sách bài hát (TXT)",
   "🎲 Bật/tắt phát ngẫu nhiên",
   "🔁 Bật/tắt lặp bài hiện tại",
   "🌙 Hẹn giờ ngủ",
   "🕘 Lịch sử nghe gần đây",
   "🔊 Âm lượng",
   "🛠️ Chẩn đoán ứng dụng",
   "🎼 Nguồn nhạc và giấy phép",
   "👑 Giới thiệu ứng dụng",
   "🎚️ Bộ chỉnh âm (EQ)",
   "🎵 Danh sách phát riêng",
   "⏩ Tốc độ phát"
  )
  AlertDialog.Builder(this).setTitle("Tiện ích cần thiết").setItems(items){_,which->
   when(which){
    0->{showListeningStats()}
    1->{
     val content=tracks.joinToString("\n"){(if(it.favorite)"♥ " else "")+it.title}
     val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Danh sách nhạc Music App");putExtra(Intent.EXTRA_TEXT,if(content.isBlank())"Thư viện nhạc đang trống." else "🎵 Danh sách nhạc Music App\n\n$content")}
     startActivity(Intent.createChooser(send,"Chia sẻ danh sách nhạc"))
    }
    2->{tracks.sortBy{it.title.lowercase(Locale.ROOT)};renderList(search.text.toString());Toast.makeText(this,"Đã sắp xếp tên A–Z.",Toast.LENGTH_SHORT).show()}
    3->{favoritesOnly=!favoritesOnly;renderList(search.text.toString());Toast.makeText(this,if(favoritesOnly)"Đang chỉ hiện bài yêu thích." else "Đang hiện toàn bộ thư viện.",Toast.LENGTH_SHORT).show()}
    4->{search.setText("");favoritesOnly=false;renderList("");Toast.makeText(this,"Đã xóa tìm kiếm và bộ lọc.",Toast.LENGTH_SHORT).show()}
    5->{
     val body=tracks.joinToString("\n"){(if(it.favorite)"♥ " else "")+it.title}
     pendingExportText=if(body.isBlank())"Thư viện nhạc đang trống." else "Danh sách nhạc Music App\n\n$body"
     val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"music-app-playlist.txt")}
     startActivityForResult(intent,102)
    }
    6->{shuffle=!shuffle;Toast.makeText(this,if(shuffle)"Đã bật phát ngẫu nhiên." else "Đã tắt phát ngẫu nhiên.",Toast.LENGTH_SHORT).show()}
    7->{repeatOne=!repeatOne;Toast.makeText(this,if(repeatOne)"Đã bật lặp bài hiện tại." else "Đã tắt lặp bài.",Toast.LENGTH_SHORT).show()}
    8->showSleepTimerDialog()
    9->showHistory()
    10->showVolume()
    11->showDiagnostics()
    12->AlertDialog.Builder(this).setTitle("Nguồn nhạc và giấy phép").setMessage("• Giai điệu tích hợp: âm thanh tổng hợp trong ứng dụng.\n• Nhạc trực tuyến: cần Internet; hãy xem giấy phép của từng bản ghi trước khi chia sẻ lại.\n• Tệp nhạc từ thiết bị: do bạn chọn và quản lý.").setPositiveButton("Đã hiểu",null).show()
    13->AlertDialog.Builder(this).setTitle("Về Music App").setMessage("Music App\nNgười tạo: Bảo Nam\n\nTrình phát nhạc tập trung vào thư viện cá nhân, yêu thích, tìm kiếm và hẹn giờ ngủ.").setPositiveButton("Đóng",null).show()
    14->showEqualizer()
    15->showPlaylistManager()
    16->showPlaybackSpeed()
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showPlaylistManager(){
  if(destroyed||isFinishing)return
  val playlists=try{JSONObject(prefs.getString("custom_playlists","{}").orEmpty())}catch(_:Exception){JSONObject()}
  val names=mutableListOf<String>()
  val keys=playlists.keys()
  while(keys.hasNext())names.add(keys.next())
  val options=mutableListOf("➕ Tạo danh sách phát mới")
  options.addAll(names.sorted().map{"▶ $it"})
  if(names.isNotEmpty())options.add("🗑️ Xóa danh sách phát")
  AlertDialog.Builder(this).setTitle("🎵 Danh sách phát của tôi").setItems(options.toTypedArray()){_,which->
   when{
    which==0->createCustomPlaylist()
    which in 1..names.size->{
     val name=names.sorted()[which-1]
     val arr=try{playlists.optJSONArray(name)}catch(_:Exception){null}
     val saved=mutableListOf<String>()
     if(arr!=null)for(i in 0 until arr.length())saved.add(arr.optString(i))
     val selected=saved.mapNotNull{title->tracks.firstOrNull{it.title==title}}
     if(selected.isEmpty())Toast.makeText(this,"Danh sách này chưa có bài hợp lệ. Hãy tạo lại danh sách.",Toast.LENGTH_LONG).show()
     else AlertDialog.Builder(this).setTitle(name).setMessage("Có ${selected.size} bài hát. Phát cả danh sách theo thứ tự?").setPositiveButton("▶ Phát"){_,_->activePlaylist=selected;playlistPosition=0;playTrack(selected.first());Toast.makeText(this,"Đang phát danh sách: $name",Toast.LENGTH_SHORT).show()}.setNeutralButton("Chỉnh sửa"){_,_->editCustomPlaylist(name,saved)}.setNegativeButton("Đóng",null).show()
    }
    names.isNotEmpty()&&which==options.lastIndex->{
     val sorted=names.sorted()
     AlertDialog.Builder(this).setTitle("Xóa danh sách phát").setItems(sorted.toTypedArray()){_,index->
      val updated=try{JSONObject(prefs.getString("custom_playlists","{}").orEmpty())}catch(_:Exception){JSONObject()}
      updated.remove(sorted[index]);prefs.edit().putString("custom_playlists",updated.toString()).apply()
      Toast.makeText(this,"Đã xóa danh sách “${sorted[index]}”.",Toast.LENGTH_SHORT).show()
     }.setNegativeButton("Hủy",null).show()
    }
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun createCustomPlaylist(){
  if(tracks.isEmpty()){Toast.makeText(this,"Hãy thêm nhạc trước khi tạo danh sách.",Toast.LENGTH_SHORT).show();return}
  val nameInput=EditText(this).apply{hint="Tên danh sách phát";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)}
  val wrap=LinearLayout(this).apply{setPadding(dp(20),dp(8),dp(20),dp(4));addView(nameInput)}
  AlertDialog.Builder(this).setTitle("Tạo danh sách phát").setView(wrap).setPositiveButton("Chọn bài"){_,_->
   val name=nameInput.text.toString().trim()
   if(name.isBlank()){Toast.makeText(this,"Hãy nhập tên danh sách.",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   val existing=try{JSONObject(prefs.getString("custom_playlists","{}").orEmpty())}catch(_:Exception){JSONObject()}
   if(existing.has(name)){Toast.makeText(this,"Tên danh sách đã tồn tại.",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   selectPlaylistTracks(name,emptyList())
  }.setNegativeButton("Hủy",null).show()
 }
 private fun editCustomPlaylist(name:String,initial:List<String>){selectPlaylistTracks(name,initial)}
 private fun selectPlaylistTracks(name:String,initial:List<String>){
  val titles=tracks.map{it.title}.toTypedArray()
  val checked=BooleanArray(tracks.size){i->initial.contains(tracks[i].title)}
  AlertDialog.Builder(this).setTitle("Chọn bài cho “$name”").setMultiChoiceItems(titles,checked){_,which,isChecked->checked[which]=isChecked}.setPositiveButton("Lưu"){_,_->
   val chosen=tracks.indices.filter{checked[it]}.map{tracks[it].title}
   if(chosen.isEmpty()){Toast.makeText(this,"Chọn ít nhất một bài hát.",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   val all=try{JSONObject(prefs.getString("custom_playlists","{}").orEmpty())}catch(_:Exception){JSONObject()}
   all.put(name,JSONArray(chosen));prefs.edit().putString("custom_playlists",all.toString()).apply()
   Toast.makeText(this,"Đã lưu “$name” với ${chosen.size} bài hát.",Toast.LENGTH_LONG).show()
  }.setNegativeButton("Hủy",null).show()
 }
 private fun showHistory(){
  if(destroyed||isFinishing)return
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
 private fun recordPlay(title:String){
  try{
   val counts=JSONObject(prefs.getString("play_counts","{}").orEmpty())
   counts.put(title,counts.optInt(title,0)+1)
   prefs.edit().putString("play_counts",counts.toString()).apply()
  }catch(_:Exception){}
 }
 private fun showListeningStats(){
  if(destroyed||isFinishing)return
  val counts=try{JSONObject(prefs.getString("play_counts","{}").orEmpty())}catch(_:Exception){JSONObject()}
  val ranking=mutableListOf<Pair<String,Int>>()
  val keys=counts.keys()
  while(keys.hasNext()){val key=keys.next();ranking.add(key to counts.optInt(key,0))}
  ranking.sortByDescending{it.second}
  val total=ranking.sumOf{it.second}
  val history=prefs.getString("play_history","").orEmpty().split("|").filter{it.isNotBlank()}
  val top=if(ranking.isEmpty())"Chưa có dữ liệu phát nhạc. Hãy mở một bài để bắt đầu thống kê." else ranking.take(5).joinToString("\n\n"){(name,count)->
   val bars="▰".repeat(count.coerceAtMost(10))+"▱".repeat((10-count.coerceAtMost(10)).coerceAtLeast(0))
   "$bars  $name\n$count lượt chọn phát"
  }
  val message="🎧 TỔNG QUAN\nTổng lượt chọn phát: $total\nBài trong thư viện: ${tracks.size}\nBài yêu thích: ${tracks.count{it.favorite}}\nGần đây: ${history.size} bài\n\n🏆 NGHE NHIỀU NHẤT\n$top\n\nLượt phát được lưu trên thiết bị này."
  AlertDialog.Builder(this).setTitle("📈 Thống kê nghe nhạc").setMessage(message).setPositiveButton("Đóng",null).setNeutralButton("Đặt lại thống kê"){_,_->prefs.edit().remove("play_counts").apply();Toast.makeText(this,"Đã đặt lại thống kê.",Toast.LENGTH_SHORT).show()}.show()
 }
 private fun showSleepTimerDialog(){
  if(destroyed||isFinishing)return
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(4))}
  val choices=arrayOf("5 phút","10 phút","15 phút","30 phút","45 phút","60 phút")
  val minutes=intArrayOf(5,10,15,30,45,60)
  val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
  val selected=prefs.getInt("sleep_timer_minutes",15)
  for(i in choices.indices){val rb=RadioButton(this).apply{text=choices[i];setTextColor(Color.WHITE);id=1000+i;isChecked=minutes[i]==selected};group.addView(rb)}
  val fade=CheckBox(this).apply{text="Giảm âm lượng dần trong 30 giây cuối";setTextColor(Color.WHITE);isChecked=prefs.getBoolean("sleep_timer_fade",true)}
  panel.addView(group);panel.addView(fade)
  val dialog=AlertDialog.Builder(this).setTitle("🌙 Hẹn giờ ngủ").setView(panel).setPositiveButton("Bắt đầu",null).setNeutralButton("Hủy hẹn giờ",null).setNegativeButton("Đóng",null).create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    val checked=group.checkedRadioButtonId
    if(checked<1000||checked>=1000+minutes.size){Toast.makeText(this,"Chọn thời gian trước nhé.",Toast.LENGTH_SHORT).show();return@setOnClickListener}
    val mins=minutes[checked-1000];val shouldFade=fade.isChecked
    prefs.edit().putInt("sleep_timer_minutes",mins).putBoolean("sleep_timer_fade",shouldFade).apply()
    sleepTimer?.cancel()
    try{player?.setVolume(1f,1f)}catch(_:Exception){}
    try{synchronized(audioLock){audioTrack?.setVolume(android.media.AudioTrack.getMaxVolume())}}catch(_:Exception){}
    val total=mins*60_000L
    sleepTimer=object:android.os.CountDownTimer(total,250L){
     override fun onTick(ms:Long){
      if(!shouldFade||ms>30_000L)return
      val factor=(ms.coerceAtLeast(0).toFloat()/30_000f).coerceIn(0f,1f)
      try{player?.setVolume(factor,factor)}catch(_:Exception){}
      try{synchronized(audioLock){audioTrack?.setVolume(android.media.AudioTrack.getMaxVolume()*factor)}}catch(_:Exception){}
     }
     override fun onFinish(){try{player?.setVolume(0f,0f)}catch(_:Exception){};stopCurrentPlayback();nowPlaying.text="🌙 Hẹn giờ kết thúc — nhạc đã dừng";sleepTimer=null}
    }.start()
    Toast.makeText(this,if(shouldFade)"Nhạc sẽ nhỏ dần trong 30 giây cuối rồi dừng sau $mins phút." else "Nhạc sẽ dừng sau $mins phút.",Toast.LENGTH_LONG).show()
    dialog.dismiss()
   }
   dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{sleepTimer?.cancel();sleepTimer=null;try{player?.setVolume(1f,1f)}catch(_:Exception){};try{synchronized(audioLock){audioTrack?.setVolume(android.media.AudioTrack.getMaxVolume())}}catch(_:Exception){};Toast.makeText(this,"Đã hủy hẹn giờ.",Toast.LENGTH_SHORT).show();dialog.dismiss()}
  }
  dialog.show()
 }
 private fun showEqualizer(){
  if(destroyed||isFinishing)return
  val mp=player
  if(mp==null){
   AlertDialog.Builder(this).setTitle("🎚️ Bộ chỉnh âm").setMessage("Hãy phát một tệp nhạc từ thiết bị trước. Bộ chỉnh âm chỉ hoạt động với nguồn phát có phiên âm thanh tương thích; nhạc tích hợp hoặc một số thiết bị có thể không hỗ trợ.").setPositiveButton("Đã hiểu",null).show()
   return
  }
  val eq=try{
   equalizer?.release()
   Equalizer(0,mp.audioSessionId).also{equalizer=it}
  }catch(_:Exception){
   equalizer=null
   AlertDialog.Builder(this).setTitle("Bộ chỉnh âm chưa khả dụng").setMessage("Thiết bị hoặc nguồn phát hiện tại không hỗ trợ EQ. Bạn vẫn có thể dùng bộ chỉnh âm trong cài đặt âm thanh của thiết bị nếu có.").setPositiveButton("Đóng",null).show()
   return
  }
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(4))}
  val enabled=CheckBox(this).apply{
   text="Bật bộ chỉnh âm";setTextColor(Color.WHITE);isChecked=eq.enabled
   setOnCheckedChangeListener{_,checked->try{eq.enabled=checked}catch(_:Exception){}}
  }
  panel.addView(enabled)
  val range=eq.bandLevelRange
  val min=range[0].toInt();val max=range[1].toInt()
  val count=eq.numberOfBands.toInt().coerceAtMost(10)
  val sliders=mutableListOf<SeekBar>()
  val labels=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
  for(i in 0 until count){
   val band=i.toShort()
   val hz=try{eq.getCenterFreq(band)/1000f}catch(_:Exception){0f}
   val name=when{hz>=1000f->String.format(Locale.US,"%.1fk",hz/1000f);hz>=1f->String.format(Locale.US,"%.0f",hz);else->"B${i+1}"}
   val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(1),0,dp(1),0)}
   val valueLabel=TextView(this).apply{text="0 dB";textSize=9f;gravity=Gravity.CENTER;setTextColor(Color.rgb(125,195,255))}
   val seek=SeekBar(this).apply{this.max=(max-min).coerceAtLeast(1)}
   sliders.add(seek)
   seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean){
     val level=(progress+min).coerceIn(min,max)
     valueLabel.text=String.format(Locale.US,"%.1f dB",level/100f)
     try{eq.setBandLevel(band,level.toShort());if(fromUser)prefs.edit().putInt("eq_band_$i",level).apply()}catch(_:Exception){}
    }
    override fun onStartTrackingTouch(bar:SeekBar?){}
    override fun onStopTrackingTouch(bar:SeekBar?){}
   })
   val saved=prefs.getInt("eq_band_$i",0).coerceIn(min,max)
   seek.progress=saved-min
   try{eq.setBandLevel(band,saved.toShort())}catch(_:Exception){}
   valueLabel.text=String.format(Locale.US,"%.1f dB",saved/100f)
   col.addView(valueLabel)
   col.addView(seek,LinearLayout.LayoutParams(dp(34),dp(142)))
   col.addView(TextView(this).apply{text=name;textSize=9f;gravity=Gravity.CENTER;setTextColor(Color.LTGRAY)})
   labels.addView(col,LinearLayout.LayoutParams(0,-2,1f))
  }
  panel.addView(labels)
  val reset=button("Đặt lại EQ")
  reset.setOnClickListener{
   sliders.forEachIndexed{i,seek->seek.progress=(0-min).coerceIn(0,seek.max);prefs.edit().putInt("eq_band_$i",0).apply()}
   Toast.makeText(this,"Đã đặt EQ về mức trung tính.",Toast.LENGTH_SHORT).show()
  }
  panel.addView(reset,LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(8)})
  AlertDialog.Builder(this).setTitle("🎚️ Bộ chỉnh âm").setView(ScrollView(this).apply{addView(panel)}).setPositiveButton("Xong",null).setNegativeButton("Đóng",null).show()
 }
 private fun applySavedEqualizer(mp:MediaPlayer){
  try{
   equalizer?.release()
   val eq=Equalizer(0,mp.audioSessionId)
   val range=eq.bandLevelRange
   for(i in 0 until eq.numberOfBands.toInt()){
    val level=prefs.getInt("eq_band_$i",0).coerceIn(range[0].toInt(),range[1].toInt())
    eq.setBandLevel(i.toShort(),level.toShort())
   }
   eq.enabled=true
   equalizer=eq
  }catch(_:Exception){try{equalizer?.release()}catch(_:Exception){};equalizer=null}
 }
 private fun showPlaybackSpeed(){
  if(destroyed||isFinishing)return
  val speeds=floatArrayOf(0.75f,1.0f,1.25f,1.5f,2.0f)
  val labels=arrayOf("0,75× — Chậm","1,0× — Bình thường","1,25×","1,5×","2,0× — Nhanh")
  val saved=prefs.getFloat("playback_speed",1.0f)
  val selected=speeds.indices.minByOrNull{ kotlin.math.abs(speeds[it]-saved) }?:1
  AlertDialog.Builder(this).setTitle("⏩ Tốc độ phát").setSingleChoiceItems(labels,selected){dialog,which->
   val speed=speeds[which]
   prefs.edit().putFloat("playback_speed",speed).apply()
   val mp=player
   if(mp!=null)try{
    if(android.os.Build.VERSION.SDK_INT>=23)mp.playbackParams=mp.playbackParams.setSpeed(speed)
    else Toast.makeText(this,"Tốc độ phát cần Android 6.0 trở lên.",Toast.LENGTH_LONG).show()
   }catch(_:Exception){Toast.makeText(this,"Nguồn phát này không hỗ trợ đổi tốc độ.",Toast.LENGTH_LONG).show()}
   Toast.makeText(this,"Đã chọn tốc độ ${labels[which].substringBefore(" —")}. Áp dụng cho bài nhạc tương thích.",Toast.LENGTH_SHORT).show()
   dialog.dismiss()
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showVolume(){
  if(destroyed||isFinishing)return
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
  if(destroyed||isFinishing)return
  val audio=getSystemService(AUDIO_SERVICE) as AudioManager
  val details="Music App — Kiểm tra nhanh\\n\\n"+
   "• Bài hát trong thư viện: ${tracks.size}\\n"+
   "• Bài yêu thích: ${tracks.count{it.favorite}}\\n"+
   "• Trạng thái phát: ${if(player!=null||audioTrack!=null)"Có thiết bị phát" else "Đang chờ / phát ngoài ứng dụng"}\\n"+
   "• Mạng: cần Internet để phát nhạc trực tuyến\\n\\n"+
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
  if(destroyed||isFinishing)return
  val items=arrayOf("🌍 Chọn quốc gia","🔐 Bảo mật","ℹ️ Thông tin ứng dụng","🛠️ Chẩn đoán ứng dụng","🕘 Lịch sử nghe")
  AlertDialog.Builder(this).setTitle("⚙ Cài đặt").setItems(items){_,which->
   when(which){
    0->showCountryPicker()
    1->AlertDialog.Builder(this).setTitle("🔐 Bảo mật và riêng tư").setMessage("Music App không cần API key để phát nhạc. Không chia sẻ dữ liệu cá nhân hoặc tệp riêng tư khi không cần thiết.").setPositiveButton("Đã hiểu",null).show()
    2->AlertDialog.Builder(this).setTitle("ℹ️ Music App 1.4").setMessage("🎵 Trình phát nhạc cá nhân\\n🌍 Tùy chọn quốc gia\\n🛠️ Chẩn đoán ứng dụng\\n🕘 Lịch sử nghe\\n\\nNgười phát triển: Bảo Nam").setPositiveButton("Đóng",null).show()
    3->showDiagnostics()
    4->showHistory()
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun showApiKeySettings(){
  val input=EditText(this).apply{
   hint="sk-..."
   setSingleLine(true)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
   setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)
   setText(prefs.getString("openai_api_key","") ?: "")
  }
  val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(4));addView(input,LinearLayout.LayoutParams(-1,dp(52)))}
  AlertDialog.Builder(this).setTitle("🔑 OpenAI API Key")
   .setMessage("Nhập key OpenAI của bạn. API có thể tính phí riêng. Key được lưu trong dữ liệu riêng của ứng dụng trên thiết bị; lưu trên máy chủ riêng sẽ an toàn hơn.")
   .setView(wrap)
   .setNeutralButton("Xóa key"){_,_->prefs.edit().remove("openai_api_key").apply();Toast.makeText(this,"Đã xóa API Key khỏi ứng dụng.",Toast.LENGTH_SHORT).show()}
   .setNegativeButton("Hủy",null)
   .setPositiveButton("Lưu"){_,_->
    val key=input.text.toString().trim()
    if(key.isBlank()){Toast.makeText(this,"Key trống. Hãy nhập key hoặc chọn Xóa key.",Toast.LENGTH_LONG).show()}
    else if(!key.startsWith("sk-")){Toast.makeText(this,"Key có vẻ không đúng định dạng. Hãy kiểm tra lại trên OpenAI Platform.",Toast.LENGTH_LONG).show()}
    else {prefs.edit().putString("openai_api_key",key).apply();Toast.makeText(this,"Đã lưu key trên thiết bị.",Toast.LENGTH_LONG).show()}
   }.show()
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
  showAiTutor()
 }
 private fun showAiTutor(){
  if(destroyed||isFinishing)return
  // API credentials must never be entered into or stored by the Android client.
  // The app talks only to the configured HTTPS backend, which keeps OPENAI_API_KEY server-side.
  prefs.edit().remove("openai_api_key").apply()
  showAiTutorWithoutKey()
 }
 private fun showAiTutorWithoutKey(){
  val navy=Color.rgb(7,16,31)
  val panel=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(navy)
  }
  val header=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(12),dp(10),dp(12),dp(10))
   setBackgroundColor(Color.rgb(12,27,49))
  }
  val back=button("‹").apply{textSize=34f;background=rounded(Color.TRANSPARENT,24)}
  header.addView(back,LinearLayout.LayoutParams(dp(42),dp(48)))
  val avatar=TextView(this).apply{
   text="🤖";textSize=25f;gravity=Gravity.CENTER
   background=rounded(Color.rgb(15,105,220),30)
  }
  header.addView(avatar,LinearLayout.LayoutParams(dp(48),dp(48)).apply{leftMargin=dp(6);rightMargin=dp(10)})
  val titleBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleBox.addView(TextView(this).apply{
   text="Bảo Nam Bot";textSize=19f
   setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
   setTextColor(Color.WHITE)
  })
  titleBox.addView(TextView(this).apply{
   text="● ChatGPT AI • Tìm web • Đọc câu trả lời";textSize=11f
   setTextColor(Color.rgb(116,203,171))
  })
  header.addView(titleBox,LinearLayout.LayoutParams(0,dp(48),1f))
  val menu=button("⋮").apply{textSize=25f;background=rounded(Color.TRANSPARENT,24)}
  header.addView(menu,LinearLayout.LayoutParams(dp(38),dp(48)))
  panel.addView(header,LinearLayout.LayoutParams(-1,dp(76)))
  val divider=android.view.View(this).apply{setBackgroundColor(Color.rgb(27,48,76))}
  panel.addView(divider,LinearLayout.LayoutParams(-1,dp(1)))
  val messages=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(dp(12),dp(14),dp(12),dp(14))
  }
  val scroll=ScrollView(this).apply{
   isFillViewport=true
   addView(messages,android.widget.FrameLayout.LayoutParams(-1,-2))
   setBackgroundColor(navy)
  }
  panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  fun addMessage(role:String,text:String){
   val isUser=role=="user"
   val line=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL
    gravity=if(isUser)Gravity.END else Gravity.START
    setPadding(0,dp(4),0,dp(4))
   }
   if(!isUser){
    val icon=TextView(this).apply{
     this.text="🤖";textSize=17f;gravity=Gravity.CENTER
     background=rounded(Color.rgb(15,83,158),24)
    }
    line.addView(icon,LinearLayout.LayoutParams(dp(34),dp(34)).apply{rightMargin=dp(7)})
   }
   val bubble=TextView(this).apply{
    this.text=text
    textSize=15f
    setTextColor(Color.WHITE)
    setPadding(dp(13),dp(10),dp(13),dp(10))
    background=rounded(if(isUser)Color.rgb(16,111,235) else Color.rgb(25,42,66),18)
    setTextIsSelectable(true)
   }
   val maxWidth=(resources.displayMetrics.widthPixels*0.78f).toInt()
   line.addView(bubble,LinearLayout.LayoutParams(maxWidth,-2))
   if(isUser){
    val icon=TextView(this).apply{
     this.text="●";textSize=20f;gravity=Gravity.CENTER
     setTextColor(Color.rgb(170,204,255))
    }
    line.addView(icon,LinearLayout.LayoutParams(dp(25),dp(34)).apply{leftMargin=dp(4)})
   }
   messages.addView(line,LinearLayout.LayoutParams(-1,-2))
   scroll.post{scroll.fullScroll(ScrollView.FOCUS_DOWN)}
  }
  val backendConfigured=BuildConfig.OPENAI_BACKEND_URL.trim().startsWith("https://",ignoreCase=true)
  addMessage("assistant",if(backendConfigured) "Xin chào! 👋 Mình là Bảo Nam AI Music.\n\n🧠 Tin nhắn sẽ được gửi qua máy chủ HTTPS để dùng mô hình AI thật. Khóa API chỉ nằm trên máy chủ, không nằm trong APK.\n🌐 Khi cần, mình có thể thử tìm thông tin trên Internet.\n\n🎓 Học tập • 🧮 Toán • 💻 Lập trình • 🎵 Âm nhạc • 🔊 Đọc câu trả lời\n\nMở menu ⋮ để chỉnh tùy chọn." else "Xin chào! 👋 Mình là Bảo Nam AI Music.\n\n⚙️ Chế độ AI thật chưa được kết nối vì bản build chưa có địa chỉ máy chủ HTTPS. Hiện tại mình sẽ dùng câu trả lời dự phòng và tìm Internet khi phù hợp.\n🔐 Không nhập API Key vào ứng dụng. Cần triển khai máy chủ AI và cấu hình OPENAI_BACKEND_URL trong GitHub Actions Variables; khóa OPENAI_API_KEY chỉ lưu trong máy chủ.\n\n🎓 Học tập • 🧮 Toán • 💻 Lập trình • 🎵 Âm nhạc • 🔊 Đọc câu trả lời")
  val conversation=JSONArray()
  var autoReadAnswer=prefs.getBoolean("ai_auto_speak",true)
  val composer=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(10),dp(9),dp(10),dp(10))
   setBackgroundColor(Color.rgb(9,21,39))
  }
  val plus=button("+").apply{
   textSize=25f
   background=rounded(Color.rgb(28,45,70),30)
  }
  composer.addView(plus,LinearLayout.LayoutParams(dp(42),dp(46)).apply{rightMargin=dp(7)})
  val input=EditText(this).apply{
   hint="Nhắn tin cho Bảo Nam Bot..."
   setSingleLine(false);maxLines=4;minHeight=dp(46)
   textSize=15f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(143,159,184))
   background=rounded(Color.rgb(24,39,61),22)
   setPadding(dp(14),dp(8),dp(14),dp(8))
   imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND
  }
  activeAiInput=input
  composer.addView(input,LinearLayout.LayoutParams(0,-2,1f))
  val mic=button("🎙").apply{textSize=17f;background=rounded(Color.rgb(28,45,70),28);setOnClickListener{try{val intent=Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"vi-VN");putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Hãy nói câu hỏi của bạn")};startActivityForResult(intent,104)}catch(_:Exception){Toast.makeText(this@MainActivity,"Thiết bị chưa hỗ trợ nhập giọng nói. Hãy kiểm tra Google app và quyền micro.",Toast.LENGTH_LONG).show()}}}
  composer.addView(mic,LinearLayout.LayoutParams(dp(44),dp(46)).apply{leftMargin=dp(6)})
  val send=button("➤").apply{
   textSize=22f
   background=rounded(Color.rgb(17,119,247),30)
  }
  composer.addView(send,LinearLayout.LayoutParams(dp(48),dp(46)).apply{leftMargin=dp(7)})
  panel.addView(composer,LinearLayout.LayoutParams(-1,-2))
  val dialog=android.app.Dialog(this)
  dialog.setContentView(panel)
  dialog.setCancelable(true)
  fun ask(){
   val q=input.text.toString().trim()
   if(q.isEmpty())return
   val historySnapshot=JSONArray()
   for(i in 0 until conversation.length())historySnapshot.put(conversation.getJSONObject(i))
   input.setText("")
   addMessage("user",q)
   send.isEnabled=false
   send.text="…"
   addMessage("assistant","🤖 Đang suy nghĩ…")
   Thread{
    val ai=openAiProxyReply(q,historySnapshot)
    val wantsWeb=prefs.getBoolean("ai_internet_search",true)&&shouldSearchInternet(q)
    val web=if(ai==null&&wantsWeb)searchInternet(q)else null
    val answer=ai ?: web ?: if(wantsWeb){
     "Mình chưa kết nối được AI hoặc lấy kết quả web lần này. Mình sẽ thử trả lời ngoại tuyến.\n\n"+offlineBotReply(q)
    }else{
     offlineBotReply(q)
    }
    runOnUiThread{
     if(!destroyed&&!isFinishing&&dialog.isShowing){
      if(messages.childCount>0)messages.removeViewAt(messages.childCount-1)
      addMessage("assistant",answer)
      conversation.put(JSONObject().put("role","user").put("content",q))
      conversation.put(JSONObject().put("role","assistant").put("content",answer))
      while(conversation.length()>20)conversation.remove(0)
      lastAiAnswer=answer
      if(autoReadAnswer)speakAi(answer)
      send.isEnabled=true
      send.text="➤"
     }
    }
   }.start()
  }
  back.setOnClickListener{dialog.dismiss()}
  menu.setOnClickListener{
   AlertDialog.Builder(this).setTitle("Bảo Nam Bot")
    .setItems(arrayOf("Giới thiệu","Đọc câu trả lời gần nhất","Gợi ý câu hỏi","Xóa cuộc trò chuyện","Bật/tắt tìm kiếm Internet",if(autoReadAnswer)"🔊 Tắt tự đọc câu trả lời" else "🔊 Bật tự đọc câu trả lời")){_,which->
     when(which){
      0->AlertDialog.Builder(this).setTitle("Giới thiệu Bảo Nam Bot")
       .setMessage("ChatGPT Music AI gửi câu hỏi đến máy chủ OpenAI đã cấu hình. Nếu AI không khả dụng, ứng dụng có thể tìm kết quả web hoặc dùng câu trả lời ngoại tuyến. API key phải được lưu trên máy chủ, không nhúng trong APK.")
       .setPositiveButton("Đóng",null).show()
      1->speakAi(lastAiAnswer)
      2->AlertDialog.Builder(this).setTitle("Gợi ý câu hỏi").setItems(arrayOf("Giải thích điệp ngữ","Tính 125 + 37","Hướng dẫn sửa lỗi Kotlin","Cách dùng Music App")){_,pick->
       val suggestions=arrayOf("Giải thích điệp ngữ là gì?","125 + 37","Hướng dẫn sửa lỗi Kotlin Android","Hướng dẫn sử dụng Music App")
       input.setText(suggestions[pick]);input.setSelection(input.text.length)
      }.show()
      3->{messages.removeAllViews();addMessage("assistant","Cuộc trò chuyện đã được xóa. 👋 Bạn muốn hỏi gì?")}
      4->{val enabled=!prefs.getBoolean("ai_internet_search",true);prefs.edit().putBoolean("ai_internet_search",enabled).apply();Toast.makeText(this,if(enabled)"🌐 Đã bật tìm Internet" else "📴 Đã tắt tìm Internet, dùng chế độ ngoại tuyến",Toast.LENGTH_LONG).show()}
      5->{autoReadAnswer=!autoReadAnswer;prefs.edit().putBoolean("ai_auto_speak",autoReadAnswer).apply();Toast.makeText(this,if(autoReadAnswer)"🔊 AI sẽ tự đọc câu trả lời mới." else "Đã tắt tự đọc câu trả lời.",Toast.LENGTH_LONG).show()}
     }
    }.show()
  }
  plus.setOnClickListener{
   AlertDialog.Builder(this).setTitle("Bảo Nam Bot")
    .setItems(arrayOf("Gợi ý học tập","Gợi ý lập trình","Tính phép tính","Đọc câu trả lời gần nhất")){_,which->
     val suggestion=when(which){
      0->"Giải thích điệp ngữ là gì?"
      1->"Hướng dẫn sửa lỗi Kotlin Android"
      2->"125 + 37"
      else->""
     }
     if(which==3)speakAi(lastAiAnswer) else {input.setText(suggestion);input.setSelection(input.text.length)}
    }.show()
  }
  send.setOnClickListener{ask()}
  input.setOnEditorActionListener{_,action,_->
   if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){ask();true}else false
  }
  dialog.setOnDismissListener{tts?.stop();activeAiInput=null}
  dialog.show()
  dialog.window?.setLayout(-1,-1)
  dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
  dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
 }
 private fun shouldSearchInternet(message:String):Boolean{
  val q=java.text.Normalizer.normalize(message.lowercase(Locale.ROOT),java.text.Normalizer.Form.NFD)
   .replace("\\p{Mn}+".toRegex(),"").replace('đ','d').trim()
  if(q.isBlank())return false
  if(Regex("^\\s*-?\\d+(?:[.,]\\d+)?\\s*[+*/x×-]\\s*-?\\d+(?:[.,]\\d+)?=?\\s*$").matches(q))return false
  if(listOf("xin chao","chao ban","hello","hi","hey","cam on","thank you","thanks","ban la ai","ten ban","quyen rieng tu","bao mat","api key","offline","ngoai tuyen").any{q.contains(it)})return false
  return true
 }
 private fun openAiProxyReply(message:String,history:JSONArray=JSONArray()):String?{
  val configured=BuildConfig.OPENAI_BACKEND_URL.trim()
  // Never send API keys from the app. A backend URL must be supplied at build time.
  if(configured.isBlank())return null
  val endpoint=if(configured.endsWith("/api/openai-chat"))configured else configured.trimEnd('/')+"/api/openai-chat"
  if(!endpoint.startsWith("https://",ignoreCase=true))return null
  var connection:HttpURLConnection?=null
  return try{
   connection=(URL(endpoint).openConnection() as HttpURLConnection).apply{
    requestMethod="POST";connectTimeout=12000;readTimeout=45000;doOutput=true
    setRequestProperty("Content-Type","application/json; charset=utf-8")
    setRequestProperty("Accept","application/json")
   }
   val safeHistory=JSONArray()
   for(i in (history.length()-10).coerceAtLeast(0) until history.length()){
    val item=history.optJSONObject(i)?:continue
    val role=item.optString("role")
    if((role=="user"||role=="assistant")&&item.optString("content").isNotBlank()){
     safeHistory.put(JSONObject().put("role",role).put("content",item.optString("content").take(3000)))
    }
   }
   val payload=JSONObject().put("message",message.take(4000)).put("history",safeHistory).toString()
   connection.outputStream.use{it.write(payload.toByteArray(Charsets.UTF_8))}
   val status=connection.responseCode
   val stream=if(status in 200..299)connection.inputStream else connection.errorStream
   val body=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText().take(250000)} ?: return null
   if(status !in 200..299)return null
   JSONObject(body).optString("answer","").trim().takeIf{it.isNotBlank()}
  }catch(_:Exception){null}
  finally{connection?.disconnect()}
 }
 private fun searchInternet(query:String):String?{
  var connection:HttpURLConnection?=null
  return try{
   val encoded=java.net.URLEncoder.encode(query,"UTF-8")
   connection=(URL("https://html.duckduckgo.com/html/?q="+encoded).openConnection() as HttpURLConnection).apply{
    requestMethod="GET"
    connectTimeout=10000
    readTimeout=10000
    instanceFollowRedirects=true
    setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
    setRequestProperty("Accept-Language","vi,en-US;q=0.8,en;q=0.6")
   }
   val status=connection.responseCode
   if(status !in 200..299)return null
   val html=connection.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText().take(1500000)}
   val resultPattern=Regex("""(?is)<div(?=[^>]*class=["'][^"']*result__body)[^>]*>(.*?)</div>\s*</div>|<a(?=[^>]*class=["'][^"']*result__a)([^>]*)>(.*?)</a>""")
   fun clean(value:String):String{
    return value.replace(Regex("(?is)<script[^>]*>.*?</script>"),"")
     .replace(Regex("(?is)<[^>]+>"),"")
     .replace("&amp;","&").replace("&quot;","\"").replace("&#x27;","'")
     .replace("&#39;","'").replace("&lt;","<").replace("&gt;",">")
     .replace("&nbsp;"," ").replace("&#x2F;","/")
     .replace(Regex("\\s+")," ").trim()
   }
   val titleMatches=Regex("""(?is)<a(?=[^>]*class=["'][^"']*result__a)([^>]*)>(.*?)</a>""").findAll(html).take(6).toList()
   val snippetMatches=Regex("""(?is)<(?:a|div|span)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div|span)>""").findAll(html).map{clean(it.groupValues[1])}.filter{it.isNotBlank()}.toList()
   if(titleMatches.isEmpty())return null
   val out=StringBuilder("🌐 Mình tìm được thông tin trên Internet về: \"").append(query).append("\"\n\n")
   var count=0
   for((index,match) in titleMatches.withIndex()){
    val attrs=match.groupValues[1]
    val title=clean(match.groupValues[2])
    val rawHref=Regex("""(?i)href=["']([^"']+)["']""").find(attrs)?.groupValues?.get(1) ?: continue
    var href=rawHref.replace("&amp;","&")
    if(href.startsWith("/l/?")||href.startsWith("https://duckduckgo.com/l/?")){
     val parsed=Uri.parse(if(href.startsWith("http"))href else "https://duckduckgo.com"+href)
     href=parsed.getQueryParameter("uddg") ?: href
    }
    if(!href.startsWith("https://")&&!href.startsWith("http://"))continue
    if(title.isBlank())continue
    count++
    out.append(count).append(". ").append(title).append("\n")
    val snippet=snippetMatches.getOrNull(index).orEmpty()
    if(snippet.isNotBlank())out.append("   Tóm tắt: ").append(snippet).append("\n")
    out.append("   Nguồn: ").append(href).append("\n\n")
   }
   if(count==0)null else out.append("Mình đã tóm tắt dựa trên đoạn mô tả của công cụ tìm kiếm, không phải câu trả lời do mô hình AI tạo ra. Đoạn mô tả có thể thiếu ngữ cảnh; hãy mở nguồn để kiểm tra.").toString()
  }catch(_:Exception){null}
  finally{connection?.disconnect()}
 }
 private var lastAiAnswer="Xin chào! Mình là Bảo Nam Bot."
 private fun offlineBotReply(message:String):String{
  val q=java.text.Normalizer.normalize(message.lowercase(Locale.ROOT),java.text.Normalizer.Form.NFD)
   .replace("\\p{Mn}+".toRegex(),"").replace('đ','d').replace("\\s+".toRegex()," ").trim()
  fun has(vararg words:String)=words.any{q.contains(it)}
  fun numberText(v:Double)=if(v.isFinite()&&v==v.toLong().toDouble())v.toLong().toString() else v.toString()
  val math=Regex("^(-?\\d+(?:[.,]\\d+)?)\\s*([+*/x×-])\\s*(-?\\d+(?:[.,]\\d+)?)=?$").find(q)
  if(math!=null){
   val a=math.groupValues[1].replace(',','.').toDoubleOrNull()
   val b=math.groupValues[3].replace(',','.').toDoubleOrNull()
   if(a!=null&&b!=null){
    val op=math.groupValues[2]
    if(op=="/"&&b==0.0)return "Không thể chia cho 0. Hãy nhập một số chia khác 0 nhé."
    val r=when(op){"+"->a+b;"-"->a-b;"*" ,"x","×"->a*b;"/"->a/b;else->Double.NaN}
    if(r.isFinite())return "🧮 Kết quả: "+numberText(a)+" "+op+" "+numberText(b)+" = "+numberText(r)+"\n\nHãy kiểm tra dấu phép tính và yêu cầu làm tròn của đề bài."
   }
  }
  return when{
   q.isBlank()->"Bạn hãy nhập câu hỏi nhé. Bạn có thể chọn dấu + cạnh ô nhập để xem gợi ý."
   has("xin chao","chao ban","hello","hi","hey")->"Chào bạn! 👋 Mình là Bảo Nam Bot. Bạn muốn học bài, tính toán, hỏi về lập trình hay cách dùng Music App?"
   has("ban la ai","ten ban","gioi thieu")->"Mình là Bảo Nam Bot 🤖, trợ lý tích hợp trong Music App do Bảo Nam phát triển. Mình hoạt động ngoại tuyến, không cần API key và không gửi câu hỏi lên máy chủ. Mình dùng quy tắc và kiến thức tích hợp sẵn nên khả năng có giới hạn."
   has("api key","can key","ngoai tuyen","offline","bao mat","du lieu ca nhan","quyen rieng tu")->"🔒 Chế độ AI hiện tại là ngoại tuyến: không cần API key, không gọi dịch vụ AI bên ngoài và không gửi nội dung trò chuyện lên máy chủ.\n\nLưu ý: câu trả lời dựa trên bộ quy tắc tích hợp sẵn nên không thể biết mọi chủ đề hoặc tin tức mới."
   has("cam on","thanks","thank you")->"Không có gì! 😊 Nếu muốn, bạn gửi câu hỏi tiếp theo hoặc đề bài đầy đủ nhé."
   has("ban co the lam gi","chuc nang","huong dan","goi y cau hoi")->"Mình có thể hỗ trợ:\n• 📚 Giải thích một số khái niệm học tập\n• 🧮 Tính phép cộng, trừ, nhân, chia cơ bản\n• 💻 Gợi ý kiểm tra lỗi lập trình thường gặp\n• 🎵 Hướng dẫn tính năng Music App\n• 🔊 Đọc câu trả lời nếu thiết bị có dữ liệu tiếng Việt.\n\nMình không duyệt web, không chạy code và có thể thiếu kiến thức chuyên sâu."
   has("diep ngu","phep lap","lap lai tu")->"📚 Điệp ngữ là biện pháp tu từ lặp lại từ ngữ hoặc cụm từ có chủ ý.\n\nTác dụng thường gặp:\n1. Nhấn mạnh hình ảnh, ý tưởng hoặc cảm xúc.\n2. Tạo nhịp điệu và liên kết cho câu thơ, câu văn.\n3. Làm nổi bật tâm trạng hoặc thông điệp.\n\nKhi làm bài, hãy trích đúng từ ngữ được lặp và nêu tác dụng gắn với đoạn văn cụ thể."
   has("nguoi ke chuyen","ngoi ke","ngoi thu nhat","ngoi thu ba")->"📖 Việc lựa chọn người kể chuyện giúp định hướng cách người đọc tiếp nhận câu chuyện.\n\n• Ngôi thứ nhất: tạo cảm giác gần gũi qua trải nghiệm và cảm xúc của người kể; phạm vi hiểu biết thường giới hạn ở điều người kể biết.\n• Ngôi thứ ba: giúp kể linh hoạt về nhiều nhân vật, sự việc; tùy cách kể có thể tạo cái nhìn bao quát.\n\nHãy xác định dấu hiệu xưng hô trong văn bản trước khi nêu tác dụng cụ thể."
   has("thanh ngu","so tu","thanh ngu noi ve so")->"📚 Thành ngữ có số từ là cụm từ cố định có chứa từ chỉ số lượng, thường mang nghĩa khái quát hoặc bóng nghĩa. Ví dụ: “một nắng hai sương” nói về sự vất vả; “ba chìm bảy nổi” nói về cuộc đời nhiều thăng trầm; “một mất một còn” chỉ tình thế quyết liệt. Khi làm bài, hãy giải thích nghĩa của cả thành ngữ trong ngữ cảnh."
   has("ngu van","van hoc","viet van","phan tich tac pham","tac dung")->"📝 Cách trả lời câu hỏi Ngữ văn gọn mà đủ ý:\n1. Nêu khái niệm hoặc nhận xét chính.\n2. Dẫn chi tiết/từ ngữ cụ thể trong văn bản.\n3. Phân tích tác dụng đối với nội dung, hình ảnh, nhịp điệu hoặc cảm xúc.\n4. Kết luận ngắn gắn với chủ đề.\n\nBạn gửi đoạn trích hoặc ảnh đề bài để mình giúp phân tích sát ngữ cảnh."
   has("phuong trinh","toan hoc","toan","phep tinh","tinh giup","bang bao nhieu")->"🧮 Mình tính được phép cộng, trừ, nhân và chia số học cơ bản. Hãy nhập biểu thức rõ ràng, ví dụ: 125 + 37 hoặc 12 * 8. Với phương trình hoặc bài nhiều bước, hãy gửi nguyên đề để mình hướng dẫn từng bước."
   has("te bao thuc vat","te bao","virus","sinh hoc")->"🔬 Gợi ý học Sinh học: xác định cấu trúc, chức năng và mối liên hệ giữa các bộ phận. Với tế bào thực vật, những phần thường cần nhớ gồm thành tế bào, màng sinh chất, tế bào chất, nhân, không bào lớn và lục lạp. Không phải mọi tế bào thực vật đều có đủ bào quan giống nhau; hãy theo đúng sơ đồ trong sách giáo khoa."
   has("vat ly","hoa hoc","phan ung hoa hoc","luc day","dien ap")->"🔬 Mình có thể giúp ôn khoa học cơ bản, nhưng cần biết câu hỏi cụ thể để tránh trả lời sai. Hãy gửi tên khái niệm, dữ kiện và đơn vị trong đề; mình sẽ nêu rõ nếu chưa đủ thông tin."
   has("python","kotlin","lap trinh","viet code","loi code","android","gradle","apk")->"💻 Để kiểm tra lỗi code hiệu quả, hãy gửi:\n1. Đoạn code liên quan.\n2. Thông báo lỗi đầy đủ và dòng lỗi.\n3. Bạn muốn chương trình hoạt động như thế nào.\n\nMình có thể giải thích và đề xuất cách sửa ngoại tuyến, nhưng không tự biên dịch hoặc chạy thử dự án trong ứng dụng này."
   has("music app","phat nhac","bai hat","ung dung","yeu thich","hen gio")->"🎵 Mẹo dùng Music App:\n• Chạm bài hát trong danh sách để phát.\n• Dùng ▶/⏸ để phát hoặc tạm dừng.\n• Nút ♥ để quản lý bài yêu thích.\n• “Thêm nhạc” để chọn tệp âm thanh trên thiết bị.\n• Dùng “Hẹn giờ” để đặt thời gian dừng nhạc.\n• Nhạc trực tuyến cần Internet; tệp cá nhân phụ thuộc quyền truy cập của thiết bị."
   has("hoc bai","on thi","bai tap","dap an","giai bai")->"📘 Bạn gửi đề bài đầy đủ (hoặc ảnh rõ nét) nhé. Mình sẽ cố gắng chia lời giải thành từng bước và nêu lý do, không chỉ đưa đáp án. Với bài ở trường, hãy đối chiếu lại với sách và hướng dẫn của giáo viên."
   has("thoi tiet","tin tuc","hom nay","gia vang","ket qua bong da","moi nhat")->"🌐 Mình đang ngoại tuyến nên không thể xác minh tin tức, thời tiết, giá cả hoặc kết quả mới nhất. Hãy kiểm tra nguồn cập nhật đáng tin cậy; nếu bạn gửi thông tin đó vào đây, mình có thể giúp giải thích."
   has("xin loi","sai roi","khong dung","khong phai")->"Cảm ơn bạn đã nhắc! 🙏 Mình có thể hiểu sai vì dùng kiến thức ngoại tuyến. Bạn chỉ rõ phần chưa đúng hoặc gửi thêm ngữ cảnh nhé; mình sẽ thử phân tích lại thay vì khẳng định khi chưa chắc."
   else->"Mình chưa có câu trả lời đủ chắc chắn cho câu hỏi này trong bộ kiến thức ngoại tuyến. 🤔\n\nBạn thử viết câu hỏi cụ thể hơn, chia câu hỏi dài thành từng ý hoặc gửi nguyên đề bài/đoạn văn liên quan nhé.\n\nMình không tra cứu Internet và sẽ nói rõ giới hạn thay vì đoán bừa."
  }
 }
 private fun openMap(){
  val uri=Uri.parse("geo:0,0?q=bản đồ")
  try{startActivity(Intent(Intent.ACTION_VIEW,uri))}
  catch(_:Exception){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps")))}catch(_:Exception){nowPlaying.text="Không mở được Bản đồ"}}
 }
 private fun showYoutubeMix(){
  val moods=arrayOf(
   "🌙 Nhạc ngủ nhẹ nhàng" to "nhạc ngủ sâu thư giãn nhẹ nhàng",
   "🌧️ Nhạc mưa chill" to "rain sounds chill music",
   "📚 Nhạc tập trung học bài" to "lofi study music no lyrics",
   "🎹 Piano thư giãn" to "relaxing piano music",
   "🎧 Lofi Việt Nam" to "lofi Việt Nam chill",
   "⚡ Nhạc năng lượng" to "nhạc upbeat tạo động lực"
  )
  AlertDialog.Builder(this).setTitle("♫ YouTube Mix").setItems(moods.map{it.first}.toTypedArray()){_,which->
   val selected=moods[which]
   val url="https://music.youtube.com/search?q="+Uri.encode(selected.second)
   val title=selected.first
   val existing=tracks.firstOrNull{it.youtubeUrl==url}
   if(existing!=null){
    Toast.makeText(this,"Mix này đã có trong danh sách.",Toast.LENGTH_SHORT).show()
    playTrack(existing)
   }else{
    tracks.add(0,Track(null,title,youtubeUrl=url))
    saveYoutubeTracks()
    renderList(search.text.toString())
    Toast.makeText(this,"Đã thêm mix “"+title+"”. Chạm bài để mở kết quả YouTube.",Toast.LENGTH_LONG).show()
    playTrack(tracks.first())
   }
  }.setNegativeButton("Đóng",null).show()
 }
 private fun addYoutubeMusic(){
  val field=EditText(this).apply{hint="Tên bài hát hoặc dán liên kết YouTube";setSingleLine(false);maxLines=3;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setPadding(dp(12),dp(10),dp(12),dp(10));background=rounded(Color.rgb(16,25,45),12)}
  AlertDialog.Builder(this).setTitle("▶ Thêm nhạc từ YouTube").setMessage("Nhập tên bài hát để tìm trên YouTube, hoặc dán liên kết YouTube. Bài sẽ được lưu vào danh sách và mở bằng ứng dụng YouTube/YouTube Music; ứng dụng không tải hay trích xuất âm thanh từ YouTube.").setView(field).setPositiveButton("Thêm"){_,_->
   val value=field.text.toString().trim()
   if(value.isBlank()){Toast.makeText(this,"Hãy nhập tên bài hát hoặc liên kết.",Toast.LENGTH_SHORT).show();return@setPositiveButton}
   val looksLikeUrl=value.startsWith("https://",true)||value.startsWith("http://",true)
   val url:String
   val title:String
   if(looksLikeUrl){
    val parsed=try{Uri.parse(value)}catch(_:Exception){null}
    val host=parsed?.host?.lowercase(Locale.ROOT)
    if(parsed==null||parsed.scheme!="https"||host !in setOf("youtube.com","www.youtube.com","music.youtube.com","youtu.be")){
     Toast.makeText(this,"Liên kết chưa hợp lệ. Hãy dùng liên kết HTTPS từ youtube.com, music.youtube.com hoặc youtu.be.",Toast.LENGTH_LONG).show();return@setPositiveButton
    }
    url=value
    title=parsed.getQueryParameter("v")?.let{ "YouTube • "+it } ?: "YouTube • "+(parsed.lastPathSegment?.takeIf{it.isNotBlank()}?: "Bài đã thêm")
   }else{
    url="https://music.youtube.com/search?q="+Uri.encode(value)
    title="▶ "+value
   }
   if(tracks.any{it.youtubeUrl==url||it.title.equals(title,true)}){
    Toast.makeText(this,"Bài này đã có trong danh sách.",Toast.LENGTH_SHORT).show()
   }else{
    tracks.add(0,Track(null,title,youtubeUrl=url))
    saveYoutubeTracks()
    renderList(search.text.toString())
    Toast.makeText(this,"Đã thêm “"+title+"” vào danh sách. Chạm bài để mở YouTube.",Toast.LENGTH_LONG).show()
   }
  }.setNegativeButton("Hủy",null).show()
 }
 private fun chooseAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
 private fun chooseAudioForTrack(t:Track){pendingYoutubeAudio=t;Toast.makeText(this,"Chọn tệp âm thanh của bài này để phát trực tiếp trong Music App.",Toast.LENGTH_LONG).show();startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";addCategory(Intent.CATEGORY_OPENABLE)},103)}
 private fun loadSavedYoutubeTracks(){
  val saved=try{JSONArray(prefs.getString("saved_youtube_tracks","[]").orEmpty())}catch(_:Exception){JSONArray()}
  for(i in 0 until saved.length()){
   val item=saved.optJSONObject(i)?:continue
   val title=item.optString("title").trim()
   val url=item.optString("url").trim()
   val parsed=try{Uri.parse(url)}catch(_:Exception){null}
   val host=parsed?.host?.lowercase(Locale.ROOT)
   if(title.isBlank()||parsed?.scheme!="https"||host !in setOf("youtube.com","www.youtube.com","music.youtube.com","youtu.be"))continue
   if(tracks.none{it.youtubeUrl==url})tracks.add(Track(null,title,youtubeUrl=url))
  }
 }
 private fun saveYoutubeTracks(){
  val saved=JSONArray()
  tracks.filter{it.youtubeUrl!=null}.forEach{t->saved.put(JSONObject().put("title",t.title).put("url",t.youtubeUrl))}
  prefs.edit().putString("saved_youtube_tracks",saved.toString()).apply()
 }
 private fun showYoutubeTrackMenu(t:Track){
  val options=arrayOf("▶ Mở trên YouTube","✦ Chi tiết bài hát","🗑 Xóa khỏi danh sách")
  AlertDialog.Builder(this).setTitle(t.title).setItems(options){_,which->
   when(which){
    0->playTrack(t)
    1->showTrackDetails(t)
    2->AlertDialog.Builder(this).setTitle("Xóa bài hát?").setMessage("Xóa “"+t.title+"” khỏi danh sách YouTube trong ứng dụng?").setNegativeButton("Hủy",null).setPositiveButton("Xóa"){_,_->
     tracks.remove(t)
     if(currentIndex>=tracks.size)currentIndex=tracks.lastIndex
     prefs.edit().remove(audioPreferenceKey(t)).remove("fav_"+t.title).apply()
     saveYoutubeTracks()
     renderList(search.text.toString())
     Toast.makeText(this,"Đã xóa bài YouTube khỏi danh sách.",Toast.LENGTH_SHORT).show()
    }.show()
   }
  }.show()
 }
 private fun audioPreferenceKey(t:Track):String="youtube_audio_"+(Uri.parse(t.youtubeUrl).getQueryParameter("v")?:t.title.hashCode().toString())
 @Deprecated("Compatibility callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==104){if(c==Activity.RESULT_OK){val said=d?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty();if(said.isNotBlank()){activeAiInput?.let{it.setText(said);it.setSelection(it.text.length)};Toast.makeText(this,if(activeAiInput!=null)"Đã nhận giọng nói. Nhấn gửi để hỏi AI." else "Đã nhận: $said. Mở AI để gửi câu hỏi.",Toast.LENGTH_SHORT).show()}};return};if(r==102&&c==Activity.RESULT_OK&&d?.data!=null){try{contentResolver.openOutputStream(d.data!!)?.use{it.write(pendingExportText.toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Đã xuất danh sách nhạc.",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"Không thể xuất danh sách: "+(e.message?:"lỗi"),Toast.LENGTH_LONG).show()};return};if(r==103){val target=pendingYoutubeAudio;pendingYoutubeAudio=null;if(c==Activity.RESULT_OK&&d?.data!=null&&target!=null){val u=d.data!!;try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};target.uri=u;prefs.edit().putString(audioPreferenceKey(target),u.toString()).apply();renderList(search.text.toString());Toast.makeText(this,"Đã gắn âm thanh vào “"+target.title+"”. Bài sẽ phát trong app, không mở YouTube Music.",Toast.LENGTH_LONG).show();playTrack(target)};return};if(r!=100||c!=Activity.RESULT_OK||d==null)return;val us=mutableListOf<Uri>();d.data?.let{us.add(it)};d.clipData?.let{x->for(i in 0 until x.itemCount)us.add(x.getItemAt(i).uri)};us.distinct().forEach{u->if(tracks.none{it.uri==u}){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};tracks.add(Track(u,getFileName(u)))}};renderList(search.text.toString())}
 private fun getFileName(u:Uri):String{var n:String?=null;contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)};return n?:"Bài hát"}
 private fun renderList(qs:String){
  if(destroyed||!::list.isInitialized||!::search.isInitialized)return
  list.removeAllViews();val q=qs.trim().lowercase(Locale.getDefault());val visible=tracks.filter{(!favoritesOnly||it.favorite)&&it.title.lowercase(Locale.getDefault()).contains(q)}
  if(visible.isEmpty()){list.addView(TextView(this).apply{text="🌙\n\nChưa có bài phù hợp";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.rgb(125,145,180));setPadding(0,dp(28),0,dp(28))});return}
  visible.forEach{tr->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(7),dp(6));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(11,22,43),Color.rgb(17,17,36))).apply{cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(25,52,90))}}
   val icon=TextView(this).apply{text=when{tr.title.startsWith("🌙")->"🌕";tr.title.startsWith("☁️")->"☁";tr.streamUrl!=null->"🎤";tr.youtubeUrl!=null->"▶";else->"♫"};textSize=23f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(22,36,66),15)}
   row.addView(icon,LinearLayout.LayoutParams(dp(50),dp(50)).apply{rightMargin=dp(9)});icon.setOnClickListener{playTrack(tr)}
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(62),1f)}
   box.addView(TextView(this).apply{text=tr.title;textSize=14f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   box.addView(TextView(this).apply{text=when{tr.streamUrl!=null->"Nhạc tiếng Việt • Phát trực tuyến miễn phí";tr.youtubeUrl!=null&&tr.uri!=null->"Âm thanh từ thiết bị • Phát trong app";tr.youtubeUrl!=null->"YouTube • Chạm để mở bài hát bên ngoài app";tr.melody!=null->"Nhạc ngủ / Chill • Tích hợp";else->"Từ thiết bị"};textSize=10f;setTextColor(Color.rgb(110,145,190));setPadding(0,dp(3),0,0)})
   row.addView(box);box.setOnClickListener{playTrack(tr)};box.setOnLongClickListener{if(tr.youtubeUrl!=null)showYoutubeTrackMenu(tr) else showTrackDetails(tr);true}
   val f=button(if(tr.favorite)"♥" else "♡");f.textSize=19f;f.background=rounded(Color.rgb(18,30,56),18);f.setOnClickListener{tr.favorite=!tr.favorite;saveFavorite(tr);renderList(search.text.toString())};row.addView(f,LinearLayout.LayoutParams(dp(46),dp(50)))
   list.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(7)})
  }
 }
 private fun applyCoverArtwork(t:Track){
  val uri=t.uri ?: return
  val expectedTitle=t.title
  Thread{
   var retriever:android.media.MediaMetadataRetriever?=null
   val bitmap=try{retriever=android.media.MediaMetadataRetriever();retriever!!.setDataSource(this,uri);val bytes=retriever!!.embeddedPicture;if(bytes!=null)BitmapFactory.decodeByteArray(bytes,0,bytes.size)else null}catch(_:Exception){null}finally{try{retriever?.release()}catch(_:Exception){}}
   runOnUiThread{
    if(!destroyed&&currentIndex in tracks.indices&&tracks[currentIndex].title==expectedTitle&&bitmap!=null){coverIcon.text="";coverIcon.background=BitmapDrawable(resources,bitmap).apply{gravity=Gravity.FILL}
    }
   }
  }.start()
 }
 private fun stopCurrentPlayback(cancelSleepTimer:Boolean=true){
  generation.incrementAndGet();stopBuiltin=true;builtinPaused=false;playerPreparing=false
  if(cancelSleepTimer){sleepTimer?.cancel();sleepTimer=null}
  synchronized(audioLock){val a=audioTrack;audioTrack=null;if(a!=null){try{a.stop()}catch(_:Exception){};try{a.release()}catch(_:Exception){}}}
  try{equalizer?.release()}catch(_:Exception){};equalizer=null
  val p=player;player=null;if(p!=null){try{p.setOnPreparedListener(null)}catch(_:Exception){};try{p.setOnCompletionListener(null)}catch(_:Exception){};try{p.setOnErrorListener(null)}catch(_:Exception){};try{p.stop()}catch(_:Exception){};try{p.release()}catch(_:Exception){}}
  if(!destroyed&&::playButton.isInitialized)playButton.text="▶"
 }
 private fun showTrackDetails(t:Track){
  if(destroyed||isFinishing)return
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
  val safeHost=host=="music.youtube.com"||host=="youtube.com"||host=="www.youtube.com"||host=="youtu.be"
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
 private fun formatTime(ms:Int):String{
  val total=(ms.coerceAtLeast(0)/1000)
  return String.format(Locale.US,"%02d:%02d",total/60,total%60)
 }
 private val progressUpdate=object:Runnable{
  override fun run(){
   if(destroyed)return
   val mp=player
   if(mp!=null)try{
    val duration=mp.duration.coerceAtLeast(0)
    val position=mp.currentPosition.coerceAtLeast(0)
    if(duration>0){
     if(!progressBar.isPressed)progressBar.progress=((position.toLong()*1000L)/duration).toInt().coerceIn(0,1000)
     elapsedLabel.text=formatTime(position)
     durationLabel.text=formatTime(duration)
    }
   }catch(_:Exception){}
   progressHandler.postDelayed(this,500)
  }
 }
 private fun playTrack(t:Track){
  if(destroyed||isFinishing)return
  if(!tracks.contains(t)){Toast.makeText(this,"Bài hát không còn trong thư viện.",Toast.LENGTH_SHORT).show();return}
  currentIndex=tracks.indexOf(t)
  recordPlay(t.title)
  coverIcon.text=when{t.title.startsWith("🌙")->"🌙";t.title.startsWith("☁️")->"☁️";t.streamUrl!=null->"🎤";t.youtubeUrl!=null->"🎧";t.uri!=null->"🎵";else->"♫"}
  coverIcon.background=rounded(when{t.title.startsWith("🌙")->Color.rgb(29,48,88);t.title.startsWith("☁️")->Color.rgb(28,54,72);t.streamUrl!=null->Color.rgb(67,39,73);else->Color.rgb(23,37,67)},17)
  applyCoverArtwork(t)
  saveHistory(t.title)
  // Changing tracks must not cancel the user's sleep timer.
  stopCurrentPlayback(cancelSleepTimer=false)
  progressBar.progress=0
  elapsedLabel.text="00:00"
  durationLabel.text="00:00"
  progressBar.isEnabled=false
  if(t.youtubeUrl!=null&&t.uri==null){nowPlaying.text="🎧 Đang mở YouTube: "+t.title;playButton.text="▶";progressBar.isEnabled=false;openYoutube(t.youtubeUrl);return}
  if(t.melody!=null){playBuiltin(t);return}
  val u=t.uri
  if(u==null&&t.streamUrl==null){nowPlaying.text="Bài này chưa có tệp âm thanh.";playButton.text="▶";progressBar.isEnabled=false;return}
  if(t.streamUrl!=null){
   val requestGeneration=generation.get()
   nowPlaying.text="Đang tải nhạc trực tuyến: "+t.title
   Thread{
    var connection:HttpURLConnection?=null
    var cachedFile:java.io.File?=null
    try{
     val conn=(URL(t.streamUrl!!).openConnection() as HttpURLConnection).apply{
      connectTimeout=15000
      readTimeout=20000
      instanceFollowRedirects=true
      requestMethod="GET"
      setRequestProperty("User-Agent","MusicApp/1.0 (Android)")
     }
     connection=conn
     conn.connect()
     val status=conn.responseCode
     if(status !in 200..299)throw java.io.IOException("Máy chủ trả về HTTP $status")
     val downloadFile=java.io.File(cacheDir,"music_stream_${System.nanoTime()}.cache")
     cachedFile=downloadFile
     var totalBytes=0L
     conn.inputStream.use{input->
      downloadFile.outputStream().use{output->
       val buffer=ByteArray(8192)
       while(true){
        if(requestGeneration!=generation.get())throw java.io.IOException("Đã hủy tải")
        val count=input.read(buffer)
        if(count<0)break
        totalBytes+=count
        if(totalBytes>80L*1024L*1024L)throw java.io.IOException("Tệp nhạc trực tuyến quá lớn")
        output.write(buffer,0,count)
       }
      }
     }
     if(downloadFile.length()<=0L)throw java.io.IOException("Nguồn nhạc trống")
     val readyFile=downloadFile
     runOnUiThread{
      if(destroyed||isFinishing||requestGeneration!=generation.get()){
       readyFile.delete()
       return@runOnUiThread
      }
      startTrackMedia(t,readyFile,requestGeneration)
     }
    }catch(e:Exception){
     cachedFile?.delete()
     runOnUiThread{
      if(!destroyed&&!isFinishing&&requestGeneration==generation.get()){
       playerPreparing=false
       progressBar.isEnabled=false
       playButton.text="▶"
       nowPlaying.text="Không tải được nguồn nhạc: "+(e.message?:"lỗi kết nối")
       Toast.makeText(this,"Không tải được bài này. Hãy kiểm tra Internet rồi thử lại.",Toast.LENGTH_LONG).show()
      }
     }
    }finally{connection?.disconnect()}
   }.start()
   return
  }
  startTrackMedia(t,null,generation.get())
 }
 private fun startTrackMedia(t:Track,sourceFile:java.io.File?,expectedGeneration:Int){
  if(destroyed||isFinishing||expectedGeneration!=generation.get()){
   sourceFile?.delete()
   return
  }
  try{
   val mp=MediaPlayer()
   playerPreparing=true
   mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
   if(sourceFile!=null)mp.setDataSource(sourceFile.absolutePath)
   else mp.setDataSource(this,t.uri!!)
   mp.setOnPreparedListener{prepared->
    if(player!==prepared||destroyed||expectedGeneration!=generation.get()){
     try{prepared.release()}catch(_:Exception){}
     sourceFile?.delete()
     return@setOnPreparedListener
    }
    playerPreparing=false
    try{
     if(android.os.Build.VERSION.SDK_INT>=23){
      try{prepared.playbackParams=prepared.playbackParams.setSpeed(prefs.getFloat("playback_speed",1.0f).coerceIn(0.5f,2.0f))}catch(_:Exception){}
     }
     prepared.start()
     nowPlaying.text="Đang phát: "+t.title
     playButton.text="⏸"
     progressBar.isEnabled=true
     progressBar.progress=0
     elapsedLabel.text="00:00"
     durationLabel.text=formatTime(prepared.duration)
     applySavedEqualizer(prepared)
    }catch(e:Exception){
     player=null
     playerPreparing=false
     progressBar.isEnabled=false
     nowPlaying.text="Không thể bắt đầu phát bài này: "+(e.message?:"lỗi thiết bị")
     playButton.text="▶"
     Toast.makeText(this,"Không phát được bài này. Hãy thử lại.",Toast.LENGTH_LONG).show()
     try{prepared.release()}catch(_:Exception){}
     sourceFile?.delete()
    }
   }
   mp.setOnCompletionListener{
    if(player===mp){
     player=null
     playerPreparing=false
     progressBar.isEnabled=false
     try{mp.release()}catch(_:Exception){}
     sourceFile?.delete()
     if(!destroyed){if(repeatOne)playTrack(t) else next()}
    }
   }
   mp.setOnErrorListener{_,what,extra->
    if(player===mp){
     player=null
     playerPreparing=false
     progressBar.isEnabled=false
     nowPlaying.text="Không phát được âm thanh (lỗi $what/$extra)"
     playButton.text="▶"
     Toast.makeText(this,"Nguồn âm thanh không đọc được (lỗi $what/$extra).",Toast.LENGTH_LONG).show()
     try{mp.release()}catch(_:Exception){}
     sourceFile?.delete()
    }
    true
   }
   player=mp
   mp.prepareAsync()
   nowPlaying.text="Đang chuẩn bị: "+t.title
  }catch(e:Exception){
   player?.let{try{it.release()}catch(_:Exception){}}
   player=null
   playerPreparing=false
   progressBar.isEnabled=false
   sourceFile?.delete()
   if(!destroyed){nowPlaying.text="Không thể mở bài này: "+(e.message?:"lỗi");playButton.text="▶"}
  }
 }
 private fun playBuiltin(t:Track){
  val g=generation.get();stopBuiltin=false;builtinPaused=false;progressBar.isEnabled=false;progressBar.progress=0;elapsedLabel.text="00:00";durationLabel.text="—";nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"
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
  val p=player;if(p!=null){if(playerPreparing){Toast.makeText(this,"Bài hát đang tải, chờ một chút nhé.",Toast.LENGTH_SHORT).show();return};try{if(p.isPlaying){p.pause();playButton.text="▶"}else{p.start();playButton.text="⏸"}}catch(_:Exception){player=null;playerPreparing=false;playButton.text="▶";nowPlaying.text="Không thể tiếp tục bài hát"}}
 }
 private fun previous(){val queue=activePlaylist;if(queue!=null&&queue.isNotEmpty()){playlistPosition=if(playlistPosition<=0)queue.lastIndex else playlistPosition-1;playTrack(queue[playlistPosition]);return};if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<=0)tracks.lastIndex else currentIndex-1])}
 private fun next(){val queue=activePlaylist;if(queue!=null&&queue.isNotEmpty()){if(shuffle){val choices=queue.indices.filter{it!=playlistPosition};playlistPosition=if(choices.isNotEmpty())choices[java.util.Random().nextInt(choices.size)] else 0}else{playlistPosition=if(repeatOne)playlistPosition else (playlistPosition+1)%queue.size};playTrack(queue[playlistPosition]);return};if(tracks.isNotEmpty()){val idx:Int;if(shuffle){val choices=tracks.indices.filter{it!=currentIndex};idx=if(choices.isNotEmpty())choices[java.util.Random().nextInt(choices.size)] else 0}else{idx=if(currentIndex<0||currentIndex>=tracks.lastIndex)0 else currentIndex+1};playTrack(tracks[idx])}}
 override fun onDestroy(){destroyed=true;progressHandler.removeCallbacks(progressUpdate);sleepTimer?.cancel();sleepTimer=null;stopCurrentPlayback();tts?.stop();tts?.shutdown();tts=null;super.onDestroy()}
}