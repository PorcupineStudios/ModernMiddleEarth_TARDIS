package com.porcupinestudios.lotrport.mount;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * LOTR's mount armour (LOTRItemMountArmor): fits one kind of mount (horse, boar, elk, rhino, warg), protects it by
 * its material's chestplate + leggings armour, and is drawn from its own texture over the mount.
 */
public class MountArmorItem extends Item {
    private final String mount;
    private final ResourceLocation texture;

    public MountArmorItem(Properties properties, String mount, int protection, ResourceLocation texture) {
        super(properties.stacksTo(1).attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(LotrPort.id("mount_armor"), protection, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.BODY).build()));
        this.mount = mount;
        this.texture = texture;
    }

    /** horse, boar, elk, rhino or warg. */
    public String mount() {
        return mount;
    }

    public ResourceLocation texture() {
        return texture;
    }
}
