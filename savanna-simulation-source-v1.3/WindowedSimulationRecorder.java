import java.awt.BorderLayout;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Shows live step records in a separate window and writes the final document.
 */
public class WindowedSimulationRecorder extends StepReportRecorder
{
    private final JFrame frame;
    private final JTextArea textArea;
    private final JLabel status;
    private final SimulationChartWindow chartWindow;

    public WindowedSimulationRecorder(String reportFileName)
    {
        super(reportFileName);
        frame = new JFrame("Savanna Simulation Record");
        textArea = new JTextArea(26, 100);
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        status = new JLabel("Waiting for simulation to start.");
        chartWindow = new SimulationChartWindow();

        frame.add(status, BorderLayout.NORTH);
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.pack();
        frame.setLocation(100, 620);
        frame.setVisible(true);
    }

    public void start(Field field, SimulationContext context)
    {
        SwingUtilities.invokeLater(() -> {
            textArea.setText("");
            status.setText("Recording every " + getRecordInterval() +
                           " simulation steps...");
            if(!frame.isVisible()) {
                frame.setVisible(true);
            }
        });
        chartWindow.clear();
        super.start(field, context);
    }

    protected void onStepRecorded(StepSnapshot snapshot)
    {
        String line = snapshot.toTextLine();
        chartWindow.addSnapshot(snapshot);
        SwingUtilities.invokeLater(() -> {
            textArea.append(line + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    protected void onReportWritten(File reportFile, String reason)
    {
        SwingUtilities.invokeLater(() -> {
            String message = "Finished: " + reason +
                             " Report: " + reportFile.getAbsolutePath();
            status.setText(message);
            textArea.append("\n" + message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }
}
