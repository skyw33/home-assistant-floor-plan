package com.shmuelzon.HomeAssistantFloorPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;

public class YamlService {

    /**
     * Generates the YAML for a single entity.
     * This logic was moved from the Entity class.
     * @param entity The entity to generate YAML for.
     * @param controller The main controller, needed for context like room bounds.
     * @return A list of strings, where each string is a complete YAML element.
     */
    public List<String> buildYamlForEntity(Entity entity, Controller controller) {
        final Map<Entity.DisplayType, String> displayTypeToYamlString = new HashMap<Entity.DisplayType, String>() {{
            put(Entity.DisplayType.BADGE, "state-badge");
            put(Entity.DisplayType.ICON, "state-icon");
            put(Entity.DisplayType.LABEL, "state-label");
        }};

        // If the entity is configured to never be displayed, or is an "always on" light (which has no icon)
        if (entity.getDisplayOperator() == Entity.DisplayOperator.NEVER || entity.getAlwaysOn()) {
            return new ArrayList<>(); // Return empty list if never displayed
        }

        List<String> elements = new ArrayList<>(); // This will hold all generated YAML elements for this entity
        List<String> conditionalElements = new ArrayList<>(); // Elements that go inside a conditional block

         //Determine if this entity needs a separate background element for its border/background
        boolean needsSeparateBackground = (entity.getDisplayType() == Entity.DisplayType.ICON || entity.getDisplayType() == Entity.DisplayType.BADGE || entity.getDisplayType() == Entity.DisplayType.ICON_AND_ANIMATED_FAN) && entity.getShowBorderAndBackground();

        if (entity.getDisplayType() == Entity.DisplayType.ICON_AND_ANIMATED_FAN) {
             //--- Generate Background/Border Element if needed (for ICON_AND_ANIMATED_FAN) ---
            if (needsSeparateBackground) {
                conditionalElements.add(generateBackgroundElementYaml(entity.getPosition(), entity.getScaleFactor(), entity.getBackgroundColor(), entity.getTapAction(), entity.getTapActionValue(), entity.getDoubleTapAction(), entity.getDoubleTapActionValue(), entity.getHoldAction(), entity.getHoldActionValue(), entity.getAssociatedFanEntityId(), entity.getName(), entity.getId(), entity.getBlinking(), entity.getOpacity()));
            }

            // --- Generate the Icon part of ICON_AND_ANIMATED_FAN ---
            StringBuilder iconStyleProperties = new StringBuilder();
            iconStyleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", entity.getPosition().y));
            iconStyleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", entity.getPosition().x));
            iconStyleProperties.append("      position: absolute\n"); // Ensure absolute positioning
            iconStyleProperties.append("      transform: translate(-50%, -50%)\n");

            // Set a responsive font-size, which will serve as the base for scaling all em-based units.
            // Using calc() adds a fixed base size to the scalable vw unit. This prevents icons from
            // becoming too small on narrow screens while still allowing them to grow on wider screens.
            double iconSizeVw = 2.0 * entity.getScaleFactor();
            double iconSizePx = 10.0 * entity.getScaleFactor();
            iconStyleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", iconSizeVw, iconSizePx));
            
            // Icon part is not clickable if background handles it, otherwise it should be clickable
            if (needsSeparateBackground) {
                iconStyleProperties.append("      pointer-events: none\n");
            }

            // No background/border styling here, as it's handled by the separate image element
            if (entity.getBlinking()) {
                iconStyleProperties.append("      animation: my-blink 1s linear infinite\n");
            } else {
                iconStyleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", entity.getOpacity()));
            }

            String iconElementYaml;
            String attributePart = (entity.getAttribute() != null && !entity.getAttribute().isEmpty() ? "    attribute: " + entity.getAttribute() + "\n" : "");
            String titlePart = "    title: " + (entity.getTitle() != null ? entity.getTitle() : "null") + "\n";

            if (needsSeparateBackground) {
                iconElementYaml = String.format(Locale.US,
                    "  - type: state-icon\n" +
                    "    entity: %s\n" +
                    attributePart +
                    titlePart +
                    "    style:\n" +
                    "%s",
                    entity.getName(),
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
                    entity.getName(),
                    actionYaml(entity.getTapAction(), entity.getTapActionValue(), entity.getAssociatedFanEntityId()),
                    actionYaml(entity.getDoubleTapAction(), entity.getDoubleTapActionValue(), entity.getAssociatedFanEntityId()),
                    actionYaml(entity.getHoldAction(), entity.getHoldActionValue(), entity.getAssociatedFanEntityId()),
                    iconStyleProperties.toString());
            }

            // --- Generate the Fan Image part of ICON_AND_ANIMATED_FAN using standard conditional elements ---
            String fanImage;
            switch (entity.getFanColor()) {
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
            switch (entity.getFanSize()) {
                case SMALL:  fanSizePercent = 3.0; break; // Use the larger dimension for square
                case MEDIUM: fanSizePercent = 5.0; break; // Use the larger dimension for square
                case LARGE:  fanSizePercent = 7.0; break; // Use the larger dimension for square
                default:     fanSizePercent = 5.0; break; // Default to Medium (5.0%)
            }
            // Apply scaleFactor to the chosen size
            fanSizePercent *= entity.getScaleFactor(); // Apply scale factor to fan image dimensions

            if (entity.getAssociatedFanEntityId() != null && !entity.getAssociatedFanEntityId().trim().isEmpty()) {
                // Base style properties used by both 'on' and 'off' states.
                String baseStyle = String.format(Locale.US,
                    "          top: %.4f%%\n" +
                    "          left: %.4f%%\n" +
                    "          width: %.4f%%\n" +
                    "          aspect-ratio: 1 / 1\n" +
                    "          transform: translate(-50%%, -50%%) translateZ(0)\n" +
                    "          pointer-events: none\n" +
                    "          will-change: transform\n",
                    entity.getPosition().y, entity.getPosition().x, fanSizePercent);

                // --- Element for 'on' state (spinning) ---
                String onStateStyle = baseStyle + String.format(Locale.US,
                    "          animation: spin 1.5s linear infinite\n" +
                    "          opacity: %.2f\n",
                    (double)entity.getFanOpacity() / 100.0);

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
                    entity.getAssociatedFanEntityId(), fanImage, onStateStyle);
                conditionalElements.add(fanOnElementYaml);

                // --- Element for 'off' state (still) ---
                if (entity.getShowFanWhenOff()) {
                    String offStateStyle = baseStyle + String.format(Locale.US,
                        "          animation: none\n" +
                        "          opacity: %.2f\n",
                        (double)entity.getFanOpacity() / 100.0);

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
                        entity.getAssociatedFanEntityId(), fanImage, offStateStyle);
                    conditionalElements.add(fanOffElementYaml);
                }
            }
            conditionalElements.add(iconElementYaml);
        } else { // Not ICON_AND_ANIMATED_FAN
            if (needsSeparateBackground) {
                conditionalElements.add(generateBackgroundElementYaml(entity.getPosition(), entity.getScaleFactor(), entity.getBackgroundColor(), entity.getTapAction(), entity.getTapActionValue(), entity.getDoubleTapAction(), entity.getDoubleTapActionValue(), entity.getHoldAction(), entity.getHoldActionValue(), entity.getAssociatedFanEntityId(), entity.getName(), entity.getId(), entity.getBlinking(), entity.getOpacity()));
            }

            StringBuilder styleProperties = new StringBuilder();
            styleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", entity.getPosition().y));
            styleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", entity.getPosition().x));
            styleProperties.append("      position: absolute\n");
            styleProperties.append("      transform: translate(-50%, -50%)\n");
            
            if (needsSeparateBackground || entity.getClickableAreaType() == Entity.ClickableAreaType.ROOM_SIZE) {
                styleProperties.append("      pointer-events: none\n");
            }

            if (entity.getDisplayType() == Entity.DisplayType.ICON || entity.getDisplayType() == Entity.DisplayType.BADGE) {
                double iconSizeVw = 2.0 * entity.getScaleFactor();
                double iconSizePx = 10.0 * entity.getScaleFactor();
                styleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", iconSizeVw, iconSizePx));
            }
            
            if (entity.getDisplayType() == Entity.DisplayType.LABEL) {
                if (entity.getShowBorderAndBackground()) {
                    styleProperties.append(String.format(Locale.US, "      background: %s\n", entity.getBackgroundColor()));
                    styleProperties.append("      border-radius: 50%\n");
                }
            }

            if (entity.getDisplayType() == Entity.DisplayType.LABEL || entity.getDisplayType() == Entity.DisplayType.BADGE) {
                styleProperties.append("      text-align: center\n");
            }

            if (entity.getBlinking()) {
                styleProperties.append("      animation: my-blink 1s linear infinite\n");
            } else {
                 styleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", entity.getOpacity()));
            }

            if (entity.getDisplayType() == Entity.DisplayType.LABEL) {
                if (entity.getLabelColor() != null && !entity.getLabelColor().trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      color: %s\n", entity.getLabelColor()));
                }
                if (entity.getLabelTextShadow() != null && !entity.getLabelTextShadow().trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      text-shadow: 1px 1px 1px %s\n", entity.getLabelTextShadow()));
                }
                if (entity.getLabelFontWeight() != null && !entity.getLabelFontWeight().trim().isEmpty()) {
                    styleProperties.append(String.format(Locale.US, "      font-weight: %s\n", entity.getLabelFontWeight()));
                }
                double scaledFontVw = 0.8 * entity.getScaleFactor();
                double scaledFontPx = 5.0 * entity.getScaleFactor();
                styleProperties.append(String.format(Locale.US, "      font-size: calc(%.2fvw + %.2fpx)\n", scaledFontVw, scaledFontPx));
            }

            if ((entity.getDisplayType() == Entity.DisplayType.ICON || entity.getDisplayType() == Entity.DisplayType.BADGE) && entity.getIconShadow() != null && !entity.getIconShadow().equals("none")) {
                String shadowRgba = entity.getIconShadow().equals("white") ? "255,255,255,1" : "0,0,0,1";
                styleProperties.append(String.format(Locale.US, "      filter: drop-shadow(2px 2px 2px rgba(%s))\n", shadowRgba));
            }
            
            String attributeString = (entity.getAttribute() != null && !entity.getAttribute().isEmpty())
                                   ? String.format("    attribute: %s\n", entity.getAttribute()) : "";

            String suffixString = "";
            if (entity.getDisplayType() == Entity.DisplayType.LABEL) {
                if (entity.getLabelSuffix() != null && !entity.getLabelSuffix().trim().isEmpty()) {
                    suffixString = String.format("    suffix: '%s'\n", entity.getLabelSuffix().replace("'", "''"));
                } else if (entity.getAttribute() != null && !entity.getAttribute().isEmpty()) {
                    suffixString = "    suffix: '°'\n";
                }
            }

            String titleString = (entity.getDisplayType() == Entity.DisplayType.BADGE) ? ""
                               : String.format("    title: %s\n", (entity.getTitle() != null ? entity.getTitle() : "null"));

            String mainVisualElementYaml = String.format(Locale.US,
                "  - type: %s\n" +
                "    entity: %s\n" +
                "%s" +
                "%s" +
                "%s" +
                "    style:\n" +
                "%s" +
                "    tap_action:\n" +
                "      action: %s\n" +
                "    double_tap_action:\n" +
                "      action: %s\n" +
                "    hold_action:\n" +
                "      action: %s\n",
                displayTypeToYamlString.get(entity.getDisplayType()), entity.getName(),
                attributeString, suffixString, titleString,
                styleProperties.toString(),
                actionYaml(entity.getTapAction(), entity.getTapActionValue(), entity.getAssociatedFanEntityId()),
                actionYaml(entity.getDoubleTapAction(), entity.getDoubleTapActionValue(), entity.getAssociatedFanEntityId()),
                actionYaml(entity.getHoldAction(), entity.getHoldActionValue(), entity.getAssociatedFanEntityId()));
            conditionalElements.add(mainVisualElementYaml);
        }

