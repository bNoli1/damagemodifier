package code.damagemodifier;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class DamageModifier extends JavaPlugin implements Listener, CommandExecutor {

    private final Random random = new Random();
    private final HashMap<UUID, Long> lastHitMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("dm") != null) {
            getCommand("dm").setExecutor(this);
        }
        getLogger().info("DamageModifier Pro (Minden funkcióval) betöltve!");
    }

    // --- ARANYALMA EFFEKTEK KEZELÉSE ---
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            getServer().getScheduler().runTask(this, () -> applyAppleEffects(player, "enchanted_apple_effects"));
        } else if (item.getType() == Material.GOLDEN_APPLE) {
            getServer().getScheduler().runTask(this, () -> applyAppleEffects(player, "golden_apple_effects"));
        }
    }

    private void applyAppleEffects(Player player, String configPath) {
        if (!getConfig().contains(configPath) || getConfig().getConfigurationSection(configPath) == null) return;

        for (String effectName : getConfig().getConfigurationSection(configPath).getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
            if (type == null) continue;

            int duration = getConfig().getInt(configPath + "." + effectName + ".duration_ticks");
            int amplifier = getConfig().getInt(configPath + "." + effectName + ".amplifier");

            player.removePotionEffect(type);
            player.addPotionEffect(new PotionEffect(type, duration, amplifier));
        }
    }

    // --- SEBZÉS MÓDOSÍTÁS + CPS VÉDELEM ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        double baseDamage = event.getDamage();
        long currentTime = System.currentTimeMillis();
        long cooldownMs = getConfig().getLong("hit_cooldown_ms", 500);

        // CPS Védelem ellenőrzése
        boolean isFastClick = false;
        if (lastHitMap.containsKey(attacker.getUniqueId())) {
            if (currentTime - lastHitMap.get(attacker.getUniqueId()) < cooldownMs) {
                isFastClick = true;
            }
        }
        lastHitMap.put(attacker.getUniqueId(), currentTime);

        // Ha túl gyors a klikk, nem számolunk bónuszokat
        if (isFastClick) return;

        double newDamage = baseDamage;

        // 1. Tárgy bónusz
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String matName = (weapon == null || weapon.getType() == Material.AIR) ? "HAND" : weapon.getType().name();
        newDamage = applyModifier(newDamage, "modifiers." + matName);

        // 2. Sharpness bónusz
        if (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasEnchant(Enchantment.SHARPNESS)) {
            int lvl = weapon.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS);
            String path = "enchantments.SHARPNESS_" + lvl;
            if (getConfig().contains(path)) {
                newDamage = applyModifier(newDamage, path);
            } else {
                for (int i = 0; i < lvl; i++) {
                    newDamage = applyModifier(newDamage, "enchantments.SHARPNESS_DEFAULT");
                }
            }
        }

        // 3. Strength bónusz
        if (attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect eff = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (eff != null) {
                int lvl = eff.getAmplifier() + 1;
                String path = "potions.STRENGTH_" + lvl;
                if (getConfig().contains(path)) {
                    newDamage = applyModifier(newDamage, path);
                } else {
                    for (int i = 0; i < lvl; i++) {
                        newDamage = applyModifier(newDamage, "potions.STRENGTH_DEFAULT");
                    }
                }
            }
        }

        // 4. Overall bónusz
        newDamage = applyModifier(newDamage, "overall_modifier");

        event.setDamage(newDamage);

        // 5. Páncél törés kezelése
        if (victim instanceof Player player) {
            handleArmorDurability(player);
        }
    }

    private void handleArmorDurability(Player player) {
        double skip = getConfig().getDouble("armor_settings.skip_damage_chance", 0.0) / 100.0;
        int extra = getConfig().getInt("armor_settings.extra_durability_loss", 0);

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) continue;
            if (armor.getItemMeta() instanceof Damageable meta) {
                if (skip > 0 && random.nextDouble() < skip) continue;
                if (extra != 0) {
                    meta.setDamage(meta.getDamage() + extra);
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
            if (!sender.hasPermission("damagemodifier.reload")) return true;
            reloadConfig();
            sender.sendMessage("§a[DamageModifier] Config frissítve!");
            return true;
        }
        return false;
    }
}
