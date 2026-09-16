package carpet.patches;

import carpet.CarpetSettings;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import io.papermc.paper.util.KeepAlive;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import carpet.folia.MixinCompat;
import carpet.utils.Messenger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("EntityConstructor")
public class EntityPlayerMPFake extends ServerPlayer
{
    private static final Set<String> spawning = new HashSet<>();
    private int fakeTickCounter;

    public Runnable fixStartingPosition = () -> {};
    public boolean isAShadow;

    public static boolean createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying)
    {

        ServerLevel worldIn = server.getLevel(dimensionId);
        server.services().nameToIdCache().resolveOfflineUsers(false);
        GameProfile gameprofile;

            UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);

            if (uuid == null && CarpetSettings.allowSpawningOfflinePlayers) {
                server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
                uuid = UUIDUtil.createOfflinePlayerUUID(username);
            }
            if (uuid == null) {
                return false;
            }
            gameprofile = new GameProfile(uuid, username);

        String name = gameprofile.name();
        spawning.add(name);

        GameProfile fetchedProfile = null;
        try
        {
            fetchedProfile = fetchGameProfileByName(server, name)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (Exception ignored) {}

        GameProfile finalProfile;
        if (fetchedProfile != null && !fetchedProfile.name().isEmpty()
                && fetchedProfile.properties().containsKey("textures"))
        {

            finalProfile = fetchedProfile;
        }
        else
        {
            finalProfile = gameprofile;
        }
        spawning.remove(name);

        try
        {
            net.minecraft.world.level.ChunkPos cpos = new net.minecraft.world.level.ChunkPos(
                    net.minecraft.core.BlockPos.containing(pos));
            org.bukkit.plugin.Plugin plugin = carpet.folia.CarpetFoliaPlugin.get();
            org.bukkit.Bukkit.getRegionScheduler().execute(
                    plugin,
                    worldIn.getWorld(),
                    cpos.x, cpos.z,
                    () -> spawnFake(server, worldIn, finalProfile, pos, yaw, pitch, dimensionId, gamemode, flying)
            );
        }
        catch (Throwable e)
        {
            CarpetSettings.LOG.error("[FoilCarpet] Failed to schedule fake player " + name, e);
        }
        return true;
    }

    private static void spawnFake(MinecraftServer server, ServerLevel worldIn, GameProfile current, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying)
    {
        EntityPlayerMPFake instance = new EntityPlayerMPFake(server, worldIn, current, ClientInformation.createDefault(), false);
        instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);

        instance.fixStartingPosition.run();
        FakeClientConnection fakeConnection = new FakeClientConnection(PacketFlow.SERVERBOUND);
        CommonListenerCookie cookie = new CommonListenerCookie(current, 0, instance.clientInformation(), false, null, Set.of(), new KeepAlive());
        server.getPlayerList().placeNewPlayer(fakeConnection, instance, cookie);
        ensureFakeHandler(instance, fakeConnection, cookie);
        loadPlayerData(instance);
        instance.stopRiding();
        instance.fixStartingPosition.run();
        instance.setHealth(20.0F);
        instance.unsetRemoved();
        instance.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
        instance.gameMode.changeGameModeForPlayer(gamemode);
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);
        server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), dimensionId);

        instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
        instance.getAbilities().flying = flying;
    }

    private static void ensureFakeHandler(EntityPlayerMPFake instance, FakeClientConnection fakeConnection, CommonListenerCookie cookie)
    {

        if (!(instance.connection instanceof NetHandlerPlayServerFake))
        {
            instance.connection = new NetHandlerPlayServerFake(instance.level().getServer(), fakeConnection, instance, cookie);
        }
    }

    private static CompletableFuture<GameProfile> fetchGameProfileByName(MinecraftServer server, final String name) {

        return CompletableFuture.supplyAsync(() -> {
            try
            {
                java.util.Optional<com.mojang.authlib.yggdrasil.response.NameAndId> nameAndId =
                        server.services().profileRepository().findProfileByName(name);
                if (nameAndId.isEmpty())
                {
                    return null;
                }
                com.mojang.authlib.yggdrasil.ProfileResult result =
                        server.services().sessionService().fetchProfile(nameAndId.get().id(), true);
                if (result != null && result.profile() != null)
                {
                    return result.profile();
                }
            }
            catch (Exception ignored)
            {
            }
            return null;
        });
    }

    private static void loadPlayerData(EntityPlayerMPFake player)
    {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), CarpetSettings.LOG))
        {
            Optional<ValueInput> optional = player.level().getServer().getPlayerList().loadPlayerData(player.nameAndId()).map((compoundTag) -> TagValueInput.create(scopedCollector, player.registryAccess(), compoundTag));
            optional.ifPresent( valueInput -> {
                player.load(valueInput);
                player.loadAndSpawnEnderPearls(valueInput);
                player.loadAndSpawnParentVehicle(valueInput);
            });
        }
    }

    public static EntityPlayerMPFake createShadow(MinecraftServer server, ServerPlayer player)
    {
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
        ServerLevel worldIn = player.level();
        GameProfile gameprofile = player.getGameProfile();
        EntityPlayerMPFake playerShadow = new EntityPlayerMPFake(server, worldIn, gameprofile, player.clientInformation(), true);
        playerShadow.setChatSession(player.getChatSession());
        FakeClientConnection fakeConnection = new FakeClientConnection(PacketFlow.SERVERBOUND);
        CommonListenerCookie cookie = new CommonListenerCookie(gameprofile, 0, player.clientInformation(), true, null, Set.of(), new KeepAlive());
        server.getPlayerList().placeNewPlayer(fakeConnection, playerShadow, cookie);
        ensureFakeHandler(playerShadow, fakeConnection, cookie);
        loadPlayerData(playerShadow);

        playerShadow.setHealth(player.getHealth());
        playerShadow.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        playerShadow.gameMode.changeGameModeForPlayer(player.gameMode.getGameModeForPlayer());
        MixinCompat.player_getActionPack(playerShadow).copyFrom(MixinCompat.player_getActionPack(player));

        playerShadow.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
        playerShadow.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, player.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));

        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(playerShadow, (byte) (player.yHeadRot * 256 / 360)), playerShadow.level().dimension());
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, playerShadow));

        playerShadow.getAbilities().flying = player.getAbilities().flying;
        return playerShadow;
    }

    public static EntityPlayerMPFake respawnFake(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation cli)
    {
        return new EntityPlayerMPFake(server, level, profile, cli, false);
    }

    public static boolean isSpawningPlayer(String username)
    {
        return spawning.contains(username);
    }

    private EntityPlayerMPFake(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli, boolean shadow)
    {
        super(server, worldIn, profile, cli);
        this.isAShadow = shadow;
    }

    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack)
    {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    @Override
    protected void markHurt()
    {

    }

    @Override
    public void kill(ServerLevel level)
    {
        kill(Messenger.s("Killed"));
    }

    public void kill(Component reason)
    {
        shakeOff();

        if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level(), this.blockPosition()))
        {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
        }
        else
        {
            net.minecraft.world.level.ChunkPos cpos = new net.minecraft.world.level.ChunkPos(this.blockPosition());
            org.bukkit.plugin.Plugin plugin = carpet.folia.CarpetFoliaPlugin.get();
            final Component r = reason;
            org.bukkit.Bukkit.getRegionScheduler().execute(plugin, this.level().getWorld(), cpos.x, cpos.z,
                    () -> this.connection.onDisconnect(new DisconnectionDetails(r)));
        }
    }

    @Override
    public void tick()
    {
        if (++fakeTickCounter % 10 == 0)
        {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
        }
        try
        {
            super.tick();
            this.doTick();
        }
        catch (Throwable ignored)
        {

        }
    }

    @Override
    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        if (super.startRiding(entityToRide, force, sendEventAndTriggers)) {

            if (entityToRide instanceof AbstractBoat) {
                this.yRotO = entityToRide.getYRot();
                this.setYRot(entityToRide.getYRot());
                this.setYHeadRot(entityToRide.getYRot());
            }
            return true;
        } else {
            return false;
        }
    }

    private void shakeOff()
    {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers())
        {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public void die(DamageSource cause)
    {
        shakeOff();
        super.die(cause);
        setHealth(20);
        this.foodData = new FoodData();
        kill(this.getCombatTracker().getDeathMessage());
    }

    @Override
    public String getIpAddress()
    {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return CarpetSettings.allowListingFakePlayers;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(0.0, y, 0.0, onGround);
    }

    @Override
    public ServerPlayer teleport(TeleportTransition serverLevel)
    {
        super.teleport(serverLevel);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }
}
