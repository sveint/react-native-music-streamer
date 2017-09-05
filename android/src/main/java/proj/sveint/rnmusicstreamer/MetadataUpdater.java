package proj.sveint.rnmusicstreamer;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;


import java.io.IOException;
import java.net.URL;

public class MetadataUpdater {

    private final Handler metadataHandler = new Handler();
    private MetadataUpdateListener listener;
    private Runnable runnable;

    public void setMetadataUpdateListener(MetadataUpdateListener listener) {
        this.listener = listener;
    }

    public void start(final String streamUrl) {
        stop();
        
        runnable = new Runnable() {
            @Override
            public void run() {
                new UpdateMetadataTask().execute(streamUrl);
                metadataHandler.postDelayed(runnable, 10000);
            }
        };
        metadataHandler.postDelayed(runnable, 10);
    }

    public void stop() {
        metadataHandler.removeCallbacks(runnable, null);
    }

    public interface MetadataUpdateListener {
        public void callback(String result);
    }

    class UpdateMetadataTask extends AsyncTask<String, Void, String> {
        
        protected String doInBackground(String... url) {
            if (url.length > 0) {
                try {
                    IcyStreamMeta icy = new IcyStreamMeta(new URL(url[0]));
                    icy.refreshMeta();
                    return icy.getFullTitle();
                }
                catch(IOException e) {
                    Log.e("MusicStreamer", "Error while fetching metadata", e);
                }
            }
            return null;
        }

        protected void onPostExecute(String result) {
            if (result != null) {
                listener.callback(result);
            }
        }
    }

    
}

