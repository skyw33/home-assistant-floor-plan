package com.shmuelzon.HomeAssistantFloorPlan;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.io.IOException;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;


public class Entity implements Comparable<Entity> {
    public enum Property {ALWAYS_ON, IS_RGB, POSITION, SCALE_FACTOR, DISPLAY_CONDITION, FURNITURE_DISPLAY_CONDITION} // Added DISPLAY_CONDITION & FURNITURE_DISPLAY_CONDITION
    public enum DisplayType {BADGE, ICON, LABEL, ICON_AND_ANIMATED_FAN}
    public enum ClickableAreaType { ENTITY_SIZE, ROOM_SIZE }
    public enum FanSize {SMALL, MEDIUM, LARGE} // Added FanSize enum
    public enum FanColor {THREE_BLADE_CEILING_BLACK, THREE_BLADE_CEILING_WHITE, FOUR_BLADE_CEILING_BLACK, FOUR_BLADE_CEILING_WHITE, FOUR_BLADE_PORTABLE_BLACK, FOUR_BLADE_PORTABLE_WHITE}
    public enum Action {MORE_INFO, NAVIGATE, NONE, TOGGLE, TOGGLE_FAN}

    // --- NEW: Enum for the different operators ---
    public enum DisplayOperator { IS, IS_NOT, GREATER_THAN, LESS_THAN, ALWAYS, NEVER }

    // --- Settings Constants ---
    private static final String SETTING_NAME_DISPLAY_TYPE = "displayType";
    private static final String SETTING_NAME_DISPLAY_OPERATOR = "displayOperator";
    private static final String SETTING_NAME_DISPLAY_VALUE = "displayValue";
    private static final String SETTING_NAME_FURNITURE_DISPLAY_OPERATOR = "furnitureDisplayOperator";
    private static final String SETTING_NAME_FURNITURE_DISPLAY_VALUE = "furnitureDisplayValue";
    
    private static final String SETTING_NAME_TAP_ACTION = "tapAction";
    private static final String SETTING_NAME_TAP_ACTION_VALUE = "tapActionValue";
    private static final String SETTING_NAME_DOUBLE_TAP_ACTION = "doubleTapAction";
    private static final String SETTING_NAME_DOUBLE_TAP_ACTION_VALUE = "doubleTapActionValue";
    private static final String SETTING_NAME_HOLD_ACTION = "holdAction";
    private static final String SETTING_NAME_HOLD_ACTION_VALUE = "holdActionValue";
    private static final String SETTING_NAME_ALWAYS_ON = "alwaysOn";
    private static final String SETTING_NAME_IS_RGB = "isRgb";
    private static final String SETTING_NAME_LEFT_POSITION = "leftPosition";
    private static final String SETTING_NAME_TOP_POSITION = "topPosition";
    private static final String SETTING_NAME_BLINKING = "blinking";
    private static final String SETTING_NAME_OPACITY = "opacity";
    private static final String SETTING_NAME_BACKGROUND_COLOR = "backgroundColor";
    private static final String SETTING_NAME_SCALE_FACTOR = "scaleFactor"; // Added missing constant
    private static final String SETTING_NAME_CLICKABLE_AREA_TYPE = "clickableAreaType";
    private static final String SETTING_NAME_EXCLUDE_FROM_OVERLAP = "excludeFromOverlap";
    private static final String SETTING_NAME_ASSOCIATED_FAN_ENTITY_ID = "associatedFanEntityId";
    private static final String SETTING_NAME_FAN_COLOR = "fanColor";
    private static final String SETTING_NAME_SHOW_FAN_WHEN_OFF = "showFanWhenOff";
    private static final String SETTING_NAME_FAN_SIZE = "fanSize"; // Added FanSize setting constant
    private static final String SETTING_NAME_FAN_OPACITY = "fanOpacity";
    private static final String SETTING_NAME_SHOW_BORDER_AND_BACKGROUND = "showBorderAndBackground";
    private static final String SETTING_NAME_LABEL_COLOR = "labelColor";
    private static final String SETTING_NAME_LABEL_TEXT_SHADOW = "labelTextShadow";
    private static final String SETTING_NAME_LABEL_FONT_WEIGHT = "labelFontWeight";
    private static final String SETTING_NAME_LABEL_SUFFIX = "labelSuffix";
    private static final String SETTING_NAME_ICON_SHADOW = "iconShadow";

    // --- Fields ---
    private List<? extends HomePieceOfFurniture> piecesOfFurniture;
    private String id;
    private String name;
    private String attribute;
    private Point2d position;
    private boolean blinking;
    private int opacity;
    private double scaleFactor;
    private String backgroundColor;
    private DisplayType displayType;
    private ClickableAreaType clickableAreaType;
    private DisplayOperator displayOperator;
    private String displayValue;
    private DisplayOperator furnitureDisplayOperator;
    private String furnitureDisplayValue;
    private Action tapAction;
    private String tapActionValue;
    private Action doubleTapAction;
    private String doubleTapActionValue;
    private Action holdAction;
    private String holdActionValue;
    private String title;
    private boolean isLight;
    private String associatedFanEntityId;
    private FanColor fanColor;
    private boolean showFanWhenOff;
    private FanSize fanSize; // Added FanSize field
    private int fanOpacity;
    private boolean showBorderAndBackground;
    private boolean alwaysOn;
    private boolean isRgb;
    private Map<HomeLight, Float> initialPower;
    private Settings settings;
    private boolean isUserDefinedPosition;
    private PropertyChangeSupport propertyChangeSupport;
    private double defaultIconBadgeBaseSizePercent;
    private String labelColor;
    private String labelTextShadow;
    private String labelFontWeight;
    private String labelSuffix;
    private boolean excludeFromOverlap;
    private String iconShadow;

    public Entity(Settings settings, List<? extends HomePieceOfFurniture> piecesOfFurniture, ResourceBundle resourceBundle) {
        this.settings = settings;
        this.piecesOfFurniture = piecesOfFurniture;
        propertyChangeSupport = new PropertyChangeSupport(this);
        initialPower = new HashMap<>();

        try {
            String sizeStr = resourceBundle.getString("HomeAssistantFloorPlan.Entity.defaultIconBadgeBaseSizePercent");
            this.defaultIconBadgeBaseSizePercent = Double.parseDouble(sizeStr);
        } catch (MissingResourceException | NumberFormatException | NullPointerException e) {
            // Log a warning or use a hardcoded default if the resource bundle or key is not found, or if parsing fails
            System.err.println("Warning: Could not load 'HomeAssistantFloorPlan.Entity.defaultIconBadgeBaseSizePercent' from properties. Using hardcoded default (5.0%). " + e.getMessage());
            this.defaultIconBadgeBaseSizePercent = 5.0; // Hardcoded fallback
        }

        loadDefaultAttributes();
    }

