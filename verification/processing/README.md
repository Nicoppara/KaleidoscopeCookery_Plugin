# 配方加工验收

本目录验证每条配方的时间/次数、批次冻结和原文件保存。测试插件只用于已登记的独立验收配置档；不得放入日常共享测试服。测试会临时修改指定位置的物理方块、登记测试配方，并由外部运行器停止其启动的进程。

## 覆盖与边界

| 工具 | 验证内容 | 运行方式 |
| ---- | ---- | ---- |
| `ProcessingProbe` | 蒸笼/烤架槽位独立时间、汤锅/茶壶时间、冻结结果、NBT 往返、旧存档与损坏计划、缺失产物保留原料、脏标记 | 实际 CraftEngine 包与控制器，在所属线程直接调用 tick/加工方法 |
| `InteractiveProcessingProbe` | 炒锅翻炒次数、砧板刀数、石磨圈数及事件取消/输入变化 | 由主 Probe 调用；直接驱动控制器与替身玩家，包含独立家具夹具 |
| `ProcessingEditProbe` | 五次真实异步保存、工厂多产物/源顺序/继续编辑、模板覆盖与恢复继承、实际 CE 完整重载、重载后九个真实厨具定义、旧批次与新批次分界、夹具恢复 | 调用实际 `RecipeEditService`，记录保存完成线程并回所属线程断言 |
| `ProcessingPerformanceRunner` + `ProcessingPerformanceProbe.measureAsync` | 基线兼容的独立性能入口，串行比较旧/新 JAR 的控制器 CPU 与分配量 | 每个制品默认 5 轮；每轮 1000 个空闲、200 个活动独立控制器，实际世界时钟预热 100 tick、采样 100 tick |
| `FoliaBoundaryProbe` | 实际石磨家具、异区域牛/驴拒绝访问、未加载与异区域落点防护、动物移动结果提交及生命周期 | 在真正 Folia 上运行；移动 future 的成功/失败/迟到完成受控注入，实际成功传送不在覆盖范围 |

功能与性能夹具中的控制器多数通过反射创建，与常规方块实体调度队列分离；部分测试临时布置实际热源方块。功能报告中的 N 次 tick 是**直接调用的逻辑进度**。性能入口按真实世界时钟在所属线程逐 tick 采样，但被测负载仍是独立控制器，不能换算为多人在线 TPS、真实放置 1200 台厨具的世界调度成本、客户端 GUI 或动画/网络包验收。

`FoliaBoundaryProbe` 使用实际注册并放置的石磨，以及另一区域的真实牛、带箱驴，验证拒绝跨区域读取、保留位置/库存/原料等防护。其传送完成测试注入受控 future，验证只在 owner 消费确认结果、失败不提交加工、卸载/重载后迟到结果失效；报告明确 `real_teleport_success_tested: false`。区块强制加载票据的查询、设置与恢复在 global scheduler 执行，方块与控制器操作回所属区域。Folia 必须在对应已登记核心单独验收，Paper 的所属线程断言不能替代它。

单元测试执行 CE 公开 API 中的真实模板合并算法。其公开制品缺少私有 SNBT 解析器，因此 `src/test` 提供仅支持字面默认值的窄适配器；复杂 SNBT 不在该适配器覆盖范围，也不打包进生产 JAR。实际服务器的编辑与重载测试使用完整 CraftEngine 运行库。

## 注册运行库与配置档

开始前读取 `E:\AGENTS.md`、`E:\MinecraftTestServers\registry.json`、`E:\MinecraftTestServers\使用说明.txt`，检查进程、端口及登记用途。当前 Paper 26.3 验收复用登记 ID `8ce1252842a1`：

