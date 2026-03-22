package j9compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import sun.misc.Unsafe;

/**
 * Java 8-compatible backport of {@link java.lang.invoke.VarHandle}.
 */
public final class VarHandle {

    public enum AccessMode {
        GET,
        SET,
        GET_VOLATILE,
        SET_VOLATILE,
        GET_OPAQUE,
        SET_OPAQUE,
        GET_ACQUIRE,
        SET_RELEASE,
        COMPARE_AND_SET,
        COMPARE_AND_EXCHANGE,
        COMPARE_AND_EXCHANGE_ACQUIRE,
        COMPARE_AND_EXCHANGE_RELEASE,
        WEAK_COMPARE_AND_SET_PLAIN,
        WEAK_COMPARE_AND_SET,
        WEAK_COMPARE_AND_SET_ACQUIRE,
        WEAK_COMPARE_AND_SET_RELEASE,
        GET_AND_SET,
        GET_AND_SET_ACQUIRE,
        GET_AND_SET_RELEASE,
        GET_AND_ADD,
        GET_AND_ADD_ACQUIRE,
        GET_AND_ADD_RELEASE,
        GET_AND_BITWISE_OR,
        GET_AND_BITWISE_OR_ACQUIRE,
        GET_AND_BITWISE_OR_RELEASE,
        GET_AND_BITWISE_AND,
        GET_AND_BITWISE_AND_ACQUIRE,
        GET_AND_BITWISE_AND_RELEASE,
        GET_AND_BITWISE_XOR,
        GET_AND_BITWISE_XOR_ACQUIRE,
        GET_AND_BITWISE_XOR_RELEASE
    }

    private final Class<?> varType;
    private final List<Class<?>> coordinateTypes;
    private final Access access;
    private final boolean exact;
    private static final UnsafeAccess UNSAFE_ACCESS = UnsafeAccess.create();
    private static final Object FENCE_LOCK = new Object();
    private static volatile int FENCE;

    private VarHandle(Class<?> varType, List<Class<?>> coordinateTypes, Access access, boolean exact) {
        this.varType = varType;
        this.coordinateTypes = Collections.unmodifiableList(new ArrayList<Class<?>>(coordinateTypes));
        this.access = access;
        this.exact = exact;
    }

    static VarHandle forField(Field field) {
        if (field == null) {
            throw new NullPointerException("field");
        }
        boolean isStatic = java.lang.reflect.Modifier.isStatic(field.getModifiers());
        field.setAccessible(true);
        if (isStatic) {
            return new VarHandle(field.getType(), Collections.<Class<?>>emptyList(),
                    new FieldAccess(field, null, true), false);
        }
        List<Class<?>> coords = new ArrayList<Class<?>>();
        coords.add(field.getDeclaringClass());
        return new VarHandle(field.getType(), coords, new FieldAccess(field, null, false), false);
    }

    static VarHandle forArray(Class<?> arrayType) {
        if (arrayType == null || !arrayType.isArray()) {
            throw new IllegalArgumentException("arrayType");
        }
        List<Class<?>> coords = new ArrayList<Class<?>>();
        coords.add(arrayType);
        coords.add(int.class);
        return new VarHandle(arrayType.getComponentType(), coords, new ArrayAccess(arrayType), false);
    }

    public Class<?> varType() {
        return varType;
    }

    public List<Class<?>> coordinateTypes() {
        return coordinateTypes;
    }

    public boolean hasInvokeExactBehavior() {
        return exact;
    }

    public VarHandle withInvokeExactBehavior() {
        return new VarHandle(varType, coordinateTypes, access, true);
    }

    public VarHandle withInvokeBehavior() {
        return new VarHandle(varType, coordinateTypes, access, false);
    }

    public Object get(Object... args) {
        return access.get(args);
    }

    public void set(Object... args) {
        access.set(args);
    }

    public Object getVolatile(Object... args) {
        return access.get(args);
    }

    public void setVolatile(Object... args) {
        access.set(args);
    }

    public Object getOpaque(Object... args) {
        return access.get(args);
    }

    public void setOpaque(Object... args) {
        access.set(args);
    }

    public Object getAcquire(Object... args) {
        return access.get(args);
    }

    public void setRelease(Object... args) {
        access.set(args);
    }

