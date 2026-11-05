package com.example.akaishi.forbidden.familiar;

import com.example.akaishi.forbidden.familiar.core.FamiliarData;
import com.example.akaishi.forbidden.familiar.item.FamiliarMaterialItems;
import com.example.akaishi.forbidden.familiar.entity.FamiliarEntity;
import com.example.akaishi.forbidden.familiar.item.FamiliarContractItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ArrayList;

/** Server-side lifecycle bridge between a released familiar and its bound contract. */
public final class FamiliarRuntimeService {
    private FamiliarRuntimeService() {
    }

    /**
     * The death path does not invent a new cost: it re-evaluates the material ledger already
     * persisted on the contract. This keeps revival deterministic and lets a later menu consume
     * exactly the same material burden that produced the familiar.
     */
    public record DeathReassessment(List<String> materials, int biomassCost, boolean eligible) {
        public DeathReassessment {
            materials = materials == null ? List.of() : List.copyOf(materials);
            biomassCost = Math.max(0, biomassCost);
        }
    }

    public static DeathReassessment reassessDeath(FamiliarData data) {
        if (data == null || !data.template().isConfigured()) return new DeathReassessment(List.of(), 0, false);
        List<String> recognizedMaterials = new ArrayList<>();
        int cost = 0;
        for (String materialId : data.materials()) {
            FamiliarMaterialItems.Definition definition = FamiliarMaterialItems.definition(materialId);
            if (definition == null) continue;
            recognizedMaterials.add(definition.id());
            cost += definition.biomassCost();
        }
        boolean eligible = !recognizedMaterials.isEmpty() && cost > 0;
        return new DeathReassessment(recognizedMaterials, cost, eligible);
    }

    @Nullable
    public static ItemStack findContract(Player player) {
        if (player == null) return null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(FamiliarContent.FAMILIAR_CONTRACT.get()) && FamiliarContractItem.canUse(stack, player)) {
                return stack;
            }
        }
        return null;
    }

    /** Resolve only the contract whose persisted active-entity UUID matches this entity. */
    @Nullable
    public static ItemStack findContractForEntity(Player player, @Nullable java.util.UUID entityId) {
        if (player == null) return null;
        if (entityId != null) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(FamiliarContent.FAMILIAR_CONTRACT.get())) continue;
                FamiliarData data = FamiliarContractItem.readData(stack);
                if (data.isOwnedBy(player.getUUID()) && entityId.equals(data.activeEntity())) return stack;
            }
        }
        return null;
    }

    /** True when the player already owns a configured familiar, including a pending revival. */
    public static boolean hasConfiguredFamiliar(Player player) {
        if (player == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(FamiliarContent.FAMILIAR_CONTRACT.get())) {
                FamiliarData data = FamiliarContractItem.readData(stack);
                if (data.isOwnedBy(player.getUUID()) && data.template().isConfigured()) return true;
            }
        }
        return false;
    }

    public static boolean storeDeathPending(FamiliarEntity familiar) {
        if (!(familiar.getServer() != null && familiar.ownerId() != null)) return false;
        FamiliarData data = familiar.toFamiliarData();
        DeathReassessment assessment = reassessDeath(data);
        data.setMaterials(assessment.materials());
        data.setMaterialBiomassCost(assessment.biomassCost());
        data.setActive(false);
        data.setActiveEntity(null);
        data.setDeathPending(true);
        data.setHealth(0.0F);
        FamiliarDeathStore.get(familiar.getServer()).remember(familiar.getUUID(), data);
        ServerPlayer owner = familiar.getServer().getPlayerList().getPlayer(familiar.ownerId());
        ItemStack contract = findContractForEntity(owner, familiar.getUUID());
        if (contract != null) reconcileContract(contract, familiar.level());
        return true;
    }

    /** Reconcile before use and whenever a stored contract returns to a player's inventory. */
    public static boolean reconcileContract(ItemStack contract, Level level) {
        if (level.isClientSide || level.getServer() == null || contract == null
                || !contract.is(FamiliarContent.FAMILIAR_CONTRACT.get())) return false;
        FamiliarData recovered = FamiliarDeathStore.get(level.getServer())
                .recovery(FamiliarContractItem.readData(contract));
        if (recovered == null) return false;
        FamiliarContractItem.writeData(contract, recovered);
        return true;
    }
}
