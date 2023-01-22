package net.arthurbr.autoshutdown;

import java.util.Date;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Init implements ModInitializer {
  public static final Logger LOGGER = LoggerFactory.getLogger("autoshutdown");
  private boolean hasStarted = false;
  private int playerCount = 0;
  private long timeToClose = 0;
  private long lastTime = 0;
  private boolean isStopping = false;
  private MinecraftServer mcserver;
  private int remain = 0;
  private final Config CONFIG = Config.of( "autoshutdown" ).provider( this::provider ).request();
  private final long DEFAULT_TIME = getTimeToClose();

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      LOGGER.info("The server just started, Auto Shutdown is now active and counting!");
      mcserver = server;
      timeToClose = DEFAULT_TIME;
      lastTime = new Date().getTime();
      hasStarted = true;
    });

    ServerTickEvents.END_SERVER_TICK.register(tick -> {
      if (playerCount != 0 || !hasStarted || isStopping || mcserver.isStopping() || mcserver.isStopped())
        return;
      if (timeToClose <= 0) {
        LOGGER.warn("Closing server!");
        isStopping = true;
        mcserver.stop(false);
      }
      else {
        long time = new Date().getTime();
        long delta = time - lastTime;
        lastTime = time;
        timeToClose -= delta;
        int newRemain = (int) (timeToClose / 1000);
        String suffix = "s";
        boolean shouldPrint = false;
        if (newRemain != remain && newRemain <= 30)
          shouldPrint = true;
        if (newRemain >= 60) {
          newRemain /= 60;
          suffix = "m";
          if (newRemain != remain && newRemain % 5 == 0)
            shouldPrint = true;
          if (newRemain != remain && newRemain == 1)
            shouldPrint = true;
        }
        remain = newRemain;
        if (shouldPrint)
          LOGGER.warn("Shutting down in: " + remain + suffix);
      }
    });

    ServerPlayConnectionEvents.DISCONNECT.register(
      (handler, server) -> {
        LOGGER.info("Player disconnected from server");
        playerCount--;
        if (playerCount == 0) {
          LOGGER.info("No players are connected, timer is now active");
          timeToClose = DEFAULT_TIME;
          lastTime = new Date().getTime();
        }
      });

    ServerPlayConnectionEvents.JOIN.register(
      (handler, sender, server) -> {
        LOGGER.info("Player joined server, stopping timer");
        playerCount++;
      });

    final int time = CONFIG.getOrDefault("shutdown.time", 10);
    LOGGER.info("Auto Shutdown is present and active!");
    LOGGER.warn(
      "Automatically shutting down if server is inactive for longer than "
      + time
      + " minute"
      + (time == 1 ? "" : "s")
    );
  }

  private String provider(String filename) {
    return "# Shutdown time in minutes:\nshutdown.time=10";
  }

  private long getTimeToClose() {
    final int time = CONFIG.getOrDefault("shutdown.time", 10);
    return (long) time * 60000 + 5000;
  }
}