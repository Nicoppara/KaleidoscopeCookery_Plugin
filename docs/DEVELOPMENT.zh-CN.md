# 开发与 API

[返回中文首页](../README.zh-CN.md) · [English](../README.md) · [配置与配方](CONFIGURATION.zh-CN.md) · [森罗厨房成就](ADVANCEMENTS.zh-CN.md)

- [项目结构](#project) · [构建](#build)
- [事件](#api) · [运行时注册表](#registries)
- [食谱菜单](#recipe-menu) · [自定义配置段](#custom-sections)

<a id="project"></a>

## 项目结构

| 项目 | 位置或标识 |
| --- | --- |
| 包名 | `net.kaleidoscope.cookery` |
| 插件主类 | `net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin` |
| 插件声明 | [plugin.yml](../src/main/resources/plugin.yml) |
| 插件默认配置 | [config.yml](../src/main/resources/config.yml) |
| 构建脚本 | [build.gradle](../build.gradle) |
| 版本与 CE 编译基线 | [gradle.properties](../gradle.properties) |
| 物品、方块及行为命名空间 | `kaleidoscopecookery:` |
| 纯展示物品命名空间 | `show:` |

修改 Key 的命名空间时，需要同步修改对应的 CraftEngine 内容配置与资源引用。

构建完整多版本插件需要 JDK 21 与 JDK 25。Minecraft 1.21.x 服务端使用 Java 21，26.x 使用 Java 25。

<a id="build"></a>

## 构建

```bash
./gradlew build
# 产物：build/libs/KaleidoscopeCookeryPlugin-<version>.jar
```

Windows 使用 `gradlew.bat build`。构建会编译正式插件源码和所有服务端适配模块并生成插件 JAR；Spigot NMS 依赖可从 CodeMC 获取，也可用 BuildTools 安装到本地 Maven 仓库。

[`gradle.properties`](../gradle.properties) 中的 `craftEngineVersion` 统一控制 Bukkit、Core 和 Proxy 使用的 **公开 API 版本**。默认保持 **26.9.1** 编译基线，兼容运行环境中的 **26.9.2** 与 **26.10-SNAPSHOT**。新数据包在运行时按实际服务器接口绑定，发送给客户端的蒸笼状态使用 CE 的编号映射，服务端方块状态和食材数据保持原样。Core API 已包含重定位的 Adventure 类型，无需再混用旧版 `craft-engine-adventure`。

**Minecraft 26.3 蒸笼下落修复（1.2.3）**：旧版会把服务器专用的蒸笼状态编号写入 `add_transient_block` 数据包，原版客户端无法识别时会断线。1.2.3 补充该数据包及其所在 bundle 的转换，保留包内其他数据、原始方块状态、下落实体与落地食材恢复；CE 模组客户端沿用自身状态编号。未提供此新包的旧版 Minecraft 自动跳过处理。森罗资源包的模型、贴图和配置无需为本次修复修改。

1.1.9 迁移了作物掉落上下文、家具的 `IntConsumer` 交互实体接口和蒸笼下落实体接口；下落被取消时保留原蒸笼中的食材。升级时替换插件 JAR 并重启服务器，继续使用原有 CraftEngine 资源包。仓库附带资源包元数据和新增成就文本，完整模型、贴图及玩法配置需沿用已有资源包。

<a id="api"></a>

## 🧩 API / 事件

插件在 `net.kaleidoscope.cookery.api.event` 下提供 CraftEngine 风格的 Bukkit 事件，可被其它插件监听（玩法触发器）。事件通过 `net.kaleidoscope.cookery.util.EventUtils` 触发，可取消的事件被取消后会跳过对应行为，未取消时行为与原版一致。

| 事件                          | 触发时机                                              | 可取消                      |
| ----------------------------- | ----------------------------------------------------- | --------------------------- |
| `PotStirFryEvent`             | 玩家翻炒炒锅一次（`count` = 累计翻炒次数）            | ✅（取消则本次翻炒无效）     |
| `PotExtractDishEvent`         | 玩家从炒锅盛出成品（可改写 `dish`）                   | ✅                           |
| `StockpotExtractDishEvent`    | 玩家从高汤锅盛出成品（可改写 `dish`）                 | ✅                           |
| `ShawarmaExtractEvent`        | 玩家从沙威玛烤架取出成品（可改写 `product`）          | ✅                           |
| `SteamerBreakFullEvent`       | 玩家打破装满成品的蒸笼（`products` = 即将掉落的成品） | ✅（取消则跳过成品特殊掉落） |
| `MillstoneGrindCompleteEvent` | 石磨磨完一批产出成品（`player` 为空表示生物拉磨）     | ✅                           |

```java
@EventHandler
public void onExtract(PotExtractDishEvent event) {
    event.setDish(event.dish());  // 可改写成品
    // event.setCancelled(true);  // 或阻止盛出
}
```

<a id="registries"></a>

### 运行时注册表

`net.kaleidoscope.cookery.api.KaleidoscopeCookeryAPI` 是给别的插件用的唯一入口，**不要去 import controller / behavior 包里的类**。
下面这些注册表由插件与配置共用，别的插件可以在自己 `onEnable` 阶段往里加东西。

| 入口 | 返回 | 能做什么 |
| ---- | ---- | -------- |
| `KaleidoscopeCookeryAPI.plugin()` | `Plugin` | 拿到插件实例 |
| `KaleidoscopeCookeryAPI.itemTags()` | `ItemTags` | 读写物品标签，就是配置里 `#kaleidoscopecookery:xxx` 那一套 |
| `KaleidoscopeCookeryAPI.blockTags()` | `BlockTags` | 读写方块标签，如水田的 `kaleidoscopecookery:tillable` |
| `KaleidoscopeCookeryAPI.entityProperties()` | `EntityProperties` | 给 `entity_properties` 战利品条件注册自定义实体状态 |
| `KaleidoscopeCookeryAPI.choppingBoardKnives()` | `ChoppingBoardKnives` | 运行时往砧板加 / 删菜刀 |
| `KaleidoscopeCookeryAPI.millstoneAnimals()` | `MillstoneAnimals` | 注册能拉磨的生物，或挂一个 `Provider` 动态判定（MythicMobs 之类） |
| `KaleidoscopeCookeryAPI.potCookConditions()` | `PotCookConditions` | 追加炒锅的开火判定 |
| `KaleidoscopeCookeryAPI.foodRecipes()` | `FoodRecipeRegistry` | 查已加载的全部烹饪配方 |
| `KaleidoscopeCookeryAPI.applianceFoods()` | `ApplianceFoodRegistry` | 查 / 改各厨具的可下锅食材白名单 |
| `KaleidoscopeCookeryAPI.soupBases()` | `SoupBaseRegistry` | 查 / 改高汤锅的汤底表 |
| `KaleidoscopeCookeryAPI.recipeMenuStyle()` | `RecipeMenuStyle` | 换食谱菜单的按钮材质 / 标题 / 厨具名 |
| `KaleidoscopeCookeryAPI.recipeMenuHooks()` | `RecipeMenuHooks` | 用自己的界面整个接管食谱菜单 |

```java
import net.kaleidoscope.cookery.api.KaleidoscopeCookeryAPI;
import net.momirealms.craftengine.core.util.Key;

// 往"菜刀"标签里加一把自己插件的刀 砧板立刻认它
KaleidoscopeCookeryAPI.itemTags().add(
        Key.of("kaleidoscopecookery:kitchen_knife"),
        java.util.List.of("craftengine:myplugin:obsidian_knife"));

// 让熊猫也能拉磨
KaleidoscopeCookeryAPI.millstoneAnimals()
        .register(org.bukkit.entity.EntityType.PANDA, 35, true, false);

// 注册其它插件的实体状态 注册后重载 CE 配置即可在战利品条件中使用
KaleidoscopeCookeryAPI.entityProperties().register(
        "myplugin:is_special",
        entity -> entity.getScoreboardTags().contains("special"));
```

实体状态条件使用本插件命名空间，未写命名空间的 flag 按 `minecraft` 处理：

```yaml
- type: kaleidoscopecookery:entity_properties
  predicate:
    flags:
      is_on_fire: true
      myplugin:is_special: false
```

内置 flag 包括燃烧、潜行、疾跑、游泳、幼年/成年、着地、水下、岩浆、雨中、细雪、冰冻、隐身、发光、无敌、静音、重力、乘坐、死亡、有效、持久化、视觉火焰、滑翔、激流、睡眠、攀爬、AI、碰撞、拴绳、跳跃、使用物品、繁殖、年龄锁定、感知、攻击性、左利手、拾取物品、日光、驯服、坐下、飞行与格挡状态。未知 flag 会在配置解析期直接报错。

> `itemTags()` 与配置文件里的 `item_tags` 段共用同一个注册表。执行 `/ce reload all` 会先清空再按配置重建，**代码里注册的内容会被冲掉**——所以要在重载后重新注册，或干脆写进 `configuration/tag/` 的 yml 里。

<a id="recipe-menu"></a>

### 🎨 改食谱菜单（`/kcrecipe`）

有三种改法，从简单到彻底：**改配置** → **调 API 换皮** → **自己写整个界面**。三者可以混用。

#### 一、只改外观：写 `config.yml`（不用写代码）

打开 `plugins/KaleidoscopeCookeryPlugin/config.yml`，找到 `recipe_menu` 段：

```yaml
recipe_menu:
  # 按钮材质 原版 id 与 CraftEngine 自定义 id 都行
  buttons:
    back: "minecraft:barrier"
    next_page: "minecraft:feather"
    filler: "minecraft:black_stained_glass_pane"
    quality_hint: "kaleidoscopecookery:recipe_quality_hint"

  # 厨具图标与名字 两项都可以只写一个
  appliances:
    pot:
      icon: "minecraft:cauldron"
      name: "铁锅"
    millstone:
      name: "磨盘"

  # 界面标题 支持 MiniMessage 标签
  titles:
    home_browse: "<gold><bold>菜谱大全"
    list_browse: "<yellow><appliance></yellow> 共 <count> 道"
```

改完执行 `/ce reload all` 生效，**不用重启**。写错的按钮名、界面名或物品 id 会在控制台打一条警告并跳过那一条，不会影响其它项。

可用的名字：

| 类别 | 可用名 |
| ---- | ------ |
| `buttons` | `filler` `invalid` `back` `previous_page` `next_page` `create` `save` `delete` `add` `count` `mode` `rotation` `liquid` `carrier_none` `equivalent_foods` `seasonings` `quality_hint` |
| `appliances` | `pot` `stockpot` `steamer` `shawarma` `millstone` `chopping_board` `teapot` |
| `titles` | `home_browse` `home_edit` `list_browse` `list_edit` `create_pick_type` `detail_accurate` `detail_flex` `detail_chopping` `detail_teapot` `soup_base` |

标题占位符：`<appliance>` 厨具名、`<count>` 条目数、`<recipe>` 成品物品名。不写颜色标签时保持内置的深灰色。键名同时支持下划线与连字符（`next_page` 与 `next-page` 等价）。

#### 二、插件换皮：`RecipeMenuStyle`

和上面能改的东西一样，只是从代码里改。适合做成"主题包"随插件分发。

```java
import net.kaleidoscope.cookery.api.KaleidoscopeCookeryAPI;
import net.kaleidoscope.cookery.api.ui.*;
import net.kaleidoscope.cookery.recipe.ApplianceType;
import net.momirealms.craftengine.core.util.Key;

RecipeMenuStyle style = KaleidoscopeCookeryAPI.recipeMenuStyle();
style.icon(MenuButton.BACK, Key.of("myplugin:fancy_arrow"));
style.applianceIcon(ApplianceType.POT, Key.of("minecraft:cauldron"));
style.applianceName(ApplianceType.POT, "铁锅");
style.title(MenuScreen.LIST_BROWSE, "<gold><appliance></gold> 共 <count> 条");

// 自定义菜单显示具体液体时用这个：water/lava 与 water_bucket/lava_bucket 都支持
Key liquidIcon = style.liquidIcon(recipe.fluid());

style.icon(MenuButton.BACK, null);  // 传 null 还原这一项
style.reset();                      // 还原全部 API 设置
```

> **优先级：API > config.yml > 内置默认。**
> 两层是分开存的，所以 `/ce reload all` 重读配置**不会**冲掉插件设的值；反过来 `reset()` 也只清 API 那一层，配置里写的照常生效。

#### 三、整个接管：`RecipeMenuProvider`

想完全自己画界面就用这个。三个可接管点各自独立，**默认全部返回 `false` 表示不接管**，只重写想换掉的那个。

```java
KaleidoscopeCookeryAPI.recipeMenuHooks().provider(new RecipeMenuProvider() {
    @Override
    public boolean openHome(Player player, boolean editable) {
        if (editable) {
            return false;          // 编辑模式仍走内置菜单
        }
        myBrowseMenu.open(player);
        return true;               // 浏览模式我自己来
    }

    @Override
    public boolean openDetail(Player player, ApplianceType cook, Key recipeId) {
        myDetailMenu.open(player, recipeId);
        return true;
    }
});
```

| 方法 | 什么时候调 |
| ---- | ---------- |
| `openHome(player, editable)` | 玩家执行 `/kcrecipe`，选厨具那一屏 |
| `openList(player, cook, editable)` | 点进某个厨具的配方列表 |
| `openDetail(player, cook, recipeId)` | 点开某条配方的详情 |

要用的数据从 `KaleidoscopeCookeryAPI.foodRecipes()` 拿，别去 import `ui` 包里的类。接管方显示配方的实际液体时应调用 `KaleidoscopeCookeryAPI.recipeMenuStyle().liquidIcon(liquid)`；茶壶配方给的是 `minecraft:water` / `minecraft:lava` 这类流体 ID，高汤锅给的是 `minecraft:water_bucket` / `minecraft:lava_bucket` 这类桶物品 ID。`icon(MenuButton.LIQUID)` 只表示通用入口或尚未选择液体时的默认图标，不能拿它画每条配方，否则岩浆也会显示成水桶。

回调运行在**触发菜单的那个线程**，也就是拥有该玩家的 region 线程，直接 `player.openInventory(...)` 即可，不要自己切线程。

外部实现如果抛异常，插件会打一条警告并**自动回落到内置菜单**，不会把玩家卡在没有界面的状态。同一时刻只能有一个 provider，重复注册会替换掉前一个；传 `null` 即撤销。

<a id="custom-sections"></a>

### 自定义配置段

插件向 CraftEngine 注册了几个自己的配置段，可以出现在资源包的任意 yml 里：

| 配置段 | 内容 | 现在写在哪 |
| ------ | ---- | ---------- |
| `item_tags` | 物品标签 | `configuration/tag/kitchenware.yml` |
| `block_tags` | 方块标签 | `configuration/tag/blocks.yml` |
| `millstone_animals` | 能拉磨的生物与转速 | `configuration/entity/millstone_animals.yml` |
| `pot_food_raw` / `stock_food_raw` | 炒锅 / 高汤锅的可下锅食材白名单 | `configuration/recipe/pot.yml`、`stockpot.yml` |
| `pot_flex_foods` / `stock_flex_foods` | 炒锅 / 高汤锅的模糊配方 | `configuration/recipe/` |
| `accurate_foods` | 蒸笼 / 石磨 / 烤肉塔的精准配方 | `configuration/recipe/accurate.yml` 等 |
| `chopping_board_raws` | 砧板配方 | `configuration/recipe/chopping_board.yml` 等 |
| `teapot_liquid` / `tea_cup` / `teapot_result` | 茶壶液体、杯中展示、茶配方 | `configuration/recipe/teapot.yml` |
| `dish_carrier` | 吃完退还什么容器 | `configuration/dish/dish_carrier.yml` |
| `equivalent_foods` / `seasonings` | 等效食物表与调味品表 | `configuration/tag/food_groups.yml` |
