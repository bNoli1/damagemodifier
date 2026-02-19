package code.damagemodifier;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public class DamageModifier extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("dm") != null) getCommand("dm").setExecutor(this);
        getLogger().info("DamageModifier Pro betöltve!");
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        double damage = event.getDamage();

        // 1. Tárgy alapú módosítás
        ItemStack weapon = attacker.getEquipment() != null ? attacker.getEquipment().getItemInMainHand() : null;
        String matName = (weapon == null || weapon.getType() == Material.AIR) ? "HAND" : weapon.getType().name();
        damage = applyModifier(damage, "modifiers." + matName);

        // 2. Élesség (Sharpness) bűvölés
        if (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasEnchant(Enchantment.SHARPNESS)) {
            int enchantLevel = weapon.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS);
            // Keresünk specifikus szintet (pl. SHARPNESS_3), ha nincs, az alap SHARPNESS-t használjuk
            String enchantPath = "enchantments.SHARPNESS_" + enchantLevel;
            if (getConfig().contains(enchantPath)) {
                damage = applyModifier(damage, enchantPath);
            } else {
                for (int i = 0; i < enchantLevel; i++) {
                    damage = applyModifier(damage, "enchantments.SHARPNESS");
                }
            }
        }

        // 3. Potion effektek (Specifikus szint vagy alap)
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (effect != null) {
                int level = effect.getAmplifier() + 1;
                String potionPath = "potions.STRENGTH_" + level;
                
                if (getConfig().contains(potionPath)) {
                    // Ha van külön beállítás a szinthez (pl. STRENGTH_2)
                    damage = applyModifier(damage, potionPath);
                } else {
                    // Ha nincs, akkor az alapértelmezett értéket szorozzuk a szinttel
                    for (int i = 0; i < level; i++) {
                        damage = applyModifier(damage, "potions.STRENGTH_DEFAULT");
                    }
                }
            }
        }

        // 4. Globális (Overall) módosító
        damage = applyModifier(damage, "overall_modifier");

        event.setDamage(damage);
    }

    private double applyModifier(double currentDamage, String path) {
        String value = getConfig().getString(path);
        if (value == null) return currentDamage;
        value = value.trim();

        try {
            if (value.endsWith("%")) {
                double percent = Double.parseDouble(value.replace("%", "")) / 100.0;
                return currentDamage * (1.0 + percent);
            } else {
                double flat = Double.parseDouble(value);
                return currentDamage + flat;
            }
        } catch (NumberFormatException e) {
            return currentDamage;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("damagemodifier.reload")) return true;
            reloadConfig();
            sender.sendMessage("§a[DamageModifier] Újratöltve!");
            return true;
        }
        return false;
    }
}
