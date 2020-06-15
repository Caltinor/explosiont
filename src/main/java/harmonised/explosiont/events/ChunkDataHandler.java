package harmonised.explosiont.events;

import harmonised.explosiont.util.BlockInfo;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.event.world.ChunkDataEvent;

import java.util.*;

public class ChunkDataHandler
{
    public static Map<ResourceLocation, Map<Integer, List<BlockInfo>>> toHealDimMap = new HashMap<>();

    public static void init()
    {
        toHealDimMap = new HashMap<>();
    }
    
    public static void handleChunkDataLoad( ChunkDataEvent.Load event )
    {
        CompoundNBT levelNBT = event.getData();
        if( levelNBT != null )
        {
            if( levelNBT.contains( "blocksToHeal" ) )
            {
                ResourceLocation dimResLoc = event.getWorld().getDimension().getType().getRegistryName();
                if( !toHealDimMap.containsKey( dimResLoc ) )
                {
                    toHealDimMap.put( dimResLoc, new HashMap<>() );
                    toHealDimMap.get( dimResLoc ).put( 0, new ArrayList<>() );
                    toHealDimMap.get( dimResLoc ).put( 1, new ArrayList<>() );
                }
                List<BlockInfo> blocksToHealExplosion = toHealDimMap.get( dimResLoc ).get( 0 );
                List<BlockInfo> blocksToHealFire = toHealDimMap.get( dimResLoc ).get( 1 );
                List<BlockInfo> blocksToAddExplosion = new ArrayList<>();
                List<BlockInfo> blocksToAddFire = new ArrayList<>();

                CompoundNBT blocksToHealNBT = ( (CompoundNBT) levelNBT.get( "blocksToHeal" ) );
                if( blocksToHealNBT == null )
                    return;
                Set<String> keySet = blocksToHealNBT.keySet();

                keySet.forEach( key ->
                {
                    CompoundNBT entry = blocksToHealNBT.getCompound( key );
                    if( entry.getInt( "type" ) == 0 )
                        blocksToAddExplosion.add( new BlockInfo( dimResLoc, NBTUtil.readBlockState( entry.getCompound( "state" ) ), NBTUtil.readBlockPos( entry.getCompound( "pos" ) ), entry.getInt( "ticksLeft" ), entry.getInt( "type" ), entry.getCompound( "tileEntity" ) ) );
                    else
                        blocksToAddExplosion.add( new BlockInfo( dimResLoc, NBTUtil.readBlockState( entry.getCompound( "state" ) ), NBTUtil.readBlockPos( entry.getCompound( "pos" ) ), entry.getInt( "ticksLeft" ), entry.getInt( "type" ), entry.getCompound( "tileEntity" ) ) );
                });

                blocksToHealExplosion.removeAll( blocksToAddExplosion );
                blocksToHealExplosion.addAll( blocksToAddExplosion );
                blocksToHealExplosion.sort( Comparator.comparingInt( blockInfo -> blockInfo.pos.getY() ) );

                blocksToHealFire.removeAll( blocksToAddFire );
                blocksToHealFire.addAll( blocksToAddFire );
                blocksToHealFire.sort( Comparator.comparingInt( blockInfo -> blockInfo.pos.getY() ) );
            }
        }
    }

    public static void handleChunkDataSave( ChunkDataEvent.Save event )
    {
        ResourceLocation dimResLoc = event.getWorld().getDimension().getType().getRegistryName();

        if( toHealDimMap.containsKey( dimResLoc ) )
        {
            Map<Integer, List<BlockInfo>> toHealTypeMap = toHealDimMap.get( dimResLoc );

            CompoundNBT levelNBT = (CompoundNBT) event.getData().get( "Level" );
            if( levelNBT == null )
                return;

            List<BlockInfo> chunkBlocksToHeal = new ArrayList<>();
            ChunkPos chunkPos = event.getChunk().getPos();

            for( Map.Entry<Integer, List<BlockInfo>> entry : toHealTypeMap.entrySet() )
            {
                List<BlockInfo> blocksToHeal = entry.getValue();

                for( BlockInfo blockInfo : blocksToHeal )
                {
                    if( new ChunkPos( blockInfo.pos ).equals( chunkPos ) )
                        chunkBlocksToHeal.add( blockInfo );
                }
            }

            if( chunkBlocksToHeal.size() <= 0 )
                return;

            CompoundNBT newBlocksToHealNBT = new CompoundNBT();
            CompoundNBT insidesNBT;

            int i = 0;

            for( BlockInfo blockInfo : chunkBlocksToHeal )
            {
                insidesNBT = new CompoundNBT();
                insidesNBT.put( "pos", NBTUtil.writeBlockPos( blockInfo.pos ) );
                insidesNBT.put( "state", NBTUtil.writeBlockState( blockInfo.state ) );
                insidesNBT.putInt( "ticksLeft", blockInfo.ticksLeft );
                insidesNBT.putInt( "type", blockInfo.type );
                if( blockInfo.tileEntityNBT != null )
                    insidesNBT.put( "tileEntity", blockInfo.tileEntityNBT );
                newBlocksToHealNBT.put( i++ + "", insidesNBT );
                if( !event.getWorld().chunkExists( chunkPos.x, chunkPos.z ) )
                    toHealTypeMap.get( blockInfo.type ).remove( blockInfo );
            }

            levelNBT.put( "blocksToHeal", newBlocksToHealNBT );
        }
    }
}
