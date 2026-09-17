package polycube.polycore.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import polycube.polycore.text.TextComponents;

import java.util.Objects;

public final class PolyCommands {
    private static PolyCommand @Nullable [] commands;

    private PolyCommands() {}

    public static void registerCommands(String modId, String modName, Logger logger, PolyCommand... commands) {
        TextComponents.modName = modName;

        var newCommands = new PolyCommand[commands.length + 1];
        System.arraycopy(commands, 0, newCommands, 0, commands.length);
        newCommands[commands.length] = new HelpCommand(modId);
        PolyCommands.commands = newCommands;

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, _) -> {
            var baseCommand = Commands.literal(modId);
            baseCommand.executes(context -> printModInfo(modId, logger, context.getSource()));
            for (PolyCommand command : newCommands) {
                for (var commandAlias : command.getCommands(buildContext)) {
                    baseCommand.then(commandAlias);
                    if (command.hasQuickAlias()) dispatcher.register(commandAlias);
                }
            }
            dispatcher.register(baseCommand);
            logger.debug("Registered {} {} subcommand(s)", newCommands.length, modId);
        });
    }

    public static int printModInfo(String modId, Logger logger, CommandSourceStack cst) {
        var optionalModData = FabricLoader.getInstance()
                .getModContainer(modId)
                .map(ModContainer::getMetadata);

        if (optionalModData.isEmpty()) {
            logger.warn("Could not find {} metadata while handling the base command", modId);
            cst.sendFailure(TextComponents.error("Could not fetch mod information."));
            return 0;
        }
        var modData = optionalModData.get();
        var authors = modData.getAuthors().stream()
                .map(Person::getName)
                .reduce((a, b) -> a + " and " + b)
                .orElse("Unknown authors");
        var modInfo = TextComponents.header(modData.getName())
                .append(TextComponents.muted(" v" + modData.getVersion().getFriendlyString()))
                .append(TextComponents.field("Made by", TextComponents.value(authors)))
                .append("\n" + modData.getDescription())
                .append("\n").append(TextComponents.action("[View commands]", "/" + modId + " help"));
        cst.sendSuccess(() -> modInfo, false);
        return 1;
    }

    public static PolyCommand[] getCommands() {
        return Objects.requireNonNull(commands, "PolyCommands are unavailable before registration");
    }
}
