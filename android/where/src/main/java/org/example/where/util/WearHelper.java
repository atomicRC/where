/*
 This class is used for communication with the WEAR Device
 */

package org.example.where;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.preview.support.v4.app.NotificationManagerCompat;
import android.preview.support.wearable.notifications.RemoteInput;
import android.preview.support.wearable.notifications.WearableNotifications;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.plus.People;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.model.people.PersonBuffer;
import org.example.where.service.WhereService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class WearHelper {
    private int cancelID;

    public class DebugHelperPeople {
        public HashMap<String,String> debugPeople = new HashMap<String, String>();

        public DebugHelperPeople() {
          //  debugPeople.put("MATTHIAS THOMA", "matthiasthoma@gmail.com");
        }
    }


    /* WEAR */
    public static final String ACTION_RESPONSE = "org.example.where.WHEREIS";
    public static final String EXTRA_REPLY = "where";
    final static String GROUP_KEY_WHERE = "group_key_where";


    private DebugHelperPeople debugHelper = new DebugHelperPeople();

    public static final String TAG = WearHelper.class.getName();

    public static String findPerson;

    public String foundPersonImageURL = null;

    public BroadcastReceiver mReceiver;
    public static int id=0;


    public void setupWear() {
        Log.i(TAG, "WearHelper setup");
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processWearResponse(intent);
            }
        };

