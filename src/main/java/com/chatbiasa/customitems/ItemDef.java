package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

public record ItemDef(String key, Material base, String texture, Component name, List<Component> lore) {
}
