package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.lotrport.machine.DisplayBlockEntity;
import com.porcupinestudios.lotrport.machine.KebabStandBlockEntity;
import com.porcupinestudios.lotrport.machine.LotrChestBlockEntity;
import com.porcupinestudios.lotrport.machine.MachineBlock;
import com.porcupinestudios.lotrport.machine.Machines;
import com.porcupinestudios.lotrport.machine.UnsmelteryBlockEntity;
import com.porcupinestudios.lotrport.machine.WeaponRackBlock;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Block entity renderers for LOTR's machines and display blocks, on LOTR's own models ({@code npc_model/<name>.json},
 * built by {@code converter/machines.py}) and textures ({@code textures/entity/block/}). Each follows LOTR's
 * renderer: the model is drawn upside down from 1.5 blocks up, turned to the block's facing.
 */
public final class MachineRenderers {
    private static final ResourceLocation WEAPON_RACK = LotrPort.id("textures/entity/block/weapon_rack.png");
    private static final ResourceLocation ARMOR_STAND = LotrPort.id("textures/entity/block/armor_stand.png");
    private static final ResourceLocation RAW = LotrPort.id("textures/entity/block/kebab/raw.png");
    private static final ResourceLocation COOKED = LotrPort.id("textures/entity/block/kebab/cooked.png");
    private static final Map<String, CreatureModel> MODELS = new HashMap<>();

    private MachineRenderers() {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        MODELS.clear(); // rebuilt on the next draw after a resource reload
        event.registerBlockEntityRenderer(Machines.DISPLAY.get(), c -> new Display());
        event.registerBlockEntityRenderer(Machines.KEBAB_STAND.get(), c -> new Kebab());
        event.registerBlockEntityRenderer(Machines.UNSMELTERY.get(), c -> new Unsmeltery());
        event.registerBlockEntityRenderer(Machines.CHEST.get(), c -> new Chest());
    }

    /** One of LOTR's block models, static (its animation and render passes left out). */
    static CreatureModel model(String name) {
        return MODELS.computeIfAbsent(name, n -> {
            ResourceLocation file = LotrPort.id("npc_model/" + n + ".json");
            return Minecraft.getInstance().getResourceManager().getResource(file).map(r -> {
                try (Reader in = r.openAsReader()) {
                    JsonObject def = JsonParser.parseReader(in).getAsJsonObject();
                    def.remove("anim");
                    def.remove("renderers");
                    return new CreatureModel(def);
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Could not build block model {}", file, e);
                    return null;
                }
            }).orElse(null);
        });
    }

    /** LOTR's turn for a block facing this way (meta 2 north 0, 3 south 180, 4 west 90, 5 east 270). */
    static float turn(BlockState state) {
        Direction d = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return 180f - d.toYRot();
    }

