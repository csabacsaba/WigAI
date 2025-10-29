package io.github.fabb.wigai.introspector;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * API Introspector Extension for examining available Bitwig API methods at runtime.
 * This helps identify which methods are actually available in the current API version.
 */
public class ApiIntrospectorExtension extends ControllerExtension {

  protected ApiIntrospectorExtension(ApiIntrospectorExtensionDefinition def, ControllerHost host) {
    super(def, host);
  }

  @Override
  public void init() {
    ControllerHost host = getHost();
    host.println("=".repeat(80));
    host.println("[API Introspector] Starting reflection test...");
    host.println("=".repeat(80));

    try {
      // 1) Project + first track
      Project project = host.getProject();
      TrackBank tb = host.createTrackBank(1, 0, 8);
      Track track = tb.getItemAt(0);
      track.selectInMixer();

      // 2) Basic API classes introspection
      printApiMethods(host, "ControllerHost", host.getClass());
      printApiMethods(host, "Project", project.getClass());
      printApiMethods(host, "Track", track.getClass());

      // 3) Device chain introspection
      DeviceBank db = track.createDeviceBank(8);
      Device device = db.getItemAt(0);
      printApiMethods(host, "Device", device.getClass());

      // 4) DeviceChain (if it exists)
      try {
        Method m = device.getClass().getMethod("deviceChain");
        Object dc = m.invoke(device);
        if (dc != null) {
          printApiMethods(host, "DeviceChain", dc.getClass());
        } else {
          host.println("[API Introspector] DeviceChain is null (no chain on this device).");
        }
      } catch (NoSuchMethodException e) {
        host.println("[API Introspector] No deviceChain() method on Device class.");
      }

      // 5) Check for InsertionPoint specifically
      host.println("");
      host.println("=".repeat(80));
      host.println("[API Introspector] SEARCHING FOR INSERTION POINT METHODS:");
      host.println("=".repeat(80));
      
      checkForInsertionPointMethods(host, track, "Track");
      checkForInsertionPointMethods(host, device, "Device");

    } catch (Exception e) {
      host.println("[API Introspector] ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
      e.printStackTrace();
    }

    host.println("");
    host.println("=".repeat(80));
    host.println("[API Introspector] Done. Check Bitwig log (Help → Show Log Files).");
    host.println("=".repeat(80));
  }

  private void printApiMethods(ControllerHost host, String label, Class<?> clazz) {
    host.println("");
    host.println("---- " + label + " (" + clazz.getName() + ") ----");
    String list = Arrays.stream(clazz.getMethods())
        .map(Method::getName)
        .distinct()
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.joining(", "));
    host.println(list);
  }

  private void checkForInsertionPointMethods(ControllerHost host, Object obj, String label) {
    host.println("");
    host.println("Checking " + label + " for insertion point methods:");
    
    Method[] methods = obj.getClass().getMethods();
    boolean found = false;
    
    for (Method method : methods) {
      String name = method.getName().toLowerCase();
      if (name.contains("insertion") || name.contains("insert") || name.contains("device")) {
        host.println("  - " + method.getName() + " : " + 
                     Arrays.toString(method.getParameterTypes()) + " -> " + 
                     method.getReturnType().getSimpleName());
        found = true;
      }
    }
    
    if (!found) {
      host.println("  (no insertion-related methods found)");
    }
  }

  @Override 
  public void flush() {}
  
  @Override 
  public void exit() { 
    getHost().println("[API Introspector] exit"); 
  }
}
