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

class TimeToolsTest {

  @RegisterExtension
  static WireMockExtension geocodingServer = WireMockExtension.newInstance().build();

  @AfterEach
  void resetStubs() {
    geocodingServer.resetAll();
  }

  private TimeTools timeTools() {
    return new TimeTools(new GeocodingClient(RestClient.builder().baseUrl(geocodingServer.baseUrl()).build()));
  }

  @Test
  void returnsCurrentTimeUsingGeocodedTimezone() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"results":[{"latitude":35.68,"longitude":139.69,"name":"Tokyo","country":"Japan","timezone":"Asia/Tokyo"}]}
            """)));

    String result = timeTools().getCurrentTime("Tokyo");

    assertTrue(result.contains("Tokyo"));
    assertTrue(result.contains("Japan"));
    assertTrue(result.contains("Asia/Tokyo"));
  }

  @Test
  void returnsFriendlyMessageWhenLocationNotFound() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));

    assertEquals("Could not find a location matching \"Nowhereville\".", timeTools().getCurrentTime("Nowhereville"));
  }

  @Test
  void returnsFriendlyMessageWhenTimezoneMissing() {
    geocodingServer.stubFor(get(urlPathEqualTo("/v1/search"))
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
            {"results":[{"latitude":0.0,"longitude":0.0,"name":"Null Island","country":"International waters"}]}
            """)));

    String result = timeTools().getCurrentTime("Null Island");

    assertEquals("Could not determine the timezone for Null Island.", result);
  }

}
