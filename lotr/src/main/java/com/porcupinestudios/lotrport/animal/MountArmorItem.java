package com.porcupinestudios.lotrport.animal;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * LOTR's mount armor (LOTRItemMountArmor): fits one kind of mount ({@code horse}, {@code boar}, {@code rhino},
 * {@code elk}, {@code warg}...), protects by LOTR's amount (its material's body defense) and is drawn over the mount
 * with its own texture. Horse armor is vanilla body armor, so vanilla horses (and the Shire pony) take it.
 */
public class MountArmorItem extends AnimalArmorItem {
    private final String mount;
    @Nullable
    private final ResourceLocation texture;

    public MountArmorItem(Holder<ArmorMaterial> material, String mount, @Nullable ResourceLocation texture,
            Properties properties) {
        super(material, mount.equals("horse") ? BodyType.EQUESTRIAN : BodyType.CANINE, false, properties);
        this.mount = mount;
        this.texture = texture;
    }

    public String mount() {
        return mount;
    }

    /** The texture drawn over the mount's skin (LOTR layered it onto the skin). */
    @Nullable
    public ResourceLocation armorTexture() {
        return texture;
    }
}
