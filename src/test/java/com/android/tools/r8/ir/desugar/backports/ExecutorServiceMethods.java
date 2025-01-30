package com.android.tools.r8.ir.desugar.backports;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceMethods {

  public static void closeExecutorService(ExecutorService executorService) {
    boolean terminated = executorService.isTerminated();
    if (!terminated) {
      executorService.shutdown();
      boolean interrupted = false;
      while (!terminated) {
        try {
          terminated = executorService.awaitTermination(1L, TimeUnit.DAYS);
        } catch (InterruptedException e) {
          if (!interrupted) {
            executorService.shutdownNow();
            interrupted = true;
          }
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
