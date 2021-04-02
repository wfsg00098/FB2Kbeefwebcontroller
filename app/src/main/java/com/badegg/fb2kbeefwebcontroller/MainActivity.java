package com.badegg.fb2kbeefwebcontroller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    GlobalVariables G;
    Timer timer;

    private void requestPermission() {
        int checkCallPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        if (checkCallPhonePermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 1);
        }
    }

    private void refresh_playing() {
        if (G.Server_addr == null) return;
        Thread thread = new Thread(() -> {
            int current_server_id = G.Server_id;
            try {
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder()
                        .addHeader("Content-type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                        .url(G.Server_addr + "/api/player?columns=%title%,%artist%,%album%")
                        .build();
                Response response = okHttpClient.newCall(request).execute();
                String result = response.body().string();
                JSONObject player = new JSONObject(result).getJSONObject("player");
                player = player.getJSONObject("activeItem");

                int song_id = player.getInt("index");
                String song_playlist = player.getString("playlistId");
                if (song_id == G.current_playing_song_id && song_playlist.equals(G.current_playing_song_playlist)) {
                    return;
                }
                G.current_playing_song_id = song_id;
                G.current_playing_song_playlist = song_playlist;
                JSONArray columns = player.getJSONArray("columns");

                request = new Request.Builder()
                        .addHeader("Content-type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                        .url(G.Server_addr + "/api/artwork/" + G.current_playing_song_playlist + "/" + G.current_playing_song_id)
                        .build();
                response = okHttpClient.newCall(request).execute();
                byte[] album_art = response.body().bytes();
                Bitmap bitmap = BitmapFactory.decodeByteArray(album_art, 0, album_art.length);
                try {
                    bitmap.getWidth();
                } catch (Exception e) {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_album_art);
                }
                Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> {
                    try {
                        TextView temp = findViewById(R.id.playing_title);
                        temp.setText(columns.getString(0));
                        temp = findViewById(R.id.playing_artist);
                        temp.setText(columns.getString(1));
                        temp = findViewById(R.id.playing_album);
                        temp.setText(columns.getString(2));
                        ImageView temp1 = findViewById(R.id.playing_album_art);
                        temp1.setImageBitmap(finalBitmap);
                        temp = findViewById(R.id.textView_current_server);
                        TypedValue typedValue = new TypedValue();
                        getTheme().resolveAttribute(R.attr.colorOnPrimary, typedValue, true);
                        temp.setTextColor(typedValue.data);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                if (current_server_id == G.Server_id) {
                    runOnUiThread(() -> {
                        TextView temp = findViewById(R.id.textView_current_server);
                        temp.setTextColor(Color.RED);
                        G.current_playing_song_id = -1;
                        temp = findViewById(R.id.playing_title);
                        temp.setText(R.string.playing_title);
                        temp = findViewById(R.id.playing_artist);
                        temp.setText(R.string.playing_artist);
                        temp = findViewById(R.id.playing_album);
                        temp.setText(R.string.playing_album);
                        ImageView temp1 = findViewById(R.id.playing_album_art);
                        temp1.setImageDrawable(getDrawable(R.drawable.default_album_art));
                    });
                }
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void start_refresh_playing_timer() {
        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refresh_playing();
            }
        };
        timer.schedule(task, 0, 1000);
    }

    private void refresh_server() {
        TextView textview_current_server = findViewById(R.id.textView_current_server);
        textview_current_server.setText(String.format(getString(R.string.textview_current_server), G.Server_addr));
    }

    private void refresh_playlist() {
        Spinner spinner_playlist = findViewById(R.id.spinner_playlist);
        String[] playlists = G.db_get_playlists();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, playlists);
        spinner_playlist.setAdapter(adapter);
        spinner_playlist.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String playlist = spinner_playlist.getItemAtPosition(position).toString();
                G.current_playlist = playlist;
                G.db_set_current_playlist(playlist);
                refresh_tracks();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refresh_tracks() {
        Spinner spinner_playlist = findViewById(R.id.spinner_playlist);
        String playlist = spinner_playlist.getSelectedItem().toString();
        EditText et = findViewById(R.id.input_search);
        String search = et.getText().toString();
        LinearLayout ll = findViewById(R.id.llayout_playlist);
        ll.removeAllViews();
        @SuppressLint("ClickableViewAccessibility") Thread thread = new Thread(() -> {
            JSONArray array = G.db_get_tracks_by_list(playlist, search, G.current_playlist_order);
            int count = array.length();
            for (int i = 0; i < count; i++) {
                try {
                    JSONObject json = array.getJSONObject(i);
                    View item = View.inflate(MainActivity.this, R.layout.layout_playlist_item, null);
                    TextView tv = item.findViewById(R.id.tv_title);
                    String title = json.getString("title");
                    tv.setText(title);
                    tv = item.findViewById(R.id.tv_id);
                    tv.setText(String.valueOf(json.getInt("id")));
                    tv = item.findViewById(R.id.tv_artist);
                    String artist = json.getString("artist");
                    tv.setText(artist);
                    tv = item.findViewById(R.id.tv_album);
                    tv.setText(json.getString("album"));
                    tv = item.findViewById(R.id.tv_length);
                    tv.setText(json.getString("length"));
                    ImageButton ib = item.findViewById(R.id.imageButton);
                    ib.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            v.setBackgroundColor(getResources().getColor(R.color.shadow, getTheme()));
                        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL){
                            v.setBackgroundColor(getResources().getColor(R.color.transparent, getTheme()));
                        }
                        return false;
                    });
                    ib.setOnLongClickListener(v -> {
                            Thread thread1 = new Thread(() -> {
                                try {
                                    OkHttpClient okHttpClient = new OkHttpClient();
                                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                                    Request request = new Request.Builder()
                                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                                            .url(G.Server_addr + "/api/player/play/" + G.current_playlist + "/" + json.getInt("id"))
                                            .post(stringBody)
                                            .build();
                                    okHttpClient.newCall(request).execute();
                                    runOnUiThread(() -> {
                                        refresh_playing();
                                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), String.format(getString(R.string.snackbar_play), title, artist), Snackbar.LENGTH_SHORT);
                                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                                        sb.show();
                                    });
                                } catch (Exception e) {
                                    runOnUiThread(() -> {
                                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                                        sb.show();
                                    });
                                    e.printStackTrace();
                                }

                            });
                            thread1.start();
                        return false;
                    });
                    runOnUiThread(() -> ll.addView(item));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_unknown_error), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private void refresh_search() {
        Thread thread = new Thread(() -> {
            String search = G.db_get_search_String();
            runOnUiThread(() -> {
                EditText et = findViewById(R.id.input_search);
                et.setText(search);
            });
        });
        thread.start();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();
        G = (GlobalVariables) getApplication();

        start_refresh_playing_timer();
        refresh_server();
        refresh_search();
        if (G.Server_id != 0) {
            refresh_playlist();
        }

        findViewById(R.id.button_play).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/play")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_next).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/next")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_previous).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/previous")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_stop).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/stop")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_pause).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/pause/toggle")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_replay).setOnClickListener(v -> {
            Thread thread = new Thread(() -> {
                try {
                    OkHttpClient okHttpClient = new OkHttpClient();
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody stringBody = RequestBody.create("{\"test\":123}", JSON);
                    Request request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/stop")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                    request = new Request.Builder()
                            .addHeader("Authorization", "Basic " + G.get_auth_phrase())
                            .url(G.Server_addr + "/api/player/play")
                            .post(stringBody)
                            .build();
                    okHttpClient.newCall(request).execute();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Snackbar sb = Snackbar.make(findViewById(R.id.main_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_SHORT);
                        sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
                        sb.show();
                    });
                    e.printStackTrace();
                }
            });
            thread.start();
            refresh_playing();
        });

        findViewById(R.id.button_search).setOnClickListener(v -> {
            EditText et = findViewById(R.id.input_search);
            String search = et.getText().toString();
            G.db_set_search_string(search);
            refresh_tracks();
        });

        findViewById(R.id.input_search).setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                return findViewById(R.id.button_search).performClick();
            }
            return false;
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.menu_server) {
            startActivityForResult(new Intent(this, ServerConfig.class), 100);
            return true;
        }
        if (id == R.id.menu_about) {
            startActivity(new Intent(this, About.class));
            return true;
        }
        if (id == R.id.menu_light) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
            return true;
        }
        if (id == R.id.menu_night) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            try {
                assert data != null;
                if (data.getBooleanExtra("update_server", false)) {
                    refresh_server();
                    refresh_playlist();
                }
                if (data.getBooleanExtra("update_playlist", false)) {
                    refresh_tracks();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        G.current_playing_song_id = -1;
        super.onRestoreInstanceState(savedInstanceState);
    }


    @Override
    protected void onDestroy() {
        G.current_playing_song_id = -1;
        timer.cancel();
        super.onDestroy();
    }
}