- 运行库：`E:\出售的插件\KaleidoscopeCookery_Plugin\build\advancement-verification\server`。
- 配置档：`E:\MinecraftTestServers\config-profiles\paper-26.3\jade-block-identity`；名称沿用先前的验收入口。
- 串行插件目录：`plugins-processing-2692`、`plugins-processing-2610`；每次仅选一个 CraftEngine 版本和一个本插件制品。
- 独立世界：`cebridge-identity`；端口 25788/25789；原世界、原插件配置和日常共享入口不被覆盖。
- 共用同一登记运行库的根 `paper.jar`、补丁版本、libraries/cache 链接。仅准备配置、依赖配置档和夹具，**不复制完整服务器，也不重复下载整套核心**。替换 JAR 使用新文件后替换路径。

Folia 配置档复用登记 ID `d063b0e22d51` 的 **Folia 26.2** 不可变运行库，入口为 `E:\MinecraftTestServers\config-profiles\folia-26.2\cookery-processing`；世界 `processing-folia`，端口 25808/25809。核心精确版本与 Paper 26.3 分开，资源夹具仍与上述专用档串行共用，运行前确认前一轮编辑已恢复初始文件。

在其他机器上用注册表提供的对应核心/精确版本入口替代这些路径。共享环境正在使用时等待空闲，不停止他人的进程。

## 复现步骤

1. 按项目构建说明准备 JDK、NMS 和 CE 依赖，构建候选插件并执行 `test shadowJar`。当前本地离线构建使用 `build/ce-runtime.init.gradle` 调整已安装的 NMS 依赖，该文件属于本地辅助资料。
2. 在上述配置档准备原 Cookery 资源包，并仅增加下节的 `configuration/recipe/processing_probe.yml`。备份上一次夹具文件；**每轮恢复这一个夹具的初始内容**，因为编辑 Probe 会实际保存修改。不要重置整个世界、原资源包或其他数据。
3. 使用 Java 25 编译本目录全部 Java 源。classpath 包含登记运行库的补丁核心与 libraries、选定完整 CE JAR 及其 libs/proxy、本次 Cookery JAR；类输出放在 `build/processing-probe-classes`。不要仅使用 CE 公开 API 运行服务器验收。
4. 将全部 Probe/辅助类打包为 `ProcessingProbe.jar`，另将 `ProcessingEditProbe` 及其内部类打包为 `ProcessingEditProbe.jar`。两者的 `plugin.yml` 分别以同名类为 `main`，`api-version: "1.21"`、`folia-supported: true`、`depend: [KaleidoscopeCookeryPlugin]`。
5. 在配置档目录启动**登记运行库的绝对 `paper.jar` 路径**，通过 `--plugins` 选择对应串行插件目录，提供下列系统属性。等待正常启动及两份 JSON 报告；编辑 Probe 自行触发完整 CE 重载。运行器只停止其创建的进程。

```text
-Dcookery.processing.acceptance=true
-Dcookery.processing.world=cebridge-identity
-Dcookery.processing.craftengine=26.9.2
-Dcookery.processing.fixture=<配置档>/resources-processing/kaleidoscopecookery/configuration/recipe/processing_probe.yml
```

CE 26.10 的实际依赖版本为 `26.10-SNAPSHOT`，相应属性必须使用这个精确字符串。记录完整 JAR 的 SHA256，不能用同名 SNAPSHOT 代表相同制品。

当前机器已有本地辅助脚本，完成资源/夹具准备且环境空闲时可使用：

```powershell
python build/prepare-processing-profile.py
python build/run-processing-profile.py 26.9.2
# 备份并恢复专用 processing_probe.yml 初始夹具后，再运行另一依赖配置档
python build/run-processing-profile.py 26.10
```

这些脚本在 `build` 中，未作为仓库运行工具分发；新 checkout 应按以上步骤组装配置档和 Probe，不将缺少脚本解释为可以复制完整测试服。Folia 的本地准备脚本是 `build/prepare-processing-folia.py`，同样仅组装配置档并复用登记运行库。

## 独立性能比较

性能使用 `ProcessingPerformanceRunner`，单独编译及打包它和 `ProcessingPerformanceProbe*.class`，`plugin.yml` 的 `main` 指向 `ProcessingPerformanceRunner`，沿用验收标记、世界、精确 CE 属性，并设 `-Dcookery.processing.repeats=5`。不要在基线制品上加载依赖新 `CookingPlan` API 的功能 Probe；独立入口可对原 1.2.3 基线编译和运行。停用编辑、功能和边界 Probe，保持核心、CE、资源夹具、JVM 参数及负载一致，串行轮换插件制品。

