name := "maml-spark"

libraryDependencies ++= Seq(
  "org.locationtech.geotrellis" %% "geotrellis-spark-testkit" % "1.1.1" % "test"
)

fork in Test := false

parallelExecution in Test := false
