package harmonised.explosiont.events;

import harmonised.explosiont.config.Config;
import harmonised.explosiont.util.BlockInfo;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;

import java.util.*;

public class WorldTickHandler
{
    private static final Random rand = new Random();
    private static final Map<ResourceLocation, Map<Integer, Double>> dimLastHeal = new HashMap<>();
    private static final Map<ResourceLocation, Set<Integer>> dimForceHeal = new HashMap<>();
    private static final Double ticksPerHealExplosion = Config.config.ticksPerHealExplosion.get();
    private static final Double ticksPerHealFire = Config.config.ticksPerHealFire.get();
    private static final Integer speedUpTresholdExplosion = Config.config.speedUpTresholdExplosion.get();
    private static final Integer speedUpTresholdFire = Config.config.speedUpTresholdFire.get();
    private static final boolean onlyHealPastMorning = Config.config.onlyHealPastMorning.get();
    private static boolean wasDay = false;

    public static void handleWorldTick( TickEvent.WorldTickEvent event )
    {
        ResourceLocation dimResLoc = ( event.world.dimension.getType().getRegistryName() );
        if( !dimForceHeal.containsKey( dimResLoc ) )
            dimForceHeal.put( dimResLoc, new HashSet<>() );
        World world = event.world;
        boolean forceHeal;

        if( !ChunkDataHandler.toHealDimMap.containsKey( world.dimension.getType().getRegistryName() ) )
            ChunkDataHandler.toHealDimMap.put( world.dimension.getType().getRegistryName(), new HashMap<>() );

        for( Map.Entry<Integer, List<BlockInfo>> entry : ChunkDataHandler.toHealDimMap.get( world.dimension.getType().getRegistryName() ).entrySet() )
        {
            forceHeal = dimForceHeal.get( dimResLoc ).contains( entry.getKey() );
            List<BlockInfo> blocksToHeal = entry.getValue();

            if( onlyHealPastMorning || forceHeal )
            {
                if( ( !wasDay && world.isDaytime() ) || forceHeal )
                {
                    blocksToHeal.forEach( blockToHeal ->
                    {
                        blockToHeal.ticksLeft = -1;
                    });
                }
            }
            else
            {
                blocksToHeal.forEach( blockToHeal ->
                {
                    blockToHeal.ticksLeft--;
                });
            }

            if( !dimLastHeal.containsKey( dimResLoc ) )
                dimLastHeal.put( dimResLoc, new HashMap<>() );

            healBlocks( world, blocksToHeal, entry.getKey(), forceHeal );
            if( blocksToHeal.size() == 0 )
                dimForceHeal.get( dimResLoc ).remove( entry.getKey() );
        }

        wasDay = world.isDaytime();
    }

    private static void healBlocks( World world, List<BlockInfo> blocksToHeal, int type, boolean forceHeal )
    {
       ResourceLocation dimResLoc = world.dimension.getType().getRegistryName();

        if( blocksToHeal.size() > 0 )
        {
            double ticksPerHeal;
            int speedUpTreshold;

            if( type == 0 )
            {
                ticksPerHeal = ticksPerHealExplosion;
                speedUpTreshold = speedUpTresholdExplosion;
            }
            else
            {
                ticksPerHeal = ticksPerHealFire;
                speedUpTreshold = speedUpTresholdFire;
            }

            if( !dimLastHeal.get( dimResLoc ).containsKey( type ) )
                dimLastHeal.get( dimResLoc ).put( type, 0D );

            dimLastHeal.get( dimResLoc ).replace( type, dimLastHeal.get( dimResLoc ).get( type ) + 1 );     //add tick
            int toHeal;
            double cost;

            if( blocksToHeal.size() > speedUpTreshold && speedUpTreshold > 0 )      //get cost, scale if needed
                cost = ticksPerHeal * ( speedUpTreshold / (double) (blocksToHeal.size() ) );
            else
                cost = ticksPerHeal;

            toHeal = (int) ( dimLastHeal.get( dimResLoc ).get( type ) / cost );
            dimLastHeal.get( dimResLoc ).replace( type, dimLastHeal.get( dimResLoc ).get( type ) % cost );  //take away cost for each block

            int index = -1;
            BlockInfo blockInfo;
            ChunkPos chunkPos;
            boolean chunkExists;
            int healed = 0;

            while( healed < toHeal || forceHeal )
            {
                if( blocksToHeal.size() > ++index )
                {
                    blockInfo = blocksToHeal.get( index );
                    chunkPos = new ChunkPos( blockInfo.pos );
                    chunkExists = checkChunkExists( world, chunkPos );

                    if( chunkExists || forceHeal )
                    {
                        if( blockInfo.ticksLeft < 0 || forceHeal )
                        {
                            healBlock( world, blockInfo );
                            blocksToHeal.remove( blockInfo );
                            healed++;
                        }
                    }
                }
                else
                    break;
            }
        }
        else
        {
            dimForceHeal.get( dimResLoc ).remove( type );
            dimLastHeal.get( dimResLoc ).replace( type, 0D );
        }
    }

