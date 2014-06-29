package org.example.where.service;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.example.where.broadcast.GcmBroadcastReceiver;
import org.example.where.activity.GooglePlayServicesActivity;
import org.example.where.broadcast.WearBroadcastReceiver;

import org.example.where.application.WhereApplication;
import org.example.where.util.WearHelper;
import org.example.where.util.WhereHelper;
import org.example.where.util.GetAddressTask;

public class WhereService extends Service {
    public static final String TAG = WhereService.class.getName();

    public static final String KEY_ACTION = "action";
    public static final String KEY_REQUESTER = "requester";
    public static final String KEY_NAME = "name";
    public static final String LOCATE = "locate";
    public static final String DISCLOSE = "disclose";

    @Override
    public IBinder onBind(Intent intent) {
        return new WhereBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service created.");

        // Creates an initial notification. In a better Version of the app this should be an
        // always on command, so that you can initiate this from Android Wear without the need
        // of an Notification
        WhereApplication.getContext().getWearHelper().setupWear();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started.");
        WhereApplication.getContext().getWearHelper().registerReceiver(this);

        if (intent != null) {
            onHandleIntent(intent);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        WhereApplication.getContext().getWearHelper().cancelNotification();

        Log.i(TAG, "Service destroyed.");
    }

    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        String actionStr = intent.getAction();

        // Hack to work with the intents coming from Wear. A real software engineeering approach
        // would look different, but it is a Hackaton :-)

        if (actionStr != null)
        if (actionStr.equals(WearHelper.ACTION_RESPONSE)) {
            WhereApplication.getContext().getWearHelper().processWearResponse(intent);
            WearBroadcastReceiver.completeWakefulIntent(intent);
            return ;
        }

        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        if (gcm == null) {
            return; //nothing to do
        }

        if (extras != null && !extras.isEmpty()) {  // has effect of unparcelling Bundle
            // The getMessageType() intent parameter must be the intent you received
            // in your BroadcastReceiver.
            String messageType = gcm.getMessageType(intent);

            Log.i(TAG, "Received GCM message: " + extras);

            /*
             * Filter messages based on message type. Since it is likely that GCM
             * will be extended in the future with new message types, just ignore
             * any message types you're not interested in, or that you don't
             * recognize.
             */
            if (GoogleCloudMessaging.
                    MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                WhereHelper.setTextStatus("Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)) {
                WhereHelper.setTextStatus("Deleted messages on server: " + extras.toString());
                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                if (extras.containsKey(KEY_ACTION)) {
                    String action = extras.getString(KEY_ACTION);
                    String requester = extras.getString(KEY_REQUESTER);
                    final String name = extras.getString(KEY_NAME);

                    if (LOCATE.equals(action) && extras.containsKey(KEY_REQUESTER)) {
                        Log.i(TAG, "Determining current location");

                        // inform about the location request
                        Message msg = Message.obtain();
                        msg.what = GooglePlayServicesActivity.LOCATION_REQUEST;
                        msg.obj = requester;
                        WhereHelper.sendMessage(msg);

                        //todo: check if user is allowed to receive location

                        if (WhereApplication.getContext().isActive()) {
                            //update notification with the location request

                            final Location location = WhereApplication.getContext().getLastLocation();
                            Log.i(TAG, "Current location: " + location);
                            if (location != null) {
                                new GetAddressTask(WhereApplication.getContext()){
                                    @Override
                                    protected void onPostExecute(String s) {
                                        WhereHelper.sendLocation(name, s);
                                    }
                                }.execute(location);
                            }
                        } else {
                            Log.i(TAG, "User is NOT active, will not reveal his/her location");
                            WhereHelper.sendLocation(name, "unavailable");
                        }
                    } else if (DISCLOSE.equals(action)) {
                                                Log.i(TAG, "Location received from GCM: " + extras);

                        //TODO: update notification with the received location
                        Message msg = Message.obtain();
                        msg.what = GooglePlayServicesActivity.LOCATION_PUSH;
                        msg.obj = extras;

                        WhereApplication.getContext().getWearHelper().showFoundNotification(WhereApplication.getContext().getWearHelper().findPerson, extras.getString("location", "unknown"));

                        WhereHelper.sendMessage(msg);
                    } else {
                        Log.i(TAG, "Unknown GCM message.");
                    }
                }

                /*// Post notification of received message.
                sendNotification("Received: " + extras.toString());
                WhereHelper.setTextStatus("GCM message: " + extras.toString());
                Log.i(TAG, "Received GCM message: " + extras.toString());*/
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
}