        String clickableAreaYaml = "";
        if (entity.getClickableAreaType() == Entity.ClickableAreaType.ROOM_SIZE && controller != null) {
            Map<String, Double> roomBounds = controller.getRoomBoundingBoxPercent(entity);
            if (roomBounds != null) {
                double iconCenterY = entity.getPosition().y;
                double iconCenterX = entity.getPosition().x;

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
                        entity.getName(), iconCenterX, iconCenterY, roomL, roomT, roomW, roomH
                    ));

                    double newTop = Math.min(roomT, iconCenterY);
                    double newLeft = Math.min(roomL, iconCenterX);
                    double newRight = Math.max(roomR, iconCenterX);
                    double newBottom = Math.max(roomB, iconCenterY);

                    roomBounds.put("top", newTop);
                    roomBounds.put("left", newLeft);
                    roomBounds.put("width", Math.max(0, newRight - newLeft));
                    roomBounds.put("height", Math.max(0, newBottom - newTop));
                }

                double roomWidthPercent = roomBounds.get("width");
                double roomHeightPercent = roomBounds.get("height");

                final int BASE_PNG_DIMENSION = 20;
                int pngWidthPx;
                int pngHeightPx;

                if (roomWidthPercent <= 0 && roomHeightPercent <= 0) {
                    pngWidthPx = 1;
                    pngHeightPx = 1;
                } else if (roomWidthPercent >= roomHeightPercent) {
                    pngWidthPx = BASE_PNG_DIMENSION;
                    pngHeightPx = (int) Math.round(BASE_PNG_DIMENSION * (roomHeightPercent / Math.max(0.001, roomWidthPercent)));
                } else {
                    pngHeightPx = BASE_PNG_DIMENSION;
                    pngWidthPx = (int) Math.round(BASE_PNG_DIMENSION * (roomWidthPercent / Math.max(0.001, roomHeightPercent)));
                }

                pngWidthPx = Math.max(1, pngWidthPx);
                pngHeightPx = Math.max(1, pngHeightPx);

                String baseNameForImage = entity.getName();
                String fullImageName = "transparent_" + baseNameForImage;

                try {
                    controller.ensureEntityTransparentImageGenerated(baseNameForImage, pngWidthPx, pngHeightPx);
                    String transparentImageHash = controller.renderHash(fullImageName, true);
                    String transparentImagePath = "/local/floorplan/" + fullImageName + ".png?version=" + transparentImageHash;

                    clickableAreaYaml = String.format(Locale.US,
                        "  - type: image\n" +
                        "    entity: %s\n" +
                        "    image: %s\n" +
                        "    tap_action:\n" +
                        "      action: %s\n" +
                        "    double_tap_action:\n" +
                        "      action: %s\n" +
                        "    hold_action:\n" +
                        "      action: %s\n" +
                        "    style:\n" +
                        "      top: %.2f%%\n" +
                        "      left: %.4f%%\n" +
                        "      width: %.4f%%\n" +
                        "      height: %.4f%%\n" +
                        "      transform: translate(0%%, 0%%)\n" +
                        "      opacity: 0%%\n",
                        entity.getName(),
                        transparentImagePath,
                        actionYaml(entity.getTapAction(), entity.getTapActionValue(), entity.getAssociatedFanEntityId()),
                        actionYaml(entity.getDoubleTapAction(), entity.getDoubleTapActionValue(), entity.getAssociatedFanEntityId()),
                        actionYaml(entity.getHoldAction(), entity.getHoldActionValue(), entity.getAssociatedFanEntityId()),
                        roomBounds.get("top"), roomBounds.get("left"), roomWidthPercent, roomHeightPercent);
                } catch (IOException e) {
                    System.err.println("Error generating/hashing transparent image for " + entity.getName() + " with dimensions " + pngWidthPx + "x" + pngHeightPx + ": " + e.getMessage());
                }
            }
        }

        // Add clickable area as a separate element if it exists
        if (!clickableAreaYaml.isEmpty()) {
            elements.add(clickableAreaYaml);
        }

        // Handle ALWAYS operator or alwaysOn flag explicitly
        if (entity.getDisplayOperator() == Entity.DisplayOperator.ALWAYS || entity.getAlwaysOn()) {
            elements.addAll(conditionalElements); // Add main visual elements directly
        } else {
            // If not ALWAYS and not forced by alwaysOn, then apply the conditional logic.
            String conditionAttributePart = (entity.getAttribute() != null && !entity.getAttribute().isEmpty())
                                            ? String.format("        attribute: %s\n", entity.getAttribute())
                                            : "";

            String conditionYaml;
            switch (entity.getDisplayOperator()) {
                case IS:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        state: '%s'",
                        entity.getName(), entity.getDisplayValue());
                    break;
                case IS_NOT:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        state_not: '%s'",
                        entity.getName(), entity.getDisplayValue());
                    break;
                case GREATER_THAN:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: numeric_state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        above: %s",
                        entity.getName(), entity.getDisplayValue());
                    break;
                case LESS_THAN:
                    conditionYaml = String.format(
                        "    conditions:\n" +
                        "      - condition: numeric_state\n" +
                        "        entity: %s\n" +
                        conditionAttributePart +
                        "        below: %s",
                        entity.getName(), entity.getDisplayValue());
                    break;
                default:
                    System.err.println("Warning: Unhandled display operator for entity " + entity.getName() + ": " + entity.getDisplayOperator());
                    return new ArrayList<>();
            }

            String indentedConditionalElements = conditionalElements.stream()
                                                    .map(s -> s.replaceAll("(?m)^", "    "))
                                                    .collect(Collectors.joining());

            String conditionalBlock = String.format(
                "  - type: conditional\n" +
                "%s\n" +
                "    elements:\n" +
                "%s",
                conditionYaml,
                indentedConditionalElements
            );
            elements.add(conditionalBlock);
        }

        return elements;
    }

    public String generateBackgroundElementYaml(Point2d position, double scaleFactor, String backgroundColor,
                                                 Entity.Action tapAction, String tapActionValue, Entity.Action doubleTapAction, String doubleTapActionValue,
                                                 Entity.Action holdAction, String holdActionValue, String associatedFanEntityId, String entityName, String entityId, boolean blinking, int opacity) {
        StringBuilder backgroundStyleProperties = new StringBuilder();
        backgroundStyleProperties.append(String.format(Locale.US, "      top: %.4f%%\n", position.y));
        backgroundStyleProperties.append(String.format(Locale.US, "      left: %.4f%%\n", position.x));
        backgroundStyleProperties.append("      position: absolute\n");
        backgroundStyleProperties.append("      transform: translate(-50%, -50%)\n");

        double backgroundSizeVw = 3.0 * scaleFactor;
        double backgroundSizePx = 15.0 * scaleFactor;
        backgroundStyleProperties.append(String.format(Locale.US, "      --mdc-icon-size: calc(%.2fvw + %.2fpx)\n", backgroundSizeVw, backgroundSizePx));
        backgroundStyleProperties.append(String.format(Locale.US, "      color: %s\n", backgroundColor));

        if (blinking) {
            backgroundStyleProperties.append("      animation: my-blink 1s linear infinite\n");
        } else {
            backgroundStyleProperties.append(String.format(Locale.US, "      opacity: %d%%\n", opacity));
        }

        return String.format(Locale.US,
            "  - type: icon\n" +
            "    icon: mdi:checkbox-blank-circle\n" +
            "    style:\n" +
            "%s" +
            "    tap_action:\n" +
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

    public String actionYaml(Entity.Action action, String value, String fanEntityId) {
        final Map<Entity.Action, String> actionToYamlString = new HashMap<Entity.Action, String>() {{
            put(Entity.Action.MORE_INFO, "more-info");
            put(Entity.Action.NAVIGATE, "navigate");
            put(Entity.Action.NONE, "none");
            put(Entity.Action.TOGGLE, "toggle");
            put(Entity.Action.TOGGLE_FAN, "call-service");
        }};

        String yaml = actionToYamlString.get(action);

        if (action == Entity.Action.NAVIGATE)
            yaml += String.format("\n" +
                "        navigation_path: %s", value);
        else if (action == Entity.Action.TOGGLE_FAN) {
            yaml += String.format("\n" +
                "      service: fan.toggle\n" +
                "      target:\n" +
                "        entity_id: %s", fanEntityId != null ? fanEntityId : "");
        }

        return yaml;
    }

    public String generateLightYaml(Scene scene, List<Entity> lights, List<Entity> onLights, String imageName, boolean includeMixBlend) throws IOException {
        String conditions = "";
        for (Entity light : lights) {
            conditions += String.format(
                "      - condition: state\n" +
                "        entity: %s\n" +
                "        state: '%s'\n",
                light.getName(), onLights.contains(light) ? "on" : "off");
        }
        conditions += scene.getConditions();
        if (conditions.length() == 0)
            conditions = "      []\n";

        return String.format(
            "  - type: conditional\n" +
            "    conditions:\n%s" +
            "    elements:\n" +
            "      - type: image\n" +
            "        tap_action:\n" +
            "          action: none\n" +
            "        hold_action:\n" +
            "          action: none\n" +
            "        image: /local/floorplan/%s.%s?version=%s\n" +
            "        filter: none\n" +
            "        style:\n" +
            "          left: 50%%\n" +
            "          top: 50%%\n" +
            "          width: 100%%\n%s",
            conditions, imageName, "png", "0", // Placeholder renderHash, will be updated in Controller
            includeMixBlend ? "          mix-blend-mode: lighten\n" : "");
    }

    public String generateRgbLightYaml(Scene scene, Entity light, String imageName) throws IOException {
        String lightName = light.getName();

        return String.format(
            "  - type: conditional\n" +
            "    conditions:\n" +
            "      - condition: state\n" +
            "        entity: %s\n" +
            "        state: 'on'\n%s" +
            "    elements:\n" +
            "      - type: custom:config-template-card\n" +
            "        variables:\n" +
            "          LIGHT_STATE: states['%s'].state\n" +
            "          COLOR_MODE: states['%s'].attributes.color_mode\n" +
            "          LIGHT_COLOR: states['%s'].attributes.hs_color\n" +
            "          BRIGHTNESS: states['%s'].attributes.brightness\n" +
            "          isInColoredMode: colorMode => ['hs', 'rgb', 'rgbw', 'rgbww', 'white', 'xy'].includes(colorMode)\n" +
            "        entities:\n" +
            "          - %s\n" +
            "        element:\n" +
            "          type: image\n" +
            "          image: >-\n" +
            "            ${!isInColoredMode(COLOR_MODE) || (isInColoredMode(COLOR_MODE) && LIGHT_COLOR && LIGHT_COLOR[0] == 0 && LIGHT_COLOR[1] == 0) ?\n" +
            "            '/local/floorplan/%s.png?version=%s' :\n" +
            "            '/local/floorplan/%s.png?version=%s' }\n" +
            "          style:\n" +
            "            filter: '${ \"hue-rotate(\" + (isInColoredMode(COLOR_MODE) && LIGHT_COLOR ? LIGHT_COLOR[0] : 0) + \"deg)\"}'\n" +
            "            opacity: '${LIGHT_STATE === ''on'' ? (BRIGHTNESS / 255) : ''100''}'\n" +
            "            mix-blend-mode: lighten\n" +
            "            pointer-events: none\n" +
            "            left: 50%%\n" +
            "            top: 50%%\n" +
            "            width: 100%%\n",
            lightName, scene.getConditions(), lightName, lightName, lightName, lightName, lightName,
            imageName, "0", imageName + ".red", "0"); // Placeholder renderHash, will be updated in Controller
    }

    public String generateEntitiesYaml(List<Entity> allEntities, Controller controller) {
        List<String> allYamlElements = new ArrayList<>();
        for (Entity entity : allEntities) {
            allYamlElements.addAll(buildYamlForEntity(entity, controller));
        }

        return String.join("", allYamlElements);
    }
}