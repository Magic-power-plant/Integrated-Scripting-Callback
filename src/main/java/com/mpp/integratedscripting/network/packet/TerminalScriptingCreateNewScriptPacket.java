package com.mpp.integratedscripting.network.packet;

import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.network.CodecField;
import org.cyclops.cyclopscore.network.PacketCodec;

/**
 * Asks the server to create an empty script file on a disk, sent when the
 * player clicks the terminal's "+" button.
 *
 * @author rubensworks
 */
public class TerminalScriptingCreateNewScriptPacket extends PacketCodec {

    @CodecField
    private int disk;

    public TerminalScriptingCreateNewScriptPacket() {
    }

    public TerminalScriptingCreateNewScriptPacket(int disk) {
        this.disk = disk;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void actionClient(World world, EntityPlayer player) {
        // Server to client only: nothing to do.
    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerTerminalScripting) {
            ((ContainerTerminalScripting) player.openContainer).createNewServerScript(this.disk);
        }
    }

}
