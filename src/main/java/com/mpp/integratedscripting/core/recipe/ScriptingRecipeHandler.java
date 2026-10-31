package com.mpp.integratedscripting.core.recipe;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.init.RecipeHandler;
import org.cyclops.cyclopscore.recipe.custom.component.DurationRecipeProperties;
import org.cyclops.cyclopscore.recipe.custom.component.IngredientAndFluidStackRecipeComponent;
import org.cyclops.integrateddynamics.block.BlockDryingBasin;
import org.cyclops.integrateddynamics.block.BlockMechanicalDryingBasin;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.block.BlockMendesite;

import java.util.Collection;

/**
 * Recipe handler for the 1.12.2 port.
 *
 * <p>Cyclops' {@link RecipeHandler} loads its XML through
 * {@code RecipeHandler.class.getResourceAsStream(...)}. In the 1.12.2 dev
 * environment CyclopsCore and this mod are loaded by different mod
 * classloaders, so that lookup does not see
 * {@code assets/integratedscriptingcallback/recipes/mendesite.xml} and the
 * handler reports zero tagged recipes (docs/MILESTONES.md M6).</p>
 *
 * <p>Per decision D3 the XML file remains the primary definition. This class
 * only registers the two drying-basin recipes from code when the XML did not
 * yield a {@code mendesite} recipe, which keeps the in-game recipes working in
 * both the dev and the production classloader layouts.</p>
 *
 * @author rubensworks
 */
public class ScriptingRecipeHandler extends RecipeHandler {

    public ScriptingRecipeHandler(ModBase mod, String... recipeFiles) {
        super(mod, recipeFiles);
    }

    @Override
    protected void registerCustomRecipes() {
        super.registerCustomRecipes();

        Collection<?> loaded = getTaggedRecipes().get("mendesite");
        if (loaded != null && !loaded.isEmpty()) {
            return;
        }

        Fluid resin = FluidRegistry.getFluid("menrilresin");
        if (resin == null) {
            IntegratedScriptingCallback.clog(
                    "Skipping Mendesite drying recipes: the menrilresin fluid is not registered.");
            return;
        }
        Block mendesite = BlockMendesite.getInstance();
        if (mendesite == null) {
            IntegratedScriptingCallback.clog(
                    "Skipping Mendesite drying recipes: the Mendesite block is not registered.");
            return;
        }

        IngredientAndFluidStackRecipeComponent input = new IngredientAndFluidStackRecipeComponent(
                // 1.12.2 has no separate andesite block: it is stone with metadata 5.
                new ItemStack(Blocks.STONE, 1, 5), new FluidStack(resin, 1000));
        IngredientAndFluidStackRecipeComponent output = new IngredientAndFluidStackRecipeComponent(
                new ItemStack(mendesite), (FluidStack) null);

        if (BlockDryingBasin.getInstance() != null) {
            BlockDryingBasin.getInstance().getRecipeRegistry().registerRecipe(
                    "integratedscriptingcallback:mendesite_dryingbasin",
                    input, output, new DurationRecipeProperties(300));
        }
        if (BlockMechanicalDryingBasin.getInstance() != null) {
            BlockMechanicalDryingBasin.getInstance().getRecipeRegistry().registerRecipe(
                    "integratedscriptingcallback:mendesite_mechanical_dryingbasin",
                    input, output, new DurationRecipeProperties(15));
        }
        IntegratedScriptingCallback.clog(
                "Registered Mendesite drying basin recipes from code (XML route found no tagged recipes).");
    }

}
