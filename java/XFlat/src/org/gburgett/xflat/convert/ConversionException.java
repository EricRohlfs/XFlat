/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.convert;

/**
 * The base class for conversion errors that occur in XFlat.
 * 
 * Copied from org.springframework.core.convert,
 * modified by Gordon Burgett
 */
public class ConversionException extends RuntimeException {

    /**
     * Creates a new instance of
     * <code>ConversionException</code> without detail message.
     */
    public ConversionException() {
    }

    /**
     * Constructs an instance of
     * <code>ConversionException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public ConversionException(String msg) {
        super(msg);
    }
}
