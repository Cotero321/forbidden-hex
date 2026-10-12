package com.example.akaishi.forbidden.api.magic;

import java.util.List;

/** 原始槽位保持对齐；空墨使用主墨。输入不包含主词。 */
public record SpellInput(List<String> words, List<String> wordInks, String mainInkId,
                         String sourceId, float channelSeconds, int volleyShots) {
    public SpellInput {
        words = List.copyOf(words);
        wordInks = List.copyOf(wordInks);
    }
}
