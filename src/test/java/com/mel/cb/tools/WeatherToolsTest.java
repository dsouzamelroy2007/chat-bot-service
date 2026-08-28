package com.mel.cb.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class WeatherToolsTest {

  @RegisterExtension
  static WireMockExtension geocodingServer = WireMockExtension.newInstance().build();

  @RegisterExtension
  static WireMockExtension weatherServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    geocodingServer.resetAll();
    weatherServer.resetAll();
  }

  @Test
  void returnsFormattedCurrentWeather() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"results":[{"latitude":52.374,"longitude":4.889,"name":"Amsterdam","country":"The Netherlands","timezone":"Europe/Amsterdam"}]}
            """)));
    weatherServer.stubFor(get(urlPathEqualTo("/v1/forecast"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"current":{"temperature_2m":22.5,"apparent_temperature":21.9,"relative_humidity_2m":64,"weather_code":3,"wind_speed_10m":16.6}}
            """)));

    WeatherTools weatherTools = new WeatherTools(
        new GeocodingClient(RestClient.builder().baseUrl(geocodingServer.baseUrl()).build()),
        RestClient.builder().baseUrl(weatherServer.baseUrl()).build());

    String result = weatherTools.getCurrentWeather("Amsterdam");

    assertTrue(result.contains("Amsterdam"));
    assertTrue(result.contains("The Netherlands"));
    assertTrue(result.contains("overcast"));
    assertTrue(result.contains("22.5"));
    assertTrue(result.contains("64% humidity"));
  }

  @Test
  void returnsFriendlyMessageWhenLocationNotFound() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));

    WeatherTools weatherTools = new WeatherTools(
        new GeocodingClient(RestClient.builder().baseUrl(geocodingServer.baseUrl()).build()),
        RestClient.builder().baseUrl(weatherServer.baseUrl()).build());

    String result = weatherTools.getCurrentWeather("Nowhereville");

    assertEquals("Could not find a location matching \"Nowhereville\".", result);
  }

  @Test
  void alwaysEnabledNoApiKeyNeeded() {
    WeatherTools weatherTools = new WeatherTools(
        new GeocodingClient(RestClient.builder().baseUrl(geocodingServer.baseUrl()).build()),
        RestClient.builder().baseUrl(weatherServer.baseUrl()).build());

    assertTrue(weatherTools.isEnabled());
  }

}
