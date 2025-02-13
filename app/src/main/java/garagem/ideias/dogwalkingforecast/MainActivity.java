package garagem.ideias.dogwalkingforecast;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import garagem.ideias.dogwalkingforecast.adapter.ForecastAdapter;
import garagem.ideias.dogwalkingforecast.api.WeatherService;
import garagem.ideias.dogwalkingforecast.auth.LoginActivity;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import garagem.ideias.dogwalkingforecast.feature.MapActivity;
import garagem.ideias.dogwalkingforecast.view.CircularScoreView;
import garagem.ideias.dogwalkingforecast.feature.UserLocationsBottomSheet;

public class MainActivity extends AppCompatActivity {
    private static final String API_KEY = "15083413d0f0d7bcc5b45362c97f8998";
    private static final String BASE_URL = "https://api.openweathermap.org/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    
    private TextView cityNameText;
    private TextView currentTempText;
    private TextView weatherDescriptionText;
    private TextView tempRangeText;
    private TextView weatherDetailsText;
    private LinearLayout hourlyForecastLayout;
    private LinearLayout dailyForecastLayout;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0;
    private double currentLongitude = 0;
    private ProgressBar progressBar;
    private String currentCityName;
    private CircularScoreView scoreView;
    private TextView recommendationText;
    private TextView welcomeMessage;
    private TextView logoutLink;
    private TextView locationMessage;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextView detailedRecommendationText;
    private LinearLayout scoreContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        cityNameText = findViewById(R.id.cityName);
        currentTempText = findViewById(R.id.currentTemp);
        weatherDescriptionText = findViewById(R.id.weatherDescription);
        tempRangeText = findViewById(R.id.tempRange);
        weatherDetailsText = findViewById(R.id.weatherDetails);
        hourlyForecastLayout = findViewById(R.id.hourlyForecast);
        dailyForecastLayout = findViewById(R.id.dailyForecast);
        progressBar = findViewById(R.id.progressBar);
        scoreView = findViewById(R.id.scoreView);
        recommendationText = findViewById(R.id.recommendationText);
        welcomeMessage = findViewById(R.id.welcomeMessage);
        locationMessage = findViewById(R.id.locationMessage);
        logoutLink = findViewById(R.id.logoutLink);
        detailedRecommendationText = findViewById(R.id.detailedRecommendationText);
        scoreContainer = findViewById(R.id.scoreContainer);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkLocationPermission();

