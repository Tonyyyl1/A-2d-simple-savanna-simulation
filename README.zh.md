# 非洲草原捕食者—猎物模拟

[English](./README.md) | [简体中文](./README.zh.md)


版本：**1.5**

这是一个使用 Java 和 BlueJ 开发、采用面向对象设计的非洲草原生态系统模拟项目。

本项目将经典的狐狸与兔子捕食者—猎物模型扩展为一个包含五种动物的生态系统，并加入天气、疾病、草地、食物链、繁殖、体力、生存压力、可视化输出、实时图表、运行报告以及无外部依赖测试套件。

## 版本亮点

* **1.5／共享渲染器、状态弹窗与口渴实验系统**：
  普通动物标记现在统一通过共享的 `FieldRenderer` 进行绘制，使实时视图与未来的快照工具能够共用 `VisualGridGeometry` 和 `VisualFootprint` 渲染路径。

  点击检查动物时，会打开增强后的双语 `AnimalStatusLog`，其中包含摘要面板、水分状态和近期活动数据。

  实验性的口渴与饮水系统已经接入 `SimulationConfig` 和 `StartupConfigDialog`，但该功能**默认关闭**。启用后，动物会在可通行的水塘岸边格子饮水，而不会进入不可通行的 `WATERHOLE` 水域。

* **1.4／上下文随机数生成器与水域检测工具**：
  生态模拟中的随机性现在由各自的 `SimulationContext` 管理，因此不同模拟运行不再依赖共享的全局随机数流。

  `WaterSafetyProbe` 会在多种面板尺寸下检测普通动物标记是否错误覆盖水面，其中包括网格缩放比例不是整数的情况。

  发布版本现在统一存放在 `releases/<tag>/` 目录中，包含可运行的 JAR、与其对应的源代码压缩包和 `MANIFEST.txt`。

* **早期第一阶段／地形与反馈系统**：
  新增独立的 2.5D 草原地形背景层，并改进模拟反馈信息。

  地图包含确定生成的水塘、草地区域、灌木带、开阔平原、干燥土壤以及季节性低地走廊。

  图形界面现在将地形、动物符号、天气与时间覆盖层以及系统指标分别绘制。终端运行器也会在运行过程中输出清晰的生态系统状态快照。

* **1.0**：完成体力、生存、可视化模拟、报告系统和长期稳定性验证。

* **1.1**：新增无外部依赖测试系统、`1000` 步和 `5000` 步稳定性测试，并记录了效率更高的参数调优流程。

完整版本历史请参阅 [CHANGELOG.md](./CHANGELOG.md)。

## 动物种类

* **狮子（Lion）**：捕食者；捕猎瞪羚、斑马和水牛。
* **猎豹（Cheetah）**：捕食者；主要捕猎瞪羚。
* **斑马（Zebra）**：食草动物。
* **水牛（Buffalo）**：食草动物。
* **瞪羚（Gazelle）**：食草动物。

## 主要功能

* 包含五种动物的非洲草原生态系统。
* 包含晴天、降雨、雾和干旱的天气系统。
* 包含环境感染、接触传播和捕食传播的疾病系统。
* 食草动物可以进食草地，天气会影响植物生长。
* 食物链捕食系统会根据猎物数量、天气、体力和脆弱程度决定捕猎结果。
* 基于雌雄性别的繁殖系统，成年动物必须在附近找到合适的成年配偶。
* 包含幼崽、未成年和成年三个生命阶段。
* 生存值根据 `foodLevel / maxFoodLevel` 计算。
* 延迟饥饿死亡：动物只有连续三个模拟步骤处于饥饿状态后才会死亡。
* 体力系统覆盖移动、捕猎、进食、繁殖和每日恢复。
* 可视化模拟支持暂停、继续以及停止并退出。
* 实时图表窗口展示种群、疾病、体力、生存状态和草地水平。
* 每隔 `100` 个模拟步骤生成一次记录，并最终生成 HTML 报告。
* 使用确定性生成的平滑地形背景，并与动物图层分开绘制。
* 2.5D 可视化效果包括地形阴影、低干扰地形标签、代表性动物符号、天气与时间色调，以及草地、疾病、生存和体力实时指标条。
* 无界面运行时，每隔 `100` 步输出一次终端诊断信息，包括种群、天气、草地、疾病、体力、生存状态、饥饿情况、警告信号、趋势变化和近期事件摘要。
* 实验性地图与疾病压力运行器，可测试 `2x`、`3x` 和 `4x` 线性地图尺寸，并允许配置初始生成、初始种群、繁殖、疾病传播和死亡率倍率。
* 独立的视口交互控制器，支持纯鼠标、纯键盘以及鼠标键盘混合的平移和缩放操作。
* 默认键盘操作：

  * `WASD`：平移视口；
  * `Q`：放大；
  * `E`：缩小。
