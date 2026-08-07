# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

非洲草原捕食者-猎物生态模拟（Java + 原 BlueJ 工程）。将经典 foxes-and-rabbits 模型扩展为五物种（Lion / Cheetah / Zebra / Buffalo / Gazelle）生态系统，含天气、疾病、草料、食物链捕食、繁殖、体力、生存压力、可视化、图表、HTML 报告，以及一个零依赖测试套件。

构建系统是手写的 `javac`，**没有** Maven / Gradle / JUnit。源码、`.class`、`.ctxt`（BlueJ 上下文）、`package.bluej` 全部平铺在仓库根目录（无 `src/`、无 package 声明，全部 default package）。

## Commands

```bash
# 编译/测试/打包（在源码根目录）
./build.sh
./test.sh
./package.sh v1.5-renderer-status-thirst
./verify-jar.sh v1.5-renderer-status-thirst

# 运行可视化模拟（默认 200000 步）
java VisualSimulationRunner
java VisualSimulationRunner 5000        # 较短的可视化运行

# 无界面长跑稳定性验证
java SimulationRunner 200000

# 测试套件（零依赖，纯 Java）
java AllTests                            # 单元 + 系统 + 1000 步稳定性
java AllTests full                       # 额外加 5000 步稳定性

# 从打包 JAR 运行
java -jar ../../releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar

# 当前 v1.5 水面安全验证（三项都必须为 0）
java -cp ../../releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar WaterSafetyProbe 1000 100
java -cp ../../releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar WaterSafetyProbe 18500 500
```

单独跑某一类测试：`AllTests` 聚合 `SimulationUnitTests` / `SimulationSystemTests` /
`SimulationIntegrationTests`，可直接 `java SimulationUnitTests` 等单独运行其 `main`。

推荐调参流程（先便宜后昂贵，避免直接长跑）：
`java AllTests` → `java AllTests full` → 大改参数后才 `java SimulationRunner 200000`。

## Current v1.5 State

### v1.6 work started (working tree only)

- `ThirstBehaviorProbe` provides repeatable thirst observability without
  changing production random consumption. It reports the 3x map's passable,
  water, shoreline, and maximum shore-distance metrics plus interval hydration,
  thirst, shoreline occupancy, and `DRINK` counts.
- `SafeMarkerPlacement` and terrain-aware `FieldRenderer` preserve shoreline
  marker visibility by clipping painted pixels to visual land. `WaterSafetyProbe`
  now also audits invisible shoreline markers at all supported panel sizes.
- The release/product version remains 1.5 until the full v1.6 plan is complete.
- `SurvivalAssessment` / `SurvivalSystem` now provide food, hydration, and
  overall values to diagnostics, status logs, reports, and renderer. The old
  `SavannahAnimal.getSurvivalPercent()` remains food-only for behavior
  compatibility; thirst-off overall survival equals that value.
- `HydrationStage` / `HydrationAssessment` now provide distance-aware
  `NORMAL` / `THIRSTY` / `CRITICAL` states. `THIRSTY` prioritizes water movement;
  `CRITICAL` protects waterward placement and records blocked-water events.

当前交付 jar：

`/Users/tony/Documents/Codex/2026-06-26/fan/releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar`

该 jar 的 manifest 是 `Main-Class: VisualSimulationRunner`。直接 `java -jar ...` 会进入启动配置界面；正式默认配置为 `SimulationConfig.default3x()` / `target3xBalanced()`，即 3x 地图，默认 random seed `1111`，terrain seed `20260629`。普通可视化入口不应再回到 1x。

最近一次已验证状态（2026-07-09，shared renderer + enhanced status dialog +
default-off thirst experiment 打包后）：

