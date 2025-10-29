package io.github.fabb.wigai.integration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify device knowledge base consistency.
 *
 * This test ensures that:
 * 1. Device UUIDs in bitwig-device-parameters.json are valid
 * 2. All documented devices have the required fields (uuid, type, pages)
 * 3. The UUIDs match what's documented in tool descriptions
 */
public class DeviceKnowledgeIntegrationTest {

    private static final String KNOWLEDGE_BASE_FILE = "/bitwig-device-parameters.json";

    // Expected UUIDs from tool descriptions - keep these in sync!
    private static final Map<String, String> EXPECTED_DEVICE_UUIDS = Map.of(
        "EQ+", "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb",
        "Filter+", "6d621c1c-ab64-43b4-aea3-dad37e6f649c",
        "Compressor", "ac02c3f8-7e93-4199-9751-0dccbb41a752",
        "Delay+", "c5b38bbd-6530-47e4-af32-d09477e1ae40",
        "Reverb", "b94713ba-65c8-4a28-9916-f08ac5aa73dc"
    );

    @Test
    public void testDeviceKnowledgeBaseExists() {
        InputStream is = getClass().getResourceAsStream(KNOWLEDGE_BASE_FILE);
        assertNotNull(is, "Device knowledge base file should exist in resources");
    }

    @Test
    public void testDeviceKnowledgeBaseIsValidJson() throws Exception {
        InputStream is = getClass().getResourceAsStream(KNOWLEDGE_BASE_FILE);
        assertNotNull(is, "Device knowledge base file should exist");

        Gson gson = new Gson();
        JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);

