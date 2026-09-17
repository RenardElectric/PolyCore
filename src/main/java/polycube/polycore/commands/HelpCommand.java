package polycube.polycore.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;
import polycube.polycore.text.TextComponents;

public class HelpCommand extends PolyCommand {
    public HelpCommand(String modId) {
        super(
                modId,
                "help",
                "Displays a list of available commands and their descriptions",
                "",
                PermissionLevel.ALL
        );
    }

    @Override
    protected int execute(CommandSourceStack source) {
        var helpMessage = TextComponents.header("Commands").append("\nClick a command to prepare it; use [Usage] for its syntax.");
        for (PolyCommand command : PolyCommands.getCommands()) {
            if (hasPermission(source, command.getPermissionLevel())) {
                String root = "/" + modId + " " + command.getName();
                helpMessage.append("\n\n  ").append(TextComponents.action(root, root + " "));
                helpMessage.append(" ").append(TextComponents.action("[Usage]", root + " help"));
                if (command.getPermissionLevel() != PermissionLevel.ALL) helpMessage.append(TextComponents.muted(" (Admin only)"));
                helpMessage.append("\n  " + command.getDescription());
            }
        }
        source.sendSuccess(() -> helpMessage, false);
        return 1;
    }
}
