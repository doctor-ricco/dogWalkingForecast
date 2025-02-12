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
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.MapEventsOverlay;

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
import android.widget.ImageView;
import com.google.android.material.snackbar.Snackbar;
import android.widget.TextView;

public class MapActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String userId;
    private MapView mapView;
    private FusedLocationProviderClient locationClient;
    private ImageView mapTargetIndicator;
    private boolean isSelectingLocation = false;

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

        mapTargetIndicator = findViewById(R.id.mapTargetIndicator);
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
                    showMarkerOptionsDialog(location, name, null);
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

    private void showMarkerOptionsDialog(GeoPoint point, String name, String documentId) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_marker_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .create();

        // Set location name
        TextView locationName = dialogView.findViewById(R.id.locationName);
        locationName.setText(name);

        // Setup cancel button
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        // Setup navigate button
        dialogView.findViewById(R.id.btnNavigate).setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED) {
                
                locationClient.getLastLocation().addOnSuccessListener(currentLocation -> {
                    if (currentLocation != null) {
                        Uri gmmIntentUri = Uri.parse("https://www.google.com/maps/dir/?api=1" +
                            "&origin=" + currentLocation.getLatitude() + "," + currentLocation.getLongitude() +
                            "&destination=" + point.getLatitude() + "," + point.getLongitude() +
                            "&travelmode=walking");
                        
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        mapIntent.setPackage("com.google.android.apps.maps");
                        
                        if (mapIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(mapIntent);
                        } else {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                            startActivity(browserIntent);
                        }
                        dialog.dismiss();
                    }
                });
            }
        });

        // Setup delete button
        dialogView.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteConfirmationDialog(documentId, name);
        });

        // Make dialog background transparent to show rounded corners
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private void showDeleteConfirmationDialog(String documentId, String name) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_confirmation, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .create();

        // Set delete message
        TextView deleteMessage = dialogView.findViewById(R.id.deleteMessage);
        deleteMessage.setText("Are you sure you want to delete " + name + "?");

        // Setup cancel button
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        // Setup confirm delete button
        dialogView.findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> {
            db.collection("locations")
                .document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Location deleted successfully", Toast.LENGTH_SHORT).show();
                    mapView.getOverlays().clear();
                    getCurrentLocation();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error deleting location: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
        });

        dialog.show();
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
                            type.equals("park") ? R.drawable.ic_dog_paw : R.drawable.ic_vet,
                            doc.getId()  // Pass the document ID
                        );
                    }
                }
            });
    }

    private void showAddLocationDialog() {
        mapTargetIndicator.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Move the map to position the target on your desired location, then tap to confirm", Toast.LENGTH_LONG).show();
        
        // Add a map events overlay to handle taps
        MapEventsReceiver mapEventsReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                GeoPoint selectedLocation = (GeoPoint) mapView.getMapCenter();
                showLocationDetailsDialog(selectedLocation);
                mapTargetIndicator.setVisibility(View.GONE);
                mapView.getOverlays().remove(this);  // Remove this overlay after selection
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(mapEventsReceiver);
        mapView.getOverlays().add(eventsOverlay);
        mapView.invalidate();
    }

    private void showLocationDetailsDialog(GeoPoint location) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_location, null);
        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        RadioGroup typeGroup = dialogView.findViewById(R.id.typeGroup);

        new AlertDialog.Builder(this)
            .setTitle("Add Location")
            .setView(dialogView)
            .setPositiveButton("Add", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Get the selected type (make it final)
                final String type = typeGroup.getCheckedRadioButtonId() == R.id.radioVet ? "vet" : "park";

                // Create location data
                Map<String, Object> locationData = new HashMap<>();
                locationData.put("userId", userId);
                locationData.put("name", name);
                locationData.put("type", type);
                locationData.put("latitude", location.getLatitude());
                locationData.put("longitude", location.getLongitude());

                // Save to Firestore
                db.collection("locations")
                    .add(locationData)
                    .addOnSuccessListener(documentReference -> {
                        addMarkerToMap(
                            location.getLatitude(),
                            location.getLongitude(),
                            name,
                            type,
                            type.equals("park") ? R.drawable.ic_dog_paw : R.drawable.ic_vet,
                            documentReference.getId()  // Pass the new document ID
                        );
                        Toast.makeText(this, "Location saved successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error saving location: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    });

                mapView.setOnClickListener(null); // Remove the click listener
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                mapView.setOnClickListener(null); // Remove the click listener
                mapTargetIndicator.setVisibility(View.GONE); // Hide the target
            })
            .show();
    }

    private void addUserMarker(GeoPoint userLocation) {
        Marker userMarker = new Marker(mapView);
        userMarker.setPosition(userLocation);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);  // Center the marker
        userMarker.setIcon(getResources().getDrawable(R.drawable.ic_user_location));
        userMarker.setTitle("You are here");
        mapView.getOverlays().add(userMarker);
    }

    private void addMarkerToMap(double latitude, double longitude, String name, 
        String type, int iconResource, String documentId) {
        
        GeoPoint point = new GeoPoint(latitude, longitude);
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(getResources().getDrawable(iconResource));
        marker.setTitle(name);
        
        // Add click listener to marker for options
        marker.setOnMarkerClickListener((marker1, mapView) -> {
            showMarkerOptionsDialog(point, name, documentId);
            return true;
        });
        
        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }
}
