# CraftEngine 双版本兼容验证

2026-10-03 验证的森罗插件版本为 **1.2.3**，默认公开 API 编译基线仍是 **26.9.1**。未引入 26.10 专属编译接口，也未改动物品 ID、厨具数据格式或成就存储。

| 运行前置 | 精确构建 | 编译检查 | 同一插件包的运行回归 |
| --- | --- | --- | --- |
| CraftEngine 26.9.2 | `26.9.2` | 通过 | 83 项兼容检查、217 项玩法及成就检查、完整资源包生成通过 |
| CraftEngine 26.10 | `26.10-20260929.192451-4`，插件版本 `26.10-SNAPSHOT` | 通过 | 83 项兼容检查、217 项玩法及成就检查、完整资源包生成通过 |

测试环境：Paper `26.3.build.140-beta`、Java 25.0.2、用户提供的完整森罗物语 CE 包、本地 UltimateAdvancementAPI Pro `2.8.1-pro.2`。默认构建另通过 58 项 JUnit 测试及全部九个 NMS 适配模块编译。

CE JAR 的 SHA-256：

- 26.9.2：`19535f1987e8a27ebe8c6d811e7deae9a1f05dbd3ef3359435aff5a3d819e0f9`
- 26.10：`46ebe45f31f3e3f0965179a85cb1f308d8729f53281d6c64f4ef2af5c23d99f6`

26.10 的结论针对上述测试构建；后续快照或正式版如修改 API，需使用相同流程复验。Minecraft 26.3 的服务器需使用 **1.2.3** 修复蒸笼下落的客户端数据包问题；森罗资源包的模型、贴图和配置无需为此修改。

## 26.3 蒸笼下落的客户端断线

[用户日志](https://mclo.gs/R4IIF3r)报告原版 26.3 客户端解码 `add_transient_block` 时无法识别状态编号 `37767`；用户确认触发操作为 CE 26.10 上的蒸笼下落。两份实际 CE JAR 未包含此新包的转换处理。此前 1.2.2 的服务端回归覆盖了实体下落与食材恢复，未检查这个包的客户端解码，不能据此判断客户端无断线问题。

1.2.3 在玩家出站通道中，只将蒸笼的服务器专用状态转换成该玩家可识别的 CE 外观状态；包的坐标及服务端世界数据保持原样，bundle 里的其他子包保留，启用 CE 自定义方块的模组客户端保留其状态编号。初始化时检测实际新包类型，旧版服务器不安装这个处理器，禁用插件时移除处理器。

`TransientBlockProbe.java` 用实际 26.3 的 `ClientboundAddTransientBlockPacket.STREAM_CODEC` 序列化，再用仅包含原版方块状态的 `IdMapper` 和实际 `ByteBufCodecs.idMapper` 解码。在本地包中，未转换编号为 `35849`，原版状态数为 `35723`：原包重现相同的编号异常，经过插件实际出站处理器的包及 bundle 子包均成功解码。编号会随资源包和服务器版本变化，测试不依赖用户日志里的固定数值。

新增 22 项断言还覆盖原包不被修改、服务端方块不被替换、普通方块及其他自定义方块不受影响、重复安装、卸载和关闭清理。原有蒸笼下落取消、落地恢复食材和成就回归继续通过。

另在隔离的 Paper 1.21.11 + CE 26.9.2 上使用 `LegacyTransientBlockProbe.java` 通过 **8 项旧版处理器检查**：插件能加载，新数据包类型不存在时不绑定、不安装通道处理器，出站对象保持原样并可正常关闭。这套旧版夹具未安装森罗完整包，仅检查处理器关闭路径，不代表完整旧版玩法或客户端验证。

## 实际运行包编译检查

默认发布构建继续使用 `gradle.properties` 的公开 API。使用下面的可选初始化脚本，将编译依赖临时替换为隔离服务器上的实际 CE JAR、内嵌 Proxy 和已初始化的重定位 Adventure 运行库：

```powershell
.\gradlew.bat -I verification/craftengine/api-runtime.init.gradle `
  '-PcraftEngineRuntimeJar=C:/isolated-server/plugins/CraftEngine.jar' `
  '-PcraftEngineRuntimeLibraries=C:/isolated-server/plugins/CraftEngine/libs' `
  compileJava
```

对两个版本分别执行；完成后使用**不带此脚本**的 `gradlew.bat test shadowJar` 构建发布包。保持较旧的编译基线，再把同一 JAR 放入两套测试服，才能检查双版本兼容。

## 隔离运行回归

`CompatProbe.java` 是独立测试插件，不能放入正式服。它会改动测试世界 `(8, 90–102, 8)` 与 `(20–32, 99–102, 8)`、强制加载区块，并由外部运行器正常关闭服务器。

使用两套完全隔离的 Paper 26.3 服务器，绑定本机不同端口，包括 CE 自托管资源包端口。安装同一森罗 JAR、完整森罗 CE 包和本地 UAA Pro，存储设为 SQLite。不得共用世界或数据库目录。

以 26.9.2 的 CE JAR、其内嵌 `proxy.jarinjar`（解压为 `proxy.jar`）、CE 的重定位运行库、Paper API、Paper 26.3 服务端与森罗插件作为编译路径，用 Java 25 同时编译 `CompatProbe.java`、`TransientBlockProbe.java`，字节码目标设为 Java 21。将生成的 `CompatProbe*.class` 与 `TransientBlockProbe*.class` 打包，添加：

```yaml
name: CompatProbe
version: 1.0
main: CompatProbe
api-version: '1.21'
depend: [KaleidoscopeCookeryPlugin]
```

再按[成就验证说明](../advancements/README.md)编译 `AdvancementProbe`、`GameplayProbe` 与 `BaoziProbe`，把相同的两个测试插件 JAR 放入两套服务器。

本次复用了 1.2.2 已完成的测试成就数据库，将每套服的 `advancement-phase.txt` 设为 `3`，验证已有进度及实际 `/ce reload all`。使用全新数据库时先运行成就夹具的第一、第二阶段，再进行第三阶段；不要将空数据库直接用于第三阶段。

```powershell
python verification/craftengine/run_server.py `
  --server C:/isolated-ce-26.9.2 --java C:/jdk-25/bin/java.exe --craftengine 26.9.2
python verification/craftengine/run_server.py `
  --server C:/isolated-ce-26.10 --java C:/jdk-25/bin/java.exe --craftengine 26.10-SNAPSHOT
```

运行器要求两个夹具报告成功、资源包 ZIP 的 CRC 和 `pack.mcmeta` 检查通过、服务器正常退出，并记录森罗 JAR 的哈希。分别输出 `ce-compatibility-result.json`、`advancement-probe-result.json`、`ce-verification-summary.json` 与完整控制台日志；两个汇总中的 `cookerySha256` 必须相同。

兼容夹具检查完整包的 107 个基础物品、底层桥接、热源、七类厨具的方块实体与 NBT 保存恢复、油壶和搪瓷盆的油量及方块态更新、由 CE 调度器实际驱动的蒸笼和茶壶热源检测，以及蒸笼下落取消和落地恢复食材。成就夹具另外覆盖包子投掷、拉面和鱼稻共生，以及已有成就进度和全量重载。

这是实际服务端加模拟玩家/事件、EmbeddedChannel 出站处理及实际数据包编解码的验证，不包含真实客户端连接与画面、完整玩家操作、Velocity / ViaVersion 转发或 Folia 区域调度实测。详细玩法夹具边界见成就验证说明。
