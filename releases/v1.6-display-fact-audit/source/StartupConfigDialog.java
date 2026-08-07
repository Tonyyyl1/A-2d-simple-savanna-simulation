import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class StartupConfigDialog
{
    private StartupConfigDialog() {}

    public static StartupOptions showDialogOrDefault()
    {
        try {
            StartupOptions options = showDialog();
            return options == null ? StartupOptions.cancelled() : options;
        }
        catch(java.awt.HeadlessException e) {
            return new StartupOptions(SimulationConfig.default3x(), 200000, 24,
                                      false);
        }
    }

    private static StartupOptions showDialog()
    {
        JDialog dialog = new JDialog((java.awt.Frame)null,
                                     "Start Savanna Simulation", true);
        JPanel form = new JPanel(new GridBagLayout());
        TextFields fields = new TextFields();
        int row = 0;
        row = addReadOnly(form, row, "Map scale", "3x");
        row = addReadOnly(form, row, "Width", String.valueOf(
            SimulationConfig.default3x().getScaledWidth()));
        row = addReadOnly(form, row, "Depth", String.valueOf(
            SimulationConfig.default3x().getScaledDepth()));
        row = addField(form, row, "Steps", fields.steps);
        row = addField(form, row, "Step delay", fields.stepDelay);
        row = addField(form, row, "Creation multiplier", fields.creation);
        row = addField(form, row, "Founding multiplier", fields.founding);
        row = addField(form, row, "Breeding multiplier", fields.breeding);
        row = addField(form, row, "Predator creation", fields.predatorCreation);
        row = addField(form, row, "Prey creation", fields.preyCreation);
        row = addField(form, row, "Predator founding", fields.predatorFounding);
        row = addField(form, row, "Prey founding", fields.preyFounding);
        row = addField(form, row, "Predator breeding", fields.predatorBreeding);
        row = addField(form, row, "Prey breeding", fields.preyBreeding);
        row = addField(form, row, "Disease transmission", fields.diseaseTx);
        row = addField(form, row, "Disease fatality", fields.diseaseFatal);
        row = addCheckBox(form, row, "Experimental thirst system",
                          fields.thirstEnabled);
        row = addField(form, row, "Random seed", fields.randomSeed);
        addField(form, row, "Terrain seed", fields.terrainSeed);

        final StartupOptions[] result = { null };
        JButton start = new JButton("Start");
        JButton reset = new JButton("Reset Defaults");
        JButton cancel = new JButton("Cancel");
        start.addActionListener(event -> {
            try {
                result[0] = fields.toOptions();
                dialog.dispose();
            }
            catch(IllegalArgumentException e) {
                JOptionPane.showMessageDialog(dialog, e.getMessage(),
                    "Invalid startup options", JOptionPane.ERROR_MESSAGE);
            }
        });
        reset.addActionListener(event -> fields.resetDefaults());
        cancel.addActionListener(event -> {
            result[0] = StartupOptions.cancelled();
            dialog.dispose();
        });

        JPanel buttons = new JPanel();
        buttons.add(start);
        buttons.add(reset);
        buttons.add(cancel);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return result[0];
    }

    private static int addField(JPanel panel, int row, String label,
                                JTextField field)
    {
        addLabel(panel, row, label);
        GridBagConstraints constraints = fieldConstraints(row);
        panel.add(field, constraints);
        return row + 1;
    }

    private static int addReadOnly(JPanel panel, int row, String label,
                                   String value)
    {
        JTextField field = new JTextField(value, 16);
        field.setEditable(false);
        return addField(panel, row, label, field);
    }

    private static int addCheckBox(JPanel panel, int row, String label,
                                   JCheckBox field)
    {
        addLabel(panel, row, label);
        GridBagConstraints constraints = fieldConstraints(row);
        panel.add(field, constraints);
        return row + 1;
    }

    private static void addLabel(JPanel panel, int row, String text)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(3, 8, 3, 8);
        panel.add(new JLabel(text), constraints);
    }

    private static GridBagConstraints fieldConstraints(int row)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(3, 8, 3, 8);
        return constraints;
    }

    public static class StartupOptions
    {
        private final SimulationConfig config;
        private final int steps;
        private final int stepDelay;
        private final boolean cancelled;

        StartupOptions(SimulationConfig config, int steps, int stepDelay,
                       boolean cancelled)
        {
            this.config = config;
            this.steps = steps;
            this.stepDelay = stepDelay;
            this.cancelled = cancelled;
        }

        public static StartupOptions cancelled()
        {
            return new StartupOptions(SimulationConfig.default3x(), 0, 0, true);
        }

        public SimulationConfig getConfig() { return config; }
        public int getSteps() { return steps; }
        public int getStepDelay() { return stepDelay; }
        public boolean isCancelled() { return cancelled; }
    }

    private static class TextFields
    {
        final JTextField steps = new JTextField("200000", 16);
        final JTextField stepDelay = new JTextField("24", 16);
        final JTextField creation = new JTextField("0.111111", 16);
        final JTextField founding = new JTextField("1.0", 16);
        final JTextField breeding = new JTextField("0.58", 16);
        final JTextField predatorCreation = new JTextField("2.60", 16);
        final JTextField preyCreation = new JTextField("0.75", 16);
        final JTextField predatorFounding = new JTextField("2.00", 16);
        final JTextField preyFounding = new JTextField("0.85", 16);
        final JTextField predatorBreeding = new JTextField("2.00", 16);
        final JTextField preyBreeding = new JTextField("0.55", 16);
        final JTextField diseaseTx = new JTextField("1.75", 16);
        final JTextField diseaseFatal = new JTextField("1.75", 16);
        final JCheckBox thirstEnabled = new JCheckBox("Enable / 启用", false);
        final JTextField randomSeed = new JTextField("1111", 16);
        final JTextField terrainSeed = new JTextField("20260629", 16);

        void resetDefaults()
        {
            steps.setText("200000");
            stepDelay.setText("24");
            creation.setText("0.111111");
            founding.setText("1.0");
            breeding.setText("0.58");
            predatorCreation.setText("2.60");
            preyCreation.setText("0.75");
            predatorFounding.setText("2.00");
            preyFounding.setText("0.85");
            predatorBreeding.setText("2.00");
            preyBreeding.setText("0.55");
            diseaseTx.setText("1.75");
            diseaseFatal.setText("1.75");
            thirstEnabled.setSelected(false);
            randomSeed.setText(String.valueOf(SimulationConfig.DEFAULT_RANDOM_SEED));
            terrainSeed.setText(String.valueOf(SimulationConfig.DEFAULT_TERRAIN_SEED));
        }

        StartupOptions toOptions()
        {
            int parsedSteps = parsePositiveInt("Steps", steps.getText());
            int parsedDelay = parseNonNegativeInt("Step delay",
                                                  stepDelay.getText());
            SimulationConfig config = SimulationConfig.builder()
                .mapScale(3)
                .creationMultiplier(parsePositiveDouble("Creation multiplier",
                    creation.getText()))
                .foundingMultiplier(parsePositiveDouble("Founding multiplier",
                    founding.getText()))
                .breedingMultiplier(parsePositiveDouble("Breeding multiplier",
                    breeding.getText()))
                .predatorCreationMultiplier(parsePositiveDouble(
                    "Predator creation", predatorCreation.getText()))
                .preyCreationMultiplier(parsePositiveDouble("Prey creation",
                    preyCreation.getText()))
                .predatorFoundingMultiplier(parsePositiveDouble(
                    "Predator founding", predatorFounding.getText()))
                .preyFoundingMultiplier(parsePositiveDouble("Prey founding",
                    preyFounding.getText()))
                .predatorBreedingMultiplier(parsePositiveDouble(
                    "Predator breeding", predatorBreeding.getText()))
                .preyBreedingMultiplier(parsePositiveDouble("Prey breeding",
                    preyBreeding.getText()))
                .diseaseTransmissionMultiplier(parsePositiveDouble(
                    "Disease transmission", diseaseTx.getText()))
                .diseaseFatalityMultiplier(parsePositiveDouble(
                    "Disease fatality", diseaseFatal.getText()))
                .randomSeed(parseLong("Random seed", randomSeed.getText()))
                .terrainSeed(parseLong("Terrain seed", terrainSeed.getText()))
                .thirstEnabled(thirstEnabled.isSelected())
                .build();
            return new StartupOptions(config, parsedSteps, parsedDelay, false);
        }

        private static int parsePositiveInt(String label, String text)
        {
            int value = Integer.parseInt(text.trim());
            if(value < 1) {
                throw new IllegalArgumentException(label + " must be >= 1.");
            }
            return value;
        }

        private static int parseNonNegativeInt(String label, String text)
        {
            int value = Integer.parseInt(text.trim());
            if(value < 0) {
                throw new IllegalArgumentException(label + " must be >= 0.");
            }
            return value;
        }

        private static double parsePositiveDouble(String label, String text)
        {
            double value = Double.parseDouble(text.trim());
            if(Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(label + " must be > 0.");
            }
            return value;
        }

        private static long parseLong(String label, String text)
        {
            try {
                return Long.parseLong(text.trim());
            }
            catch(NumberFormatException e) {
                throw new IllegalArgumentException(label + " must be a whole number.");
            }
        }
    }
}