每个制品执行 5 轮；每轮创建 1000 个空闲、200 个活动控制器，逐个验证活动进度，实际世界时钟预热 100 tick、采样 100 tick。重复世界时刻的回调会跳过，报告保留 `world_game_time_samples`。完成后在所属区域恢复临时物理方块，再由 global scheduler 恢复区块票据。旧茶壶按 23 tick 批量推进，报告记录其实际进度，不假设新旧每帧进度相同。

报告的 CPU、线程分配量和帧耗时只包围控制器调用，排除构造、预热、调度等待、清理和报告构建。任务统计仅覆盖 Bukkit 可见的 CE/Cookery/Probe pending tasks，未统计 CE 内部 ticker 条目或 Folia scheduler 任务；没有客户端，`packet_count` 为 `null`，后台线程分配量、跨区域动物运动和真人网络负载也未测量。每轮 median/p95/p99 的最终汇总是**各轮指标的中位数**，并非将 500 个样本混合后重新计算的分位数。

独立性能入口中，首轮基线与候选各五次 `thread_cpu_nanos` 均记录为 0，后续轮换偶有粗粒度跳变；当前 Windows/JDK 计时分辨率无法可靠量化这些短采样区间，不能据此判断实际 CPU 成本。最终性能比较使用控制器调用的墙钟耗时和线程分配量，并保留这个 CPU 测量限制。

源码中保留的同步 `measure`（500 轮预热/400 轮采样）是早期辅助路径；本次最终比较以独立入口的 `measureAsync` 报告为准。本地 `build/prepare-processing-performance.py` 准备 `plugins-performance-baseline`/`plugins-performance-candidate` 两个串行插件档，运行库仍为同一登记 Paper 26.3，未复制完整服。

## 编辑夹具初始内容

以下内容只写到专用验收资源中的 `processing_probe.yml`。三个实例必须保持 first/middle/last 顺序且初始不设时间；模板值必须为 31 tick。

```yaml
templates:
  kaleidoscopecookery:processing_recipe_template:
    require: minecraft:ghast_tear
    result: minecraft:baked_potato
    cook: steamer
    cooking_time: 31
accurate_foods:
  kaleidoscopecookery:processing_template:
    template: kaleidoscopecookery:processing_recipe_template
config_factory#processing_fixture:
  instances:
    - id: first
      input: minecraft:heart_of_the_sea
    - id: middle
      input: minecraft:nautilus_shell
    - id: last
      input: minecraft:fire_charge
  blueprint:
    accurate_foods:
      kaleidoscopecookery:processing_${id}:
        require: "${input}"
        result: minecraft:baked_potato
        cook: steamer
    items:
      kaleidoscopecookery:processing_display_${id}:
        material: paper
        data:
          item_name: "${id}"
```

编辑 Probe 依次设置 middle=47、first=13、last=19，设置模板覆盖为 57，再删除局部覆盖回到 31。它检查三个额外展示物品仍生成、源文件仍可编辑、实际重载读回保存结果，以及已经冻结的计划保持旧值。

## 报告与验收状态

功能结果写入选定插件目录下的 `ProcessingProbe/processing-result.json` 和 `ProcessingEditProbe/processing-edit-result.json`，独立性能入口写入其 data folder 的 `result.json`。外部运行器归档到配置档 `reports`，同时记录控制台、进程退出状态和制品 SHA256。核对功能报告 `passed: true`、性能报告 `success: true`、`error: null`、实际核心/CE 版本及检查列表；Junit 报告还应检查 `skipped`，不能仅看命令退出码。

2026-10-06 冻结候选制品 SHA256：

```text
673450ce636f205dd39cbe6b495175d5b5edf9dc45338128c5569322dd08a2c7
```

