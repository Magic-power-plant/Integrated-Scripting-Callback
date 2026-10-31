package com.mpp.integratedscripting.network.packet;

import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.network.CodecField;
import org.cyclops.cyclopscore.network.PacketCodec;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Removes a script file from a disk, and notifies the other side about the
 * removal.
 *
 * @author rubensworks
 */
public class TerminalScriptingDeleteScriptPacket extends PacketCodec {

    @CodecField
    private int disk;
    @CodecField
    private String path;

    public TerminalScriptingDeleteScriptPacket() {
    }

    public TerminalScriptingDeleteScriptPacket(int disk, Path path) {
        this.disk = disk;
        this.path = path.toString();
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void actionClient(World world, EntityPlayer player) {
        if (player.openContainer instanceof ContainerTerminalScripting) {
            ((ContainerTerminalScripting) player.openContainer)
                    .setLastScript(this.disk, Paths.get(this.path), null);
        }
    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerTerminalScripting) {
            ((ContainerTerminalScripting) player.openContainer)
                    .setServerScript(this.disk, Paths.get(this.path), null);
        }
    }

}
