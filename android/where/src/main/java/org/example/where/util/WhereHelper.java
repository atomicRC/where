package org.example.where.util;

import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preview.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.appspot.unique_voyage_610.where.Where;
import com.appspot.unique_voyage_610.where.model.WhereLocation;
import com.appspot.unique_voyage_610.where.model.WhereStatus;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.plus.People;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.android.gms.plus.model.people.PersonBuffer;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import org.example.where.AppConstants;
import org.example.where.R;
import org.example.where.application.WhereApplication;
import org.example.where.activity.GooglePlayServicesActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Florian Antonescu on 16/06/14.
 */
public class WhereHelper {

    public static final String TAG = WhereHelper.class.getName();

    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";

    /**
     * This is the project number from the API Console, as described in "Getting Started."
     */
    public static String SENDER_ID = "649196046194";

    private static int notificationId = 0xFA01;

    public static final int NOTIFICATION_HIDE_DELAY_MS = 5000;

    public static void listPeople() {
        GoogleApiClient googleApiClient = WhereApplication.getContext().getGoogleApiClient();
        Plus.PeopleApi.loadVisible(googleApiClient, null)
                .setResultCallback(peopleResultCallback);
    }

    /* ------------- GAE Endpoints -------------- */

    public static boolean checkForPreviousGoogleLogin() {
        boolean loggedOn = false;

        SharedPreferences settings = WhereApplication.getContext().getPreferences();
        String accountName = settings.getString(AccountManager.KEY_ACCOUNT_NAME, null);

        if (accountName != null) {
            setSelectedAccountName(accountName);

            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.ACCOUNT_NAME_RETRIEVED;
            msg.obj = accountName;
            sendMessage(msg);

            loggedOn = true;
        }

        return loggedOn;
    }

    public static void prepareGoogleCredential() {
        Log.i(TAG, "preparing Google Credential");
        SharedPreferences settings = WhereApplication.getContext().getPreferences();
        String accountName = settings.getString(AccountManager.KEY_ACCOUNT_NAME, null);

        if (accountName == null) {
            // Not signed in, show login window or request an account.
            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.SHOW_ACCOUNT_PICKER;
            sendMessage(msg);
        } else {
            setSelectedAccountName(accountName);

            Message msg = Message.obtain();
            msg.what = GooglePlayServicesActivity.ACCOUNT_NAME_RETRIEVED;
            msg.obj = accountName;
            sendMessage(msg);
        }
    }

    public static void clearCredential() {
        SharedPreferences settings = WhereApplication.getContext().getPreferences();
        settings.edit().remove(AccountManager.KEY_ACCOUNT_NAME).commit();

        GoogleApiClient mGoogleApiClient = WhereApplication.getContext().getGoogleApiClient();
        Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
        mGoogleApiClient.disconnect();
        mGoogleApiClient.connect();
    }

