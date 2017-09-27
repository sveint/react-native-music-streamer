package proj.sveint.rnmusicstreamer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MusicStreamerNotificationReceiver extends BroadcastReceiver {

    private final String packageName;
    private final MusicStreamerService service;

    public MusicStreamerNotificationReceiver(MusicStreamerService service) {
        this.service = service;
        this.packageName = service.getPackageName();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (!intent.hasExtra("PACKAGE_NAME") ||
            !intent.getStringExtra("PACKAGE_NAME").equals(packageName)) {
            return;
        }

        if (MusicStreamerService.MEDIA_BUTTON.equals(intent.getAction())) {
            if (intent.hasExtra(MusicStreamerService.MEDIA_ACTION)) {
                String mediaAction = intent.getStringExtra(MusicStreamerService.MEDIA_ACTION);
                if (mediaAction.equals("PLAY")) {
                    service.prepare(service.getCurrentUrl());
                    service.play();
                } else if (mediaAction.equals("STOP")) {
                    service.stop();
                }
            }
        }
    }
}