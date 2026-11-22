package com.kokuhaku.block;

import com.kokuhaku.Kokuhaku;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Custom blocks: numbered shoe lockers, vending machines, job boards, and a body for the crime scenes. */
public final class ModBlocks {
    public static final Block LOCKER = register("locker", new LockerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VENDING_MACHINE = register("vending_machine", new ThinCollisionBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque().luminance(s -> 7)));
    /** Nothing you can see: it fills the cell above a vending machine, so the top half can be clicked at eye level too. */
    public static final Block VENDING_MACHINE_TOP = register("vending_machine_top", new VendingMachineTop(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));
    public static final Block JOB_BOARD = register("job_board", new ThinPanelBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));
    public static final Block EVIDENCE = register("evidence", new EvidenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque().noCollision()));

    public static final Block RADIO = register("radio", new RadioBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque()));

    /** One and a half blocks wide and one tall: the rock on the beach. */
    public static final Block ROCK = register("rock", new RockBlock(AbstractBlock.Settings.copy(Blocks.STONE).nonOpaque()));

    private ModBlocks() {
    }

    /** The upper half of a vending machine. The block below draws the model; this one only answers the crosshair. */
    private static final class VendingMachineTop extends Block {
        /** The whole machine, from up here, so it outlines the same as the bottom half does. */
        private static final VoxelShape OUTLINE = Block.createCuboidShape(1, -16, 1, 15, 16, 15);
        private static final VoxelShape COLLISION = Block.createCuboidShape(1, 0, 1, 15, 16, 15);

        VendingMachineTop(Settings settings) {
            super(settings);
        }

        @Override
        public BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.INVISIBLE;
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return OUTLINE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return COLLISION;
        }
    }

    /** Forces the class to load so the blocks register during mod init. */
    public static void init() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(Kokuhaku.MOD_ID, name), block);
    }
}
