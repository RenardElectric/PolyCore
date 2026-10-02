package polycube.polycore.commands;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import polycube.polycore.text.TextComponents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public final class PolyCommands {
    private static final Map<String, Registration> REGISTRATIONS = new LinkedHashMap<>();

    private PolyCommands() {}

    public static void registerCommands(String modId, String modName, Logger logger, PolyCommand... commands) {
        TextComponents.modName = modName;
        var registered = new ArrayList<>(List.of(commands));
        registered.add(new HelpCommand(modId));
        if (REGISTRATIONS.putIfAbsent(modId, new Registration(modName, List.copyOf(registered))) != null)
            throw new IllegalStateException("Commands already registered for " + modId);

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, _) -> {
            var baseCommand = Commands.literal(modId);
            baseCommand.executes(context -> printModInfo(modId, logger, context.getSource()));
            for (PolyCommand command : registered) {
                for (var commandAlias : command.getCommands(buildContext)) {
                    baseCommand.then(commandAlias);
                    if (command.hasQuickAlias()) dispatcher.register(commandAlias);
                }
            }
            dispatcher.register(baseCommand);
            logger.debug("Registered {} {} subcommand(s)", registered.size(), modId);
        });
    }

    public static int printModInfo(String modId, Logger logger, CommandSourceStack source) {
        var optionalModData = FabricLoader.getInstance().getModContainer(modId).map(ModContainer::getMetadata);
        if (optionalModData.isEmpty()) {
            logger.warn("Could not find {} metadata while handling the base command", modId);
            source.sendFailure(TextComponents.error("Could not fetch mod information."));
            return 0;
        }
        var modData = optionalModData.get();
        var authors = modData.getAuthors().stream().map(Person::getName)
                .reduce((a, b) -> a + " and " + b).orElse("Unknown authors");
        var modInfo = TextComponents.header(modId)
                .append(TextComponents.muted(" v" + modData.getVersion().getFriendlyString()))
                .append(TextComponents.field("Made by", TextComponents.value(authors)))
                .append("\n" + modData.getDescription())
                .append("\n").append(TextComponents.action("[View commands]", "/" + modId + " help"));
        source.sendSuccess(() -> modInfo, false);
        return 1;
    }

    public static List<PolyCommand> getCommands(String modId) {
        return registration(modId).commands();
    }

    static @Nullable CommandNode<CommandSourceStack> findNode(CommandSourceStack source, String path) {
        CommandNode<CommandSourceStack> node = source.getServer().getCommands().getDispatcher().getRoot();
        for (String part : path.split(" ")) {
            node = node.getChild(part);
            if (node == null || !node.canUse(source)) return null;
        }
        return node;
    }

    private static Registration registration(String modId) {
        return Objects.requireNonNull(REGISTRATIONS.get(modId), "Commands are unavailable for " + modId);
    }

    private record Registration(String modName, List<PolyCommand> commands) {}
}
