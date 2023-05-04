package subtick.queues;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import me.jellysquid.mods.lithium.common.world.scheduler.LithiumServerTickScheduler;
import me.jellysquid.mods.lithium.common.world.scheduler.TickEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerTickList;
import net.minecraft.world.level.TickNextTickData;
import net.minecraft.world.level.block.Block;
import oshi.util.tuples.Pair;
import subtick.SubTick;
import subtick.TickPhase;
import subtick.TickingMode;
import subtick.mixins.lithium.LithiumServerTickSchedulerAccessor;

public class BlockTickQueue extends TickingQueue
{
  private static TickingMode INDEX = new TickingMode("Block Tick", "Block Ticks");
  private static TickingMode PRIORITY = new TickingMode("Block Tick Priority", "Block Tick Priorities");

  private int lithium_scheduled_tick_step_index = 0;

  public BlockTickQueue(ServerLevel level)
  {
    super(Map.of("index", INDEX, "priority", PRIORITY), INDEX, level, TickPhase.BLOCK_TICK, "blockTick");
  }

  @Override
  public void start()
  {
    if(SubTick.hasLithium)
      startLithium();
    else
      startVanilla();
  }

  @Override
  public Pair<Integer, Boolean> step(int count, BlockPos pos, int range)
  {
    if(SubTick.hasLithium)
      return stepLithium(count, pos, range);
    return stepVanilla(count, pos, range);
  }

  @Override
  public void end()
  {
    if(SubTick.hasLithium)
      endLithium();
    else
      endVanilla();
  }

  private void startVanilla()
  {
    ServerTickList<Block> tickList = level.blockTicks;
    Iterator<TickNextTickData<Block>> iterator = tickList.tickNextTickList.iterator();
    for(int i = 0; i < 65536 && iterator.hasNext();)
    {
      TickNextTickData<Block> tick = iterator.next();
      if(tick.triggerTick > level.getGameTime())
        break;
      if(level.isPositionTickingWithEntitiesLoaded(tick.pos))
      {
        iterator.remove();
        tickList.tickNextTickSet.remove(tick);
        tickList.currentlyTicking.add(tick);
        i ++;
      }
    }
  }

  private void startLithium()
  {
    ((LithiumServerTickScheduler<Block>)level.blockTicks).selectTicks(level.getGameTime());
    lithium_scheduled_tick_step_index = 0;
  }

  public Pair<Integer, Boolean> stepVanilla(int count, BlockPos pos, int range)
  {
    int executed_steps = 0;
    ServerTickList<Block> tickList = level.blockTicks;
    while(executed_steps < count)
    {
      TickNextTickData<Block> tick = tickList.currentlyTicking.poll();
      if(tick == null)
      {
        exhausted = true;
        return new Pair<Integer, Boolean>(executed_steps, true);
      }

      if(level.isPositionTickingWithEntitiesLoaded(tick.pos))
      {
        tickList.alreadyTicked.add(tick);
        tickList.ticker.accept(tick);
      }
      else
        tickList.scheduleTick(tick.pos, tick.getType(), 0);

      if(rangeCheck(tick.pos, pos, range))
      {
        addBlockOutline(tick.pos);
        if(currentMode == INDEX)
          executed_steps ++;
      }

      if(currentMode == PRIORITY)
      {
        TickNextTickData<Block> nextTick = tickList.currentlyTicking.peek();
        if(nextTick != null && nextTick.priority != tick.priority)
          executed_steps ++;
      }
    }
    return new Pair<Integer, Boolean>(executed_steps, exhausted = tickList.currentlyTicking.isEmpty());
  }

  // Accessor cast warnings
  @SuppressWarnings("unchecked")
  private Pair<Integer, Boolean> stepLithium(int count, BlockPos pos, int range)
  {
    LithiumServerTickSchedulerAccessor<Block> scheduler = (LithiumServerTickSchedulerAccessor<Block>)(LithiumServerTickScheduler<Block>)level.blockTicks;
    int executed_steps = 0;
    ArrayList<TickEntry<Block>> ticks = scheduler.getExecutingTicks();
    int ticksSize = ticks.size();
    for(; lithium_scheduled_tick_step_index < ticksSize && executed_steps < count; lithium_scheduled_tick_step_index++)
    {
      TickEntry<Block> tick = ticks.get(lithium_scheduled_tick_step_index);
      if(tick == null)
        continue;
      tick.consumed = true;
      scheduler.getTickConsumer().accept(tick);
      if(rangeCheck(tick.pos, pos, range))
      {
        addBlockOutline(tick.pos);
        if(currentMode == INDEX)
          executed_steps ++;
      }

      if(currentMode == PRIORITY)
      {
        TickEntry<Block> nextTick = ticks.get(lithium_scheduled_tick_step_index + 1);
        if(nextTick != null && nextTick.priority != tick.priority)
          executed_steps ++;
      }
    }
    return new Pair<Integer, Boolean>(executed_steps, exhausted = lithium_scheduled_tick_step_index == ticksSize);
  }

  private void endVanilla()
  {
    level.blockTicks.alreadyTicked.clear();
    level.blockTicks.currentlyTicking.clear();
  }

  // Accessor cast warnings
  @SuppressWarnings("unchecked")
  private void endLithium()
  {
    ((LithiumServerTickSchedulerAccessor<Block>)level.blockTicks).getExecutingTicks().clear();
    ((LithiumServerTickSchedulerAccessor<Block>)level.blockTicks).getExecutingTicksSet().clear();
  }
}
