package io.github.fabb.wigai.introspector;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

/**
 * Definition for the API Introspector Extension.
 */
public class ApiIntrospectorExtensionDefinition extends ControllerExtensionDefinition {
    
    private static final UUID DRIVER_ID = UUID.fromString("a1c10d25-9dbf-4ac4-8888-9d00b4ab3333");

    @Override
    public String getName() {
        return "WigAI API Introspector";
    }

    @Override
    public String getAuthor() {
        return "WigAI Team";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "WigAI";
    }

    @Override
    public String getHardwareModel() {
        return "API Introspector";
    }

    @Override
    public int getRequiredAPIVersion() {
        return 19;
    }

    @Override
    public int getNumMidiInPorts() {
        return 0;
    }

    @Override
    public int getNumMidiOutPorts() {
        return 0;
    }

    @Override
    public String getHelpFilePath() {
        return "https://github.com/fabb/WigAI";
    }

    @Override
    public void listAutoDetectionMidiPortNames(
            final com.bitwig.extension.controller.AutoDetectionMidiPortNamesList list,
            final PlatformType platformType) {
        // No MIDI auto-detection needed
    }

    @Override
    public ApiIntrospectorExtension createInstance(final ControllerHost host) {
        return new ApiIntrospectorExtension(this, host);
    }
}
