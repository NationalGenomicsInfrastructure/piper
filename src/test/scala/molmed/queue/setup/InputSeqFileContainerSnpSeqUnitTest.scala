package molmed.queue.setup

import org.testng.annotations.Test
import org.testng.Assert
import java.io.File
import molmed.queue.SnpSeqBaseTest

class InputSeqFileContainerSnpSeqUnitTest{
    
    val baseTest = SnpSeqBaseTest
    
    @Test
    def testIsMatePaired() {    
        
        val mate1: File = new File(baseTest.pathToMate1)      
        val mate2: File = new File(baseTest.pathToMate1)
        val sampleName: Option[String] = Some("testSample")
                      
        // Class under test
        val readPairContainer: InputSeqFileContainer = new InputSeqFileContainer(Seq(mate1, mate2), sampleName, hasPair = true)
        
        // Run the test
        assert(readPairContainer.isMatePaired())
    }
    
    @Test
    def testIsNotMatePaired() {
    	
        val mate1: File = new File(baseTest.pathToMate1)
        val mate2: File = null
        val sampleName: Option[String] = Some("testSample")
      // Class under test
        val readPairContainer: InputSeqFileContainer = new InputSeqFileContainer(Seq(mate1, mate2), sampleName)
        
        // Run the test
        assert(!readPairContainer.isMatePaired())
    }

}