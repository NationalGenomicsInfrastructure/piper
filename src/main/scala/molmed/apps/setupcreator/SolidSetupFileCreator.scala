package molmed.apps.setupcreator

import java.io.File

import ConsoleInputParser.getSingleInput
import ConsoleInputParser.packOptionalValue
import ConsoleInputParser.withDefaultValue
import scopt.OptionParser

/**
 * A simple application to create a setup XML file.
 */
object SolidSetupFileCreator extends App {

  case class Config(
                     outputFile: Option[File] = None,
                     projectName: Option[String] = None,
                     seqencingPlatform: Option[String] = None,
                     sequencingCenter: Option[String] = None,
                     uppmaxProjectId: Option[String] = None,
                     uppmaxQoSFlag: Option[String] = Some(""),
                     seqFiles: Option[Seq[File]] = Some(Seq()),
                     pairedEnd: Option[Boolean] = None,
                     reference: Option[File] = None)

  val parser = new OptionParser[Config]("AfMDSetupFileCreator") {
    head("AfMDSetupFileCreator", " - A utility program to create pipeline AfMD setup xml files for Piper..")

    opt[File]('o', "output") required () valueName ("Output xml file.") action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text ("This is a required argument.")

    opt[String]('p', "project_name") optional () valueName ("The name of this project.") action { (x, c) =>
      c.copy(projectName = Some(x))
    } text ("This is a required argument.")

    opt[String]('s', "sequencing_platform") optional () valueName ("The technology used for sequencing, e.g. Illumina") action { (x, c) =>
      c.copy(seqencingPlatform = Some(x))
    } text ("This is a required argument.")

    opt[String]('c', "sequencing_center") optional () valueName ("Where the sequencing was carried out, e.g. NGI, AfMD") action { (x, c) =>
      c.copy(sequencingCenter = Some(x))
    } text ("This is a required argument.")

    opt[String]('q', "qos") optional () valueName ("A optional quality of service (QoS) flag to forward to the cluster.") action { (x, c) =>
      c.copy(uppmaxQoSFlag = Some(x))
    } text ("This is a optional argument.")

    opt[String]('a', "uppnex_project_id") optional () valueName ("The uppnex project id to charge the core hours to.") action { (x, c) =>
      c.copy(uppmaxProjectId = Some(x))
    } text ("This is a required argument.")

    opt[File]('i', "input_seqfile") unbounded () optional () valueName ("Input path to sequence files to include in analysis.") action { (x, c) =>
      c.copy(seqFiles = c.seqFiles.getOrElse(Seq()) :+ x)
    } text ("This is a required argument. Can be specified multiple times.")

    opt[File]('r', "reference") optional () valueName ("Reference fasta file to use.") action { (x, c) =>
      c.copy(reference = Some(x))
    } text ("This is a required argument.")
  }

  // Start up the app!
  parser.parse(args, new Config()) map { config =>

    val allFieldsAreSet =
      config.getClass().getDeclaredFields.
        forall(p => p.isDefined)

    if (allFieldsAreSet)
      createSetupFile(config)
    else
      parser.showUsage

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  def createSetupFile(config: Config): Unit = {

    val project = SolidSetupUtils.createProject()

    val projectWithMetaData = SolidSetupUtils.setMetaData(project)(
      config.projectName.get,
      config.seqencingPlatform.get,
      config.sequencingCenter.get,
      config.uppmaxProjectId.get,
      config.uppmaxQoSFlag.get,
      config.reference.get)
    val inputFiles = config.seqFiles.get

    val projectWithSamplesAdded =
      SolidSetupUtils.createProjectXML(projectWithMetaData)(inputFiles)

    SolidSetupUtils.writeToFile(projectWithSamplesAdded, config.outputFile.get)

    println("Successfully created: " + config.outputFile.get + ".")
  }

}