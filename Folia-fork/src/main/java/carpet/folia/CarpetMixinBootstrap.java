package carpet.folia;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;

public final class CarpetMixinBootstrap {

    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        try {
            MixinBootstrap.init();
            MixinExtrasBootstrap.init();
            Mixins.addConfiguration("carpet.mixins.json");
        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize carpet mixins", t);
        }
    }

    private CarpetMixinBootstrap() {
    }
}