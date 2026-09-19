package polycube.polycore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

/// Creates persistent mannequin NPCs whose behavior is defined by a callback.
/// The NPCs are automatically restored when the server restarts.
@SuppressWarnings("unused")
public final class NpcCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcCreator.class);
    private static final Map<Identifier, NpcFactory> NPC_TYPES = new HashMap<>();
    private static final Map<MinecraftServer, RuntimeState> SERVER_STATES = new IdentityHashMap<>();
    private static final Map<Pose, Vec3> POSE_OFFSETS = Map.of(
            Pose.STANDING, new Vec3(0, 2, 0),
            Pose.CROUCHING, new Vec3(0, 1.75, 0),
            Pose.SWIMMING, new Vec3(0, 1, 0),
            Pose.FALL_FLYING, new Vec3(0, 1, 0),
            Pose.SLEEPING, new Vec3(0, 1, 0)
    );

    private static boolean callbacksRegistered;

    private NpcCreator() {}

    /// Registers the factory used to recreate an NPC callback from its persisted data.
    public static void registerNpcType(Identifier type, NpcFactory factory) {
        if (!callbacksRegistered) {
            callbacksRegistered = true;
            registerCallbacks();
        }

        var previous = NPC_TYPES.putIfAbsent(type, factory);
        if (previous != null && previous != factory) {
            throw new IllegalStateException("NPC type " + type + " is already registered");
        }

        if (previous == null) {
            SERVER_STATES.values().forEach(state -> state.restoreType(type, factory));
        }
    }

    /// Spawns an NPC whose callback can be reconstructed from {@code type} and empty callback data.
    public static DataResult<NpcCallback> summonNpc(
            ServerLevel level, Identifier type, ResolvableProfile skin,
            Vec3 pos, Vec2 rotation, Pose pose
    ) {
        return summonNpc(level, type, new CompoundTag(), skin, pos,rotation, pose);
    }

    /// Spawns an NPC whose callback can be reconstructed from {@code type} and {@code callbackData}.
    public static DataResult<NpcCallback> summonNpc(
            ServerLevel level, Identifier type, CompoundTag callbackData, ResolvableProfile skin,
            Vec3 pos, Vec2 rotation, Pose pose
    ) {
        var textOffset = POSE_OFFSETS.get(pose);
        if (textOffset == null) {
            return DataResult.error(() -> "Pose " + pose + " is not supported for NPCs");
        }

        NpcFactory factory = NPC_TYPES.get(type);
        if (factory == null) {
            return DataResult.error(() -> "Unknown NPC type " + type);
        }

        NpcCallback callback;
        try {
            callback = Objects.requireNonNull(factory.create(callbackData.copy()), "NPC factory returned null");
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Failed to create callback for NPC type " + type + ": " + exception.getMessage());
        }

        var entityData = new CompoundTag();
        entityData.putBoolean("immovable", true);
        entityData.putBoolean("hide_description", true);

        var entity = EntityType.loadEntityRecursive(
                EntityTypes.MANNEQUIN,
                entityData,
                level,
                EntitySpawnReason.COMMAND,
                loadedEntity -> loadedEntity
        );

        if (!(entity instanceof Mannequin mannequin)) {
            return DataResult.error(() -> "Failed to create mannequin entity");
        }

        mannequin.snapTo(pos.x, pos.y, pos.z, rotation.y, rotation.x);
        mannequin.setYHeadRot(rotation.y);
        mannequin.setYBodyRot(rotation.y);
        mannequin.setComponent(DataComponents.PROFILE, skin);
        mannequin.setPermanentlyInvulnerable(true);
        mannequin.setNoGravity(true);
        mannequin.setSilent(true);
        mannequin.setPose(pose);
        mannequin.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        mannequin.tick();

        if (!level.tryAddFreshEntityWithPassengers(mannequin)) {
            return DataResult.error(() -> "Failed to add mannequin to the world");
        }

        Display.TextDisplay textDisplay = EntityTypes.TEXT_DISPLAY.spawn(
                level,
                mannequin.blockPosition(),
                EntitySpawnReason.COMMAND
        );
        if (textDisplay == null) {
            mannequin.discard();
            return DataResult.error(() -> "Failed to create text display entity");
        }

        pos = pos.add(textOffset);
        textDisplay.snapTo(pos.x, pos.y, pos.z, 0.0F, rotation.x);
        try {
            callback.update(textDisplay, mannequin);
            stateFor(level.getServer()).add(new NpcDescriptor(
                    type,
                    mannequin.getUUID(),
                    textDisplay.getUUID(),
                    callbackData
            ), callback);
        } catch (RuntimeException exception) {
            textDisplay.discard();
            mannequin.discard();
            return DataResult.error(() -> "Failed to initialize NPC type " + type + ": " + exception.getMessage());
        }

        return DataResult.success(callback);
    }

    private static void registerCallbacks() {
        ServerLifecycleEvents.SERVER_STARTED.register(NpcCreator::stateFor);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVER_STATES::remove);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var state = stateFor(server);
            for (var registration : state.registrations().entrySet()) {
                var callback = registration.getValue();
                if (!callback.onTick()) {
                    continue;
                }
                var textEntity = server.overworld().getEntityInAnyDimension(registration.getKey().textDisplayId());
                var mannequinEntity = server.overworld().getEntityInAnyDimension(registration.getKey().mannequinId());
                if (textEntity instanceof Display.TextDisplay textDisplay && mannequinEntity instanceof Mannequin mannequin) {
                    callback.update(textDisplay, mannequin);
                }
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            var removalReason = entity.getRemovalReason();
            if (removalReason == null || !removalReason.shouldDestroy()) {
                return;
            }

            var descriptor = stateFor(level.getServer()).remove(entity.getUUID());
            if (descriptor != null) {
                var otherId = descriptor.mannequinId().equals(entity.getUUID()) ? descriptor.textDisplayId() : descriptor.mannequinId();
                var otherEntity = level.getEntityInAnyDimension(otherId);
                if (otherEntity != null) {
                    otherEntity.discard();
                }
            }
        });

        UseEntityCallback.EVENT.register((player, _, _, entity, _) -> interacted(player, entity, NpcCallback::onInteract));
        AttackEntityCallback.EVENT.register((player, _, _, entity, _) -> interacted(player, entity, NpcCallback::onAttack));
    }

    private static RuntimeState stateFor(MinecraftServer server) {
        return SERVER_STATES.computeIfAbsent(server, ignored -> RuntimeState.load(server));
    }

    private static InteractionResult interacted(
            Player player, Entity entity, BiConsumer<NpcCallback, ServerPlayer> action
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || player.isSpectator()
                || !(entity instanceof Mannequin mannequin)) {
            return InteractionResult.PASS;
        }

        var callback = stateFor(serverPlayer.level().getServer()).callback(mannequin.getUUID());
        if (callback == null) {
            return InteractionResult.PASS;
        }

        serverPlayer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        action.accept(callback, serverPlayer);
        return InteractionResult.SUCCESS;
    }

    private static final class RuntimeState extends SavedData {
        private static final Codec<RuntimeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NpcDescriptor.CODEC.listOf()
                        .optionalFieldOf("npcs", List.of())
                        .forGetter(RuntimeState::entries)
        ).apply(instance, RuntimeState::new));

        private static final SavedDataType<RuntimeState> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(PolyCore.MOD_ID, "npcs"),
                RuntimeState::new,
                RuntimeState.CODEC,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );

        private final Map<UUID, NpcDescriptor> descriptorsByEntity = new LinkedHashMap<>();
        private final Map<NpcDescriptor, NpcCallback> callbacksByDescriptor = new LinkedHashMap<>();

        private RuntimeState() {}

        private RuntimeState(List<NpcDescriptor> descriptors) {
            descriptors.forEach(this::putWithoutSaving);
        }

        private static RuntimeState load(MinecraftServer server) {
            var state = server.getDataStorage().computeIfAbsent(TYPE);
            for (var descriptor : state.entries()) {
                var factory = NPC_TYPES.get(descriptor.type());
                if (factory == null) {
                    LOGGER.warn("NPC type {} is not registered; keeping its saved registration for a later load", descriptor.type());
                    continue;
                }
                state.restore(descriptor, factory);
            }
            return state;
        }

        private Map<NpcDescriptor, NpcCallback> registrations() {
            return Map.copyOf(callbacksByDescriptor);
        }

        private @Nullable NpcCallback callback(UUID entityId) {
            var npcDescriptor = descriptorsByEntity.get(entityId);
            if (npcDescriptor == null) return null;
            return callbacksByDescriptor.get(npcDescriptor);
        }

        private List<NpcDescriptor> entries() {
            return descriptorsByEntity.values().stream().distinct().toList();
        }

        private void restoreType(Identifier type, NpcFactory factory) {
            for (var descriptor : entries()) {
                if (descriptor.type().equals(type) && !callbacksByDescriptor.containsKey(descriptor)) {
                    restore(descriptor, factory);
                }
            }
        }

        private void restore(NpcDescriptor descriptor, NpcFactory factory) {
            try {
                callbacksByDescriptor.put(descriptor, Objects.requireNonNull(
                        factory.create(descriptor.callbackData()),
                        "NPC factory returned null"
                ));
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to restore NPC {} of type {}", descriptor.mannequinId(), descriptor.type(), exception);
            }
        }

        private void putWithoutSaving(NpcDescriptor descriptor) {
            descriptorsByEntity.put(descriptor.mannequinId(), descriptor);
            descriptorsByEntity.put(descriptor.textDisplayId(), descriptor);
        }

        private void add(NpcDescriptor descriptor, NpcCallback callback) {
            if (descriptorsByEntity.containsKey(descriptor.mannequinId()) || descriptorsByEntity.containsKey(descriptor.textDisplayId())) {
                throw new IllegalStateException("An NPC entity UUID is already registered");
            }

            callbacksByDescriptor.put(descriptor, callback);
            putWithoutSaving(descriptor);
            setDirty();
        }

        private @Nullable NpcDescriptor remove(UUID entityId) {
            var descriptor = descriptorsByEntity.remove(entityId);
            if (descriptor == null) return null;

            descriptorsByEntity.remove(descriptor.mannequinId(), descriptor);
            descriptorsByEntity.remove(descriptor.textDisplayId(), descriptor);
            callbacksByDescriptor.remove(descriptor);
            setDirty();
            return descriptor;
        }
    }

    private record NpcDescriptor(
            Identifier type, UUID mannequinId,
            UUID textDisplayId, CompoundTag callbackData
    ) {
        private static final Codec<NpcDescriptor> CODEC = RecordCodecBuilder.<NpcDescriptor>create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(NpcDescriptor::type),
                UUIDUtil.STRING_CODEC.fieldOf("mannequin").forGetter(NpcDescriptor::mannequinId),
                UUIDUtil.STRING_CODEC.fieldOf("text_display").forGetter(NpcDescriptor::textDisplayId),
                CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(NpcDescriptor::callbackData)
        ).apply(instance, NpcDescriptor::new))
                .validate(descriptor ->
                        descriptor.mannequinId().equals(descriptor.textDisplayId())
                                ? DataResult.error(() -> "Mannequin and text display UUIDs must be different")
                                : DataResult.success(descriptor)
                );

        private NpcDescriptor {
            callbackData = callbackData.copy();
        }

        @Override
        public CompoundTag callbackData() {
            return callbackData.copy();
        }
    }

    /// Factory interface for creating NPC callbacks from persisted data.
    /// Implementations should be stateless and reconstructable from persisted data.
    @FunctionalInterface
    public interface NpcFactory {
        NpcCallback create(CompoundTag persistedData);
    }

    /// Callback interface for NPC behavior. Implementations should be stateless and reconstructable from persisted data.
    public interface NpcCallback {
        /// Called every tick for the NPC. Return true to update the text display and mannequin after this tick.
        default boolean onTick() {
            return false;
        }

        /// Called to update the text display and mannequin. This is called after onTick() returns true.
        default void update(Display.TextDisplay textDisplay, Mannequin mannequin) {}

        /// Called when a player interacts with the NPC.
        default void onInteract(ServerPlayer player) {}

        /// Called when a player attacks the NPC.
        default void onAttack(ServerPlayer player) {}
    }
}
