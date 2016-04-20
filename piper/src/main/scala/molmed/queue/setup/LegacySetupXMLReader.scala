package molmed.queue.setup

import java.io.File
import molmed.xml.illuminareport.SequencingReport
import java.io.StringReader
import javax.xml.bind.JAXBContext
import scala.collection.mutable.Buffer
import molmed.xml.illuminareport.Read
import javax.xml.bind.Marshaller
import collection.JavaConversions._
import java.io.FileNotFoundException
import molmed.utils.GeneralUtils
import molmed.xml.setup.legacy.Samplefolder
import molmed.xml.setup.legacy.Project

/**
 * @TODO
 * At some point this will need to be removed. Right now however
 * being able to switch automatically between the old and the 
 * new format seems like a good idea to make sure that we can go
 * back to old projects without having to recreate the pipelineSetup.xml
 * /JD 20140821
 * 
 * A Setup reader class which reads a legacy setup xml, used for configuring 
 * the piper pipeline, based on the xml schema specified in
 * src/main/resources/PipelineSetupSchema.xsd.
 *
 * The underlying classes, such as for example "Project" are located in the
 * molmed.xml.setup namespace and are generated from the xml schema using xjc,
 * which means that changes to the xml schema requires regeneration of the
 *  code, and possibly changes to this reader class.
 *
 *  @constructor create a new NewSetupXMLReader specifying the file to read.
 *  @param setupXML a setup xml file following the PipelineSetupSchema.xsd schema
 */
class LegacySetupXMLReader(setupXML: File) extends SetupXMLReaderAPI {

  // XML related fields         
  private val context = JAXBContext.newInstance(classOf[Project])
  private val unmarshaller = context.createUnmarshaller()
  private val setupReader = new StringReader(scala.io.Source.fromFile(setupXML).mkString)
  private val project = unmarshaller.unmarshal(setupReader).asInstanceOf[Project]

  // Fields containing information on the runfolders/samples etc kept in a convenient form.

  private val sampleList = project.getInputs().getRunfolder().flatMap(f => f.getSamplefolder())

  private val runFolderList = project.getInputs().getRunfolder().toList

  private val runFolderReportToSampleListMap: Map[File, java.util.List[molmed.xml.setup.legacy.Samplefolder]] =
    runFolderList.map(runFolder => {
      (new File(runFolder.getReport()), runFolder.getSamplefolder())
    }).toMap

  // ----------------------------------
  // Implementations of the API methods
  // ----------------------------------

  def getPlatform(): String = {
    project.getMetadata().getPlatfrom()
  }

  def getSequencingCenter(): String = {
    project.getMetadata().getSequenceingcenter()
  }

  def getProjectName(): Option[String] = {
    val projectName = project.getMetadata().getName()
    if (projectName.isEmpty()) None else Some(projectName)
  }

  def getUppmaxQoSFlag(): Option[String] = {
    val uppmaxQoSFlag = project.getMetadata().getUppmaxqos()
    if (uppmaxQoSFlag.isEmpty()) None else Some(uppmaxQoSFlag)
  }

  def getSamples(): Map[String, Seq[Sample]] = {

    // ------------------------------------------------------------
    // Helper methods - see below for the actual top function call.
    // ------------------------------------------------------------

    /**
     * Get's all sample entries related to a single sample name
     */
    def getSampleList(sampleName: String): Seq[Sample] = {

      /**
       * Checks a map of run folders and their associated sample folders for
       * samples and creates a list of Sample instances taking lanes etc into account.
       */
      def getSamplesFromAllRunFolders(sampleName: String, runFolderToSampleFolderMap: Map[File, java.util.List[Samplefolder]]): List[Sample] = {
        runFolderToSampleFolderMap.flatMap(tupple => {

          val report = tupple._1
          val sampleFolderList = tupple._2.filter(s => s.getName().equalsIgnoreCase(sampleName))

          val reportReader = ReportReader(report)

          val sampleList: Seq[Sample] = sampleFolderList.flatMap(sampleFolder => {

            val sampleFolderName = sampleFolder.getName()

            def buildSampleList(sampleFolderName: String): List[Sample] =
              reportReader.getLanes(sampleFolderName).map(lane => {
                val readPairContainer = buildReadPairContainer(sampleFolder, lane)
                val readGroupInfo = buildReadGroupInformation(sampleFolderName, lane, reportReader)
                new Sample(sampleFolderName, getReference(sampleFolderName), readGroupInfo, readPairContainer)
              })

            buildSampleList(sampleFolderName)
          })
          sampleList
        }).toList
      }

      getSamplesFromAllRunFolders(sampleName, runFolderReportToSampleListMap)
    }

    //  ------------------------------------------------------------
    //  The actual method
    //  ------------------------------------------------------------
    //  For every unique sample in the file, create a sample list
    //  Return a map of sample names -> samples

    val distinctSampleNames = sampleList.map(f => f.getName()).distinct.toList

    assert(distinctSampleNames.size >= 1, "Did not find any sample names.")

    val sampleListMap = distinctSampleNames.map(sampleName => {
      (sampleName, getSampleList(sampleName))
    }).toMap

    assert(!sampleListMap.isEmpty, "Sample name to list map was empty.")

    sampleListMap
  }

