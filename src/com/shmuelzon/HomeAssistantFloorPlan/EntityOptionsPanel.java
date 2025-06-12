package com.shmuelzon.HomeAssistantFloorPlan;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.vecmath.Point2d;

import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.AutoCommitSpinner;
import com.eteks.sweethome3d.swing.ResourceAction;
import com.eteks.sweethome3d.swing.SwingTools;
import com.eteks.sweethome3d.tools.OperatingSystem;

public class EntityOptionsPanel extends JPanel {
    private enum ActionType {CLOSE, RESET_TO_DEFAULTS}

    private static final String FAN_ENTITY_PLACEHOLDER = "Fan Entity (e.g. fan.my_fan)";
    private Entity entity;
    private JLabel isFanAssociatedLabel;
    private JCheckBox isFanAssociatedCheckbox;
    private JComboBox<String> fanEntityNameComboBox;
    private JLabel displayTypeLabel;
    private JComboBox<Entity.DisplayType> displayTypeComboBox;
    private JLabel displayConditionLabel;
    private JComboBox<Entity.DisplayCondition> displayConditionComboBox;
    private JLabel tapActionLabel;
    private JComboBox<Entity.Action> tapActionComboBox;
    private JTextField tapActionValueTextField;
    private JLabel doubleTapActionLabel;
    private JComboBox<Entity.Action> doubleTapActionComboBox;
    private JTextField doubleTapActionValueTextField;
    private JLabel holdActionLabel;
    private JComboBox<Entity.Action> holdActionComboBox;
    private JTextField holdActionValueTextField;
    private JLabel positionLabel;
    private JLabel positionLeftLabel;
    private JSpinner positionLeftSpinner;
    private JLabel positionTopLabel;
    private JSpinner positionTopSpinner;
    private JLabel opacityLabel;
    private JSpinner opacitySpinner;
    private JLabel backgroundColorLabel;
    private JTextField backgroundColorTextField;
    private JLabel alwaysOnLabel;
    private JCheckBox alwaysOnCheckbox;
    private JLabel isRgbLabel;
    private JCheckBox isRgbCheckbox;
    private JLabel displayFurnitureConditionLabel;
    private JComboBox<Entity.DisplayFurnitureCondition> displayFurnitureConditionComboBox;
    private JTextField displayFurnitureConditionValueTextField;
    private JButton closeButton;
    private JButton resetToDefaultsButton;
    private ResourceBundle resource;

    public EntityOptionsPanel(UserPreferences preferences, Entity entity, List<String> availableFanEntities) {
        super(new GridBagLayout());
        this.entity = entity;

        resource = ResourceBundle.getBundle("com.shmuelzon.HomeAssistantFloorPlan.ApplicationPlugin", Locale.getDefault());
        createActions(preferences);
        createComponents(availableFanEntities);
        layoutComponents();
        markModified();
        // Call showHideComponents after layout and initial setup to ensure correct UI state
        showHideComponents();
    }

