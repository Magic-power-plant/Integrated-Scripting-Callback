package com.mpp.integratedscripting.api.item;

import org.cyclops.integrateddynamics.api.item.IVariableFacade;

import java.nio.file.Path;

/**
 * Variable facade for variables that refer to a script.
 *
 * <p>Direct port of the 1.20 reference API (level A in
 * docs/PORTING-1.12.2.md §6.3). The NBT layout is fixed by §10.2:
 * {@code disk / path / member} next to the handler's {@code _type} and
 * {@code _id}.</p>
 *
 * @author rubensworks
 */
public interface IScriptVariableFacade extends IVariableFacade {

    /**
     * @return The script disk id.
     */
    public int getDisk();

    /**
     * @return The path to the script inside the disk.
     */
    public Path getPath();

    /**
     * @return The referenced member element inside the file referred to by the
     * path inside the disk.
     */
    public String getMember();

}
