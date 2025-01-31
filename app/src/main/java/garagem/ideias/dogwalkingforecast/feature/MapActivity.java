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

import garagem.ideias.dogwalkingforecast.R;
import android.content.Intent;
import android.net.Uri;
import com.google.android.material.appbar.MaterialToolbar;

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

        mapView = findViewById(R.id.map);
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Set initial map settings
        IMapController mapController = mapView.getController();
        mapController.setZoom(15.0);

        // Initialize the location client
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        // Check and request location permissions
        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getUserLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getUserLocation() {
        locationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();

                            // Center the map on the user's location
                            GeoPoint userLocation = new GeoPoint(latitude, longitude);
                            mapView.getController().setCenter(userLocation);

                            // Add user location marker
                            Marker userMarker = new Marker(mapView);
                            userMarker.setPosition(userLocation);
                            userMarker.setTitle("You are here");
                            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            
                            // Make the marker red and bigger
                            userMarker.setIcon(getResources().getDrawable(R.drawable.ic_user_location));
                            
                            mapView.getOverlays().add(userMarker);
                            mapView.invalidate();

                            // Fetch nearby parks dynamically
                            fetchNearbyDogParks(latitude, longitude);
                        } else {
                            Toast.makeText(MapActivity.this, "Unable to get location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }


    private void addNearbyMarkers(double userLat, double userLon) {
        // Example nearby locations (replace with JSON or API data)
        ArrayList<GeoPoint> nearbyLocations = new ArrayList<>();
        nearbyLocations.add(new GeoPoint(38.7272, -9.1525)); // Eduardo VII Park
        nearbyLocations.add(new GeoPoint(38.7339, -9.1951)); // Monsanto Forest Park
        nearbyLocations.add(new GeoPoint(38.715, -9.14)); // Another example location

        for (GeoPoint location : nearbyLocations) {
            double distance = calculateDistance(userLat, userLon, location.getLatitude(), location.getLongitude());
            if (distance <= 5.0) { // Show locations within 5 km
                Marker marker = new Marker(mapView);
                marker.setPosition(location);
                marker.setTitle("Nearby Dog Park");
                mapView.getOverlays().add(marker);
            }
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000; // Distance in km
    }

    private void fetchNearbyDogParks(double latitude, double longitude) {
        String overpassQuery = "[out:json];"
                + "node[leisure=park](around:5000," + latitude + "," + longitude + ");"
                + "out;";

        String apiUrl = "https://overpass-api.de/api/interpreter?data=" + overpassQuery;

        new FetchDogParksTask().execute(apiUrl);
    }

    private class FetchDogParksTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                return jsonResponse.getJSONArray("elements");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONArray elements) {
            if (elements != null) {
                addDynamicMarkers(elements);
            } else {
                Toast.makeText(MapActivity.this, "Failed to fetch nearby locations", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addDynamicMarkers(JSONArray elements) {
        try {
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                if (element.has("lat") && element.has("lon")) {
                    double lat = element.getDouble("lat");
                    double lon = element.getDouble("lon");

                    GeoPoint parkLocation = new GeoPoint(lat, lon);
                    Marker marker = new Marker(mapView);
                    marker.setPosition(parkLocation);
                    marker.setTitle("Dog-Friendly Park");
                    marker.setSnippet("Tap for directions");
                    
                    // Set paw icon for park markers
                    marker.setIcon(getResources().getDrawable(R.drawable.ic_dog_paw));
                    
                    // Add click listener to marker
                    marker.setOnMarkerClickListener((marker1, mapView) -> {
                        openGoogleMapsNavigation(lat, lon);
                        return true;
                    });
                    
                    mapView.getOverlays().add(marker);
                }
            }
            mapView.invalidate();
            Toast.makeText(this, "Nearby parks loaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
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

}
