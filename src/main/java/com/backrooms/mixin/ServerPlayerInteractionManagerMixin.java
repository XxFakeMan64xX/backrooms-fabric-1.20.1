package com.backrooms.mixin;

import com.backrooms.event.BlockPlacementHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerInteractionManagerMixin {
    
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onBlockPlace(ServerPlayer player, net.minecraft.world.level.Level level, net.minecraft.world.item.ItemStack stack, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        
        BlockPlacementHandler.handleBlockPlace(level, pos, state, player);
    }
}