* 包含单元测试、系统交互测试和集成测试的无外部依赖测试套件。
* 支持无界面的长期稳定性验证。
* 每次模拟运行通过 `SimulationContext.getRandom()` 使用独立的生态随机数流，固定的视觉随机数源与生态行为随机性相互分离。
* 普通动物标记通过共享的 `FieldRenderer` 绘制，并复用 `VisualGridGeometry` 和 `VisualFootprint`。
* 增强后的双语动物状态弹窗包含摘要指标、水分状态和口渴系统状态。
* 可选的实验性口渴与饮水系统默认关闭，可在启动配置窗口中启用。

## 时间模型

* `1 个模拟步骤 = 1 个模拟小时`
* `24 个模拟步骤 = 1 个模拟日`

为了提高性能，可视化画面每隔 `100` 个模拟步骤刷新一次，但生态模型仍然会计算每一个步骤。

## 可视化与终端反馈

第一阶段在不改变动物行为的前提下加入了可视化地形图层。

`SimulationContext` 持有一个 `TerrainMap`，`SimulatorView` 会先将该地图渲染为缓存背景图像，然后在透明动物图层上绘制动物。

地形地图使用固定随机种子的坐标规则生成，而不是使用随机像素噪声。地图包含：

* 位于地图左侧中央附近的圆形水塘；
* 环绕水塘连续分布的草地区域；
* 分布在水塘附近、地图边缘和低地走廊附近的灌木带；
* 横跨地图中央和右上方的大面积开阔平原；
* 位于地图下方和右下方的干燥土壤；
* 一条以平缓曲线横穿地图的季节性低地走廊。

渲染系统采用 2.5D 风格。大型地形区域具有渐变光照、阴影偏移、高亮边界、地形专属纹理标记和较为低调的地形标签。

动物图层会绘制具有代表性的个体符号：

* 所有捕食者保持可见；
* 感染疾病的动物保持可见；
* 生存状态危险的动物保持可见；
* 体力较低的动物保持可见；
* 数量密集的普通食草动物则采用抽样显示。

动物标记规则如下：

* 捕食者使用三角形标记；
* 食草动物使用圆形标记；
* 感染动物显示红色疾病点；
* 生存状态危险的动物显示橙色圆环；
* 体力较低的动物显示小型体力条。

种群聚合现在只用于对密集的普通动物进行抽样，不再在地图上绘制大面积压力色块。

普通动物标记的绘制逻辑现在位于 `FieldRenderer` 中，而 `SimulatorView` 继续负责：

* Swing 图层；
* 视口变换；
* 热力图；
* 检查模式中的动物动画；
* 界面覆盖层。

可视化图层还会根据天气和时间改变画面色调。

地图上的紧凑指标面板会显示：

* 草地水平；
* 疾病情况；
* 生存状态；
* 平均体力；
* 当前警告信号；
* 简短趋势变化。

因此，用户无需阅读完整报告，也能快速了解当前生态系统状态。

图形界面与终端运行器共用同一个 `SimulationDiagnostics` 状态快照，从而保证两种输出中的数据一致。

在无界面运行模式下，`SimulationRunner` 会在以下时间输出紧凑的诊断信息：

* 模拟开始时；
* 每隔 `100` 个步骤；
* 模拟结束时。

每条诊断信息包含：

