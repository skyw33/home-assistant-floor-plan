package com.shmuelzon.HomeAssistantFloorPlan;

import javax.swing.JOptionPane;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
public class HomeAssistantApiClient {
    private final String baseUrl;
    private final String accessToken;

    public HomeAssistantApiClient(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
    }

    // A definitive list of entity domains to be imported, filtering out irrelevant ones.
    private static final Set<String> ALLOWED_DOMAINS = new HashSet<>(Arrays.asList(
        "air_quality", "alarm_control_panel", "assist_satellite", "binary_sensor", "button", "camera",
        "climate", "cover", "device_tracker", "fan", "humidifier", "input_boolean", "input_button",
        "lawn_mower", "light", "lock", "media_player", "remote", "sensor", "siren", "switch",
        "todo", "update", "vacuum", "valve", "water_heater", "weather"
    ));

    private JSONArray getJsonArrayFromApi(String endpoint) throws IOException, ParseException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000); // 10 seconds
            conn.setReadTimeout(10000); // 10 seconds

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                JSONParser parser = new JSONParser();
                Object result = parser.parse(in);
                in.close();
                if (result instanceof JSONArray) {
                    return (JSONArray) result;
                }
                throw new IOException("Unexpected JSON type from endpoint " + endpoint + ". Expected JSONArray.");
            } else {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorContent = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorContent.append(errorLine);
                    }
                    throw new IOException("HTTP error " + responseCode + ": " + errorContent.toString());
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String postTemplate(String template) throws IOException {
        URL url = new URL(baseUrl + "/api/template");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            JSONObject payload = new JSONObject();
            payload.put("template", template);
            String jsonInputString = payload.toJSONString();

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorContent = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorContent.append(errorLine);
                    }
                    throw new IOException("HTTP error " + responseCode + " from /api/template: " + errorContent.toString());
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Map<String, String> fetchAreas() throws Exception {
        Map<String, String> areas = new java.util.HashMap<>();
        // This template builds a JSON string of area_id: area_name pairs.
        // It uses a safe pattern that avoids restricted functions like .append() or .update().
        String template = "{%- set sep = \"\" -%}" +
                          "{{ \"{\" }}" +
                          "{%- for area_id in areas() -%}" +
                          "  {%- set area_name_val = area_name(area_id) -%}" +
                          "  {%- if area_name_val is not none -%}" +
                          "    {{ sep }}\"{{ area_id }}\":\"{{ area_name_val }}\"" +
                          "    {%- set sep = \",\" -%}" +
                          "  {%- endif -%}" +
                          "{%- endfor -%}" +
                          "{{ \"}\" }}";

        String jsonResponse = postTemplate(template);

        if (jsonResponse != null && !jsonResponse.trim().isEmpty()) {
            JSONParser parser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject) parser.parse(jsonResponse);
                for (Object key : jsonObject.keySet()) {
                    String areaId = (String) key;
                    String areaName = (String) jsonObject.get(key);
                    if (areaId != null && areaName != null && !areaName.equalsIgnoreCase("None")) {
                        areas.put(areaId, areaName);
                    }
                }
            } catch (ParseException e) {
                System.err.println("Error parsing JSON response from template for areas: " + e.getMessage());
            }
        }
        return areas;
    }

    private Map<String, String> fetchEntityAreaMappings() throws Exception {
        Map<String, String> entityAreaMap = new java.util.HashMap<>();
        // This template builds a JSON string of entity_id: area_id pairs.
        // It uses a safe pattern that avoids restricted functions.
        String template = "{%- set sep = \"\" -%}" +
                          "{{ \"{\" }}" +
                          "{%- for state in states -%}" +
                          "  {% set area = area_id(state.entity_id) %}" +
                          "  {% if area is not none %}" +
                          "    {{ sep }}\"{{ state.entity_id }}\":\"{{ area }}\"" +
                          "    {%- set sep = \",\" -%}" +
                          "  {% endif %}" +
                          "{% endfor %}" +
                          "{{ \"}\" }}";

        String jsonResponse = postTemplate(template);

        if (jsonResponse != null && !jsonResponse.trim().isEmpty()) {
            JSONParser parser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject) parser.parse(jsonResponse);
                for (Object key : jsonObject.keySet()) {
                    String entityId = (String) key;
                    String areaId = (String) jsonObject.get(key);
                    if (entityId != null && areaId != null) {
                        entityAreaMap.put(entityId, areaId);
                    }
                }
            } catch (ParseException e) {
                System.err.println("Error parsing JSON response from template for entity-area mappings: " + e.getMessage());
            }
        } else {
            System.err.println("Warning: Template for entity-area mappings returned empty or null response.");
        }
        return entityAreaMap;
    }

    /**
     * Formats an area_id (e.g., "living_room") into a user-friendly name (e.g., "Living Room").
     * @param areaId The area ID string.
     * @return A formatted, capitalized string.
     */
    private String formatAreaId(String areaId) {
        if (areaId == null || areaId.trim().isEmpty()) {
            return null;
        }
        // Replace underscores and hyphens with spaces, and capitalize each word.
        String[] words = areaId.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                formattedName.append(Character.toUpperCase(word.charAt(0)))
                             .append(word.substring(1).toLowerCase())
                             .append(" ");
            }
        }
        return formattedName.toString().trim();
    }

    public List<HaEntity> fetchEntities() throws Exception {
        Map<String, String> areas = new java.util.HashMap<>(); // Initialize to empty map
        try {
            areas = fetchAreas();
        } catch (Exception e) {
            System.err.println("Warning: Could not fetch Home Assistant Areas. Proceeding without room information. Error: " + e.getMessage());
            // If fetching areas fails (e.g., 404 on older HA versions), proceed with an empty map.
            // Error already logged in fetchAreas, just proceed with empty map.
        }
        
        Map<String, String> entityAreaMappings = new java.util.HashMap<>(); // Initialize to empty map
        try {
            entityAreaMappings = fetchEntityAreaMappings();
        } catch (IOException | ParseException e) { // Catch specific exceptions
            System.err.println("Warning: Could not fetch Home Assistant Entity-Area mappings. Proceeding without room information. Error: " + e.getMessage());
            // Error already logged in fetchEntityAreaMappings, just proceed with empty map.
        }

        List<HaEntity> entities = new ArrayList<>();
        JSONArray statesArray = getJsonArrayFromApi("/api/states");

        for (Object obj : statesArray) {
            JSONObject entityJson = (JSONObject) obj;
            String entityId = (String) entityJson.get("entity_id");

            // Filter out entities that are not in the allowed domains.
            if (entityId == null || !entityId.contains(".")) {
                continue;
            }
            String domain = entityId.substring(0, entityId.indexOf('.'));
            if (!ALLOWED_DOMAINS.contains(domain)) {
                continue;
            }

            JSONObject attributes = (JSONObject) entityJson.get("attributes");
            String friendlyName = (attributes != null) ? (String) attributes.get("friendly_name") : null;

            if (friendlyName == null || friendlyName.trim().isEmpty()) {
                friendlyName = entityId;
            }

            String areaId = entityAreaMappings.get(entityId);
            // Try to get the official friendly name first.
            String areaName = (areaId != null) ? areas.get(areaId) : null;

            // If the official name isn't found (because /api/areas failed),
            // but we have an area_id, format it into a user-friendly name as a fallback.
            if (areaName == null && areaId != null) {
                areaName = formatAreaId(areaId);
            }
            entities.add(new HaEntity(entityId, friendlyName, areaName));
        }

        java.util.Collections.sort(entities);
        return entities;
    }
}