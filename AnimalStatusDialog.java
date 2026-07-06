import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Bilingual (Chinese/English) status log for a single animal, opened by
 * clicking the animal's icon in inspect (zoomed) mode.
 *
 * The dialog is a pure view over a snapshot of the animal, the simulation
 * context, and the recent event list. It never mutates simulation state.
 */
public class AnimalStatusDialog extends JDialog
{
    private static final int MAX_LOG_ROWS = 60;
    private static final int HOURS_PER_DAY = 24;

    public AnimalStatusDialog(JFrame owner, SavannahAnimal animal,
                              SimulationContext context,
                              List<SimulationEvent> events,
                              Image icon)
    {
        super(owner, false);
        setTitle(speciesName(animal.getProfile().getName()) +
                 " #" + animal.getId() + " 状态日志 Status Log");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(buildHeader(animal, icon), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            wrapTable("当前状态 Current Status",
                      buildStatusTable(animal, context)),
            wrapTable("事件日志 Event Log (最近 recent " +
                      MAX_LOG_ROWS + ")",
                      buildLogTable(animal, events)));
        split.setResizeWeight(0.52);
        split.setBorder(null);
        content.add(split, BorderLayout.CENTER);

        JButton close = new JButton("关闭 Close");
        close.addActionListener(event -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(new Dimension(560, 620));
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader(SavannahAnimal animal, Image icon)
    {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        if(icon != null) {
            header.add(new JLabel(new ImageIcon(
                icon.getScaledInstance(44, 44, Image.SCALE_SMOOTH))));
        }
        String role = animal.getProfile().isPredator()
            ? "捕食者 Predator" : "食草动物 Herbivore";
        JLabel title = new JLabel("<html><b>" +
            speciesName(animal.getProfile().getName()) + " #" + animal.getId() +
            "</b><br>" + role + "</html>");
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 13.0f));
        header.add(title);
        return header;
    }

