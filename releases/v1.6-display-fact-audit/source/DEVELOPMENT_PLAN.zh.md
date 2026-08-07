# 后续开发计划与下一窗口接力文案

更新时间：2026-07-10

执行状态更新：2026-07-14。R0 已完成；A1 已锁定失败基线；A2 已完成旁路事实帧，
尚未切换现有 renderer。

当前正式版本仍为 **1.5**。本文是后续开发的唯一计划来源，不代表计划中的
1.6/1.7 功能已经实现或打包。

## 协作与模型执行约束（最高优先级）

当使用 **5.6 sol** 且推理强度 **大于等于“高”** 时，必须遵守以下规则：

1. 用户提出开发方向、问题、想法或要求“写计划/先想想/设计方案”时，默认只做
   代码审计并把**详细、具体、可直接执行的计划**写入本文；不得据此修改生产
   源码、替换现有实现、打包 release 或宣称已经完成。
2. 只有用户明确发出“执行”“开工”“按计划实现”“修改代码”等实施授权后，
   才能进入代码修改阶段。授权只覆盖被明确点名的计划批次，不能顺手实施后续
   批次。
3. 用户明确要求“回滚”时，可以执行精确回滚和回归验证；回滚结束后，如果用户
   同时要求重新考虑方案，则只更新计划，不继续实施新方案。
4. 每份计划必须至少写清：现状证据、确认根因、目标与非目标、涉及文件、数据
   结构/接口、逐步修改顺序、先写的失败测试、每步验收、性能边界、风险、回滚
   点和开发 jar 里程碑。不得只写“优化渲染”“统一数据”等不可执行口号。
5. 计划中的“建议新增文件/接口”都属于待实施设计，不得在状态清单中标成已
   完成。实施后必须以测试输出和 jar 路径为证据更新状态。

## 待用户审核：显示表层精确回滚计划 R0

**状态：根据用户审核意见修订完成，尚未获得执行授权。下面任何源码、测试或
打包步骤都不得在用户明确回复“批准执行 R0”之前开始。**

### 一、最新失败的审计结论

用户提供的截图只剩 terrain，动物和状态覆盖层同时消失。根据当前代码可确认：

1. `Simulator.simulate()` 在模拟线程调用 `SimulatorView.showStatus()`；Swing
   `paintComponent()` 则在 EDT 绘制线程执行。当前没有 `invokeLater`、不可变帧
   发布或锁来约束这两个线程。
2. `showStatus()` 调用 `FieldView.preparePaint()`；后者在共享的
   `animalImage/animalGraphics` 上先执行 `clearAnimalLayer()`，再扫描整个 Field
   并逐只画动物，最后才画天气 tint 和诊断面板。
3. `paintComponent()` 同时直接读取这张仍在被修改的 `animalImage`。因此 EDT
   可以恰好在“已经 clear、动物和状态面板尚未画完”时把透明动物层合成到 terrain
   上，得到截图中的纯地形画面。
4. 上一轮未经审核把 `VISUAL_REFRESH_INTERVAL` 从 100 改为 1，使上述危险窗口
   每个模拟步都发生；3x 地图每次还要扫描 `360x240=86,400` 个格子，进一步提高
   EDT 读到空层和界面饥饿的概率。这不是合格的动画方案。
5. 上一轮又让模拟线程每 10 步调用 `rebuildStoryboard()`；该方法会修改 Swing
   `Timer`、`storyboard`、`ambientActors` 和计时字段，同样跨线程，而且会频繁
   重置 inspect 动画起点。
6. `195/195` 测试只验证了常量等于 1/10，没有验证 animal layer 像素、EDT 线程
   归属或连续帧可见性，因此“测试通过”不能证明 GUI 正常。
7. `build.sh` 直接执行 `javac *.java` 且不清理旧 `.class`；虽然
   `RenderFrame.java` 已删除，目录和所谓 rollback jar 中仍存在
   `RenderFrame.class` / `RenderFrame$Entry.class`。上一轮 jar 不是干净构建，必须
   标记为无效：
   `savanna-simulation-v1.6-display-surface-rollback-dev.jar`。

### 二、用户审核纠正与本轮严格范围

原设计进入放大/inspect 视图后会自动暂停模拟，退出 inspect 后恢复进入前的暂停
状态。这是预期交互，不是本轮缺陷，必须保留。

本轮只处理一个问题：普通/不放大视图恢复到上一轮修改前的显示表层，动物必须
正常出现并按原有批次更新，不能出现纯地形空帧。

本轮明确不做：

- 不重新引入 `RenderFrame`、`VisibleAnimalSet` 或共享 painter。
- 不修改动物物种、颜色、图标、生态位置、口渴、生存、行为和随机数逻辑。
- 不做 `+N` 聚团、历史事件分层、LOD 或标签避让。
- 不取消 inspect 自动暂停，不要求放大期间 step 推进，也不刷新后续模拟状态。
- 不修改 release，不覆盖 v1.5 正式产物。

### 三、R0：先精确回到上一轮改动前的显示表层

目的：恢复一个已知能显示动物的原交互基线，并停止；本轮不再包含 R1 或任何
新的动态渲染设计。

文件级修改：

1. `SimulationSettings.java`
   - `VISUAL_REFRESH_INTERVAL` 从 1 恢复为 100。
   - 删除 `INSPECT_REFRESH_INTERVAL=10`。
2. `SimulatorView.java`
   - 删除 `showStatus()` 末尾“每 10 步 rebuild storyboard”的分支，恢复一次
     `fieldView.repaint()`。
   - 恢复 `pauseBeforeInspect` 和原来的 inspect 进入/退出暂停逻辑；进入放大模式
     自动暂停，退出时仅在进入前未暂停的情况下恢复模拟。
3. `SimulationUnitTests.java`
   - 恢复 refresh interval 100 的旧断言。
   - 删除 inspect interval 10 的断言。
4. 干净构建
   - 不再从源码目录的 `*.class` 直接打 jar。
   - 新建空的 `build/classes`，只把当前 `.java` 编译进去，再从该目录打包。
   - jar 清单必须确认不存在无源码的 `RenderFrame.class`。

R0 验收：

- `./test.sh` 全通过；jar 内 `AllTests` 全通过。
- 启动后普通视图连续截取 20 帧，动物层非透明像素每帧都大于 0；不能出现只有
  terrain 的帧。
- 人工截图必须同时包含动物和状态覆盖层。
- 进入 inspect 后 step 在观察期内保持不变；退出 inspect 后，如果进入前正在
  运行，step 必须恢复增长；如果进入前已经手动暂停，退出后仍保持暂停。
