package com.mgatelabs.ffbe.shared.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgatelabs.ffbe.shared.util.JsonTool;

import java.io.File;
import java.io.IOException;

/**
 * Created by @mgatelabs (Michael Fuller) on 9/1/2017.
 */
public class DeviceDefinition {
    private String name;
    @JsonIgnore
    private String deviceId;
    private String viewId;
    private int width;
    private int height;

    public DeviceDefinition() {
    }

    public DeviceDefinition(String deviceId) {
        this.deviceId = deviceId;
        name = deviceId;
        width = 1024;
        height = 1024;
        viewId = "";
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public static File getFileFor(String deviceName) {
        return new File("devices/" + deviceName + ".json");
    }

    public static boolean exists(String viewName) {
        return getFileFor(viewName).exists();
    }

    public static DeviceDefinition read(String deviceName) {
        final File deviceFile = getFileFor(deviceName);
        if (deviceFile.exists()) {
            final ObjectMapper objectMapper = JsonTool.INSTANCE;
            try {
                DeviceDefinition deviceDefinition = objectMapper.readValue(deviceFile, DeviceDefinition.class);
                deviceDefinition.setDeviceId(deviceName);
                return deviceDefinition;
            } catch (JsonParseException e) {
                e.printStackTrace();
            } catch (JsonMappingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
