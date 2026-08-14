package io.github.nbcss.higherlogisticspatch.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Bug Fix: https://github.com/Creators-of-Create/Create/issues/9937
 */
@Mixin(value = StockKeeperRequestScreen.class, remap = false)
public class StockKeeperRequestScreenMixin {

    @Inject(method = "resolveIngredientAmounts", at = @At("HEAD"), cancellable = true)
    private void hlp$fastResolveIngredientAmounts(
            List<List<BigItemStack>> validIngredients,
            CallbackInfoReturnable<List<List<BigItemStack>>> cir) {
        int n = validIngredients.size();
        int[] ptr = new int[n];
        int[][] pulled = new int[n][];
        for (int i = 0; i < n; i++) pulled[i] = new int[validIngredients.get(i).size()];

        while (true) {
            // Advance past drained candidates; tally how many slots currently draw from each shared stack.
            IdentityHashMap<BigItemStack, Integer> pointingSlots = new IdentityHashMap<>();
            boolean anyActive = false;
            for (int i = 0; i < n; i++) {
                List<BigItemStack> slot = validIngredients.get(i);
                while (ptr[i] < slot.size() && slot.get(ptr[i]).count <= 0) ptr[i]++;
                if (ptr[i] < slot.size()) {
                    anyActive = true;
                    pointingSlots.merge(slot.get(ptr[i]), 1, Integer::sum);
                }
            }
            if (!anyActive) break;

            // Whole rounds we can take before the scarcest stack can no longer feed all its slots
            int rounds = Integer.MAX_VALUE;
            for (var e : pointingSlots.entrySet())
                rounds = Math.min(rounds, e.getKey().count / e.getValue());

            if (rounds > 0) {
                for (int i = 0; i < n; i++)
                    if (ptr[i] < validIngredients.get(i).size())
                        pulled[i][ptr[i]] += rounds;
                for (var e : pointingSlots.entrySet())
                    e.getKey().count -= e.getValue() * rounds;
            } else {
                // One exact round in slot order: earlier slots win the scarce last units
                for (int i = 0; i < n; i++) {
                    List<BigItemStack> slot = validIngredients.get(i);
                    int k = ptr[i];
                    while (k < slot.size() && slot.get(k).count <= 0) k++;
                    ptr[i] = k;
                    if (k < slot.size()) {
                        slot.get(k).count--;
                        pulled[i][k]++;
                    }
                }
            }
        }

        List<List<BigItemStack>> resolved = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<BigItemStack> slot = validIngredients.get(i);
            List<BigItemStack> resolvedList = new ArrayList<>();
            for (int k = 0; k < slot.size(); k++)
                if (pulled[i][k] > 0) resolvedList.add(new BigItemStack(slot.get(k).stack, pulled[i][k]));
            resolved.add(resolvedList);
        }
        cir.setReturnValue(resolved);
    }
}
