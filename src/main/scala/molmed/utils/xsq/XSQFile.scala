package molmed.utils.xsq

import ch.systemsx.cisd.hdf5._
import scala.collection.JavaConversions._

import org.slf4j.{LoggerFactory, Logger}


/**
 * Created by vql on 29/01/15.
 * Present information for XSQ file
 * @param xsqFile full path to the XSQ file to read/write
 */
class XSQFile(xsqFile: String) {
  final val log: Logger = LoggerFactory.getLogger(classOf[XSQFile])
   private val xsq = HDF5Factory.openForReading(xsqFile)

    //  private object ReservedNames extends Enumeration {
    //    type ReservedNames = Value
    //    val RunMetadata, TagDetails, Indexing, LibraryDetails, Unassigned, Unclassified = Value
    //  }
    private val ReservedNames: List[String] = List("RunMetadata", "TagDetails",
      "Indexing", "LibraryDetails", "Unassigned", "Unclassified")

    private val runMetadataDSPath = "/RunMetadata"
    private val libraryDetailsDSPath =  runMetadataDSPath + "/LibraryDetails"
    private val libraryIndexDSPath = "/Indexing/LibraryIndex"
    private val libraryDetails:Array[HDF5CompoundDataMap] = xsq.compound().
      readArray(libraryDetailsDSPath, classOf[HDF5CompoundDataMap])
    private val libraryGroupIndex = xsq.`object`().getAllGroupMembers("/").filter(r => !ReservedNames.exists(_ == r) )

    // TODO: Isn't there a better way to do this runMetadata thingie?
    val runMetadata:Map[String, Any] = Map(
      "FileVersion" -> xsq.string().getAttr(runMetadataDSPath, "FileVersion"),
      "FlowcellAssignment" -> xsq.int8().getAttr(runMetadataDSPath, "FlowcellAssignment"),
      "HDFVersion" -> xsq.string().getAttr(runMetadataDSPath, "HDFVersion"),
      "InstrumentModel" -> xsq.string().getAttr(runMetadataDSPath, "InstrumentModel"),
      "InstrumentName" -> xsq.string().getAttr(runMetadataDSPath, "InstrumentName"),
      "InstrumentSerial" -> xsq.string().getAttr(runMetadataDSPath, "InstrumentSerial"),
      "InstrumentVendor" -> xsq.string().getAttr(runMetadataDSPath, "InstrumentVendor"),
      "IsIndexingRun" -> xsq.int8().getAttr(runMetadataDSPath, "IsIndexingRun"),
      "LaneNumber" -> xsq.int8().getAttr(runMetadataDSPath, "LaneNumber"),
      "LibraryType" -> xsq.string().getAttr(runMetadataDSPath, "LibraryType"),
      "Operator" -> xsq.string().getAttr(runMetadataDSPath, "Operator"),
      "RunEndTime" -> xsq.string().getAttr(runMetadataDSPath, "RunEndTime"),
      "RunName" -> xsq.string().getAttr(runMetadataDSPath, "RunName"),
      "RunStartTime" -> xsq.string().getAttr(runMetadataDSPath, "RunStartTime"),
      "SequencingSampleDescription" -> xsq.string().getAttr(runMetadataDSPath, "SequencingSampleDescription")
    )

    val isBarcoded: Boolean = runMetadata.get("IsIndexingRun").getOrElse(2).toString.toInt == 1


    private val indexingMeta = if (isBarcoded) {
      Map("IndexKitName" -> xsq.string().getAttr("/Indexing", "IndexKitName"),
        "IndexLength" -> xsq.int32().getAttr("/Indexing","IndexLength")
      )
    }   else Map()
    /**
     * LibraryIndex: return a map of LibraryName -> Index
     */
    def getLibraryIndex:Map[String, Int] = {
      if (isBarcoded) {
        getLibraries.map(l => l.name -> l.indexID.get).toMap
      } else Map("DefaultLibrary" -> 0 )
    }

    def getLibraryNames: Seq[String] = {

      libraryDetails.map(row => row.get("LibraryName").toString)
    }

    def getLibraries:Seq[Library] = {
      if (isBarcoded) {
        val libraries = for {
          l <- libraryGroupIndex
          libName = xsq.string().getAttr("/" + l,"LibraryName")
          indexName = xsq.string().getAttr("/" + l,"IndexName")
          indexID = xsq.int16().getAttr("/" + l,"IndexID").toInt
        } yield new Library(xsqFile, name = libName, indexID = Some(indexID), indexName = Some(indexName))
        libraries.toSeq
      } else {
        Seq(new Library(xsqFile, name = "DefaultLibrary"))
      }
    }

    def printLibNames = for (n <- getLibraryNames) println(n)

    def printLibraryIndex = {
      for (k <- getLibraryIndex.keys) {
        println(k + " " + getLibraryIndex.get(k).get.toString)
      }
    }

  }
