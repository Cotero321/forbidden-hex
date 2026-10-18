package com.example.akaishi.forbidden.visual;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

/** Selection and ownership decisions without accessing a renderer or graphics context. */
public final class PostEffectPolicy {
    public record Request(ResourceLocation id, int priority, float strength) {
        public Request {
            Objects.requireNonNull(id, "id");
        }
    }

    public enum Action {
        KEEP,
        LOAD,
        RELEASE,
        YIELD
    }

    private PostEffectPolicy() { }

    public static ResourceLocation select(Iterable<Request> requests, Set<ResourceLocation> failed) {
        Request best = null;
        for (Request request : requests) {
            if (!Float.isFinite(request.strength()) || request.strength() <= 0f || failed.contains(request.id())) {
                continue;
            }
            if (best == null || request.priority() > best.priority()
                    || request.priority() == best.priority() && request.id().toString().compareTo(best.id().toString()) < 0) {
                best = request;
            }
        }
        return best == null ? null : best.id();
    }

    public static Action decide(boolean externalChain, ResourceLocation owned, ResourceLocation requested) {
        if (externalChain) {
            return Action.YIELD;
        }
        if (Objects.equals(owned, requested)) {
            return Action.KEEP;
        }
        if (requested != null) {
            return Action.LOAD;
        }
        return owned == null ? Action.KEEP : Action.RELEASE;
    }
}
