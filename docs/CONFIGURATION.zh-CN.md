# 配置与配方指南

[返回首页](../README.md) · [开发与 API](DEVELOPMENT.zh-CN.md) · [森罗厨房成就](ADVANCEMENTS.zh-CN.md)

插件设置在 `plugins/KaleidoscopeCookeryPlugin/config.yml`；厨具、食材、配方与模型在 CraftEngine 内容包中配置。下列目录和示例以完整森罗内容包为准，仓库中的少量资源文件不能代替完整内容包。更新前请备份已有修改。

- [目录与命名](#directories) · [模板与工厂](#templates)
- [物品标签](#item-tags) · [方块标签](#block-tags) · [碰撞箱](#hitboxes)
- [常见改动](#customization) · [多语言](#languages) · [PlaceholderAPI](#placeholders)
- [容器退还](#containers) · [行为参数](#behaviors)
- [配方参考](#recipes) · [食材组与调味品](#food-groups) · [加工时间与次数](#processing)
- [领地权限](#protection) · [食谱菜单外观](DEVELOPMENT.zh-CN.md#recipe-menu)

<a id="directories"></a>

## 📁 配置目录结构

插件本身只有一个 `plugins/KaleidoscopeCookeryPlugin/config.yml`（控制台语言、食谱菜单外观、成就和 bStats 开关）。
**玩法内容全部在 CraftEngine 资源包里**，资源包目录名可以自定义，但包根必须有声明固定命名空间的 `pack.yml`：

```yaml
namespace: kaleidoscopecookery
```

```
Kaleidoscope/
├── pack.yml                    固定 namespace，插件依靠它定位资源包
├── configuration/              ← 所有 yml 配置都在这里
│   ├── template/               公共模板 改一处全体生效 先看这里
│   │   ├── block_settings.yml    方块属性模板 硬度 抗性 挖掘工具
│   │   ├── display_item.yml      展示物品与物品骨架模板
│   │   ├── furniture.yml         家具通用设置 音效 掉落 摆放规则
│   │   ├── hitbox.yml            碰撞箱模板
│   │   └── dish.yml              可摆放菜品模板
│   ├── tag/                    标签 把"一类东西"声明一次到处引用
│   │   ├── kitchenware.yml       物品标签 菜刀 锅铲 油壶
│   │   └── blocks.yml            方块标签 水田可开垦方块
│   ├── appliance/              八台厨具 炒锅 汤锅 蒸笼 石磨 砧板 炉灶 烤肉塔 搪瓷盆 厨具架 菜谱板
│   ├── furniture/              家具 椅子 凳子 餐桌 茶壶 果篮 垃圾桶 稻草人
│   ├── crop/                   作物 番茄 生菜 辣椒 水稻
│   ├── item/                   物品 食材 熟食 厨具 护甲 悬挂串
│   ├── dish/                   可摆放的菜
│   │   ├── dishes.yml            每道菜一条 口数 营养 碰撞箱 掉落
│   │   └── dish_models.yml       每道菜每一口的展示模型
│   ├── recipe/                 配方 合成 烧制 以及各厨具的专属配方
│   ├── sound/                  自定义音效
│   ├── loot/                   生物额外掉落与草帽割草种子
│   ├── font/                   字体图片 茶壶液体进度条
│   ├── entity/                 生物相关 石磨拉磨生物表
│   ├── gui/                    CraftEngine 物品浏览器的分类目录
│   └── lang/                   物品、方块与界面的翻译文本
└── resourcepack/               资源包 模型 贴图 音频
    └── assets/minecraft/
        ├── models/block/custom/cook/   方块与展示模型
        ├── textures/                   贴图
        └── sounds/custom/cook/         音频
```

玩法与资源配置修改后，执行 `/ce reload all` 重新加载；模型、贴图和字体变化还需要重新生成并向玩家分发资源包。插件 JAR 更新后需要重启服务器。

---

<a id="naming"></a>

## 📛 命名规范

配置里所有 id 都是 `<命名空间>:<名字>` 的形式。命名空间使用小写字母、数字、下划线、连字符或点；名字还可以包含路径分隔符 `/`。建议普通条目使用 `snake_case`，模板用 `/` 分层。
中文不能写进 id——这些 id 会变成资源包里的文件路径和 Bukkit 的 `NamespacedKey`，Minecraft 只认 `[a-z0-9_.-/]`，写中文会直接导致客户端加载失败。
所以每个 id 上面都加了一行 `#中文名` 注释，看名字就知道是什么。

| 命名空间 | 用途 | 例子 |
| -------- | ---- | ---- |
| `kaleidoscopecookery:` | 真正的物品 / 方块 / 家具 / 音效 / 配方 | `kaleidoscopecookery:pot` |
| `show:` | 纯展示用的模型物品，没有任何功能，玩家拿不到 | `show:pot_has_oil` |
| `minecraft:` | 原版物品 / 方块 | `minecraft:bowl` |
| `craftengine:<命名空间>:<名字>` | 在**行为配置**里指代一个 CraftEngine 自定义物品 | `craftengine:kaleidoscopecookery:sickle` |
| `#<命名空间>:<名字>` | 物品标签，见下文 | `#kaleidoscopecookery:kitchen_knife` |

模板 id 统一写成 `kaleidoscopecookery:<类别>/<名字>`，类别固定就这几种：

| 类别 | 装什么 |
| ---- | ------ |
| `settings/` | 方块属性（硬度、抗性、挖掘工具、透光） |
| `appearance/` | 方块外观（借哪个原版方块状态、挂哪个展示模型） |
| `display/` | 展示物品（`show:` 那一族） |
| `item/` | 物品骨架（模型写法、食物组件） |
| `block/` | 方块骨架 |
| `furniture/` | 家具骨架（音效、掉落、摆放规则） |
| `hitbox/` | 碰撞箱 |
| `dish/` | 可摆放菜品 |
| `recipe/` | 配方 |
| `sound/` | 音效 |

`config_factory#` 后面的名字用复数，表示"批量生成一批同类东西"，例如 `config_factory#chairs`、`config_factory#millstone_recipes`。

---

<a id="templates"></a>

## 🧩 模板与工厂

这两个是 CraftEngine 自带的功能，本配置里到处都在用。**看懂这两个，配置就看懂一大半。**

### 模板 `templates:` —— 把重复的写法抽出来

模板就是一段可以被别处引用的配置。定义时用 `${参数名}` 留空位，引用时用 `arguments:` 填。

```yaml
# template/block_settings.yml 里定义
templates:
  #金属厨具方块属性
  kaleidoscopecookery:settings/metal_appliance:
    template:
      - default:sound/metal          # 模板本身还能套模板
    overrides:
      hardness: ${hardness:-1.0}     # :- 后面是默认值 不填就用它
      resistance: ${resistance:-4.5}
      is_redstone_conductor: ${solid:-true}
      tags:
        - minecraft:mineable/pickaxe
```

```yaml
# appliance/pot.yml 里引用 只写与默认值不同的那两项
settings:
  template: kaleidoscopecookery:settings/metal_appliance
  arguments:
    resistance: 1.0
    solid: false
```

三个关键字：

| 关键字 | 作用 |
| ------ | ---- |
| `template:` | 要套用的模板，可以写一个，也可以写成列表套多个（按顺序深度合并） |
| `arguments:` | 给模板里的 `${}` 填值 |
| `merges:` | 在模板结果的基础上**追加**内容（map 深度合并，list 追加） |
| `overrides:` | 直接**替换**模板结果里的同名项 |

> ⚠️ 参数是从外往里传的，**外层已经定义过的参数名，内层再写同名的会被忽略**。所以嵌套模板里要另起一个参数名，例如菜品模板里每一口的高度参数叫 `stage_height` 而不是 `height`。

内置参数 `${__ID__}` 会自动替换成当前这条配置的 id（不含命名空间），例如 `kaleidoscopecookery:braised_fish` 里的 `${__ID__}` 就是 `braised_fish`。

### 工厂 `config_factory#` —— 一次生成一批

结构完全相同、只差几个值的东西（11 种木材的椅子、16 种颜色的桌布、51 道菜的展示模型）不用一条条抄，写一份蓝图加一张表就行：

```yaml
config_factory#chairs:
  instances:                       # 表 每行生成一份
    #橡木
    - wood: oak
    #云杉木
    - wood: spruce
  blueprint:                       # 蓝图 instances 里的字段当参数用
    items:
      #各木材椅子
      kaleidoscopecookery:chair_${wood}:
        template: kaleidoscopecookery:item/chair
```

**加一种新木材的椅子，只要往 `instances` 里加两行**（一行 `#中文名`，一行 `- wood: xxx`），再补上模型、贴图和翻译。

`blueprint` 下面可以放任何顶层配置段：`items:`、`blocks:`、`furniture:`、`recipes:`、`sounds:`、`images:` 都行。

---

<a id="item-tags"></a>

## 🏷️ 物品标签 `item_tags`

菜刀这种**一类东西**不要在每个行为里重复列一遍，统一在 `configuration/tag/` 下声明一次，行为里用 `#标签名` 引用。

```yaml
# configuration/tag/kitchenware.yml
item_tags:

  #菜刀
  kaleidoscopecookery:kitchen_knife:
    - craftengine:kaleidoscopecookery:iron_kitchen_knife
    - craftengine:kaleidoscopecookery:gold_kitchen_knife
    - minecraft:iron_sword                     # 原版物品直接写 id
    - othermod:steel_knife                     # 别的模组/插件的物品也行

  #全部厨具
  kaleidoscopecookery:kitchenware:
    - "#kaleidoscopecookery:kitchen_knife"     # 标签里还能套标签
    - craftengine:kaleidoscopecookery:stockpot_lid
```

行为里这样用：

```yaml
behaviors:
  - type: kaleidoscopecookery:chopping_board
    knives:
      - "#kaleidoscopecookery:kitchen_knife"
```

成员写法与命名规范表一致：`craftengine:命名空间:名字` 指 CraftEngine 自定义物品，其余按原版 / 模组 id 写。

**目前用到的标签：**

| 标签 | 用在哪 |
| ---- | ------ |
| `kaleidoscopecookery:kitchen_knife` | 砧板认哪些东西算菜刀 |
| `kaleidoscopecookery:kitchen_shovel` | 锅铲 |
| `kaleidoscopecookery:oil_pot` | 油壶 |
| `kaleidoscopecookery:range_harvest_tool` | 哪些工具走范围收割、因而不触发右键单点收割 |
| `kaleidoscopecookery:kitchenware` | 以上全部 |

标签是**运行时查的**，改完 `/ce reload all` 立刻生效，不用重载引用它的方块。
新加一把菜刀，只要往 `kaleidoscopecookery:kitchen_knife` 里加一行，砧板那边一个字都不用动。

---

<a id="block-tags"></a>

## 🧱 方块标签 `block_tags`

和 `item_tags` 是两套，一套管物品一套管方块，别写串了。写法一样，也放在 `configuration/tag/` 下。

```yaml
# configuration/tag/blocks.yml
block_tags:

  # 锄头能在水下开成耕地的方块（水田）
  kaleidoscopecookery:tillable:
    - "#minecraft:dirt"        # # 开头 = 原版方块标签，把里面的方块全拉进来
    - minecraft:dirt_path      # 也可以直接写方块 id
    - minecraft:farmland
```

**这里只管原版方块。** CraftEngine 自定义方块不看这个段，改在它自己的定义里加：

```yaml
settings:
  tags:
    - kaleidoscopecookery:tillable
```

两边**用的是同一个标签名**，插件查的时候会自动分流——因为 CE 自定义方块的 `getType()` 返回的是伪装用的视觉方块，拿它查材质会误判。

**目前用到的标签：**

| 标签 | 用在哪 |
| ---- | ------ |
| `kaleidoscopecookery:tillable` | 锄头能在水下开成耕地的方块（种水稻用） |

写成**空列表**表示"原版方块一个都不匹配"，和不写这个标签的效果不同——想彻底关掉水下开垦就把成员清空。

> **1.0.5 起：** 这份配置原来在 `config.yml` 的 `paddy.tillable`，已迁到这里。老配置**不再被读取**，如果你改过它，请把内容搬到 `block_tags` 下。好处是现在支持 `/ce reload all` 热重载，不用重启了。

---

<a id="hitboxes"></a>

## 📐 家具食物的碰撞箱

可摆放的菜（`dish/dishes.yml`）碰撞箱。菜品展示模型挂在 `position: 0,0.5,0` 的展示实体上，模型的 1 像素 = 1/16 格，所以：

```
高度 height = 模型最高的那个像素 ÷ 16
宽度 width  = 2 × max(|模型边界像素 − 8|) ÷ 16      向上取整到 1/16
```

- **高度按每一口分别算**，写成 `h0`、`h1`… 一口一个。吃到最后只剩个空盘子时，盘子只有 2 像素高，碰撞箱也就跟着降到 `0.125`，不会还杵着半格高的空气墙。
- **宽度取所有阶段里最宽的那一个**，因为盘子本身不会随着吃而变小。
- 交互箱是以家具原点为中心的正方体，所以宽度取的是"能盖住模型最远那一侧"的对称尺寸，而不是简单的 `最大 − 最小`。
- 双盘菜（红烧排骨、冷肉炙、油泼鱼）两个模型分别偏移 ±0.45 格，宽度计算里已经算进这个偏移。

```yaml
  #红烧鱼
  kaleidoscopecookery:braised_fish:
    template: kaleidoscopecookery:dish/stages_5
    arguments:
      width: 1.0        # 模型 x/z 从 1 到 16 像素 离中心最远 8 像素 → 16/16
      h0: 0.3125        # 模型最高 5 像素 → 5/16
      h1: 0.3125
```

改了模型就要跟着改这几个数，否则点不到或者点到空气。

---

<a id="customization"></a>

## 🛠️ 常见改动怎么做

### 加一道可摆放的菜

1. 把每一口的模型放进 `resourcepack/.../models/block/custom/cook/block/food/`，命名 `<菜名>_0.json` 到 `<菜名>_<口数-1>.json`；物品图标放进 `textures/item/custom/cook/food/<菜名>.png`。
2. `dish/dish_models.yml` 里找到对应口数的工厂（例如 5 口就是 `config_factory#food_fwd_5`），往 `instances` 里加两行。
3. `dish/dishes.yml` 里加一条：

```yaml
  #红烧鱼
  kaleidoscopecookery:braised_fish:
    template: kaleidoscopecookery:dish/stages_5      # 几口就用 stages_几
    arguments:
      texture: minecraft:item/custom/cook/food/braised_fish
      color: "#FFA500"                # 物品名颜色
      hit_times: 4                    # 打几下拆掉
      food: 3                         # 每一口回多少饱食
      saturation: 1.5                 # 每一口回多少饱和度
      leftover: minecraft:bowl        # 没吃完被打掉时掉的容器
      width: 1.0                      # 碰撞箱 按上一节算
      h0: 0.3125
      h1: 0.3125
      h2: 0.3125
      h3: 0.3125
      h4: 0.3125
      extra_effects: []               # 下面三项没有内容也要写成空列表 不能省
      eaten_pools: []
      broken_pools: []
```

4. `lang/*.yml` 里加 `item.kaleidoscopecookery.braised_fish` 的翻译。
5. 想让它进物品浏览器，往 `gui/categories.yml` 对应分类的 `list` 里加一行。

**这三项要填内容时长这样：**

```yaml
      # 吃下去额外给的效果 会追加在回饱食之后
      extra_effects:
        - type: potion_effect
          potion-effect: "minecraft:levitation"
          duration: 200
          amplifier: 0
      # 吃完最后一口额外掉的东西
      eaten_pools:
        - template: kaleidoscopecookery:dish/byproduct
          arguments:
            byproduct: minecraft:bone
      # 没吃完被打掉时额外掉的东西
      broken_pools:
        - template: kaleidoscopecookery:dish/byproduct_when_bitten
          arguments:
            byproduct: minecraft:bone
```

一道菜占两格宽时（红烧排骨、冷肉炙、油泼鱼）另外加一行，模型名要带 `_left_` / `_right_`：

```yaml
      plate: kaleidoscopecookery:dish/plate_wide
```

### 加一种茶壶液面（液体）

茶壶能烧的液体、烧的时候壶身液面进度条长什么样，都在 `recipe/teapot.yml` 的 `teapot_liquid` 段里。**加一种新液面分三步：**

**第一步**，在 `font/bar.yml` 里加满格图片。进度条是一族字体图片，`instances` 加一行就生成一张：

```yaml
config_factory#tea_bar_images:
  instances:
    #空茶格
    - id: tea_empty
      file: empty
    #满岩浆茶格
    - id: tea_lava_full
      file: lava_full
    #满蜂蜜茶格
    - id: tea_honey_full          # ← 新加这两行
      file: honey_full            #    对应 textures/font/bar/honey_full.png
```

**第二步**，在 `recipe/teapot.yml` 的 `teapot_liquid` 段里把液体和图片对上。键名就是液体方块的 id：

```yaml
teapot_liquid:
  #水
  minecraft:water:
    display_name: "kaleidoscopecookery.message.teapot.liquid.water"
    bar_left: kaleidoscopecookery:tea_left      # 进度条左端封口
    bar_right: kaleidoscopecookery:tea_right    # 进度条右端封口
    bar_empty: kaleidoscopecookery:tea_empty    # 还没烧到的那几格
    bar_water: kaleidoscopecookery:tea_water_full   # 已经烧好的那几格
  #蜂蜜
  minecraft:honey_block:
    display_name: "kaleidoscopecookery.message.teapot.liquid.honey"
    bar_left: kaleidoscopecookery:tea_left
    bar_right: kaleidoscopecookery:tea_right
    bar_empty: kaleidoscopecookery:tea_empty
    bar_honey: kaleidoscopecookery:tea_honey_full   # 键名 bar_<随便起> 用哪个都行
```

**第三步**，`lang/*.yml` 里补上 `display_name` 指向的翻译键。

想让这种液面能煮出东西，再往 `teapot_result` 里加配方，`fluid` 填液体 id：

```yaml
teapot_result:
  #蜂蜜柚子茶
  honey_citron_tea:
    template: kaleidoscopecookery:recipe/teapot
    arguments:
      fluid: "'minecraft:honey_block'"     # 带冒号的默认值要套单引号
      require: minecraft:sweet_berries
      result: honey_citron_tea
```

### 加一层搪瓷盆油量（另一种"液面"）

搪瓷盆的油面是**方块状态**做的，不是字体图片。它有 `oil_level` 0～4 五档，每档一个模型。加一档要动三处（`appliance/enamel_basin.yml`）：

```yaml
# 1. 把 oil_level 的上限调大
oil_level:
  type: int
  default: 0
  min: 0
  max: 5                       # 4 → 5

# 2. 四个朝向各加一个外观 level 就是模型名
appearances:
  full_north:
    template: kaleidoscopecookery:appearance/enamel_basin
    arguments:
      level: full
      yaw: 180
  # full_south / full_east / full_west 同理

# 3. 四个朝向各加一条状态映射
variants:
  facing=north,oil_level=5:
    appearance: full_north
  # 其余三向同理
```

最后在文件底部的 `config_factory#enamel_basin_displays` 里加一行 `- level: full`，展示模型就自动生成了，模型文件放 `models/block/custom/cook/block/enamel_basin/full.json`。

炒锅（`has_oil`）、汤锅（`has_lid` / `has_base` / `has_chains`）也是同一套写法：**加一个视觉状态 = 属性加一档 + 外观加一条 + variants 映射加一条 + 展示模型加一行**。

### 加一种木材的椅子 / 凳子 / 餐桌

只要往对应文件的工厂 `instances` 里加两行，再补模型贴图和翻译：

```yaml
# furniture/chairs.yml
config_factory#chairs:
  instances:
    #远古木
    - wood: ancient
```

需要的模型是 `models/block/custom/cook/block/chair/ancient.json`，翻译键是 `item.kaleidoscopecookery.chair_ancient`。
凳子在 `furniture/stool.yml`、餐桌在 `furniture/tables.yml`（餐桌还要七个端型的模型：`single` / `left` / `middle` / `right` 和三个 `_rot` 版本）。
合成配方分别在 `recipe/crafting_stools.yml` 和 `recipe/tables.yml`，也是往 `instances` 加一行。

### 加一段自定义音效

```yaml
# sound/cook.yml
sounds:
  #油炸声
  kaleidoscopecookery:deep_fry:
    template: kaleidoscopecookery:sound/loop
    arguments:
      subtitle: block.deep_fry            # 翻译键 subtitles.kaleidoscopecookery.block.deep_fry
      name: custom/cook/block/deep_fry    # 音频路径 sounds/custom/cook/block/deep_fry.ogg
      volume: 0.8                         # 可选 默认 1
      distance: 8                         # 可选 衰减距离 默认 5
```

一组随机播放的循环音（石磨、汤锅、茶壶就是这样）用工厂批量生成，`instances` 里 `- n: 0` 到 `- n: 5` 各一行。

### 加一种作物

`crop/crops.yml` 的 `blocks:` 段里加一条，套 `kaleidoscopecookery:block/crop_8stage` 模板：

```yaml
  #茄子作物
  kaleidoscopecookery:eggplant_crop:
    template: kaleidoscopecookery:block/crop_8stage
    arguments:
      crop: eggplant                                   # 模型目录名
      crop_item: kaleidoscopecookery:eggplant          # 收获物
      crop_seed: kaleidoscopecookery:eggplant_seed     # 种子
      visual_state: minecraft:potatoes[age=6]          # 借用的原版方块状态
      harvest_entries:
        - type: item
          item: kaleidoscopecookery:eggplant
```

再往文件底部 `config_factory#crop_stage_displays` 的 `instances` 加一行（八个阶段的展示模型自动生成），种子往 `item/generated_full_items.yml` 的 `config_factory#crop_seeds` 加一行。
可选参数：`grow_speed`（默认 0.125）、`light_requirement`（默认 9）、`bone_meal_min` / `bone_meal_max`（骨粉催熟的随机阶段数，默认 2～5）、`harvest_reset_age`（右键收割后退回第几阶段，默认 5）。

### 改石磨能被哪些生物拉

`entity/millstone_animals.yml`：

```yaml
millstone_animals:
  #熊猫
  panda:
    seconds: 35              # 拉完一圈要几秒 越大越慢
    allowed: true            # 能不能拉
    force_leash: false       # 原版拴不住的生物要不要强行允许拴绳
    interaction_disabled: true   # 拉磨时禁止右键交互 可选 默认 true
    orbit_radius: 2.5            # 绕磨盘走的半径 可选 默认 2.5
```

键名就是 Bukkit 的 `EntityType`，小写。玩家推磨固定 7.5 秒一圈，被打时会临时加速到骡子的速度。

### 改厨具的方块属性

不要在单个厨具里抄一份 `settings`，改 `template/block_settings.yml` 里的模板，全体生效；只想改一个的话在那个厨具里传参数：

```yaml
settings:
  template: kaleidoscopecookery:settings/metal_appliance
  arguments:
    hardness: 2.5        # 只有这台变硬 其余不受影响
```

| 模板 | 参数 |
| ---- | ---- |
| `settings/metal_appliance` | `hardness`（1.0）、`resistance`（4.5）、`solid`（true，是否挡红石与闷人） |
| `settings/wood_appliance` | `hardness`（1.0）、`resistance`（1.0） |
| `settings/wood_furniture` | `item`（必填）、`hardness`（2.0）、`resistance`（3.0） |
| `settings/hanging_string` | `map_color`（15） |
| `settings/crop` | `seed`（必填，挖掉时掉的种子） |

### 改家具的音效与掉落

同理，改 `template/furniture.yml`：

| 模板 | 参数 | 用在哪 |
| ---- | ---- | ------ |
| `furniture/metal_base` | `item`（必填）、`hit_times`（3） | 石磨、垃圾桶、菜谱板 |
| `furniture/wood_base` | 同上 | 椅子、凳子 |
| `furniture/armor_stand_base` | 同上 | 稻草人 |
| `furniture/base` | 上面三个的底座，另需 `break_sound` / `place_sound` / `hit_sound` | 自定义音效时用 |

摆放规则也有现成的：`furniture/rule_ground_four`（地上四向）、`furniture/rule_ground_any`（地上任意角度）、`furniture/rule_wall`（挂墙）。

### 改碰撞箱

`template/hitbox.yml` 提供三种：

| 模板 | 参数 | 说明 |
| ---- | ---- | ---- |
| `hitbox/shulker` | `scale`（必填）、`position`（0,0,0）、`peek`（0）、`direction`（up）、`blocks_building`（false）、`interactive`（false）、`interaction_entity`（true）、`can_use_item_on`（true）、`can_be_hit_by_projectile`（true） | 有真实碰撞，人穿不过去 |
| `hitbox/shulker_seat` | 同上，另加 `seat`（0,-0.05,0） | 上面那个 + 可以坐 |
| `hitbox/interaction` | `width`、`height`（都必填），其余同上 | 只能点，没有碰撞 |

潜影贝最矮也是一整格，只能靠 `scale` 缩小、`peek`（0～100）往上撑；想拼出不规则形状就叠几个，椅子的坐面和靠背就是这么拼的。


---

<a id="languages"></a>

## 🌐 多语言

**控制台语言**在插件 `config.yml` 的 `console.language` 中设置，支持以下 12 个 locale：

| 语言 | locale | 语言 | locale |
| --- | --- | --- | --- |
| 简体中文 | `zh_cn` | English | `en_us` |
| 繁體中文（台灣） | `zh_tw` | Español | `es_es` |
| 繁體中文（香港） | `zh_hk` | 日本語 | `ja_jp` |
| 文言 | `lzh` | 한국어 | `ko_kr` |
| Français | `fr_fr` | Русский | `ru_ru` |
| Italiano | `it_it` | Deutsch | `de_de` |

修改后重启服务器；执行 `/ce reload all` 时，本插件的 CraftEngine 自检日志也会重新读取该配置。

**客户端翻译**与控制台语言独立。物品名称、分类名和菜品描述通过内容包的翻译键（如 `<lang:...>`）提供，实际支持的语言与翻译覆盖范围以安装的完整内容包为准。玩家需要接受包含这些翻译的资源包，并在客户端选择相应语言；仓库增补文件不代表完整内容包的所有文本均已提供 12 种翻译。

---

<a id="placeholders"></a>

## 🔌 PlaceholderAPI 变量

服务器安装 PlaceholderAPI 时，插件会自动注册内置 expansion。变量前缀为 `kaleidoscopecookery`，可直接在菜单、计分板、聊天格式等支持 PAPI 的插件中使用。

| 变量 | 含义 |
| ---- | ---- |
| `%kaleidoscopecookery_version%` | 当前插件版本 |
| `%kaleidoscopecookery_enabled%` | 插件是否已启用，返回 `true` / `false` |
| `%kaleidoscopecookery_loaded%` | `enabled` 的同义变量 |
| `%kaleidoscopecookery_recipes_total%` | 已加载的烹饪配方总数 |
| `%kaleidoscopecookery_recipes_flex_total%` | 已加载的动态配方总数，主要用于炒锅 / 高汤锅 |
| `%kaleidoscopecookery_recipes_accurate_total%` | 已加载的单输入精准配方总数，主要用于蒸笼 / 石磨 / 沙威玛 |
| `%kaleidoscopecookery_recipes_chopping_total%` | 已加载的砧板配方数量 |
| `%kaleidoscopecookery_recipes_teapot_total%` | 已加载的茶壶配方数量 |
| `%kaleidoscopecookery_teapot_liquids_total%` | 已注册的茶壶液体类型数量 |
| `%kaleidoscopecookery_tea_cups_total%` | 已注册的茶杯展示定义数量 |

也可以按厨具查询配方数量：

| 变量 | 含义 |
| ---- | ---- |
| `%kaleidoscopecookery_recipes_pot_total%` | 炒锅配方数量 |
| `%kaleidoscopecookery_recipes_stockpot_total%` | 高汤锅配方数量 |
| `%kaleidoscopecookery_recipes_steamer_total%` | 蒸笼配方数量 |
| `%kaleidoscopecookery_recipes_shawarma_total%` | 沙威玛烤架配方数量 |
| `%kaleidoscopecookery_recipes_millstone_total%` | 石磨配方数量 |
| `%kaleidoscopecookery_recipes_chopping_board_total%` | 砧板配方数量 |
| `%kaleidoscopecookery_recipes_teapot_total%` | 茶壶配方数量 |

变量名中的 `recipe_...` 单数写法也兼容，例如 `%kaleidoscopecookery_recipe_total%`。

<a id="containers"></a>

## 🥣 退还容器 `dish_carrier`

吃完一道菜退还碗、茶杯、竹筒这类容器。物品菜与家具菜两条进食路径查同一张表，不往物品里存 NBT，所以改完 `/ce reload all` 老物品也跟着走。

容器**掉在吃完的位置**，不直接进背包：手持物品吃完掉在玩家脚下，摆盘家具菜掉在家具处——和同一段 `eat_functions` 里 `drop_loot` 掉 `eaten_pools` 的位置一致，所以碗会和骨头、竹子这类副产物落在一起。

炒锅与高汤锅的模糊配方自带 `carrier`，会自动进表，**不用在这里重复写**。合成、蒸笼、茶壶这三条路没有 `carrier` 字段，只能在这里补：

```yaml
# configuration/dish/dish_carrier.yml
dish_carrier:
  kaleidoscopecookery:barley_tea: kaleidoscopecookery:empty_cup
  kaleidoscopecookery:bamboo_tube_rice: minecraft:bamboo
  kaleidoscopecookery:braised_beef_rice_bowl: minecraft:bowl
```

键是成品 id，值是退还的物品 id，两边都支持原版与 CraftEngine 自定义物品。同一个成品在这里和模糊配方里都写了的话，**以这里为准**。

> ⚠️ 自带 `use_remainder` 的原版物品（谜之炖菜等）别写进来，原版已经会退一个，写了就退两个。炒锅的失败与烧糊产物（谜之炒菜、黑暗料理）是自定义物品，没有这层，需要在这里补。

---

<a id="behaviors"></a>

## 🎛️ 行为（behaviors）配置参考

下列配置写在 CraftEngine 的方块 / 家具 / 物品定义里的 `behaviors:` 段。键名同时支持下划线与连字符两种写法（`stir_fry_count` 与 `stir-fry-count` 等价）。下面列出的都是**默认值**，只写与默认不同的那几项即可。

> **文案不在这里配。** 所有玩家可见的提示语都走翻译键，翻译键定义在 `net.kaleidoscope.cookery.util.MessageKeys`，文案本体在 `configuration/lang/*.yml`。想改提示语就去改 lang 文件，行为段里写 `msg_xxx` 是没用的。

> **`animation_view_distance`**（仅**有动画**的机型：炒锅、高汤锅、沙威玛、石磨、垃圾桶、茶壶）：动画帧发包的区块视距，切比雪夫距离，默认 `1`。离机型超过该区块距离的玩家不再收到动画包——反正太远也渲染不出来。想更远可见就调大，想进一步省发包就调小。
>
> **粒子发包不受此项控制**：所有机型的粒子统一只发给粒子点约 8 格球形范围内的玩家（Paper `ParticleBuilder.receivers`），固定不可配。所以纯出粒子、无动画的机型（炉灶、蒸笼、砧板）**没有** `animation_view_distance` 这一项。

### 🥘 炒锅 `kaleidoscopecookery:cooking_pot`

倒油、翻炒、投料、盛出、记录食谱。

```yaml
behaviors:
  - type: kaleidoscopecookery:cooking_pot
    stir_fry_count: 6            # 出锅所需翻炒次数
    cook_done_time: 200          # 出锅后多少 tick 进入烧焦阶段（-1 = 永不烧焦）
    burnt_to_charcoal_time: 400  # 烧焦后多少 tick 变成木炭
    animation_view_distance: 1   # 翻炒动画发包视距（区块）
    stir_fry_damage_chance: 0.25 # 每次翻炒磨损锅铲的概率（0 = 不磨损，创造模式恒不磨损）
    stir_fry_damage: 1           # 磨损时扣多少点耐久
    # 配不出菜时的产物，以及盛它要拿什么。容器写 minecraft:air 表示空手就能盛
    failed_result_item: kaleidoscopecookery:suspicious_stir_fry
    failed_result_carrier: minecraft:bowl
    # 出锅后过了盛出窗口烧糊的产物
    burnt_result_item: kaleidoscopecookery:dark_cuisine
    burnt_result_carrier: minecraft:bowl
    oil_item: kaleidoscopecookery:oil
    shovel_item: kaleidoscopecookery:kitchen_shovel_no_oil          # 锅铲物品，油状态存在物品上
    shovel_oil_model: kaleidoscopecookery:kitchen_shovel_has_oil    # 沾油时切到的 item_model
    oil_pot_item: kaleidoscopecookery:oil_pot
    oil_pot_empty_item: kaleidoscopecookery:oil_pot_empty
    recipe_item_no_recipe: kaleidoscopecookery:recipe_item_no_recipe
    recipe_item_has_recipe: kaleidoscopecookery:recipe_item_has_recipe
    bowl_item: minecraft:bowl    # 只是默认盛具 每道菜实际用什么由配方的 carrier 决定
```

### 🔥 炉灶 `kaleidoscopecookery:stove`

点火（打火石 / 火焰弹）与熄火（铲子、降雨、上方水流），切换 `lit` 状态。

```yaml
behaviors:
  - type: kaleidoscopecookery:stove
    extinguish_kitchen_shovel_item: kaleidoscopecookery:kitchen_shovel_no_oil  # 原版各种铲子始终可熄火，沾了油的锅铲不能
    shovel_oil_model: kaleidoscopecookery:kitchen_shovel_has_oil               # 判断锅铲沾没沾油，与锅保持一致
    particle_interval: 20        # 每隔多少 tick 出一次火焰/烟雾 越大发包越少
    particle_count: 3            # 一个包内塞几颗粒子 越大越密但不增发包
```

### 🫕 搪瓷盆 `kaleidoscopecookery:cooking_enamel_basin`

右键开合、加油 / 取油、厨铲沾油。

```yaml
behaviors:
  - type: kaleidoscopecookery:cooking_enamel_basin
    max_oil: 16                  # 盆的油容量上限
    oil_item: kaleidoscopecookery:oil
    shovel_item: kaleidoscopecookery:kitchen_shovel_no_oil
    shovel_oil_model: kaleidoscopecookery:kitchen_shovel_has_oil
```

> 锅铲已合并成一个物品，沾没沾油存在物品上，靠切 `item_model` 换外观。
> `shovel_oil_model` 指向的模型由 `kitchen_shovel_has_oil` 这个物品定义生成，
> 旧沾油锅铲的同名模型条目用于兼容现有物品；移除该模型会导致沾油铲显示为缺失模型。

### 🍲 高汤锅 `kaleidoscopecookery:stockpot`

盖 / 揭锅盖、加 / 舀汤底、加 / 取食材、盛出成品。

```yaml
behaviors:
  - type: kaleidoscopecookery:stockpot
    cooking_time: 400            # 盖盖后多少 tick 炖煮完成
    particle_interval: 20
    particle_count: 3
    animation_view_distance: 1
    lid_item: kaleidoscopecookery:stockpot_lid
    bowl_item: minecraft:bowl
    recipe_item_no_recipe: kaleidoscopecookery:recipe_item_no_recipe
    recipe_item_has_recipe: kaleidoscopecookery:recipe_item_has_recipe
    # 配不出菜时的产物，以及盛它要拿什么。容器写 minecraft:air 表示空手就能盛
    failed_result_item: minecraft:suspicious_stew
    failed_result_carrier: minecraft:bowl
```

> 高汤锅的失败产物默认是原版**谜之炖菜**，它自带 `use_remainder`，吃完原版会自己退一个碗，所以**别**再写进 `dish_carrier`。换成自定义物品的话就需要在那边补一条。

### 🫔 蒸笼 `kaleidoscopecookery:steamer`

右键放料 / 取料、盖盖子、堆叠蒸笼，失去支撑时掉落。

```yaml
behaviors:
  - type: kaleidoscopecookery:steamer
    cooking_time: 200            # 每个食材蒸熟所需 tick
    campfire_stack_height: 8     # 在篝火等热源上最多叠几层
    stove_stack_height: 16       # 在炉灶上最多叠几层
    particle_interval: 20
    particle_count: 3
```

### 🔪 砧板 `kaleidoscopecookery:chopping_board`

右键放原料、手持菜刀逐刀切割、切满产出加权成品、空手取回未切完的料。

```yaml
behaviors:
  - type: kaleidoscopecookery:chopping_board
    # 不写这一项时默认就认 #kaleidoscopecookery:kitchen_knife 这个标签
    knives:
      - "#kaleidoscopecookery:kitchen_knife"
      - "craftengine:otherplugin:custom_knife"
      - othermod:steel_knife
      - minecraft:iron_sword
```

> 想加一把新菜刀，正常做法是往 `configuration/tag/kitchenware.yml` 的 `kaleidoscopecookery:kitchen_knife` 里加一行，砧板这边不用动。见 [物品标签](#item-tags)。

### 🍢 沙威玛烤架 `kaleidoscopecookery:shawarma_spit`

右键放料 / 取料，红石信号控制旋转，上下两半联动。

```yaml
behaviors:
  - type: kaleidoscopecookery:shawarma_spit
    grill_time: 300              # 每个食材烤熟所需 tick
    animation_view_distance: 1
```

### 🪨 石磨 `kaleidoscopecookery:millstone`

玩家推磨或拴绳牵生物拉磨研磨食材。**研磨按圈数产出**：转满所需圈数产出一批，真实耗时由拉磨者的转速（秒/圈）决定——转得慢就产得慢。转速见下方 `millstone_animals`；玩家与村民被打时会临时加速到骡子的速度。每种食材的所需圈数默认取 `grind_rotations`，精准配方可用 `rotations` 各自覆盖。

```yaml
behaviors:
  - type: kaleidoscopecookery:millstone
    grind_rotations: 4                       # 每批产出所需圈数 精准配方可用 rotations 覆盖
    animation_view_distance: 1
    stick_item: show:new_millstone_stick     # 中心自转棍展示模型
    stick2_item: show:new_millstone_stick2   # 公转支架展示模型
    stone_item: show:new_millstone_stone     # 横向滚动磨石展示模型
```

玩家推磨的手感调参（除非要改推磨判定，否则别动）：

```yaml
    push_bar_length: 2.5         # 推杆长度 也就是玩家绕磨心走的半径 默认同 orbit_radius
    push_contact_tolerance: 20   # 玩家角度与推杆角度差多少度以内算贴着推杆
    push_lead_tolerance: 60      # 玩家最多能领先推杆多少度 超了就断开
    push_max_seconds: 3.5        # 玩家离开推杆多久后判定为停止推磨（秒）
    push_shove_strength: 0.45    # 推杆推着玩家走的力度
    push_resist_strength: 0.3    # 玩家推超前时的回拽力度
    push_angle_offset: 0         # 推杆视觉角与判定角的偏移（度）模型换了才需要改
```

#### 拉磨生物 `millstone_animals`

独立配置段，现在放在 `configuration/entity/millstone_animals.yml`。键名是 Bukkit 的 `EntityType`，小写。不写这个文件就用内置默认值。

```yaml
millstone_animals:
  #牛
  cow:
    seconds: 40                 # 拉一圈所需秒数 数值越小越快
    allowed: true               # 是否允许该生物拉磨
    force_leash: false          # 原版不能被拴的生物 设 true 后手持拴绳右键可强制拴上
    interaction_disabled: true  # 拉磨时是否禁用对它的右键 驴骡开箱加料始终放行
    orbit_radius: 2.5           # 绕磨半径 即起始与行走位置离磨心的距离
```

内置默认（秒每圈）：骡 6、村民 7.5、驴 10、马 / 骷髅马 25、羊驼 / 行商羊驼 30、牛 / 哞菇 40、绵羊 / 山羊 50。村民 `force_leash: true`，其余为 false。

想接入 MythicMobs 等插件的自定义生物，注册一个 Provider 即可：

```java
import net.kaleidoscope.cookery.api.MillstoneAnimals;

// Profile(秒每圈, 是否允许, 是否强制拴绳, 是否禁右键, 绕磨半径)
MillstoneAnimals.instance().addProvider(entity ->
        isMyCustomMob(entity) ? new MillstoneAnimals.Profile(20, true, true, false, 3.0) : null);
```

石磨支持的生物的刷怪蛋右键石磨即可直接生成并开始拉磨。

### 🫖 茶壶 `kaleidoscopecookery:teapot`

壶身与盖子由假实体渲染，烧水动画与蒸汽粒子由这个行为发。

```yaml
behaviors:
  - type: kaleidoscopecookery:teapot
    animation_view_distance: 1
    particle_interval: 20
    particle_count: 3
```

### ☕ 茶杯垫 `kaleidoscopecookery:teacup_coaster`

右键放杯 / 倒茶 / 取茶。杯中茶水的展示模型由 `tea_cup` 配方段决定，这里只配空杯。

```yaml
behaviors:
  - type: kaleidoscopecookery:teacup_coaster
    empty_cup_display_model: show:empty_cup      # 空杯的展示模型
    empty_cup_item: kaleidoscopecookery:empty_cup # 取回时给玩家的物品
    cup_y_offset: 0.5            # 杯子相对垫子的高度
    cup_scale: 1.0               # 杯子缩放
```

### 🪑 椅子 `kaleidoscopecookery:chair`

家具行为。坐下由碰撞箱的 `seats` 提供，这个行为只管椅垫（右键地毯铺 / 换色，空手取回）。

```yaml
behaviors:
  - type: kaleidoscopecookery:chair
    carpet_offset: 0,0.5,0       # 椅垫相对家具原点的偏移
    carpet_scale: 1.0            # 椅垫缩放
    yaw_offset: 0                # 椅垫朝向偏移（度）
```

### 🗑️ 垃圾桶 `kaleidoscopecookery:trashcan`

家具行为。支持投放、取出与进入桶内躲藏。

- 手持物品右键投放，最多存 3 件，满了挤掉最旧的一件。
- 空手右键取出（倒序返还）。
- 跳起落到桶顶、落差大于 1 自动进入桶内：切旁观并把视角固定到桶口的相机实体，戴头盔由客户端渲染遮罩，潜行退出。
- 投放、取出、进入分别播放桶盖开合、进入摆动与占用待机动画；有玩家在里面时禁止投放取出，桶内掉落物对所有人隐藏（仍保存）。

```yaml
behaviors:
  - type: kaleidoscopecookery:trashcan
    animation_view_distance: 1
    helmet_item: kaleidoscopecookery:trashcan_helmet  # 躲进去时给玩家戴的头盔 遮罩靠它
    allow_hiding: true           # 躲桶会把玩家切成旁观 等于无冷却避难所 PvP 服可整个关掉
    hide_permission: ""          # 留空表示所有人都能躲 填权限节点则只放给有权限的人
```

> 遮罩复用装备遮盖层：资源包的 `assets/minecraft/textures/misc/zhezhao.png` 就是垃圾桶遮罩（透明缝隙版），由 `trashcan_helmet` 的 `camera_overlay` 指向。

### 🌾 稻草人 `kaleidoscopecookery:scarecrow`

家具行为。范围内耕地不被踩坏、鹦鹉停肩、三个装备槽（头 / 主手 / 副手）。

```yaml
behaviors:
  - type: kaleidoscopecookery:scarecrow
    protection_radius: 16        # 耕地保护半径
    parrot_perch: true           # 是否允许鹦鹉停在肩上
    parrot_scan_interval: 40     # 多少 tick 扫一次附近鹦鹉
    parrot_pickup_range: 2.0     # 鹦鹉被吸附上肩的距离
    shoulder_offset: 0.72,1.62,0 # 鹦鹉停肩的位置
    lantern_light: 15            # 手上挂灯笼时的发光等级
    interact_cooldown: 5         # 右键冷却 tick 防止一次点击触发两次
    body_offset: 0,0.5,0         # 身体展示模型的偏移
    slots:                       # 三个槽各自的展示参数与交互箱
      head:
        position: 0,2.25,0
        scale: 1.125
        item_transform: 5        # ItemDisplayContext 序号 0=NONE 2=三人称右手 5=头部
        yaw_offset: 180
        hitbox:
          template: kaleidoscopecookery:hitbox/scarecrow
          arguments:
            position: 0,1.65,0
            width: 0.7
```

`main_hand` 与 `off_hand` 写法相同，默认值见 `configuration/furniture/scarecrow.yml`。

### 🌶️ 悬挂串 `kaleidoscopecookery:hanging_string`

辣椒串、蘑菇串这类挂着的串。右键采摘一次，采过的段换成 `sheared` 外观，再摘整段消失。

```yaml
behaviors:
  - type: kaleidoscopecookery:hanging_string
    harvest_item: kaleidoscopecookery:red_chili   # 采摘掉什么 必填
    harvest_amount: 3                             # 一次掉几个
    harvest_sound: minecraft:block.sweet_berry_bush.pick_berries
```

### 🌱 作物收割 `kaleidoscopecookery:crop_harvest`

挂在作物方块上，成熟后右键直接收一茬并退回指定生长阶段，不用破坏重种。

```yaml
behaviors:
  - type: kaleidoscopecookery:crop_harvest
    reset_age: 5                 # 收割后退回第几阶段
    sound: minecraft:block.crop.break
    blacklist:                   # 手持这些东西时不走右键收割 免得和范围收割掉两份
      - "#kaleidoscopecookery:range_harvest_tool"
    loot:                        # 收割掉落 不写则用方块自己的掉落表
      pools:
        - rolls: 1
          entries:
            - type: item
              item: kaleidoscopecookery:tomato
```

### 🌾 水稻 `kaleidoscopecookery:rice_crop`

三段高的水稻，自带生长、夜间蛙鸣、水生生物加速与右键收割（收割部分的键与 `crop_harvest` 相同）。

```yaml
behaviors:
  - type: kaleidoscopecookery:rice_crop
    grow_speed: 0.0625           # 每次随机刻的生长概率
    light_requirement: 9         # 生长所需最低光照
    max_light_requirement: 15    # 生长所需最高光照
    bone_meal_age_bonus:         # 骨粉一次催熟几个阶段
      type: uniform
      min: 1
      max: 2
    booster_entities:            # 附近有这些生物时加速生长
      - minecraft:cod
      - minecraft:axolotl
    booster_radius: 4            # 加速生物的搜索半径
    booster_cache_ticks: 40      # 生物扫描结果缓存多少 tick
    night_sound: kaleidoscopecookery:paddy   # 夜间蛙鸣音效 不写则不放
    night_sound_cell_size: 8     # 按多大的格子合并蛙鸣 避免一片稻田几百个声源
    night_sound_cooldown: 40     # 同一格子多少 tick 内只响一次
    night_sound_volume_step: 0.15  # 格子里每多一株音量加多少
    night_sound_max_bonus: 0.4     # 音量加成上限
    harvest_reset_age: 1         # 右键收割后退回第几阶段
    harvest_blacklist:
      - "#kaleidoscopecookery:range_harvest_tool"
```

### 🪣 油壶 `kaleidoscopecookery:oil_pot` / `kaleidoscopecookery:oil_pot_item`

放地上的油壶方块（空手右键取油、手持油脂右键加油）与手上的油壶物品（耐久就是剩余油量）。**三处 `max_oil` 与物品的 `max_damage` 必须一致**，对不上会让耐久与油量的换算错位。

```yaml
# 物品行为 放下时把耐久换算成方块里的油量
behavior:
  type: kaleidoscopecookery:oil_pot_item
  max_oil: 256
  block:
    behaviors:
      # 方块行为
      - type: kaleidoscopecookery:oil_pot
        max_oil: 256
        oil_item: kaleidoscopecookery:oil
        pot_item: kaleidoscopecookery:oil_pot
        empty_pot_item: kaleidoscopecookery:oil_pot_empty
```

### 🌿 镰刀 `kaleidoscopecookery:sickle_range_harvest`

物品行为。右键范围收割作物。

```yaml
behavior:
  type: kaleidoscopecookery:sickle_range_harvest
  horizontal_radius: 2         # 水平半径 最大 8
  cooldown_ticks: 10           # 两次收割的冷却
  damage_item: true            # 是否扣耐久
  durability_per_use: 1        # 每次扣多少耐久
  crops:                       # 额外算作作物的方块 不写则用内置判定
    - minecraft:wheat
  bushes:                      # 额外算作灌木的方块
    - minecraft:sweet_berry_bush
  blacklist:                   # 不收割这些方块
    - minecraft:pitcher_crop
```

### 📦 叠放额外掉落 `kaleidoscopecookery:stacked_extra_drop`

给"一个方块其实是两层"的机型补掉落，例如整格蒸笼打掉要掉两个半格蒸笼。

```yaml
behaviors:
  - type: kaleidoscopecookery:stacked_extra_drop
    extra_drops:
      - property: type         # 看哪个方块状态属性
        value: double          # 等于这个值时
        item: kaleidoscopecookery:steamer   # 额外掉这个
        amount: 1
```

### 🧩 其它行为

无配置项，直接挂上即可：

| 行为 | 用在哪 |
| ---- | ---- |
| `kaleidoscopecookery:kitchenware_racks` | 厨具架 |
| `kaleidoscopecookery:recipe_furniture` | 菜谱板家具 |
| `kaleidoscopecookery:fruit_basket` | 果篮 |
| `kaleidoscopecookery:table` | 餐桌拼接与桌布 |
| `kaleidoscopecookery:transmutation_lunch_bag` | 嬗变饭袋 |
| `kaleidoscopecookery:teapot_item` | 手持茶壶倒茶 |

---

<a id="recipes"></a>

## 🍳 配方（recipes）配置参考

推荐通过 `/kcrecipe edit`（别名 `/kcfood edit`）编辑配方，保存时会校验来源并写回原文件。手工修改前请先备份，修改后执行 `/ce reload all`。

配方由 `FoodRecipeManager` 解析，注册到对应的注册表中。每种配方是一个独立的顶层配置段，可以出现在资源包的任意 yml 里。

**通用约定：**

- `require` / `result` 这类"物品加数量"的项写作 `"物品id 数量"`，数量缺省为 1。
- 精准配方与砧板配方的 `result` 可以是单个标量，也可以是 `"物品id 数量 权重"` 的列表。
- 配方 id 随便起，只要不重复；它会显示在 `/kcrecipe` 菜单里，也是重载时定位来源文件的依据。

<a id="ingredient-whitelist"></a>

### 🥬 可下锅食材白名单

决定什么东西**能被放进锅里**，与"放进去能出什么菜"是两回事（后者看模糊配方）。

```yaml
# 炒锅
pot_food_raw:
  items:
    - minecraft:beef
    - minecraft:potato

# 高汤锅
stock_food_raw:
  liquid:                        # 汤底表 只有这一段有特殊含义
    #水桶
    - item: minecraft:water_bucket
      show: show:stove_water     # 锅里的液面模型 不写默认画成水
    #岩浆桶
    - item: minecraft:lava_bucket
      show: show:stove_lava
  items:
    - minecraft:beef
    - minecraft:cod
```

> **`items` 这个键名本身没有含义。** 除了高汤锅的 `liquid` 之外，这一段下面所有子键的列表都会被合并成同一张白名单，键名只当注释用。老配置里 `meat:` / `vegetable:` 那种分组照样能读——分类轴早就废除了，引擎只关心并集里有哪些 id。
>
> 另外，**模糊配方 `perfect` 里出现过的食材会自动进白名单**，不用再手动列一遍。这里主要用来放那些"能下锅但配不出菜"的料（下去只能出迷之炒菜）。

<a id="flex-recipes"></a>

### 🔀 模糊配方 `pot_flex_foods` / `stock_flex_foods`

炒锅与高汤锅用。**匹配走并集余弦取最近邻，不是条件判定**——你不需要写"至少要几个肉"这种门槛，只要给出这道菜的**理想配比** `perfect`，引擎会拿锅里实际投料的向量跟每道菜的理想向量算夹角，取最接近的那道。

```yaml
pot_flex_foods:
  #红烧鱼
  kaleidoscopecookery:braised_fish_from_cod:
    result: kaleidoscopecookery:braised_fish
    carrier: minecraft:bowl      # 盛具 写 minecraft:air 表示空手就能盛
    perfect:                     # 理想配比 6 条鳕鱼配 1 个红辣椒
      minecraft:cod: 6
      kaleidoscopecookery:red_chili: 1

stock_flex_foods:
  #番茄牛腩汤
  kaleidoscopecookery:tomato_beef_brisket_soup:
    result: kaleidoscopecookery:tomato_beef_brisket_soup
    carrier: minecraft:bowl
    liquid:                      # 仅高汤锅 当前汤底须命中其一 不写表示不限汤底
      - minecraft:water_bucket
    perfect:
      minecraft:beef: 3
      kaleidoscopecookery:tomato: 4
```

**字段：**

| 字段 | 必填 | 含义 |
| ---- | ---- | ---- |
| `result` | ✅ | 成品物品 id |
| `perfect` | ✅ | 理想配比，`物品id: 份量`。份量只有**相对比例**有意义，`{牛肉:3, 番茄:4}` 与 `{牛肉:6, 番茄:8}` 是同一个方向 |
| `carrier` | | 盛具。省略或写 `minecraft:air` = 空手就能盛出 |
| `liquid` | | 仅高汤锅。当前汤底桶 id 须命中列表里的其一，不写表示任何汤底都行 |
| `use_equivalent_foods` | | 这道菜认不认[等效食物表](#food-groups)，默认 `true` |
| `use_seasonings` | | 这道菜认不认[调味品表](#food-groups)，默认 `true` |

`perfect` 也可以写成字符串列表（老写法，等价）：

```yaml
    perfect:
      - "minecraft:beef 3"
      - "kaleidoscopecookery:tomato 4"
```

**匹配是怎么算的：**

1. 把锅里的投料数成一个向量（`{牛肉:2, 番茄:3}`），把每道菜的 `perfect` 也当向量。
2. 对每道菜算两个向量的余弦（并集维度）。**缺料**会让理想向量的那一维落空，**乱加料**会让实际向量多出无用模长，两种偏差都只会拉低余弦。
3. 取余弦最高的那道菜。**同分时按配置里的先后顺序取先者。**
4. 最高分低于 `0.15` 就干脆不出菜，由炒锅产出迷之炒菜。

**份数**按凑齐了几套完整理想配比算（每种料各能凑几套，取最小值），不是拿总投料数除。配料不齐也至少给一份，但品质会被压到最低档。

**品质**在选出菜之后再算一遍，这次不只看方向还看量：余弦取四次方锐化，再乘一个"投料总数与该份数所需总数的贴合度"（多了少了都打折，差一倍打到 0.5）。最终得分决定档位：

| 档位 | 最低得分 | 食物属性倍率 | 颜色 |
| ---- | ---- | ---- | ---- |
| 完美 `SUPERB` | 0.95 | ×1.2 | 金 |
| 优秀 `EXCELLENT` | 0.82 | ×0.9 | 绿 |
| 普通 `STANDARD` | 0.55 | ×0.6 | 白 |
| 生疏 `POOR` | 0 | ×0.3 | 深灰 |

> 倍率作用于饱食度、饱和度与效果时长。物品自己声明的食物属性是**上限**，正常发挥只拿 `STANDARD` 的 0.6 —— 想让某道菜吃起来更实在，调物品的 `minecraft:food` 组件，别去调倍率。

> ⚠️ **两道菜的理想配比方向不能相同。** 余弦是尺度无关的，`{牛肉:1}` 和 `{牛肉:2}` 方向完全一致，会永远打平。加载期就会检出来并报错跳过后注册的那条。高汤锅里汤底不重叠的两条配方不算冲突（水底饺子和岩浆底生煎馒头就是同一个向量，但永远碰不到一起）。

<a id="food-groups"></a>

### 🧂 等效食物表与调味品表 `equivalent_foods` / `seasonings`

两张表都只列 `item_tags` 里的标签，成员由标签自己定义。模糊配方默认两张都认，单条配方可以关掉。**只对炒锅和高汤锅有意义**，精准配方是一进一出，不走这套。

```yaml
# configuration/tag/food_groups.yml
item_tags:
  kaleidoscopecookery:equivalent_red_meat:
    - minecraft:beef
    - minecraft:porkchop
    - minecraft:mutton
  kaleidoscopecookery:seasoning:
    - minecraft:sugar

equivalent_foods:            # 同一标签内的食材互相顶替
  tags:
    - "#kaleidoscopecookery:equivalent_red_meat"
seasonings:                  # 只占位 不参与任何计算
  tags:
    - "#kaleidoscopecookery:seasoning"
```

> `tags:` 这层**不能省**。CraftEngine 只把值是映射的顶层键分发给解析器，顶层直接写成列表会被**静默跳过**——不报错也不加载。子键叫什么不重要，同一段下所有子键的列表会并起来。

**等效食物**：配方 `perfect` 里照常写具体物品。开着这个开关时，写牛肉的菜用猪肉羊肉一样能做出来，且同组食材的数量**合并计算**——牛肉 1 块加猪肉 1 块，对只要 2 份红肉的配方来说就是刚好齐了。一个食材落在多个等效标签里时取 `equivalent_foods` 里**先声明**的那个。

**调味品**：加进锅里只占一格，不进理想配比、不算杂料、不影响品质，也不参与选菜。

两类食材都**不需要**手动加进[下锅白名单](#ingredient-whitelist)：调味品进了表就能下锅；等效组里只要有一个成员已在白名单里（通常是被某条配方的 `perfect` 带进去的），整组都能下锅。所以给 `minecraft:egg` 加上棕蛋蓝蛋这种事，只改标签就够了，配方和白名单都不用动。

关掉某条配方的开关后，那条配方退回按具体物品严格匹配，调味品对它就是普通杂料，会拉低品质：

```yaml
pot_flex_foods:
  kaleidoscopecookery:strict_dish:
    result: kaleidoscopecookery:strict_dish
    use_equivalent_foods: false
    use_seasonings: false
    perfect:
      minecraft:beef: 2
```

> 两张表**一填上就对所有没关开关的配方立刻生效**。两段都删掉或留空即关闭该功能——插件不会自己生成配置，读不到就是空表。

**菜单里管理分组**：`/kcrecipe edit` → 首页底排「食材分组」。列表里能新建 / 编辑 / 删除，编辑页设标签 id、加减物品、左键切换用途（等效食材 ↔ 调味品），保存即写回 `food_groups.yml` 并立刻生效。手改文件与菜单改效果一致，同一个文件。

**单条配方的两个开关**：`/kcrecipe edit` → 选厨具 → 选配方，编辑页底排两个按钮左键切换，保存时只有关掉的那项会写进 yml。

> 菜单写入的成员一律存成纯 id（不带 `craftengine:` 前缀）。这两张表按物品 id 精确匹配，不走原版材质回退，所以纯 id 就够；手写的 `craftengine:` 前缀在被菜单编辑后会被归一成纯 id，匹配结果不变。

<a id="processing"></a>

### ⏱️ 每条配方的加工时间与次数

每条配方可单独设置加工量，沿用各厨具原有玩法。新字段是可选的：没有覆盖值时，仍使用原厨具配置；已有资源包无需批量补字段，也无需调整方块状态、屏障用法或模型。

| 厨具 | 配方段 | 配方字段 | 未设置时 |
| ---- | ---- | ---- | ---- |
| 蒸笼 | `accurate_foods`，`cook: steamer` | `cooking_time` / `cooking-time` | 厨具行为的 `cooking_time` |
| 沙威玛烤架 | `accurate_foods`，`cook: shawarma` | `cooking_time` / `cooking-time` | 厨具行为的 `grill_time` |
| 高汤锅 | `stock_flex_foods` | `cooking_time` / `cooking-time` | 厨具行为的 `cooking_time` |
| 茶壶 | `teapot_result` | `cooking_time` / `cooking-time` / 原有 `time` | 模板中的值；均未提供时为 200 tick |
| 炒锅 | `pot_flex_foods` | `stir_fry_count` / `stir-fry-count` | 厨具行为的 `stir_fry_count` |
| 石磨 | `accurate_foods`，`cook: millstone` | 原有 `rotations` | 厨具行为的 `grind_rotations` |
| 砧板 | `chopping_board_raws` | 原有 `stage` | 模板中的值；均未提供时为 1 刀 |

`cooking_time` 和 `stir_fry_count` 必须是正整数。时间在 YAML 中以 **tick** 为单位，20 TPS 时 20 tick 约等于 1 秒；多个别名同时存在时，值必须相同。零、负数、小数、超出整数范围或用在不对应厨具上的新字段会报配置错误。不要填 `0` 表示立即完成，删除局部字段即可恢复继承；石磨原有 `rotations: 0` 仍保留其旧的继承含义。

```yaml
accurate_foods:
  kaleidoscopecookery:steamer_fast_mantou:
    require: kaleidoscopecookery:raw_dough
    result: kaleidoscopecookery:mantou
    cook: steamer
    cooking_time: 47                 # 47 tick，正常 TPS 下约 2.35 秒

pot_flex_foods:
  kaleidoscopecookery:pot_quick_beef:
    result: minecraft:cooked_beef
    perfect:
      minecraft:beef: 1
    stir_fry_count: 3                # 翻炒 3 次，不额外添加秒数门槛
```

茶壶的时间字段只控制**熬煮段**。投料后原有的 200 tick 准备段仍然存在；例如 `time: 47` 表示准备 200 tick，再熬煮 47 tick。断火、开盖、冷却等条件继续按各厨具原有规则处理，时间不是脱离这些条件的现实时间倒计时。

**菜单修改**：`/kcrecipe edit` → 选厨具 → 选配方。时间按钮输入正数秒数，必须是 0.05 秒的整数倍，换算后不得超过 `2147483647` tick（`107374182.35` 秒）；保存时不做四舍五入。翻炒、刀数、圈数按钮继续输入次数，翻炒允许完整正整数范围，刀数与圈数沿用原编辑菜单范围。专用编辑按钮显示当前值和简短继承来源，默认值按标准厨具显示，自定义厨具以自身设置为准。右键按钮恢复继承，并删除该配方的局部设置；如果配方套用了含时间或次数的模板，恢复的是该模板的实际值。模板局部覆盖在保存前显示“保存后确认”，避免为了预览重复执行模板参数。保存写回原文件，连续点击保存只发起一次写入。

**修改从下一批次生效**：蒸笼、烤架和石磨在投料时，砧板在放料时，炒锅在首次有效翻炒时，高汤锅在开始炖煮时，茶壶在投料时冻结配方计划。当前批次的目标时间/次数、已选成品 ID、数量、品质、lore 和容器会随进度保存，重载及读档后继续使用；重新投料、改变炒锅/汤锅的原料等操作会重新开始批次。旧存档没有计划时沿用原进度，并在首次恢复时补齐计划。

食谱列表、详情和编辑页中的物品预览直接保留 CraftEngine 生成的原名称、原始 lore 与颜色，不追加配方说明、加工时间或来源信息；物品本身没有 lore 时不添加 lore。编辑页的操作说明放在独立按钮中。加工时间与次数在专用设置按钮中查看和修改，按钮仅显示数值、继承来源和操作说明。

炒锅和高汤锅的模糊配方详情在成品左侧显示黄色注意图标。鼠标悬停即可查看“投料越接近理想配比 品质越高”“多放杂料会拉低品质”，该图标只展示提示。图标配置和贴图位于 [recipe_menu_hint.yml](../Kaleidoscope/kaleidoscopecookery/configuration/gui/recipe_menu_hint.yml) 与 [recipe_quality_hint.png](../Kaleidoscope/kaleidoscopecookery/resourcepack/assets/kaleidoscopecookery/textures/gui/recipe_quality_hint.png)；复制这两个新增文件到现有森罗包后，重载 CE 并重新分发资源包即可。未安装图标资源时使用原版黄色染料，提示仍可显示；`recipe_menu.buttons.quality_hint` 可替换图标。

计划保存的是配方结果数据，成品物品仍在完成时按 CraftEngine 的物品定义构建。若产物已被删除或计划损坏，厨具保留原料并暂停完成，不会重新匹配或重抽产物。

**模板与工厂保存**：普通配方只修改相关字段；模板保留 `template` 和其他参数，写局部 `overrides`。工厂中能安全定位的独立参数只改该实例；其余情况在原位置拆分实例并保留完整蓝图，其他配方、展示物品及生成顺序保持一致。无法唯一定位来源、原文件已变化或参数无法安全验证时，保存失败并提示重新打开菜单或手动编辑，不会把工厂实例删掉再物化成单条食谱。YAML 排版可能被规范化。

保存会检查引用模板的完整依赖链：模板必须有唯一、静态可定位的定义，参数类型须可安全重复解析；动态模板名、工厂生成模板、状态型或未知参数会拒绝自动保存。模板文件须与最近一次成功加载的定义一致；手动修改模板后先成功重载 CraftEngine，再打开菜单编辑。检查及文件读取在异步保存阶段进行，浏览菜单不会读取配置文件或重新执行模板。

同一原文件内的配方按原 YAML 的位置保持顺序，包括工厂实例及其蓝图条目；保存拆分和 CraftEngine 重载后，菜单及运行时仍沿用这一顺序。

**开发接口**：精准和模糊配方保留原构造器及 `FlexFoodRecipe.of(...)` 重载，新参数的内部 `0` 表示继承。读配方使用已发布的不可变快照；批次使用 `CookingPlan`，完成时才在厨具所属线程创建物品。YAML 读写与纯配置处理走串行异步队列，重载整代完成后再一次发布，不为每条配方新增定时任务。

<a id="exact-recipes"></a>

### 🎯 精准配方 `accurate_foods`

蒸笼、石磨、沙威玛烤架用。一进一出，不做模糊匹配。

```yaml
accurate_foods:
  #蒸笼蒸馒头
  kaleidoscopecookery:steamer_mantou:
    require: kaleidoscopecookery:raw_dough     # 原料 输入
    result: kaleidoscopecookery:mantou         # 成品 标量 = 100% 出这个
    cook: steamer                              # steamer / millstone / shawarma
    result_count: 1                            # 一次产出几份 默认 1
    lore:                                      # 可选 显示在 /kcrecipe 菜单里
      - "<gray><lang:lore.kaleidoscopecookery.recipe.accurate.mantou>"
```

`result` 写成列表时按权重随机出一个，每项 `"物品id 权重"`，权重缺省 100：

```yaml
accurate_foods:
  #石磨磨铁矿石
  kaleidoscopecookery:millstone_iron_ore:
    require: minecraft:iron_ore
    cook: millstone
    rotations: 6                  # 转满 6 圈产出 仅石磨可用 不写则用 grind_rotations
    result:
      - minecraft:iron_ingot 45
      - minecraft:gold_ingot 45
```

> `rotations` **只有石磨能用**，写在其它机型的配方上会在控制台报错并跳过整条配方。
> `require` 的物品会自动进对应机型的白名单，不用另外写。

<a id="chopping-recipes"></a>

### 🔪 砧板配方 `chopping_board_raws`

```yaml
chopping_board_raws:
  #菜板切鳕鱼
  kaleidoscopecookery:cod:
    require: minecraft:cod        # 原料 只有 require 里出现过的物品能放上砧板
    stage: 5                      # 切几刀 放上去是第 0 阶段 每切一刀 +1 切满产出
    values: cb:block/custom/cook/block/chopping_board/cod   # 阶段模型前缀
    mode: single                  # single / single_extra / multi_random
    result: minecraft:cooked_cod 1
```

- `values` 是**模型 id 前缀**，按 `stage` 自动派生 `前缀/0` 到 `前缀/(stage-1)`。省略则不做分阶段模型，砧板上直接展示放上去的东西本身，切的刀数与产出照常。
- 加载时会校验实际存在的模型数与 `stage` 是否一致，对不上只在控制台提示，不阻断注册。

**三种产出模式：**

| `mode` | `result` | 行为 |
| ---- | ---- | ---- |
| `single`（默认） | 只能一个 | 固定产出。配多个会报错并跳过整条配方 |
| `single_extra` | 只能一个 | 固定产出主产物，再让 `extra` 列表每项各自按权重当百分比独立判定是否附带掉落 |
| `multi_random` | 可多个 | 每项把权重当百分比独立判定，全部没命中时再按权重保底出一个 |

产物写作 `"物品id 数量 权重"`，数量缺省 1，权重缺省 100。

```yaml
chopping_board_raws:
  #菜板切生牛肉
  kaleidoscopecookery:beef:
    require: minecraft:beef
    stage: 4
    mode: single_extra
    result: kaleidoscopecookery:raw_cow_offal 2
    extra:
      - minecraft:bone 1 25        # 25% 概率附带一根骨头
```

<a id="tea-recipes"></a>

### 🫖 茶壶 `teapot_liquid` / `tea_cup` / `teapot_result`

三段配合使用，缺一条茶就出不来。

**`teapot_liquid`** —— 壶里能装什么液体，以及烧的时候进度条长什么样。键名是液体方块 id。

```yaml
teapot_liquid:
  #水
  minecraft:water:
    display_name: kaleidoscopecookery.message.teapot.liquid.water   # 翻译键
    bar_left: kaleidoscopecookery:tea_left       # 进度条左端封口
    bar_right: kaleidoscopecookery:tea_right     # 进度条右端封口
    bar_empty: kaleidoscopecookery:tea_empty     # 还没烧到的格子
    bar_water: kaleidoscopecookery:tea_water_full  # 已经烧好的格子
```

> 满格字形那个键名可以随便起，只要是 `bar_` 开头、且不是 `bar_left` / `bar_right` / `bar_empty`——解析时取第一个符合的。所以岩浆写 `bar_lava`、蜂蜜写 `bar_honey` 都行。

**`tea_cup`** —— 成品倒进杯子里长什么样。键名是**成品 id**。

```yaml
tea_cup:
  #乌龙茶
  kaleidoscopecookery:oolong:
    item: kaleidoscopecookery:oolong    # 手持它右键杯垫可直接放茶 缺省取成品自身
    display_model:                      # 杯中展示模型 多个则随机取一个
      - show:oolong_1
      - show:oolong_2
```

**`teapot_result`** —— 真正的配方。

```yaml
teapot_result:
  #大麦茶
  barley_tea_from_wheat_seeds:
    fluid: minecraft:water              # 用哪种液体 必须已在 teapot_liquid 里注册
    require: minecraft:wheat_seeds 12   # 原料 数量
    result: kaleidoscopecookery:barley_tea 1   # 成品 数量
    time: 240                           # 处理 tick 默认 200
```

> 加载期有两道校验：`fluid` 没在 `teapot_liquid` 里注册、或 `result` 没在 `tea_cup` 里定义展示模型，都会在控制台报错并跳过该配方。

**产量是按投料比例算的。** 茶壶满液体条 = 8 份（可倒 8 杯）。放的原料越少，产量按比例减少：

```
份数 = 向下取整( 8 × 实际放入量 / require 要求量 )     结果不足 1 时按 1 算
```

以上面 `require: minecraft:wheat_seeds 12` 为例：放满 12 个 → 8 份；放 6 个 → `8×6/12 = 4` 份；放 1 个 → `8×1/12 = 0.67 → 1` 份。
放料上限就是 `require` 的数量，放再多也只按满产量算。之后每往杯垫的空杯里倒一杯消耗 1 份、液体条减一格，倒空则恢复空壶。
`result` 后面的数量是**每一份给几个成品**，与份数是两码事。

<a id="protection"></a>

## 🛡️ 领地权限

所有家具的右键交互入口都会先经过 AntiGriefLib 判定，无权限玩家无法在他人领地内使用。
拥有权限节点 `kaleidoscopecookery.antigrief.bypass` 的玩家可绕过该判定。
