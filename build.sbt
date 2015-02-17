name := """dl-validator"""

version := "1.0"

scalaVersion := "2.11.1"

assemblyJarName in assembly := "dl-validator.jar"

mainClass in assembly := Some("file.Init")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.10.4",
  "com.typesafe.akka" %% "akka-actor" % "2.3.7",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.7",
  "com.typesafe.akka" %% "akka-remote" % "2.3.7",
  "com.typesafe.akka" %% "akka-camel" % "2.3.7"
  		exclude("org.slf4j", "slf4j-api"),
  "org.apache.camel" % "camel-core" % "2.14.1"
  		exclude("org.slf4j", "slf4j-api"),
  "org.apache.camel" % "camel-jetty" % "2.14.1"
  		exclude("org.slf4j", "slf4j-api"),
  "org.apache.camel" % "camel-quartz" % "2.14.1"
  		exclude("org.slf4j", "slf4j-api"),
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.scalatest" %% "scalatest" % "2.1.6" % "test"
  		exclude("org.mockito", "mockito-core"),
  "junit" % "junit" % "4.11" % "test"
  		exclude("org.mockito", "mockito-core"),
  "org.apache.commons" % "commons-lang3" % "3.1",
  "commons-io" % "commons-io" % "2.4",
  "org.specs2" % "specs2_2.11" % "2.4"
  			exclude("org.mockito", "mockito-core"),
  	"com.google.code.gson" % "gson" % "2.2.4",
  	"org.json4s" % "json4s-native_2.11" % "3.2.11",
  	"org.apache.thrift" % "libthrift" % "0.9.1"
  			exclude("org.slf4j", "slf4j-api") 
   			exclude("org.slf4j", "slf4j-log4j12"),
  	"org.apache.kafka" % "kafka_2.11" % "0.8.2.0"
  			exclude("org.slf4j", "slf4j-api") 
   			exclude("org.slf4j", "slf4j-log4j12"),
  	"ly.stealth" % "scala-kafka" % "0.1.0.0"
   		intransitive(),
   	"com.101tec" % "zkclient" % "0.4"
   			exclude("org.slf4j", "slf4j-api") 
   			exclude("org.slf4j", "slf4j-log4j12"),
   	"com.yammer.metrics" % "metrics-core" % "2.2.0"
   			exclude("org.slf4j", "slf4j-api"),
   	"net.sf.jopt-simple" % "jopt-simple" % "4.8",
   	"org.apache.camel" % "camel-scala" % "2.14.1"
   			exclude("org.slf4j", "slf4j-api"),
   	"org.apache.camel" % "camel-ftp" % "2.14.1"
   			exclude("org.slf4j", "slf4j-api"),
   	"com.jsuereth" % "scala-arm_2.11" % "1.4"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

resolvers += Resolver.mavenLocal

unmanagedBase := baseDirectory.value / "lib"

net.virtualvoid.sbt.graph.Plugin.graphSettings