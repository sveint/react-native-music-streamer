package proj.sveint.rnmusicstreamer;

import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;

import java.lang.Math;
import java.lang.Exception;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.List;

public class RNMusicStreamerModule extends ReactContextBaseJavaModule {

    MusicStreamerService musicService = null;
    private ReactApplicationContext reactContext = null;
    private MusicStreamerService.StatusUpdateListener updateListener = null;
    private MusicStreamerService.MetadataUpdateListener metadataListener = null;

    private Thread artworkThread;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            MusicStreamerService.LocalBinder binder = (MusicStreamerService.LocalBinder) service;
            musicService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            musicService = null;
        }
    };

    public RNMusicStreamerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        Intent intent = new Intent(this.reactContext, MusicStreamerService.class);
        this.reactContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        final ReactApplicationContext ctx = reactContext;
        this.updateListener = new MusicStreamerService.StatusUpdateListener() {
            @Override
            public void callback(String status) {
                ctx
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("RNMusicStreamerStatusChanged", status);
            }
        };
        
        this.metadataListener = new MusicStreamerService.MetadataUpdateListener() {
            @Override
            public void callback(String result) {
                ctx
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("RNMusicStreamerMetadataChanged", result);
            }
        };
    }

    @Override
    public String getName() {
        return "RNMusicStreamer";
    }

    @ReactMethod
    public void prepare(String urlString, ReadableMap metadata) {
        
        final String title = metadata.hasKey("title") ? metadata.getString("title") : null;
        final String artist = metadata.hasKey("artist") ? metadata.getString("artist") : null;
        final String album = metadata.hasKey("album") ? metadata.getString("album") : null;
        boolean metaFromStream = metadata.hasKey("metadataFromStream") ? metadata.getBoolean("metadataFromStream") : false;
        
        if (musicService != null) {
            musicService.setStatusUpdateListener(this.updateListener);
            musicService.setMetadataUpdateListener(this.metadataListener);
            musicService.prepare(urlString);
            musicService.enableMetadataFromStream(metaFromStream);
            musicService.setNotification(title, artist, album, null);
            
            String artwork = null;
            boolean localArtwork = false;
            if(metadata.hasKey("artwork")) {
                if(metadata.getType("artwork") == ReadableType.Map) {
                    artwork = metadata.getMap("artwork").getString("uri");
                    localArtwork = true;
                } else {
                    artwork = metadata.getString("artwork");
                }
            }
            
            if(artwork != null) {
                final String artworkUrl = artwork;
                final boolean artworkLocal = localArtwork;

                if(artworkThread != null && artworkThread.isAlive()) artworkThread.interrupt();

                artworkThread = new Thread(new Runnable() {

                    private Bitmap createSquaredBitmap(Bitmap srcBmp) {
                        int dim = Math.max(srcBmp.getWidth(), srcBmp.getHeight());
                        Bitmap dstBmp = Bitmap.createBitmap(dim, dim, Config.ARGB_8888);

                        Canvas canvas = new Canvas(dstBmp);
                        canvas.drawColor(Color.WHITE);
                        canvas.drawBitmap(srcBmp, (dim - srcBmp.getWidth()) / 2, (dim - srcBmp.getHeight()) / 2, null);

                        return dstBmp;
                    }

                    @Override
                    public void run() {
                        Bitmap bitmap = loadArtwork(artworkUrl, artworkLocal);
                        if (bitmap != null) {
                            bitmap = createSquaredBitmap(bitmap);
                        }
                        musicService.setNotification(title, artist, album, bitmap);
                        musicService.updateNotification();
                        artworkThread = null;
                    }
                });
                artworkThread.start();
            }
        }

        
    }

    @ReactMethod
    public void getCurrentUrl(Promise promise) {
        try {
            promise.resolve(musicService.getCurrentUrl());
        }
        catch (Exception e) {
            promise.reject("Error", e);
        }
    }

    @ReactMethod
    public void play() {
        if (musicService != null) {
            musicService.play();
        }
    }

    @ReactMethod
    public void pause() {
        if (musicService != null) musicService.pause();
    }

    @ReactMethod
    public void stop() {
        if (musicService != null) {
            musicService.stop();
        }
    }

    @ReactMethod
    public void seekToTime(double time) {
        if (musicService != null) musicService.seekToTime(time);
    }

    @ReactMethod
    public void currentTime(Promise promise) {
        try {
            promise.resolve(musicService.getCurrentTime());
        }
        catch (Exception e) {
            promise.reject("Error", e);
        }
    }

    @ReactMethod
    public void status(Promise promise) {
        try {
            promise.resolve(musicService.getStatus());
        }
        catch (Exception e) {
            promise.reject("Error", e);
        }
    }

    @ReactMethod
    public void duration(Promise promise) {
        try {
            promise.resolve(musicService.getDuration());
        }
        catch (Exception e) {
            promise.reject("Error", e);
        }
    }


    private Bitmap loadArtwork(String url, boolean local) {
        Bitmap bitmap = null;

        try {
            if(local) {

                // Gets the drawable from the RN's helper for local resources
                ResourceDrawableIdHelper helper = ResourceDrawableIdHelper.getInstance();
                Drawable image = helper.getResourceDrawable(getReactApplicationContext(), url);

                if(image instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable)image).getBitmap();
                } else {
                    bitmap = BitmapFactory.decodeFile(url);
                }

            } else {

                // Open connection to the URL and decodes the image
                URLConnection con = new URL(url).openConnection();
                con.setConnectTimeout(2000);
                con.setReadTimeout(10000);
                con.connect();
                InputStream input = con.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);
                input.close();

            }
        } catch(IOException ex) {
            Log.w("MusicControl", "Could not load the artwork", ex);
        }
        
        return bitmap;
    }
}