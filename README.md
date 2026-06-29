<div align="center">


# 🍳 森罗物语 · Kaleidoscope Cookery

**基于 [CraftEngine](https://github.com/Xiao-MoMi/craft-engine) 的中式烹饪玩法插件**

炒锅 · 高汤锅 · 蒸笼 · 搪瓷盆 · 砧板 · 石磨 · 沙威玛烤架 · 厨具架 · 垃圾桶 —— 一整套能玩的厨房与配套配方系统

**简体中文** · [English](README_EN.md)

![CraftEngine](https://img.shields.io/badge/CraftEngine-required-4C8BF5?style=flat-square)
![Paper](https://img.shields.io/badge/Paper-1.21+-EF6C00?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square)
![i18n](https://img.shields.io/badge/语言-5种-2E7D32?style=flat-square)

</div>

---

## 📖 简介

`KaleidoscopeCookeryPlugin` 是把 **森罗物语（Kaleidoscope Cookery）** 模组的中式烹饪玩法移植到 **CraftEngine** 的服务端插件 + 资源配置。
无需客户端装模组，玩家只用原版客户端 + 服务器资源包即可体验整套厨房：从切菜、炒菜、炖汤、蒸笼，到摆盘上桌。

|                      |                                                             |
| -------------------- | ----------------------------------------------------------- |
| **包名**             | `net.kaleidoscope.cookery`                                  |
| **主类**             | `net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin` |
| **物品命名空间**     | 主物品 `kaleidoscopecookery:` · 纯展示模型 `show:`          |
| **行为类型命名空间** | `kaleidoscopecookery:`                                      |

> ⚠️ 修改物品 / 行为 Key 的命名空间时，CraftEngine 端对应的资源与配置（`.yml`）必须同步修改，否则运行时找不到物品或行为。

---

## ✨ 特性

- 🍳 **8 大烹饪台 / 厨具** —— 炒锅翻炒、高汤锅炖煮、蒸笼蒸制、石磨研磨、砧板切配、沙威玛烤架炭烤、搪瓷盆储油、炉灶控火，各有独立交互与配方系统。
- 🥘 **100+ 食材与成品** —— 生熟肉链、面团面条、盖饭汤品面食、招牌摆盘菜、拼盘装盘，全部带营养与食用效果。
- 🪑 **整套餐厅家具** —— 桌、椅、厨娘凳（11 种木材）、垃圾桶、食谱。
- 🔨 **完整配方** —— 工作台合成、熔炉烧制、以及各烹饪台的专属配方，约 250 条。
- 🌐 **5 种语言** —— 简体中文 / 繁体中文（香港·台湾）/ 文言 / English，客户端切换即生效。
- 🛡️ **领地兼容** —— 内置 AntiGriefLib，自动复用服务器领地 / 保护插件做交互与破坏判定。

---

## 🧱 烹饪台一览

| 台子         | 行为 Key                                   | 玩法                                  |
| ------------ | ------------------------------------------ | ------------------------------------- |
| 🥘 炒锅       | `kaleidoscopecookery:cooking_pot`          | 倒油 → 投料 → 翻炒 → 盛出，可记录食谱 |
| 🍲 高汤锅     | `kaleidoscopecookery:stockpot`             | 加汤底 → 投料 → 盖盖炖煮 → 盛出       |
| 🫔 蒸笼       | `kaleidoscopecookery:steamer`              | 放料蒸制，可叠层，失去支撑会掉落      |
| 🪨 石磨       | `kaleidoscopecookery:millstone`            | 玩家推磨 / 生物拉磨，研磨食材         |
| 🔪 砧板       | `kaleidoscopecookery:chopping_board`       | 手持菜刀逐刀切割，切满产出加权成品    |
| 🍢 沙威玛烤架 | `kaleidoscopecookery:shawarma_spit`        | 红石控制旋转，炭火慢烤肉类            |
| 🫕 搪瓷盆     | `kaleidoscopecookery:cooking_enamel_basin` | 储油、加油 / 取油、厨铲沾油           |
| 🔥 炉灶       | `kaleidoscopecookery:stove`                | 打火石点火、铲子 / 降雨熄火           |

---

## 📦 依赖与构建

- **运行环境**：Paper / Folia，需安装 [CraftEngine](https://github.com/Xiao-MoMi/craft-engine)（`plugin.yml` 声明为前置且 `load: BEFORE`）。

- **领地保护**：内置打包 [AntiGriefLib](https://github.com/Xiao-MoMi/AntiGriefLib)（shadow 重定位到 `net.kaleidoscope.cookery.libs.antigrieflib`），自动复用服务器上的领地 / 保护插件做交互与破坏判定。

- **构建**：

  ```bash
  ./gradlew shadowJar
  # 产物：build/libs/KaleidoscopeCookeryPlugin-<version>-all.jar  （务必用带 -all 的那个）
  ```

> 💡 部署时请用 **`-all`（shaded）** 的 jar，否则会缺少内置的 AntiGriefLib 而在交互时报 `NoClassDefFoundError`。

### 🛡️ 领地权限

所有家具的右键交互入口都会先经过 AntiGriefLib 判定，无权限玩家无法在他人领地内使用。
拥有权限节点 `kaleidoscopecookery.antigrief.bypass` 的玩家可绕过该判定。

---

## 🌐 多语言

物品名、分类名、菜品描述全部走翻译键（`<lang:...>`），由资源包提供 5 个客户端语言：

| 语言                 | locale  | 来源            |
| -------------------- | ------- | --------------- |
| 简体中文             | `zh_cn` | 模组原文        |
| 繁體中文（中国香港） | `zh_hk` | 模组繁体        |
| 繁體中文（中国臺灣） | `zh_tw` | 模组繁体        |
| 文言                 | `lzh`   | 模组文言 + 回退 |
| English              | `en_us` | 模组原文        |

玩家把客户端语言切到对应语言即可看到对应翻译，无需任何额外操作。

---

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

---

## 🎛️ 行为（behaviors）配置参考

下列配置写在 CraftEngine 的方块 / 家具 / 物品定义里的 `behaviors:` 段。键名同时支持下划线与连字符两种写法。默认值即下方所示。

### 🥘 炒锅 `kaleidoscopecookery:cooking_pot`

倒油、翻炒、投料、盛出、记录食谱。

```yaml
behaviors:
  - type: kaleidoscopecookery:cooking_pot
    data_key: "kaleidoscopecookery:cooking_pot"
    stir_fry_count: 6            # 出锅所需翻炒次数
    cook_done_time: 200          # 出锅后多少 tick 进入烧焦阶段（-1 = 永不烧焦）
    burnt_to_charcoal_time: 400  # 烧焦后多少 tick 变成木炭
    oil_item: "kaleidoscopecookery:oil"
    shovel_no_oil_item: "kaleidoscopecookery:kitchen_shovel_no_oil"
    shovel_has_oil_item: "kaleidoscopecookery:kitchen_shovel_has_oil"
    recipe_item_no_recipe: "kaleidoscopecookery:recipe_item_no_recipe"
    recipe_item_has_recipe: "kaleidoscopecookery:recipe_item_has_recipe"
    bowl_item: "minecraft:bowl"
    msg_need_bowl: "你需要碗来盛装！"
    msg_has_oil: "锅里已经有油了"
    msg_pot_occupied: "锅里还有东西！"
    msg_need_heat: "需要先放火上"
    msg_need_oil_first: "请先倒油！"
    msg_not_enough_ingredients: "食材不足！"
    msg_burnt_no_recipe: "糊了，没法记食谱！"
    msg_not_done_yet: "菜还没出锅呢！"
    msg_mixed_no_recipe: "大乱炖没法记食谱！"
    msg_recipe_saved: "食谱记录成功！"
    msg_start_cooking: "开始烹饪了，记得准备碗来盛菜"
    msg_dish_ready: "出锅！"
    msg_all_burnt: "糟糕，全糊了！"
    msg_not_ingredient: "无法加入：不是食材"
```

### 🔥 炉灶 `kaleidoscopecookery:stove`

点火（打火石 / 火焰弹）与熄火（铲子、降雨、上方水流），切换 `lit` 状态。

```yaml
behaviors:
  - type: kaleidoscopecookery:stove
    # 可熄火的自定义厨铲（原版各种铲子始终可熄火）
    extinguish_kitchen_shovel_item: "kaleidoscopecookery:kitchen_shovel_no_oil"
```

### 🫕 搪瓷盆 `kaleidoscopecookery:cooking_enamel_basin`

右键开合、加油 / 取油、厨铲沾油等交互。

```yaml
behaviors:
  - type: kaleidoscopecookery:cooking_enamel_basin
    max_oil: 16                  # 盆的油容量上限
    oil_item: "kaleidoscopecookery:oil"
    shovel_no_oil_item: "kaleidoscopecookery:kitchen_shovel_no_oil"
    shovel_has_oil_item: "kaleidoscopecookery:kitchen_shovel_has_oil"
```

### 🍲 高汤锅 `kaleidoscopecookery:stockpot`

盖 / 揭锅盖、加 / 舀汤底、加 / 取食材、盛出成品。

```yaml
behaviors:
  - type: kaleidoscopecookery:stockpot
    cooking_time: 400                # 盖盖后多少 tick 炖煮完成
    lid_item: "kaleidoscopecookery:stockpot_lid"
    bowl_item: "minecraft:bowl"
    recipe_item_no_recipe: "kaleidoscopecookery:recipe_item_no_recipe"
    recipe_item_has_recipe: "kaleidoscopecookery:recipe_item_has_recipe"
    msg_start_stewing: "开始炖煮了，记得准备碗来盛菜"
    msg_not_enough_ingredients: "背包中没有足够的食材！"
    msg_no_recipe: "当前食材没有对应的配方！"
    msg_recipe_saved: "已成功记录这道菜的食谱！"
    msg_use_bowl: "请手持碗右键盛出"
```

### 🫔 蒸笼 `kaleidoscopecookery:steamer`

右键放料 / 取料、盖盖子、堆叠蒸笼，失去支撑时掉落。

```yaml
behaviors:
  - type: kaleidoscopecookery:steamer
    cooking_time: 200            # 每个食材蒸熟所需 tick
    campfire_stack_height: 8     # 在篝火等热源上最多叠几层
    stove_stack_height: 16       # 在炉灶上最多叠几层
    msg_max_layers: "蒸笼最多只能叠 {max} 层"   # {max} 替换为实际上限
    msg_full: "蒸笼已满"
    msg_need_stove: "请放在炉灶上方"
```

### 🔪 砧板 `kaleidoscopecookery:chopping_board`

右键放原料、手持菜刀逐刀切割、切满产出加权成品、空手取回未切完的料。

```yaml
behaviors:
  - type: kaleidoscopecookery:chopping_board
    diamond_knife_item: "kaleidoscopecookery:diamond_kitchen_knife"
    gold_knife_item: "kaleidoscopecookery:gold_kitchen_knife"
    iron_knife_item: "kaleidoscopecookery:iron_kitchen_knife"
    netherite_knife_item: "kaleidoscopecookery:netherite_kitchen_knife"
```

### 🍢 沙威玛烤架 `kaleidoscopecookery:shawarma_spit`

右键放料 / 取料，红石信号控制旋转，上下两半联动。

```yaml
behaviors:
  - type: kaleidoscopecookery:shawarma_spit
    grill_time: 300              # 每个食材烤熟所需 tick
```

### 🪨 石磨 `kaleidoscopecookery:millstone`

玩家推磨或拴绳牵生物拉磨研磨食材。**研磨按圈数产出**：转满所需圈数产出一批，真实耗时由拉磨者的转速（秒/圈）决定——转得慢就产得慢。转速按各生物的拉一圈秒数，见下方 `millstone_animals`；玩家与村民被打时会临时加速到骡子的速度。每种食材的所需圈数默认取 `grind_rotations`，精准配方可用 `rotations` 各自覆盖。

```yaml
behaviors:
  - type: kaleidoscopecookery:millstone
    grind_rotations: 4           # 每料产出所需圈数 默认 精准配方可用 rotations 覆盖
    stick_item: "show:new_millstone_stick"     # 中心自转棍展示模型
    stick2_item: "show:new_millstone_stick2"   # 公转支架展示模型
    stone_item: "show:new_millstone_stone"     # 横向滚动磨石展示模型
    msg_already_pushing: "§c你已经在推另一个石磨了"
    msg_need_ground_below: "§c起始位置脚下必须有方块"
    msg_uneven: "§c起始位置不平整，无法推磨"
    msg_not_same_plane: "§c你与石磨不在同一平面，无法推磨"
    msg_exit_hint: "§e再次 潜行+右键石磨 退出推磨"
    msg_stop_animal_hint: "§e手持剪刀 Shift+右键 石磨可停止生物拉磨"
```

#### 拉磨生物 `millstone_animals`

配置每种生物拉磨一圈的秒数 是否允许拉磨 以及原版不可拴的生物是否强制拴绳。作为独立的配置节解析 随 CE 重载生效。不写则使用下方内置默认值。

```yaml
millstone_animals:
  cow:
    seconds: 40                 # 拉一圈所需秒数 数值越小越快
    allowed: true               # 是否允许该生物拉磨
    force_leash: false          # 原版不能被拴的生物 设 true 后手持拴绳右键可强制拴上
    interaction_disabled: true  # 拉磨时是否禁用对它的右键 驴骡开箱加料始终放行
    orbit_radius: 2.5           # 绕磨半径 即起始与行走位置离磨心的距离
  villager:
    seconds: 7.5
    allowed: true
    force_leash: true
```

内置默认（单位 秒每圈）：骡 6 村民 7.5 驴 10 马 / 骷髅马 25 羊驼 / 行商羊驼 30 牛 / 哞菇 40 绵羊 / 山羊 50。

想接入 MythicMobs 等插件的自定义生物 或在代码里覆盖速度、起始位置、是否禁右键等 注册一个 Provider 即可。公开 API 类在 `net.kaleidoscope.cookery.api.MillstoneAnimals`。三参构造沿用默认（禁右键、半径 2.5），全参构造可覆盖更多：

```java
import net.kaleidoscope.cookery.api.MillstoneAnimals;

// Profile(秒每圈, 是否允许, 是否强制拴绳, 是否禁右键, 绕磨半径)
MillstoneAnimals.instance().addProvider(entity ->
        isMyCustomMob(entity) ? new MillstoneAnimals.Profile(20, true, true, false, 3.0) : null);
```

石磨支持的生物的刷怪蛋右键石磨即可直接生成并开始拉磨。

### 垃圾桶 `kaleidoscopecookery:trashcan`

家具行为。支持投放、取出与进入桶内躲藏。

- 手持物品右键投放，最多存 3 件，满了挤掉最旧的一件。
- 空手右键取出（倒序返还）。
- 跳起落到桶顶、落差大于 1 自动进入桶内：切旁观并把视角固定到桶口的相机实体，戴雕刻南瓜头由客户端渲染遮罩，潜行退出。
- 投放、取出、进入分别播放桶盖开合、进入摆动与占用待机（开盖加眼睛冒出）动画；有玩家在里面时禁止投放取出，桶内掉落物对所有人隐藏（仍保存）。

需在家具定义里挂上该行为，渲染才会接管桶身、桶盖、眼睛与掉落物：

```yaml
behaviors:
  - type: kaleidoscopecookery:trashcan
```

> 进入桶内的遮罩复用原版南瓜头覆盖层：把资源包的 `assets/minecraft/textures/misc/pumpkinblur.png` 替换成垃圾桶遮罩（透明缝隙版），戴南瓜时看到的就是它。这是全局的，戴真南瓜也会变这个图。

### 🧩 其它行为

无额外配置项，直接挂载即可：

- 厨具架 `kaleidoscopecookery:kitchenware_racks`（`data_key` 默认 `kaleidoscopecookery:kitchenware_racks`）
- 配方展示家具 `kaleidoscopecookery:recipe_furniture`

---

## 🍳 配方（recipes）配置参考

配方由 `FoodRecipeManager` 解析，注册到对应的注册表中。

**通用约定：**

- `require` 项写作 `"物品id [数量]"`，数量缺省为 1。
- `raw` 项写作 `"分类 [最小值]"`，最小值缺省为 0（0 = 不参与匹配/计数）。
- `result` 可为单个标量（权重 100、1:1）或 `"物品id [权重]"` 列表（权重缺省 100）。

### 🗂️ 食材分类表

```yaml
# 炒锅食材表
pot_food_raw:
  meat:
    - minecraft:beef
  vegetable:
    - minecraft:carrot

# 高汤锅食材表（含汤底）
stock_food_raw:
  liquid:                                  # 汤底表：item = 倒入所需的桶，show = 锅中液面模型
    - { item: minecraft:water_bucket, show: show:stove_water }
    - { item: minecraft:lava_bucket,  show: show:lava }
    - { item: minecraft:cod_bucket,   show: show:stove_water }
  meat:
    - minecraft:beef
```

### 🔀 模糊配方

```yaml
pot_flex_foods:
  kaleidoscopecookery:cooked_beef:
    result: minecraft:cooked_beef
    require: [ minecraft:beef ]
    raw: [ "meat 1", "vegetable 0" ]
    preferred: [ minecraft:potato ]
    unpreferred: [ minecraft:rotten_flesh ]
    lore: [ { require: minecraft:potato, data: "..." } ]

stock_flex_foods:
  kaleidoscopecookery:fish_soup:
    result: minecraft:cooked_cod
    liquid: [ minecraft:water_bucket, minecraft:cod_bucket ]   # 当前汤底命中其一才匹配
    require: [ minecraft:cod ]
```

> `lore` 支持两种写法：扁平 `{ require, data }`，或块状 `{ when: [...], unpreferred, data: [...] }`。

### 🎯 精准配方

```yaml
accurate_foods:
  kaleidoscopecookery:shawarma_cooked_beef:   # 配方名（任意）
    require: kaleidoscopecookery:beef          # 原料（输入）
    result: kaleidoscopecookery:cooked_beef    # 成品（输出 标量 = 100%）
    cook: shawarma
    lore:
      - "沙威玛烤架烤出来的肉，有股木香"
```

石磨配方可在 `require` 下加 `rotations` 指定产出所需圈数（不写则用 behavior 的 `grind_rotations` 默认）。`rotations` **仅石磨可用**，写在非石磨配方上会在后台报错并跳过该配方。`result` 用权重列表时按权重随机出一个：

```yaml
accurate_foods:
  kaleidoscopecookery:millstone_iron_ore:
    require: minecraft:iron_ore
    rotations: 6                  # 转满 6 圈产出 仅石磨可用
    result:
      - minecraft:iron_ingot 45   # 权重 45
      - minecraft:gold_ingot 45   # 权重 45
    cook: millstone
```

### 🔪 砧板配方

```yaml
chopping_board_raws:
  kaleidoscopecookery:cod:        # 配方名 任意
    require: minecraft:cod        # 原料 输入 仅 require 中存在的物品可放上砧板
    stage: 5                      # 阶段数 放下 = 阶段 1 模型 .../0 每切一刀 +1 切满产出
    values: cb:block/.../cod      # 模型 id 前缀 按 stage 自动派生 0 ~ stage-1
    mode: single                  # 产出模式 single 单产物 single_extra 单产物加附带 multi_random 多产物随机
    result: minecraft:cooked_cod 1   # single 与 single_extra 只能一个产物 物品 数量 不需要权重
```

产出模式说明：

- `single`（默认）：`result` 固定产出一个产物，写成标量或单元素列表都可以，不需要权重；配置多个会报错并跳过该配方。
- `single_extra`：`result` 同样只配一个主产物，再让 `extra` 列表每一项各自把权重当作百分比独立判定是否附带掉落。
- `multi_random`：`result` 可配多个，每一项把权重当作百分比独立判定，全部未命中时再按权重保底产出一个。

```yaml
chopping_board_raws:
  kaleidoscopecookery:fish_with_bone:
    require: minecraft:cod
    stage: 3
    values: cb:block/.../cod
    mode: single_extra
    result:                       # 主产物 必出一个
      - minecraft:cooked_cod 1 100
    extra:                        # 附带产物 权重 = 百分比 各自独立判定
      - minecraft:bone 1 30       # 30% 概率附带 1 根骨头
```

---



## 📜 致谢与许可

- 玩法 / 美术原型：[森罗物语 · Kaleidoscope Cookery](https://github.com/KaleidoscopeMods/KaleidoscopeCookery) 模组团队。
- 运行框架：[CraftEngine](https://github.com/Xiao-MoMi/craft-engine) · [AntiGriefLib](https://github.com/Xiao-MoMi/AntiGriefLib)。

本仓库为该模组的 CraftEngine 服务端移植，仅供学习与服务器使用，美术 / 玩法版权归原模组团队所有。

<div align="center">


🍜 _慢火细炖，方得至味_ 🍜

</div>
