package com.mbtechpro.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.exoplayer2.ui.PlayerView;

public class MainActivity extends AppCompatActivity {


    private PlayerView playerView;
    private PlayerManager player;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        playerView = findViewById(R.id.player_view);

        player = new PlayerManager(this);


    }


    @Override
    public void onResume() {
        super.onResume();
        player.init(this, playerView);
    }

    @Override
    public void onPause() {
        super.onPause();
        player.reset();
    }

    @Override
    public void onDestroy() {
        player.release();
        super.onDestroy();
    }





}
