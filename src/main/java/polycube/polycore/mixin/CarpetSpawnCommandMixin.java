package polycube.polycore.mixin;

import carpet.commands.SpawnCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = SpawnCommand.class, remap = false)
public abstract class CarpetSpawnCommandMixin {

    @ModifyConstant(
            method = "register",
            constant = @Constant(stringValue = "spawn"),
            remap = false
    )
    private static String polycube$renameCarpetSpawn(String original) {
        return "carpetspawn";
    }
}