name := "scalka"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.4"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
