package example.test

import minitest._
import scala.sys.process._
import java.io.File

object SbtRunnerTest extends SimpleTestSuite with PowerAssertions {
  // 1.3.0, 1.3.0-M4
  private val versionRegEx = "\\d(\\.\\d+){2}(-\\w+)?"

  lazy val isCygwin: Boolean = java.lang.Boolean.getBoolean("test.cygwin")
  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")
  lazy val sbtScript =
    if (isWindows && !isCygwin) new File("target/universal/stage/bin/sbt.bat")
    else new File("target/universal/stage/bin/sbt")

  def cygpath(v: File): String =
    sbt.internal.Process("""C:\cygwin64\bin\cygpath -u """ + v.getAbsolutePath).!!.trim

  def sbtProcess(arg: String) = sbtProcessWithOpts(arg, "", "")
  def sbtProcessWithOpts(arg: String, javaOpts: String, sbtOpts: String) = {
    val p = if (isCygwin) """C:\cygwin64\bin\bash --login -c "cd """ +
              cygpath(new File("citest")) + ";" + cygpath(sbtScript) + " " + arg + "\""
            else sbtScript.getAbsolutePath + " " + arg
    sbt.internal.Process(p, new File("citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts)
  }

  test("sbt runs") {
    assert(sbtScript.exists)
    val out = sbtProcess("compile -v").!
    assert(out == 0)
    ()
  }

  test("sbt -no-colors") {
    val out = sbtProcess("compile -no-colors -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --no-colors") {
    val out = sbtProcess("compile --no-colors -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --color=false") {
    val out = sbtProcess("compile --color=false -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.color=false"))
    ()
  }

  test("sbt --debug-inc") {
    val out = sbtProcess("compile --debug-inc -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dxsbt.inc.debug=true"))
    ()
  }

  test("sbt --supershell=never") {
    val out = sbtProcess("compile --supershell=never -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.supershell=never"))
    ()
  }

  test("sbt --timings") {
    val out = sbtProcess("compile --timings -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.task.timings=true"))
    ()
  }

  test("sbt -D arguments") {
    val out = sbtProcess("-Dsbt.supershell=false compile -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.supershell=false"))
    ()
  }

  test("sbt --sbt-version") {
    val out = sbtProcess("--sbt-version 1.3.0 compile -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.version=1.3.0"))
    ()
  }

  test("sbt -mem 503") {
    val out = sbtProcess("compile -mem 503 -v").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    ()
  }

  test("sbt with -mem 503, -Xmx in JAVA_OPTS") {
    val out = sbtProcessWithOpts("compile -mem 503 -v", "-Xmx1024m", "").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    assert(!out.contains[String]("-Xmx1024m"))
    ()
  }

  test("sbt with -mem 503, -Xmx in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -mem 503 -v", "", "-Xmx1024m").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    assert(!out.contains[String]("-Xmx1024m"))
    ()
  }

  test("sbt with -Xms2048M -Xmx2048M -Xss6M in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-Xms2048M -Xmx2048M -Xss6M").!!.linesIterator.toList
    assert(out.contains[String]("-Xss6M"))
    ()
  }

  test("sbt with -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080").!!.linesIterator.toList
    assert(out.contains[String]("-Dhttp.proxyHost=proxy"))
    assert(out.contains[String]("-Dhttp.proxyPort=8080"))
    ()
  }

  test("sbt with -XX:ParallelGCThreads=16 -XX:PermSize=128M in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-XX:ParallelGCThreads=16 -XX:PermSize=128M").!!.linesIterator.toList
    assert(out.contains[String]("-XX:ParallelGCThreads=16"))
    assert(out.contains[String]("-XX:PermSize=128M"))
    ()
  }

  test("sbt with -XX:+UseG1GC -XX:+PrintGC in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-XX:+UseG1GC -XX:+PrintGC").!!.linesIterator.toList
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))
    ()
  }

  test("sbt with -XX:-UseG1GC -XX:-PrintGC in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-XX:-UseG1GC -XX:-PrintGC").!!.linesIterator.toList
    assert(out.contains[String]("-XX:-UseG1GC"))
    assert(out.contains[String]("-XX:-PrintGC"))
    assert(!out.contains[String]("-XX:-UseG1GC=-XX:-PrintGC"))
    ()
  }

  test("sbt with --no-colors in SBT_OPTS") {
    if (isWindows && !isCygwin) cancel("Test not supported on windows")
    val out = sbtProcessWithOpts("compile -v", "", "--no-colors").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt -V|-version|--version should print sbtVersion") {
    val out = sbtProcessWithOpts("-version", "", "").!!.trim
    val expectedVersion =
      s"""|(?m)^sbt version in this project: $versionRegEx(\\r)?
          |sbt script version: $versionRegEx$$
          |""".stripMargin.trim.replace("\n", "\\n")
    assert(out.matches(expectedVersion))

    val out2 = sbtProcessWithOpts("--version", "", "").!!.trim
    assert(out2.matches(expectedVersion))

    val out3 = sbtProcessWithOpts("-V", "", "").!!.trim
    assert(out3.matches(expectedVersion))
    ()
  }

  test("sbt --numeric-version should print sbt script version") {
    val out = sbtProcessWithOpts("--numeric-version", "", "").!!.trim
    val expectedVersion = "^"+versionRegEx+"$"
    assert(out.matches(expectedVersion))
    ()
  }

  test("sbt --script-version should print sbtVersion") {
    val out = sbtProcessWithOpts("--script-version", "", "").!!.trim
    val expectedVersion = "^"+versionRegEx+"$"
    assert(out.matches(expectedVersion))
    ()
  }

  test("sbt --sbt-jar should run") {
    val out = sbtProcess("compile -v --sbt-jar ../target/universal/stage/bin/sbt-launch.jar").!!.linesIterator.toList
    assert(out.contains[String]("../target/universal/stage/bin/sbt-launch.jar") ||
      out.contains[String]("\"../target/universal/stage/bin/sbt-launch.jar\"")
    )
    ()
  }
}
