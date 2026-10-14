# 实验性vbo静态模型优化说明

本文档描述针对较高精度的静态模型的实验性优化尝试。相关API和内部实现随时可能变更，暂不推荐作为稳定的长期方案。

对应 `2.6.0-neoforge-mc1.21.1`。使用方法见 [即时／批量接口](static-mesh-api.md) 和 [世界渲染组](world-mesh-rendering.md)，本次平台适配与验证见 [移植记录](../1.21.1-2.6.0-port.md)。

## 结构与职责

包根为 `com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh`。调用方提供几何、不可变 key 和实例状态；SBM 管理捕获、缓存、上传、剔除与绘制。

- 临时网格：`StaticMeshRenderer`／`StaticMeshBufferSource` 使用 `(owner, geometryKey)` 缓存，处理当前调用或渲染阶段的命令。
- 世界网格：`WorldMeshGroup` 登记长期对象，每组独立缓存；`WorldMeshRenderer` 在 AFTER_BLOCK_ENTITIES 读取状态、准备和绘制。
- 共用后端：`GeometryCollector`／`MeshSink` 捕获几何，`MeshBatchRenderer` 执行材质、矩阵、VAO 与 draw；`WorldMeshBuffers` 管世界 GPU 资源。

INSTANCE 同 key 共享 VBO，逐对象应用矩阵；SECTION 按 `16×16×16` section、材质和拓扑拼接几何。SECTION 的位置／旋转／显隐变化重建受影响批次，纯光照变化只更新独立 UV2 流。每个批次保留 active 和最多一份 pending，上传完成后换版；成员光照范围随版本保存，避免新版范围写入旧 VBO。

## 缓存与上传

准备任务、就绪几何和绘制命令分别管理。同键复用唯一任务，全部 Part 就绪才可绘制；提交时固定 target／兼容 previous，flush 不改选网格。命令只属于当前阶段，不跨帧补画。

provider 在当前调用内同步捕获并编码，保留 BufferBuilder／Oculus 的当前分类上下文。任务保存独立 RenderedBuffer、实际格式及上传进度，不保存临时 ItemStack、PoseStack 或 consumer。返回 false 表示暂不可捕获，后续退避重试。

临时上传由 SBM 在 Forge RenderTick START 推进；默认每帧捕获 4 次／2ms，上传 4 个 Part／8MiB／2ms，准备数据和几何驻留分别使用 256MiB 软准入预算。首个 Part 可以越过字节／时间额度，一个超大几何可单独驻留。预算不足保留任务等待。

当前帧／上一帧仍请求的条目、准备中条目及有绘制引用的条目不参与空闲淘汰。真正闲置后按 128 条 LRU 和 retention 回收，默认 30 秒；释放绘制引用不取消准备任务。失效时，未上传数据及 GPU Part 各释放一次，旧代结果不能重新进入缓存。

世界渲染组仍使用 SectionRenderDispatcher 上传队列。每组的捕获和网格准备分别有 4 项／2ms 软预算，尚不支持主动取消原版排队上传。

1.21.1 上传数据由 `MeshUpload.Payload` 同时持有 `MeshData` 和 `ByteBufferBuilder`。世界上传保留到 future 完成；临时缓存保留到上传或取消，失败与同步拒绝也释放原生存储。`MeshData` 被 VertexBuffer 消费后，底层存储仍由 Payload 负责关闭。

## 光照与整数属性

采用整数参数 VBO：65536 个 `(u,v)`、每项两个 GL_INT、8 字节，共 512KiB，一次初始化上传。两个完整 16 位分量须在 0–255 内，offset 为 `(v * 256 + u) * 8`；超范围返回不支持，保留非 16 倍数值的精度。

| 属性 | 来源 | divisor |
| --- | --- | ---: |
| 临时网格 UV1、普通 UV2 | 实例 overlay／light 参数记录 | 1 |
| 世界网格 UV1 | 几何 | 0 |
| 世界 UNIFORM UV2 | 实例 light 参数记录 | 1 |
| 固定发光 UV2 | 几何固定值 | 0 |
| 世界 MUTABLE UV2 | 独立的 4 B/顶点光照流 | 0 |

每个 Part 保存实际格式、buffer ID／代次、属性布局和 offset。新 Part、重新上传、池复用、格式变化及参数表重建均重新挂载；更新 pointer 时核对 ARRAY_BUFFER，必要时重绑。SECTION 合并相邻／重叠脏光照范围，在可见 draw 前上传，自发光范围保持固定值。

后端检查 IPointer 与 core／ARB divisor 的版本、扩展和函数指针。普通 draw 配合 divisor=1 读取 instance 0，仍为每实例一次 draw。

## 生命周期与故障

所有操作在渲染线程执行。资源／格式／GL context 或世界失效时清理对应任务、缓存和命令。临时路径首次 RuntimeException 记录并禁用 owner，后续返回 UNSUPPORTED，生命周期重置后恢复；参数表分配故障停用后端。可能已部分绘制的本次调用仍由 VBO 接管，避免重复绘制；清理失败附加到原异常并向上传播。

## 验证依据与边界

| 依据 | 保留结论 |
| --- | --- |
| 1.21.1 的 2.6.0 自动检查 | 129 项 JUnit 测试通过；隐藏 GL 像素读回覆盖多 VAO、变化 light／overlay、固定发光、属性重排与 padding、格式／参数表重建，以及实际 512 实例／200 键缓存上传、取消与故障禁用 |
| 1.20.1 源版本的 2.5.26 自动检查 | 115 项测试通过；512 实例／200 键准备与驻留回归、隐藏 GL 像素读回覆盖多 VAO、变化 light／overlay、固定发光、格式／参数表重建及故障禁用 |
| 1.20.1 的 2026-10-06 桌面游戏长测 | RTX 4060 Laptop／NVIDIA 591.74，每模式 12000 帧；128 次调用的缓冲＋缓存方案相对 I2I，单 Part CPU 中位数增加 3.69%，234 Part 荧光场景增加 1.81% |

长测来自 1.20.1 生产绑定保护加入前的虚拟模型场景，不能代表 1.21.1、现行完整 TaCZ pass 或移动设备；GPU 查询区间含提交空闲。详细实验输出由 `build/`、`run/benchmark-results/` 保存。

世界组不处理透明排序和 shadow；首次准备／策略切换有预热窗口，SECTION 换版前可能保留旧成员快照。真实 TaCZ 实体／shadow 批量接入及移动设备画面验证尚未完成。
