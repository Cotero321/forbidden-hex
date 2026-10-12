package com.example.akaishi.forbidden.menu;

import com.example.akaishi.forbidden.api.magic.*;
import com.example.akaishi.forbidden.magic.table.MagicWord;
import com.example.akaishi.forbidden.magic.table.MagicInkTable;
import java.util.*;

public final class InscriptionSelectionSelfCheck {
    private InscriptionSelectionSelfCheck() {}
    public static void main(String[] args) {
        var words = new ArrayList<>(Collections.nCopies(8, ""));
        var inks = new ArrayList<>(Collections.nCopies(8, ""));
        var unlocked = MagicWord.byIdView().keySet();
        var allInks = MagicInkTable.all().keySet();
        require(InscriptionSelection.reason(words, inks, 0, "cha", "chi", "life", 8, unlocked, allInks) == null, "parameter draft");
        words.set(1, "tou"); inks.set(1, "chi");
        require(InscriptionSelection.reason(words, inks, 3, "tou", "ju", "life", 8, unlocked, allInks) != null, "duplicate");
        require(InscriptionSelection.reason(words, inks, 3, "zhao_cover", "ju", "life", 8, unlocked, allInks) != null, "delivery conflict");
        require(InscriptionSelection.reason(words, inks, 1, "tou", "xing", "san", 8, unlocked, allInks) == null, "replace excludes old ink");
        require(InscriptionSelection.reason(words, inks, 3, "jian", "xing", "san", 8, unlocked, allInks) != null, "frozen ink star conflict");
        require(InscriptionSelection.reason(words, inks, 1, "tou", "xing", "life", 8, unlocked, allInks) != null, "star source");
        require(InscriptionSelection.reason(words, inks, 3, "jian", "ju", "life", 2, unlocked, allInks) != null, "sealed slot");
        words.set(3, "jian"); inks.set(3, "ju");
        var plan = SpellPlans.compile(new SpellInput(words, inks, "chi", "life", 0, 0)).plan();
        require(plan != null && plan.cost().words().get(1).slot() == 3
                && plan.cost().words().get(1).inkId().equals("ju"), "sparse slot ink alignment");
        require(Math.abs(plan.cost().segmentCost() * plan.schedule().segments() - plan.cost().total()) < 0.001f, "display production total");
        System.out.println("InscriptionSelectionSelfCheck: PASS");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }
}
