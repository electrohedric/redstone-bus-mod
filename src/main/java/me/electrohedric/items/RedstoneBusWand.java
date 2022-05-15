package me.electrohedric.items;

import me.electrohedric.RedstoneBusMod;
import me.electrohedric.blocks.RedstoneBusBlock;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
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
    static double RANGE = 120; // second click (must be able to see redstone bus block)
    static double MAX_LENGTH = 256; // any longer and signal will probably not be able to propogate all the way down

    public RedstoneBusWand(Settings settings) {
        super(settings);
    }

    public void init() {
        // handle when the player left clicks client-side
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient) return ActionResult.PASS; // server passes

            // do special stuff if a block is whacked with this stick ...
            if (player.getStackInHand(hand).isOf(this)) {
                BlockState hitBlock = world.getBlockState(pos);
                if (hitBlock.isOf(RedstoneBusMod.REDSTONE_BUS)) {
                    player.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                    // ... namely setting the client-side variable, so we can access it later
                    lastBusClickedPos = pos;
                }
            }
            return ActionResult.PASS;
        });

        // handle when a player needs a bunch of blocks set server-side
        ServerPlayNetworking.registerGlobalReceiver(BLOCK_SETTER_PACKET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            BlockPos endPos = buf.readBlockPos();

            if (pos.getY() != endPos.getY()) return; // y's not aligned: invalid

            int dx = endPos.getX() - pos.getX();
            int dz = endPos.getZ() - pos.getZ();
            if ((dx == 0) == (dz == 0)) return; // same block OR neither aligned: invalid
            int dist = Math.abs(dx + dz); // only one will be nonzero
            if (dist > RANGE) return; // distance too far
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

                Direction placeDir = goDir.getOpposite(); // again, the place direction is weird
                if (state.get(RedstoneBusBlock.FACING) != placeDir) return; // state invalidated :(

                // ok, nothing's changed since the client sent the packet. we are ok to start setting blocks
                // but first, check that we won't overwrite anything except redstone buses and air
                BlockPos curPos = pos;
                for (int i = 0; i < dist; i++) {
                    curPos = curPos.offset(goDir);
                    BlockState testState = world.getBlockState(curPos);
                    // test that we won't overwrite anything important
                    if (testState.isAir()) continue; // most probable. check first
                    // is it a redstone bus facing the same direction? that's also ok.
                    if (testState.isOf(RedstoneBusMod.REDSTONE_BUS) && testState.get(RedstoneBusBlock.FACING) == placeDir) continue;

                    player.sendMessage(Text.of("Obstruction trying to place " + dist + " blocks at " + curPos.toShortString()), false);
                    return; // anything else we should abort
                }

                // set all the blocks :)
                curPos = pos;
                for (int i = 0; i < dist; i++) {
                    curPos = curPos.offset(goDir);
                    BlockState testState = world.getBlockState(curPos);
                    if (testState.isAir()) {
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
        double Bz = pos.getZ() + 0.5;
        double Vx = dir.getOffsetX(); // block vec (direction)
        double Vz = dir.getOffsetZ();
        double Px = playerEntity.getX(); // player pos
        double Pz = playerEntity.getZ();
        Vec3d look3d = playerEntity.getRotationVector();
        double Lx = look3d.x; // look vec (direction)
        double Lz = look3d.z;
        double Dx = Px - Bx; // delta
        double Dz = Pz - Bz;

        // using linear algebra... solving the system of equations:
        // sVx - tPx = Dx
        // sVz - tPz = Dz
        // where s is the scalar for the block direction unit vector (blockVecMag)
        //   and t is the scalar for the player direction unit vector (playerVecMag)
        //
        //              LzDx - LxDz             VzDx - VxDz
        // we get  s = -------------  and  t = -------------
        //              VxLz - VzLx             VxLz - VzLx
        //
        // so we conclude that there is no solution if VxLz = VzLx:
        //   (aka the cross product of the look vector and block vector is 0) ((aka looking parallel to block))
        //   if this is the case, we assume no intersection
        //   if the denominator is tiny, this may create a very large block vector
        //   so we will also ignore block vectors which are more than 100% than its max length
        // there is another edge case where the player is looking straight up or down:
        //   if this is the case, just project the block -> player vector (D) onto the block vector
        //   it is likely that the player is looking straight down onto where they want the bus to end
        //   projection will easily catch that case and any other similar situations intuitively
        double playerVecMag = 0, blockVecMag = -1; // invalid by default
        if (Lx * Lx + Lz * Lz < 0.01) {
            // looking very straight up or down
            double Py = playerEntity.getEyeY();
            double By = pos.getY();
            double Dy = Py - By; // player is higher = positive
            double Ly = look3d.y; // player is looking up = positive
            if (Dy * Ly <= 0) { // opposite sign or same level
                playerVecMag = 1; // valid
                blockVecMag = Dx * Vx + Dz * Vz; // proj D -> V = dot product of D and V when magnitude of V is 1
            }
        } else {
            // not looking straight up or down
            double denom = Vx * Lz - Vz * Lx;
            // checks denom != 0 but also edge cases where player look vector is too close to the block vector
            //   and thus would be very inprecise and not what the player expects
            if (Math.abs(denom) > 0.05) {
                // probably looking at the block vector line
                blockVecMag = (Lz * Dx - Lx * Dz) / denom;
                playerVecMag = (Vz * Dx - Vx * Dz) / denom;
            }
        }

        // if block vector is non-positive (hence not going the right direction) do not place any blocks
        // if the block vector is too large (explained above) do the same
        // if look vector is negative, the player isn't actually looking in the right direction
        // if look vector is too long, they probably aren't accurate anyway
        if (blockVecMag <= 0.5 || blockVecMag > 1.5 * MAX_LENGTH || playerVecMag < 0 || playerVecMag > RANGE) {
            return null;
        }

        // extend the block pos out to magnitude calculated, up to max RANGE
        int blockMag = (int) Math.min(Math.round(blockVecMag), MAX_LENGTH);
        return pos.offset(dir, blockMag);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity playerEntity, Hand hand) {
        /* item right click */

        // client side only. we will send a packet to the server to do the block filling later
        // the server doesn't even know what the original position clicked is
        ItemStack item = playerEntity.getStackInHand(hand);
        if (!world.isClient) return TypedActionResult.pass(item);

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

        Vec3i posVec = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
        playerEntity.sendMessage(Text.of("Bus is " + endPos.getManhattanDistance(posVec) + " blocks"), true);

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
