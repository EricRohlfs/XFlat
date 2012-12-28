/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.gburgett.xflat.convert.converters.StringConverters;
import org.jdom2.Element;

/**
 * An ID Generator that generates timestamp based IDs.
 * The timestamps are stored as ISO date strings.  To uniqueify the ID, the millisecond
 * is incremented.
 * Do not use this ID generator in insert-heavy tables that may need more than 1000
 * dates per second.
 * @author gordon
 */
public class TimestampIdGenerator extends IdGenerator {

    AtomicLong lastDate = new AtomicLong(0l);
    
    @Override
    public boolean supports(Class<?> idType) {
        return String.class.equals(idType) ||
                Long.class.equals(idType) ||
                Date.class.equals(idType);
    }

    @Override
    public Object generateNewId(Class<?> idType) {
        long now = System.currentTimeMillis();
        
        long last;
        do{
            last = lastDate.get();
            if(now <= last){
                now = last + 1;
            }
            //keep going until a successful set.
        }while(!lastDate.compareAndSet(last, now));
        
        if(Long.class.equals(idType)){
            return now;
        }
        Date ret = new Date(now);
        if(Date.class.equals(idType)){
            return ret;
        }
        if(String.class.equals(idType)){
            return StringConverters.DateToStringConverter.convert(ret);
        }
        
        throw new UnsupportedOperationException("Unsupported ID type " + idType);
    }

    @Override
    public String idToString(Object id) {
        if(id == null){
            return "0";
        }
        Class<?> clazz = id.getClass();
        if(String.class.equals(clazz)){
            return (String)id;
        }
        Date ret;
        if(Date.class.equals(clazz)){
            ret = (Date)id;
        }
        else if(Long.class.equals(clazz)){
            ret = new Date((Long)id);
        }
        else{
            throw new UnsupportedOperationException("Unknown ID type " + id.getClass());
        }
        
        return StringConverters.DateToStringConverter.convert(ret);
    }

    @Override
    public Object stringToId(String id, Class<?> idType) {
        
        if(String.class.equals(idType)){
            return id;
        }
        
        Date date;
        if(id == null){
            date = new Date(0);
        }
        else{
            date = StringConverters.StringToDateConverter.convert(id);
        }
        
        if(Date.class.equals(idType)){
            return date;
        }
        if(Long.class.equals(idType)){
            return date.getTime();
        }
        
        throw new UnsupportedOperationException("Unknown ID type " + idType);
    }
    
    @Override
    public void saveState(Element state){
        state.setAttribute("maxId", Long.toString(this.lastDate.get()), Database.xFlatNs);
    }
    
    @Override
    public void loadState(Element state){
        String maxId = state.getAttributeValue("maxId", Database.xFlatNs);
        this.lastDate.set(Long.parseLong(maxId));
    }
}