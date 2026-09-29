package com.baonam.music;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    AudioTrack toneTrack;
    MediaPlayer filePlayer;
    volatile boolean playing;
    final ArrayList<Song> songs = new ArrayList<>();
    final HashSet<String> favorites = new HashSet<>();
    LinearLayout listBox;
    EditText search;
    SharedPreferences prefs;

    int[][] notes = {
        {60,64,67,64,62,65,69,65,60,64,67,72,69,67,64,60},
        {64,67,71,69,67,64,62,65,69,67,65,62,60,64,67,72,69,67,65,64},
        {64,67,69,67,64,62,64,67,72,71,69,67,64,67,69,72,74,72,69,67,64,62,64}
    };
    int[] bpm = {58,84,112};

    static class Song {
        String title, uri;
        int builtIn;
        Song(String title, int builtIn, String uri) { this.title=title; this.builtIn=builtIn; this.uri=uri; }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("music_library", MODE_PRIVATE);
        loadLibrary();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 12);

        TextView title = new TextView(this);
        title.setText("🎵 Music Kho Lưu Trữ");
        title.setTextSize(28);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Nhạc của bạn • Chill • Ru ngủ");
        sub.setTextSize(15);
        root.addView(sub);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        Button add = new Button(this);
        add.setText("＋ Thêm nhạc");
        add.setOnClickListener(v -> pickAudio());
        tools.addView(add, new LinearLayout.LayoutParams(0, -2, 1));

        Button stop = new Button(this);
        stop.setText("⏹ Dừng");
        stop.setOnClickListener(v -> stopAll());
        tools.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(tools);

        search = new EditText(this);
        search.setHint("🔎 Tìm bài hát...");
        search.setSingleLine(true);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ renderList(); }
            public void afterTextChanged(android.text.Editable e){}
        });
        root.addView(search);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView foot = new TextView(this);
        foot.setText("💾 Thư viện được lưu trên thiết bị");
        foot.setTextSize(13);
        root.addView(foot);

        setContentView(root);
        renderList();
    }

    void loadLibrary() {
        songs.add(new Song("🌙 Ru ngủ", 0, null));
        songs.add(new Song("🌌 Chill", 1, null));
        songs.add(new Song("💜 Neon Dream", 2, null));
        int count = prefs.getInt("count", 0);
        for (int i=0;i<count;i++) {
            String uri = prefs.getString("uri_"+i, null);
            String name = prefs.getString("name_"+i, "Bài hát");
            if (uri != null) songs.add(new Song(name, -1, uri));
        }
        String favs = prefs.getString("favorites", "");
        if (!favs.isEmpty()) favorites.addAll(Arrays.asList(favs.split("\\|")));
    }

    void saveLibrary() {
        SharedPreferences.Editor e = prefs.edit();
        int userCount = 0;
        for (Song s : songs) if (s.builtIn < 0) {
            e.putString("uri_"+userCount, s.uri);
            e.putString("name_"+userCount, s.title);
            userCount++;
        }
        e.putInt("count", userCount);
        e.putString("favorites", String.join("|", favorites));
        e.apply();
    }

    void renderList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String q = search == null ? "" : search.getText().toString().toLowerCase();
        for (Song s : songs) {
            if (!s.title.toLowerCase().contains(q)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 12, 8, 12);

            TextView name = new TextView(this);
            name.setText((s.builtIn >= 0 ? "🎵 " : "🎧 ") + s.title);
            name.setTextSize(18);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

            Button fav = new Button(this);
            fav.setText(favorites.contains(key(s)) ? "★" : "☆");
            fav.setOnClickListener(v -> {
                String k = key(s);
                if (favorites.contains(k)) favorites.remove(k); else favorites.add(k);
                saveLibrary();
                renderList();
            });
            row.addView(fav, new LinearLayout.LayoutParams(60, -2));

            Button play = new Button(this);
            play.setText("▶");
            play.setOnClickListener(v -> playSong(s));
            row.addView(play, new LinearLayout.LayoutParams(65, -2));

            listBox.addView(row);
        }
        if (listBox.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Không tìm thấy bài nhạc.");
            empty.setPadding(12, 24, 12, 24);
            listBox.addView(empty);
        }
    }

    String key(Song s) { return s.builtIn >= 0 ? "built:"+s.builtIn : "uri:"+s.uri; }

    void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 100);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 100 || resultCode != RESULT_OK || data == null) return;
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i=0;i<clip.getItemCount();i++) addUri(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) addUri(data.getData());
        saveLibrary();
        renderList();
    }

    void addUri(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        String name = getName(uri);
        for (Song s : songs) if (uri.toString().equals(s.uri)) return;
        songs.add(new Song(name, -1, uri.toString()));
    }

    String getName(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (n >= 0 && c.moveToFirst()) return c.getString(n);
            } finally { c.close(); }
        }
        return "Bài hát mới";
    }

    void stopAll() {
        playing = false;
        if (toneTrack != null) { try { toneTrack.stop(); } catch(Exception ignored){} toneTrack.release(); toneTrack=null; }
        if (filePlayer != null) { try { filePlayer.stop(); } catch(Exception ignored){} filePlayer.release(); filePlayer=null; }
    }

    void playSong(Song s) {
        stopAll();
        if (s.builtIn >= 0) playTone(s.builtIn);
        else playFile(Uri.parse(s.uri));
    }

    void playFile(Uri uri) {
        try {
            filePlayer = new MediaPlayer();
            filePlayer.setDataSource(this, uri);
            filePlayer.setOnCompletionListener(mp -> stopAll());
            filePlayer.prepare();
            filePlayer.start();
            playing = true;
        } catch (Exception e) {
            Toast.makeText(this, "Không phát được bài này", Toast.LENGTH_SHORT).show();
            stopAll();
        }
    }

    void playTone(int k) {
        playing = true;
        new Thread(() -> {
            int sr=44100;
            int buf=AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            toneTrack=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,buf,AudioTrack.MODE_STREAM);
            toneTrack.play();
            double beat=60.0/bpm[k];
            try {
                while(playing) for(int midi:notes[k]) {
                    if(!playing) break;
                    double f=440*Math.pow(2,(midi-69)/12.0);
                    int count=(int)(sr*beat*2);
                    short[] a=new short[count];
                    for(int i=0;i<count;i++) {
                        double env=i<300?i/300.0:(i>count-1000?(count-i)/1000.0:1);
                        a[i]=(short)(Math.sin(2*Math.PI*f*i/sr)*8000*env);
                    }
                    toneTrack.write(a,0,a.length);
                }
            } finally {
                if(toneTrack!=null){try{toneTrack.stop();}catch(Exception ignored){} toneTrack.release(); toneTrack=null;}
            }
        }).start();
    }

    @Override protected void onDestroy(){ stopAll(); super.onDestroy(); }
}
