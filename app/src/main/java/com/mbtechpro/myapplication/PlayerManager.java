/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbtechpro.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.lang.reflect.Constructor;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import static com.google.android.exoplayer2.Player.STATE_BUFFERING;
import static com.google.android.exoplayer2.Player.STATE_ENDED;
import static com.google.android.exoplayer2.Player.STATE_IDLE;
import static com.google.android.exoplayer2.Player.STATE_READY;

/** Manages the {@link ExoPlayer}, the IMA plugin and all video playback. */
/* package */ final class PlayerManager implements PlaybackPreparer {

  private final DataSource.Factory mediaDataSourceFactory;

  private ViewGroup adUiViewGroup;

  private SimpleExoPlayer player;
  private long contentPosition;

  private Handler mainHandler;
  private EventLogger eventLogger;
  private DefaultTrackSelector trackSelector;

  private Context mContext;

  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();


  protected String userAgent;

  private PlayerView playerView;

  public PlayerManager(Context context) {
    mContext = context;

    userAgent = Util.getUserAgent(mContext, context.getString(R.string.app_name));

    TrackSelection.Factory adaptiveTrackSelectionFactory =
            new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
    trackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);

    mainHandler = new Handler();
    eventLogger = new EventLogger(trackSelector);

    // New Implementation
    mediaDataSourceFactory = buildDataSourceFactory(true);
  }

  public boolean useExtensionRenderers() {
    return BuildConfig.FLAVOR.equals("withExtensions");
  }

  public void init(Context context, PlayerView playerVie) {
    playerView = playerVie;

    // New Implementation

    boolean needNewPlayer = player == null;
    if(needNewPlayer) {
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      boolean preferExtensionDecoders = true;//intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode =
              (useExtensionRenderers()
                      ? (preferExtensionDecoders ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                      : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                      : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
      DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context,
              drmSessionManager, extensionRendererMode);
      player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
      player.addListener(new PlayerEventListener());
      player.setPlayWhenReady(true);

      // Bind the player to the view.
      playerView.setPlayer(player);
      playerView.setPlaybackPreparer(this);
    }


    String contentUrl = "https://video.thehindu.com/thehindu/7129Mizoram-Football.mp4";

    // New Implementation
    MediaSource[] contentMediaSource = new MediaSource[1];
    contentMediaSource[0] = buildMediaSource(Uri.parse(contentUrl), null, mainHandler, eventLogger);

    MediaSource mediaSource = new ConcatenatingMediaSource(contentMediaSource);

    // Prepare the player with the source.
    player.seekTo(contentPosition);
    player.prepare(mediaSource, true, false);

  }

  public void reset() {
    if (player != null) {
      contentPosition = player.getContentPosition();
      player.release();
      player = null;
    }
  }

  public void release() {
    if (player != null) {
      player.release();
      player = null;
    }
  }


  private MediaSource buildMediaSource(
          Uri uri,
          String overrideExtension,
          @Nullable Handler handler,
          @Nullable MediaSourceEventListener listener) {
    @ContentType int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri)
            : Util.inferContentType("." + overrideExtension);
    switch (type) {
      case C.TYPE_OTHER:
        return new ExtractorMediaSource.Factory(mediaDataSourceFactory)
                .createMediaSource(uri, handler, listener);
      default: {
        throw new IllegalStateException("Unsupported type: " + type);
      }
    }
  }

  /**
   * Returns a new DataSource factory.
   *
   * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
   *     DataSource factory.
   * @return A new DataSource factory.
   */
  private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
    return buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
  }

  /** Returns a {@link DataSource.Factory}. */
  public DataSource.Factory buildDataSourceFactory(TransferListener<? super DataSource> listener) {
    return new DefaultDataSourceFactory(mContext, listener, buildHttpDataSourceFactory(listener));
  }

  /** Returns a {@link HttpDataSource.Factory}. */
  public HttpDataSource.Factory buildHttpDataSourceFactory(
          TransferListener<? super DataSource> listener) {
    return new DefaultHttpDataSourceFactory(userAgent, listener);
  }

  @Override
  public void preparePlayback() {
    init(mContext, playerView);
  }

  private class PlayerEventListener implements Player.EventListener {

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
      Log.i("Ashwani", "onTimelineChanged");
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      Log.i("Ashwani", "onTracksChanged");
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      Log.i("Ashwani", "onLoadingChanged :: "+isLoading);


    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      switch (playbackState) {
        case STATE_IDLE:
          Log.i("Ashwani", "idel");
          break;
        case STATE_BUFFERING:
          Log.i("Ashwani", "buffering");
          playerView.showProgressBar();
          break;
        case STATE_READY:
          Log.i("Ashwani", "ready");
          playerView.hideProgressBar();
          break;
        case STATE_ENDED:
          Log.i("Ashwani", "ended");
          playerView.hideProgressBar();
          break;
      }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      Log.i("Ashwani", "onRepeatModeChanged");
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      Log.i("Ashwani", "onShuffleModeEnabledChanged");
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      Log.i("Ashwani", "onPlayerError");
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
      Log.i("Ashwani", "onPositionDiscontinuity");
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      Log.i("Ashwani", "onPlaybackParametersChanged");
    }

    @Override
    public void onSeekProcessed() {
      Log.i("Ashwani", "onSeekProcessed");
    }
  }


  public static Bitmap SavePixels(int x, int y, int w, int h, GL10 gl) {
    int b[]=new int[w*(y+h)];
    int bt[]=new int[w*h];
    IntBuffer ib=IntBuffer.wrap(b);
    ib.position(0);
    gl.glReadPixels(x, 0, w, y+h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, ib);

    for(int i=0, k=0; i<h; i++, k++) {
      //remember, that OpenGL bitmap is incompatible with Android bitmap
      //and so, some correction need.
      for(int j=0; j<w; j++) {
        int pix=b[i*w+j];
        int pb=(pix>>16)&0xff;
        int pr=(pix<<16)&0x00ff0000;
        int pix1=(pix&0xff00ff00) | pr | pb;
        bt[(h-k-1)*w+j]=pix1;
      }
    }


    Bitmap sb=Bitmap.createBitmap(bt, w, h, Bitmap.Config.ARGB_8888);
    return sb;
  }





}
