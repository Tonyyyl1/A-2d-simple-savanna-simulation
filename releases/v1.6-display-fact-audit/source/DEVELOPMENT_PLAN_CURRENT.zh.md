# 当前开发计划：B3 事实一致性旁路审计已验收完成；等待发布授权

更新时间：2026-07-15

本文是当前唯一开发计划。DEVELOPMENT_PLAN.zh.md 只保留历史讨论与实施记录，
不再代表下一批次已经获准执行。B2 已完成；B3 已获实施授权并完成只读旁路、自动
验收、固定种子回归、原生 GUI 压力/性能验收和开发候选打包。B3 不得自动发布；
后续动作必须取得单独发布授权。

已按本计划实施 B2 的生产代码、确定性原子探针、字节码门、原生 GUI 压力验收和开发候选
产物。正式发布版本仍为 1.5；现有 v1.6 产物都是工作目录中的开发候选，不得写入
releases/。

## 一、优先级结论

用户截图中 terrain 正常存在，但普通动物、天气 tint 和右下状态面板同时消失。
这不是有意暂时移除，也不是“该区域恰好没有动物”，而是当前显示路径中尚未修复的
并发空帧缺陷。

该缺陷定为 **B2 最高优先级、阻断发布**：

- B2 完成前，暂停其他显示增强、事实旁路审计、口渴扩展和 v1.6 发布工作。
- 任何一次 terrain-only 普通帧都判 B2 失败，不能用“之后又恢复”降级处理。
- B2 只修普通表层的构建与提交边界，不借机替换两套 renderer 或改生态逻辑。

B2 状态：**开发完成；headless 自动门及原生 GUI 压力验收均已通过；未获发布授权，
仍是开发候选。** 2026-07-15 的原生窗口复测连续两次覆盖五种尺寸、99→100/199→200
边界、暂停、巡检、缩放和 Stop & Exit，未出现 terrain-only 或不完整前层。

原计划中名为 B2 的“可选事实一致性旁路审计”顺延为 B3，现列为下一轮唯一开发主体，
但尚未开始实施。

## 二、实际基线：纠正旧计划中的过时状态

### B0：显示契约回归门

状态：**已完成。**

- DisplayContractProbe：20/20。
- 全量 headless：219/219。
- 证据：build/B0_TEST_EVIDENCE.txt。
- 开发 jar：build/savanna-simulation-v1.6-display-contract-b0-dev.jar。
- jar SHA-256：
  d58e3b28dccad0a9d2896102d81505c1c79aded8b2689eda30d0bc53e6bc2720。
- source zip SHA-256：
  b346d30cb875ff210d2a7da9c78acc003a3eb24fad3b06a6913f8beb3b7854ec。

### B1：死亡历史 actor 当前事实门

状态：**逻辑修复及自动验收已完成；整个 GUI 候选未通过真实窗口验收。**

- RenderConsistencyProbe：8/8。
- DisplayContractProbe：20/20。
- 全量源码与 jar：231/231。
- 负向变异：4/4 能被拒绝。
- 证据：build/B1_TEST_EVIDENCE.txt。
- 开发 jar：build/savanna-simulation-v1.6-dead-actor-consistency-b1-dev.jar。
- jar SHA-256：
  f8afff22457753f48972f6c489ffcf6d88b0b910cfd37208d643861a05913bc8。
- source zip SHA-256：
  29d71b503db10a07bf35f4643dbd0e33b756f9186a97c187996027c7f030b59b。

B1 的 ID/species/alive/viewport 门必须保留。当前空帧不能归因于 B1：

- B0 与 B1 jar 中 SimulatorView.class 的 SHA-256 完全相同，均为
  1e4ee4ebabd30aeffb6bb7a343b027fb72c1561c42a5f423d46353aa7875e0e7。
- B0 与 B1 的 Simulator.class 也逐字节相同。
- B1 只改动 SceneDirector、一致性探针和系统测试。

因此，B1 是 B2 的逻辑基线，但不是可发布的 GUI 基线。B2 不回滚 B1。

## 三、已确认的根因与故障边界

当前代码的调用链可以确定空帧，不依赖概率猜测：

1. VisualSimulationRunner 在普通主/模拟线程创建 Simulator 并执行 simulate。
2. Simulator.refreshVisualStatus 从模拟线程直接调用
   SimulatorView.showStatus。
