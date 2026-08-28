package com.mel.cb.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Resolves a free-text place name to coordinates (+ IANA timezone) via Open-Meteo's free, keyless
 * geocoding API -- shared by {@link WeatherTools}, {@link TimeTools}, and {@link TransitTools} so
 * "which city did they mean" is answered the same way everywhere. Response schema confirmed against
 * the live API on 2026-08-28 (a live request against geocoding-api.open-meteo.com), not assumed.
 */
public class GeocodingClient {

  private final RestClient restClient;

  public GeocodingClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public Optional<GeocodeResult> geocode(String location) {
    GeocodingResponse response = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/v1/search")
            .queryParam("name", location)
            .queryParam("count", 1)
            .queryParam("language", "en")
            .queryParam("format", "json")
            .build())
        .retrieve()
        .body(GeocodingResponse.class);
    if (response == null || response.results() == null || response.results().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(response.results().get(0));
  }

  public record GeocodeResult(
      @JsonProperty("latitude") double latitude,
      @JsonProperty("longitude") double longitude,
      @JsonProperty("name") String name,
      @JsonProperty("country") String country,
      @JsonProperty("timezone") String timezone) {
  }

  private record GeocodingResponse(@JsonProperty("results") List<GeocodeResult> results) {
  }

}
