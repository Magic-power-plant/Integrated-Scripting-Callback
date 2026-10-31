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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sends the contents of one script file between the server and the client that
 * has the scripting terminal open.
 *
 * <p>1.12.2 note (docs/PORTING-1.12.2.md §7.10): Cyclops' {@code PacketCodec}
 * supports {@code List<String>} fields, so the line list of the reference
 * implementation is kept as-is.</p>
 *
 * @author rubensworks
 */
public class TerminalScriptingModifiedScriptPacket extends PacketCodec {

    @CodecField
    private int disk;
    @CodecField
    private String path;
    @CodecField
    private List<String> script;

    public TerminalScriptingModifiedScriptPacket() {
        this.script = new ArrayList<>();
    }

    public TerminalScriptingModifiedScriptPacket(int disk, Path path, String script) {
        this.disk = disk;
        this.path = path.toString();
        this.script = Arrays.asList(script.split("\n", -1));
    }

    public String getScript() {
        return String.join("\n", this.script);
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
                    .setLastScript(this.disk, Paths.get(this.path), getScript());
        }
    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {
        if (player.openContainer instanceof ContainerTerminalScripting) {
            ((ContainerTerminalScripting) player.openContainer)
                    .setServerScript(this.disk, Paths.get(this.path), getScript());
        }
    }

}