        // Get user's name from Firestore and update welcome message
        if (auth.getCurrentUser() != null) {
            db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.isEmpty()) {
                            welcomeMessage.setText(String.format("Hi, %s!\nWelcome to your today's Dog Walking", name));
                        } else {
                            // Fallback to email if name is not found
                            String firstName = getFirstNameFromEmail(auth.getCurrentUser().getEmail());
                            welcomeMessage.setText(String.format("Hi, %s!\nWelcome to your today's Dog Walking", firstName));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback to email on error
                    String firstName = getFirstNameFromEmail(auth.getCurrentUser().getEmail());
                    welcomeMessage.setText(String.format("Hi, %s!\nWelcome to your today's Dog Walking", firstName));
                });
        } else {
            welcomeMessage.setText("Hi, Guest!\nWelcome to your today's Dog Walking");
        }

        // Initialize location message (will be updated when location is available)
        locationMessage.setText("in Unknown Location");

        // Setup logout
        logoutLink.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Setup click listeners for map navigation
        View.OnClickListener mapClickListener = v -> {
            startActivity(new Intent(this, MapActivity.class));
        };

        // Apply click listener to both views
        scoreContainer.setOnClickListener(mapClickListener);
        locationMessage.setOnClickListener(mapClickListener);

    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                showError("Location permission is required for local weather");
            }
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                currentLatitude = location.getLatitude();
                                currentLongitude = location.getLongitude();
                                updateLocationName();
                                fetchWeatherForecast();
                            }
                        }
                    });
        }
    }

    private void updateLocationName() {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                currentCityName = address.getLocality();
                cityNameText.setText(currentCityName);
                // Update location message
                locationMessage.setText(String.format("in %s", currentCityName));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCityName() {
        return currentCityName != null ? currentCityName : "Unknown Location";
    }

    private void fetchWeatherForecast() {
        progressBar.setVisibility(View.VISIBLE);
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherService service = retrofit.create(WeatherService.class);
        
        service.getWeatherForecast(
            currentLatitude, 
            currentLongitude, 
            "metric", 
            API_KEY,
            "minutely,hourly"
        ).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().list != null) {
                    updateUI(response.body());
                } else {
                    try {
                        showError("Error: " + response.errorBody().string());
                    } catch (Exception e) {
                        showError("Error fetching forecast");
                    }
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private List<WeatherResponse.ForecastItem> filterDailyForecasts(List<WeatherResponse.ForecastItem> allForecasts) {
        Map<String, WeatherResponse.ForecastItem> dailyMap = new HashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        // Get current date for comparison
        String currentDate = dateFormat.format(new Date());

        for (WeatherResponse.ForecastItem forecast : allForecasts) {
            String date = dateFormat.format(new Date(forecast.dt * 1000));
            
            // If we haven't stored a forecast for this day yet, or if this forecast is closer to noon
            if (!dailyMap.containsKey(date) || isCloserToNoon(dailyMap.get(date), forecast)) {
                dailyMap.put(date, forecast);
            }
        }

        // Convert to list and sort
        List<WeatherResponse.ForecastItem> sortedForecasts = new ArrayList<>(dailyMap.values());
        Collections.sort(sortedForecasts, (a, b) -> {
            // Compare dates
            Date dateA = new Date(a.dt * 1000);
            Date dateB = new Date(b.dt * 1000);
            return dateA.compareTo(dateB);
        });

        return sortedForecasts;
    }

    private boolean isCloserToNoon(WeatherResponse.ForecastItem existing, WeatherResponse.ForecastItem newForecast) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH", Locale.getDefault());
        int existingHour = Integer.parseInt(timeFormat.format(new Date(existing.dt * 1000)));
        int newHour = Integer.parseInt(timeFormat.format(new Date(newForecast.dt * 1000)));

        int existingDiff = Math.abs(existingHour - 12);
        int newDiff = Math.abs(newHour - 12);

        return newDiff < existingDiff;
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateUI(WeatherResponse response) {
        if (response == null || response.list == null || response.list.isEmpty()) return;

        WeatherResponse.ForecastItem current = response.list.get(0);
        
        // Update current weather
        cityNameText.setText(getCityName());
        currentTempText.setText(String.format(Locale.getDefault(), "%.0f°C", current.main.temp));
        weatherDescriptionText.setText(current.weather.get(0).description);
        
        // Update temperature range
        tempRangeText.setText(String.format(Locale.getDefault(), 
            "H:%.0f°C L:%.0f°C", current.main.temp_max, current.main.temp_min));
        
        // Update weather details
        String details = String.format(Locale.getDefault(),
            "%s. Wind gusts are up to %.0f km/h.",
            current.weather.get(0).description,
            current.wind.speed * 3.6); // Convert m/s to km/h
        weatherDetailsText.setText(details);

        // Update hourly forecast
        updateHourlyForecast(response.list);

        // Update daily forecast
        updateDailyForecast(response.list);

        // Calculate and update walking score
        int walkingScore = calculateWalkingScore(current);
        scoreView.setScore(walkingScore);
        
        // Set recommendations
        String recommendation = generateWalkingRecommendation(walkingScore);
        String detailedRecommendation = generateDetailedRecommendation(walkingScore);
        recommendationText.setText(recommendation);
        detailedRecommendationText.setText(detailedRecommendation);
    }

    private void updateHourlyForecast(List<WeatherResponse.ForecastItem> forecasts) {
        hourlyForecastLayout.removeAllViews();
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        
        for (int i = 0; i < Math.min(forecasts.size(), 24); i++) {
            WeatherResponse.ForecastItem forecast = forecasts.get(i);
            
            View hourlyView = getLayoutInflater().inflate(
                R.layout.item_hourly_forecast, hourlyForecastLayout, false);
            
            ((TextView) hourlyView.findViewById(R.id.timeText)).setText(
                timeFormat.format(new Date(forecast.dt * 1000)));
            ((TextView) hourlyView.findViewById(R.id.tempText)).setText(
                String.format(Locale.getDefault(), "%.0f°C", forecast.main.temp));
            
            hourlyForecastLayout.addView(hourlyView);
        }
    }

    private void updateDailyForecast(List<WeatherResponse.ForecastItem> forecasts) {
        dailyForecastLayout.removeAllViews();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        Map<String, WeatherResponse.ForecastItem> dailyMap = new HashMap<>();
        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        // Group forecasts by day
        for (WeatherResponse.ForecastItem forecast : forecasts) {
            String dayKey = dayKeyFormat.format(new Date(forecast.dt * 1000));
            // Keep the forecast closest to noon for each day
            if (!dailyMap.containsKey(dayKey) || isCloserToNoon(dailyMap.get(dayKey), forecast)) {
                dailyMap.put(dayKey, forecast);
            }
        }
        
        // Convert to list and sort by date
        List<Map.Entry<String, WeatherResponse.ForecastItem>> sortedEntries = new ArrayList<>(dailyMap.entrySet());
        Collections.sort(sortedEntries, (a, b) -> {
            Date dateA = new Date(a.getValue().dt * 1000);
            Date dateB = new Date(b.getValue().dt * 1000);
            return dateA.compareTo(dateB);
        });
        
        // Add daily views
        for (Map.Entry<String, WeatherResponse.ForecastItem> entry : sortedEntries) {
            View dailyView = getLayoutInflater().inflate(
                R.layout.item_daily_forecast, dailyForecastLayout, false);
            
            WeatherResponse.ForecastItem forecast = entry.getValue();
            String dayName = dateFormat.format(new Date(forecast.dt * 1000));
            
            // Show "Today" for the first day
            if (entry.equals(sortedEntries.get(0))) {
                dayName = "Today";
            }
            
            ((TextView) dailyView.findViewById(R.id.dayText)).setText(dayName);
            ((TextView) dailyView.findViewById(R.id.tempText)).setText(
                String.format(Locale.getDefault(), "%.0f°C", forecast.main.temp));
            
            dailyForecastLayout.addView(dailyView);
            
            // Add a divider except for the last item
            if (!entry.equals(sortedEntries.get(sortedEntries.size() - 1))) {
                View divider = new View(this);
                divider.setBackgroundColor(getResources().getColor(android.R.color.white));
                divider.setAlpha(0.1f);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(0, 8, 0, 8);
                divider.setLayoutParams(params);
                dailyForecastLayout.addView(divider);
            }
        }
    }

    private int calculateWalkingScore(WeatherResponse.ForecastItem forecast) {
        int tempScore = calculateTemperatureScore(forecast.main.temp);
        int rainScore = calculateRainScore(forecast.pop);
        int windScore = calculateWindScore(forecast.wind.speed);

        // Weight the scores (temperature being most important, then rain, then wind)
        double weightedScore = (tempScore * 0.5) + (rainScore * 0.3) + (windScore * 0.2);
        return (int) Math.round(weightedScore);
    }

    private int calculateTemperatureScore(double temp) {
        if (temp < -10) return 0;  // Too cold
        if (temp > 35) return 0;   // Too hot

        if (temp >= 10 && temp <= 20) {
            return 100;  // Perfect temperature range
        }

        if (temp < 10) {
            return (int) (100 * (1 - (10 - temp) / 20));  // Linear decrease from 10°C to -10°C
        } else {
            return (int) (100 * (1 - (temp - 20) / 15));  // Linear decrease from 20°C to 35°C
        }
    }

    private int calculateRainScore(double rainProbability) {
        return (int) (100 * (1 - rainProbability));
    }

    private int calculateWindScore(double windSpeed) {
        if (windSpeed < 1) return 100;    // Perfect conditions
        if (windSpeed > 15) return 0;     // Too windy
        return (int) (100 * (1 - (windSpeed - 1) / 14));
    }

    private String generateWalkingRecommendation(int score) {
        if (score >= 90) return "IDEAL CONDITIONS";
        if (score >= 70) return "FAVORABLE CONDITIONS";
        if (score >= 50) return "MODERATE CONDITIONS";
        if (score >= 30) return "EXERCISE CAUTION";
        return "NOT RECOMMENDED";
    }

    private String generateDetailedRecommendation(int score) {
        if (score >= 90) return "Perfect day for a long dog walking!";
        if (score >= 70) return "Great conditions for a dog walking";
        if (score >= 50) return "Decent day for a short dog walking";
        if (score >= 30) return "Keep the walk brief and stay alert";
        return "Better to stay inside today";
    }

    private String getFirstNameFromEmail(String email) {
        if (email == null || email.isEmpty()) return "Guest";
        
        // Remove everything after @ to get username
        String username = email.split("@")[0];
        
        // Split by common separators and get first part
        String[] parts = username.split("[._-]");
        
        // Capitalize first letter
        if (parts.length > 0 && !parts[0].isEmpty()) {
            String firstName = parts[0];
            return firstName.substring(0, 1).toUpperCase() + firstName.substring(1).toLowerCase();
        }
        
        return "Guest";
    }
} 