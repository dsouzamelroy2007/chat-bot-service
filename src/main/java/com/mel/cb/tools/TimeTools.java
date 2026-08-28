package com.mel.cb.tools;

import com.mel.cb.tools.GeocodingClient.GeocodeResult;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Current local date/time for a place name -- reuses {@link GeocodingClient} for the lookup, since
 * Open-Meteo's geocoding response already includes each result's IANA timezone directly (confirmed
 * against the live API on 2026-08-28), so no second API call or timezone-lookup service is needed.
 * Always enabled, no API key.
 */
public class TimeTools implements ChatTool {

  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy HH:mm");

  private final GeocodingClient geocodingClient;

  public TimeTools(GeocodingClient geocodingClient) {
    this.geocodingClient = geocodingClient;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Tool(description = "Get the current local date and time for a place name (city, region, or landmark).")
  public String getCurrentTime(
      @ToolParam(description = "Place name, e.g. 'Tokyo' or 'Tokyo, Japan'") String location) {
    Optional<GeocodeResult> place = geocodingClient.geocode(location);
    if (place.isEmpty()) {
      return "Could not find a location matching \"" + location + "\".";
    }
    GeocodeResult g = place.get();
    if (g.timezone() == null || g.timezone().isBlank()) {
      return "Could not determine the timezone for " + g.name() + ".";
    }
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of(g.timezone()));
    return "Current time in %s, %s (%s): %s".formatted(g.name(), g.country(), g.timezone(), now.format(FORMAT));
  }

}