    /** LOTR's renderer frame: 1.5 up, turned, flipped (model space is y-down). */
    static void frame(PoseStack pose, BlockState state) {
        pose.translate(0.5, 1.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn(state)));
        pose.scale(-1, -1, 1);
    }

    static void draw(String model, ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light) {
        CreatureModel m = model(model);
        if (m != null) {
            m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                    OverlayTexture.NO_OVERLAY, -1);
        }
    }

    /** Plates (stacked food), the weapon rack (model + weapon) and the armour stand (model + armour). */
    static class Display implements BlockEntityRenderer<DisplayBlockEntity> {
        private ArmorStand dummy;

        @Override
        public void render(DisplayBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                int overlay) {
            switch (be.kind()) {
                case "weapon_rack" -> rack(be, pose, buffers, light);
                case "armor_stand" -> stand(be, partial, pose, buffers, light);
                default -> plate(be, pose, buffers, light);
            }
        }

        private void plate(DisplayBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
            ItemStack food = be.held();
            if (food.isEmpty()) {
                return;
            }
            // LOTR: each item lies flat, 1/32 above the last, turned by a random from the block position
            long seed = be.getBlockPos().asLong() * 116129781L;
            java.util.Random random = new java.util.Random(seed);
            pose.pushPose();
            pose.translate(0.5, 0.125 + 0.01, 0.5);
            for (int i = 0; i < food.getCount(); i++) {
                pose.pushPose();
                pose.translate(0, i * 0.03125, 0);
                pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360f));
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.scale(0.5f, 0.5f, 0.5f);
                Minecraft.getInstance().getItemRenderer().renderStatic(food, ItemDisplayContext.FIXED, light,
                        OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
                pose.popPose();
            }
            pose.popPose();
        }

        private void rack(DisplayBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
            boolean wall = WeaponRackBlock.onWall(be.getBlockState());
            pose.pushPose();
            pose.translate(0.5, 1.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(turn(be.getBlockState())));
            if (wall) {
                pose.translate(0, 0.375, -0.5); // LOTR: hung on the block behind
            }
            pose.scale(-1, -1, 1);
            rackPose(wall);
            draw("weapon_rack", WEAPON_RACK, pose, buffers, light);
            ItemStack weapon = be.held();
            if (!weapon.isEmpty()) {
                // LOTR's numbers: 0.625 size, mirrored, 0.52 down the model, turned 45 degrees so the icon's diagonal
                // hangs upright; then 1.7.10's flat icon spans 0..1 with its front at z 0
                pose.scale(-0.625f, 0.625f, 0.625f);
                pose.translate(0, 0.52, 0);
                if (wall) {
                    pose.translate(0, 1.1, 0.51);
                }
                pose.mulPose(Axis.ZP.rotationDegrees(45));
                pose.translate(0.5, 0.5, -1 / 32f);
                pose.scale(-1, 1, 1); // 1.7.10 drew the icon mirrored
                Minecraft.getInstance().getItemRenderer().renderStatic(weapon, ItemDisplayContext.NONE, light,
                        OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            }
            pose.popPose();
        }

        /** LOTRModelWeaponRack.onWall: the base turns up against the wall, without its stand or upper hooks. */
        private static void rackPose(boolean wall) {
            CreatureModel m = model("weapon_rack");
            if (m == null) {
                return;
            }
            ModelPart base = m.part("base");
            ModelPart stand = m.part("stand");
            ModelPart holder = m.part("holder");
            ModelPart upper = m.part("holder_upper_parts");
            if (base == null || stand == null || holder == null || upper == null) {
                return;
            }
            base.xRot = wall ? (float) Math.toRadians(-90) : 0;
            stand.visible = !wall;
            holder.xRot = 0;
            holder.y = wall ? -2 : -8;
            upper.visible = !wall;
        }

        private void stand(DisplayBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                int light) {
            pose.pushPose();
            frame(pose, be.getBlockState());
            draw("armor_stand", ARMOR_STAND, pose, buffers, light);
            pose.popPose();
            // the armour, drawn on an invisible vanilla armour stand (its armour layers draw while it's invisible)
            if (dummy == null || dummy.level() != be.getLevel()) {
                dummy = new ArmorStand(be.getLevel(), 0, 0, 0);
                dummy.setInvisible(true);
            }
            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET };
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                ItemStack s = be.getItem(i);
                dummy.setItemSlot(slots[i], s);
                any |= !s.isEmpty();
            }
            if (!any) {
                return;
            }
            float yaw = 180f - turn(be.getBlockState());
            dummy.setYRot(yaw);
            dummy.yRotO = yaw;
            dummy.setYBodyRot(yaw);
            dummy.yBodyRotO = yaw;
            dummy.setYHeadRot(yaw);
            dummy.yHeadRotO = yaw;
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0.5, 0.1, 0.5, yaw, partial, pose,
                    buffers, light);
        }
    }

    /** The kebab stand: LOTR's stand, and the meat on the spit, wider with more pieces, turning while it cooks. */
    static class Kebab implements BlockEntityRenderer<KebabStandBlockEntity> {
        private final ModelPart[] meat = new ModelPart[9];

        Kebab() {
            for (int i = 1; i < meat.length; i++) {
                int s = i + 1;
                MeshDefinition mesh = new MeshDefinition();
                mesh.getRoot().addOrReplaceChild("meat", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-s / 2f, 0, -s / 2f, s, 11, s), PartPose.offset(0, 10, 0));
                meat[i] = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("meat");
            }
        }

        @Override
        public void render(KebabStandBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                int light, int overlay) {
            pose.pushPose();
            frame(pose, be.getBlockState());
            String texture = LotrBlocks.texture(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath());
            draw("kebab_stand", LotrPort.id("textures/entity/block/kebab/" + (texture == null ? "stand" : texture)
                    + ".png"), pose, buffers, light);
            int n = Math.min(meat.length - 1, be.amount());
            if (n > 0) {
                ModelPart part = meat[n];
                part.yRot = be.cooking() && be.getLevel() != null
                        ? (be.getLevel().getGameTime() + partial) * 4f * Mth.DEG_TO_RAD : 0;
                part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(be.cooked() ? COOKED : RAW)), light,
                        OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
    }

    /** The unsmeltery: LOTR's model, in its active texture while it burns. */
    static class Unsmeltery implements BlockEntityRenderer<UnsmelteryBlockEntity> {
        private static final ResourceLocation IDLE = LotrPort.id("textures/entity/block/unsmeltery/idle.png");
        private static final ResourceLocation ACTIVE = LotrPort.id("textures/entity/block/unsmeltery/active.png");

        @Override
        public void render(UnsmelteryBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                int light, int overlay) {
            BlockState state = be.getBlockState();
            boolean lit = state.hasProperty(MachineBlock.LIT) && state.getValue(MachineBlock.LIT);
            pose.pushPose();
            frame(pose, state);
            draw("unsmeltery", lit ? ACTIVE : IDLE, pose, buffers, light);
            pose.popPose();
        }
    }

    /** LOTR's chests: 1.7.10's chest model (its textures use that layout), in the chest's own texture. */
    static class Chest implements BlockEntityRenderer<LotrChestBlockEntity> {
        private final ModelPart lid;
        private final ModelPart knob;
        private final ModelPart bottom;

        Chest() {
            MeshDefinition mesh = new MeshDefinition();
            var root = mesh.getRoot();
            root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(0, -5, -14, 14, 5, 14),
                    PartPose.offset(1, 7, 15));
            root.addOrReplaceChild("knob", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -15, 2, 4, 1),
                    PartPose.offset(8, 7, 15));
            root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(0, 0, 0, 14, 10, 14),
                    PartPose.offset(1, 6, 1));
            ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
            lid = baked.getChild("lid");
            knob = baked.getChild("knob");
            bottom = baked.getChild("bottom");
        }

        @Override
        public void render(LotrChestBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                int light, int overlay) {
            BlockState state = be.getBlockState();
            String texture = LotrBlocks.texture(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            var buffer = buffers.getBuffer(RenderType.entityCutout(
                    LotrPort.id("textures/entity/block/chest/" + (texture == null ? "stone" : texture) + ".png")));
            float open = 1f - be.getOpenNess(partial);
            open = 1f - open * open * open;
            pose.pushPose();
            // 1.7.10's chest frame: up one, flipped on y and z, turned about the middle
            pose.translate(0, 1, 1);
            pose.scale(1, -1, -1);
            pose.translate(0.5, 0.5, 0.5);
            Direction d = state.getValue(HorizontalDirectionalBlock.FACING);
            float angle = switch (d) {
                case NORTH -> 180;
                case WEST -> 90;
                case EAST -> -90;
                default -> 0;
            };
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(-0.5, -0.5, -0.5);
            lid.xRot = -open * Mth.HALF_PI;
            knob.xRot = lid.xRot;
            lid.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            knob.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            bottom.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

}
