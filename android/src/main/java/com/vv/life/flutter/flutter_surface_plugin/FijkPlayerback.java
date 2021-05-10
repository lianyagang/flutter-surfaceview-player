//MIT License
//
//Copyright (c) [2019-2020] [Befovy]
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all
//copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//SOFTWARE.

package com.vv.life.flutter.flutter_surface_plugin;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoViewBridge;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.TextureRegistry;

public class FijkPlayerback extends LifeVideoView implements MethodChannel.MethodCallHandler {
    /**
     * surface 返回渲染的Id
     */
    final private int mPlayerId;
    /**
     * 回调给Flutter的数据
     */
    final private EventChannel mEventChannel;
    /**
     * Flutter唤起原生的操作方法
     */
    final private MethodChannel mMethodChannel;
    /**
     * 不知道干嘛用的，反正通过这个进行回调
     */
    final private QueuingEventSink mEventSink = new QueuingEventSink();
    /**
     * 需要注册渲染的SurfaceTexture
     */
    private TextureRegistry.SurfaceTextureEntry mSurfaceTextureEntry;
    /**
     * 从Flutter传递布局进行原生Id的渲染
     */
    private SurfaceTexture mSurfaceTexture;

    private Surface mSurface;

    final Context mContext;

    final private HostOption mHostOptions = new HostOption();

    /**
     * Flutter 需要的参数引擎
     */
    final private FijkEngine mEngine;
    //===============这里是回调给Flutter的状态=================start

    final private static int idle = 0;
    final private static int initialized = 1;
    final private static int asyncPreparing = 2;
    @SuppressWarnings("unused")
    final private static int prepared = 3;
    @SuppressWarnings("unused")
    final private static int started = 4;
    final private static int paused = 5;
    final private static int completed = 6;
    final private static int stopped = 7;
    @SuppressWarnings("unused")
    final private static int error = 8;
    final private static int end = 9;
    //===============这里是回调给Flutter的状态=================end


    FijkPlayerback(@NonNull FijkEngine engine) {
        super(engine.context());
        mContext = engine.context();
        mEngine = engine;
        mPlayerId = 0;
        // IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_INFO);
        mMethodChannel = new MethodChannel(mEngine.messenger(), "befovy.com/fijkplayer/" + mPlayerId);
        mMethodChannel.setMethodCallHandler(this);

        mEventChannel = new EventChannel(mEngine.messenger(), "befovy.com/fijkplayer/event/" + mPlayerId);
        mEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, EventChannel.EventSink eventSink) {
                mEventSink.setDelegate(eventSink);
            }

