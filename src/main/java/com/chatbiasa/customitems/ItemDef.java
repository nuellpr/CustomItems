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
        lore = List.copyOf(lore);
        heldEffects = List.copyOf(heldEffects);
        useEffects = List.copyOf(useEffects);
        onHit = List.copyOf(onHit);
        onRightClick = List.copyOf(onRightClick);
    }

    public record PotionEffectDef(PotionEffectType type, int amplifier, int duration) {}

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
