/* 
*	Copyright 2013 Gordon Burgett and individual contributors
*
*	Licensed under the Apache License, Version 2.0 (the "License");
*	you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*	Unless required by applicable law or agreed to in writing, software
*	distributed under the License is distributed on an "AS IS" BASIS,
*	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*	See the License for the specific language governing permissions and
*	limitations under the License.
*/
package org.xflatdb.xflat.db;

import java.io.File;
import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.xflatdb.xflat.DatabaseConfig;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.XFlatConstants;
import org.xflatdb.xflat.XFlatException;
import org.xflatdb.xflat.convert.ConversionException;
import org.xflatdb.xflat.util.DocumentFileWrapper;

/**
 * A Factory which creates {@link TableMetadata} instances.
 * @author Gordon
 */
public class TableMetadataFactory {
    
    private XFlatDatabase db;
    private DatabaseConfig dbConfig;
    private DocumentFileWrapper wrapper;
    
    /**
     * Creates a new TableMetadataFactory with the given dependencies.
     * @param db The XFlatDatabase from which dependencies are retrieved when engines are provided.
     * @param metadataDirectory The metadata directory to which the factory should read and save table metadata.
     */
    public TableMetadataFactory(XFlatDatabase db, File metadataDirectory){
        this(db, db.getConfig(), new DocumentFileWrapper(metadataDirectory));
    }
    
    /**
     * Creates a new TableMetadataFactory with the given dependencies.
     * @param db The XFlatDatabase from which dependencies are retrieved when engines are provided.
     * @param config The XFlat Database configuration.
     * @param wrapper A Wrapper around the metadata directory allowing the factory to read and save table metadata.
     */
    TableMetadataFactory(XFlatDatabase db, DatabaseConfig config, DocumentFileWrapper wrapper){
        this.db = db;
        this.dbConfig= config;
        this.wrapper = wrapper;
    }
    
    /**
     * Creates a new TableMetadataFactory with no dependencies.
     */
    TableMetadataFactory(){
    }
    
    /**
     * Gets the metadata document for the given table name.
     * @param name The table name for which to get the metadata document.
     * @return A Document representing the metadata associated to the table.
     */
    public Document getMetadataDoc(String name){
        try {
             return this.wrapper.readFile(name + ".config.xml");
        } catch (IOException | JDOMException ex) {
            Log log = LogFactory.getLog(getClass());
            if(log.isInfoEnabled())
                log.info(String.format("corrupt metadata file: %s.config.xml in directory %s", name, this.wrapper.toString()), ex);
            
            return null;
        }
    }
    
    /**
     * Creates a TableMetadata for the given engine.  This TableMetadata object
     * does not have the ID generator or table config, so it can function only as
     * an EngineProvider.  Do not call {@link TableMetadata#getTable(java.lang.Class) } on the
     * resulting object.
     * @param name The name of the table.
     * @param engineFile The file where the engine data should be stored.
     * @return A new TableMetadata object.
     */
    public TableMetadata makeTableMetadata(String name, File engineFile){
        Document doc;
        
        try {
             doc = this.wrapper.readFile(name + ".config.xml");
        } catch (IOException | JDOMException ex) {
            Log log = LogFactory.getLog(getClass());
            if(log.isInfoEnabled())
                log.info(String.format("corrupt metadata file: %s.config.xml in directory %s", name, this.wrapper.toString()), ex);
            
            doc = null;
        }
        
        TableMetadata ret = new TableMetadata(name, db, engineFile);
        
        if(doc == null){
            //no need for config or ID generator
            ret.engineMetadata = new Element("engine", XFlatConstants.xFlatNs);
        }
        else{
            //load engine
            ret.engineMetadata = doc.getRootElement().getChild("engine", XFlatConstants.xFlatNs);
            if(ret.engineMetadata == null){
                ret.engineMetadata = new Element("engine", XFlatConstants.xFlatNs);
            }
        }
        
        return ret;
    }
    
    /**
     * Creates a TableMetadata for the given table information.
     * @param name The name of the table
     * @param engineFile The file locating the engine.
     * @param config The configuration of the table, null to use {@link TableConfig#DEFAULT}
     * @param idType The type of the ID property for the table.
     * @return A table metadata for the given table.
     */
    public TableMetadata makeTableMetadata(String name, File engineFile, TableConfig config, Class<?> idType){
        Document doc;
        TableMetadata ret;
        try {
             doc = this.wrapper.readFile(name + ".config.xml");
        } catch (IOException | JDOMException ex) {
            Log log = LogFactory.getLog(getClass());
            if(log.isInfoEnabled())
                log.info(String.format("regenerating corrupt metadata file: %s.config.xml in directory %s", name, this.wrapper.toString()), ex);
            
            doc = null;
        }
        
        if(doc == null){
            ret = makeNewTableMetadata(name, engineFile, config, idType);            
        }
        else{
            ret = makeTableMetadataFromDocument(name, engineFile, doc, config, idType);
        }        
        
        return ret;
    }
    
