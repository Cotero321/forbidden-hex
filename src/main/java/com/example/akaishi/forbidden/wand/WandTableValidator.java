package com.example.akaishi.forbidden.wand;

import com.example.akaishi.forbidden.ForbiddenIds;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import com.example.akaishi.forbidden.magic.table.MagicSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 法杖配件表与源专精威力范围的静态自检。 */
public final class WandTableValidator {
    private static final Set<String> POWER_SCOPES = Set.of("mind", "heal", "debuff", "defense");
    private static final float EFFICACY_MIN = 0.90f;
    private static final float EFFICACY_MAX = 1.25f;

    private WandTableValidator() {
    }

    public static List<String> inspect() {
        List<String> failures = new ArrayList<>();
        validate("tip", WandTipTable.all().values(), failures);
        validate("handle", WandHandleTable.all().values(), failures);
        validate("core", WandCoreTable.all().values(), failures);
        for (String sourceId : List.of("san", "life", "dark", "white")) {
            long specializedCores = WandCoreTable.all().values().stream()
                    .filter(part -> sourceId.equals(part.sourceId()) && POWER_SCOPES.contains(part.powerScope()))
                    .count();
            if (specializedCores != 1) failures.add("expected one power-specialized core for " + sourceId);
        }
        for (String inkId : MagicInkTable.order()) {
            String currentId = "forbidden_hex_wand_tip_ink_" + ForbiddenIds.inkName(inkId);
            WandPartDef part = WandTipTable.get(currentId);
            if (part == null || !inkId.equals(part.inkId())) {
                failures.add("ink tip registration mismatch " + inkId + ":" + currentId);
            }
            if (!currentId.equals(ForbiddenIds.itemPath("akaishi_wand_tip_ink_" + inkId))) {
                failures.add("legacy ink tip migration mismatch " + inkId);
            }
        }
        return failures;
    }

    private static void validate(String table, Iterable<WandPartDef> parts, List<String> failures) {
        Set<String> ids = new HashSet<>();
        for (WandPartDef part : parts) {
            if (!ids.add(part.id())) failures.add(table + " duplicate id " + part.id());
            if (!part.sourceId().isEmpty() && java.util.Arrays.stream(MagicSource.values())
                    .noneMatch(source -> source.id().equals(part.sourceId()))) {
                failures.add(table + " unknown source " + part.id() + ":" + part.sourceId());
            }
            if (!part.powerScope().isEmpty() && !POWER_SCOPES.contains(part.powerScope())) {
                failures.add(table + " unknown power scope " + part.id() + ":" + part.powerScope());
            }
            float power = part.mod(WandMod.POWER);
            if (!Float.isFinite(power) || power <= 0f) failures.add(table + " invalid power " + part.id());
            float efficacy = part.mod(WandMod.EFFICACY);
            if (!Float.isFinite(efficacy) || efficacy < EFFICACY_MIN || efficacy > EFFICACY_MAX) {
                failures.add(table + " efficacy outside safe range " + part.id() + ":" + efficacy);
            }
            if (part.kind() == WandPartDef.Kind.HANDLE && power != WandMod.POWER.defaultValue()) {
                failures.add("handle must not provide direct power " + part.id());
            }
            if (!part.powerScope().isEmpty() && part.sourceId().isEmpty()) {
                failures.add(table + " scoped power lacks source " + part.id());
            }
        }
    }
}
