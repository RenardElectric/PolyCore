package polycube.polycore.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PolyCommands {
    private static @Nullable List<PolyCommand> commands;

    private PolyCommands() {}

    private static void registerCommands(String modId, Logger logger, List<PolyCommand> commands) {
        List<PolyCommand> registeredCommands = new ArrayList<>(commands);
        registeredCommands.add(new HelpCommand(modId));
        PolyCommands.commands = registeredCommands;

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, _) -> {
            var baseCommand = Commands.literal(modId);
            baseCommand.executes(context -> printModInfo(modId, logger, context.getSource()));
            for (PolyCommand command : registeredCommands) {
                for (var commandAlias : command.getCommands(buildContext)) {
                    baseCommand.then(commandAlias);
                    if (command.hasQuickAlias()) dispatcher.register(commandAlias);
                }
            }
            dispatcher.register(baseCommand);
            logger.debug("Registered {} {} subcommand(s)", registeredCommands.size(), modId);
        });
    }

    public static int printModInfo(String modId, Logger logger, CommandSourceStack cst) {
        var optionalModData = FabricLoader.getInstance()
                .getModContainer(modId)
                .map(ModContainer::getMetadata);

        if (optionalModData.isEmpty()) {
            logger.warn("Could not find {} metadata while handling the base command", modId);
            cst.sendFailure(CommandText.error("Could not fetch mod information."));
            return 0;
        }
        var modData = optionalModData.get();
        var authors = modData.getAuthors().stream()
                .map(Person::getName)
                .reduce((a, b) -> a + " and " + b)
                .orElse("Unknown authors");
        var modInfo = CommandText.header(modData.getName())
                .append(CommandText.muted(" v" + modData.getVersion().getFriendlyString()))
                .append(CommandText.field("Made by", CommandText.value(authors)))
                .append("\n" + modData.getDescription())
                .append("\n").append(CommandText.action("[View commands]", "/" + modId + " help"));
        cst.sendSuccess(() -> modInfo, false);
        return 1;
    }

    public static List<PolyCommand> getCommands() {
        return Objects.requireNonNull(commands, "PolyCommands are unavailable before registration");
    }
}
