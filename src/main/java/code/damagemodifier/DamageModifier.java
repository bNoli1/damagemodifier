package code.damagemodifier;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

public class DamageModifier extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dm").setExecutor(this);
        getLogger().info("DamageModifier (Hibrid mód) betöltve!");
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        double damage = event.getDamage();
        
        // 1. Tárgy alapú módosítás
        ItemStack weapon = attacker.getEquipment() != null ? attacker.getEquipment().getItemInMainHand() : null;
        String mat = (weapon == null || weapon.getType() == Material.AIR) ? "HAND" : weapon.getType().name();
        
        damage = applyModifier(damage, "modifiers." + mat);

        // 2. Erő effekt (STRENGTH)
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (effect != null) {
                int level = effect.getAmplifier() + 1;
                for (int i = 0; i < level; i++) {
                    damage = applyModifier(damage, "potions.STRENGTH_MODIFIER");
                }
            }
        }

        event.setDamage(damage);
    }

    // Segédfüggvény, ami eldönti, hogy % vagy fix szám
    private double applyModifier(double currentDamage, String path) {
        String value = getConfig().getString(path);
        if (value == null) return currentDamage;

        try {
            if (value.endsWith("%")) {
                // Százalékos számítás (pl: "50%")
                double percent = Double.parseDouble(value.replace("%", "")) / 100.0;
                return currentDamage * (1.0 + percent);
            } else {
                // Fix szám hozzáadása (pl: "2.0")
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
            sender.sendMessage("§aKonfiguráció frissítve!");
            return true;
        }
        return false;
    }
}
