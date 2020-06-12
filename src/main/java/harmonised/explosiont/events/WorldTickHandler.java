package harmonised.explosiont.events;

import harmonised.explosiont.config.Config;
import harmonised.explosiont.util.BlockInfo;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
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
import net.minecraftforge.event.TickEvent;

import java.util.*;

public class WorldTickHandler
{
    private static final Random rand = new Random();
    private static final Map<ResourceLocation, Double> dimLastHeal = new HashMap<>();
    private static final Set<ResourceLocation> dimForceHeal = new HashSet<>();
    private static final Double ticksPerHeal = Config.config.ticksPerHeal.get();
    private static final Integer speedUpTreshold = Config.config.speedUpTreshold.get();
//    public static long lastHeal = System.currentTimeMillis();

    public static void handleWorldTick( TickEvent.WorldTickEvent event )
    {
        ResourceLocation dimResLoc = ( event.world.dimension.getType().getRegistryName() );
        World world = event.world;
        boolean forceHeal = dimForceHeal.contains( dimResLoc );

        if( !ChunkDataHandler.toHealDimMap.containsKey( world.dimension.getType().getRegistryName() ) )
            ChunkDataHandler.toHealDimMap.put( world.dimension.getType().getRegistryName(), new ArrayList<>() );
        List<BlockInfo> blocksToHeal = ChunkDataHandler.toHealDimMap.get( world.dimension.getType().getRegistryName() );

        blocksToHeal.forEach( blockToHeal ->
        {
            blockToHeal.ticksLeft--;
        });

        if( !dimLastHeal.containsKey( dimResLoc ) )
            dimLastHeal.put( dimResLoc, 0D );

        dimLastHeal.replace( dimResLoc, dimLastHeal.get( dimResLoc ) + 1 );
        double cost = ticksPerHeal;

        while( forceHeal || dimLastHeal.get( dimResLoc ) > cost )
        {
            if( blocksToHeal.size() > speedUpTreshold && speedUpTreshold > 0 )
                cost = ticksPerHeal * ( speedUpTreshold / (double) (blocksToHeal.size() ) );
            else
                cost = ticksPerHeal;

//            System.out.println( blocksToHeal.size() + " " + ticksPerHeal + " " + cost );
            dimLastHeal.replace( dimResLoc, Math.max( 0, dimLastHeal.get( dimResLoc ) - cost ) );

            if( !processBlock( event, forceHeal ) )
            {
                dimForceHeal.remove( dimResLoc );
                break;
            }
        }
    }

    public static boolean processBlock( TickEvent.WorldTickEvent event, boolean forceHeal )
    {
        World world = event.world;
        List<BlockInfo> blocksToHeal = ChunkDataHandler.toHealDimMap.get( world.dimension.getType().getRegistryName() );

        if( blocksToHeal.size() > 0 )
        {
            int index = 0;
            BlockInfo blockInfo = blocksToHeal.get( index );
            ChunkPos chunkPos = new ChunkPos( blockInfo.pos );
            boolean chunkExists = checkChunkExists( world, chunkPos, forceHeal );

            if( forceHeal )
                blockInfo.ticksLeft = 0;

            while( !chunkExists || blockInfo.ticksLeft > 0 )
            {
                if( blocksToHeal.size() > ++index )
                {
                    blockInfo = blocksToHeal.get( index );
                    chunkPos = new ChunkPos( blockInfo.pos );
                    chunkExists = checkChunkExists( world, chunkPos, forceHeal );
                }
                else
                    break;
            }

            if( chunkExists && blockInfo.ticksLeft <= 0 )
            {
                BlockPos pos = blockInfo.pos;
                Block block = world.getBlockState(pos).getBlock();
                IFluidState fluidInfo = world.getFluidState(pos);

                if ( block.equals( Blocks.AIR ) || block.equals( Blocks.CAVE_AIR ) || ( !fluidInfo.isEmpty() && !fluidInfo.isSource() ) )
                {
                    if( blockInfo.state.has( GrassBlock.SNOWY ) )
                        blockInfo.state = blockInfo.state.with( GrassBlock.SNOWY, false );
                    world.setBlockState( pos, blockInfo.state );
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
                        world.playSound(null, a.getPositionVector().getX(), a.getPositionVector().getY(), a.getPositionVector().getZ(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.BLOCKS, 0.8F + rand.nextFloat() * 0.4F, 0.9F + rand.nextFloat() * 0.15F);
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
                blocksToHeal.remove( blockInfo );
            }
            else
                return false;
        }
        else
            return false;

        return true;
    }

    private static boolean checkChunkExists( World world, ChunkPos chunkPos, boolean forceHeal )
    {
        return world.chunkExists( chunkPos.x, chunkPos.z ) || forceHeal;
    }

    public static void forceAllHeal()
    {
        ChunkDataHandler.toHealDimMap.forEach( (key, value) ->
        {
            dimForceHeal.add( key );
        });
    }
}
