import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records interval snapshots and writes a detailed HTML document at the end.
 */
public class StepReportRecorder implements SimulationRecorder
{
    private final List<String> htmlRows;
    private final List<String> textLines;
    private final String reportFileName;
    private File lastReportFile;
    private boolean started;
    private int lastRecordedStep;

    public StepReportRecorder(String reportFileName)
    {
        this.reportFileName = reportFileName;
        htmlRows = new ArrayList<>();
        textLines = new ArrayList<>();
        started = false;
        lastRecordedStep = -1;
    }

    public void start(Field field, SimulationContext context)
    {
        htmlRows.clear();
        textLines.clear();
        lastReportFile = null;
        started = true;
        lastRecordedStep = -1;
        recordStepNow(0, field, context);
    }

    public void recordStep(int step, Field field, SimulationContext context)
    {
        if(!started) {
            start(field, context);
            return;
        }
        if(!shouldRecord(step)) {
            return;
        }
        recordStepNow(step, field, context);
    }

    private void recordStepNow(int step, Field field, SimulationContext context)
    {
        if(lastRecordedStep == step) {
            return;
        }
        StepSnapshot snapshot = new StepSnapshot(step, field, context);
        htmlRows.add(snapshot.toHtmlRow());
        textLines.add(snapshot.toTextLine());
        lastRecordedStep = step;
        onStepRecorded(snapshot);
    }

    public void finish(Field field, SimulationContext context, String reason)
    {
        if(!started) {
            start(field, context);
        }
        recordStepNow(context.getStep(), field, context);
        lastReportFile = writeReport(field, context, reason);
        onReportWritten(lastReportFile, reason);
    }

    /**
     * Hook for subclasses that show live output.
     */
    protected void onStepRecorded(StepSnapshot snapshot)
    {
    }

    /**
     * Hook for subclasses that show report status.
     */
    protected void onReportWritten(File reportFile, String reason)
    {
    }

    /**
     * @return The last generated report file, or null if no report was written.
     */
    public File getLastReportFile()
    {
        return lastReportFile;
    }

    protected int getRecordInterval()
    {
        return SimulationSettings.RECORD_INTERVAL;
    }

    private boolean shouldRecord(int step)
    {
        return step == 0 || step % getRecordInterval() == 0;
    }

    private File writeReport(Field field, SimulationContext context, String reason)
    {
        File reportFile = new File(reportFileName).getAbsoluteFile();
        try(PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("<!doctype html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<title>Savanna Simulation Step Report</title>");
            writer.println("<style>");
            writer.println("body{font-family:Arial,sans-serif;margin:24px;color:#222;}");
            writer.println("h1{font-size:24px;margin-bottom:4px;}");
            writer.println("p{line-height:1.35;}");
            writer.println("table{border-collapse:collapse;width:100%;font-size:12px;}");
            writer.println("th,td{border:1px solid #ccc;padding:6px;vertical-align:top;}");
            writer.println("th{background:#f0f3f6;text-align:left;position:sticky;top:0;}");
            writer.println("tr:nth-child(even){background:#fafafa;}");
            writer.println(".summary{background:#f7fbef;border:1px solid #bdd49b;padding:10px;}");
            writer.println("</style>");
            writer.println("</head>");
            writer.println("<body>");
            writer.println("<h1>African Savanna Simulation Step Report</h1>");
            writer.println("<div class=\"summary\">");
            writer.println("<p><strong>Finish reason:</strong> " + escape(reason) + "</p>");
            writer.println("<p><strong>Final state:</strong> " +
                           escape(new StepSnapshot(context.getStep(), field, context).toTextLine()) +
                           "</p>");
            writer.println("</div>");
            writer.println("<h2>Interval record</h2>");
            writer.println("<p>Records are saved every " + getRecordInterval() +
                           " simulation steps, plus the start and final state.</p>");
            writer.println("<table>");
            writer.println("<tr><th>Step</th><th>Environment</th><th>Total</th><th>Species details</th></tr>");
            for(String row : htmlRows) {
                writer.println(row);
            }
            writer.println("</table>");
            writer.println("</body>");
            writer.println("</html>");
        }
        catch(IOException e) {
            System.out.println("Could not write simulation report: " + e.getMessage());
        }
        return reportFile;
    }

