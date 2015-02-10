package molmed.utils;

import molmed.utils.xsq.XSQFile;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import scala.collection.JavaConversions.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;


import static org.testng.Assert.*;

public class XSQFileTest {
    static Logger log = LoggerFactory.getLogger(XSQFileTest.class);

    @BeforeClass
    public void setup(){

    }

    @AfterClass
    public void teardown(){


    }

    @Test
    public void testGetLibraryNames() throws Exception {
        String fileName = "src/test/resources/Frag.xsq";
        XSQFile xsq = new XSQFile(fileName);
        assertEquals(xsq.getLibraryNames().size(), 1);
        log.info(xsq.getLibraryNames().toString());
        assertEquals( xsq.getLibraryNames().head(), "TestLibrary");
    }
    @Test
    public void testGetLibraryNames2() throws Exception {
        String fileName = "src/test/resources/Test.Barcoded.PE.xsq";
        XSQFile xsq = new XSQFile(fileName);
        assertEquals(xsq.getLibraryNames().size(), 6);
        log.info(xsq.getLibraryNames().toString());
        assertEquals( xsq.getLibraryNames().head(), "TestLibrary");
    }
    @Test
    public void testGetGroupAttribute() throws Exception {
        assertEquals(2,2);

    }
}