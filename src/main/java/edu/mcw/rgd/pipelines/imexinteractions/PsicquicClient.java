package edu.mcw.rgd.pipelines.imexinteractions;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;

/**
 * Created by jthota on 3/10/2016.
 */
public class PsicquicClient {
    public static final String MITAB25 = "tab25";
    public static final String COUNT = "count";
    private String serviceRestUrl;
    private int connectionTimeout = 10000;
    private int readTimeout =100000;
    private Proxy proxy;

    public PsicquicClient(String serviceRestUrl) {
        this.serviceRestUrl = serviceRestUrl;
    }


    public PsicquicClient(String serviceRestUrl, Proxy proxy) {
        this(serviceRestUrl);
        this.proxy = proxy;
    }
    public InputStream getByQuery(String query) throws IOException {
        return getByQuery(query, MITAB25);
    }

    public InputStream getByInteractor(String query) throws IOException {
        return getByQuery(query, MITAB25);
    }

    public InputStream getByInteraction(String query) throws IOException {
        return getByQuery(query, MITAB25);
    }

    public InputStream getByQuery(String query, String format) throws IOException {
        return getByQuery(query, format, 0, Integer.MAX_VALUE);
    }

    public InputStream getByInteractor(String query, String format) throws IOException {
        return getByInteractor(query, format, 0, Integer.MAX_VALUE);
    }

    public InputStream getByInteraction(String query, String format) throws IOException {
        return getByInteraction(query, format, 0, Integer.MAX_VALUE);
    }

    public InputStream getByQuery(String query, String format, int firstResult, int maxResults) throws IOException {
        return getBy("query", query, format, firstResult, maxResults);
    }

    public InputStream getByInteractor(String query, String format, int firstResult, int maxResults) throws IOException {
        return getBy("interactor", query, format, firstResult, maxResults);
    }


    public InputStream getByInteraction(String query, String format, int firstResult, int maxResults) throws IOException {
        return getBy("interaction", query, format, firstResult, maxResults);
    }


    public long countByQuery(String query) throws IOException {
        return countBy("query", query);
    }
    public long countByInteractor(String query) throws IOException {
        return countBy("interactor", query);
    }
    public long countByInteraction(String query) throws IOException {
        return countBy("interaction", query);
    }
    public List<String> getFormats() throws IOException {
        String queryType = "/formats";
        String strUrl = serviceRestUrl + queryType;
        strUrl = strUrl.replaceAll("/"+ queryType, queryType);
        HttpURLConnection connection = null;
        InputStream result = null;
        URL url;
        try {
            url = new URL(strUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Problem creating URL: "+strUrl, e);
        }
        if (this.proxy == null){
            connection = (HttpURLConnection) url.openConnection();
        }
        else {
            connection = (HttpURLConnection) url.openConnection(this.proxy);
        }
        connection.setConnectTimeout(connectionTimeout);
        connection.setReadTimeout(readTimeout);


        connection.connect();
        result = connection.getInputStream();
        String formats = streamToString(result);


        return Arrays.asList(formats.split("\n"));

    }


    private InputStream getBy(String queryType, String query, String format, int firstResult, int maxResults) throws IOException {
        HttpURLConnection connection = null;
        final String encodedQuery = encodeQuery(query);
        InputStream inputStream = null;

        URL url = createUrl(queryType, encodedQuery, format, firstResult, maxResults);
        if (this.proxy == null){
            connection = (HttpURLConnection) url.openConnection();
        }
        else {
            connection = (HttpURLConnection) url.openConnection(this.proxy);
        }


        connection.setConnectTimeout(connectionTimeout);
        connection.setReadTimeout(readTimeout);
        connection.connect();

            inputStream = connection.getInputStream();


        return inputStream;
    }
    private long countBy(String queryType, String query) throws IOException {
        InputStream result = getBy(queryType, query, COUNT, 0, 0);
        String strCount = streamToString(result);
        strCount = strCount.replaceAll("\n", "");


        return Long.parseLong(strCount);
    }


    private String encodeQuery(String query) {
        String encodedQuery;

        try {
            encodedQuery = URLEncoder.encode(query, "UTF-8");
            encodedQuery = encodedQuery.replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 should be supported");
        }
        return encodedQuery;
    }


    private URL createUrl(String queryType, String encodedQuery, String format, int firstResult, int maxResults) {
        String strUrl = serviceRestUrl+"/"+queryType+"/"+encodedQuery+"?format="+format+"&firstResult="+firstResult+"&maxResults="+maxResults;
        strUrl = strUrl.replaceAll("//"+queryType, "/"+queryType);

        URL url;
        try {
            url = new URL(strUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Problem creating URL: "+strUrl, e);
        }
        return url;
    }



    private String streamToString(InputStream is) throws IOException {
        if (is == null) return "";


        StringBuilder sb = new StringBuilder();
        String line;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            finally{
                if (reader != null){
                    reader.close();
                }
            }
        } finally {
            if (is != null){
                is.close();
            }
        }
        return sb.toString();
    }
    public int getReadTimeout() {
        return readTimeout;
    }


    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }


    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

}