3. showStatus 在该线程修改 Swing 标签和共享显示字段，然后调用
   FieldView.preparePaint。
4. preparePaint 可能替换 animalImage、释放共享 animalGraphics，并调用
   clearAnimalLayer 把当前已发布的动物层清成全透明。
5. 代码随后扫描 360×240 共 86,400 个格子，逐只画动物，最后才画天气 tint 和
   诊断状态面板。
6. Swing EDT 同时在 paintComponent 中读取同一张 animalImage。它可以恰好在
   clear 完成、动物和 overlay 尚未重画时合成该图。
7. resize 回调又会在 EDT 直接调用 showStatus，因此还存在两个生产者同时遍历
   Field、替换图像和释放 Graphics2D 的风险。

terrainImage 是另一张缓存；动物、天气 tint 和状态面板都在 animalImage。于是
竞态命中时，画面会精确表现为“完整 terrain + 全透明前层”，与用户截图一致。

补充诊断也排除了“生态区域为空”：

- 默认固定种子 step 0 的 144×96 视口内有 243 个 living animals；
- step 100 同视口有 161 个；
- step 1000 同视口有 796 个。

以上数量只作根因辅助，不替代 B2 的正式并发测试。

repaint 只会向 EDT 排队，不能为之前的跨线程清空和绘制建立原子完成边界。
VISUAL_REFRESH_INTERVAL=100 只把危险窗口从每步降低到每 100 步，没有消灭它。
所以旧 R0 是必要回滚，但不是该竞态的最终修复。

## 四、B2 不可破坏契约

下列条目是前置门。任一失败，整个 B2 候选失败并回滚：

1. **完整帧发布**：EDT 只能看到上一张完整帧或下一张完整帧，绝不能看到 clear
   后、绘制中、overlay 未完成或尺寸不一致的帧。
2. **发布后只读**：一张 BufferedImage 成为前台帧后，不得再 clear、重画、
   复用为 back buffer 或释放其仍在使用的 Graphics2D。
3. **EDT 所有权**：Swing 组件、前台帧引用、viewport/resize 状态、storyboard
   和 Timer 只允许在 EDT 变更。
4. **模拟事实稳定**：渲染读取 Field 时模拟线程必须停止推进；不能为了后台绘制
   随意把可变 Field 交给线程池。
5. **100 步节奏保留**：运行中的新生态事实仍只在 step 0、100、200……进入普通
   层；不得增加每步 delay 或新的 polling timer。
6. **resize 语义澄清**：resize 可以缩放/重画同一张已提交事实帧，但不得伪装成
   新生态刷新，也不得从 EDT 并发遍历正在变化的 Field。
7. **inspect 动画保留**：进入 inspect 后生态按现有交互暂停，sceneTimer 仍以
   33 ms 推进，SceneActor.at(elapsedMs) 仍产生时间差。
8. **暂停恢复保留**：进入前运行则退出后恢复；进入前已暂停则退出后仍暂停。
9. **两套表层保留**：普通层继续使用轻量 marker；inspect 继续使用
   SceneDirector/storyboard，不能合并 renderer。
10. **B1 当前事实门保留**：当前 actor 必须 alive、ID/species 匹配并位于当前
    viewport；不能重新引入死亡幽灵或屏幕外 actor。
11. **生态和随机流不变**：地图尺寸、物种、口渴、捕食、繁殖、疾病、固定种子、
    随机调用顺序和五物种结果均不得因 B2 改变。
12. **首帧不撒谎**：窗口只能在第一张完整普通帧提交后显示；不得先展示一张
    terrain-only 初始画面。

100 步契约指“新生态事实的发布频率”，不等于禁止 Swing repaint。窗口暴露、
resize 或从 inspect 返回时，可以重画已有完整帧。

## 五、B2 目标与明确非目标

### 目标

- 消除启动、step 100 刷新、resize、缩放和退出 inspect 时的 terrain-only 帧。
- 将普通层改成“私有构建 -> 完成封口 -> EDT 单次引用提交”。
- 发生构建异常、resize 或过期结果时继续显示上一张完整帧，绝不以透明层过渡。
- 让 labels、step、尺寸、terrain、动物层和 overlay 属于同一个提交 generation。
- 以确定性并发探针和真实窗口压力验收同时证明修复，不再只看最终离屏像素。

### 非目标