    protected static String escape(String text)
    {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Immutable snapshot of a single step.
     */
    protected static class StepSnapshot
    {
        private final int step;
        private final String weather;
        private final String time;
        private final int grassPercent;
        private final int diseased;
        private final int total;
        private final Map<String, SpeciesStepStats> speciesStats;

        StepSnapshot(int step, Field field, SimulationContext context)
        {
            this.step = step;
            weather = context.getWeatherSystem().getDisplayText();
            time = context.getClock().getDisplayText();
            grassPercent = (int)Math.round(context.getFoodSystem().getAverageFoodLevel() * 100);
            diseased = context.getDiseaseSystem().countInfected(field);
            total = field.getLivingPopulation();
            speciesStats = collectSpeciesStats(field);
        }

        int getStep()
        {
            return step;
        }

        int getGrassPercent()
        {
            return grassPercent;
        }

        int getDiseased()
        {
            return diseased;
        }

        int getTotal()
        {
            return total;
        }

        Map<String, Integer> getSpeciesTotals()
        {
            Map<String, Integer> totals = new LinkedHashMap<>();
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String name = factory.getProfile().getName();
                SpeciesStepStats stats = speciesStats.get(name);
                totals.put(name, stats == null ? 0 : stats.total);
            }
            return totals;
        }

        int getAverageSurvival()
        {
            int counted = 0;
            double totalSurvival = 0.0;
            for(SpeciesStepStats stats : speciesStats.values()) {
                counted += stats.total;
                totalSurvival += stats.totalSurvival;
            }
            return counted == 0 ? 0 : (int)Math.round(totalSurvival / counted);
        }

        int getAverageStamina()
        {
            int counted = 0;
            double totalStamina = 0.0;
            for(SpeciesStepStats stats : speciesStats.values()) {
                counted += stats.total;
                totalStamina += stats.totalStamina;
            }
            return counted == 0 ? 0 : (int)Math.round(totalStamina / counted);
        }

        String toTextLine()
        {
            return "Step " + step +
                   " | Weather=" + weather +
                   " | Time=" + time +
                   " | Grass=" + grassPercent + "%" +
                   " | Diseased=" + diseased +
                   " | Total=" + total +
                   " | " + speciesDetailsText();
        }

        String toHtmlRow()
        {
            return "<tr><td>" + step + "</td><td>" +
                   "Weather: " + escape(weather) + "<br>" +
                   "Time: " + escape(time) + "<br>" +
                   "Grass: " + grassPercent + "%<br>" +
                   "Diseased: " + diseased +
                   "</td><td>" + total + "</td><td>" +
                   escape(speciesDetailsText()).replace("; ", "<br>") +
                   "</td></tr>";
        }

        private String speciesDetailsText()
        {
            StringBuilder details = new StringBuilder();
            for(SpeciesFactory factory : SpeciesRegistry.getFactories()) {
                String name = factory.getProfile().getName();
                SpeciesStepStats stats = speciesStats.get(name);
                if(stats == null) {
                    stats = new SpeciesStepStats();
                }
                if(details.length() > 0) {
                    details.append("; ");
                }
                details.append(name)
                       .append(": total=").append(stats.total)
                       .append(", male=").append(stats.male)
                       .append(", female=").append(stats.female)
                       .append(", cub=").append(stats.cub)
                       .append(", juvenile=").append(stats.juvenile)
                       .append(", adult=").append(stats.adult)
                       .append(", infected=").append(stats.infected)
                       .append(", avgSurvival=").append(stats.averageSurvival()).append("%")
                       .append(", avgStamina=").append(stats.averageStamina()).append("%")
                       .append(", lowSurvival=").append(stats.lowSurvival)
                       .append(", starving1=").append(stats.starvingOne)
                       .append(", starving2=").append(stats.starvingTwo)
                       .append(", starving3=").append(stats.starvingThreeOrMore);
            }
            return details.toString();
        }

        private Map<String, SpeciesStepStats> collectSpeciesStats(Field field)
        {
            Map<String, SpeciesStepStats> statsBySpecies = new LinkedHashMap<>();
            for(Animal animal : field.getAnimals()) {
                if(animal instanceof SavannahAnimal && animal.isAlive()) {
                    SavannahAnimal savannahAnimal = (SavannahAnimal)animal;
                    String name = savannahAnimal.getProfile().getName();
                    SpeciesStepStats stats = statsBySpecies.get(name);
                    if(stats == null) {
                        stats = new SpeciesStepStats();
                        statsBySpecies.put(name, stats);
                    }
                    stats.total++;
                    if(savannahAnimal.getSex() == Sex.MALE) {
                        stats.male++;
                    }
                    else {
                        stats.female++;
                    }
                    if(savannahAnimal.getLifeStage() == LifeStage.CUB) {
                        stats.cub++;
                    }
                    else if(savannahAnimal.getLifeStage() == LifeStage.JUVENILE) {
                        stats.juvenile++;
                    }
                    else {
                        stats.adult++;
                    }
                    if(savannahAnimal.isInfected()) {
                        stats.infected++;
                    }
                    stats.totalSurvival += savannahAnimal.getSurvivalPercent();
                    stats.totalStamina += savannahAnimal.getStaminaPercent();
                    if(savannahAnimal.getSurvivalPercent() < 30.0) {
                        stats.lowSurvival++;
                    }
                    if(savannahAnimal.getStarvingSteps() == 1) {
                        stats.starvingOne++;
                    }
                    else if(savannahAnimal.getStarvingSteps() == 2) {
                        stats.starvingTwo++;
                    }
                    else if(savannahAnimal.getStarvingSteps() >= 3) {
                        stats.starvingThreeOrMore++;
                    }
                }
            }
            return statsBySpecies;
        }
    }

    private static class SpeciesStepStats
    {
        int total;
        int male;
        int female;
        int cub;
        int juvenile;
        int adult;
        int infected;
        int lowSurvival;
        int starvingOne;
        int starvingTwo;
        int starvingThreeOrMore;
        double totalSurvival;
        double totalStamina;

        int averageSurvival()
        {
            return total == 0 ? 0 : (int)Math.round(totalSurvival / total);
        }

        int averageStamina()
        {
            return total == 0 ? 0 : (int)Math.round(totalStamina / total);
        }
    }
}
