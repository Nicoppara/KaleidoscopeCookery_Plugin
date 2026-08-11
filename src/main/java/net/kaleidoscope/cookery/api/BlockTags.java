package net.kaleidoscope.cookery.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kaleidoscope.cookery.plugin.KaleidoscopeCookeryPlugin;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.SectionConfigParser;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockTags {
   public static final LoadingStage BLOCK_TAGS = new LoadingStage("block tags");
   public static final Key TILLABLE = Key.of("kaleidoscopecookery:tillable");
   private static final BlockTags INSTANCE = new BlockTags();
   private final Map<Key, Set<Material>> tags = new ConcurrentHashMap<>();

   private BlockTags() {
   }

   public static BlockTags instance() {
      return INSTANCE;
   }

   public void register(@NotNull Key tag, @NotNull Collection<String> members) {
      this.tags.put(tag, resolve(tag, members));
   }

   public void add(@NotNull Key tag, @NotNull Collection<String> members) {
      Set<Material> resolved = resolve(tag, members);
      this.tags.merge(tag, resolved, (first, second) -> {
         Set<Material> merged = EnumSet.noneOf(Material.class);
         merged.addAll((Collection<? extends Material>)first);
         merged.addAll((Collection<? extends Material>)second);
         return merged;
      });
   }

   public boolean remove(@NotNull Key tag) {
      return this.tags.remove(tag) != null;
   }

   public boolean exists(@NotNull Key tag) {
      return this.tags.containsKey(tag);
   }

   @NotNull
   public Set<Material> materials(@NotNull Key tag) {
      Set<Material> entry = this.tags.get(tag);
      return entry == null ? Set.of() : Set.copyOf(entry);
   }

   @NotNull
   public Set<Key> keys() {
      return Set.copyOf(this.tags.keySet());
   }

   public boolean matches(@NotNull Key tag, @Nullable Block block) {
      return block != null && this.matches(tag, block.getType());
   }

   public boolean matches(@NotNull Key tag, @Nullable Material material) {
      if (material == null) {
         return false;
      }

      Set<Material> entry = this.tags.get(tag);
      return entry != null && entry.contains(material);
   }

   public static void registerParser() {
      CraftEngine.instance().packManager().registerConfigSectionParser(new BlockTags.BlockTagsParser());
   }

   private static Set<Material> resolve(Key tag, Collection<String> members) {
      Set<Material> result = EnumSet.noneOf(Material.class);

      for (String member : members) {
         if (member != null) {
            String trimmed = member.trim();
            if (!trimmed.isEmpty()) {
               if (trimmed.charAt(0) == '#') {
                  addVanillaTag(result, tag, trimmed.substring(1).trim());
               } else {
                  Material material = Material.matchMaterial(trimmed);
                  if (material != null && material.isBlock()) {
                     result.add(material);
                  } else {
                     warn(tag, "未知的方块 " + trimmed);
                  }
               }
            }
         }
      }

      return result;
   }

   private static void addVanillaTag(Set<Material> result, Key tag, String id) {
      NamespacedKey key = NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
      Tag<Material> vanilla = key == null ? null : Bukkit.getTag("blocks", key, Material.class);
      if (vanilla == null) {
         warn(tag, "未知的原版方块标签 #" + id);
      } else {
         result.addAll(vanilla.getValues());
      }
   }

   private static void warn(Key tag, String message) {
      KaleidoscopeCookeryPlugin.instance().getLogger().warning("[block_tags] " + tag.asString() + " " + message);
   }

   private static final class BlockTagsParser extends SectionConfigParser {
      private int count;

      public String[] sectionId() {
         return new String[]{"block_tags", "block-tags", "block_tag", "block-tag"};
      }

      public LoadingStage loadingStage() {
         return BlockTags.BLOCK_TAGS;
      }

      public List<LoadingStage> dependencies() {
         return List.of();
      }

      public int count() {
         return this.count;
      }

      public void preProcess() {
         this.count = 0;
         BlockTags.INSTANCE.tags.clear();
      }

      protected void parseSection(Pack pack, Path path, ConfigSection section) {
         for (String tagId : section.keySet()) {
            Key tag = Key.of(tagId.trim());
            List<String> members = new ArrayList<>(section.getStringList(tagId));
            BlockTags.INSTANCE.add(tag, members);
            this.count++;
         }
      }
   }
}
