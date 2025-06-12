package com.shmuelzon.HomeAssistantFloorPlan;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;


public class Entity implements Comparable<Entity> {
    public enum Property {ALWAYS_ON, DISPLAY_FURNITURE_CONDITION, IS_RGB, POSITION,}
    public enum DisplayType {BADGE, ICON, LABEL, FAN}
    public enum DisplayCondition {ALWAYS, NEVER, WHEN_ON, WHEN_OFF}
    public enum Action {MORE_INFO, NAVIGATE, NONE, TOGGLE, CALL_SERVICE}
    public enum DisplayFurnitureCondition {ALWAYS, STATE_EQUALS, STATE_NOT_EQUALS}

    private static final String SETTING_NAME_DISPLAY_TYPE = "displayType";
    private static final String SETTING_NAME_DISPLAY_CONDITION = "displayCondition";
    private static final String SETTING_NAME_TAP_ACTION = "tapAction";
    private static final String SETTING_NAME_TAP_ACTION_VALUE = "tapActionValue";
    private static final String SETTING_NAME_DOUBLE_TAP_ACTION = "doubleTapAction";
    private static final String SETTING_NAME_DOUBLE_TAP_ACTION_VALUE = "doubleTapActionValue";
    private static final String SETTING_NAME_HOLD_ACTION = "holdAction";
    private static final String SETTING_NAME_HOLD_ACTION_VALUE = "holdActionValue";
    private static final String SETTING_NAME_ALWAYS_ON = "alwaysOn";
    private static final String SETTING_NAME_IS_RGB = "isRgb";
    private static final String SETTING_NAME_LEFT_POSITION = "leftPosition";
    private static final String SETTING_NAME_IS_FAN_ASSOCIATED = "isFanAssociated";
    private static final String SETTING_NAME_FAN_ENTITY_NAME = "fanEntityName";
    private static final String SETTING_NAME_TOP_POSITION = "topPosition";
    private static final String SETTING_NAME_OPACITY = "opacity";
    private static final String SETTING_NAME_BACKGROUND_COLOR = "backgroundColor";
    private static final String SETTING_NAME_DISPLAY_FURNITURE_CONDITION = "displayFurnitureCondition";
    private static final String SETTING_NAME_DISPLAY_FURNITURE_CONDITION_VALUE = "displayFurnitureConditionValue";

    private static final double FAN_COMBO_VISUAL_WIDTH_PERCENT = 4.0; // Width for the fan visual in a combo
    private List<? extends HomePieceOfFurniture> piecesOfFurniture;
    private String id;
    private String name;
    private Point2d position;
    private int opacity;
    private String backgroundColor;
    private DisplayType displayType;
    private DisplayCondition displayCondition;
    private Action tapAction;
    private String tapActionValue;
    private Action doubleTapAction;
    private String doubleTapActionValue;
    private Action holdAction;
    private String holdActionValue;
    private String title;
    private boolean isLight;
    private boolean alwaysOn;
    private boolean isRgb;
    private boolean isFanAssociated;
    private String fanEntityName;
    private DisplayFurnitureCondition displayFurnitureCondition;
    private String displayFurnitureConditionValue;
    private Map<HomeLight, Float> initialPower;

    private Settings settings;
    private boolean isUserDefinedPosition;
    private PropertyChangeSupport propertyChangeSupport;

    public Entity(Settings settings, List<? extends HomePieceOfFurniture> piecesOfFurniture) {
        this.settings = settings;
        this.piecesOfFurniture = piecesOfFurniture;
        propertyChangeSupport = new PropertyChangeSupport(this);
        initialPower = new HashMap<>();

        loadDefaultAttributes();
    }

