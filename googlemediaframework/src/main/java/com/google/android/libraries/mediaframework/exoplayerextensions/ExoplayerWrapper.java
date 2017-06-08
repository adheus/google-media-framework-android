/**
 Copyright 2014 Google Inc. All rights reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/**
 * This file has been taken from the ExoPlayer demo project with minor modifications.
 * https://github.com/google/ExoPlayer/
 */
package com.google.android.libraries.mediaframework.exoplayerextensions;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.*;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface. It can be prepared
 * with one of a video to suit different use cases (e.g. DASH,
 * SmoothStreaming and so on).
 */
public class ExoplayerWrapper implements ExoPlayer.EventListener {

  private final DefaultTrackSelector trackSelector;
  private final Context context;

  /**
   * A listener for basic playback events.
   */
  public interface PlaybackListener {
    void onStateChanged(boolean playWhenReady, int playbackState);
    void onError(Exception e);
    void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                            float pixelWidthHeightRatio);
  }

  /**
   * The underlying SimpleExoPlayer instance responsible for playing the video.
   */
  private final SimpleExoPlayer player;

  /**
   * Used by track renderers to send messages to the event listeners within this class.
   */
  private final Handler mainHandler;

  /**
   * Listeners are notified when the video size changes, when the underlying player's state changes,
   * or when an error occurs.
   */
  private final CopyOnWriteArrayList<PlaybackListener> playbackListeners;

  /**
   * States are idle, prepared, buffering, ready, or ended. This is an integer (instead of an enum)
   * because the Exoplayer library uses integers.
   */
  private int lastReportedPlaybackState;

  /**
   * Whether the player was in a playWhenReady state the last time we checked.
   */
  private boolean lastReportedPlayWhenReady;

  private DefaultBandwidthMeter bandwidthMeter;

  private OfflineLicenseProvider offlineLicenseProvider;
  private UUID drmSchemeUuid;
  private String manifestUri;
  private String drmLicenseUrl;
  private String[] keyRequestPropertiesArray;
  private boolean isCastLabsWidevine;
  private boolean offlinePlaybackMode;


  public ExoplayerWrapper(Context context, SimpleExoPlayerView simpleExoPlayerView) {
    this.context = context;
    this.bandwidthMeter = new DefaultBandwidthMeter(getMainHandler(),
            null);
    TrackSelection.Factory videoTrackSelectionFactory =
            new AdaptiveTrackSelection.Factory(bandwidthMeter);
    trackSelector =
            new DefaultTrackSelector(videoTrackSelectionFactory);
    DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context,
            createDrmSessionManager(), 0); // TODO CHECK EXTENSIONS
    player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
    onRenderers();
    player.addListener(this);
    mainHandler = new Handler();
    playbackListeners = new CopyOnWriteArrayList<>();
    lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
    simpleExoPlayerView.setPlayer(player);
  }


  private DrmSessionManager<FrameworkMediaCrypto> createDrmSessionManager() {
    DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
    if (drmSchemeUuid != null) {
      try {
        drmSessionManager = buildDrmSessionManager(drmSchemeUuid, drmLicenseUrl,
                keyRequestPropertiesArray);
        return drmSessionManager;
      } catch (com.google.android.exoplayer2.drm.UnsupportedDrmException e) {

      }
    }
    return null;
  }

    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
            String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
      if (Util.SDK_INT < 18) {
        return null;
      }
      if (isCastLabsWidevine) {
        for (String key : keyRequestPropertiesArray) {
          Log.d("DEBUG DO DEDEU", key);
        }
        CastlabsWidevineDrmCallback castlabsWidevineDrmCallback = new CastlabsWidevineDrmCallback(licenseUrl,
                keyRequestPropertiesArray[1],
                keyRequestPropertiesArray[3],
                keyRequestPropertiesArray[5],
                keyRequestPropertiesArray[7],
                buildHttpDataSourceFactory(null));

        DefaultDrmSessionManager<FrameworkMediaCrypto> defaultDrmSessionManager = new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), castlabsWidevineDrmCallback, null, mainHandler, null);
        Uri uri = Uri.parse(manifestUri);
        if (offlineLicenseProvider != null && offlinePlaybackMode) {
          byte[] licenseKeySet = offlineLicenseProvider.licenseForVideoUri(uri);
          assert licenseKeySet != null;
          Log.d("DEBUG", "Found offline licenseKeySet for video:" + licenseKeySet.toString());
          try {
            Pair<Long, Long> duration = new  OfflineLicenseHelper<>(FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID), castlabsWidevineDrmCallback, null).getLicenseDurationRemainingSec(licenseKeySet);
            Log.d("DEBUG", "Remaining time:  " + duration.first.toString() + " s / Playback duration: " + duration.second.toString() + " s");
            Log.d("DEBUG", "Found offline licenseKeySet for video:" + licenseKeySet.toString());
          } catch (DrmSession.DrmSessionException e) {
            e.printStackTrace();
          }

          defaultDrmSessionManager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, licenseKeySet);
        }

        return defaultDrmSessionManager;
      } else {
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
                buildHttpDataSourceFactory(null));
        if (keyRequestPropertiesArray != null) {
          for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
            drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
                    keyRequestPropertiesArray[i + 1]);
          }
        }
        return new DefaultDrmSessionManager<>(uuid,
                FrameworkMediaDrm.newInstance(uuid), drmCallback, null, mainHandler, null);
      }
    }

  public void loadVideo(Video video) {
    DataSource.Factory mediaDataSourceFactory = buildDataSourceFactory(bandwidthMeter);
    player.prepare(video.toMediaSource(buildDataSourceFactory(null), mediaDataSourceFactory, getMainHandler()));
  }

  private DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
    return new DefaultDataSourceFactory(context, bandwidthMeter,
            buildHttpDataSourceFactory(bandwidthMeter));
  }

  private HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
    return new DefaultHttpDataSourceFactory(Util.getUserAgent(context, "GMF-1.0.0-alpha"), bandwidthMeter);
  }

  /**
   * Add a listener to respond to size change and error events.
   *
   * @param playbackListener
   */
  public void addListener(PlaybackListener playbackListener) {
    playbackListeners.add(playbackListener);
  }

  /**
   * Remove a listener from notifications about size changes and errors.
   *
   * @param playbackListener
   */
  public void removeListener(PlaybackListener playbackListener) {
    playbackListeners.remove(playbackListener);
  }


  /**
   * Build the renderers.
   */
  public void prepare() {
    maybeReportPlayerState();
  }

  private void onRenderers() {
    maybeReportPlayerState();
  }

  /**
   * Move the seek head to the given position.
   * @param positionMs A number of milliseconds after the start of the video.
   */
  public void seekTo(int positionMs) {
    player.seekTo(positionMs);
  }

  public void play() {
    this.player.setPlayWhenReady(true);
    for (PlaybackListener playbackListener : playbackListeners) {
      playbackListener.onStateChanged(true, getPlaybackState());
    }

  }

  public void pause() {
    this.player.setPlayWhenReady(false);
    for (PlaybackListener playbackListener : playbackListeners) {
      playbackListener.onStateChanged(false, getPlaybackState());
    }
  }

  /**
   * When you are finished using this object, make sure to call this method.
   */
  public void release() {
    player.release();
  }

  /**
   * Returns the state of the Exoplayer instance.
   */
  public int getPlaybackState() {
    return player.getPlaybackState();
  }

  /**
   * Returns the position of the seek head in the number of
   * milliseconds after the start of the video.
   */
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  /**
   * Returns the duration of the video in milliseconds.
   */
  public long getDuration() {
    return player.getDuration();
  }

  /**
   * Returns the handler which responds to messages.
   */
  Handler getMainHandler() {
    return mainHandler;
  }

  /* ExoPlayerListener methods */

  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest) {

  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

  }

  @Override
  public void onLoadingChanged(boolean isLoading) {

  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    maybeReportPlayerState();
  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {
    for (PlaybackListener playbackListener : playbackListeners) {
      playbackListener.onError(error);
    }
  }

  @Override
  public void onPositionDiscontinuity() {

  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

  }
  /**
   * If either playback state or the play when ready values have changed, notify all the playback
   * listeners.
   */
  private void maybeReportPlayerState() {
    boolean playWhenReady = player.getPlayWhenReady();
    int playbackState = getPlaybackState();
    if (playbackListeners != null && (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState)) {
      for (PlaybackListener playbackListener : playbackListeners) {
        playbackListener.onStateChanged(playWhenReady, playbackState);
      }
      lastReportedPlayWhenReady = playWhenReady;
      lastReportedPlaybackState = playbackState;
    }
  }


}