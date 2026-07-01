package io.github.nbcss.createfactorycontroller.content.promise;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapLike;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;

/**
 * A {@link RequestPromise} minted by a Factory Controller gauge, carrying metadata Create's promise has no room
 * for: which gauge/address created it and a per-promise TTL. Two features build on it:
 * <ul>
 *   <li><b>Per-owner timeout</b> — {@link #tick()} advances our own {@link #age} and holds the base
 *       {@code ticksExisted} at 0 until {@code age >= ttl}, then latches it to {@link #EXPIRED_TICKS}
 *       ({@code Integer.MAX_VALUE}). Create's {@code getTotalPromisedAndRemoveExpired(stack, expiry)} removes on
 *       {@code ticksExisted >= expiry}: while held at 0 no caller can expire us (protected from a Create panel's
 *       small timeout); once latched, our own gauge's query — passing {@code expiry = EXPIRED_TICKS} — removes it.
 *       Because a base promise's real {@code ticksExisted} never reaches MAX, that query touches only our own
 *       flagged promises, so a gauge's timeout only ever affects its own. No accessor/sweep needed.</li>
 *   <li><b>Promise limit</b> — {@link #ownerKey}/{@link #targetAddress} let a gauge count its (or an address's)
 *       in-flight promises and stop requesting once the limit is reached.</li>
 * </ul>
 *
 * <p>Durability: the promise queue serializes via {@code Codec.list(RequestPromise.CODEC)}; a scoped
 * {@code @Redirect} in {@code RequestPromiseQueueMixin} swaps in {@link #CFC_CODEC}, which layers our fields on top
 * of Create's codec (transparent for base promises, rebuilds this subclass when our keys are present).</p>
 */
public class ControllerPromise extends RequestPromise {

    /** Sentinel {@code ticksExisted} a promise latches to once it passes its {@link #ttl}. A base promise's real
     *  {@code ticksExisted} never reaches this, so a query with {@code expiry = EXPIRED_TICKS} removes only our own
     *  flagged-expired promises. */
    public static final int EXPIRED_TICKS = Integer.MAX_VALUE;

    private static final String KEY_OWNER = "CfcOwner";
    private static final String KEY_ADDRESS = "CfcAddress";
    private static final String KEY_TTL = "CfcTtl";
    private static final String KEY_AGE = "CfcAge";

    /** Stable identity of the creating gauge. Used to count a gauge's own promises. */
    public String ownerKey;
    /** The packager address this promise was dispatched toward — for a future per-address quota. */
    public String targetAddress;
    /** Lifetime in ticks before we expire it ourselves; {@code -1} = never. */
    public int ttl;
    /** Our own age tick counter — the base {@code ticksExisted} is frozen (see {@link #tick()}). */
    public int age;

    public ControllerPromise(BigItemStack promisedStack, String ownerKey, String targetAddress, int ttl) {
        super(promisedStack);
        this.ownerKey = ownerKey;
        this.targetAddress = targetAddress;
        this.ttl = ttl;
        this.age = 0;
    }

    public ControllerPromise(int ticksExisted, BigItemStack promisedStack, String ownerKey, String targetAddress,
                             int ttl, int age) {
        super(ticksExisted, promisedStack);
        this.ownerKey = ownerKey;
        this.targetAddress = targetAddress;
        this.ttl = ttl;
        this.age = age;
    }

    /** Advance our own age (never {@code super.tick()}, so {@code ticksExisted} stays held). Hold {@code ticksExisted}
     *  at 0 until we pass {@link #ttl}, then latch it to {@link #EXPIRED_TICKS} so the owning gauge's
     *  {@code expiry = EXPIRED_TICKS} query removes it — while no smaller foreign expiry ever can. */
    @Override
    public void tick() {
        if (ticksExisted == EXPIRED_TICKS) return;   // latched
        age++;
        if (ttl >= 0 && age >= ttl) ticksExisted = EXPIRED_TICKS;
    }

    /**
     * {@code Codec<RequestPromise>} used in place of {@code RequestPromise.CODEC} inside the queue's write/read.
     * Byte-identical to Create's codec for a plain {@link RequestPromise}; for a {@link ControllerPromise} it also
     * writes {@code owner/address/ttl/age}, and on decode rebuilds the subclass only when those keys are present.
     */
    public static final Codec<RequestPromise> CFC_CODEC = Codec.of(
        new Encoder<>() {
            @Override
            public <T> DataResult<T> encode(RequestPromise input, DynamicOps<T> ops, T prefix) {
                DataResult<T> base = RequestPromise.CODEC.encode(input, ops, prefix);
                if (!(input instanceof ControllerPromise cp)) return base;   // transparent for Create's promises
                return base
                    .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_OWNER),   ops.createString(safe(cp.ownerKey))))
                    .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_ADDRESS), ops.createString(safe(cp.targetAddress))))
                    .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_TTL),     ops.createInt(cp.ttl)))
                    .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_AGE),     ops.createInt(cp.age)));
            }
        },
        new Decoder<>() {
            @Override
            public <T> DataResult<Pair<RequestPromise, T>> decode(DynamicOps<T> ops, T input) {
                return RequestPromise.CODEC.decode(ops, input).map(pair -> {
                    RequestPromise base = pair.getFirst();
                    MapLike<T> map = ops.getMap(input).result().orElse(null);
                    if (map == null || map.get(KEY_OWNER) == null) return pair;   // not one of ours
                    ControllerPromise cp = new ControllerPromise(base.ticksExisted, base.promisedStack,
                        str(ops, map, KEY_OWNER), str(ops, map, KEY_ADDRESS), intv(ops, map, KEY_TTL, -1),
                        intv(ops, map, KEY_AGE, 0));
                    return Pair.of((RequestPromise) cp, pair.getSecond());
                });
            }
        });

    private static String safe(String s) { return s == null ? "" : s; }

    private static <T> String str(DynamicOps<T> ops, MapLike<T> map, String key) {
        T v = map.get(key);
        return v == null ? "" : ops.getStringValue(v).result().orElse("");
    }

    private static <T> int intv(DynamicOps<T> ops, MapLike<T> map, String key, int def) {
        T v = map.get(key);
        return v == null ? def : ops.getNumberValue(v).result().map(Number::intValue).orElse(def);
    }
}