//        registerReceiver(context);
    }

    public void registerReceiver(WhereService context) {
   //     context.registerReceiver(mReceiver, new IntentFilter(ACTION_RESPONSE));
    }

    public void onPause(WhereApplication context) {
        NotificationManagerCompat.from(context).cancel(0);
        context.unregisterReceiver(mReceiver);
    }

    public void processWearResponse(Intent intent) {
        String text = intent.getStringExtra(EXTRA_REPLY);
        showNotFoundNotification(text, "Location unknown", 10000);
        Log.i(TAG, "Process wear response");


        if (WhereHelper.isAuthenticated()) {
            Log.i(TAG, "User is authenticated!");
            // This is a Q&D hack and deserves a way nicer solution!
            findPerson = text;
            GoogleApiClient googleApiClient = WhereApplication.getContext().getGoogleApiClient();

            if (!googleApiClient.isConnected())
                googleApiClient.connect();

            Plus.PeopleApi.loadVisible(googleApiClient, null).setResultCallback(peopleWEARResultCallback);
        } else {
            showNotFoundNotification(text, "Location unknown");
        }
    }

    private void showNotFoundNotification(String title, String notifcationText, int timeout) {
        final int cancelId = id;
        final String myNotification = notifcationText;
        final String myTitle = title;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Log.i(TAG, "Show timeout");
                NotificationManagerCompat.from(WhereApplication.getContext()).cancel(cancelID);
                if (id==cancelId)
                    showNotFoundNotification(myTitle, myNotification);
            }
        }, timeout);
    }


    public String getEmailForName(String searchString) {
        String debugMail = debugHelper.debugPeople.get(searchString.toUpperCase());

        if (debugMail != null)
            return debugMail;

        ArrayList<String> emlRecs = new ArrayList<String>();
        HashSet<String> emlRecsHS = new HashSet<String>();
        Context context = WhereApplication.getContext();
        ContentResolver cr = context.getContentResolver();
        String[] PROJECTION = new String[] { ContactsContract.RawContacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_ID,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Photo.CONTACT_ID };
        String order = "CASE WHEN "
                + ContactsContract.Contacts.DISPLAY_NAME
                + " NOT LIKE '%@%' THEN 1 ELSE 2 END, "
                + ContactsContract.Contacts.DISPLAY_NAME
                + ", "
                + ContactsContract.CommonDataKinds.Email.DATA
                + " COLLATE NOCASE";
        String filter = ContactsContract.CommonDataKinds.Email.DATA + " NOT LIKE ''";
        Cursor cur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, PROJECTION, filter, null, order);
        if (cur.moveToFirst()) {
            do {
                // names comes in hand sometimes
                String name = cur.getString(1);
                String emlAddr = cur.getString(3);

                // keep unique only
                if (emlRecsHS.add(emlAddr.toLowerCase())) {
                    emlRecs.add(emlAddr);
                }

                if (name.toUpperCase().equals(searchString.toUpperCase())) {
                    return emlAddr;
                }

            } while (cur.moveToNext());
        }

        cur.close();
        return null;
    }

    public String getEmailForName2(String searchString){
        ArrayList<String> names = new ArrayList<String>();
        ContentResolver cr = WhereApplication.getContext().getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,null, null, null, null);
        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                Cursor cur1 = cr.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                        new String[]{id}, null);
                while (cur1.moveToNext()) {
                    //to get the contact names
                    String email = cur1.getString(cur1.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));

                    if(email!=null){
                        String name=cur1.getString(cur1.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        Log.d("Name :", name);
                        Log.d("Email", email);
                        if (name.toUpperCase().equals(searchString.toUpperCase())) {
                            return email;
                        }
                    }
                }
                cur1.close();
            }
        }

        return null;
    }



    private ResultCallback<People.LoadPeopleResult> peopleWEARResultCallback = new ResultCallback<People.LoadPeopleResult>() {
        @Override
        public void onResult(People.LoadPeopleResult peopleData) {
            Log.i(TAG, "Entering list callback of Google+");

            String debugMail = debugHelper.debugPeople.get(findPerson.toUpperCase());
            if (debugMail != null) {
                Log.i(TAG, "Debug person found, wont look in circles");
                foundPersonImageURL = null;
                String email = getEmailForName(findPerson);
                WhereHelper.askForLocation(email);
                return ;
            }

            if (peopleData.getStatus().getStatusCode() == CommonStatusCodes.SUCCESS) {
                PersonBuffer personBuffer = peopleData.getPersonBuffer();
                try {
                    int count = personBuffer.getCount();
                    for (int i = 0; i < count; i++) {
                        Person person = personBuffer.get(i);
                        String displayName = person.getDisplayName().toUpperCase();
                        if (findPerson != null)
                            findPerson = findPerson.toUpperCase();

                        Log.d(WhereApplication.TAG, "Display name: " + person.getDisplayName());
                        if (displayName.equals(findPerson)) {
                            String email = getEmailForName(findPerson);
                            if (email != null) {
                                foundPersonImageURL = person.getImage().getUrl();
                                WhereHelper.askForLocation(email);
                            } else {
                                showNotFoundNotification(findPerson, "Location unknown");
                            }
                            return ; // person found, lets quit
                        }
                    }
                } finally {
                    personBuffer.close();
                }
            } else {
                Log.e(WhereApplication.TAG, "Error requesting visible circles: " + peopleData.getStatus());
                showNotFoundNotification(findPerson, "Person unknown");
            }

            showNotFoundNotification(findPerson, "Person unknown");
        }
    };

    public Bitmap retrieveImageFromURL(String imageURL) {
        if (imageURL == null)
            return null;

        try {
            URL url = new URL(imageURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            return null;
        }
    }

    /*
      Shows a "Where" Notification on the wear device.

     */

    public void showNotification(String notifcationText, int timeout) {
        final int cancelId = id;
        final String myNotification = notifcationText;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Log.i(TAG, "Show timeout");
                NotificationManagerCompat.from(WhereApplication.getContext()).cancel(cancelID);
                if (id==cancelId)
                    showNotification(myNotification);
            }
        }, timeout);
    }

    public void showNotification(String notifcationText) {
        Log.i(TAG, "Show notification");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(WhereApplication.getContext())
                .setContentTitle("Where?")
                .setSmallIcon(R.drawable.find_user)
                .setLargeIcon(BitmapFactory.decodeResource(
                        WhereApplication.getContext().getResources(), R.drawable.map_android))
                .setContentText(notifcationText);

        Intent intent = new Intent(ACTION_RESPONSE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(WhereApplication.getContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);
        Notification notification = new WearableNotifications.Builder(builder)
                .setMinPriority()
                .addRemoteInputForContentIntent(
                        new RemoteInput.Builder(EXTRA_REPLY)
                                .setLabel("Look for").build())
                .build();
        NotificationManagerCompat.from(WhereApplication.getContext()).cancel(id);
        NotificationManagerCompat.from(WhereApplication.getContext()).notify(++id, notification);
    }

    public void showNotFoundNotification(String title, String notifcationText) {
        Log.i(TAG, "Show not found notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(WhereApplication.getContext())
                .setContentTitle(title)
                .setSmallIcon(R.drawable.sign_stop)
                .setLargeIcon(BitmapFactory.decodeResource(
                        WhereApplication.getContext().getResources(), R.drawable.map_android))
                .setContentText(notifcationText);

        Intent intent = new Intent(ACTION_RESPONSE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(WhereApplication.getContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);
        Notification notification = new WearableNotifications.Builder(builder)
                .setMinPriority()
                .addRemoteInputForContentIntent(
                        new RemoteInput.Builder(EXTRA_REPLY)
                                .setLabel("Look for").build())
                .build();
        NotificationManagerCompat.from(WhereApplication.getContext()).cancel(id);
        NotificationManagerCompat.from(WhereApplication.getContext()).notify(++id, notification);
    }

    public void showFoundNotification(String notifcationText, String title) {
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... strings) {
                Log.i(TAG, "Show found notification");


                String title = strings[0];
                String notifcationText = strings[1];

                NotificationCompat.Builder builder = new NotificationCompat.Builder(WhereApplication.getContext())
                        .setContentTitle(title)
                        .setSmallIcon(R.drawable.ok_apply)
                        .setContentText(notifcationText);

                Bitmap personBitmap = retrieveImageFromURL(foundPersonImageURL);

                builder.setLargeIcon(personBitmap);

                foundPersonImageURL = null;

                Intent intent = new Intent(ACTION_RESPONSE);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(WhereApplication.getContext(), 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);

                builder.setContentIntent(pendingIntent);

                // Show on Maps Action
                String url = "http://maps.google.com/maps?q=" + notifcationText;
                Intent showMapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                PendingIntent mapPendingIntent =
                        PendingIntent.getActivity(WhereApplication.getContext(), 0, showMapIntent, 0);

                builder.addAction(R.drawable.map_android, "Show on map", mapPendingIntent);

                // Schedule meeting intent
                Intent showNavigationIntent = new Intent(Intent.ACTION_VIEW);
                PendingIntent navigationPendingIntent =
                        PendingIntent.getActivity(WhereApplication.getContext(), 0, showNavigationIntent, 0);
                builder.addAction(R.drawable.user_group, "Schedule meeting", navigationPendingIntent);

                // Schedule Fence intent
                Intent showFenceIntent = new Intent(Intent.ACTION_VIEW);
                PendingIntent fencePendingIntent =
                        PendingIntent.getActivity(WhereApplication.getContext(), 0, showFenceIntent, 0);
                builder.addAction(R.drawable.geo_fence, "Inform when here", fencePendingIntent);

                // Meet me here Fence intent
                Intent showMeetMeHereIntent = new Intent(Intent.ACTION_VIEW);
                PendingIntent meetMeHerePendingIntent =
                        PendingIntent.getActivity(WhereApplication.getContext(), 0, showMeetMeHereIntent, 0);
                builder.addAction(R.drawable.pin, "Meet me here", meetMeHerePendingIntent);

                Notification notification = new WearableNotifications.Builder(builder)
                        .setMinPriority()
//                .setGroup(GROUP_KEY_WHERE)
                        .addRemoteInputForContentIntent(
                                new RemoteInput.Builder(EXTRA_REPLY)
                                        .setLabel("Look for")
                                        .build())
                        .build();
                NotificationManagerCompat.from(WhereApplication.getContext()).cancel(id);
                NotificationManagerCompat.from(WhereApplication.getContext()).notify(++id, notification);
                return null;
            }
        }.execute(notifcationText, title);
        }

    public void onResume(WhereApplication context) {
         context.registerReceiver(mReceiver, new IntentFilter(ACTION_RESPONSE));
        }

    public void cancelNotification() {
        NotificationManagerCompat.from(WhereApplication.getContext()).cancel(id);
    }
}
