package com.mpp;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * Referenced registry entries.
 *
 * <p>1.12.2 has no {@code @ObjectHolder}, and Cyclops {@code ExtendedConfig}
 * singletons are assigned later than class initialisation, so every entry is
 * resolved lazily from the game registry by its full name. Never cache these
 * values in static fields: they only exist after pre-initialisation.</p>
 *
 * @author rubensworks
 */
public final class RegistryEntries {

    private RegistryEntries() {
    }

    public static Item itemScriptingDisk() {
        return Item.getByNameOrId(Reference.MOD_ID + ":scripting_disk");
    }

    public static Item itemRefresher() {
        return Item.getByNameOrId(Reference.MOD_ID + ":refresher");
    }

    public static Block blockScriptingDrive() {
        return Block.getBlockFromName(Reference.MOD_ID + ":scripting_drive");
    }

    public static Block blockAddressedMemory() {
        return Block.getBlockFromName(Reference.MOD_ID + ":addressed_memory");
    }

    public static Block blockMendesite() {
        return Block.getBlockFromName(Reference.MOD_ID + ":mendesite");
    }

    public static Block blockPartTerminalScripting() {
        return Block.getBlockFromName(Reference.MOD_ID + ":part_terminal_scripting_block");
    }

    public static Item itemPartTerminalScripting() {
        return Item.getByNameOrId(Reference.MOD_ID + ":part_terminal_scripting_item");
    }
}
