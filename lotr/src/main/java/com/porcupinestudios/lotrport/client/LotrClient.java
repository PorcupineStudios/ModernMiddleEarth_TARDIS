package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.HudNet;
import com.porcupinestudios.lotrport.net.MapNet;
import com.porcupinestudios.lotrport.net.QuestNet;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.content.LotrBowItem;
import com.porcupinestudios.lotrport.content.LotrCrossbowItem;
import com.porcupinestudios.lotrport.content.LotrItems;
import com.porcupinestudios.lotrport.content.LotrDrinkItem;
import com.porcupinestudios.lotrport.drink.Drinks;
import com.porcupinestudios.lotrport.machine.Machines;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client setup: entity renderers, model layers, the map key. Never referenced from common code. */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LotrClient {
    /**
     * For the harness mod (optional): where each part of a creature was drawn ({@link PartPoses}), so its harness
     * follows the animation. A plain NeoForge message; neither mod depends on the other.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void offerPartPoses(net.neoforged.fml.event.lifecycle.InterModEnqueueEvent event) {
        PartPoses.register();
        net.neoforged.fml.InterModComms.sendTo("ringbearer_harness", "part_pose", () -> PartPoses.LOOKUP);
    }

    /** LOTR people: the classic humanoid, for 64x32 skins and for 64x64 ones. */
    public static final ModelLayerLocation NPC_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "main");
    public static final ModelLayerLocation NPC_TALL_LAYER = new ModelLayerLocation(LotrPort.id("npc"), "tall");

    /** Opens the Middle-earth map (M, as LOTR's map is reached from the Red Book). */
    public static final KeyMapping MAP_KEY = new KeyMapping("key.lotr_port.map", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, "key.categories.lotr_port");

    /** On the map screen: teleport to the block under the cursor (operators and creative players), as LOTR's. */
    public static final KeyMapping MAP_TELEPORT_KEY = new KeyMapping("key.lotr_port.map_teleport",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, "key.categories.lotr_port");

    /** On the map screen: fast travel to the selected waypoint, as LOTR's. */
    public static final KeyMapping FAST_TRAVEL_KEY = new KeyMapping("key.lotr_port.fast_travel",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.lotr_port");

    /** Opens the jukebox: the music that can play here, to take tracks out of the shuffle or play one now. */
    public static final KeyMapping JUKEBOX_KEY = new KeyMapping("key.lotr_port.jukebox", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.lotr_port");

    /** Server admins (op level 4): the race screen (players' races and sizes, race presets' size and reach). */
    public static final KeyMapping RACE_ADMIN_KEY = new KeyMapping("key.lotr_port.race_admin", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.lotr_port");

    /** Opens the unit screen: your hired units and your colonies' soldiers. */
    public static final KeyMapping UNITS_KEY = new KeyMapping("key.lotr_port.units", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U, "key.categories.lotr_port");

    /** The alignment bar's faction: previous / next, LOTR's arrow keys. */
    public static final KeyMapping ALIGNMENT_PREV_KEY = new KeyMapping("key.lotr_port.alignment_prev",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.lotr_port");
    public static final KeyMapping ALIGNMENT_NEXT_KEY = new KeyMapping("key.lotr_port.alignment_next",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.lotr_port");
    /** The alignment bar's region (LOTR's up/down): West, East, South, then realms. */
    public static final KeyMapping ALIGNMENT_REGION_PREV_KEY = new KeyMapping("key.lotr_port.alignment_region_prev",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, "key.categories.lotr_port");
    public static final KeyMapping ALIGNMENT_REGION_NEXT_KEY = new KeyMapping("key.lotr_port.alignment_region_next",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "key.categories.lotr_port");

    private LotrClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        Atmosphere.init();
        ColorGrade.init();
        RingSight.init();
        event.enqueueWork(WoodSetRenderers::setup);
        MapNet.clientHandler = (data, ctx) -> MapScreen.open(data);
        com.porcupinestudios.lotrport.border.BorderNet.clientHandler = BorderLayer::accept;
        QuestNet.offerHandler = QuestOfferScreen::open;
        com.porcupinestudios.lotrport.npc.Traders.interactHandler = TraderInteractScreen::open;
        QuestNet.bookHandler = RedBookScreen::show;
        HudNet.barHandler = LotrHud::receive;
        HudNet.bonusHandler = AlignmentPopups::receive;
        RigRenderer.init();
        com.porcupinestudios.lotrport.unit.UnitNet.listHandler = UnitScreen::show;
        com.porcupinestudios.lotrport.colony.members.HallNet.hallHandler = HallScreen::show;
        com.porcupinestudios.lotrport.colony.work.LedgerNet.citizenHandler = CitizenScreen::show;
        com.porcupinestudios.lotrport.social.SocialNet.iconsHandler = MapScreen::icons;
        com.porcupinestudios.lotrport.social.SocialNet.viewHandler = VisibilityScreen::show;
        com.porcupinestudios.lotrport.ui.UiNet.showHandler = com.porcupinestudios.lotrport.client.ui.UiScreen::show;
        com.porcupinestudios.lotrport.colony.book.Books.showHandler = com.porcupinestudios.lotrport.client.book.BookScreen::show;
        com.porcupinestudios.lotrport.client.book.BookPreview.init();
        com.porcupinestudios.lotrport.ui.UiNet.closeHandler = com.porcupinestudios.lotrport.client.ui.UiScreen::close;
        com.porcupinestudios.lotrport.ui.UiNet.noticeHandler = com.porcupinestudios.lotrport.client.ui.UiScreen::notice;
        event.enqueueWork(LotrClient::rangedProperties);
    }

    @SubscribeEvent
    public static void clientExtensions(RegisterClientExtensionsEvent event) {
        ArmorModels.register(event);
        // the drinks' fluids (in Create's pipes, tanks and basins): water's textures in the drink's colour
        ResourceLocation still = ResourceLocation.withDefaultNamespace("block/water_still");
        ResourceLocation flow = ResourceLocation.withDefaultNamespace("block/water_flow");
        for (Map.Entry<ResourceLocation, Supplier<FluidType>> e : Drinks.FLUID_TYPE_BY_ITEM.entrySet()) {
            int tint = 0xFF000000 | Drinks.BY_ITEM.get(e.getKey()).color();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return still;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flow;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            }, e.getValue().get());
        }
    }

    /** LOTR's connected textures: gates and connected bricks ({@link ConnectedModel}). */
    @SubscribeEvent
    public static void geometry(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LotrPort.id("connected"), ConnectedModel.LOADER);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(Drinks.KEG_MENU.get(), KegScreen::new);
        event.register(com.porcupinestudios.lotrport.npc.Traders.TRADE_MENU.get(), TradeScreen::new);
        event.register(com.porcupinestudios.lotrport.unit.UnitNet.INVENTORY_MENU.get(), UnitInventoryScreen::new);
        event.register(com.porcupinestudios.lotrport.npc.Traders.EXCHANGE_MENU.get(), CoinExchangeScreen::new);
        event.register(Machines.FORGE_MENU.get(), MachineScreen::new);
        event.register(Machines.OVEN_MENU.get(), MachineScreen::new);
        event.register(Machines.MILLSTONE_MENU.get(), MachineScreen::new);
        event.register(Machines.UNSMELTERY_MENU.get(), MachineScreen::new);
        event.register(Machines.ARMOR_STAND_MENU.get(), MachineScreen::new);
        event.register(com.porcupinestudios.lotrport.content.pouch.Pouches.MENU.get(), PouchScreen::new);
    }

    /**
     * Bow and crossbow pull frames: vanilla registers its "pulling"/"pull"/"charged" predicates only for its own bow
     * and crossbow. A LOTR bow's pull runs over its own draw time.
     */
    private static void rangedProperties() {
        ResourceLocation pulling = ResourceLocation.withDefaultNamespace("pulling");
        ResourceLocation pull = ResourceLocation.withDefaultNamespace("pull");
        ResourceLocation charged = ResourceLocation.withDefaultNamespace("charged");
        ItemPropertyFunction vessel = (stack, level, e, seed) -> Drinks.vesselId(stack); // the drink's picture
        for (var holder : LotrItems.BY_ID.values()) {
            Item item = holder.get();
            if (item instanceof LotrDrinkItem) {
                ItemProperties.register(item, LotrPort.id("vessel"), vessel);
            } else if (item instanceof LotrBowItem bow) {
                ItemProperties.register(item, pulling,
                        (stack, level, e, seed) -> e != null && e.isUsingItem() && e.getUseItem() == stack ? 1f : 0f);
                ItemProperties.register(item, pull, (stack, level, e, seed) -> e == null || e.getUseItem() != stack ? 0f
                        : (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / (float) bow.drawTime());
            } else if (item instanceof LotrCrossbowItem) {
                ItemProperties.register(item, pulling, (stack, level, e, seed) -> e != null && e.isUsingItem()
                        && e.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(item, pull, (stack, level, e, seed) -> e == null || CrossbowItem.isCharged(stack)
                        ? 0f : (stack.getUseDuration(e) - e.getUseItemRemainingTicks())
                                / (float) CrossbowItem.getChargeDuration(stack, e));
                ItemProperties.register(item, charged,
                        (stack, level, e, seed) -> CrossbowItem.isCharged(stack) ? 1f : 0f);
            }
        }
    }

    /** Fallen leaves of the grey vanilla-leaf icons (oak, dark oak, acacia), tinted by foliage like LOTR's. */
    private static final String[] TINTED_FALLEN = {"fallen_oak_leaves", "fallen_dark_oak_leaves",
        "fallen_acacia_leaves"};

    private static net.minecraft.world.level.block.Block[] tintedFallen() {
        return java.util.Arrays.stream(TINTED_FALLEN)
                .map(id -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, id)))
                .filter(b -> b != net.minecraft.world.level.block.Blocks.AIR)
                .toArray(net.minecraft.world.level.block.Block[]::new);
    }

    @SubscribeEvent
    public static void blockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null
                ? net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(level, pos)
                : net.minecraft.world.level.FoliageColor.getDefaultColor(), tintedFallen());
        // LOTR's grey tall grasses and clovers take the biome's grass colour (their colorMultiplier)
        event.register((state, level, pos, tint) -> level != null && pos != null
                ? net.minecraft.client.renderer.BiomeColors.getAverageGrassColor(level, pos)
                : net.minecraft.world.level.GrassColor.getDefaultColor(),
                com.porcupinestudios.lotrport.content.LotrBlocks.tinted("grass"));
    }

    @SubscribeEvent
    public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> net.minecraft.world.level.FoliageColor.getDefaultColor(),
                java.util.Arrays.stream(tintedFallen()).map(net.minecraft.world.level.block.Block::asItem)
                        .toArray(net.minecraft.world.level.ItemLike[]::new));
        event.register((stack, layer) -> layer == 0 ? net.minecraft.world.level.GrassColor.getDefaultColor() : -1,
                java.util.Arrays.stream(com.porcupinestudios.lotrport.content.LotrBlocks.tinted("grass"))
                        .map(net.minecraft.world.level.block.Block::asItem)
                        .filter(i -> i != net.minecraft.world.item.Items.AIR)
                        .toArray(net.minecraft.world.level.ItemLike[]::new));
        event.register((stack, layer) -> layer == 0
                ? net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack, -1) : -1,
                com.porcupinestudios.lotrport.content.LotrItems.dyeable().toArray(new net.minecraft.world.item.Item[0]));
    }

    /** LOTR's alignment bar, compass and attack meter. */
    @SubscribeEvent
    public static void guiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        LotrHud.registerLayers(event);
        Atmosphere.layers(event);
        ColorGrade.layers(event); // below the filter: registerBelowAll puts each under the last
    }

    /** Middle-earth's sky (dimension type effects {@code lotr_port:middle_earth}). */
    @SubscribeEvent
    public static void skyEffects(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(com.porcupinestudios.lotrport.LotrPort.id("middle_earth"), new MiddleEarthSky());
    }

    @SubscribeEvent
    public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        ColorGrade.register(event);
    }

    /** Region fogs and filters (assets/<ns>/atmosphere). */
    @SubscribeEvent
    public static void reloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        Atmosphere.reload(event);
        RingSight.reload(event);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(MAP_KEY);
        event.register(JUKEBOX_KEY);
        event.register(UNITS_KEY);
        event.register(RACE_ADMIN_KEY);
        event.register(MAP_TELEPORT_KEY);
        event.register(FAST_TRAVEL_KEY);
        event.register(ALIGNMENT_PREV_KEY);
        event.register(ALIGNMENT_NEXT_KEY);
        event.register(ALIGNMENT_REGION_PREV_KEY);
        event.register(ALIGNMENT_REGION_NEXT_KEY);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NPC_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 32));
        event.registerLayerDefinition(NPC_TALL_LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64));
        for (String kind : NpcModels.KINDS) {
            event.registerLayerDefinition(NpcModels.layer(kind), () -> NpcModels.tall(kind));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LotrEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(LotrEntities.RIGGED.get(), NpcRenderer::new);
        event.registerEntityRenderer(com.porcupinestudios.lotrport.land.LandEntities.BANNER.get(),
                ClaimBannerRenderer::new);
        com.porcupinestudios.lotrport.mount.Mounts.TYPE_BY_BREED.values()
                .forEach(t -> event.registerEntityRenderer(t.get(), MountRenderer::new));
        // rocks, snowballs and wraith bolts thrown by LOTR's creatures, drawn as their item
        event.registerEntityRenderer(LotrEntities.PROJECTILE.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(com.porcupinestudios.lotrport.content.figure.Figures.TYPE.get(), FigureRenderer::new);
        MachineRenderers.register(event);
        ThrownRenderers.register(event);
        WoodSetRenderers.register(event);
    }
}
