package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapLike;
import net.liukrast.deployer.lib.logistics.packagerLink.GenericRequestPromise;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A Repackaged fluid {@link GenericRequestPromise} tagged with the Factory-Controller gauge (and target address) that
 * minted it — the fluid analogue of {@code ControllerPromise}. The tags let the promise-limit gate count a fluid
 * gauge's (or an address's) in-flight fluid requests, by scanning the queue's generic promises
 * ({@code RPQExtension.deployer$flatten}) and bucketing our subclass by owner/address.
 *
 * <p>Apart from the tags this behaves exactly like a plain generic promise (same {@code tick()}/expiry), so the fluid
 * gauge's existing timeout is unchanged. Every reference here is to a Deployer class, so this loads only on the
 * fluid-gauge path (Repackaged, hence Deployer, installed) — see {@link RepackagedFluidStock} and the
 * {@code GenericPromiseCodecMixin} that keeps the tags durable across save/load.</p>
 */
public class ControllerFluidPromise extends GenericRequestPromise<FluidStack> {

    private static final String KEY_OWNER = "CfcOwner";
    private static final String KEY_ADDRESS = "CfcAddress";

    /** Stable identity of the creating gauge (its {@code gaugeId}); {@code ""}/null when unattributed. */
    final String ownerKey;
    /** The packager address this promise was dispatched toward (for the per-address quota scope). */
    final String targetAddress;

    ControllerFluidPromise(FluidStack promisedStack, String ownerKey, String targetAddress) {
        super(promisedStack);
        this.ownerKey = ownerKey;
        this.targetAddress = targetAddress;
    }

    ControllerFluidPromise(int ticksExisted, FluidStack promisedStack, String ownerKey, String targetAddress) {
        super(ticksExisted, promisedStack);
        this.ownerKey = ownerKey;
        this.targetAddress = targetAddress;
    }

    /**
     * Wraps Deployer's generic-promise codec so our owner/address tags survive save/load. Byte-identical to the base
     * codec for a plain {@link GenericRequestPromise} (of any value type); for a {@link ControllerFluidPromise} it also
     * writes {@code owner/address}, and on decode rebuilds the subclass only when those keys are present. Installed in
     * place of {@code INetworkHandler.requestCodec()} by {@code GenericPromiseCodecMixin} (fluid analogue of the
     * item-side {@code RequestPromise.CODEC} redirect). Wrapping non-fluid value types is harmless — it stays
     * transparent since only fluid promises are ever this subclass / carry our keys.
     */
    public static Codec<GenericRequestPromise<FluidStack>> wrapCodec(Codec<GenericRequestPromise<FluidStack>> base) {
        return Codec.of(
            new Encoder<>() {
                @Override
                public <T> DataResult<T> encode(GenericRequestPromise<FluidStack> input, DynamicOps<T> ops, T prefix) {
                    DataResult<T> enc = base.encode(input, ops, prefix);
                    if (!(input instanceof ControllerFluidPromise cp)) return enc;   // transparent for plain promises
                    return enc
                        .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_OWNER),   ops.createString(safe(cp.ownerKey))))
                        .flatMap(t -> ops.mergeToMap(t, ops.createString(KEY_ADDRESS), ops.createString(safe(cp.targetAddress))));
                }
            },
            new Decoder<>() {
                @Override
                public <T> DataResult<Pair<GenericRequestPromise<FluidStack>, T>> decode(DynamicOps<T> ops, T input) {
                    return base.decode(ops, input).map(pair -> {
                        GenericRequestPromise<FluidStack> b = pair.getFirst();
                        MapLike<T> map = ops.getMap(input).result().orElse(null);
                        if (map == null || map.get(KEY_OWNER) == null) return pair;   // not one of ours
                        ControllerFluidPromise cp = new ControllerFluidPromise(b.ticksExisted, b.promisedStack,
                            str(ops, map, KEY_OWNER), str(ops, map, KEY_ADDRESS));
                        return Pair.of((GenericRequestPromise<FluidStack>) cp, pair.getSecond());
                    });
                }
            });
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static <T> String str(DynamicOps<T> ops, MapLike<T> map, String key) {
        T v = map.get(key);
        return v == null ? "" : ops.getStringValue(v).result().orElse("");
    }
}
