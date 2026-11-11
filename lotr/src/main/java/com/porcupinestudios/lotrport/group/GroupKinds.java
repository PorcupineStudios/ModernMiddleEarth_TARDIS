package com.porcupinestudios.lotrport.group;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.resources.ResourceLocation;

/** The kinds of group and the roles the port uses so far. New kinds and roles need no change to {@link Group}. */
public final class GroupKinds {
    /** A colony: its group has the colony's id, and its members are the citizens. */
    public static final ResourceLocation COLONY = LotrPort.id("colony");
    /** A player's hired units. */
    public static final ResourceLocation COMPANY = LotrPort.id("company");

    public static final ResourceLocation ROLE_CITIZEN = LotrPort.id("citizen");
    public static final ResourceLocation ROLE_FOLLOWER = LotrPort.id("follower");

    /** Member flag: joined by a recruitment quest, not migration. */
    public static final String FLAG_RARE = "rare";

    private GroupKinds() {}
}
