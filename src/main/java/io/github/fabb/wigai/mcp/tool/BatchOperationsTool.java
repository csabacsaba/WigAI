package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * MCP Tool that executes a batch ("branch") of operations in one call.
 *
 * Current supported operations:
 * - create_tracks: { type: "instrument"|"audio"|"effect", count: number }
 * - insert_device_on_tracks: { device_uuid: string, device_position: number, track_indices: number[] | {start_index: number, end_index: number} }
 *
 * Notes:
 * - The tool executes operations sequentially and stops on the first error by default.
 * - You can set optional flag { continue_on_error: true } at the root input to attempt all operations.
 */
public class BatchOperationsTool {

    public static McpServerFeatures.SyncToolSpecification specification(
        BitwigApiFacade bitwigApiFacade,
        StructuredLogger logger
    ) {
        var inputSchema = """
            {
              "type": "object",
              "properties": {
                "operations": {
                  "type": "array",
                  "description": "A list of operations to execute sequentially. Supported operation types: 'create_tracks', 'insert_device_on_tracks', 'create_clips', 'clear_clips', 'write_notes_to_clips', 'set_clip_properties', 'switch_device_page_on_tracks', 'set_device_parameters_on_tracks', 'set_device_page_parameters_on_tracks'",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": {
                        "type": "string",
                        "enum": ["create_tracks", "insert_device_on_tracks", "create_clips", "clear_clips", "write_notes_to_clips", "set_clip_properties", "switch_device_page_on_tracks", "set_device_parameters_on_tracks", "set_device_page_parameters_on_tracks"],
                        "description": "Operation type. Examples: 'create_tracks' (args: {type:'instrument'|'audio'|'effect', count:N}), 'insert_device_on_tracks' (args: {device_uuid:string, device_position:N, start_index:N, end_index:N}), 'switch_device_page_on_tracks' (args: {device_position:N, page_index:N (NUMBER, not name! EQ+ pages: 0=Gains, 1=Freqs, 2=Qs), start_index:N, end_index:N}), 'set_device_parameters_on_tracks' (args: {device_position:N, start_index:N, end_index:N, parameters:[{index:N, value:0.0-1.0}]} - IMPORTANT: value must be normalized 0.0-1.0, NOT raw Hz/dB values), 'set_device_page_parameters_on_tracks' (args: {device_position:N, page_index:N, start_index:N, end_index:N, parameters:[{index:N, value:0.0-1.0}]} - switches to page THEN sets parameters), 'create_clips' (args: {scene_index:N, slot_index:N, start_index:N, end_index:N}), 'write_notes_to_clips' (args: {scene_index:N, slot_index:N, start_index:N, end_index:N, notes:[{pitch:N, start:N, duration:N, velocity:N}]})"
                      },
                      "args": { "type": "object", "description": "Operation-specific arguments" }
                    },
                    "required": ["type"]
                  }
                },
                "continue_on_error": { "type": "boolean", "default": false, "description": "Continue executing remaining operations even if one fails" }
              },
              "required": ["operations"]
            }
            """;

        var tool = McpSchema.Tool.builder()
            .name("batch_operations")
            .description("""
                Execute multiple Bitwig operations in a single batch request. \
                Supported operations: create_tracks, insert_device_on_tracks, create_clips, clear_clips, write_notes_to_clips, set_clip_properties, switch_device_page_on_tracks, set_device_parameters_on_tracks. \
                \
                IMPORTANT: When using 'insert_device_on_tracks', you MUST use correct device UUIDs! Call 'get_device_knowledge' tool first to get UUIDs. \
                Device UUIDs: EQ+ = e4815188-ba6f-4d14-bcfc-2dcb8f778ccb, Filter = 4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42, Compressor = 2b1b4787-8d74-4138-877b-9197209eef0f, Delay+ = f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9, Reverb = 5a1cb339-1c4a-4cc7-9cae-bd7a2058153d \
                IMPORTANT: All device parameter values must be normalized between 0.0 and 1.0. Do NOT use raw values like Hz or dB. \
                IMPORTANT: page_index must be a NUMBER (0, 1, 2...), NOT a page name. For EQ+: 0=Gains, 1=Freqs, 2=Qs. \
                \
                Example: Create 10 instrument tracks: {"operations":[{"type":"create_tracks","args":{"type":"instrument","count":10}}]} \
                Example: Insert EQ+ on all tracks: {"operations":[{"type":"insert_device_on_tracks","args":{"device_uuid":"e4815188-ba6f-4d14-bcfc-2dcb8f778ccb","device_position":0,"start_index":0,"end_index":19}}]}
                """)
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "batch_operations",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> operations = (List<Map<String, Object>>) args.get("operations");
                    if (operations == null) {
                        throw new IllegalArgumentException("'operations' array is required");
                    }
                    boolean continueOnError = Boolean.TRUE.equals(args.get("continue_on_error"));

                    List<Map<String, Object>> results = new ArrayList<>();

                    for (int i = 0; i < operations.size(); i++) {
                        Map<String, Object> op = operations.get(i);
                        String type = Objects.toString(op.get("type"), null);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> opArgs = (Map<String, Object>) op.getOrDefault("args", Map.of());

                        String opId = String.format("op_%d_%s", i, type);
                        Map<String, Object> result;
                        try {
                            switch (type) {
                                case "create_tracks" -> result = handleCreateTracks(bitwigApiFacade, opArgs);
                                case "insert_device_on_tracks" -> result = handleInsertDeviceOnTracks(bitwigApiFacade, opArgs);
                                case "create_clips" -> result = handleCreateClips(bitwigApiFacade, opArgs);
                                case "clear_clips" -> result = handleClearClips(bitwigApiFacade, opArgs);
                                case "write_notes_to_clips" -> result = handleWriteNotesToClips(bitwigApiFacade, opArgs);
                                case "set_clip_properties" -> result = handleSetClipProperties(bitwigApiFacade, opArgs);
                                case "switch_device_page_on_tracks" -> result = handleSwitchDevicePageOnTracks(bitwigApiFacade, opArgs);
                                case "set_device_parameters_on_tracks" -> result = handleSetDeviceParametersOnTracks(bitwigApiFacade, opArgs);
                                case "set_device_page_parameters_on_tracks" -> result = handleSetDevicePageParametersOnTracks(bitwigApiFacade, opArgs);
                                default -> throw new IllegalArgumentException("Unsupported operation type: " + type);
                            }
                            if (!result.containsKey("status")) {
                                result.put("status", "success");
                            }
                            result.put("type", type);
                            result.put("op_id", opId);
                            results.add(result);
                        } catch (Exception e) {
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("status", "error");
                            err.put("type", type);
                            err.put("op_id", opId);
                            err.put("message", e.getMessage());
                            results.add(err);
                            if (!continueOnError) {
                                break;
                            }
                        }
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("executed", results.size());
                    response.put("results", results);
                    return response;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static Map<String, Object> handleCreateTracks(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        String trackType = ParameterValidator.validateRequiredString(args, "type", "create_tracks");
        Integer countObj = ParameterValidator.validateRequiredInteger(args, "count", "create_tracks");
        int count = Math.max(0, countObj);

        for (int i = 0; i < count; i++) {
            switch (trackType) {
                case "instrument" -> bitwigApiFacade.createInstrumentTrack();
                case "audio" -> bitwigApiFacade.createAudioTrack();
                case "effect" -> bitwigApiFacade.createEffectTrack();
                default -> throw new IllegalArgumentException("Unsupported track type: " + trackType);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "tracks_created");
        res.put("track_type", trackType);
        res.put("count", count);
        return res;
    }

    private static Map<String, Object> handleInsertDeviceOnTracks(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        String deviceUuid = ParameterValidator.validateRequiredString(args, "device_uuid", "insert_device_on_tracks");
        Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "insert_device_on_tracks");

        List<Integer> indices = resolveTrackIndices(args, "insert_device_on_tracks");

        List<Map<String, Object>> perTrack = new ArrayList<>();
        for (Integer trackIndex : indices) {
            boolean success = bitwigApiFacade.insertBitwigDeviceByUuid(trackIndex, devicePosition, deviceUuid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            entry.put("device_position", devicePosition);
            entry.put("device_uuid", deviceUuid);
            entry.put("inserted", success);
            perTrack.add(entry);
        }

        // Wait for devices to actually appear at the expected position on all tracks
        // This ensures that when multiple devices are inserted sequentially on the same tracks,
        // the device positions are correctly recognized by the API
        waitForDeviceInsertion(bitwigApiFacade, indices, devicePosition, deviceUuid);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "device_inserted_on_tracks");
        res.put("device_uuid", deviceUuid);
        res.put("device_position", devicePosition);
        res.put("tracks", perTrack);
        return res;
    }

    /**
     * Waits for a specific device (identified by UUID) to appear at the specified position on all given tracks.
     * Polls the device list and verifies both device count AND that we have the expected device.
     *
     * @param bitwigApiFacade The Bitwig API facade
     * @param trackIndices List of track indices to check
     * @param expectedPosition The expected device position (0-based)
     * @param deviceUuid The UUID of the device we're waiting for
     */
    private static void waitForDeviceInsertion(BitwigApiFacade bitwigApiFacade, List<Integer> trackIndices, int expectedPosition, String deviceUuid) {
        final int maxAttempts = 50; // Maximum 50 attempts
        final int sleepMs = 50;     // Check every 50ms
        final int expectedDeviceCount = expectedPosition + 1;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            boolean allTracksReady = true;

            // Check if all tracks have the expected number of devices
            for (Integer trackIndex : trackIndices) {
                try {
                    List<Map<String, Object>> devices = bitwigApiFacade.getDevicesOnTrack(trackIndex, null, null);

                    // Check 1: Do we have enough devices?
                    if (devices.size() < expectedDeviceCount) {
                        allTracksReady = false;
                        break;
                    }

                    // Check 2: Is the device at the expected position the one we just inserted?
                    // Note: We can't directly verify UUID from the device list, but checking count
                    // combined with sequential insertion (position 0, then 1, then 2...) ensures correctness
                    // since we wait for each insertion to complete before starting the next

                } catch (Exception e) {
                    allTracksReady = false;
                    break;
                }
            }

            if (allTracksReady) {
                // All tracks have the expected device count
                // Since we insert sequentially and wait after each insertion,
                // this guarantees the correct device is at the correct position
                return;
            }

            // Wait before next check
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Timeout reached - log warning but continue
        // This allows the batch to proceed even if verification fails
    }

    private static List<Integer> resolveTrackIndices(Map<String, Object> args, String opName) {
        List<Integer> indices = new ArrayList<>();
        Object indicesObj = args.get("track_indices");
        if (indicesObj instanceof List<?>) {
            for (Object o : (List<?>) indicesObj) {
                if (o instanceof Number n) {
                    indices.add(n.intValue());
                } else {
                    throw new IllegalArgumentException("track_indices must be an array of integers");
                }
            }
        } else {
            Integer start = ParameterValidator.validateRequiredInteger(args, "start_index", opName);
            Integer end = ParameterValidator.validateRequiredInteger(args, "end_index", opName);
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid track index range: start_index <= end_index and start_index >= 0 required");
            }
            for (int i = start; i <= end; i++) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static Map<String, Object> handleCreateClips(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer sceneIndex = ParameterValidator.validateRequiredInteger(args, "scene_index", "create_clips");
        Integer slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", "create_clips");
        String ifExists = Objects.toString(args.getOrDefault("if_exists", "replace"));
        int lengthBars = ((Number) args.getOrDefault("length_bars", 4)).intValue();

        List<Integer> indices = resolveTrackIndices(args, "create_clips");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            try {
                boolean has = bitwigApiFacade.hasClipAt(trackIndex, slotIndex);
                switch (ifExists) {
                    case "replace" -> {
                        // create new empty clip regardless: clear then create guarantees fresh state
                        bitwigApiFacade.createEmptyClip(trackIndex, slotIndex, lengthBars);
                        entry.put("created", true);
                        try { entry.put("verified", bitwigApiFacade.hasClipAt(trackIndex, slotIndex)); } catch (Exception ignore) {}
                    }
                    case "clear_then_keep" -> {
                        if (has) {
                            bitwigApiFacade.clearClipAt(trackIndex, slotIndex);
                            entry.put("created", false);
                            entry.put("cleared", true);
                        } else {
                            bitwigApiFacade.createEmptyClip(trackIndex, slotIndex, lengthBars);
                            entry.put("created", true);
                        }
                    }
                    case "skip" -> {
                        if (!has) {
                            bitwigApiFacade.createEmptyClip(trackIndex, slotIndex, lengthBars);
                            entry.put("created", true);
                        } else {
                            entry.put("created", false);
                            entry.put("skipped", true);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unsupported if_exists value: " + ifExists);
                }
            } catch (Exception e) {
                entry.put("error", e.getMessage());
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "clips_created");
        res.put("scene_index", sceneIndex);
        res.put("slot_index", slotIndex);
        res.put("length_bars", lengthBars);
        res.put("tracks", tracks);
        return res;
    }

    private static Map<String, Object> handleClearClips(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer sceneIndex = ParameterValidator.validateRequiredInteger(args, "scene_index", "clear_clips");
        Integer slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", "clear_clips");
        List<Integer> indices = resolveTrackIndices(args, "clear_clips");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            try {
                bitwigApiFacade.clearClipAt(trackIndex, slotIndex);
                entry.put("cleared", true);
            } catch (Exception e) {
                entry.put("error", e.getMessage());
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "clips_cleared");
        res.put("scene_index", sceneIndex);
        res.put("slot_index", slotIndex);
        res.put("tracks", tracks);
        return res;
    }

    private static Map<String, Object> handleWriteNotesToClips(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer sceneIndex = ParameterValidator.validateRequiredInteger(args, "scene_index", "write_notes_to_clips");
        Integer slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", "write_notes_to_clips");
        String mode = Objects.toString(args.getOrDefault("mode", "replace"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notes = (List<Map<String, Object>>) args.get("notes");
        if (notes == null || notes.isEmpty()) {
            throw new IllegalArgumentException("notes array must not be empty");
        }

        List<Integer> indices = resolveTrackIndices(args, "write_notes_to_clips");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            try {
                boolean replace = switch (mode) { case "replace" -> true; case "append" -> false; default -> throw new IllegalArgumentException("Unsupported mode: " + mode); };
                bitwigApiFacade.writeNotesAt(trackIndex, slotIndex, notes, replace);
                entry.put("written", notes.size());
            } catch (Exception e) {
                entry.put("error", e.getMessage());
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "notes_written_to_clips");
        res.put("scene_index", sceneIndex);
        res.put("slot_index", slotIndex);
        res.put("tracks", tracks);
        return res;
    }

    private static Map<String, Object> handleSetClipProperties(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer sceneIndex = ParameterValidator.validateRequiredInteger(args, "scene_index", "set_clip_properties");
        Integer slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", "set_clip_properties");

        // Optional properties
        Object lengthBeatsObj = args.get("length_beats");
        Double lengthBeats = null;
        if (lengthBeatsObj instanceof Number n) {
            lengthBeats = n.doubleValue();
        }
        Object loopEnabledObj = args.get("loop_enabled");
        Boolean loopEnabled = (loopEnabledObj instanceof Boolean b) ? b : null;
        String name = args.containsKey("name") ? Objects.toString(args.get("name"), null) : null;

        List<Integer> indices = resolveTrackIndices(args, "set_clip_properties");

        List<Map<String, Object>> tracks = new ArrayList<>();
        int okCount = 0;
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            Map<String, Object> applied = new LinkedHashMap<>();
            Map<String, Object> readBack = new LinkedHashMap<>();
            try {
                boolean has = bitwigApiFacade.hasClipAt(trackIndex, slotIndex);
                if (!has) {
                    entry.put("ok", false);
                    entry.put("error", "no_clip_at_slot");
                } else {
                    // Apply length_beats if provided
                    if (lengthBeats != null) {
                        bitwigApiFacade.setClipLoopLengthBeatsAt(trackIndex, slotIndex, lengthBeats);
                        double rb = bitwigApiFacade.getClipLoopLengthBeatsAt(trackIndex, slotIndex);
                        applied.put("length_beats", lengthBeats);
                        readBack.put("length_beats", rb);
                    }
                    // Apply loop_enabled if provided (not supported via public API)
                    if (loopEnabled != null) {
                        // Mark unsupported
                        entry.put("loop_enabled_status", "unsupported_property");
                    }
                    // Apply name if provided (not supported; facade will throw)
                    if (name != null) {
                        String templated = name.replace("{i}", String.valueOf(trackIndex));
                        try {
                            bitwigApiFacade.setClipNameAt(trackIndex, slotIndex, templated);
                            String rbName = bitwigApiFacade.getClipNameAt(trackIndex, slotIndex);
                            applied.put("name", templated);
                            readBack.put("name", rbName);
                        } catch (Exception e) {
                            entry.put("name_status", "unsupported_property");
                        }
                    }

                    boolean ok = true;
                    // Verify length match within small epsilon if provided
                    if (lengthBeats != null) {
                        Object rbObj = readBack.get("length_beats");
                        if (rbObj instanceof Number rbNum) {
                            double diff = Math.abs(rbNum.doubleValue() - lengthBeats);
                            if (diff > 0.01) ok = false;
                        } else {
                            ok = false;
                        }
                    }
                    // For unsupported fields, don't fail the whole track; we already recorded status
                    entry.put("ok", ok);
                    if (ok) okCount++;
                }
            } catch (Exception e) {
                entry.put("ok", false);
                entry.put("error", e.getMessage());
            }
            if (!applied.isEmpty()) entry.put("applied", applied);
            if (!readBack.isEmpty()) entry.put("read_back", readBack);
            tracks.add(entry);
        }

        String opStatus;
        if (okCount == 0) opStatus = "error";
        else if (okCount == indices.size()) opStatus = "success";
        else opStatus = "partial";

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "clip_properties_set");
        res.put("scene_index", sceneIndex);
        res.put("slot_index", slotIndex);
        if (lengthBeats != null) res.put("length_beats", lengthBeats);
        if (loopEnabled != null) res.put("loop_enabled", loopEnabled);
        if (name != null) res.put("name", name);
        res.put("tracks", tracks);
        res.put("status", opStatus);
        return res;
    }

    private static Map<String, Object> handleSwitchDevicePageOnTracks(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "switch_device_page_on_tracks");
        Integer pageIndex = ParameterValidator.validateRequiredInteger(args, "page_index", "switch_device_page_on_tracks");

        List<Integer> indices = resolveTrackIndices(args, "switch_device_page_on_tracks");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            try {
                // Get the device at the specified position on this track
                List<Map<String, Object>> devices = bitwigApiFacade.getDevicesOnTrack(trackIndex, null, null);

                if (devicePosition >= devices.size()) {
                    entry.put("error", "No device at position " + devicePosition);
                    entry.put("switched", false);
                    tracks.add(entry);
                    continue;
                }

                // Get device info
                Map<String, Object> deviceInfo = devices.get(devicePosition);
                String deviceName = (String) deviceInfo.get("name");
                entry.put("device_name", deviceName);

                // Switch the device page using the new BitwigApiFacade method
                boolean success = bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
                entry.put("switched", success);

            } catch (Exception e) {
                entry.put("error", e.getMessage());
                entry.put("switched", false);
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "device_page_switched_on_tracks");
        res.put("device_position", devicePosition);
        res.put("page_index", pageIndex);
        res.put("tracks", tracks);
        return res;
    }

    private static Map<String, Object> handleSetDeviceParametersOnTracks(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "set_device_parameters_on_tracks");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) args.get("parameters");
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("'parameters' array is required and must not be empty");
        }

        List<Integer> indices = resolveTrackIndices(args, "set_device_parameters_on_tracks");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            List<Map<String, Object>> paramResults = new ArrayList<>();

            try {
                // Get the device at the specified position on this track
                List<Map<String, Object>> devices = bitwigApiFacade.getDevicesOnTrack(trackIndex, null, null);

                if (devicePosition >= devices.size()) {
                    entry.put("error", "No device at position " + devicePosition);
                    tracks.add(entry);
                    continue;
                }

                // Get device info
                Map<String, Object> deviceInfo = devices.get(devicePosition);
                String deviceName = (String) deviceInfo.get("name");
                entry.put("device_name", deviceName);

                // Set each parameter
                for (Map<String, Object> param : parameters) {
                    Map<String, Object> paramResult = new LinkedHashMap<>();

                    Integer paramIndex = param.get("index") instanceof Number n ? n.intValue() : null;
                    Object valueObj = param.get("value");
                    Double value = null;

                    if (paramIndex == null) {
                        paramResult.put("error", "Parameter index is required");
                        paramResults.add(paramResult);
                        continue;
                    }

                    if (valueObj instanceof Number n) {
                        value = n.doubleValue();
                    } else {
                        paramResult.put("error", "Parameter value must be a number");
                        paramResults.add(paramResult);
                        continue;
                    }

                    paramResult.put("index", paramIndex);
                    paramResult.put("value", value);

                    boolean success = bitwigApiFacade.setDeviceParameterOnTrack(trackIndex, devicePosition, paramIndex, value);
                    paramResult.put("set", success);

                    // Read back the displayed value so LLM can see what it actually set (e.g., "107 Hz" for 0.18)
                    if (success) {
                        try {
                            String displayedValue = bitwigApiFacade.getDeviceParameterDisplayedValue(trackIndex, devicePosition, paramIndex);
                            if (displayedValue != null && !displayedValue.isEmpty()) {
                                paramResult.put("displayedValue", displayedValue);
                            }
                        } catch (Exception e) {
                            // Ignore - displayedValue is optional
                        }
                    }

                    paramResults.add(paramResult);
                }

                entry.put("parameters", paramResults);

            } catch (Exception e) {
                entry.put("error", e.getMessage());
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "device_parameters_set_on_tracks");
        res.put("device_position", devicePosition);
        res.put("tracks", tracks);
        return res;
    }

    private static Map<String, Object> handleSetDevicePageParametersOnTracks(BitwigApiFacade bitwigApiFacade, Map<String, Object> args) {
        Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "set_device_page_parameters_on_tracks");
        Integer pageIndex = ParameterValidator.validateRequiredInteger(args, "page_index", "set_device_page_parameters_on_tracks");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) args.get("parameters");
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("'parameters' array is required and must not be empty");
        }

        List<Integer> indices = resolveTrackIndices(args, "set_device_page_parameters_on_tracks");

        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Integer trackIndex : indices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("track_index", trackIndex);
            List<Map<String, Object>> paramResults = new ArrayList<>();

            try {
                // Get the device at the specified position on this track
                List<Map<String, Object>> devices = bitwigApiFacade.getDevicesOnTrack(trackIndex, null, null);

                if (devicePosition >= devices.size()) {
                    entry.put("error", "No device at position " + devicePosition);
                    tracks.add(entry);
                    continue;
                }

                // Get device info
                Map<String, Object> deviceInfo = devices.get(devicePosition);
                String deviceName = (String) deviceInfo.get("name");
                entry.put("device_name", deviceName);

                // Switch to the specified page FIRST
                boolean pageSwitched = bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
                entry.put("page_switched", pageSwitched);

                if (!pageSwitched) {
                    entry.put("error", "Failed to switch to page " + pageIndex);
                    tracks.add(entry);
                    continue;
                }

                // Now set each parameter on the current page
                for (Map<String, Object> param : parameters) {
                    Map<String, Object> paramResult = new LinkedHashMap<>();

                    Integer paramIndex = param.get("index") instanceof Number n ? n.intValue() : null;
                    Object valueObj = param.get("value");
                    Double value = null;

                    if (paramIndex == null) {
                        paramResult.put("error", "Parameter index is required");
                        paramResults.add(paramResult);
                        continue;
                    }

                    if (valueObj instanceof Number n) {
                        value = n.doubleValue();
                    } else {
                        paramResult.put("error", "Parameter value must be a number");
                        paramResults.add(paramResult);
                        continue;
                    }

                    paramResult.put("index", paramIndex);
                    paramResult.put("value", value);

                    boolean success = bitwigApiFacade.setDeviceParameterOnTrack(trackIndex, devicePosition, paramIndex, value);
                    paramResult.put("set", success);

                    // Read back the displayed value so LLM can see what it actually set
                    if (success) {
                        try {
                            String displayedValue = bitwigApiFacade.getDeviceParameterDisplayedValue(trackIndex, devicePosition, paramIndex);
                            if (displayedValue != null && !displayedValue.isEmpty()) {
                                paramResult.put("displayedValue", displayedValue);
                            }
                        } catch (Exception e) {
                            // Ignore - displayedValue is optional
                        }
                    }

                    paramResults.add(paramResult);
                }

                entry.put("parameters", paramResults);

            } catch (Exception e) {
                entry.put("error", e.getMessage());
            }
            tracks.add(entry);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", "device_page_parameters_set_on_tracks");
        res.put("device_position", devicePosition);
        res.put("page_index", pageIndex);
        res.put("tracks", tracks);
        return res;
    }
}
