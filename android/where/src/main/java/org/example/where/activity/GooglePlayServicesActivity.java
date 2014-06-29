package org.example.where.activity;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.appspot.unique_voyage_610.where.model.WhereStatus;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import org.example.where.R;
import org.example.where.application.WhereApplication;
import org.example.where.util.WhereHelper;
import org.example.where.service.WhereBinder;
import org.example.where.service.WhereService;
import org.example.where.util.GetAddressTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class GooglePlayServicesActivity extends Activity {
    private static final String TAG = "GooglePlayServicesActivity";

    /**
     * Request code for auto Google Play Services error resolution.
     */
    private static final int REQUEST_CODE_RESOLUTION = 1;

    /* Request code for Google Account Picker */
    private static final int REQUEST_ACCOUNT_PICKER = 2;

    /* Request code for Google Play Services resolution */
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 3;

    /* Request code for Google Location resolution */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 4;

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";
    private static final String KEY_LAST_CHECKED_EMAIL = "last_email";

    /* Messenger codes */
    public static final int GOOGLE_API_CLIENT_CONNECTION_FAILURE = 0xFA00;
    public static final int SHOW_ACCOUNT_PICKER = 0xFA01;
    public static final int GAE_RESPONSE = 0xFA02;
    public static final int ACCOUNT_NAME_RETRIEVED = 0xFA03;
    public static final int DISPLAY_MESSAGE = 0xFA04;
    public static final int DISPLAY_GOOGLE_PLAY_SERVICES_ERR = 0xFA05;
    public static final int GAE_LOCATION_RESPONSE = 0xFA06;
    public static final int GOOGLE_LOCATION_CLIENT_CONNECTION_FAILURE = 0xFA07;
    public static final int LOCATION_REQUEST = 0xFA08;
    public static final int LOCATION_PUSH = 0xFA09;
    public static final int GOOGLE_CLIENT_CONNECTED = 0xFA0A;
    public static final String KEY_SHOULD_UPDATE_ACTIVE_FLAG = "should_update_active_flag";

    private String lastCheckedEmail;

    private WhereService whereService;
    private boolean shouldUpdateActiveFlag;
    private boolean shouldDisplayUserDetails;

    private Menu mainMenu;

    /**
     * Called when the activity is starting. Restores the activity state.
     * locked and unlocked icons: http://www.iconarchive.com/show/oxygen-icons-by-oxygen-icons.org/Actions-document-encrypt-icon.html
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWhereApplication().setMessenger(new Messenger(handler));
        getWhereApplication().setInResolution(false);

        /* set either main either login layout */
        if (WhereHelper.checkForPreviousGoogleLogin()) {
            setupMainLayoutComponents();
        } else {
            setupLoginComponents();
        }
    }

    private void setupLoginComponents() {
        setContentView(R.layout.layout_login);

        findViewById(R.id.btnGoogleSignIn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLoginButtonClick();
            }
        });

        // cancel any running Wear notification
        getWhereApplication().getWearHelper().cancelNotification();
    }

    private void setupMainLayoutComponents() {
        setContentView(R.layout.layout_main);

        final AutoCompleteTextView editTextEmail = (AutoCompleteTextView) findViewById(R.id.autoCompleteEmailAddress);
        List<String> lastSearchedEmails = WhereHelper.getLastSearchedEmails();
        if (!lastSearchedEmails.isEmpty()) {
            ArrayAdapter<String> emailsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, lastSearchedEmails);
            editTextEmail.setAdapter(emailsAdapter);
        }

        editTextEmail.setText(lastCheckedEmail);

        findViewById(R.id.buttonCheckRegistration).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideKeyboard();

                if (WhereHelper.isAuthenticated()) {
                    String accountName = editTextEmail.getText().toString();
                    lastCheckedEmail = accountName;
                    WhereHelper.isRegistered(accountName);
                } else {
                    Toast.makeText(GooglePlayServicesActivity.this, "Please login first!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.buttonLocate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideKeyboard();

                String accountName = editTextEmail.getText().toString();
                lastCheckedEmail = accountName;
                if (WhereHelper.isAuthenticated()) {
                    WhereHelper.askForLocation(accountName);
                } else {
                    Toast.makeText(GooglePlayServicesActivity.this, "Please login first!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // user is already logged-on, start the Where service
        startService(new Intent(this, WhereService.class));

        // show Wear notification
        WhereApplication.getContext().getWearHelper().showNotification("Who are you looking for?");
    }

    /**
     * Called when the Activity is made visible.
     * A connection to Play Services need to be initiated as
     * soon as the activity is visible. Registers {@code ConnectionCallbacks}
     * and {@code OnConnectionFailedListener} on the
     * activities itself.
     */
    @Override
    protected void onStart() {
        super.onStart();

        getWhereApplication().connectToGoogleApiClient();
        getWhereApplication().getLocationClient().connect();
    }

    /**
     * Called when activity gets invisible. Connection to Play Services needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onStop() {

        getWhereApplication().disconnectGoogleApiClient();
        getWhereApplication().getLocationClient().disconnect();
        Log.i(TAG, "onStop: purposely NOT stopping Google API and Google Location clients");
        super.onStop();
    }

    // You need to do the Play Services APK check here too.
    @Override
    protected void onResume() {
        super.onResume();
        getWhereApplication().checkPlayServices();

        SharedPreferences preferences = getWhereApplication().getPreferences();
        lastCheckedEmail = preferences.getString(KEY_LAST_CHECKED_EMAIL, "");
        shouldUpdateActiveFlag = preferences.getBoolean(KEY_SHOULD_UPDATE_ACTIVE_FLAG, false);

        Log.i(TAG, "onResume: shouldUpdateActiveFlag: " + shouldUpdateActiveFlag);
        Log.i(TAG, "onResume: activeFlag: " + getWhereApplication().isActive());

        // bind local Where service
        Intent intent= new Intent(this, WhereService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = getWhereApplication().getPreferences().edit();
        editor.putString(KEY_LAST_CHECKED_EMAIL, lastCheckedEmail);
        editor.putBoolean(KEY_IN_RESOLUTION, getWhereApplication().isInResolution());
        editor.putBoolean(KEY_SHOULD_UPDATE_ACTIVE_FLAG, shouldUpdateActiveFlag);

        Log.i(TAG, "onPause: shouldUpdateActiveFlag: " + shouldUpdateActiveFlag);
        Log.i(TAG, "onPause: activeFlag: " + getWhereApplication().isActive());

        editor.commit();

        // disconnect from Where service
        unbindService(mConnection);
    }

    /* Create application menu */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        mainMenu = menu;
        updateMainMenu();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_item_toggle_active:
                onMenuActiveFlagClick();
                return true;
            case R.id.menu_item_logout:
                onLogout();
                return true;
            case R.id.menu_item_current_location:
                onLastLocationClick();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Handles Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                getWhereApplication().retryConnectingToGoogleClient();
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (data != null && data.getExtras() != null) {
                    String accountName = data.getExtras().getString(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        WhereHelper.setSelectedAccountName(accountName);

                        // User is authorized.
                        setupMainLayoutComponents();
                        displayUserDetails();

                        if (whereService != null) {
                            updateActiveFlagOnServer(getWhereApplication().isActive(), null);
                        } else {
                            shouldUpdateActiveFlag = true;
                        }
                    }
                }
                break;
        }
    }

    private void displayUserDetails() {
        try {
            GoogleApiClient mGoogleApiClient = getWhereApplication().getGoogleApiClient();
            if (mGoogleApiClient.isConnecting()) {
                shouldDisplayUserDetails = true;
                return;
            }

            Log.i(TAG, "Displaying user's details.");
            if (Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) != null) {
                Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
                String personName = currentPerson.getDisplayName();
                String personPhotoUrl = currentPerson.getImage().getUrl();
                String personGooglePlusProfile = currentPerson.getUrl();
                String email = Plus.AccountApi.getAccountName(mGoogleApiClient);

                Log.e(TAG, "Name: " + personName + ", plusProfile: "
                        + personGooglePlusProfile + ", email: " + email
                        + ", Image: " + personPhotoUrl);

                TextView txtDisplayName = (TextView) findViewById(R.id.textViewDisplayName);
                txtDisplayName.setText(personName);

                TextView txtEmail = (TextView) findViewById(R.id.textViewEmailAddress);
                txtEmail.setText(email);

            } else {
                Log.w(TAG, "User details are null.");
                Toast.makeText(getApplicationContext(), "Unable to display user's information", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setTextStatus(String status) {
        TextView textStatus = (TextView) findViewById(R.id.textStatus);
        if (textStatus != null) {
            textStatus.setText(status);
        } else {
            Log.w(TAG, status);
        }
    }

    private android.os.Handler handler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISPLAY_MESSAGE:
                    String sMessage = (String) msg.obj;
                    setTextStatus(sMessage);
                    break;
                case GOOGLE_CLIENT_CONNECTED:
                    getWhereApplication().setInResolution(false);
                    if (WhereHelper.checkForPreviousGoogleLogin()) {
                        displayUserDetails();
                    }
                    if (shouldUpdateActiveFlag) {
                        updateActiveFlagOnServer(getWhereApplication().isActive(), null);
                    }
                    break;
                case GOOGLE_API_CLIENT_CONNECTION_FAILURE:
                    onGoogleApiClientConnectionFailure((ConnectionResult) msg.obj);
                    break;
                case DISPLAY_GOOGLE_PLAY_SERVICES_ERR:
                    int resultCode = msg.arg1;
                    if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                        GooglePlayServicesUtil.getErrorDialog(resultCode, GooglePlayServicesActivity.this,
                                PLAY_SERVICES_RESOLUTION_REQUEST).show();
                    } else {
                        Log.i(TAG, "This device is not supported by Google Services.");

                        finish();
                    }
                    break;
                case GOOGLE_LOCATION_CLIENT_CONNECTION_FAILURE:
                    ConnectionResult connectionResult = (ConnectionResult) msg.obj;
                    if (connectionResult.hasResolution()) {
                        try {
                            // Start an Activity that tries to resolve the error
                            connectionResult.startResolutionForResult(
                                    GooglePlayServicesActivity.this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                        /*
                         * Thrown if Google Play services canceled the original
                         * PendingIntent
                         */
                        } catch (IntentSender.SendIntentException e) {
                            // Log the error
                            e.printStackTrace();
                        }
                    } else {
                        /*
                         * If no resolution is available, display a dialog to the
                         * user with the error.
                         */
                        Toast.makeText(getApplicationContext(), "Error while obtaining location: " + connectionResult.toString(), Toast.LENGTH_SHORT).show();
                    }
                case SHOW_ACCOUNT_PICKER:
                    GoogleAccountCredential credential = getWhereApplication().getCredential();
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                    break;
                case ACCOUNT_NAME_RETRIEVED:
                    Log.i(TAG, "Account name retrieved.");
                    setupMainLayoutComponents();
                    displayUserDetails();

                    if (whereService != null) {
                        updateActiveFlagOnServer(getWhereApplication().isActive(), null);
                    } else {
                        shouldUpdateActiveFlag = true;
                        Log.i(TAG, "handleMessage ACCOUNT_NAME_RETRIEVED: shouldUpdateActiveFlag: " + shouldUpdateActiveFlag);
                    }
                    break;
                case GAE_RESPONSE:
                    WhereStatus status = (WhereStatus) msg.obj;
                    if (status != null) {
                        if (status.getCode() == 1 || status.getCode() == 2) {
                              getWhereApplication().getWearHelper().showNotFoundNotification("Where?", status.getMessage());
                        }
                        setTextStatus(status.getMessage());
                    }
                    break;
                case GAE_LOCATION_RESPONSE:
                    setTextStatus("Received Location request from");
                    break;
                case LOCATION_REQUEST:
                    TextView textViewRequester = (TextView)findViewById(R.id.textViewRequester);
                    String now = new SimpleDateFormat("HH:mm").format(new Date());
                    textViewRequester.setText(String.valueOf(msg.obj) + " at " + now);
                    break;
                case LOCATION_PUSH:
                    Bundle extras = (Bundle)msg.obj;
                    TextView textViewWho = (TextView) findViewById(R.id.textViewWho);
                    TextView textViewWhere = (TextView) findViewById(R.id.textViewWhere);
                    TextView textViewWhen = (TextView) findViewById(R.id.textViewWhen);

                    textViewWho.setText(extras.getString("user", "unknown"));
                    textViewWhere.setText(extras.getString("location", "unknown"));
                    textViewWhen.setText(new Date().toString());
                default:
                    break;
            }
        }
    };

    private void onLogout() {
        setupLoginComponents();

        // on server, mark user as inactive
        updateActiveFlagOnServer(false, new Runnable() {
            @Override
            public void run() {
                WhereHelper.clearCredential();
            }
        });

        // stop service
        stopService(new Intent(this, WhereService.class));

        Toast.makeText(this, "Logout completed.", Toast.LENGTH_SHORT).show();
    }

    private void onLoginButtonClick() {
        WhereHelper.prepareGoogleCredential();
    }

    private void onLastLocationClick() {
        final Location currentLocation = getWhereApplication().getLocationClient().getLastLocation();
        setTextStatus(currentLocation.toString() + "\nGeocoding in progress...");

        new GetAddressTask(this) {
            @Override
            protected void onPostExecute(String s) {
                StringBuilder sb = new StringBuilder();
                sb.append("latitude: ").append(currentLocation.getLatitude()).append("\n");
                sb.append("longitude: ").append(currentLocation.getLongitude()).append("\n");
                sb.append("altitude: ").append(currentLocation.getAltitude()).append("\n");
                sb.append("speed: ").append(currentLocation.getSpeed()).append("\n");
                sb.append("time: ").append(new Date(currentLocation.getTime())).append("\n");
                sb.append("accuracy: ").append(currentLocation.getAccuracy()).append("\n");
                sb.append("provider: ").append(currentLocation.getProvider()).append("\n");
                sb.append(s);
                setTextStatus(sb.toString());
            }
        }.execute(currentLocation);
    }

    private void onGoogleApiClientConnectionFailure(ConnectionResult result) {
        if (!result.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    result.getErrorCode(), this, 0, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            getWhereApplication().retryConnectingToGoogleClient();
                        }
                    }
            ).show();

            setTextStatus("Unable to authenticate.");
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (getWhereApplication().isInResolution()) {
            return;
        }
        getWhereApplication().setInResolution(true);
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            getWhereApplication().retryConnectingToGoogleClient();
        }
    }

    public WhereApplication getWhereApplication() {
        return (WhereApplication) getApplication();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            WhereBinder b = (WhereBinder) binder;
            whereService = b.getService();

            if (shouldUpdateActiveFlag) {
                shouldUpdateActiveFlag = false;
                Log.i(TAG, "Where Service is connected. Setting user's status to: " + (getWhereApplication().isActive()?"":"IN") + "active");
                updateActiveFlagOnServer(getWhereApplication().isActive(), null);
            } else {
                Log.i(TAG, "Where Service is connected.");
            }

            if (shouldDisplayUserDetails) {
                shouldDisplayUserDetails = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayUserDetails();
                    }
                });
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            whereService = null;
        }
    };

    private void updateMainMenu() {
        if (mainMenu == null) {
            Log.i(TAG, "Unable to update menu: main  menu is NULL");
            return;
        }

        // set correct menu actions
        MenuItem menuToggleActiveFlag = mainMenu.findItem(R.id.menu_item_toggle_active);
        if (getWhereApplication().isActive()) {
            menuToggleActiveFlag.setTitle(R.string.reject_location_queries);
            menuToggleActiveFlag.setIcon(R.drawable.locked);
        } else {
            menuToggleActiveFlag.setTitle(R.string.accept_location_queries);
            menuToggleActiveFlag.setIcon(R.drawable.unlocked);
        }
    }

    private void onMenuActiveFlagClick() {
        boolean activeFlag = getWhereApplication().flipActive();
        Log.i(TAG, "Active flag is now: " + activeFlag);
        if (whereService != null) {
            updateActiveFlagOnServer(activeFlag, null);
            updateMainMenu();
        }
    }

    private void updateActiveFlagOnServer(boolean activeFlag, Runnable runnable) {
        if (whereService == null) {
            Log.i(TAG, "unable to change active flag: Where service is NULL");
            Toast.makeText(this, "Unable to update settings on server", Toast.LENGTH_SHORT).show();
        } else {
            WhereHelper.sendActiveFlag(activeFlag, runnable);
            shouldUpdateActiveFlag = false;
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.autoCompleteEmailAddress).getWindowToken(), 0);
    }
}
