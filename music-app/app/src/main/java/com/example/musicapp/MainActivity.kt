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
import kotlin.math.PI
import kotlin.math.sin

data class Track(val uri:Uri?,val title:String,var favorite:Boolean=false,val melody:List<Pair<Double,Int>>?=null,val youtubeUrl:String?=null)

class MainActivity:AppCompatActivity(){
 private val tracks=mutableListOf<Track>();private var currentIndex=-1;private var player:MediaPlayer?=null;private var audioTrack:AudioTrack?=null
 private val generation=AtomicInteger(0);private val audioLock=Any();@Volatile private var stopBuiltin=false
 @Volatile private var builtinPaused=false
 @Volatile private var destroyed=false
 private lateinit var list:LinearLayout;private lateinit var nowPlaying:TextView;private lateinit var playButton:Button;private lateinit var search:EditText;private var favoritesOnly=false
 private var sleepTimer: android.os.CountDownTimer?=null
 private val prefs by lazy{getSharedPreferences("music_app",MODE_PRIVATE)}
 private var isOwner=false
 private var aiUnlocked=false
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
  Track(null,"▶️ Bài YouTube mới",youtubeUrl="https://music.youtube.com/watch?v=liTfD88dbCo")
 )
 private fun melody(vararg n:Double)=n.map{it to 650}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun rounded(c:Int,r:Int=18)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
 private fun button(t:String)=Button(this).apply{text=t;isAllCaps=false;textSize=14f;setTextColor(Color.WHITE);background=rounded(Color.rgb(38,44,58),16)}
 override fun onCreate(b:Bundle?){super.onCreate(b);tracks.addAll(builtIn.map{it.copy()});loadFavorites();tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val vi=Locale("vi","VN");val result=tts?.setLanguage(vi)?:TextToSpeech.LANG_NOT_SUPPORTED;if(result==TextToSpeech.LANG_NOT_SUPPORTED||result==TextToSpeech.LANG_MISSING_DATA){runOnUiThread{Toast.makeText(this,"⚠️ Máy chưa có dữ liệu giọng nói tiếng Việt. Hãy cài Google Text-to-Speech.",Toast.LENGTH_LONG).show()}};tts?.setSpeechRate(0.95f)}else{runOnUiThread{Toast.makeText(this,"⚠️ Không khởi tạo được giọng nói.",Toast.LENGTH_LONG).show()}}};buildUi();renderList("");showWelcomeIfNeeded()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(10));setBackgroundResource(com.example.musicapp.R.drawable.moon_background)}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  top.addView(TextView(this).apply{text="♫";textSize=28f;setTextColor(Color.rgb(90,190,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(48)))
  val titleBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(52),1f)}
  titleBox.addView(TextView(this).apply{text="Music";textSize=28f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE)})
  titleBox.addView(TextView(this).apply{text="Âm nhạc cho tâm hồn";textSize=12f;setTextColor(Color.rgb(145,170,205))})
  top.addView(titleBox)
  top.addView(TextView(this).apply{text="⌕";textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(42),dp(48)))
  top.addView(TextView(this).apply{text="⚙";textSize=23f;setTextColor(Color.rgb(180,195,220));gravity=Gravity.CENTER;setOnClickListener{showSettings()}},LinearLayout.LayoutParams(dp(42),dp(48)))
  top.addView(TextView(this).apply{text="👑";textSize=23f;setTextColor(Color.rgb(255,215,80));gravity=Gravity.CENTER;setOnClickListener{showOwnerLogin()}},LinearLayout.LayoutParams(dp(42),dp(48)))
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
  actions.addView(action("Thêm nhạc","♫"){chooseAudio()},LinearLayout.LayoutParams(0,dp(62),1f))
  actions.addView(action("Yêu thích","♥"){favoritesOnly=!favoritesOnly;renderList(search.text.toString())},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Nhạc ngủ","☾"){search.setText("Ru ngủ")},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("YouTube","▶"){tracks.firstOrNull{it.youtubeUrl!=null}?.let{playTrack(it)}},LinearLayout.LayoutParams(0,dp(62),1f).apply{leftMargin=dp(7)})
  actions.addView(action("Bản đồ","🗺️"){openMap()},LinearLayout.LayoutParams(dp(92),dp(62)).apply{leftMargin=dp(7)})
  actions.addView(action("AI","🤖"){showAiAssistantGate()},LinearLayout.LayoutParams(dp(82),dp(62)).apply{leftMargin=dp(7)})
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
  val info="Music App 1.0\n\n✓ Nhạc tích hợp\n✓ Nhạc từ thiết bị\n✓ Yêu thích được lưu trên máy\n✓ Hẹn giờ ngủ\n✓ YouTube mở bằng HTTPS an toàn\n✓ AI offline, không cần API key\n✓ Không dùng cleartext network\n✓ Không yêu cầu quyền đọc bộ nhớ\n\nMã chủ/AI được kiểm tra bằng hash trong ứng dụng; đây là bảo vệ cục bộ, không phải xác thực máy chủ."
  AlertDialog.Builder(this).setTitle("⚙ Cài đặt & Bảo mật").setMessage(info).setPositiveButton("OK",null).show()
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
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(2),dp(4),dp(2))}
  val chat=TextView(this).apply{text="🤖 AI offline: Xin chào! Mình có thể giúp giải thích Toán, Văn, Anh, Khoa học và lập trình. Không cần API key.\n\n";textSize=14f;setTextColor(Color.WHITE);setPadding(dp(10),dp(10),dp(10),dp(10));background=rounded(Color.rgb(10,18,38),16)}
  val scroll=ScrollView(this).apply{addView(chat)};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val input=EditText(this).apply{hint="Nhập câu hỏi...";setSingleLine(false);maxLines=3;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);background=rounded(Color.rgb(16,27,52),16);setPadding(dp(12),dp(8),dp(12),dp(8))}
  val send=button("Gửi").apply{background=rounded(Color.rgb(36,118,225),18)}
  val speak=button("🔊 Nói").apply{background=rounded(Color.rgb(24,75,110),18)}
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(input,LinearLayout.LayoutParams(0,dp(54),1f));row.addView(send,LinearLayout.LayoutParams(dp(70),dp(54)).apply{leftMargin=dp(7)});row.addView(speak,LinearLayout.LayoutParams(dp(75),dp(54)).apply{leftMargin=dp(7)})
  panel.addView(row,LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(7)})
  val dialog=AlertDialog.Builder(this).setTitle("🤖 AI Giảng Dạy Offline").setView(panel).setNegativeButton("Đóng",null).create()
  var lastAnswer="Xin chào! Bạn hãy nhập câu hỏi."
  fun append(role:String,text:String){chat.append((if(role=="user")"\n👤 Bạn: " else "\n🤖 AI: ")+text+"\n");scroll.post{scroll.fullScroll(ScrollView.FOCUS_DOWN)}}
  fun ask(){val q=input.text.toString().trim();if(q.isEmpty())return;input.setText("");append("user",q);send.isEnabled=false;send.text="…";Thread{val answer=offlineTutor(q);runOnUiThread{lastAnswer=answer;send.isEnabled=true;send.text="Gửi";append("assistant",answer);speakAi(answer)}}}
  send.setOnClickListener{ask()};speak.setOnClickListener{if(lastAnswer.isNotBlank())speakAi(lastAnswer)};input.setOnEditorActionListener{_,_,_->ask();true};dialog.setOnDismissListener{tts?.stop()};dialog.show()
 }
 private fun offlineTutor(q:String):String{
  val s=q.lowercase(Locale.getDefault()).trim()
  return when{
   s.contains("2+2")||s.contains("2 + 2")->"2 cộng 2 bằng 4."
   s.contains("đạo hàm")||s.contains("dao ham")->"Đạo hàm mô tả tốc độ thay đổi của hàm số. Ví dụ, nếu f của x bằng x bình phương thì đạo hàm là 2x."
   s.contains("phân số")||s.contains("phan so")->"Muốn cộng hai phân số, hãy quy đồng mẫu số, cộng các tử số rồi rút gọn kết quả."
   s.contains("diện tích")||s.contains("dien tich")->"Diện tích hình chữ nhật bằng chiều dài nhân chiều rộng. Diện tích tam giác bằng đáy nhân chiều cao rồi chia 2."
   s.contains("ngữ văn")||s.contains("ngu van")||s.contains("biện pháp tu từ")||s.contains("bien phap tu tu")->"Mình có thể giúp phân tích nhân vật, chủ đề, biện pháp tu từ, người kể chuyện và lập dàn ý. Hãy gửi đề bài cụ thể."
   s.contains("tiếng anh")||s.contains("tieng anh")||s.contains("english")->"Mình có thể giải thích từ vựng, ngữ pháp, các thì và cách làm bài tiếng Anh. Hãy gửi câu cụ thể."
   s.contains("python")||s.contains("lập trình")||s.contains("lap trinh")->"Mình có thể giải thích Python và lập trình từng bước. Hãy gửi đoạn code hoặc mô tả bài toán."
   s.contains("hóa học")||s.contains("hoa hoc")||s.contains("sinh học")||s.contains("sinh hoc")||s.contains("vật lý")||s.contains("vat ly")->"Mình có thể giải thích kiến thức Khoa học và hướng dẫn bài tập từng bước. Hãy gửi đề bài cụ thể."
   s.contains("xin chào")||s.contains("xin chao")||s=="hello"||s=="hi"||s=="chào"||s=="chao"->"Xin chào! Bạn muốn học môn nào hôm nay?"
   else->"Mình đang chạy offline nên chưa có kiến thức như ChatGPT đầy đủ. Hãy gửi một câu hỏi cụ thể về Toán, Văn, Anh, Khoa học hoặc lập trình; mình sẽ hướng dẫn trong phạm vi kiến thức tích hợp."
  }
 }
 private fun openMap(){
  val uri=Uri.parse("geo:0,0?q=bản đồ")
  try{startActivity(Intent(Intent.ACTION_VIEW,uri))}
  catch(_:Exception){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps")))}catch(_:Exception){nowPlaying.text="Không mở được Bản đồ"}}
 }
 private fun chooseAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
 @Deprecated("Compatibility callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=100||c!=Activity.RESULT_OK||d==null)return;val us=mutableListOf<Uri>();d.data?.let{us.add(it)};d.clipData?.let{x->for(i in 0 until x.itemCount)us.add(x.getItemAt(i).uri)};us.distinct().forEach{u->if(tracks.none{it.uri==u}){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};tracks.add(Track(u,getFileName(u)))}};renderList(search.text.toString())}
 private fun getFileName(u:Uri):String{var n:String?=null;contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)};return n?:"Bài hát"}
 private fun renderList(qs:String){
  list.removeAllViews();val q=qs.trim().lowercase(Locale.getDefault());val visible=tracks.filter{(!favoritesOnly||it.favorite)&&it.title.lowercase(Locale.getDefault()).contains(q)}
  if(visible.isEmpty()){list.addView(TextView(this).apply{text="🌙\n\nChưa có bài phù hợp";textSize=15f;gravity=Gravity.CENTER;setTextColor(Color.rgb(125,145,180));setPadding(0,dp(28),0,dp(28))});return}
  visible.forEach{tr->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(7),dp(6));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(11,22,43),Color.rgb(17,17,36))).apply{cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(25,52,90))}}
   val icon=TextView(this).apply{text=when{tr.title.startsWith("🌙")->"🌕";tr.title.startsWith("☁️")->"☁";tr.youtubeUrl!=null->"▶";else->"♫"};textSize=23f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(22,36,66),15)}
   row.addView(icon,LinearLayout.LayoutParams(dp(50),dp(50)).apply{rightMargin=dp(9)});icon.setOnClickListener{playTrack(tr)}
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,dp(62),1f)}
   box.addView(TextView(this).apply{text=tr.title;textSize=14f;setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
   box.addView(TextView(this).apply{text=when{tr.youtubeUrl!=null->"YouTube Music • Chỉ nghe nhạc";tr.melody!=null->"Nhạc ngủ / Chill • Tích hợp";else->"Từ thiết bị"};textSize=10f;setTextColor(Color.rgb(110,145,190));setPadding(0,dp(3),0,0)})
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
   t.melody!=null -> "🎵 "+t.title+"\n\n• Loại: nhạc nền tích hợp trong Music.\n• Không khí: nhẹ nhàng, phù hợp thư giãn hoặc nghe trước khi ngủ.\n• Điểm nên chú ý: giai điệu lặp êm, nhịp ổn định và âm lượng đều.\n• Gợi ý: nghe ở âm lượng vừa và thử tập trung vào từng lớp giai điệu."
   t.youtubeUrl!=null -> "🎧 "+t.title+"\n\n• Nguồn: liên kết YouTube Music.\n• Ứng dụng chỉ mở nguồn nghe, không tải hoặc nhúng bản thu.\n• Gợi ý: có thể dùng tai nghe ở âm lượng vừa để cảm nhận rõ nhịp và không gian âm thanh."
   t.uri!=null -> "🎵 "+t.title+"\n\n• Nguồn: tệp nhạc từ thiết bị của bạn.\n• Có thể phát trực tiếp trong thư viện Music.\n• Gợi ý: thử chú ý đến nhịp, giai điệu, giọng hát và các lớp âm thanh."
   else -> "🎵 "+t.title+"\n\nĐây là bài nhạc trong thư viện Music. Hãy nghe ở âm lượng vừa và thử tập trung vào nhịp, giai điệu và cảm xúc của bài."
  }
  AlertDialog.Builder(this).setTitle("✦ Chi tiết sâu hơn").setMessage(message).setPositiveButton("Nghe ngay"){_,_->playTrack(t)}.setNegativeButton("Đóng",null).show()
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
  currentIndex=tracks.indexOf(t);stopCurrentPlayback()
  if(t.youtubeUrl!=null){openYoutube(t.youtubeUrl);return}
  if(t.melody!=null){playBuiltin(t);return}
  val u=t.uri?:return
  try{val mp=MediaPlayer();mp.setDataSource(this,u);mp.setOnPreparedListener{if(player!==mp){try{mp.release()}catch(_:Exception){};return@setOnPreparedListener};mp.start();nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"};mp.setOnCompletionListener{if(player===mp)next()};mp.setOnErrorListener{_,_,_->if(player===mp){player=null;nowPlaying.text="Không phát được tệp này";playButton.text="▶";try{mp.release()}catch(_:Exception){}};true};player=mp;mp.prepareAsync();nowPlaying.text="Đang tải: "+t.title}catch(_:Exception){player=null;playButton.text="▶"}
 }
 private fun playBuiltin(t:Track){
  val g=generation.get();stopBuiltin=false;builtinPaused=false;nowPlaying.text="Đang phát: "+t.title;playButton.text="⏸"
  Thread{
   val sr=22050;val mb=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
   if(g!=generation.get()||stopBuiltin)return@Thread
   val at=try{AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,mb.coerceAtLeast(4096),AudioTrack.MODE_STREAM)}catch(_:Exception){return@Thread}
   synchronized(audioLock){if(g!=generation.get()||stopBuiltin){try{at.release()}catch(_:Exception){};return@Thread};audioTrack=at}
   var completed=false
   try{at.play();repeat(3){for((freq,dur)in t.melody?:emptyList()){val count=sr*dur/1000;val s=ShortArray(count);var i=0;while(i<count){if(stopBuiltin||g!=generation.get())return@Thread;while(builtinPaused&&!stopBuiltin&&g==generation.get()){try{Thread.sleep(50)}catch(_:Exception){}};if(stopBuiltin||g!=generation.get())return@Thread;val x=i.toDouble()/sr;val fi=(i/(sr*.04)).coerceAtMost(1.0);val fo=((count-i)/(sr*.04)).coerceAtMost(1.0);s[i]=(sin(2*PI*freq*x)*.18*minOf(fi,fo)*Short.MAX_VALUE).toInt().toShort();i++};synchronized(audioLock){if(audioTrack!==at)return@Thread;try{at.write(s,0,s.size)}catch(_:Exception){return@Thread}}}};completed=true}catch(_:Exception){}finally{var owner=false;synchronized(audioLock){if(audioTrack===at){audioTrack=null;owner=true}};if(owner){try{at.stop()}catch(_:Exception){};try{at.release()}catch(_:Exception){}};runOnUiThread{if(g==generation.get()&&!stopBuiltin&&completed&&!destroyed&&!isFinishing){playButton.text="▶";next()}}}
  }.start()
 }
 private fun togglePlay(){
  synchronized(audioLock){audioTrack?.let{a->try{if(a.playState==AudioTrack.PLAYSTATE_PLAYING){builtinPaused=true;a.pause();playButton.text="▶"}else{builtinPaused=false;a.play();playButton.text="⏸"}}catch(_:Exception){playButton.text="▶"};return}}
  val p=player;if(p!=null){try{if(p.isPlaying){p.pause();playButton.text="▶"}else{p.start();playButton.text="⏸"}}catch(_:Exception){player=null;playButton.text="▶";nowPlaying.text="Không thể tiếp tục bài hát"}}
 }
 private fun previous(){if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<=0)tracks.lastIndex else currentIndex-1])}
 private fun next(){if(tracks.isNotEmpty())playTrack(tracks[if(currentIndex<0||currentIndex>=tracks.lastIndex)0 else currentIndex+1])}
 override fun onDestroy(){destroyed=true;sleepTimer?.cancel();sleepTimer=null;stopCurrentPlayback();tts?.stop();tts?.shutdown();tts=null;super.onDestroy()}
}