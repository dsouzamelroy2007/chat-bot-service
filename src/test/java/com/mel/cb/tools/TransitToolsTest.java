package com.mel.cb.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class TransitToolsTest {

  @RegisterExtension
  static WireMockExtension geocodingServer = WireMockExtension.newInstance().build();

  @RegisterExtension
  static WireMockExtension directionsServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    geocodingServer.resetAll();
    directionsServer.resetAll();
  }

  private TransitTools transitTools(String apiKey) {
    return new TransitTools(
        new GeocodingClient(RestClient.builder().baseUrl(geocodingServer.baseUrl()).build()),
        RestClient.builder().baseUrl(directionsServer.baseUrl()).build(),
        apiKey, "OPENROUTESERVICE_API_KEY");
  }

  private void stubGeocode(String name, double lat, double lon) {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .withQueryParam("name", com.github.tomakehurst.wiremock.client.WireMock.equalTo(name))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"results":[{"latitude":%f,"longitude":%f,"name":"%s","country":"Testland","timezone":"UTC"}]}
            """.formatted(lat, lon, name))));
  }

  @Test
  void returnsFormattedDirectionsForDrivingByDefault() {
    stubGeocode("Origin", 49.41461, 8.681495);
    stubGeocode("Destination", 49.420318, 8.687872);
    directionsServer.stubFor(get(urlPathMatching("/v2/directions/driving-car"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"features":[{"properties":{"summary":{"distance":1234.5,"duration":300.0}}}]}
            """)));

    String result = transitTools("test-key").getDirections("Origin", "Destination", null);

    assertTrue(result.contains("Origin"));
    assertTrue(result.contains("Destination"));
    assertTrue(result.contains("car"));
    assertTrue(result.contains("1.2 km"));
    assertTrue(result.contains("5 minutes"));
  }

  @Test
  void mapsWalkingModeToFootWalkingProfile() {
    stubGeocode("A", 0.0, 0.0);
    stubGeocode("B", 1.0, 1.0);
    directionsServer.stubFor(get(urlPathMatching("/v2/directions/foot-walking"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"features":[{"properties":{"summary":{"distance":500.0,"duration":600.0}}}]}
            """)));

    String result = transitTools("test-key").getDirections("A", "B", "walking");

    assertTrue(result.contains("walking"));
  }

  @Test
  void disabledWithoutApiKey() {
    assertFalse(transitTools(null).isEnabled());
    assertFalse(transitTools("").isEnabled());
  }

  @Test
  void enabledWithApiKey() {
    assertTrue(transitTools("a-real-key").isEnabled());
  }

  @Test
  void returnsFriendlyMessageWhenOriginNotFound() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));

    String result = transitTools("test-key").getDirections("Nowhere", "Somewhere", null);

    assertEquals("Could not find a location matching \"Nowhere\".", result);
  }

}
