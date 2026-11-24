# SBM 渲染优化前期调研

前期调研优先关注世界 billboard 粒子实例化。第一人称枪体也有加速空间，但瞄具模板、手部投影和材质 flush 会限制批次边界。对于第一人称，可以先用现有 AR 和逐刚性块 VBO 做基线，再验证派生 Iris shader 能否在复杂枪上同时降低 CPU 逐顶点变换和 draw 提交的成本。派生 shader 适合作为可关闭、可回退的实验路径。

SBM 部分以 `1.20.1-dev` 为基准，并对照 `SBM-1.21.1` 的世界粒子入口。第一人称规模来自现有模型的估算，常规枪处理后约有 20–30 个独立骨骼，复杂枪约有 50–60 个，约一半会独立运动，其余主要用于显隐或定位。现有 TaCZ 实测指出 CPU 逐顶点矩阵变换是主要开销。外部项目的实现来自下文固定的提交。本文记录动手试验前的技术判断。

## 动画网格和光影管线

SBM 的世界网格 `INSTANCE` 共享静态 VBO，按对象应用矩阵并逐个 draw。它在 `AFTER_BLOCK_ENTITIES` 绘制，该事件在 Iris/Oculus 的阴影 pass 中不会触发。顶点布局适配解决的是绘制输入问题，世界模型投影仍取决于阴影阶段是否也提交了相应几何。SBM 会按实际上传格式处理属性位置，因为光影开启后 Oculus 可能使用扩展实体顶点格式。 [世界网格说明](../mesh/world-mesh-rendering.md) · [WorldMeshRenderer.java](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/client/mesh/world/WorldMeshRenderer.java#L44) · [WorldMeshBuffers.java](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/client/mesh/gpu/WorldMeshBuffers.java#L161)

| 路径 | 顶点变换和提交 | 光影接入 |
| --- | --- | --- |
| 逐刚性块绘制 | CPU 计算骨骼矩阵。每块静态几何用自己的 `ModelViewMat`，同矩阵的索引范围可以合并提交。 | 使用现有 shader 和顶点布局。每次更新矩阵时同步相关法线和光照状态。 |
| GPU 预变换 | CPU 提交姿态。Compute shader 或 transform feedback 生成已变形的顶点缓冲。同一帧姿态的结果可供需要的 pass 复用。 | 用对应 pass 期望的顶点布局、对象数据和材质提交。GPU 多一次顶点写入和读取，不同姿态各占输出空间。 |
| 最终顶点 shader 内查矩阵 | 顶点按骨骼编号查姿态，共享模型几何的多个世界实例还可用 `gl_InstanceID` 选择各自的实例数据。 | 光影包接管最终 shader 时，需要为该程序提供读取骨骼或实例数据的逻辑。可在运行时派生程序。 |

Iris 接管实体和阴影程序。自定义骨骼属性、SSBO 和 `gl_InstanceID` 的业务含义需要绘制程序理解。`glMultiDrawElements` 的各个子 draw 共用当前 program、uniform 和材质状态，`baseVertex` 只能改变顶点索引基址。若需要用 `gl_DrawID` 区分骨骼矩阵，shader 仍须主动查表。CPU 先算完所有顶点再 MultiDraw 可以合批，但仍承担逐顶点变换。 [Iris 自定义 shader 兼容说明](https://github.com/IrisShaders/Iris/blob/26.1/docs/development/compatibility/core-shaders.md) · [Iris 渲染阶段](https://shaders.properties/current/reference/programs/overview/) · [OpenGL shader draw parameters](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_shader_draw_parameters.txt) · [Transform Feedback](https://wikis.khronos.org/opengl/Transform_Feedback)

## AcceleratedRendering 的缓存和整模提交

SBM 的 v2 树模型按骨骼缓存 `IMesh`。烘焙模型按 `BakedGeometryChunk` 缓存，静态几何可预先折叠到最近的运行时挂接骨骼。缓存还按消费端 `IBufferGraph` 区分。每个 `beginTransform()` 提交一组位置和法线矩阵，随后 `mesh.write()` 登记局部几何、颜色和光照。实际绘制由 AR 在后续批次中执行。当前依赖的 `ServerMesh` 会按顶点布局共享底层缓冲，多个 `IMesh` 可以只是同一缓冲里的不同范围。 [SBM 的 AR 接入](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/client/compat/acceleratedrendering/AcceleratedRenderer.java#L79) · [几何烘焙](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/common/model/baked/BedrockGeometryBaker.java#L39) · [核对的 AR 版本](https://www.curseforge.com/minecraft/mc-mods/accelerated-rendering/files/8179092)

整模缓存的潜在收益是把多个 `mesh.write()` 和矩阵登记合成每模型、每材质一次提交。AR 已经做了后续合批，最终 draw 数未必随提交数同比减少。骨骼仍独立运动时，整模输入需要携带顶点所属骨骼及该实例的矩阵表。当前 `IMesh.write()` 配合单次 `beginTransform()` 只能对整块使用同一组矩阵。核对的 AR 版本提供上传与变换 compute override，可作为原型接入点。画面一致性还取决于保留骨骼显隐、全亮以及原有材质分流。 [AR 项目](https://github.com/Argon4W/AcceleratedRendering) · [SBM 骨骼绘制](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/common/model/tree/TreeBedrockModel.java#L158)

## TaCZ 第一人称的提交边界

逐块 VBO 的 draw 数由当帧可见、带几何且需要独立变换的块数决定。按现有模型估算，常规枪有约 10–15 个独立运动节点，复杂枪约 25–30 个。无几何定位点无需绘制。只切显隐的子节点可以把局部几何烘到父级空间，保留独立索引范围，与父级共用 `ModelViewMat`。例如子弹相对弹匣固定时，可以按显隐选出索引范围，再随弹匣一起提交。SBM 当前的烘焙按最近的运行时骨骼归并几何，被保留为运行时骨骼的显隐节点仍可能单独成块。 [几何归并实现](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/common/model/baked/BedrockGeometryBaker.java#L39) · [运行时骨骼选择](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/common/model/baked/BedrockModelBaker.java#L119)

使用标准 shader 逐块更新矩阵时，需要保持目标版本的法线与光照约定。当前 SBM 的 `drawItem()` 在批次内更新 `ModelViewMat` 和物品方向光。Iris 1.20.1 的 `ExtendedShader.apply()` 还从模型视图矩阵派生 `NormalMat` 和 `ModelViewMatInverse`，只更新 `ModelViewMat` 可能让后两者沿用上一块的值。 [MeshBatchRenderer.java](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v2/client/mesh/render/MeshBatchRenderer.java#L61) · [Iris 矩阵派生](https://github.com/IrisShaders/Iris/blob/1.20.1/src/main/java/net/irisshaders/iris/pipeline/programs/ExtendedShader.java#L142-L168)

TaCZ 的枪身、瞄具局部模型、手臂和粒子共用手部 pass 中的投影与深度状态，但模板写入、模板测试和清理之间有各自的绘制窗口。现有 TaCZ AR 路径有批次前后回调处理模板，Oculus 兼容层会解包手部 `UnflushableWrapper` 来执行需要同步的 flush。新路径需要在原窗口内完成批次，不能把枪身或瞄具片段随意延迟到整帧结束。现有 AR 路径可以作为对照。 [TaCZ 枪体模板阶段](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/client/model/BedrockGunModel.java) · [Iris 手部 pass](https://github.com/IrisShaders/Iris/blob/1.20.1/src/main/java/net/irisshaders/iris/pathways/HandRenderer.java)

## DuDuMeshLoader 的逐网格 VBO

DuDuMeshLoader `959b5fdf` 面向旧版 TaCZ 的 Bedrock `poly_mesh`。加载时识别退化 quad，生成真实三角形，再按位置、UV 和法线的精确浮点位模式去重。它有自定义平滑角和背面剔除，也保留 LOD 和附件接入。每个骨骼下的 `PolyMesh` 可有自己的索引 VBO，绘制时遍历显隐、更新 `PoseStack` 并用当前标准实体 shader `drawWithShader()`。这个路径移除了主路径的 CPU 逐顶点变换，仍有每可见 mesh 一次 draw 和绑定。 [几何解析与索引化](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/core/PolyMesh.java#L115-L239) · [逐网格绘制](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/core/PolyMeshModel.java#L305-L344)

项目还按枪型和类型缓存内置附件，并提供装饰方块和武器架。 [项目说明](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/README.md)

第一人称主动关闭 TaCZ 的 AR 路径，在原生枪身模板清理前同步绘制。瞄具的 `scope_body` 等特殊子树可在模板窗口内直绘。其余适用场景也可把局部 `IMesh`、矩阵和光照交给 AR，必要时经 `VertexConsumer` 绘制。 [枪身接入](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/tacz/TaczPolyMeshGunModel.java#L108-L149) · [瞄具接入](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/tacz/TaczPolyMeshAttachmentModel.java#L135-L153) · [AR 接入](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/compat/ar/PolyMeshAcceleratedRenderer.java#L65-L118)

该提交有三个会影响复用的细节。索引 VBO 只保存首次上传的 UV2，环境光和枪口光变化可能沿用旧值。光影切换调用的 `invalidateVboCache()` 漏掉了索引 VBO。非等比缩放的回退条件只在构造时检查骨骼，动画后续改变缩放时仍可能走 VBO。 [索引 VBO 与缓存失效](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/core/PolyMesh.java#L811-L1012) · [刚性变换检查](https://github.com/YAMAISIHOTO/DuDuMeshLoader/blob/959b5fdf41a3ad0d58e88b4d283f15df3f071461/src/main/java/com/example/dudumeshloader/core/PolyMeshModel.java#L71-L104)

## TaCZ Render Improvements 的派生 shader

`1.21.1` 分支 `7301c4f6` 面向 NeoForge、TaCZ 1.2.0 和 OpenGL 4.3。第一人称入口接收 `entity_cutout` 的 `QUADS` 和支持的缓冲源，后台将模型的 cube 几何编成常驻 VBO。几何片段和姿态槽分别编号，符合条件的匿名叶节点可以共享父级矩阵，同时独立显隐。可见片段按提交顺序拼接索引，并缓存最多 32 套 EBO。每批上传姿态数据后绘制一次模型几何，`glDrawElementsInstanced` 的实例数为 1。不同材质和模板窗口仍可能拆批，材质层还有触发 flush 的退化图元。 [第一人称布局与索引缓存](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/HeldGunRenderer.java#L137-L273) · [实际提交](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/RenderPipeline.java#L139-L183)

掉落物、展示框和第三人称右手枪缓存外观几何。在同一缓冲所有者、外观、显示上下文和材质下合并实例，顶点用 `gl_InstanceID` 读取实例矩阵。姿态记录每项 176 字节，包含位置矩阵、法线矩阵、颜色、光照、overlay 和 Iris 对象标识。阴影阶段优先从 TaCZ 已有的外观变体中选择 LOD。 [物品实例化](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/ItemGunRenderer.java#L30-L80) · [姿态记录](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/PoseBuffer.java)

### 改写与状态隔离

1. 从当前 program 读取 Iris 已处理过的顶点 GLSL。保留 `in vec3 iris_Position` 等输入声明，把引用改成 `tr_Position` 等临时变量，把原 `main()` 改名为 `tr_original_main()`。
2. 提升到至少 `430 core`，加入 SSBO、骨骼属性和新的 `main()`。第一人称按骨骼编号查矩阵，世界物品按实例编号查矩阵，先算出位置、法线等输入，再调用原 `main()`。
3. 创建独立 program，用新顶点阶段和原片元、几何阶段链接。复制属性、片元输出和资源块绑定，按原 program ID 和绘制模式缓存。
4. 批次绘制前调用原 shader 的 `apply()`，让 Iris 设置纹理、混合和写入目标，再把原 program 的 uniform 值复制给派生 program。模型在原材质 `clearRenderState()` 前绘制。 [GLSL 改写](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/ShaderTransform.java) · [顶点变换](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/resources/assets/tacz_render_improvements/shaders/geometry.glsl) · [程序派生](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/ShaderBridge.java#L62-L153)

其他普通 draw 继续使用原 program ID。共享状态的影响来自派生批次里的 `apply()`、`clear()` 和 framebuffer 操作。代码显式恢复 VAO、数组缓冲、SSBO 及绑定范围。其余状态由原 shader 和材质清理。核对的 Iris 1.8.12 在 `ExtendedShader.clear()` 中重新绑定主目标，因此共用 flush 窗口的其他渲染要考虑绑定顺序。项目还会在帧开始时启用主目标 stencil，两个优化开关都关掉也可能触发 NeoForge 重建深度模板附件。可选加速接入应当让桥接和该路径引入的 framebuffer 操作随开关停用。 [逐批状态清理](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/RenderPipeline.java#L167-L183) · [帧开始的 stencil 操作](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/RenderPipeline.java#L46-L89)

### 光影兼容范围

Iris 会先将光影包的兼容或 core 风格输入转换成内部属性。Iris 1.8.12 的 `gl_Vertex` 和 `vaPosition` 均有映射到 `iris_Position` 的路径。因此普通第一人称 cutout 有较广的潜在覆盖，仍要按实际的手部、瞄具和世界阴影 pass 分别验证。半透明第一人称和其他图元走原路径。若 shader 使用细分阶段、没有可识别的位置输入，或派生程序编译链接失败，代码会尝试用 CPU 补画当前批次，并为该材质层停用加速。 [Iris 的 GLSL 转换](https://shaders.properties/current/how-to/compatibility_vs_core/) · [第一人称入口](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/HeldGunRenderer.java#L37-L55) · [回退路径](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/RenderPipeline.java#L180-L218)

链接成功只说明 program 可用。某个 pass 仍可能读取派生程序未绑定的属性，或者依赖不同的状态和数据约定，导致材质或模板错误而不触发自动回退。NeoForge Iris 1.8.12 与 1.8.14-beta.1 的本地 JAR 都包含桥接查找的方法，但反射接口变化可能直接抛异常。最小 GLSL 输入已验证入口和属性替换，BSL、Complementary 等具体版本和预设仍需验证画面与实际加速命中。 [顶点属性绑定](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/GpuMesh.java#L37-L74) · [Iris 反射桥接](https://github.com/CHUJIAN-afk/tacz-render-improvements/blob/7301c4f6bd356974bfc81a9ec6799b1f5f6ccf9a/src/main/java/first/tacz/render/client/RenderPipeline.java#L56-L102)

## 世界粒子实例化与 MadParticle

SBM 世界粒子由 `WorldEmitterManager` 送进原 `ParticleEngine`，`MolangWorldParticleRenderType` 已按材质和纹理批绘制。`SnowStormParticle.render()` 每帧先做位置插值或锚点跟随，再算 billboard 朝向、自旋、尺寸和四角，最后写四个顶点。Sodium 可以加快顶点写入，四角仍由 CPU 算出。第一人称局部粒子和转换到世界后的粒子是两个入口，世界粒子可以先验证，避免改动瞄具模板窗口里的手部粒子。 [世界粒子入口](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/WorldEmitterManager.java#L142) · [现有 billboard 展开](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/SnowStormParticle.java#L321) · [世界材质批次](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/MolangWorldParticleRenderType.java#L39)

MadParticle 有与两个目标 Minecraft 版本对应的历史提交，主分支 `f04d87c` 是 26.1.2。三代实现都让实例重复使用四边形，CPU 工作的位置不同。

| Minecraft 版本 | 实例数据与执行位置 | 同步方式 |
| --- | --- | --- |
| 1.20.1 `5927739` | 每粒子记录 112 字节，包括 CPU 构造的完整矩阵、UV、颜色和光照。 | 渲染时分线程写实例缓冲，draw 前 `join()`。 |
| 1.21.1 `36cea9d` | 每粒子 41 字节，提交位置、自旋、尺寸、UV、颜色和压缩光照。shader 根据相机和 `gl_VertexID` 展开四角。 | 每帧分线程打包，draw 前仍需等待。 |
| 26.1.2 `f04d87c` | 每粒子 56 字节，含前后两个 tick 的位置和自旋。GPU 按 `partialTick` 插值，再展开 billboard。 | tick 后并行填下一份映射缓冲，完成后轮换。渲染帧主要更新相机参数和绘制。 |

[MadParticle 1.20.1 实例化](https://github.com/USS-Shenzhou/MadParticle/blob/59277398856a75a50472083e2c4b883af9af3cbb/src/main/java/cn/ussshenzhou/madparticle/particle/InstancedRenderManager.java#L147-L274) · [1.21.1 打包](https://github.com/USS-Shenzhou/MadParticle/blob/36cea9d6d4b6614e6f2b74f548b2007069c9565f/src/main/java/cn/ussshenzhou/madparticle/particle/optimize/InstancedRenderManager.java#L208-L330) · [26.1.2 缓冲更新](https://github.com/USS-Shenzhou/MadParticle/blob/f04d87c3fa025f6e950e3dd3c91c472f8dcaf2d2/src/main/java/cn/ussshenzhou/madparticle/particle/render/NeoInstancedRenderManager.java#L155-L263) · [GPU 插值](https://github.com/USS-Shenzhou/MadParticle/blob/f04d87c3fa025f6e950e3dd3c91c472f8dcaf2d2/src/main/resources/assets/madparticle/shaders/instanced_particle.vsh#L50-L75)

### MadParticle 的 Iris framebuffer 接入

1.21.1 的普通混合路径先 `apply()` 自己的实例化 shader，再从 `GameRenderer.getParticleShader()` 返回的 Iris shader 对象反射读取 `writingToAfterTranslucent`。它只绑定该 framebuffer 的 OpenGL ID，接着用自己的 shader 进行粒子实例化。颜色和深度操作落在 Iris 已配置的附件上。开启 OIT 时，粒子先写自己的累积缓冲，再通过全屏 pass 将结果混入 Iris 目标。26.1.2 则在 Iris final pass 内调用自己的粒子渲染。 [普通混合路径](https://github.com/USS-Shenzhou/MadParticle/blob/36cea9d6d4b6614e6f2b74f548b2007069c9565f/src/main/java/cn/ussshenzhou/madparticle/particle/optimize/InstancedRenderManager.java#L375-L408) · [OIT 合成](https://github.com/USS-Shenzhou/MadParticle/blob/36cea9d6d4b6614e6f2b74f548b2007069c9565f/src/main/java/cn/ussshenzhou/madparticle/particle/optimize/InstancedRenderManager.java#L452-L477) · [final pass 接入](https://github.com/USS-Shenzhou/MadParticle/blob/f04d87c3fa025f6e950e3dd3c91c472f8dcaf2d2/src/main/java/cn/ussshenzhou/madparticle/mixin/compat/IrisFinalPassRendererMixin.java)

借用 framebuffer 会沿用光影包的输出映射。例如 `RENDERTARGETS: 5,3` 把片元输出槽 0 映射到 `colortex5`。MadParticle 的普通片元 shader 输出单个 RGBA，不会提供包定义的颜色编码、法线或材质附件内容。后续合成能否正确解释粒子，取决于具体光影包。需要保留原粒子着色时，可以在 GPU 上预展开到标准 `PARTICLE` 顶点布局，再走原粒子 pass。 [MadParticle 片元输出](https://github.com/USS-Shenzhou/MadParticle/blob/36cea9d6d4b6614e6f2b74f548b2007069c9565f/src/main/resources/assets/madparticle/shaders/core/instanced_particle.fsh#L16-L24) · [Iris 输出映射](https://shaders.properties/current/reference/constants/rendertargets/)

[试验方案](experiments.md) · [世界粒子测量](measurements.md) · [当前结论](conclusions.md)
