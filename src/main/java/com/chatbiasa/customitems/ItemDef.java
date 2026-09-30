package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public record ItemDef(
        String key,
        Material base,
        String texture,
        String model,
        ModelStates modelStates,
        String armorModel,
        Furniture furniture,
        Component name,
        List<Component> lore,
        Double damageBonus,
        Double armorBonus,
        Double attackSpeedBonus,
        List<PotionEffectDef> heldEffects,
        List<PotionEffectDef> useEffects,
        List<Ability> onHit,
        List<Ability> onRightClick
) {
    public ItemDef {
        modelStates = modelStates == null ? ModelStates.EMPTY : modelStates;
        lore = List.copyOf(lore);
        heldEffects = List.copyOf(heldEffects);
        useEffects = List.copyOf(useEffects);
        onHit = List.copyOf(onHit);
        onRightClick = List.copyOf(onRightClick);
    }

    public record ModelStates(List<String> pulling, String charged, String firework, String cast, String blocking) {
        private static final ModelStates EMPTY = new ModelStates(List.of(), null, null, null, null);

        public ModelStates {
            pulling = List.copyOf(pulling);
        }

        public boolean isEmpty() {
            return pulling.isEmpty() && charged == null && firework == null && cast == null && blocking == null;
        }
    }

    public record PotionEffectDef(PotionEffectType type, int amplifier, int duration) {}

    public record Furniture(boolean fixedRotation, float hitboxWidth, float hitboxHeight,
                            float offsetX, float offsetY, float offsetZ) {}

    public enum AbilityType { POTION, HEAL }

    public record Ability(
            AbilityType type,
            PotionEffectType effect,
            int amplifier,
            int duration,
            double amount,
            double chance,
            int cooldownSeconds
    ) {}
}
