package wtf.moneymod.client.api.forge;

import com.google.common.base.Strings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import wtf.moneymod.client.Main;
import wtf.moneymod.client.api.events.ConnectionEvent;
import wtf.moneymod.client.api.events.PacketEvent;
import wtf.moneymod.client.api.events.TotemPopEvent;
import wtf.moneymod.client.api.setting.Option;
import wtf.moneymod.client.impl.command.Command;
import wtf.moneymod.client.impl.module.Module;
import wtf.moneymod.client.impl.utility.Globals;
import wtf.moneymod.client.impl.utility.impl.render.ColorUtil;
import wtf.moneymod.client.impl.utility.impl.render.JColor;
import wtf.moneymod.eventhandler.listener.Handler;
import wtf.moneymod.eventhandler.listener.Listener;

import java.awt.*;
import java.util.Objects;
import java.util.UUID;

public class EventHandler implements Globals {

    @SubscribeEvent public void onInput(InputEvent.KeyInputEvent event) {
        Main.getMain().getModuleManager().get(module -> Keyboard.getEventKey() != 0 && Keyboard.getEventKeyState() && Keyboard.getEventKey() == module.getKey()).forEach(Module::toggle);
    }

    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START || nullCheck()) return;
        Main.getMain().getModuleManager().get(Module::isToggled).forEach(Module::onTick);
    }

    @SubscribeEvent public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith(Main.getMain().getCommandManagement().getPrefix())) {
            String temp = message.substring(1);
            for (Command command : Main.getMain().getCommandManagement()) {
                String[] split = temp.split(" ");
                for (String name : command.getAlias()) {
                    if (name.equalsIgnoreCase(split[ 0 ])) {
                        command.execute(split);
                        event.setCanceled(true);
                        mc.ingameGUI.getChatGUI().addToSentMessages(event.getMessage());
                        return;
                    }
                }
            }
            event.setCanceled(true);
        }
    }

    @Handler public Listener<PacketEvent.Receive> packetEventReceive = new Listener<>(PacketEvent.Receive.class, e -> {
        if (e.getPacket() instanceof SPacketEntityStatus) {
            SPacketEntityStatus packet = e.getPacket();
            if (packet.getEntity(mc.world) instanceof EntityPlayer && packet.getOpCode() == 35) {
                Main.EVENT_BUS.dispatch(new TotemPopEvent(( EntityPlayer ) packet.getEntity(mc.world)));
            }
        } else if (e.getPacket() instanceof SPacketPlayerListItem && !nullCheck()) {
            SPacketPlayerListItem packet = e.getPacket();
            packet.getEntries().stream().filter(Objects::nonNull).filter(data -> !Strings.isNullOrEmpty(data.getProfile().getName()) || data.getProfile().getId() != null).forEach(data -> {
                final UUID id;
                final String name;
                final EntityPlayer entity;
                String logoutName;
                id = data.getProfile().getId();
                switch (packet.getAction()) {
                    case ADD_PLAYER:
                        name = data.getProfile().getName();
                        Main.EVENT_BUS.dispatch(new ConnectionEvent(packet.getAction(), null, id, name));
                        break;
                    case REMOVE_PLAYER:
                        entity = mc.world.getPlayerEntityByUUID(id);
                        if (entity != null) {
                            logoutName = entity.getName();
                            Main.EVENT_BUS.dispatch(new ConnectionEvent(packet.getAction(), entity, id, logoutName));
                        }
                        break;
                }
            });

        }
    });


    @SubscribeEvent public void onRenderUpdate(RenderWorldLastEvent event) {
        Main.getMain().getFpsManagement().update();
        Main.getMain().getPulseManagement().update();
        for (Module m : Main.getMain().getModuleManager()) {
            for (Option<?> setting : Option.getContainersForObject(m)) {
                if (setting.getValue() instanceof JColor) {
                    JColor color = ( JColor ) setting.getValue();
                    float[] hsb = Color.RGBtoHSB(color.getColor().getRed(), color.getColor().getGreen(), color.getColor().getBlue(), null);
                    if (color.isRainbow()) {
                        (( Option<JColor> ) setting).setValue(new JColor(ColorUtil.injectAlpha(ColorUtil.rainbowColor(0, hsb[ 1 ], hsb[ 2 ]), color.getColor().getAlpha()), color.isRainbow()));
                    }
                }
            }
        }
    }

}
