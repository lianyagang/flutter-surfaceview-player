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
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.TextureRegistry;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

public class FijkPlayer implements MethodChannel.MethodCallHandler, IMediaPlayer.OnInfoListener, IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnPreparedListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnErrorListener, IMediaPlayer.OnCompletionListener {

    final private static AtomicInteger atomicId = new AtomicInteger(0);

    final private static int BUFFERING_UPDATE = 502;
    final private static int VIDEO_SIZE_CHANGED = 400;
    final private static int PREPARED = 200;
    final private static int VIDEO_RENDERING_START = 402;
    final private static int PLAYBACK_STATE_CHANGED = 700;
    final private static int BUFFERING_START = 500;
    final private static int BUFFERING_END = 501;
    final private static int VIDEO_ROTATION_CHANGED = 404;
    final private static int SEEK_COMPLETE = 600;
    final private static int ERROR = 100;
    final private static int CURRENT_POSITION_UPDATE = 510;

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

    final private int mPlayerId;
    final private IjkMediaPlayer mIjkMediaPlayer;
    final private FijkEngine mEngine;
    // non-local field prevent GC
    final private EventChannel mEventChannel;

    // non-local field prevent GC
    final private MethodChannel mMethodChannel;

    final private QueuingEventSink mEventSink = new QueuingEventSink();
    final private HostOption mHostOptions = new HostOption();

    private int mState;
    private int mRotate = -1;
    private int mWidth = 0;
    private int mHeight = 0;
    private TextureRegistry.SurfaceTextureEntry mSurfaceTextureEntry;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    final private boolean mJustSurface;
    FijkHandler handler;

    FijkPlayer(@NonNull FijkEngine engine, boolean justSurface) {
        mEngine = engine;
        handler = new FijkHandler(this);
        mPlayerId = atomicId.incrementAndGet();
        mState = 0;
        mJustSurface = justSurface;
        if (justSurface) {
            mIjkMediaPlayer = null;
            mEventChannel = null;
            mMethodChannel = null;
        } else {
            mIjkMediaPlayer = new IjkMediaPlayer();
            mIjkMediaPlayer.setOnInfoListener(this);
            mIjkMediaPlayer.setOnBufferingUpdateListener(this);
            mIjkMediaPlayer.setOnVideoSizeChangedListener(this);
            mIjkMediaPlayer.setOnSeekCompleteListener(this);
            mIjkMediaPlayer.setOnPreparedListener(this);
            mIjkMediaPlayer.setOnCompletionListener(this);
            mIjkMediaPlayer.setOnErrorListener(this);
            mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-position-notify", 1);
            mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);

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
    }

    int getPlayerId() {
        return mPlayerId;
    }

    void setup() {
        if (mJustSurface)
            return;
        if (mHostOptions.getIntOption(HostOption.ENABLE_SNAPSHOT, 0) > 0) {
            mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2");
        }
    }

    long setupSurface() {
        setup();
        if (mSurfaceTextureEntry == null) {
            TextureRegistry.SurfaceTextureEntry surfaceTextureEntry = mEngine.createSurfaceEntry();
            mSurfaceTextureEntry = surfaceTextureEntry;
            if (surfaceTextureEntry != null) {
                mSurfaceTexture = surfaceTextureEntry.surfaceTexture();
                mSurface = new Surface(mSurfaceTexture);
            }
            if (!mJustSurface) {
                mIjkMediaPlayer.setSurface(mSurface);
            }
        }
        if (mSurfaceTextureEntry != null)
            return mSurfaceTextureEntry.id();
        else {
            Log.e("FIJKPLAYER", "setup surface, null SurfaceTextureEntry");
            return 0;
        }
    }

    void release() {
        if (!mJustSurface) {
            handleEvent(PLAYBACK_STATE_CHANGED, end, mState, null);
            mIjkMediaPlayer.release();
            cancelProgressTimer();
        }
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
        if (!mJustSurface) {
            mMethodChannel.setMethodCallHandler(null);
            mEventChannel.setStreamHandler(null);
        }
    }

