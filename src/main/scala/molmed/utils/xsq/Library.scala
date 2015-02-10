package molmed.utils.xsq


/**
 * Object containing a XSQ library node and a function to process it.
 * @author Biocyberman
 */

private object Library extends Enumeration {
    type LibraryAttributeNames = Value
    val IndexID, LibraryName = Value
  }

case class Library(xsqFile: String,
                   name: String,
                   indexID: Option[Int] = None,
                   indexName: Option[String] = None) {
  //TODO: Implement adding libraries to combine or link the data to a new XSQ file
  def getNameAndBarCode = name + "_" + indexName.get
}