- 普通/inspect 往返 10 次，普通动物层每次都能恢复，不能永久消失。
- R0 产物命名为 `savanna-simulation-v1.6-display-exact-rollback-r0.jar`，只放工作
  目录。用户看过并确认后，本计划结束，不自动进入任何后续批次。

### 四、R0 必须增加或恢复的验证

1. 恢复 `SimulationSettings` 的 refresh interval 100 断言。
2. 增加干净 jar 检查：`RenderFrame.class`、`RenderFrame$Entry.class` 必须不存在。
3. 增加普通层固定 Field 像素探针：五个物种存在时，动物层非透明像素必须大于
   0，且不能只有 terrain。
4. 增加 inspect 暂停状态测试：运行 -> 进入 inspect -> step 不变 -> 退出 ->
   step 恢复；手动暂停 -> inspect 往返 -> 仍暂停。

### 五、审批口令

- 用户回复 **“批准执行 R0”**：只做上述精确回滚、验证和 R0 jar，完成后停止。
- 除此以外保持 plan-only；以后如果要改变 inspect 暂停规则，必须另写计划并
  重新审核，不能混入本轮。

## v1.6 最高优先级纠偏计划（2026-07-10）

### 2026-07-14 当前实施状态

- R0 已完成：恢复 `VISUAL_REFRESH_INTERVAL=100`、inspect 自动暂停/退出恢复，
  并生成工作目录回滚 jar。
- A1 已完成基线探针：`RenderConsistencyProbe` 已覆盖当前动物事实、历史 actor、
  缩放序列和五种 viewport 尺寸；当前旧 inspect 路径会被探针复现为“死亡历史
  actor 进入当前场景”，该失败必须在 A4 前修复。
- A2 已完成旁路事实帧：新增 `RenderAnimalSnapshot`、`RenderFrame`、
  `RenderFramePublisher`；模拟每步发布不可变帧，但现有 `SimulatorView` 和旧
  renderer 未接线切换。
- A2 开发 jar：`work/savanna-simulation/build/savanna-simulation-v1.6-render-frame-sidecar-a2-dev.jar`。
- 当前不允许直接进入 A3；先由下一窗口复核 A2 帧的不可变性、随机流不变性和
  发布时机，再决定是否接入 overview。

用户已将下面两项确定为当前最高优先级。在这两项完成前，暂停继续扩展普通
功能和 v1.7 行为管线：

1. 缩放前后必须显示同一份当前动物事实，不能切换后出现不同物种、历史幽灵
   动物或错误视口。
2. 口渴改为三档状态；进入第二档后，饮水/寻水就是头号行为，第三档进入紧急
   保命模式。饮水事件和热力图必须能够真实反映全过程。

### 优先问题 A：保留两套显示系统，修复移动、缩放一致性和拥挤

#### 2026-07-10 回滚状态与设计纠正

- 已精确撤销上一轮尚未被用户认可的 `RenderFrame` 接线、inspect `+N` 屏幕
  聚团和斑马改色；没有回滚已经验证的三档口渴、第二档头号饮水、`DRINK`
  表达和热力图增强。
- 上一轮未经用户审核，把 `VISUAL_REFRESH_INTERVAL` 从 100 改成 1、删除 inspect
  自动暂停并增加每 10 步重建场景。截图已证明该方案会暴露共享
  `animalImage` 的跨线程清空/绘制竞争，不能视为完成。
- 当时的 `195/195` 仅为逻辑测试，未覆盖 GUI 空帧；开发 jar
  `savanna-simulation-v1.6-display-surface-rollback-dev.jar` 已标记无效。后续只按
  上方待审 R0 计划推进。
- **必须保留两套显示系统**：
  1. `OverviewRenderer`（普通/不放大视图）用轻量 marker 表达全局种群，并且在
     模拟运行时持续移动；
  2. `InspectRenderer`（放大视图）用更丰富的动物造型、动作和可点击信息表达
     局部细节。
- “共享同一个当前事实帧”只表示两套 renderer 在同一时刻读取相同的动物 ID、
  species、location 和状态，**绝不表示合并两套绘制器、关闭放大图层、让普通
  图层顶替放大图层，或把所有动物画成同一种符号**。
- 禁止恢复上一轮的固定 `32px` 方格聚团和 `+N` 替代物。拥挤必须通过分级细节、
  图标/文字避让和绘制预算解决，不能把一群真实动物压成会造成误解的团块。

#### 已确认根因（以当前回滚代码为准）

1. 普通系统把动物画进缓存 `animalImage`，而紧急回滚前 `Simulator` 只在
   `VISUAL_REFRESH_INTERVAL=100` 时调用 `showStatus()` 重建该图；Swing 重绘只是
   重画旧图片，所以普通视图会长时间静止。这是“不放大的显示系统不动”的直接
   原因，不是缩放变换本身的问题。
2. 进入 inspect 后 `paintComponent()` 隐藏 `animalImage`，改画
   `SceneDirector.buildScene()` / `buildBehaviorActors()` 生成的 actor。两套路径
   分别从 `Field`、`recentEvents` 和候选筛选中取数，没有一个带时间戳的共同
   当前事实边界。
3. inspect 把 `recentEvents` 中的历史捕猎、死亡、出生角色和当前存活动物混在
   同一层；历史动物可能已经死亡、移位或离开视口，因此缩放后看起来像“换了
   物种”。
4. `behaviorCandidates()` 会在当前视口动物不足时扩大候选范围，导致放大层引入
   屏幕外动物。它可以用于挑选历史故事，但不能用来定义“当前动物”。
5. 普通全图 marker 约 3px 时，蓝色 hydration 水滴/橙色危急圈可能覆盖物种
   主体色；这会造成斑马等物种视觉误判。状态色必须是外部附属标记，不能重涂
   物种本体。
6. inspect 对当前动物、行为文字和历史场景都按“尽量多画”处理，没有屏幕空间
   预算、碰撞检测和细节级别；密集区域因此出现大量重叠图标和文字。
7. resize 时背景、geometry、viewport、actor 和点击命中不是原子更新；inspect
   的自动暂停属于预期行为，但暂停后仍必须允许 resize 重建 geometry，不能长期
   保留旧尺寸。

#### 目标与非目标

目标：

- 普通视图在模拟运行时能肉眼看到连续移动，不再每 100 步突然跳一次。
- 普通和放大视图仍是两套独立视觉系统，但在一次缩放切换的同一帧内对当前
  动物的 ID/species/location 结论一致。
- inspect 只把当前视口内当前存活动物画在“当前动物层”；历史事件另设可关闭
  的回放层，默认关闭。