        assertNotNull(root, "JSON should be parseable");
        assertTrue(root.has("devices"), "JSON should have 'devices' object");
    }

    @Test
    public void testAllExpectedDevicesAreDocumented() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();

        for (String deviceName : EXPECTED_DEVICE_UUIDS.keySet()) {
            assertTrue(devices.has(deviceName),
                "Device '" + deviceName + "' should be documented in knowledge base");
        }
    }

    @Test
    public void testDeviceUuidsMatchExpectedValues() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();

        for (Map.Entry<String, String> entry : EXPECTED_DEVICE_UUIDS.entrySet()) {
            String deviceName = entry.getKey();
            String expectedUuid = entry.getValue();

            assertTrue(devices.has(deviceName),
                "Device '" + deviceName + "' should exist in knowledge base");

            JsonObject device = devices.getAsJsonObject(deviceName);
            assertTrue(device.has("uuid"),
                "Device '" + deviceName + "' should have a 'uuid' field");

            String actualUuid = device.get("uuid").getAsString();
            assertEquals(expectedUuid, actualUuid,
                "Device '" + deviceName + "' UUID should match expected value");
        }
    }

    @Test
    public void testAllDevicesHaveRequiredFields() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();

        for (String deviceName : EXPECTED_DEVICE_UUIDS.keySet()) {
            JsonObject device = devices.getAsJsonObject(deviceName);

            assertTrue(device.has("uuid"),
                deviceName + " should have 'uuid' field");
            assertTrue(device.has("type"),
                deviceName + " should have 'type' field");
            assertTrue(device.has("description"),
                deviceName + " should have 'description' field");
            assertTrue(device.has("pages"),
                deviceName + " should have 'pages' array");

            assertTrue(device.get("pages").isJsonArray(),
                deviceName + " 'pages' should be an array");
            assertTrue(device.getAsJsonArray("pages").size() > 0,
                deviceName + " should have at least one page");
        }
    }

    @Test
    public void testDevicePagesHaveRequiredFields() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();

        for (String deviceName : EXPECTED_DEVICE_UUIDS.keySet()) {
            JsonObject device = devices.getAsJsonObject(deviceName);
            var pages = device.getAsJsonArray("pages");

            for (int i = 0; i < pages.size(); i++) {
                JsonObject page = pages.get(i).getAsJsonObject();
                String pageContext = deviceName + " page " + i;

                assertTrue(page.has("index"),
                    pageContext + " should have 'index' field");
                assertTrue(page.has("name"),
                    pageContext + " should have 'name' field");
                assertTrue(page.has("parameters"),
                    pageContext + " should have 'parameters' array");

                assertEquals(i, page.get("index").getAsInt(),
                    pageContext + " index should match array position");

                var parameters = page.getAsJsonArray("parameters");
                assertTrue(parameters.size() > 0,
                    pageContext + " should have at least one parameter");
                assertTrue(parameters.size() <= 8,
                    pageContext + " should have at most 8 parameters (remote control page limit)");
            }
        }
    }

    @Test
    public void testDeviceParametersHaveRequiredFields() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();

        for (String deviceName : EXPECTED_DEVICE_UUIDS.keySet()) {
            JsonObject device = devices.getAsJsonObject(deviceName);
            var pages = device.getAsJsonArray("pages");

            for (int pageIdx = 0; pageIdx < pages.size(); pageIdx++) {
                JsonObject page = pages.get(pageIdx).getAsJsonObject();
                var parameters = page.getAsJsonArray("parameters");

                for (int paramIdx = 0; paramIdx < parameters.size(); paramIdx++) {
                    JsonObject param = parameters.get(paramIdx).getAsJsonObject();
                    String paramContext = deviceName + " page " + pageIdx + " param " + paramIdx;

                    assertTrue(param.has("index"),
                        paramContext + " should have 'index' field");
                    assertTrue(param.has("name"),
                        paramContext + " should have 'name' field");
                    assertTrue(param.has("description"),
                        paramContext + " should have 'description' field");

                    assertEquals(paramIdx, param.get("index").getAsInt(),
                        paramContext + " index should match array position");
                }
            }
        }
    }

    @Test
    public void testEqPlusHasThreePages() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();
        JsonObject eqPlus = devices.getAsJsonObject("EQ+");

        assertNotNull(eqPlus, "EQ+ should exist");
        var pages = eqPlus.getAsJsonArray("pages");
        assertEquals(3, pages.size(), "EQ+ should have exactly 3 pages");

        // Verify page names
        assertEquals("Gains", pages.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Freqs", pages.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("Qs", pages.get(2).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void testFilterPlusHasOnePageWithEightParameters() throws Exception {
        JsonObject devices = loadDeviceKnowledgeBase();
        JsonObject filterPlus = devices.getAsJsonObject("Filter+");

        assertNotNull(filterPlus, "Filter+ should exist");
        var pages = filterPlus.getAsJsonArray("pages");
        assertEquals(1, pages.size(), "Filter+ should have exactly 1 page");

        JsonObject page = pages.get(0).getAsJsonObject();
        assertEquals("Filter+", page.get("name").getAsString());

        var parameters = page.getAsJsonArray("parameters");
        assertEquals(8, parameters.size(), "Filter+ should have 8 parameters");
    }

    @Test
    public void testToolDescriptionsContainCorrectUuids() throws Exception {
        // This test verifies that when we update UUIDs in the knowledge base,
        // we remember to update them in the tool descriptions as well.
        // If this test fails, update the UUIDs in:
        // - DeviceInsertTool.java
        // - BatchOperationsTool.java
        // - GetDeviceKnowledgeTool.java

        JsonObject devices = loadDeviceKnowledgeBase();

        for (Map.Entry<String, String> entry : EXPECTED_DEVICE_UUIDS.entrySet()) {
            String deviceName = entry.getKey();
            String expectedUuid = entry.getValue();

            JsonObject device = devices.getAsJsonObject(deviceName);
            String actualUuid = device.get("uuid").getAsString();

            assertEquals(expectedUuid, actualUuid,
                "UUID mismatch for " + deviceName + ". " +
                "If you changed the UUID in bitwig-device-parameters.json, " +
                "you must also update it in: " +
                "DeviceInsertTool.java, BatchOperationsTool.java, GetDeviceKnowledgeTool.java, " +
                "and EXPECTED_DEVICE_UUIDS in this test");
        }
    }

    // Helper method to load the device knowledge base
    private JsonObject loadDeviceKnowledgeBase() throws Exception {
        InputStream is = getClass().getResourceAsStream(KNOWLEDGE_BASE_FILE);
        assertNotNull(is, "Device knowledge base file should exist");

        Gson gson = new Gson();
        JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);

        return root.getAsJsonObject("devices");
    }
}
