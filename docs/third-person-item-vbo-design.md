# 第三人称手持与掉落枪械静态 VBO 实施细节

更新日期：2026-09-24。本文针对 `TimelessAndClassicsZeroF`；展示枪与物品即时 VBO 代码已接入并通过 Java 编译，游戏内画面与光影 pass 仍待实测。

## 1. 范围和核心决定

`THIRD_PERSON_RIGHT_HAND` 与 `GROUND` 的枪械模型都是静态外观。配件、弹药、瞄具安装等状态只在外观改变时重新选择网格；每帧的实体运动和掉落旋转只改变绘制矩阵。第三人称与掉落物不执行瞄具模板逻辑，也不使用枪体骨骼动画。当前没有染色功能，顶点颜色固定为白色，接口与缓存键都不引入颜色参数。第一人称、GUI 和预览保持各自现有流程。

新路径复用展示枪的 `ExternalGunModel`、`DisplayGunModelInstance.configureGun`、普通／自发光分流和完整外观组合捕获。首版不拆枪身、可见骨骼或配件成逐次 draw；一个外观键对应按材质和拓扑分组的完整静态网格。是否进一步拆分只根据实际缓存数量、显存与 draw 成本决定。

TACZ 的 [GunItemRendererWrapper.renderByItem](D:/Minecraft/Dev/MCModderAnchor/TimelessAndClassicsZeroF/src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java:316) 已排除第一人称和第三人称左手，并选择枪模、LOD 与贴图。新分支只接 `THIRD_PERSON_RIGHT_HAND` 和 `GROUND`。显式不受支持的外观走旧绘制；受支持但 VBO 尚未就绪时，本次跳过模型绘制，同时继续捕获／上传调度。枪口事件或激光若仍由外围渲染器触发，不写入静态网格，也不影响捕获键。

## 2. 从展示枪抽取同一套外观和几何

当前 [DisplayGunMeshGroup](D:/Minecraft/Dev/MCModderAnchor/TimelessAndClassicsZeroF/src/main/java/com/tacz/guns/client/renderer/block/DisplayGunMeshGroup.java:44) 私有 `GeometryKey`、`Snapshot` 和配件解析。将其移至 TACZ 共享的外部外观解析类，例如 `ExternalGunAppearance`：

- 输入为 `ItemStack`、选定的 `GunDisplayInstance` 和是否使用 LOD；输出不可变快照与稳定几何键。不要长期保存可变的原始 `ItemStack`。若捕获延后，快照持有用于捕获的拷贝，且必须在提交前核对资源版本。
- 键至少包含 `ClientIndexManager.resourceRevision()`、display 身份、实际模型／LOD 选择、有效配件类型和 ID、弹膛／弹匣有弹状态。当前展示原型对配件变体和 slot adapter 会完整回退；物品首版沿用相同支持边界，不能静默渲染成普通配件。
- 位置、朝向、物品上下浮动、环境光和 overlay 不进入外观键。`FIXED`、`THIRD_PERSON_RIGHT_HAND`、`GROUND` 的定位节点和 display 缩放是各自固定的捕获变换；物品 VBO 键额外包含视角，避免不同静态变换共用错误顶点。
- `DisplayGunModelInstance.configureGun` 已处理弹药、弹匣、瞄具安装、护木、默认配件及 adapter 子节点显隐；仅在捕获某个外观时调用。它复用一个可变实例，因此捕获必须串行，不能同时把它用于两项捕获。

将 [DisplayGunGeometry.capture](D:/Minecraft/Dev/MCModderAnchor/TimelessAndClassicsZeroF/src/main/java/com/tacz/guns/client/renderer/block/DisplayGunGeometry.java:46) 改为接受物品视角，同一捕获流程按视角应用 `SbmGunTransforms`。展示枪仍在捕获时烘入 `FIXED`；第三人称和掉落物分别烘入其固定定位与 display 缩放。`ItemRenderer.render` 在调用 BEWLR 前统一施加 `(-0.5,-0.5,-0.5)`，因此这两个物品视角的静态捕获先补入 `(0.5,0.5,0.5)`；展示枪绕开 ItemRenderer，不补此位移。这样掉落物的旋转中心保持与旧路径一致，也让非均匀缩放的法线在静态捕获中处理。配件仍根据 SBM 挂点及 pivot 进入同一次完整外观捕获。普通与 `_illuminated` 顶点继续分到各自 RenderType。当前捕获只调用 QUADS；若明确扩大模型支持范围，再补 TRIANGLES 的 pass，首版保持展示原型的现有边界。

