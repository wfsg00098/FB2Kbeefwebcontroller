package com.badegg.fb2kbeefwebcontroller;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

public class ServerConfig extends AppCompatActivity {
    GlobalVariables G;
    Intent intent;
    boolean updating = false;


    private void refresh_server_list(int selected_id) {
        try {
            JSONArray array = G.db_get_servers();
            String[] servers = new String[array.length()];
            for (int i = 1; i <= array.length(); i++) {
                JSONObject temp = array.getJSONObject(i - 1);
                servers[i - 1] = String.valueOf(temp.getInt("id"));
            }
            Spinner spinner_server = findViewById(R.id.spinner_server);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, servers);
            spinner_server.setAdapter(adapter);
            int count = spinner_server.getCount();
            boolean changed = false;
            for (int i = 0; i < count; i++) {
                if (spinner_server.getItemAtPosition(i).toString().equals(String.valueOf(selected_id))) {
                    spinner_server.setSelection(i);
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                spinner_server.setSelection(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void refresh_playlist_order() {
        TextView order = findViewById(R.id.textView_playlist_order);
        if (G.current_playlist_order == G.ORDER_DEFAULT) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_default)));
        }
        if (G.current_playlist_order == G.ORDER_TITLE_ASC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_title_asc)));
        }
        if (G.current_playlist_order == G.ORDER_TITLE_DESC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_title_desc)));
        }
        if (G.current_playlist_order == G.ORDER_ARTIST_ASC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_artist_asc)));
        }
        if (G.current_playlist_order == G.ORDER_ARTIST_DESC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_artist_desc)));
        }
        if (G.current_playlist_order == G.ORDER_ALBUM_ASC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_album_asc)));
        }
        if (G.current_playlist_order == G.ORDER_ALBUM_DESC) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_album_desc)));
        }
        if (G.current_playlist_order == G.ORDER_RANDOM) {
            order.setText(String.format(getString(R.string.textview_playlist_order), getString(R.string.button_order_random)));
        }
    }

    private void refresh_track_limit() {
        EditText et = findViewById(R.id.et_track_limit);
        et.setText(String.valueOf(G.tracks_limit));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_config);
        G = (GlobalVariables) getApplication();
        setTitle(R.string.Activity_server_config_title);
        intent = getIntent();
        refresh_server_list(G.Server_id);
        Spinner spinner_server = findViewById(R.id.spinner_server);
        spinner_server.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String so = spinner_server.getItemAtPosition(position).toString();
                String[] server = G.db_get_server(Integer.parseInt(so));
                EditText et = findViewById(R.id.input_server_addr);
                et.setText(server[0]);
                et = findViewById(R.id.input_server_user);
                et.setText(server[1]);
                et = findViewById(R.id.input_server_pass);
                et.setText(server[2]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        refresh_playlist_order();
        refresh_track_limit();


        findViewById(R.id.button_server_confirm).setOnClickListener(v -> {
            String so = spinner_server.getSelectedItem().toString();
            G.db_select_server(Integer.parseInt(so));
            intent.putExtra("update_server", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.snackbar_server_used), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_server_add).setOnClickListener(v -> {
            int id = G.db_add_server();
            refresh_server_list(id);
        });

        findViewById(R.id.button_server_del).setOnClickListener(v -> {
            new AlertDialog.Builder(ServerConfig.this).setMessage(R.string.server_del_confirm)
                    .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                        String so = spinner_server.getSelectedItem().toString();
                        G.db_del_server(Integer.parseInt(so));
                        ServerConfig.this.refresh_server_list(0);
                    })
                    .setNegativeButton(R.string.btn_cancel, (dialog, which) -> {})
                    .show();
        });

        findViewById(R.id.button_server_save).setOnClickListener(v -> {
            int id = Integer.parseInt(spinner_server.getSelectedItem().toString());
            EditText et = findViewById(R.id.input_server_addr);
            String addr = et.getText().toString();
            et = findViewById(R.id.input_server_user);
            String user = et.getText().toString();
            et = findViewById(R.id.input_server_pass);
            String pass = et.getText().toString();
            G.db_save_server(id, addr, user, pass);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.snackbar_server_saved), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_update_playlist).setOnClickListener(v -> {

            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.playlist_updating), Snackbar.LENGTH_INDEFINITE);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
            if (updating) return;
            updating = true;
            Thread thread = new Thread(() -> {
                if (G.db_update_playlist()) {
                    runOnUiThread(() -> {
                        @SuppressLint("CutPasteId") Snackbar sb1 = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.playlist_updated), Snackbar.LENGTH_INDEFINITE);
                        sb1.setAction(R.string.snackbar_got_it, v1 -> sb1.dismiss());
                        sb1.show();
                        updating = false;
                        intent.putExtra("update_server", true);
                    });
                } else {
                    runOnUiThread(() -> {
                        @SuppressLint("CutPasteId") Snackbar sb1 = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.snackbar_invalid_connection), Snackbar.LENGTH_INDEFINITE);
                        sb1.setAction(R.string.snackbar_got_it, v1 -> sb1.dismiss());
                        sb1.show();
                        updating = false;
                    });
                }
            });
            thread.start();
        });


        findViewById(R.id.button_order_default).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_DEFAULT);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_default)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_title_asc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_TITLE_ASC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_title_asc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_title_desc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_TITLE_DESC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_title_desc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_artist_asc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_ARTIST_ASC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_artist_asc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_artist_desc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_ARTIST_DESC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_artist_desc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_album_asc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_ALBUM_ASC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_album_asc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_album_desc).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_ALBUM_DESC);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_album_desc)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_order_random).setOnClickListener(v -> {
            G.db_set_playlist_order(G.ORDER_RANDOM);
            refresh_playlist_order();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), String.format(getString(R.string.snackbar_playlist_order), getString(R.string.button_order_random)), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });

        findViewById(R.id.button_track_limit_save).setOnClickListener(v -> {
            EditText et = findViewById(R.id.et_track_limit);
            int limit = Integer.parseInt(et.getText().toString());
            G.db_set_list_limit(limit);
            G.tracks_limit = limit;
            refresh_track_limit();
            intent.putExtra("update_playlist", true);
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.snackbar_track_limit_saved), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        });
    }


    @Override
    public void onBackPressed() {
        if (updating) {
            Snackbar sb = Snackbar.make(findViewById(R.id.server_layout), getString(R.string.playlist_updating), Snackbar.LENGTH_SHORT);
            sb.setAction(R.string.snackbar_got_it, v1 -> sb.dismiss());
            sb.show();
        } else {
            setResult(RESULT_OK, intent);
            super.onBackPressed();
        }
    }

}