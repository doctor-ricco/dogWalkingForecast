package garagem.ideias.dogwalkingforecast.model;

import java.util.List;

public class WeatherResponse {
    public List<ForecastItem> list;
    public City city;

    public static class City {
        public long sunrise;
        public long sunset;
    }

    public static class ForecastItem {
        public long dt;
        public Main main;
        public List<Weather> weather;
        public Clouds clouds;
        public Wind wind;
        public double pop;
        public String dt_txt;
        public double uvi;
        public AirQuality air_quality;
    }

    public static class Main {
        public double temp;
        public double feels_like;
        public double temp_min;
        public double temp_max;
        public int pressure;
        public int humidity;
        public double grnd_temp;
    }

    public static class Weather {
        public int id;
        public String main;
        public String description;
        public String icon;
    }

    public static class Clouds {
        public int all;
    }

    public static class Wind {
        public double speed;
        public double deg;
        public double gust;
    }

    public static class AirQuality {
        public double aqi;
    }
} 