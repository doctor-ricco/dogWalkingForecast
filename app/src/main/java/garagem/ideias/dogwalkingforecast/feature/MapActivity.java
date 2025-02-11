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
import java.io.IOException;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.Map;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import java.util.HashMap;
import garagem.ideias.dogwalkingforecast.auth.LoginActivity;

public class MapActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String userId;
    private MapView mapView;
    private FusedLocationProviderClient locationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_map);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        userId = auth.getCurrentUser().getUid();

        // Setup toolbar with add location button
        MaterialToolbar toolbar = findViewById(R.id.mapToolbar);
        toolbar.inflateMenu(R.menu.map_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_location) {
                showAddLocationDialog();
                return true;
            }
            return false;
        });
        
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            
            locationClient.getCurrentLocation(100, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // Clear any existing overlays
                        mapView.getOverlays().clear();
                        
                        // Set user location
                        GeoPoint userLocation = new GeoPoint(
                            location.getLatitude(), 
                            location.getLongitude()
                        );
                        mapView.getController().setCenter(userLocation);
                        
                        // Add user marker
                        addUserMarker(userLocation);
                        
                        // Load user's saved locations
                        loadUserLocations();
                    } else {
                        Toast.makeText(this, 
                            "Could not get current location", 
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Toast.makeText(this, 
                        "Error getting location: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
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

    private void searchNearbyPlaces(Location location) {
        try {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            
            Log.d("MapActivity", String.format("Searching near lat: %f, lon: %f", lat, lon));
            
            // Search for dog parks with a larger radius
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    try {
                        // Try primary API first
                        String overpassUrl = String.format(
                            "https://overpass-api.de/api/interpreter?data=[out:json];(" +
                            "way[\"leisure\"=\"dog_park\"](around:10000,%f,%f);" +
                            "way[\"leisure\"=\"park\"](around:10000,%f,%f);" +
                            "node[\"leisure\"=\"dog_park\"](around:10000,%f,%f);" +
                            "node[\"leisure\"=\"park\"](around:10000,%f,%f);" +
                            ");out body center;",
                            lat, lon, lat, lon, lat, lon, lat, lon);
                        
                        try {
                            String result = makeHttpRequest(overpassUrl);
                            if (result != null) return result;
                        } catch (Exception e) {
                            Log.e("MapActivity", "Primary API failed: " + e.getMessage());
                        }

                        // Try backup API if primary fails
                        String backupUrl = String.format(
                            "https://lz4.overpass-api.de/api/interpreter?data=[out:json];(" +
                            "way[\"leisure\"=\"dog_park\"](around:10000,%f,%f);" +
                            "way[\"leisure\"=\"park\"](around:10000,%f,%f);" +
                            "node[\"leisure\"=\"dog_park\"](around:10000,%f,%f);" +
                            "node[\"leisure\"=\"park\"](around:10000,%f,%f);" +
                            ");out body center;",
                            lat, lon, lat, lon, lat, lon, lat, lon);
                            
                        return makeHttpRequest(backupUrl);
                    } catch (Exception e) {
                        Log.e("MapActivity", "Error fetching parks: " + e.getMessage(), e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(String result) {
                    handlePlacesResult(result, R.drawable.ic_dog_paw, "Park");
                }
            }.execute();

            // Search for veterinarians with a larger radius
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    try {
                        // Try primary API first
                        String overpassUrl = String.format(
                            "https://overpass-api.de/api/interpreter?data=[out:json];(" +
                            "node[\"amenity\"=\"veterinary\"](around:10000,%f,%f);" +
                            "way[\"amenity\"=\"veterinary\"](around:10000,%f,%f);" +
                            ");out body center;",
                            lat, lon, lat, lon);
                        
                        try {
                            String result = makeHttpRequest(overpassUrl);
                            if (result != null) return result;
                        } catch (Exception e) {
                            Log.e("MapActivity", "Primary API failed: " + e.getMessage());
                        }

                        // Try backup API if primary fails
                        String backupUrl = String.format(
                            "https://lz4.overpass-api.de/api/interpreter?data=[out:json];(" +
                            "node[\"amenity\"=\"veterinary\"](around:10000,%f,%f);" +
                            "way[\"amenity\"=\"veterinary\"](around:10000,%f,%f);" +
                            ");out body center;",
                            lat, lon, lat, lon);
                            
                        return makeHttpRequest(backupUrl);
                    } catch (Exception e) {
                        Log.e("MapActivity", "Error fetching vets: " + e.getMessage(), e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(String result) {
                    handlePlacesResult(result, R.drawable.ic_vet, "Veterinary");
                }
            }.execute();

        } catch (Exception e) {
            Log.e("MapActivity", "Error in searchNearbyPlaces: " + e.getMessage(), e);
            Toast.makeText(this, "Error searching nearby places", Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePlacesResult(String result, int iconRes, String placeType) {
        if (result != null) {
            try {
                JSONObject data = new JSONObject(result);
                int count = data.getJSONArray("elements").length();
                Log.d("MapActivity", "Found " + count + " " + placeType.toLowerCase());
                if (count > 0) {
                    addMarkersToMap(result, iconRes, placeType);
                } else {
                    Toast.makeText(MapActivity.this, 
                        "No " + placeType.toLowerCase() + "s found in this area", 
                        Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("MapActivity", "Error parsing " + placeType.toLowerCase() + " data: " + e.getMessage(), e);
                Toast.makeText(MapActivity.this, 
                    "Error loading " + placeType.toLowerCase() + " locations", 
                    Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MapActivity.this, 
                "Could not fetch " + placeType.toLowerCase() + " data", 
                Toast.LENGTH_SHORT).show();
        }
    }

    private String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "DogWalkingForecast Android App");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("MapActivity", "HTTP error code: " + responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            return response.toString();
        } catch (Exception e) {
            Log.e("MapActivity", "Error making HTTP request: " + e.getMessage());
            return null;
        } finally {
            connection.disconnect();
        }
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

    private void loadUserLocations() {
        db.collection("locations")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(documents -> {
                for (DocumentSnapshot doc : documents) {
                    String type = doc.getString("type");
                    String name = doc.getString("name");
                    Double latitude = doc.getDouble("latitude");
                    Double longitude = doc.getDouble("longitude");
                    
                    if (latitude != null && longitude != null) {
                        addMarkerToMap(
                            latitude,
                            longitude,
                            name,
                            type,
                            type.equals("park") ? R.drawable.ic_dog_paw : R.drawable.ic_vet
                        );
                    }
                }
            });
    }

    private void showAddLocationDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_location, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        RadioGroup typeGroup = dialogView.findViewById(R.id.typeGroup);

        new AlertDialog.Builder(this)
            .setTitle("Add Location")
            .setView(dialogView)
            .setPositiveButton("Add", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show();
                    return;
                }

                String type = typeGroup.getCheckedRadioButtonId() == R.id.radioPark ? "park" : "vet";
                org.osmdroid.util.GeoPoint mapCenter = (org.osmdroid.util.GeoPoint) mapView.getMapCenter();

                // Create location data
                Map<String, Object> location = new HashMap<>();
                location.put("userId", auth.getCurrentUser().getUid());
                location.put("name", name);
                location.put("type", type);
                location.put("latitude", mapCenter.getLatitude());
                location.put("longitude", mapCenter.getLongitude());
                location.put("createdAt", com.google.firebase.Timestamp.now());

                Log.d("MapActivity", "Saving location: " + location.toString());

                db.collection("locations")
                    .add(location)
                    .addOnSuccessListener(documentReference -> {
                        Log.d("MapActivity", "Location added with ID: " + documentReference.getId());
                        addMarkerToMap(
                            mapCenter.getLatitude(),
                            mapCenter.getLongitude(),
                            name,
                            type,
                            type.equals("park") ? R.drawable.ic_dog_paw : R.drawable.ic_vet
                        );
                        Toast.makeText(this, "Location added successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("MapActivity", "Error adding location: " + e.getMessage(), e);
                        Toast.makeText(this, 
                            "Error adding location: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void addUserMarker(GeoPoint userLocation) {
        // Add user marker
        Marker userMarker = new Marker(mapView);
        userMarker.setPosition(userLocation);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        userMarker.setIcon(getResources().getDrawable(R.drawable.ic_user_location));
        userMarker.setTitle("You are here");
        mapView.getOverlays().add(userMarker);
    }

    private void addMarkerToMap(double lat, double lon, String name, String type, int iconRes) {
        // Add marker to map
        GeoPoint location = new GeoPoint(lat, lon);
        Marker marker = new Marker(mapView);
        marker.setPosition(location);
        marker.setTitle(name);
        marker.setSnippet("Tap for directions");
        marker.setIcon(getResources().getDrawable(iconRes));
        
        // Use the final coordinates array in the lambda
        marker.setOnMarkerClickListener((marker1, mapView) -> {
            openGoogleMapsNavigation(lat, lon);
            return true;
        });
        
        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }
}