    public boolean compareAndSet(Object... args) {
        return access.compareAndSet(args);
    }

    public Object compareAndExchange(Object... args) {
        return access.compareAndExchange(args);
    }

    public Object compareAndExchangeAcquire(Object... args) {
        return access.compareAndExchange(args);
    }

    public Object compareAndExchangeRelease(Object... args) {
        return access.compareAndExchange(args);
    }

    public boolean weakCompareAndSetPlain(Object... args) {
        return access.compareAndSet(args);
    }

    public boolean weakCompareAndSet(Object... args) {
        return access.compareAndSet(args);
    }

    public boolean weakCompareAndSetAcquire(Object... args) {
        return access.compareAndSet(args);
    }

    public boolean weakCompareAndSetRelease(Object... args) {
        return access.compareAndSet(args);
    }

    public Object getAndSet(Object... args) {
        return access.getAndSet(args);
    }

    public Object getAndSetAcquire(Object... args) {
        return access.getAndSet(args);
    }

    public Object getAndSetRelease(Object... args) {
        return access.getAndSet(args);
    }

    public Object getAndAdd(Object... args) {
        return access.getAndAdd(args);
    }

    public Object getAndAddAcquire(Object... args) {
        return access.getAndAdd(args);
    }

    public Object getAndAddRelease(Object... args) {
        return access.getAndAdd(args);
    }

    public Object getAndBitwiseOr(Object... args) {
        return access.getAndBitwiseOr(args);
    }

    public Object getAndBitwiseOrAcquire(Object... args) {
        return access.getAndBitwiseOr(args);
    }

    public Object getAndBitwiseOrRelease(Object... args) {
        return access.getAndBitwiseOr(args);
    }

    public Object getAndBitwiseAnd(Object... args) {
        return access.getAndBitwiseAnd(args);
    }

    public Object getAndBitwiseAndAcquire(Object... args) {
        return access.getAndBitwiseAnd(args);
    }

    public Object getAndBitwiseAndRelease(Object... args) {
        return access.getAndBitwiseAnd(args);
    }

    public Object getAndBitwiseXor(Object... args) {
        return access.getAndBitwiseXor(args);
    }

    public Object getAndBitwiseXorAcquire(Object... args) {
        return access.getAndBitwiseXor(args);
    }

    public Object getAndBitwiseXorRelease(Object... args) {
        return access.getAndBitwiseXor(args);
    }

    public MethodType accessModeType(AccessMode mode) {
        return accessModeType(mode, varType, coordinateTypes);
    }

    public boolean isAccessModeSupported(AccessMode mode) {
        return mode != null;
    }

    public MethodHandle toMethodHandle(AccessMode mode) {
        MethodType type = accessModeType(mode);
        MethodHandle handle;
        switch (mode) {
            case GET:
            case GET_ACQUIRE:
            case GET_OPAQUE:
            case GET_VOLATILE:
                handle = lookupHandle("invokeGet", Object[].class); break;
            case SET:
            case SET_OPAQUE:
            case SET_RELEASE:
            case SET_VOLATILE:
                handle = lookupHandle("invokeSet", Object[].class); break;
            case COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET_ACQUIRE:
            case WEAK_COMPARE_AND_SET_PLAIN:
            case WEAK_COMPARE_AND_SET_RELEASE:
                handle = lookupHandle("invokeCompareAndSet", Object[].class); break;
            case COMPARE_AND_EXCHANGE:
            case COMPARE_AND_EXCHANGE_ACQUIRE:
            case COMPARE_AND_EXCHANGE_RELEASE:
                handle = lookupHandle("invokeCompareAndExchange", Object[].class); break;
            case GET_AND_SET:
            case GET_AND_SET_ACQUIRE:
            case GET_AND_SET_RELEASE:
                handle = lookupHandle("invokeGetAndSet", Object[].class); break;
            case GET_AND_ADD:
            case GET_AND_ADD_ACQUIRE:
            case GET_AND_ADD_RELEASE:
                handle = lookupHandle("invokeGetAndAdd", Object[].class); break;
            case GET_AND_BITWISE_OR:
            case GET_AND_BITWISE_OR_ACQUIRE:
            case GET_AND_BITWISE_OR_RELEASE:
                handle = lookupHandle("invokeGetAndBitwiseOr", Object[].class); break;
            case GET_AND_BITWISE_AND:
            case GET_AND_BITWISE_AND_ACQUIRE:
            case GET_AND_BITWISE_AND_RELEASE:
                handle = lookupHandle("invokeGetAndBitwiseAnd", Object[].class); break;
            case GET_AND_BITWISE_XOR:
            case GET_AND_BITWISE_XOR_ACQUIRE:
            case GET_AND_BITWISE_XOR_RELEASE:
                handle = lookupHandle("invokeGetAndBitwiseXor", Object[].class); break;
            default:
                throw new UnsupportedOperationException("Unsupported access mode: " + mode);
        }
        MethodHandle adapted = handle.asCollector(Object[].class, coordinateTypes.size()
                + extraArgs(mode));
        return adapted.asType(type);
    }