- 不把普通层改成每个生态步连续移动或插值。
- 不把 A2 RenderFrame 接入 SimulatorView，不新增 renderTimer、
  pollRenderFrame 或 33 ms 普通层 polling。
- 不替换 FieldRenderer，不合并普通层和 inspect renderer。
- 不修改 SceneDirector 或撤销 B1 的当前事实修复。
- 不做 LOD、聚团、颜色、标签避让、heatmap 或水面策略重构。
- 不修改任何生态、口渴、行为、配置或随机数逻辑。
- 不在 B2 同批删除全部旧 renderer 结构；无风险清理另开后续批次。
- 不生成正式 v1.6，不修改 releases/。

## 六、设计与线程所有权

### 6.1 待新增的帧契约

建议新增 OverviewFrameBuffer.java，包含“构建中帧”和“已完成帧”两个状态：

- WritableFrame：
  - 私有 BufferedImage 和局部 Graphics2D；
  - 只允许构建阶段写入；
  - complete 前不能发布；
  - complete 或失败时在 finally 中 dispose Graphics2D。
- CompletedFrame：
  - generation、step、源宽高；
  - 完整 terrain image 与 animal/overlay image；
  - 对应 VisualGridGeometry/terrain 身份；
  - drawnAnimalCount、overlayComplete 等审计元数据；
  - 不再暴露任何写入 Graphics2D。
- OverviewFrameBuffer：
  - 只在 EDT 提交 CompletedFrame；
  - 前台引用由一次赋值替换；
  - currentFrame 在一次 paint 开始时只读取一次。

terrain 尺寸未变时可以复用上一张只读 terrain image；animal/overlay back buffer
每次必须新建，禁止清空前台图后原地重画。

### 6.2 线程模型

- **模拟线程**：唯一推进可变 Field；只在既有视觉刷新点请求 showStatus。
- **EDT**：执行普通层构建和原子提交。非 EDT 的 showStatus 必须同步 marshal 到
  EDT，调用方在完成前等待，因此 Field 在整次扫描期间不会进入下一生态步。
- **resize 回调**：不再调用 showStatus 或遍历 lastField。它只更新 EDT 所有的
  目标尺寸/geometry 并 repaint；新完整帧到来前，两层共同缩放上一张完整帧。
- **paintComponent**：开头保存一次 CompletedFrame 局部引用，terrain、动物层和
  对应尺寸都从该引用读取，整次 paint 不混用两个 generation。

选择同步 EDT 构建是本批次的正确性边界：默认 100 步才构建一次，可以用明确性能
门验证。若性能门失败，必须停止 B2 并另写“不可变事实快照 + 后台构建”计划；不得
直接把可变 Field 投递到线程池来换取表面流畅。

### 6.3 构建与提交顺序

1. 在 EDT 获取当前目标尺寸和 resize generation。
2. 新建未发布的 back buffer 与局部 Graphics2D。
3. 在该 buffer 上完成 terrain（需要时）、population pressure、动物 marker、
   weather tint 和 diagnostic panel。
4. 生成 population/environment 文本及完整性元数据。
5. 所有绘制成功后 complete；失败则 dispose 并保留旧前台帧。
6. 一次提交 frame 引用，再更新同 generation 的 label/last-events 状态。
7. 最后 repaint；第一帧成功提交后才 setVisible(true)。

不得在第 2 至第 5 步之间修改当前前台 frame。

## 七、文件范围

### 允许修改的生产文件

- SimulatorView.java
  - 拆分私有构建与 EDT 提交；
  - 修复首帧、resize、paintComponent 和 Swing 线程边界。
- Simulator.java
  - 仅允许安全创建/调用视图及避免持锁等待 EDT；
  - 禁止修改 shouldRefreshVisualStep、simulateOneStep、刷新频率和生态顺序。
- OverviewFrameBuffer.java（建议新增）
  - 实现 writable/completed/front-frame 所有权。

FieldRenderer.java 已支持向调用方提供的 Graphics2D 绘制，B2 不应修改它。

### 允许新增或修改的测试/构建文件

- OverviewFrameAtomicityProbe.java（新增）
- OverviewGuiStressProbe.java（新增，真实显示环境运行）
- DisplayContractProbe.java
- SimulationUnitTests.java
- SimulationSystemTests.java
- verify-display-contract.sh
- verify-overview-frame-contract.sh（可新增）
- test.sh
- package.bluej

### 禁止修改

