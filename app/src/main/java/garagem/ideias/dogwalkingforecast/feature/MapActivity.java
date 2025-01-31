package garagem.ideias.dogwalkingforecast.feature;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import android.os.AsyncTask;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import garagem.ideias.dogwalkingforecast.R;
import android.content.Intent;
import android.net.Uri;
import com.google.android.material.appbar.MaterialToolbar;
import android.location.LocationManager;
import android.app.AlertDialog;
import android.content.Context;

public class MapActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private MapView mapView;
    private FusedLocationProviderClient locationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_map);

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.mapToolbar);
        setSupportActionBar(toolbar);
        
        // Setup back button
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Initialize map
        mapView = findViewById(R.id.map);
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // Initialize location client
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        // Check permissions and get location
        if (checkLocationPermission()) {
            getCurrentLocation();
        }

        // Check location services
        checkLocationServices();
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // Clear any existing overlays
                        mapView.getOverlays().clear();
                        
                        // Set user location
                        GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                        mapView.getController().setCenter(userLocation);
                        
                        // Add user marker
                        Marker userMarker = new Marker(mapView);
                        userMarker.setPosition(userLocation);
                        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        userMarker.setIcon(getResources().getDrawable(R.drawable.ic_user_location));
                        userMarker.setTitle("You are here");
                        mapView.getOverlays().add(userMarker);
                        
                        // Search for nearby places
                        searchNearbyPlaces(location.getLatitude(), location.getLongitude());
                        
                        // Refresh map
                        mapView.invalidate();
                    } else {
                        Toast.makeText(this, "Unable to get your location. Please check if GPS is enabled.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error getting location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh location when activity resumes
        if (checkLocationPermission()) {
            getCurrentLocation();
        }
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission is required to show nearby places", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void searchNearbyPlaces(double latitude, double longitude) {
        // Search for dog parks
        searchPlacesByType(latitude, longitude, "leisure=dog_park", R.drawable.ic_dog_paw, "Dog Park");
        
        // Search for veterinarians (both regular and emergency)
        searchPlacesByType(latitude, longitude, "amenity=veterinary", R.drawable.ic_vet, "Veterinary");
    }

    private void searchPlacesByType(double latitude, double longitude, String query, 
            int markerIcon, String placeType) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    // Modified query to get only the center points of areas
                    String overpassUrl;
                    if (query.contains("leisure=dog_park")) {
                        overpassUrl = String.format(
                            "https://overpass-api.de/api/interpreter?data=" +
                            "[out:json][timeout:25];" +
                            "(" +
                            "  way[%s](around:5000,%f,%f);" +
                            ");" +
                            "out center;" + // Get center points for ways
                            "node[%s](around:5000,%f,%f);" +
                            "out;",
                            query, latitude, longitude,
                            query, latitude, longitude
                        );
                    } else {
                        // Original query for other types (vets)
                        overpassUrl = String.format(
                            "https://overpass-api.de/api/interpreter?data=" +
                            "[out:json][timeout:25];" +
                            "(" +
                            "  node[%s](around:5000,%f,%f);" +
                            ");" +
                            "out;",
                            query, latitude, longitude
                        );
                    }

                    URL url = new URL(overpassUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        result.append(line);
                    }
                    rd.close();
                    return result.toString();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    addMarkersToMap(result, markerIcon, placeType);
                }
            }
        }.execute();
    }

    private void addMarkersToMap(String jsonResult, int markerIcon, String placeType) {
        try {
            JSONObject data = new JSONObject(jsonResult);
            JSONArray elements = data.getJSONArray("elements");
            Set<String> addedLocations = new HashSet<>();

            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                
                // Get coordinates either directly or from center
                final double[] coordinates = new double[2]; // [lat, lon]
                if (element.has("lat") && element.has("lon")) {
                    coordinates[0] = element.getDouble("lat");
                    coordinates[1] = element.getDouble("lon");
                } else if (element.has("center")) {
                    JSONObject center = element.getJSONObject("center");
                    coordinates[0] = center.getDouble("lat");
                    coordinates[1] = center.getDouble("lon");
                } else {
                    continue;  // Skip if no coordinates found
                }

                // Create a unique key for this location
                String locationKey = String.format("%.5f,%.5f", coordinates[0], coordinates[1]);
                
                // Skip if we've already added a marker at this location
                if (addedLocations.contains(locationKey)) {
                    continue;
                }
                addedLocations.add(locationKey);

                // Get name from tags if available
                final String name = element.has("tags") && 
                                  element.getJSONObject("tags").has("name") ? 
                                  element.getJSONObject("tags").getString("name") : 
                                  placeType;

                GeoPoint location = new GeoPoint(coordinates[0], coordinates[1]);
                Marker marker = new Marker(mapView);
                marker.setPosition(location);
                marker.setTitle(name);
                marker.setSnippet("Tap for directions");
                marker.setIcon(getResources().getDrawable(markerIcon));
                
                // Use the final coordinates array in the lambda
                marker.setOnMarkerClickListener((marker1, mapView) -> {
                    openGoogleMapsNavigation(coordinates[0], coordinates[1]);
                    return true;
                });
                
                mapView.getOverlays().add(marker);
            }
            mapView.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(MapActivity.this, 
                "Error loading " + placeType + " locations", Toast.LENGTH_SHORT).show());
        }
    }

    private void openGoogleMapsNavigation(double destLat, double destLon) {
        // Create a Uri with the destination coordinates
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destLat + "," + destLon + "&mode=w");
        
        // Create an Intent to open Google Maps
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        // Check if Google Maps is installed
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // If Google Maps isn't installed, open in browser
            Uri browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" 
                + destLat + "," + destLon + "&travelmode=walking");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            startActivity(browserIntent);
        }
    }

    private void checkLocationServices() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        
        if (!gpsEnabled) {
            // Show dialog to enable GPS
            new AlertDialog.Builder(this)
                .setMessage("GPS is disabled. Would you like to enable it?")
                .setPositiveButton("Settings", (dialogInterface, i) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
}
