package me.electrohedric.items;

import com.mojang.blaze3d.systems.RenderSystem;
import me.electrohedric.RedstoneBusMod;
import me.electrohedric.blocks.RedstoneBusBlock;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class RedstoneBusWand extends Item {
    public static final Identifier BLOCK_SETTER_PACKET = new Identifier(RedstoneBusMod.MODID + "_block_setter");

    @Environment(EnvType.CLIENT)
    static BlockPos lastBusClickedPos = null; // client-side only. destroyed on client restart
    @Environment(EnvType.CLIENT)
    static BlockPos currentBusLineLookPos = null;
    @Environment(EnvType.CLIENT)
    static boolean isHoldingWand = false;

    static double RANGE = 120; // second click (must be able to see redstone bus block)
    static double MAX_LENGTH = 256; // any longer and signal will probably not be able to propogate all the way down

    public RedstoneBusWand(Settings settings) {
        super(settings);
    }

    public void clientInit() {
        // handle when the player left clicks client-side
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            // do special stuff if a block is whacked with this stick ...
            if (player.getStackInHand(hand).isOf(this)) {
                BlockState hitBlock = world.getBlockState(pos);
                if (hitBlock.isOf(RedstoneBusMod.REDSTONE_BUS)) {
                    player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                    // ... namely setting the client-side variable, so we can access it later
                    lastBusClickedPos = pos;
                } else if (lastBusClickedPos != null) {
                    // TODO: if someone can figure out how to catch swing events and not just block swing events
                    //   that is what i would replace this with
                    lastBusClickedPos = null; // every swing (which hits a non-bus block) erases the position
                    currentBusLineLookPos = null;
                    player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.6f);
                }
            }
            return ActionResult.PASS;
        });

        // do ghost rendering
        WorldRenderEvents.LAST.register(context -> {
            if (lastBusClickedPos == null || !isHoldingWand) return;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            Vec3d cam = context.camera().getPos().negate();
            Box bb;
            if (currentBusLineLookPos == null) {
                bb = new Box(lastBusClickedPos);
            } else {
                // create a bounding box around both positions
                BlockPos p1 = lastBusClickedPos;
                BlockPos p2 = currentBusLineLookPos;

                bb = new Box(Math.min(p1.getX(), p2.getX()), p1.getY(), Math.min(p1.getZ(), p2.getZ()),
                        Math.max(p1.getX(), p2.getX()) + 1, p1.getY() + 1, Math.max(p1.getZ(), p2.getZ()) + 1);
            }
            // make smaller like slab and move to camera position so it can place correctly (idk, look at DebugRenderer)
            bb = bb.withMaxY(bb.maxY - 0.8);
            DebugRenderer.drawBox(bb.offset(cam),1.0f, 0.05f, 0.15f, 0.2f);
        });
    }

    public void serverInit() {
        // handle when a player needs a bunch of blocks set server-side
        ServerPlayNetworking.registerGlobalReceiver(BLOCK_SETTER_PACKET, (server, player, handler, buf, responseSender) -> {
            if (!player.isCreative()) return; // there's no permissions yet, so we just check if they are probably a mod

            BlockPos pos = buf.readBlockPos();
            BlockPos endPos = buf.readBlockPos();

            if (pos.getY() != endPos.getY()) return; // y's not aligned: invalid

            int dx = endPos.getX() - pos.getX();
            int dz = endPos.getZ() - pos.getZ();
            if ((dx == 0) == (dz == 0)) return; // same block OR neither aligned: invalid
            int dist = Math.abs(dx + dz); // only one will be nonzero
            if (dist > MAX_LENGTH) return; // too many blocks to place
            World world = player.getWorld();

            // blocks are aligned in either the x or z, but not both
            server.execute(() -> {
                BlockState state = world.getBlockState(pos);
                if (!state.isOf(RedstoneBusMod.REDSTONE_BUS)) return; // state invalidated by the time we got here :(

                Direction goDir;
                if (dx != 0) { // along x
                    goDir = dx > 0 ? Direction.EAST : Direction.WEST;
                } else { // along z
                    goDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
                }
                Direction reverseDir = goDir.getOpposite();

                // ok, nothing's changed since the client sent the packet. we are ok to start setting blocks
                // but first, check that we won't overwrite anything except redstone buses and air
                BlockPos curPos = pos;
                for (int i = 0; i < dist; i++) {
                    curPos = curPos.offset(goDir);
                    BlockState testState = world.getBlockState(curPos);
                    BlockPos underPos = curPos.down();
                    BlockState underState = world.getBlockState(underPos);

                    /* test that we won't overwrite anything important */

                    // test 1a: overwriting air
                    if (testState.isAir()) {
                        // test 1b: block underneath can support redstone-like blocks OR we can place one
                        if (underState.isAir()) continue;
                        if (underState.isSideSolid(world, underPos, Direction.UP, SideShapeType.RIGID)) continue;
                        player.sendMessage(Text.of("Bad support block at " + underPos.toShortString()), false);
                        return; // fail
                    }

                    // test 2: redstone bus aligned in same axis? that's also ok.
                    if (testState.isOf(RedstoneBusMod.REDSTONE_BUS)) {
                        Direction overwriteFacing = testState.get(RedstoneBusBlock.FACING);
                        if (overwriteFacing == goDir || overwriteFacing == reverseDir) continue;
                        // fall through: obstruction
                    }

                    player.sendMessage(Text.of("Obstruction at " + curPos.toShortString()), false);
                    return; // fail
                }

                // cache stuff we need for validating
                Direction properFacing = state.get(RedstoneBusBlock.FACING);
                BlockState supportBlock = world.getBlockState(pos.down());

                // TODO: check that we aren't cutting off a redstone staircase or something
                // set all the blocks :)

                curPos = pos;
                for (int i = 0; i < dist; i++) {
                    curPos = curPos.offset(goDir);

                    // give the block support (of the original pos support type) if it needs it
                    BlockPos underPos = curPos.down();
                    BlockState underState = world.getBlockState(underPos);
                    if (underState.isAir()) {
                        world.setBlockState(underPos, supportBlock, Block.NOTIFY_LISTENERS, 0);
                    }

                    // not afraid of overwriting something important (we already checked that)
                    // just need to check this condition to only set blocks which aren't correct
                    BlockState testState = world.getBlockState(curPos);
                    if (testState.isAir() || (testState.isOf(RedstoneBusMod.REDSTONE_BUS) &&
                            testState.get(RedstoneBusBlock.FACING) != properFacing)) {
                        world.setBlockState(curPos, state, Block.NOTIFY_LISTENERS, 0);
                    }
                }
            });
        });
    }

    public BlockPos calcWandEndPos(PlayerEntity playerEntity, BlockPos pos, BlockState state) {
        // for some reason, redstone gates (the superclass of RedstoneBusBlock) are totally directionally backwards
        //   (i.e. north is south, east is west, etc.)
        Direction dir = state.get(RedstoneBusBlock.FACING).getOpposite();

        double Bx = pos.getX() + 0.5; // center of block pos
        double By = pos.getY();
        double Bz = pos.getZ() + 0.5;
        double Vx = dir.getOffsetX(); // block vec (direction)
//        double Vy = 0;
        double Vz = dir.getOffsetZ();
        double Px = playerEntity.getX(); // player pos
        double Py = playerEntity.getEyeY();
        double Pz = playerEntity.getZ();
        Vec3d look3d = playerEntity.getRotationVector();
        double Lx = look3d.x; // look vec (direction)
        double Ly = look3d.y;
        double Lz = look3d.z;
        double Dx = Px - Bx; // delta
        double Dy = Py - By;
        double Dz = Pz - Bz;

        // solve the system of equations:
        // sVx - tLx = Dx
        // sVz - tLz = Dz
        // where s is the scalar for the block direction unit vector (blockVecMag)
        //   and t is the scalar for the player direction unit vector (playerVecMag)
        // this represents the situation where the player is looking horizontally at the bus axis
        //
        //              LzDx - LxDz             VzDx - VxDz
        // we get  s = -------------  and  t = -------------
        //              VxLz - VzLx             VxLz - VzLx
        //
        // so we conclude that there is no solution if VxLz = VzLx:
        //   (aka the cross product of the look vector and block vector is 0) ((aka looking parallel to block))
        //
        // also solve the equation:
        //   tLy = -Dy
        // where t is the same as above, but Dy is negated because when Ly is postive, the player
        //   is looking up and the left side of the equation will be positive, but Dy would be negative
        // this represents the situation where the player is looking vertically at the bus plane
        //
        // we get  t = -Dy / Ly
        //   then get the position on the plane by projecting the look vector out that far
        //   from there we can project that position vector onto the bus axis
        //
        // we conclude that there is no solution if Ly == 0
        //   (aka the player is looking horizontally)

        // first we have to calculate which calculation would provide better accuracy
//        double projBlockMag = Dx * Vx + Dz * Vz; // proj D -> V = dot product of D and V when magnitude of V is 1
//        double projBX = Bx + dir.getOffsetX() * projBlockMag;
//        double projBZ = Bz + dir.getOffsetZ() * projBlockMag;
//        double hDist = Math.abs(Px - projBX + Pz - projBZ); // manhatten, but one delta is going to be 0
//        double vDist = Math.abs(Dy);

        // try both (assuming they are valid) and pick the shorter one
        // min(a, b) is guaranteed to be a smooth transition
        // it also forces the player to be more accurate when describing the position
        double playerVecMag = 0, blockVecMag = MAX_LENGTH; // invalid by default
        // assuming vertical placement is about 1.5x as good as horizontal, although horizontal is still crucial
        if (Math.abs(Ly) > 0.01) {
            playerVecMag = -Dy / Ly;
            double planeX = Px + Lx * playerVecMag;
            double planeZ = Pz + Lz * playerVecMag;
            double planeOffX = planeX - Bx;
            double planeOffZ = planeZ - Bz;
            blockVecMag = (planeOffX * Vx + planeOffZ * Vz); // proj plane delta -> V
        }
        double denom = Vx * Lz - Vz * Lx;
        // checks denom != 0 but also edge cases where player look vector is too close to the block vector
        //   and thus would be very inprecise and not what the player expects
        double tempBlockMag = (Lz * Dx - Lx * Dz) / denom;
        if (Math.abs(denom) > 0.02 && Math.abs(tempBlockMag) < Math.abs(blockVecMag)) {
            // probably looking at the block vector line
            blockVecMag = tempBlockMag;
            playerVecMag = (Vz * Dx - Vx * Dz) / denom;
        }

        // if block vector is non-positive (hence not going the right direction) do not place any blocks
        // if the look vector is negative, the player isn't actually looking in the right direction
        // if the look vector is too long, they probably aren't accurate anyway
        if (Math.abs(blockVecMag) <= 0.5 || playerVecMag <= 0 || playerVecMag > RANGE) {
            return null;
        }

        // extend the block pos out to magnitude calculated, up to max RANGE
        int blockMag = (int) Math.min(Math.max(Math.round(blockVecMag), -MAX_LENGTH), MAX_LENGTH);
        return pos.offset(dir, blockMag);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient) return;
        /* 20 times a second, whenever the item is in the inventory, for every item */

        if (!selected) {
            isHoldingWand = false;
            return;
        }
        isHoldingWand = true;

        if (lastBusClickedPos == null) return;

        // run a simulation and update the rendering vars
        if (entity instanceof PlayerEntity p) {
            BlockState state = world.getBlockState(lastBusClickedPos);
            if (state.isOf(RedstoneBusMod.REDSTONE_BUS)) {
                currentBusLineLookPos = calcWandEndPos(p, lastBusClickedPos, state);
                if (currentBusLineLookPos != null) {
                    Vec3i pos1 = new Vec3i(lastBusClickedPos.getX(), lastBusClickedPos.getY(), lastBusClickedPos.getZ());
                    int dist = currentBusLineLookPos.getManhattanDistance(pos1);
                    p.sendMessage(Text.of("Bus length: " + dist), true);
                }
            }

        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity playerEntity, Hand hand) {
        /* item right click */

        // client side only. we will send a packet to the server to do the block filling later
        // the server doesn't even know what the original position clicked is
        ItemStack item = playerEntity.getStackInHand(hand);
        if (!world.isClient) return TypedActionResult.pass(item);
        if (!playerEntity.isCreative()) return TypedActionResult.pass(item);

        // did they click on a bus before doing this?
        BlockPos pos = lastBusClickedPos;
        if (pos == null) {
            playerEntity.sendMessage(Text.of("No position set"), true);
            return TypedActionResult.fail(item);
        }

        // get original click pos and rotation
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(RedstoneBusMod.REDSTONE_BUS)) {
            playerEntity.sendMessage(Text.of("Position " + pos.toShortString() + " no longer valid"), true);
            return TypedActionResult.fail(item); // no original click pos
        }

        BlockPos endPos = calcWandEndPos(playerEntity, pos, state);
        if (endPos == null) {
            // player either not looking at the block vector line or too far or too close
            return TypedActionResult.fail(item);
        }

        // send the start and end positions to the server for block setting
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBlockPos(endPos);
        ClientPlayNetworking.send(BLOCK_SETTER_PACKET, buf);

        return TypedActionResult.success(item);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        // don't destroy anything when clicked
        return false;
    }
}