    private boolean isPlayable(int state) {
        return state == started || state == paused || state == completed || state == prepared;
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

    private void handleEvent(int what, int arg1, int arg2, Object extra) {
        Map<String, Object> event = new HashMap<>();

        switch (what) {
            ///哪个时机获取视频长度比较合适
            case PREPARED:
                event.put("event", "prepared");
                long duration = mIjkMediaPlayer.getDuration();
                event.put("duration", duration);
                mEventSink.success(event);
                break;
            case PLAYBACK_STATE_CHANGED:
                mState = arg1;
                event.put("event", "state_change");
                event.put("new", arg1);
                event.put("old", arg2);
                onStateChanged(arg1, arg2);
                mEventSink.success(event);
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
            case VIDEO_RENDERING_START:
                event.put("event", "rendering_start");
                event.put("type", what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START ? "video" : "audio");
                mEventSink.success(event);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case BUFFERING_START:
            case BUFFERING_END:
                event.put("event", "freeze");
                event.put("value", what == IMediaPlayer.MEDIA_INFO_BUFFERING_START);
                mEventSink.success(event);
                break;
            //通过BufferListener回调
            case BUFFERING_UPDATE:
                event.put("event", "buffering");
                event.put("head", arg1);
                event.put("percent", arg2);
                mEventSink.success(event);
                break;
            //通过handler回调获取
            case CURRENT_POSITION_UPDATE:
                event.put("event", "pos");
                event.put("pos", arg1);
                mEventSink.success(event);
                break;
            //通过OnVideoSizeChangedListener回调获取
            case VIDEO_ROTATION_CHANGED:
                event.put("event", "rotate");
                event.put("degree", arg1);
                mRotate = arg1;
                mEventSink.success(event);
                if (mWidth > 0 && mHeight > 0) {
                    handleEvent(VIDEO_SIZE_CHANGED, mWidth, mHeight, null);
                }
                break;
            //通过OnVideoSizeChangedListener回调获取
            case VIDEO_SIZE_CHANGED:
                event.put("event", "size_changed");
                if (mRotate == 0 || mRotate == 180) {
                    event.put("width", arg1);
                    event.put("height", arg2);
                    mEventSink.success(event);
                } else if (mRotate == 90 || mRotate == 270) {
                    event.put("width", arg2);
                    event.put("height", arg1);
                    mEventSink.success(event);
                }
                // default mRotate is -1 which means unknown
                // do not send event if mRotate is unknown
                mWidth = arg1;
                mHeight = arg2;
                break;
            ///通过OnSeekCompleteListener回调获取
            case SEEK_COMPLETE:
                event.put("event", "seek_complete");
                event.put("pos", arg1);
                event.put("err", arg2);
                mEventSink.success(event);
                break;
            //通过OnErrorListener回调获取
            case ERROR:
                mEventSink.error(String.valueOf(arg1), extra.toString(), arg2);
                break;
            default:
                // Log.d("FLUTTER", "jonEvent:" + what);
                break;
        }
    }

    private void applyOptions(Object options) {
        if (options instanceof Map) {
            Map optionsMap = (Map) options;
            for (Object o : optionsMap.keySet()) {
                Object option = optionsMap.get(o);
                if (o instanceof Integer && option instanceof Map) {
                    int cat = (Integer) o;
                    Map optionMap = (Map) option;
                    for (Object key : optionMap.keySet()) {
                        Object value = optionMap.get(key);
                        if (key instanceof String && cat != 0) {
                            String name = (String) key;
                            if (value instanceof Integer) {
                                mIjkMediaPlayer.setOption(cat, name, (Integer) value);
                            } else if (value instanceof String) {
                                mIjkMediaPlayer.setOption(cat, name, (String) value);
                            }
                        } else if (key instanceof String) {
                            // cat == 0, hostCategory
                            String name = (String) key;
                            if (value instanceof Integer) {
                                mHostOptions.addIntOption(name, (Integer) value);
                            } else if (value instanceof String) {
                                mHostOptions.addStrOption(name, (String) value);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        //noinspection IfCanBeSwitch
        if (call.method.equals("setupSurface")) {
            long viewId = setupSurface();
            result.success(viewId);
        } else if (call.method.equals("setOption")) {
            Integer category = call.argument("cat");
            final String key = call.argument("key");
            if (call.hasArgument("long")) {
                final Integer value = call.argument("long");
                if (category != null && category != 0) {
                    mIjkMediaPlayer.setOption(category, key, value != null ? value.longValue() : 0);
                } else if (category != null) {
                    // cat == 0, hostCategory
                    mHostOptions.addIntOption(key, value);
                }
            } else if (call.hasArgument("str")) {
                final String value = call.argument("str");
                if (category != null && category != 0) {
                    mIjkMediaPlayer.setOption(category, key, value);
                } else if (category != null) {
                    // cat == 0, hostCategory
                    mHostOptions.addStrOption(key, value);
                }
            } else {
                Log.w("FIJKPLAYER", "error arguments for setOptions");
            }
            result.success(null);
        } else if (call.method.equals("applyOptions")) {
            applyOptions(call.arguments);
            result.success(null);
        } else if (call.method.equals("setDataSource")) {
            String url = call.argument("url");
            Uri uri = Uri.parse(url);
            boolean openAsset = false;
            if ("asset".equals(uri.getScheme())) {
                openAsset = true;
                String host = uri.getHost();
                String path = uri.getPath() != null ? uri.getPath().substring(1) : "";
                String asset = mEngine.lookupKeyForAsset(path, host);
                if (!TextUtils.isEmpty(asset)) {
                    uri = Uri.parse(asset);
                }
            }
            try {
                Context context = mEngine.context();
                if (openAsset && context != null) {
                    AssetManager assetManager = context.getAssets();
                    InputStream is = assetManager.open(uri.getPath() != null ? uri.getPath() : "", AssetManager.ACCESS_RANDOM);
                    mIjkMediaPlayer.setDataSource(new RawMediaDataSource(is));
                } else if (context != null) {
                    if (TextUtils.isEmpty(uri.getScheme()) || "file".equals(uri.getScheme())) {
                        String path = uri.getPath() != null ? uri.getPath() : "";
                        IMediaDataSource dataSource = new FileMediaDataSource(new File(path));
                        mIjkMediaPlayer.setDataSource(dataSource);
                    } else {
                        mIjkMediaPlayer.setDataSource(mEngine.context(), uri);
                    }
                } else {
                    Log.e("FIJKPLAYER", "context null, can't setDataSource");
                }
                handleEvent(PLAYBACK_STATE_CHANGED, initialized, -1, null);
                if (context == null) {
                    handleEvent(PLAYBACK_STATE_CHANGED, error, -1, null);
                }
                result.success(null);
            } catch (FileNotFoundException e) {
                result.error("-875574348", "Local File not found:" + e.getMessage(), null);
            } catch (IOException e) {
                result.error("-1162824012", "Local IOException:" + e.getMessage(), null);
            }
        } else if (call.method.equals("prepareAsync")) {
            startProgressTimer();
            setup();
            mIjkMediaPlayer.prepareAsync();
            Log.e("onMethodCall: ", "prepareAsync");
            handleEvent(PLAYBACK_STATE_CHANGED, asyncPreparing, -1, null);
            result.success(null);
        } else if (call.method.equals("start")) {
            startProgressTimer();
            mIjkMediaPlayer.start();
            handleEvent(PLAYBACK_STATE_CHANGED, started, -1, null);
            result.success(null);
        } else if (call.method.equals("pause")) {
            mIjkMediaPlayer.pause();
            handleEvent(PLAYBACK_STATE_CHANGED, paused, -1, null);
            result.success(null);
        } else if (call.method.equals("stop")) {
            mIjkMediaPlayer.stop();
            handleEvent(PLAYBACK_STATE_CHANGED, stopped, -1, null);
            result.success(null);
        } else if (call.method.equals("reset")) {
            mIjkMediaPlayer.reset();
            handleEvent(PLAYBACK_STATE_CHANGED, idle, -1, null);
            result.success(null);
        } else if (call.method.equals("getCurrentPosition")) {
            long pos = mIjkMediaPlayer.getCurrentPosition();
            result.success(pos);
        } else if (call.method.equals("setVolume")) {
            final Double volume = call.argument("volume");
            float vol = volume != null ? volume.floatValue() : 1.0f;
            mIjkMediaPlayer.setVolume(vol, vol);
            result.success(null);
        } else if (call.method.equals("seekTo")) {
            final Integer msec = call.argument("msec");
            if (mState == completed)
                handleEvent(PLAYBACK_STATE_CHANGED, paused, -1, null);
            mIjkMediaPlayer.seekTo(msec != null ? msec.longValue() : 0);
            result.success(null);
        } else if (call.method.equals("setLoop")) {
            final boolean loopEnable = call.argument("loop");
            mIjkMediaPlayer.setLooping(loopEnable);
            result.success(null);
        } else if (call.method.equals("setSpeed")) {
            final Double speed = call.argument("speed");
            mIjkMediaPlayer.setSpeed(speed != null ? speed.floatValue() : 1.0f);
            result.success(null);
        } else if (call.method.equals("snapshot")) {
            mMethodChannel.invokeMethod("_onSnapshot", "not support");

            result.success(null);
        } else {
            result.notImplemented();
        }
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, final int what, final int extra) {
        /** 需要根据这些code 来判断当前播放的业务顺序状态 以下是刚开始播放时候的状态=====》》》注意需要对应匹配handleEvent里面的业务状态
         * MEDIA_INFO_OPEN_INPUT 10005
         * MEDIA_INFO_FIND_STREAM_INFO 10006
         * MEDIA_INFO_COMPONENT_OPEN 10007
         * MEDIA_INFO_VIDEO_ROTATION_CHANGED 10001
         * MEDIA_INFO_BUFFERING_START 701
         * MEDIA_INFO_VIDEO_DECODED_START 10004
         * MEDIA_INFO_BUFFERING_END 702
         * MEDIA_INFO_AUDIO_DECODED_START 10003
         * MEDIA_INFO_AUDIO_RENDERING_START 10002
         * MEDIA_INFO_VIDEO_RENDERING_START 3
         */
        /**
         * 如果拖动进度条回调如下
         *
         */
        Log.e("onInfo: what>>>>>>", what + ">>>>extra"+extra);
        if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            handleEvent(VIDEO_ROTATION_CHANGED, extra, extra, extra);
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START || what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            handleEvent(PLAYBACK_STATE_CHANGED, started, -1, null);
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {

        } else {
            handleEvent(what, -1, -1, extra);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, final int percent) {
        handleEvent(BUFFERING_UPDATE, percent, (int) iMediaPlayer.getCurrentPosition(), null);
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
        handleEvent(VIDEO_SIZE_CHANGED, width, height, null);
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        handleEvent(PREPARED, -1, -1, null);
        handleEvent(PLAYBACK_STATE_CHANGED, prepared, -1, null);
        handleEvent(PLAYBACK_STATE_CHANGED, started, prepared, null);
    }

    @Override
    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
        handleEvent(SEEK_COMPLETE, (int) iMediaPlayer.getCurrentPosition(), -1, null);
    }

    @Override
    public boolean onError(IMediaPlayer mp, final int what, final int extra) {
        handleEvent(ERROR, what, what, extra);
        return false;
    }

    ///启动定时器 或者暂停定时器
    protected boolean mPostProgress = false;

    /**
     * 获取当前播放进度的定时器、由于0.8版本没有进度回调，所以使用这么low B的方案
     */
    protected void startProgressTimer() {
        cancelProgressTimer();
        mPostProgress = true;
        handler.sendEmptyMessageDelayed(GET_DURATION_CODE, 300);
    }

    protected void cancelProgressTimer() {
        mPostProgress = false;
        handler.removeCallbacksAndMessages(null);
        handler.removeMessages(GET_DURATION_CODE);
    }


    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
        int currentPosition = (int) mIjkMediaPlayer.getDuration();
        handleEvent(CURRENT_POSITION_UPDATE, currentPosition, -1, null);
        handleEvent(PLAYBACK_STATE_CHANGED, completed, -1, null);
        cancelProgressTimer();
    }

    public static final int GET_DURATION_CODE = 100001;//获取当前播放进度
    public static final int GET_DURATION_DELAY = 1000;//获取当前播放进度

    /**
     * 获取播放时长
     */
    private static class FijkHandler extends Handler {
        final WeakReference<FijkPlayer> mWeakReference;

        private FijkHandler(FijkPlayer mActivity) {
            mWeakReference = new WeakReference<>(mActivity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == GET_DURATION_CODE) {
                FijkPlayer fijkPlayer = mWeakReference.get();
                if (fijkPlayer == null) {
                    return;
                }
                Log.e("handleMessage: ", fijkPlayer.mState + ">>>");
                if (fijkPlayer.mState == started || fijkPlayer.mState == prepared || fijkPlayer.mState == asyncPreparing) {
                    int currentPosition = (int) fijkPlayer.mIjkMediaPlayer.getCurrentPosition();
                    fijkPlayer.handleEvent(CURRENT_POSITION_UPDATE, currentPosition, -1, null);
                }
                if (fijkPlayer.mPostProgress) {
                    sendEmptyMessageDelayed(GET_DURATION_CODE, GET_DURATION_DELAY);
                }
            }

        }
    }
}

