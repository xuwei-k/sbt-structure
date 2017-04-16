
def newProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(
      name := "sbt-structure-" + projectName,
      organization := "org.jetbrains",
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
      publishMavenStyle := false
    )
    .settings(
      bintrayRepository := "sbt-plugins",
      bintrayOrganization := Some("jetbrains"),
      bintrayCredentialsFile := file(".credentials")
    )

// can't add this in the `newProject` method because sbt-cross-building plugin is somehow causing sbt 0.13.2 to be
// added to the sbt classpath, which causes errors loading the build. wow.
enablePlugins(GitVersioning)

git.useGitDescribe in ThisBuild := true


lazy val core = newProject("core")
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        // if scala 2.11+ is used, add dependency on scala-xml module
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.5")
        case _ =>
          Seq.empty
      }
    },
    crossScalaVersions := Seq("2.9.3", "2.10.6", "2.11.9", "2.12.1")
  )

lazy val extractor = newProject("extractor")
  .settings(crossBuildingSettings:_*)
  .settings(
    name := name.value + "-" + CrossBuilding.pluginSbtVersion.value,
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.googlecode.java-diff-utils" % "diffutils" % "1.2" withSources(),
      "org.specs2" %% "specs2" % "1.12.3" % "test"),
    CrossBuilding.crossSbtVersions := Seq("0.12.4", "0.13.0", "0.13.9", "0.13.13"),
    testSetup := {
      System.setProperty("structure.sbtversion.full", CrossBuilding.pluginSbtVersion.value)
      System.setProperty("structure.sbtversion.short", CrossBuilding.pluginSbtVersion.value.substring(0, 4))
      System.setProperty("structure.scalaversion", scalaBinaryVersion.value)
    },
    test in Test := (test in Test).dependsOn(testSetup).value,
    testOnly in Test := (testOnly in Test).dependsOn(testSetup).evaluated,
    name in bintray := "sbt-structure-extractor",
    scalacOptions ++= Seq("-deprecation")
  )

lazy val sbtStructure = project.in(file(".")).aggregate(core, extractor)

lazy val testSetup = taskKey[Unit]("Setup tests for extractor")

addCommandAlias("publishAllLocal", ";project core; ++ 2.9.3 publishLocal; ++ 2.10.6. publishLocal; ++ 2.11.9 publishLocal; project extractor; ^^ 0.12 publishLocal; ^^ 0.13 publishLocal")
addCommandAlias("publishAll", ";project core; ++ 2.9.3 publish; ++ 2.10.6. publish; ++ 2.11.9 publish; project extractor; ^^ 0.12 publish; ^^ 0.13 publish")
