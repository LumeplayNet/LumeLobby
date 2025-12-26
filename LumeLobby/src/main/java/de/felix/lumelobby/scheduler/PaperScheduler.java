package de.felix.lumelobby.scheduler;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@RequiredArgsConstructor
public final class PaperScheduler {

    private final Plugin plugin;

    public Executor sync() {
        return command -> Bukkit.getScheduler().runTask(plugin, command);
    }

    public Executor async() {
        return command -> Bukkit.getScheduler().runTaskAsynchronously(plugin, command);
    }

    public <T> CompletableFuture<T> callSync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        var future = new CompletableFuture<T>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}

