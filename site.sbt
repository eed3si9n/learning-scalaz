lazy val siteDirectory = settingKey[File]("output dir for site")
lazy val genSite = taskKey[File]("generate site")
lazy val packageSite = taskKey[File]("package site")
lazy val packageSitePath = settingKey[File]("path for the package")

siteDirectory := target.value / "www"

packageSitePath := target.value / "learningscalaz71.tar.gz"

packageSite := {
  val out = packageSitePath.value
  IO.delete(out)
  val siteDir = genSite.value
  val items = ((siteDir ** "*").get map { _.relativeTo(siteDir) }).flatten
  Process(s"""tar zcf ${ packageSitePath.value.getAbsolutePath } ${ items.mkString(" ") }""", Some(siteDir)).!
  out
}

genSite := {
  val wwwDir = siteDirectory.value
  IO.delete(wwwDir)
  IO.createDirectory(wwwDir)
  s"pf docs $wwwDir".!
  val en = IO.read(wwwDir / "Combined+Pages.md", IO.utf8)
  val enMod = """(?m)learning Scalaz\r?\n=+""".r replaceAllIn (en, "---\ntitle: learning Scalaz\nauthor: eugene yokota (\\@eed3si9n)\ntags: [scala, scalaz]\n...\n\npreface\n-------")
  IO.write(wwwDir / "learning-scalaz.md", enMod, IO.utf8)
  Process(s"""pandoc ${ wwwDir / "learning-scalaz.md" } -o ${ wwwDir / "learning-scalaz.pdf" } --latex-engine=xelatex --toc""", Some(wwwDir)).!
  val jaDir = wwwDir / "ja"
  val ja = IO.read(jaDir / "Combined+Pages.md", IO.utf8)
  val jaMod = """(?m)独習 Scalaz\r?\n=+""".r replaceAllIn (ja, "---\ntitle: 独習 Scalaz\nauthor: eugene yokota (\\@eed3si9n_ja)\ntags: [scala, scalaz]\n...\n\n前書き\n-------")
  IO.write(jaDir / "learning-scalaz.md", jaMod, IO.utf8)
  Process(s"""pandoc ${ jaDir / "learning-scalaz.md" } -o ${ jaDir / "learning-scalaz.pdf" } -V documentclass=ltjarticle --latex-engine=lualatex --toc""", Some(jaDir)).!
  wwwDir
}
