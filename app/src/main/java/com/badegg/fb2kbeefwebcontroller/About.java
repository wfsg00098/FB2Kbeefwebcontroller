package com.badegg.fb2kbeefwebcontroller;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import java.util.Calendar;

public class About extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.Activity_about_title);
        TextView tv = findViewById(R.id.tv_copyright);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        tv.setText(String.format(getString(R.string.tv_copyright),year));
    }
}