package com.foodflow.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.foodflow.model.CharityRequest;
import com.foodflow.model.CharityStatus;
import com.foodflow.store.InMemoryStore;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class CharityRequestDatabase extends InMemoryStore<CharityRequest> {

    private static final String REQUESTS_RESOURCE = "database/charity-requests.json";

    @PostConstruct
    public void init() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(REQUESTS_RESOURCE)) {
            if (stream == null) {
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            JsonNode root = mapper.readTree(stream);
            if (!root.isArray()) {
                return;
            }
            List<CharityRequest> initialRequests = new ArrayList<>();
            for (JsonNode node : root) {
                CharityRequest request = new CharityRequest();
                request.setId(node.path("charity_id").asText(null));
                request.setCharityName(node.path("charity_name").asText(null));
                request.setDeliveryLocation(node.path("location").asText(null));
                request.setContactEmail(node.path("contact_email").asText(null));

                JsonNode requirements = node.path("requirements");
                if (requirements.isObject()) {
                    JsonNode acceptedItems = requirements.path("accepted_item_types");
                    if (acceptedItems.isArray()) {
                        List<String> accepted = new ArrayList<>();
                        acceptedItems.forEach(item -> accepted.add(item.asText()));
                        request.setAcceptedDietaryTypes(accepted);
                    }
                    JsonNode acceptableAllergens = requirements.path("acceptable_allergens");
                    if (acceptableAllergens.isArray()) {
                        List<String> acceptable = new ArrayList<>();
                        acceptableAllergens.forEach(item -> acceptable.add(item.asText()));
                        request.setPreferences(acceptable);
                    }
                    request.setMaxCapacityItems(requirements.path("max_capacity_items").asInt(0));

                    JsonNode storageCaps = requirements.path("storage_capabilities");
                    if (storageCaps.isArray()) {
                        List<String> capabilities = new ArrayList<>();
                        storageCaps.forEach(item -> capabilities.add(item.asText()));
                        request.setStorageCapabilities(String.join(", ", capabilities));
                    }
                    JsonNode operatingHours = requirements.path("operating_hours");
                    if (operatingHours.isObject()) {
                        request.setLatestDeliveryTime(parseTime(operatingHours.path("closes_at").asText(null)));
                    }
                    JsonNode transport = requirements.path("transportation");
                    if (transport.isObject()) {
                        request.setCanCollect(transport.path("can_collect").asBoolean(false));
                    }
                }
                request.setStatus(CharityStatus.PENDING);
                initialRequests.add(request);
            }
            initialRequests.forEach(this::save);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load initial charity requests from " + REQUESTS_RESOURCE, e);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value);
    }

    @Override
    protected String idOf(CharityRequest item) {
        return item.getId();
    }

    public List<CharityRequest> findPending() {
        return findAll().stream()
                .filter(c -> c.getStatus() == CharityStatus.PENDING)
                .toList();
    }
}