* 总种群；
* 捕食者与猎物压力；
* 草地水平；
* 疾病情况；
* 平均体力；
* 平均生存值；
* 低生存值动物数量；
* 低体力动物数量；
* 饥饿动物数量；
* 与上一条诊断信息相比的趋势变化；
* 简短事件摘要；
* 高层级警告信号。

可选的口渴实验系统采用较为保守的设计，并且默认关闭。

启用后：

* 动物的水分会随时间下降；
* 干旱天气下水分下降得更快；
* 降雨天气下水分下降得更慢；
* 口渴动物会寻找可通行的水塘岸边格子；
* 动物饮水时会记录 `DRINK` 事件。

水塘地形 `WATERHOLE` 本身始终不可通行。

## 快速开始

### 使用 JAR 运行可视化模拟

```bash
java -jar savanna-simulation.jar
```

默认情况下，程序会运行 `200000` 个模拟步骤。

运行较短的可视化模拟：

```bash
java -jar savanna-simulation.jar 5000
```

可视化版本会打开：

* 主模拟窗口；
* 实时间隔记录窗口；
* 实时图表窗口。

### 从源代码运行

编译：

```bash
javac *.java
```

运行可视化模拟：

```bash
java VisualSimulationRunner
```

运行较短的可视化模拟：

```bash
java VisualSimulationRunner 5000
```

## 测试与稳定性

项目包含一套使用纯 Java 编写的测试系统，不需要 JUnit、Maven 或 Gradle。

运行默认测试套件：

```bash
java AllTests
```

该命令会运行：

* 单元测试；
* 系统交互测试；
* `1000` 步无界面稳定性测试。

最近一次记录的测试结果：

```text
Passed 180/180 tests in 43830 ms
```

运行完整的每日测试套件：

```bash
java AllTests full
```

该命令会额外运行一次 `5000` 步无界面稳定性测试。

当前说明：

```text
建议在大规模参数调优或行为修改后运行；当前默认测试门槛为 ./test.sh。
```

运行当前水面标记覆盖检测：

```bash
java WaterSafetyProbe 1000 100
```

最近一次记录的结果：

```text
water animals == 0
ordinary visual water samples == 0
visual water samples == 0
Water safety probe passed through step 1000
```

运行最终长期稳定性验证：

```bash
java SimulationRunner 200000
```

运行地图与疾病压力实验的快速检查：

```bash
java SimulationExperimentRunner smoke
```

运行完整实验矩阵：

```bash
java SimulationExperimentRunner
```

运行选定的平衡型 `3x` 地图候选配置：

```bash
java SimulationExperimentRunner best3x
```

最近一次默认 `java SimulationRunner 5000` 运行结果：

```text
Lion=358, Cheetah=109, Zebra=618, Buffalo=512, Gazelle=1046
Final total=2643, final balance ratio=9.60, thirst=off
```

此前通过验证的运行结果：

```text
Completed 200000 steps without extinction.
Final balance ratio: 6.03
Runtime: 1116418 ms
```

## 推荐的参数调优流程

1. 运行 `java AllTests`。
2. 测试通过后，运行 `java AllTests full`。
3. 完成较大的参数修改后，运行 `java SimulationRunner 200000`。

这种分层测试流程能够在执行耗时较长的模拟前发现局部错误。

当前的计时数据表明：

* `AllTests full` 比直接运行一次 `200000` 步模拟快约 `96.94%`；
* 在进行多轮参数调优时，该流程可将整体调优时间减少约 `46.94%`。

详细计算请参阅 [TUNING_EFFICIENCY.md](./TUNING_EFFICIENCY.md)。

## 可视化控制

主模拟窗口包含以下控制按钮：

* **Pause**：暂停模拟。
* **Resume**：从暂停状态继续运行。
* **Stop & Exit**：停止模拟、写入最终报告并关闭程序。

直接关闭模拟窗口时，程序也会先写入最终报告，然后退出。

## 报告系统

可视化模拟会生成：

* 实时文本记录窗口；
* 最终的 `savanna-simulation-step-report.html` 文件。

系统每隔 `100` 个模拟步骤保存一次记录，同时也会保存初始状态和最终状态。

每条记录包含：