    public void move(Vector2d direction) {
        if (isUserDefinedPosition)
            return;
        position.add(direction);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<? extends HomePieceOfFurniture> getPiecesOfFurniture() {
        return piecesOfFurniture;
    }

    public String getTitle() {
        return title;
    }

    public boolean getIsLight() {
        return isLight;
    }

    public DisplayType getDisplayType() {
        return displayType;
    }

    public void setDisplayType(DisplayType displayType) {
        this.displayType = displayType;
        settings.set(name + "." + SETTING_NAME_DISPLAY_TYPE, displayType.name());
    }

    public boolean isDisplayTypeModified() {
        return settings.get(name + "." + SETTING_NAME_DISPLAY_TYPE) != null;
    }

    public DisplayCondition getDisplayCondition() {
        return displayCondition;
    }

    public void setDisplayCondition(DisplayCondition displayCondition) {
        this.displayCondition = displayCondition;
        settings.set(name + "." + SETTING_NAME_DISPLAY_CONDITION, displayCondition.name());
    }

    public boolean isDisplayConditionModified() {
        return settings.get(name + "." + SETTING_NAME_DISPLAY_CONDITION) != null;
    }

    public Action getTapAction() {
        return tapAction;
    }

    public void setTapAction(Action tapAction) {
        this.tapAction = tapAction;
        settings.set(name + "." + SETTING_NAME_TAP_ACTION, tapAction.name());
    }

    public boolean isTapActionModified() {
        return settings.get(name + "." + SETTING_NAME_TAP_ACTION) != null;
    }

    public String getTapActionValue() {
        return tapActionValue;
    }

    public void setTapActionValue(String tapActionValue) {
        this.tapActionValue = tapActionValue;
        settings.set(name + "." + SETTING_NAME_TAP_ACTION_VALUE, tapActionValue);
    }

    public boolean isTapActionValueModified() {
        return settings.get(name + "." + SETTING_NAME_TAP_ACTION_VALUE) != null;
    }

    public Action getDoubleTapAction() {
        return doubleTapAction;
    }

    public void setDoubleTapAction(Action doubleTapAction) {
        this.doubleTapAction = doubleTapAction;
        settings.set(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION, doubleTapAction.name());
    }

    public boolean isDoubleTapActionModified() {
        return settings.get(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION) != null;
    }

    public String getDoubleTapActionValue() {
        return doubleTapActionValue;
    }

    public void setDoubleTapActionValue(String doubleTapActionValue) {
        this.doubleTapActionValue = doubleTapActionValue;
        settings.set(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION_VALUE, doubleTapActionValue);
    }

    public boolean isDoubleTapActionValueModified() {
        return settings.get(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION_VALUE) != null;
    }

    public Action getHoldAction() {
        return holdAction;
    }

    public void setHoldAction(Action holdAction) {
        this.holdAction = holdAction;
        settings.set(name + "." + SETTING_NAME_HOLD_ACTION, holdAction.name());
    }

    public boolean isHoldActionModified() {
        return settings.get(name + "." + SETTING_NAME_HOLD_ACTION) != null;
    }

    public String getHoldActionValue() {
        return holdActionValue;
    }

    public void setHoldActionValue(String holdActionValue) {
        this.holdActionValue = holdActionValue;
        settings.set(name + "." + SETTING_NAME_HOLD_ACTION_VALUE, holdActionValue);
    }

    public boolean isHoldActionValueModified() {
        return settings.get(name + "." + SETTING_NAME_HOLD_ACTION_VALUE) != null;
    }

    public boolean getAlwaysOn() {
        return alwaysOn;
    }

    public void setAlwaysOn(boolean alwaysOn) {
        boolean oldAlwaysOn = this.alwaysOn;
        this.alwaysOn = alwaysOn;
        settings.setBoolean(name + "." + SETTING_NAME_ALWAYS_ON, alwaysOn);
        propertyChangeSupport.firePropertyChange(Property.ALWAYS_ON.name(), oldAlwaysOn, alwaysOn);
    }

    public boolean isAlwaysOnModified() {
        return settings.get(name + "." + SETTING_NAME_ALWAYS_ON) != null;
    }

    public boolean getIsRgb() {
        return isRgb;
    }

    public void setIsRgb(boolean isRgb) {
        boolean oldIsRgb = this.isRgb;
        this.isRgb = isRgb;
        settings.setBoolean(name + "." + SETTING_NAME_IS_RGB, isRgb);
        propertyChangeSupport.firePropertyChange(Property.IS_RGB.name(), oldIsRgb, isRgb);
    }

    public boolean isIsRgbModified() {
        return settings.get(name + "." + SETTING_NAME_IS_RGB) != null;
    }

    public DisplayFurnitureCondition getDisplayFurnitureCondition() {
        return displayFurnitureCondition;
    }

    public void setDisplayFurnitureCondition(DisplayFurnitureCondition displayFurnitureCondition) {
        DisplayFurnitureCondition olddisplayFurnitureCondition = this.displayFurnitureCondition;
        this.displayFurnitureCondition = displayFurnitureCondition;
        settings.set(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION, displayFurnitureCondition.name());
        propertyChangeSupport.firePropertyChange(Property.DISPLAY_FURNITURE_CONDITION.name(), olddisplayFurnitureCondition, displayFurnitureCondition);
    }

    public boolean isDisplayFurnitureConditionModified() {
        return settings.get(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION) != null;
    }

    public String getDisplayFurnitureConditionValue() {
        return displayFurnitureConditionValue;
    }

    public void setDisplayFurnitureConditionValue(String displayFurnitureConditionValue) {
        this.displayFurnitureConditionValue = displayFurnitureConditionValue;
        settings.set(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION_VALUE, displayFurnitureConditionValue);
    }

    public boolean isDisplayFurnitureConditionValueModified() {
        return settings.get(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION_VALUE) != null;
    }

    public Point2d getPosition() {
        return new Point2d(position);
    }

    public void setPosition(Point2d position, boolean savePersistent) {
        if (isUserDefinedPosition && !savePersistent)
            return;

        Point2d oldPosition = getPosition();
        this.position = new Point2d(position);

        if (!savePersistent)
            return;

        settings.setDouble(name + "." + SETTING_NAME_LEFT_POSITION, position.x);
        settings.setDouble(name + "." + SETTING_NAME_TOP_POSITION, position.y);
        isUserDefinedPosition = true;
        propertyChangeSupport.firePropertyChange(Property.POSITION.name(), oldPosition, position);
    }

    public boolean isPositionModified() {
        return settings.get(name + "." + SETTING_NAME_LEFT_POSITION) != null;
    }

    public boolean getIsFanAssociated() {
        return isFanAssociated;
    }

    public void setIsFanAssociated(boolean isFanAssociated) {
        this.isFanAssociated = isFanAssociated;
        settings.setBoolean(name + "." + SETTING_NAME_IS_FAN_ASSOCIATED, isFanAssociated);
    }

    public boolean isFanAssociatedModified() {
        return settings.get(name + "." + SETTING_NAME_IS_FAN_ASSOCIATED) != null;
    }

    public String getFanEntityName() {
        return fanEntityName;
    }

    public void setFanEntityName(String fanEntityName) {
        this.fanEntityName = fanEntityName;
        settings.set(name + "." + SETTING_NAME_FAN_ENTITY_NAME, fanEntityName);
    }

    public int getOpacity() {
        return opacity;
    }

    public void setOpacity(int opacity) {
        this.opacity = opacity;
        settings.setInteger(name + "." + SETTING_NAME_OPACITY, opacity);
    }

    public boolean isOpacityModified() {
        return settings.get(name + "." + SETTING_NAME_OPACITY) != null;
    }

    public String getBackgrounColor() {
        return backgroundColor;
    }

    public void setBackgrounColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        settings.set(name + "." + SETTING_NAME_BACKGROUND_COLOR, backgroundColor);
    }

