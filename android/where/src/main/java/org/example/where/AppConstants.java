package org.example.where;

import com.appspot.unique_voyage_610.where.Where;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

public class AppConstants {

    /**
     * Class instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new AndroidJsonFactory();

    /**
     * Class instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();

    /* Google App Engine Web Client ID */
    public static final String WEB_APPSPOT_CLIENT_ID = "649196046194-qd83ij5jluu9s126s0vai6qf9pgm82fo.apps.googleusercontent.com";


    /**
     * Retrieve a Where api service handle to access the API.
     */
    public static Where getApiServiceHandle(GoogleAccountCredential credential) {
        // Use a builder to help formulate the API request.
        Where.Builder where = new Where.Builder(AppConstants.HTTP_TRANSPORT,
                AppConstants.JSON_FACTORY, credential).setApplicationName("org.example.where");

        return where.build();
    }

}