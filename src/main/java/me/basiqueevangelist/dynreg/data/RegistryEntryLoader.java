package me.basiqueevangelist.dynreg.data;

import com.google.gson.JsonObject;
import me.basiqueevangelist.dynreg.DynReg;
import me.basiqueevangelist.dynreg.entry.RegistrationEntry;
import me.basiqueevangelist.dynreg.entry.EntryDescriptionReaders;
import me.basiqueevangelist.dynreg.event.StaticDataLoadCallback;
import me.basiqueevangelist.dynreg.round.DynamicRound;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RegistryEntryLoader implements SimpleResourceReloadListener<Map<Identifier, RegistrationEntry>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynReg/RegistryEntryLoader");
    static final HashSet<Identifier> ADDED_ENTRIES = new HashSet<>();
    static final HashSet<Identifier> STARTUP_ENTRIES = new HashSet<>();

    public static final RegistryEntryLoader INSTANCE = new RegistryEntryLoader();

    public static void init(){
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ADDED_ENTRIES.clear();
            ADDED_ENTRIES.addAll(STARTUP_ENTRIES);
        });

        StaticDataLoadCallback.EVENT.register((manager, round) -> {
            var entries = loadAll(manager);

            for (var entry : entries.values()) {
                round.addEntry(entry);
                RegistryEntryLoader.ADDED_ENTRIES.add(entry.id());
                RegistryEntryLoader.STARTUP_ENTRIES.add(entry.id());
            }
        });
    }

    public static Map<Identifier, RegistrationEntry> loadAll(ResourceManager manager) {
        Map<Identifier, RegistrationEntry> descriptions = new HashMap<>();

        var resources = manager.findResources("entries", id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier id = entry.getKey();
            var realId = new Identifier(id.getNamespace(), id.getPath().substring("entries".length() + 1, id.getPath().length() - 5));

            try (var br = new BufferedReader(new InputStreamReader(entry.getValue().getInputStream()))) {
                JsonObject obj = JsonHelper.deserialize(br, true);
                Identifier type = new Identifier(JsonHelper.getString(obj, "type"));
                RegistrationEntry desc = EntryDescriptionReaders.getReader(type).apply(realId, obj);

                descriptions.put(realId, desc);
            } catch (IOException e) {
                LOGGER.error("Encountered error while loading {}", id, e);
            }
        }

        LOGGER.info("Loaded {} registry entries", descriptions.size());

        return descriptions;
    }

    @Override
    public CompletableFuture<Map<Identifier, RegistrationEntry>> load(ResourceManager manager, Profiler profiler, Executor executor) {
        return CompletableFuture.supplyAsync(() -> loadAll(manager), executor);
    }

    @Override
    public CompletableFuture<Void> apply(Map<Identifier, RegistrationEntry> data, ResourceManager manager, Profiler profiler, Executor executor) {
        DynamicRound round = new DynamicRound(DynReg.SERVER);

        for (var key : ADDED_ENTRIES) {
            round.removeEntry(key);
        }
        ADDED_ENTRIES.clear();

        for (var entry : data.entrySet()) {
            round.addEntry(entry.getValue());
            ADDED_ENTRIES.add(entry.getKey());
        }

        if (round.needsRunning())
            round.run();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Identifier getFabricId() {
        return DynReg.id("registry_entry");
    }
}