    public static void isRegistered(final String email) {
        if (email == null || email.isEmpty()) {
            Toast.makeText(WhereApplication.getContext(), "Please provide a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        saveSearchedEmail(email);

        // Use of an anonymous class is done for sample code simplicity. {@code AsyncTasks} should be
        // static-inner or top-level classes to prevent memory leak issues.
        // @see http://goo.gl/fN1fuE @26:00 for a great explanation.
        AsyncTask<String, Void, WhereStatus> endpointCaller = new AsyncTask<String, Void, WhereStatus>() {
            @Override
            protected WhereStatus doInBackground(String... o) {
                // Retrieve service handle.
                GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
                Where apiServiceHandle = AppConstants.getApiServiceHandle(credential);

                try {
                    Log.i(TAG, "check is user is registered to GAE Where Service: " + email);
                    setTextStatus("Asking Where service if " + email + " is registered...");

                    Where.IsRegistered whereCommand = apiServiceHandle.isRegistered(email);
                    WhereStatus status = whereCommand.execute();

                    // notify activity
                    Message msg = Message.obtain();
                    msg.what = GooglePlayServicesActivity.GAE_RESPONSE;
                    msg.obj = status;

                    sendMessage(msg);

                    return status;
                } catch (IOException e) {
                    Log.e(TAG, "Exception during API call: " + e, e);
                    setTextStatus("Error: " + e.getMessage());
                }
                return null;
            }
        };

        endpointCaller.execute();
    }

    /**
     * Sends the registration ID to GAE server over HTTP, so it can use GCM/HTTP
     * or CCS to send messages to the Where app.
     */
    private static void registerToGaeEndpoint(final String regid) {
        // Use of an anonymous class is done for sample code simplicity. {@code AsyncTasks} should be
        // static-inner or top-level classes to prevent memory leak issues.
        // @see http://goo.gl/fN1fuE @26:00 for a great explanation.
        AsyncTask<String, Void, WhereStatus> endpointCaller = new AsyncTask<String, Void, WhereStatus>() {
            @Override
            protected WhereStatus doInBackground(String... o) {
                // Retrieve service handle.
                GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
                Where apiServiceHandle = AppConstants.getApiServiceHandle(credential);

                try {
                    Log.i(TAG, "send GCM registration ID to GAE Where Service");
                    Where.Register whereCommand = apiServiceHandle.register(regid);
                    WhereStatus status = whereCommand.execute();

                    // notify activity
                    Message msg = Message.obtain();
                    msg.what = GooglePlayServicesActivity.GAE_RESPONSE;
                    msg.obj = status;

                    sendMessage(msg);

                    return status;
                } catch (IOException e) {
                    Log.e(TAG, "Exception during API call: " + e, e);
                    setTextStatus("Error: " + e.getMessage());
                }
                return null;
            }
        };

        endpointCaller.execute();
    }

    /**
     * Sends the 'active' flag to GAE endpoint
     */
    public static void sendActiveFlag(final boolean active, final Runnable runnable) {
        // Use of an anonymous class is done for sample code simplicity. {@code AsyncTasks} should be
        // static-inner or top-level classes to prevent memory leak issues.
        // @see http://goo.gl/fN1fuE @26:00 for a great explanation.
        AsyncTask<String, Void, WhereStatus> endpointCaller = new AsyncTask<String, Void, WhereStatus>() {
            @Override
            protected WhereStatus doInBackground(String... o) {
                // Retrieve service handle.
                GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
                Where apiServiceHandle = AppConstants.getApiServiceHandle(credential);

                try {
                    Log.i(TAG, "send active ("+active+") flag to GAE endpoint");
                    Where.SetActiveFlag whereCommand = apiServiceHandle.setActiveFlag(active);
                    WhereStatus status = whereCommand.execute();

                    // notify activity
                    Message msg = Message.obtain();
                    msg.what = GooglePlayServicesActivity.GAE_RESPONSE;
                    msg.obj = status;

                    sendMessage(msg);

                    if (runnable != null) {
                        runnable.run();
                    }

                    return status;
                } catch (IOException e) {
                    Log.e(TAG, "Exception during API call: " + e, e);
                    setTextStatus("Error: " + e.getMessage());
                }
                return null;
            }
        };

        endpointCaller.execute();
    }

    public static void askForLocation(final String email) {
        if (email == null || email.isEmpty()) {
            Toast.makeText(WhereApplication.getContext(), "Please provide a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        saveSearchedEmail(email);

        // Use of an anonymous class is done for sample code simplicity. {@code AsyncTasks} should be
        // static-inner or top-level classes to prevent memory leak issues.
        // @see http://goo.gl/fN1fuE @26:00 for a great explanation.
        AsyncTask<String, Void, WhereStatus> endpointCaller = new AsyncTask<String, Void, WhereStatus>() {
            @Override
            protected WhereStatus doInBackground(String... o) {
                // Retrieve service handle.
                GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
                Where apiServiceHandle = AppConstants.getApiServiceHandle(credential);

                try {
                    Log.i(TAG, "ask for location of: " + email);
                    setTextStatus("Asking Where service for location of " + email);

                    Where.Locate whereCommand = apiServiceHandle.locate(email);
                    WhereStatus status = whereCommand.execute();

                    // notify activity
                    Message msg = Message.obtain();
                    msg.what = GooglePlayServicesActivity.GAE_RESPONSE;
                    msg.obj = status;

                    sendMessage(msg);

                    return status;
                } catch (IOException e) {
                    Log.e(TAG, "Exception during API call: " + e, e);
                    setTextStatus("Error: " + e.getMessage());
                }
                return null;
            }
        };

        endpointCaller.execute();
    }

    public static void sendLocation(final String nickname, final String location) {
        if (location == null || location.isEmpty()) {
            return;
        }

        // Use of an anonymous class is done for sample code simplicity. {@code AsyncTasks} should be
        // static-inner or top-level classes to prevent memory leak issues.
        // @see http://goo.gl/fN1fuE @26:00 for a great explanation.
        AsyncTask<String, Void, WhereStatus> endpointCaller = new AsyncTask<String, Void, WhereStatus>() {
            @Override
            protected WhereStatus doInBackground(String... o) {
                // Retrieve service handle.
                GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
                Where apiServiceHandle = AppConstants.getApiServiceHandle(credential);

                try {
                    Log.i(TAG, "send location to GAE: " + location);
                    setTextStatus("Sending location to Where cloud endpoint...");

                    WhereLocation whereLocation = new WhereLocation();
                    whereLocation.setLocation(location);

                    Where.Publish whereCommand = apiServiceHandle.publish(whereLocation);
                    WhereStatus status = whereCommand.execute();

                    // notify activity
                    Message msg = Message.obtain();
                    msg.what = GooglePlayServicesActivity.GAE_RESPONSE;
                    msg.obj = status;

                    sendMessage(msg);

                    return status;
                } catch (IOException e) {
                    Log.e(TAG, "Exception during API call: " + e, e);
                    setTextStatus("Error: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(WhereStatus whereStatus) {
                if (whereStatus != null) {
                    if (whereStatus.getCode() == 0) {
                        showWearNotification(nickname, "asked for location and it was replied with: " + location);
                    } else {
                        showWearNotification(nickname, "asked for location but there was a problem responding");
                    }
                }
            }
        };

        endpointCaller.execute();
    }

    // setSelectedAccountName definition
    public static void setSelectedAccountName(String accountName) {
        Log.i(TAG, "account name: " + accountName);

        if (accountName != null) {
            registerToGCM();
        }

        SharedPreferences.Editor editor = WhereApplication.getContext().getPreferences().edit();
        editor.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
        editor.commit();

        // update credential's account name
        WhereApplication.getContext().getCredential().setSelectedAccountName(accountName);
    }

    public static boolean isAuthenticated() {
        GoogleAccountCredential credential = WhereApplication.getContext().getCredential();
        return credential != null && credential.getSelectedAccountName() != null;
    }

    /* ------------------------ GCM API methods ------------------------- */

    /* Register to GCM */
    public static void registerToGCM() {
        // Check device for Play Services APK. If check succeeds, proceed with
        //  GCM registration.
        if (WhereApplication.getContext().checkPlayServices()) {
            String regid = getRegistrationId();

            if (regid.isEmpty()) {
                setTextStatus("Registering to GCM");
                registerInBackground();
            } else {
                setTextStatus("GCM ready.");
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
            setTextStatus("Google Play Services not found.");
        }
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * <p/>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     * registration ID.
     */
    private static String getRegistrationId() {
        final SharedPreferences prefs = WhereApplication.getContext().getPreferences();
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        } else {
            Log.i(TAG, "GCM Registration ID restored.");
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(WhereApplication.getContext());
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private static void registerInBackground() {
        new AsyncTask<Object, Object, String>() {

            @Override
            protected String doInBackground(Object... params) {
                String msg;
                try {
                    String regid = WhereApplication.getContext().getGoogleCloudMessagingClient().register(SENDER_ID);
                    Log.i(TAG, "GCM registration ID: " + regid);
                    msg = "Device registered to GCM";

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    registerToGaeEndpoint(regid);

                    // Persist the regID - no need to register again.
                    storeRegistrationId(regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    setTextStatus(msg);
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String str) {
                setTextStatus(str);
            }
        }.execute();
    }
    /**
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param regId   registration ID
     */
    private static void storeRegistrationId(String regId) {
        final SharedPreferences prefs = WhereApplication.getContext().getPreferences();
        int appVersion = getAppVersion(WhereApplication.getContext());
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }
    /** Displays a text message  */
    public static void setTextStatus(final String message) {
        Message msg = Message.obtain();
        msg.what = GooglePlayServicesActivity.DISPLAY_MESSAGE;
        msg.obj = message;
        sendMessage(msg);
    }

    /* send a message to the main activity */
    public static void sendMessage(Message msg) {
        Messenger messenger = WhereApplication.getContext().getMessenger();
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

    private static ResultCallback<People.LoadPeopleResult> peopleResultCallback = new ResultCallback<People.LoadPeopleResult>() {
        @Override
        public void onResult(People.LoadPeopleResult peopleData) {
            if (peopleData.getStatus().getStatusCode() == CommonStatusCodes.SUCCESS) {
                PersonBuffer personBuffer = peopleData.getPersonBuffer();
                try {
                    int count = personBuffer.getCount();
                    for (int i = 0; i < count; i++) {
                        Person person = personBuffer.get(i);
                        Log.d(TAG, "Display name: " + person.getDisplayName());
                    }
                } finally {
                    personBuffer.close();
                }
            } else {
                Log.e(TAG, "Error requesting visible circles: " + peopleData.getStatus());
            }
        }
    };

    private static void saveSearchedEmail(String email) {
        if (getLastSearchedEmails().contains(email)) {
            return;
        }

        SharedPreferences preferences = WhereApplication.getContext().getPreferences();
        int lastEmailsCount = preferences.getInt("search.emails.count", 0);

        String key = String.format("search.email.%s", lastEmailsCount);
        preferences.edit().putString(key, email).commit();

        preferences.edit().putInt("search.emails.count", lastEmailsCount+1).commit();
    }

    public static List<String> getLastSearchedEmails() {
        List<String> emails = new ArrayList<String>();
        SharedPreferences preferences = WhereApplication.getContext().getPreferences();

        int lastEmailsCount = preferences.getInt("search.emails.count", 0);
        if (lastEmailsCount > 0) {
            for (int i = 0; i < lastEmailsCount; i++) {
                String key = String.format("search.email.%s", i);
                String email = preferences.getString(key, "");
                if (!email.isEmpty()) {
                    emails.add(email);
                }
            }
        }

        return emails;
    }

    private static void showWearNotification(String title, String message) {
        // Build intent for notification content
        Intent viewIntent = new Intent(WhereApplication.getContext(), GooglePlayServicesActivity.class);
        PendingIntent viewPendingIntent = PendingIntent.getActivity(WhereApplication.getContext(), 0, viewIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(WhereApplication.getContext())
                        .setSmallIcon(R.drawable.find_user)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setContentIntent(viewPendingIntent);

        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(WhereApplication.getContext());

        // Build the notification and issues it with notification manager.
        notificationManager.notify(notificationId, notificationBuilder.build());

        new Timer("where-notification-handler", true).schedule(new TimerTask() {
            @Override
            public void run() {
                dismissNotification();
            }
        }, NOTIFICATION_HIDE_DELAY_MS);
    }

    private static void dismissNotification() {
        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(WhereApplication.getContext());
        notificationManager.cancel(notificationId);
    }
}
