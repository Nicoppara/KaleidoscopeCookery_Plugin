# 森罗厨房成就

[返回首页](../README.md) · [配置与配方](CONFIGURATION.zh-CN.md) · [开发与 API](DEVELOPMENT.zh-CN.md)

成就功能可选安装兼容的 UltimateAdvancementAPI Pro；未安装时，厨具、烹饪和包子投掷等原有玩法正常运行。

- [支持范围](#coverage) · [玩法条件](#mechanics)
- [安装、升级与配置](#setup) · [开发接口](#development)

<a id="coverage"></a>

## 支持范围

名称、描述、任务 / 目标 / 挑战类型、隐藏条件和分项要求对齐官方森罗物语 **1.6.0-forge+mc1.20.1**：[源码基准](https://github.com/KaleidoscopeMods/KaleidoscopeCookery/tree/2f4e386ce23f49a385ddf003c67fc6415c55417a)。目录保留“森罗厨房”入口及全部 **37 项成就**。完整资源包下注册入口和 **33 项可完成的成就**，包括烹饪、切菜、拉面、肉包打狗、鱼稻共生、水田、猪儿虫喂鸡、稻草人和菜刀击杀等条件。

尚未移植的 **4 项**氮气飞行成就自动隐藏：漂浮效果没有模组的胀气推进及累计爬升机制。缺少必需物品的节点也会隐藏，剩余子节点接到最近可用的祖先。

<a id="mechanics"></a>

## 玩法条件

**肉包打狗**：手持包子，潜行并右键以每次一个的数量投掷，普通右键仍然食用。主副手均可投掷，两手都有包子时只投主手。包子沿抛物线飞行；命中野生或已驯服的狼会回满血并出现爱心，满血狼也能完成成就。投掷成功后消耗一个（与模组一致，包括创造模式）；发射被取消不扣物品。命中方块或其他实体后消失，不造成伤害，落空五秒后清理，不返还。领地拒绝交互、命中事件或回血事件被取消时不回血、不发成就。其他插件调整回血量时尊重其结果。

1.2.2 的投掷和成就直接使用现有包子模型及已提供的成就文本。已部署 1.2.0 成就文本补丁的服务器只需替换插件 JAR 并重启，无需再次修改或重新生成资源包。未安装 UltimateAdvancementAPI 时投掷与回血也可正常使用。

“拉拉拉拉拉拉面”监听资源包中生面团的完整使用和面条替换，取消操作或仅获得面条不会完成成就。“鱼稻共生”要求玩家用生物桶成功放出生物，并在放生处同高度的 3×3 范围内找到水稻；生物类型须在该水稻的 `booster_entities` 配置中。桶操作取消、生物生成被阻止或发射器放生不计入。蒸笼恢复挂在原模组的拉面节点下。

“绝代双椒”的红椒、绿椒和“农夫乐事”的四类装备分别保存进度，重复获取同一物品不会填满其他条件。“黑暗料理”的两种料理按任一满足处理。入口要求获得模组物品，登录不会直接赠送。石磨成就奖励成功绑定动物的主人。被取消的操作、失败的烹饪和未造成伤害的取料不会发放对应成就。

<a id="setup"></a>

## 安装、升级与配置

升级步骤：

1. 确认已有完整森罗内容包，替换插件 JAR，安装兼容的 UltimateAdvancementAPI Pro 并重启服务器。
2. 把仓库中的 [advancements.yml](../Kaleidoscope/kaleidoscopecookery/configuration/advancements.yml) 合并到现有 CraftEngine 森罗物语包的 `configuration/` 目录。
3. 执行 `/ce reload all`，然后按服务器现有流程重新生成并分发资源包。新增内容只有成就文本，模型和贴图沿用现有包。未更新文本的客户端显示内置中文回退文本。

`config.yml` 中 `advancements.enabled` 控制集成，`advancements.disabled` 可填写模组节点名（例如 `dangerous_chef`）。执行 `/ce reload all` 后重新注册成就树，已保存的进度保留。成就文本支持官方语言文件提供的语言；Pro 的本地化显示和自动布局接口会在运行时自动启用。

<a id="development"></a>

## 开发接口

默认构建使用公开的 UAA 2.8.1 API，只作为编译依赖。使用本地兼容 Pro 版 API 构建时可指定 JAR 路径：

```powershell
.\gradlew.bat build "-PultimateAdvancementApiJar=C:/path/UltimateAdvancementAPI-Plugin-2.8.1-pro.2.jar"
```

成就接口不会被打包到本插件中。游戏行为可通过 `KaleidoscopeAdvancements.recordEvent(player, event)` 在操作成功后发送对应模组事件；未注册或尚未移植的事件会忽略。成就目录位于 [kaleidoscope.json](../src/main/resources/advancements/kaleidoscope.json)，上游许可和来源说明随插件保存在 [上游许可目录](../src/main/resources/licenses/) 中。
