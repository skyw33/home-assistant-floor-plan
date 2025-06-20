package com.shmuelzon.HomeAssistantFloorPlan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.lang.InterruptedException;
import java.nio.channels.ClosedByInterruptException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.StandardCopyOption;
import java.util.ResourceBundle;
import javax.swing.SwingUtilities;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.media.j3d.Transform3D;
import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;
import javax.vecmath.Point3f; // Added for 3D centroid
import javax.vecmath.Vector4d;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.shmuelzon.HomeAssistantFloorPlan.Entity.DisplayOperator;


public class Controller {
    public enum Property {COMPLETED_RENDERS, NUMBER_OF_RENDERS}
    public enum LightMixingMode {CSS, OVERLAY, FULL}
    public enum Renderer {YAFARAY, SUNFLOW}
    public enum Quality {HIGH, LOW}
    public enum ImageFormat {PNG, JPEG}

    private static final String TRANSPARENT_IMAGE_NAME = "transparent";

    private static final String CONTROLLER_RENDER_WIDTH = "renderWidth";
    private static final String CONTROLLER_RENDER_HEIGHT = "renderHeight";
    private static final String CONTROLLER_LIGHT_MIXING_MODE = "lightMixingMode";
    private static final String CONTROLLER_SENSITIVTY = "sensitivity";
    private static final String CONTROLLER_RENDERER = "renderer";
    private static final String CONTROLLER_QUALITY = "quality";
    private static final String CONTROLLER_IMAGE_FORMAT = "imageFormat";
    private static final String CONTROLLER_RENDER_TIME = "renderTime";
    private static final String CONTROLLER_OUTPUT_DIRECTORY_NAME = "outputDirectoryName";
    private static final String CONTROLLER_USE_EXISTING_RENDERS = "useExistingRenders";