    private JPanel wrapTable(String heading, JTable table)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(heading);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12.0f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JTable buildStatusTable(SavannahAnimal animal,
                                    SimulationContext context)
    {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"物种 Species",
            speciesName(animal.getProfile().getName())});
        rows.add(new String[] {"编号 ID", "#" + animal.getId()});
        rows.add(new String[] {"性别 Sex",
            animal.getSex() == Sex.MALE ? "雄性 Male"
                                        : "雌性 Female"});
        rows.add(new String[] {"年龄 Age",
            animal.getAge() + " 小时 hours (≈" +
            (animal.getAge() / HOURS_PER_DAY) + " 天 days)"});
        rows.add(new String[] {"生命阶段 Life stage",
            lifeStageName(animal.getLifeStage())});
        rows.add(new String[] {"食物 Food",
            animal.getFoodLevel() + " / " +
            animal.getProfile().getMaxFoodLevel()});
        String survival = Math.round(animal.getSurvivalPercent()) + "%";
        if(animal.isSurvivalCritical()) {
            survival += "  ⚠ 危急 CRITICAL";
        }
        rows.add(new String[] {"生存值 Survival", survival});
        rows.add(new String[] {"体力 Stamina",
            Math.round(animal.getStaminaPercent()) + "% (" +
            staminaStageName(animal.getStaminaStage()) + ")"});
        rows.add(new String[] {"疾病 Disease",
            animal.isInfected()
                ? "已感染 Infected (" +
                  Math.round(animal.getInfectionLevel() * 100) + "%)"
                : "健康 Healthy"});
        rows.add(new String[] {"连续饥饿 Starving steps",
            animal.getStarvingSteps() + " 步 steps"});
        Location location = animal.getLocation();
        rows.add(new String[] {"位置 Location",
            location == null ? "-" :
            "(" + location.row() + ", " + location.col() + ")"});
        if(context != null && location != null) {
            rows.add(new String[] {"地形 Terrain",
                terrainName(context.getTerrainMap().getTerrainAt(location))});
        }
        if(context != null) {
            rows.add(new String[] {"当前步 Step",
                context.getStep() + " (" + timeText(context.getStep()) + ")"});
            rows.add(new String[] {"天气 Weather",
                weatherName(context.getWeatherSystem().getCurrentWeather())});
        }

        DefaultTableModel model = readOnlyModel(new String[] {
            "属性 Attribute", "数值 Value"});
        for(String[] row : rows) {
            model.addRow(row);
        }
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(320);
        table.setFillsViewportHeight(true);
        return table;
    }

    private JTable buildLogTable(SavannahAnimal animal,
                                 List<SimulationEvent> events)
    {
        DefaultTableModel model = readOnlyModel(new String[] {
            "步 Step", "时间 Time",
            "事件 Event", "详情 Detail"});

        List<SimulationEvent> mine = new ArrayList<>();
        if(events != null) {
            for(SimulationEvent event : events) {
                if(event.actorId == animal.getId() ||
                   event.targetId == animal.getId()) {
                    mine.add(event);
                }
            }
        }
        int added = 0;
        for(int index = mine.size() - 1; index >= 0 && added < MAX_LOG_ROWS;
            index--, added++) {
            SimulationEvent event = mine.get(index);
            model.addRow(new String[] {
                String.valueOf(event.step),
                timeText(event.step),
                eventName(event, animal.getId()),
                eventDetail(event, animal.getId())});
        }
        if(model.getRowCount() == 0) {
            model.addRow(new String[] {"-", "-",
                "暂无记录 No recorded events", "-"});
        }

        JTable table = new JTable(model);
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(48);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        table.setFillsViewportHeight(true);
        return table;
    }

    private DefaultTableModel readOnlyModel(String[] columns)
    {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };
    }

    private String timeText(int step)
    {
        int day = step / HOURS_PER_DAY + 1;
        int hour = step % HOURS_PER_DAY;
        return "第" + day + "天 Day " + day + ", " +
               String.format("%02d:00", hour);
    }

    private String eventName(SimulationEvent event, long animalId)
    {
        boolean isActor = event.actorId == animalId;
        switch(event.type) {
            case HUNT:
                return isActor ? "捕猎成功 Hunt success"
                               : "遭捕食 Killed by predator";
            case GRAZE:
                return "进食牧草 Grazed";
            case BIRTH:
                return isActor ? "产下幼崽 Gave birth"
                               : "出生 Born";
            case INFECTION:
                if(isActor && event.targetId >= 0) {
                    return "传播疾病 Transmitted disease";
                }
                return "感染疾病 Infected";
            case RECOVERY:
                return "疾病康复 Recovered";
            case DISEASE_DEATH:
                return "病亡 Died of disease";
            case MOVE:
                return "移动 Moved";
            default:
                return String.valueOf(event.type);
        }
    }

    private String eventDetail(SimulationEvent event, long animalId)
    {
        boolean isActor = event.actorId == animalId;
        String at = "(" + event.row + ", " + event.col + ")";
        String from = event.fromRow >= 0
            ? "(" + event.fromRow + ", " + event.fromCol + ")" : null;
        switch(event.type) {
            case HUNT:
                return isActor
                    ? "猎物 prey: " + speciesName(event.targetSpecies) +
                      " @ " + at
                    : "捕食者 predator: " +
                      speciesName(event.actorSpecies);
            case GRAZE:
                return "位置 at " + at;
            case BIRTH:
                return isActor
                    ? "幼崽 cub: " + speciesName(event.targetSpecies) +
                      " @ " + at
                    : "母体 parent: " +
                      speciesName(event.actorSpecies);
            case INFECTION:
                if(event.targetId >= 0) {
                    return isActor
                        ? "目标 target: " +
                          speciesName(event.targetSpecies)
                        : "来源 source: " +
                          speciesName(event.actorSpecies);
                }
                return "环境感染 environmental @ " + at;
            case MOVE:
                return from == null ? "→ " + at : from + " → " + at;
            default:
                return "位置 at " + at;
        }
    }

    private static String speciesName(String species)
    {
        if(species == null) {
            return "-";
        }
        switch(species) {
            case SpeciesRegistry.LION:    return "狮子 Lion";
            case SpeciesRegistry.CHEETAH: return "猎豹 Cheetah";
            case SpeciesRegistry.ZEBRA:   return "斑马 Zebra";
            case SpeciesRegistry.BUFFALO: return "非洲水牛 Buffalo";
            case SpeciesRegistry.GAZELLE: return "瞪羚 Gazelle";
            default: return species;
        }
    }

    private static String lifeStageName(LifeStage stage)
    {
        switch(stage) {
            case CUB:      return "幼崽 Cub";
            case JUVENILE: return "亚成体 Juvenile";
            default:       return "成年 Adult";
        }
    }

    private static String staminaStageName(StaminaStage stage)
    {
        switch(stage) {
            case HIGH:   return "高 High";
            case MEDIUM: return "中 Medium";
            default:     return "低 Low";
        }
    }

    private static String terrainName(TerrainType terrain)
    {
        switch(terrain) {
            case WATERHOLE:  return "水塘 Waterhole";
            case GRASSLAND:  return "草地 Grassland";
            case BUSH:       return "灌木 Bush";
            case OPEN_PLAIN: return "开阔平原 Open plain";
            case DRY_SOIL:   return "旱土 Dry soil";
            default:         return terrain.getDisplayName();
        }
    }

    private static String weatherName(WeatherType weather)
    {
        switch(weather) {
            case CLEAR:   return "晴 Clear";
            case RAIN:    return "雨 Rain";
            case FOG:     return "雾 Fog";
            case DROUGHT: return "旱 Drought";
            default:      return String.valueOf(weather);
        }
    }
}
