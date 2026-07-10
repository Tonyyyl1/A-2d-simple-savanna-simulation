# 非洲草原模拟：共享渲染、状态弹窗与口渴实验功能
<p align="right">
  <a href="./README.md">English</a> | 简体中文
</p>

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

## 后续建议

1. 增加 `RenderSnapshotTool`
   - 直接复用 `FieldRenderer`。
   - 输出固定尺寸 PNG，便于报告、回放和回归检查。

2. 给饮水事件做 inspect 动画
   - 当前已记录 `DRINK` 事件。
   - 后续可在 `SceneDirector` 中给饮水加蓝色标记或短暂停留动画。

3. 扩展口渴系统调参
   - 当前影响保守，适合作为默认关闭的实验功能。
   - 后续可以增加命令行参数或配置字段，控制扣水速度、临界阈值、干旱倍率。

4. 进一步增强状态弹窗
   - 可以增加事件日志分栏。
   - 可以增加趋势字段，例如最近是否饮水、是否长期处于低水分。
