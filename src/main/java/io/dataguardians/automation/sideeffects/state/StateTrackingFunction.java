package io.dataguardians.automation.sideeffects.state;

import io.dataguardians.automation.sideeffects.SideEffect;
import io.dataguardians.automation.watchdog.WatchDog;
import java.util.Set;

/**
 * Interface for tracking the state of a function.
 */
public interface StateTrackingFunction {

  /**
   * Adds a side effect to the current state.
   *
   * @param last The side effect to add.
   */
  void addSideEffect(SideEffect last);

  /**
   * Retrieves the current set of side effects.
   *
   * @return The current set of tracked side effects.
   */
  Set<SideEffect> getCurrentSideEffects();

  /**
   * Retrieves the WatchDog associated with this function.
   *
   * @return The WatchDog instance.
   */
  WatchDog getWatchDog();
}
