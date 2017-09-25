package proj.sveint.rnmusicstreamer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.util.Log;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;


import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.util.List;
import java.util.Map;

public class MusicStreamerService extends Service implements ExoPlayer.EventListener, BandwidthMeter.EventListener, ExtractorMediaSource.EventListener, AudioManager.OnAudioFocusChangeListener {

    private final IBinder binder = new LocalBinder();

    // Notification / MediaSession
    private NotificationCompat.Builder nb;
    private MediaSessionCompat mediaSession;
    private final int N_ID = 10;
    private MetadataUpdater metadataUpdater;
    private MetadataUpdateListener metadataUpdateListener;
    private boolean metaFromStream = false;
    static final String MEDIA_BUTTON = "NRMS_MEDIA_BUTTON";
    static final String MEDIA_ACTION = "NRMS_MEDIA_ACTION";
    private MusicStreamerNotificationReceiver notificationReceiver;

    // Player
    private SimpleExoPlayer player = null;
    private String status = "STOPPED";
    private static String currentUrl;

    // Status
    private static final String PLAYING = "PLAYING";
    private static final String PAUSED = "PAUSED";
    private static final String STOPPED = "STOPPED";
    private static final String FINISHED = "FINISHED";
    private static final String BUFFERING = "BUFFERING";
    private static final String ERROR = "ERROR";
    private StatusUpdateListener statusUpdateListener;