- 工作目录 `.class`：
  - `./test.sh` -> `180/180` passed
  - `java WaterSafetyProbe 1000 100` -> 每个检查点三项均为 `0`
  - `java SimulationRunner 5000` -> 默认 `thirst=off`，生态数据与既有
    `baseline-simulation-5000-after-rng.txt` 一致；差异仅为配置摘要新增
    `thirst=off` 和 `Elapsed ms`
  - `javac *.java` -> passed
- 打包后 jar 自身验证：
  - `./verify-jar.sh v1.5-renderer-status-thirst` -> jar 内 `AllTests 180/180`、
    `WaterSafetyProbe 1000 100`、`WaterSafetyProbe 18500 500` 全部通过

`WaterSafetyProbe` 现在不是只扫动物中心格。它必须同时验证三项：

1. `water animals == 0`
   - 真实 `Field` 中 alive animal 的 `location` 不在 `WATERHOLE`。
2. `ordinary visual water samples == 0`
   - 普通模拟层的小点/三角 marker 的最终绘制 footprint 不压到视觉水面。
   - 这个专门对应用户截图里“小点看起来在水池里”的问题。
   - 当前 probe 会审计多组 panel size，包括非整数网格尺寸 `1387x829`、`1093x731`、`640x480`。
3. `visual water samples == 0`
   - inspect/行为 actor 的逐帧动画 footprint 不扫过水面。

以后凡是改地形、普通绘制、inspect 绘制、SceneDirector 动画、移动/捕食/繁殖，都不能只跑中心格水审计；必须跑至少：

```bash
./build.sh
./test.sh
java WaterSafetyProbe 1000 100
```

打 jar 后还要用 jar 自己再跑：

```bash
./package.sh <version-tag>
./verify-jar.sh <version-tag>
```

## Architecture

**时间模型**：`1 step = 1 模拟小时`，`24 步 = 1 天`。生态模型每步都算，**可视化每 100 步才刷新一次**（性能）。

**核心循环**：`Simulator.java` 持有 `Field`（动物的网格世界，当前正式默认 3x 为 240 深 × 360 宽；baseline 仍可用于测试/对比）、
`SimulationContext`（共享系统 + 步级状态）、可选的 `SimulatorView`。每步 `context.startStep()`
推进时钟/天气/草料增长，然后遍历动物行为。

**系统通过接口 + 实现类解耦**，全部由 `SimulationContext` 持有并注入：

| 接口 | 实现 |
|------|------|
| `WeatherSystem` | `SeasonalWeatherSystem` |
| `DiseaseSystem` | `SavannahDiseaseSystem` |
| `FoodSystem` | `GrasslandFoodSystem` |
| `PredationSystem` | `FoodChainPredationSystem` |
| `BreedingSystem` | `MateFindingBreedingSystem` |
| `SimulationRecorder` | `StepReportRecorder` / `WindowedSimulationRecorder` / `NullSimulationRecorder` |

物种参数集中在 `SpeciesRegistry` + `SpeciesProfile`（`BasicSpeciesProfile`）。共享动物行为在
`SavannahAnimal`（具体物种 `Lion`/`Zebra`/… 仅是薄壳）。`Animal` 是顶层抽象。

**地形与移动**：`SimulationContext` 按 `SimulationConfig.getTerrainSeed()` 创建 `TerrainMap`。`TerrainMap.isPassable()` 以 `WATERHOLE` 为不可通行。初始化、founder placement、普通移动、繁殖位置、觅食候选、捕食候选和最终放置兜底都应走 passable 过滤。`TerrainNavigator` 提供地形感知邻格、range 搜索和一次 BFS reachability；`FoodChainPredationSystem` 每个 predator hunt 应只建一次 reachability，不要每个猎物重复 BFS。

**栖息地偏好**：`HabitatPreferences` 参与 spawn / movement / grazing / hunting。偏好是强权重，不是硬边界。水塘仍不可通行。

**可视化层**（关键）：`SimulatorView.FieldView` 把渲染分成地形背景层 `terrainImage`、普通动物 marker 层 `animalImage`、weather/time/metric overlay，以及 inspect actor 层。

