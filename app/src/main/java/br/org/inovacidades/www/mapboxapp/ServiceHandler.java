package br.org.inovacidades.www.mapboxapp;

import android.util.Log;

import com.loopj.android.http.HttpGet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.ClientProtocolException;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;

/**
 * Created by mario.rscastro on 06/10/2015.
 */
public class ServiceHandler {

    static String response = null;
    public final static int GET = 1;
    public final static int POST = 2;

    public ServiceHandler() {

    }

    public String makeServiceCall(String url, int method) {
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpEntity httpEntity = null;
            HttpResponse httpResponse = null;

            if (method == POST) {
                HttpPost httpPost = new HttpPost(url);

                httpResponse = httpClient.execute(httpPost);
            } else if (method == GET) {
                HttpGet httpGet = new HttpGet(url);

                httpResponse = httpClient.execute(httpGet);
            }
            if (httpResponse != null) {
                httpEntity = httpResponse.getEntity();
            }
            if (httpEntity != null) {
                response = EntityUtils.toString(httpEntity);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.e("Response: ", "> ClientProtocolException");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("Response: ", "> IOException");
            e.printStackTrace();
        }

        return response;
    }
}
