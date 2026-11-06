package com.kokuhaku.mixin;

import com.kokuhaku.client.StudentSkins;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Everyone in the run wears the uniform, whatever account is signed in: you look like You, and your friends like classmates. Your profile still shows your own skin. */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$skin(CallbackInfoReturnable<Identifier> cir) {
        if (StudentSkins.ownSkin) return;
        java.util.UUID id = ((AbstractClientPlayerEntity) (Object) this).getUuid();
        Identifier base = (Object) this instanceof ClientPlayerEntity ? StudentSkins.YOU : StudentSkins.classmate(id);
        cir.setReturnValue(com.kokuhaku.client.OutfitSkins.dress(id, base));
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void kokuhaku$slim(CallbackInfoReturnable<String> cir) {
        if (!StudentSkins.ownSkin) cir.setReturnValue("slim");
    }
}