- SceneDirector.java
- SimulationSettings.java
- RenderFrame.java
- RenderFramePublisher.java
- RenderAnimalSnapshot.java
- FieldRenderer.java
- 所有动物、生态、配置、随机数和 release 文件

若实现确实需要超出上述文件，立即停止并先重写计划，不能自动扩权。

## 八、测试先行：必须先在原样 B1 上保存红灯

先让 verify-overview-frame-contract.sh 在原样 B1 class 上确定性失败：它必须识别
showStatus -> preparePaint -> clearAnimalLayer 仍会修改 paintComponent 正在读取的
前台 animalImage，并识别 resize 回调仍调用 showStatus。该红灯证明旧生产路径，
不依赖新类尚未存在造成的编译失败。

### 8.1 确定性原子测试

新增 OverviewFrameAtomicityProbe，不以 sleep 和碰运气截图作为唯一证据：

1. 先提交带 animal/overlay sentinel 的完整旧帧 A。
2. 用 CountDownLatch 把新 back buffer B 停在“已创建、尚未画动物/overlay”。
3. 此时 EDT 连续读取只能得到完整 A，不能得到透明 B。
4. 完成 B 并 dispose graphics 后，才允许在 EDT 单次切换到 B。
5. 每次 paint 只能观测 A 或 B，不能混合 generation。
6. unfinished、失败、尺寸过期或 overlayComplete=false 的帧必须拒绝提交。

随后给 OverviewFrameBuffer 建立最小接口并运行原子探针；未实现所有权约束时必须
红灯，完成边界与提交逻辑实现后再转绿。

### 8.2 EDT 所有权测试

- 从非 EDT 直接 commit 必须被拒绝。
- 从非 EDT 调公共 showStatus 必须 marshal 到 EDT。
- label、前台引用、setVisible 和 repaint 的提交点必须在 EDT。
- Simulator 不得持有 pauseLock 等可回调锁再同步等待 EDT。
- resize 回调的真实 class 中不得再调用 showStatus。

### 8.3 完整性与尺寸测试

- 首帧提交前窗口不可见；首个可见帧同时具有 terrain、动物/overlay 和 step。
- back buffer 构建期间 resize，旧完整帧继续可见。
- 过期尺寸不得覆盖更新 generation，不得用 null/透明图片过渡。
- 普通层始终存在 overlay sentinel；当 drawnAnimalCount 大于 0 时动物 marker
  像素也必须大于 0。
- 构建抛出受控异常后，front frame 的对象身份和像素摘要保持不变。

现有 testOrdinaryAnimalLayerHasVisiblePixels 只证明 FieldRenderer 最终能画出像素，
不能代替上述并发测试。

### 8.4 保留门与负向变异

先复跑 B0/B1 全部探针，证明新红灯与 cadence、动画和死亡 actor 无关。完成实现后
至少验证以下变异都能被拒绝：

- 重新在 published image 上调用 clear；
- complete 前发布；
- 绕过 EDT 直接 commit；
- resize 回调重新调用 showStatus；
- paint 中二次读取前台引用并混合 generation；
- 省略 overlay 完成标记；
- 把 VISUAL_REFRESH_INTERVAL 从 100 改为 1；
- 把 SceneActor.at(elapsedMs) 改回固定时间。

## 九、实施顺序与停止点

1. 锁定现有 B1 jar、source zip、文件 SHA 和 B1_TEST_EVIDENCE。
2. 只增加 B2 探针和字节码门；在未改生产代码的 B1 上保存预期红灯。
3. 新增 OverviewFrameBuffer 所有权契约，先让原子探针转绿。
4. 在 SimulatorView 中把 animalGraphics 长生命周期改为未发布帧的局部资源。
5. 将 showStatus 变成同步 EDT 构建与提交，失败时保留旧帧。
6. 改造 paintComponent，使一次 paint 只消费一个 CompletedFrame。
7. 移除 resize -> showStatus 生产路径，改为旧完整帧同步缩放和尺寸 generation。
8. 修正首帧可见顺序及 setPaused/setStopping 等本批涉及的 Swing 调用边界。
9. 如 Simulator 需要同步调用 EDT，先把 UI 调用移出 pauseLock，再做线程切换。
10. 跑 B2 探针、B0/B1 契约、全量测试、固定种子、变异测试和文件边界审计。
11. 仅在自动门全绿后生成 B2 dev jar 与 source zip，然后停止。
12. 原生真实窗口压力验收通过后，B2 开发工作才可标为完成；不自动执行 B3 或发布。

