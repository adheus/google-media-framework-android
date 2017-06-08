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

package com.google.android.libraries.mediaframework.layeredvideo;

import android.app.Activity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;

/**
 * A video player which includes subtitle support and a customizable UI for playback control.
 *
 * <p>NOTE: If you want to get a video player up and running with minimal effort, just instantiate
 * this class and call play();
 */
public class SimpleVideoPlayer {

  /**
   * The {@link Activity} that contains this video player.
   */
  private final Activity activity;
  private final SimpleExoPlayerView container;


  private ExoplayerWrapper exoPlayer;


  /**
   * Set whether the video should play immediately.
   */
  private boolean autoplay;

  /**
   * @param activity The activity that will contain the video player.
   * @param container The {@link FrameLayout} which will contain the video player.
   * @param video The video that should be played.
   * @param autoplay Whether the video should start playing immediately.
   */
  public SimpleVideoPlayer(Activity activity,
                           SimpleExoPlayerView container,
                           Video video,
                           boolean autoplay) {
    this(activity, container, video, autoplay, 0);
  }

  /**
   * @param activity The activity that will contain the video player.
   * @param container The {@link FrameLayout} which will contain the video player.
   * @param video The video that should be played.
   * @param autoplay Whether the video should start playing immediately.
   */
  public SimpleVideoPlayer(Activity activity,
                           SimpleExoPlayerView container,
                           Video video,
                           boolean autoplay,
                           int startPostitionMs) {
    this.activity = activity;

    this.autoplay = autoplay;
    this.container = container;
    exoPlayer = new ExoplayerWrapper(activity, container);
    exoPlayer.loadVideo(video);
    exoPlayer.prepare();
    exoPlayer.seekTo(startPostitionMs);

  }



  /**
   * Set a listener which reacts to state changes, video size changes, and errors.
   * @param listener Listens to playback events.
   */
  public void addPlaybackListener(ExoplayerWrapper.PlaybackListener listener) {
    exoPlayer.addListener(listener);
  }


  /**
   * Returns the current playback position in milliseconds.
   */
  public long getCurrentPosition() {
    return exoPlayer.getCurrentPosition();
  }

  /**
   * Returns the duration of the track in milliseconds or
   * -1 if the duration is unknown.
   */
  public long getDuration() {
    return exoPlayer.getDuration();
  }

  /**
   * Fades the playback control layer out and then removes it from the
   * container.
   */
  public void hide() {
    View surfaceView = this.container.getVideoSurfaceView();
    if (surfaceView instanceof SurfaceView) {
      ((SurfaceView) surfaceView).setZOrderMediaOverlay(false);
    }
  }


  /**
   * Pause video playback.
   */
  public void pause() {
    exoPlayer.pause();
  }

  /**
   * Resume video playback.
   */
  public void play() {
    exoPlayer.play();
  }

  /**
   * Add the playback control layer back to the container. It will disappear when the user taps
   * the screen.
   */
  public void show() {
    View surfaceView = this.container.getVideoSurfaceView();
    if (surfaceView instanceof SurfaceView) {
      ((SurfaceView) surfaceView).setZOrderMediaOverlay(true);
    }
  }

  /**
   * When you are finished using this {@link SimpleVideoPlayer}, make sure to call this method.
   */
  public void release() {
    exoPlayer.release();
  }

}
