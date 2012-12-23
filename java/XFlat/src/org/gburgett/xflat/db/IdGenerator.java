/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

/**
 *
 * @author gordon
 */
public abstract class IdGenerator {
    
    /**
     * Indicates whether the given ID property type is supported by this
     * ID generator.  If false, then IDs generated by this generator
     * cannot be assigned to the ID property of the POJO, and thus the POJO
     * cannot be stored in this Table.
     * @param idType The type of the ID property on the POJO.
     * @return true if the type is supported by the ID generator.
     */
    public abstract boolean supports(Class<?> idType);
    
    /**
     * Generates a new ID, converting it to the given id type.
     * @param idType The type to convert the ID to, must be one of the supported
     * types as given by {@link #supports(java.lang.Class) }
     * @return The converted ID value.
     */
    public abstract Object generateNewId(Class<?> idType);
    
    /**
     * A convenience for converting IDs to their string representations.
     * Since all engines take string IDs (which can be stored in the XML DOM)
     * this is how we persist the ID.
     * @param id The ID to convert, cannot be null.
     * @return The string representation of the given ID.
     */
    abstract String idToString(Object id);
    
    /**
     * A convenience for converting IDs from their string representations.
     * Since all engines take string IDs (which can be stored in the XML DOM)
     * this is how we get a persisted ID.
     * @param id The string value of the ID.
     * @param idType The type to convert the ID to, must be one of the supported
     * types as given by {@link #supports(java.lang.Class) }.
     * @return The converted ID.
     */
    abstract Object stringToId(String id, Class<?> idType);
}
