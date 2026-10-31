package com.mpp.integratedscripting.proxy;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.network.packet.TerminalScriptingCreateNewScriptPacket;
import com.mpp.integratedscripting.network.packet.TerminalScriptingDeleteScriptPacket;
import com.mpp.integratedscripting.network.packet.TerminalScriptingModifiedScriptPacket;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.network.PacketHandler;
import org.cyclops.cyclopscore.proxy.CommonProxyComponent;

/**
 * Proxy for both the physical server and the logical server side.
 *
 * @author rubensworks
 */
public class CommonProxy extends CommonProxyComponent {

    @Override
    public ModBase getMod() {
        return IntegratedScriptingCallback._instance;
    }

    @Override
    public void registerPacketHandlers(PacketHandler packetHandler) {
        super.registerPacketHandlers(packetHandler);
        // M5: terminal script synchronisation (docs/PORTING-1.12.2.md §7.10).
        // The client proxy delegates to this method, so both sides are covered.
        packetHandler.register(TerminalScriptingModifiedScriptPacket.class);
        packetHandler.register(TerminalScriptingCreateNewScriptPacket.class);
        packetHandler.register(TerminalScriptingDeleteScriptPacket.class);
    }

}
