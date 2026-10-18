package com.example.akaishi.forbidden.forge.client.visual;

import com.example.akaishi.forbidden.forge.client.ForbiddenVisualConfig;
import com.example.akaishi.forbidden.visual.ShaderCompatibility;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Optional Oculus public API bridge; read on the client thread at rendering boundaries. */
public final class ClientShaderCompatibility {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static Object api;
    private static Method shaderPackInUse;
    private static boolean initialized;
    private static boolean providerPresent;
    private static boolean failed;
    private static volatile boolean reloadPending;
    private static ShaderCompatibility.State lastState;

    private ClientShaderCompatibility() { }

    public static boolean allowsCustomPrograms() {
        return ShaderCompatibility.allowsCustomPrograms(state(), ForbiddenVisualConfig.FORCE_VANILLA_RENDERING.get());
    }

    public static ShaderCompatibility.State state() {
        if (reloadPending) {
            initialized = false;
            reloadPending = false;
        }
        if (!initialized) initialize();
        ShaderCompatibility.State result;
        if (!providerPresent) {
            result = ShaderCompatibility.State.ABSENT;
        } else if (failed) {
            result = ShaderCompatibility.State.UNKNOWN;
        } else {
            try {
                result = Boolean.TRUE.equals(shaderPackInUse.invoke(api))
                        ? ShaderCompatibility.State.ACTIVE : ShaderCompatibility.State.INACTIVE;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                fail(exception);
                result = ShaderCompatibility.State.UNKNOWN;
            }
        }
        if (result != lastState) {
            lastState = result;
            LogUtils.getLogger().info("Shader-pack compatibility state: {}; custom programs {}", result,
                    ShaderCompatibility.allowsCustomPrograms(result, false) ? "eligible" : "blocked");
        }
        return result;
    }

    public static void onResourceReload() {
        reloadPending = true;
    }

    private static void initialize() {
        initialized = true;
        failed = false;
        api = null;
        shaderPackInUse = null;
        providerPresent = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
        if (!providerPresent) return;
        try {
            Class<?> apiClass = Class.forName(IRIS_API, false, ClientShaderCompatibility.class.getClassLoader());
            Method getInstance = apiClass.getMethod("getInstance");
            shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            if (!Modifier.isStatic(getInstance.getModifiers()) || shaderPackInUse.getReturnType() != boolean.class) {
                throw new IllegalStateException("Unsupported Iris public API signature");
            }
            api = getInstance.invoke(null);
            if (!apiClass.isInstance(api)) {
                throw new IllegalStateException("Iris public API returned no compatible instance");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
        }
    }

    private static void fail(Throwable exception) {
        if (!failed) {
            LogUtils.getLogger().warn("Oculus/Iris state unavailable; using vanilla visuals and disabling local post effects until resource reload", exception);
        }
        failed = true;
    }
}
