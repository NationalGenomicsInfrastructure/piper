package molmed.utils

/**
 * Trait holding the parameters that are needed for a QScript to run on Uppmax.
 * Extend QScript with this to run add them to the commandline arguments.
 */
trait Uppmaxable {

  @Argument(doc = "Uppmax qos flag", fullName = "quality_of_service", shortName = "qos", required = false)
  var uppmaxQoSFlag: Option[String] = UppmaxConfig.defaultUppmaxQoSFlag

  @Argument(doc = "Uppmax project id", fullName = "project_id", shortName = "upid", required = false)
  var projId: String = UppmaxConfig.defaultProjectId

  @Argument(doc = "Project name", fullName = "project_name", shortName = "name", required = false)
  var projectName: Option[String] = Some("DefaultProject")

}