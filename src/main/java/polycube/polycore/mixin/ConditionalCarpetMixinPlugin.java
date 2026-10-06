package polycube.polycore.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

public class ConditionalCarpetMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("CarpetSpawnCommandMixin")) {
            return FabricLoader.getInstance().isModLoaded("carpet");
        }
        return true;
    }
}
