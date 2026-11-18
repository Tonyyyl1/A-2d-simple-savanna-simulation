import java.awt.BorderLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Bilingual read-only status table for an inspect-mode animal selection.
 */
public class AnimalStatusLog
{
    private AnimalStatusLog() {}

    public static void showDialog(Frame parent, SavannahAnimal animal,
                                  SimulationContext context, Field field)
    {
        if(animal == null) {
            return;
        }
        JDialog dialog = new JDialog(parent,
            "Animal Status Log / 动物状态日志", false);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel(" " + animal.getProfile().getName() +
            " #" + animal.getId() + " / 动物状态");
        dialog.add(title, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
            rowsFor(animal, context, field),
            new String[] {"Metric / 指标", "Value / 数值"}) {
                public boolean isCellEditable(int row, int column)
                {
                    return false;
                }
            };
        JTable table = new JTable(model);
        table.setRowHeight(24);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton close = new JButton("Close / 关闭");
        close.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel();
        buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);

        dialog.setSize(640, 460);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    public static String[][] rowsFor(SavannahAnimal animal,
                                     SimulationContext context,
                                     Field field)
    {
        List<String[]> rows = new ArrayList<>();
        Location location = animal.getLocation();
        SpeciesProfile profile = animal.getProfile();
        TerrainType terrain = terrainAt(context, location);

        add(rows, "Species / 物种",
            profile.getName() + " / " + speciesChinese(profile.getName()));
        add(rows, "Animal ID / 动物编号", String.valueOf(animal.getId()));
        add(rows, "Role / 生态角色",
            profile.isPredator() ? "Predator / 捕食者" : "Herbivore / 食草动物");
        add(rows, "Alive / 存活", yesNo(animal.isAlive()));
        add(rows, "Sex / 性别", sexText(animal.getSex()));
        add(rows, "Life stage / 生命阶段",
            animal.getLifeStage() + " / " + lifeStageChinese(animal.getLifeStage()));
        add(rows, "Age / 年龄", animal.getAge() + " hours / " +
            animal.getAge() + " 模拟小时");
        add(rows, "Location / 位置", location == null ? "Unknown / 未知" :
            "row " + location.row() + ", col " + location.col() +
            " / 行 " + location.row() + "，列 " + location.col());
        add(rows, "Terrain / 地形", terrain == null ? "Unknown / 未知" :
            terrain.getDisplayName() + " / " + terrainChinese(terrain));
        add(rows, "Food / 食物",
            animal.getFoodLevel() + "/" + profile.getMaxFoodLevel() +
            " (" + percent(animal.getSurvivalPercent()) + ")");
        add(rows, "Survival pressure / 生存压力",
            percent(animal.getSurvivalPressure() * 100.0) +
            (animal.isSurvivalCritical() ? " - critical / 危急" :
                                           " - stable / 稳定"));
        add(rows, "Stamina / 体力", percent(animal.getStaminaPercent()));
        add(rows, "Stamina stage / 体力阶段",
            animal.getStaminaStage() + " / " +
            staminaChinese(animal.getStaminaStage()));
        add(rows, "Disease / 疾病", diseaseText(animal));
        add(rows, "Active now / 当前活跃", activeText(animal, context));
        add(rows, "Weather / 天气", weatherText(context));
        add(rows, "Time / 时间", timeText(context));
        add(rows, "Population / 同类数量", populationText(animal, context));
        add(rows, "Recent activity / 最近行为",
            recentActivityText(animal, context));
        add(rows, "Risk note / 风险提示", riskText(animal));

        return rows.toArray(new String[rows.size()][]);
    }

    private static void add(List<String[]> rows, String metric, String value)
    {
        rows.add(new String[] { metric, value });
    }

    private static TerrainType terrainAt(SimulationContext context,
                                         Location location)
    {
        if(context == null || location == null) {
            return null;
        }
        return context.getTerrainMap().getTerrainAt(location);
    }

    private static String percent(double value)
    {
        return (int)Math.round(value) + "%";
    }

    private static String yesNo(boolean value)
    {
        return value ? "Yes / 是" : "No / 否";
    }

    private static String sexText(Sex sex)
    {
        return sex == Sex.MALE ? "Male / 雄性" : "Female / 雌性";
    }

    private static String diseaseText(SavannahAnimal animal)
    {
        if(!animal.isInfected()) {
            return "Healthy / 健康";
        }
        return "Infected / 感染，level " +
            percent(animal.getInfectionLevel() * 100.0) +
            ", resistance " + percent(animal.getDiseaseResistance() * 100.0) +
            " / 感染程度，抵抗力";
    }

