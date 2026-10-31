package com.mpp.integratedscripting.api.language;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import com.mpp.integratedscripting.api.network.IScriptFactory;

import java.util.List;

/**
 * Logic for handling a specific programming language.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §6.4): syntax highlighting uses
 * the 16 {@link TextFormatting} colours instead of RGB {@code Style}s, so
 * {@link #markupLine(String)} returns formatting codes.</p>
 *
 * @author rubensworks
 */
public interface ILanguageHandler {

    public String getName();

    @SideOnly(Side.CLIENT)
    public IImage getIcon();

    public List<String> getExtensions();

    public List<Pair<TextFormatting, String>> markupLine(String line);

    public IScriptFactory getScriptFactory() throws EvaluationException;

    public void onServerStarted();

}
