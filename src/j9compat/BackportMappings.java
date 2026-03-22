package j9compat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

final class BackportMappings {

    private BackportMappings() {}

    static BackportMethod find(Class<?> owner, String name, Class<?>... params) {
        if (owner == null || name == null) {
            return null;
        }
        try {
            if (owner == java.util.List.class) {
                if ("of".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "listOf", params);
                }
                if ("copyOf".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "listCopyOf", params);
                }
            }
            if (owner == java.util.Set.class) {
                if ("of".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "setOf", params);
                }
                if ("copyOf".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "setCopyOf", params);
                }
            }
            if (owner == java.util.Map.class) {
                if ("of".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "mapOf", params);
                }
                if ("ofEntries".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "mapOfEntries", params);
                }
                if ("entry".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "mapEntry", params);
                }
                if ("copyOf".equals(name)) {
                    return BackportMethod.staticMethod(CollectionBackport.class,
                            "mapCopyOf", params);
                }
            }
            if (owner == Stream.class) {
                if ("takeWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(StreamBackport.class,
                            "takeWhile", Stream.class, params);
                }
                if ("dropWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(StreamBackport.class,
                            "dropWhile", Stream.class, params);
                }
                if ("ofNullable".equals(name)) {
                    return BackportMethod.staticMethod(StreamBackport.class,
                            "ofNullable", params);
                }
                if ("iterate".equals(name)) {
                    return BackportMethod.staticMethod(StreamBackport.class,
                            "iterate", params);
                }
            }
            if (owner == IntStream.class) {
                if ("takeWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(IntStreamBackport.class,
                            "takeWhile", IntStream.class, params);
                }
                if ("dropWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(IntStreamBackport.class,
                            "dropWhile", IntStream.class, params);
                }
                if ("iterate".equals(name)) {
                    return BackportMethod.staticMethod(IntStreamBackport.class,
                            "iterate", params);
                }
            }
            if (owner == LongStream.class) {
                if ("takeWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(LongStreamBackport.class,
                            "takeWhile", LongStream.class, params);
                }
                if ("dropWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(LongStreamBackport.class,
                            "dropWhile", LongStream.class, params);
                }
                if ("iterate".equals(name)) {
                    return BackportMethod.staticMethod(LongStreamBackport.class,
                            "iterate", params);
                }
            }
            if (owner == DoubleStream.class) {
                if ("takeWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(DoubleStreamBackport.class,
                            "takeWhile", DoubleStream.class, params);
                }
                if ("dropWhile".equals(name)) {
                    return BackportMethod.instanceToStatic(DoubleStreamBackport.class,
                            "dropWhile", DoubleStream.class, params);
                }
                if ("iterate".equals(name)) {
                    return BackportMethod.staticMethod(DoubleStreamBackport.class,
                            "iterate", params);
                }
            }
            if (owner == Collectors.class) {
                if ("filtering".equals(name)) {
                    return BackportMethod.staticMethod(CollectorsBackport.class,
                            "filtering", params);
                }
                if ("flatMapping".equals(name)) {
                    return BackportMethod.staticMethod(CollectorsBackport.class,
                            "flatMapping", params);
                }
                if ("toUnmodifiableList".equals(name)) {
                    return BackportMethod.staticMethod(CollectorsBackport.class,
                            "toUnmodifiableList", params);
                }
                if ("toUnmodifiableSet".equals(name)) {
                    return BackportMethod.staticMethod(CollectorsBackport.class,
                            "toUnmodifiableSet", params);
                }
                if ("toUnmodifiableMap".equals(name)) {
                    return BackportMethod.staticMethod(CollectorsBackport.class,
                            "toUnmodifiableMap", params);
                }
            }
            if (owner == Optional.class) {
                if ("ifPresentOrElse".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalBackport.class,
                            "ifPresentOrElse", Optional.class, params);
                }
                if ("or".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalBackport.class,
                            "or", Optional.class, params);
                }
                if ("stream".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalBackport.class,
                            "stream", Optional.class, params);
                }
                if ("orElseThrow".equals(name) && params.length == 0) {
                    return BackportMethod.instanceToStatic(OptionalBackport.class,
                            "orElseThrow", Optional.class, params);
                }
            }
            if (owner == OptionalInt.class) {
                if ("ifPresentOrElse".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalIntBackport.class,
                            "ifPresentOrElse", OptionalInt.class, params);
                }
                if ("stream".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalIntBackport.class,
                            "stream", OptionalInt.class, params);
                }
                if ("orElseThrow".equals(name) && params.length == 0) {
                    return BackportMethod.instanceToStatic(OptionalIntBackport.class,
                            "orElseThrow", OptionalInt.class, params);
                }
            }
            if (owner == OptionalLong.class) {
                if ("ifPresentOrElse".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalLongBackport.class,
                            "ifPresentOrElse", OptionalLong.class, params);
                }
                if ("stream".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalLongBackport.class,
                            "stream", OptionalLong.class, params);
                }
                if ("orElseThrow".equals(name) && params.length == 0) {
                    return BackportMethod.instanceToStatic(OptionalLongBackport.class,
                            "orElseThrow", OptionalLong.class, params);
                }
            }
            if (owner == OptionalDouble.class) {
                if ("ifPresentOrElse".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalDoubleBackport.class,
                            "ifPresentOrElse", OptionalDouble.class, params);
                }
                if ("stream".equals(name)) {
                    return BackportMethod.instanceToStatic(OptionalDoubleBackport.class,
                            "stream", OptionalDouble.class, params);
                }
                if ("orElseThrow".equals(name) && params.length == 0) {
                    return BackportMethod.instanceToStatic(OptionalDoubleBackport.class,
                            "orElseThrow", OptionalDouble.class, params);
                }
            }
            if (isInputStreamOwner(owner)) {
                if ("transferTo".equals(name)) {
                    return BackportMethod.instanceToStatic(IOBackport.class,
                            "transferTo", InputStream.class, params);
                }
                if ("readAllBytes".equals(name)) {
                    return BackportMethod.instanceToStatic(IOBackport.class,
                            "readAllBytes", InputStream.class, params);
                }
                if ("readNBytes".equals(name)) {
                    return BackportMethod.instanceToStatic(IOBackport.class,
                            "readNBytes", InputStream.class, params);
                }
            }
            if (owner == java.util.Objects.class) {
                if ("requireNonNullElse".equals(name)) {
                    return BackportMethod.staticMethod(ObjectsBackport.class,
                            "requireNonNullElse", params);
                }
                if ("requireNonNullElseGet".equals(name)) {
                    return BackportMethod.staticMethod(ObjectsBackport.class,
                            "requireNonNullElseGet", params);
                }
                if ("checkIndex".equals(name)) {
                    return BackportMethod.staticMethod(ObjectsBackport.class,
                            "checkIndex", params);
                }
                if ("checkFromToIndex".equals(name)) {
                    return BackportMethod.staticMethod(ObjectsBackport.class,
                            "checkFromToIndex", params);
                }
                if ("checkFromIndexSize".equals(name)) {
                    return BackportMethod.staticMethod(ObjectsBackport.class,
                            "checkFromIndexSize", params);
                }
            }
            if (owner == CompletableFuture.class) {
                if ("orTimeout".equals(name)) {
                    return BackportMethod.instanceToStatic(CompletableFutureBackport.class,
                            "orTimeout", CompletableFuture.class, params);
                }
                if ("completeOnTimeout".equals(name)) {
                    return BackportMethod.instanceToStatic(CompletableFutureBackport.class,
                            "completeOnTimeout", CompletableFuture.class, params);
                }
                if ("failedFuture".equals(name)) {
                    return BackportMethod.staticMethod(CompletableFutureBackport.class,
                            "failedFuture", params);
                }
                if ("completedStage".equals(name)) {
                    return BackportMethod.staticMethod(CompletableFutureBackport.class,
                            "completedStage", params);
                }
                if ("failedStage".equals(name)) {
                    return BackportMethod.staticMethod(CompletableFutureBackport.class,
                            "failedStage", params);
                }
                if ("minimalCompletionStage".equals(name)) {
                    return BackportMethod.instanceToStatic(CompletableFutureBackport.class,
                            "minimalCompletionStage", CompletableFuture.class, params);
                }
                if ("newIncompleteFuture".equals(name)) {
                    return BackportMethod.instanceToStatic(CompletableFutureBackport.class,
                            "newIncompleteFuture", CompletableFuture.class, params);
                }
                if ("copy".equals(name)) {
                    return BackportMethod.instanceToStatic(CompletableFutureBackport.class,
                            "copy", CompletableFuture.class, params);
                }
            }
            if (owner == Process.class) {
                if ("toHandle".equals(name)) {
                    return BackportMethod.instanceToStatic(ProcessHandle.class,
                            "fromProcess", Process.class, params);
                }
            }
            if (owner == Class.class) {
                if ("getModule".equals(name)) {
                    return BackportMethod.instanceToStatic(ModuleBackport.class,
                            "getModule", Class.class, params);
                }
            }
        } catch (NoSuchMethodException ignored) {
            return null;
        }
        return null;
    }

    private static boolean isInputStreamOwner(Class<?> owner) {
        return owner != null && InputStream.class.isAssignableFrom(owner);
    }

    static final class BackportMethod {
        final Method method;
        final boolean needsReceiver;

        private BackportMethod(Method method, boolean needsReceiver) {
            this.method = method;
            this.needsReceiver = needsReceiver;
        }

        static BackportMethod staticMethod(Class<?> owner, String name, Class<?>... params)
                throws NoSuchMethodException {
            return new BackportMethod(owner.getMethod(name, params), false);
        }

        static BackportMethod instanceToStatic(Class<?> owner, String name,
                                               Class<?> receiverType,
                                               Class<?>... params)
                throws NoSuchMethodException {
            Class<?>[] remapped = new Class<?>[params.length + 1];
            remapped[0] = receiverType;
            System.arraycopy(params, 0, remapped, 1, params.length);
            return new BackportMethod(owner.getMethod(name, remapped), true);
        }
    }
}