- 普通模式：画 `terrainImage` + `animalImage`。
- inspect 模式：只画地形和 inspect actor，不再叠加普通小点层。
- 普通动物 marker 的实际绘制已抽到 `FieldRenderer`，`SimulatorView` 继续负责
  Swing 图层、viewport transform、heatmap、inspect actor 和 overlay。未来
  `RenderSnapshotTool` 应复用 `FieldRenderer`，不要再复制普通 marker 绘制逻辑。
- 地形、普通动物、inspect actor、点击命中和 probe 必须共享 `VisualGridGeometry` / `VisualFootprint`。不要再直接用 `xScale/yScale` 自己算动物格子；地形格、动物 marker、probe 都要使用同一套 rounded cell bounds。
- 普通动物 marker 绘制前必须通过 `VisualWaterMask.ordinaryAnimalFootprintTouchesWater()`；该类现在是 `VisualFootprint.ordinaryTouchesWater()` 的兼容薄壳。岸边 `GRASSLAND` 格子如果 marker footprint 会压到视觉水面，就不能画。
- inspect actor 由 `SceneDirector` 生成；当前要求显示 viewport 内所有可见动物 actor，不再裁到 20 个。高密度时只能稀疏低优先级 `forage` / `prowl` 文本，不能减少动物图标。
- `SceneDirector` 会 sanitize keyframes；若 actor 端点安全但插值轨迹扫水，会折叠到安全陆地 footprint，避免动画扫过水面。
- 放大模式点击 inspect actor 图标会打开 `AnimalStatusLog` 中英双语状态表。点击命中必须基于屏幕上的 actor footprint，而不是只按 `Field` 中心格。
- `AnimalStatusLog` 现在是摘要区 + 可测试表格。数据层仍通过
  `AnimalStatusLog.rowsFor()` 暴露，包含 `Hydration / 水分` 和
  `Thirst system / 口渴系统` 行。

**口渴实验功能**：`SimulationConfig.isThirstEnabled()` 默认为 `false`。
`StartupConfigDialog` 有 `Experimental thirst system` 复选框，默认不勾选。
开启时 `SimulationContext` 创建 `SavannahThirstSystem`，动物从 passable 岸边格
饮水并记录 `DRINK` 事件；`WATERHOLE` 仍不可通行。默认关闭时不要消耗额外生态
随机数，也不要改变既有基线。

**记录与报告**：`StepReportRecorder` 每 100 步快照，最终生成
`savanna-simulation-step-report.html`。无界面跑由 `SimulationRunner` 每 100 步打印诊断行
（`SimulationDiagnostics`）。

**随机源规则**：生态随机数归 `SimulationContext.getRandom()` 所有。`Simulator` 的动物顺序、
初始种群、founder placement、系统类、动物构造链和地形感知候选洗牌都应使用 context random。
视觉固定随机源（如图标纹理、地形 tile 装饰）可以独立使用固定 seed，但不能消费生态随机流。

**事件与长期观测**：`SimulationContext.recentEvents` 仍然是最近窗口，最大 600 条，只适合
inspect actor、最近行为文本和近期统计。长期热力图、回放索引、全程事件统计必须用
`SimulationEventListener` + `EventAccumulator`，不要从 `recentEvents` 反推全程历史。
`Simulator` 保存 listener 注册表并在 `reset()` 重建 `SimulationContext` 后重新挂载，避免观察器
丢失。当前 GUI 已在控制栏提供 `Heatmap / 热力图` 开关和事件类型下拉；热力图画在地形层之上、
普通动物/inspect actor 之下，按 `EventAccumulator` 的全程事件计数绘制，并跳过不可通行水塘格。

**快照基础**：`AnimalSnapshot` / `SimulationSnapshot` / `SnapshotRecorder` 是不可变状态快照
基础，用于后续时间轴、回放和导出。不要把 `Field`、`Animal` 或 `SimulationContext` 的可变引用
直接存成“历史快照”。

