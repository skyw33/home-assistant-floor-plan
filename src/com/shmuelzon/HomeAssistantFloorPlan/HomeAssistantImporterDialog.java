package com.shmuelzon.HomeAssistantFloorPlan;

import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.concurrent.ExecutionException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.stream.Collectors;

public class HomeAssistantImporterDialog extends JDialog {
    private final Settings settings;
    private final Controller controller;
    private JTextField urlTextField;
    private JPasswordField tokenField;
    private DefaultListModel<HaEntity> entityListModel;
    private JTree importHaEntitiesTree;
    private DefaultTreeModel treeModel; // New field for the tree model
    private DefaultMutableTreeNode rootNode; // New field for the root of the tree
    private JButton fetchButton;
    private JButton importButton;
    private JTabbedPane tabbedPane; // New JTabbedPane
    private JTree associateHaEntitiesTree;

    // Components for the "Associate Existing Furniture" tab
    private JTree sh3dFurnitureTree;
    private DefaultTreeModel sh3dFurnitureTreeModel;
    private DefaultMutableTreeNode sh3dFurnitureRootNode;
    private JButton associateButton;
    private JButton cancelButton; // Declare cancelButton as a field
    private JButton selectLightsButton;
    private JButton selectSwitchesButton;
    private JButton clearSelectionButton;
    private StickyHeaderPanel stickyHeaderPanel; // The new sticky header component
    private Set<String> existingEntityIds; // New field to store IDs of already imported entities

    private JLabel statusLabel; // New status label

