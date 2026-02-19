package code.damagemodifier;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // EZ HIÁNYZOTT!
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class DamageModifier extends JavaPlugin implements Listener, CommandExecutor {

    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("dm") != null) {
            getCommand("dm").setExecutor(this);
        }
        getLogger().info("DamageModifier (Final Version) betöltve!");
    }

    // priority = EventPriority.HIGHEST: Utoljára fut le, hogy minden mást felülbírálhasson
    // ignoreCancelled = true: Ha a Minecraft i-frame miatt törli az eseményt, a plugin nem fut le
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        double baseDamage = event.getDamage();
        double newDamage = baseDamage;

        // 1. Tárgy módosító
        ItemStack weapon = attacker.getEquipment() != null ? attacker.getEquipment().getItemInMainHand() : null;
        String matName = (weapon == null || weapon.getType() == Material.AIR) ? "HAND" : weapon.getType().name();
        newDamage = applyModifier(newDamage, "modifiers." + matName);

        // 2. Élesség (Sharpness)
        if (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasEnchant(Enchantment.SHARPNESS)) {
            int level = weapon.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS);
            String enchantPath = "enchantments.SHARPNESS_" + level;
            if (getConfig().contains(enchantPath)) {
                newDamage = applyModifier(newDamage, enchantPath);
            } else {
                for (int i = 0; i < level; i++) {
                    newDamage = applyModifier(newDamage, "enchantments.SHARPNESS_DEFAULT");
                }
            }
        }

        // 3. Erő (Strength)
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (effect != null) {
                int level = effect.getAmplifier() + 1;
                String potionPath = "potions.STRENGTH_" + level;
                if (getConfig().contains(potionPath)) {
                    newDamage = applyModifier(newDamage, potionPath);
                } else {
                    for (int i = 0; i < level; i++) {
                        newDamage = applyModifier(newDamage, "potions.STRENGTH_DEFAULT");
                    }
                }
            }
        }

        // 4. Globális módosító
        newDamage = applyModifier(newDamage, "overall_modifier");

        if (newDamage != baseDamage) {
            event.setDamage(newDamage);
        }

        // 5. Páncél törés
        if (victim instanceof Player player) {
            handleArmorDurability(player);
        }
    }

    private void handleArmorDurability(Player player) {
        double skipChance = getConfig().getDouble("armor_settings.skip_damage_chance", 0.0) / 100.0;
        int extraLoss = getConfig().getInt("armor_settings.extra_durability_loss", 0);

        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack armor : armorContents) {
            if (armor == null || armor.getType() == Material.AIR) continue;

            if (armor.getItemMeta() instanceof Damageable meta) {
                if (skipChance > 0 && random.nextDouble() < skipChance) continue;

                if (extraLoss != 0) {
                    meta.setDamage(meta.getDamage() + extraLoss);
                    armor.setItemMeta(meta);
                }
            }
        }
    }

    private double applyModifier(double currentDamage, String path) {
        String value = getConfig().getString(path);
        if (value == null) return currentDamage;
        try {
            if (value.endsWith("%")) {
                double percent = Double.parseDouble(value.replace("%", "")) / 100.0;
                return currentDamage * (1.0 + percent);
            } else {
                return currentDamage + Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            return currentDamage;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("damagemodifier.reload")) {
                sender.sendMessage("§cNincs jogosultságod!");
                return true;
            }
            reloadConfig();
            sender.sendMessage("§a[DamageModifier] Újratöltve!");
            return true;
        }
        return false;
    }
}
