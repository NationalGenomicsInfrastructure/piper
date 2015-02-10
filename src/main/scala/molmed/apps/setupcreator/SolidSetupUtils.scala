package molmed.apps.setupcreator

import java.io.File
import java.io.FileOutputStream
import molmed.xml.setup.Project
import molmed.xml.setup.Metadata
import molmed.xml.setup.Platformunit
import molmed.xml.setup.Library
import molmed.xml.setup.Sample
import molmed.xml.setup.Seqfile
import molmed.xml.setup.Inputs

import scala.collection.JavaConversions._
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import molmed.apps.MakeSolidProjectHierarchy
import molmed.queue.setup.ReportReader
import molmed.utils.xsq._


//
//trait SetupUtils{
//  def createProject(): Project
//  def platformInfo(sampleInfo: MakeSolidProjectHierarchy.SampleInfo): String
//  def setMetaData(project: Project)(projectName: String,
//                                    seqencingPlatform: String,
//                                    sequencingCenter: String,
//                                    uppmaxProjectId: String,
//                                    uppmaxQoSFlag: String,
//                                    reference: File): Project
//  def createProjectXML(project: Project)(fileList: Seq[File]): Project
//
//  def writeToFile(project: Project, outputFile: File):Unit
//  def writeToStdOut(project: Project):Unit
//
//
//}

object SolidSetupUtils extends App{


  /**
   * Create a XML-serializable project instance
   */
  def createProject(): Project = {
    val project = new Project
    project
  }

  /**
   * Construct the platform id (unique sample identifier) from the
   * sample info.
   */
  private def platformInfo(sampleInfo: MakeSolidProjectHierarchy.SampleInfo): String = {
    sampleInfo.flowCellId + "." + sampleInfo.sampleName + "." + sampleInfo.lane
  }

  /**
   * Does this file match the assumed UUSNP format?
   * @file file
   *
   * A colorspace runfolder may look like so:
   *WESF00005_B1_FC2
    |-- L01
    |   `-- result
    |       |-- EstimateThroughput.stats
    |       |-- QVMetric.txt
    |       |-- WESF00005_B1_FC2_L01.xsq
    |       |-- WESF00005_B1_FC2_L01_Multiplex_.txt
    |       |-- panelmetrics
    |       `-- plots
    |-- L02
    |   `-- result
    |       |-- EstimateThroughput.stats
    |       |-- QVMetric.txt
    |       |-- WESF00005_B1_FC2_L02.xsq
    |       |-- WESF00005_B1_FC2_L02_Multiplex_.txt
    |       |-- panelmetrics
    |       `-- plots


   */
  def matchesDirContent(file: File): Boolean = {
    val resultDir = file.getParentFile()
    val laneDir = resultDir.getParentFile

    laneDir.getName().startsWith("L") &&
      resultDir.listFiles().exists(p =>
        p.getName() == "EstimateThroughput.stats" || p.getName() == "QVMetric.txt")
  }

  /**
   * Check runfolder naming convention
   * @file file
   */
  def matchesRootDirNamePattern(file: File): Boolean = {
    val runfolderDir = file.getParentFile()
    runfolderDir.getName().matches("^\\W{3,4}\\d{3,5}.*$")
  }

  /**
   * Set the meta data for a project
   * @param project	The project to a apply the meta data to.
   * @param projectName
   * @param	seqencingPlatform
   * @param sequencingCenter
   * @param uppmaxProjectId
   * @param uppmaxQoSFlag
   * @param reference
   * @return The project modified with the meta data.
   */
  def setMetaData(project: Project)(projectName: String,
                                    seqencingPlatform: String,
                                    sequencingCenter: String,
                                    uppmaxProjectId: String,
                                    uppmaxQoSFlag: String,
                                    reference: File): Project = {
    val projectMetaData = new Metadata()

    projectMetaData.setName(projectName)
    projectMetaData.setPlatform(seqencingPlatform)
    projectMetaData.setSequenceingcenter(sequencingCenter)
    projectMetaData.setUppmaxprojectid(uppmaxProjectId)
    projectMetaData.setUppmaxqos(uppmaxQoSFlag)
    projectMetaData.setReference(reference.getAbsolutePath())
    project.setMetadata(projectMetaData)

    project.setInputs(new Inputs)
    project
  }

