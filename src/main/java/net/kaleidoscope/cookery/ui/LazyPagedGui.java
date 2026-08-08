package net.kaleidoscope.cookery.ui;

import net.momirealms.craftengine.core.plugin.gui.AbstractGui;
import net.momirealms.craftengine.core.plugin.gui.Click;
import net.momirealms.craftengine.core.plugin.gui.GuiElement;
import net.momirealms.craftengine.core.plugin.gui.GuiLayout;
import net.momirealms.craftengine.core.plugin.gui.ItemWithAction;
import net.momirealms.craftengine.core.plugin.gui.PagedGui;

import java.util.List;
import java.util.function.Consumer;

// 按页加载的分页容器
public final class LazyPagedGui extends AbstractGui implements PagedGui {

    // 取第 fromIndex 起 count 条 不足时返回实际条数 由调用方决定怎么建图标
    @FunctionalInterface
    public interface PageLoader {
        List<ItemWithAction> load(int fromIndex, int count);
    }

    private final PageLoader loader;
    private final int totalItems;
    private final int elementsPerPage;
    private final int maxPages;
    private List<ItemWithAction> pageItems = List.of();
    private int currentPage = 1;

    public LazyPagedGui(GuiLayout layout, Consumer<Click> inventoryClickConsumer,
                        int totalItems, PageLoader loader) {
        super(layout, inventoryClickConsumer);
        this.loader = loader;
        this.totalItems = totalItems;
        int slots = 0;
        for (GuiElement element : this.guiElements) {
            if (element instanceof GuiElement.PageOrderedGuiElement) {
                slots++;
            }
        }
        this.elementsPerPage = slots;
        this.maxPages = slots == 0 ? 1 : Math.max(1, (totalItems - 1) / slots + 1);
        loadPage();
    }

    @Override
    public List<ItemWithAction> items() {
        return this.pageItems;
    }

    @Override
    public ItemWithAction itemAt(int index) {
        if (index < 0 || index >= this.pageItems.size()) {
            return ItemWithAction.EMPTY;
        }
        return this.pageItems.get(index);
    }

    @Override
    public void setPage(int page) {
        this.currentPage = Math.max(1, Math.min(page, this.maxPages));
        loadPage();
    }

    @Override
    public int currentPage() {
        return this.currentPage;
    }

    @Override
    public int maxPages() {
        return this.maxPages;
    }

    public int totalItems() {
        return this.totalItems;
    }

    private void loadPage() {
        if (this.elementsPerPage == 0) {
            this.pageItems = List.of();
            return;
        }
        int from = (this.currentPage - 1) * this.elementsPerPage;
        if (from >= this.totalItems) {
            this.pageItems = List.of();
            return;
        }
        this.pageItems = this.loader.load(from, Math.min(this.elementsPerPage, this.totalItems - from));
    }
}
