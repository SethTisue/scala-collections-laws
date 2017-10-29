package laws

trait Generator[A, B, CC] {
  def instanceExplorer(): Exploratory[Instance[A, CC]]
  def opsExplorer(): Exploratory[Ops[A, B]]
  def autoTags: Set[Tag]
  def ccType: String
  def eltType: String
  def eltCC: String = eltType
  def safeElt: String = eltType.collect{
    case ',' => '_'
    case c if c.isLetterOrDigit => c
  }
  def altType: String
  def heritage: String
  def generatorName: String
  def pkgName: String
  def pkgFull: String
  def colType: String = f"$pkgFull.$ccType[$eltCC]"
  def instTypes: String = f"$eltType, $colType"
  def opsTypes: String = f"$eltType, $altType"
  def className: String = f"Test_${pkgName}_${ccType}_${safeElt}"
  def code: String = {
    val instance = instanceExplorer.completeIterator.take(1).toList.head
    val appropriate = Laws.all.
      filter(law => law.checker passes instance.methods).
      filter(law => law.tags compatible (autoTags ++ instance.flags)).
      sortBy(_.lineNumber);
    (
      Array(
        f"// Autogenerated test for collection $ccType with element type $eltType",
        f"",
        f"package laws",
        f"",
        f"class $className(numb: Numbers, oper: Ops[$opsTypes], inst: Instance[$instTypes], lln: Int)",
        f"extends $heritage[$colType, $className](numb, oper, inst, lln) {",
        f"  import Test.ComparesTo",
        f"  import Test.Logic",
        f"",
        f"  def renumber(numb: Numbers) = ",
        f"    new $className(numb, ops, instance, lawLine)",
        f"",
        f"  def reinstance(inst: Instance[$instTypes]) = ",
        f"    new $className(num, ops, inst, lawLine)",
        f"",
        f"  def reoperate(oper: Ops[$opsTypes]) = ",
        f"    new $className(num, oper, instance, lawLine)",
        f"",
        f"  def relaw(lln: Int) = new $className(num, ops, instance, lln)",
        f"",
        f"  /***** Individual laws begin here *****/",
        f""
      ) ++
      appropriate.map{ law =>
        f"  def runLaw${law.lineNumber}: Boolean = {\n" +
        law.cleanCode.split("\n").map("    " + _).mkString("", "\n", "\n") +
        f"  }\n"
      } ++
      Array(
        f"  /****** Individual laws end here ******/",
        f"",
        f"  val lawTable: Map[Int, () => Boolean] = Map("
      ) ++
      appropriate.map(_.lineNumber).zipWithIndex.map{ case (n, i) =>
        f"    $n -> (runLaw$n _)${if (i+1 < appropriate.length)"," else ""}"
      } ++
      Array(
        f"  )",
        f"",
        f"  def runLaw(n: Int): Option[Boolean] = lawTable.get(n).map(_())",
        f"}",
        f"",
        f"",
        f"object $className extends Test.Companion {",
        f"  val lawNumbers = ${appropriate.map(_.lineNumber).mkString("Set[Int](", ", ", ")")}",
        f"",
        f"  val obeys = lawNumbers.map(n => n -> Laws.byLineNumber(n)).toMap",
        f"",
        f"  val factory: (Int, Instance[$instTypes], Ops[$opsTypes], Numbers) => $className =",
        f"    (l, i, o, n) => new $className(n, o, i, l)",
        f"",
        f"  val instanceExpy = () => $generatorName.instanceExplorer",
        f"",
        f"  val opsExpy = () => $generatorName.opsExplorer",
        f"",
        f"  def runnerOf(lln: Int): Runner[$eltType, $altType, $colType, $className] =",
        f"    new Runner(lln, instanceExpy, opsExpy, factory)",
        f"}",
        f""
      )
    ).mkString("\n")
  }
}

abstract class IntGenerator[CC] extends Generator[Int, Long, CC] {
  val opsExplorer = IntOpsExplorer
  val heritage = "IntTest"
  val eltType = "Int"
  val altType = "Long"
  val autoTags = Set(Tag.INT)
}

abstract class StrGenerator[CC] extends Generator[String, Option[String], CC] {
  val opsExplorer = StrOpsExplorer
  val heritage = "StrTest"
  val eltType = "String"
  val altType = "Option[String]"
  val autoTags = Set(Tag.STR)
  override def className: String = f"Test_${pkgName}_${ccType}_Str"
}

abstract class LongStrGenerator[CC] extends Generator[(Long, String), (String, Long), CC] {
  val opsExplorer = LongStrOpsExplorer
  val heritage = "LongStrTest"
  val eltType = "(Long, String)"
  val altType = "(String, Long)"
  val autoTags = Set.empty[Tag]
}

