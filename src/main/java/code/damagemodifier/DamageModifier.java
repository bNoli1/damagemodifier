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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public class DamageModifier extends JavaPlugin implements Listener, CommandExecutor {

    @Override
    public void onEnable() {
        // Alapértelmezett config létrehozása, ha nem létezik
        saveDefaultConfig();
        
        // Események és parancs regisztrálása
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("dm") != null) {
            getCommand("dm").setExecutor(this);
        }
        
        getLogger().info("DamageModifier sikeresen betöltve!");
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        // Csak akkor foglalkozunk vele, ha egy élőlény sebez
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        double originalDamage = event.getDamage();
        double currentDamage = originalDamage;

        // 1. Fegyver/Tárgy alapú módosítás
        ItemStack weapon = (attacker.getEquipment() != null) ? attacker.getEquipment().getItemInMainHand() : null;
        String matName = (weapon == null || weapon.getType() == Material.AIR) ? "HAND" : weapon.getType().name();
        
        currentDamage = applyModifier(currentDamage, "modifiers." + matName);

        // 2. Strength (Erő) effekt alapú módosítás
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect effect = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (effect != null) {
                // A szint 0-tól indul, így +1 kell (Strength I = level 1)
                int level = effect.getAmplifier() + 1;
                for (int i = 0; i < level; i++) {
                    currentDamage = applyModifier(currentDamage, "potions.STRENGTH_MODIFIER");
                }
            }
        }

        // Csak akkor módosítunk, ha tényleg változott az érték
        if (currentDamage != originalDamage) {
            event.setDamage(currentDamage);
            // Debug üzenet a konzolba (opcionális, kikapcsolható)
            // getLogger().info("Sebzés módosítva: " + originalDamage + " -> " + currentDamage);
        }
    }

    private double applyModifier(double damage, String path) {
        // Megnézzük, létezik-e az útvonal a configban
        if (!getConfig().contains(path)) return damage;
        
        String value = getConfig().getString(path);
        if (value == null) return damage;
        
        // Szóközök eltávolítása a biztonság kedvéért
        value = value.trim();

        try {
            if (value.endsWith("%")) {
                // Százalékos számítás: pl. "25%" -> 1.25-ös szorzó
                double percent = Double.parseDouble(value.replace("%", "")) / 100.0;
                return damage * (1.0 + percent);
            } else {
                // Fix szám hozzáadása: pl. "2.0"
                double flat = Double.parseDouble(value);
                return damage + flat;
            }
        } catch (NumberFormatException e) {
            getLogger().warning("Hibás formátum a configban (" + path + "): " + value);
            return damage;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("damagemodifier.reload")) {
                sender.sendMessage("§cNincs jogosultságod ehhez!");
                return true;
            }
            reloadConfig();
            sender.sendMessage("§a[DamageModifier] Konfiguráció újratöltve!");
            return true;
        }
        return false;
    }
}
