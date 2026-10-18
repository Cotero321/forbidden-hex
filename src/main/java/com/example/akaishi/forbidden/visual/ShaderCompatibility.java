package com.example.akaishi.forbidden.visual;

import java.util.Objects;

/** Shader-pack policy without loading client or optional renderer classes. */
public final class ShaderCompatibility {
    public enum State {
        ABSENT,
        INACTIVE,
        ACTIVE,
        UNKNOWN
    }

    private ShaderCompatibility() { }

    public static boolean allowsCustomPrograms(State state, boolean forceVanilla) {
        Objects.requireNonNull(state, "state");
        return !forceVanilla && (state == State.ABSENT || state == State.INACTIVE);
    }
}
