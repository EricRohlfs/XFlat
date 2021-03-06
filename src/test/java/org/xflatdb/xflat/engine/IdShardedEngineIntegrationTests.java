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
package org.xflatdb.xflat.engine;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.xflatdb.xflat.ShardsetConfig;
import org.xflatdb.xflat.Table;
import org.xflatdb.xflat.TableConfig;
import org.xflatdb.xflat.db.BigIntIdGenerator;
import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.query.NumericIntervalProvider;
import org.xflatdb.xflat.query.XPathQuery;
import org.jdom2.Element;
import org.junit.BeforeClass;
import org.junit.Test;
import test.Foo;
import test.Utils;
import static org.junit.Assert.*;
import org.xflatdb.xflat.Id;
import org.xflatdb.xflat.db.TimestampIdGenerator;

/**
 *
 * @author Gordon
 */
public class IdShardedEngineIntegrationTests {
    static File workspace = new File(new File("DbIntegrationTests"), "IdShardedEngineIntegrationTests");
    
    static String tbl = "table";
    
    @BeforeClass
    public static void setUpClass(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
        }
    }
    
    private XFlatDatabase getDatabase(String testName){
        return getDatabase(testName, TableConfig.DEFAULT
                .withIdGenerator(BigIntIdGenerator.class)
                .sharded(ShardsetConfig.by(XPathQuery.Id, Integer.class, NumericIntervalProvider.forInteger(2, 100))));
    }
    
    private XFlatDatabase getDatabase(String testName, TableConfig config){
        File dbDir = new File(workspace, testName);
        XFlatDatabase ret = (XFlatDatabase)XFlatDatabase.Build(dbDir)
            .withTableConfig(tbl, config)
            .create();
        
        ret.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        ret.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        return ret;
    }
    
    
    @Test
    public void testInsertRetrieve_SingleShard_OneFileCreated() throws Exception {
        String testName = "testInsertRetrieve_SingleShard_OneFileCreated";
        System.out.println(testName);
        
        XFlatDatabase db = getDatabase(testName);
        
        Table<Foo> table = db.getTable(Foo.class, tbl);
        
        Foo foo = new Foo();
        foo.fooInt = 1;
        
        table.insert(foo);
        
        Foo foo2 = table.find(foo.getId());
        
        assertEquals("should retrieve same data", foo, foo2);
        
        db.shutdown();
        
        File shardDir = new File(new File(workspace, testName), tbl + ".xml");
        assertTrue("shard directory should exist", shardDir.exists());
        assertTrue("shard directory should be a directory", shardDir.isDirectory());
        
        File[] shards = shardDir.listFiles();
        assertEquals("should be one shard", 1, shards.length);
        assertTrue("Should be named after the range of data", new File(shardDir, "-98.xml").exists());
    }
    
    @Test
    public void testInsertRetrieve_MultipleShards_MultipleFilesCreated() throws Exception {
        String testName = "testInsertRetrieve_MultipleShards_MultipleFilesCreated";
        System.out.println(testName);
        
        XFlatDatabase db = getDatabase(testName);
        
        
        Table<Foo> table = db.getTable(Foo.class, tbl);
        
        Foo foo = new Foo();
        foo.fooInt = 1;        
        table.insert(foo);
        
        foo = new Foo();
        foo.fooInt = 2;
        table.insert(foo);
        
        foo = new Foo();
        foo.fooInt = 3;
        table.insert(foo);
        
        List<Foo> fooList = table.findAll(XPathQuery.gt(XPathQuery.Id, 1));
        
        //can't trust ordering of a query
        Collections.sort(fooList, new Comparator<Foo>(){
            @Override
            public int compare(Foo o1, Foo o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        
        assertEquals("Should get 2 foos", 2, fooList.size());
        assertEquals("should retrieve same data", 2, fooList.get(0).fooInt);
        assertEquals("should retrieve same data", 3, fooList.get(1).fooInt);
        
        
        db.shutdown();
        
        File shardDir = new File(new File(workspace, testName), this.tbl + ".xml");
        assertTrue("shard directory should exist", shardDir.exists());
        assertTrue("shard directory should be a directory", shardDir.isDirectory());
        
        File[] shards = shardDir.listFiles();
        assertEquals("should be two shards", 2, shards.length);
        assertTrue("Should be named after the range of data", new File(shardDir, "-98.xml").exists());
        assertTrue("Should be named after the range of data", new File(shardDir, "2.xml").exists());
    }
    
    
    @Test
    public void testInsertRetrieve_DifferentIdRepresentation_UsesIdGeneratorForConversion() throws Exception {
        String testName = "testInsertRetrieve_DifferentIdRepresentation_UsesIdGeneratorForConversion";
        
        System.out.println(testName);
        
        XFlatDatabase db = getDatabase(testName, new TableConfig()
                                    .withIdGenerator(TimestampIdGenerator.class)
                                    .sharded(ShardsetConfig.by(XPathQuery.Id, Long.class, NumericIntervalProvider.forLong(2, 100))));
        
        
        Table<FooLongId> table = db.getTable(FooLongId.class, tbl);
        
        FooLongId foo = new FooLongId();
        foo.setFooString("junk");
        
        table.insert(foo);
        
        FooLongId foo2 = table.find(foo.getId());
        
        assertEquals("should retrieve same data", foo, foo2);
        
        db.shutdown();
    }
    
    
}

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
class FooLongId {
    
    @Id
    private Long id;
    
    public Long getId(){
        return id;
    }
    
    @XmlElement
    private String fooString;
    /**
     * Gets the fooString.
     */
    public String getFooString(){
        return this.fooString;
    }
    /**
     * Sets the fooString.
     */
    public void setFooString(String fooString){
        this.fooString = fooString;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.id);
        hash = 97 * hash + Objects.hashCode(this.fooString);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FooLongId other = (FooLongId) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.fooString, other.fooString)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "FooLongId{" + "id=" + id + ", fooString=" + fooString + '}';
    }    
}