    public boolean isBackgroundColorModified() {
        return settings.get(name + "." + SETTING_NAME_BACKGROUND_COLOR) != null;
    }

    public void resetToDefaults() {
        boolean oldAlwaysOn = alwaysOn;
        boolean oldIsRgb = isRgb;
        Point2d oldPosition = getPosition();

        settings.set(name + "." + SETTING_NAME_DISPLAY_TYPE, null);
        settings.set(name + "." + SETTING_NAME_DISPLAY_CONDITION, null);
        settings.set(name + "." + SETTING_NAME_TAP_ACTION, null);
        settings.set(name + "." + SETTING_NAME_TAP_ACTION_VALUE, null);
        settings.set(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION, null);
        settings.set(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION_VALUE, null);
        settings.set(name + "." + SETTING_NAME_HOLD_ACTION, null);
        settings.set(name + "." + SETTING_NAME_HOLD_ACTION_VALUE, null);
        settings.set(name + "." + SETTING_NAME_ALWAYS_ON, null);
        settings.set(name + "." + SETTING_NAME_IS_RGB, null);
        settings.set(name + "." + SETTING_NAME_LEFT_POSITION, null);
        settings.set(name + "." + SETTING_NAME_TOP_POSITION, null);
        settings.set(name + "." + SETTING_NAME_IS_FAN_ASSOCIATED, null);
        settings.set(name + "." + SETTING_NAME_FAN_ENTITY_NAME, null);
        settings.set(name + "." + SETTING_NAME_OPACITY, null);
        settings.set(name + "." + SETTING_NAME_BACKGROUND_COLOR, null);
        settings.set(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION, null);
        settings.set(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION_VALUE, null);
        loadDefaultAttributes();

        propertyChangeSupport.firePropertyChange(Property.ALWAYS_ON.name(), oldAlwaysOn, alwaysOn);
        propertyChangeSupport.firePropertyChange(Property.IS_RGB.name(), oldIsRgb, isRgb);
        propertyChangeSupport.firePropertyChange(Property.POSITION.name(), oldPosition, position);
    }

