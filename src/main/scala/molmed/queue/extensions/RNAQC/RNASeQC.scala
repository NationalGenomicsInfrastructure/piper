package molmed.queue.extensions.RNAQC

import org.broadinstitute.sting.commandline._
import java.io.File
import org.broadinstitute.sting.queue.function.CommandLineFunction

class RNASeQC extends CommandLineFunction {

  analysisName = "RNASeQC"

  @Argument(doc = "String of format Sample ID|Bam File|Notes", shortName = "i", fullName = "input", required = true)
  var input: String = _

  @Input(doc = "Reference file in fasta format.", shortName = "r", fullName = "reference", required = true)
  var reference: File = _

  @Output(doc = "StdOut used for placeholding purposes", shortName = "place", fullName = "place_holder", required = true)
  var placeHolderFile: File = _

  @Input(doc = "GTF File defining the transcripts (must end in .gtf)", shortName = "t", fullName = "transcripts", required = true)
  var transcripts: File = _

  @Input(doc = "Output directory", shortName = "o", fullName = "output_dir", required = true)
  var output: File = _

  @Argument(doc = "interval file for rRNA loci (must end in .list). This is an alternative flag to the -BWArRNA flag.", shortName = "rRNA", fullName = "rRNA_targets", required = false)
  var rRNATargets: File = null

  @Argument(doc = "Perform downsampling to the given number of reads.", shortName = "d", fullName = "downsample", required = false)
  var downsample: Int = -1

  val rRNATargetString: String = if (rRNATargets != null) " -rRNA " + rRNATargets.getAbsolutePath() + " " else ""
  val downsampleString: String = if (downsample > 0) " -d " + downsample + " " else ""

  override def commandLine = "java -jar resources/RNA-SeQC_v1.1.7.jar " +
    " -s " + input +
    " -r " + reference +
    " -t " + transcripts +
    rRNATargetString +
    downsampleString +
    " -o " + output +
    " > " + placeHolderFile
}