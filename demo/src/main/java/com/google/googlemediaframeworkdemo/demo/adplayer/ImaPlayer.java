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

package com.google.googlemediaframeworkdemo.demo.adplayer;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;
import com.google.android.libraries.mediaframework.layeredvideo.SimpleVideoPlayer;
import com.google.android.libraries.mediaframework.layeredvideo.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The ImaPlayer is responsible for displaying both videos and ads. This is accomplished using two
 * video players. The content player displays the user's video. When an ad is requested, the ad
 * video player is overlaid on the content video player. When the ad is complete, the ad video
 * player is destroyed and the content video player is displayed again.
 */
public class ImaPlayer {

  private static String PLAYER_TYPE = "google/gmf-android";
  private static String PLAYER_VERSION = "1.0.0-alpha";

  /**
   * The activity that is displaying this video player.
   */
  private Activity activity;

  /**
   * Url of the ad.
   */
  private Uri adTagUrl;

  /**
   * Plays the ad.
   */
  private SimpleVideoPlayer adPlayer;

  /**
   * The layout that contains the ad player.
   */
  private SimpleExoPlayerView adPlayerContainer;

  /**
   * Used by the IMA SDK to overlay controls (i.e. skip ad) over the ad player.
   */
  private FrameLayout adUiContainer;

  /**
   * Responsible for requesting the ad and creating the
   * {@link com.google.ads.interactivemedia.v3.api.AdsManager}
   */
  private AdsLoader adsLoader;


  /**
   * Responsible for containing listeners for processing the elements of the ad.
   */
  private AdsManager adsManager;

  private AdListener adListener;

  /**
   * These callbacks are notified when the video is played and when it ends. The IMA SDK uses this
   * to poll for video progress and when to stop the ad.
   */
  private List<VideoAdPlayer.VideoAdPlayerCallback> callbacks;

  /**
   * Contains the content player and the ad frame layout.
   */
  private FrameLayout container;

  /**
   * Plays the content (i.e. the actual video).
   */
  private SimpleVideoPlayer contentPlayer;


  private SimpleExoPlayerView contentPlayerContainer;

  /**
   * This is the layout of the container before fullscreen mode has been entered.
   * When we leave fullscreen mode, we restore the layout of the container to this layout.
   */
  private ViewGroup.LayoutParams originalContainerLayoutParams;

  /**
   * A flag to indicate whether the ads has been shown.
   */
  private boolean adsShown;