展示枪的槽位矩阵和 `FIXED` 捕获变换未改变；仍需在游戏内对比重构前后位置、挂点和材质，并分别核对手持／掉落的定位矩阵。

## 3. 即时物品 VBO 缓存

SBM 当前 `GeometryCache` 属于单个 `WorldMeshGroup`，`WorldMeshRenderer.submit` 也要求已登记的世界组，绘制固定在 `AFTER_BLOCK_ENTITIES`。不能把每帧的手持或掉落物登记为世界对象再等该阶段绘制；实体 pass、阴影目标和物品前后顺序会改变。

SBM 已增加 `ImmediateStaticMeshRenderer`，复用 `GeometryCollector`、`MeshSink`、静态 `VertexBuffer` 池和异步上传／安全回收逻辑。缓存按完整外观键加物品视角保存材质／拓扑 pass；相同外观和视角的多个手持或掉落实例共享一份 VBO。展示枪世界组仍有自己的缓存，同一外观在展示、手持和掉落视角可分别占用 GPU 网格。静态捕获变换不同，不能直接共享同一份顶点。

当前公开接口形态：

```java
DrawResult tryDraw(Object geometryKey, GeometryProvider provider,
                   PoseStack pose, int packedLight, int packedOverlay,
                   Runnable beforeDraw);
// DrawResult: DRAWN / PENDING / UNSUPPORTED
```

`provider` 只在该键尚未捕获时调用，使用同一个 `DisplayGunGeometry` 捕获函数；不能每帧执行 `renderBoneTree`。第一次请求可在渲染线程按预算捕获并排队上传。`PENDING` 表示此物品已由 VBO 路径接管，本次不绘制模型，也绝不执行耗时旧路径；下一帧继续请求并推进队列。仅全部 pass 上传完成后返回 `DRAWN`。捕获失败不能缓存为空成功；可重试故障继续 `PENDING` 并记录原因，只有经判断永久不支持的外观才返回 `UNSUPPORTED`。物品实例没有稳定登记身份，缓存应按最后使用帧和 GPU 字节预算回收；上传中的句柄须等 future 完成后才能放回池。

捕获／上传队列要保证进展：每帧至少处理一个待办项，其余受时间与字节预算约束；轮转排队项，避免某个昂贵模型阻塞后面的外观。不能把旧绘制耗时计入并耗尽新路径的准备预算。资源缺失等可重试失败要有退避或显式重新触发，避免每帧空转。

## 4. TACZ 物品入口与矩阵

在 `GunItemRendererWrapper.renderByItem` 的旧模型绘制前插入物品分支。先按同一距离规则选择 `ExternalGunModel`、贴图和外观键，只有 `UNSUPPORTED` 才继续解析旧模型；仅 `THIRD_PERSON_RIGHT_HAND`、`GROUND` 且非预览时尝试。`DRAWN` 和 `PENDING` 都跳过旧枪体绘制。独立客户端开关 `render.EnableExternalItemGunVbo` 默认开启，VBO 默认优先于 AR：不因 AR 可用、Oculus 加载或已验证可用的光影包而改走旧路径；AR 只可能在明确不支持 VBO 或用户关闭该功能时通过旧路径接管。`FIXED` 仍由展示枪流程负责，其他上下文不接入。

为 [SbmGunTransforms](D:/Minecraft/Dev/MCModderAnchor/TimelessAndClassicsZeroF/src/main/java/com/tacz/guns/client/renderer/item/SbmGunTransforms.java:9) 增加 `applyThirdPerson` 与 `applyGround`：读取 `thirdperson_hand`／`ground` 定位节点和各自 display scale，仅在对应视角的捕获阶段调用。即时绘制使用 ItemRenderer 已提供的 `PoseStack`，其中的实体运动与掉落旋转每次更新；不要把这些运行时矩阵烘进缓存。三种视角的静态变换以旧路径顶点位置和挂点逐一验证。

物品分支不能改变第一人称共享模型状态。当前第三人称枪口世界坐标上报位于旧绘制之后；若该行为仍需保留，应使用外部模型的枪口查询点与同一实例矩阵计算，并使新旧分支共用后续上报代码，而不是为此再调用旧枪体 render。静态网格不包含粒子或激光。

## 5. 即时绘制契约

一次成功绘制必须在 `renderByItem` 当前 pass 内完成，不能推迟到世界网格阶段。流程为：

