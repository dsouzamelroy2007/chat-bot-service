package com.mel.cb.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mel.cb.tools.GeocodingClient.GeocodeResult;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

/**
 * Current conditions via Open-Meteo's free, keyless forecast API -- always enabled, no API key to
 * configure. Response schema (the {@code current.*} fields) confirmed against the live API on
 * 2026-08-28, not assumed.
 */
public class WeatherTools implements ChatTool {

  private static final Map<Integer, String> WEATHER_CODE_DESCRIPTIONS = Map.ofEntries(
      Map.entry(0, "clear sky"), Map.entry(1, "mainly clear"), Map.entry(2, "partly cloudy"),
      Map.entry(3, "overcast"), Map.entry(45, "fog"), Map.entry(48, "depositing rime fog"),
      Map.entry(51, "light drizzle"), Map.entry(53, "moderate drizzle"), Map.entry(55, "dense drizzle"),
      Map.entry(56, "light freezing drizzle"), Map.entry(57, "dense freezing drizzle"),
      Map.entry(61, "slight rain"), Map.entry(63, "moderate rain"), Map.entry(65, "heavy rain"),
      Map.entry(66, "light freezing rain"), Map.entry(67, "heavy freezing rain"),
      Map.entry(71, "slight snow fall"), Map.entry(73, "moderate snow fall"), Map.entry(75, "heavy snow fall"),
      Map.entry(77, "snow grains"), Map.entry(80, "slight rain showers"), Map.entry(81, "moderate rain showers"),
      Map.entry(82, "violent rain showers"), Map.entry(85, "slight snow showers"), Map.entry(86, "heavy snow showers"),
      Map.entry(95, "thunderstorm"), Map.entry(96, "thunderstorm with slight hail"),
      Map.entry(99, "thunderstorm with heavy hail"));

  private final GeocodingClient geocodingClient;
  private final RestClient restClient;

  public WeatherTools(GeocodingClient geocodingClient, RestClient restClient) {
    this.geocodingClient = geocodingClient;
    this.restClient = restClient;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Tool(description = "Get the current weather conditions for a place name (city, region, or landmark).")
  public String getCurrentWeather(
      @ToolParam(description = "Place name, e.g. 'Paris' or 'Paris, France'") String location) {
    Optional<GeocodeResult> place = geocodingClient.geocode(location);
    if (place.isEmpty()) {
      return "Could not find a location matching \"" + location + "\".";
    }
    GeocodeResult g = place.get();

    ForecastResponse forecast = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
            .queryParam("latitude", g.latitude())
            .queryParam("longitude", g.longitude())
            .queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m")
            .queryParam("timezone", "auto")
            .build())
        .retrieve()
        .body(ForecastResponse.class);
    if (forecast == null || forecast.current() == null) {
      return "Weather data is currently unavailable for " + g.name() + ".";
    }

    CurrentWeather c = forecast.current();
    return "Current weather in %s, %s: %s, %.1f°C (feels like %.1f°C), %d%% humidity, wind %.1f km/h."
        .formatted(g.name(), g.country(), describeWeatherCode(c.weatherCode()), c.temperature(),
            c.apparentTemperature(), c.humidity(), c.windSpeed());
  }

  private static String describeWeatherCode(int code) {
    return WEATHER_CODE_DESCRIPTIONS.getOrDefault(code, "unknown conditions (code " + code + ")");
  }

  private record CurrentWeather(
      @JsonProperty("temperature_2m") double temperature,
      @JsonProperty("apparent_temperature") double apparentTemperature,
      @JsonProperty("relative_humidity_2m") int humidity,
      @JsonProperty("weather_code") int weatherCode,
      @JsonProperty("wind_speed_10m") double windSpeed) {
  }

  private record ForecastResponse(@JsonProperty("current") CurrentWeather current) {
  }

}
