package accident.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.handler.PacketSizeLogger;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import accident.events.api.EventManager;
import accident.events.impl.PacketEvent;
import accident.util.config.impl.proxy.ProxyConfig;
import accident.util.proxy.Proxy;
import java.net.InetSocketAddress;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Receive packetEvent = new PacketEvent.Receive(packet);
        EventManager.callEvent(packetEvent);
        if (packetEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendPre(Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Send packetEvent = new PacketEvent.Send(packet);
        EventManager.callEvent(packetEvent);
        if (packetEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addHandlers", at = @At("RETURN"))
    private static void addHandlersHook(ChannelPipeline pipeline, NetworkSide side, boolean local, PacketSizeLogger packetSizeLogger, CallbackInfo ci) {
        ProxyConfig config = ProxyConfig.getInstance();
        Proxy proxy = config.getDefaultProxy();

        if (proxy != null && config.isProxyEnabled() && !proxy.isEmpty() && side == NetworkSide.CLIENTBOUND && !local) {
            InetSocketAddress proxyAddress = new InetSocketAddress(proxy.getIp(), proxy.getPort());
            if (proxy.type == Proxy.ProxyType.SOCKS4) {
                pipeline.addFirst("rich_socks4_proxy", new Socks4ProxyHandler(proxyAddress, proxy.username));
            } else {
                pipeline.addFirst("rich_socks5_proxy", new Socks5ProxyHandler(proxyAddress, proxy.username, proxy.password));
            }
            config.setLastUsedProxy(new Proxy(proxy));
        }
    }
}