## 十、验收矩阵

| 契约 | 自动验证 | 真实窗口验证 |
|---|---|---|
| 原子前台 | latch 强制交错只能看到完整 A/B | 连续采集至少 300 次普通 paint，terrain-only 为 0 |
| 首帧 | 可见前必须已有完整 generation | 启动时不闪现纯 terrain |
| EDT | commit/labels/visible/repaint 线程断言 | 无 AWT 线程异常、卡死或控件失灵 |
| 100 步节奏 | B0 20/20；bytecode gate | 运行中新生态事实仍按 0/100/200 更新 |
| B1 actor | RenderConsistencyProbe 8/8 | 无死亡幽灵、错物种或视口外 actor |
| resize | 过期 generation 拒绝；旧帧保留 | 五种窗口尺寸往返 10 轮，无空层 |
| zoom | 1→2.49→2.5→4→6→1 状态测试 | 10 轮缩放；退出 inspect 每次恢复普通层 |
| inspect | 33 ms 调用链；t=0/t=1200 有差异 | step 暂停但动画持续 |
| 暂停恢复 | 运行/已暂停两种状态自动测试 | 两种状态各往返 inspect 10 次 |
| 完整性 | overlay sentinel；计数与像素一致 | 动物与状态面板同时存在 |
| 失败保底 | 注入构建失败后 front 不变 | 不以透明层或半帧过渡 |
| 生态 | 不少于 B1 的 231/231；固定种子结果相同 | 不通过修改生态掩盖显示问题 |
| 性能 | 无新 timer/队列；最多 front+back 两帧 | resize、Pause、Stop & Exit 保持响应 |
| 构建 | 干净 Java 17；class diff 仅限允许文件 | 产物只进入 build/ |

真实窗口尺寸至少覆盖：

- 3600×2400
- 1732×948
- 1387×829
- 932×712
- 640×480

真实窗口采样必须跨过 step 99→100、199→200，包含运行、手动暂停、inspect、
退出 inspect、连续 resize 和 Stop & Exit。只要出现一次 terrain-only、半个状态
面板或永久不恢复，B2 即失败。

## 十一、性能边界

- 不增加普通层 timer，不增加每生态步发布，不改变 stepDelay。
- 默认 3x 地图每次 showStatus 的 p95 不得超过 B1 同机基线的 125%。
- 单次 EDT 占用若 p95 超过 150 ms，或输入事件延迟超过 250 ms，B2 不得交付；
  必须停下改写为不可变事实快照方案。
- 同时最多保留一张已发布 front 和一张正在构建的 back；不得积压 frame 队列。
- Graphics2D 必须在成功与失败路径都 dispose；连续 1,000 次构建后内存不得持续
  单调增长。
- resize 风暴期间允许暂时缩放旧完整帧，但不允许透明帧、错误 generation 或
  无界重建。

## 十二、产物、回滚与授权边界

B2 回滚基线：

- B1 jar：
  build/savanna-simulation-v1.6-dead-actor-consistency-b1-dev.jar
- B1 jar SHA-256：
  f8afff22457753f48972f6c489ffcf6d88b0b910cfd37208d643861a05913bc8
- B1 source zip SHA-256：
  29d71b503db10a07bf35f4643dbd0e33b756f9186a97c187996027c7f030b59b

建议 B2 产物：

- build/savanna-simulation-v1.6-atomic-overview-frame-b2-dev.jar
- build/savanna-simulation-v1.6-atomic-overview-frame-b2-source.zip
- build/B2_TEST_EVIDENCE.txt
- B2 前后完整源码 SHA 清单

任一 B0/B1 契约、固定种子结果、文件边界或 GUI 原子性失败，恢复整个 B2 前源码
快照，不在失败架构上继续补丁。B1 只能作为逻辑回滚基线，不能误称为已通过 GUI
的可发布版本。

本次已获得并执行 B2 实施授权。B2 的 headless 自动门、原生真实窗口压力验收和开发候选
打包均已完成；B2 开发工作已完成，仍不得 release。原生 GUI 验收两次均覆盖本计划第十节
规定的尺寸、step 边界、inspect、暂停恢复和 Stop & Exit 操作。B3 的实施必须获得单独
授权；本次仅更新其计划。

## 十三、B3：事实一致性旁路审计

