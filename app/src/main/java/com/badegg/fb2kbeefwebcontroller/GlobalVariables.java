package com.badegg.fb2kbeefwebcontroller;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import java.util.TreeSet;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class GlobalVariables extends Application {

    String data_path;

    public final int ORDER_DEFAULT = 0;
    public final int ORDER_TITLE_ASC = 1;
    public final int ORDER_TITLE_DESC = 2;
    public final int ORDER_ARTIST_ASC = 3;
    public final int ORDER_ARTIST_DESC = 4;
    public final int ORDER_ALBUM_ASC = 5;
    public final int ORDER_ALBUM_DESC = 6;
    public final int ORDER_RANDOM = 7;

    private SQLiteDatabase db;

    //Server
    public int Server_id;
    public String Server_addr;
    public String Server_user;
    public String Server_pass;

    //Now playing
    public String current_playing_song_playlist;
    public int current_playing_song_id;

    //Playlist
    public String current_playlist;
    public int current_playlist_order;

    //Tracks limit
    public int tracks_limit;

    public String sqliteEscape(String keyWord) {
        keyWord = keyWord.replace("\\", "\\\\");
        keyWord = keyWord.replace("%", "\\%");
        keyWord = keyWord.replace("_", "\\_");
        return keyWord;
    }

    public String get_auth_phrase() {
        return Base64.encodeToString((Server_user + ":" + Server_pass).getBytes(), Base64.NO_WRAP);
    }


    public JSONArray db_get_servers() {
        try {
            JSONArray result = new JSONArray();
            Cursor cursor = db.rawQuery("select * from server", null);
            if (!cursor.moveToFirst()) return result;
            JSONObject json = new JSONObject();
            json.put("id", cursor.getInt(0));
            json.put("addr", cursor.getString(1));
            json.put("user", cursor.getString(2));
            json.put("pass", cursor.getString(3));
            result.put(json);
            while (cursor.moveToNext()) {
                json = new JSONObject();
                json.put("id", cursor.getInt(0));
                json.put("addr", cursor.getString(1));
                json.put("user", cursor.getString(2));
                json.put("pass", cursor.getString(3));
                result.put(json);
            }
            cursor.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public int db_add_server() {
        db.execSQL("insert into server(addr,user,pass) values('','','')");
        Cursor cursor = db.rawQuery("select max(id) from server", null);
        int id;
        if (cursor.moveToFirst()) {
            id = cursor.getInt(0);
        } else {
            id = 1;
        }
        cursor.close();
        db.execSQL("create table list_s" + id + " (list text, id int, title text, artist text, album text, length text)");
        return id;
    }

    public void db_del_server(int id) {
        db.execSQL("drop table list_s" + id);
        db.execSQL("delete from server where id=" + id);
    }

    public void db_save_server(int id, String addr, String user, String pass) {
        String[] s = new String[3];
        s[0] = addr;
        s[1] = user;
        s[2] = pass;
        db.execSQL("update server set addr=?,user=?,pass=? where id=" + id, s);
    }

    public void db_select_server(int id) {
        db.execSQL("update current set server=" + id);
        Cursor cursor = db.rawQuery("select addr,user,pass from server where id=" + id, null);
        if (cursor.moveToFirst()) {
            Server_id = id;
            Server_addr = cursor.getString(0);
            Server_user = cursor.getString(1);
            Server_pass = cursor.getString(2);
        }
        cursor.close();
    }

    public int db_get_current_server() {
        Cursor cursor = db.rawQuery("select server from current", null);
        int id;
        if (cursor.moveToFirst()) {
            id = cursor.getInt(0);
        } else {
            id = 0;
        }
        cursor.close();
        return id;
    }

    public String[] db_get_server(int id) {
        String[] result = new String[3];
        String[] ids = new String[1];
        ids[0] = String.valueOf(id);
        Cursor cursor = db.rawQuery("select addr,user,pass from server where id=?", ids);
        cursor.moveToFirst();
        result[0] = cursor.getString(0);
        result[1] = cursor.getString(1);
        result[2] = cursor.getString(2);
        cursor.close();
        return result;
    }

    public void db_set_playlist_order(int mode) {
        db.execSQL("update current set mode=" + mode);
        current_playlist_order = mode;
    }

    public int db_get_playlist_order() {
        int mode;
        Cursor cursor = db.rawQuery("select mode from current", null);
        if (cursor.moveToFirst()) {
            mode = cursor.getInt(0);
        } else {
            mode = 0;
        }
        cursor.close();
        return mode;
    }

    public boolean db_update_playlist() {
        db.execSQL("delete from list_s" + Server_id);
        try {
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder()
                    .addHeader("Content-type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", "Basic " + get_auth_phrase())
                    .url(Server_addr + "/api/playlists")
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            String result = response.body().string();
            JSONObject json = new JSONObject(result);
            JSONArray array = json.getJSONArray("playlists");
            int count = array.length();
            for (int i = 0; i < count; i++) {
                JSONObject json1 = array.getJSONObject(i);
                String id = json1.getString("id");
                int itemcount = json1.getInt("itemCount");
                request = new Request.Builder()
                        .addHeader("Content-type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Basic " + get_auth_phrase())
                        .url(Server_addr + "/api/playlists/" + id + "/items/0:" + itemcount + "?columns=%title%,%artist%,%album%,%length%")
                        .build();
                response = okHttpClient.newCall(request).execute();
                result = response.body().string();
                JSONObject json2 = new JSONObject(result);
                json2 = json2.getJSONObject("playlistItems");
                JSONArray array2 = json2.getJSONArray("items");
                for (int j = 0; j < itemcount; j++) {
                    JSONObject column = array2.getJSONObject(j);
                    JSONArray columns = column.getJSONArray("columns");
                    db_insert_song(
                            Server_id,
                            id,
                            j,
                            columns.getString(0),
                            columns.getString(1),
                            columns.getString(2),
                            columns.getString(3)
                    );
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            db.execSQL("delete from list_s" + Server_id);
            return false;
        }
    }

    public void db_insert_song(int server, String list, int id, String title, String artist, String album, String length) {
        String[] args = new String[5];
        args[0] = list;
        args[1] = title;
        args[2] = artist;
        args[3] = album;
        args[4] = length;
        db.execSQL("insert into list_s" + server + "(list,id,title,artist,album,length) values(?," + id + ",?,?,?,?)", args);
    }

    public void db_set_search_string(String search) {
        String[] args = new String[1];
        args[0] = search;
        db.execSQL("update current set search=?", args);
    }

    public String db_get_search_String() {
        String result;
        Cursor cursor = db.rawQuery("select search from current", null);
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        } else {
            result = "";
        }
        cursor.close();
        return result;
    }

    public void db_set_current_playlist(String list) {
        db.execSQL("update current set playlist='" + list + "'");
    }

    public String db_get_current_playlist() {
        String result;
        Cursor cursor = db.rawQuery("select playlist from current", null);
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        } else {
            result = db_get_playlists()[0];
        }
        cursor.close();
        return result;
    }

    public String[] db_get_playlists() {
        String[] result = new String[]{"p1"};
        Cursor cursor = db.rawQuery("select distinct list from list_s" + Server_id, null);
        if (cursor.moveToFirst()) {
            int count = cursor.getCount();
            result = new String[count];
            for (int i = 0; i < count; i++) {
                result[i] = cursor.getString(0);
                cursor.moveToNext();
            }
        }
        cursor.close();
        return result;
    }


    public JSONArray db_get_tracks_by_list(String list, String search, int order) {
        String[] args = new String[1];
        args[0] = list;
        JSONArray array = new JSONArray();
        String order_sql = "";
        switch (order) {
            case ORDER_DEFAULT:
                order_sql = "";
                break;
            case ORDER_TITLE_ASC:
                order_sql = "order by title";
                break;
            case ORDER_TITLE_DESC:
                order_sql = "order by title desc";
                break;
            case ORDER_ARTIST_ASC:
                order_sql = "order by artist";
                break;
            case ORDER_ARTIST_DESC:
                order_sql = "order by artist desc";
                break;
            case ORDER_ALBUM_ASC:
                order_sql = "order by album";
                break;
            case ORDER_ALBUM_DESC:
                order_sql = "order by album desc";
                break;
            case ORDER_RANDOM:
                order_sql = "order by random()";
                break;
            default:
                order_sql = "";
        }
        if (search.equals("")) {
            Cursor cursor = db.rawQuery("select id,title,artist,album,length from list_s" + Server_id + " where list=? " + order_sql + " limit " + tracks_limit, args);
            if (cursor.moveToFirst()) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("id", cursor.getInt(0));
                    json.put("title", cursor.getString(1));
                    json.put("artist", cursor.getString(2));
                    json.put("album", cursor.getString(3));
                    json.put("length", cursor.getString(4));
                    array.put(json);
                    while (cursor.moveToNext()) {
                        json = new JSONObject();
                        json.put("id", cursor.getInt(0));
                        json.put("title", cursor.getString(1));
                        json.put("artist", cursor.getString(2));
                        json.put("album", cursor.getString(3));
                        json.put("length", cursor.getString(4));
                        array.put(json);
                    }
                    cursor.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            String[] searches = search.split(" ");
            TreeSet<Integer> set = new TreeSet<>();
            set.clear();
            Cursor cursor = db.rawQuery("select count(*) from list_s" + Server_id + " where list=? ", args);
            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                for (int i = 0; i < count; i++) {
                    set.add(i);
                }
            }
            String[] args1 = new String[4];
            args1[0] = args[0];
            for (String sea : searches) {
                TreeSet<Integer> set1 = new TreeSet<>();
                set1.clear();

                if (sea.matches("[0-9]+")) {
                    cursor = db.rawQuery("select id from list_s" + Server_id + " where list=? and id=" + Integer.parseInt(sea), args);
                    if (cursor.moveToFirst()) {
                        set1.add(cursor.getInt(0));
                    }
                    cursor.close();
                }
                args1[1] = "%" + sqliteEscape(sea) + "%";
                args1[2] = "%" + sqliteEscape(sea) + "%";
                args1[3] = "%" + sqliteEscape(sea) + "%";
                cursor = db.rawQuery("select id from list_s" + Server_id + " where list=? and (title like ? escape '\\' or artist like ? escape '\\' or album like ? escape '\\')", args1);
                if (cursor.moveToFirst()) {
                    set1.add(cursor.getInt(0));
                    while (cursor.moveToNext()) {
                        set1.add(cursor.getInt(0));
                    }
                }
                cursor.close();
                set.retainAll(set1);
            }
            Iterator<Integer> it = set.iterator();
            int set_size = set.size();
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0; i < set_size; i++) {
                if (i != 0) sb.append(",");
                sb.append(it.next());
            }
            sb.append(")");
            cursor = db.rawQuery("select id,title,artist,album,length from list_s" + Server_id + " where list=? and id in " + sb.toString() + " limit " + tracks_limit, args);
            if (cursor.moveToFirst()) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("id", cursor.getInt(0));
                    json.put("title", cursor.getString(1));
                    json.put("artist", cursor.getString(2));
                    json.put("album", cursor.getString(3));
                    json.put("length", cursor.getString(4));
                    array.put(json);
                    while (cursor.moveToNext()) {
                        json = new JSONObject();
                        json.put("id", cursor.getInt(0));
                        json.put("title", cursor.getString(1));
                        json.put("artist", cursor.getString(2));
                        json.put("album", cursor.getString(3));
                        json.put("length", cursor.getString(4));
                        array.put(json);
                    }
                    cursor.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return array;
    }

    public void db_set_list_limit(int limit) {
        db.execSQL("update current set listlimit=" + limit + "");
    }

    public int db_get_list_limit() {
        int result;
        Cursor cursor = db.rawQuery("select listlimit from current", null);
        if (cursor.moveToFirst()) {
            result = cursor.getInt(0);
        } else {
            result = 200;
        }
        cursor.close();
        return result;
    }

    private boolean clear_all(){
        File file = new File(data_path + "config.db");
        return file.delete();
    }

    @Override
    public void onCreate() {
        data_path = getFilesDir().getAbsolutePath() + "/";
        //clear_all();

        db = SQLiteDatabase.openOrCreateDatabase(data_path + "config.db", null);
        db.execSQL("create table if not exists current(server integer, playlist text, mode integer, search text, listlimit integer)");
        db.execSQL("insert into current values(0,'',0,'',200)");
        db.execSQL("create table if not exists server(id integer primary key AUTOINCREMENT , addr text, user text, pass text)");
        Server_id = db_get_current_server();
        db_select_server(Server_id);
        current_playlist_order = db_get_playlist_order();
        current_playlist = db_get_current_playlist();
        tracks_limit = db_get_list_limit();
        super.onCreate();
    }

    @Override
    public void onTerminate() {
        db.close();
        super.onTerminate();
    }
}