单元测试 **124 项通过，失败/错误/跳过均为 0**。已读取并核对以下同一候选制品的真实服务器报告：

| 核心与依赖 | 功能 / 异步编辑 / 边界检查 | 报告（对应配置档的 `reports` 下） |
| ---- | ---- | ---- |
| Paper 26.3 build 140 + CE 26.9.2 | 118 / 92 / —，均通过 | `20261006T044937Z-processing-26.9.2.json` |
| Paper 26.3 build 140 + CE 26.10-SNAPSHOT | 118 / 92 / —，均通过 | `20261006T045111Z-processing-26.10.json` |
| Folia 26.2 build 7 + CE 26.10-SNAPSHOT | 118 / 92 / 69，均通过 | `20261006T050216Z-functional-26.10.json` |

两轮均核对成功 CE 重载后 first/middle/last 的原文件顺序、三个独立展示物品、九个真实厨具定义（蒸笼、烤架、高汤锅、茶壶、炒锅、砧板、灶台、石磨及其地面定义），五次保存均在异步线程完成，`restore_completed: true`，进程退出码为 0。CE 完整制品摘要分别为 `19535f1987e8a27ebe8c6d811e7deae9a1f05dbd3ef3359435aff5a3d819e0f9` 和 `46ebe45f31f3e3f0965179a85cb1f308d8729f53281d6c64f4ef2af5c23d99f6`。

Folia 报告与上述制品摘要一致，功能、异步编辑及边界报告均 `passed: true`、`error: null`，编辑夹具恢复完成，进程退出码为 0。边界报告确认实际异区域牛与带箱驴已激活（`actual_foreign_entities`、`remote_entities_confirmed` 均为 `true`），并在写报告前清理已知动物、家具、临时方块和 global 区块票据（`cleanup_completed_before_report: true`）。移动结果仍为受控完成测试，`real_teleport_success_tested: false`。

### 最终独立性能比较

完整机器可读记录见 [results.json](results.json)。同一 Paper 26.3 / CE 26.9.2 / JVM 配置按基线→候选→候选→基线运行，报告依次为 `20261006T045724Z`、`20261006T045927Z`、`20261006T050357Z`、`20261006T050611Z`（文件名均以 `-performance-26.9.2.json` 结尾）。每份报告含五轮，两种制品各合并十轮、1000 个采样帧；所有活动控制器的进度检查均通过。

四份报告共用性能 Probe SHA256 `f12b36f6250d6b4a1e064e38e3f8bd3110942f6a05b14467dad5a29e03b00c23`；基线插件 SHA256 为 `6b08c9a920a018092d222a74837e928d685a94bc32ded84524a66db5b49ae7ac`，两轮候选均为上面的冻结制品。

| 指标（每帧 1000 空闲 + 200 活动控制器） | 基线 | 候选 | 变化 |
| ---- | ----: | ----: | ----: |
| 各轮平均耗时的中位数，μs | 190.7795 | 190.5830 | −0.103% |
| 各轮耗时中位数的中位数，μs | 172.35 | 180.15 | +4.526% |
| 各轮 p95 的中位数，μs | 295.50 | 269.65 | −8.748% |
| 各轮 p99 的中位数，μs | 437.85 | 323.25 | −26.173% |
| 各轮平均分配量的中位数，byte | 7812.6 | 7748.6 | −0.819% |
| 全部采样帧的算术平均耗时，μs | 188.7486 | 193.9372 | +2.749% |

指标变化方向不同，轮次波动较大：各轮平均耗时范围为基线 135.294–231.999 μs、候选 163.109–229.945 μs；各轮耗时中位数范围分别为 124.9–187.7 μs、153.6–225.0 μs。二十轮的前后 Bukkit 可见任务快照均为 CE 11、Cookery 0、Probe 0；这只覆盖上述快照范围。

本次计时路径负载未观察到超出轮次波动的明确持续退化；不能据此证明全服 TPS 或零成本，CPU 和发包仍未量化。较早制品或同步辅助性能样本不参与这个最终比较。