状态：**生产旁路、headless 自动验收、原生 GUI 压力与性能验收均完成；等待发布授权。**

B3 已在 B2 真实 GUI 验收后的用户授权下实施。`DisplayFactAudit` / 
`DisplayFactAuditProbe` 记录同一 step、generation 和 viewport 下的不可变事实，
比较 Field living IDs、普通 marker 候选输入与 inspect living actor 输入。它没有接入
A2 `RenderFrame`、接管 `SimulatorView` 或改变画面。

## 十四、下一轮开发主体：B3 离线事实一致性旁路审计

### 14.1 唯一目标

为同一 `step`、viewport 和 visual generation 建立只读审计记录，离线核对三类事实：

1. `Field` 中当前存活且位于 viewport 的动物事实（ID、species、位置）；
2. 普通层准备绘制的 marker 输入；
3. inspect storyboard 中准备绘制的 living actor 输入。

审计应能在测试中明确指出“缺失 ID、错误 species、已死亡 actor、viewport 外 actor”
的来源，但不得改变普通层或 inspect 的实际绘制结果。

### 14.2 本轮主体与交付物

| 主体 | 交付物 | 完成判定 |
|---|---|---|
| 只读事实快照 | 具有 step、generation、viewport、ID/species/位置的不可变审计记录 | 不暴露可写 Field 或 Graphics2D，快照形成后不可变 |
| 双路径比较器 | Field ↔ ordinary marker、Field ↔ inspect living actor 的离线比较器 | 能定位缺失、死亡、物种不符及越界四类差异 |
| 确定性负向测试 | 至少四个注入差异的测试和 B3 证据文件 | 每种差异均被拒绝；正常默认种子结果为零差异 |
| 回归与边界审计 | B0/B1/B2 契约、固定种子、全量测试及文件 SHA 清单 | B2 的 100 步节奏、原子帧和性能门保持绿色 |

### 14.3 明确停止线

- 不接入 `A2 RenderFrame`，不让比较器驱动或替换 `SimulatorView`。
- 不增加普通层 timer、每步显示发布、后台绘制或跨线程访问 `Field`。
- 不改变生态、随机流、地图、物种、口渴、捕食、繁殖或疾病逻辑。
- 不修改 `releases/`，不创建正式 v1.6 发布。
- 若 B2 原子帧、显示节奏、固定种子或性能门任一回归，停止 B3 并先恢复 B2 基线。

### 14.4 建议实施顺序

1. 先锁定当前 B2 jar、source zip、性能结果与文件 SHA。
2. 只增加不可变审计 DTO 和无 UI 副作用的采集点。
3. 编写比较器与四类负向变异，使红灯先出现再转绿。
4. 复跑 B0/B1/B2 自动门、固定种子和原生 GUI 性能门。
5. 仅生成 B3 开发候选与证据；等待单独的发布授权。

### 14.5 2026-07-15 实施结果与剩余门

- `DisplayFactAudit` 是只读不可变 DTO；不持有 `Animal`、`Field` 或 `Graphics2D`。
- `DisplayFactAuditProbe` 的正常固定种子路径为零差异；缺失 ID、错误 species、死亡
  actor、viewport 外 actor 和位置不符的注入均被确定性拒绝（9/9）。
- `verify-display-fact-audit-contract.sh` 同时拒绝对 A2 `RenderFrame` 的接入，并验证
  普通 marker 与 inspect storyboard 的采集点存在。
- `./test.sh` 为 253/253，B0/B1/B2 自动门均通过；固定 seed `StabilityProbe 1000`
  与 B2 终态一致：Buffalo=597、Zebra=631、Lion=256、Gazelle=1054、Cheetah=116。
- `OverviewGuiStressProbe` 已在原生显示环境通过：300 次普通 paint 覆盖五种尺寸、
  99→100/199→200、暂停、inspect、缩放、恢复与 Stop & Exit，未报告 terrain-only
  或不完整前层。`OverviewFramePerformanceProbe b3` 同机通过：1732×948 的
  p95=122.30 ms、max=124.35 ms、queued-input=126.23 ms，分别低于 150 ms、
  150 ms 和 250 ms 限值，且 p95 低于 B1 133.21 ms 基线的 125%（166.51 ms）。
  B3 开发候选保留在 `build/`，不得写入 `releases/` 或创建正式 v1.6，直至另获发布授权。
