package com.shmuelzon.HomeAssistantFloorPlan;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SunriseSunsetApiClient {
    private static final String API_URL = "https://api.sunrise-sunset.org/json";

    public Map<String, Instant> fetchSunriseSunset(double latitude, double longitude, LocalDate date) throws IOException, ParseException {
        String formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        // Use formatted=0 to get ISO 8601 UTC date-time strings which are easy to parse
        URL url = new URL(String.format(Locale.US, "%s?lat=%f&lng=%f&date=%s&formatted=0", API_URL, latitude, longitude, formattedDate));

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000); // 15 seconds
        conn.setReadTimeout(15000); // 15 seconds

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            // Try to read error stream for more details
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String errorBody = errorReader.lines().collect(Collectors.joining("\n"));
                throw new IOException("HTTP error " + responseCode + ": " + errorBody);
            } catch (Exception e) {
                throw new IOException("HTTP error code: " + responseCode);
            }
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            JSONParser parser = new JSONParser();
            JSONObject jsonResponse = (JSONObject) parser.parse(in);
            String status = (String) jsonResponse.get("status");

            if (!"OK".equals(status)) {
                throw new IOException("API returned status: " + status);
            }

            JSONObject results = (JSONObject) jsonResponse.get("results");
            Instant sunrise = Instant.parse((String) results.get("sunrise"));
            Instant sunset = Instant.parse((String) results.get("sunset"));
            Instant solarNoon = Instant.parse((String) results.get("solar_noon"));
            Instant civilTwilightEnd = Instant.parse((String) results.get("civil_twilight_end"));

            Map<String, Instant> times = new HashMap<>();
            times.put("sunrise", sunrise);
            times.put("sunset", sunset);
            times.put("solar_noon", solarNoon);
            times.put("civil_twilight_end", civilTwilightEnd);
            return times;
        } finally {
            conn.disconnect();
        }
    }
}