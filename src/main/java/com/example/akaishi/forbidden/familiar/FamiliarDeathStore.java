package com.example.akaishi.forbidden.familiar;

import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Durable death snapshots, independent of the owner's login state and the contract's location. */
public final class FamiliarDeathStore extends SavedData {
    private static final String KEY = "forbidden_hex_familiar_deaths";
    private final Map<UUID, Death> deaths = new HashMap<>();

    private record Death(UUID entityId, FamiliarData data) { }

    public static FamiliarDeathStore get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FamiliarDeathStore::load, FamiliarDeathStore::new, KEY);
    }

    public void remember(UUID entityId, FamiliarData data) {
        if (entityId == null || data == null || data.owner() == null
                || !data.template().isConfigured() || !data.deathPending()) return;
        FamiliarData snapshot = data.copy();
        snapshot.setActive(false);
        snapshot.setActiveEntity(null);
        snapshot.setHealth(0.0F);
        Death previous = deaths.get(snapshot.familiarId());
        if (previous != null && previous.entityId().equals(entityId)
                && previous.data().save().equals(snapshot.save())) return;
        deaths.put(snapshot.familiarId(), new Death(entityId, snapshot));
        setDirty();
    }

    /** An old death must never overwrite a different owner, familiar or later summoned entity. */
    @Nullable
    public FamiliarData recovery(FamiliarData contract) {
        if (contract == null || !contract.active() || contract.activeEntity() == null) return null;
        Death death = deaths.get(contract.familiarId());
        if (death == null || !death.entityId().equals(contract.activeEntity())
                || !death.data().isOwnedBy(contract.owner())) return null;
        // Retain the latest tombstone until the next death of this familiar. Removing it
        // here could lose recovery if world data saves before the updated item does.
        return death.data().copy();
    }

    public static FamiliarDeathStore load(CompoundTag tag) {
        FamiliarDeathStore result = new FamiliarDeathStore();
        ListTag entries = tag.getList("Deaths", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Entity") || !entry.contains("Data", Tag.TAG_COMPOUND)) continue;
            CompoundTag dataTag = entry.getCompound("Data");
            if (!dataTag.hasUUID("FamiliarId") || !dataTag.hasUUID("Owner")) continue;
            FamiliarData data = FamiliarData.load(dataTag);
            if (!data.template().isConfigured() || !data.deathPending()) continue;
            data.setActive(false);
            data.setActiveEntity(null);
            data.setHealth(0.0F);
            result.deaths.put(data.familiarId(), new Death(entry.getUUID("Entity"), data));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Death death : deaths.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Entity", death.entityId());
            entry.put("Data", death.data().save());
            entries.add(entry);
        }
        tag.put("Deaths", entries);
        return tag;
    }
}
