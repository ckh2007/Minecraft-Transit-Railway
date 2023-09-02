package org.mtr.mod.item;

import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;

import javax.annotation.Nullable;
import java.util.List;

public class ItemRailModifier extends ItemNodeModifierBase {

	private final boolean isOneWay;
	private final RailType railType;

	public ItemRailModifier(ItemSettings itemSettings) {
		super(true, true, true, false, itemSettings);
		isOneWay = false;
		railType = null;
	}

	public ItemRailModifier(boolean forNonContinuousMovementNode, boolean forContinuousMovementNode, boolean forAirplaneNode, boolean isOneWay, RailType railType, ItemSettings itemSettings) {
		super(forNonContinuousMovementNode, forContinuousMovementNode, forAirplaneNode, true, itemSettings);
		this.isOneWay = isOneWay;
		this.railType = railType;
	}

	@Override
	public void addTooltips(ItemStack stack, @Nullable World world, List<MutableText> tooltip, TooltipContext options) {
		if (isConnector && railType != null && railType.canAccelerate) {
			tooltip.add(TextHelper.translatable("tooltip.mtr.rail_speed_limit", railType.speedLimit).formatted(TextFormatting.GRAY));
		}
		super.addTooltips(stack, world, tooltip, options);
	}

	@Override
	protected void onConnect(World world, ItemStack stack, TransportMode transportMode, BlockState stateStart, BlockState stateEnd, BlockPos posStart, BlockPos posEnd, Angle facingStart, Angle facingEnd, @Nullable ServerPlayerEntity player) {
		if (railType != null) {
			final boolean isValidContinuousMovement;
			final RailType newRailType;
			if (transportMode.continuousMovement) {
				final Block blockStart = stateStart.getBlock();
				final Block blockEnd = stateEnd.getBlock();

				if (blockStart.data instanceof BlockNode.BlockContinuousMovementNode && blockEnd.data instanceof BlockNode.BlockContinuousMovementNode) {
					if (((BlockNode.BlockContinuousMovementNode) blockStart.data).isStation && ((BlockNode.BlockContinuousMovementNode) blockEnd.data).isStation) {
						isValidContinuousMovement = true;
						newRailType = railType.hasSavedRail ? railType : RailType.CABLE_CAR_STATION;
					} else {
						final int differenceX = posEnd.getX() - posStart.getX();
						final int differenceZ = posEnd.getZ() - posStart.getZ();
						isValidContinuousMovement = !railType.hasSavedRail && facingStart.isParallel(facingEnd)
								&& ((facingStart == Angle.N || facingStart == Angle.S) && differenceX == 0
								|| (facingStart == Angle.E || facingStart == Angle.W) && differenceZ == 0
								|| (facingStart == Angle.NE || facingStart == Angle.SW) && differenceX == -differenceZ
								|| (facingStart == Angle.SE || facingStart == Angle.NW) && differenceX == differenceZ);
						newRailType = RailType.CABLE_CAR;
					}
				} else {
					isValidContinuousMovement = false;
					newRailType = railType;
				}
			} else {
				isValidContinuousMovement = true;
				newRailType = railType;
			}

			final Position positionStart = new Position(posStart.getX(), posStart.getY(), posStart.getZ());
			final Position positionEnd = new Position(posEnd.getX(), posEnd.getY(), posEnd.getZ());
			final Rail rail1;
			final Rail rail2;

			switch (newRailType) {
				case PLATFORM:
					rail1 = Rail.newPlatformRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					rail2 = Rail.newPlatformRail(positionEnd, facingEnd, positionStart, facingStart, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					break;
				case SIDING:
					rail1 = Rail.newSidingRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					rail2 = Rail.newSidingRail(positionEnd, facingEnd, positionStart, facingStart, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					break;
				case TURN_BACK:
					rail1 = Rail.newTurnBackRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					rail2 = Rail.newTurnBackRail(positionEnd, facingEnd, positionStart, facingStart, Rail.Shape.CURVE, Rail.Shape.CURVE, transportMode);
					break;
				default:
					rail1 = Rail.newRail(positionStart, facingStart, positionEnd, facingEnd, (long) newRailType.speedLimitMetersPerMillisecond, newRailType.railShape, newRailType.railShape, false, newRailType.canAccelerate, newRailType.hasSignal, transportMode);
					rail2 = Rail.newRail(positionEnd, facingEnd, positionStart, facingStart, (long) newRailType.speedLimitMetersPerMillisecond, newRailType.railShape, newRailType.railShape, false, newRailType.canAccelerate, newRailType.hasSignal, transportMode);
			}

			final boolean goodRadius = rail1.goodRadius() && rail2.goodRadius();
			final boolean isValid = !rail1.isInvalid() && !rail2.isInvalid();

			if (goodRadius && isValid && isValidContinuousMovement) {
				world.setBlockState(posStart, stateStart.with(new Property<>(BlockNode.IS_CONNECTED.data), true));
				world.setBlockState(posEnd, stateEnd.with(new Property<>(BlockNode.IS_CONNECTED.data), true));
				// TODO
			} else if (player != null) {
				player.sendMessage(new Text(TextHelper.translatable(isValidContinuousMovement ? goodRadius ? "gui.mtr.invalid_orientation" : "gui.mtr.radius_too_small" : "gui.mtr.cable_car_invalid_orientation").data), true);
			}
		}
	}

	@Override
	protected void onRemove(World world, BlockPos posStart, BlockPos posEnd, @Nullable ServerPlayerEntity player) {
		// TODO
	}
}