    private static void healBlock( World world, BlockInfo blockInfo )
    {
        BlockPos pos = blockInfo.pos;
        Block block = world.getBlockState(pos).getBlock();
        IFluidState fluidInfo = world.getFluidState(pos);

        if ( block.equals( Blocks.AIR ) || block.equals( Blocks.CAVE_AIR ) || block.equals( Blocks.FIRE ) || ( !fluidInfo.isEmpty() && !fluidInfo.isSource() ) )
        {
//            System.out.println( blockInfo.type + " " + ( blockInfo.type == 0 ? 3 : 2 | 16 ) );
            if( blockInfo.state.has( GrassBlock.SNOWY ) )
                blockInfo.state = blockInfo.state.with( GrassBlock.SNOWY, false );
            if( blockInfo.state.has( LeavesBlock.DISTANCE ) )
                blockInfo.state = blockInfo.state.with( LeavesBlock.DISTANCE, 1 );
//            world.setBlockState( pos, blockInfo.type == 0 ? blockInfo.state.getBlock().getDefaultState() : blockInfo.state, blockInfo.type == 0 ? 3 : 2 | 16 );
            world.setBlockState( pos, blockInfo.state, blockInfo.type == 0 ? 3 : 2 | 16 );
            if (blockInfo.tileEntityNBT != null && blockInfo.tileEntityNBT.size() > 0)
                world.setTileEntity(pos, TileEntity.create(blockInfo.tileEntityNBT));
//                    blockInfo.state.updateNeighbors( world, pos, 0 );

            world.getEntitiesWithinAABB( Entity.class, new AxisAlignedBB( pos, pos.up().south().east() ) ).forEach( a ->
            {
                BlockPos entityPos = a.getPosition();
                int i = 1;
                while( world.getBlockState( entityPos.up( i ) ).isSolid() || world.getBlockState( entityPos.up( i + 1 ) ).isSolid() )
                {
                    i++;
                }
                a.setPosition( a.getPositionVector().getX(), a.getPositionVector().y + i, a.getPositionVector().z );
                world.playSound(null, a.getPositionVector().getX(), a.getPositionVector().getY(), a.getPositionVector().getZ(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.BLOCKS, 0.8F + rand.nextFloat() * 0.4F, 0.9F + rand.nextFloat() * 0.15F );
            });
        }
        else
        {
            Block.spawnAsEntity(world, pos, new ItemStack(blockInfo.state.getBlock().asItem()));
            if (blockInfo.tileEntityNBT != null && blockInfo.tileEntityNBT.contains("Items"))
            {
                ListNBT items = (ListNBT) blockInfo.tileEntityNBT.get("Items");
                if (items != null)
                {
                    for (int i = 0; i < items.size(); i++)
                    {
                        Block.spawnAsEntity(world, pos, ItemStack.read(items.getCompound(i)));
                    }
                }
            }
        }

        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.15F);
    }

    private static boolean checkChunkExists( World world, ChunkPos chunkPos )
    {
        return world.chunkExists( chunkPos.x, chunkPos.z );
    }

    public static void forceAllHeal()
    {
        //Map<ResourceLocation, Map<Integer, List<BlockInfo>>>
        ChunkDataHandler.toHealDimMap.forEach( (key, value) ->
        {
            if( !dimForceHeal.containsKey( key ) )
                dimForceHeal.put( key, new HashSet<>() );

            value.forEach( (key2, value2) ->
            {
                dimForceHeal.get( key ).add( key2 );
            });
        });
    }
}
