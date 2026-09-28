package com.baonam.music;
import android.app.*; import android.os.*; import android.media.*; import android.widget.*; import java.util.*;

public class MainActivity extends Activity {
 AudioTrack track; volatile boolean playing;
 int[][] notes={{60,64,67,64,62,65,69,65,60,64,67,72,69,67,64,60},
 {64,67,71,69,67,64,62,65,69,67,65,62,60,64,67,72,69,67,65,64},
 {64,67,69,67,64,62,64,67,72,71,69,67,64,67,69,72,74,72,69,67,64,62,64}};
 int[] bpm={58,84,112};
 public void onCreate(Bundle b){super.onCreate(b); LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(32,40,32,20);
 TextView t=new TextView(this);t.setText("🎵 Nhạc Chill & Ru Ngủ");t.setTextSize(26);l.addView(t);
 String[] n={"🌙 Ru ngủ","🌌 Chill","💜 Neon Dream"};for(int i=0;i<3;i++){final int k=i;Button x=new Button(this);x.setText(n[i]);x.setOnClickListener(v->play(k));l.addView(x);}
 Button s=new Button(this);s.setText("⏹ Dừng");s.setOnClickListener(v->stop());l.addView(s);setContentView(l);}
 void stop(){playing=false;if(track!=null){track.stop();track.release();track=null;}}
 void play(int k){stop();playing=true;new Thread(()->{int sr=44100,buf=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);track=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,buf,AudioTrack.MODE_STREAM);track.play();double beat=60.0/bpm[k];try{while(playing){for(int midi:notes[k]){if(!playing)break;double f=440*Math.pow(2,(midi-69)/12.0);int count=(int)(sr*beat*2);short[] a=new short[count];for(int i=0;i<count;i++){double env=i<300?i/300.0:(i>count-1000?(count-i)/1000.0:1);a[i]=(short)(Math.sin(2*Math.PI*f*i/sr)*8000*env);}track.write(a,0,a.length);}}}finally{if(track!=null){track.stop();track.release();track=null;}}}).start();}
 protected void onDestroy(){stop();super.onDestroy();}
}