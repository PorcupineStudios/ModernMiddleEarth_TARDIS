package com.porcupinestudios.lotrport.ui;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A whole screen as the server built it for one player.
 *
 * @param id the provider that built it ({@link Views})
 * @param context which thing it is about (a colony's id; empty for views about the player)
 * @param title shown above the tabs
 * @param tabs empty for a view with a single page
 * @param tab the open tab's id
 * @param back the view the Back button returns to, if any (all colonies → one colony → back)
 */
public record View(ResourceLocation id, String context, Component title, List<Tab> tabs, String tab, Page page,
        java.util.Optional<Target> back) {
    public View {
        tabs = List.copyOf(tabs);
    }

    public record Tab(String id, Component label) {}

    /** Where a view is: provider, context and tab. */
    public record Target(ResourceLocation view, String context, String tab) {}

    public Target target() {
        return new Target(id, context, tab);
    }
}