## Current Handoff / 接力状态

**最新接力与开发计划的唯一事实源**：`DEVELOPMENT_PLAN.zh.md`。该文件包含
2026-07-10 口渴行为实测、岸边 marker 隐藏根因、生存值设计决定、v1.6/v1.7
分批计划、验收矩阵和可直接复制到下一窗口的完整接力文案。本文下面的 v1.5
状态用于说明已交付基线；若“建议下一步”与计划文件冲突，以计划文件为准。

本轮目标是选择性吸收 `/Users/tony/claude code/savanna-simulation` 的优秀做法，
而不是整包覆盖当前主线。当前主线目录是：

`/Users/tony/Documents/Codex/2026-06-26/fan/work/savanna-simulation`

已完成吸收：

1. **生态随机源显式化**
   - `SimulationContext` 现在持有 per-run `Random`。
   - 生产模拟路径通过 `context.getRandom()` 传递随机流。
   - 已保留现有 `SimulationEventListener`、`EventAccumulator`、热力图、
     `AnimalSnapshot` / `SimulationSnapshot` / `SnapshotRecorder`。
2. **`WaterSafetyProbe` 增强**
   - 普通 marker 水面审计现在覆盖多种 panel size。
   - 包含非整数网格尺寸 `1387x829`、`1093x731`、`640x480`。
   - 审计的是实际绘制 footprint，不只是动物中心格。
3. **release 三件套**
   - `package.sh <tag>` 输出到 `releases/<tag>/`。
   - 每个 release 包含 jar、source zip、`MANIFEST.txt`。
   - `verify-jar.sh <tag>` 会验证 jar 本身并追加 manifest。
4. **维护文档**
   - `README.md`、`CHANGELOG.md` 和本文件已记录 context RNG、多尺寸 probe、
     release 规则和验证结果。
5. **共享 `FieldRenderer`**
   - `SimulatorView.FieldView.drawAnimal()` 已委托给 `FieldRenderer`。
   - `FieldRenderer` 复用 `VisualGridGeometry` / `VisualFootprint`。
   - 普通 marker 的疾病点、生存圈、低体力条和口渴水滴都在共享 renderer。
6. **增强状态弹窗**
   - `AnimalStatusLog` 增加摘要区。
   - `rowsFor()` 继续作为可测试数据层。
   - 已加入水分和口渴系统开关状态。
7. **默认关闭的口渴/饮水系统**
   - 新增 `ThirstSystem` / `SavannahThirstSystem`。
   - `SimulationConfig` 和 `StartupConfigDialog` 已接入 `thirstEnabled`。
   - 饮水发生在 passable 岸边格，不允许动物站进 `WATERHOLE`。

当前最新 release：

`/Users/tony/Documents/Codex/2026-06-26/fan/releases/v1.5-renderer-status-thirst/`

关键产物：

- `savanna-simulation-v1.5-renderer-status-thirst.jar`
- `savanna-simulation-v1.5-renderer-status-thirst-source.zip`
- `MANIFEST.txt`

已验证：

- `./test.sh` -> `180/180` passed
- `java WaterSafetyProbe 1000 100` -> 三项全 0
- `java SimulationRunner 5000` -> 默认 `thirst=off`，生态数据与既有
  context-RNG 基线一致；差异仅为配置摘要新增 `thirst=off` 和 `Elapsed ms`
- `javac *.java` -> passed
- `./verify-jar.sh v1.5-renderer-status-thirst` -> jar 内 `AllTests 180/180`、
  `WaterSafetyProbe 1000 100`、`WaterSafetyProbe 18500 500` 全部通过

接力注意：

- 当前目录不是 git 仓库，不要依赖 git 回滚。
- 剩余 `Randomizer.getRandom()` 主要是兼容构造器/旧测试入口；生产模拟主路径
  应继续使用 `context.getRandom()`。
