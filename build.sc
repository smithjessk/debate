import $ivy.`com.goyeau::mill-scalafix::0.2.11` // TODO refactor where this is
import com.goyeau.mill.scalafix.ScalafixModule
import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.scalalib.scalafmt._
import coursier.maven.MavenRepository
// import ammonite.ops._
import os._

// val thisPublishVersion = "0.1.0-SNAPSHOT"
val thisScalaVersion = "2.13.8"
val thisScalaJSVersion = "1.6.0"

val kindProjectorVersion = "0.13.2"

val jjmVersion = "0.2.1"
val declineVersion = "1.0.0"
val boopickleVersion = "1.4.0"
val logbackVersion = "1.2.3"
val osLibVersion = "0.8.0"

val scalajsDomVersion = "1.1.0"
val scalajsJqueryVersion = "1.0.0"
val scalacssVersion = "0.7.0"

val jqueryVersion = "2.1.4"
val reactVersion = "15.6.1"

trait CommonModule extends ScalaModule with ScalafmtModule with ScalafixModule {

  def scalaVersion = thisScalaVersion

  def platformSegment: String

  override def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  override def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-Ymacro-annotations",
    "-Ywarn-unused"
  )

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    // ivy"io.tryp:::splain:$splainVersion",
    ivy"org.typelevel:::kind-projector:$kindProjectorVersion"
  )

  override def ivyDeps = Agg(
    // most of the FP dependencies are pulled in by JJM
    ivy"org.julianmichael::jjm-core::$jjmVersion",
    ivy"org.julianmichael::jjm-io::$jjmVersion",
    ivy"io.suzaku::boopickle::$boopickleVersion",
    // ivy"org.typelevel::kittens::$kittensVersion",
    ivy"io.github.cquiroz::scala-java-time::2.0.0" // TODO probably not the right version
  )
}

trait JsPlatform extends CommonModule with ScalaJSModule {
  def scalaJSVersion = T(thisScalaJSVersion)
  def platformSegment = "js"
}

trait JvmPlatform extends CommonModule {
  def platformSegment = "jvm"
}

import $file.`build-scripts`.SimpleJSDepsBuild, SimpleJSDepsBuild.SimpleJSDeps

object debate extends Module {
  trait DebateModule extends CommonModule {
    def millSourcePath = build.millSourcePath / "debate"
  }

  trait JvmBase extends DebateModule with JvmPlatform {

    // resolvers += Resolver.sonatypeRepo("snapshots"),

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::os-lib:$osLibVersion",
      // ivy"com.lihaoyi::scalatags:0.8.2",
      ivy"com.lihaoyi::scalatags:0.8.2",
      ivy"com.monovore::decline::$declineVersion",
      ivy"com.monovore::decline-effect::$declineVersion",
      // java dependencies
      ivy"ch.qos.logback:logback-classic:$logbackVersion"
    )

    def resources = T.sources(
      millSourcePath / "resources",
      debate.js.fastOpt().path / RelPath.up,
      debate.js.aggregatedJSDeps().path / RelPath.up
    )
  }

  object jvm extends JvmBase

  object prod extends JvmBase {
    override def resources = T.sources(
      millSourcePath / "resources",
      debate.js.fullOpt().path / RelPath.up,
      debate.js.aggregatedJSDeps().path / RelPath.up
    )
  }

  object js extends DebateModule with JsPlatform with SimpleJSDeps {

    def mainClass = T(Some("debate.App"))

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.julianmichael::jjm-ui::$jjmVersion",
      ivy"com.github.japgolly.scalacss::core::$scalacssVersion",
      ivy"com.github.japgolly.scalacss::ext-react::$scalacssVersion",
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"be.doeraene::scalajs-jquery::$scalajsJqueryVersion"
    )

    def jsDeps = Agg(
      s"https://code.jquery.com/jquery-$jqueryVersion.min.js",
      s"https://cdnjs.cloudflare.com/ajax/libs/react/$reactVersion/react.js",
      s"https://cdnjs.cloudflare.com/ajax/libs/react/$reactVersion/react-dom.js"
    )
  }
}
