package molmed.queue.setup
import java.io.File

import molmed.queue.setup.InputSeqFileType.InputSeqFileType

/**
 * Container class for read pairs
 * 
 * Written in the early days of Piper, before learning Scala well, therefore contains some rather
 * ugly design choices. /JD
 * 
 * @todo It would be better if the mates were mate options instead of using null.
 * @todo It would be better if the sampleName was not mutable.
 */

final object InputSeqFileType extends Enumeration{
  type InputSeqFileType = Value
  val FASTQ, BAM, XSQ = Value

  }

@Deprecated
case class ReadPairContainer(mate1: File, mate2: File = null, var sampleName: String = null) {
	def isMatePaired(): Boolean = {mate2 != null}
	def getFiles(): Seq[File] = if(isMatePaired) Seq(mate1, mate2) else Seq(mate1)
}

/**
 *
 * @param files  List of input files, may be in XSQ, FASTQ, or BAM format, but only one format at a time.
 * @param hasPair  Whether the input files has paired-end read or not. For FASTQ this means having two fastq for a library
 * @param fileType  What data format are the input sequence files. Possible values are FASTQ, BAM, or XSQ. Default to FASTQ
 */
case class InputSeqFileContainer( files: Seq[File],
                                  var sampleName: String = null,
                                  hasPair: Boolean = false,
                                  fileType: InputSeqFileType = InputSeqFileType.FASTQ
                                  ){
  // following methods are for backward compatibility
  def mate1: File = if (fileType == InputSeqFileType.FASTQ)  files(0) else null
  def mate2: File = if (fileType == InputSeqFileType.FASTQ && hasPair == true)  files(1) else null
  def getFiles = files
  def isMatePaired():Boolean = hasPair
}