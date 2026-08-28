package com.mel.cb.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mel.cb.tools.GeocodingClient.GeocodeResult;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

/**
 * Approximate travel distance/duration between two place names via OpenRouteService's free-tier
 * Directions API. Deliberately named "transit directions" per the brief, but honestly scoped: ORS's
 * free tier has no real public-transit (bus/train schedule) routing -- only driving/cycling/walking
 * profiles -- so that's what this offers, best-effort, not live timetables. Optional: off (its
 * {@link #isEnabled()} returns false) unless an API key is configured, same convention as
 * {@code com.mel.cb.provider.AnthropicChatProvider}.
 */
@Slf4j
public class TransitTools implements ChatTool {

  private static final Set<String> VALID_PROFILES = Set.of("driving-car", "cycling-regular", "foot-walking");

  private final GeocodingClient geocodingClient;
  private final RestClient restClient;
  private final String apiKey;
  private final AtomicBoolean enabled = new AtomicBoolean(true);

  public TransitTools(GeocodingClient geocodingClient, RestClient restClient, String apiKey, String apiKeyEnv) {
    this.geocodingClient = geocodingClient;
    this.restClient = restClient;
    this.apiKey = apiKey != null ? apiKey : "";
    if (apiKey == null || apiKey.isBlank()) {
      enabled.set(false);
      log.warn("Transit directions tool disabled: no API key configured (env var {} not set)", apiKeyEnv);
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled.get();
  }

  @Tool(description = "Get the approximate travel distance and duration between two place names by "
      + "car, bike, or on foot. Not real-time public transit schedules.")
  public String getDirections(
      @ToolParam(description = "Starting place name") String origin,
      @ToolParam(description = "Destination place name") String destination,
      @ToolParam(description = "Travel mode: 'driving', 'cycling', or 'walking' (defaults to driving)", required = false) String mode) {
    Optional<GeocodeResult> from = geocodingClient.geocode(origin);
    if (from.isEmpty()) {
      return "Could not find a location matching \"" + origin + "\".";
    }
    Optional<GeocodeResult> to = geocodingClient.geocode(destination);
    if (to.isEmpty()) {
      return "Could not find a location matching \"" + destination + "\".";
    }

    String profile = toProfile(mode);
    GeocodeResult fromPlace = from.get();
    GeocodeResult toPlace = to.get();

    DirectionsResponse response = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/v2/directions/" + profile)
            .queryParam("api_key", apiKey)
            .queryParam("start", fromPlace.longitude() + "," + fromPlace.latitude())
            .queryParam("end", toPlace.longitude() + "," + toPlace.latitude())
            .build())
        .retrieve()
        .body(DirectionsResponse.class);
    if (response == null || response.features() == null || response.features().isEmpty()) {
      return "No route found between " + fromPlace.name() + " and " + toPlace.name() + ".";
    }

    Summary summary = response.features().get(0).properties().summary();
    double distanceKm = summary.distance() / 1000.0;
    double durationMin = summary.duration() / 60.0;
    return "From %s to %s by %s: approximately %.1f km, %.0f minutes.".formatted(
        fromPlace.name(), toPlace.name(), mode == null || mode.isBlank() ? "car" : mode.toLowerCase(Locale.ROOT),
        distanceKm, durationMin);
  }

  private static String toProfile(String mode) {
    if (mode == null || mode.isBlank()) {
      return "driving-car";
    }
    String profile = switch (mode.toLowerCase(Locale.ROOT)) {
      case "cycling", "bike", "bicycle" -> "cycling-regular";
      case "walking", "foot", "walk" -> "foot-walking";
      default -> "driving-car";
    };
    return VALID_PROFILES.contains(profile) ? profile : "driving-car";
  }

  private record DirectionsResponse(@JsonProperty("features") List<Feature> features) {
  }

  private record Feature(@JsonProperty("properties") Properties properties) {
  }

  private record Properties(@JsonProperty("summary") Summary summary) {
  }

  private record Summary(@JsonProperty("distance") double distance, @JsonProperty("duration") double duration) {
  }

}
