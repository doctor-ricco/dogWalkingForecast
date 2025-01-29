package garagem.ideias.dogwalkingforecast.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import garagem.ideias.dogwalkingforecast.R;
import garagem.ideias.dogwalkingforecast.model.WeatherResponse.ForecastItem;
import garagem.ideias.dogwalkingforecast.view.CircularScoreView;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder> {
    private List<ForecastItem> forecasts;

    public ForecastAdapter(List<ForecastItem> forecasts) {
        this.forecasts = forecasts;
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
        
        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        String date = sdf.format(new Date(forecast.dt * 1000));
        
        // Calculate walking score
        int walkingScore = calculateWalkingScore(forecast);
        
        // Format temperature
        String temperature = String.format(Locale.getDefault(), 
            "Temperature: %.1f°C (Min: %.1f°C, Max: %.1f°C)",
            forecast.main.temp, forecast.main.temp_min, forecast.main.temp_max);
        
        // Get weather description
        String description = String.format("Weather: %s\nWind: %.1f m/s\nRain chance: %.0f%%",
            forecast.weather.get(0).description,
            forecast.wind.speed,
            forecast.pop * 100);
        
        // Update recommendation text style
        holder.recommendationText.setTextSize(16f);
        holder.recommendationText.setAlpha(0.9f);
        holder.recommendationText.setPadding(24, 16, 24, 24);
        
        String recommendation = generateWalkingRecommendation(walkingScore);

        // Set all the text views
        holder.dateText.setText(date);
        holder.temperatureText.setText(temperature);
        holder.descriptionText.setText(description);
        holder.recommendationText.setText(recommendation);
        holder.scoreView.setScore(walkingScore);
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
            return "Perfect weather for a walk! 🐾";
        } else if (score >= 70) {
            return "Great conditions for walking! 🐕";
        } else if (score >= 50) {
            return "Decent weather for a short walk 🦮";
        } else if (score >= 30) {
            return "Consider a quick walk only 🌦️";
        } else {
            return "Better stay indoors today 🏠";
        }
    }

    @Override
    public int getItemCount() {
        return forecasts.size();
    }

    public void updateForecasts(List<ForecastItem> newForecasts) {
        this.forecasts = newForecasts;
        notifyDataSetChanged();
    }

    static class ForecastViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView temperatureText;
        TextView descriptionText;
        TextView recommendationText;
        CircularScoreView scoreView;

        ForecastViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            temperatureText = itemView.findViewById(R.id.temperatureText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            recommendationText = itemView.findViewById(R.id.recommendationText);
            scoreView = itemView.findViewById(R.id.scoreView);
        }
    }
} 