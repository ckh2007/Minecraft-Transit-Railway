package mtr.data;

import mtr.path.PathData2;
import mtr.path.PathFinder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Depot extends AreaBase {

	private long lastDeployedMillis;

	public final List<Long> routeIds;

	private final int[] frequencies = new int[HOURS_IN_DAY];

	public static final int HOURS_IN_DAY = 24;
	public static final int TICKS_PER_HOUR = 1000;
	public static final int TICKS_PER_DAY = HOURS_IN_DAY * TICKS_PER_HOUR;

	private static final String KEY_ROUTE_IDS = "route_ids";
	private static final String KEY_FREQUENCIES = "frequencies";
	private static final String KEY_LAST_DEPLOYED = "last_deployed";

	public Depot() {
		super();
		routeIds = new ArrayList<>();
	}

	public Depot(long id) {
		super(id);
		routeIds = new ArrayList<>();
	}

	public Depot(CompoundTag tag) {
		super(tag);

		routeIds = new ArrayList<>();
		final long[] routeIdsArray = tag.getLongArray(KEY_ROUTE_IDS);
		for (final long routeId : routeIdsArray) {
			routeIds.add(routeId);
		}

		for (int i = 0; i < HOURS_IN_DAY; i++) {
			frequencies[i] = tag.getInt(KEY_FREQUENCIES + i);
		}

		lastDeployedMillis = System.currentTimeMillis() - tag.getLong(KEY_LAST_DEPLOYED);
	}

	public Depot(PacketByteBuf packet) {
		super(packet);

		routeIds = new ArrayList<>();
		final int routeIdCount = packet.readInt();
		for (int i = 0; i < routeIdCount; i++) {
			routeIds.add(packet.readLong());
		}

		for (int i = 0; i < HOURS_IN_DAY; i++) {
			frequencies[i] = packet.readInt();
		}

		lastDeployedMillis = packet.readLong();
	}

	@Override
	public CompoundTag toCompoundTag() {
		final CompoundTag tag = super.toCompoundTag();

		tag.putLongArray(KEY_ROUTE_IDS, routeIds);

		for (int i = 0; i < HOURS_IN_DAY; i++) {
			tag.putInt(KEY_FREQUENCIES + i, frequencies[i]);
		}

		tag.putLong(KEY_LAST_DEPLOYED, System.currentTimeMillis() - lastDeployedMillis);

		return tag;
	}

	@Override
	public void writePacket(PacketByteBuf packet) {
		super.writePacket(packet);

		packet.writeInt(routeIds.size());
		routeIds.forEach(packet::writeLong);

		for (final int frequency : frequencies) {
			packet.writeInt(frequency);
		}

		packet.writeLong(lastDeployedMillis);
	}

	@Override
	public void update(String key, PacketByteBuf packet) {
		switch (key) {
			case KEY_ROUTE_IDS:
				routeIds.clear();
				final int routeIdCount = packet.readInt();
				for (int i = 0; i < routeIdCount; i++) {
					routeIds.add(packet.readLong());
				}
				break;
			case KEY_FREQUENCIES:
				for (int i = 0; i < HOURS_IN_DAY; i++) {
					frequencies[i] = packet.readInt();
				}
				break;
			default:
				super.update(key, packet);
		}
	}

	public int getFrequency(int index) {
		if (index >= 0 && index < frequencies.length) {
			return frequencies[index];
		} else {
			return 0;
		}
	}

	public void setFrequencies(int newFrequency, int index, Consumer<PacketByteBuf> sendPacket) {
		if (index >= 0 && index < frequencies.length) {
			frequencies[index] = newFrequency;
			final PacketByteBuf packet = PacketByteBufs.create();
			packet.writeLong(id);
			packet.writeString(KEY_FREQUENCIES);
			for (final int frequency : frequencies) {
				packet.writeInt(frequency);
			}
			sendPacket.accept(packet);
		}
	}

	public void setRouteIds(Consumer<PacketByteBuf> sendPacket) {
		final PacketByteBuf packet = PacketByteBufs.create();
		packet.writeLong(id);
		packet.writeString(KEY_ROUTE_IDS);
		packet.writeInt(routeIds.size());
		routeIds.forEach(packet::writeLong);
		sendPacket.accept(packet);
	}

	public boolean deployTrain(WorldAccess world) {
		final long currentMillis = System.currentTimeMillis();
		final int hour = (int) wrapTime(world.getLunarTime(), -6000) / TICKS_PER_HOUR;
		final boolean success = frequencies[hour] > 0 && currentMillis - lastDeployedMillis >= 50 * TICKS_PER_HOUR / frequencies[hour];
		if (success) {
			lastDeployedMillis = currentMillis;
		}
		return success;
	}

	public void generateRoute(Map<BlockPos, Map<BlockPos, Rail>> rails, Set<Platform> platforms, Set<Siding> sidings, Set<Route> routes) {
		final List<SavedRailBase> platformsInRoute = new ArrayList<>();
		routeIds.forEach(routeId -> {
			final Route route = RailwayData.getDataById(routes, routeId);
			if (route != null) {
				route.platformIds.forEach(platformId -> {
					final Platform platform = RailwayData.getDataById(platforms, platformId);
					if (platform != null) {
						platformsInRoute.add(platform);
					}
				});
			}
		});

		if (platformsInRoute.isEmpty()) {
			return;
		}

		final List<PathData2> path = PathFinder.findPath(rails, platformsInRoute.toArray(new SavedRailBase[0]));
		if (!path.isEmpty()) {
			sidings.forEach(siding -> {
				final BlockPos sidingPos = siding.getMidPos();

				if (inArea(sidingPos.getX(), sidingPos.getZ())) {
					final List<PathData2> finalPath = new ArrayList<>();

					final boolean success1 = PathFinder.addToPath(finalPath, PathFinder.findPath(rails, siding, platformsInRoute.get(0)));
					if (success1) {
						PathFinder.addToPath(finalPath, path);
					}
					final boolean success2 = success1 && PathFinder.addToPath(finalPath, PathFinder.findPath(rails, platformsInRoute.get(platformsInRoute.size() - 1), siding));

					if (!success2) {
						final List<BlockPos> orderedPositions = siding.getOrderedPositions(new BlockPos(0, 0, 0), false);
						final BlockPos pos1 = orderedPositions.get(0);
						final BlockPos pos2 = orderedPositions.get(1);
						if (RailwayData.containsRail(rails, pos1, pos2)) {
							finalPath.add(new PathData2(rails.get(pos1).get(pos2), 0, pos1, pos2));
						}
					}

					siding.setPath(finalPath, this);
				}
			});
		}
	}

	public static float wrapTime(float time1, float time2) {
		return (time1 - time2 + TICKS_PER_DAY) % TICKS_PER_DAY;
	}
}