    public static void fullFence() {
        if (UNSAFE_ACCESS != null && UNSAFE_ACCESS.fullFence != null) {
            UNSAFE_ACCESS.fullFence();
            return;
        }
        synchronized (FENCE_LOCK) {
            FENCE++;
        }
    }

    public static void acquireFence() {
        if (UNSAFE_ACCESS != null && UNSAFE_ACCESS.loadFence != null) {
            UNSAFE_ACCESS.loadFence();
            return;
        }
        int ignore = FENCE;
        if (ignore != 0) {
            // no-op
        }
    }

    public static void releaseFence() {
        if (UNSAFE_ACCESS != null && UNSAFE_ACCESS.storeFence != null) {
            UNSAFE_ACCESS.storeFence();
            return;
        }
        FENCE = 0;
    }

    public static void loadLoadFence() {
        acquireFence();
    }

    public static void storeStoreFence() {
        releaseFence();
    }

    private MethodHandle lookupHandle(String name, Class<?> argType) {
        try {
            return MethodHandles.lookup().findVirtual(VarHandle.class, name,
                    MethodType.methodType(Object.class, argType)).bindTo(this);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object invokeGet(Object[] args) {
        return get(args);
    }

    private Object invokeSet(Object[] args) {
        set(args);
        return null;
    }

    private Object invokeCompareAndSet(Object[] args) {
        return Boolean.valueOf(compareAndSet(args));
    }

    private Object invokeCompareAndExchange(Object[] args) {
        return compareAndExchange(args);
    }

    private Object invokeGetAndSet(Object[] args) {
        return getAndSet(args);
    }

    private Object invokeGetAndAdd(Object[] args) {
        return getAndAdd(args);
    }

    private Object invokeGetAndBitwiseOr(Object[] args) {
        return getAndBitwiseOr(args);
    }

    private Object invokeGetAndBitwiseAnd(Object[] args) {
        return getAndBitwiseAnd(args);
    }

    private Object invokeGetAndBitwiseXor(Object[] args) {
        return getAndBitwiseXor(args);
    }

    private static MethodType accessModeType(AccessMode mode,
                                             Class<?> varType,
                                             List<Class<?>> coordinates) {
        List<Class<?>> params = new ArrayList<Class<?>>(coordinates);
        switch (mode) {
            case GET:
            case GET_VOLATILE:
            case GET_OPAQUE:
            case GET_ACQUIRE:
                return MethodType.methodType(varType, params.toArray(new Class<?>[0]));
            case SET:
            case SET_VOLATILE:
            case SET_OPAQUE:
            case SET_RELEASE:
                params.add(varType);
                return MethodType.methodType(void.class, params.toArray(new Class<?>[0]));
            case COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET_ACQUIRE:
            case WEAK_COMPARE_AND_SET_PLAIN:
            case WEAK_COMPARE_AND_SET_RELEASE:
                params.add(varType);
                params.add(varType);
                return MethodType.methodType(boolean.class, params.toArray(new Class<?>[0]));
            case COMPARE_AND_EXCHANGE:
            case COMPARE_AND_EXCHANGE_ACQUIRE:
            case COMPARE_AND_EXCHANGE_RELEASE:
                params.add(varType);
                params.add(varType);
                return MethodType.methodType(varType, params.toArray(new Class<?>[0]));
            case GET_AND_SET:
            case GET_AND_SET_ACQUIRE:
            case GET_AND_SET_RELEASE:
            case GET_AND_ADD:
            case GET_AND_ADD_ACQUIRE:
            case GET_AND_ADD_RELEASE:
            case GET_AND_BITWISE_OR:
            case GET_AND_BITWISE_OR_ACQUIRE:
            case GET_AND_BITWISE_OR_RELEASE:
            case GET_AND_BITWISE_AND:
            case GET_AND_BITWISE_AND_ACQUIRE:
            case GET_AND_BITWISE_AND_RELEASE:
            case GET_AND_BITWISE_XOR:
            case GET_AND_BITWISE_XOR_ACQUIRE:
            case GET_AND_BITWISE_XOR_RELEASE:
                params.add(varType);
                return MethodType.methodType(varType, params.toArray(new Class<?>[0]));
            default:
                throw new UnsupportedOperationException("Unsupported access mode: " + mode);
        }
    }

    private static int extraArgs(AccessMode mode) {
        switch (mode) {
            case SET:
            case SET_VOLATILE:
            case SET_OPAQUE:
            case SET_RELEASE:
                return 1;
            case COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET:
            case WEAK_COMPARE_AND_SET_ACQUIRE:
            case WEAK_COMPARE_AND_SET_PLAIN:
            case WEAK_COMPARE_AND_SET_RELEASE:
                return 2;
            case COMPARE_AND_EXCHANGE:
            case COMPARE_AND_EXCHANGE_ACQUIRE:
            case COMPARE_AND_EXCHANGE_RELEASE:
                return 2;
            case GET_AND_SET:
            case GET_AND_SET_ACQUIRE:
            case GET_AND_SET_RELEASE:
            case GET_AND_ADD:
            case GET_AND_ADD_ACQUIRE:
            case GET_AND_ADD_RELEASE:
            case GET_AND_BITWISE_OR:
            case GET_AND_BITWISE_OR_ACQUIRE:
            case GET_AND_BITWISE_OR_RELEASE:
            case GET_AND_BITWISE_AND:
            case GET_AND_BITWISE_AND_ACQUIRE:
            case GET_AND_BITWISE_AND_RELEASE:
            case GET_AND_BITWISE_XOR:
            case GET_AND_BITWISE_XOR_ACQUIRE:
            case GET_AND_BITWISE_XOR_RELEASE:
                return 1;
            default:
                return 0;
        }
    }

    private interface Access {
        Object get(Object[] args);
        void set(Object[] args);
        boolean compareAndSet(Object[] args);
        Object compareAndExchange(Object[] args);
        Object getAndSet(Object[] args);
        Object getAndAdd(Object[] args);
        Object getAndBitwiseOr(Object[] args);
        Object getAndBitwiseAnd(Object[] args);
        Object getAndBitwiseXor(Object[] args);
    }

    private static final class FieldAccess implements Access {
        private final Field field;
        private final Object staticBase;
        private final boolean isStatic;
        private final Object lock = new Object();
        private final long offset;
        private final Class<?> type;
        private final boolean useUnsafe;
        private final boolean casSupported;

        FieldAccess(Field field, Object staticBase, boolean isStatic) {
            this.field = field;
            this.isStatic = isStatic;
            this.type = field.getType();
            if (UNSAFE_ACCESS != null && UNSAFE_ACCESS.available) {
                Object resolvedBase = staticBase;
                if (isStatic) {
                    this.offset = UNSAFE_ACCESS.unsafe.staticFieldOffset(field);
                    resolvedBase = UNSAFE_ACCESS.unsafe.staticFieldBase(field);
                } else {
                    this.offset = UNSAFE_ACCESS.unsafe.objectFieldOffset(field);
                }
                this.staticBase = resolvedBase;
                this.useUnsafe = UNSAFE_ACCESS.supportsType(type);
                this.casSupported = UNSAFE_ACCESS.casSupported(type);
            } else {
                this.offset = -1;
                this.useUnsafe = false;
                this.casSupported = false;
                this.staticBase = staticBase;
            }
        }

        private Object receiver(Object[] args) {
            if (isStatic) {
                return staticBase;
            }
            if (args.length == 0 || args[0] == null) {
                throw new NullPointerException("receiver");
            }
            return args[0];
        }

        private int valueIndex() {
            return isStatic ? 0 : 1;
        }

        private Object[] setArgs(Object receiver, Object value) {
            if (isStatic) {
                return new Object[]{value};
            }
            return new Object[]{receiver, value};
        }

        @Override
        public Object get(Object[] args) {
            Object target = receiver(args);
            if (useUnsafe) {
                return UNSAFE_ACCESS.get(valueBase(target), offset, type);
            }
            synchronized (lock) {
                try {
                    return field.get(target);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public void set(Object[] args) {
            int index = valueIndex();
            if (args.length <= index) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = receiver(args);
            Object value = args[index];
            if (useUnsafe) {
                UNSAFE_ACCESS.put(valueBase(target), offset, type, value);
                return;
            }
            synchronized (lock) {
                try {
                    field.set(target, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public boolean compareAndSet(Object[] args) {
            int index = valueIndex();
            if (args.length <= index + 1) {
                throw new IllegalArgumentException("missing compare arguments");
            }
            Object target = receiver(args);
            Object expected = args[index];
            Object update = args[index + 1];
            if (useUnsafe && casSupported) {
                return UNSAFE_ACCESS.compareAndSet(valueBase(target), offset, type, expected, update);
            }
            synchronized (lock) {
                Object current = get(args);
                if (equalsValue(current, expected)) {
                    set(setArgs(target, update));
                    return true;
                }
                return false;
            }
        }

        @Override
        public Object compareAndExchange(Object[] args) {
            int index = valueIndex();
            if (args.length <= index + 1) {
                throw new IllegalArgumentException("missing compare arguments");
            }
            Object target = receiver(args);
            Object expected = args[index];
            Object update = args[index + 1];
            if (useUnsafe && casSupported) {
                Object current;
                do {
                    current = UNSAFE_ACCESS.get(valueBase(target), offset, type);
                    if (!equalsValue(current, expected)) {
                        return current;
                    }
                } while (!UNSAFE_ACCESS.compareAndSet(valueBase(target), offset, type, expected, update));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                if (equalsValue(current, expected)) {
                    set(setArgs(target, update));
                }
                return current;
            }
        }

        @Override
        public Object getAndSet(Object[] args) {
            int index = valueIndex();
            if (args.length <= index) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = receiver(args);
            Object update = args[index];
            if (useUnsafe && casSupported) {
                Object current;
                do {
                    current = UNSAFE_ACCESS.get(valueBase(target), offset, type);
                } while (!UNSAFE_ACCESS.compareAndSet(valueBase(target), offset, type, current, update));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                set(setArgs(target, update));
                return current;
            }
        }

        @Override
        public Object getAndAdd(Object[] args) {
            int index = valueIndex();
            if (args.length <= index) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = receiver(args);
            Number delta = toNumber(args[index]);
            if (useUnsafe && casSupported) {
                Object current;
                Object next;
                do {
                    current = UNSAFE_ACCESS.get(valueBase(target), offset, type);
                    next = addNumber(current, delta);
                } while (!UNSAFE_ACCESS.compareAndSet(valueBase(target), offset, type, current, next));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                Object next = addNumber(current, delta);
                set(setArgs(target, next));
                return current;
            }
        }

        @Override
        public Object getAndBitwiseOr(Object[] args) {
            return updateBitwise(args, BitwiseOp.OR);
        }

        @Override
        public Object getAndBitwiseAnd(Object[] args) {
            return updateBitwise(args, BitwiseOp.AND);
        }

        @Override
        public Object getAndBitwiseXor(Object[] args) {
            return updateBitwise(args, BitwiseOp.XOR);
        }

        private Object updateBitwise(Object[] args, BitwiseOp op) {
            int index = valueIndex();
            if (args.length <= index) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = receiver(args);
            Number delta = toNumber(args[index]);
            if (useUnsafe && casSupported) {
                Object current;
                Object next;
                do {
                    current = UNSAFE_ACCESS.get(valueBase(target), offset, type);
                    next = applyBitwise(current, delta, op);
                } while (!UNSAFE_ACCESS.compareAndSet(valueBase(target), offset, type, current, next));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                Object next = applyBitwise(current, delta, op);
                set(setArgs(target, next));
                return current;
            }
        }

        private Object valueBase(Object target) {
            return isStatic ? (staticBase != null ? staticBase : target) : target;
        }
    }

    private static final class ArrayAccess implements Access {
        private final Class<?> arrayType;
        private final Object lock = new Object();
        private final Class<?> componentType;
        private final long baseOffset;
        private final int indexScale;
        private final boolean useUnsafe;
        private final boolean casSupported;

        ArrayAccess(Class<?> arrayType) {
            this.arrayType = arrayType;
            this.componentType = arrayType.getComponentType();
            if (UNSAFE_ACCESS != null && UNSAFE_ACCESS.available) {
                this.baseOffset = UNSAFE_ACCESS.unsafe.arrayBaseOffset(arrayType);
                this.indexScale = UNSAFE_ACCESS.unsafe.arrayIndexScale(arrayType);
                this.useUnsafe = UNSAFE_ACCESS.supportsType(componentType);
                this.casSupported = UNSAFE_ACCESS.casSupported(componentType);
            } else {
                this.baseOffset = 0;
                this.indexScale = 1;
                this.useUnsafe = false;
                this.casSupported = false;
            }
        }

        private Object array(Object[] args) {
            if (args.length == 0 || args[0] == null) {
                throw new NullPointerException("array");
            }
            return args[0];
        }

        private int index(Object[] args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("missing index");
            }
            return ((Number) args[1]).intValue();
        }

        private int valueIndex() {
            return 2;
        }

        @Override
        public Object get(Object[] args) {
            Object target = array(args);
            int idx = index(args);
            if (useUnsafe) {
                long offset = elementOffset(idx);
                return UNSAFE_ACCESS.get(target, offset, componentType);
            }
            synchronized (lock) {
                return Array.get(target, idx);
            }
        }

        @Override
        public void set(Object[] args) {
            int idx = valueIndex();
            if (args.length <= idx) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = array(args);
            int index = index(args);
            Object value = args[idx];
            if (useUnsafe) {
                long offset = elementOffset(index);
                UNSAFE_ACCESS.put(target, offset, componentType, value);
                return;
            }
            synchronized (lock) {
                Array.set(target, index, value);
            }
        }

        @Override
        public boolean compareAndSet(Object[] args) {
            int idx = valueIndex();
            if (args.length <= idx + 1) {
                throw new IllegalArgumentException("missing compare args");
            }
            Object target = array(args);
            int index = index(args);
            Object expected = args[idx];
            Object update = args[idx + 1];
            if (useUnsafe && casSupported) {
                return UNSAFE_ACCESS.compareAndSet(target, elementOffset(index), componentType, expected, update);
            }
            synchronized (lock) {
                Object current = get(args);
                if (equalsValue(current, expected)) {
                    set(new Object[]{target, index, update});
                    return true;
                }
                return false;
            }
        }

        @Override
        public Object compareAndExchange(Object[] args) {
            int idx = valueIndex();
            if (args.length <= idx + 1) {
                throw new IllegalArgumentException("missing compare args");
            }
            Object target = array(args);
            int index = index(args);
            Object expected = args[idx];
            Object update = args[idx + 1];
            if (useUnsafe && casSupported) {
                Object current;
                do {
                    current = UNSAFE_ACCESS.get(target, elementOffset(index), componentType);
                    if (!equalsValue(current, expected)) {
                        return current;
                    }
                } while (!UNSAFE_ACCESS.compareAndSet(target, elementOffset(index), componentType, expected, update));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                if (equalsValue(current, expected)) {
                    set(new Object[]{target, index, update});
                }
                return current;
            }
        }

        @Override
        public Object getAndSet(Object[] args) {
            int idx = valueIndex();
            if (args.length <= idx) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = array(args);
            int index = index(args);
            Object update = args[idx];
            if (useUnsafe && casSupported) {
                Object current;
                do {
                    current = UNSAFE_ACCESS.get(target, elementOffset(index), componentType);
                } while (!UNSAFE_ACCESS.compareAndSet(target, elementOffset(index), componentType, current, update));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                set(new Object[]{target, index, update});
                return current;
            }
        }

        @Override
        public Object getAndAdd(Object[] args) {
            int idx = valueIndex();
            if (args.length <= idx) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = array(args);
            int index = index(args);
            Number delta = toNumber(args[idx]);
            if (useUnsafe && casSupported) {
                Object current;
                Object next;
                do {
                    current = UNSAFE_ACCESS.get(target, elementOffset(index), componentType);
                    next = addNumber(current, delta);
                } while (!UNSAFE_ACCESS.compareAndSet(target, elementOffset(index), componentType, current, next));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                Object next = addNumber(current, delta);
                set(new Object[]{target, index, next});
                return current;
            }
        }

        @Override
        public Object getAndBitwiseOr(Object[] args) {
            return updateBitwise(args, BitwiseOp.OR);
        }

        @Override
        public Object getAndBitwiseAnd(Object[] args) {
            return updateBitwise(args, BitwiseOp.AND);
        }

        @Override
        public Object getAndBitwiseXor(Object[] args) {
            return updateBitwise(args, BitwiseOp.XOR);
        }

        private Object updateBitwise(Object[] args, BitwiseOp op) {
            int idx = valueIndex();
            if (args.length <= idx) {
                throw new IllegalArgumentException("missing value");
            }
            Object target = array(args);
            int index = index(args);
            Number delta = toNumber(args[idx]);
            if (useUnsafe && casSupported) {
                Object current;
                Object next;
                do {
                    current = UNSAFE_ACCESS.get(target, elementOffset(index), componentType);
                    next = applyBitwise(current, delta, op);
                } while (!UNSAFE_ACCESS.compareAndSet(target, elementOffset(index), componentType, current, next));
                return current;
            }
            synchronized (lock) {
                Object current = get(args);
                Object next = applyBitwise(current, delta, op);
                set(new Object[]{target, index, next});
                return current;
            }
        }

        private long elementOffset(int index) {
            return baseOffset + ((long) index) * indexScale;
        }
    }

    private static final class UnsafeAccess {
        final Unsafe unsafe;
        final boolean available;
        final java.lang.reflect.Method fullFence;
        final java.lang.reflect.Method loadFence;
        final java.lang.reflect.Method storeFence;

        private UnsafeAccess(Unsafe unsafe,
                             java.lang.reflect.Method fullFence,
                             java.lang.reflect.Method loadFence,
                             java.lang.reflect.Method storeFence) {
            this.unsafe = unsafe;
            this.available = unsafe != null;
            this.fullFence = fullFence;
            this.loadFence = loadFence;
            this.storeFence = storeFence;
        }

        static UnsafeAccess create() {
            try {
                java.lang.reflect.Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Unsafe unsafe = (Unsafe) field.get(null);
                java.lang.reflect.Method fullFence = findMethod(unsafe, "fullFence");
                java.lang.reflect.Method loadFence = findMethod(unsafe, "loadFence");
                java.lang.reflect.Method storeFence = findMethod(unsafe, "storeFence");
                return new UnsafeAccess(unsafe, fullFence, loadFence, storeFence);
            } catch (Exception e) {
                return null;
            }
        }

        boolean supportsType(Class<?> type) {
            if (type == null) {
                return false;
            }
            if (!type.isPrimitive()) {
                return true;
            }
            return type == int.class || type == long.class || type == float.class || type == double.class;
        }

        boolean casSupported(Class<?> type) {
            return supportsType(type);
        }

        Object get(Object base, long offset, Class<?> type) {
            if (!type.isPrimitive()) {
                return unsafe.getObjectVolatile(base, offset);
            }
            if (type == int.class) {
                return Integer.valueOf(unsafe.getIntVolatile(base, offset));
            }
            if (type == long.class) {
                return Long.valueOf(unsafe.getLongVolatile(base, offset));
            }
            if (type == float.class) {
                return Float.valueOf(Float.intBitsToFloat(unsafe.getIntVolatile(base, offset)));
            }
            if (type == double.class) {
                return Double.valueOf(Double.longBitsToDouble(unsafe.getLongVolatile(base, offset)));
            }
            return unsafe.getObject(base, offset);
        }

        void put(Object base, long offset, Class<?> type, Object value) {
            if (!type.isPrimitive()) {
                unsafe.putObjectVolatile(base, offset, value);
                return;
            }
            if (type == int.class) {
                unsafe.putIntVolatile(base, offset, ((Number) value).intValue());
                return;
            }
            if (type == long.class) {
                unsafe.putLongVolatile(base, offset, ((Number) value).longValue());
                return;
            }
            if (type == float.class) {
                int bits = Float.floatToRawIntBits(((Number) value).floatValue());
                unsafe.putIntVolatile(base, offset, bits);
                return;
            }
            if (type == double.class) {
                long bits = Double.doubleToRawLongBits(((Number) value).doubleValue());
                unsafe.putLongVolatile(base, offset, bits);
                return;
            }
            unsafe.putObjectVolatile(base, offset, value);
        }

        boolean compareAndSet(Object base, long offset, Class<?> type, Object expected, Object update) {
            if (!type.isPrimitive()) {
                return unsafe.compareAndSwapObject(base, offset, expected, update);
            }
            if (type == int.class) {
                return unsafe.compareAndSwapInt(base, offset,
                        ((Number) expected).intValue(),
                        ((Number) update).intValue());
            }
            if (type == long.class) {
                return unsafe.compareAndSwapLong(base, offset,
                        ((Number) expected).longValue(),
                        ((Number) update).longValue());
            }
            if (type == float.class) {
                int expectedBits = Float.floatToRawIntBits(((Number) expected).floatValue());
                int updateBits = Float.floatToRawIntBits(((Number) update).floatValue());
                return unsafe.compareAndSwapInt(base, offset, expectedBits, updateBits);
            }
            if (type == double.class) {
                long expectedBits = Double.doubleToRawLongBits(((Number) expected).doubleValue());
                long updateBits = Double.doubleToRawLongBits(((Number) update).doubleValue());
                return unsafe.compareAndSwapLong(base, offset, expectedBits, updateBits);
            }
            return unsafe.compareAndSwapObject(base, offset, expected, update);
        }

        void fullFence() {
            invokeFence(fullFence);
        }

        void loadFence() {
            invokeFence(loadFence);
        }

        void storeFence() {
            invokeFence(storeFence);
        }

        private void invokeFence(java.lang.reflect.Method method) {
            if (method == null) {
                return;
            }
            try {
                method.invoke(unsafe);
            } catch (Exception ignored) {
            }
        }

        private static java.lang.reflect.Method findMethod(Unsafe unsafe, String name) {
            try {
                return unsafe.getClass().getMethod(name);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private enum BitwiseOp { OR, AND, XOR }

    private static boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static Number toNumber(Object value) {
        if (!(value instanceof Number)) {
            throw new UnsupportedOperationException("Numeric operation on non-numeric type");
        }
        return (Number) value;
    }

    private static Object addNumber(Object current, Number delta) {
        if (current instanceof Integer) {
            return ((Integer) current) + delta.intValue();
        }
        if (current instanceof Long) {
            return ((Long) current) + delta.longValue();
        }
        if (current instanceof Short) {
            return (short) (((Short) current) + delta.intValue());
        }
        if (current instanceof Byte) {
            return (byte) (((Byte) current) + delta.intValue());
        }
        if (current instanceof Float) {
            return ((Float) current) + delta.floatValue();
        }
        if (current instanceof Double) {
            return ((Double) current) + delta.doubleValue();
        }
        throw new UnsupportedOperationException("Numeric operation on non-numeric type");
    }

    private static Object applyBitwise(Object current, Number delta, BitwiseOp op) {
        if (current instanceof Integer) {
            int a = (Integer) current;
            int b = delta.intValue();
            return op == BitwiseOp.OR ? (a | b) : op == BitwiseOp.AND ? (a & b) : (a ^ b);
        }
        if (current instanceof Long) {
            long a = (Long) current;
            long b = delta.longValue();
            return op == BitwiseOp.OR ? (a | b) : op == BitwiseOp.AND ? (a & b) : (a ^ b);
        }
        if (current instanceof Short) {
            short a = (Short) current;
            short b = delta.shortValue();
            return op == BitwiseOp.OR ? (short) (a | b)
                    : op == BitwiseOp.AND ? (short) (a & b)
                    : (short) (a ^ b);
        }
        if (current instanceof Byte) {
            byte a = (Byte) current;
            byte b = delta.byteValue();
            return op == BitwiseOp.OR ? (byte) (a | b)
                    : op == BitwiseOp.AND ? (byte) (a & b)
                    : (byte) (a ^ b);
        }
        throw new UnsupportedOperationException("Bitwise operation on non-integral type");
    }
}
