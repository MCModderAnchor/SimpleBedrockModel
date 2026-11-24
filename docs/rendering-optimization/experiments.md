# SBM 渲染优化试验方案

世界粒子试验对比 CPU 展开、实例化和 GPU 预展开。四条路径由同一个 `ParticleEngine` 管理粒子生命周期、可见性和遍历顺序。第一人称枪体单独验证，因为瞄具模板、手部投影和材质 flush 决定了它的批次边界。[前期调研](research.md) · [测量结果](measurements.md)

## 世界粒子基准

开发基准使用固定相机、固定存活粒子数、固定纹理和固定 UV 的 `ROTATE_XYZ` 世界粒子。默认材质为 `PARTICLES_ALPHA`，几条路径共用材质的混合和深度写入，每批一次 draw。测试场景是 Forge 1.20.1 超平坦世界，分辨率为 1280×720，关闭 VSync。指定光影包的对照加载 Oculus，其余基准关闭 Oculus。大粒子场景在相同位置把 billboard 边长放大十倍。[基准实现](../../src/main/java/example/client/particle/ExampleWorldParticleBenchmark.java) · [基准启动配置](../../build.gradle)

| 试验 | 对照与执行方式 |
| --- | --- |
| 渲染基线 | 同一客户端切换 `VertexConsumer` 和 Embeddium 快速写入，使用 1,024、4,096、12,000 个小粒子和 12,000 个大粒子。以 120 FPS 上限和无上限各跑三轮。 |
| 烟雾 tick | 固定存活数，对比基础更新、`test_basic_smoke` 和用等价 Java 除法替换颜色渐变进度表达式的烟雾。烟雾保留翻页 UV、运动和颜色更新。预先生成粒子、延长寿命并错开初始年龄，采样稳定存活期。 |
| GPU 原型 | 对比 Embeddium、实例化、AR compute 和自写 compute，沿用基线的四个场景。各轮轮换提交顺序，并在预热后核对 12,000 粒子的截图。 |
| 大批次压力 | 给基准专用 RenderType 注册容量 96,000 的队列，测试 24,000、48,000、96,000 个小粒子和 96,000 个大粒子，逐帧确认进入渲染的数量。 |
| CPU 调用栈 | 对比四条渲染路径在 24,000 和 96,000 小粒子时的 CPU 栈。使用 Windows async-profiler 的 CPU 事件，采样间隔 2 ms。每组正式窗口约 5 秒，根据 UTC 边界筛选渲染线程样本。 |
| CPU 热点隔离 | 在 24,000 和 96,000 个全部可见的小粒子上，比较实例化基线、跳过逐粒子视锥检查、按地址写记录、两项合用，以及跳过检查后用直接循环替代引擎逐粒子分发。五条路径共用 shader、48 B 布局和粒子 tick。 |
| Oculus 光影 | 对比 Embeddium 与自写 compute。每个光影包测三轮，覆盖 4,096 小粒子、12,000 小粒子和 12,000 大粒子。按帧合计各绘制阶段的 CPU 和 GPU 时间。在合成后截取每轮 12,000 粒子的最终画面。 |
| Oculus 翻页烟雾 | 同时指定光影包和烟雾模式。两条路径都更新原烟雾组件，测试 4,096 和 12,000 粒子。第 4、14 粒子 tick 在相同插值端点截图并记录首粒子的 UV。正式计时使用正常帧插值。 |

CPU 准备覆盖批次遍历、可见性检查、解算和打包。CPU 提交单独计量。GPU `GL_TIME_ELAPSED` 查询覆盖整个批次，包含上传、compute 和 draw，也可能包含 CPU 提交期间的 GPU 空闲。粒子更新以 ms/tick 计，使用同一采样窗口内的累计时间比较 tick 与渲染。大批次轮和 CPU 热点隔离轮关闭 profiler，CPU 栈的比例来自采样次数。[测量表与输出](measurements.md)

## GPU 路径的输入与输出

| 路径 | CPU 侧和每粒子上传量 | GPU 侧 |
| --- | --- | --- |
| Embeddium | CPU 展开四角，写入 4 × 28 B 顶点 | 使用当前粒子 shader 绘制。 |
| 实例化 | 写入 48 B 的中心、自旋、尺寸、UV、颜色和光照 | `glDrawElementsInstanced` 在自写顶点 shader 中展开四角，复用原粒子片元 shader。 |
| AR compute | CPU 计算局部 quad 到相机相对世界坐标的矩阵，写入 112 B 变换记录和 28 B 网格记录 | 复用 AR 8179092 的 `pos_color_tex_light_mesh_uploading_shader` 和固定 UV 共享 quad。将 AR 的输出字段绑定到粒子 shader。 |
| 自写 compute | 与实例化共用 48 B 输入和打包代码。CPU 插值中心、自旋，并提交整批共享的相机右轴和上轴 | 每个 invocation 展开一个粒子，写出 112 B 的标准 `PARTICLE` 顶点数据。顶点属性 barrier 后复用静态索引和当前粒子 shader。 |

48 B 记录的按地址写入实验使用 `bufferBase + index × 48` 找到记录起点，检查一次记录容量后，用 `MemoryUtil` 在固定字段偏移处写入。顺序写入和按地址写入使用同一堆外布局，上传量相同。直接循环试验每批由 `ParticleEngine` 分发到首粒子一次，再遍历保序列表并调用相同的打包代码，保留存活检查和顺序写入。原版 tick 继续遍历 FIFO。[渲染原型](../../src/main/java/example/client/particle/ParticleGpuPrototype.java) · [实例化 shader](../../src/main/resources/assets/example/shaders/core/world_particle_instanced.vsh) · [自写 compute](../../src/main/resources/assets/example/shaders/core/world_particle_expand.comp)