```text
根据外观键取得所有已上传 pass；任何 pass 未就绪则本次跳过模型绘制并继续准备
在当前 bufferSource 上提交排在物品前面的缓冲
读取当前 PoseStack 中的实体／掉落物矩阵；视角固定变换已在捕获时烘入网格
对每个材质／拓扑 pass：setupRenderState → 设置矩阵和实例属性 → bind/draw VBO → clearRenderState
恢复 VBO、shader、顶点属性及矩阵状态；返回 DRAWN
```

当前 TACZ 旧路径在 [BedrockModel.render](D:/Minecraft/Dev/MCModderAnchor/TimelessAndClassicsZeroF/src/main/java/com/tacz/guns/client/model/bedrock/BedrockModel.java:379) 中显式 `endBatch(renderType)`，并有 Oculus 包装缓冲的兼容处理。新路径在直接 draw 前也要保证传入缓冲真正提交；真正无法识别或无法 flush 的 `MultiBufferSource` 才返回 `UNSUPPORTED`，不改用全局缓冲偷偷绘制。各 pass 使用其真实 RenderType 的目标、深度、裁剪和材质状态；主场景和阴影 pass 分别使用当前目标。光影已验证可用，不能仅凭启用光影就标记为 `UNSUPPORTED`。

普通顶点光照是本次实例的 `packedLight`，自发光 pass 保留捕获时的满亮；overlay 是本次调用的 `packedOverlay`。可沿用世界 VBO 的 UV2 常量整数 attribute 技术，并为 UV1/overlay 增加对应逐 draw 常量属性；绑定不同 VAO 后需重新设置，上传格式变化时需重新验证元素布局。颜色固定白色；预览的透明 alpha 不进入此路径。

局部位置正确并不代表法线正确：本地 1.20.1 的 entity cutout shader 直接使用顶点 `Normal` 参与方向光计算。display 的非均匀缩放已在视角静态捕获时按法线矩阵烘入；即时绘制把当前 pass 的方向光变换到实例局部空间。对运行时额外的非均匀实体缩放，单一反变换光源方向不保证逐顶点完全等价，需要游戏内另行验证。`drawWithShader()` 可能重设光照 uniform，因此即时路径手动更新当前 pass 的光源 uniform 后调用 VBO draw。

## 6. 失效、交接和验证

`ClientIndexManager.resourceRevision()` 改变、F3+T 重载、模型／LOD 或贴图替换、顶点格式漂移时，使旧键和相应 VBO 失效。GPU 释放只在渲染线程进行；已排队上传的缓冲在完成后再回收。关闭优化开关应立即让物品走旧路径，并禁止同一调用再次 VBO 绘制。世界切换时清理依赖旧 `ChunkRenderDispatcher` 的排队任务。

展示枪原型的 `DisplayGunMeshGroup.tryRender` 当前调用 `claimForWorldDraw` 后直接返回 true。这与“受支持但未就绪时跳过旧绘制”一致：`claim` 的返回值只表示本帧世界层是否已有可画 VBO，不决定是否调用 BER。迁移展示捕获矩阵时应保留这个交接语义，不能把 `claim == false` 误改为旧路径回退。未就绪可能短时不可见，统计中要区分等待、捕获失败和上传失败。

实施顺序：

1. 抽出共同外观解析与按视角静态捕获，保持展示枪 `FIXED` 变换和未就绪跳过语义；编译并在游戏内确认展示效果不变。
2. 在 SBM 实现物品专用缓存、上传和即时 draw；先验证单一普通材质、单位缩放与 `NO_OVERLAY`，再完成自发光、动态光照／overlay 与非均匀缩放法线。
3. 在 TACZ 接入第三人称右手和 `GROUND`，完成定位矩阵、LOD、未就绪跳过与明确不支持时的旧路径；检查资源重载和 GPU 回收。
4. 验证原版、Embeddium、Oculus 主场景／阴影 pass 与 AR 环境。已验证可用的光影组合默认尝试 VBO，AR 的优先级低于 VBO。

游戏内对照应覆盖近远 LOD、手持运动、掉落物旋转、非均匀缩放、明暗变化、满亮、自带／安装配件、满空弹、overlay、资源重载及从未就绪到就绪的过渡。性能记录捕获次数、上传字节、VBO 数和显存、draw 数、CPU 绘制时间及帧时间波动；共享 VBO 避免逐帧重建顶点，但不同物品仍各自发起 draw，不等同于硬件实例化。现有 [世界网格文档](D:/Minecraft/Dev/SimpleBedrockModel/docs/world-mesh-rendering.md) 的 `cpuUs` 是 CPU 计时，不是 GPU 计时。
