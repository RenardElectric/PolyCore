package polycube.polycore.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import polycube.polycore.text.TextComponents;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "RedundantThrows"})
public abstract class PolyCommand {
    final String modId;
    private final String name;
    private final String description;
    private final String usage;
    private final PermissionLevel permissionLevel;
    private final boolean hasQuickAlias;
    private final List<String> aliases;

    public PolyCommand(String modId, String name, String description, String usage, PermissionLevel permissionLevel) {
        this(modId, name, description, usage, permissionLevel, false);
    }

    public PolyCommand(String modId, String name, String description, String usage, PermissionLevel permissionLevel, boolean hasQuickAlias) {
        this(modId, name, description, usage, permissionLevel, hasQuickAlias, List.of());
    }

    public PolyCommand(String modId, String name, String description, String usage, PermissionLevel permissionLevel, boolean hasQuickAlias, List<String> aliases) {
        this.modId = modId;
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.permissionLevel = permissionLevel;
        this.hasQuickAlias = hasQuickAlias;
        this.aliases = aliases;
    }

    protected String getName() {
        return name;
    }

    protected String getDescription() {
        return description.endsWith(".") ? description : description + ".";
    }

    protected Component getFullDescription() {
        var message = TextComponents.header("/" + modId + " " + name).append("\n" + getDescription());
        if (permissionLevel != PermissionLevel.ALL) message.append(TextComponents.muted(" (Admin only)"));
        for (String variant : usage.split(" \\| ")) {
            String command = "/" + modId + " " + name;
            message.append("\n  ").append(TextComponents.action(
                    command + (variant.isBlank() ? "" : " " + variant),
                    command + (variant.isBlank() ? "" : " ")));
        }
        if (hasQuickAlias) {
            var shortcuts = new ArrayList<String>();
            shortcuts.add("/" + name);
            for (String alias : aliases) shortcuts.add("/" + alias);
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
                    e.getSource().sendSuccess(this::getFullDescription, false);
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
        source.sendFailure(TextComponents.error("Incomplete command. Choose one of the forms below.").append("\n").append(getFullDescription()));
        return 0;
    }
}
