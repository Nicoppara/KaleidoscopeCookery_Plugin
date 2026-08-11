package net.kaleidoscope.cookery.recipe.edit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.momirealms.craftengine.core.util.Key;

public final class RecipeSourceIndex {
   private static final RecipeSourceIndex INSTANCE = new RecipeSourceIndex();
   private final Map<RecipeSourceIndex.SourceKey, Object> sources = new ConcurrentHashMap<>();
   private final Map<Object, RecipeSourceIndex.SourceKey> byRecipe = Collections.synchronizedMap(new IdentityHashMap<>());
   private final Set<Object> duplicates = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
   private final Set<RecipeSourceIndex.SourceKey> deleted = ConcurrentHashMap.newKeySet();
   private final Set<RecipeSourceIndex.SourceKey> pendingRestore = ConcurrentHashMap.newKeySet();
   private final Map<RecipeSourceIndex.Kind, Integer> activeLoads = new ConcurrentHashMap<>();
   private final Map<RecipeSourceIndex.Kind, List<Runnable>> pendingActions = new ConcurrentHashMap<>();

   private RecipeSourceIndex() {
   }

   public static RecipeSourceIndex instance() {
      return INSTANCE;
   }

   public void put(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath, Object recipe, boolean duplicate) {
      this.put(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath), recipe, duplicate);
   }

   public void put(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target, Object recipe, boolean duplicate) {
      if (kind != null && id != null && file != null && target != null && recipe != null) {
         RecipeSourceIndex.SourceKey source = new RecipeSourceIndex.SourceKey(kind, id, file, target);
         this.sources.put(source, recipe);
         this.byRecipe.put(recipe, source);
         if (duplicate) {
            this.duplicates.add(recipe);
         }
      }
   }

   public Path get(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.get(recipe);
      return source == null ? null : source.file();
   }

   public RecipeSourceIndex.Kind kind(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.get(recipe);
      return source == null ? null : source.kind();
   }

   public String nodePath(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.get(recipe);
      return source == null ? null : source.nodePath();
   }

   public RecipeFileStore.SourceTarget target(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.get(recipe);
      return source == null ? null : source.target();
   }

   public boolean isDuplicate(Object recipe) {
      return recipe != null && this.duplicates.contains(recipe);
   }

   public boolean hasOtherSource(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath) {
      if (kind != null && id != null) {
         Path normalized = file == null ? null : file.toAbsolutePath().normalize();
         RecipeFileStore.SourceTarget target = RecipeFileStore.SourceTarget.direct(nodePath);

         for (RecipeSourceIndex.SourceKey source : this.sources.keySet()) {
            boolean sameNode = normalized != null && source.file().equals(normalized) && source.target().equals(target);
            if (source.kind() == kind && source.id().equals(id) && !sameNode) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean hasSource(RecipeSourceIndex.Kind kind, Key id) {
      if (kind != null && id != null) {
         for (RecipeSourceIndex.SourceKey source : this.sources.keySet()) {
            if (source.kind() == kind && source.id().equals(id)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isDeleted(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath) {
      if (kind != null && id != null && file != null && nodePath != null) {
         Path normalized = file.toAbsolutePath().normalize();

         for (RecipeSourceIndex.SourceKey source : this.deleted) {
            if (source.kind() == kind && source.id().equals(id) && source.file().equals(normalized) && source.nodePath().equals(nodePath)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isDeleted(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      return kind != null && id != null && file != null && target != null && this.deleted.contains(new RecipeSourceIndex.SourceKey(kind, id, file, target));
   }

   public void markDeleted(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.get(recipe);
      if (source != null) {
         this.deleted.add(source);
      }
   }

   public void markDeleted(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath) {
      this.markDeleted(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
   }

   public void markDeleted(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      if (kind != null && id != null && file != null && target != null) {
         this.deleted.add(new RecipeSourceIndex.SourceKey(kind, id, file, target));
      }
   }

   public synchronized void restore(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath) {
      this.restore(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
   }

   public synchronized void restore(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      if (kind != null && id != null && file != null && target != null) {
         RecipeSourceIndex.SourceKey source = new RecipeSourceIndex.SourceKey(kind, id, file, target);
         if (this.activeLoads.getOrDefault(kind, 0) > 0) {
            this.pendingRestore.add(source);
         } else {
            this.deleted.remove(source);
         }
      }
   }

   public synchronized void beginLoad(RecipeSourceIndex.Kind kind) {
      if (kind != null) {
         this.activeLoads.merge(kind, 1, Integer::sum);
      }
   }

   public synchronized void endLoad(RecipeSourceIndex.Kind kind) {
      if (kind != null) {
         int remaining = this.activeLoads.getOrDefault(kind, 0) - 1;
         if (remaining > 0) {
            this.activeLoads.put(kind, remaining);
         } else {
            this.activeLoads.remove(kind);
            this.pendingRestore.removeIf(source -> {
               if (source.kind() != kind) {
                  return false;
               }

               this.deleted.remove(source);
               return true;
            });
            List<Runnable> actions = this.pendingActions.remove(kind);
            if (actions != null) {
               for (Runnable action : actions) {
                  action.run();
               }
            }
         }
      }
   }

   public synchronized void afterCurrentLoad(RecipeSourceIndex.Kind kind, Runnable action) {
      if (kind != null && this.activeLoads.getOrDefault(kind, 0) > 0) {
         this.pendingActions.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(action);
      } else {
         action.run();
      }
   }

   public void remove(Object recipe) {
      RecipeSourceIndex.SourceKey source = this.byRecipe.remove(recipe);
      this.duplicates.remove(recipe);
      if (source != null) {
         this.sources.remove(source, recipe);
      }
   }

   public Object removeSource(RecipeSourceIndex.Kind kind, Key id, Path file, String nodePath) {
      return this.removeSource(kind, id, file, RecipeFileStore.SourceTarget.direct(nodePath));
   }

   public Object removeSource(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      if (kind != null && id != null && file != null && target != null) {
         RecipeSourceIndex.SourceKey source = new RecipeSourceIndex.SourceKey(kind, id, file, target);
         Object recipe = this.sources.remove(source);
         if (recipe != null) {
            this.byRecipe.remove(recipe);
            this.duplicates.remove(recipe);
         }

         return recipe;
      } else {
         return null;
      }
   }

   public List<RecipeFileStore.SourceTarget> deletedTargets(RecipeSourceIndex.Kind kind, Key id, Path file) {
      if (kind != null && id != null && file != null) {
         Path normalized = file.toAbsolutePath().normalize();
         List<RecipeFileStore.SourceTarget> result = new ArrayList<>();

         for (RecipeSourceIndex.SourceKey source : this.deleted) {
            if (source.kind() == kind && source.id().equals(id) && source.file().equals(normalized)) {
               result.add(source.target());
            }
         }

         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   public List<Object> recipes(RecipeSourceIndex.Kind kind, Key id) {
      if (kind != null && id != null) {
         List<Object> result = new ArrayList<>();

         for (Entry<RecipeSourceIndex.SourceKey, Object> entry : this.sources.entrySet()) {
            if (entry.getKey().kind() == kind && entry.getKey().id().equals(id)) {
               result.add(entry.getValue());
            }
         }

         return List.copyOf(result);
      } else {
         return List.of();
      }
   }

   public void duplicate(Object recipe, boolean duplicate) {
      if (recipe != null) {
         if (duplicate) {
            this.duplicates.add(recipe);
         } else {
            this.duplicates.remove(recipe);
         }
      }
   }

   public void remove(RecipeSourceIndex.Kind kind, Key id) {
      if (kind != null && id != null) {
         synchronized (this.byRecipe) {
            this.byRecipe.entrySet().removeIf(entry -> {
               RecipeSourceIndex.SourceKey source = entry.getValue();
               if (source.kind() == kind && source.id().equals(id)) {
                  this.sources.remove(source, entry.getKey());
                  this.duplicates.remove(entry.getKey());
                  return true;
               } else {
                  return false;
               }
            });
         }
      }
   }

   public void clearKind(RecipeSourceIndex.Kind kind) {
      if (kind != null) {
         synchronized (this.byRecipe) {
            this.byRecipe.entrySet().removeIf(entry -> {
               RecipeSourceIndex.SourceKey source = entry.getValue();
               if (source.kind() != kind) {
                  return false;
               }

               this.sources.remove(source, entry.getKey());
               this.duplicates.remove(entry.getKey());
               return true;
            });
         }
      }
   }

   public void clear() {
      this.sources.clear();
      this.byRecipe.clear();
      this.duplicates.clear();
      this.deleted.clear();
      this.pendingRestore.clear();
      this.activeLoads.clear();
      this.pendingActions.clear();
   }

   public enum Kind {
      ACCURATE,
      POT_FLEX,
      STOCK_FLEX,
      CHOPPING,
      TEAPOT;
   }

   private record SourceKey(RecipeSourceIndex.Kind kind, Key id, Path file, RecipeFileStore.SourceTarget target) {
      private SourceKey {
         file = file.toAbsolutePath().normalize();
      }

      private String nodePath() {
         return this.target.generatedNode();
      }
   }
}