  /**
   * Notifies callbacks when the ad finishes.
   */
  private final ExoplayerWrapper.PlaybackListener adPlaybackListener
      = new ExoplayerWrapper.PlaybackListener() {

    /**
     * We don't respond to errors.
     * @param e The error.
     */
    @Override
    public void onError(Exception e) {

    }

    /**
     * We notify all callbacks when the ad ends.
     * @param playWhenReady Whether the video should play as soon as it is loaded.
     * @param playbackState The state of the Exoplayer instance.
     */
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == ExoPlayer.STATE_ENDED) {
        for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
          callback.onEnded();
        }
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
      // No need to respond to size changes here.
    }
  };

    /**
     * Listener for the content player
     */
    private final ExoplayerWrapper.PlaybackListener contentPlaybackListener
            = new ExoplayerWrapper.PlaybackListener() {

      /**
       * We don't respond to errors.
       * @param e The error.
       */
      @Override
      public void onError(Exception e) {

      }

      /**
       * We notify the adLoader when the content has ended so it knows to play postroll ads.
       * @param playWhenReady Whether the video should play as soon as it is loaded.
       * @param playbackState The state of the Exoplayer instance.
       */
      @Override
      public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
          adsLoader.contentComplete();
        }
      }

      /**
       * We don't respond to size changes.
       * @param width The new width of the player.
       * @param height The new height of the player.
       * @param unappliedRotationDegrees The new rotation angle of the player thats not applied.
       */
      @Override
      public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

      }
    };

  /**
   * Sets up ads manager, responds to ad errors, and handles ad state changes.
   */
  private class AdListener implements AdErrorEvent.AdErrorListener,
      AdsLoader.AdsLoadedListener, AdEvent.AdEventListener {
    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
      // If there is an error in ad playback, log the error and resume the content.
      Log.d(this.getClass().getSimpleName(), adErrorEvent.getError().getMessage());

      // Display a toast message indicating the error.
      // You should remove this line of code for your production app.
      Toast.makeText(activity, adErrorEvent.getError().getMessage(), Toast.LENGTH_SHORT).show();
      resumeContent();
    }

    @Override
    public void onAdEvent(AdEvent event) {
      switch (event.getType()) {
        case LOADED:
          adsManager.start();
          break;
        case CONTENT_PAUSE_REQUESTED:
          pauseContent();
          break;
        case CONTENT_RESUME_REQUESTED:
          resumeContent();
          break;
        default:
          break;
      }
    }

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
      adsManager = adsManagerLoadedEvent.getAdsManager();
      adsManager.addAdErrorListener(this);
      adsManager.addAdEventListener(this);
      adsManager.init();
    }
  }

  /**
   * Handles loading, playing, retrieving progress, pausing, resuming, and stopping ad.
   */
  private final VideoAdPlayer videoAdPlayer = new VideoAdPlayer() {
    @Override
    public void playAd() {
      hideContentPlayer();
    }

    @Override
    public void loadAd(String mediaUri) {
      adTagUrl = Uri.parse(mediaUri);
      createAdPlayer();
    }

    @Override
    public void stopAd() {
      destroyAdPlayer();
      showContentPlayer();
    }

    @Override
    public void pauseAd() {
      if (adPlayer != null){
        adPlayer.pause();
      }
    }

    @Override
    public void resumeAd() {
      if(adPlayer != null) {
        adPlayer.play();
      }
    }

    @Override
    public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      callbacks.add(videoAdPlayerCallback);
    }

    @Override
    public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      callbacks.remove(videoAdPlayerCallback);
    }

    /**
     * Reports progress in ad player or content player (whichever is currently playing).
     *
     * NOTE: When the ad is buffering, the video is paused. However, when the buffering is
     * complete, the ad is resumed. So, as a workaround, we will attempt to resume the ad, by
     * calling the start method, whenever we detect that the ad is buffering. If the ad is done
     * buffering, the start method will resume playback. If the ad has not finished buffering,
     * then the start method will be ignored.
     */
    @Override
    public VideoProgressUpdate getAdProgress() {
      VideoProgressUpdate vpu;

      if (adPlayer == null && contentPlayer == null) {
        // If neither player is available, indicate that the time is not ready.
        vpu = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      } else if (adPlayer != null) {
        // If an ad is playing, report the progress of the ad player.
        vpu = new VideoProgressUpdate(adPlayer.getCurrentPosition(),
            adPlayer.getDuration());
      } else {
        // If the cotntent is playing, report the progress of the content player.
        vpu = new VideoProgressUpdate(contentPlayer.getCurrentPosition(),
            contentPlayer.getDuration());
      }
      return vpu;
    }
  };

  private final ContentProgressProvider contentProgressProvider = new ContentProgressProvider() {
    @Override
    public VideoProgressUpdate getContentProgress() {
      if (adPlayer != null || contentPlayer == null || contentPlayer.getDuration() <= 0) {
        return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      }
      return new VideoProgressUpdate(contentPlayer.getCurrentPosition(),
          contentPlayer.getDuration());
    }
  };


  /**
   * @param activity The activity that will contain the video player.
   * @param container The {@link FrameLayout} which will contain the video player.
   * @param video The video that should be played.
   * @param sdkSettings The settings that should be used to configure the IMA SDK.
   * @param adTagUrl The URL containing the VAST document of the ad.
   */
  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video,
                   ImaSdkSettings sdkSettings,
                   String adTagUrl) {
    this.activity = activity;
    this.container = container;

    if (adTagUrl != null) {
      this.adTagUrl = Uri.parse(adTagUrl);
    }

    sdkSettings.setPlayerType(PLAYER_TYPE);
    sdkSettings.setPlayerVersion(PLAYER_VERSION);
    adsLoader = ImaSdkFactory.getInstance().createAdsLoader(activity, sdkSettings);
    adListener = new AdListener();
    adsLoader.addAdErrorListener(adListener);
    adsLoader.addAdsLoadedListener(adListener);

    callbacks = new ArrayList<>();

    boolean autoplay = false;

    contentPlayerContainer =  new SimpleExoPlayerView(activity);
    container.addView(contentPlayerContainer);
    contentPlayerContainer.setVisibility(View.GONE);
    contentPlayer = new SimpleVideoPlayer(activity, contentPlayerContainer,
        video,
        autoplay);

    contentPlayer.addPlaybackListener(contentPlaybackListener);

    // Move the content player's surface layer to the background so that the ad player's surface
    // layer can be overlaid on top of it during ad playback.
//    contentPlayer.moveSurfaceToBackground();
    contentPlayer.hide();

    // Create the ad adDisplayContainer UI which will be used by the IMA SDK to overlay ad controls.
    adUiContainer = new FrameLayout(activity);
    container.addView(adUiContainer);
    adUiContainer.setLayoutParams(Util.getLayoutParamsBasedOnParent(
        adUiContainer,
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));


    this.originalContainerLayoutParams = container.getLayoutParams();

  }

  /**
   * @param activity The activity that will contain the video player.
   * @param container The {@link FrameLayout} which will contain the video player.
   * @param video The video that should be played.
   * @param adTagUrl The URL containing the VAST document of the ad.
   */
  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video,
                   String adTagUrl) {
    this(activity,
        container,
        video,
        ImaSdkFactory.getInstance().createImaSdkSettings(),
        adTagUrl);
  }

  /**
   * @param activity The activity that will contain the video player.
   * @param container The {@link FrameLayout} which will contain the video player.
   * @param video The video that should be played.
   */
  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video) {
    this(activity,
        container,
        video,
        ImaSdkFactory.getInstance().createImaSdkSettings(), null);
  }

  /**
   * Pause video playback.
   */
  public void pause() {
    if (adPlayer != null) {
      adPlayer.pause();
    }
    contentPlayer.pause();
  }

  /**
   * Resume video playback.
   */
  public void play() {
    if (adTagUrl != null) {
      requestAd();
    } else {
      contentPlayerContainer.setVisibility(View.VISIBLE);
      contentPlayer.play();
    }
  }

  /**
   * When you are finished using this {@link ImaPlayer}, make sure to call this method.
   */
  public void release() {
    if (adPlayer != null) {
      adPlayer.release();
      adPlayer = null;
    }
    if (adsManager != null) {
      adsManager.destroy();
      adsManager = null;
    }
    adsLoader.contentComplete();
    contentPlayer.release();
    adsLoader.removeAdsLoadedListener(adListener);
  }

  /**
   * Create a {@link SimpleVideoPlayer} to play an ad and display it.
   */
  private void createAdPlayer(){
    // Kill any existing ad player.
    destroyAdPlayer();

    // Add the ad frame layout to the adDisplayContainer that contains all the content player.
    adPlayerContainer = new SimpleExoPlayerView(activity);
    adPlayerContainer.setUseController(false);
    container.addView(adPlayerContainer);


    // Ensure tha the ad ui adDisplayContainer is the topmost view.
    container.removeView(adUiContainer);
    container.addView(adUiContainer);


    Video adVideo = new Video(adTagUrl.toString(), Video.VideoType.MP4);
    adPlayer = new SimpleVideoPlayer(activity,
        adPlayerContainer,
        adVideo,
        true,
        0);

    adPlayer.addPlaybackListener(adPlaybackListener);

    // Move the ad player's surface layer to the foreground so that it is overlaid on the content
    // player's surface layer (which is in the background).
    contentPlayer.hide();
    adPlayer.show();
    adPlayer.play();

    // Notify the callbacks that the ad has begun playing.
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onPlay();
    }
  }

  /**
   * Destroy the {@link SimpleVideoPlayer} responsible for playing the ad and remove it.
   */
  private void destroyAdPlayer(){
    if(adPlayerContainer != null){
      container.removeView(adPlayerContainer);
    }
    if (adUiContainer != null) {
      container.removeView(adUiContainer);
    }
    if(adPlayer != null){
      adPlayer.release();
    }
    adPlayerContainer = null;
    adPlayer = null;
  }

  /**
   * Pause and hide the content player.
   */
  private void hideContentPlayer(){
    contentPlayer.pause();
    contentPlayer.hide();
    contentPlayerContainer.setVisibility(View.GONE);
  }

  /**
   * Show the content player and start playing again.
   */
  private void showContentPlayer(){
    contentPlayerContainer.setVisibility(View.VISIBLE);
    contentPlayer.play();
  }

  /**
   * Pause the content player and notify the ad callbacks that the content has paused.
   */
  private void pauseContent(){
    hideContentPlayer();
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onPause();
    }
  }

  /**
   * Resume the content and notify the ad callbacks that the content has resumed.
   */
  private void resumeContent(){
    destroyAdPlayer();
    showContentPlayer();
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onResume();
    }
  }

  /**
   * Create an ads request which will request the VAST document with the given ad tag URL.
   * @param tagUrl URL pointing to a VAST document of an ad.
   * @return a request for the VAST document.
   */
  private AdsRequest buildAdsRequest(String tagUrl) {
    AdDisplayContainer adDisplayContainer = ImaSdkFactory.getInstance().createAdDisplayContainer();
    adDisplayContainer.setPlayer(videoAdPlayer);
    adDisplayContainer.setAdContainer(adUiContainer);
    AdsRequest request = ImaSdkFactory.getInstance().createAdsRequest();
    request.setAdTagUrl(tagUrl);
    request.setContentProgressProvider(contentProgressProvider);

    request.setAdDisplayContainer(adDisplayContainer);
    return request;
  }

  /**
   * Make the ads loader request an ad with the ad tag URL which this {@link ImaPlayer} was
   * created with
   */
  private void requestAd() {
    adsLoader.requestAds(buildAdsRequest(adTagUrl.toString()));
  }
}
