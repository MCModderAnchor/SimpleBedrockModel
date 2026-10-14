# 世界网格渲染组使用

类型位于 `com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world`。调用方负责对象有效性、光照采样及几何，库负责遍历、缓存、上传、剔除与绘制；两种策略的组织见 [最终设计](README.md)。

## 登记对象

对象直接实现 `MeshRenderable`，或使用共享 `MeshRenderableAdapter<T>`：

```java
WorldMeshGroup<MyBlockEntity> group = WorldMeshRenderer.createGroup(
        "mymod:decorations", WorldMeshStrategy.SECTION);
MeshRenderableAdapter<MyBlockEntity> adapter = new MeshRenderableAdapter<>() {

    public boolean isValid(MyBlockEntity entity) {
        return !entity.isRemoved() && entity.getLevel() != null;
    }

    public Vec3 origin(MyBlockEntity entity) {
        return Vec3.atLowerCornerOf(entity.getBlockPos());
    }

    public int packedLight(MyBlockEntity entity) {
        return LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos());
    }

    public Object geometryKey(MyBlockEntity entity) {
        return GeometryCollector.blockModelKey(modelId, texture, Direction.NORTH);
    }

    public boolean collectGeometry(MyBlockEntity entity, GeometryCollector collector) {
        return collector.blockModel(modelId, texture, Direction.NORTH);
    }
};
group.track(blockEntity, adapter);
// 对象实现 MeshRenderable 时改用 group.track(blockEntity)。
```

modelId、texture 为接入方的资源 ID；实际朝向及外观变化应在回调中读取并进入 key。`blockModel` 自动处理绑定姿势、局部平移 `(0.5,0,0.5)`、朝向与普通／发光分流。自定义顶点的语义见 [几何接口](static-mesh-api.md#几何与回退)。

| 回调 | 契约 |
| --- | --- |
| isValid | 默认 true；false 自动解除登记 |
| needsUpdate | 默认 true；控制是否重读轻量属性 |
| isVisible | 默认 true；隐藏仍保留对象 |
| origin | 必须提供世界原点 |
| localTransform | 默认恒等；相对 origin 的实例旋转／统一缩放 |
| packedLight | 必须提供当前光照 |
| geometryKey | 必须提供不可变非 null key；相同 key 的完整几何与材质必须等价 |
| collectGeometry | 必须提供；false 丢弃本次结果并限额重试，true 的空几何有效 |

登记按对象引用身份，重复 track 无操作；更换适配器先 untrack 再 track。所有操作和回调在渲染线程执行，track 要求当前存在世界。

几何 key 包含模型、材质、拓扑、骨骼姿势与内部显隐；origin、localTransform、整体显隐和 light 独立返回。同组同 key 共享捕获结果，库从当前有效对象中选择提供者。

## 更新与生命周期

`group.markDirty(object)` 请求重读状态；没有可靠通知时保留 needsUpdate 默认值，有通知时可返回 false 并主动标脏。每个绘制阶段检查有效性；初次登记、标脏及资源失效强制读取属性，其余每客户端 tick 至多检查一次 needsUpdate。只有 key 改变或缓存失效才重新捕获，纯光照变化不重建几何。回调中再次 markDirty 保留到下次遍历。

`untrack` 移除单个对象，`clear` 清空对象，`close` 清空并注销组且可重复调用。markDirty／untrack 对未登记对象无操作；关闭后不能再登记。回调不应创建／关闭组或修改全局渲染配置。

资源重载及 `WorldMeshRenderer.clearCaches()` 保留对象并重建；`setEnabled(false)` 释放缓存、继续检查对象状态，重新开启后重建。世界卸载清空对象，但保留组注册，同一组可在新世界重新 track。

## BER 回退

需要保留普通 BER 绘制时，创建仅支持 INSTANCE 的组：

```java
WorldMeshGroup<MyObject> group = WorldMeshRenderer.createGroup(
        "mymod:instance_only", WorldMeshStrategy.INSTANCE,
        Set.of(WorldMeshStrategy.INSTANCE));
```

在每次主世界 BER 绘制前调用 `claimForWorldDraw(object, expectedGeometryKey)`。true 表示全部 Part 已就绪并在随后 AFTER_BLOCK_ENTITIES 绘制；false 时执行普通 BER。可传第三个参数 previousGeometryKey，允许精确兼容的已就绪旧 LOD。两个 key 均应含资源／外观修订。

首次 claim 后对象使用显式帧级绘制，后续未 claim 的帧不会复用上次决定。SECTION 和 shadow 不使用此入口；世界网格关闭时返回 false。

## 策略与诊断

用户配置优先于调用方默认策略，AUTO 使用默认值。不支持的用户选择回退到组的默认策略，默认值必须在支持集合内。配置位于 `config/simplebedrockmodel-client.toml`：

```toml
[worldMesh]
strategy = "AUTO" # AUTO / INSTANCE / SECTION
```

生产命令修改同一配置，策略变化保留对象并重建缓存：

```text
/sbmrender strategy auto
/sbmrender strategy instance
/sbmrender strategy section
/sbmrender groups
```

`WorldMeshRenderer.stats()`／`group.stats()` 提供全局／组统计，包含对象与缓存数量、策略来源及拒绝原因。

开发环境可对照策略和局部光照：

```text
/sbmmesh stress same 256
/sbmmesh path instance
/sbmmesh path section
/sbmmesh stats detail
/sbmmesh stress light start 20 16
/sbmmesh stress light stop
/sbmmesh stress clear
```

path 会启用绘制层。先等 `waitingGeometry=0,dirty=0,pending=0`，再等至少 120 帧记录数据；只改光照时 builds／submittedVertices 应不变，rangeBytes 反映独立 UV2 上传量。frameMs 是最近最多 120 帧的阶段间隔均值，cpuUs 是本层 CPU 时间。

定时光照 start 的参数为 tick 间隔／每轮数量，默认 20 tick／全部实例；stop 保留当前合成光照，reset 恢复生成时采样值。`/sbmmesh cache clear` 保留对象并失效缓存。测试期间固定镜头与世界负载，资源重载、策略切换后重新预热。
