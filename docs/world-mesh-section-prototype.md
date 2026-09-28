# Section 合批与光照压测

世界网格支持共享几何的逐实例绘制，以及按 `16×16×16` section 合并绘制。
两条路径使用同一 `WorldMeshRenderer`、材质和 `AFTER_BLOCK_ENTITIES` 绘制阶段。
默认仍走逐实例路径，切换命令同时作用于示例方块实体和静态压力测试挂具。

## 游戏内对照

保持镜头不动，依次执行：

```text
/sbmmesh path instance
/sbmmesh stress same 256
/sbmmesh stats detail
/sbmmesh path section
/sbmmesh stats detail
/sbmmesh path instance
/sbmmesh stats detail
```

`path` 会启用世界网格绘制层。切换时保留压测实例的模型、位置、贴图、当前光照及光照压测配置，重新建立所选路径的缓冲。
等待 section 的 `waitingGeometry=0,dirty=0,pending=0`，再至少等 120 帧后记录数据，避免把预热和切换前的帧时间混入比较。
在 `stress models 256`、`stress all 256` 下重复比较，分别观察不同模型与不同材质的影响。
`stress dynamic` 仍走原来的动态对照路径，不受 `path` 改变。

统计关注：

- `draws`：逐实例 draw 与 section/材质批次 draw 的实际差异。
- `frameMs`：最近 120 帧平均间隔；`cpuUs`：世界网格绘制层 CPU 耗时，包含 prepare 和提交，不是 GPU 耗时。
- 渲染组 `section.active`：当前有可绘制 VBO 的批次数；空几何 pass 在收集结束后过滤，不建立批次。
- 渲染组 `section.waitingGeometry/dirty/pending`：等待共享几何的对象数、等待重建与等待上传的批次。
- `section.builds/submittedVertices/failures`：累计重建次数、提交顶点量与失败次数；比较前后增量。
- `section.prepareMicros`：该渲染组最近一次分区维护/重建的 CPU 耗时。

真实示例方块可以直接用 `path auto|instance|section` 切换。检查放置/破坏方块、改变朝向、放置/移除光源、
跨 section 边界和负坐标、离开 128 格后返回、`cache clear`、世界切换和光影切换。
压测实例默认使用生成时采样的光照；可用下述定时光照模式覆盖。真实方块仍从世界采样。

## 实现

- `WorldMeshGroup` 持有逻辑对象，库内 `SectionMeshBatches` 维护分组，批次键为 section 原点、`RenderType`、QUADS/TRIANGLES。
  不要求批次内模型或朝向相同；不同贴图仍属于不同材质。
- 顶点位置使用 section 局部坐标，包围盒使用真实几何的世界范围，支持跨边界模型。
- 增删实例、改变模型/贴图/朝向/位置会将受影响批次标脏。仅光照变化不重建几何。
- 每个渲染组每帧最多重建 4 个批次，约 2 毫秒软预算；脏队列轮转，单个大批次仍可能超过预算。
- 同一批次最多一份 pending；上传成功后替换 active，期间继续绘制旧网格。
  pending 期间继续发生的几何修改保留脏标记，在后续再次重建。
- 共享 CPU 几何由缓存引用计数；批次版本独占 GPU 句柄并交给池回收。空批次立即释放，实例淘汰会更新所属批次。
- 渲染组准备由库统一调度，GPU 句柄由库管理。
- 提交时使用 `MeshLighting.MUTABLE`，实际光照存入独立的 4 B/顶点 UV2 缓冲；几何上传后不因光照变化而改写。

## 局部光照更新

烘焙时以零光照捕获普通顶点，自发光顶点保留固定值，按成员保存普通顶点的连续范围。
对象修改自己的光照并调用 `group.markDirty(object)`，或通过 needsUpdate 回调报告变化；库读取状态后根据版本映射将范围写入 CPU 暂存。
此处零光照是真实黑暗，不是绘制时的实例模板标记。

active 和 pending 各自持有范围映射，只有模型、贴图、朝向、位置与该版几何一致时才更新对应成员。
几何尚在上传时也能暂存新光照；新版本开始显示前会上传最新值。
因此重建期间变更成员顺序、移除成员或变更其几何，不会误用新版范围去写旧 VBO。

库在已上传且可见的网格绘制前，通过 `glBufferSubData` 上传独立 UV2 流的脏范围。
相邻／重叠区间合并，同范围的连续变更只上传最终值；不可见批次暂存到再次可见时处理。
自发光范围不参与普通光照更新。完整几何、材质和 draw 数不因纯光照变化而改变。

## 定时光照压力测试

先生成渲染组并预热，再启动光照变化：

```text
/sbmmesh path section
/sbmmesh stress same 256
/sbmmesh stats detail
/sbmmesh stress light start 20 16
```

等待 `waitingGeometry=0,dirty=0,pending=0` 后记录初始统计。最后一条命令每 20 个客户端 tick 轮转更新 16 个实例。
每个选中实例的天空光与方块光一起在 0～15 间循环，各实例错开相位；这是合成光照，不修改世界光源。