abstract class StrLongGenerator[CC] extends Generator[(String, Long), (Long, String), CC] {
  val opsExplorer = StrLongOpsExplorer
  val heritage = "StrLongTest"
  val eltType = "(String, Long)"
  val altType = "(Long, String)"
  val autoTags = Set.empty[Tag]
}

/** Generates all classes that take Int.
  *
  * Many commonalities with `AllStrGenerators`, but it's a such a pain to make everything generic that it's not worth abstracting.
  */
object AllIntGenerators {
  val io = InstantiatorsOfInt

  class Gen[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[Int, CC], ct: String = "")(implicit name: sourcecode.Name)
  extends IntGenerator[CC] {
    def generatorName = f"AllIntGenerators.${p.nickname}.${name.value}"
    def pkgName = p.nickname
    def pkgFull = p.fullyQualified
    override def colType = if (ct.isEmpty) super.colType else ct
    val instanceExplorer = io.map(iexp(p).tupled)
    def ccType = name.value.toString.capitalize
  }

  private val everyoneBuffer = Array.newBuilder[Gen[_, _]]

  def register[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[Int, CC], ct: String = "")(implicit name: sourcecode.Name): Gen[P, CC] = {
    val ans = new Gen(p)(iexp, ct)(name)
    everyoneBuffer += ans
    ans
  }

  object Imm {
    val hashSet = register(io.Imm)(_.hashSet())
    val list = register(io.Imm)(_.list())
    val set = register(io.Imm)(_.set())
    val stream = register(io.Imm)(_.stream())
    val vector = register(io.Imm)(_.vector())
  }

  object Mut {
    val arrayBuffer = register(io.Mut)(_.arrayBuffer())
    val hashSet = register(io.Mut)(_.hashSet())
    val wrappedArray = register(io.Mut)(_.wrappedArray())
  }

  object ImmInt {
    val bitSet = register(io.ImmInt)(_.bitSet(), "collection.immutable.BitSet")
    val range = register(io.ImmInt)(_.range(), "collection.immutable.Range")
  }

  val force = Imm :: Mut :: ImmInt :: Nil

  lazy val all = everyoneBuffer.result

  def write(targetDir: java.io.File): Map[String, Boolean] =
    all.map(g => g.className -> FileIO(new java.io.File(targetDir, g.className + ".scala"), g.code)).toMap
}

object AllStrGenerators {
  val io = InstantiatorsOfStr

  class Gen[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[String, CC], ct: String = "")(implicit name: sourcecode.Name)
  extends StrGenerator[CC] {
    def generatorName = f"AllStrGenerators.${p.nickname}.${name.value}"
    def pkgName = p.nickname
    def pkgFull = p.fullyQualified
    override def colType = if (ct.isEmpty) super.colType else ct
    val instanceExplorer = io.map(iexp(p).tupled)
    def ccType = name.value.toString.capitalize
  }

  private val everyoneBuffer = Array.newBuilder[Gen[_, _]]

  def register[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[String, CC], ct: String = "")(implicit name: sourcecode.Name): Gen[P, CC] = {
    val ans = new Gen(p)(iexp, ct)(name)
    everyoneBuffer += ans
    ans
  }

  object Imm {
    val hashSet = register(io.Imm)(_.hashSet())
    val indexedSeq = register(io.Imm)(_.indexedSeq())
    val iterable = register(io.Imm)(_.iterable())
    val linearSeq = register(io.Imm)(_.linearSeq())
    val list = register(io.Imm)(_.list())
    val queue = register(io.Imm)(_.queue())
    val seq = register(io.Imm)(_.seq())
    val set = register(io.Imm)(_.set())
    val sortedSet = register(io.Imm)(_.sortedSet())
    val stream = register(io.Imm)(_.stream())
    val traversable = register(io.Imm)(_.traversable())
    val treeSet = register(io.Imm)(_.treeSet())
    val vector = register(io.Imm)(_.vector())
  }

  object Mut {
    val array = register(io.Mut)(_.array(), "Array[String]")
    val arrayBuffer = register(io.Mut)(_.arrayBuffer())
    val arraySeq = register(io.Mut)(_.arraySeq())
    val arrayStack = register(io.Mut)(_.arrayStack())
    val buffer = register(io.Mut)(_.buffer())
    val hashSet = register(io.Mut)(_.hashSet())
    val indexedSeq = register(io.Mut)(_.indexedSeq())
    val iterable = register(io.Mut)(_.iterable())
    val linearSeq = register(io.Mut)(_.linearSeq())
    val linkedHashSet = register(io.Mut)(_.linkedHashSet())
    val listBuffer = register(io.Mut)(_.listBuffer())
    val priorityQueue = register(io.Mut)(_.priorityQueue())
    val queue = register(io.Mut)(_.queue())
    val seq = register(io.Mut)(_.seq())
    val treeSet = register(io.Mut)(_.treeSet())
    val wrappedArray = register(io.Mut)(_.wrappedArray())
  }