    private TableMetadata makeNewTableMetadata(String name, File engineFile, TableConfig config, Class<?> idType){

        TableMetadata ret = new TableMetadata(name, db, engineFile);
        
        config = config == null ? new TableConfig() : config;
        ret.config = config;

        //make ID Generator
        Class<? extends IdGenerator> generatorClass = config.getIdGenerator();
        if(generatorClass != null){
            ret.idGenerator = makeIdGenerator(generatorClass);
            if(idType != null && !ret.idGenerator.supports(idType)){
                throw new XFlatException("Id Generator " + generatorClass.getName() +
                        " does not support type " + idType);
            }
        }
        else {
            
            //pick using our strategy
            if(idType != null)
                for(Class<? extends IdGenerator> g : dbConfig.getIdGeneratorStrategy()){
                    IdGenerator gen = makeIdGenerator(g);
                    if(gen.supports(idType)){
                        ret.idGenerator = gen;
                        break;
                    }
                }
        
            if(ret.idGenerator == null){
                throw new XFlatException("Could not pick id generator for type " + idType);
            }
        }

        ret.engineMetadata = new Element("engine", XFlatConstants.xFlatNs);
        
        return ret;
    }

    private TableMetadata makeTableMetadataFromDocument(String name, File engineFile, Document metadata, TableConfig config, Class<?> idType){
        TableMetadata ret = new TableMetadata(name, db, engineFile);
        if(config == null){
            Element c = metadata.getRootElement().getChild("config", XFlatConstants.xFlatNs);
            try {
                config = TableConfig.FromElementConverter.convert(c);
            } catch (ConversionException ex) {
                throw new XFlatException("Cannot deserialize metadata for table " + name, ex);
            }
        }
        //else we already verified that config was equal to that stored in metadata

        ret.config = config;
        
        //load ID generator
        Class<? extends IdGenerator> generatorClass = null;
        Element g = metadata.getRootElement().getChild("generator", XFlatConstants.xFlatNs);
        if(g != null){
            String gClassStr = g.getAttributeValue("class", XFlatConstants.xFlatNs);
            if(gClassStr != null){
                try {
                    generatorClass = (Class<? extends IdGenerator>) TableMetadata.class.getClassLoader().loadClass(gClassStr);
                } catch (ClassNotFoundException ex) {
                    throw new XFlatException("Cannot load metadata: generator class could not be loaded", ex);
                }
            }
        }
        ret.idGenerator = makeIdGenerator(generatorClass);
        
        if(idType != null && !ret.idGenerator.supports(idType)){
            throw new XFlatException("Id Generator " + generatorClass + " does not support " + 
                    " ID type " + idType);
        }
        ret.idGenerator.loadState(g);

        //load engine
        ret.engineMetadata = metadata.getRootElement().getChild("engine", XFlatConstants.xFlatNs);
        if(ret.engineMetadata == null){
            ret.engineMetadata = new Element("engine", XFlatConstants.xFlatNs);
        }
        
        return ret;
    }

    /**
     * Saves the given metadata to disk.
     * @param metadata The metadata to save.
     * @throws IOException 
     */
    public void saveTableMetadata(TableMetadata metadata) throws IOException {
        Document doc = new Document();
        doc.setRootElement(new Element("metadata", XFlatConstants.xFlatNs));
        
        //save config
        if(metadata.config != null){
            Element cfg;
            try {
                cfg = TableConfig.ToElementConverter.convert(metadata.config);
            } catch (ConversionException ex) {
                throw new XFlatException("Cannot serialize table metadata", ex);
            }
            doc.getRootElement().addContent(cfg);
        }
        
        //save generator
        if(metadata.idGenerator != null){
            Element g= new Element("generator", XFlatConstants.xFlatNs);
            g.setAttribute("class", metadata.idGenerator.getClass().getName(), XFlatConstants.xFlatNs);
            metadata.idGenerator.saveState(g);

            doc.getRootElement().addContent(g);
        }
        
        //save engine
        Element e = metadata.engineMetadata.clone();
        
        doc.getRootElement().addContent(e);
        
        this.wrapper.writeFile(metadata.name + ".config.xml", doc);
    }
    
    private IdGenerator makeIdGenerator(Class<? extends IdGenerator> generatorClass){
        if(generatorClass == null){
            throw new XFlatException("generator class could not be loaded");
        }

        try {
            return generatorClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new XFlatException("Cannot load metadata: generator class could not be instantiated", ex);
        }
    }
}
