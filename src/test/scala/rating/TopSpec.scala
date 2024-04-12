package rating

import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

object TopSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("TopRatings tests")(
      test("Top.add should respect size limit") {
        check(Gen.int(1, 10)) { size =>
          val values = 1 to (size + 1)
          val top = Top(size)
          val updatedTop = top.addAll(values.toList)
          assertTrue(updatedTop.size == size)
        }
      },
      test("Top.add reaching full") {
        check(Gen.int(1, 100)) { size =>
          val existingValues = (1 until size).toList
          val top = Top(size).addAll(existingValues)
          val updatedTop = top.add(size)
          assertTrue(updatedTop.values.size == size, updatedTop.values.toList.sorted == existingValues :+ size)
        }
      },
      test("Top.add when full adding less than min does not change anything") {
        check(Gen.int(1, 100)) { size =>
          val existingValues = (1 to size).toList
          val top = Top(size).addAll(existingValues)
          val updatedTop = top.add(0)
          assertTrue(updatedTop.values.size == size, updatedTop.values.toList.sorted == existingValues)
        }
      },
      test("Top.add when full adding more than min removes previous min") {
        check(Gen.int(1, 100)) { size =>
          val existingValues = (1 to (size + 1)).toList
          val top = Top(size).addAll(existingValues)
          assertTrue(top.values.size == size, top.values.toList.sorted == (2 to (size + 1)))
        }
      },
    )
}

extension (top: Top) def addAll(values: List[Int]): Top =
  values.foldLeft(top) { case (acc, value) => acc.add(value) }
