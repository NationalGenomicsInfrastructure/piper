package molmed.apps.setupcreator

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.nio.file.{Paths,Files}


import org.testng.Assert._

class SolidSetupUtilsTest {

    @Test
    def testCreateProjectXML() {
        val proj = SolidSetupUtils.createProject()
        val project = SolidSetupUtils.setMetaData(proj)(
            projectName = "TestProject",
            seqencingPlatform = "SOLiD",
            sequencingCenter = "AfMD",
            uppmaxProjectId = "WESF00010",
            uppmaxQoSFlag ="Unknown",
            reference = new File("src/test/resources/testdata/exampleFASTA.fasta")

        )
        val fileList = Seq(new File("src/test/resources/Test.Barcoded.PE.xsq"))
        val projectXML = SolidSetupUtils.createProjectXML(project)(fileList)
        val fn = "src/test/resources/AfMDSetup.xml"
        val outFile = new File(fn)
        outFile.createNewFile()
        SolidSetupUtils.writeToFile(projectXML,outFile)
        assertTrue(Files.exists(Paths.get(fn)))
    }

    @Test
    def testCreateProject() {


    }
}