```text
/sbmmesh stress light start 1 1
/sbmmesh stress light start 1 16
/sbmmesh stress light start 1
/sbmmesh stress light stop
/sbmmesh stress light reset
```

- `start [intervalTicks] [batchSize]`：默认间隔 20 tick、每轮全部实例。间隔范围 1～1200，数量范围 1～512；超过渲染组实例数按全部处理。
- `start 1 1` 便于观察单模型局部更新；`start 1 16` 用于部分更新压力；`start 1` 每个客户端 tick 更新全部模型。
- `stop` 停止计时并保留当前合成光照；`reset` 停止计时并恢复生成渲染组时采样的光照。
- 客户端正常约 20 tick/s，游戏暂停时不推进；该频率与渲染帧率无关。
- `path auto|instance|section` 保留计时设置与当前光照，用于相同数据集的 A/B；`same/models/all/dynamic` 都支持光照压测。
  重新生成渲染组、stress clear 或世界切换会停止计时，需要重新 start；资源重载和 cache clear 保留对象与计时器。
  关闭世界网格绘制层也保留状态，重新开启后重建到最新光照。

`stats detail` 的判定方法：

- 预热后只更新光照，渲染组 `section.builds`、`submittedVertices` 和累计 `submits` 应保持不变，`waitingGeometry/dirty/pending` 应保持 0。
- `stress.lightRounds/lightChanges` 表示本次 start 后的更新轮数／实例数；`section.lightChanges` 表示该批处理器累计接到的纯光照变更。
- `rangeUploads/rangeBytes` 是最近绘制阶段实际执行的局部 UV2 上传次数和字节数；详细累计项为 `lightRangeUploads/lightRangeBytes`。
  它们不含首次创建光照流的整块分配，但包含首次给普通顶点设置采样光照的范围上传，因此应在预热后记录基线。
- 没到更新 tick 或网格被剔除时，单帧范围计数可以是 0；比较一段时间内累计值的增量更可靠。
- 每个更新顶点对应 4 B 光照数据。只更新少量模型时，不应上传整块几何；全量变更仍可能覆盖整条光照流。
- `stress.lightUpdateUs` 现在只记录最近一轮 tick 修改对象字段和 markDirty 的耗时，不再包含 CPU 光照范围暂存。
  读取对象状态、比较、暂存光照和实际上传提交都在渲染组遍历阶段，计入 `cpuUs`。整体影响还需结合 `frameMs` 与帧时间波动判断。

切到逐实例路径时，普通网格使用常量 UV2，不产生范围上传；发光图元位于独立材质 pass，
由无方向光的 shader 绘制。动态对照直接将每个实例的新光照传给模型绘制。

额外检查更新中增删真实方块、改变朝向、跨 section 移动，以及移出视野后返回；模型应保留自发光，且不出现光照范围串写。

## 当前边界

- 光照更新按普通顶点范围上传；自定义单材质 pass 若交错写入固定与动态光照，可能产生较多小范围上传。尚未引入合并间隙或上传字节预算。
- 独立光照流额外消耗约 4 B/顶点 GPU 空间和同等 CPU 暂存空间，另有每版实例范围索引。
- 烘焙/提交仍在渲染线程，软预算不是严格的时间或字节上限。
- 初次接管及路径切换需要等待重建和下一帧上传，可能短暂不显示；旧批次只在已有 active 时可保留。
- 批次成员变化在新版本可用前仍显示旧快照，移除的单个成员可能暂留；最后一个成员移除则立即释放。
- 与 INSTANCE 路径一样只捕获绑定姿势，当前接入的是 cutout；不处理透明排序、阴影 pass 或跨组几何合批。
- 分区复制各实例几何，显存通常高于共享 VBO。分区级剔除也可能多画几何。
- 世界卸载清除对象；资源重载和 `cache clear` 仅失效缓存，保留压力渲染组。
- 库内部逐实例路径使用 `MeshLighting.INSTANCE`，section 使用 `MeshLighting.MUTABLE`；调用方不选择这些存储模式。

本次验证采用 Java 编译检查；游戏内兼容性、观感与性能数据需要实机 A/B。

完整 API 与命令说明见 [世界网格渲染组接口](D:/Minecraft/Dev/SimpleBedrockModel/docs/world-mesh-rendering.md)。

默认策略由创建 WorldMeshGroup 的调用方指定。`/sbmrender strategy auto|instance|section` 直接修改用户配置中的策略；
`/sbmmesh path` 是示例侧的同义入口。`/sbmrender groups` 可查看默认值、实际策略与选择来源。

接入方式为对象／适配器登记：方块示例使用 isRemoved 等有效性回调，压测对象直接实现 MeshRenderable；
光照顶点范围由库管理。

调用方提供 geometryKey、位置、光照、显隐和 collectGeometry。
材质及拓扑由 GeometryCollector 的实际输出确定，库再建立 section 批次。

几何捕获由 WorldMeshGroup.capturePending 调度；section 策略拼接已捕获结果。
`section.prepareMicros` 只反映批次准备阶段，共享几何捕获仍包含在全局 `cpuUs` 中。
