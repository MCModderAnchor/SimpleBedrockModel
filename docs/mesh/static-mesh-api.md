# 即时与批量静态网格使用

类型位于 `com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh`，捕获接口位于 `.capture`。缓存、上传与属性设计见 [最终设计](README.md)。

## 调用入口

即时绘制与批量提交共用缓存。`owner` 用于隔离资源及故障；key 包含影响几何的模型、材质、资源修订、LOD、视角固定变换及外观状态，位置、运行时姿势、light／overlay 独立传入。

```java
ResourceLocation owner = ResourceLocation.fromNamespaceAndPath("mymod", "static_items");
MeshDrawResult result = StaticMeshRenderer.tryDraw(
        owner, key, previousKey, provider, pose, light, overlay,
        Duration.ofSeconds(30));
```

阶段所有者需要集中提交时：

```java
try (StaticMeshBufferSource batch = StaticMeshRenderer.openBatch(
        owner, StaticMeshRenderer.BatchOrder.MATERIAL, pass::isCurrent)) {
    MeshSubmitResult result = batch.submit(key, previousKey, provider,
            pose, light, overlay, Duration.ofSeconds(30));
    // hasMesh() 为 true 时，按 usedPrevious() 选择匹配的动态效果。
    batch.endBatch();
}
```

| 结果 | 含义 |
| --- | --- |
| DRAWN／QUEUED | 目标网格已绘制／入队 |
| DRAWN_PREVIOUS／QUEUED_PREVIOUS | 使用兼容且完整就绪的 previous |
| PENDING | 本次调用由 VBO 接管，等待准备；不重复走旧绘制 |
| UNSUPPORTED | 使用调用方的既有绘制路径 |

previousKey 由调用方保证兼容。批次复制完整 modelView 和方向光逆线性变换，保存 light／overlay 并持有网格引用，提交后可弹出或复用 PoseStack。

## 排序与阶段边界

SUBMISSION 保持调用及 Part 顺序，只合并相邻同材质；MATERIAL 按实际 RenderType 和网格分组，每组共享材质设置与连续 VBO 绑定，仍逐实例 draw。顺序敏感材质由调用方安排顺序及边界。

`flush()` 排空并继续接受提交；`endBatch()` 最后排空后关闭；`close()`／`discard()` 幂等取消待绘制命令。flush／endBatch 返回本次排空的增量统计。

阶段所有者提供 `isCurrent`，在 framebuffer、投影、方向光、共享 shader 参数或光影上下文变化前协调原版缓冲与网格排空。世界组不能代替物品／shadow 阶段队列。

## 几何与回退

provider 在本次调用的预算内同步捕获，资源暂不可用返回 false。`GeometryCollector` 支持 NEW_ENTITY 语义的 QUADS／TRIANGLES；普通 UV2 写 0，固定发光写非零值。混合时使用 `collector.buffer(ordinary, emissive, mode)` 分流，同一图元不能混用语义。

法线保留局部空间，实例矩阵支持旋转和统一缩放；非均匀缩放在捕获期处理并体现在 key 中。资源重载后通过资源 ID 重新取得模型，避免长期持有旧资源。

外观解析前可调用 `supportsInstanceAttributes(light, overlay)` 和 `isOwnerEnabled(owner)`；明确不支持时保持 UNSUPPORTED。关闭某功能使用 `invalidateOwner(owner)`。`preparationStats()` 提供准备、上传、驻留及捕获统计。

1.20.1 的 TaCZ 接入使用 `tacz:external_item_guns`，在第三人称右手、GROUND 和世界内 FIXED 即时绘制，展示枪使用 [世界渲染组](world-mesh-rendering.md)。这些下游接入代码不随 SBM 库移植自动更新；1.21.1 接入方需要迁移到新的包和入口，并按实际选中 LOD 生成动态效果。实体／BER／shadow 批次各有独立 scope，源版本的批量接入尚未实施。

## 构建与检查

```powershell
./gradlew build --offline
./gradlew publishToMavenLocal --offline
./tools/run_immediate_integer_attributes_gl_check.ps1 -JavaHome $env:JAVA_HOME
```

GL 工具要求 JDK 21，使用已有 `build/moddev/clientLegacyClasspath.txt` 和 NeoForge 的合并 JAR；`JAVA_HOME` 未指向 JDK 21 时通过 `-JavaHome` 传入其目录。该工具属于 Git 忽略的本地开发文件，发布源码包不包含它。Maven Local 坐标为 `com.github.mcmodderanchor:simplebedrockmodel:2.6.0-neoforge-mc1.21.1`。

开发客户端的最小画面／性能对照：

```text
/sbmmesh batch view immediate same 128
/sbmmesh batch view batched same 128
/sbmmesh batch start all 128 120 3
/sbmmesh batch stop
/sbmmesh attrib view_glow production 24
/sbmmesh attrib glow 128 1200 10
/sbmmesh attrib stop
```

两种测试不能同时运行；保持视角、画质与世界负载不变，先等几何就绪。结果在游戏目录的 `benchmark-results/`；CPU 包含实际提交／排空，GPU elapsed 不等于纯 shader 时间。`/sbmmesh` 仅用于开发环境。
