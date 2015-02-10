package molmed.apps

import java.io.File

import molmed.apps.setupcreator.SolidSetupUtils
import molmed.utils.xsq.XSQFile

/**
 * Created by vql on 21/01/15.
 */


object ListXSQ extends App{
   val xsq = new XSQFile("src/test/resources/Test.Barcoded.PE.xsq")
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

    SolidSetupUtils.writeToStdOut(projectXML)

}
