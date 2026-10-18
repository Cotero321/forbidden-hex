package com.example.akaishi.forbidden.visual;

public enum ItemTooltipMode {
    SUMMARY,
    USAGE,
    TECHNICAL;

    public static ItemTooltipMode select(boolean shift, boolean control) {
        return control ? TECHNICAL : shift ? USAGE : SUMMARY;
    }
}