- 放大后保留局部细节和动作感，同时通过 LOD、避让和标签预算消除密恐式堆叠。
- 斑马使用浅灰白身体、深色条纹；蓝色只作为水分状态外部徽标，二者在小尺寸
  和放大尺寸都不混淆。

非目标：

- 不把普通视图和 inspect 视图改成同一个 painter。
- 不因共享事实帧而永久隐藏任一图层。
- 不改变动物生态位置、移动决策、物种或随机数流来迁就视觉布局。
- 不使用随机抽样、固定网格聚团或 `+N` 替代当前动物身份。
- 不在本问题中重写完整 v1.7 行为意图管线。

#### 待新增的数据契约与文件（均未实施）

- `RenderAnimalSnapshot.java`
  - 字段：`animalId`、`species`、`row`、`col`、`alive`、`hydrationStage`、
    `survivalStage`、`lastBehavior`；全部不可变。
  - 不持有可变 `Animal` 引用，不触发任何随机调用。
- `RenderFrame.java`
  - 字段：`step`、`publishedAtNanos`、`terrainRevision`、`panelSize`、当前全部存活
    动物快照、从上帧到本帧的事件摘要。
  - 同一步只发布一个实例；缩放只改变 `ViewportTransform`，不能重建动物事实。
- `RenderFramePublisher.java`
  - 在模拟线程完成一步且 Field 状态一致后发布不可变帧；Swing 线程只读最新
    完整帧，不直接遍历正在变化的 `Field`。
- `RenderTransition.java`
  - 保存 previous/current frame 和按 `animalId` 匹配的起止位置；只做显示插值，
    不反写 Field。
- `OverviewRenderer.java`
  - 只负责轻量 marker、外部状态徽标和全局移动插值。
- `InspectRenderer.java`
  - 只负责当前动物的详细图标、动作姿态、选择与命中。
- `InspectLayoutPolicy.java`
  - 负责 LOD、确定性屏幕避让、标签预算和优先级；输入相同必须输出相同布局。
- `HistoricalEventOverlay.java`
  - 只消费历史事件摘要，默认关闭；启用时使用半透明/虚线和“回放”标识，
    manifest 中不得将其计为当前动物。
- `RenderManifest.java`
  - 分别记录 `currentAnimalFacts`、overview 表达、inspect 表达和 historical overlay；
    测试按 ID/species 对账，不能只比较绘制数量。
- `RenderConsistencyProbe.java`
  - 命令行复现固定 seed、尺寸和缩放序列，输出丢失、额外、物种不符、位置越界、
    重叠率和最大静止时长。

主要修改文件：`Simulator.java`、`SimulatorView.java`、`SimulationSettings.java`、
`ViewportTransform.java`、`VisualGridGeometry.java`、`FieldRenderer.java`、
`SceneDirector.java`、`AnimalIconSet.java`、`SpeciesRegistry.java`、
`SimulationSystemTests.java`、`WaterSafetyProbe.java`、`package.bluej`。

#### 两套显示系统的精确职责

`OverviewRenderer`：

1. 读 `RenderTransition`，按 animal ID 对 previous/current 位置插值。
2. 用 Swing `Timer(33ms)` 只推进显示时间和 `repaint()`；不得在 EDT 上推进模拟。
3. 模拟正在运行且存在 MOVE 时，连续刷新目标为 30 FPS；在低性能设备允许降到
   15 FPS，但不能回退为 100 步静态缓存。
4. 3px marker 的中心/主体永远使用物种色和物种形状；hydration、疾病、危急
   状态只能使用不超过主体面积 25% 的边角点或外圈。
5. 全局视图允许为了性能跳过非关键标签，但不允许把某物种改画成另一物种。

`InspectRenderer`：

1. 当前动物输入严格来自同一 `RenderFrame` 与 `visibleCells()` 的交集；不允许
   调用“至少补 20 只”的候选逻辑。
2. 保留独立的动物造型、动作和命中区域；不能拿 overview 的 3px marker 放大
   冒充详细图标。
3. 细节级别采用连续 LOD，而不是上一轮粗暴聚团：
   - `2.5 <= zoom < 4.0`：所有当前动物画紧凑物种剪影；只给选中动物、当前主
     行为动物和危急动物画文字；
   - `4.0 <= zoom < 6.0`：选中/行为/危急动物画完整图标，其余画无文字的简化
     图标；
   - `zoom >= 6.0`：在碰撞预算允许时画完整图标；不允许时仍按优先级简化背景
     动物，但数据层不得丢失或改种。
4. 使用确定性屏幕空间避让：先放选中动物，再放危急状态、饮水/捕猎 actor、
   捕食者，最后放普通背景动物；显示锚点可以轻微偏移，但真实格点位置要保留
   连线/阴影锚点，避免视觉位置冒充生态位置。
5. 行为标签最多 12 个且每个 `96x64px` 屏幕块最多 1 个；超预算时隐藏低优先级
   标签，不用 `+N` 圆团替代动物。

`HistoricalEventOverlay`：

1. 默认关闭；开关必须独立于缩放级别。
2. 只显示事件回放，不参与当前动物点击、当前种群计数或当前 manifest。
3. 已死亡/已离开视口的角色必须使用回放样式，并显示事件 step；不能看起来像
   当前活体。

#### 分阶段实施顺序（收到“执行”后才能开始）

**A0：锁定回滚基线和开发 jar**

- 保存 `194/194` 测试结果，打包仅包含回滚状态的 jar。
- 记录现有五物种颜色、普通视图刷新间隔和 inspect 截图，作为前后对照。
- 回滚点：当前源码副本 + 基线 jar；后续每批只允许增量修改。

**A1：先写失败测试与一致性探针，不改绘制结果**

- 测试同一步固定 viewport 在 `1.0 -> 2.49 -> 2.5 -> 4.0 -> 6.0 -> 1.0`
  中的 current ID/species/location manifest。
- 测试死亡、移位和视口外 event actor 不进入 current layer。
- 测试有 MOVE 事件时普通画面在 500ms 内至少出现两个不同插值位置。
- 测试五种窗口尺寸的 viewport、screen/grid 双向映射和点击命中。
- 测试密集夹具的图标重叠率、标签预算、斑马/水分色区分。

**A2：只建立不可变事实帧，不切换任何 renderer**

- 在模拟步结束后构造 `RenderFrame`，保持旧 `showStatus()` 继续工作。
- 加 `RenderManifest` 对旧普通/inspect 输入做旁路审计；不改变用户看到的图。
- 验证发布帧不消费随机数、不改变 `thirst=off` 生态基线。
- A2 失败时只删除新增旁路类，不触碰现有显示系统。

**A3：先让普通/不放大系统恢复持续移动**

