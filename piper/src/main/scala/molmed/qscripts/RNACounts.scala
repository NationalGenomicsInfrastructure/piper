package molmed.qscripts

import java.io.File
import java.io.PrintWriter
import java.util.regex.Pattern
import scala.collection.JavaConversions._
import org.broadinstitute.gatk.queue.extensions.picard._
import org.broadinstitute.gatk.queue.function.InProcessFunction
import org.broadinstitute.gatk.queue.function.ListWriterFunction
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.util.QScriptUtils
import molmed.queue.setup._
import molmed.utils.CufflinksUtils
import molmed.utils.GeneralUtils
import molmed.utils.ReadGroupUtils
import molmed.utils.ReadGroupUtils._
import molmed.utils.TophatAligmentUtils
import molmed.utils.UppmaxConfig
import molmed.utils.UppmaxJob
import molmed.config.UppmaxXMLConfiguration
import molmed.utils.Uppmaxable
import molmed.config.FileAndProgramResourceConfig
import molmed.report.ReportGenerator

class RNACounts
    extends QScript
    with UppmaxXMLConfiguration
    with FileAndProgramResourceConfig {

  qscript =>

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
  var transcripts: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  /**
   * **************************************************************************
   * --------------------------------------------------------------------------
   * 		Arguments and Flags
   * --------------------------------------------------------------------------
   * **************************************************************************
   */

  @Argument(doc = "Perform downsampling to the given number of reads.", shortName = "d", fullName = "downsample", required = false)
  var downsampling: Int = -1

  @Argument(doc = "Use to find novel transcripts. If not will only find transcripts supplied in annotations file.", fullName = "findNovel", shortName = "fn", required = false)
  var findNovelTranscripts: Boolean = false

  @Argument(doc = "Do fussion search using tophat", fullName = "fusionSearch", shortName = "fs", required = false)
  var fusionSearch: Boolean = false

  @Argument(doc = "Only do the aligments - useful when there is more data to be delivered in a project", fullName = "onlyAlignments", shortName = "oa", required = false)
  var onlyAlignment: Boolean = false

  @Argument(doc = "library type. Options: fr-unstranded (default), fr-firststrand, fr-secondstrand", fullName = "library_type", shortName = "lib", required = false)
  var libraryType: String = "fr-unstranded"

  @Argument(doc = "Run cuffmerge to merge together the cufflink assemblies.", fullName = "merge", shortName = "me", required = false)
  var merge: Boolean = false

  @Argument(doc = "Run cutadapt", fullName = "cutadapt", shortName = "ca", required = false)
  var runCutadapt: Boolean = false

  @Argument(doc = "Number of threads tophat should use", fullName = "tophat_threads", shortName = "tt", required = false)
  var tophatThreads: Int = 1

  /**
   * **************************************************************************
   * --------------------------------------------------------------------------
   * 		Input and output files/paths
   * --------------------------------------------------------------------------
   * **************************************************************************
   */

  @Argument(doc = "GTF file with transcripts to ignore, e.g. rRNA, mitochondrial transcripts etc.", fullName = "mask", shortName = "m", required = false)
  var maskFile: Option[File] = None

  @Argument(doc = "Output path for the cufflink processed files.", fullName = "cufflink_output_directory", shortName = "cufflink_outputDir", required = false)
  var outputDirCufflink: String = ""
  def getOutputDirCufflink: File = if (outputDirCufflink.isEmpty()) new File("cufflink/") else new File(outputDirCufflink + "/")

  @Argument(doc = "Output path for the cuffmerged files.", fullName = "cuffmerge_output_directory", shortName = "cuffmerge_outputDir", required = false)
  var outputDirCuffmerge: String = ""
  def getOutputDirCuffmerge: File = if (outputDirCuffmerge.isEmpty()) new File("cuffmerge/") else new File(outputDirCuffmerge + "/")

  @Argument(doc = "Output path for the processed BAM files.", fullName = "bam_output_directory", shortName = "bamOutputDir", required = false)
  var outputDirProcessedBAM: String = ""
  def getOutputDirBAM: File = if (outputDirProcessedBAM.isEmpty()) new File("raw_alignments/") else new File(outputDirProcessedBAM + "/")

  @Argument(doc = "Output path for the QC results", fullName = "qc_output_directory", shortName = "qcOutputDir", required = false)
  var outputDirQCResult: String = ""
  def getOutputDirQCResult: File = if (outputDirQCResult.isEmpty()) new File("RNA_QC/") else new File(outputDirQCResult + "/")

  @Argument(doc = "intervalFile for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
  var rRNATargetsFile: File = _

  /**
   * Help methods
   */

  /**
   *
   * Create folders for sample and perform cufflink operation
   *
   */
  private def runCuffLinks(cufflinksUtils: CufflinksUtils, bamFile: File, sampleName: String, outDir: File): File = {
    var cufflinkOutputDir: File = new File(outDir + "/" + sampleName)
    if (!cufflinkOutputDir.exists()) {
      cufflinkOutputDir.mkdirs()
    }

    val placeHolderFile = new File(cufflinkOutputDir + "/qscript_cufflinks.stdout.log")

    add(cufflinksUtils.cufflinks(this.cufflinksPath, this.maskFile, bamFile, cufflinkOutputDir, placeHolderFile, this.findNovelTranscripts))
    placeHolderFile
  }

  /**
   *
   * Create folders for sample and perform QC
   *
   */
  private def runQC(bamFile: File, index: File, sampleName: String, sample: SampleAPI, outDir: File, generalUtils: GeneralUtils): File = {
    val sampleOutputDirQC = new File(outDir + "/" + sampleName)
    if (!sampleOutputDirQC.exists())
      sampleOutputDirQC.mkdir()
    val placeHolderFile: File = new File(sampleOutputDirQC + "/qscript_RNASeQC.stdout.log")
    add(generalUtils.RNA_QC(bamFile, index, sampleName, this.rRNATargetsFile, this.downsampling, sample.getReference(), sampleOutputDirQC, this.transcripts, placeHolderFile, this.pathToRNASeQC))
    placeHolderFile
  }

  /**
   * **************************************************************************
   * Main script
   * **************************************************************************
   */

  def script() {

    //-------------------------------------------------------------------------
    //                      Create output folders
    //-------------------------------------------------------------------------

    //Check for or create output dir for process BAM files
    if (!getOutputDirBAM.exists()) {
      getOutputDirBAM.mkdir()
    }

    if (!onlyAlignment) {
      //Check for or create output dir for QC result
      if (!getOutputDirQCResult.exists()) {
        getOutputDirQCResult.mkdirs()
      }

      //Check for or create output dir for cufflink
      if (!getOutputDirCufflink.exists()) {
        getOutputDirCufflink.mkdirs()
      }
    }

    //Import uppmxax-settings,samples and project info
    val uppmaxConfig = loadUppmaxConfigFromXML()

    // Get default paths to resources from global config xml
    // We don't need the default DNA resource files.
    val resourceMap =
      this.configureResourcesFromConfigXML(
        this.globalConfig,
        doNotLoadDefaultResourceFiles = true)

    val sampleMap: Map[String, Seq[SampleAPI]] = setupReader.getSamples()
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)
    //Setup cufflink
    val cufflinksUtils = new CufflinksUtils(projectName, transcripts, libraryType, uppmaxConfig)
    //Setup TopHat alignment
    val tophatUtils = new TophatAligmentUtils(tophatPath, tophatThreads,
      projectName, uppmaxConfig)

    // Assumes same reference is used for all samples
    // which should be true when using the new setup format
    val reference = sampleMap.head._2.head.getReference

    // Create the version report
    // @TODO Assumes that pipeline_output is the set output path
    // this should be fixed with a general base path in the future.
    val reportFile = new File("pipeline_output/logs/version_report.txt")
    ReportGenerator.constructRNACountsReport(resourceMap, reference, transcripts, maskFile, reportFile)

    var qcPlacehodlerList: Seq[File] = Seq()
    var cohortList: Seq[File] = Seq()

    for ((sampleName, samples) <- sampleMap) {
      var counter = 1
      for (sample <- samples) {
        /**
         * Make sure that if there are several instances of a sample
         * they are processed separately with folder names:
         * <original sample name>_<int>
         */
        val sampleNameCounter = if (samples.size == 1) sampleName else sampleName + "_" + counter
        counter += 1

        //Align fastq file(s)
        val bamFile: File =
          if (runCutadapt)
            tophatUtils.align(this, this.libraryType, this.transcripts, getOutputDirBAM, sampleNameCounter, generalUtils.cutSamplesUsingCuteAdapt(this, this.cutadaptPath, sample, getOutputDirBAM, syncPath), this.fusionSearch)
          else
            tophatUtils.align(this, this.libraryType, this.transcripts, getOutputDirBAM, sampleNameCounter, sample, this.fusionSearch)

        cohortList :+= bamFile

        if (!onlyAlignment) {
          //Assemble transcripts
          runCuffLinks(cufflinksUtils, bamFile, sampleName, getOutputDirCufflink)

          //Create index for BAM file
          val index = new File(bamFile.replace(".bam", ".bai"))
          add(generalUtils.createIndex(bamFile, index))

          // Perform QC on processed BAM file
          qcPlacehodlerList :+= runQC(bamFile, index, sampleName, sample, getOutputDirQCResult, generalUtils)
        }
      }
    }
    //Gather QC result into one file
    if (!onlyAlignment) {
      val aggregatedMetrics = new File(getOutputDirQCResult + "/aggregated_metrics.tsv")
      add(generalUtils.createAggregatedMetrics(qcPlacehodlerList, getOutputDirQCResult, aggregatedMetrics))
    }

    // output a BAM list with all the processed files
    val cohortFile = new File(getOutputDirBAM + "/cohort.list")
    add(writeList(cohortList, cohortFile))
  }

  /**
   * Special list writer class which uses a place holder to make sure it waits for input.
   */
  case class writeList(@Input inBams: Seq[File], outBamList: File) extends ListWriterFunction {
    this.inputFiles = inBams
    this.listFile = outBamList
    override def jobRunnerJobName = projectName.get + "_bamList"
  }
}