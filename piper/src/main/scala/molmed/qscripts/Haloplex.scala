package molmed.qscripts

import java.io.FileNotFoundException
import java.io.PrintWriter
import scala.collection.JavaConversions._
import scala.io.Source
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk.BamGatherFunction
import org.broadinstitute.gatk.queue.extensions.gatk.BaseRecalibrator
import org.broadinstitute.gatk.queue.extensions.gatk.ClipReads
import org.broadinstitute.gatk.queue.extensions.gatk.CommandLineGATK
import org.broadinstitute.gatk.queue.extensions.gatk.IndelRealigner
import org.broadinstitute.gatk.queue.extensions.gatk.RealignerTargetCreator
import org.broadinstitute.gatk.queue.extensions.gatk.UnifiedGenotyper
import org.broadinstitute.gatk.queue.extensions.gatk.VariantFiltration
import org.broadinstitute.gatk.queue.extensions.gatk.VcfGatherFunction
import org.broadinstitute.gatk.queue.extensions.picard.MergeSamFiles
import org.broadinstitute.gatk.queue.extensions.picard.SortSam
import org.broadinstitute.gatk.queue.function.ListWriterFunction
import molmed.queue.extensions.picard.CollectTargetedPcrMetrics
import molmed.queue.setup.ReadGroupInformation
import molmed.queue.setup.ReadPairContainer
import molmed.queue.setup.Sample
import molmed.queue.setup.SampleAPI
import molmed.queue.setup.SetupXMLReader
import molmed.queue.setup.SetupXMLReaderAPI
import molmed.utils.Resources
import molmed.utils.GeneralUtils._
import htsjdk.samtools.SAMFileHeader
import htsjdk.samtools.SAMFileHeader.SortOrder
import htsjdk.samtools.SAMTextHeaderCodec
import molmed.utils.ReadGroupUtils._
import molmed.utils.Uppmaxable
import molmed.utils.BwaAlignmentUtils
import molmed.utils.GeneralUtils
import molmed.utils.UppmaxConfig
import molmed.config.UppmaxXMLConfiguration
import molmed.utils.UppmaxJob
import molmed.utils.BwaAln
import molmed.utils.BedToIntervalUtils
import org.broadinstitute.gatk.engine.downsampling.DownsampleType
import org.broadinstitute.gatk.utils.commandline.Hidden
import molmed.report.ReportGenerator
import molmed.config.FileAndProgramResourceConfig

/**
 * Haloplex best practice analysis from fastqs to variant calls.
 */