- 把模拟状态发布频率和报告/统计频率拆开：报告仍可每 100 步，render frame
  建议每 5-10 步发布一次，并由 33ms Swing timer 在两帧间插值。
- `OverviewRenderer` 取代 `animalImage` 的静态动物层；terrain 仍可缓存。
- 运行/暂停/停止分别验证：运行时移动、暂停时定格、恢复后无瞬移回旧帧。
- A3 单独打一个开发 jar，先由用户确认普通视图确实会动，再进入放大层改造。

**A4：把 inspect 接到同一事实帧，但保留独立 renderer**

- 新增 `InspectRenderer`，当前动物只读 `RenderFrame`；`SceneDirector` 暂时只保留
  历史场景数据，不再定义 current animal set。
- 缩放切换时锁定同一个 frame sequence；切换完成后再接收更新帧，避免边界帧
  一边旧一边新。
- inspect 保留原有自动暂停：进入时暂停，退出时恢复进入前状态；暂停期间必须
  使用进入 inspect 时的完整当前状态，不能读到半帧。

**A5：实施 inspect LOD、避让和颜色修正**

- 先用密集测试夹具实现 `InspectLayoutPolicy`，再接真实场景。
- 去除当前动物层的行为文字泛滥；按上述优先级和 12 标签预算显示。
- 斑马改为浅灰白本体（建议 `#E8E6DE`）+ 深灰黑条纹（建议 `#222222`）；
  hydration 蓝（建议 `#2F9BEA`）只画外部徽标，不修改本体填充。
- 人工对比 1x、2.5x、4x、6x 截图，确认既不换种也不形成密集圆团。

**A6：最后拆分历史回放层**

- 建立独立开关，默认关闭；开启时加入 replay 样式和事件 step。
- 当前层与历史层分别输出 manifest；所有 current 点击命中忽略历史 actor。
- 不允许以“历史场景更热闹”为理由默认混入当前动物。

**A7：全矩阵验收和开发 jar**

- 依次运行 `./build.sh`、`./test.sh`、`java AllTests full`、
  `RenderConsistencyProbe`、`WaterSafetyProbe` 和 `ThirstBehaviorProbe`。
- 每个可视里程碑只在工作目录生成开发 jar；未通过用户人工视觉确认前不写入
  `releases/`，不升级正式 1.5 版本号。

#### 可量化验收标准

- 同一 frame/viewport 的缩放序列中，current animal 的 ID、species、location
  manifest 差异为 0；允许视觉细节不同，不允许事实不同。
- 模拟运行且该窗口存在 MOVE 事件时，普通画面不得连续静止超过 500ms；30 FPS
  采样期间不得出现倒退到上一位置的跳帧。
- inspect 当前层不得出现已死亡、已移位到视口外或仅存在于历史事件的 ID。
- 五种窗口尺寸 `3600x2400`、`1732x948`、`1387x829`、`932x712`、
  `640x480` 下，resize 前后中心锚点网格误差不超过 1 格，点击命中读取同帧。
- 密集夹具中选中/危急/主行为动物 100% 可见；完整图标平均重叠面积不超过较小
  图标面积的 15%；行为标签总数不超过 12，且不出现上一版 `+N` 圆团。
- 五个物种在 3px overview marker 与 inspect 图标中均能区分；斑马主体不得使用
  hydration 蓝，水分徽标不得覆盖主体面积 25% 以上。
- `thirst=off` 的随机流和 5000 步种群基线不变；水面安全像素测试继续通过。
- 每个批次都能独立回退到前一批 jar，不能靠整目录覆盖回滚。

### 优先问题 B：三档口渴、头号饮水行为与可观测性

#### 当前实测基线

当前代码开启口渴运行 800 步，全程真实 `DRINK` 为 642 次，但严重失衡：

```text
Lion=207, Cheetah=46, Buffalo=240, Zebra=147, Gazelle=2
饮水岸格 128/194，最热岸格 39 次
第 800 步仍口渴：Gazelle=108, Zebra=21, Cheetah=9, Buffalo=7, Lion=28
口渴动物距离岸边：0-20=26, 21-60=15, 61-120=82, 121+=50
```

固定 35% 才开始寻水是主要模型错误。默认晴天剩余 35 水分理论上只够约 146
步，干旱只够约 88 步，而 3x 地图最大岸边距离为 212。

#### 三档口渴模型

新增建议：

- `HydrationStage.java`：`NORMAL`、`THIRSTY`、`CRITICAL`。
- `HydrationAssessment.java`：当前水分、距离、天气有效消耗、预计到岸水分、
  stage、pressure 和原因。
- `ThirstConfig.java`：默认阈值、安全余量、天气倍率、脱水宽限。
- `WaterNeedProfile.java`：分物种 drain、seek threshold、critical threshold、
  safety margin。
- `WaterAccessMap.java`：统一岸边距离、下一步候选和可达性查询。

推荐初始档位参考现有生存值的三段语义，最终由多种子校准确认：

```text
NORMAL   ：hydration > max(70, requiredReserve)
THIRSTY  ：30 < hydration <= max(70, requiredReserve)
CRITICAL ：hydration <= 30，或预计到岸水分 <= criticalReserve

effectiveDrain  = baseDrain * weatherMultiplier * speciesMultiplier
requiredReserve = distanceToShore * effectiveDrain + safetyMargin
```

距离储备可以提前把远水区动物提升到 `THIRSTY`，不能再等固定 35% 才出发。

#### 行为优先级硬规则

`NORMAL`：

- 保持现有捕猎、觅食、繁殖、休息和普通移动。
- 如果已经在岸格且低于补水目标，可顺便饮水，但不强制中断主行为。

`THIRSTY`（第二档）：

- `DRINK` / `SEEK_WATER` 成为头号主行为。
- 先尝试饮水；不能饮水时必须选择严格缩短岸边距离的可行步。
- 禁止繁殖，跳过捕猎、觅食、休息和普通随机移动。
- 若最短方向拥堵，使用 `WaterAccessMap` 在可控半径内寻找次优但仍缩短距离的
  路径；没有可行步时记录 `WATER_BLOCKED`，不得静默向远处移动。

`CRITICAL`（第三档）：

- 饮水/寻水保持绝对最高优先级，并进入紧急位置预约。
- 对水向目的格使用显式 `tryReserve`，繁殖和普通移动不得抢占。
- 允许更大范围 reroute；持续 blocked 必须进入诊断、事件和死亡原因统计。
- 不得通过增强 food pressure 间接增加捕猎或觅食。

v1.6 可以用隔离的 `HydrationAssessment + ThirstDecision` 早分支实现上述优先级，
但不要提前混入完整 v1.7 `BehaviorIntent` 管线。

