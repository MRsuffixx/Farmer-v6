package xyz.geik.farmer.listeners.backend;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.api.FarmerAPI;
import xyz.geik.farmer.api.managers.FarmerManager;
import xyz.geik.farmer.guis.UsersGui;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.chat.Placeholder;
import space.arim.morepaperlib.MorePaperLib;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatEvent implements Listener {
    @Getter
    private static final Map<String, String> players = new ConcurrentHashMap<>();
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = 500; // 500ms cooldown

    @EventHandler
    public void chatEvent(AsyncPlayerChatEvent e) {
        String playerName = e.getPlayer().getName();
        String regionId = players.remove(playerName);
        if (regionId == null) {
            return;
        }

        e.setCancelled(true);
        Player player = e.getPlayer();
        String input = e.getMessage();
        long currentTime = System.currentTimeMillis();
        Long lastTime = cooldowns.get(playerName);
        if (lastTime != null && currentTime - lastTime < COOLDOWN_TIME) {
            return;
        }
        cooldowns.put(playerName, currentTime);
        if (input.equalsIgnoreCase(Main.getLangFile().getVarious().getInputCancelWord())) {
            ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getInputCancel());
            return;
        }
        UUID targetUUID = FastPlayerLookup.lookupPlayer(input);
        if (targetUUID == null) {
            ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getUserCouldntFound());
            return;
        }
        MorePaperLib morePaperLib = new MorePaperLib(Main.getInstance());
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            try {
                Farmer farmer = FarmerManager.getFarmers().get(regionId);

                if (farmer == null) {
                    ChatUtils.sendMessage(player, "Error: Could not find farmer data");
                    return;
                }
                // Check if the player is the owner or has farmer.admin permission
                if (!farmer.getOwnerUUID().equals(player.getUniqueId())
                        && !player.hasPermission("farmer.admin")) {
                    ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getNoPerm());
                    return;
                }
                if (farmer.getUsers().stream().anyMatch(user -> user.getUuid().equals(targetUUID))) {
                    ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getUserAlreadyExist(),
                            new Placeholder("{player}", input));
                } else {
                    farmer.addUser(targetUUID, input);
                    ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getUserAdded(),
                            new Placeholder("{player}", input));
                }

                UsersGui.showGui(player, farmer);

            } catch (Exception ex) {
                ChatUtils.sendMessage(player, Main.getLangFile().getMessages().getUserCouldntFound());
            }
        }, null);
    }
}
