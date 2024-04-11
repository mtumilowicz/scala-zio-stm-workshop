package rating

import rating.{Ratings, Top}
import zio.{UIO, ZIO}
import zio.stm.{TMap, TRef}
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check, sized}

object RatingsSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment, Any] =
    suite("RatingsSpec")(
      test("adding a rating should update top and all") {
        check(Gen.string, Gen.int(0, 100)) { (id, rating) =>
          for {
            topRef <- TRef.make(Top(5)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            _      <- ratings.add(id, rating)
            top    <- topRef.get.commit
            all    <- all.get(id).commit
          } yield assertTrue(top.values.contains(rating), all.contains(rating))
        }
      },
      test("indexing many ratings should keep top and all") {
        check(Gen.listOfBounded(20, 1000)(Gen.uuid.map(_.toString) <*> Gen.int)) { list =>
          val size = 10
          for {
            topRef <- TRef.make(Top(size)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            _      <- ratings.addAll(list)
            allValues <- all.toMap.commit
            topValues <- ratings.top.get.map(_.values).commit
          } yield assertTrue(
            topValues == allValues.values.toList.sorted(Ordering[Int].reverse).take(size).toSet,
            list.forall { case (id, rating) => allValues(id) == rating })
        }
      },
      test("indexing many ratings should keep top and all") {
        check(Gen.listOfBounded(20, 1000)(Gen.uuid.map(_.toString) <*> Gen.int)) { list =>
          val size = 10
          for {
            topRef <- TRef.make(Top(size)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            _      <- ratings.addAll(list)
            allValues <- all.toMap.commit
            topValues <- ratings.top.get.map(_.values).commit
          } yield assertTrue(
            topValues == allValues.values.toList.sorted(Ordering[Int].reverse).take(size).toSet,
            list.forall { case (id, rating) => allValues(id) == rating })
        }
      },
    )
}

extension (ratings: Ratings) def addAll(values: List[(String, Int)]): UIO[Unit] =
  ZIO.foreachParDiscard(values) { value => ratings.add.tupled(value) }