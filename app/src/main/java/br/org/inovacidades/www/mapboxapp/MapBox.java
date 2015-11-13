package br.org.inovacidades.www.mapboxapp;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.mapboxsdk.events.MapListener;
import com.mapbox.mapboxsdk.events.RotateEvent;
import com.mapbox.mapboxsdk.events.ScrollEvent;
import com.mapbox.mapboxsdk.events.ZoomEvent;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.Marker;
import com.mapbox.mapboxsdk.tileprovider.tilesource.MapboxTileLayer;
import com.mapbox.mapboxsdk.views.MapView;
import com.metaio.sdk.MetaioDebug;
import com.metaio.tools.io.AssetsManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

public class MapBox extends AppCompatActivity implements
        ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    private MapView mapView;
    private LatLng mMyLocation;

    private GoogleApiClient mGoogleApiClient;

    private LocationRequest mLocationRequest;
    private Location mLastLocation;
    private Location mCurrentLocation;
    private boolean mRequestingLocationUpdates = false;
    private static final String REQUESTING_LOCATION_UPDATES_KEY = "request_key";
    private static final String LOCATION_KEY = "location_key";

    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final String DIALOG_ERROR = "erro_de_conexão";
    private boolean mResolvingError = false;

    private ProgressDialog pDialog;

    private ArrayList<Marker> markers = new ArrayList<>();
    private ArrayList<MyMarker> myMarkers = new ArrayList<>();
    private Marker[] markersArray;
    private static final String MARKERS_ARRAY_KEY = "Markers Array";
    private Marker userMarker;

    private boolean mapLoaded = false;

    private Bitmap markerBitmap;
    private Drawable markerIcon;
    private Drawable userMarkerIcon;

    AssetsExtract mTask;

    private Button btnCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        updateValuesFromBundle(savedInstanceState);

        setContentView(R.layout.activity_map_box);

        Drawable markerIconProv = ContextCompat.getDrawable(this, R.drawable.ponto);
        if (markerIconProv != null) {
            markerBitmap = ((BitmapDrawable) markerIconProv).getBitmap();
            markerIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(markerBitmap, 149, 149, true));
        }

        Drawable userMarkerIconProv = ContextCompat.getDrawable(this, R.drawable.userponto);
        if (userMarkerIconProv != null) {
            Bitmap userMarkerBitmap = ((BitmapDrawable) userMarkerIconProv).getBitmap();
            userMarkerIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(userMarkerBitmap, 12, 12, true));
        }

        buildGoogleApiClient();
        createMap();

        new MemoriesGetter().execute();

        btnCamera = (Button) findViewById(R.id.btn_camera);

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTask = new AssetsExtract();
                mTask.execute(0);

                Bundle b = new Bundle();
                b.putSerializable(MARKERS_ARRAY_KEY, myMarkers);

                Intent intent = new Intent(getApplicationContext(), ARActivity.class);
                intent.putExtra("BUNDLE", b);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            starLocationUpdates();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                if (!mGoogleApiClient.isConnecting() && !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
        outState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        outState.putParcelable(LOCATION_KEY, mCurrentLocation);
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(REQUESTING_LOCATION_UPDATES_KEY);
            }

            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            updateUI();
        }
    }

    public void createMap() {
        mapView = (MapView) this.findViewById(R.id.mapview);
        mapView.setAccessToken("pk.eyJ1IjoibWFyaW9kZWNhc3RybyIsImEiOiJjaWY3MzM0czYwMXM3c2xsenBhMXZhaDV4In0.ZArC1jz2WHEiRX3u6vZWDQ");
        mapView.setTileSource(new MapboxTileLayer("mariodecastro.nj6g39mc"));

        mapView.setMinZoomLevel(5);
        mapView.setMaxZoomLevel(18);

        mapView.addListener(new MapListener() {
            @Override
            public void onScroll(ScrollEvent event) {

            }

            @Override
            public void onZoom(ZoomEvent event) {
                if (mapLoaded) {
                    float zoom = event.getZoomLevel();
                    int size = Math.round(((129 * (zoom - 5)) / 13) + 20);

                    markerIcon = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(markerBitmap, size, size, true));
                    for (Marker aMarkersArray : markersArray) {
                        aMarkersArray.setMarker(markerIcon);
                    }
                }
            }

            @Override
            public void onRotate(RotateEvent event) {

            }
        });

        if (mMyLocation != null) {
            mapView.setCenter(mMyLocation);
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        if (!mRequestingLocationUpdates) {
            starLocationUpdates();
        }

        if (mLastLocation != null) {
            mMyLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

            if (mapView != null) {
                mapView.setCenter(mMyLocation);
            }
        }
    }

    protected void starLocationUpdates() {
        createLocationRequest();
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        mRequestingLocationUpdates = true;
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mRequestingLocationUpdates = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        updateUI();
    }

    private void updateUI() {
        if (mCurrentLocation != null) {
            mLastLocation = mCurrentLocation;
        }

        if (mLastLocation != null) {
            mMyLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

            int smallestDistance = Integer.MAX_VALUE;

            if (mapLoaded) {
                userMarker.setPoint(mMyLocation);

                for (Marker aMarkersArray : markersArray) {
                    LatLng newLatLng = aMarkersArray.getPosition();
                    int distance = mMyLocation.distanceTo(newLatLng);

                    if (distance < smallestDistance) {
                        smallestDistance = distance;
                    }
                }

                if (smallestDistance <= 100) {
                    smallDistance();
                } else if (smallestDistance <= 1000) {
                    mediumDistance(smallestDistance);
                } else {
                    largeDistance(smallestDistance);
                }
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (mResolvingError) {
            Log.d("Response: ", "> Already resolving error");
        } else if (connectionResult.hasResolution()) {
            try {
                mResolvingError = true;
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                mGoogleApiClient.connect();
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError = true;
        }
    }

    private void showErrorDialog(int errorCode) {
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();

        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "deu_erro");
    }

    public void onDialogDismissed() {
        mResolvingError = false;
    }

    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {}

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MapBox) getActivity()).onDialogDismissed();
        }
    }

    private class MemoriesGetter extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            pDialog = new ProgressDialog(MapBox.this);
            pDialog.setMessage("Carregando Memórias...");
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            ServiceHandler sh = new ServiceHandler();

            String url = "http://cidadeaumentada.esy.es/siteTeste/php/visualisasasai.php";
            String jsonStr = sh.makeServiceCall(url, ServiceHandler.GET);

            if(jsonStr != null) {
                try {
                    JSONArray memories = new JSONArray(jsonStr);

                    for (int i = 0; i < memories.length(); i++) {

                        JSONArray c = memories.getJSONArray(i);

                        int id = c.getInt(0);

                        double latitude;
                        try {
                            latitude = c.getDouble(1);
                        } catch (JSONException e) {
                            latitude = 0;
                        }

                        double longitude;
                        try {
                            longitude = c.getDouble(2);
                        } catch (JSONException e) {
                            longitude = 0;
                        }

                        if (latitude != 0 && longitude != 0) {
                            markers.add(new Marker(mapView, "Titulo", "" + id, new LatLng(latitude, longitude)));
                            myMarkers.add(new MyMarker("Titulo", "" + id, latitude, longitude));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                Log.e("ServiceHandler", "Couldn't get any data from the url");
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (pDialog.isShowing()) {
                pDialog.dismiss();
            }

            markersArray = new Marker[markers.size()];
            markers.toArray(markersArray);
            int smallestDistance = Integer.MAX_VALUE;

            for (Marker aMarkersArray : markersArray) {
                LatLng newLatLng = aMarkersArray.getPosition();
                int distance = mMyLocation.distanceTo(newLatLng);

                aMarkersArray.setMarker(markerIcon);
                aMarkersArray.setHotspot(Marker.HotspotPlace.CENTER);

                if (distance < smallestDistance) {
                    smallestDistance = distance;
                }

                mapView.addMarker(aMarkersArray);
            }

            if (smallestDistance <= 100) {
                smallDistance();
                mapView.setZoom(18);
            } else if (smallestDistance <= 1000) {
                mediumDistance(smallestDistance);
                mapView.setZoom(16);
            } else {
                largeDistance(smallestDistance);
            }

            userMarker = new Marker(mapView, "Usuário", "Você está aqui!", mMyLocation);

            userMarker.setMarker(userMarkerIcon);
            userMarker.setHotspot(Marker.HotspotPlace.CENTER);

            mapView.addMarker(userMarker);

            mapLoaded = true;
        }
    }

    private void smallDistance() {
        btnCamera.setVisibility(View.VISIBLE);
    }

    private void mediumDistance(int distance) {
        Toast.makeText(MapBox.this, "Desenha rota? " + distance, Toast.LENGTH_LONG).show();
        btnCamera.setVisibility(View.INVISIBLE);
    }

    private void largeDistance(int distance) {
        Toast.makeText(MapBox.this, "Muito longe! " + distance, Toast.LENGTH_LONG).show();
        btnCamera.setVisibility(View.INVISIBLE);
    }

    private class AssetsExtract extends AsyncTask<Integer, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Integer... params) {
            try {
                AssetsManager.extractAllAssets(getApplicationContext(), true);
            } catch (IOException e) {
                MetaioDebug.printStackTrace(Log.ERROR, e);
                return false;
            }

            return true;
        }
    }
}