- 不要用 `/Users/tony/claude code/savanna-simulation` 直接覆盖当前代码，
  否则会丢失现有事件监听、热力图、快照和共享视觉几何等主线优势。

建议下一步（按 `DEVELOPMENT_PLAN.zh.md` 执行）：

1. **永久化 `ThirstBehaviorProbe` 和当前失败基线**
   - 记录岸边可达性、分物种饮水率、寻水方向、刷新区间事件和多尺寸可见性。
2. **先修岸边可见性与 `DRINK` 表达**
   - marker 必须在陆地侧可见，同时保持动物水面像素为 0。
   - 不得继续依赖“整只隐藏岸边动物”让 WaterSafetyProbe 通过。
3. **再接入 `SurvivalAssessment`**
   - food/hydration pressure 分离；overall 取两者较低值。
   - `thirst=off` 时 hydration survival 必须为 100，默认基线不变。
4. **v1.6 完成可观测性和模型校准后，v1.7 再做行为意图管线**
   - 不要把高风险行为/位置重构与第一批视觉修复一次性混装。

## Critical Notes For Future Agents

- 不要再声称“动物不在水里”只因为 `animal.location` 不在 `WATERHOLE`。用户截图反复暴露的是最终可视 footprint 压到视觉水面。
- `WaterSafetyProbe` 的三项输出都必须是 0，少一项都不算完成。
- `VisualGridGeometry` 是地形/动物/probe 的共享坐标层，使用和 `TerrainMap.buildRegions()` 一致的 rounded cell bounds，专门防止非整除窗口尺寸下的坐标错位。
- `VisualFootprint` 是普通 marker 和 inspect actor 的共享 footprint 层。`SimulatorView`、`SceneDirector`、`VisualWaterMask`、`WaterSafetyProbe` 都应调用它，不要复制采样逻辑。
- `VisualWaterMask` 当前用 `TerrainMap.isVisuallyWaterAt()` + `VisualFootprint` 对普通 marker footprint 做快速水面判定，避免为了测试去渲染巨大的 AWT image。
- 如果修改 `TerrainMap.drawWaterhole()` 的视觉形状，必须同步 `TerrainMap.isVisuallyWaterAt()` / `VisualWaterMask`，否则测试和截图会再次不一致。
- 如果修改普通 marker 尺寸、阴影、缩放规则，必须同步 `VisualFootprint.ordinaryMarker()` / `VisualFootprint.ordinaryBounds()`。
- 如果修改 inspect actor 尺寸、锚点、插值、marker 逻辑，必须同步 `VisualFootprint.inspectActorTouchesWater()` / `VisualFootprint.inspectIconPixels()` 和相关 probe。
- 打包后必须验证 jar 本身，而不是只验证工作目录 `.class`。
- 新发布一律进入 `releases/<version-tag>/`，包含 jar、匹配源码 zip 和 `MANIFEST.txt`。
- 不要在生产生态路径新增 `Randomizer.getRandom()`；需要随机数时优先传入或使用 `context.getRandom()`。
- `recentEvents` 只保留 600 条。热力图/回放/长期统计不能依赖它；必须接事件监听器或不可变快照。
- 热力图绘制必须继续使用 `VisualGridGeometry.cellBounds()`，不能复制新的 row/col 到 pixel 公式。

## User Workflow Preference

When the user asks for `xhigh`, deep reasoning, architecture review, or says not to edit directly, first output a concrete execution plan and do not modify files.

The plan must include:
- Problem hypothesis and main suspect areas.
- Files to inspect.
- Step-by-step executable commands or code snippets.
- Architecture boundaries and intended changes.
- Verification strategy, including whether checks run against workspace `.class` files or the packaged jar.
- A clear note that implementation starts only after user confirmation.

For repeated visual/water/coordinate bugs, assume the root cause may be geometry mismatch between logical Field coordinates, terrain rendering, animal rendering, zoom transforms, and probe sampling. Do not claim the issue is fixed until all relevant layers are audited.