    private static String activeText(SavannahAnimal animal,
                                     SimulationContext context)
    {
        if(context == null) {
            return "Unknown / 未知";
        }
        return animal.getProfile().isActiveDuring(context.getClock().getPhase())
            ? "Active / 活跃" : "Resting / 休息";
    }

    private static String weatherText(SimulationContext context)
    {
        if(context == null) {
            return "Unknown / 未知";
        }
        WeatherType weather = context.getWeatherSystem().getCurrentWeather();
        return weather + " / " + weatherChinese(weather);
    }

    private static String timeText(SimulationContext context)
    {
        if(context == null) {
            return "Unknown / 未知";
        }
        return "Step " + context.getStep() + ", " +
            context.getClock().getDisplayText() + " / 步数与模拟时间";
    }

    private static String populationText(SavannahAnimal animal,
                                         SimulationContext context)
    {
        if(context == null) {
            return "Unknown / 未知";
        }
        String species = animal.getProfile().getName();
        return context.getPopulation(species) + " " + species +
            " / 当前同类数量";
    }

    private static String recentActivityText(SavannahAnimal animal,
                                             SimulationContext context)
    {
        if(context == null) {
            return "No context / 无上下文";
        }
        List<String> events = new ArrayList<>();
        List<SimulationEvent> recent = context.getRecentEvents();
        for(int index = recent.size() - 1; index >= 0 && events.size() < 5;
            index--) {
            SimulationEvent event = recent.get(index);
            if(event.actorId == animal.getId() ||
               event.targetId == animal.getId()) {
                events.add(eventText(event, animal.getId()));
            }
        }
        if(events.isEmpty()) {
            return "No recent event / 暂无近期事件";
        }
        return String.join(" | ", events);
    }

    private static String eventText(SimulationEvent event, long animalId)
    {
        String role = event.targetId == animalId ? "target / 目标" :
            "actor / 主体";
        return "step " + event.step + " " + event.type + " / " +
            eventChinese(event.type) + " (" + role + ")";
    }

    private static String riskText(SavannahAnimal animal)
    {
        List<String> notes = new ArrayList<>();
        if(animal.isSurvivalCritical()) {
            notes.add("low food / 食物危急");
        }
        if(animal.getStaminaStage() == StaminaStage.LOW) {
            notes.add("low stamina / 体力偏低");
        }
        if(animal.isInfected()) {
            notes.add("disease / 疾病风险");
        }
        if(notes.isEmpty()) {
            return "Normal / 正常";
        }
        return String.join(", ", notes);
    }

    private static String speciesChinese(String species)
    {
        if(SpeciesRegistry.LION.equals(species)) {
            return "狮子";
        }
        if(SpeciesRegistry.CHEETAH.equals(species)) {
            return "猎豹";
        }
        if(SpeciesRegistry.ZEBRA.equals(species)) {
            return "斑马";
        }
        if(SpeciesRegistry.BUFFALO.equals(species)) {
            return "水牛";
        }
        if(SpeciesRegistry.GAZELLE.equals(species)) {
            return "瞪羚";
        }
        return "未知物种";
    }

    private static String lifeStageChinese(LifeStage stage)
    {
        switch(stage) {
            case CUB:      return "幼崽";
            case JUVENILE: return "亚成体";
            case ADULT:    return "成年";
            default:       return "未知";
        }
    }

    private static String staminaChinese(StaminaStage stage)
    {
        switch(stage) {
            case HIGH:   return "高";
            case MEDIUM: return "中";
            case LOW:    return "低";
            default:     return "未知";
        }
    }

    private static String terrainChinese(TerrainType terrain)
    {
        switch(terrain) {
            case WATERHOLE:  return "水池";
            case GRASSLAND:  return "草地";
            case BUSH:       return "灌木带";
            case OPEN_PLAIN: return "开阔平原";
            case DRY_SOIL:   return "干土";
            default:         return "未知";
        }
    }

    private static String weatherChinese(WeatherType weather)
    {
        switch(weather) {
            case RAIN:    return "降雨";
            case FOG:     return "雾";
            case DROUGHT: return "干旱";
            case CLEAR:   return "晴朗";
            default:      return "未知";
        }
    }

    private static String eventChinese(SimulationEvent.EventType type)
    {
        switch(type) {
            case HUNT:          return "捕猎";
            case GRAZE:         return "进食";
            case BIRTH:         return "出生";
            case INFECTION:     return "感染";
            case RECOVERY:      return "康复";
            case DISEASE_DEATH: return "病死";
            case MOVE:          return "移动";
            default:            return "事件";
        }
    }
}