  /**
   * Once the smapleInfoSet has been parsed, this class can be used
   * to create the xml hierarchy and add it to the project.
   * @param project The project to write to
   * @param sampleInfoSet The set of all samples
   */
  private def constructProjectHierarchy(
    project: Project,
    sampleInfoSet: Set[MakeSolidProjectHierarchy.SampleInfo]): Project = {

    /**
     * Helper method which recursively will go down through the project
     * hierarchy and add fastq file leaves from the SampleInfo instances.
     * It will create and add any other nodes along the way that has
     * not already been created.
     *
     * Since the xjc generated xml elements are not immutable by nature
     * this method will do some stuff, like adding to lists, which will actually
     * change the state of the project. Be aware that this is happening and
     * check for side effects.
     *
     * @param x 		A part of the project hierarchy. Since this is not
     * 				    enforced by a class bound, don't send the wrong stuff
     *         		    in here!
     * @param project 	The current project to add to.
     * @param sampleInfo A sample
     * @return A project with the the info in sampleInfo added to it.
     *
     */
    def constructHelper(
      x: Any,
      project: Project,
      sampleInfo: MakeSolidProjectHierarchy.SampleInfo): Project = {

      /**
       * See if there is a matching element in the collection
       * and create one if there is not.
       *
       * @param list
       * @param sampleInfo
       * @param predicate
       * @param constructNewA
       * @return The matching element
       */
      def findMatchingInCollection[A](
        list: java.util.List[A],
        sampleInfo: MakeSolidProjectHierarchy.SampleInfo,
        predicate: (A, MakeSolidProjectHierarchy.SampleInfo) => Boolean,
        constructNewA: (MakeSolidProjectHierarchy.SampleInfo => A)): A = {

        list.find(x => predicate(x, sampleInfo)).
          getOrElse({
            constructNewA(sampleInfo)
          })

      }

      /**
       * Add the element x if it's not already in the list.
       * @param list
       * @param x
       * @param sampleInfo
       * @param predicate
       * @returns The list with the element added if it was not already there.
       */
      def addIfNotThere[B](
        list: java.util.List[B],
        x: B,
        sampleInfo: MakeSolidProjectHierarchy.SampleInfo,
        predicate: (B, MakeSolidProjectHierarchy.SampleInfo) => Boolean): java.util.List[B] = {

        if (!list.exists(p => predicate(p, sampleInfo))) {
          list.add(x)
          list
        } else
          list

      }

      x match {
        case x: Platformunit => {

          val inputFiles = x.getSeqfile()

          inputFiles.add({
            val fastqFile = new Seqfile
            fastqFile.setPath(sampleInfo.seqfile.getAbsolutePath())
            fastqFile
          })

          project
        }
        case x: Library => {

          val platformUnits = x.getPlatformunit()

          def predicate =
            (p: Platformunit, sampleInfo: MakeSolidProjectHierarchy.SampleInfo) =>
              p.getUnitinfo() == platformInfo(sampleInfo)

          val platformUnit =
            findMatchingInCollection(
              platformUnits,
              sampleInfo,
              predicate,
              (sampleInfo: MakeSolidProjectHierarchy.SampleInfo) => {
                val pu = new Platformunit
                pu.setUnitinfo(platformInfo(sampleInfo))
                pu
              })

          addIfNotThere(platformUnits, platformUnit, sampleInfo, predicate)

          constructHelper(platformUnit, project, sampleInfo)
        }
        case x: Sample => {
          val libraries = x.getLibrary()

          def predicate =
            (p: Library, sampleInfo: MakeSolidProjectHierarchy.SampleInfo) =>
              p.getLibraryname() == sampleInfo.library

          val library =
            findMatchingInCollection(
              libraries,
              sampleInfo,
              predicate,
              (sampleInfo: MakeSolidProjectHierarchy.SampleInfo) => {
                val l = new Library
                l.setLibraryname(sampleInfo.library)
                l
              })

          addIfNotThere(libraries, library, sampleInfo, predicate)

          constructHelper(library, project, sampleInfo)
        }
        case x: Inputs => {
          val samples = x.getSample()

          def predicate =
            (p: Sample, sampleInfo: MakeSolidProjectHierarchy.SampleInfo) =>
              p.getSamplename() == sampleInfo.sampleName

          val sample =
            findMatchingInCollection(
              samples,
              sampleInfo,
              predicate,
              (s: MakeSolidProjectHierarchy.SampleInfo) =>
                {
                  val s = new Sample
                  s.setSamplename(sampleInfo.sampleName)
                  s
                })

          addIfNotThere(samples, sample, sampleInfo, predicate)

          constructHelper(sample, project, sampleInfo)
        }
        case x: Project => {
          val inputs = x.getInputs()
          constructHelper(inputs, project, sampleInfo)
        }
      }

    }

    sampleInfoSet.foldLeft(project)((project, sampleInfo) => {
      constructHelper(project, project, sampleInfo)
    })

  }