    // System
    private AudioManager audioManager;
    private final BroadcastReceiver audioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        stop();
                    }
                }
            };


    @Override
    public void onCreate() {

        // Register for audiofocus (phone calls etc)
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        // Handle polling for metadata (title) from stream
        metadataUpdater = new MetadataUpdater();
        metadataUpdater.setMetadataUpdateListener(
            new MetadataUpdater.MetadataUpdateListener() {
                @Override
                public void callback(String result) {
                    nb.setContentTitle(result);
                    updateNotification();
                    // Return result to RN module
                    if (metadataUpdateListener != null) {
                        metadataUpdateListener.callback(result);
                    }
                }
            }
        );

        notificationReceiver = new MusicStreamerNotificationReceiver(this);
        registerReceiver(notificationReceiver, new IntentFilter(MEDIA_BUTTON));
    }

    @Override
    public void onDestroy() {
        audioManager.abandonAudioFocus(this);
        try {
            unregisterReceiver(audioNoisyReceiver);
            unregisterReceiver(notificationReceiver);
        }
        catch (IllegalArgumentException e) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("MusicStreamer", "Task removed, stopping service...");
        stopSelf(); // Stop the service as we won't need it anymore
    }

    public void prepare(String urlString) {

        this.currentUrl = urlString;

        if (player != null){
            player.stop();
            player = null;
            status = "STOPPED";
            emitStatusUpdate();
        }

        // Create player
        TrackSelector trackSelector = new DefaultTrackSelector();
        LoadControl loadControl = new DefaultLoadControl();
        this.player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, loadControl);

        // Create player source
        Handler mainHandler = new Handler();
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, this);
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, getDefaultUserAgent(), bandwidthMeter);
        MediaSource audioSource = new ExtractorMediaSource(Uri.parse(urlString), dataSourceFactory, extractorsFactory, mainHandler, this);

        // Start preparing audio
        player.prepare(audioSource);
        player.addListener(this);
        
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    
    public void setStatusUpdateListener(StatusUpdateListener listener) {
        statusUpdateListener = listener;
    }

    public void setMetadataUpdateListener(MetadataUpdateListener listener) {
        metadataUpdateListener = listener;
    }

    public void play() {
        if(player != null) {
            player.setPlayWhenReady(true);
            registerReceiver(audioNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            startForeground(N_ID, nb.build());
            if (metaFromStream) {
                metadataUpdater.start(currentUrl);
            }
        }
    }

    public void pause() {
        if(player != null) {
            player.setPlayWhenReady(false);
        }
        metadataUpdater.stop();
        stopForeground(false);
    }

    public void stop() {
        if (player != null){
            player.stop();
            player.seekTo(0);  // Position isn't reset by stop()
            status = "STOPPED";
            try {
                unregisterReceiver(audioNoisyReceiver);
            }
            catch (IllegalArgumentException e) {}  // Already unregistered
            emitStatusUpdate();
        }
        metadataUpdater.stop();
        stopForeground(false);
    }

    public void seekToTime(double time) {
        if(player != null) player.seekTo((long)time * 1000);
    }

    public double getCurrentTime() {
        if (player == null){
            return (double)0;
        } else {
            return (double)(player.getCurrentPosition()/1000);
        }
    }

    public String getStatus() {
        return status;
    }

    public double getDuration() {
        if (player == null){
            return (double)0;
        }else{
            return (double)(player.getDuration()/1000);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if(focusChange <= 0) {
            stop();
        } else {
            play();
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d("onPlayerStateChanged", ""+playbackState);

        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                status = STOPPED;
                setNotificationButton(false);
                emitStatusUpdate();
                break;
            case ExoPlayer.STATE_BUFFERING:
                status = BUFFERING;
                emitStatusUpdate();
                setNotificationButton(true);
                break;
            case ExoPlayer.STATE_READY:
                if (this.player != null && this.player.getPlayWhenReady()) {
                    status = PLAYING;
                    emitStatusUpdate();
                    setNotificationButton(true);
                } else {
                    status = PAUSED;
                    emitStatusUpdate();
                    setNotificationButton(false);
                }
                break;
            case ExoPlayer.STATE_ENDED:
                status = FINISHED;
                emitStatusUpdate();
                setNotificationButton(false);
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        status = ERROR;
        emitStatusUpdate();
    }

    @Override
    public void onPositionDiscontinuity() {}

    @Override
    public void onLoadingChanged(boolean isLoading) {
        if (isLoading == true){
            status = BUFFERING;
            emitStatusUpdate();
        }else if (this.player != null){
            if (this.player.getPlayWhenReady()) {
                status = PLAYING;
                emitStatusUpdate();
            } else {
                status = PAUSED;
                emitStatusUpdate();
            }
        }else{
            status = STOPPED;
            emitStatusUpdate();
        }
    }

    @Override
    public void onLoadError(IOException error) {
        status = ERROR;
        emitStatusUpdate();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {}

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        Log.d("ExoPlayer", "" + bytes);
    }

    private void emitStatusUpdate() {
        if (statusUpdateListener != null) {
            statusUpdateListener.callback(status);
        }
    }

    private static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        public MusicStreamerService getService() {
            return MusicStreamerService.this;
        }
    }

    public interface StatusUpdateListener {
        public void callback(String status);
    }

    public interface MetadataUpdateListener {
        public void callback(String result);
    }

    

    //
    // Notification / MediaSession
    //

    public void setNotification(String title, String artist, String album, Bitmap artwork) {

        // Create MediaSession
        //ComponentName compName = new ComponentName(this, MusicControlReceiver.class);

        mediaSession = new MediaSessionCompat(this, "MusicStreamer");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        // TODO: Add callbacks

        // Create MediaMetadata
        MediaMetadataCompat.Builder md = new MediaMetadataCompat.Builder();
        md.putText(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        md.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        md.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
        if (artwork != null) {
            md.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork);
        }
        else {
            md.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, null);
        }

        // Create media notification with the MediaMetadata
        NotificationCompat.Builder newNb = new NotificationCompat.Builder(this);
        newNb.setStyle(
            new NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
        );

        Intent openApp = getPackageManager().getLaunchIntentForPackage(getPackageName());
        newNb.setContentIntent(PendingIntent.getActivity(this, 0, openApp, 0));
        newNb.setContentTitle(title);
        newNb.setContentText(artist);
        newNb.setContentInfo(album);
        if (artwork != null) {
            newNb.setLargeIcon(artwork);
        }
        else {
            newNb.setLargeIcon(null);
        }
        newNb.setSmallIcon(getResources().getIdentifier("play_white", "drawable", getPackageName()));
        newNb.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        newNb.setShowWhen(false);

        nb = newNb;
        mediaSession.setMetadata(md.build());
        mediaSession.setActive(true);

    }

    public void setNotificationButton(boolean isPlaying) {
        Intent intent = new Intent(MEDIA_BUTTON);
        intent.putExtra(MEDIA_ACTION, isPlaying ? "STOP" : "PLAY");
        PendingIntent pi = PendingIntent.getBroadcast(this, 123, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        nb.mActions.clear();
        int actionIcon = getResources().getIdentifier(isPlaying ? "stop" : "play", "drawable", getPackageName());
        nb.addAction(actionIcon, isPlaying ? "Stop" : "Play", pi);

        // we need to set the style again due to setShowActionInCompactView...
        nb.setStyle(
            new NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0)
        );
        updateNotification();
    }

    public void enableMetadataFromStream(boolean enable) {
        metaFromStream = enable;
    }

    public void updateNotification() {
        getSystemService(NotificationManager.class).notify(N_ID, nb.build());
    }

}