/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.accessor.IItemStack;
import com.google.common.collect.ImmutableSet;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.*;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.loot.LootManager;
import net.minecraft.world.loot.LootTables;
import net.minecraft.world.loot.context.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BlockOptionalMeta {

    private final Block block;
    private final Set<BlockState> blockstates;
    private final ImmutableSet<Integer> stateHashes;
    private final ImmutableSet<Integer> stackHashes;
    private static final Pattern pattern = Pattern.compile("^(.+?)(?::(\\d+))?$");
    private static final Map<Object, Object> normalizations;
    private static LootManager manager;
    private static Map<Block, List<Item>> drops = new HashMap<>();

    public BlockOptionalMeta(Block block) {
        this.block = block;
        this.blockstates = getStates(block);
        this.stateHashes = getStateHashes(blockstates);
        this.stackHashes = getStackHashes(blockstates);
    }

    public BlockOptionalMeta(String selector) {
        Matcher matcher = pattern.matcher(selector);

        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid block selector");
        }

        MatchResult matchResult = matcher.toMatchResult();

        Identifier id = new Identifier(matchResult.group(1));

        if (!Registry.BLOCK.containsId(id)) {
            throw new IllegalArgumentException("Invalid block ID");
        }

        block = Registry.BLOCK.getOrEmpty(id).orElse(null);
        blockstates = getStates(block);
        stateHashes = getStateHashes(blockstates);
        stackHashes = getStackHashes(blockstates);
    }

    static {
        Map<Object, Object> _normalizations = new HashMap<>();
        Consumer<Enum<?>> put = instance -> _normalizations.put(instance.getClass(), instance);
        put.accept(Direction.NORTH);
        put.accept(Direction.Axis.Y);
        put.accept(BlockHalf.BOTTOM);
        put.accept(StairShape.STRAIGHT);
        put.accept(Attachment.FLOOR);
        put.accept(DoubleBlockHalf.UPPER);
        put.accept(SlabType.BOTTOM);
        put.accept(DoorHinge.LEFT);
        put.accept(BedPart.HEAD);
        put.accept(RailShape.NORTH_SOUTH);
        _normalizations.put(BannerBlock.ROTATION, 0);
        _normalizations.put(BedBlock.OCCUPIED, false);
        _normalizations.put(BrewingStandBlock.BOTTLE_PROPERTIES[0], false);
        _normalizations.put(BrewingStandBlock.BOTTLE_PROPERTIES[1], false);
        _normalizations.put(BrewingStandBlock.BOTTLE_PROPERTIES[2], false);
        _normalizations.put(AbstractButtonBlock.POWERED, false);
        // _normalizations.put(BlockCactus.AGE, 0);
        // _normalizations.put(BlockCauldron.LEVEL, 0);
        // _normalizations.put(BlockChorusFlower.AGE, 0);
        _normalizations.put(ChorusPlantBlock.NORTH, false);
        _normalizations.put(ChorusPlantBlock.EAST, false);
        _normalizations.put(ChorusPlantBlock.SOUTH, false);
        _normalizations.put(ChorusPlantBlock.WEST, false);
        _normalizations.put(ChorusPlantBlock.UP, false);
        _normalizations.put(ChorusPlantBlock.DOWN, false);
        // _normalizations.put(BlockCocoa.AGE, 0);
        // _normalizations.put(BlockCrops.AGE, 0);
        _normalizations.put(SnowyBlock.SNOWY, false);
        _normalizations.put(DoorBlock.OPEN, false);
        _normalizations.put(DoorBlock.POWERED, false);
        // _normalizations.put(BlockFarmland.MOISTURE, 0);
        _normalizations.put(FenceBlock.NORTH, false);
        _normalizations.put(FenceBlock.EAST, false);
        _normalizations.put(FenceBlock.WEST, false);
        _normalizations.put(FenceBlock.SOUTH, false);
        // _normalizations.put(BlockFenceGate.POWERED, false);
        // _normalizations.put(BlockFenceGate.IN_WALL, false);
        _normalizations.put(FireBlock.AGE, 0);
        _normalizations.put(FireBlock.NORTH, false);
        _normalizations.put(FireBlock.EAST, false);
        _normalizations.put(FireBlock.SOUTH, false);
        _normalizations.put(FireBlock.WEST, false);
        _normalizations.put(FireBlock.UP, false);
        // _normalizations.put(BlockFrostedIce.AGE, 0);
        _normalizations.put(GrassBlock.SNOWY, false);
        // _normalizations.put(BlockHopper.ENABLED, true);
        // _normalizations.put(BlockLever.POWERED, false);
        // _normalizations.put(BlockLiquid.LEVEL, 0);
        // _normalizations.put(BlockMycelium.SNOWY, false);
        // _normalizations.put(BlockNetherWart.AGE, false);
        _normalizations.put(LeavesBlock.DISTANCE, false);
        // _normalizations.put(BlockLeaves.DECAYABLE, false);
        // _normalizations.put(BlockObserver.POWERED, false);
        _normalizations.put(PaneBlock.NORTH, false);
        _normalizations.put(PaneBlock.EAST, false);
        _normalizations.put(PaneBlock.WEST, false);
        _normalizations.put(PaneBlock.SOUTH, false);
        // _normalizations.put(BlockPistonBase.EXTENDED, false);
        // _normalizations.put(BlockPressurePlate.POWERED, false);
        // _normalizations.put(BlockPressurePlateWeighted.POWER, false);
        // _normalizations.put(BlockRailDetector.POWERED, false);
        // _normalizations.put(BlockRailPowered.POWERED, false);
        _normalizations.put(RedstoneWireBlock.WIRE_CONNECTION_NORTH, false);
        _normalizations.put(RedstoneWireBlock.WIRE_CONNECTION_EAST, false);
        _normalizations.put(RedstoneWireBlock.WIRE_CONNECTION_SOUTH, false);
        _normalizations.put(RedstoneWireBlock.WIRE_CONNECTION_WEST, false);
        // _normalizations.put(BlockReed.AGE, false);
        _normalizations.put(SaplingBlock.STAGE, 0);
        _normalizations.put(SignBlock.ROTATION, 0);
        _normalizations.put(StemBlock.AGE, 0);
        _normalizations.put(TripwireBlock.NORTH, false);
        _normalizations.put(TripwireBlock.EAST, false);
        _normalizations.put(TripwireBlock.WEST, false);
        _normalizations.put(TripwireBlock.SOUTH, false);
        _normalizations.put(VineBlock.NORTH, false);
        _normalizations.put(VineBlock.EAST, false);
        _normalizations.put(VineBlock.SOUTH, false);
        _normalizations.put(VineBlock.WEST, false);
        _normalizations.put(VineBlock.UP, false);
        _normalizations.put(WallBlock.UP, false);
        _normalizations.put(WallBlock.NORTH, false);
        _normalizations.put(WallBlock.EAST, false);
        _normalizations.put(WallBlock.WEST, false);
        _normalizations.put(WallBlock.SOUTH, false);
        normalizations = Collections.unmodifiableMap(_normalizations);
    }

    private static <C extends Comparable<C>, P extends Property<C>> P castToIProperty(Object value) {
        //noinspection unchecked
        return (P) value;
    }

    private static <C extends Comparable<C>, P extends Property<C>> C castToIPropertyValue(P iproperty, Object value) {
        //noinspection unchecked
        return (C) value;
    }

    public static BlockState normalize(BlockState state) {
        BlockState newState = state;

        for (Property<?> property : state.getProperties()) {
            Class<?> valueClass = property.getValueType();
            if (normalizations.containsKey(property)) {
                try {
                    newState = newState.with(
                            castToIProperty(property),
                            castToIPropertyValue(property, normalizations.get(property))
                    );
                } catch (IllegalArgumentException ignored) {}
            } else if (normalizations.containsKey(state.get(property))) {
                try {
                    newState = newState.with(
                            castToIProperty(property),
                            castToIPropertyValue(property, normalizations.get(state.get(property)))
                    );
                } catch (IllegalArgumentException ignored) {}
            } else if (normalizations.containsKey(valueClass)) {
                try {
                    newState = newState.with(
                            castToIProperty(property),
                            castToIPropertyValue(property, normalizations.get(valueClass))
                    );
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return newState;
    }

    public static int stateMeta(BlockState state) {
        return state.hashCode();
    }

    private static Set<BlockState> getStates(Block block) {
        return new HashSet<>(block.getStateFactory().getStates());
    }

    private static ImmutableSet<Integer> getStateHashes(Set<BlockState> blockstates) {
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .map(BlockState::hashCode)
                        .toArray(Integer[]::new)
        );
    }

    private static ImmutableSet<Integer> getStackHashes(Set<BlockState> blockstates) {
        //noinspection ConstantConditions
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .flatMap(state -> {
                            List<Item> originDrops = drops(state.getBlock());
                            originDrops.add(state.getBlock().asItem());
                                    return originDrops.stream().map(item -> new ItemStack(item ,1));
                                }
                        )
                        .map(stack -> ((IItemStack) (Object) stack).getBaritoneHash())
                        .toArray(Integer[]::new)
        );
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(Block block) {
        return block == this.block;
    }

    public boolean matches(BlockState blockstate) {
        Block block = blockstate.getBlock();
        return block == this.block && stateHashes.contains(blockstate.hashCode());
    }

    public boolean matches(ItemStack stack) {
        //noinspection ConstantConditions
        int hash = ((IItemStack) (Object) stack).getBaritoneHash();

        hash -= stack.getDamage();

        return stackHashes.contains(hash);
    }

    @Override
    public String toString() {
        return String.format("BlockOptionalMeta{block=%s}", block);
    }

    public BlockState getAnyBlockState() {
        if (blockstates.size() > 0) {
            return blockstates.iterator().next();
        }

        return null;
    }

    public static LootManager getManager() {
        if (manager == null) {
            ResourcePackContainerManager<?> rpl = new ResourcePackContainerManager<>(ResourcePackContainer::new);
            rpl.addCreator(new DefaultResourcePackCreator());
            rpl.callCreators();
            List<ResourcePack> thePacks = new ArrayList<>();

            while (rpl.getEnabledContainers() != null && rpl.getEnabledContainers().iterator().hasNext()) {
                ResourcePack thePack = rpl.getEnabledContainers().iterator().next().createResourcePack();
                thePacks.add(thePack);
            }
            ReloadableResourceManager resourceManager = new ReloadableResourceManagerImpl(ResourceType.SERVER_DATA, null);
            manager = new LootManager();
            resourceManager.registerListener(manager);
            try {
                resourceManager.beginReload(new ThreadPerTaskExecutor(Thread::new), new ThreadPerTaskExecutor(Thread::new), thePacks, CompletableFuture.completedFuture(Unit.INSTANCE)).get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        return manager;
    }

    private static synchronized List<Item> drops(Block b) {
        return drops.computeIfAbsent(b, block -> {
            Identifier lootTableLocation = block.getDropTableId();
            if (lootTableLocation == LootTables.EMPTY) {
                return Collections.emptyList();
            } else {
                IntegratedServer server = Helper.mc.getServer();
                return getManager().getSupplier(lootTableLocation).getDrops(new LootContext.Builder(server.getWorld(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().player().dimension)).setRandom(new Random()).put(LootContextParameters.POSITION, BlockPos.ORIGIN).put(LootContextParameters.TOOL, ItemStack.EMPTY).putNullable(LootContextParameters.BLOCK_ENTITY, null).put(LootContextParameters.BLOCK_STATE, block.getDefaultState()).build(LootContextTypes.BLOCK)).stream().map(ItemStack::getItem).collect(Collectors.toList());
            }
        });
    }
}
