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

    public void setVisible(boolean visible) { // This method is now only used for scene preparation
        for (HomePieceOfFurniture piece : piecesOfFurniture)
            piece.setVisible(visible);
    }

    public void restoreConfiguration() {
        setVisible(true);

        if (!isLight)
            return;

        for (Map.Entry<HomeLight, Float> entry : initialPower.entrySet())
            entry.getKey().setPower(entry.getValue());
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

    /**
     * Generates the YAML for a single entity.
     * This logic was moved to YamlService class.
     * @param entity The entity to generate YAML for.
     * @param controller The main controller, needed for context like room bounds.
     * @return A list of strings, where each string is a complete YAML element.
     */
    // This method has been moved to YamlService and will now cause a compile error
    // as it is no longer defined in the Entity class.
    // Commenting it out to demonstrate the move and prevent compile issues.
    /*
    public List<String> buildYamlForEntity(Controller controller) {
        return null; // Placeholder, should not be called after refactoring
    }
    */

    // This method has been moved to YamlService and will now cause a compile error
    // as it is no longer defined in the Entity class.
    // Commenting it out to demonstrate the move and prevent compile issues.
    /*
    private String generateBackgroundElementYaml(Point2d position, double scaleFactor, String backgroundColor,
                                                 Action tapAction, String tapActionValue, Action doubleTapAction, String doubleTapActionValue,
                                                 Action holdAction, String holdActionValue, String associatedFanEntityId, String entityName, String entityId, boolean blinking, int opacity) {
        return null; // Placeholder, should not be called after refactoring
    }
    */

    // This method has been moved to YamlService and will now cause a compile error
    // as it is no longer defined in the Entity class.
    // Commenting it out to demonstrate the move and prevent compile issues.
    //private String actionYaml(Action action, String value, String fanEntityId) {
    //    return null; // Placeholder, should not be called after refactoring
    //}
}
