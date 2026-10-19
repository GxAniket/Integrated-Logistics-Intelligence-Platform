package com.shiptrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptrack.service.MapsService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapsServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void normalizesAbbreviatedIndianAddressesBeforeGeocoding() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response =
                (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[{\"lat\":\"17.42\",\"lon\":\"78.49\"}]");
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);

        MapsService mapsService = new MapsService(
                httpClient,
                new ObjectMapper(),
                "https://nominatim.example/search",
                "https://router.example/route/v1/driving",
                "ShipTrack test",
                Duration.ofSeconds(5)
        );

        MapsService.Coordinates coordinates =
                mapsService.geocode("1-4-27-129 bholakpur,hyderabad");

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        );

        assertEquals(17.42, coordinates.latitude());
        assertEquals(78.49, coordinates.longitude());
        assertTrue(requestCaptor.getValue().uri().getRawQuery()
                .contains("q=bholakpur%2C+hyderabad%2C+India"));
    }
}