  def getReference(sampleName: String): File = {
    val matchingSamples = sampleList.filter(p => p.getName().equals(sampleName))
    val referenceForSample = matchingSamples.map(sample => sample.getReference()).distinct
    assert(referenceForSample.size != 0, "Did not find reference for Sample name: " + sampleName)
    assert(referenceForSample.size == 1, "Found more than reference for the same sample. Sample name: " + sampleName)
    new File(referenceForSample(0)).getAbsoluteFile()
  }

  def getUppmaxProjectId(): String = {
    project.getMetadata().getUppmaxprojectid()
  }

  /**
   * Private help methods used to construct ReadGroupInformations and ReadPaircontainer objects
   */

  private def buildReadGroupInformation(sampleName: String, lane: Int, illuminaXMLReportReader: ReportReaderAPI): ReadGroupInformation = {

    val readGroupId = illuminaXMLReportReader.getReadGroupID(sampleName, lane)
    val sequencingCenter = this.getSequencingCenter()
    val readLibrary = illuminaXMLReportReader.getReadLibrary(sampleName, lane)
    val platform = this.getPlatform()
    val numerOfReadsPassedFilter = illuminaXMLReportReader.getNumberOfReadsPassedFilter(sampleName, lane)
    val platformUnitId = illuminaXMLReportReader.getPlatformUnitID(sampleName, lane)

    new ReadGroupInformation(sampleName, readGroupId, sequencingCenter, readLibrary, platform, platformUnitId, numerOfReadsPassedFilter)

  }

  /**
   * Construct a read pair container for the specified sample folder.
   * @param  sampleFolder	The sample folder to check
   * @param	 lane			The lane in sample folder to get the sample for
   * @return A ReadPairContainer for the sample from the lane.
   */
  private def buildReadPairContainer(
    sampleFolder: Samplefolder,
    lane: Int): ReadPairContainer = {

    val sampleName = sampleFolder.getName()
    val folder = new File(sampleFolder.getPath())

    require(folder.isDirectory(), folder + " was not a directory.")
    require(
      folder.getName().startsWith("Sample_"),
      "A sample folder needs to be prefixed with Sample_. The name was: " +
        folder.getName())

    def getFastq(lane: Int, read: Int): List[File] =
      folder.listFiles().
        filter(f => {
          val name = f.getName()
          name.contains(
            "_L" + GeneralUtils.getZerroPaddedIntAsString(lane, 3) +
              "_R" + read + "_")
        }).toList

    val fastq1: List[File] = getFastq(lane, 1)
    val fastq2: List[File] = getFastq(lane, 2)

    val readPairContainer =
      (fastq1.size, fastq2.size) match {
        case (1, 1) =>
          new ReadPairContainer(
            fastq1.get(0).getAbsoluteFile(),
            fastq2.get(0).getAbsoluteFile(),
            sampleName)
        case (1, 0) =>
          new ReadPairContainer(fastq1.get(0), null, sampleName)
        case m: (Int, Int) if m._1 + m._2 > 2 =>
          throw new IllegalArgumentException(
            "Found more than two hits for sample: " + sampleName +
              " and lane: " + lane + ". This might happen if the same sample" +
              "was sequenced multiple time in the same lane but with " +
              "different indicies. This is currently not supported by " +
              "piper. Please manually fix this problem and try again. ")
        case _ =>
          throw new FileNotFoundException(
            "Problem with read pairs in folder: " +
              folder.getAbsolutePath() +
              " could not find suitable files. \n" +
              "the sample name was: " + sampleName +
              " and the sample lane: " + lane + "fastq1: " +
              fastq1 + "fastq2: " + fastq2)
      }

    readPairContainer
  }
}