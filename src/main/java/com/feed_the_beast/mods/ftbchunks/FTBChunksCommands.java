package com.feed_the_beast.mods.ftbchunks;

import com.feed_the_beast.mods.ftbchunks.api.ChunkDimPos;
import com.feed_the_beast.mods.ftbchunks.api.ClaimResult;
import com.feed_the_beast.mods.ftbchunks.api.ClaimedChunk;
import com.feed_the_beast.mods.ftbchunks.api.FTBChunksAPI;
import com.feed_the_beast.mods.ftbchunks.impl.ClaimedChunkImpl;
import com.feed_the_beast.mods.ftbchunks.impl.ClaimedChunkPlayerDataImpl;
import com.feed_the_beast.mods.ftbchunks.impl.FTBChunksAPIImpl;
import com.feed_the_beast.mods.ftbguilibrary.utils.MathUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author LatvianModder
 */
public class FTBChunksCommands
{
	public FTBChunksCommands(FMLServerStartingEvent event)
	{
		LiteralCommandNode<CommandSource> command = event.getCommandDispatcher().register(Commands.literal("ftbchunks")
				.then(Commands.literal("claim")
						.executes(context -> claim(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 300))
								.executes(context -> claim(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))
						)
				)
				.then(Commands.literal("unclaim")
						.executes(context -> unclaim(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 300))
								.executes(context -> unclaim(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))
						)
				)
				.then(Commands.literal("load")
						.executes(context -> load(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 300))
								.executes(context -> load(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))
						)
				)
				.then(Commands.literal("unload")
						.executes(context -> unload(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 300))
								.executes(context -> unload(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))
						)
				)
				.then(Commands.literal("unclaim_all")
						.then(Commands.argument("players", GameProfileArgument.gameProfile())
								.requires(source -> source.hasPermissionLevel(2))
								.executes(context -> unclaimAll(context.getSource(), GameProfileArgument.getGameProfiles(context, "players")))
						)
						.executes(context -> unclaimAll(context.getSource(), Collections.singleton(context.getSource().asPlayer().getGameProfile())))
				)
				.then(Commands.literal("unload_all")
						.then(Commands.argument("players", GameProfileArgument.gameProfile())
								.requires(source -> source.hasPermissionLevel(2))
								.executes(context -> unloadAll(context.getSource(), GameProfileArgument.getGameProfiles(context, "players")))
						)
						.executes(context -> unloadAll(context.getSource(), Collections.singleton(context.getSource().asPlayer().getGameProfile())))
				)
				.then(Commands.literal("info")
						.executes(context -> info(context.getSource(), new ChunkDimPos(context.getSource().getWorld().dimension.getType(), new ChunkPos(new BlockPos(context.getSource().getPos())))))
						.then(Commands.argument("x", IntegerArgumentType.integer())
								.then(Commands.argument("z", IntegerArgumentType.integer())
										.executes(context -> info(context.getSource(), new ChunkDimPos(context.getSource().getWorld().dimension.getType(), IntegerArgumentType.getInteger(context, "x") >> 4, IntegerArgumentType.getInteger(context, "z") >> 4)))
										.then(Commands.argument("dimension", DimensionArgument.getDimension())
												.executes(context -> info(context.getSource(), new ChunkDimPos(DimensionArgument.getDimensionArgument(context, "dimension"), IntegerArgumentType.getInteger(context, "x") >> 4, IntegerArgumentType.getInteger(context, "z") >> 4)))
										)
								)
						)
				)
				.then(Commands.literal("export")
						.then(Commands.literal("json")
								.executes(context -> exportJson(context.getSource()))
						)
						.then(Commands.literal("svg")
								.executes(context -> exportSvg(context.getSource()))
						)
				)
		);

		event.getCommandDispatcher().register(Commands.literal("chunks").redirect(command));
	}

	private interface ChunkCallback
	{
		void accept(ClaimedChunkPlayerData data, ChunkDimPos pos) throws CommandSyntaxException;
	}

	private void forEachChunk(CommandSource source, int r, ChunkCallback callback) throws CommandSyntaxException
	{
		ClaimedChunkPlayerData data = FTBChunksAPI.INSTANCE.getManager().getData(source.asPlayer());
		int c = r >> 4;
		DimensionType type = source.getWorld().dimension.getType();
		int ox = MathHelper.floor(source.getPos().x) >> 4;
		int oz = MathHelper.floor(source.getPos().z) >> 4;
		List<ChunkDimPos> list = new ArrayList<>();

		for (int z = -c; z <= c; z++)
		{
			for (int x = -c; x <= c; x++)
			{
				list.add(new ChunkDimPos(type, ox + x, oz + z));
			}
		}

		list.sort(Comparator.comparingDouble(o -> MathUtils.distSq(ox, oz, o.x, o.z)));

		for (ChunkDimPos pos : list)
		{
			callback.accept(data, pos);
		}
	}

	private int claim(CommandSource source, int r) throws CommandSyntaxException
	{
		int[] success = new int[1];
		Instant time = Instant.now();

		forEachChunk(source, r, (data, pos) -> {
			ClaimResult result = data.claim(source, pos, false);

			if (result.isSuccess())
			{
				success[0]++;

				if (result instanceof ClaimedChunkImpl)
				{
					((ClaimedChunkImpl) result).time = time;
				}
			}
		});

		source.sendFeedback(new StringTextComponent("Claimed " + success[0] + " chunks!"), true);
		FTBChunks.LOGGER.info(source.getName() + " claimed " + success[0] + " chunks at " + new ChunkDimPos(source.asPlayer()));
		return success[0];
	}

	private int unclaim(CommandSource source, int r) throws CommandSyntaxException
	{
		int[] success = new int[1];

		forEachChunk(source, r, (data, pos) -> {
			if (data.unclaim(source, pos, false).isSuccess())
			{
				success[0]++;
			}
		});

		source.sendFeedback(new StringTextComponent("Unclaimed " + success[0] + " chunks!"), true);
		FTBChunks.LOGGER.info(source.getName() + " unclaimed " + success[0] + " chunks at " + new ChunkDimPos(source.asPlayer()));
		return success[0];
	}

	private int load(CommandSource source, int r) throws CommandSyntaxException
	{
		int[] success = new int[1];

		forEachChunk(source, r, (data, pos) -> {
			if (data.load(source, pos, false).isSuccess())
			{
				success[0]++;
			}
		});

		source.sendFeedback(new StringTextComponent("Loaded " + success[0] + " chunks!"), true);
		FTBChunks.LOGGER.info(source.getName() + " loaded " + success[0] + " chunks at " + new ChunkDimPos(source.asPlayer()));
		return success[0];
	}

	private int unload(CommandSource source, int r) throws CommandSyntaxException
	{
		int[] success = new int[1];

		forEachChunk(source, r, (data, pos) -> {
			if (data.unload(source, pos, false).isSuccess())
			{
				success[0]++;
			}
		});

		source.sendFeedback(new StringTextComponent("Unloaded " + success[0] + " chunks!"), true);
		FTBChunks.LOGGER.info(source.getName() + " unloaded " + success[0] + " chunks at " + new ChunkDimPos(source.asPlayer()));
		return success[0];
	}

	private int unclaimAll(CommandSource source, Collection<GameProfile> players)
	{
		for (GameProfile profile : players)
		{
			ClaimedChunkPlayerDataImpl data = FTBChunksAPIImpl.manager.playerData.get(profile.getId());

			if (data != null)
			{
				for (ClaimedChunk c : data.getClaimedChunks())
				{
					data.unclaim(source, c.getPos(), false);
				}

				data.save();
			}
		}

		return 1;
	}

	private int unloadAll(CommandSource source, Collection<GameProfile> players)
	{
		for (GameProfile profile : players)
		{
			ClaimedChunkPlayerDataImpl data = FTBChunksAPIImpl.manager.playerData.get(profile.getId());

			if (data != null)
			{
				for (ClaimedChunk c : data.getClaimedChunks())
				{
					data.unload(source, c.getPos(), false);
				}
			}
		}

		return 1;
	}

	private int info(CommandSource source, ChunkDimPos pos)
	{
		source.sendFeedback(new StringTextComponent("Location: " + pos), true);

		ClaimedChunk chunk = FTBChunksAPIImpl.manager.getChunk(pos);

		if (chunk == null)
		{
			source.sendFeedback(new StringTextComponent("Chunk not claimed!"), true);
			return 0;
		}

		source.sendFeedback(new StringTextComponent("Owner: " + chunk.getPlayerData().getName() + " / " + UUIDTypeAdapter.fromUUID(chunk.getPlayerData().getUuid())), true);

		if (source.hasPermissionLevel(2))
		{
			source.sendFeedback(new StringTextComponent("Force Loaded: " + chunk.isForceLoaded()), true);
		}

		return 1;
	}

	private int exportJson(CommandSource source)
	{
		FTBChunksAPIImpl.manager.exportJson();
		source.sendFeedback(new StringTextComponent("Exported FTB Chunks data to <world directory>/data/ftbchunks/export/all.json!"), true);
		return 1;
	}

	private int exportSvg(CommandSource source)
	{
		FTBChunksAPIImpl.manager.exportSvg();
		source.sendFeedback(new StringTextComponent("Exported FTB Chunks data to <world directory>/data/ftbchunks/export/<dimension>.svg!"), true);
		return 1;
	}
}