#### 饮水事件、探针和热力图

1. 修复 `ThirstBehaviorProbe`：当前实现只统计刷新点当步 `currentEvents`，并非
   区间。改用 listener 累积每个区间和全程 `DRINK`，输出分物种饮水率、
   toward/same/away/blocked、距离档位和首饮 step。
2. 新增 `SEEK_WATER` / `WATER_BLOCKED` 事件或等价的带 reason MOVE 事件，区分
   “去水边的轨迹”和“已经喝水的岸格”。
3. `RenderEventBuffer` 保存自上次画面刷新以来的饮水总数和代表样本；只要区间
   发生过饮水，下一帧必须显示。
4. `SceneDirector` 增加 `DRINK` 场景：岸格真实 actor、水滴/波纹、物种和计数；
   不得把岸边 actor 移到与真实位置无关的远处安全格。
5. 热力图拆分：
   - `Seek water / 寻水` 显示路线；
   - `Drink / 饮水` 显示岸格事件。
6. 饮水热力图不得只按最热格线性归一化。使用 `sqrt/log` 或分位数缩放，并给
   非零岸格最小可见强度和最小屏幕半径；全图 2-3 像素格也必须可见。
7. GUI 显示本区间/全程饮水计数，并与 `EventAccumulator`、probe 对账。

验收：

- `THIRSTY` 动物只要存在可达的缩短距离步，当步主行为必须是 `DRINK` 或
  `SEEK_WATER`；不得同时繁殖、捕猎、觅食或随机远离。
- `CRITICAL` 动物的水向预约不得被幼崽或后处理动物抢占。
- 从近、中、最远测试点出发，在晴天和干旱下均应在水分归零前到达岸格；如果
  物理上不可达，必须在初始 assessment 中明确报告配置不可行。
- 多种子 800/5000 步中五个物种均有持续饮水记录；按 100 animal-hours 标准化
  后不得再出现 Gazelle 近零、Buffalo/Lion 独占的数量级失衡。
- 区间 `DRINK` 总数、全程 listener、热力图和 probe 完全一致。
- 194 个饮水岸格的非零事件在全图模式有最低可见表达；热力图图例显示计数
  范围和缩放方式。
- `thirst=off` 的 5000 步种群、随机流和 v1.5 基线保持不变。

### 两项优先问题的实施顺序

1. **等待用户审批 R0 计划**：上一轮所谓显示表层回滚已经失败，当前不得再
   修改源码或打包。
2. **审批后只执行 R0 并等待人工确认**：恢复有动物且 inspect 自动暂停的精确
   基线；完成后停止，不得自动开始 A1-A7 的新渲染架构。
3. **三档口渴第一轮已经完成**：`HydrationStage` / `HydrationAssessment`、第二档
   水优先、`WATER_BLOCKED`、`DRINK` 场景和热力图增强保留。
4. **收到下一次明确“执行”后才继续**：严格按 A1 失败测试 -> A2 旁路事实帧 ->
   A3 overview -> A4 inspect -> A5 LOD -> A6 历史层 -> A7 验收分批实施；每批
   单独提供开发 jar，不发布正式 v1.6 release。

## 可直接交给下一个窗口的接力文案

````markdown
接力背景：当前项目位于
`/Users/tony/Documents/Codex/2026-06-26/fan/work/savanna-simulation`。
当前目录不是 git 仓库，不要依赖 git 回滚，也不要用
`/Users/tony/claude code/savanna-simulation` 整包覆盖主线。

请先完整阅读：

1. `DEVELOPMENT_PLAN.zh.md`：最新架构审计、已确认设计和分批执行计划。
2. `CLAUDE.md`：工程规则、命令、架构边界和当前发布状态。
3. `README.zh.md`：v1.5 已完成内容与验证记录。

当前正式版本和最新 release 仍是 1.5：

`/Users/tony/Documents/Codex/2026-06-26/fan/releases/v1.5-renderer-status-thirst/`

关键产物：

- `savanna-simulation-v1.5-renderer-status-thirst.jar`
- `savanna-simulation-v1.5-renderer-status-thirst-source.zip`
- `MANIFEST.txt`

当前工作树已完成：共享普通 marker renderer、增强状态弹窗、默认关闭的
口渴/岸边饮水系统、初始化页面口渴开关、context per-run RNG、多尺寸
WaterSafetyProbe、事件监听/热力图/快照基础和 release 三件套。

最近验证：

- `./test.sh` -> `180/180` passed（2026-07-10 再次复核）
- `java SimulationRunner 5000` -> 默认 `thirst=off` 基线最终
  `Pop=2643`，balance ratio `9.60`
- v1.5 jar 内 `AllTests`、`WaterSafetyProbe 1000 100`、
  `WaterSafetyProbe 18500 500` 已通过

本轮代码重读和临时诊断确认：口渴逻辑确实运行，主要问题是行为不可见，
不是动物没有去水边。默认 3x 地图、开启口渴运行 800 步时：

- `642` 次 `DRINK`，第一次在 step `175`
- 口渴移动样本约 `95.7%` 在缩短岸边距离
- 第 800 步有 `32` 只动物实际位于饮水岸格，但全部被普通视图隐藏
- 194 个饮水岸格在 `3600x2400`、`1387x829`、`1093x731` 下
  `194/194` 都会触发 marker 隐藏；`640x480` 为 `192/194`
- 642 次饮水只有 7 次恰好发生在百步刷新点
- 分物种累计饮水严重失衡：Lion 207、Cheetah 46、Buffalo 240、
  Zebra 147、Gazelle 2

根因：

1. `SimulatorView.canDrawOrdinaryAnimal()` 会隐藏 footprint 碰到视觉水面的
   岸边动物，现有 `WaterSafetyProbe` 还把“隐藏 marker”当成通过路径。
2. `VISUAL_REFRESH_INTERVAL=100`，饮水停留通常只有一个模拟步。
3. `SceneDirector` 没有 `DRINK` 场景，inspect 水面安全逻辑还会把岸边 actor
   移到远离水面的安全格。
4. 固定水分阈值 35 没有考虑 3x 地图最大岸边距离 212、天气和物种差异。
5. `SavannahAnimal.act()` 中繁殖先于寻水移动，可能占用寻水目的格。

用户已确认的设计方向：

- 口渴应接入总体生存值，但不能直接把 hydration 塞进现有食物生存压力。
- 新增 food、hydration、overall 三套值；口渴关闭时 hydration survival
  必须视为 100。
- `overallSurvival = min(foodSurvival, hydrationSurvival)` 用于 UI、诊断和
  总体风险。
- 觅食/捕猎继续使用 food pressure，寻水使用 hydration pressure，避免
  口渴错误增强捕猎或觅食。
