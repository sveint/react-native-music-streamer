/**
 From https://stackoverflow.com/q/8970548
*/

package proj.sveint.rnmusicstreamer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.String;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IcyStreamMeta {

    protected URL streamUrl;
    private Map<String, String> metadata;
    private boolean isError;

    public IcyStreamMeta(URL streamUrl) {
        setStreamUrl(streamUrl);

        isError = false;
    }

    /**
     * Get artist using stream's title
     *
     * @return String
     * @throws IOException
     */
    public String getArtist() throws IOException {
        Map<String, String> data = getMetadata();

        if (!data.containsKey("StreamTitle"))
            return "";

        if (!data.containsKey("-"))
            return "";


        String streamTitle = data.get("StreamTitle");
        String title = streamTitle.substring(0, streamTitle.indexOf("-"));
        return title.trim();
    }

    /**
     * Get title using stream's title
     *
     * @return String
     * @throws IOException
     */
    public String getTitle() throws IOException {
        Map<String, String> data = getMetadata();

        if (!data.containsKey("StreamTitle"))
            return "";

        String streamTitle = data.get("StreamTitle");
        String artist = streamTitle.substring(streamTitle.indexOf("-")+1);
        return artist.trim();
    }

    /**
     * Get title using stream's title
     *
     * @return String
     * @throws IOException
     */
    public String getFullTitle() throws IOException {
        Map<String, String> data = getMetadata();

        if (!data.containsKey("StreamTitle"))
            return "";

        return data.get("StreamTitle").trim();
    }

    public Map<String, String> getMetadata() throws IOException {
        if (metadata == null) {
            refreshMeta();
        }

        return metadata;
    }

    public void refreshMeta() throws IOException {
        retreiveMetadata();
    }

    private void retreiveMetadata() throws IOException {
        URLConnection con = streamUrl.openConnection();
        con.setReadTimeout(2000);  // 2 sec timeout
        con.setConnectTimeout(2000);
        con.setRequestProperty("Icy-MetaData", "1");
        con.setRequestProperty("Connection", "close");
        con.setRequestProperty("Accept", null);
        con.connect();

        int metaDataOffset = 0;
        Map<String, List<String>> headers = con.getHeaderFields();
        InputStream stream = con.getInputStream();

        if (headers.containsKey("icy-metaint")) {
            // Headers are sent via HTTP
            metaDataOffset = Integer.parseInt(headers.get("icy-metaint").get(0));
        } else {
            // Headers are sent within a stream
            StringBuilder strHeaders = new StringBuilder();
            char c;
            while ((c = (char)stream.read()) != -1) {
                strHeaders.append(c);
                if (strHeaders.length() > 5 && (strHeaders.substring((strHeaders.length() - 4), strHeaders.length()).equals("\r\n\r\n"))) {
                    // end of headers
                    break;
                }
            }

            // Match headers to get metadata offset within a stream
            Pattern p = Pattern.compile("\\r\\n(icy-metaint):\\s*(.*)\\r\\n");
            Matcher m = p.matcher(strHeaders.toString());
            if (m.find()) {
                metaDataOffset = Integer.parseInt(m.group(2));
            }
        }

        // In case no data was sent
        if (metaDataOffset == 0) {
            isError = true;
            return;
        }

        // Read metadata
        int b;
        int count = 0;
        int metaDataLength = 4080; // 4080 is the max length
        boolean inData = false;
        ByteBuffer metaDataRaw = ByteBuffer.allocate(4080);
        // Stream position should be either at the beginning or right after headers
        while ((b = stream.read()) != -1) {
            count++;

            // Length of the metadata
            if (count == metaDataOffset + 1) {
                metaDataLength = b * 16;
            }

            if (count > metaDataOffset + 1 && count < (metaDataOffset + metaDataLength)) {
                inData = true;
            } else {
                inData = false;
            }
            if (inData) {
                if (b != 0) {
                    metaDataRaw.put((byte)b);
                }
            }
            if (count > (metaDataOffset + metaDataLength)) {
                break;
            }

        }

        String metaData = new String(
            metaDataRaw.array(),
            metaDataRaw.arrayOffset(),
            metaDataRaw.position() - metaDataRaw.arrayOffset(),
            Charset.forName("UTF-8")
        );
        // Set the data
        metadata = IcyStreamMeta.parseMetadata(metaData);

        // Close
        stream.close();
    }

    public boolean isError() {
        return isError;
    }

    public URL getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(URL streamUrl) {
        this.metadata = null;
        this.streamUrl = streamUrl;
        this.isError = false;
    }

    public static Map<String, String> parseMetadata(String metaString) {
        Map<String, String> metadata = new HashMap();
        String[] metaParts = metaString.split(";");
        Pattern p = Pattern.compile("^([a-zA-Z]+)=\\'(.*)\\'$");
        Matcher m;
        for (int i = 0; i < metaParts.length; i++) {
            m = p.matcher(metaParts[i]);
            if (m.find()) {
                metadata.put((String)m.group(1), (String)m.group(2));
            }
        }

        return metadata;
    }
}

