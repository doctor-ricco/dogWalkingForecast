package garagem.ideias.dogwalkingforecast;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import garagem.ideias.dogwalkingforecast.adapter.ForecastAdapter;
import garagem.ideias.dogwalkingforecast.api.WeatherService;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {
    // Replace with your actual OpenWeatherMap API key
    private static final String API_KEY = "15083413d0f0d7bcc5b45362c97f8998";
    private static final String BASE_URL = "https://api.openweathermap.org/";
    private static final double LATITUDE = -23.5505;
    private static final double LONGITUDE = -46.6333;
    private static final String LOCATION_NAME = "São Paulo, Brazil";
    
    private RecyclerView recyclerView;
    private ForecastAdapter adapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setSubtitle(LOCATION_NAME);
        }

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ForecastAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        fetchWeatherForecast();
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
        
        service.getWeatherForecast(LATITUDE, LONGITUDE, "metric", API_KEY)
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