    private Home home;
    private Settings settings;
    private Camera camera;
    private List<Entity> lightEntities = new ArrayList<>();
    private List<Entity> otherEntities = new ArrayList<>();
    private List<Entity> otherLevelsEntities = new ArrayList<>();
    private Map<String, List<Entity>> lightsGroups = new HashMap<>();
    private Vector4d cameraPosition;
    private Transform3D perspectiveTransform;
    private PropertyChangeSupport propertyChangeSupport;
    private int numberOfCompletedRenders;
    private AbstractPhotoRenderer photoRenderer;
    private int renderWidth;
    private int renderHeight;
    private LightMixingMode lightMixingMode;
    private int sensitivity;
    private Renderer renderer;
    private Quality quality;
    private ImageFormat imageFormat;
    private List<Long> renderDateTimes;
    private String outputDirectoryName;
    private String outputRendersDirectoryName;
    private String outputFloorplanDirectoryName;
    private boolean useExistingRenders;
    private Scenes scenes;
    private Map<String, Double> houseNdcBounds;
    private ResourceBundle resourceBundle;

public Controller(Home home, ResourceBundle resourceBundle) {
        this.home = home;
        settings = new Settings(home);
        camera = home.getCamera().clone();

        // Listen to the Home model for camera changes
        home.addPropertyChangeListener("camera", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        // Explicitly update the plugin's working camera to the latest from Home
                        applySelectedCameraToWorkingCamera(); // Make sure this.camera is fresh
                        repositionEntities(); // This will then use the fresh this.camera
                    }
                });
            }
        });

        this.resourceBundle = resourceBundle;
        propertyChangeSupport = new PropertyChangeSupport(this);
        loadDefaultSettings();
        createHomeAssistantEntities(); // Call the new method

        buildLightsGroups();
        buildScenes();
        repositionEntities(); // This will also calculate houseNdcBounds
    }

    public void loadDefaultSettings() {
        renderWidth = settings.getInteger(CONTROLLER_RENDER_WIDTH, 1024);
        renderHeight = settings.getInteger(CONTROLLER_RENDER_HEIGHT, 576);
        lightMixingMode = LightMixingMode.valueOf(settings.get(CONTROLLER_LIGHT_MIXING_MODE, LightMixingMode.CSS.name()));
        sensitivity = settings.getInteger(CONTROLLER_SENSITIVTY, 10);
        renderer = Renderer.valueOf(settings.get(CONTROLLER_RENDERER, Renderer.YAFARAY.name()));
        quality = Quality.valueOf(settings.get(CONTROLLER_QUALITY, Quality.HIGH.name()));
        imageFormat = ImageFormat.valueOf(settings.get(CONTROLLER_IMAGE_FORMAT, ImageFormat.PNG.name()));
        renderDateTimes = settings.getListLong(CONTROLLER_RENDER_TIME, Arrays.asList(camera.getTime()));
        outputDirectoryName = settings.get(CONTROLLER_OUTPUT_DIRECTORY_NAME, System.getProperty("user.home"));
        outputRendersDirectoryName = outputDirectoryName + File.separator + "renders";
        outputFloorplanDirectoryName = outputDirectoryName + File.separator + "floorplan";
        useExistingRenders = settings.getBoolean(CONTROLLER_USE_EXISTING_RENDERS, true);
    }

    public void addPropertyChangeListener(Property property, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(property.name(), listener);
    }

    public void removePropertyChangeListener(Property property, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(property.name(), listener);
    }
    
    public List<Entity> getLightEntities() {
        return lightEntities;
    }

    public List<Entity> getOtherEntities() {
        return otherEntities;
    }

    public Map<String, List<Entity>> getLightsGroups() {
        return lightsGroups;
    }

    private int getNumberOfControllableLights(List<Entity> lights) {
        int numberOfControllableLights = 0;

        for (Entity light : lights)
            numberOfControllableLights += light.getAlwaysOn() ? 0 : 1;

        return numberOfControllableLights;
    }

    public int getNumberOfTotalRenders() {
        int numberOfLightRenders = 1;

        if (scenes == null)
            return 0;

        for (List<Entity> groupLights : lightsGroups.values()) {
            numberOfLightRenders += (1 << getNumberOfControllableLights(groupLights)) - 1;
        }
        return numberOfLightRenders * scenes.size();
    }

    public int getRenderHeight() {
        return renderHeight;
    }

    public void setRenderHeight(int renderHeight) {
        this.renderHeight = renderHeight;
        settings.setInteger(CONTROLLER_RENDER_HEIGHT, renderHeight);
        repositionEntities();
    }

    public int getRenderWidth() {
        return renderWidth;
    }

    public void setRenderWidth(int renderWidth) {
        this.renderWidth = renderWidth;
        settings.setInteger(CONTROLLER_RENDER_WIDTH, renderWidth);
        repositionEntities();
    }

    public int getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(int sensitivity) {
        this.sensitivity = sensitivity;
        settings.setInteger(CONTROLLER_SENSITIVTY, sensitivity);
    }

    public LightMixingMode getLightMixingMode() {
        return lightMixingMode;
    }

    public void setLightMixingMode(LightMixingMode lightMixingMode) {
        int oldNumberOfTotaleRenders = getNumberOfTotalRenders();
        this.lightMixingMode = lightMixingMode;
        buildLightsGroups();
        settings.set(CONTROLLER_LIGHT_MIXING_MODE, lightMixingMode.name());
        propertyChangeSupport.firePropertyChange(Property.NUMBER_OF_RENDERS.name(), oldNumberOfTotaleRenders, getNumberOfTotalRenders());
    }

    public String getOutputDirectory() {
        return outputDirectoryName;
    }

    public void setOutputDirectory(String outputDirectoryName) {
        this.outputDirectoryName = outputDirectoryName;
        outputRendersDirectoryName = outputDirectoryName + File.separator + "renders";
        outputFloorplanDirectoryName = outputDirectoryName + File.separator + "floorplan";
        settings.set(CONTROLLER_OUTPUT_DIRECTORY_NAME, outputDirectoryName);
    }

    public boolean getUserExistingRenders() {
        return useExistingRenders;
    }

    public void setUserExistingRenders(boolean useExistingRenders) {
        this.useExistingRenders = useExistingRenders;
        settings.setBoolean(CONTROLLER_USE_EXISTING_RENDERS, useExistingRenders);
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
        settings.set(CONTROLLER_RENDERER, renderer.name());
    }

    public Quality getQuality() {
        return quality;
    }

    public void setQuality(Quality quality) {
        this.quality = quality;
        settings.set(CONTROLLER_QUALITY, quality.name());
    }

    public ImageFormat getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(ImageFormat imageFormat) {
        this.imageFormat = imageFormat;
        settings.set(CONTROLLER_IMAGE_FORMAT, imageFormat.name());
    }

    public List<Long> getRenderDateTimes() {
        return renderDateTimes;
    }

    public void setRenderDateTimes(List<Long> renderDateTimes) {
        this.renderDateTimes = renderDateTimes;
        settings.setListLong(CONTROLLER_RENDER_TIME, renderDateTimes);
        buildScenes();
    }
    
    // Method to get stored camera names, might be useful if re-enabled or for other purposes
    // public List<String> getStoredCameraNamesFromHome() { ... }
    
    public BufferedImage generatePreviewImage() throws IOException, InterruptedException {
        // The plugin's 'this.camera' is already a clone of home.getCamera()
        // from when the modal dialog was opened.
        // renderWidth and renderHeight are current settings.

        AbstractPhotoRenderer previewPhotoRenderer = null;
        try {
            Map<Renderer, String> rendererToClassName = new HashMap<Renderer, String>() {{
                put(Renderer.SUNFLOW, "com.eteks.sweethome3d.j3d.PhotoRenderer");
                put(Renderer.YAFARAY, "com.eteks.sweethome3d.j3d.YafarayRenderer");
            }};
            // Use the currently selected renderer type, but force LOW quality
            previewPhotoRenderer = AbstractPhotoRenderer.createInstance(
                rendererToClassName.get(this.renderer),
                this.home, // Pass the actual home object, rendering its current light state
                null,
                AbstractPhotoRenderer.Quality.LOW); // Force LOW quality for speed

            BufferedImage image = new BufferedImage(this.renderWidth, this.renderHeight, BufferedImage.TYPE_INT_RGB);

            // Render with the plugin's current working camera
            previewPhotoRenderer.render(image, this.camera, null);

            if (Thread.interrupted()) { // Check if the thread was interrupted during render
                throw new InterruptedException("Preview rendering interrupted");
            }
            return image;
        } finally {
            if (previewPhotoRenderer != null) {
                previewPhotoRenderer.dispose();
            }
        }
    }

    public Map<String, Double> getRoomBoundingBoxPercent(Entity entity) {
        if (this.houseNdcBounds == null) {
            System.err.println("Error: houseNdcBounds not calculated. Cannot get room bounding box.");
            return null;
        }
        if (entity == null) { // getRoomForEntity will handle if piecesOfFurniture is empty
            return null;
        }

        // Use the consistent getRoomForEntity method (origin-based) to determine the entity's room.
        Room entityRoom = getRoomForEntity(entity);

        if (entityRoom == null) {
            // System.err.println("Info: Entity " + entity.getName() + " not found in any room by getRoomForEntity. Cannot calculate Room Size bounds.");
            return null;
        }

        // The level for calculating bounds should be the room's level.
        com.eteks.sweethome3d.model.Level roomLevel = entityRoom.getLevel();
        if (roomLevel == null) {
             System.err.println("Warning: Room '" + entityRoom.getName() + "' associated with entity '" + entity.getName() + "' has no level. Cannot calculate Room Size bounds.");
            return null;
        }
        
        return calculateRoom2DBounds(entityRoom, roomLevel);
    }

    private Map<String, Double> calculateRoom2DBounds(Room room, 
                                                  com.eteks.sweethome3d.model.Level entityLevel) {
        List<float[]> roomWorldPoints = Arrays.asList(room.getPoints());
        // This check might be redundant if called after ensuring roomWorldPoints is valid, but good for a helper.
        if (roomWorldPoints == null || roomWorldPoints.isEmpty()) return null;

        // Use clamped houseNdcBounds for normalization
        // This was confirmed by debug output to be calculated and not always [-1,1]
        double hNdcMinX = Math.max(-1.0, Math.min(1.0, this.houseNdcBounds.get("minX")));
        double hNdcMaxX = Math.max(-1.0, Math.min(1.0, this.houseNdcBounds.get("maxX")));
        double hNdcMinY = Math.max(-1.0, Math.min(1.0, this.houseNdcBounds.get("minY")));
        double hNdcMaxY = Math.max(-1.0, Math.min(1.0, this.houseNdcBounds.get("maxY")));

        double minCornerX = Double.POSITIVE_INFINITY, maxCornerX = Double.NEGATIVE_INFINITY;
        double minCornerY = Double.POSITIVE_INFINITY, maxCornerY = Double.NEGATIVE_INFINITY;

        float roomBaseElevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
        
        float actualRoomHeight;
        if (room.getLevel() != null) {
            actualRoomHeight = room.getLevel().getHeight(); // Use Level.getHeight()
            if (actualRoomHeight <= 0) { 
                // If level height is not positive, fall back to home's default wall height.
                // home.getWallHeight() returns the default wall height for new walls.
                actualRoomHeight = home.getWallHeight();
            }
        } else {
            actualRoomHeight = home.getWallHeight(); // Fallback if room has no level
        }
                               
        float roomCeilingElevation = roomBaseElevation + actualRoomHeight;

        List<Vector4d> cornerPointsToProject = new ArrayList<>();
        // Add floor points: p[0] is X, p[1] is Z (depth). Y is vertical.
        for (float[] p2d : roomWorldPoints) {
            cornerPointsToProject.add(new Vector4d(p2d[0], roomBaseElevation, p2d[1], 1.0));
        }
        // Add ceiling points
        if (room.isCeilingVisible() && actualRoomHeight > 0) { // Only add if room has height and visible ceiling
            for (float[] p2d : roomWorldPoints) {
                cornerPointsToProject.add(new Vector4d(p2d[0], roomCeilingElevation, p2d[1], 1.0));
            }
        }

        // Calculate house's screen footprint percentages based on its (clamped) NDC bounds
        // For X-axis: (ndc * 0.5 + 0.5)
        double houseScreenLeftPct   = (hNdcMinX * 0.5 + 0.5) * 100.0;
        double houseScreenRightPct  = (hNdcMaxX * 0.5 + 0.5) * 100.0;
        double houseScreenWidthPct  = houseScreenRightPct - houseScreenLeftPct;

        // For Y-axis: using the user-preferred (ndc * 0.5 + 0.5) scaling for NDC-to-Viewport mapping
        double houseScreenTopPct    = (hNdcMinY * 0.5 + 0.5) * 100.0;
        double houseScreenBottomPct = (hNdcMaxY * 0.5 + 0.5) * 100.0;
        double houseScreenHeightPct = houseScreenBottomPct - houseScreenTopPct;


        for (Vector4d worldPos : cornerPointsToProject) {
            Vector4d viewPos = new Vector4d(worldPos);
            // cameraPosition is (camX, camZ_depth, camY_vertical, 0)
            viewPos.sub(cameraPosition); 
            perspectiveTransform.transform(viewPos);

            if (viewPos.w == 0) continue; // Avoid division by zero
            viewPos.scale(1.0 / viewPos.w); // Perspective divide (NDC)
            
            // Room corner's normalized position (0 to 1) within the house's (clamped) NDC span
            // Also clamp the viewPos.x/y to be within the house's NDC bounds before normalization
            // to prevent extreme values if a room corner projects outside the house bounds.
            double clampedViewPosX = Math.max(hNdcMinX, Math.min(hNdcMaxX, viewPos.x));
            double clampedViewPosY = Math.max(hNdcMinY, Math.min(hNdcMaxY, viewPos.y));

            double roomCornerXNormInHouse = (hNdcMaxX == hNdcMinX) ? 0.5 : (clampedViewPosX - hNdcMinX) / (hNdcMaxX - hNdcMinX);
            double roomCornerYNormInHouse = (hNdcMaxY == hNdcMinY) ? 0.5 : (clampedViewPosY - hNdcMinY) / (hNdcMaxY - hNdcMinY);

            // Final screen percentage for this room corner, relative to the full viewport
            double screenXPercent = houseScreenLeftPct + (roomCornerXNormInHouse * houseScreenWidthPct);
            double screenYPercent = houseScreenTopPct  + (roomCornerYNormInHouse * houseScreenHeightPct);
            
            minCornerX = Math.min(minCornerX, screenXPercent);
            maxCornerX = Math.max(maxCornerX, screenXPercent);
            minCornerY = Math.min(minCornerY, screenYPercent);
            maxCornerY = Math.max(maxCornerY, screenYPercent);
        }

        if (Double.isInfinite(minCornerX) || Double.isInfinite(minCornerY) || Double.isInfinite(maxCornerX) || Double.isInfinite(maxCornerY)) {
            // All points were behind camera or at infinity.
            return null;
        }

        // 1. Calculate initial projected width and height
        double initialProjectedWidth = Math.max(0, maxCornerX - minCornerX);
        double initialProjectedHeight = Math.max(0, maxCornerY - minCornerY);

        // 2. Apply proportional shrinkage to get the target shrunken area
        double targetShrunkenWidth = initialProjectedWidth * 0.80; // Increased to 20% shrinkage
        double targetShrunkenHeight = initialProjectedHeight * 0.80; // Increased to 20% shrinkage

        // 3. Calculate the top-left of this target shrunken area, centered within the initial projection
        double targetShrunkenLeft = minCornerX + (initialProjectedWidth - targetShrunkenWidth) / 2.0;
        double targetShrunkenTop = minCornerY + (initialProjectedHeight - targetShrunkenHeight) / 2.0;

        
        Map<String, Double> finalBounds = new HashMap<>();
        // Adjust top and left to keep the smaller area centered within the original projection
        // Original left was minCornerX, new left is minCornerX + (initialWidth - finalWidth) / 2
        finalBounds.put("left", minCornerX + (initialProjectedWidth - targetShrunkenWidth) / 2.0);
        finalBounds.put("top", minCornerY + (initialProjectedHeight - targetShrunkenHeight) / 2.0);
        finalBounds.put("width", targetShrunkenWidth); 
        finalBounds.put("height", targetShrunkenHeight);

        return finalBounds;
    }

    public String[] getSuggestedStatesForEntity(Entity entity) {
        List<String> suggestions = new ArrayList<>();
        if (entity == null || entity.getName() == null || !entity.getName().contains(".")) {
            return new String[0];
        }
        String domain = entity.getName().split("\\.")[0];
        String propertyKey = "entity.states." + domain.toLowerCase();

        try {
            String statesFromProps = resourceBundle.getString(propertyKey);
            String[] states = statesFromProps.split(",");
            if (states.length > 0) {
                suggestions.addAll(Arrays.asList(states));
            }
        } catch (MissingResourceException e) {
            // Key not found, no suggestions for this domain, which is fine.
        }
        return suggestions.toArray(new String[0]);
    }

    public String[] getSuggestedStatesForFurniture(Entity entity) {
        // For now, let's assume furniture can use the same state suggestions as the entity itself.
        // This could be specialized if furniture had different state domains.
        return getSuggestedStatesForEntity(entity);
    }

    public List<String> getFanEntityIds() {
        List<String> fanIds = new ArrayList<>();
        Stream.concat(lightEntities.stream(), otherEntities.stream())
            .map(Entity::getName)
            .filter(name -> name != null && name.startsWith("fan."))
            .distinct()
            .sorted()
            .forEach(fanIds::add);
        return fanIds;
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public void stop() {
        if (photoRenderer != null) {
            photoRenderer.stop();
            photoRenderer = null;
        }
    }

    public boolean isProjectEmpty() {
        return home == null || home.getFurniture().isEmpty();
    }

    public void render() throws IOException, InterruptedException {
        propertyChangeSupport.firePropertyChange(Property.COMPLETED_RENDERS.name(), numberOfCompletedRenders, 0);
        numberOfCompletedRenders = 0;

        // Ensure the plugin's working camera and projection are up-to-date with the selected setting
        applySelectedCameraToWorkingCamera(); // Refreshes this.camera
        build3dProjection(); // Rebuilds this.perspectiveTransform based on the refreshed this.camera

        try {
            Files.createDirectories(Paths.get(outputRendersDirectoryName));
            Files.createDirectories(Paths.get(outputFloorplanDirectoryName));

            // Perform overlap check for ROOM_SIZE entities and print warnings
            List<String> overlapErrors = checkForOverlappingRoomSizeEntities();
            if (!overlapErrors.isEmpty()) {
                System.err.println("Warning: Overlapping 'Room Size' clickable areas detected. Rendering will proceed, but please adjust settings to avoid overlaps:");
                for (String error : overlapErrors) {
                    System.err.println("- " + error);
                }
            }


            copyStaticAssetsToFloorplanDirectory(); // Call to copy fan GIFs
   
            generateTransparentImage(outputFloorplanDirectoryName + File.separator + TRANSPARENT_IMAGE_NAME + ".png");
            String yaml = String.format(
                "type: picture-elements\n" +
                "image: /local/floorplan/%s.png?version=%s\n" +
                "elements:\n", TRANSPARENT_IMAGE_NAME, renderHash(TRANSPARENT_IMAGE_NAME, true));

            turnOffLightsFromOtherLevels();
            for (Scene scene : scenes) {
                Files.createDirectories(Paths.get(outputRendersDirectoryName + File.separator + scene.getName()));
                Files.createDirectories(Paths.get(outputFloorplanDirectoryName + File.separator + scene.getName()));
                final Scene currentSceneForEdt = scene;
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        currentSceneForEdt.prepare();
                    });
                } catch (InvocationTargetException e) {
                    throw new RuntimeException("Error preparing scene on EDT", e.getCause() != null ? e.getCause() : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Re-assert interrupt status
                    throw e; // Propagate InterruptedException
                }

                String baseImageName = "base";
                if (!scene.getName().isEmpty())
                    baseImageName = scene.getName() + File.separator + baseImageName;
                BufferedImage baseImage = generateBaseRender(scene, baseImageName);
                yaml += generateLightYaml(scene, Collections.emptyList(), null, baseImageName, false);

                for (String group : lightsGroups.keySet())
                    yaml += generateGroupRenders(scene, group, baseImage);
            }

            yaml += generateEntitiesYaml();

            // Append global styles for animations, etc.
            String globalStyles = "\n" +
                "style: |-\n" + // Using |- for multi-line string
                "  @keyframes my-blink {\n" +
                "    0% { opacity: 0; }\n" +
                "    50% { opacity: 1; }\n" + // Assuming 100% opacity is 1
                "    100% { opacity: 0; }\n" +
                "  }\n";
            yaml += globalStyles;

            // Append grid_options
            yaml += "\ngrid_options:\n" +
                    "  columns: full\n";

            Files.write(Paths.get(outputFloorplanDirectoryName + File.separator + "floorplan.yaml"), yaml.getBytes());
        } catch (InterruptedIOException e) {
            throw new InterruptedException();
        } catch (ClosedByInterruptException e) {
            throw new InterruptedException();
        } catch (IOException e) {
            // It's good practice to log exceptions if they are caught and rethrown,
            // or if they are caught and handled.
            // e.printStackTrace(); // Consider if this is needed or if the caller handles logging.
            throw e;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    restoreEntityConfiguration();
                });
            } catch (InvocationTargetException e) {
                System.err.println("Error restoring entity configuration on EDT: " + e.getCause());
                 // Log or handle more gracefully
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Re-assert interrupt status
                System.err.println("Interrupted while waiting for entity configuration restoration on EDT.");
            }
        }
    }

    private void copyStaticAssetsToFloorplanDirectory() {
        String[] staticAssetFiles = {
            "animated_fan.gif",
            "animated_fan_still.gif",
            "animated_fan_grey.gif",
            "animated_fan_still_grey.gif"
            // Add any other static assets here
        };
   
        Path destinationDir = Paths.get(outputFloorplanDirectoryName);
   
        for (String fileName : staticAssetFiles) {
            // Assumes files are in a 'resources' sub-package relative to this class's package
            String resourcePath = "/com/shmuelzon/HomeAssistantFloorPlan/resources/" + fileName;
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    System.err.println("Warning: Static asset resource not found in plugin: " + resourcePath + 
                                       ". Ensure it's in src/com/shmuelzon/HomeAssistantFloorPlan/resources/ and Makefile copies it.");
                    continue;
                }
                Path destinationFile = destinationDir.resolve(fileName);
                Files.copy(is, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                // System.out.println("Copied static asset " + fileName + " to " + destinationFile); // Optional: for debugging
            } catch (IOException e) {
                System.err.println("Error copying static asset resource " + fileName + ": " + e.getMessage());
                // Decide if this should throw an exception upwards or just log.
                // For now, logging and continuing.
            }
        }
    }

    // This method is removed as we now create an Entity per piece directly.
    // private void addEligibleFurnitureToMap(Map<String, List<HomePieceOfFurniture>> furnitureByName, List<HomePieceOfFurniture> lightsFromOtherLevels, List<HomePieceOfFurniture> furnitureList) { ... }

    // Helper to add common property change listeners to an entity
    private void addCommonPropertyChangeListeners(Entity entity) {
        entity.addPropertyChangeListener(Entity.Property.POSITION, ev -> repositionEntities());
        PropertyChangeListener buildAndUpdateCountsListener = ev -> {
            buildLightsGroups();
            buildScenes();
            propertyChangeSupport.firePropertyChange(Property.NUMBER_OF_RENDERS.name(), null, getNumberOfTotalRenders());
        };
        entity.addPropertyChangeListener(Entity.Property.ALWAYS_ON, buildAndUpdateCountsListener);
        entity.addPropertyChangeListener(Entity.Property.FURNITURE_DISPLAY_CONDITION, buildAndUpdateCountsListener);
        entity.addPropertyChangeListener(Entity.Property.IS_RGB, buildAndUpdateCountsListener);
    }

    // Helper to recursively collect and categorize HomePieceOfFurniture items
    private void collectAndCategorizeHaPieces(List<HomePieceOfFurniture> furnitureListToProcess,
                                              Map<String, List<HomePieceOfFurniture>> lightHaPiecesGroupedByName,
                                              List<HomePieceOfFurniture> nonLightHaPiecesOutputList) {
        for (HomePieceOfFurniture piece : furnitureListToProcess) {
            if (piece instanceof HomeFurnitureGroup) {
                collectAndCategorizeHaPieces(((HomeFurnitureGroup) piece).getFurniture(), lightHaPiecesGroupedByName, nonLightHaPiecesOutputList);
                continue;
            }

            if (isHomeAssistantEntity(piece.getName()) && piece.isVisible()) {
                String haName = piece.getName();
                if (haName.startsWith("light.") || haName.startsWith("switch.")) {
                    lightHaPiecesGroupedByName.computeIfAbsent(haName, k -> new ArrayList<>()).add(piece);
                } else {
                    nonLightHaPiecesOutputList.add(piece);
                }
            }
        }
    }

    private void createHomeAssistantEntities() {
        // Map for grouping LIGHT HA entities by their HA name
        Map<String, List<HomePieceOfFurniture>> lightHaPiecesGroupedByName = new HashMap<>();
        // List for HomePieceOfFurniture items that are HA entities but NOT lights
        List<HomePieceOfFurniture> nonLightHaPieces = new ArrayList<>();

        // Step 1: Traverse all furniture. Group light pieces by HA name. Collect non-light HA pieces.
        collectAndCategorizeHaPieces(home.getFurniture(), lightHaPiecesGroupedByName, nonLightHaPieces);

        // Step 2: Create Entity objects for grouped LIGHT entities
        for (Map.Entry<String, List<HomePieceOfFurniture>> entry : lightHaPiecesGroupedByName.entrySet()) {
            String haLightName = entry.getKey(); // e.g., "light.family_room"
            List<HomePieceOfFurniture> associatedLightPieces = entry.getValue();

            if (associatedLightPieces.isEmpty()) continue;

            // Create a single Entity for this light.* HA entity name
            Entity lightEntity = new Entity(settings, associatedLightPieces, resourceBundle);
            addCommonPropertyChangeListeners(lightEntity);

            // Determine if this lightEntity belongs to lightEntities or otherLevelsEntities
            boolean anyPieceOnSelectedLevel = associatedLightPieces.stream()
                .anyMatch(p -> home.getEnvironment().isAllLevelsVisible() || (p.getLevel() != null && p.getLevel() == home.getSelectedLevel()));

            // True if all SH3D pieces for this light.* HA entity are actual HomeLight instances
            // AND all these pieces are exclusively on other levels.
            boolean allPiecesAreSh3dLightsAndExclusivelyOnOtherLevels =
                associatedLightPieces.stream().allMatch(p -> p instanceof HomeLight) &&
                associatedLightPieces.stream().allMatch(p ->
                    !home.getEnvironment().isAllLevelsVisible() &&
                    (p.getLevel() != null && p.getLevel() != home.getSelectedLevel())
                );

            if (allPiecesAreSh3dLightsAndExclusivelyOnOtherLevels) {
                this.otherLevelsEntities.add(lightEntity);
            } else if (anyPieceOnSelectedLevel) {
                this.lightEntities.add(lightEntity);
            }
        }

        // Step 3: Create Entity objects for non-light HA entities (one Entity per piece)
        for (HomePieceOfFurniture nonLightPiece : nonLightHaPieces) {
            // Create an Entity for this single non-light piece.
            Entity otherEntity = new Entity(settings, Arrays.asList(nonLightPiece), resourceBundle);
            addCommonPropertyChangeListeners(otherEntity);

            boolean isOnSelectedLevel = home.getEnvironment().isAllLevelsVisible() ||
                                        (nonLightPiece.getLevel() != null && nonLightPiece.getLevel() == home.getSelectedLevel());

            if (isOnSelectedLevel) {
                this.otherEntities.add(otherEntity);
            }
            // Non-light entities on other levels are currently not added to primary lists.
        }

        // Step 4: Sort all lists
        Collections.sort(this.lightEntities);
        Collections.sort(this.otherEntities);
        Collections.sort(this.otherLevelsEntities);
    }

    private void buildLightsGroupsByRoom() {
        List<Room> homeRooms = home.getRooms();

        for (Room room : homeRooms) {
            if (!home.getEnvironment().isAllLevelsVisible() && room.getLevel() != home.getSelectedLevel())
                continue;
            String roomName = room.getName() != null ? room.getName() : room.getId();
            for (Entity entity : lightEntities) {
                HomePieceOfFurniture light = entity.getPiecesOfFurniture().get(0);
                if (room.containsPoint(light.getX(), light.getY(), 0) && room.getLevel() == light.getLevel()) {
                    if (!lightsGroups.containsKey(roomName))
                        lightsGroups.put(roomName, new ArrayList<>());
                    lightsGroups.get(roomName).add(entity);
                }
            }
        }
    }

    private void buildLightsGroupsByLight() {
        for (Entity entity : lightEntities) {
            lightsGroups.put(entity.getName(), new ArrayList<>());
            lightsGroups.get(entity.getName()).add(entity);
        }
    }

    private void buildLightsGroupsByHome() {
        lightsGroups.put("Home", new ArrayList<>());
        for (Entity entity : lightEntities)
            lightsGroups.get("Home").add(entity);
    }

    private void buildLightsGroups() {
        lightsGroups.clear();

        if (lightMixingMode == LightMixingMode.CSS)
            buildLightsGroupsByLight();
        else if (lightMixingMode == LightMixingMode.OVERLAY)
            buildLightsGroupsByRoom();
        else if (lightMixingMode == LightMixingMode.FULL)
            buildLightsGroupsByHome();
    }

    private void buildScenes() {
        int oldNumberOfTotaleRenders = getNumberOfTotalRenders();
        scenes = new Scenes(camera);
        scenes.setRenderingTimes(renderDateTimes);
        
        // --- MODIFIED: This filter is now more robust to prevent the OutOfMemoryError ---
        scenes.setEntitiesToShowOrHide(otherEntities.stream().filter(entity -> {
            String value = entity.getFurnitureDisplayValue();
            // We only care about entities that have an explicit, non-empty condition.
            // A value of "ALWAYS" or "NEVER" is handled by the scene's visibility logic, not by creating combinations.
            return !(value == null || value.trim().isEmpty() || value.equalsIgnoreCase("ALWAYS") || value.equalsIgnoreCase("NEVER"));
        }).collect(Collectors.toList()));
        
        propertyChangeSupport.firePropertyChange(Property.NUMBER_OF_RENDERS.name(), oldNumberOfTotaleRenders, getNumberOfTotalRenders());
    }

    private boolean isHomeAssistantEntity(String name) {
        List<String> sensorPrefixes = Arrays.asList(
            "air_quality.",
            "alarm_control_panel.",
            "assist_satellite.",
            "binary_sensor.",
            "button.",
            "camera.",
            "climate.",
            "cover.",
            "device_tracker.",
            "fan.",
            "humidifier.",
            "input_boolean.",
            "input_button.",
            "lawn_mower.",
            "light.",
            "lock.",
            "media_player.",
            "remote.",
            "sensor.",
            "siren.",
            "switch.",
            "todo.",
            "update.",
            "vacuum.",
            "valve.",
            "water_heater.",
            "weather."
        );

        if (name == null)
            return false;

        return sensorPrefixes.stream().anyMatch(name::startsWith);
    }

    private void build3dProjection() {
        cameraPosition = new Vector4d(camera.getX(), camera.getZ(), camera.getY(), 0);

        Transform3D yawRotation = new Transform3D();
        yawRotation.rotY(camera.getYaw());

        Transform3D pitchRotation = new Transform3D();
        pitchRotation.rotX(-camera.getPitch());

        perspectiveTransform = new Transform3D();
        perspectiveTransform.perspective(camera.getFieldOfView(), (double)renderWidth / renderHeight, 0.1, 100);
        perspectiveTransform.mul(pitchRotation);
        perspectiveTransform.mul(yawRotation);
    }

    private BufferedImage generateBaseRender(Scene scene, String imageName) throws IOException, InterruptedException {
        BufferedImage image = generateImage(new ArrayList<>(), imageName);
        return generateFloorPlanImage(image, image, imageName, false);
    }

    private String getFloorplanImageExtention() {
        if (this.lightMixingMode == LightMixingMode.OVERLAY)
            return "png";
        return this.imageFormat.name().toLowerCase();
    }

    private String generateGroupRenders(Scene scene, String group, BufferedImage baseImage) throws IOException, InterruptedException {
        List<Entity> groupLights = lightsGroups.get(group);

        List<List<Entity>> lightCombinations = getCombinations(groupLights);
        String yaml = "";
        for (List<Entity> onLights : lightCombinations) {
            String imageName = String.join("_", onLights.stream().map(Entity::getName).collect(Collectors.toList()));
            if (!scene.getName().isEmpty())
                imageName = scene.getName() + File.separator + imageName;
            BufferedImage image = generateImage(onLights, imageName);
            Entity firstLight = onLights.get(0);
            boolean createOverlayImage = lightMixingMode == LightMixingMode.OVERLAY || (lightMixingMode == LightMixingMode.CSS && firstLight.getIsRgb());
            BufferedImage floorPlanImage = generateFloorPlanImage(baseImage, image, imageName, createOverlayImage);
            if (firstLight.getIsRgb()) {
                generateRedTintedImage(floorPlanImage, imageName);
                yaml += generateRgbLightYaml(scene, firstLight, imageName);
            }
            else
                yaml += generateLightYaml(scene, groupLights, onLights, imageName);
        }
        return yaml;
    }

    private void generateTransparentImage(String fileName) throws IOException {
        BufferedImage image = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0);
        File imageFile = new File(fileName);
        ImageIO.write(image, "png", imageFile);
    }

    private void generateAndCacheEntityTransparentImage(String entityNameForFilename, int desiredWidth, int desiredHeight) throws IOException {
        String imageName = "transparent_" + entityNameForFilename;
        String filePath = outputFloorplanDirectoryName + File.separator + imageName + ".png";
        File imageFile = new File(filePath);

        if (imageFile.exists()) {
            if (useExistingRenders) {
                try {
                    BufferedImage existingImage = ImageIO.read(imageFile);
                    if (existingImage != null && existingImage.getWidth() == desiredWidth && existingImage.getHeight() == desiredHeight) {
                        // System.out.println("Skipping generation, existing transparent image " + filePath + " matches dimensions " + desiredWidth + "x" + desiredHeight);
                        return; // Exists with correct dimensions, and we want to use existing.
                    }
                    // If dimensions don't match, proceed to regenerate.
                    // System.out.println("Regenerating transparent image " + filePath + ", existing dimensions " +
                    // (existingImage != null ? existingImage.getWidth()+"x"+existingImage.getHeight() : "null") +
                    // " != desired " + desiredWidth + "x" + desiredHeight);
                } catch (IOException e) {
                    // Problem reading existing image, so proceed to regenerate.
                    System.err.println("Could not read existing transparent image " + filePath + " to check dimensions. Regenerating. Error: " + e.getMessage());
                }
            }
            // If not useExistingRenders, or if dimensions didn't match (or read failed),
            // we fall through to regenerate, overwriting the existing file.
        }

        // Create a transparent PNG with the desired aspect ratio (and small dimensions)
        BufferedImage image = new BufferedImage(desiredWidth, desiredHeight, BufferedImage.TYPE_INT_ARGB);
        // A new BufferedImage of TYPE_INT_ARGB is transparent by default.

        // Ensure the output directory exists
        Files.createDirectories(Paths.get(outputFloorplanDirectoryName));
        ImageIO.write(image, "png", imageFile);
        // System.out.println("Generated transparent image: " + filePath + " with dimensions " + desiredWidth + "x" + desiredHeight);
    }

    /**
     * Ensures a transparent PNG for the given entity exists with the specified dimensions.
     * The image will be named transparent_entityName.png.
     */
    public void ensureEntityTransparentImageGenerated(String entityName, int width, int height) throws IOException {
        generateAndCacheEntityTransparentImage(entityName, width, height);
    }

    private BufferedImage generateImage(List<Entity> onLights, String name) throws IOException, InterruptedException {
        String fileName = outputRendersDirectoryName + File.separator + name + ".png";

        if (useExistingRenders && Files.exists(Paths.get(fileName))) {
            propertyChangeSupport.firePropertyChange(Property.COMPLETED_RENDERS.name(), numberOfCompletedRenders, ++numberOfCompletedRenders);
            return ImageIO.read(Files.newInputStream(Paths.get(fileName)));
        }        
        final List<Entity> finalOnLights = new ArrayList<>(onLights); // Ensure effectively final for lambda
        try {
            SwingUtilities.invokeAndWait(() -> {
                prepareScene(finalOnLights);
            });
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Error preparing scene for image on EDT", e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Re-assert interrupt status
            throw e; // Propagate InterruptedException
        }

        BufferedImage image = renderScene();
        File imageFile = new File(fileName);
        ImageIO.write(image, "png", imageFile);
        propertyChangeSupport.firePropertyChange(Property.COMPLETED_RENDERS.name(), numberOfCompletedRenders, ++numberOfCompletedRenders);
        return image;
    }

    private void prepareScene(List<Entity> onLights) {
        for (Entity light : lightEntities)
            light.setLightPower(onLights.contains(light) || light.getAlwaysOn());
    }

    private BufferedImage renderScene() throws IOException, InterruptedException {
        Map<Renderer, String> rendererToClassName = new HashMap<Renderer, String>() {{
            put(Renderer.SUNFLOW, "com.eteks.sweethome3d.j3d.PhotoRenderer");
            put(Renderer.YAFARAY, "com.eteks.sweethome3d.j3d.YafarayRenderer");
        }};
        photoRenderer = AbstractPhotoRenderer.createInstance(
            rendererToClassName.get(renderer),
            home, null, this.quality == Quality.LOW ? AbstractPhotoRenderer.Quality.LOW : AbstractPhotoRenderer.Quality.HIGH);
        BufferedImage image = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_RGB);
        photoRenderer.render(image, camera, null);
        if (photoRenderer != null) {
            photoRenderer.dispose();
            photoRenderer = null;
        }
        if (Thread.interrupted())
            throw new InterruptedException();

        return image;
    }

    private BufferedImage generateFloorPlanImage(BufferedImage baseImage, BufferedImage image, String name, boolean createOverlayImage) throws IOException {
        String imageExtension = createOverlayImage ? "png" : getFloorplanImageExtention();
        File floorPlanFile = new File(outputFloorplanDirectoryName + File.separator + name + "." + imageExtension);

        if (!createOverlayImage) {
            ImageIO.write(image, imageExtension, floorPlanFile);
            return image;
        }

        BufferedImage overlay = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for(int x = 0; x < baseImage.getWidth(); x++) {
            for(int y = 0; y < baseImage.getHeight(); y++) {
                int diff = pixelDifference(baseImage.getRGB(x, y), image.getRGB(x, y));
                overlay.setRGB(x, y, diff > sensitivity ? image.getRGB(x, y) : 0);
            }
        }

        ImageIO.write(overlay, "png", floorPlanFile);
        return overlay;
    }

    private int pixelDifference(int first, int second) {
        int diff =
            Math.abs((first & 0xff) - (second & 0xff)) +
            Math.abs(((first >> 8) & 0xff) - ((second >> 8) & 0xff)) +
            Math.abs(((first >> 16) & 0xff) - ((second >> 16) & 0xff));
        return diff / 3;
    }

    private BufferedImage generateRedTintedImage(BufferedImage image, String imageName) throws IOException {
        File redTintedFile = new File(outputFloorplanDirectoryName + File.separator + imageName + ".red.png");
        BufferedImage tintedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for(int x = 0; x < image.getWidth(); x++) {
            for(int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                if (rgb == 0)
                    continue;
                Color original = new Color(rgb, true);
                float hsb[] = Color.RGBtoHSB(original.getRed(), original.getGreen(), original.getBlue(), null);
                Color redTint = Color.getHSBColor(1.0f, 0.75f, hsb[2]);
                tintedImage.setRGB(x, y, redTint.getRGB());
            }
        }

        ImageIO.write(tintedImage, "png", redTintedFile);
        return tintedImage;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[b >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[b & 0x0F];
        }
        return new String(hexChars);
    }

    public String renderHash(String imageName) throws IOException {
        return renderHash(imageName, false);
    }

    public String renderHash(String imageName, boolean forcePng) throws IOException {
        String imageExtension = forcePng ? "png" : getFloorplanImageExtention();
        byte[] content = Files.readAllBytes(Paths.get(outputFloorplanDirectoryName + File.separator + imageName + "." + imageExtension));
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(content);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Long.toString(System.currentTimeMillis() / 1000L);
        }
    }

    private String generateLightYaml(Scene scene, List<Entity> lights, List<Entity> onLights, String imageName) throws IOException {
        return generateLightYaml(scene, lights, onLights, imageName, true);
    }

    private String generateLightYaml(Scene scene, List<Entity> lights, List<Entity> onLights, String imageName, boolean includeMixBlend) throws IOException {
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
            conditions, normalizePath(imageName), getFloorplanImageExtention(), renderHash(imageName),
            includeMixBlend && lightMixingMode == LightMixingMode.CSS ? "          mix-blend-mode: lighten\n" : "");
    }

    private String generateRgbLightYaml(Scene scene, Entity light, String imageName) throws IOException {
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
            normalizePath(imageName), renderHash(imageName, true), normalizePath(imageName) + ".red", renderHash(imageName + ".red", true));
    }

    private String normalizePath(String fileName) {
        if (File.separator.equals("/"))
            return fileName;
        return fileName.replace('\\', '/');
    }

    private void turnOffLightsFromOtherLevels() {
        otherLevelsEntities.forEach(entity -> entity.setLightPower(false));
    }

    private void restoreEntityConfiguration() {
        Stream.of(lightEntities, otherEntities, otherLevelsEntities).flatMap(Collection::stream)
            .forEach(Entity::restoreConfiguration);
    }

    private void removeAlwaysOnLights(List<Entity> inputList) {
        ListIterator<Entity> iter = inputList.listIterator();

        while (iter.hasNext()) {
            if (iter.next().getAlwaysOn())
                iter.remove();
        }
    }

    public List<List<Entity>> getCombinations(List<Entity> inputSet) {
        List<List<Entity>> combinations = new ArrayList<>();
        List<Entity> inputList = new ArrayList<>(inputSet);

        removeAlwaysOnLights(inputList);
        _getCombinations(inputList, 0, new ArrayList<Entity>(), combinations);

        return combinations;
    }

    private void _getCombinations(List<Entity> inputList, int currentIndex, List<Entity> currentCombination, List<List<Entity>> combinations) {
        if (currentCombination.size() > 0)
            combinations.add(new ArrayList<>(currentCombination));

        for (int i = currentIndex; i < inputList.size(); i++) {
            currentCombination.add(inputList.get(i));
            _getCombinations(inputList, i + 1, currentCombination, combinations);
            currentCombination.remove(currentCombination.size() - 1);
        }
    }

    private Point2d getFurniture2dLocation(HomePieceOfFurniture piece) {
        if (this.houseNdcBounds == null) {
            System.err.println("Error: houseNdcBounds not calculated. Cannot get furniture 2D location.");
            // Fallback to a clearly problematic default or throw an exception
            return new Point2d(-1.0, -1.0); 
        }
        float levelOffset = piece.getLevel() != null ? piece.getLevel().getElevation() : 0;
        // Calculate the Y-coordinate of the top surface of the piece in world coordinates.
        // piece.getElevation() is relative to its level's floor.
        float pieceWorldY = piece.getElevation() + piece.getHeight() + levelOffset;

        // World coordinates of the point to project (center of the furniture piece)
        // (worldX, worldY_vertical, worldZ_depth, 1.0)
        Vector4d pointToProject = new Vector4d(piece.getX(), pieceWorldY, piece.getY(), 1.0);
        
        // Transform to view space and then to NDC, consistent with getRoomBoundingBoxPercent
        Vector4d ndcPos = new Vector4d(pointToProject);
        // cameraPosition is (camX_world, camZ_depth_world, camY_vertical_world, 0)
        ndcPos.sub(cameraPosition); // After sub, ndcPos.w is still 1.0 (because pointToProject.w=1, cameraPosition.w=0)
        perspectiveTransform.transform(ndcPos); // ndcPos.w is now perspective w'

        if (ndcPos.w == 0) { // Avoid division by zero
            System.err.println("Warning: NDC.w is zero for entity " + piece.getName() + 
                               " during getFurniture2dLocation. Defaulting to screen center.");
            return new Point2d(50.0, 50.0); 
        }
        ndcPos.scale(1.0 / ndcPos.w); // Perspective divide (NDC: -1 to 1 range)
        
        // Convert NDC to screen percentage (0% to 100%) for the entire viewport.
        // (ndcPos.x * 0.5 + 0.5) maps NDC X from [-1, 1] to screen X [0, 1]
        // (ndcPos.y * 0.5 + 0.5) maps NDC Y from [-1, 1] to screen Y [0, 1] (where 0% is top, 100% is bottom if Y points up in NDC)
        double screenXPercent = (ndcPos.x * 0.5 + 0.5) * 100.0;
        double screenYPercent = (ndcPos.y * 0.5 + 0.5) * 100.0; 
        return new Point2d(screenXPercent, screenYPercent);
    }


    private String generateEntitiesYaml() {
        // Combine light and other entities into a single list for sorting
        List<Entity> allEntities = Stream.concat(lightEntities.stream(), otherEntities.stream())
                                       .collect(Collectors.toList());
        Collections.sort(allEntities, new Comparator<Entity>() {
            @Override
            public int compare(Entity e1, Entity e2) {
                boolean e1IsAllNone = e1.getTapAction() == Entity.Action.NONE && e1.getDoubleTapAction() == Entity.Action.NONE && e1.getHoldAction() == Entity.Action.NONE;
                boolean e2IsAllNone = e2.getTapAction() == Entity.Action.NONE && e2.getDoubleTapAction() == Entity.Action.NONE && e2.getHoldAction() == Entity.Action.NONE;

                // Primary sort: Entities with all 'NONE' actions come first
                if (e1IsAllNone && !e2IsAllNone) return -1; // e1 comes first
                if (!e1IsAllNone && e2IsAllNone) return 1;  // e2 comes first

                // Secondary sort (if both are 'all none' or both are not 'all none'): ClickableAreaType
                // ROOM_SIZE comes before ENTITY_SIZE
                boolean e1IsRoomSize = e1.getClickableAreaType() == Entity.ClickableAreaType.ROOM_SIZE;
                boolean e2IsRoomSize = e2.getClickableAreaType() == Entity.ClickableAreaType.ROOM_SIZE;

                if (e1IsRoomSize && !e2IsRoomSize) return -1; // e1 (Room Size) comes before e2 (Not Room Size)
                if (!e1IsRoomSize && e2IsRoomSize) return 1;  // e2 (Room Size) comes before e1 (Not Room Size)

                // Tertiary sort: Natural order (name, then ID)
                return e1.compareTo(e2);
            }
        });

        return allEntities.stream()
            .map(entity -> entity.buildYaml(this)) // Pass controller instance
            .filter(yamlString -> yamlString != null && !yamlString.isEmpty()) // Filter out empty YAML strings
            .collect(Collectors.joining());
    }

    private void repositionEntities() {
        build3dProjection();
        calculateHouseNdcBounds(); // Calculate overall house bounds in NDC
        calculateEntityPositions();
        // moveEntityIconsToAvoidIntersection(); // Temporarily comment out to disable collision avoidance
    }

    private void calculateEntityPositions() {
        Stream.concat(lightEntities.stream(), otherEntities.stream())
            .forEach(entity -> {
                Point2d entityCenter = new Point2d();
                for (HomePieceOfFurniture piece : entity.getPiecesOfFurniture())
                    entityCenter.add(getFurniture2dLocation(piece));
                entityCenter.scale(1.0 / entity.getPiecesOfFurniture().size());

                entity.setPosition(entityCenter, false);
            });
    }

    private boolean doStateIconsIntersect(Entity first, Entity second) {
        final double STATE_ICON_RAIDUS_INCLUDING_MARGIN = 25.0;

        Point2d firstPositionInPixels = new Point2d(first.getPosition().x / 100.0 * renderWidth, first.getPosition().y / 100 * renderHeight);
        Point2d secondPositionInPixels = new Point2d(second.getPosition().x / 100.0 * renderWidth, second.getPosition().y / 100 * renderHeight);

        double x = Math.pow(firstPositionInPixels.x - secondPositionInPixels.x, 2) + Math.pow(firstPositionInPixels.y - secondPositionInPixels.y, 2);

        return x <= Math.pow(STATE_ICON_RAIDUS_INCLUDING_MARGIN * 2, 2);
    }

    private boolean doesStateIconIntersectWithSet(Entity entity, Set<Entity> entities) {
        for (Entity other : entities) {
            if (doStateIconsIntersect(entity, other))
                return true;
        }
        return false;
    }

    private Set<Entity> setWithWhichStateIconIntersects(Entity entity, List<Set<Entity>> entities) {
        for (Set<Entity> set : entities) {
            if (doesStateIconIntersectWithSet(entity, set))
                return set;
        }
        return null;
    }

    private Optional<Entity> stateIconWithWhichStateIconIntersects(Entity entity) {
        return Stream.concat(lightEntities.stream(), otherEntities.stream())
            .filter(other -> {
                if (entity == other)
                    return false;
                return doStateIconsIntersect(entity, other);
            }).findFirst();
    }

    private List<Set<Entity>> findIntersectingStateIcons() {
        List<Set<Entity>> intersectingStateIcons = new ArrayList<Set<Entity>>();

        Stream.concat(lightEntities.stream(), otherEntities.stream())
            .forEach(entity -> {
                Set<Entity> interectingSet = setWithWhichStateIconIntersects(entity, intersectingStateIcons);
                if (interectingSet != null) {
                    interectingSet.add(entity);
                    return;
                }
                Optional<Entity> intersectingStateIcon = stateIconWithWhichStateIconIntersects(entity);
                if (!intersectingStateIcon.isPresent())
                    return;
                Set<Entity> intersectingGroup = new HashSet<Entity>();
                intersectingGroup.add(entity);
                intersectingGroup.add(intersectingStateIcon.get());
                intersectingStateIcons.add(intersectingGroup);
            });

        return intersectingStateIcons;
    }

    private Point2d getCenterOfStateIcons(Set<Entity> entities) {
        Point2d centerPostition = new Point2d();
        for (Entity entity : entities )
            centerPostition.add(entity.getPosition());
        centerPostition.scale(1.0 / entities.size());
        return centerPostition;
    }

    private void separateStateIcons(Set<Entity> entities) {
        final double STEP_SIZE = 2.0;

        Point2d centerPostition = getCenterOfStateIcons(entities);

        for (Entity entity : entities) {
            Vector2d direction = new Vector2d(entity.getPosition().x - centerPostition.x, entity.getPosition().y - centerPostition.y);

            if (direction.length() == 0) {
                double[] randomRepeatableDirection = { entity.getId().hashCode(), entity.getName().hashCode() };
                direction.set(randomRepeatableDirection);
            }

            direction.normalize();
            direction.x = direction.x * (100.0 * (STEP_SIZE / renderWidth));
            direction.y = direction.y * (100.0 * (STEP_SIZE / renderHeight));
            entity.move(direction);
        }
    }

    private void moveEntityIconsToAvoidIntersection() {
        for (int i = 0; i < 100; i++) {
            List<Set<Entity>> intersectingStateIcons = findIntersectingStateIcons();
            if (intersectingStateIcons.size() == 0)
                break;
            for (Set<Entity> set : intersectingStateIcons)
                separateStateIcons(set);
        }
    }

    private boolean doAreasOverlap(Map<String, Double> rectA, Map<String, Double> rectB) {
        if (rectA == null || rectB == null) {
            return false;
        }

        double aLeft = rectA.get("left");
        double aTop = rectA.get("top");
        double aWidth = rectA.get("width");
        double aHeight = rectA.get("height");

        double bLeft = rectB.get("left");
        double bTop = rectB.get("top");
        double bWidth = rectB.get("width");
        double bHeight = rectB.get("height");

        // Check for non-overlap conditions first for clarity
        if (aLeft + aWidth <= bLeft || aLeft >= bLeft + bWidth || aTop + aHeight <= bTop || aTop >= bTop + bHeight) {
            return false; // No overlap
        }
        return true; // Overlap
    }

    private List<String> checkForOverlappingRoomSizeEntities() {
        List<String> errors = new ArrayList<>();
        List<Entity> roomSizeEntities = getAllConfiguredEntities().stream()
                                            .filter(e -> e.getClickableAreaType() == Entity.ClickableAreaType.ROOM_SIZE)
                                            .collect(Collectors.toList());

        for (int i = 0; i < roomSizeEntities.size(); i++) {
            for (int j = i + 1; j < roomSizeEntities.size(); j++) {
                Entity entityA = roomSizeEntities.get(i);
                Entity entityB = roomSizeEntities.get(j);
                Map<String, Double> boundsA = getRoomBoundingBoxPercent(entityA); // Uses current controller state
                Map<String, Double> boundsB = getRoomBoundingBoxPercent(entityB); // Uses current controller state

                if (doAreasOverlap(boundsA, boundsB)) {
                    errors.add(String.format(Locale.US, 
                        "Entity '%s' and Entity '%s'",
                        entityA.getName(), entityB.getName()));
                }
            }
        }
        return errors;
    }

    public List<Entity> getAllConfiguredEntities() {
        return Stream.concat(lightEntities.stream(), otherEntities.stream()).collect(Collectors.toList());
    }

    private void applySelectedCameraToWorkingCamera() {
        Camera baseCameraToUse = null;
        // Always use the current home camera as the base.
        baseCameraToUse = home.getCamera();

        // Update this.camera to reflect baseCameraToUse.
        // clone() is known to be available on Camera objects,
        // and clone() is known to be available on Camera objects,
        // we replace this.camera with a clone of baseCameraToUse.
        // this.camera is guaranteed to be non-null here due to initialization in the constructor.
        this.camera = baseCameraToUse.clone();
    }

    public Room getRoomForEntity(Entity entity) {
        if (entity == null || entity.getPiecesOfFurniture().isEmpty()) {
            return null;
        }
        HomePieceOfFurniture firstPiece = entity.getPiecesOfFurniture().get(0);

        // Get the furniture's 2D world coordinates (X and Y-depth)
        float furnitureCheckX = firstPiece.getX();
        float furnitureCheckY_depth = firstPiece.getY(); 

        // Iterate through all rooms. For each room, check if the furniture's 2D (X, Y-depth)
        // coordinates fall within the room's 2D area by testing against the room's floor level.
        for (Room room : home.getRooms()) {
            // Ensure we are checking rooms on the same level as the furniture piece.
            if (firstPiece.getLevel() == room.getLevel()) {
                // For the Z coordinate in containsPoint, use the room's floor elevation.
                // Add a very small epsilon to ensure the point is slightly above the floor plane,
                // which can help with floating point precision in some containment checks.
                float roomFloorZ = room.getLevel().getElevation() + 0.01f; 

                if (room.containsPoint(furnitureCheckX, furnitureCheckY_depth, roomFloorZ)) {
                    return room;
                }
            }
        }
        return null; // Entity not found in any room
    }

    private Point3f getEntity3DCentroid(Entity entity) {
        if (entity.getPiecesOfFurniture().isEmpty()) {
            return null;
        }
        float sumX = 0, sumY_depth = 0, sumZ_vertical = 0;
        int count = 0;
        for (HomePieceOfFurniture piece : entity.getPiecesOfFurniture()) {
            float pieceLevelElevation = (piece.getLevel() != null ? piece.getLevel().getElevation() : 0);
            sumX += piece.getX();
            sumY_depth += piece.getY(); // SH3D Y-axis is depth
            // Calculate vertical center of the piece in world coordinates
            sumZ_vertical += pieceLevelElevation + piece.getElevation() + (piece.getHeight() / 2.0f);
            count++;
        }
        if (count == 0) { // Should not happen if piecesOfFurniture is not empty
            return null;
        }
        // Return Point3f(worldX, worldY_depth, worldZ_vertical)
        return new Point3f(sumX / count, sumY_depth / count, sumZ_vertical / count);
    }

    private void calculateHouseNdcBounds() {
        if (home == null || perspectiveTransform == null || cameraPosition == null) {
            this.houseNdcBounds = null;
            return;
        }

        List<Vector4d> allHousePoints = new ArrayList<>();
        boolean allLevelsVisible = home.getEnvironment().isAllLevelsVisible();
        com.eteks.sweethome3d.model.Level selectedLevel = home.getSelectedLevel();

        for (Room room : home.getRooms()) {
            if (!allLevelsVisible && room.getLevel() != selectedLevel) {
                continue; // Skip rooms not on the currently visible/selected level
            }
            if (room.getPoints() == null || room.getPoints().length == 0) continue;

            float roomBaseElevation = room.getLevel() != null ? room.getLevel().getElevation() : 0;
            float actualRoomHeight;
            if (room.getLevel() != null) {
                actualRoomHeight = room.getLevel().getHeight();
                if (actualRoomHeight <= 0) {
                    actualRoomHeight = home.getWallHeight();
                }
            } else {
                actualRoomHeight = home.getWallHeight();
            }
            float roomCeilingElevation = roomBaseElevation + actualRoomHeight;

            for (float[] p2d : room.getPoints()) {
                allHousePoints.add(new Vector4d(p2d[0], roomBaseElevation, p2d[1], 1.0));
                if (actualRoomHeight > 0 && room.isCeilingVisible()) { 
                     allHousePoints.add(new Vector4d(p2d[0], roomCeilingElevation, p2d[1], 1.0));
                }
            }
        }

        if (allHousePoints.isEmpty()) {
            this.houseNdcBounds = createDefaultNdcBounds();
            System.err.println("Warning: No points found to calculate house NDC bounds. Defaulting to full view.");
            return;
        }

        double minNdcX = Double.POSITIVE_INFINITY, maxNdcX = Double.NEGATIVE_INFINITY;
        double minNdcY = Double.POSITIVE_INFINITY, maxNdcY = Double.NEGATIVE_INFINITY;

        for (Vector4d worldPos : allHousePoints) {
            Vector4d ndcPos = new Vector4d(worldPos);
            ndcPos.sub(cameraPosition);
            perspectiveTransform.transform(ndcPos);

            if (ndcPos.w == 0 || Double.isNaN(ndcPos.w) || Double.isInfinite(ndcPos.w)) continue;
            ndcPos.scale(1.0 / ndcPos.w); 

            minNdcX = Math.min(minNdcX, ndcPos.x);
            maxNdcX = Math.max(maxNdcX, ndcPos.x);
            minNdcY = Math.min(minNdcY, ndcPos.y);
            maxNdcY = Math.max(maxNdcY, ndcPos.y);
        }
        
        if (Double.isInfinite(minNdcX) || Double.isInfinite(maxNdcX) || 
            Double.isInfinite(minNdcY) || Double.isInfinite(maxNdcY) ||
            minNdcX >= maxNdcX || minNdcY >= maxNdcY) { 
            this.houseNdcBounds = createDefaultNdcBounds();
            System.err.println("DEBUG: Invalid house NDC bounds calculated. Defaulting to full view. MinX=" + minNdcX + ", MaxX=" + maxNdcX + ", MinY=" + minNdcY + ", MaxY=" + maxNdcY);
            System.out.println("DEBUG: Using DEFAULT houseNdcBounds: minX=" + this.houseNdcBounds.get("minX") + 
                               ", maxX=" + this.houseNdcBounds.get("maxX") + 
                               ", minY=" + this.houseNdcBounds.get("minY") + 
                               ", maxY=" + this.houseNdcBounds.get("maxY"));
        } else {
            this.houseNdcBounds = new HashMap<>();
            this.houseNdcBounds.put("minX", minNdcX);
            this.houseNdcBounds.put("maxX", maxNdcX);
            this.houseNdcBounds.put("minY", minNdcY);
            this.houseNdcBounds.put("maxY", maxNdcY);
            System.out.println("DEBUG: Calculated houseNdcBounds: minX=" + minNdcX + 
                               ", maxX=" + maxNdcX + 
                               ", minY=" + minNdcY + 
                               ", maxY=" + maxNdcY);
        }
    }

    private Map<String, Double> createDefaultNdcBounds() {
        Map<String, Double> defaultBounds = new HashMap<>();
        defaultBounds.put("minX", -1.0);
        defaultBounds.put("maxX", 1.0);
        defaultBounds.put("minY", -1.0);
        defaultBounds.put("maxY", 1.0);
        return defaultBounds;
    }
};
