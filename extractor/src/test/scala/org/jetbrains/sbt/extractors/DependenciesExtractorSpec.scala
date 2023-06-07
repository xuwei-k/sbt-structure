package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.*
import org.jetbrains.sbt.structure as jb
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.{contain, convertToAnyMustWrapper}
import sbt.{Attributed, globFilter as _, *}
import sbt.jetbrains.apiAdapter

class DependenciesExtractorSpec extends AnyFreeSpecLike {

  val projects: Seq[ProjectRef] =
    Seq("project-1", "project-2").map(ProjectRef(file("/tmp/test-project"), _))
  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil
  val CustomConf = config("custom-conf").extend(sbt.Test)
  val buildDependencies = apiAdapter.buildDependencies(
    Map(
      projects.head -> Seq(
        ResolvedClasspathDependency(projects(1), Some("compile"))
      ),
      projects(1) -> Seq.empty
    ),
    Map.empty
  )
  val internalDependencyClasspath1: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
    configuration.name match {
      case "compile" => mapToAttributedFiles("/tmp/test-project/project-2/targets/scala/classes")
      case "test" => mapToAttributedFiles(
        "/tmp/test-project/project-1/targets/scala/classes",
        "/tmp/test-project/project-2/targets/scala/classes")
      case "runtime" => mapToAttributedFiles(
        "/tmp/test-project/project-1/targets/scala/classes",
        "/tmp/test-project/project-2/targets/scala/classes")
      case _ => Nil
    }
  }

  private def mapToAttributedFiles(files: String*): Seq[Attributed[File]] =
    files.map { filename =>
      Attributed.blank(file(filename)).put(Keys.configuration.key, sbt.Compile)
    }

  val dependencyClasspathMapping = Map(
    file("/tmp/test-project/project-1/targets/scala/classes") -> projects.head,
    file("/tmp/test-project/project-1/targets/scala/test-classes") -> projects.head,
    file("/tmp/test-project/project-2/targets/scala/classes") -> projects(1),
    file("/tmp/test-project/project-2/targets/scala/test-classes") -> projects(1),
  )

  "DependenciesExtractor" - {
    "should always extract internal dependency classpath; merge (compile, test, runtime) -> compile IDEA scope" in {
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val productionDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, productionDependencies),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "should always extract internal dependency classpath; merge (compile, test) -> provided IDEA scope" in {
      val internalDependencyClasspath: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
        configuration.name match {
          case "compile" => mapToAttributedFiles("/tmp/test-project/project-2/targets/scala/classes")
          case "test" => mapToAttributedFiles(
            "/tmp/test-project/project-1/targets/scala/classes",
            "/tmp/test-project/project-2/targets/scala/classes")
          case "runtime" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case _ => Nil
        }
      }
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        internalDependencyClasspath = internalDependencyClasspath,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val productionDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, productionDependencies),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "should always extract internal dependency classpath; merge (runtime, test) -> runtime IDEA scope" in {
      val internalDependencyClasspath: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
        configuration.name match {
          case "compile" => Nil
          case "test" => mapToAttributedFiles(
            "/tmp/test-project/project-1/targets/scala/classes",
            "/tmp/test-project/project-2/targets/scala/classes")
          case "runtime" => mapToAttributedFiles(
            "/tmp/test-project/project-2/targets/scala/classes",
            "/tmp/test-project/project-1/targets/scala/classes")
          case _ => Nil
        }
      }
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        internalDependencyClasspath = internalDependencyClasspath,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val productionDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Runtime))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, productionDependencies),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "should always extract internal dependency classpath; merge (compile) -> provided IDEA scope" in {
      val internalDependencyClasspath: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
        configuration.name match {
          case "compile" => mapToAttributedFiles("/tmp/test-project/project-2/targets/scala/classes")
          case "test" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case "runtime" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case _ => Nil
        }
      }
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Nil,
        internalDependencyClasspath = internalDependencyClasspath,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val productionDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, productionDependencies),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "should always extract internal dependency classpath; merge (it) -> test IDEA scope" in {
      val internalDependencyClasspath: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
        configuration.name match {
          case "compile" => Nil
          case "test" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case "it" => mapToAttributedFiles("/tmp/test-project/project-2/targets/scala/classes")
          case "runtime" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case _ => Nil
        }
      }
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime, sbt.IntegrationTest),
        testConfigurations = Seq(sbt.IntegrationTest, sbt.Test),
        internalDependencyClasspath = internalDependencyClasspath,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, Nil),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "should always extract internal dependency classpath; merge (custom-conf) -> test IDEA scope" in {
      val internalDependencyClasspath: sbt.Configuration => Keys.Classpath = (configuration: sbt.Configuration) => {
        configuration.name match {
          case "compile" => Nil
          case "test" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case "custom-conf" => mapToAttributedFiles("/tmp/test-project/project-2/targets/scala/classes")
          case "runtime" => mapToAttributedFiles("/tmp/test-project/project-1/targets/scala/classes")
          case _ => Nil
        }
      }
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime, sbt.IntegrationTest, CustomConf),
        testConfigurations = Seq(sbt.IntegrationTest, sbt.Test, CustomConf),
        internalDependencyClasspath = internalDependencyClasspath,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, Nil),
        modules = Dependencies(Nil,Nil),
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "always extract unmanaged dependencies" in {
      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(
            attributed(file("foo.jar")),
            attributed(file("bar.jar"))
          ),
          sbt.Test -> Seq(attributed(file("baz.jar")))
        ).apply,
        externalDependencyClasspath = None,
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val projectDependencies = Dependencies(
        forTestSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
          ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided)))
      )
      val jarDependencies = Dependencies(
        forTestSources = Seq(JarDependencyData(file("baz.jar"), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Provided)),
          JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Provided)))
      )
      val expected = DependencyData(
        projects = projectDependencies,
        modules = Dependencies(Nil,Nil),
        jars = jarDependencies
      )
      assertIdentical(expected, actual)
    }

    "extract managed dependencies when supplied" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId("foo"), Artifact("foo")),
              attributedWith(file("bar.jar"))(moduleId("bar"), Artifact("bar"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq(sbt.Test),
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val projectDependencies = Dependencies(
        forTestSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
         ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided)))
      )
      val moduleDependencies = Dependencies(
        forTestSources = Seq(ModuleDependencyData(toIdentifier(moduleId("baz")), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(
          ModuleDependencyData(toIdentifier(moduleId("foo")), Seq(jb.Configuration.Provided)),
          ModuleDependencyData(toIdentifier(moduleId("bar")), Seq(jb.Configuration.Provided)),
        )
      )

      val expected = DependencyData(
        projects = projectDependencies,
        modules = moduleDependencies,
        jars = Dependencies(Nil,Nil)
      )

      assertIdentical(expected, actual)
    }

    "merge custom test configurations in unmanaged and managed dependencies" in {
      val moduleId = (name: String) => ModuleID("com.example", name, "SNAPSHOT")

      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = Map(
          sbt.Test -> Seq(attributed(file("foo.jar"))),
          CustomConf -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Test -> Seq(
              attributedWith(file("baz.jar"))(moduleId("baz"), Artifact("baz"))
            ),
            CustomConf -> Seq(
              attributedWith(file("qux.jar"))(moduleId("qux"), Artifact("qux"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Test, CustomConf),
        testConfigurations = Seq(sbt.Test, CustomConf),
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val testDependencies = Seq(
        ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
        ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
      )
      val moduleDependencies = Dependencies(
        forTestSources = Seq(
          ModuleDependencyData(toIdentifier(moduleId("baz")), Seq(jb.Configuration.Compile)),
          ModuleDependencyData(toIdentifier(moduleId("qux")), Seq(jb.Configuration.Compile))
        ),
        forProductionSources = Nil
      )
      val jarDependencies = Dependencies(
        forTestSources = Seq(
          JarDependencyData(file("foo.jar"), Seq(jb.Configuration.Compile)),
          JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile))
        ),
        forProductionSources = Nil
      )
      val expected = DependencyData(
        projects = Dependencies(testDependencies, Nil),
        modules = moduleDependencies,
        jars = jarDependencies
      )

      assertIdentical(expected, actual)
    }

    "extract managed dependency with classifier as different dependencies" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = emptyClasspath,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo")),
              attributedWith(file("foo-tests.jar"))(
                moduleId,
                Artifact("foo", "tests")
              )
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile),
        testConfigurations = Seq.empty,
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val expectedModules = Seq(
        toIdentifier(moduleId),
        toIdentifier(moduleId).copy(classifier = "tests")
      )
      val moduleDependencies = Dependencies(
        forTestSources = Nil,
        forProductionSources = expectedModules.map(
          it => ModuleDependencyData(it, Seq(jb.Configuration.Provided))
        )
      )
      val expected = DependencyData(
        projects = Dependencies(Nil, Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided)))),
        modules = moduleDependencies,
        jars = Dependencies(Nil,Nil)
      )
      assertIdentical(expected, actual)
    }

    "merge (compile, test, runtime) -> compile in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test -> Seq(attributed(file("bar.jar"))),
          sbt.Runtime -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Runtime -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test, sbt.Runtime),
        testConfigurations = Seq.empty,
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val projectDependencies = Dependencies(
        forTestSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
          ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
        ),
        forProductionSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)))
      )
      val moduleDependencies = Dependencies(
        forTestSources = Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Compile)))
      )
      val jarDependencies = Dependencies(
        forTestSources = Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile)))
      )
      val expected = DependencyData(
        projects = projectDependencies,
        modules = moduleDependencies,
        jars = jarDependencies
      )
      assertIdentical(expected, actual)
    }

    "merge (compile, test) -> provided in unmanaged and managed dependencies to match IDEA scopes" in {
      val moduleId = "com.example" % "foo" % "SNAPSHOT"

      val actual = new DependenciesExtractor(
        projects.head,
        unmanagedClasspath = Map(
          sbt.Compile -> Seq(attributed(file("bar.jar"))),
          sbt.Test -> Seq(attributed(file("bar.jar")))
        ).apply,
        externalDependencyClasspath = Some(
          Map(
            sbt.Compile -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            ),
            sbt.Test -> Seq(
              attributedWith(file("foo.jar"))(moduleId, Artifact("foo"))
            )
          ).apply
        ),
        dependencyConfigurations = Seq(sbt.Compile, sbt.Test),
        testConfigurations = Seq.empty,
        internalDependencyClasspath = internalDependencyClasspath1,
        exportedClasspathToProjectMapping = dependencyClasspathMapping
      ).extract

      val projectDependencies = Dependencies(
        forTestSources = Seq(
          ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Compile)),
          ProjectDependencyData(s"${projects.head.project}:main", Some(projects.head.build), Seq(jb.Configuration.Compile))
        ),
        forProductionSources = Seq(ProjectDependencyData(s"${projects(1).project}:main", Some(projects(1).build), Seq(jb.Configuration.Provided)))
      )
      val moduleDependencies = Dependencies(
        forTestSources = Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(ModuleDependencyData(toIdentifier(moduleId), Seq(jb.Configuration.Provided)))
      )
      val jarDependencies = Dependencies(
        forTestSources = Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Compile))),
        forProductionSources = Seq(JarDependencyData(file("bar.jar"), Seq(jb.Configuration.Provided)))
      )
      val expected = DependencyData(
        projects = projectDependencies,
        modules = moduleDependencies,
        jars = jarDependencies
      )
      assertIdentical(expected, actual)
    }
  }

  def assertIdentical(expected: DependencyData, actual: DependencyData): Unit = {
    def checkAllDependenciesElements[D](actual: Dependencies[D], expected: Dependencies[D]): Unit = {
      actual.forTestSources must contain theSameElementsAs expected.forTestSources
      actual.forProductionSources must contain theSameElementsAs expected.forProductionSources
    }
    checkAllDependenciesElements(actual.projects, expected.projects)
    checkAllDependenciesElements(actual.modules, expected.modules)
    checkAllDependenciesElements(actual.jars, expected.jars)
  }

  def attributedWith(file: File)(moduleId: ModuleID,
                                 artifact: Artifact): Attributed[File] =
    Attributed(file)(
      AttributeMap.empty
        .put(sbt.Keys.moduleID.key, moduleId)
        .put(sbt.Keys.artifact.key, artifact)
    )

  def attributed(file: File): Attributed[File] =
    Attributed(file)(AttributeMap.empty)

  def toIdentifier(moduleId: ModuleID): ModuleIdentifier =
    ModuleIdentifier(
      moduleId.organization,
      moduleId.name,
      moduleId.revision,
      Artifact.DefaultType,
      ""
    )

}
