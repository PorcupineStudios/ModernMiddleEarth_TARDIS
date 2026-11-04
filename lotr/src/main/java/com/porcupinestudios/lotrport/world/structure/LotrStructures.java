package com.porcupinestudios.lotrport.world.structure;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The structure type {@code lotr_port:lotr} and its piece type. */
public final class LotrStructures {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, LotrPort.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<LotrStructure>> LOTR =
            TYPES.register("lotr", () -> () -> LotrStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE =
            PIECES.register("lotr", () -> (StructurePieceType.StructureTemplateType) LotrStructurePiece::new);

    private LotrStructures() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