    // Helper method to create unique setting keys
    private String getSettingKey(String settingSuffix) {
        // this.name is the HA entity name (e.g., light.living_room)
        // this.id is the SH3D piece ID (e.g., "obj123")
        return this.name + "_" + this.id + "." + settingSuffix;
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

    public String getAttribute() {
        return attribute;
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
        settings.set(getSettingKey(SETTING_NAME_DISPLAY_TYPE), displayType.name());
    }

    public boolean isDisplayTypeModified() {
        return settings.get(getSettingKey(SETTING_NAME_DISPLAY_TYPE)) != null;
    }
    
    public DisplayOperator getDisplayOperator() {
        return displayOperator;
    }

    public void setDisplayOperator(DisplayOperator displayOperator) {
        this.displayOperator = displayOperator;
        settings.set(getSettingKey(SETTING_NAME_DISPLAY_OPERATOR), displayOperator.name());
    }
    
    public String getDisplayValue() {
        return displayValue;
    }

    public void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
        settings.set(getSettingKey(SETTING_NAME_DISPLAY_VALUE), displayValue);
    }
    
    public boolean isDisplayConditionModified() {
        return settings.get(getSettingKey(SETTING_NAME_DISPLAY_OPERATOR)) != null
            || settings.get(getSettingKey(SETTING_NAME_DISPLAY_VALUE)) != null;
    }

    public DisplayOperator getFurnitureDisplayOperator() {
        return furnitureDisplayOperator;
    }