  val force = Imm :: Mut :: Nil

  lazy val all = everyoneBuffer.result

  def write(targetDir: java.io.File): Map[String, Boolean] =
    all.map(g => g.className -> FileIO(new java.io.File(targetDir, g.className + ".scala"), g.code)).toMap
}

object AllLongStrGenerators {
  val io = InstantiatorsOfLongStr

  class Gen[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[(Long, String), CC], ct: String = "")(implicit name: sourcecode.Name)
  extends LongStrGenerator[CC] {
    def generatorName = f"AllLongStrGenerators.${p.nickname}.${name.value}"
    def pkgName = p.nickname
    def pkgFull = p.fullyQualified
    override def colType = if (ct.isEmpty) super.colType else ct
    val instanceExplorer = io.map(iexp(p).tupled)
    def ccType = name.value.toString.capitalize
    override lazy val eltCC = eltType.dropWhile(_ == '(').reverse.dropWhile(_ == ')').reverse
  }

  private val everyoneBuffer = Array.newBuilder[Gen[_, _]]

  def register[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[(Long, String), CC], ct: String = "")(implicit name: sourcecode.Name): Gen[P, CC] = {
    val ans = new Gen(p)(iexp, ct)(name)
    everyoneBuffer += ans
    ans
  }

  object ImmKV {
    val hashMap =   register(io.ImmKV)(_.hashMap())
    val listMap =   register(io.ImmKV)(_.listMap())
    val sortedMap = register(io.ImmKV)(_.sortedMap())
    val treeMap =   register(io.ImmKV)(_.treeMap())
  }

  object MutKV {
    val hashMap       = register(io.MutKV)(_.hashMap())
    val listMap       = register(io.MutKV)(_.listMap())
    val linkedHashMap = register(io.MutKV)(_.linkedHashMap())
    val openHashMap   = register(io.MutKV)(_.openHashMap())
    val sortedMap     = register(io.MutKV)(_.sortedMap())
    val treeMap       = register(io.MutKV)(_.treeMap())
  }

  val force = ImmKV :: MutKV :: Nil

  lazy val all = everyoneBuffer.result

  def write(targetDir: java.io.File): Map[String, Boolean] =
    all.map(g => g.className -> FileIO(new java.io.File(targetDir, g.className + ".scala"), g.code)).toMap
}

object AllStrLongGenerators {
  val io = InstantiatorsOfStrLong

  class Gen[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[(String, Long), CC], ct: String = "")(implicit name: sourcecode.Name)
  extends StrLongGenerator[CC] {
    def generatorName = f"AllStrLongGenerators.${p.nickname}.${name.value}"
    def pkgName = p.nickname
    def pkgFull = p.fullyQualified
    override def colType = if (ct.isEmpty) super.colType else ct
    val instanceExplorer = io.map(iexp(p).tupled)
    def ccType = name.value.toString.capitalize
    override lazy val eltCC = eltType.dropWhile(_ == '(').reverse.dropWhile(_ == ')').reverse
  }

  private val everyoneBuffer = Array.newBuilder[Gen[_, _]]

  def register[P <: Instance.PackagePath, CC](p: P)(iexp: P => Instance.FromArray[(String, Long), CC], ct: String = "")(implicit name: sourcecode.Name): Gen[P, CC] = {
    val ans = new Gen(p)(iexp, ct)(name)
    everyoneBuffer += ans
    ans
  }

  object ImmKV {
    val hashMap =   register(io.ImmKV)(_.hashMap())
    val listMap =   register(io.ImmKV)(_.listMap())
    val sortedMap = register(io.ImmKV)(_.sortedMap())
    val treeMap =   register(io.ImmKV)(_.treeMap())
  }

  object MutKV {
    val hashMap       = register(io.MutKV)(_.hashMap())
    val listMap       = register(io.MutKV)(_.listMap())
    val linkedHashMap = register(io.MutKV)(_.linkedHashMap())
    val openHashMap   = register(io.MutKV)(_.openHashMap())
    val sortedMap     = register(io.MutKV)(_.sortedMap())
    val treeMap       = register(io.MutKV)(_.treeMap())
  }
  object MutKrefV {
    val anyRefMap     = register(io.MutKrefV)(_.anyRefMap())
  }

  val force = ImmKV :: MutKV :: MutKrefV :: Nil

  lazy val all = everyoneBuffer.result

  def write(targetDir: java.io.File): Map[String, Boolean] =
    all.map(g => g.className -> FileIO(new java.io.File(targetDir, g.className + ".scala"), g.code)).toMap
}

object GenerateAll {
  def write(targetDir: java.io.File): Map[String, Boolean] =
    AllIntGenerators.write(targetDir) ++
    AllStrGenerators.write(targetDir) ++
    AllLongStrGenerators.write(targetDir) ++
    AllStrLongGenerators.write(targetDir)
}

