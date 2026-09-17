package polycube.polycore.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;
import polycube.polycore.text.TextComponents;

/// Converts data result errors to Brigadier errors only at the command boundary.
@SuppressWarnings("unused")
public final class CommandResult {
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
            message -> TextComponents.error(message.toString())
    );

    private CommandResult() {}

    public static <T> T require(DataResult<T> result) throws CommandSyntaxException {
        return result.getOrThrow(ERROR::create);
    }
}