    public void setFurnitureDisplayOperator(DisplayOperator furnitureDisplayOperator) {
        this.furnitureDisplayOperator = furnitureDisplayOperator;
        settings.set(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_OPERATOR), furnitureDisplayOperator.name());
        propertyChangeSupport.firePropertyChange(Property.FURNITURE_DISPLAY_CONDITION.name(), null, furnitureDisplayOperator);
    }

    public String getFurnitureDisplayValue() {
        return furnitureDisplayValue;
    }

    public void setFurnitureDisplayValue(String furnitureDisplayValue) {
        this.furnitureDisplayValue = furnitureDisplayValue;
        settings.set(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_VALUE), furnitureDisplayValue);
        propertyChangeSupport.firePropertyChange(Property.FURNITURE_DISPLAY_CONDITION.name(), null, furnitureDisplayValue);
    }
    
    public boolean isFurnitureDisplayConditionModified() {
        return settings.get(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_OPERATOR)) != null
            || settings.get(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_VALUE)) != null;
    }

    public Action getTapAction() {
        return tapAction;
    }

    public void setTapAction(Action tapAction) {
        this.tapAction = tapAction;
        settings.set(getSettingKey(SETTING_NAME_TAP_ACTION), tapAction.name());
    }

    public boolean isTapActionModified() {
        return settings.get(getSettingKey(SETTING_NAME_TAP_ACTION)) != null;
    }

    public String getTapActionValue() {
        return tapActionValue;
    }

    public void setTapActionValue(String tapActionValue) {
        this.tapActionValue = tapActionValue;
        settings.set(getSettingKey(SETTING_NAME_TAP_ACTION_VALUE), tapActionValue);
    }

    public boolean isTapActionValueModified() {
        return settings.get(getSettingKey(SETTING_NAME_TAP_ACTION_VALUE)) != null;
    }

    public Action getDoubleTapAction() {
        return doubleTapAction;
    }

    public void setDoubleTapAction(Action doubleTapAction) {
        this.doubleTapAction = doubleTapAction;
        settings.set(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION), doubleTapAction.name());
    }

    public boolean isDoubleTapActionModified() {
        return settings.get(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION)) != null;
    }

    public String getDoubleTapActionValue() {
        return doubleTapActionValue;
    }

    public void setDoubleTapActionValue(String doubleTapActionValue) {
        this.doubleTapActionValue = doubleTapActionValue;
        settings.set(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION_VALUE), doubleTapActionValue);
    }

    public boolean isDoubleTapActionValueModified() {
        return settings.get(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION_VALUE)) != null;
    }

    public Action getHoldAction() {
        return holdAction;
    }

    public void setHoldAction(Action holdAction) {
        this.holdAction = holdAction;
        settings.set(getSettingKey(SETTING_NAME_HOLD_ACTION), holdAction.name());
    }

    public boolean isHoldActionModified() {
        return settings.get(getSettingKey(SETTING_NAME_HOLD_ACTION)) != null;
    }

    public String getHoldActionValue() {
        return holdActionValue;
    }

    public void setHoldActionValue(String holdActionValue) {
        this.holdActionValue = holdActionValue;
        settings.set(getSettingKey(SETTING_NAME_HOLD_ACTION_VALUE), holdActionValue);
    }

    public boolean isHoldActionValueModified() {
        return settings.get(getSettingKey(SETTING_NAME_HOLD_ACTION_VALUE)) != null;
    }

    public String getAssociatedFanEntityId() {
        return associatedFanEntityId;
    }

    public void setAssociatedFanEntityId(String associatedFanEntityId) {
        this.associatedFanEntityId = associatedFanEntityId;
        settings.set(getSettingKey(SETTING_NAME_ASSOCIATED_FAN_ENTITY_ID), associatedFanEntityId);
    }

    public boolean isAssociatedFanEntityIdModified() {
        return settings.get(getSettingKey(SETTING_NAME_ASSOCIATED_FAN_ENTITY_ID)) != null;
    }

    public boolean getShowFanWhenOff() {
        return showFanWhenOff;
    }

    public void setShowFanWhenOff(boolean showFanWhenOff) {
        this.showFanWhenOff = showFanWhenOff;
        settings.setBoolean(getSettingKey(SETTING_NAME_SHOW_FAN_WHEN_OFF), showFanWhenOff);
    }

    public boolean isShowFanWhenOffModified() {
        return settings.get(getSettingKey(SETTING_NAME_SHOW_FAN_WHEN_OFF)) != null;
    }

    public FanColor getFanColor() {
        return fanColor;
    }

    public void setFanColor(FanColor fanColor) {
        this.fanColor = fanColor;
        settings.set(getSettingKey(SETTING_NAME_FAN_COLOR), fanColor.name());
    }

    public boolean isFanColorModified() {
        return settings.get(getSettingKey(SETTING_NAME_FAN_COLOR)) != null;
    }

    public boolean getShowBorderAndBackground() {
        return showBorderAndBackground;
    }

    public void setShowBorderAndBackground(boolean showBorderAndBackground) {
        this.showBorderAndBackground = showBorderAndBackground;
        settings.setBoolean(getSettingKey(SETTING_NAME_SHOW_BORDER_AND_BACKGROUND), showBorderAndBackground);
    }

    public boolean isShowBorderAndBackgroundModified() {
        return settings.get(getSettingKey(SETTING_NAME_SHOW_BORDER_AND_BACKGROUND)) != null;
    }

    public boolean getAlwaysOn() {
        return alwaysOn;
    }

    public void setAlwaysOn(boolean alwaysOn) {
        boolean oldAlwaysOn = this.alwaysOn;
        this.alwaysOn = alwaysOn;
        settings.setBoolean(getSettingKey(SETTING_NAME_ALWAYS_ON), alwaysOn);
        propertyChangeSupport.firePropertyChange(Property.ALWAYS_ON.name(), oldAlwaysOn, alwaysOn);
    }

    public boolean isAlwaysOnModified() {
        return settings.get(getSettingKey(SETTING_NAME_ALWAYS_ON)) != null;
    }

    public boolean getIsRgb() {
        return isRgb;
    }

    public void setIsRgb(boolean isRgb) {
        boolean oldIsRgb = this.isRgb;
        this.isRgb = isRgb;
        settings.setBoolean(getSettingKey(SETTING_NAME_IS_RGB), isRgb);
        propertyChangeSupport.firePropertyChange(Property.IS_RGB.name(), oldIsRgb, isRgb);
    }

    public boolean isIsRgbModified() {
        return settings.get(getSettingKey(SETTING_NAME_IS_RGB)) != null;
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

        settings.setDouble(getSettingKey(SETTING_NAME_LEFT_POSITION), position.x);
        settings.setDouble(getSettingKey(SETTING_NAME_TOP_POSITION), position.y);
        isUserDefinedPosition = true;
        propertyChangeSupport.firePropertyChange(Property.POSITION.name(), oldPosition, position);
    }

    public boolean isPositionModified() {
        return settings.get(getSettingKey(SETTING_NAME_LEFT_POSITION)) != null;
    }

    public boolean getBlinking() {
        return blinking;
    }

    public void setBlinking(boolean blinking) {
        this.blinking = blinking;
        settings.setBoolean(getSettingKey(SETTING_NAME_BLINKING), blinking);
    }

    public boolean isBlinkingModified() {
        return settings.get(getSettingKey(SETTING_NAME_BLINKING)) != null;
    }
    public int getOpacity() {
        return opacity;
    }

    public void setOpacity(int opacity) {
        this.opacity = opacity;
        settings.setInteger(getSettingKey(SETTING_NAME_OPACITY), opacity);
    }

    public boolean isOpacityModified() {
        return settings.get(getSettingKey(SETTING_NAME_OPACITY)) != null;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(double scaleFactor) {
        double oldScaleFactor = this.scaleFactor;
        this.scaleFactor = scaleFactor;
        settings.setDouble(getSettingKey(SETTING_NAME_SCALE_FACTOR), scaleFactor);
        propertyChangeSupport.firePropertyChange(Property.SCALE_FACTOR.name(), oldScaleFactor, scaleFactor);
    }

    public boolean isExcludedFromOverlap() {
        return excludeFromOverlap;
    }

    public void setExcludeFromOverlap(boolean excludeFromOverlap) {
        this.excludeFromOverlap = excludeFromOverlap;
        settings.setBoolean(getSettingKey(SETTING_NAME_EXCLUDE_FROM_OVERLAP), excludeFromOverlap);
    }

    public String getIconShadow() {
        return iconShadow;
    }

    public void setIconShadow(String iconShadow) {
        this.iconShadow = iconShadow;
        settings.set(getSettingKey(SETTING_NAME_ICON_SHADOW), iconShadow);
    }

    public boolean isIconShadowModified() {
        // Check if the setting exists, which implies it has been modified from the default.
        // The default is not stored, so a non-null value means it's been set.
        return settings.get(getSettingKey(SETTING_NAME_ICON_SHADOW)) != null;
    }


    public boolean isScaleFactorModified() {
        return settings.get(getSettingKey(SETTING_NAME_SCALE_FACTOR)) != null;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        settings.set(getSettingKey(SETTING_NAME_BACKGROUND_COLOR), backgroundColor);
    }

    public boolean isBackgroundColorModified() {
        return settings.get(getSettingKey(SETTING_NAME_BACKGROUND_COLOR)) != null;
    }

    public ClickableAreaType getClickableAreaType() {
        return clickableAreaType;
    }

    public void setClickableAreaType(ClickableAreaType clickableAreaType) {
        this.clickableAreaType = clickableAreaType;
        settings.set(getSettingKey(SETTING_NAME_CLICKABLE_AREA_TYPE), clickableAreaType.name());
    }

    public boolean isClickableAreaTypeModified() {
        return settings.get(getSettingKey(SETTING_NAME_CLICKABLE_AREA_TYPE)) != null;
    }

    public FanSize getFanSize() {
        return fanSize;
    }

    public void setFanSize(FanSize fanSize) {
        this.fanSize = fanSize;
        settings.set(getSettingKey(SETTING_NAME_FAN_SIZE), fanSize.name());
    }

    public boolean isFanSizeModified() {
        return settings.get(getSettingKey(SETTING_NAME_FAN_SIZE)) != null;
    }

    public int getFanOpacity() {
        return fanOpacity;
    }

    public void setFanOpacity(int fanOpacity) {
        this.fanOpacity = fanOpacity;
        settings.setInteger(getSettingKey(SETTING_NAME_FAN_OPACITY), fanOpacity);
    }

    public boolean isFanOpacityModified() {
        return settings.get(getSettingKey(SETTING_NAME_FAN_OPACITY)) != null;
    }

    public double getDefaultIconBadgeBaseSizePercent() {
        return this.defaultIconBadgeBaseSizePercent;
    }


    public String getLabelColor() {
        return labelColor;
    }

    public void setLabelColor(String labelColor) {
        this.labelColor = labelColor;
        settings.set(getSettingKey(SETTING_NAME_LABEL_COLOR), labelColor);
    }

    public boolean isLabelColorModified() {
        return settings.get(getSettingKey(SETTING_NAME_LABEL_COLOR)) != null;
    }

    public String getLabelTextShadow() {
        return labelTextShadow;
    }

    public void setLabelTextShadow(String labelTextShadow) {
        this.labelTextShadow = labelTextShadow;
        settings.set(getSettingKey(SETTING_NAME_LABEL_TEXT_SHADOW), labelTextShadow);
    }

    public boolean isLabelTextShadowModified() {
        return settings.get(getSettingKey(SETTING_NAME_LABEL_TEXT_SHADOW)) != null;
    }

    public String getLabelFontWeight() {
        return labelFontWeight;
    }

    public void setLabelFontWeight(String labelFontWeight) {
        this.labelFontWeight = labelFontWeight;
        settings.set(getSettingKey(SETTING_NAME_LABEL_FONT_WEIGHT), labelFontWeight);
    }

    public boolean isLabelFontWeightModified() {
        return settings.get(getSettingKey(SETTING_NAME_LABEL_FONT_WEIGHT)) != null;
    }

    public String getLabelSuffix() {
        return labelSuffix;
    }

    public void setLabelSuffix(String labelSuffix) {
        this.labelSuffix = labelSuffix;
        settings.set(getSettingKey(SETTING_NAME_LABEL_SUFFIX), labelSuffix);
    }

    public boolean isLabelSuffixModified() {
        return settings.get(getSettingKey(SETTING_NAME_LABEL_SUFFIX)) != null;
    }

    public void resetToDefaults() {
        boolean oldAlwaysOn = alwaysOn;
        boolean oldIsRgb = isRgb;
        Point2d oldPosition = getPosition();

        double oldScaleFactor = scaleFactor; // Store old scaleFactor

        settings.set(getSettingKey(SETTING_NAME_DISPLAY_TYPE), null);
        settings.set(getSettingKey(SETTING_NAME_DISPLAY_OPERATOR), null);
        settings.set(getSettingKey(SETTING_NAME_DISPLAY_VALUE), null);
        settings.set(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_OPERATOR), null);
        settings.set(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_VALUE), null);
        settings.set(getSettingKey(SETTING_NAME_TAP_ACTION), null);
        settings.set(getSettingKey(SETTING_NAME_TAP_ACTION_VALUE), null);
        settings.set(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION), null);
        settings.set(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION_VALUE), null);
        settings.set(getSettingKey(SETTING_NAME_HOLD_ACTION), null);
        settings.set(getSettingKey(SETTING_NAME_HOLD_ACTION_VALUE), null);
        settings.set(getSettingKey(SETTING_NAME_ALWAYS_ON), null);
        settings.set(getSettingKey(SETTING_NAME_IS_RGB), null);
        settings.set(getSettingKey(SETTING_NAME_LEFT_POSITION), null);
        settings.set(getSettingKey(SETTING_NAME_TOP_POSITION), null);
        settings.set(getSettingKey(SETTING_NAME_BLINKING), null);
        settings.set(getSettingKey(SETTING_NAME_OPACITY), null);
        settings.set(getSettingKey(SETTING_NAME_BACKGROUND_COLOR), null);
        settings.set(getSettingKey(SETTING_NAME_SCALE_FACTOR), null);
        settings.set(getSettingKey(SETTING_NAME_CLICKABLE_AREA_TYPE), null); // Reset clickable area type
        settings.set(getSettingKey(SETTING_NAME_ASSOCIATED_FAN_ENTITY_ID), null);
        settings.set(getSettingKey(SETTING_NAME_SHOW_FAN_WHEN_OFF), null);
        settings.set(getSettingKey(SETTING_NAME_FAN_COLOR), null);
        settings.set(getSettingKey(SETTING_NAME_FAN_SIZE), null); // Reset FanSize
        settings.set(getSettingKey(SETTING_NAME_FAN_OPACITY), null);
        settings.set(getSettingKey(SETTING_NAME_SHOW_BORDER_AND_BACKGROUND), null);
        settings.set(getSettingKey(SETTING_NAME_LABEL_COLOR), null);
        settings.set(getSettingKey(SETTING_NAME_LABEL_TEXT_SHADOW), null);
        settings.set(getSettingKey(SETTING_NAME_LABEL_FONT_WEIGHT), null);
        settings.set(getSettingKey(SETTING_NAME_LABEL_SUFFIX), null);
        settings.set(getSettingKey(SETTING_NAME_EXCLUDE_FROM_OVERLAP), null);
        settings.set(getSettingKey(SETTING_NAME_ICON_SHADOW), null);
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

    private String actionYaml(Action action, String value, String fanEntityId) {
        final Map<Action, String> actionToYamlString = new HashMap<Action, String>() {{
            put(Action.MORE_INFO, "more-info");
            put(Action.NAVIGATE, "navigate");
            put(Action.NONE, "none");
            put(Action.TOGGLE, "toggle");
            put(Action.TOGGLE_FAN, "call-service");
        }};

        String yaml = actionToYamlString.get(action);

        if (action == Action.NAVIGATE)
            yaml += String.format("\n" +
                "        navigation_path: %s", value);
        else if (action == Action.TOGGLE_FAN) {
            yaml += String.format("\n" +
                "      service: fan.toggle\n" +
                "      target:\n" +
                "        entity_id: %s", fanEntityId != null ? fanEntityId : "");
        }

        return yaml;
    }

    public List<String> buildYaml(Controller controller) { // Pass controller to get room bounds
        final Map<DisplayType, String> displayTypeToYamlString = new HashMap<DisplayType, String>() {{
            put(DisplayType.BADGE, "state-badge");
            put(DisplayType.ICON, "state-icon");
            put(DisplayType.LABEL, "state-label");
        }};

        // Calculate the final size for icons/badges as a percentage of the card's dimensions.
        // This is done by taking a base percentage and multiplying it by the user-defined scaleFactor.
        // This makes the icons responsive to the viewing size (desktop vs. mobile).
        double scaledIconBadgeSizePercent = defaultIconBadgeBaseSizePercent * scaleFactor;


        // Use the new displayOperator logic
        // If the entity is configured to never be displayed, or is an "always on" light (which has no icon)
        if (this.displayOperator == DisplayOperator.NEVER || getAlwaysOn()) {
            return new ArrayList<>(); // Return empty list if never displayed
        }

        List<String> elements = new ArrayList<>(); // This will hold all generated YAML elements for this entity
        List<String> conditionalElements = new ArrayList<>(); // Elements that go inside a conditional block

         //Determine if this entity needs a separate background element for its border/background
        boolean needsSeparateBackground = (displayType == DisplayType.ICON || displayType == DisplayType.BADGE || displayType == DisplayType.ICON_AND_ANIMATED_FAN) && this.showBorderAndBackground;

        if (displayType == DisplayType.ICON_AND_ANIMATED_FAN) {
             //--- Generate Background/Border Element if needed (for ICON_AND_ANIMATED_FAN) ---
            if (needsSeparateBackground) {
                conditionalElements.add(generateBackgroundElementYaml(position, scaleFactor, backgroundColor, tapAction, tapActionValue, doubleTapAction, doubleTapActionValue, holdAction, holdActionValue, associatedFanEntityId, name, id, this.blinking, this.opacity));
            }

            // --- Generate the Icon part of ICON_AND_ANIMATED_FAN ---
            StringBuilder iconStyleProperties = new StringBuilder();
            iconStyleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", position.y));
            iconStyleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", position.x));
            iconStyleProperties.append("      position: absolute\n"); // Ensure absolute positioning
            iconStyleProperties.append("      transform: translate(-50%, -50%)\n");

            // Set a responsive font-size, which will serve as the base for scaling all em-based units.
            // Using calc() adds a fixed base size to the scalable vw unit. This prevents icons from
            // becoming too small on narrow screens while still allowing them to grow on wider screens.
            double iconSizeVw = 2.0 * scaleFactor;
            double iconSizePx = 10.0 * scaleFactor;
            iconStyleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", iconSizeVw, iconSizePx));
            
            // Icon part is not clickable if background handles it, otherwise it should be clickable
            if (needsSeparateBackground) {
                iconStyleProperties.append("      pointer-events: none\n");
            }

            // No background/border styling here, as it's handled by the separate image element
            if (blinking) {
                iconStyleProperties.append("      animation: my-blink 1s linear infinite\n");
            } else {
                iconStyleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", opacity));
            }

            String iconElementYaml;
            String attributePart = (this.attribute != null && !this.attribute.isEmpty() ? "    attribute: " + this.attribute + "\n" : "");
            String titlePart = "    title: " + (title != null ? title : "null") + "\n";

            if (needsSeparateBackground) {
                iconElementYaml = String.format(Locale.US,
                    "  - type: state-icon\n" +
                    "    entity: %s\n" +
                    attributePart +
                    titlePart +
                    "    style:\n" +
                    "%s",
                    name,
                    iconStyleProperties.toString());
            } else {
                iconElementYaml = String.format(Locale.US,
                    "  - type: state-icon\n" +
                    "    entity: %s\n" +
                    attributePart +
                    titlePart +
                    "    tap_action:\n" +
                    "      action: %s\n" +
                    "    double_tap_action:\n" +
                    "      action: %s\n" +
                    "    hold_action:\n" +
                    "      action: %s\n" +
                    "    style:\n" +
                    "%s",
                    name,
                    actionYaml(tapAction, tapActionValue, this.associatedFanEntityId),
                    actionYaml(doubleTapAction, doubleTapActionValue, this.associatedFanEntityId),
                    actionYaml(holdAction, holdActionValue, this.associatedFanEntityId),
                    iconStyleProperties.toString());
            }

            // --- Generate the Fan Image part of ICON_AND_ANIMATED_FAN using standard conditional elements ---
            String fanImage;
            switch (this.fanColor) {
                case THREE_BLADE_CEILING_BLACK:
                    fanImage = "/local/floorplan/3_blade_black.png";
                    break;
                case THREE_BLADE_CEILING_WHITE:
                    fanImage = "/local/floorplan/3_blade_grey.png";
                    break;
                case FOUR_BLADE_CEILING_BLACK:
                    fanImage = "/local/floorplan/fan_blades_black.png";
                    break;
                case FOUR_BLADE_CEILING_WHITE:
                    fanImage = "/local/floorplan/fan_blades_grey.png";
                    break;
                case FOUR_BLADE_PORTABLE_BLACK:
                    fanImage = "/local/floorplan/mdi_fan_black.png";
                    break;
                case FOUR_BLADE_PORTABLE_WHITE:
                    fanImage = "/local/floorplan/mdi_fan_grey.png";
                    break;
                default: // Fallback
                    fanImage = "/local/floorplan/fan_blades_black.png";
                    break;
            }
 
            double fanSizePercent; // Use a single variable for square aspect ratio
            switch (this.fanSize) {
                case SMALL:  fanSizePercent = 3.0; break; // Use the larger dimension for square
                case MEDIUM: fanSizePercent = 5.0; break; // Use the larger dimension for square
                case LARGE:  fanSizePercent = 7.0; break; // Use the larger dimension for square
                default:     fanSizePercent = 5.0; break; // Default to Medium (5.0%)
            }
            // Apply scaleFactor to the chosen size
            fanSizePercent *= scaleFactor; // Apply scale factor to fan image dimensions

            if (this.associatedFanEntityId != null && !this.associatedFanEntityId.trim().isEmpty()) {
                // Base style properties used by both 'on' and 'off' states.
                // The transform property is handled separately for each state.
                // Moved transform: translate(-50%, -50%) to baseStyle for consistent centering of the element's bounding box.
                // FAN_OFFSET_X/Y removed as per user request to rely solely on aspect ratio.
                String baseStyle = String.format(Locale.US, // Increased precision to .4f, added translateZ(0) and removed trailing semicolons
                    "          top: %.4f%%\n" +
                    "          left: %.4f%%\n" +
                    "          width: %.4f%%\n" + // Set width based on fanSizePercent
                    "          aspect-ratio: 1 / 1\n" + // Force a square aspect ratio, browser will calculate height
                    "          transform: translate(-50%%, -50%%) translateZ(0)\n" + // Ensure it's always centered and hardware accelerated
                    "          pointer-events: none\n" +
                    "          will-change: transform\n", // Hint to browser for smoother animation
                    position.y, position.x, fanSizePercent); // Only width is needed, height derived from aspect-ratio

                // --- Element for 'on' state (spinning) ---
                String onStateStyle = baseStyle + String.format(Locale.US,
                    "          animation: spin 1.5s linear infinite\n" +
                    "          opacity: %.2f\n",
                    (double)this.fanOpacity / 100.0);

                String fanOnElementYaml = String.format(Locale.US,
                    "  - type: conditional\n" +
                    "    conditions:\n" +
                    "      - entity: %s\n" +
                    "        state: \"on\"\n" +
                    "    elements:\n" +
                    "      - type: image\n" +
                    "        image: %s\n" +
                    "        style:\n" +
                    "%s",
                    this.associatedFanEntityId, fanImage, onStateStyle);
                conditionalElements.add(fanOnElementYaml);

                // --- Element for 'off' state (still) ---
                if (this.showFanWhenOff) {
                    String offStateStyle = baseStyle + String.format(Locale.US,
                        "          animation: none\n" +
                        "          opacity: %.2f\n",
                        (double)this.fanOpacity / 100.0);

                    String fanOffElementYaml = String.format(Locale.US,
                        "  - type: conditional\n" +
                        "    conditions:\n" +
                        "      - entity: %s\n" +
                        "        state: \"off\"\n" +
                        "    elements:\n" +
                        "      - type: image\n" +
                        "        image: %s\n" +
                        "        style:\n" +
                        "%s",
                        this.associatedFanEntityId, fanImage, offStateStyle);
                    conditionalElements.add(fanOffElementYaml);
                }
            }
            // The fan image should come before the icon in YAML for proper layering
            // (icon on top of fan).
            conditionalElements.add(iconElementYaml); // Icon is layered on top of fan
        } else { // Not ICON_AND_ANIMATED_FAN
            // --- Generate Background/Border Element if needed (for ICON or BADGE) ---
            if (needsSeparateBackground) {
                conditionalElements.add(generateBackgroundElementYaml(position, scaleFactor, backgroundColor, tapAction, tapActionValue, doubleTapAction, doubleTapActionValue, holdAction, holdActionValue, associatedFanEntityId, name, id, this.blinking, this.opacity));
            }

            // --- Generate the main visual element (Icon, Badge, or Label) ---
            StringBuilder styleProperties = new StringBuilder();
            styleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", position.y));
            styleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", position.x));
            styleProperties.append("      position: absolute\n"); // Ensure absolute positioning
            styleProperties.append("      transform: translate(-50%, -50%)\n");
            
            // If there's a separate background element, this visual element should not be clickable
            if (needsSeparateBackground) {
                styleProperties.append("      pointer-events: none\n");
            } else if (clickableAreaType == ClickableAreaType.ROOM_SIZE) {
                styleProperties.append("      pointer-events: none\n"); // Room size clickable area handles clicks
            }

            if (displayType == DisplayType.ICON || displayType == DisplayType.BADGE) { // Icon or Badge
                // Using calc() adds a fixed base size to the scalable vw unit. This prevents icons from
                // becoming too small on narrow screens while still allowing them to grow on wider screens.
                double iconSizeVw = 2.0 * scaleFactor;
                double iconSizePx = 10.0 * scaleFactor;
                styleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", iconSizeVw, iconSizePx));
                // No background/border styling here, as it's handled by the separate image element
            }
            
            if (displayType == DisplayType.LABEL) {
                // Add background and border if requested for labels. This is applied directly
                // to the label element, allowing the background to size with the text content.
                if (this.showBorderAndBackground) { // This condition is controlled by the "Border/Background" checkbox in the UI
                    styleProperties.append(String.format(Locale.US, "      background: %s\n", this.backgroundColor));
                    styleProperties.append("      border-radius: 50%\n"); // Make it circular/elliptical (fixed extra %)
                    // Removed box-sizing: border-box as requested
                }
            }

            if (displayType == DisplayType.LABEL || displayType == DisplayType.BADGE) { // Badges can also have text
                styleProperties.append("      text-align: center\n");
            }

            if (blinking) {
                styleProperties.append("      animation: my-blink 1s linear infinite\n");
            } else {
                 styleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", opacity));
            }

            if (displayType == DisplayType.LABEL) {

                if (labelColor != null && !labelColor.trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      color: %s\n", labelColor));
                }
                if (labelTextShadow != null && !labelTextShadow.trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      text-shadow: 1px 1px 1px %s\n", labelTextShadow));
                }
                if (labelFontWeight != null && !labelFontWeight.trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      font-weight: %s\n", labelFontWeight));
                }
                // Using calc() for labels provides a minimum font size and prevents text from becoming
                // too small on narrow screens, improving readability.
                double scaledFontVw = 0.8 * scaleFactor;
                double scaledFontPx = 5.0 * scaleFactor;
                styleProperties.append(String.format(Locale.US, "      font-size: calc(%.2fvw + %.2fpx)\n", scaledFontVw, scaledFontPx));
            }

            // Add icon shadow filter if applicable
            if ((displayType == DisplayType.ICON || displayType == DisplayType.BADGE) && iconShadow != null && !iconShadow.equals("none")) {
                String shadowRgba = iconShadow.equals("white") ? "255,255,255,1" : "0,0,0,1";
                styleProperties.append(String.format(Locale.US, "      filter: drop-shadow(2px 2px 2px rgba(%s))\n", shadowRgba));
            }
            
            // Prepare conditional parts as arguments for String.format
            String attributeString = (this.attribute != null && !this.attribute.isEmpty())
                                   ? String.format("    attribute: %s\n", this.attribute) : "";

            String suffixString = "";
            if (displayType == DisplayType.LABEL) {
                if (labelSuffix != null && !labelSuffix.trim().isEmpty()) {
                    suffixString = String.format("    suffix: '%s'\n", labelSuffix.replace("'", "''"));
                } else if (this.attribute != null && !this.attribute.isEmpty()) {
                    suffixString = "    suffix: '°'\n";
                }
            }

            String titleString = (displayType == DisplayType.BADGE) ? ""
                               : String.format("    title: %s\n", (title != null ? title : "null"));

            String mainVisualElementYaml = String.format(Locale.US,
                "  - type: %s\n" +
                "    entity: %s\n" +
                "%s" + // attributeString
                "%s" + // suffixString
                "%s" + // titleString
                "    style:\n" +
                "%s" +
                "    tap_action:\n" +
                "      action: %s\n" +
                "    double_tap_action:\n" +
                "      action: %s\n" +
                "    hold_action:\n" +
                "      action: %s\n",
                displayTypeToYamlString.get(displayType), name,
                attributeString, suffixString, titleString,
                styleProperties.toString(),
                actionYaml(tapAction, tapActionValue, this.associatedFanEntityId),
                actionYaml(doubleTapAction, doubleTapActionValue, this.associatedFanEntityId),
                actionYaml(holdAction, holdActionValue, this.associatedFanEntityId));
            conditionalElements.add(mainVisualElementYaml);
        }

        // --- Clickable Area (Room Size) ---
        String clickableAreaYaml = ""; // This will be added as a separate top-level element
        if (clickableAreaType == ClickableAreaType.ROOM_SIZE && controller != null) {
            Map<String, Double> roomBounds = controller.getRoomBoundingBoxPercent(this);
            if (roomBounds != null) {
                // Check if the icon's center is within the calculated room bounds
                double iconCenterY = this.position.y;
                double iconCenterX = this.position.x;

                double roomT = roomBounds.get("top");
                double roomL = roomBounds.get("left");
                double roomW = roomBounds.get("width");
                double roomH = roomBounds.get("height");
                double roomR = roomL + roomW;
                double roomB = roomT + roomH;

                boolean iconCenterIsInside =
                    iconCenterX >= roomL && iconCenterX <= roomR &&
                    iconCenterY >= roomT && iconCenterY <= roomB;

                if (!iconCenterIsInside) {
                    System.err.println(String.format(Locale.US,
                        "Entity.java Warning: Icon center for entity '%s' (at L:%.2f%%, T:%.2f%%) is outside its calculated room's clickable area (L:%.2f%%, T:%.2f%%, W:%.2f%%, H:%.2f%%). Adjusting clickable area to include icon center.",
                        this.name, iconCenterX, iconCenterY, roomL, roomT, roomW, roomH
                    ));

                    // Expand roomBounds to include the icon's center
                    double newTop = Math.min(roomT, iconCenterY);
                    double newLeft = Math.min(roomL, iconCenterX);
                    double newRight = Math.max(roomR, iconCenterX);
                    double newBottom = Math.max(roomB, iconCenterY);

                    // Update the map directly
                    roomBounds.put("top", newTop);
                    roomBounds.put("left", newLeft);
                    roomBounds.put("width", Math.max(0, newRight - newLeft));   // Ensure non-negative
                    roomBounds.put("height", Math.max(0, newBottom - newTop)); // Ensure non-negative
                }

                // Calculate dimensions for the transparent PNG based on aspect ratio
                double roomWidthPercent = roomBounds.get("width");
                double roomHeightPercent = roomBounds.get("height");

                final int BASE_PNG_DIMENSION = 20; // Base size for the longer side of the PNG (in pixels)
                int pngWidthPx;
                int pngHeightPx;

                if (roomWidthPercent <= 0 && roomHeightPercent <= 0) { // Handles zero or negative dimensions
                    pngWidthPx = 1;
                    pngHeightPx = 1;
                } else if (roomWidthPercent >= roomHeightPercent) {
                    pngWidthPx = BASE_PNG_DIMENSION;
                    pngHeightPx = (int) Math.round(BASE_PNG_DIMENSION * (roomHeightPercent / Math.max(0.001, roomWidthPercent)));
                } else {
                    pngHeightPx = BASE_PNG_DIMENSION;
                    pngWidthPx = (int) Math.round(BASE_PNG_DIMENSION * (roomWidthPercent / Math.max(0.001, roomHeightPercent)));
                }

                // Ensure minimum 1x1 pixel dimension
                pngWidthPx = Math.max(1, pngWidthPx);
                pngHeightPx = Math.max(1, pngHeightPx);

                String baseNameForImage = this.name;
                String fullImageName = "transparent_" + baseNameForImage;

                try {
                    // ensureEntityTransparentImageGenerated prepends "transparent_" internally
                    controller.ensureEntityTransparentImageGenerated(baseNameForImage, pngWidthPx, pngHeightPx);
                    // renderHash needs the full name of the file that was created
                    String transparentImageHash = controller.renderHash(fullImageName, true);
                    // The path for Home Assistant also needs the full name
                    String transparentImagePath = "/local/floorplan/" + fullImageName + ".png?version=" + transparentImageHash;

                    clickableAreaYaml = String.format(Locale.US,
                        "  - type: image\n" + // Increased precision to .4f
                        "    entity: %s\n" +
                        "    image: %s\n" +
                        "    tap_action:\n" + // Increased precision to .4f
                        "      action: %s\n" +
                        "    double_tap_action:\n" +
                        "      action: %s\n" +
                        "    hold_action:\n" +
                        "      action: %s\n" +
                        "    style:\n" +
                        "      top: %.2f%%\n" +
                        "      left: %.4f%%\n" +
                        "      width: %.4f%%\n" +  // Use original percentage from roomBounds
                        "      height: %.4f%%\n" + // Use original percentage from roomBounds
                        "      transform: translate(0%%, 0%%)\n" +
                        "      opacity: 0%%\n",
                        this.name,
                        transparentImagePath,
                        actionYaml(tapAction, tapActionValue, this.associatedFanEntityId),
                        actionYaml(doubleTapAction, doubleTapActionValue, this.associatedFanEntityId),
                        actionYaml(holdAction, holdActionValue, this.associatedFanEntityId),
                        roomBounds.get("top"), roomBounds.get("left"), roomWidthPercent, roomHeightPercent);
                } catch (IOException e) {
                    System.err.println("Error generating/hashing transparent image for " + this.name + " with dimensions " + pngWidthPx + "x" + pngHeightPx + ": " + e.getMessage());
                }
            }
        } 

        // Add clickable area as a separate element if it exists
        if (!clickableAreaYaml.isEmpty()) {
            elements.add(clickableAreaYaml);
        }

        // Handle ALWAYS operator or alwaysOn flag explicitly
        if (this.displayOperator == DisplayOperator.ALWAYS || getAlwaysOn()) { // Consider alwaysOn as well
            elements.addAll(conditionalElements); // Add main visual elements directly
        } else {
            // If not ALWAYS and not forced by alwaysOn, then apply the conditional logic.
            String conditionAttributePart = (this.attribute != null && !this.attribute.isEmpty())
                                            ? String.format("        attribute: %s\n", this.attribute)
                                            : "";

            String conditionYaml;
            switch (this.displayOperator) {
                case IS:
                    conditionYaml = String.format(
                        "    conditions:\n" + // Ensure conditions block starts here
                        "      - condition: state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        state: '%s'",
                        name, this.displayValue);
                    break;
                case IS_NOT:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        state_not: '%s'",
                        name, this.displayValue);
                    break;
                case GREATER_THAN:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: numeric_state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        above: %s",
                        name, this.displayValue);
                    break;
                case LESS_THAN:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: numeric_state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        below: %s",
                        name, this.displayValue);
                    break;
                default:
                    // This case should ideally not be reached if ALWAYS/NEVER are handled above.
                    // If it is reached, it means an unhandled operator. Return empty list.
                    System.err.println("Warning: Unhandled display operator for entity " + name + ": " + this.displayOperator);
                    return new ArrayList<>();
            }

            // Indent all elements that go inside the conditional block
            String indentedConditionalElements = conditionalElements.stream()
                                                    .map(s -> s.replaceAll("(?m)^", "    ")) // Add 4 spaces to each line
                                                    .collect(Collectors.joining());

            String conditionalBlock = String.format(
                "  - type: conditional\n" +
                "%s\n" +
                "    elements:\n" +
                "%s",
                conditionYaml, // Use the generated conditionYaml
                indentedConditionalElements
            );
            elements.add(conditionalBlock);
        }

        return elements; // Return the list of all generated elements
    }

    /**
     * Generates the YAML for a transparent image element that serves as a background/border.
     * This element handles the sizing, background color, border-radius, and actions.
     */
    private String generateBackgroundElementYaml(Point2d position, double scaleFactor, String backgroundColor,
                                                 Action tapAction, String tapActionValue, Action doubleTapAction, String doubleTapActionValue,
                                                 Action holdAction, String holdActionValue, String associatedFanEntityId, String entityName, String entityId, boolean blinking, int opacity) { // Increased precision to .4f
        StringBuilder backgroundStyleProperties = new StringBuilder();
        backgroundStyleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", position.y));
        backgroundStyleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", position.x));
        backgroundStyleProperties.append("      position: absolute\n");
        backgroundStyleProperties.append("      transform: translate(-50%, -50%)\n");

        // Calculate the size of the background circle in vw units.
        // This size should be large enough to contain the icon. We use calc() to keep it
        // proportional to the icon's size across all screen widths.
        double backgroundSizeVw = 3.0 * scaleFactor;
        double backgroundSizePx = 15.0 * scaleFactor;
        backgroundStyleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", backgroundSizeVw, backgroundSizePx));
        // The color of the icon IS the background color.
        backgroundStyleProperties.append(String.format(Locale.US, "      color: %s\n", backgroundColor));

        if (blinking) {
            backgroundStyleProperties.append("      animation: my-blink 1s linear infinite\n");
        } else {
            backgroundStyleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", opacity));
        }

        return String.format(Locale.US,
            "  - type: icon\n" +
            "    icon: mdi:checkbox-blank-circle\n" + // A solid circle icon
            "    style:\n" +
            "%s" +
            "    tap_action:\n" + // Background handles actions
            "      action: %s\n" +
            "    double_tap_action:\n" +
            "      action: %s\n" +
            "    hold_action:\n" +
            "      action: %s\n",
            backgroundStyleProperties.toString(),
            actionYaml(tapAction, tapActionValue, associatedFanEntityId),
            actionYaml(doubleTapAction, doubleTapActionValue, associatedFanEntityId),
            actionYaml(holdAction, holdActionValue, associatedFanEntityId));
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

        String rawPieceName = firstPiece.getName();
        if (rawPieceName != null && rawPieceName.contains("/")) {
            int slashIndex = rawPieceName.indexOf('/');
            this.name = rawPieceName.substring(0, slashIndex);
            this.attribute = rawPieceName.substring(slashIndex + 1);
        } else {
            this.name = rawPieceName;
            this.attribute = null;
        }
        position = loadPosition();
        displayType = getSavedEnumValue(DisplayType.class, getSettingKey(SETTING_NAME_DISPLAY_TYPE), defaultDisplayType());
        
        displayOperator = getSavedEnumValue(DisplayOperator.class, getSettingKey(SETTING_NAME_DISPLAY_OPERATOR), DisplayOperator.ALWAYS);
        displayValue = settings.get(getSettingKey(SETTING_NAME_DISPLAY_VALUE), "");
        
        furnitureDisplayOperator = getSavedEnumValue(DisplayOperator.class, getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_OPERATOR), DisplayOperator.ALWAYS);
        // --- MODIFIED: Default to an empty string to prevent OutOfMemoryError ---
        furnitureDisplayValue = settings.get(getSettingKey(SETTING_NAME_FURNITURE_DISPLAY_VALUE), "");
        clickableAreaType = getSavedEnumValue(ClickableAreaType.class, getSettingKey(SETTING_NAME_CLICKABLE_AREA_TYPE), ClickableAreaType.ENTITY_SIZE);

        tapAction = getSavedEnumValue(Action.class, getSettingKey(SETTING_NAME_TAP_ACTION), defaultAction());
        tapActionValue = settings.get(getSettingKey(SETTING_NAME_TAP_ACTION_VALUE), "");
        doubleTapAction = getSavedEnumValue(Action.class, getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION), Action.NONE);
        doubleTapActionValue = settings.get(getSettingKey(SETTING_NAME_DOUBLE_TAP_ACTION_VALUE), "");
        holdAction = getSavedEnumValue(Action.class, getSettingKey(SETTING_NAME_HOLD_ACTION), Action.MORE_INFO);
        holdActionValue = settings.get(getSettingKey(SETTING_NAME_HOLD_ACTION_VALUE), "");
        blinking = settings.getBoolean(getSettingKey(SETTING_NAME_BLINKING), false);
        title = firstPiece.getDescription();
        opacity = settings.getInteger(getSettingKey(SETTING_NAME_OPACITY), 100);
        backgroundColor = settings.get(getSettingKey(SETTING_NAME_BACKGROUND_COLOR), "rgba(0, 0, 0, 0.5)");
        scaleFactor = settings.getDouble(getSettingKey(SETTING_NAME_SCALE_FACTOR), 1.0);
        alwaysOn = settings.getBoolean(getSettingKey(SETTING_NAME_ALWAYS_ON), false);
        associatedFanEntityId = settings.get(getSettingKey(SETTING_NAME_ASSOCIATED_FAN_ENTITY_ID), "");
        fanColor = getSavedEnumValue(FanColor.class, getSettingKey(SETTING_NAME_FAN_COLOR), FanColor.FOUR_BLADE_CEILING_BLACK); // Default to 4 Blade Ceiling Black
        showFanWhenOff = settings.getBoolean(getSettingKey(SETTING_NAME_SHOW_FAN_WHEN_OFF), true);
        fanSize = getSavedEnumValue(FanSize.class, getSettingKey(SETTING_NAME_FAN_SIZE), FanSize.MEDIUM);
        fanOpacity = settings.getInteger(getSettingKey(SETTING_NAME_FAN_OPACITY), 100);
        showBorderAndBackground = settings.getBoolean(getSettingKey(SETTING_NAME_SHOW_BORDER_AND_BACKGROUND), false); // Default to false
        labelColor = settings.get(getSettingKey(SETTING_NAME_LABEL_COLOR), "white"); // Default to "white" for better contrast with dark backgrounds
        labelTextShadow = settings.get(getSettingKey(SETTING_NAME_LABEL_TEXT_SHADOW), "");
        labelFontWeight = settings.get(getSettingKey(SETTING_NAME_LABEL_FONT_WEIGHT), "normal"); // Default to "normal"
        labelSuffix = settings.get(getSettingKey(SETTING_NAME_LABEL_SUFFIX), "");

        iconShadow = settings.get(getSettingKey(SETTING_NAME_ICON_SHADOW), "none"); // Default to "none"
        excludeFromOverlap = settings.getBoolean(getSettingKey(SETTING_NAME_EXCLUDE_FROM_OVERLAP), false);
        isRgb = settings.getBoolean(getSettingKey(SETTING_NAME_IS_RGB), false);
        
        // Determine if this Entity represents a light based on its HA name or if any associated SH3D piece is a HomeLight
        boolean hasAnySh3dLightPiece = false;
        if (piecesOfFurniture != null) { // piecesOfFurniture should not be null here
            for (HomePieceOfFurniture pof : piecesOfFurniture) {
                if (pof instanceof HomeLight) {
                    hasAnySh3dLightPiece = true;
                    break;
                }
            }
        }
        this.isLight = (name != null && (name.startsWith("light.") || name.startsWith("switch."))) || hasAnySh3dLightPiece;
        saveInitialLightPowerValues();

        // Apply specific defaults for binary_sensor entities
        // These will apply if no user-saved setting exists for these properties,
        // or when resetToDefaults() is called.
        if (name != null && name.startsWith("binary_sensor.") && name.contains("motion")) {
            this.displayType = DisplayType.ICON;
            this.displayOperator = DisplayOperator.IS;
            this.displayValue = "on"; // Common 'active' state for binary_sensors
            this.tapAction = Action.NONE;
            this.doubleTapAction = Action.NONE; // Already the general default, but explicit
            this.holdAction = Action.NONE;
            this.blinking = true;
            // Other defaults like opacity, scaleFactor, clickableAreaType, showBorderAndBackground, furnitureDisplayOperator are generally fine.
        }
    }

    private DisplayType defaultDisplayType() {
        if (name.startsWith("fan.")) {
            return DisplayType.ICON_AND_ANIMATED_FAN;
        } else {
            return name.startsWith("sensor.") ? DisplayType.LABEL : DisplayType.ICON;
        }
    }

    public int compareTo(Entity other) {
        int nameCompare = getName().compareTo(other.getName());
        if (nameCompare == 0) {
            return getId().compareTo(other.getId()); // getId() is the sh3dPieceId
        }
        return nameCompare;
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
        double leftPosition = settings.getDouble(getSettingKey(SETTING_NAME_LEFT_POSITION), -1);
        double topPosition = settings.getDouble(getSettingKey(SETTING_NAME_TOP_POSITION), -1);
        if (leftPosition != -1 || topPosition != -1) {
            isUserDefinedPosition = true;
            return new Point2d(leftPosition, topPosition);
        }

        isUserDefinedPosition = false;
        return new Point2d(0, 0);
    }
}