            @Override
            public void onCancel(Object o) {
                mEventSink.setDelegate(null);
            }
        });
    }

    int getPlayerId() {
        return mPlayerId;
    }

    /**
     * 关联原生TextureId到Flutter的Surface画布
     * 这里地方的关联 需要考虑到时机问题
     *
     * @return
     */
    long setupSurface() {
        if (mSurfaceTextureEntry == null) {
            TextureRegistry.SurfaceTextureEntry surfaceTextureEntry = mEngine.createSurfaceEntry();
            mSurfaceTextureEntry = surfaceTextureEntry;
            if (surfaceTextureEntry != null) {
                mSurfaceTexture = surfaceTextureEntry.surfaceTexture();
                mSurface = new Surface(mSurfaceTexture);
            }
            getGSYVideoManager().setDisplay(mSurface);
        }
        if (mSurfaceTextureEntry != null)
            return mSurfaceTextureEntry.id();
        else {
            Log.e("FIJKPLAYER", "setup surface, null SurfaceTextureEntry");
            return 0;
        }
    }


    @Override
    protected void releaseVideos() {
        GSYVideoManager.releaseAllVideos();
        if (mSurfaceTextureEntry != null) {
            mSurfaceTextureEntry.release();
            mSurfaceTextureEntry = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        mMethodChannel.setMethodCallHandler(null);
        mEventChannel.setStreamHandler(null);
    }

    @Override
    protected void setStateAndUi(int state) {
        ///这里更新UI操作 设置播放显示状态
        Log.e(TAG, "setStateAndUi>>state: " + state);
        resolveUIState(state, -1, -1);
    }

    /**
     * 处理控制显示
     *
     * @param state
     */
    protected void resolveUIState(int state, int arg1, int arg2) {
        Map<String, Object> event = new HashMap<>();
        switch (state) {
            case CURRENT_STATE_PREPAREING:
                event.put("event", "prepared");
                long duration = getDuration();
                event.put("duration", duration);
                mEventSink.success(event);
                break;
            case CURRENT_STATE_NORMAL:
                event.put("event", "state_change");
                event.put("new", arg1);
                event.put("old", arg2);
                onStateChanged(arg1, arg1);
                mEventSink.success(event);
                break;
            case CURRENT_STATE_PLAYING:
                break;
            case CURRENT_STATE_PAUSE:
                break;
            case CURRENT_STATE_ERROR:
                break;
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                event.put("event", "freeze");
                event.put("value", true);
                mEventSink.success(event);
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                break;
            case CURRENT_BUFFERING_UPDATE:
                event.put("event", "buffering");
                event.put("percent", arg2);
                mEventSink.success(event);
                break;
        }
    }


    private void onStateChanged(int newState, int oldState) {
        if (newState == started && oldState != started) {
            mEngine.onPlayingChange(1);

            if (mHostOptions.getIntOption(HostOption.REQUEST_AUDIOFOCUS, 0) == 1) {
                mEngine.audioFocus(true);
            }

            if (mHostOptions.getIntOption(HostOption.REQUEST_SCREENON, 0) == 1) {
                mEngine.setScreenOn(true);
            }
        } else if (newState != started && oldState == started) {
            mEngine.onPlayingChange(-1);

            if (mHostOptions.getIntOption(HostOption.RELEASE_AUDIOFOCUS, 0) == 1) {
                mEngine.audioFocus(false);
            }

            if (mHostOptions.getIntOption(HostOption.REQUEST_SCREENON, 0) == 1) {
                mEngine.setScreenOn(false);
            }
        }

        if (isPlayable(newState) && !isPlayable(oldState)) {
            mEngine.onPlayableChange(1);
        } else if (!isPlayable(newState) && isPlayable(oldState)) {
            mEngine.onPlayableChange(-1);
        }
    }

    private boolean isPlayable(int state) {
        return state == started || state == paused || state == completed || state == prepared;
    }
    /**
     * 提供一个GsyManager 用来控制播放器
     *
     * @return
     */
    @Override
    public GSYVideoViewBridge getGSYVideoManager() {
        GSYVideoManager.instance().initContext(mContext);
        return GSYVideoManager.instance();
    }

    /**
     * 开始播放
     */
    @Override
    public void startPlayLogic() {
        if (mVideoAllCallBack != null) {
            Debuger.printfLog("onClickStartThumb");
            mVideoAllCallBack.onClickStartThumb(mOriginUrl, mTitle, FijkPlayerback.this);
        }
        prepareVideo();
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (call.method.equals("setupSurface")) {
            long viewId = setupSurface();
            result.success(viewId);
        } else if (call.method.equals("setDataSource")) {
            ///设置播放路径
            String url = call.argument("url");
            setUp(url, true, "xxs");
            resolveUIState(CURRENT_STATE_NORMAL, initialized, -1);
            if (mContext == null) {
                resolveUIState(CURRENT_STATE_ERROR, error, -1);
            }
            result.success(null);
        } else if (call.method.equals("prepareAsync")) {
            prepareVideo();
            resolveUIState(CURRENT_STATE_NORMAL, asyncPreparing, -1);
            result.success(null);
        } else if (call.method.equals("start")) {
            startPlayLogic();
            result.success(null);
        } else if (call.method.equals("pause")) {
            onVideoPause();
            result.success(null);
        } else if (call.method.equals("stop")) {
            onVideoPause();
            resolveUIState(CURRENT_STATE_NORMAL, stopped, -1);
            result.success(null);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public void onBufferingUpdate(int percent) {
        resolveUIState(CURRENT_BUFFERING_UPDATE, -1, percent);
    }

    @Override
    public void onBackFullscreen() {

    }

}
