package com.mpp.integratedscripting.client.gui.image;

import com.mpp.Reference;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.cyclopscore.client.gui.image.Image;

/**
 * Client-side images used by the scripting terminal.
 *
 * <p>The icon sheet ({@code textures/gui/icons.png}, a 256x256 GUI sheet like
 * every other Cyclops texture) contains two 12x12 icons: a generic file icon at
 * (0, 0) and the script file icon at (12, 0). Both rectangles are shared with
 * the 1.20 reference implementation (docs/PORTING-1.12.2.md §7.9.3, §9.11).</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class ScriptImages {

    public static final ResourceLocation ICONS = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/icons.png");

    /** Icon for a file whose language handler has no icon of its own. */
    public static final IImage FILE_OTHER = new Image(ICONS, 0, 0, 12, 12);
    /** Icon for a JavaScript file, provided by {@code LanguageHandlerJavaScript}. */
    public static final IImage FILE_JS = new Image(ICONS, 12, 0, 12, 12);
    /**
     * Icon for a TypeScript file. The sheet has no third 12x12 icon yet (the next
     * column starts at x=24 and is empty), so TypeScript shares the JavaScript
     * page; the registry still stores it per language id, so a dedicated texture
     * only has to change this one constant.
     */
    public static final IImage FILE_TS = FILE_JS;

    private ScriptImages() {
    }

}
