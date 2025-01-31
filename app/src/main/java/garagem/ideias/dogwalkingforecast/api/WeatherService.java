package garagem.ideias.dogwalkingforecast.api;

import garagem.ideias.dogwalkingforecast.model.WeatherResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherService {
    @GET("data/2.5/forecast")
    Call<WeatherResponse> getWeatherForecast(
        @Query("lat") double lat,
        @Query("lon") double lon,
        @Query("units") String units,
        @Query("appid") String apiKey,
        @Query("exclude") String exclude
    );
} 