    public void setLightPower(boolean on) {
        if (!isLight)
            return;

        for (Map.Entry<HomeLight, Float> entry : initialPower.entrySet())
            entry.getKey().setPower(on ? entry.getValue() : 0);
    }

    public void restoreConfiguration() {
        setVisible(true);

        if (!isLight)
            return;

        for (Map.Entry<HomeLight, Float> entry : initialPower.entrySet())
            entry.getKey().setPower(entry.getValue());
    }

    public void setVisible(boolean visible) {
        for (HomePieceOfFurniture piece : piecesOfFurniture)
            piece.setVisible(visible);
    }

    private String actionYaml(Action action, String value) {
        final Map<Action, String> actionToYamlString = new HashMap<Action, String>() {{
            put(Action.MORE_INFO, "more-info");
            put(Action.NAVIGATE, "navigate");
            put(Action.NONE, "none");
            put(Action.TOGGLE, "toggle");
            put(Action.CALL_SERVICE, "call-service");
        }};

        String yaml = actionToYamlString.get(action);

        if (action == Action.NAVIGATE)
            yaml += String.format("\n" +
                "      navigation_path: %s", value);

        return yaml;
    }

    private String fanServiceActionYaml() {
        if (!isFanAssociated || !isLight || fanEntityName == null || fanEntityName.trim().isEmpty()) {
            return actionYaml(Action.NONE, ""); // Default to NONE if not configured
        }
        return String.format(Locale.US, 
            "call-service\n" +
            "      service: fan.toggle\n" +
            "      target:\n" +
            "        entity_id: %s", fanEntityName);
    }
    
    private String indentYaml(String yamlString) {
        return yamlString.replaceAll("(?m)^", "    ");
    }