    public HomeAssistantImporterDialog(Window owner, UserPreferences preferences, Controller controller) {
        super(owner, "Import from Home Assistant", ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.settings = new Settings(controller.getHome());
        ResourceBundle resource = controller.getResourceBundle();

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Home Assistant URL:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        urlTextField = new JTextField(settings.get(Settings.CONTROLLER_HA_URL, "http://homeassistant.local:8123"), 40);
        mainPanel.add(urlTextField, gbc);

        // Token
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("Long-Lived Access Token:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        tokenField = new JPasswordField(settings.get(Settings.CONTROLLER_HA_TOKEN, ""), 40);
        mainPanel.add(tokenField, gbc);

        // Status Label
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusLabel = new JLabel("Enter connection details and click 'Fetch Entities'.");
        mainPanel.add(statusLabel, gbc);

        // --- NEW: Selection Helper Panel ---
        JPanel importSelectionHelperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        selectLightsButton = new JButton("Select Lights");
        selectSwitchesButton = new JButton("Select Switches");
        clearSelectionButton = new JButton("Clear Selection");
        importSelectionHelperPanel.add(selectLightsButton);
        importSelectionHelperPanel.add(selectSwitchesButton);
        importSelectionHelperPanel.add(clearSelectionButton);

        // --- NEW: JTabbedPane ---
        tabbedPane = new JTabbedPane();

        // --- Tab 1: Import New Entities (Existing functionality) ---
        JPanel importNewEntitiesPanel = new JPanel(new GridBagLayout());
        GridBagConstraints importGbc = new GridBagConstraints();
        importGbc.insets = new Insets(5, 5, 5, 5);
        importGbc.fill = GridBagConstraints.HORIZONTAL;
        importGbc.gridx = 0; importGbc.gridy = 0; importGbc.gridwidth = 2;
        importGbc.weightx = 1.0;
        importNewEntitiesPanel.add(importSelectionHelperPanel, importGbc);

        importGbc.gridy = 1; importGbc.weighty = 1.0; importGbc.fill = GridBagConstraints.BOTH;
        rootNode = new DefaultMutableTreeNode("Home Assistant Entities"); // Hidden root node
        treeModel = new DefaultTreeModel(rootNode);
        HaEntityTreeCellRenderer haEntityRenderer = new HaEntityTreeCellRenderer(); // Create one renderer to share
        importHaEntitiesTree = new JTree(treeModel);
        importHaEntitiesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION); // Allow multiple selections
        importHaEntitiesTree.setRootVisible(false); // Hide the root node
        importHaEntitiesTree.setShowsRootHandles(true); // Show handles for top-level nodes
        importHaEntitiesTree.setCellRenderer(haEntityRenderer); // Set custom renderer

        JScrollPane scrollPane = new JScrollPane(importHaEntitiesTree);
        stickyHeaderPanel = new StickyHeaderPanel();
        scrollPane.setColumnHeaderView(stickyHeaderPanel); // This is the standard and correct way
        // Listen for scroll events to update the header
        scrollPane.getViewport().addChangeListener(e -> stickyHeaderPanel.updateHeader());
        importNewEntitiesPanel.add(scrollPane, importGbc);

        // Add the import button specifically to this tab
        importGbc.gridy = 2;
        importGbc.weighty = 0;
        importGbc.fill = GridBagConstraints.NONE;
        importGbc.anchor = GridBagConstraints.CENTER;
        importButton = new JButton("Import Selected");
        importNewEntitiesPanel.add(importButton, importGbc);
        tabbedPane.addTab("Import New Entities", importNewEntitiesPanel);

        // --- Tab 2: Associate Existing Furniture ---
        JPanel associateExistingPanel = new JPanel(new GridBagLayout());
        GridBagConstraints associateGbc = new GridBagConstraints();
        associateGbc.insets = new Insets(5, 5, 5, 5);
        associateGbc.fill = GridBagConstraints.BOTH;

        // SH3D Furniture Tree
        associateGbc.gridx = 0; associateGbc.gridy = 0; associateGbc.weightx = 0.5; associateGbc.weighty = 1.0;
        sh3dFurnitureRootNode = new DefaultMutableTreeNode("Existing Furniture");
        sh3dFurnitureTreeModel = new DefaultTreeModel(sh3dFurnitureRootNode);
        sh3dFurnitureTree = new JTree(sh3dFurnitureTreeModel);
        sh3dFurnitureTree.setRootVisible(false);
        sh3dFurnitureTree.setShowsRootHandles(true);
        sh3dFurnitureTree.setCellRenderer(new Sh3dFurnitureTreeCellRenderer()); // Custom renderer
        associateExistingPanel.add(new JScrollPane(sh3dFurnitureTree), associateGbc);

        // HA Entities Tree for the second tab
        associateGbc.gridx = 1; associateGbc.gridy = 0; associateGbc.weightx = 0.5; associateGbc.weighty = 1.0;
        associateHaEntitiesTree = new JTree(treeModel); // New instance, same model
        associateHaEntitiesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION); // Only one HA entity at a time for association
        associateHaEntitiesTree.setRootVisible(false);
        associateHaEntitiesTree.setShowsRootHandles(true);
        associateHaEntitiesTree.setCellRenderer(haEntityRenderer); // Share the renderer
        associateExistingPanel.add(new JScrollPane(associateHaEntitiesTree), associateGbc);

        // Associate Button
        associateGbc.gridx = 0; associateGbc.gridy = 1; associateGbc.gridwidth = 2; associateGbc.weightx = 0; associateGbc.weighty = 0;
        associateGbc.fill = GridBagConstraints.NONE; associateGbc.anchor = GridBagConstraints.CENTER;
        associateButton = new JButton("Associate Selected");
        associateButton.addActionListener(e -> associateSelected());
        associateExistingPanel.add(associateButton, associateGbc);

        tabbedPane.addTab("Associate Existing Furniture", associateExistingPanel);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(tabbedPane, gbc); // Add the tabbed pane to the main panel

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        fetchButton = new JButton("Fetch Entities");
        cancelButton = new JButton("Cancel");
        
        buttonPanel.add(fetchButton);
        buttonPanel.add(cancelButton);

        fetchButton.addActionListener(e -> fetchEntities());
        cancelButton.addActionListener(e -> dispose());
        importButton.addActionListener(e -> importSelectedEntities()); // The listener is still valid
        // Add listeners for new helper buttons
        selectLightsButton.addActionListener(e -> selectEntitiesByDomain("light"));
        selectSwitchesButton.addActionListener(e -> selectEntitiesByDomain("switch"));
        clearSelectionButton.addActionListener(e -> clearTreeSelection());

        // Initially disable import button until entities are fetched
        importButton.setEnabled(false);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void fetchEntities() {
        String url = urlTextField.getText();
        String token = new String(tokenField.getPassword());

        if (url.isEmpty() || token.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both URL and Access Token.", "Missing Information", JOptionPane.WARNING_MESSAGE);
            return;
        }

        settings.set(Settings.CONTROLLER_HA_URL, url);
        settings.set(Settings.CONTROLLER_HA_TOKEN, token);

        // Disable UI during fetch
        setUiEnabled(false);
        statusLabel.setText("Fetching entities from Home Assistant...");

        new SwingWorker<List<HaEntity>, Void>() {
            @Override
            protected List<HaEntity> doInBackground() throws Exception {
                HomeAssistantApiClient apiClient = new HomeAssistantApiClient(url, token);
                return apiClient.fetchEntities(); // This fetches all HA entities
            }

            @Override
            protected void done() {
                setUiEnabled(true);
                // Get existing entity IDs from the controller to mark them as already imported
                existingEntityIds = controller.getAllConfiguredEntities().stream()
                                              .map(Entity::getName) // Assuming Entity.getName() returns the HA entity_id
                                              .collect(Collectors.toSet());
                
                // --- NEW: Populate unassociated SH3D furniture tree ---
                populateUnassociatedSh3dFurnitureTree();

                rootNode.removeAllChildren(); // Clear existing nodes
                try {
                    List<HaEntity> fetchedEntities = get();
                    if (fetchedEntities.isEmpty()) {
                        statusLabel.setText("No entities found or connection failed.");
                        importButton.setEnabled(false);
                    } else { // Build the tree structure for new entities
                        buildTree(fetchedEntities);
                        long newCount = fetchedEntities.stream().filter(haEntity -> !existingEntityIds.contains(haEntity.getEntityId())).count();
                        statusLabel.setText(String.format("%d entities fetched (%d new). Select entities to import.", fetchedEntities.size(), newCount));

                        importButton.setEnabled(true);

                        // --- NEW: Force an initial update of the sticky header ---
                        if (stickyHeaderPanel != null) {
                            stickyHeaderPanel.updateHeader();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Fetching interrupted.");
                    JOptionPane.showMessageDialog(HomeAssistantImporterDialog.this, "Fetching entities was interrupted.", "Interrupted", JOptionPane.WARNING_MESSAGE);
                    importButton.setEnabled(false);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLabel.setText("Error fetching entities.");
                    JOptionPane.showMessageDialog(HomeAssistantImporterDialog.this, "Error connecting to Home Assistant: " + cause.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                    importButton.setEnabled(false);
                    cause.printStackTrace(); // Log the full stack trace for debugging
                }
            }
        }.execute();
    }

    /**
     * Builds the JTree structure from the fetched HaEntity list.
     * The hierarchy is Area -> Domain -> Entity.
     */
    private void buildTree(List<HaEntity> fetchedEntities) {
        String currentArea = null;
        DefaultMutableTreeNode areaNode = null;
        String currentDomain = null;
        DefaultMutableTreeNode domainNode = null;

        for (HaEntity entity : fetchedEntities) {
            String areaName = entity.getAreaName();
            if (areaName == null || areaName.trim().isEmpty() || areaName.equals("zzz_No Area")) {
                areaName = "No Area Assigned";
            }

            if (!areaName.equals(currentArea)) {
                areaNode = new DefaultMutableTreeNode(areaName);
                rootNode.add(areaNode);
                currentArea = areaName;
                currentDomain = null; // Reset domain when area changes
            }

            String domain = entity.getDomain();
            if (!domain.equals(currentDomain)) {
                domainNode = new DefaultMutableTreeNode(domain);
                if (areaNode != null) {
                    areaNode.add(domainNode);
                }
                currentDomain = domain;
            }

            if (domainNode != null) {
                domainNode.add(new DefaultMutableTreeNode(entity)); // Add HaEntity as a user object
            }
        }
        treeModel.reload(); // Notify tree model of changes

        // Expand all nodes by default for initial visibility
        for (int i = 0; i < importHaEntitiesTree.getRowCount(); i++) {
            importHaEntitiesTree.expandRow(i);
        }
        // Also expand the tree on the second tab
        for (int i = 0; i < associateHaEntitiesTree.getRowCount(); i++) {
            associateHaEntitiesTree.expandRow(i);
        }
    }

    /**
     * Populates the JTree with existing Sweet Home 3D furniture that is not yet associated with HA entities.
     */
    private void populateUnassociatedSh3dFurnitureTree() {
        sh3dFurnitureRootNode.removeAllChildren();
        List<HomePieceOfFurniture> unassociatedFurniture = controller.getUnassociatedSh3dFurniture();

        // Group by room for better organization
        Map<String, DefaultMutableTreeNode> roomNodes = new HashMap<>();
        for (HomePieceOfFurniture piece : unassociatedFurniture) {
            String roomName = "No Room Assigned";
            Room room = controller.getRoomForSh3dPiece(piece); // Assuming a method to get room for a SH3D piece
            if (room != null && room.getName() != null && !room.getName().trim().isEmpty()) {
                roomName = room.getName();
            }

            DefaultMutableTreeNode roomNode = roomNodes.computeIfAbsent(roomName, k -> {
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(k);
                sh3dFurnitureRootNode.add(newNode);
                return newNode;
            });
            roomNode.add(new DefaultMutableTreeNode(piece)); // Store HomePieceOfFurniture as user object
        }
        sh3dFurnitureTreeModel.reload();
        // Expand all nodes by default
        for (int i = 0; i < sh3dFurnitureTree.getRowCount(); i++) {
            sh3dFurnitureTree.expandRow(i);
        }
    }

    /**
     * Handles the association of a selected SH3D furniture piece with a selected HA entity.
     */
    private void associateSelected() {
        TreePath selectedSh3dPath = sh3dFurnitureTree.getSelectionPath();
        TreePath selectedHaPath = associateHaEntitiesTree.getSelectionPath();

        if (selectedSh3dPath == null || selectedHaPath == null) {
            JOptionPane.showMessageDialog(this, "Please select one existing furniture piece and one Home Assistant entity to associate.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultMutableTreeNode sh3dNode = (DefaultMutableTreeNode) selectedSh3dPath.getLastPathComponent();
        DefaultMutableTreeNode haNode = (DefaultMutableTreeNode) selectedHaPath.getLastPathComponent();

        if (!(sh3dNode.getUserObject() instanceof HomePieceOfFurniture) || !(haNode.getUserObject() instanceof HaEntity)) {
            JOptionPane.showMessageDialog(this, "Please select a specific furniture piece and a specific Home Assistant entity (not a group).", "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        HomePieceOfFurniture sh3dPiece = (HomePieceOfFurniture) sh3dNode.getUserObject();
        HaEntity haEntity = (HaEntity) haNode.getUserObject();

        // Check if the HA entity is already associated with something else
        if (existingEntityIds.contains(haEntity.getEntityId())) {
            JOptionPane.showMessageDialog(this, "The Home Assistant entity '" + haEntity.getEntityId() + "' is already associated with another piece of furniture.", "Already Associated", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Perform the association
        controller.associateFurnitureWithHaEntity(sh3dPiece, haEntity);

        // Refresh both trees to reflect the changes
        // Re-fetch existing entities to update the greyed-out status
        existingEntityIds = controller.getAllConfiguredEntities().stream()
                                      .map(Entity::getName)
                                      .collect(Collectors.toSet());
        populateUnassociatedSh3dFurnitureTree(); // Refresh SH3D tree
        // Rebuild the HA entities tree to remove the now-associated entity
        // This requires re-fetching from HA or filtering the existing fetched list.
        // For now, we'll just rely on the grey-out in the HA entities tree.
        // A full refresh of HA entities would require another API call, which might be slow.
        // Let's just update the status and rely on grey-out. Both trees will repaint to show the new greyed-out status.
        statusLabel.setText("Associated '" + sh3dPiece.getName() + "' with '" + haEntity.getEntityId() + "'.");
        importHaEntitiesTree.repaint(); // Repaint tree on the "Import" tab
        associateHaEntitiesTree.repaint(); // Repaint tree on the "Associate" tab
        
        JOptionPane.showMessageDialog(this, "Association complete. The furniture piece's name has been updated in Sweet Home 3D.", "Association Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void importSelectedEntities() {
        List<HaEntity> selectedEntities = getSelectedHaEntities();
        if (selectedEntities.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one entity to import.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Filter out already existing entities from the selection
        List<HaEntity> entitiesToActuallyImport = selectedEntities.stream()
            .filter(haEntity -> existingEntityIds == null || !existingEntityIds.contains(haEntity.getEntityId()))
            .collect(Collectors.toList());

        if (entitiesToActuallyImport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All selected entities are already imported.", "No New Entities", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Pass the filtered list to the controller
        controller.importHaEntities(entitiesToActuallyImport);
        
        // After import, close the dialog
        dispose();
    }

    /**
     * Selects all leaf nodes in the tree that match the given entity domain.
     * @param domain The domain to select (e.g., "light", "switch").
     */
    private void selectEntitiesByDomain(String domain) {
        importHaEntitiesTree.clearSelection(); // Clear previous selection first

        Enumeration<?> enumeration = rootNode.depthFirstEnumeration();
        List<TreePath> pathsToSelect = new ArrayList<>();

        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            if (node.isLeaf() && node.getUserObject() instanceof HaEntity) {
                HaEntity entity = (HaEntity) node.getUserObject();
                // --- MODIFIED: Only select if the domain matches AND the entity is not already imported ---
                if (domain.equals(entity.getDomain()) && (existingEntityIds == null || !existingEntityIds.contains(entity.getEntityId()))) {
                    pathsToSelect.add(new TreePath(node.getPath()));
                }
            }
        }

        importHaEntitiesTree.setSelectionPaths(pathsToSelect.toArray(new TreePath[0]));

        // Scroll to the first selected item to make it visible
        if (!pathsToSelect.isEmpty()) {
            importHaEntitiesTree.scrollPathToVisible(pathsToSelect.get(0));
        }
    }

    private void clearTreeSelection() {
        importHaEntitiesTree.clearSelection();
    }

    /**
     * Retrieves the list of selected HaEntity objects from the JTree.
     * Only leaf nodes (actual entities) are considered.
     * @return A list of selected HaEntity objects.
     */
    private List<HaEntity> getSelectedHaEntities() {
        List<HaEntity> selected = new ArrayList<>();
        TreePath[] selectionPaths = importHaEntitiesTree.getSelectionPaths();
        if (selectionPaths != null) {
            for (TreePath path : selectionPaths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.isLeaf() && node.getUserObject() instanceof HaEntity) {
                    selected.add((HaEntity) node.getUserObject());
                }
            }
        }
        return selected;
    }

    private void setUiEnabled(boolean enabled) {
        urlTextField.setEnabled(enabled);
        tokenField.setEnabled(enabled);
        fetchButton.setEnabled(enabled); // For both tabs
        associateButton.setEnabled(enabled); // For the associate tab
        selectLightsButton.setEnabled(enabled);
        selectSwitchesButton.setEnabled(enabled);
        clearSelectionButton.setEnabled(enabled);
        importHaEntitiesTree.setEnabled(enabled);
        associateHaEntitiesTree.setEnabled(enabled);
        sh3dFurnitureTree.setEnabled(enabled);
        // Import button enabled state is managed separately after fetch
        // importButton.setEnabled(enabled);
        // Cancel button always enabled
    }

    /**
     * A custom panel that acts as a "sticky header" at the top of the scroll pane,
     * displaying the current Area (room) name.
     */
    private class StickyHeaderPanel extends JPanel {
        private final JLabel headerLabel;
        private int yOffset = 0; // Store the offset as a field

        public StickyHeaderPanel() { // This is for the newEntitiesTree
            setLayout(new BorderLayout()); // A simple layout is fine
            setOpaque(true); // The panel itself should be opaque
            setBackground(UIManager.getColor("TableHeader.background")); // Match header background

            headerLabel = new JLabel(" ");
            headerLabel.setOpaque(false); // The label itself is not opaque, we just use it for rendering properties
            // Style the label to look like a standard table header
            headerLabel.setForeground(UIManager.getColor("TableHeader.foreground"));
            headerLabel.setFont(UIManager.getFont("TableHeader.font"));
            headerLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            add(headerLabel, BorderLayout.CENTER); // Add it to the panel
        }

        @Override
        public Dimension getPreferredSize() {
            // The panel's height is always the label's preferred height
            return headerLabel.getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            // We override paintComponent to control drawing position
            // This allows the "push up" effect without clipping.
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());

            // Temporarily move the graphics origin to draw the label
            g.translate(0, yOffset);
            headerLabel.paint(g);
            g.translate(0, -yOffset);
        }

        public void updateHeader() {
            // Ensure the tree (importHaEntitiesTree) is visible and has a parent (viewport)
            if (importHaEntitiesTree == null || importHaEntitiesTree.getParent() == null || !(importHaEntitiesTree.getParent() instanceof JViewport)) {
                if (headerLabel.isVisible()) {
                    headerLabel.setVisible(false);
                    repaint();
                }
                return;
            }

            JViewport viewport = (JViewport) importHaEntitiesTree.getParent();
            Point viewPosition = viewport.getViewPosition();
            // Use getClosestPathForLocation for more robustness near node boundaries
            TreePath path = importHaEntitiesTree.getClosestPathForLocation(viewPosition.x, viewPosition.y);

            if (path == null) {
                if (headerLabel.isVisible()) {
                    headerLabel.setVisible(false);
                    repaint();
                }
                return;
            }

            DefaultMutableTreeNode areaNode = findAreaNode(path);
            if (areaNode == null) {
                if (headerLabel.isVisible()) {
                    headerLabel.setVisible(false);
                    repaint();
                }
                return;
            }

            // Update label text and visibility
            // --- MODIFIED: Construct header text with Area and Domain ---
            String headerText = areaNode.getUserObject().toString();
            DefaultMutableTreeNode domainNode = findDomainNode(path);
            if (domainNode != null) {
                // Using an arrow character for better visual separation
                headerText += " → " + domainNode.getUserObject().toString();
            }
            headerLabel.setText(headerText);
            if (!headerLabel.isVisible()) {
                headerLabel.setVisible(true);
            }

            // Calculate the "push up" offset
            int newYOffset = 0;
            int areaNodeIndex = rootNode.getIndex(areaNode);
            if (areaNodeIndex < rootNode.getChildCount() - 1) {
                DefaultMutableTreeNode nextAreaNode = (DefaultMutableTreeNode) rootNode.getChildAt(areaNodeIndex + 1);
                Rectangle nextAreaBounds = importHaEntitiesTree.getPathBounds(new TreePath(nextAreaNode.getPath()));

                if (nextAreaBounds != null) {
                    int headerHeight = getPreferredSize().height;
                    int nextHeaderY = nextAreaBounds.y - viewPosition.y;
                    if (nextHeaderY < headerHeight) {
                        newYOffset = nextHeaderY - headerHeight;
                    }
                }
            }

            // If the offset changed, trigger a repaint of this header panel
            if (newYOffset != this.yOffset) {
                this.yOffset = newYOffset;
                this.repaint();
            }
        }

        private DefaultMutableTreeNode findAreaNode(TreePath path) {
            if (path == null) return null;
            Object[] pathComponents = path.getPath();
            // The Area node is the first child of the root node in the path (index 1)
            if (pathComponents.length > 1) {
                return (DefaultMutableTreeNode) pathComponents[1];
            }
            return null;
        }

        private DefaultMutableTreeNode findDomainNode(TreePath path) {
            if (path == null) return null;
            Object[] pathComponents = path.getPath();
            // The Domain node is the second child of the root node in the path (index 2)
            if (pathComponents.length > 2) {
                return (DefaultMutableTreeNode) pathComponents[2];
            }
            return null;
        }
    }
    
    // Custom TreeCellRenderer for SH3D Furniture Tree
    private class Sh3dFurnitureTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof HomePieceOfFurniture) {
                HomePieceOfFurniture piece = (HomePieceOfFurniture) userObject;
                setText(piece.getName() + " (" + piece.getDescription() + ")"); // Show name and description
                // You could add icons based on piece type if desired
            } else if (userObject instanceof String) {
                // This handles Room names
                setText((String) userObject);
            }
            return this;
        }
    }

    // Custom TreeCellRenderer to display HaEntity objects and hierarchical nodes
    private class HaEntityTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof HaEntity) {
                HaEntity entity = (HaEntity) userObject;
                setText(entity.getFriendlyName() + " (" + entity.getEntityId() + ")");
                // You can set custom icons here based on entity.getDomain() if desired
                // setIcon(someIconForDomain);
                if (existingEntityIds != null && existingEntityIds.contains(entity.getEntityId())) {
                    setForeground(Color.GRAY); // Grey out already imported entities
                    setToolTipText("This entity is already imported.");
                } else {
                    setForeground(Color.BLACK); // Default color for new entities
                    setToolTipText(null); // Clear tooltip
                }
            } else if (userObject instanceof String) {
                // This handles Area names and Domain names
                setText((String) userObject);
                setForeground(Color.BLACK); // Default color for group nodes
                setToolTipText(null); // Clear tooltip
                // You can set custom icons for folders (Area/Domain) here
                // setIcon(someFolderIcon);
            }
            return this;
        }
    }
}