class Haloplex extends QScript
    with UppmaxXMLConfiguration with FileAndProgramResourceConfig {

  qscript =>

  /**
   * Arguments
   */

  /**
   * **************************************************************************
   * Required Parameters
   * **************************************************************************
   */

  @Input(doc = "Location of resource files such as dbSnp, hapmap, etc.", fullName = "resources", shortName = "res", required = true)
  var resourcesPath: File = _

  @Input(doc = "bed files with haloplex intervals to be analyzed. (Covered from design package)", fullName = "gatk_interval_file", shortName = "intervals", required = true)
  var intervals: File = _

  @Input(doc = "Haloplex amplicons file", fullName = "amplicons", shortName = "amp", required = true)
  var amplicons: File = _

  /**
   * **************************************************************************
   * Optional Parameters
   * **************************************************************************
   */

  @Argument(doc = "Output path for the processed BAM files.", fullName = "output_directory", shortName = "outputDir", required = false)
  var outputDir: String = ""

  @Argument(doc = "Do not convert from hg19 amplicons/convered etc.", fullName = "do_not_convert", shortName = "dnc", required = false)
  var doNotConvert: Boolean = false

  @Argument(doc = "Test mode", fullName = "test_mode", shortName = "test", required = false)
  var testMode: Boolean = false

  @Argument(doc = "Only do the aligments - useful when there is more data to be delivered in a project", fullName = "onlyAlignments", shortName = "oa", required = false)
  var onlyAligment: Boolean = false

  @Argument(doc = "How many ways to scatter/gather", fullName = "scatter_gather", shortName = "sg", required = false)
  var nContigs: Int = 23

  @Argument(doc = "Number of threads to use in thread enabled walkers. Default: 8", fullName = "nbr_of_threads", shortName = "nt", required = false)
  var nbrOfThreads: Int = 8

  @Argument(doc = "Downsample BQSR to x coverage (can get stuck in high coverage regions).", fullName = "downsample_bsqr", shortName = "dbsqr", required = false)
  var downsampleBQSR: Int = -1

  /**
   * Private variables
   */
  private var resources: Resources = null

  /**
   * Helper methods
   */

  def getOutputDir(): File = {
    if (outputDir.isEmpty()) "" else outputDir + "/"
  }

  def cutSamples(sampleMap: Map[String, Seq[SampleAPI]], generalUtils: GeneralUtils): Map[String, Seq[SampleAPI]] = {

    // Standard Illumina adaptors    
    val adaptor1 = "AGATCGGAAGAGCACACGTCTGAACTCCAGTCAC"
    val adaptor2 = "AGATCGGAAGAGCGTCGTGTAGGGAAAGAGTGTAGATCTCGGTGGTCGCCGTATCATT"

    val cutadaptOutputDir = getOutputDir() + "/cutadapt"
    cutadaptOutputDir.mkdirs()

    // Run cutadapt & sync    

    def cutAndSyncSamples(samples: Seq[SampleAPI]): Seq[SampleAPI] = {

      def addSamples(sample: SampleAPI): SampleAPI = {

        def constructTrimmedName(name: String): String = {
          if (name.matches("fastq.gz"))
            name.replace("fastq.gz", "trimmed.fastq.gz")
          else
            name.replace("fastq", "trimmed.fastq.gz")
        }

        val readpairContainer = sample.getFastqs

        val platformUnitOutputDir = new File(cutadaptOutputDir + "/" + sample.getReadGroupInformation.platformUnitId)
        platformUnitOutputDir.mkdirs()

        val mate1SyncedFastq = new File(platformUnitOutputDir + "/" + constructTrimmedName(sample.getFastqs.mate1.getName()))
        add(new generalUtils.cutadapt(readpairContainer.mate1, mate1SyncedFastq, adaptor1, cutadaptPath, syncPath))

        val mate2SyncedFastq =
          if (readpairContainer.isMatePaired) {
            val mate2SyncedFastq = new File(platformUnitOutputDir + "/" + constructTrimmedName(sample.getFastqs.mate2.getName()))
            add(new generalUtils.cutadapt(readpairContainer.mate2, mate2SyncedFastq, adaptor2, cutadaptPath, syncPath))
            mate2SyncedFastq
          } else null

        val readGroupContainer = new ReadPairContainer(mate1SyncedFastq, mate2SyncedFastq, sample.getSampleName)
        new Sample(sample.getSampleName, sample.getReference, sample.getReadGroupInformation, readGroupContainer)
      }

      val cutAndSyncedSamples = for (sample <- samples) yield { addSamples(sample) }
      cutAndSyncedSamples

    }

    val cutSamples = for { (sampleName, samples) <- sampleMap }
      yield (sampleName, cutAndSyncSamples(samples))

    cutSamples
  }

  // Override the normal swapExt metod by adding the outputDir to the file path by default if it is defined.
  override def swapExt(file: File, oldExtension: String, newExtension: String) = {
    if (outputDir.isEmpty())
      new File(file.getName.stripSuffix(oldExtension) + newExtension)
    else
      swapExt(outputDir, file, oldExtension, newExtension);
  }

  /**
   * The actual script
   */

  def script() = {

    resources = new Resources(resourcesPath, testMode)

    // Create output dirs
    val vcfOutputDir = new File(getOutputDir() + "/vcf_files")
    vcfOutputDir.mkdirs()
    val miscOutputDir = new File(getOutputDir() + "/misc")
    miscOutputDir.mkdirs()
    val bamOutputDir = new File(getOutputDir() + "/bam_files")
    bamOutputDir.mkdirs()

    // Get and setup input files
    val uppmaxConfig = loadUppmaxConfigFromXML()
    val haloplexUtils = new HaloplexUtils(uppmaxConfig)
    val samples: Map[String, Seq[SampleAPI]] = setupReader.getSamples()   

    // Assume that the same reference is used for all samples
    val reference = samples.head._2.head.getReference
    
    // Get default paths to resources from global config xml
    val resourceMap =
      this.configureResourcesFromConfigXML(this.globalConfig, false)

    // Create the version report
    val reportFile = new File(getOutputDir + "/version_report.txt")
    ReportGenerator.constructHaloplexReport(resourceMap, resources, reference, reportFile)

    // Run cutadapt    
    val generalUtils = new GeneralUtils(projectName, uppmaxConfig)
    val cutAndSyncedSamples = cutSamples(samples, generalUtils)

    // Align with bwa
    val alignmentHelper = new BwaAlignmentUtils(this, bwaPath, nbrOfThreads, samtoolsPath, projectName, uppmaxConfig)
    val cohortList =
      cutAndSyncedSamples.values.flatten.map(sample => alignmentHelper.align(sample, bamOutputDir, false, Some(BwaAln))).toSeq

    // Convert intervals and amplicons from bed files to
    // picard metric files.
    val intervalsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.intervals, ".bed", ".intervals"))
    val ampliconsAsPicardIntervalFile = new File(swapExt(miscOutputDir, qscript.amplicons, ".bed", ".intervals"))

    add(BedToIntervalUtils.convertCoveredToIntervals(qscript.intervals, intervalsAsPicardIntervalFile, cohortList.toList(0), doNotConvert))
    add(BedToIntervalUtils.convertBaitsToIntervals(qscript.amplicons, ampliconsAsPicardIntervalFile, cohortList.toList(0), doNotConvert))

    if (!onlyAligment) {
      // Make raw variation calls
      val preliminaryVariantCalls = new File(vcfOutputDir + "/" + projectName.get + ".pre.vcf")
      val reference = samples.values.flatten.toList(0).getReference
      add(haloplexUtils.genotype(cohortList.toSeq, reference, preliminaryVariantCalls, false, intervalsAsPicardIntervalFile))

      // Create realignment targets
      val targets = new File(miscOutputDir + "/" + projectName.get + ".targets.intervals")
      add(haloplexUtils.target(preliminaryVariantCalls, targets, reference, intervalsAsPicardIntervalFile))

      // Do indel realignment
      val postCleaningBamList =
        for (bam <- cohortList) yield {

          // Indel realignment
          val indelRealignedBam = swapExt(bamOutputDir, bam, ".bam", ".clean.bam")
          add(haloplexUtils.clean(Seq(bam), targets, indelRealignedBam, reference))
          indelRealignedBam
        }

      // BQSR
      val covariates = new File(miscOutputDir + "/bqsr.grp")
      add(haloplexUtils.cov(postCleaningBamList.toSeq, covariates, reference, intervalsAsPicardIntervalFile))

      // Clip reads and apply BQSR
      val clippedAndRecalibratedBams =
        for (bam <- postCleaningBamList) yield {
          val clippedBam = swapExt(bamOutputDir, bam, ".bam", ".clipped.recal.bam")
          add(haloplexUtils.clip(bam, clippedBam, covariates, reference))
          clippedBam
        }

      // Collect targetedPCRMetrics
      for (bam <- clippedAndRecalibratedBams) {
        val generalStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".statistics")
        val perAmpliconStatisticsOutputFile = swapExt(bamOutputDir, bam, ".bam", ".amplicon.statistics")
        add(generalUtils.collectTargetedPCRMetrics(bam, generalStatisticsOutputFile, perAmpliconStatisticsOutputFile,
          ampliconsAsPicardIntervalFile, intervalsAsPicardIntervalFile, reference))
      }

      // Make variant calls
      val afterCleanupVariants = swapExt(vcfOutputDir, preliminaryVariantCalls, ".pre.vcf", ".vcf")
      add(haloplexUtils.genotype(clippedAndRecalibratedBams.toSeq, reference, afterCleanupVariants, true, intervalsAsPicardIntervalFile))

      // Filter variant calls
      val filteredCallSet = swapExt(vcfOutputDir, afterCleanupVariants, ".vcf", ".filtered.vcf")
      add(haloplexUtils.filterVariations(afterCleanupVariants, filteredCallSet, reference))
    }
  }

  /**
   * Case class wappers for external programs
   */

  class HaloplexUtils(uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig) {

    // General arguments to GATK walkers
    trait CommandLineGATKArgs extends CommandLineGATK

    case class genotype(@Input bam: Seq[File], reference: File, @Output @Gather(classOf[VcfGatherFunction]) vcf: File, isSecondPass: Boolean, @Input intervalFile: File) extends UnifiedGenotyper with CommandLineGATKArgs with EightCoreJob {

      if (qscript.testMode)
        this.no_cmdline_in_header = true

      this.isIntermediate = false

      this.input_file = bam
      this.out = vcf

      this.dbsnp = resources.dbsnp
      this.reference_sequence = reference
      this.intervals = Seq(intervalFile)
      this.scatterCount = nContigs
      this.nt = nbrOfThreads
      this.stand_call_conf = 30.0
      this.stand_emit_conf = 10.0

      // Depending on if this is used to call preliminary or final variations different
      // parameters should be used.
      if (isSecondPass) {
        this.dt = DownsampleType.NONE
        this.annotation = Seq("AlleleBalance")
        this.filterMBQ = true
      } else {
        this.downsample_to_coverage = 200
      }

      this.output_mode = org.broadinstitute.gatk.tools.walkers.genotyper.OutputMode.EMIT_VARIANTS_ONLY
      this.glm = org.broadinstitute.gatk.tools.walkers.genotyper.GenotypeLikelihoodsCalculationModel.Model.BOTH

      override def jobRunnerJobName = projectName.get + "_genotype"

    }

    case class target(@Input candidateIndels: File, outIntervals: File, reference: File, @Input intervalFile: File) extends RealignerTargetCreator with CommandLineGATKArgs with EightCoreJob {

      this.reference_sequence = reference
      this.num_threads = nbrOfThreads
      this.intervals = Seq(intervalFile)
      this.out = outIntervals
      this.mismatchFraction = 0.0
      this.known :+= resources.mills
      this.known :+= resources.phase1
      this.known :+= candidateIndels
      this.scatterCount = nContigs
      override def jobRunnerJobName = projectName.get + "_target"

    }

    case class clean(inBams: Seq[File], tIntervals: File, outBam: File, reference: File) extends IndelRealigner with CommandLineGATKArgs with OneCoreJob {

      this.isIntermediate = true
      this.reference_sequence = reference
      this.input_file = inBams
      this.targetIntervals = tIntervals
      this.out = outBam
      this.known :+= resources.dbsnp
      this.known :+= resources.mills
      this.known :+= resources.phase1
      this.consensusDeterminationModel = org.broadinstitute.gatk.tools.walkers.indels.IndelRealigner.ConsensusDeterminationModel.KNOWNS_ONLY
      this.compress = 0
      this.scatterCount = nContigs
      override def jobRunnerJobName = projectName.get + "_clean"

    }

    case class cov(inBam: Seq[File], outRecalFile: File, reference: File, @Input intervalFile: File) extends BaseRecalibrator with CommandLineGATKArgs with EightCoreJob {

      this.reference_sequence = reference
      this.isIntermediate = false

      this.num_cpu_threads_per_data_thread = nbrOfThreads

      if (qscript.downsampleBQSR != -1)
        this.downsample_to_coverage = qscript.downsampleBQSR
      this.knownSites :+= resources.dbsnp
      this.covariate ++= Seq("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "ContextCovariate")
      this.input_file = inBam
      this.disable_indel_quals = false
      this.out = outRecalFile
      this.intervals = Seq(intervalFile)

      this.scatterCount = nContigs
      override def jobRunnerJobName = projectName.get + "_cov"

    }

    case class clip(@Input inBam: File, @Output @Gather(classOf[BamGatherFunction]) outBam: File, covariates: File, reference: File) extends ClipReads with CommandLineGATKArgs with OneCoreJob {
      this.isIntermediate = false
      this.reference_sequence = reference
      this.input_file = Seq(inBam)
      this.out = outBam
      this.cyclesToTrim = "1-5"
      this.scatterCount = nContigs
      this.clipRepresentation = org.broadinstitute.gatk.utils.clipping.ClippingRepresentation.WRITE_NS
      this.BQSR = covariates

      override def jobRunnerJobName = projectName.get + "_clean"

    }

    case class filterVariations(@Input inVcf: File, @Output outVcf: File, reference: File) extends VariantFiltration with CommandLineGATKArgs with OneCoreJob {

      if (qscript.testMode)
        this.no_cmdline_in_header = true

      this.reference_sequence = reference
      this.variant = inVcf
      this.out = outVcf

      this.clusterWindowSize = 10
      this.clusterSize = 3
      this.filterExpression = Seq("MQ0 >= 4 && (( MQ0 / (1.0 * DP )) > 0.1)",
        "DP < 10",
        "QUAL < 30.0",
        "QUAL > 30.0 && QUAL < 50.0",
        "QD < 1.5")

      this.filterName = Seq("HARD_TO_VALIDATE", "LowCoverage", "VeryLowQual", "LowQual", "LowQD")

      override def jobRunnerJobName = projectName.get + "_filterVariants"

    }

  }

}