- 当前框架继续保留 `Simulator`、`SimulationContext`、系统接口、`Field`、
  事件监听、热力图和快照；通过增量引入 `SurvivalAssessment`、
  `BehaviorIntent`、统一动作执行和位置冲突处理，不做整包重写。

实施顺序：

1. 先建立永久 `ThirstBehaviorProbe` 和失败测试，保留当前实测基线。
2. 修复岸边 marker：动物必须可见，同时动物像素仍不能落到视觉水面。
3. 给 `DRINK` 增加 inspect/普通视图表达，并保存上次刷新以来的事件。
4. 引入 `SurvivalAssessment`，先迁移 UI/诊断，再谨慎接入繁殖和风险规则。
5. 引入 `ThirstConfig`、分物种需水参数和距离感知寻水阈值，完成多种子校准。
6. v1.7 再引入 `BehaviorIntent` / `BehaviorResolver` / `ActionExecutor` /
   `FieldTransition`，不要把高风险架构改造与 v1.6 可见性修复一次性混装。

建议版本边界：

- `v1.6-thirst-survival-observable`：口渴可见、事件可观测、综合生存值、
  诊断和模型校准。
- `v1.7-behavior-pipeline`：行为意图、统一决策、动作执行、位置冲突和完整
  共享渲染框架。

关键硬约束：

- `thirst=off` 必须保持既有 5000 步生态结果和随机流。
- `WATERHOLE` 继续不可通行；饮水只能发生在 passable 岸格。
- 水面测试的新语义必须是“岸边 marker 有可见陆地像素，同时水面动物像素
  为 0”，不能再通过整只隐藏动物来达成。
- 新功能默认关闭，不得在关闭路径消费额外生态随机数。
- 不要删除现有事件监听、`EventAccumulator`、热力图、快照、共享视觉几何。
- 修改代码后同步 `README.md`、`README.zh.md`、`CLAUDE.md`、
  `CHANGELOG.md`、`UPDATE_NOTES.md`、`README.TXT` 和 `package.bluej`。
- 当前版本在真正实现、验证和重新打包之前仍写 1.5，不能提前改成 1.6。

开始实现前先复跑：

```bash
cd /Users/tony/Documents/Codex/2026-06-26/fan/work/savanna-simulation
./test.sh
java WaterSafetyProbe 1000 100
java SimulationRunner 5000
```

完成 v1.6 后按顺序验证：工作树测试、永久 thirst probe、多尺寸像素审计、
默认关闭 5000 步基线、开启口渴多种子稳定性、`AllTests full`，最后打包并用
jar 本身复核。目标 tag 建议为 `v1.6-thirst-survival-observable`。
````

## 已确认事实与设计决定

### 当前事实

- `SimulationConfig.isThirstEnabled()` 默认 `false`。
- 开启后 `SavannahThirstSystem` 每步扣水，并从 passable 岸格饮水。
- `WATERHOLE` 不可通行。
- 普通视图每 100 个模拟步刷新一次。
- 普通岸边 marker 当前会因为视觉 footprint 碰水而被整体跳过。
- `SceneDirector` 当前没有 `DRINK` 场景。
- 当前测试只证明直接扣水、岸边饮水和单步能选择一个水方向，没有验证完整
  寻水轨迹、物种公平性和岸边可见性。

### 生存值决定

禁止直接把 hydration 并入现有无 context 的 `getSurvivalPercent()` 后让所有
旧行为自动继承，因为旧方法目前实际代表 food survival，并参与捕猎、觅食、
繁殖、体力和渲染。

目标评估模型：

```text
foodSurvival      = 当前食物百分比
hydrationSurvival = thirst 开启 ? 当前水分百分比 : 100
overallSurvival   = min(foodSurvival, hydrationSurvival)
foodPressure      = 只驱动捕猎/觅食等食物行为
hydrationPressure = 只驱动寻水/饮水行为
overallPressure   = UI、诊断、繁殖限制和总体风险
```

严重状态仍保留资源特定阈值，不应仅以一个综合百分比覆盖所有规则。例如食物
危急可以继续使用 30%，严重脱水应由 `ThirstConfig` 的独立阈值决定。

### 架构边界

保留：

- `Simulator` 主循环与双 Field 步进模型。
- `SimulationContext` 作为 per-run 服务和状态容器。
- Weather/Disease/Food/Predation/Breeding/Thirst 系统接口。
- `SimulationEventListener`、`EventAccumulator`、热力图和不可变快照。
- `VisualGridGeometry`、`VisualFootprint`、`VisualWaterMask`。
- BlueJ/default-package/手写 `javac` 工程形态。

增量增加：

```text
Simulator
  -> physiology update
  -> SurvivalAssessment
  -> BehaviorIntent providers
  -> BehaviorResolver
  -> ActionExecutor
  -> FieldTransition
  -> SimulationEvent
  -> diagnostics / recorder / renderer / replay
```

## 当前架构问题

1. `SavannahAnimal.act()` 同时负责生理更新、繁殖、行为选择、移动和动作执行，
   新需求会继续扩大条件链。
2. `SavannahThirstSystem` 同时负责扣水、严重脱水效果、距离场、寻水、饮水和
   事件记录，职责过多。
3. `SimulationContext` 硬编码所有具体系统，关闭口渴以 `null` 表示。
4. `SimulationConfig` 是平铺参数和兼容构造器，继续添加实验参数会失控。
5. `Field.placeAnimal()` 能静默替换已有动物，没有预约/冲突结果。
6. `TerrainNavigator.nearestFreePassable()` 的候选按网格扫描返回，不严格保证
   最近，拥挤时可能产生方向偏差。
7. `DRINK` 虽已加入事件枚举、状态翻译和热力图，但没有完整场景、报告和
   区间事件表达。
8. `recentEvents` 最大 600 条且头删 `ArrayList`，高种群时可能连一个完整模拟步
   都保存不下。
9. `FieldRenderer` 只共享普通 marker，安全锚点、terrain、inspect 和 overlay
   仍在视图中分散。
10. 所有生态系统共享一条 `Random`，未来功能新增随机调用会扰动其他系统。
11. 当前没有统一 `DeathCause`，无法区分衰老、饥饿、脱水、疾病和捕食死亡。

## 分批执行计划

### 批次 0：锁定基线与永久口渴探针

目标：先把本轮临时诊断变成可重复的工程能力，避免调参凭截图判断。

新增建议：

- `ThirstBehaviorProbe.java`
- 必要时增加只读的 `ThirstMetrics` / `WaterAccessMap` 查询接口

探针至少输出：

