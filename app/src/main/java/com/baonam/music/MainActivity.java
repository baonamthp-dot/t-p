package com.baonam.music;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    final ArrayList<Song> songs = new ArrayList<>();
    final HashSet<String> favorites = new HashSet<>();
    SharedPreferences prefs;
    LinearLayout body, miniPlayer;
    MediaPlayer player;
    AudioTrack tonePlayer;
    Thread toneThread;
    volatile boolean stopTone;
    Song current;
    boolean playing = false;
    int page = 0;
    TextView nowTitle, nowArtist, playButton;

    static class Song {
        String title, uri, artist;
        Song(String title, String artist, String uri){this.title=title;this.artist=artist;this.uri=uri;}
    }

    int BG=Color.rgb(4,10,42), CARD=Color.rgb(9,27,76), BLUE=Color.rgb(35,154,255), TEXT=Color.WHITE, MUTED=Color.rgb(156,178,220);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("moon_music",MODE_PRIVATE);
        load();
        addBuiltInSongs();
        build();
        showHome();
    }

    void addBuiltInSongs(){
        if(!songs.isEmpty()){ for(Song s:songs) if(s.uri.startsWith("builtin://")) return; }
        String[][] data={
            {"Vầng Trăng Tròn","Moon Chill","0"},{"Đêm Bình Yên","Moon Chill","1"},{"Ánh Sao Đêm","Moon Chill","2"},
            {"Mưa Tháng Sáu","H2K Demo","3"},{"Chill Cùng Gió","Various Demo","4"},{"Biển Đêm","Ocean Demo","5"},
            {"Mưa Rơi Nhẹ","Sleep Demo","6"},{"Tiếng Sóng Êm","Sleep Demo","7"},{"Rừng Đêm","Nature Demo","8"},
            {"Giai Điệu Bình Yên","Piano Demo","9"},{"Hoàng Hôn","Piano Demo","10"},{"Ngày Mới","Piano Demo","11"},
            {"Nhạc Trẻ Chill","Demo Artist","12"},{"Bolero Đêm","Demo Artist","13"},{"Lofi Ánh Trăng","Demo Artist","14"},
            {"Mộng Mơ","Demo Artist","15"},{"Bình Minh","Demo Artist","16"},{"Thư Giãn","Demo Artist","17"}
        };
        for(String[] d:data)songs.add(new Song(d[0],d[1],"builtin://"+d[2]));
    }

    GradientDrawable bg(int color,float r){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(r); return d;
    }
    TextView tv(String s,float size,int color){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    TextView pill(String s, boolean active){
        TextView t=tv(s,14,active?Color.WHITE:MUTED); t.setGravity(Gravity.CENTER);
        t.setPadding(18,7,18,7); t.setBackground(bg(active?BLUE:Color.rgb(13,31,75),40)); return t;
    }

    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        miniPlayer=new LinearLayout(this); miniPlayer.setOrientation(LinearLayout.HORIZONTAL); miniPlayer.setPadding(12,8,10,8);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL); miniPlayer.setBackground(bg(Color.rgb(7,22,61),28));
        root.addView(miniPlayer,new LinearLayout.LayoutParams(-1,70));
        buildMini();

        LinearLayout nav=new LinearLayout(this); nav.setPadding(8,4,8,6); nav.setGravity(Gravity.CENTER);
        String[] labels={"⌂\nTrang chủ","▣\nThư viện","⌕\nKhám phá","♙\nCá nhân"};
        for(int i=0;i<4;i++){
            final int p=i; TextView n=tv(labels[i],11,i==0?BLUE:MUTED); n.setGravity(Gravity.CENTER);
            n.setOnClickListener(v->{page=p; updateNav(nav); if(p==0)showHome(); else if(p==1)showLibrary(); else showSimple(p==2?"Khám phá":"Cá nhân");});
            nav.addView(n,new LinearLayout.LayoutParams(0,58,1));
        }
        root.addView(nav);
        setContentView(root);
    }

    void updateNav(LinearLayout nav){
        for(int i=0;i<nav.getChildCount();i++) ((TextView)nav.getChildAt(i)).setTextColor(i==page?BLUE:MUTED);
    }

    void buildMini(){
        miniPlayer.removeAllViews();
        TextView art=tv("🌙",25,Color.WHITE); art.setGravity(Gravity.CENTER); art.setBackground(bg(Color.rgb(20,63,130),22));
        miniPlayer.addView(art,new LinearLayout.LayoutParams(54,54));
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(10,0,0,0);
        nowTitle=tv(current==null?"Chưa phát nhạc":current.title,15,TEXT);
        nowArtist=tv(current==null?"Chọn một bài để bắt đầu":current.artist,11,MUTED);
        info.addView(nowTitle); info.addView(nowArtist);
        miniPlayer.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        playButton=tv(playing?"Ⅱ":"▶",25,TEXT); playButton.setGravity(Gravity.CENTER);
        playButton.setOnClickListener(v->{if(player!=null){if(player.isPlaying()){player.pause();playing=false;}else{player.start();playing=true;} updateMini();}});
        miniPlayer.addView(playButton,new LinearLayout.LayoutParams(52,-1));
    }
    void updateMini(){ if(nowTitle!=null){nowTitle.setText(current==null?"Chưa phát nhạc":current.title);nowArtist.setText(current==null?"Chọn một bài để bắt đầu":current.artist);} if(playButton!=null)playButton.setText(playing?"Ⅱ":"▶"); }

    void header(String title,String sub){
        LinearLayout h=new LinearLayout(this); h.setPadding(18,18,18,10); h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);
        TextView a=tv(title,27,TEXT);a.setTypeface(null,1);x.addView(a);
        x.addView(tv(sub,12,MUTED));
        h.addView(x,new LinearLayout.LayoutParams(0,-2,1));
        TextView search=tv("⌕",28,TEXT); h.addView(search,new LinearLayout.LayoutParams(50,55));
        TextView set=tv("⚙",25,TEXT);h.addView(set,new LinearLayout.LayoutParams(45,55)); body.addView(h);
    }

    void showHome(){
        body.removeAllViews(); header("🎵 Music","Âm nhạc cho tâm hồn");
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(22,20,22,20);
        hero.setBackground(bg(Color.rgb(13,39,92),28));
        TextView moon=tv("🌙",60,Color.WHITE);moon.setGravity(Gravity.RIGHT);hero.addView(moon,new LinearLayout.LayoutParams(-1,70));
        TextView ht=tv("Những bản nhạc\nđưa bạn đến\nbình yên... ♥",21,Color.WHITE);hero.addView(ht,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(hero,margin(16,8,16,12));

        LinearLayout quick=new LinearLayout(this);quick.setPadding(14,0,14,0);
        quick.addView(action("🎵","Thêm nhạc",v->pickAudio()),w()); quick.addView(action("♥","Yêu thích",v->showFavorites()),w());
        quick.addView(action("🌙","Nhạc ngủ",v->showSleep()),w()); quick.addView(action("▶","YouTube",v->toast("Mở YouTube")),w());
        body.addView(quick);

        TextView s=tv("Danh sách nổi bật",20,TEXT);s.setTypeface(null,1);s.setPadding(18,18,0,8);body.addView(s);
        LinearLayout cats=new LinearLayout(this);cats.setPadding(14,0,14,0);
        String[] cs={"🎵\nNhạc Trẻ\nHot nhất","💜\nNhạc Buồn\nTâm trạng","🎶\nBolero\nTrữ tình","🌙\nChill\nThư giãn"};
        for(String c:cs){TextView q=tv(c,13,Color.WHITE);q.setGravity(Gravity.CENTER);q.setPadding(5,8,5,8);q.setBackground(bg(Color.rgb(14,42,93),22));cats.addView(q,new LinearLayout.LayoutParams(0,92,1));}
        body.addView(cats);

        TextView p=tv("Playlist của bạn",20,TEXT);p.setTypeface(null,1);p.setPadding(18,18,0,8);body.addView(p);
        addPlaylist("🌙  Nhạc Chill Cực Hay","120 bài • 8 tiếng 12 phút");
        addPlaylist("🎵  Bolero Tuyển Chọn","80 bài • 6 tiếng 45 phút");
    }

    LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}
    LinearLayout.LayoutParams w(){return new LinearLayout.LayoutParams(0,100,1);}
    TextView action(String icon,String name,View.OnClickListener c){TextView t=tv(icon+"\n"+name,13,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(bg(Color.rgb(14,42,94),25));t.setOnClickListener(c);return t;}
    void addPlaylist(String a,String b){LinearLayout r=row(a,b,"▶");body.addView(r,margin(16,3,16,3));}

    LinearLayout row(String title,String sub,String button){
        LinearLayout r=new LinearLayout(this);r.setPadding(12,8,8,8);r.setGravity(Gravity.CENTER_VERTICAL);r.setBackground(bg(CARD,20));
        TextView im=tv("🌌",28,Color.WHITE);im.setGravity(Gravity.CENTER);r.addView(im,new LinearLayout.LayoutParams(55,55));
        LinearLayout inf=new LinearLayout(this);inf.setOrientation(LinearLayout.VERTICAL);inf.setPadding(10,0,0,0);inf.addView(tv(title,15,TEXT));inf.addView(tv(sub,11,MUTED));r.addView(inf,new LinearLayout.LayoutParams(0,-2,1));
        TextView b=tv(button,20,TEXT);b.setGravity(Gravity.CENTER);r.addView(b,new LinearLayout.LayoutParams(50,55));return r;
    }

    void showLibrary(){
        body.removeAllViews();header("Thư viện",songs.size()+" bài hát");
        LinearLayout chips=new LinearLayout(this);chips.setPadding(16,4,10,10);
        for(String s:new String[]{"Tất cả","Bài hát","Playlist","Ca sĩ","Album"})chips.addView(pill(s,s.equals("Tất cả")),new LinearLayout.LayoutParams(-2,44));
        body.addView(chips);
        if(songs.isEmpty()){TextView e=tv("Chưa có bài hát\nNhấn + Thêm nhạc để chọn MP3",17,MUTED);e.setGravity(Gravity.CENTER);body.addView(e,margin(20,80,20,20));return;}
        for(Song s:songs){LinearLayout r=row("🎵  "+s.title,s.artist,favorites.contains(key(s))?"♥":"♡");r.setOnClickListener(v->playSong(s));body.addView(r,margin(14,4,14,4));}
    }

    void showFavorites(){
        body.removeAllViews();header("♥ Yêu thích","Những bài bạn đã lưu");
        for(Song s:songs)if(favorites.contains(key(s))){LinearLayout r=row("🎵  "+s.title,s.artist,"▶");r.setOnClickListener(v->playSong(s));body.addView(r,margin(14,4,14,4));}
    }
    void showSleep(){
        body.removeAllViews();header("🌙 Nhạc ngủ","Thư giãn • Ngủ ngon • Sống chậm");
        String[] a={"Tiếng mưa rơi","Tiếng sóng biển","Tiếng suối chảy","Nhạc không lời nhẹ nhàng","Ru ngủ bình an"};
        for(String s:a){LinearLayout r=row("🌙  "+s,"Thư giãn", "▶");r.setOnClickListener(v->toast("Thêm nhạc MP3 để phát bài này"));body.addView(r,margin(14,4,14,4));}
    }
    void showSimple(String title){body.removeAllViews();header(title,"Moon Music");TextView t=tv("✨ Tính năng đang được xây dựng",18,MUTED);t.setGravity(Gravity.CENTER);body.addView(t,new LinearLayout.LayoutParams(-1,300));}

    void playSong(Song s){
        stop(); current=s;
        if(s.uri.startsWith("builtin://")){
            int pattern=0; try{pattern=Integer.parseInt(s.uri.substring(10));}catch(Exception ignored){}
            playBuiltIn(pattern); return;
        }
        try{
            player=new MediaPlayer();
            player.setDataSource(this,Uri.parse(s.uri));
            player.setOnCompletionListener(mp->{playing=false;updateMini();});
            player.prepare(); player.start(); playing=true; updateMini();
            toast("Đang phát: "+s.title);
        }catch(Exception e){toast("Không phát được bài này");}
    }

    void playBuiltIn(final int pattern){
        final int sampleRate=22050, seconds=28, total=22050*28;
        int min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
        tonePlayer=new AudioTrack(AudioManager.STREAM_MUSIC,sampleRate,AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,min*2,AudioTrack.MODE_STREAM);
        tonePlayer.play(); playing=true; updateMini(); toast("Đang phát: "+current.title);
        stopTone=false;
        toneThread=new Thread(()->{
            short[] buf=new short[1024];
            double[][] scales={{261.63,293.66,329.63,392,440,392,329.63,293.66},{220,261.63,293.66,329.63,392,329.63,293.66,261.63},{329.63,392,440,493.88,523.25,493.88,440,392}};
            double[] scale=scales[pattern%3]; int pos=0;
            while(pos<total && !stopTone){
                int n=Math.min(buf.length,total-pos);
                for(int i=0;i<n;i++){
                    double t=(pos+i)/(double)sampleRate; int note=(int)(t*2)%scale.length; double f=scale[note];
                    double v=Math.sin(2*Math.PI*f*t)*.18+Math.sin(2*Math.PI*(f/2)*t)*.08+Math.sin(2*Math.PI*(f*.5)*t)*.035;
                    double fade=Math.min(1,Math.min(t/.8,(seconds-t)/.8)); buf[i]=(short)(32767*v*fade);
                }
                tonePlayer.write(buf,0,n); pos+=n;
            }
            if(!stopTone){playing=false;runOnUiThread(()->updateMini());}
            try{tonePlayer.stop();}catch(Exception ignored){}
        }); toneThread.start();
    }

    void stop(){
        stopTone=true;
        if(tonePlayer!=null){try{tonePlayer.stop();}catch(Exception ignored){}try{tonePlayer.release();}catch(Exception ignored){}tonePlayer=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
        playing=false;
    }
    void pickAudio(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,100);}
    @Override protected void onActivityResult(int rc,int result,Intent data){super.onActivityResult(rc,result,data);if(rc!=100||result!=RESULT_OK||data==null)return;ClipData c=data.getClipData();if(c!=null)for(int i=0;i<c.getItemCount();i++)addUri(c.getItemAt(i).getUri());else if(data.getData()!=null)addUri(data.getData());save();showHome();}
    void addUri(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}for(Song s:songs)if(u.toString().equals(s.uri))return;songs.add(new Song(name(u),"Thiết bị",u.toString()));}
    String name(Uri u){Cursor c=getContentResolver().query(u,null,null,null,null);if(c!=null)try{int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0&&c.moveToFirst())return c.getString(n);}finally{c.close();}return "Bài hát mới";}
    String key(Song s){return "uri:"+s.uri;}
    void load(){int n=prefs.getInt("n",0);for(int i=0;i<n;i++){String u=prefs.getString("u"+i,null);if(u!=null)songs.add(new Song(prefs.getString("t"+i,"Bài hát"),"Thiết bị",u));}favorites.addAll(prefs.getStringSet("fav",new HashSet<String>()));}
    void save(){SharedPreferences.Editor e=prefs.edit();int local=0;for(Song s:songs)if(!s.uri.startsWith("builtin://"))local++;e.putInt("n",local);int i=0;for(Song s:songs)if(!s.uri.startsWith("builtin://")){e.putString("u"+i,s.uri);e.putString("t"+i,s.title);i++;}e.putStringSet("fav",favorites);e.apply();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){stop();super.onDestroy();}
}
