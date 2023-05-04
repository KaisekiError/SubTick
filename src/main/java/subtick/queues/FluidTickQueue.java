package subtick.queues;

import java.util.ArrayList;
import java.util.Iterator;

import me.jellysquid.mods.lithium.common.world.scheduler.LithiumServerTickScheduler;
import me.jellysquid.mods.lithium.common.world.scheduler.TickEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerTickList;
import net.minecraft.world.level.TickNextTickData;
import net.minecraft.world.level.material.Fluid;
import oshi.util.tuples.Pair;
import subtick.SubTick;
import subtick.TickPhase;
import subtick.mixins.lithium.LithiumServerTickSchedulerAccessor;

public class FluidTickQueue extends TickingQueue
{
  private int lithium_scheduled_tick_step_index = 0;

  public FluidTickQueue(ServerLevel level)
  {
    super(level, TickPhase.FLUID_TICK, "fluidTick", "Fluid Tick", "Fluid Ticks");
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
    ServerTickList<Fluid> tickList = level.liquidTicks;
    Iterator<TickNextTickData<Fluid>> iterator = tickList.tickNextTickList.iterator();
    for(int i = 0; i < 65536 && iterator.hasNext();)
    {
      TickNextTickData<Fluid> tick = iterator.next();
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
    ((LithiumServerTickScheduler<Fluid>)level.liquidTicks).selectTicks(level.getGameTime());
    lithium_scheduled_tick_step_index = 0;
  }

  public Pair<Integer, Boolean> stepVanilla(int count, BlockPos pos, int range)
  {
    int executed_steps = 0;
    ServerTickList<Fluid> tickList = level.liquidTicks;
    while(executed_steps < count)
    {
      TickNextTickData<Fluid> tick = tickList.currentlyTicking.poll();
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
        executed_steps ++;
      }
    }
    exhausted = false;
    return new Pair<Integer, Boolean>(executed_steps, false);
  }

  // Accessor cast warnings
  @SuppressWarnings("unchecked")
  private Pair<Integer, Boolean> stepLithium(int count, BlockPos pos, int range)
  {
    LithiumServerTickSchedulerAccessor<Fluid> scheduler = (LithiumServerTickSchedulerAccessor<Fluid>)(LithiumServerTickScheduler<Fluid>)level.liquidTicks;
    int executed_steps = 0;
    ArrayList<TickEntry<Fluid>> ticks = scheduler.getExecutingTicks();
    int ticksSize = ticks.size();
    for(; lithium_scheduled_tick_step_index < ticksSize && executed_steps < count; lithium_scheduled_tick_step_index++)
    {
      TickEntry<Fluid> tick = ticks.get(lithium_scheduled_tick_step_index);
      if(tick == null)
        continue;
      tick.consumed = true;
      scheduler.getTickConsumer().accept(tick);
      if(rangeCheck(tick.pos, pos, range))
      {
        addBlockOutline(tick.pos);
        executed_steps ++;
      }
    }
    return new Pair<Integer, Boolean>(executed_steps, exhausted = lithium_scheduled_tick_step_index == ticksSize);
  }

  private void endVanilla()
  {
    level.liquidTicks.alreadyTicked.clear();
    level.liquidTicks.currentlyTicking.clear();
  }

  // Accessor cast warnings
  @SuppressWarnings("unchecked")
  private void endLithium()
  {
    ((LithiumServerTickSchedulerAccessor<Fluid>)level.liquidTicks).getExecutingTicks().clear();
    ((LithiumServerTickSchedulerAccessor<Fluid>)level.liquidTicks).getExecutingTicksSet().clear();
  }
}
