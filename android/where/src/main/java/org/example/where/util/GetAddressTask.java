package org.example.where.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * A subclass of AsyncTask that calls getFromLocation() in the
 * background. The class definition has these generic types:
 * Location - A Location object containing
 * the current location.
 * Void     - indicates that progress units are not used
 * String   - An address passed to onPostExecute()
 */
public class GetAddressTask extends AsyncTask<Location, Void, String> {
    public static final String TAG = GetAddressTask.class.getName();
    
    private Context mContext;
    
    public GetAddressTask(Context context) {
        mContext = context;
    }

    /**
     * Get a Geocoder instance, get the latitude and longitude
     * look up the address, and return it
     *
     * @params params One or more Location objects
     * @return A string containing the address of the current
     * location, or an empty string if no address can be found,
     * or an error message
     */
    @Override
    protected String doInBackground(Location... params) {
        Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
        // Get the current location from the input parameter list
        Location loc = params[0];
        // Create a list to contain the result address
        List<Address> addresses = null;
        int retries = 0;

        String errorMessage = null;
        while (retries<2) {
            try {
                /* Return 1 address. */
                addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);

                break;
            } catch (IOException e1) {
                errorMessage = "IO Exception trying to get address";
                Log.e(TAG, errorMessage, e1);
            } catch (IllegalArgumentException e2) {
                // Error message to post in the log
                errorMessage = "Illegal arguments " +
                        Double.toString(loc.getLatitude()) +
                        " , " +
                        Double.toString(loc.getLongitude()) +
                        " passed to address service";
                Log.e(TAG, errorMessage);
                e2.printStackTrace();
            }

            retries++;
        }

        // an error indicates that after 2 retries the geocoding was still unsuccessful
        if (errorMessage != null) {
            return errorMessage;
        }

        String addressText = "No Address found";

        // If the reverse geocode returned an address
        if (addresses != null && addresses.size() > 0) {
            // Get the first address
            Address address = addresses.get(0);
                /* Format the first line of address (if available),
                 * city, and country name. */
            addressText = String.format(
                    "%s, %s, %s",
                    // If there's a street address, add it
                    address.getMaxAddressLineIndex() > 0 ?
                            address.getAddressLine(0) : "",
                    // Locality is usually a city
                    address.getLocality(),
                    // The country of the address
                    address.getCountryName());
        }

        // Return the text
        return addressText;
    }
}