  /**
   * This will parse a list of FASTQ files assuming the uu snp formatting (see
   * below) and create a project xml.
   *
   *
   * @param project The project to add the info to
   * @param fileList a list of FASTQ files
   * @return a Project instance
   */
  def createProjectXML(project: Project)(fileList: Seq[File]): Project = {
    /**
     * 
     * File structure of a sequencing output dir may look like this.
     *|-- L01
     *|   |-- result
     *|       |-- EstimateThroughput.stats
     *|       |-- QVMetric.txt
     *|       |-- WESF00006_B1_L01.xsq
     *|       |-- WESF00006_B1_L01_Multiplex_.txt
     *|       |-- panelmetrics
     *|       |-- plots
     *|-- L02
     *|   |-- result
     *|       |-- EstimateThroughput.stats
     *|       |-- QVMetric.txt
     *|       |-- WESF00006_B1_L02.xsq
     *|       |-- WESF00006_B1_L02_Multiplex_.txt
     *|       |-- panelmetrics
     *|       |-- plots
     *|-- L03
     *   |-- result
     *        |-- EstimateThroughput.stats
     *        |-- QVMetric.txt
     *        |-- WESF00006_B1_L03.xsq
     *        |-- WESF00006_B1_L03_Multiplex_.txt
     *        |-- panelmetrics
     *        |-- plots
     *
     *And list of samples may relate like this:
     *L01/result/WESF00006_B1_L01.xsq
     *    lib	WES06A	idx:13	Fragment	60	color
     *    lib	WES06B	idx:14	Fragment	60	color
     *    lib	WES06C	idx:15	Fragment	60	color
     *    lib	WES06D	idx:16	Fragment	60	color
     *L02/result/WESF00006_B1_L02.xsq
     *    lib	WES06A	idx:13	Fragment	60	color
     *    lib	WES06B	idx:14	Fragment	60	color
     *    lib	WES06C	idx:15	Fragment	60	color
     *    lib	WES06D	idx:16	Fragment	60	color
     *L03/result/WESF00006_B1_L03.xsq
     *    lib	WES06A	idx:13	Fragment	60	color
     *    lib	WES06B	idx:14	Fragment	60	color
     *    lib	WES06C	idx:15	Fragment	60	color
     *    lib	WES06D	idx:16	Fragment	60	color

     */



    val fileInfo =
      for {
        xsqFile<- fileList
        xsq = new XSQFile(xsqFile.getAbsolutePath)
        metaData = xsq.runMetadata
        lane = metaData.get("LaneNumber").get.toString.toInt
        flowCellId = metaData.get("FlowcellAssignment").get.toString
        date = metaData.get("RunEndTime").get.toString
        xsqLib <- xsq.getLibraries

      } yield {

        val sampleName = xsqLib.name

        val library = xsqLib.getNameAndBarCode

        val index = xsqLib.indexName.get

        val read = 1 // TODO: this is just to mimic fastq setup, find a better way.

        new MakeSolidProjectHierarchy.SampleInfo(
          sampleName,
          library,
          lane,
          date,
          flowCellId,
          index,
          xsqFile,
          read)
      }

    //createXMLFromFileInfo(project, fileInfo.toSet)
    constructProjectHierarchy(project, fileInfo.toSet)
  }

  /**
   * Writes the project to a specified xml file.
   * @param project		The project to write
   * @param outputFile	The file to write to.
   */
  def writeToFile(project: Project, outputFile: File) = {
    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val os = new FileOutputStream(outputFile)
    marshaller.marshal(project, os)
    os.close()

  }

  /**
   * Writes the project to stdout. Useful for debugging.
   * @param project		The project to write
   */
  def writeToStdOut(project: Project) = {
    // The xml marshaller is used to create the xml instance
    val context = JAXBContext.newInstance(classOf[Project])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

    val os = new FileOutputStream(new File("/dev/stdout"))
    marshaller.marshal(project, os)
    os.close()

  }
}