- 地图 passable、水格、饮水岸格、不可达格和最大岸边距离。
- 总体及分物种平均水分、口渴数、岸边数、饮水数。
- 口渴移动 toward/same/away/blocked/drank/dead 统计。
- 每个画布尺寸的岸边 marker 可见数和水面像素违规数。
- 每个刷新区间的 `DRINK` 数，而不仅是刷新点当步事件数。

验收：

- 临时探针中已经观察到的核心结论能被永久 probe 稳定复现。
- probe 不消费生态随机数，不改变模拟结果。
- 加入 `package.bluej`，source zip 自动包含该源文件。

### 批次 1：修复岸边动物不可见

目标：保持水面安全，但不再以隐藏整只动物作为安全策略。

涉及文件：

- `VisualFootprint.java`
- `VisualWaterMask.java`
- `FieldRenderer.java`
- `SimulatorView.java`
- `SceneDirector.java`
- `WaterSafetyProbe.java`
- `SimulationSystemTests.java`

建议实现：

- 新增 `SafeMarkerPlacement`，根据邻接水方向将岸边 marker 缩小并向陆地侧
  偏移。
- 普通模式中 `canDrawOrdinaryAnimal()` 不再直接整只跳过；使用安全 placement
  后绘制。
- `shouldDrawAnimal()` 对口渴/刚饮水动物强制可见，不参与高密度抽样。
- inspect actor 使用陆地侧显示锚点，同时保留指向真实岸格的水滴/波纹表达，
  不把真实行为伪装成远离水面的静态动物。
- `WaterSafetyProbe` 从“碰水就隐藏”改为同时验证可见性和零水面动物像素。

验收：

- 默认地图全部 194 个饮水岸格在 `3600x2400`、`1387x829`、`1093x731`、
  `640x480` 下都有至少一个可见陆地 marker 像素。
- 所有尺寸的动物水面像素仍为 0。
- 真实 Field 中动物仍不进入 `WATERHOLE`。

### 批次 2：接入综合生存评估

目标：把水分纳入总体生存表现，但不破坏食物和口渴各自的行为语义。

新增建议：

- `SurvivalAssessment.java`
- `SurvivalSystem.java`
- `SavannahSurvivalSystem.java`

兼容迁移：

- 新增 `SavannahAnimal.getFoodSurvivalPercent()`。
- 暂时保留 `getSurvivalPercent()` 作为 food-only 兼容入口，避免一次性改变旧
  生态行为。
- `SimulationContext` 持有 `SurvivalSystem`。
- `AnimalStatusLog`、`SimulationDiagnostics`、`StepReportRecorder`、
  `SimulationChartWindow` 和 renderer 生存圈改读同一个 `SurvivalAssessment`。
- UI 同时显示 food、hydration、overall，不能只显示一个无法解释的总分。
- 口渴关闭时 hydration survival 固定为 100。
- 第二步再显式修改繁殖资格：口渴开启时低水分动物不能繁殖；不要通过修改
  food pressure 间接实现。

验收：

- `thirst=off` 时总体生存值等于旧 food survival。
- `thirst=on` 时总体值等于食物和水分的较小值。
- 觅食/捕猎结果仍由 food pressure 驱动，寻水由 hydration pressure 驱动。
- 默认关闭 5000 步最终种群与 v1.5 基线一致。

### 批次 3：修复事件窗口和视觉刷新语义

目标：即使模拟仍按批次刷新，也能看到刷新区间内真实发生的饮水行为。

新增建议：

- `RenderEventBuffer.java`
- `RenderFrame.java`
- 后续再引入 `RuntimeConfig.java`

具体工作：

- 使用 listener 累积“自上次 render 以来”的事件，按类型保留总数和代表样本。
- `SceneDirector` 增加 `DRINK` 优先级和饮水场景。
- `SimulatorView` 显示区间饮水数，饮水热力图继续使用全程 `EventAccumulator`。
- 把 `stepDelay` 明确为 frame delay；后续把 steps-per-frame 与 report interval
  分开配置。
- `showStatus()` 优先遍历 `field.getAnimals()`，避免每次扫描完整 3x 网格，为
  更高刷新频率腾出性能空间。
- 把 `recentEvents` 改为真正的有界队列或 ring buffer，避免 `remove(0)`。

验收：

- 某个百步区间只要发生过饮水，下一帧一定能显示计数或代表动画。
- 区间计数、长期热力图和 listener 总数一致。
- 普通运行性能不显著退化，暂停/停止按钮仍可响应。

### 批次 4：口渴模型配置化和校准

目标：解决固定阈值不考虑距离、地图尺寸、天气和物种差异的问题。

新增建议：

- `ThirstConfig.java`
- `WaterNeedProfile.java`
- 可选 `WaterAccessMap.java`

配置项：

- enabled
- base drain / species multiplier
- seek threshold
- critical threshold
- rain/drought multiplier
- safety reserve
- dehydration grace steps

寻水条件应至少考虑：

```text
requiredReserve = distanceToShore * effectiveDrain + safetyMargin
needsWater = hydration <= max(speciesSeekThreshold, requiredReserve)
```

先用距离感知阈值和分物种参数校准；只有数据仍证明单水源不足时，才在后续
增加多水塘或季节性临时水源。

验收：

- 每个物种从近、中、最远陆地测试点出发，在晴天和干旱下均能在水分归零前
  抵达岸边。
- 暖机后每个物种在连续观察窗口内都有可解释的饮水率，不能再出现高种群
  物种几乎永不饮水。
- 开启口渴的多种子 5000 步运行保持全部物种存活，最终 balance ratio 不超过
  既有稳定性门槛 12。

### 批次 5：v1.7 行为意图与动作管线

目标：为迁徙、休息、恐惧、群体行为和季节性水源建立稳定扩展点。

新增建议：

- `BehaviorIntent.java`
- `ActionType.java`
- `BehaviorReason.java`
- `BehaviorProvider.java`
- `BehaviorResolver.java`
- `ActionExecutor.java`

迁移顺序：

1. 先让 thirst 只提出 `DRINK` / `SEEK_WATER` intent。
2. 再迁移 food、predation、rest/random movement。
3. 最后迁移 breeding，因为它会创建新动物并占用多个位置。

建议优先级：

```text
critical dehydration
critical food survival
seek water
hunt or graze
rest
breed
ordinary movement
```

每步只执行一个主动作；繁殖如果保留为次动作，也必须明确资格、成本和位置
预约，不能继续隐式抢占寻水目的格。

验收：

- 每次移动都有明确 `BehaviorReason`。
- 严重脱水动物不会因为繁殖、捕猎或普通觅食放弃可行的寻水步。
- 新增行为不需要继续扩大 `SavannahAnimal.act()` 条件链。

