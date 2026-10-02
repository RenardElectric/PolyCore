package polycube.polycore.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import polycube.polycore.PolyCore;
import polycube.polycore.text.TextComponents;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "RedundantThrows"})
public abstract class PolyCommand {
    private final String name;
    private final String description;
    private final PermissionLevel permissionLevel;
    private final boolean hasQuickAlias;
    private final List<String> aliases;

    protected String modId = PolyCore.MOD_ID;
    protected TextComponents textComponents = TextComponents.of(modId);

    public PolyCommand(String name, String description, PermissionLevel permissionLevel) {
        this(name, description, permissionLevel, false);
    }

    public PolyCommand(String name, String description, PermissionLevel permissionLevel, boolean hasQuickAlias) {
        this(name, description, permissionLevel, hasQuickAlias, List.of());
    }

    public PolyCommand(String name, String description, PermissionLevel permissionLevel, boolean hasQuickAlias, List<String> aliases) {
        this.name = name;
        this.description = description;
        this.permissionLevel = permissionLevel;
        this.hasQuickAlias = hasQuickAlias;
        this.aliases = aliases;
    }

    protected PolyCommand setInfo(String modId, String modName) {
        this.modId = modId;
        this.textComponents = TextComponents.of(modName);
        return this;
    }

    protected String getName() {
        return name;
    }

    protected String getDescription() {
        return description.endsWith(".") ? description : description + ".";
    }

    protected Component getFullDescription(CommandSourceStack source) {
        String path = this.modId + " " + this.name;
        var message = textComponents.header("/" + path).append("\n" + this.getDescription());
        var node = PolyCommands.findNode(source, path);
        if (node != null) {
            for (var usage : source.getServer().getCommands().getDispatcher().getAllUsage(node, source, true)) {
                if (usage.equals("help") || usage.endsWith(" help")) continue;
                var command = "/" + path + (usage.isBlank() ? "" : " " + usage);
                message.append("\n  ").append(TextComponents.run(command, "/" + path + " "));
            }
        }
        if (this.hasQuickAlias) {
            var shortcuts = new ArrayList<String>();
            shortcuts.add("/" + this.name);
            for (String alias : this.aliases) shortcuts.add("/" + alias);
            message.append(TextComponents.field("Shortcuts", TextComponents.value(String.join(", ", shortcuts))));
        }
        return message;
    }
    protected PermissionLevel getPermissionLevel() {
        return this.permissionLevel;
    }

    protected boolean hasQuickAlias() {
        return this.hasQuickAlias;
    }

    protected List<String> getAliases() {
        return this.aliases;
    }

    public LiteralArgumentBuilder<CommandSourceStack> getCommand(String name) {
        return Commands.literal(name)
                .requires(source -> hasPermission(source, permissionLevel))
                .executes(e -> execute(e.getSource()))
                .then(Commands.literal("help").executes(e -> {
                    e.getSource().sendSuccess(() -> this.getFullDescription(e.getSource()), false);
                    return 1;
                }));

    }

    public LiteralArgumentBuilder<CommandSourceStack> getCommand(String name, CommandBuildContext buildContext) {
        return getCommand(name);
    }

    public List<LiteralArgumentBuilder<CommandSourceStack>> getCommands(CommandBuildContext buildContext) {
        var commands = new ArrayList<LiteralArgumentBuilder<CommandSourceStack>>();
        var aliases = new ArrayList<>(getAliases());
        aliases.add(name);
        for (String alias : aliases) {
            commands.add(getCommand(alias, buildContext));
        }
        return commands;
    }

    protected boolean hasPermission(CommandSourceStack source, PermissionLevel permissionLevel) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(permissionLevel));
    }

    protected int execute(CommandSourceStack source) throws CommandSyntaxException {
        source.sendFailure(textComponents.error("Incomplete command. Choose one of the forms below.").append("\n").append(this.getFullDescription(source)));
        return 0;
    }

    public final class CommandResult {

        private CommandResult() {}

        private final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
                message -> textComponents.error(message.toString())
        );

        public <T> T require(DataResult<T> result) throws CommandSyntaxException {
            return result.getOrThrow(ERROR::create);
        }
    }
}
