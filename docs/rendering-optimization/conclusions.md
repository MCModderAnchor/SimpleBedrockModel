# SBM 渲染优化当前结论

截至 2026 年 11 月 24 日，现有同步粒子路径可作为射弹尾迹和普通爆炸的交付基线。需要优化大批次时，无光影场景的实例化 GPU 批次耗时最低。自写 compute 预展开已在 Oculus 的三个光影包中完成固定 UV 粒子基准。[完整测量](measurements.md) · [试验方案](experiments.md)

## 常规特效与异步

4,096 个粒子时，Embeddium 的渲染 CPU P50 约 0.27 ms/帧，实例化和自写 compute 开发原型约 0.10 ms/帧。同规模的带 Molang 烟雾更新约 0.69 ms/tick，在 120 FPS 下摊到每帧约 0.12 ms，执行更新的帧承担当次 tick 的完整耗时。数据来自固定存活数量、1280×720、无光影的场景，支持以几百到几千个并发存活粒子为量级的常规特效先用同步路径。[GPU 四路测量](measurements.md#自写-compute-预展开) · [烟雾 tick 测量](measurements.md#带-molang-的烟雾粒子-tick)

尾迹的稳态存活数约为同时发射的尾迹数乘以每秒发射数，再乘以平均粒子寿命。例如假设 20 条尾迹、每条每秒 20 个、寿命 1.5 秒，稳态约 600 个。仓库的爆炸样例四个子发射器合计配置 181 个粒子。爆炸集中生成的 tick 峰值和近屏幕烟雾叠层的 GPU 成本由真实特效场景判断。[爆炸样例](../../src/main/resources/assets/example/particle_definitions/bomb_explosion.particle.json)

暂缓异步改造。常规负载下可移出的 CPU 打包开销有限，worker 方案还要构建快照、交接缓冲并同步结果。若真实特效的帧耗时显示打包持续占用明显预算，先验证从稳定快照并行打包。粒子更新继续使用原线程处理共享 Molang 上下文、世界查询和事件。[异步试验边界](experiments.md#光影与真实特效验证)

## 渲染路径取舍

原先对 CPU 侧开销的判断成立。12,000 个小粒子的无上限对照中，Embeddium 快速写入把四角展开和写入从 2.653 降到 0.771 ms/帧。两条路径共用插值、朝向、自旋和四角计算，这个差值体现的是更换写入路径的收益。96,000 粒子的 CPU 栈进一步显示，Embeddium 可归因样本中约 43% 在四角准备、15% 在顶点写入。部分原生三角函数样本缺少 Java 调用方，比例以已归因样本为分母。[无上限基线](measurements.md#解除帧率上限后的复测) · [CPU 栈](measurements.md#大批次-cpu-调用栈采样)

12,000 个小粒子的四路同场试验中，实例化和自写 compute 的 CPU 准备加提交分别为 0.274 和 0.290 ms/帧，GPU 批次查询为 0.020 和 0.045 ms/帧。两条路径都只上传 48 B/粒子。自写 compute 还要写出标准顶点缓冲供原粒子 shader 读取。复用 AR 内核需要先在 CPU 计算矩阵、上传 140 B/粒子，在 96,000 小粒子时 CPU 耗时已接近 Embeddium，GPU 批次更长。AR 路径保留为预展开对照。[四路实测](measurements.md#自写-compute-预展开) · [大批次数据](measurements.md#2400096000-粒子单批压力测试)

## 光影兼容

正式后端通过客户端配置 `config/simplebedrockmodel-world-particles-client.toml` 中的 `compute = true` 启用。OpenGL 4.5 下，六种世界粒子材质共用 `ROTATE_XYZ` 的展开路径，并沿用各自的混合和深度写入状态。粒子 UV 取 tick 更新后的值。Forge 1.20.1 在 RenderType 的 `end()` 提交，NeoForge 1.21.1 在 `ParticleEngine` 的 `BufferBuilder.build()` 提交点接入。两版都沿用引擎剔除，并在遇到不支持的粒子时按原顺序回退整批。[Forge 后端](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/WorldParticleCompute.java) · [正式路径复测方式](experiments.md#复测方式)

自写 compute 保留光影包的粒子着色接口。它只生成标准 `PARTICLE` 顶点，光影包仍可选择该阶段的程序并配置雾、光照、混合和 framebuffer。实例化原型用自写顶点 shader 读取每实例属性，因此接入光影包时要另外处理顶点阶段的接口。[前期兼容调研](research.md#madparticle-的-iris-framebuffer-接入) · [Iris core shader 兼容说明](https://github.com/IrisShaders/Iris/blob/26.1/docs/development/compatibility/core-shaders.md)

原型在每个粒子阶段沿用 `RenderSystem.getShader()`。Oculus 1.7.0 下，BSL 和 Complementary 的第二个阶段选中 `particles_trans`，此时 `GameRenderer.getParticleShader()` 仍返回 `particles`。Derivative 只绘制一个阶段。12,000 个固定 UV 小粒子时，三个光影包的 CPU 准备加提交均降低约 67%，GPU 批次查询降低约 20–27%。关闭 BSL 的 TAA、云层和光束后，固定 UV 的两种尺寸及翻页烟雾的第 14 tick 截图逐像素一致。翻页烟雾的第 4 tick 存在少量局部差异。真实枪械特效、锚点和其他 billboard 朝向仍待验证。[当前 draw 入口](../../src/main/java/example/client/particle/ParticleGpuPrototype.java#L88) · [Oculus 实测](measurements.md#oculus-170-光影对照2026-11-24) · [翻页测量](measurements.md#oculus-下的翻页烟雾)

## 大批次 CPU 优化

96,000 个小粒子时，实例化和自写 compute 都把渲染 CPU 耗时降到约 3.5 ms/帧。两条路径的 CPU 栈中约 60–62% 落在引擎遍历、分发和视锥检查。相同采样窗口内，粒子更新占整个粒子 CPU 耗时的 19–21%，但占单独客户端 tick 时间的 92–93%。[大批次数据](measurements.md#2400096000-粒子单批压力测试) · [CPU 栈](measurements.md#大批次-cpu-调用栈采样)

全部可见的 96,000 粒子场景中，省略逐粒子视锥检查三轮都节省 0.45–0.50 ms/帧。按固定字段偏移写 48 B 记录也三轮都有收益，两项合用时 CPU 准备加提交从 3.504 降到 2.667 ms/帧。实际接入可对完全在视锥内的空间组省略逐粒子检查，对边界组保留原检查，并计入分组及包围盒维护成本。在同样跳过逐粒子剔除时，换成直接循环没有稳定收益。[CPU 隔离实测](measurements.md#cpu-热点隔离实验2026-11-24)

## 第一人称渲染

本文的数据支持世界粒子路径的取舍。第一人称枪体的逐刚性块 VBO、现有 AR 和派生 shader 有各自的提交成本及模板窗口约束，仍按常规枪和复杂枪两个场景验证。[第一人称试验方案](experiments.md#第一人称枪体试验方案)