    public String buildYaml() {
        final Map<DisplayType, String> displayTypeToYamlString = new HashMap<DisplayType, String>() {{
            put(DisplayType.BADGE, "state-badge");
            put(DisplayType.ICON, "state-icon");
            put(DisplayType.LABEL, "state-label");
            put(DisplayType.FAN, "image");
        }};

        if (displayCondition == Entity.DisplayCondition.NEVER || getAlwaysOn())
            return "";

        Action actualTapAction = isFanAssociated && isLight ? Action.TOGGLE : tapAction;
        String actualTapActionValue = isFanAssociated && isLight ? "" : tapActionValue;
        Action actualDoubleTapAction = isFanAssociated && isLight ? Action.CALL_SERVICE : doubleTapAction;
        // actualDoubleTapActionValue is handled by fanServiceActionYaml or actionYaml
        Action actualHoldAction = isFanAssociated && isLight ? Action.MORE_INFO : holdAction;
        String actualHoldActionValue = isFanAssociated && isLight ? "" : holdActionValue;

        String entityCoreYaml;
        if (isFanAssociated && isLight) { // This is the "Is Light/Fan Combo"
            // The light entity itself becomes an invisible clickable area
            entityCoreYaml = String.format(Locale.US,
                "  - type: image\n" +
                "    entity: %s\n" + // The light entity
                "    title: null\n" +
                "    tap_action:\n" + // Controls the light
                "      action: toggle\n" +
                "    double_tap_action:\n" + // No double tap specified for combo, set to none
                "      action: none\n" +
                "    hold_action:\n" + // Controls the fan
                "      action: call-service\n" + // Explicitly set action type
                "      service: fan.toggle\n" +   // Service to call
                "      target:\n" +              // Target for the service
                "        entity_id: %s\n" +     // Fan entity ID
                "    state_image:\n" +
                "      \"on\": /local/floorplan/transparent.png\n" +
                "      \"off\": /local/floorplan/transparent.png\n" +
                "    style:\n" +
                "      top: %.2f%%\n" +
                "      left: %.2f%%\n" +
                "      width: %.2f%%\n" +   // Width matches the fan visual's width
                "      transform: translate(-50%%, -50%%)\n" +
                "      background-color: %s\n" + // Keep background and opacity for potential debug
                "      opacity: %d%%\n",
                name, // Light entity name
                fanEntityName, // Pass fanEntityName directly for the target
                position.y, position.x,
                FAN_COMBO_VISUAL_WIDTH_PERCENT, backgroundColor, opacity
            );
        } else if (displayType == DisplayType.FAN) { // This is for non-light entities set to DisplayType.FAN
            entityCoreYaml = String.format(Locale.US,
                "  - type: image\n" +
                "    entity: %s\n" +
                // No title for FAN display type as per example
                "    state_image:\n" +
                "      \"on\": /local/floorplan/animated_fan.gif\n" +
                "      \"off\": /local/floorplan/animated_fan_still.gif\n" +
                "    style:\n" +
                "      top: %.2f%%\n" +
                "      left: %.2f%%\n" +
                "      width: 2.8%%\n" +
                "      color: \"#000\"\n" +
                "      border-radius: 50%%\n" +
                "      text-align: center\n" +
                "      background-color: %s\n" +
                "      opacity: %d%%\n" + // Opacity from entity
                "      font-size: 11px\n" +
                "      font-weight: bold\n" +
                "      transform: translate(-50%%, -50%%)\n",
                name, position.y, position.x, backgroundColor, opacity);
            // Actions are not included for FAN display type
        } else {
            entityCoreYaml = String.format(Locale.US,
                "  - type: %s\n" +
                "    entity: %s\n" +
                "    title: %s\n" +
                "    style:\n" +
                "      top: %.2f%%\n" +
                "      left: %.2f%%\n" +
                "      border-radius: 50%%\n" + // Common style
                "      text-align: center\n" +   // Common style
                "      background-color: %s\n" +
                "      opacity: %d%%\n" +
                "      transform: translate(-50%%, -50%%)\n" + // Added for centering all types
                "    tap_action:\n" +
                "      action: %s\n" +
                "    double_tap_action:\n" +
                "      action: %s\n" +
                "    hold_action:\n" +
                "      action: %s\n",
                displayTypeToYamlString.get(displayType), name, title, position.y, position.x, backgroundColor, opacity,
                actionYaml(actualTapAction, actualTapActionValue),
                isFanAssociated && isLight ? fanServiceActionYaml() : actionYaml(actualDoubleTapAction, doubleTapActionValue),
                actionYaml(actualHoldAction, actualHoldActionValue));
        }

        String fanYaml = "";
        // This is the VISUAL fan part for the "Is Light/Fan Combo"
        if (isFanAssociated && isLight && fanEntityName != null && !fanEntityName.trim().isEmpty()) {
            fanYaml = String.format(Locale.US,
                "  - type: image\n" +
                "    entity: %s\n" +
                "    title: null\n" + 
                "    state_image:\n" +
                "      \"on\": /local/floorplan/animated_fan_grey.gif\n" + // Visual fan when on
                "      \"off\": /local/floorplan/animated_fan_still_grey.gif\n" + // Still visual fan when off
                "    style:\n" +
                "      top: %.2f%%\n" +
                "      left: %.2f%%\n" +
                "      width: %.2f%%\n" + // Use the defined width
                "      transform: translate(-50%%, -50%%)\n" +
                "      pointer-events: none\n",
                fanEntityName, position.y, position.x, FAN_COMBO_VISUAL_WIDTH_PERCENT);
        }


        String combinedYaml = entityCoreYaml + fanYaml;

        if (displayCondition == DisplayCondition.ALWAYS) {
            return combinedYaml;
        }

        return String.format(
            "  - type: conditional\n" +
            "    conditions:\n" +
            "      - condition: state\n" +
            "        entity: %s\n" +
            "        state: '%s'\n" +
            "    elements:\n" +
            "%s",
            name, displayCondition == DisplayCondition.WHEN_ON ? "on" : "off",
            indentYaml(combinedYaml)
        );
    }

    private void saveInitialLightPowerValues() {
        if (!isLight)
            return;

        for (HomePieceOfFurniture piece : piecesOfFurniture) {
            HomeLight light = (HomeLight)piece;
            initialPower.put(light, light.getPower());
        }
    }

