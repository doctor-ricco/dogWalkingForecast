package garagem.ideias.dogwalkingforecast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
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
import garagem.ideias.dogwalkingforecast.adapter.ForecastAdapter;
import garagem.ideias.dogwalkingforecast.api.WeatherService;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // Replace with your actual OpenWeatherMap API key
    private static final String API_KEY = "15083413d0f0d7bcc5b45362c97f8998";
    private static final String BASE_URL = "https://api.openweathermap.org/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    
    private RecyclerView recyclerView;
    private ForecastAdapter adapter;
    private ProgressBar progressBar;
    private MaterialToolbar toolbar;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ForecastAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkLocationPermission();
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
                String cityName = address.getLocality();
                String countryName = address.getCountryName();
                String locationText = cityName + ", " + countryName;
                
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(locationText);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchWeatherForecast() {
        if (API_KEY.equals("YOUR_OPENWEATHERMAP_API_KEY")) {
            showError("Please set your OpenWeatherMap API key in MainActivity.java");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherService service = retrofit.create(WeatherService.class);
        
        service.getWeatherForecast(currentLatitude, currentLongitude, "metric", API_KEY)
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().list != null) {
                            // Get one forecast per day (around noon)
                            List<WeatherResponse.ForecastItem> dailyForecasts = filterDailyForecasts(response.body().list);
                            adapter.updateForecasts(dailyForecasts);
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

        for (WeatherResponse.ForecastItem forecast : allForecasts) {
            String date = dateFormat.format(new Date(forecast.dt * 1000));
            
            // If we haven't stored a forecast for this day yet, or if this forecast is closer to noon
            if (!dailyMap.containsKey(date) || isCloserToNoon(dailyMap.get(date), forecast)) {
                dailyMap.put(date, forecast);
            }
        }

        return new ArrayList<>(dailyMap.values());
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
} 