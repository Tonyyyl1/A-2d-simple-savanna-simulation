# 非洲草原模拟：共享渲染、状态弹窗与口渴实验功能

## v1.6 开工状态

已按 `DEVELOPMENT_PLAN.zh.md` 开始 v1.6：加入永久
`ThirstBehaviorProbe`，并将岸边 marker 改为只裁剪水面像素、保留可见陆地
像素。当前正式版本仍为 1.5，综合生存值与后续校准尚未宣称完成。

当前工作树已继续接入 `SurvivalAssessment`，统一输出 food、hydration、overall
三套生存值；口渴关闭时 hydration 固定为 100%，overall 保持旧版 food survival，
不会改变既有生态行为决策。

当前开发快照继续加入三档口渴（`NORMAL`、`THIRSTY`、`CRITICAL`）、距离感知
储备、第二档头号寻水、饮水场景、增强饮水热力图，以及缩放/resize 一致性修复。

本文记录本轮改造的编写计划、实现边界和验证结果。主线目标是选择性吸收参考项目中的优秀做法，而不是整包覆盖当前工程。

## 编写计划

1. 抽取共享 `FieldRenderer`
   - 让 `SimulatorView` 和未来的 `RenderSnapshotTool` 共用普通动物 marker 绘制逻辑。
   - 保留现有 `VisualGridGeometry`、`VisualFootprint`、`VisualWaterMask` 作为唯一几何和水面安全来源。
   - 不把 Swing 窗口、缩放、热力图、inspect 动画层塞进 renderer。

2. 增强动物状态弹窗
   - 保留 `AnimalStatusLog.rowsFor()` 作为可测试数据层。
   - 在 UI 上增加摘要区，突出食物、生存、体力、水分、疾病和风险。
   - 状态表继续保留完整双语指标，方便教学和调试。

3. 接入口渴/饮水系统
   - 新功能必须默认关闭，不影响既有基线。
   - 通过 `SimulationConfig.isThirstEnabled()` 和 builder 开关控制。
   - 水塘 `WATERHOLE` 仍不可通行，动物只能在可通行岸边格饮水。
   - 第一版口渴影响保持保守：低水分先影响体力和食物压力，不直接激进改写生态平衡。

4. 初始化页面增加开关
   - `StartupConfigDialog` 增加 `Experimental thirst system` 复选框。
   - 默认不勾选，`Reset Defaults` 也恢复关闭。

5. 补测试与验证
   - 覆盖默认关闭、开启后扣水、岸边饮水、饮水事件、状态弹窗水分字段、BlueJ/命令行编译。
   - 继续运行默认测试、水面 probe 和 5000 步稳定性检查。

## 本轮完成

### 共享 renderer

新增 `FieldRenderer.java`，负责普通动物 marker 的复用绘制：

- 捕食者三角形、食草动物圆形。
- 生存压力橙色圈。
- 低体力黄色条。
- 感染红点。
- 口渴蓝色水滴。

`SimulatorView.FieldView.drawAnimal()` 已改为调用 `FieldRenderer.drawAnimal()`。视图层仍负责：

- terrain/animal 缓冲图层。
- viewport transform。
- heatmap。
- inspect 动画层。
- 天气、时间和指标 overlay。

### 状态弹窗

`AnimalStatusLog.showDialog()` 增加了顶部摘要区，原表格仍然保留。`rowsFor()` 新增：

- `Hydration / 水分`
- `Thirst system / 口渴系统`

口渴系统关闭时显示 `Disabled / 未启用`；开启后显示水分百分比，低水分时标记 `thirsty / 口渴`。

### 口渴/饮水系统

新增：

- `ThirstSystem.java`
- `SavannahThirstSystem.java`

配置接入：

- `SimulationConfig` 增加 `thirstEnabled`，默认 `false`。
- `SimulationConfig.Builder.thirstEnabled(boolean)` 可开启实验功能。
- `SimulationContext` 只在开启时创建 `SavannahThirstSystem`。

动物状态接入：

- `SavannahAnimal` 增加 `hydration` 和 `dehydrationSteps`。
- 默认水分初始化为确定值，不额外消耗随机数，避免默认关闭时改变历史随机流。
- 开启后每步扣水，干旱扣得更多，降雨扣得更少。
- 口渴动物会优先尝试在岸边饮水，或向最近岸边移动。

饮水规则：

- `WATERHOLE` 仍不可通行。
- 只有可通行且邻近水塘的岸边格可以饮水。
- 饮水会恢复水分，并记录 `SimulationEvent.EventType.DRINK`。

### 初始化页面

`StartupConfigDialog` 新增 `Experimental thirst system` 复选框：

- 默认关闭。
- 重置默认值后仍关闭。
- 勾选后通过 config builder 写入 `thirstEnabled(true)`。

`SimulationConfig.describe()` 现在包含：

```text
thirst=off
```

或：

```text
thirst=on
```

## 验证结果

本轮已经打包为：

```text
releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst.jar
releases/v1.5-renderer-status-thirst/savanna-simulation-v1.5-renderer-status-thirst-source.zip
releases/v1.5-renderer-status-thirst/MANIFEST.txt
```

已运行：

```bash
./verify-jar.sh v1.5-renderer-status-thirst
```

结果：

```text
jar AllTests 180/180
WaterSafetyProbe 1000 100 passed
WaterSafetyProbe 18500 500 passed
```

已运行：

```bash
./test.sh
```

结果：

```text
Passed 180/180 tests
```

已运行：

```bash
java WaterSafetyProbe 1000 100
```

结果：

```text
water animals == 0
ordinary visual water samples == 0
visual water samples == 0
Water safety probe passed through step 1000
```

已运行：

```bash
java SimulationRunner 5000
```

结果：

```text
Final: Step 5000 ... Pop=2643 {Lion=358, Cheetah=109, Zebra=618, Buffalo=512, Gazelle=1046}
Extinction: false
Final balance ratio: 9.60
Balance limit: 12.0
```

默认关闭口渴系统时，5000 步生态数据与既有 `baseline-simulation-5000-after-rng.txt` 一致；差异仅为配置摘要新增 `thirst=off` 和运行耗时不同。

## 后续开发计划

最新代码审计确认：口渴行为确实运行，但岸边普通 marker 会被水面安全检查
整体隐藏，百步刷新也很难采到只持续一个模拟步的饮水状态。后续优先级已经
从旧的快照工具建议调整为：

1. 建立永久口渴行为 probe 和失败测试。
2. 让岸边动物可见，同时继续保证动物像素不落入视觉水面。
3. 给 `DRINK` 增加区间事件保留和 inspect/普通视图表达。
4. 将水分以独立 pressure 接入综合生存评估。
5. 配置化并按地图距离、天气和物种校准口渴模型。
6. 后续版本再引入行为意图、统一动作执行和位置冲突处理。

完整实测数据、架构边界、逐文件任务、验收矩阵、发布步骤，以及可直接交给
下一个窗口的接力文案，统一维护在：

[`DEVELOPMENT_PLAN.zh.md`](DEVELOPMENT_PLAN.zh.md)

当前正式版本仍为 **1.5**；计划中的 v1.6/v1.7 功能尚未实现或打包。