    private void createActions(UserPreferences preferences) {
        final ActionMap actions = getActionMap();
        actions.put(ActionType.CLOSE, new ResourceAction(preferences, Panel.class, ActionType.CLOSE.name(), true) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                attemptClose();
            }
        });
        actions.put(ActionType.RESET_TO_DEFAULTS, new ResourceAction(preferences, Panel.class, ActionType.RESET_TO_DEFAULTS.name(), true) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                entity.resetToDefaults();
                // After reset, the fan association might be false or fan name cleared.
                // The validation in attemptClose() will re-evaluate based on the new entity state.
                // If reset makes the state valid for closing (e.g., unchecks "Is Light/Fan Combo"),
                // then it will close. Otherwise, it will show the error if still invalid.
                // This ensures the dialog doesn't close with an invalid state even after reset.
                attemptClose();
            }
        });
    }

    private void createComponents(List<String> availableFanEntities) {
        final ActionMap actionMap = getActionMap();

        isFanAssociatedLabel = new JLabel();
        isFanAssociatedLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.isFanAssociatedLabel.text"));
        isFanAssociatedCheckbox = new JCheckBox();
        isFanAssociatedCheckbox.setSelected(entity.getIsFanAssociated());
        isFanAssociatedCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                entity.setIsFanAssociated(isFanAssociatedCheckbox.isSelected());
                if (isFanAssociatedCheckbox.isSelected()) {
                    // Force display condition to ALWAYS when fan is associated
                    displayConditionComboBox.setSelectedItem(Entity.DisplayCondition.ALWAYS);
                    // Force display type to FAN when Light/Fan combo is selected
                    displayTypeComboBox.setSelectedItem(Entity.DisplayType.FAN);
                    entity.setDisplayType(Entity.DisplayType.FAN); // Update model as well
                    entity.setDisplayCondition(Entity.DisplayCondition.ALWAYS);
                } else {
                    // If unchecked, revert display type to ICON (or another default if preferred)
                    displayTypeComboBox.setSelectedItem(Entity.DisplayType.ICON);
                    entity.setDisplayType(Entity.DisplayType.ICON);
                    entity.setDisplayCondition(Entity.DisplayCondition.ALWAYS);
                }
                showHideComponents();
                markModified();
            }
        });

        fanEntityNameComboBox = new JComboBox<String>();
        if (availableFanEntities != null) {
            for (String fanName : availableFanEntities) {
                fanEntityNameComboBox.addItem(fanName);
            }
        }
        fanEntityNameComboBox.setEditable(true);

        final JTextField editorComponent = (JTextField) fanEntityNameComboBox.getEditor().getEditorComponent();

        // Initial setup for placeholder/value will be handled by showHideComponents
        // called after all components are created and laid out.

        editorComponent.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (editorComponent.getText().equals(FAN_ENTITY_PLACEHOLDER)) {
                    editorComponent.setText("");
                    editorComponent.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (editorComponent.getText().isEmpty()) {
                    editorComponent.setText(FAN_ENTITY_PLACEHOLDER);
                    editorComponent.setForeground(Color.GRAY);
                }
            }
        });

        ((AbstractDocument) editorComponent.getDocument()).setDocumentFilter(new DocumentFilter() {
            private boolean isValidFanEntityFormat(String text) {
                return text.isEmpty() || text.equals(FAN_ENTITY_PLACEHOLDER) || text.startsWith("fan.");
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
                String newText = currentText.substring(0, offset) + string + currentText.substring(offset);
                if (isValidFanEntityFormat(newText)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
                String newText = currentText.substring(0, offset) + (text == null ? "" : text) + currentText.substring(offset + length);
                if (isValidFanEntityFormat(newText)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });

        fanEntityNameComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Avoid processing if the action is due to programmatic changes (like setEnabled)
                if (!fanEntityNameComboBox.isEnabled()) {
                    return;
                }
                
                Object currentEditorItem = fanEntityNameComboBox.getEditor().getItem(); // Use editor's item
                String currentText = currentEditorItem != null ? currentEditorItem.toString() : "";

                if (currentText.equals(FAN_ENTITY_PLACEHOLDER)) {
                    entity.setFanEntityName("");
                } else if (currentText.startsWith("fan.") || currentText.isEmpty()) {
                    entity.setFanEntityName(currentText);
                } else { // Should be caught by DocumentFilter for typed text, but safeguard
                    // This case should ideally be prevented by the DocumentFilter for typed input.
                    // If an invalid item was somehow selected from the dropdown (if it contained invalid items),
                    // or if set programmatically.
                    entity.setFanEntityName("");
                    // Reset editor to placeholder if it's not already the placeholder and is invalid
                    if (!editorComponent.getText().equals(FAN_ENTITY_PLACEHOLDER)) { 
                        editorComponent.setText(FAN_ENTITY_PLACEHOLDER);
                        editorComponent.setForeground(Color.GRAY);
                    }
                }
                markModified();
            }
        });

        displayTypeLabel = new JLabel();
        displayTypeLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.displayTypeLabel.text"));
        displayTypeComboBox = new JComboBox<Entity.DisplayType>(Entity.DisplayType.values());
        displayTypeComboBox.setSelectedItem(entity.getDisplayType());
        displayTypeComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.displayTypeComboBox.%s.text", ((Entity.DisplayType)o).name())));
                return rendererComponent;
            }
        });
        displayTypeComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                entity.setDisplayType((Entity.DisplayType)displayTypeComboBox.getSelectedItem());
                showHideComponents(); // Call showHideComponents when display type changes
                markModified();
            }
        });

        displayConditionLabel = new JLabel();
        displayConditionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.displayConditionLabel.text"));
        displayConditionComboBox = new JComboBox<Entity.DisplayCondition>(Entity.DisplayCondition.values());
        displayConditionComboBox.setSelectedItem(entity.getDisplayCondition());
        displayConditionComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.displayConditionComboBox.%s.text", ((Entity.DisplayCondition)o).name())));
                return rendererComponent;
            }
        });
        displayConditionComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                entity.setDisplayCondition((Entity.DisplayCondition)displayConditionComboBox.getSelectedItem());
                markModified();
            }
        });

        tapActionLabel = new JLabel();
        tapActionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.tapActionLabel.text"));
        tapActionComboBox = new JComboBox<Entity.Action>(Entity.Action.values());
        tapActionComboBox.setSelectedItem(entity.getTapAction());
        tapActionComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.actionComboBox.%s.text", ((Entity.Action)o).name())));
                return rendererComponent;
            }
        });
        tapActionComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                Entity.Action action = (Entity.Action)tapActionComboBox.getSelectedItem();
                showHideComponents();
                if (tapActionValueTextField.getText().isEmpty() && action == Entity.Action.NAVIGATE)
                    return;
                entity.setTapAction(action);
                markModified();
            }
        });
        tapActionValueTextField = new JTextField(10);
        tapActionValueTextField.setText(entity.getTapActionValue());
        tapActionValueTextField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void executeUpdate(DocumentEvent e) {
                String actionValue = tapActionValueTextField.getText();
                entity.setTapActionValue(actionValue);
                // Only set action type if value is not empty, or if action is not NAVIGATE
                if (!actionValue.isEmpty() || (Entity.Action)tapActionComboBox.getSelectedItem() != Entity.Action.NAVIGATE) {
                    entity.setTapAction((Entity.Action)tapActionComboBox.getSelectedItem());
                }
                markModified();
            }
        });

        doubleTapActionLabel = new JLabel();
        doubleTapActionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.doubleTapActionLabel.text"));
        doubleTapActionComboBox = new JComboBox<Entity.Action>(Entity.Action.values());
        doubleTapActionComboBox.setSelectedItem(entity.getDoubleTapAction());
        doubleTapActionComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.actionComboBox.%s.text", ((Entity.Action)o).name())));
                return rendererComponent;
            }
        });
        doubleTapActionComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                Entity.Action action = (Entity.Action)doubleTapActionComboBox.getSelectedItem();
                showHideComponents();
                if (doubleTapActionValueTextField.getText().isEmpty() && action == Entity.Action.NAVIGATE)
                    return;
                entity.setDoubleTapAction(action);
                markModified();
            }
        });
        doubleTapActionValueTextField = new JTextField(10);
        doubleTapActionValueTextField.setText(entity.getDoubleTapActionValue());
        doubleTapActionValueTextField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void executeUpdate(DocumentEvent e) {
                String actionValue = doubleTapActionValueTextField.getText();
                entity.setDoubleTapActionValue(actionValue);
                if (!actionValue.isEmpty() || (Entity.Action)doubleTapActionComboBox.getSelectedItem() != Entity.Action.NAVIGATE) {
                    entity.setDoubleTapAction((Entity.Action)doubleTapActionComboBox.getSelectedItem());
                }
                markModified();
            }
        });

        holdActionLabel = new JLabel();
        holdActionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.holdActionLabel.text"));
        holdActionComboBox = new JComboBox<Entity.Action>(Entity.Action.values());
        holdActionComboBox.setSelectedItem(entity.getHoldAction());
        holdActionComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.actionComboBox.%s.text", ((Entity.Action)o).name())));
                return rendererComponent;
            }
        });
        holdActionComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                Entity.Action action = (Entity.Action)holdActionComboBox.getSelectedItem();
                showHideComponents();
                if (holdActionValueTextField.getText().isEmpty() && action == Entity.Action.NAVIGATE)
                    return;
                entity.setHoldAction(action);
                markModified();
            }
        });
        holdActionValueTextField = new JTextField(10);
        holdActionValueTextField.setText(entity.getHoldActionValue());
        holdActionValueTextField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void executeUpdate(DocumentEvent e) {
                String actionValue = holdActionValueTextField.getText();
                entity.setHoldActionValue(actionValue);
                if (!actionValue.isEmpty() || (Entity.Action)holdActionComboBox.getSelectedItem() != Entity.Action.NAVIGATE) {
                    entity.setHoldAction((Entity.Action)holdActionComboBox.getSelectedItem());
                }
                markModified();
            }
        });

        final Point2d position = entity.getPosition();
        positionLabel = new JLabel();
        positionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.positionLabel.text"));
        positionLeftLabel = new JLabel();
        positionLeftLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.positionLeftLabel.text"));
        final SpinnerNumberModel positionLeftSpinnerModel = new SpinnerNumberModel(0, 0, 1, 0.001);
        positionLeftSpinner = new AutoCommitSpinner(positionLeftSpinnerModel);
        JSpinner.NumberEditor positionLeftEditor = new JSpinner.NumberEditor(positionLeftSpinner, "0.00 %");
        ((JSpinner.DefaultEditor)positionLeftEditor).getTextField().setColumns(5);
        positionLeftSpinner.setEditor(positionLeftEditor);
        positionLeftSpinnerModel.setValue(position.x / 100.0);
        positionLeftSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ev) {
              final Point2d position = entity.getPosition();
              position.x = ((Number)positionLeftSpinnerModel.getValue()).doubleValue() * 100;
              entity.setPosition(position, true);
              markModified();
            }
        });
        positionTopLabel = new JLabel();
        positionTopLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.positionTopLabel.text"));
        final SpinnerNumberModel positionTopSpinnerModel = new SpinnerNumberModel(0, 0, 1, 0.001);
        positionTopSpinner = new AutoCommitSpinner(positionTopSpinnerModel);
        JSpinner.NumberEditor positionTopEditor = new JSpinner.NumberEditor(positionTopSpinner, "0.00 %");
        ((JSpinner.DefaultEditor)positionTopEditor).getTextField().setColumns(5);
        positionTopSpinner.setEditor(positionTopEditor);
        positionTopSpinnerModel.setValue(position.y / 100.0);
        positionTopSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ev) {
              final Point2d position = entity.getPosition();
              position.y = ((Number)positionTopSpinnerModel.getValue()).doubleValue() * 100;
              entity.setPosition(position, true);
              markModified();
            }
        });

        opacityLabel = new JLabel();
        opacityLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.opacityLabel.text"));
        final SpinnerNumberModel opacitySpinnerModel = new SpinnerNumberModel(0, 0, 1, 0.01);
        opacitySpinner = new AutoCommitSpinner(opacitySpinnerModel);
        JSpinner.NumberEditor opacityEditor = new JSpinner.NumberEditor(opacitySpinner, "0 %");
        ((JSpinner.DefaultEditor)opacityEditor).getTextField().setColumns(5);
        opacitySpinner.setEditor(opacityEditor);
        opacitySpinnerModel.setValue(entity.getOpacity() / 100.0);
        opacitySpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ev) {
              entity.setOpacity((int)(((Number)opacitySpinnerModel.getValue()).doubleValue() * 100));
              markModified();
            }
        });

        backgroundColorLabel = new JLabel();
        backgroundColorLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.backgroundColorLabel.text"));
        backgroundColorTextField = new JTextField(20);
        backgroundColorTextField.setText(entity.getBackgrounColor());
        backgroundColorTextField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void executeUpdate(DocumentEvent e) {
                entity.setBackgrounColor(backgroundColorTextField.getText());
                markModified();
            }
        });

        alwaysOnLabel = new JLabel();
        alwaysOnLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.alwaysOnLabel.text"));
        alwaysOnCheckbox = new JCheckBox();
        alwaysOnCheckbox.setSelected(entity.getAlwaysOn());
        alwaysOnCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                entity.setAlwaysOn(alwaysOnCheckbox.isSelected());
                markModified();
            }
        });

        isRgbLabel = new JLabel();
        isRgbLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.isRgbLabel.text"));
        isRgbCheckbox = new JCheckBox();
        isRgbCheckbox.setSelected(entity.getIsRgb());
        isRgbCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                entity.setIsRgb(isRgbCheckbox.isSelected());
                markModified();
            }
        });

        displayFurnitureConditionLabel = new JLabel();
        displayFurnitureConditionLabel.setText(resource.getString("HomeAssistantFloorPlan.Panel.displayFurnitureConditionLabel.text"));
        displayFurnitureConditionComboBox = new JComboBox<Entity.DisplayFurnitureCondition>(Entity.DisplayFurnitureCondition.values());
        displayFurnitureConditionComboBox.setSelectedItem(entity.getDisplayFurnitureCondition());
        displayFurnitureConditionComboBox.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> jList, Object o, int i, boolean b, boolean b1) {
                Component rendererComponent = super.getListCellRendererComponent(jList, o, i, b, b1);
                setText(resource.getString(String.format("HomeAssistantFloorPlan.Panel.displayFurnitureConditionComboBox.%s.text", ((Entity.DisplayFurnitureCondition)o).name())));
                return rendererComponent;
            }
        });
        displayFurnitureConditionComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                Entity.DisplayFurnitureCondition condition = (Entity.DisplayFurnitureCondition)displayFurnitureConditionComboBox.getSelectedItem();
                showHideComponents();
                if (displayFurnitureConditionValueTextField.getText().isEmpty() && condition != Entity.DisplayFurnitureCondition.ALWAYS)
                    return;
                entity.setDisplayFurnitureCondition(condition);
                markModified();
            }
        });
        displayFurnitureConditionValueTextField = new JTextField(10);
        displayFurnitureConditionValueTextField.setText(entity.getDisplayFurnitureConditionValue());
        displayFurnitureConditionValueTextField.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void executeUpdate(DocumentEvent e) {
                String conditionValue = displayFurnitureConditionValueTextField.getText();
                entity.setDisplayFurnitureConditionValue(conditionValue);
                if (!conditionValue.isEmpty() || (Entity.DisplayFurnitureCondition)displayFurnitureConditionComboBox.getSelectedItem() == Entity.DisplayFurnitureCondition.ALWAYS) {
                    entity.setDisplayFurnitureCondition((Entity.DisplayFurnitureCondition)displayFurnitureConditionComboBox.getSelectedItem());
                }
                markModified();
            }
        });

        closeButton = new JButton(actionMap.get(ActionType.CLOSE));
        closeButton.setText(resource.getString("HomeAssistantFloorPlan.Panel.closeButton.text"));

        resetToDefaultsButton = new JButton(actionMap.get(ActionType.RESET_TO_DEFAULTS));
        resetToDefaultsButton.setText(resource.getString("HomeAssistantFloorPlan.Panel.resetToDefaultsButton.text"));
    }

    private void layoutComponents() {
        int labelAlignment = OperatingSystem.isMacOSX() ? JLabel.TRAILING : JLabel.LEADING;
        int standardGap = Math.round(2 * SwingTools.getResolutionScale());
        Insets insets = new Insets(0, standardGap, 0, standardGap);
        int currentGridYIndex = 0;

        /* Is Fan Associated - Only for lights */
        if (entity.getIsLight()) {
            add(isFanAssociatedLabel, new GridBagConstraints(
                0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, insets, 0, 0));
            isFanAssociatedLabel.setHorizontalAlignment(labelAlignment);
            add(isFanAssociatedCheckbox, new GridBagConstraints(
                1, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.LINE_START,
                GridBagConstraints.NONE, insets, 0, 0));
            add(fanEntityNameComboBox, new GridBagConstraints(
                2, currentGridYIndex, 3, 1, 1.0, 0, GridBagConstraints.LINE_START, // Give text field extra horizontal space
                GridBagConstraints.HORIZONTAL, insets, 0, 0));
            currentGridYIndex++;
        }
        /* Display type */
        add(displayTypeLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        displayTypeLabel.setHorizontalAlignment(labelAlignment);
        add(displayTypeComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Display Condition */
        add(displayConditionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        displayConditionLabel.setHorizontalAlignment(labelAlignment);
        add(displayConditionComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Tap action */
        add(tapActionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        tapActionLabel.setHorizontalAlignment(labelAlignment);
        add(tapActionComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(tapActionValueTextField, new GridBagConstraints(
            3, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Double tap action */
        add(doubleTapActionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        doubleTapActionLabel.setHorizontalAlignment(labelAlignment);
        add(doubleTapActionComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(doubleTapActionValueTextField, new GridBagConstraints(
            3, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Hold action */
        add(holdActionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        holdActionLabel.setHorizontalAlignment(labelAlignment);
        add(holdActionComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(holdActionValueTextField, new GridBagConstraints(
            3, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Position */
        add(positionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        positionLabel.setHorizontalAlignment(labelAlignment);
        add(positionLeftLabel, new GridBagConstraints(
            1, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(positionLeftSpinner, new GridBagConstraints(
            2, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(positionTopLabel, new GridBagConstraints(
            3, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(positionTopSpinner, new GridBagConstraints(
            4, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Opacity */
        add(opacityLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        opacityLabel.setHorizontalAlignment(labelAlignment);
        add(opacitySpinner, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Background color */
        add(backgroundColorLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        backgroundColorLabel.setHorizontalAlignment(labelAlignment);
        add(backgroundColorTextField, new GridBagConstraints(
            1, currentGridYIndex, 4, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        if (entity.getIsLight())
            layoutLightSpecificComponents(labelAlignment, insets, currentGridYIndex);
        else
            layoutNonLightSpecificComponents(labelAlignment, insets, currentGridYIndex);
    }

    private void layoutLightSpecificComponents(int labelAlignment, Insets insets, int currentGridYIndex) {
        /* Always on */
        add(alwaysOnLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        alwaysOnLabel.setHorizontalAlignment(labelAlignment);
        add(alwaysOnCheckbox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;

        /* Is RGB */
        add(isRgbLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        isRgbLabel.setHorizontalAlignment(labelAlignment);
        add(isRgbCheckbox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;
    }

    private void layoutNonLightSpecificComponents(int labelAlignment, Insets insets, int currentGridYIndex) {
        add(displayFurnitureConditionLabel, new GridBagConstraints(
            0, currentGridYIndex, 1, 1, 0, 0, GridBagConstraints.CENTER,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        displayFurnitureConditionLabel.setHorizontalAlignment(labelAlignment);
        add(displayFurnitureConditionComboBox, new GridBagConstraints(
            1, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        add(displayFurnitureConditionValueTextField, new GridBagConstraints(
            3, currentGridYIndex, 2, 1, 0, 0, GridBagConstraints.LINE_START,
            GridBagConstraints.HORIZONTAL, insets, 0, 0));
        currentGridYIndex++;
    }

    private void markModified() {
        Color modifiedColor = new Color(200, 0, 0);

        if (entity.getIsLight()) {
            isFanAssociatedLabel.setForeground(entity.isFanAssociatedModified() ? modifiedColor : Color.BLACK);
        }
        displayTypeLabel.setForeground(entity.isDisplayTypeModified() ? modifiedColor : Color.BLACK);
        displayConditionLabel.setForeground(entity.isDisplayConditionModified() ? modifiedColor : Color.BLACK);
        tapActionLabel.setForeground(entity.isTapActionModified() ? modifiedColor : Color.BLACK);
        doubleTapActionLabel.setForeground(entity.isDoubleTapActionModified() ? modifiedColor : Color.BLACK);
        holdActionLabel.setForeground(entity.isHoldActionModified() ? modifiedColor : Color.BLACK);
        positionLabel.setForeground(entity.isPositionModified() ? modifiedColor : Color.BLACK);
        alwaysOnLabel.setForeground(entity.isAlwaysOnModified() ? modifiedColor : Color.BLACK);
        isRgbLabel.setForeground(entity.isIsRgbModified() ? modifiedColor : Color.BLACK);
        opacityLabel.setForeground(entity.isOpacityModified() ? modifiedColor : Color.BLACK);
        backgroundColorLabel.setForeground(entity.isBackgroundColorModified() ? modifiedColor : Color.BLACK);
        displayFurnitureConditionLabel.setForeground(entity.isDisplayFurnitureConditionModified() ? modifiedColor : Color.BLACK);
    }

    private void showHideComponents() {
        boolean isFanAndLight = entity.getIsLight() && entity.getIsFanAssociated();
        // boolean isDisplayTypeFan = entity.getDisplayType() == Entity.DisplayType.FAN; // This check will be more localized

        JTextField currentEditorComp = (JTextField) fanEntityNameComboBox.getEditor().getEditorComponent();
        if (entity.getIsLight()) {
            fanEntityNameComboBox.setEnabled(isFanAndLight);
            if (!isFanAndLight) {
                // Disabled: show placeholder, clear model
                currentEditorComp.setText(FAN_ENTITY_PLACEHOLDER);
                currentEditorComp.setForeground(Color.GRAY);
                if (entity.getFanEntityName() != null && !entity.getFanEntityName().isEmpty()) { 
                    entity.setFanEntityName(""); 
                }
            } else {
                // Enabled: restore from model or show placeholder if model is empty
                if (entity.getFanEntityName() != null && !entity.getFanEntityName().isEmpty()) {
                    fanEntityNameComboBox.setSelectedItem(entity.getFanEntityName()); 
                    currentEditorComp.setForeground(Color.BLACK);
                } else {
                    currentEditorComp.setText(FAN_ENTITY_PLACEHOLDER);
                    currentEditorComp.setForeground(Color.GRAY);
                }
            }
        }

        displayConditionComboBox.setEnabled(!isFanAndLight);
        displayTypeComboBox.setEnabled(!isFanAndLight); // Disable display type if Light/Fan Combo

        // Actions are primarily disabled by the "Is Light/Fan Combo" feature.
        // The DisplayType.FAN itself (for non-light entities) shouldn't disable actions.
        // If it's a light AND a Light/Fan Combo, actions are disabled.
        boolean actionsEnabled = !isFanAndLight;
        boolean isNavigate;

        tapActionComboBox.setEnabled(actionsEnabled);
        isNavigate = actionsEnabled && (Entity.Action)tapActionComboBox.getSelectedItem() == Entity.Action.NAVIGATE;
        tapActionValueTextField.setEnabled(isNavigate);
        tapActionValueTextField.setVisible(isNavigate);

        doubleTapActionComboBox.setEnabled(actionsEnabled);
        isNavigate = actionsEnabled && (Entity.Action)doubleTapActionComboBox.getSelectedItem() == Entity.Action.NAVIGATE;
        doubleTapActionValueTextField.setEnabled(isNavigate);
        doubleTapActionValueTextField.setVisible(isNavigate);

        holdActionComboBox.setEnabled(actionsEnabled);
        isNavigate = actionsEnabled && (Entity.Action)holdActionComboBox.getSelectedItem() == Entity.Action.NAVIGATE;
        holdActionValueTextField.setEnabled(isNavigate);
        holdActionValueTextField.setVisible(isNavigate);

        // DisplayFurnitureCondition is for non-lights and not for DisplayType.FAN
        // It should only be disabled if the entity IS a light.
        // If DisplayType is FAN for a non-light, this should still be configurable.
        boolean displayFurnitureConditionEnabled = !entity.getIsLight();
        displayFurnitureConditionComboBox.setEnabled(displayFurnitureConditionEnabled);
        displayFurnitureConditionValueTextField.setEnabled(displayFurnitureConditionEnabled && (Entity.DisplayFurnitureCondition)displayFurnitureConditionComboBox.getSelectedItem() != Entity.DisplayFurnitureCondition.ALWAYS);
        displayFurnitureConditionValueTextField.setVisible(displayFurnitureConditionEnabled && (Entity.DisplayFurnitureCondition)displayFurnitureConditionComboBox.getSelectedItem() != Entity.DisplayFurnitureCondition.ALWAYS);
    }

    public void displayView(Component parentComponent) {
        final JOptionPane optionPane = new JOptionPane(this,
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object [] {closeButton, resetToDefaultsButton}, closeButton);
        final JDialog dialog = optionPane.createDialog(SwingUtilities.getRootPane(parentComponent), entity.getName());
        dialog.applyComponentOrientation(parentComponent != null ?
            parentComponent.getComponentOrientation() : ComponentOrientation.getOrientation(Locale.getDefault()));
        
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptClose();
            }
        });
        
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    private void attemptClose() {
        if (entity.getIsLight() && entity.getIsFanAssociated()) {
            String fanName = "";
            Object selectedItem = fanEntityNameComboBox.getEditor().getItem();
            if (selectedItem != null) {
                fanName = selectedItem.toString();
            }

            if (fanName.isEmpty() || fanName.equals(FAN_ENTITY_PLACEHOLDER)) {
                JOptionPane.showMessageDialog(this,
                    resource.getString("HomeAssistantFloorPlan.Panel.error.fanEntityRequired.text"),
                    resource.getString("HomeAssistantFloorPlan.Panel.error.title"),
                    JOptionPane.ERROR_MESSAGE);
                return; // Don't close
            }
        }
        performActualClose();
    }

    private void performActualClose() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && window.isDisplayable())
            window.dispose();
    }
}
