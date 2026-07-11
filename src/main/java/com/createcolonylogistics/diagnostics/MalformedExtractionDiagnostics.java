package com.createcolonylogistics.diagnostics;

import com.createcolonylogistics.CreateColonyLogistics;
import com.createcolonylogistics.config.ColonyLogisticsConfig;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class MalformedExtractionDiagnostics {
    private static final String MALFORMED_EXTRACTION_DIAGNOSTICS_PROPERTY =
            "create_colony_logistics.debugMalformedExtraction";
    private static final long FULL_DIAGNOSTIC_INTERVAL_TICKS = 200L;
    private static final int TEXT_LIMIT = 180;
    private static final Map<DiagnosticKey, DiagnosticState> DIAGNOSTIC_STATE = new HashMap<>();

    private MalformedExtractionDiagnostics() {
    }

    @SuppressWarnings("unchecked")
    public static Predicate<ItemStack> itemStackOnlyFilter(InvManipulationBehaviour behaviour, IItemHandler inventory,
                                                           Predicate<ItemStack> filter) {
        Predicate<Object> safeFilter = candidate -> {
            if (!(candidate instanceof ItemStack stack)) {
                logMalformedExtraction(behaviour, inventory, candidate, null);
                return false;
            }
            return filter.test(stack);
        };
        return (Predicate<ItemStack>) (Predicate<?>) safeFilter;
    }

    public static boolean isComponentMapCast(ClassCastException exception) {
        String message = exception.getMessage();
        return message != null
                && message.contains("cannot be cast")
                && (message.contains("PatchedDataComponentMap") || message.contains("DataComponentMap"))
                && message.contains("ItemStack");
    }

    public static void logMalformedExtraction(InvManipulationBehaviour behaviour, IItemHandler inventory,
                                              Object candidate, ClassCastException exception) {
        Level level = behaviour.getWorld();
        BlockPos pos = behaviour.getPos();
        String candidateClass = candidate == null ? candidateClassFrom(exception) : candidate.getClass().getName();
        String handlerClass = inventory == null ? "null" : inventory.getClass().getName();
        DiagnosticKey key = new DiagnosticKey(pos.immutable(), handlerClass, candidateClass);
        long gameTime = level == null ? -1L : level.getGameTime();
        boolean fullDiagnostics = fullDiagnosticsEnabled();
        DiagnosticState state = DIAGNOSTIC_STATE.computeIfAbsent(key, ignored -> new DiagnosticState());
        state.seen++;

        if (!state.conciseLogged) {
            state.conciseLogged = true;
            state.lastFullGameTime = gameTime;
            logConciseDiagnostic(key, state.seen, level, exception);
            if (fullDiagnostics) {
                logFullDiagnostic(behaviour, key, state.seen, level, inventory, candidate, exception);
            }
            return;
        }

        if (!fullDiagnostics || gameTime >= 0L && gameTime - state.lastFullGameTime < FULL_DIAGNOSTIC_INTERVAL_TICKS) {
            return;
        }

        state.lastFullGameTime = gameTime;
        logFullDiagnostic(behaviour, key, state.seen, level, inventory, candidate, exception);
    }

    private static boolean fullDiagnosticsEnabled() {
        return Boolean.getBoolean(MALFORMED_EXTRACTION_DIAGNOSTICS_PROPERTY)
                || ColonyLogisticsConfig.DEBUG_LOGGING.get();
    }

    private static void logConciseDiagnostic(DiagnosticKey key, long seen, Level level,
                                             ClassCastException exception) {
        CreateColonyLogistics.LOGGER.warn(
                "Blocked malformed Create extraction candidate: pos={}, dimension={}, handler={}, candidate={}, owner={}, seen={}, full diagnostics with -D{}=true",
                key.pos(),
                dimensionName(level),
                key.handlerClass(),
                key.candidateClass(),
                classOwner(key.candidateClass()),
                seen,
                MALFORMED_EXTRACTION_DIAGNOSTICS_PROPERTY);
        if (exception != null && fullDiagnosticsEnabled()) {
            CreateColonyLogistics.LOGGER.warn("Malformed extraction cast sample", exception);
        }
    }

    private static void logFullDiagnostic(InvManipulationBehaviour behaviour, DiagnosticKey key, long seen, Level level,
                                          IItemHandler inventory, Object candidate, ClassCastException exception) {
        TargetContext target = targetContext(behaviour, level);
        CreateColonyLogistics.LOGGER.warn(
                "Malformed extraction diagnostic: pos={}, blockState={}, dimension={}, gameTime={}, target={}, targetBlockState={}, targetBlockEntity={}, face={}, handler={}, handlerOwner={}, handlerText={}, handlerTraits={}, candidate={}, candidateOwner={}, candidateText={}, adjacent={}, delegates={}, seen={}",
                key.pos(),
                blockState(level, key.pos()),
                dimensionName(level),
                level == null ? -1L : level.getGameTime(),
                target.pos(),
                target.blockState(),
                target.blockEntityClass(),
                target.face(),
                key.handlerClass(),
                classOwner(key.handlerClass()),
                safeText(inventory),
                handlerTraits(inventory, target),
                key.candidateClass(),
                classOwner(key.candidateClass()),
                safeText(candidate),
                adjacentStates(level, key.pos()),
                delegateSummary(inventory, 0),
                seen,
                exception == null ? new Throwable("Malformed extraction candidate stack sample") : exception);
    }

    private static String candidateClassFrom(ClassCastException exception) {
        if (exception == null || exception.getMessage() == null) {
            return "unknown";
        }
        String message = exception.getMessage();
        int classStart = message.indexOf("class ");
        int classEnd = message.indexOf(" cannot be cast");
        if (classStart >= 0 && classEnd > classStart) {
            return message.substring(classStart + "class ".length(), classEnd).trim();
        }
        return "unknown";
    }

    private static String dimensionName(Level level) {
        return level == null ? "unknown" : level.dimension().location().toString();
    }

    private static String blockState(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return "unavailable";
        }
        return String.valueOf(level.getBlockState(pos));
    }

    private static String adjacentStates(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return "unavailable";
        }
        StringBuilder out = new StringBuilder();
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            out.append(direction.getName()).append('=');
            if (level.isLoaded(adjacent)) {
                out.append(adjacent).append(':').append(level.getBlockState(adjacent));
            } else {
                out.append(adjacent).append(":unloaded");
            }
            out.append("; ");
        }
        return truncate(out.toString());
    }

    private static TargetContext targetContext(InvManipulationBehaviour behaviour, Level level) {
        Object face = invokeNoArg(behaviour, "getTarget");
        Object opposite = invokeNoArg(face, "getOpposite");
        Object targetPosObject = invokeNoArg(opposite, "getPos");
        BlockPos targetPos = targetPosObject instanceof BlockPos blockPos ? blockPos : null;
        String blockState = blockState(level, targetPos);
        String blockEntityClass = "unavailable";
        if (level != null && targetPos != null && level.isLoaded(targetPos)) {
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            blockEntityClass = blockEntity == null ? "none" : blockEntity.getClass().getName();
        }
        return new TargetContext(targetPos, blockState, blockEntityClass, safeText(face));
    }

    private static String handlerTraits(IItemHandler inventory, TargetContext target) {
        String handlerClass = inventory == null ? "" : inventory.getClass().getName().toLowerCase(Locale.ROOT);
        String targetState = target.blockState().toLowerCase(Locale.ROOT);
        String targetEntity = target.blockEntityClass().toLowerCase(Locale.ROOT);
        return "createVault=" + (handlerClass.contains("vault") || targetState.contains("create:item_vault")
                || targetEntity.contains("vault"))
                + ", wrapperOrDelegate=" + looksWrapped(inventory)
                + ", inferredOwner=" + classOwner(inventory == null ? "null" : inventory.getClass().getName());
    }

    private static boolean looksWrapped(Object object) {
        if (object == null) {
            return false;
        }
        String className = object.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("wrapper") || className.contains("delegate") || className.contains("bridge")) {
            return true;
        }
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (name.contains("delegate") || name.contains("wrapped") || name.contains("handler")) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String delegateSummary(Object object, int depth) {
        if (object == null || depth > 1) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("delegate") && !name.contains("wrapped") && !name.contains("handler")
                        && !name.contains("storage") && !name.contains("inventory")) {
                    continue;
                }
                Object value = fieldValue(field, object);
                if (value == null) {
                    continue;
                }
                out.append(field.getName()).append('=').append(value.getClass().getName())
                        .append('(').append(classOwner(value.getClass().getName())).append("); ");
                if (depth == 0) {
                    String nested = delegateSummary(value, depth + 1);
                    if (!"none".equals(nested)) {
                        out.append("nested[").append(nested).append("]; ");
                    }
                }
            }
            type = type.getSuperclass();
        }
        return out.length() == 0 ? "none" : truncate(out.toString());
    }

    private static Object fieldValue(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (RuntimeException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object owner, String methodName) {
        if (owner == null) {
            return null;
        }
        Class<?> type = owner.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String classOwner(String className) {
        if (className == null || className.equals("null") || className.equals("unknown")) {
            return "unknown";
        }
        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.startsWith("com.createcolonylogistics")) {
            return "Create Colony Logistics";
        }
        if (lower.startsWith("com.simibubi.create") || lower.startsWith("com.jozufozu.flywheel")
                || lower.startsWith("net.createmod")) {
            return "Create";
        }
        if (lower.startsWith("com.minecolonies")) {
            return "MineColonies";
        }
        if (lower.startsWith("net.p3pp3rf1y.sophisticated")) {
            return "Sophisticated Storage/Backpacks";
        }
        if (lower.contains("fabric")) {
            return "Fabric bridge/API";
        }
        if (lower.startsWith("net.neoforged")) {
            return "NeoForge";
        }
        if (lower.contains("create")) {
            return "Create addon";
        }
        if (lower.startsWith("net.minecraft")) {
            return "Minecraft";
        }
        return "unknown";
    }

    private static String safeText(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return truncate(String.valueOf(value));
        } catch (RuntimeException exception) {
            return "<toString failed: " + exception.getClass().getName() + ">";
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "null";
        }
        String normalized = text.replaceAll("\\s+", " ");
        return normalized.length() <= TEXT_LIMIT ? normalized : normalized.substring(0, TEXT_LIMIT) + "...";
    }

    private record DiagnosticKey(BlockPos pos, String handlerClass, String candidateClass) {
    }

    private static final class DiagnosticState {
        private boolean conciseLogged;
        private long lastFullGameTime = Long.MIN_VALUE / 2L;
        private long seen;
    }

    private record TargetContext(BlockPos pos, String blockState, String blockEntityClass, String face) {
    }
}
