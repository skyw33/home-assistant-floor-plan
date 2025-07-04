package com.shmuelzon.HomeAssistantFloorPlan;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.eteks.sweethome3d.model.Home;

public class Settings {
    private static final String PROPERTY_PREFIX = "com.shmuelzon.HomeAssistantFloorPlan.";
    public static final String CONTROLLER_HA_URL = "haUrl";
    public static final String CONTROLLER_HA_TOKEN = "haToken";

    private Home home;

    public Settings(Home home) {
        this.home = home;
    }

    public String get(String name, String defaultValue) {
        String value = home.getProperty(PROPERTY_PREFIX + name);
        if (value == null)
            return defaultValue;
        return value;
    }

    public String get(String name) {
        return get(name, null);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        return Boolean.valueOf(get(name, String.valueOf(defaultValue)));
    }

    public int getInteger(String name, int defaultValue) {
        return Integer.valueOf(get(name, String.valueOf(defaultValue)));
    }

    public long getLong(String name, long defaultValue) {
        return Long.parseLong(get(name, String.valueOf(defaultValue)));
    }

    public List<Long> getListLong(String name, List<Long> defaultValue) {
        String values = get(name);

        if (values == null)
            return defaultValue;
        return Arrays.stream(values.split(",")).map(Long::valueOf).collect(Collectors.toList());
    }

    public double getDouble(String name, double defaultValue) {
        return Double.parseDouble(get(name, String.valueOf(defaultValue)));
    }

    public void set(String name, String value) {
        String oldValue = get(name);

        if (oldValue != null && oldValue.equals(value))
            return;
        home.setProperty(PROPERTY_PREFIX + name, value);
        home.setModified(true);
    }

    public void setBoolean(String name, boolean value) {
        set(name, String.valueOf(value));
    }

    public void setInteger(String name, int value) {
        set(name, String.valueOf(value));
    }

    public void setLong(String name, long value) {
        set(name, String.valueOf(value));
    }

    public void setListLong(String name, List<Long> value) {
        set(name, String.join(",", value.stream().map(String::valueOf).collect(Collectors.toList())));
    }

    public void setDouble(String name, double value) {
        set(name, String.valueOf(value));
    }
};