## 复测方式

在仓库根目录用 JDK 17 和已有 Gradle 缓存运行。脚本创建隔离客户端目录，完成采样后退出并打印结果目录。CPU 录制的输出包含 `cpu.jfr`、正式采样窗口和逐帧、逐 tick CSV。

```powershell
./tools/run_world_particle_benchmark.ps1
./tools/run_world_particle_benchmark.ps1 -Uncapped
./tools/run_world_particle_benchmark.ps1 -SmokeTick -Uncapped
./tools/run_world_particle_benchmark.ps1 -GpuPrototypes -Uncapped
./tools/run_world_particle_benchmark.ps1 -LargeBatches -Uncapped
./tools/run_world_particle_benchmark.ps1 -CpuIsolation -Uncapped
./tools/run_world_particle_benchmark.ps1 -Uncapped -CpuProfilerAgent '<IntelliJ 安装路径>/lib/async-profiler/amd64/libasyncProfiler.dll'
./tools/run_world_particle_benchmark.ps1 -Uncapped -ShaderPack 'run/shaderpacks/BSL_v8.4.01.2.zip'
./tools/run_world_particle_benchmark.ps1 -SmokeTick -Uncapped -ShaderPack 'run/shaderpacks/BSL_v8.4.01.2.zip' -ShaderPackOptions 'build/world-particle-benchmark/bsl-static-visual.properties'
```

`-ShaderPack` 接受 ZIP 或解压后的光影包目录。脚本把光影包与可选的 `-ShaderPackOptions` 配置文件复制到隔离客户端。`shader-selection.csv` 记录各阶段的选中程序和 `GameRenderer.getParticleShader()` 的返回值。

`-ProductionCompute` 使用正式的 `SnowStormParticle` 批次入口对照 Embeddium。加上 `-SmokeTick` 时，还会在同一材质和纹理的队列中插入一个 `ROTATE_Y` 粒子，检查整个批次按原顺序回放。命令可以与 `-ShaderPack` 合用。

翻页截图的 BSL 配置为 `TAA=false`、`CLOUDS=0` 和 `LIGHT_SHAFT=false`，每个选项在配置文件中独占一行。

[基准运行脚本](../../tools/run_world_particle_benchmark.ps1) · [JetBrains profiler 说明](https://www.jetbrains.com/help/idea/custom-profiler-configurations.html)

## 光影与真实特效验证

光影对照使用同一场景、材质、视角、光影包及预设，比较现有粒子路径和自写 compute。Compute 输出标准 `PARTICLE` 数据，最终 draw 留在原粒子阶段并使用 `RenderSystem.getShader()`。同一帧若有多个绘制阶段，各阶段都复用其选中的程序。画面先比较两条路径，再用 Embeddium 跨轮截图衡量光影动画本身的变化。翻页 UV 已用 BSL 验证，锚点跟随、特殊 FOV 和其他 billboard 朝向分别验证。[原型 draw 入口](../../src/main/java/example/client/particle/ParticleGpuPrototype.java#L88) · [翻页测量](measurements.md#oculus-下的翻页烟雾)

其他世界 billboard 朝向可共用标准顶点输出。`ROTATE_Y` 使用每帧共享的相机轴，`DIRECTION_X/Y/Z` 使用固定轴。`LOOKAT_XYZ/Y` 依赖各粒子的当前位置，`LOOKAT_DIRECTION` 还依赖速度，接入时保留近零速度下使用相机朝向的行为。可先从 CPU 上传两根轴复用当前四角展开，再测量把朝向计算移到 GPU 的收益。材质和纹理相同的粒子可以混用朝向，批次内按原顺序提交。当前世界渲染的 `EMITTER_TRANSFORM_*` 落入相机朝向回退分支。[世界 billboard 朝向](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/SnowStormParticle.java#L403)

真实使用场景以持续的射弹烟雾尾迹、同一时段的多次爆炸和近屏幕烟雾叠层为主。记录峰值存活数、当 tick 生成数、粒子 tick 与事件、CPU 准备和提交、GPU 批次及帧耗时 P95/P99。爆炸生成尖峰和靠近相机的 GPU 覆盖率在此场景单独观察。需要试验异步时，用稳定快照驱动独立 CPU 打包对照，保留原线程的世界访问、事件和共享 Molang 更新。[共享 Molang 上下文](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/runtime/ParticleEmitterInstance.java#L242) · [粒子 tick](../../src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/SnowStormParticle.java#L179)

## 第一人称枪体试验方案

用常规枪和复杂枪各做一组。缓存预热后固定场景和光影预设，对比原绘制、现有 AR、逐刚性块 VBO 和可选派生 shader。记录渲染线程耗时、GPU 时间、实际 draw 数和加速或回退命中。画面对照覆盖换弹显隐、灯光变化、瞄具模板和开关光影。逐块 draw 的矩阵更新和提交成本明显时，再判断派生程序能否抵消每批复制 uniform、上传 SSBO 和查询 GL 状态的开销。[第一人称提交边界](research.md#tacz-第一人称的提交边界)

