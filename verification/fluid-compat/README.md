# KC 茶壶与 FluidCore 放置边界回归

此问题的修复位于农夫乐事的放置监听器：原守卫递归读取茶壶的 `fluid` 字段并取消所有 CE 放置。新版按待放置目标的真实 FluidCore 行为判断归属，森罗茶壶继续使用原数据与控制器；真正的 FluidCore 记录和流体配方转换仍保留保护。

`TeapotPlacementProbe` 是独立验收插件源文件，不自动停服、不修改用户资源包。必须在注册的 Paper/Folia 精确版本入口的隔离配置档串行运行；先读取 `E:/AGENTS.md`、测试服注册表及使用说明。禁止在日常服安装本夹具。

它使用已加载的 CraftEngine、KC、Farmersdelight-Plugin-Pro、FluidCore 和原始 KC 茶壶/炉子定义。插件清单需要声明这些依赖；主类与插件名均为 `TeapotPlacementProbe`，`folia-supported: true`。Paper 插件清单须让探针能访问上述依赖的类路径。

必需启动参数：

```text
-Dcookery.fluid.acceptance=true
-Dcookery.fluid.world=<已有隔离验收世界，不能是 world>
-Dcookery.fluid.craftengine=<精确 CraftEngine plugin version，如 26.9.2 或 26.10-SNAPSHOT>
-Dcookery.fluid.mode=candidate
```

使用 `mode=baseline` 加载旧 FD 制品，期望装水、岩浆、成茶茶壶的真实 CE 尝试放置事件全部被取消，以复现故障。使用 `candidate` 验收新 FD 制品放行茶壶及继续保护真实流体记录。两种模式都要求实际 FD/FluidCore 服务和监听器已启用；每轮使用相同世界、原包与 KC 制品串行切换 FD。

可选 `-Dcookery.fluid.tank=<已加载的原生tank方块ID>`。默认从真实 CE 定义中查找含 FluidCore tank 行为的定义，按行为判断，不依赖方块命名空间。若要覆盖 `farmersdelight:jug` 别名的实际定义，可在隔离配置档的小夹具包单独添加如下定义并指定 `teapotprobe:legacy_jug`；不修改原始包：

```yaml
items:
  teapotprobe:legacy_jug:
    material: iron_block
    data:
      item_name: Processing probe native jug alias
      max_stack_size: 1
    behavior:
      type: block_item
      block:
        behavior:
          type: farmersdelight:jug
          capacity: 8000
        state:
          auto_state: solid
          model: minecraft:block/iron_block
```

候选模式验证水、岩浆及保存完整结果NBT/3份成茶三种茶壶，在普通方块、未点燃和点燃的 KC 炉上，主副手真实 `CustomBlockAttemptPlaceEvent` 分发均放行；每项随后实际创建 CE 茶壶方块、调用真实 KC 的物品恢复与整壶拾取，往返两次比较液体、结果完整NBT及剩余份数。拾取遵循 KC 现有玩法，返回主手。

此外验证三个 FluidCore 原生标记错误类型在 KC 放置时被拒绝、同ID原生tank携带外来数据仍被拒绝、实际 FD 原生tank通用转换拒绝 KC 茶壶与错误类型原生记录且不改变输入/输出/流体、实际 FluidCore 记录写入器拒绝覆盖受保护数据。别名工厂注册也必须存在。

此夹具的 Bukkit/CE 玩家是动态模拟对象：它验证真实监听器及真实方块/控制器恢复拾取，未验证完整 NMS 玩家点击、CE `BlockItemBehavior` 扣物品路径、客户端画面或实际饮用成品。报告会明确标注此范围；不得将通过结果描述为真实玩家端到端验收。

仅保留并改动区块0内 `(8,179..181,8)` 和 `(11,179..181,8)` 的六格方块。遇未知CE状态/方块实体/TileState立即拒绝操作。创建的方块、夹具库存、原始方块数据和临时区块票据均清理恢复后，再异步写入 `plugins/TeapotPlacementProbe/result.json`。外部 runner 等报告后负责正常停服和保存报告。最长1200个owner ticks；不创建完整服、不操作其他进程、不枚举全服实体。

## 2026-10-06 验收结果

[results.json](results.json) 包含精确版本、制品摘要、案例、断言及完整报告/控制台摘要。登记 Paper 26.3 build 140 上，旧 FD 1.2.2 + CE 26.10 通过 131 项故障复现检查；FD 1.2.3-SNAPSHOT 修复候选分别搭配 CE 26.9.2 和 26.10，各通过 514 项回归检查，同一探针和修复制品。十八种茶壶事件放行且每种两次真实控制器往返保持完整数据；清理恢复后报告成功。

FluidCore 为最新本地源码重构的 `0.1.0-SNAPSHOT`，与 Build 3 发布制品字节相同；KC 为已合入 dev 的 `1.3.0-SNAPSHOT`。Farmers 的 841 项单元测试无失败、错误或跳过，公开 CE 26.9.1、实际 26.9.2/26.10 API 编译验证通过。探针覆盖范围仍以以上模拟玩家边界为准。
