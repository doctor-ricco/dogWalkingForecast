package garagem.ideias.dogwalkingforecast.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import garagem.ideias.dogwalkingforecast.R;
import garagem.ideias.dogwalkingforecast.feature.MapActivity;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse.ForecastItem;
import garagem.ideias.dogwalkingforecast.view.CircularScoreView;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder> {
    private List<ForecastItem> forecasts;
    private WeatherResponse.City city;

    public ForecastAdapter(List<ForecastItem> forecasts) {
        this.forecasts = forecasts;
    }

    public void updateData(List<ForecastItem> newForecasts, WeatherResponse.City city) {
        this.forecasts = newForecasts;
        this.city = city;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_forecast, parent, false);
        return new ForecastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
        ForecastItem forecast = forecasts.get(position);

        // Format date with day of week and date (on same line)
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        String date = sdf.format(new Date(forecast.dt * 1000));
        holder.dateText.setText(date);

        // Calculate walking score
        int walkingScore = calculateWalkingScore(forecast);
        holder.scoreView.setScore(walkingScore);
        
        // Set recommendation
        String recommendation = generateWalkingRecommendation(walkingScore);
        holder.recommendationText.setText(recommendation);

        // Format temperature and weather description on the same line
        String temperature = String.format(Locale.getDefault(), 
            "Temperature: %.1f°C     %s",
            forecast.main.temp,
            forecast.weather.get(0).description);
        holder.temperatureText.setText(temperature);

        // Rain chance and wind on second line
        String description = String.format("Rain chance: %.0f%%     Wind speed: %.1f m/s",
            forecast.pop * 100,
            forecast.wind.speed);
        holder.descriptionText.setText(description);

        // Format UV Index
        try {
            String uvIndex = String.format("UV Index: %.1f  %s", 
                forecast.uvi,
                getUVIndexWarning(forecast.uvi));
            holder.uvIndexText.setText(uvIndex);
        } catch (Exception e) {
            holder.uvIndexText.setVisibility(View.GONE);
        }

        // Format Air Quality
        try {
            if (forecast.air_quality != null) {
                String airQuality = String.format("Air Quality: %s", 
                    getAirQualityDescription(forecast.air_quality.aqi));
                holder.airQualityText.setText(airQuality);
            } else {
                holder.airQualityText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            holder.airQualityText.setVisibility(View.GONE);
        }

        // Format Ground Temperature
        try {
            double estimatedGroundTemp = estimateGroundTemperature(forecast);
            String groundTemp = String.format("Ground Temperature: %.1f°C %s",
                estimatedGroundTemp,
                getPawSafetyWarning(estimatedGroundTemp));
            holder.groundTempText.setText(groundTemp);
            holder.groundTempText.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            holder.groundTempText.setVisibility(View.GONE);
        }

        // Set click listener for the card view
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), MapActivity.class);
            view.getContext().startActivity(intent);
        });
    }

    private int calculateWalkingScore(ForecastItem forecast) {
        int tempScore = calculateTemperatureScore(forecast.main.temp);
        int rainScore = calculateRainScore(forecast.pop);
        int windScore = calculateWindScore(forecast.wind.speed);

        // Weight the scores (temperature being most important, then rain, then wind)
        double weightedScore = (tempScore * 0.5) + (rainScore * 0.3) + (windScore * 0.2);
        return (int) Math.round(weightedScore);
    }

    private int calculateTemperatureScore(double temp) {
        // Ideal temperature range for dogs: 10°C to 20°C
        if (temp < -10) return 0;  // Too cold
        if (temp > 35) return 0;   // Too hot

        if (temp >= 10 && temp <= 20) {
            return 100;  // Perfect temperature range
        }

        // Score decreases as temperature moves away from ideal range
        if (temp < 10) {
            return (int) (100 * (1 - (10 - temp) / 20));  // Linear decrease from 10°C to -10°C
        } else {
            return (int) (100 * (1 - (temp - 20) / 15));  // Linear decrease from 20°C to 35°C
        }
    }

    private int calculateRainScore(double rainProbability) {
        // Convert probability (0-1) to score (100-0)
        return (int) (100 * (1 - rainProbability));
    }

    private int calculateWindScore(double windSpeed) {
        // Wind speed in m/s
        if (windSpeed < 1) return 100;    // Perfect conditions
        if (windSpeed > 15) return 0;     // Too windy

        // Linear decrease from 1 m/s to 15 m/s
        return (int) (100 * (1 - (windSpeed - 1) / 14));
    }

    private String generateWalkingRecommendation(int score) {
        if (score >= 90) {
            return "IDEAL CONDITIONS";
        } else if (score >= 70) {
            return "FAVORABLE CONDITIONS";
        } else if (score >= 50) {
            return "MODERATE CONDITIONS";
        } else if (score >= 30) {
            return "EXERCISE CAUTION";
        } else {
            return "NOT RECOMMENDED";
        }
    }

    private String getUVIndexWarning(double uvi) {
        if (uvi >= 11) return "Extreme";
        if (uvi >= 8) return "Very High";
        if (uvi >= 6) return "High";
        if (uvi >= 3) return "Moderate";
        return "Low";
    }

    private String getAirQualityDescription(double aqi) {
        if (aqi >= 300) return "Hazardous";
        if (aqi >= 200) return "Very Unhealthy";
        if (aqi >= 150) return "Unhealthy";
        if (aqi >= 100) return "Moderate";
        if (aqi >= 50) return "Fair";
        return "Good";
    }

    private String getPawSafetyWarning(double temp) {
        if (temp >= 52) return "Critical - Avoid Walking";
        if (temp >= 45) return "Very Hot - Not Safe";
        if (temp >= 35) return "Caution Required";
        if (temp <= 0) return "Too Cold";
        return "Safe";
    }

    private double estimateGroundTemperature(ForecastItem forecast) {
        double airTemp = forecast.main.temp;
        double solarFactor = calculateSolarFactor(forecast);
        
        // Ground temperature is typically warmer than air temperature during the day
        // and slightly cooler at night
        return airTemp + (solarFactor * 15); // Can be up to 15°C warmer than air temp
    }

    private double calculateSolarFactor(ForecastItem forecast) {
        // Get hour of the day (0-23)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(forecast.dt * 1000);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        
        // Base solar factor on time of day (0.0 to 1.0)
        double timeFactor = 0.0;
        if (hour >= 6 && hour <= 18) { // Daytime
            // Peak at noon (hour 12)
            timeFactor = 1.0 - Math.abs(12 - hour) / 6.0;
        }
        
        // Adjust for cloud cover (0.0 to 1.0)
        double cloudFactor = 1.0 - (forecast.clouds.all / 100.0);
        
        // Adjust for weather conditions
        double weatherFactor = 1.0;
        if (forecast.weather != null && !forecast.weather.isEmpty()) {
            String weatherMain = forecast.weather.get(0).main.toLowerCase();
            if (weatherMain.contains("rain") || weatherMain.contains("snow")) {
                weatherFactor = 0.3;
            } else if (weatherMain.contains("cloud")) {
                weatherFactor = 0.7;
            }
        }
        
        return timeFactor * cloudFactor * weatherFactor;
    }

    @Override
    public int getItemCount() {
        return forecasts.size();
    }

    static class ForecastViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView temperatureText;
        TextView descriptionText;
        TextView recommendationText;
        CircularScoreView scoreView;
        TextView uvIndexText;
        TextView airQualityText;
        TextView groundTempText;

        ForecastViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            temperatureText = itemView.findViewById(R.id.temperatureText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            recommendationText = itemView.findViewById(R.id.recommendationText);
            scoreView = itemView.findViewById(R.id.scoreView);
            uvIndexText = itemView.findViewById(R.id.uvIndexText);
            airQualityText = itemView.findViewById(R.id.airQualityText);
            groundTempText = itemView.findViewById(R.id.groundTempText);
        }
    }
}
