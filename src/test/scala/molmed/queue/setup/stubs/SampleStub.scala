package molmed.queue.setup.stubs

import molmed.queue.setup.SampleAPI
import molmed.queue.setup.InputSeqFileContainer
import java.io.File
import molmed.queue.setup.ReadGroupInformation
import org.apache.commons.lang3.NotImplementedException

class SampleStub(sampleName: String) extends SampleAPI{
    
    var readPairContainer: InputSeqFileContainer = null
    var bwaReadGroupInfo: String = ""
    var tophatReadgroupInfo: String = ""
    var reference: File = null    
    
    def getSampleName(): String = sampleName
    def getInputSeqFiles(): InputSeqFileContainer = readPairContainer
    def getBwaStyleReadGroupInformationString: String = bwaReadGroupInfo
    def getTophatStyleReadGroupInformationString(): String = tophatReadgroupInfo
    def getReference: File = reference
    def getReadGroupInformation(): ReadGroupInformation = throw new NotImplementedException("getReadGroupInformation not implemented")
    
    
    override
    def hashCode(): Int = {
        sampleName.hashCode()
    }
    
}