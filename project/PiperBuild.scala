import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object PiperBuild extends Build {

  lazy val piper = Project(
    id = "piper",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++
      Seq(
        scalacOptions in Compile ++= Seq("-deprecation", "-unchecked"),
        fork in Test := true,
        mappings in (Compile, packageBin) ~=
          (_ filter {
            case (f, s) =>
              !s.contains("molmed/qscripts")}),
        parallelExecution in Test := false) ++
        packSettings ++
        Seq(
          packMain := Map(
            "piper" -> "org.broadinstitute.gatk.queue.QCommandLine",
            "setupFileCreator" -> "molmed.apps.setupcreator.SetupFileCreator",
            "SolidSetupFileCreator" -> "molmed.apps.setupcreator.SolidSetupFileCreator",
            "sthlm2UUSNP" -> "molmed.apps.Sthlm2UUSNP",
            "reportParser" -> "molmed.apps.ReportParser"))
          ++ dependencies)
    .configs(PipelineTestRun)
    .settings(inConfig(PipelineTestRun)(Defaults.testTasks): _*)

  lazy val PipelineTestRun = config("pipelinetestrun").extend(Test)
  //libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.10"
  //libraryDependencies += "org.hdfgroup" % "hdf-java" % "2.6.1",

  val dependencies = Seq(
    libraryDependencies += "commons-lang" % "commons-lang" % "2.5",
    libraryDependencies += "commons-io" % "commons-io" % "2.1",
    libraryDependencies += "org.testng" % "testng" % "5.14.1",
    libraryDependencies += "net.java.dev.jets3t" % "jets3t" % "0.8.1",
    libraryDependencies += "org.simpleframework" % "simple-xml" % "2.0.4",
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0",
    libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test",
    //libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.10",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.6.0"
    )
}