    public void addPropertyChangeListener(Property property, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(property.name(), listener);
    }

    public void removePropertyChangeListener(Property property, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(property.name(), listener);
    }

    public String toString() {
        return name;
    }

    private <T extends Enum<T>> T getSavedEnumValue(Class<T> type, String name, T defaultValue) {
        try {
            return Enum.valueOf(type, settings.get(name, defaultValue.name()));
        } catch (IllegalArgumentException e) {
            settings.set(name, null);
        }
        return defaultValue;
    }

    private void loadDefaultAttributes() {
        HomePieceOfFurniture firstPiece = piecesOfFurniture.get(0);
        id = firstPiece.getId();
        name = firstPiece.getName();
        position = loadPosition();
        displayType = getSavedEnumValue(DisplayType.class, name + "." + SETTING_NAME_DISPLAY_TYPE, defaultDisplayType());
        displayCondition = getSavedEnumValue(DisplayCondition.class, name + "." + SETTING_NAME_DISPLAY_CONDITION, DisplayCondition.ALWAYS);
        tapAction = getSavedEnumValue(Action.class, name + "." + SETTING_NAME_TAP_ACTION, defaultAction());
        tapActionValue = settings.get(name + "." + SETTING_NAME_TAP_ACTION_VALUE, "");
        doubleTapAction = getSavedEnumValue(Action.class, name + "." + SETTING_NAME_DOUBLE_TAP_ACTION, Action.NONE);
        doubleTapActionValue = settings.get(name + "." + SETTING_NAME_DOUBLE_TAP_ACTION_VALUE, "");
        holdAction = getSavedEnumValue(Action.class, name + "." + SETTING_NAME_HOLD_ACTION, Action.MORE_INFO);
        holdActionValue = settings.get(name + "." + SETTING_NAME_HOLD_ACTION_VALUE, "");
        isFanAssociated = settings.getBoolean(name + "." + SETTING_NAME_IS_FAN_ASSOCIATED, false);
        fanEntityName = settings.get(name + "." + SETTING_NAME_FAN_ENTITY_NAME, "");
        title = firstPiece.getDescription();
        opacity = settings.getInteger(name + "." + SETTING_NAME_OPACITY, 100);
        backgroundColor = settings.get(name + "." + SETTING_NAME_BACKGROUND_COLOR, "rgba(255, 255, 255, 0.3)");
        alwaysOn = settings.getBoolean(name + "." + SETTING_NAME_ALWAYS_ON, false);
        isRgb = settings.getBoolean(name + "." + SETTING_NAME_IS_RGB, false);
        displayFurnitureCondition = getSavedEnumValue(DisplayFurnitureCondition.class, name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION, DisplayFurnitureCondition.ALWAYS);
        displayFurnitureConditionValue = settings.get(name + "." + SETTING_NAME_DISPLAY_FURNITURE_CONDITION_VALUE, "");

        isLight = firstPiece instanceof HomeLight;
        saveInitialLightPowerValues();
    }
    
    // Default display type logic remains unchanged for now. 
    // If fan. entities should default to DisplayType.FAN, this would need adjustment.
    private DisplayType defaultDisplayType() {
        return name.startsWith("sensor.") ? DisplayType.LABEL : DisplayType.ICON;
    }

    public int compareTo(Entity other) {
        return getName().compareTo(other.getName());
    }

    private Action defaultAction() {
        String[] actionableEntityPrefixes = {
            "alarm_control_panel.",
            "button.",
            "climate.",
            "cover.",
            "fan.",
            "humidifier.",
            "lawn_mower.",
            "light.",
            "lock.",
            "media_player.",
            "switch.",
            "vacuum.",
            "valve.",
            "water_header.",
        };

        for (String prefix : actionableEntityPrefixes ) {
            if (name.startsWith(prefix))
                return Action.TOGGLE;
        }
        return Action.MORE_INFO;
    }

    private Point2d loadPosition() {
        double leftPosition = settings.getDouble(name + "." + SETTING_NAME_LEFT_POSITION, -1);
        double topPosition = settings.getDouble(name + "." + SETTING_NAME_TOP_POSITION, -1);
        if (leftPosition != -1 || topPosition != -1) {
            isUserDefinedPosition = true;
            return new Point2d(leftPosition, topPosition);
        }

        isUserDefinedPosition = false;
        return new Point2d(0, 0);
    }
}
