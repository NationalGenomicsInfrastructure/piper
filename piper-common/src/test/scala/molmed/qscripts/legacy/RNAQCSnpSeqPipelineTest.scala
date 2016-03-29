package molmed.qscripts.legacy

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.gatk.queue.pipeline._

/**
 * TODO
 * Implement cluster style tegatk as in AlignWithBwaSnpSeqPipelineTest
 */

class RNAQCSnpSeqPipelineTest {

  val pathToScript = "-S src/main/scala/molmed/qscripts/RNAQC.scala"

  val snpSeqBaseTest = SnpSeqBaseTest

  var run: Boolean = false

  @BeforeClass
  @Parameters(Array("runpipeline"))
  def init(runpipeline: Boolean): Unit = {
    this.run = runpipeline
  }

  @Test
  def testRNAQC {
    val projectName = "test1"

    val aggregatedMetrics = "aggregated_metrics.tsv"

    val spec = new PipelineTestSpec
    spec.jobRunners = Seq("Shell")
    spec.name = "BasicRNAQC"
    spec.args = Array(
      pathToScript,
      " -i " + snpSeqBaseTest.pathToRNAtestBam,
      " --xml_input " + snpSeqBaseTest.pathToLegacySetupFile,
      " --reference " + snpSeqBaseTest.hg19,
      " --transcripts " + snpSeqBaseTest.hg19annotations,
      " --rna_seqc " + snpSeqBaseTest.pathToRNASeQC,
      " --rRNA_targets " + snpSeqBaseTest.hg19_rRNA,
      " -outputDir " + PipelineTest.runDir(spec.name, spec.jobRunners(0)),
      " -startFromScratch ").mkString

    spec.fileMD5s += aggregatedMetrics -> "560b4a92982055adc1b5116a0d5723c7"

    spec.run = run
    PipelineTest.executeTest(spec)
  }
}
