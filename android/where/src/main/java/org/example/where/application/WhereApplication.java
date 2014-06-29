package org.example.where.application;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.plus.Plus;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import org.example.where.AppConstants;
import org.example.where.activity.GooglePlayServicesActivity;
import org.example.where.util.WearHelper;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Florian Antonescu
 */
public class WhereApplication extends Application {
    public static final String TAG = WhereApplication.class.getName();

    public static final String WHERE = "where";
    public static final String ACTIVE = "active";

    /* application context */
    private static WhereApplication context;

    /* Google Account credential */
    private GoogleAccountCredential credential;

    /** Google API client. */
    private GoogleApiClient googleApiClient;

    private boolean active = false;

    /**
     * Determines if the Google API client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mIsInResolution;

    /* Google Cloud Messaging client */
    private GoogleCloudMessaging gcm;

    private LocationClient locationClient;

    private ActivityRecognitionClient mActivityRecognitionClient;

    /* messenger for communicating back to the Activity */
    private Messenger messenger;
    private WearHelper wearHelper = new WearHelper();

    public WhereApplication() {
        context = this;
    }

    public static WhereApplication getContext() {
        return context;
    }

    public boolean isInResolution() {
        return mIsInResolution;
    }

    public void setInResolution(boolean mIsInResolution) {
        this.mIsInResolution = mIsInResolution;
    }

    public void setCredential(GoogleAccountCredential credential) {
        this.credential = credential;
    }

    public GoogleAccountCredential getCredential() {
        if (credential == null) {
            credential = GoogleAccountCredential.usingAudience(this,
                    "server:client_id:" + AppConstants.WEB_APPSPOT_CLIENT_ID);
        }
        return credential;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    public SharedPreferences getPreferences() {
        return getSharedPreferences(WHERE, Context.MODE_PRIVATE);
    }

    public void setMessenger(Messenger messenger) {
        this.messenger = messenger;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public GoogleApiClient getGoogleApiClient() {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(WhereApplication.getContext())
                    .addApi(Plus.API)
                    .addScope(Plus.SCOPE_PLUS_LOGIN)
//                    .addScope(new Scope("https://www.googleapis.com/auth/plus.me"))
//                    .addScope(new Scope("https://www.googleapis.com/auth/plus.circles.read"))
                    .addConnectionCallbacks(googleApiCallback)
                    .addOnConnectionFailedListener(googleApiConnectionFailedListener)
                    .build();
        }
        return googleApiClient;
    }

    public GoogleCloudMessaging getGoogleCloudMessagingClient() {
        if (gcm == null) {
            gcm = GoogleCloudMessaging.getInstance(this);
        }

        return gcm;
    }

    public LocationClient getLocationClient() {
        if (locationClient == null) {
            locationClient = new LocationClient(this, locationConnectionCallback, locationConnectionFailedCallback);
        }

        return locationClient;
    }

    /* Tries to get the last known location, waiting is necessary for the Location Client to become connected */
    public Location getLastLocation() {
        Location location = null;

        LocationClient client = getLocationClient();
        if (!client.isConnected()) {
            client.connect();
            while (!client.isConnected()) {
                try {
                    locationClientCondition.await();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for Location Client to connect.");
                    break;
                }
            }
        }

        if (client.isConnected()) {
            location = client.getLastLocation();
        }

        return location;
    }

    /* -------------- Google Client API ------------------- */

    /* Connect to Google API Client */
    public void connectToGoogleApiClient() {
        setTextStatus("Connecting to local Google API Client.");
        getGoogleApiClient().connect();
    }

    /* Disconnect from Google API Client */
    public void disconnectGoogleApiClient() {
        if (googleApiClient != null) {
            setTextStatus("Disconnecting to local Google API Client.");
            getGoogleApiClient().disconnect();
        }
    }

    /** Retry connecting to Google API Client */
    public void retryConnectingToGoogleClient() {
        WhereApplication.getContext().setInResolution(false);
        if (!getGoogleApiClient().isConnecting()) {
            connectToGoogleApiClient();
        }
    }

    private GoogleApiClient.ConnectionCallbacks googleApiCallback = new GoogleApiClient.ConnectionCallbacks() {
        /**
         * Called when {@code mGoogleApiClient} is connected.
         */
        @Override
        public void onConnected(Bundle connectionHint) {
            Log.i(TAG, "GoogleApiClient connected");

            setTextStatus("Connected to local Google API Client.");

            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.GOOGLE_CLIENT_CONNECTED;
            sendMessage(msg);
        }

        /**
         * Called when {@code mGoogleApiClient} connection is suspended.
         */
        @Override
        public void onConnectionSuspended(int cause) {
            Log.i(TAG, "GoogleApiClient connection suspended");
            retryConnectingToGoogleClient();
        }
    };

    private GoogleApiClient.OnConnectionFailedListener googleApiConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        /**
         * Called when {@code mGoogleApiClient} is trying to connect but failed.
         * Handle {@code result.getResolution()} if there is a resolution
         * available.
         */
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());

            // notify Activity
            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.GOOGLE_API_CLIENT_CONNECTION_FAILURE;
            msg.obj = result;
            sendMessage(msg);
        }
    };

    /* -------------- END Google Client API ------------------- */

    /* -------- Google Location Client Callbacks ---------- */

    private Lock locationClientLock = new ReentrantLock();
    private Condition locationClientCondition = locationClientLock.newCondition();

    private GooglePlayServicesClient.ConnectionCallbacks locationConnectionCallback = new GooglePlayServicesClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            Log.i(TAG, "Google Location Client is now connected.");

            // signal connection status
            try {
                locationClientLock.lock();
                locationClientCondition.signal();
            } finally {
                locationClientLock.unlock();
            }
        }

        @Override
        public void onDisconnected() {
            Log.i(TAG, "Google Location Client has disconnected.");
        }
    };

    private GooglePlayServicesClient.OnConnectionFailedListener locationConnectionFailedCallback = new GooglePlayServicesClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            /*
             * Google Play services can resolve some errors it detects.
             * If the error has a resolution, try sending an Intent to
             * start a Google Play services activity that can resolve
             * error.
             */
            Log.i(TAG, "Google Location Connection failed: " + connectionResult.toString());

            // notify Activity
            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.GOOGLE_LOCATION_CLIENT_CONNECTION_FAILURE;
            msg.obj = connectionResult;
            sendMessage(msg);
        }
    };

    /* -------- END Google Location Client Callbacks ---------- */

    /** Displays a text message  */
    public void setTextStatus(final String message) {
        Message msg = Message.obtain();
        msg.what = GooglePlayServicesActivity.DISPLAY_MESSAGE;
        msg.obj = message;
        sendMessage(msg);
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    public boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(WhereApplication.getContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.DISPLAY_GOOGLE_PLAY_SERVICES_ERR;
            msg.arg1 = resultCode;
            sendMessage(msg);

            return false;
        }
        return true;
    }

    /** Send message to main Activity  */
    private void sendMessage(Message msg) {
        if (messenger != null) {
            try {
                messenger.send(msg);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to send message: " + e, e);
            }
        } else {
            Log.w(TAG, "Unable to send message to activity: messenger is NULL");
        }
    }

    public WearHelper getWearHelper() {
        return wearHelper;
    }

    public boolean isActive() {
        active = getPreferences().getBoolean(ACTIVE, false);
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        getPreferences().edit().putBoolean(ACTIVE, active).commit();
    }

    public boolean flipActive() {
        setActive(!active);
        return active;
    }
}