* 天气；
* 时间；
* 草地水平；
* 疾病数量；
* 总种群数量；
* 各物种种群数量；
* 雄性与雌性数量；
* 幼崽、未成年和成年动物数量；
* 感染动物数量；
* 平均生存值；
* 平均体力；
* 低生存值动物数量；
* 饥饿状态计数。

## 系统架构

项目将主要生态行为拆分为接口和对应的实现类：

* `WeatherSystem` / `SeasonalWeatherSystem`
* `DiseaseSystem` / `SavannahDiseaseSystem`
* `FoodSystem` / `GrasslandFoodSystem`
* `PredationSystem` / `FoodChainPredationSystem`
* `BreedingSystem` / `MateFindingBreedingSystem`
* `SimulationRecorder` / `StepReportRecorder`
* `SimulationEventListener` / `EventAccumulator`
* `ThirstSystem` / `SavannahThirstSystem`
  实验性功能，默认关闭。

物种参数集中存放在 `SpeciesRegistry` 和 `SpeciesProfile` 中。

动物的共同行为由 `SavannahAnimal` 实现。

`SimulationContext.getRecentEvents()` 会刻意将近期事件数量限制为最新的 `600` 条，用于检查模式界面。

需要处理完整运行过程的功能应使用：

* 通过 `SimulationEventListener` 和 `EventAccumulator` 收集的全程事件；
* 或由 `SnapshotRecorder` 捕获的不可变 `SimulationSnapshot` 世界快照。

这些功能包括：

* 全程热力图；
* 回放索引；
* 长时间事件摘要；
* 未来的时间线工具。

可视化窗口包含：

* `Heatmap / 热力图` 开关；
* 事件类型选择器。

热力图覆盖层根据整个运行过程中累计的事件绘制，而不是仅使用最近的 `600` 条事件。

默认的可视化启动窗口中包含 `Experimental thirst system` 复选框。

该选项默认未勾选，点击恢复默认设置后也会重新关闭。

## 重要文件

* `VisualSimulationRunner.java`：启动图形界面模拟。
* `SimulationRunner.java`：运行无界面的长期稳定性验证。
* `AllTests.java`：运行无外部依赖测试套件。
* `Simulator.java`：主模拟循环。
* `SimulatorView.java`：可视化网格窗口。
* `FieldRenderer.java`：共享的普通动物标记渲染器。
* `ThirstSystem.java` / `SavannahThirstSystem.java`：可选的岸边饮水和水分状态行为。
* `SimulationChartWindow.java`：实时图表窗口。
* `StepReportRecorder.java`：间隔记录和 HTML 报告生成。
* `EventAccumulator.java`：统计完整运行过程中的事件，用于热力图和事件摘要。
* `SimulationSnapshot.java`：供未来回放和时间线工具使用的不可变世界快照。
* `SnapshotRecorder.java`：收集不可变世界快照。
* `SpeciesRegistry.java`：物种配置。
* `SavannahAnimal.java`：动物共同行为。
* `CHANGELOG.md`：版本历史。
* `README.zh.md`：项目中文说明文档。
* `TUNING_EFFICIENCY.md`：参数调优流程与计时数据。
* `UPDATE_NOTES.md`：简要更新说明。

## 发布文件

新的发布版本会写入：

```text
releases/<version-tag>/
```

其中包含：

* `savanna-simulation-<version-tag>.jar`：可直接运行的可视化模拟程序。
* `savanna-simulation-<version-tag>-source.zip`：与该 JAR 构建版本完全对应的源代码快照。
* `MANIFEST.txt`：记录构建日期、主类、源代码快照以及 JAR 验证状态。

旧的 `outputs/` 文件夹会继续保留，作为此前交付版本的历史记录。

新的 JAR 发布版本应统一使用：

```text
releases/<version-tag>/
```

## 注意事项

本项目是一套用于教学和程序设计展示的生态模拟，而不是经过科学校准的真实生态模型。

其中的参数经过调整，目标是创建一个稳定、易于解释的捕食者—猎物生态系统，使其能够长期运行，而不需要在物种灭绝后强制重新生成动物。