### 批次 6：统一位置冲突和死亡原因

新增建议：

- `FieldTransition.java`
- `MovementResolver.java`
- `DeathCause.java`

目标：

- 用 `tryReserve` / `tryMove` / `trySpawn` 替代静默覆盖。
- 明确 stay、blocked、rerouted、conflict-lost 等结果。
- 修正 `nearestFreePassable()` 的距离排序和方向偏差。
- 统一记录 AGE、STARVATION、DEHYDRATION、DISEASE、PREDATION 等死亡原因。

验收：

- 每步结束后 Field map、animal list、动物 location 完全一致。
- 不发生无事件的动物静默消失或替换。
- 可以从事件/报告准确统计脱水相关死亡，而不是从饥饿计数猜测。

### 批次 7：完成同源事实、双渲染器和扩展约定

目标：让实时视图、固定 PNG、回放和测试共享同一份事实数据，同时保留普通和
放大两套独立视觉系统。

具体工作：

- `OverviewRenderer.render(RenderFrame, RenderOptions)` 负责普通视图的 terrain、
  轻量动物 marker 和全局移动表达。
- `InspectRenderer.render(RenderFrame, InspectLayout)` 负责放大视图的详细图标、
  动作、点击和 LOD；不能由 overview painter 代替。
- `HistoricalEventOverlay` 独立消费回放事件，不能混入任一 renderer 的当前动物
  manifest。
- Swing 视图只负责窗口、控制、viewport transform 和点击交互。
- `RenderSnapshotTool` 使用同一 renderer 输出固定尺寸 PNG。
- 建立事件元数据注册表，集中提供名称、中文、颜色、图标和场景构建器，减少
  `SimulatorView`、`SceneDirector`、`AnimalStatusLog` 的重复 switch。

以后每个新功能固定提供：

```text
XxxConfig（默认关闭）
XxxSystem 或 BehaviorProvider
BehaviorIntent / ActionType
SimulationEvent 与展示元数据
诊断指标和 renderer 表达
单元、流程、稳定性和打包测试
```

## 验证矩阵

| 层级 | 必跑验证 | 通过条件 |
|---|---|---|
| 编译 | `javac *.java` | 无错误 |
| 默认测试 | `./test.sh` | 现有 180 项及新增测试全部通过 |
| 完整测试 | `java AllTests full` | 5000 步集成测试通过 |
| 关闭口渴 | `java SimulationRunner 5000` | v1.5 生态基线不变，忽略耗时 |
| 开启口渴 | 永久 `ThirstBehaviorProbe` + 多种子 5000 步 | 五物种均有合理饮水率，无灭绝，ratio <= 12 |
| 逻辑水面 | `WaterSafetyProbe` | alive animal location 不在 WATERHOLE |
| 视觉水面 | 多 panel size 像素审计 | 岸边 marker 可见且动物水面像素为 0 |
| 事件 | 区间 buffer / accumulator 对账 | DRINK 和 MOVE 计数一致 |
| 发布 | `package.sh` + `verify-jar.sh` | jar、source zip、manifest 和 jar 内测试通过 |

## v1.6 发布步骤

实施和工作树验证完成后：

```bash
./package.sh v1.6-thirst-survival-observable
./verify-jar.sh v1.6-thirst-survival-observable
```

必须人工复核 source zip 内版本和计划文档：

```bash
unzip -p ../../releases/v1.6-thirst-survival-observable/\
savanna-simulation-v1.6-thirst-survival-observable-source.zip README.md | sed -n '1,35p'

unzip -l ../../releases/v1.6-thirst-survival-observable/\
savanna-simulation-v1.6-thirst-survival-observable-source.zip | \
rg 'DEVELOPMENT_PLAN.zh.md|README.zh.md|CLAUDE.md'
```

同步文档：

- `README.md`
- `README.zh.md`
- `CLAUDE.md`
- `CHANGELOG.md`
- `UPDATE_NOTES.md`
- `README.TXT`
- `package.bluej`

版本一致性要求：目录 tag、jar 名、source zip 名、manifest、README 当前版本和
CHANGELOG 顶部发布版本必须一致。历史 1.5 内容可以保留，但不能继续被描述为
当前版本。

## 当前状态清单

- [x] 重读口渴、生存、移动、事件和渲染链路。
- [x] 临时诊断确认口渴行为确实运行且岸边 marker 被隐藏。
- [x] 2026-07-10 复跑 `./test.sh`，结果 `180/180`。
- [x] 用户确认总体生存值应考虑水分，但 food/hydration pressure 必须分离。
- [x] 形成 v1.6/v1.7 分批计划和接力文案。
- [x] 彻查缩放前后物种不一致：确认双数据源、历史 actor、抽样、状态覆盖和
  resize geometry 五类根因。
- [x] 用户确认三档口渴；进入 `THIRSTY` 第二档后饮水/寻水为头号行为，
  `CRITICAL` 第三档进入紧急预约和 reroute。
- [x] 将缩放一致性和三档口渴提升为 v1.6 最高优先级并写入验收矩阵。
- [x] 第一轮实现三档 hydration、距离储备、第二档水优先、`WATER_BLOCKED`、
  `DRINK` 场景、饮水热力图增强和探针区间统计；工作树测试 `193/193` 通过。
- [x] 增加永久 `ThirstBehaviorProbe`。
- [x] 修复岸边 marker 可见性和 `DRINK` 场景。
- [x] 实现 `SurvivalAssessment` 并迁移 UI/诊断。
- [x] 撤销未获认可的 `RenderFrame` 接线、inspect `+N` 聚团和斑马改色。
- [x] 审计上一轮失败：逐步刷新直接读写共享 `animalImage` 造成纯 terrain 空帧，
  每 10 步重建 inspect 也跨线程；`195/195` 未覆盖 GUI，相关 jar 已标记无效。
- [x] R0 已按批准范围完成并生成开发 jar；inspect 保持原有自动暂停。
- [x] A1 已加入并运行一致性基线探针；已确认死亡历史 actor 会进入旧 inspect
  场景，属于待修复失败基线。
- [x] A2 已建立不可变事实帧旁路并生成开发 jar；尚未切换 renderer。
- [ ] A3-A7：未实施，必须逐批执行、逐批验证和逐批生成工作目录开发 jar。
- [x] 实现 `HydrationStage` / `HydrationAssessment` 和第二档头号饮水行为。
- [ ] 实现区间 render event buffer。
- [ ] 配置化并校准口渴模型。
- [ ] 完成 v1.6 全套验证、文档同步和 release。
- [ ] v1.7 再实施行为意图与位置冲突管线。
