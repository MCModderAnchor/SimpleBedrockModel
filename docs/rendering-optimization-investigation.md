# SBM 渲染优化文档

世界粒子的实测范围覆盖 Forge 1.20.1 下的同步基线、实例化、GPU 预展开，以及 Oculus 1.7.0 的三个光影包。第一人称枪体有独立的提交边界和试验方案。

| 文档 | 内容 |
| --- | --- |
| [前期调研](rendering-optimization/research.md) | 动画网格、TaCZ 第一人称、AcceleratedRendering、Iris/Oculus 和 MadParticle 的实现与约束。 |
| [各项测量](rendering-optimization/measurements.md) | 粒子基线、烟雾 tick、四路对照、96,000 粒子压力测试、CPU 采样、隔离实验及 Oculus 光影对照。 |
| [试验方案](rendering-optimization/experiments.md) | 原型结构、测量口径、复测命令、光影与真实特效的验证条件。 |
| [当前结论](rendering-optimization/conclusions.md) | 常规规模是否够用、异步取舍、渲染路径选择及后续验证重点。 |
