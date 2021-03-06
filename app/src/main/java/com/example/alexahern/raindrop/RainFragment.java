package com.example.alexahern.raindrop;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class RainFragment extends Fragment implements FragmentCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private ProgressBar spinningLoader;
    private TextView chanceOfRain;
    private TextView periodOfMeasurement;
    private TextView lastUpdated;
    private GetRainTask rainTask;

    private String timeFrame;
    private String[] latitudeAndLongitude = new String[2];
    private ShareActionProvider mShareActionProvider;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    public String getTimeFrame() {
        return timeFrame;
    }

    @Override
    public String getApiKey() {
        return this.getString(R.string.apikey);
    }

    public String[] getLatitudeAndLongitude() {
        return latitudeAndLongitude;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rain_fragment, container, false);
        lastUpdated = ((TextView) rootView.findViewById(R.id.last_updated_textview));
        setLastUpdatedWithStoredTime();
        chanceOfRain = (TextView) rootView.findViewById(R.id.rain_textview);
        periodOfMeasurement = (TextView) rootView.findViewById(R.id.periodOfMeasurement);
        setTimeFrameMessage();
        spinningLoader = (ProgressBar) rootView.findViewById(R.id.spinningLoader);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_rain, menu);
        MenuItem shareMenuItem = menu.findItem(R.id.action_share);

        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareMenuItem);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeatherWithNewLocation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateWeatherWithNewLocation() {
        Location currentLocation = getLocationIfHavePermission();
        updateLocation(currentLocation);
        if (currentLocation != null) {
            updateWeather();
        }
    }

    public Location getLocationIfHavePermission() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return null;
        } else {
            return LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
        }
    }

    public void updateLocation(Location mLastLocation) {
        if (mLastLocation != null) {
            latitudeAndLongitude[0] = mLastLocation.getLatitude() + "";
            latitudeAndLongitude[1] = mLastLocation.getLongitude() + "";
            saveLastLocationToSharedPreferences();
        }
    }

    public void saveLastLocationToSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("latitude_key", latitudeAndLongitude[0]);
        editor.putString("longitude_key", latitudeAndLongitude[1]);
        editor.apply();
    }

    public SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    private void updateWeather() {
        setTimeFrameMessage();
        if (thereIsANetwork()) {
            setLastUpdatedWithStoredTime();
            executeRainTask();
        } else {
            displayNetworkErrorMessage();
        }
    }

    public void setTimeFrameMessage() {
        if (thereIsANetwork()) {
            setTimeFrameFromPreference();
            if (timeFrame.equals("hourly")) {
                periodOfMeasurement.setText(getString(R.string.hourly_timeframe_message));
            } else if (timeFrame.equals("daily")) {
                periodOfMeasurement.setText(getString(R.string.daily_timeframe_message));
            }
        }
    }

    public boolean thereIsANetwork() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        return (networkInfo != null && networkInfo.isConnected());
    }

    public void setTimeFrameFromPreference() {
        SharedPreferences prefs = getSharedPreferences();
        timeFrame = prefs.getString(getString(R.string.pref_timeframe_key),
                getString(R.string.pref_timeframe_daily));
    }

    public void setLastUpdatedWithStoredTime() {
        SharedPreferences prefs = getSharedPreferences();
        lastUpdated.setText(getString(R.string.last_updated_message, prefs.getString("last_updated_key", "never")));
    }

    public void executeRainTask() {
        rainTask = new GetRainTask(this);
        rainTask.execute();
    }

    public void displayNetworkErrorMessage() {
        Toast.makeText(getContext(), "NETWORK ERROR", Toast.LENGTH_SHORT).show();
    }


    public void setShareIntent(String textToShare) {
        Intent shareButtonIntent = new Intent();
        shareButtonIntent.setAction(Intent.ACTION_SEND);
        shareButtonIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
        shareButtonIntent.setType("text/plain");
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(shareButtonIntent);
        }
    }


    @Override
    public void displayResult(int result) {
        hideLoading();
        chanceOfRain.setText(result + "");
        chanceOfRain.append("%");
        setLastUpdatedWithNewTime();
    }

    public void setLastUpdatedWithNewTime() {
        String currentTime = getCurrentTime();
        setTimeFrameMessage();
        lastUpdated.setText(getString(R.string.last_updated_message, currentTime));
        saveCurrentTimeToSharedPreferences(currentTime);
    }

    public String getCurrentTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(cal.getTime());
    }

    public void saveCurrentTimeToSharedPreferences(String currentTime) {
        SharedPreferences prefs = getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_updated_key", currentTime);
        editor.apply();
    }

    @Override
    public void displayLoading() {
        chanceOfRain.setVisibility(View.GONE);
        spinningLoader.setVisibility(View.VISIBLE);
    }

    public void hideLoading() {
        spinningLoader.setVisibility(View.GONE);
        chanceOfRain.setVisibility(View.VISIBLE);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        updateWeatherWithNewLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(rainTask != null && rainTask.getStatus() == AsyncTask.Status.RUNNING)
            rainTask.cancel(true);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        String LOG_TAG = getClass().getSimpleName();
        Log.e("ERROR" + connectionResult, LOG_TAG);
    }

}
