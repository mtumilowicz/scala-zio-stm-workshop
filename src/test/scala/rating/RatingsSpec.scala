package rating

import rating.Ratings
import zio.stm.{TMap, TRef}
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue, check}
import zio.{UIO, ZIO}

object RatingsSpec extends ZIOSpecDefault {

  def genRatings(biggerThan: Int, smallerThan: Int): Gen[Any, List[(String, Int)]] =
    Gen.listOfBounded(biggerThan, smallerThan)(Gen.uuid.map(_.toString) <*> Gen.int)

  val spec: Spec[TestEnvironment, Any] =
    suite("RatingsSpec")(
      test("adding a rating should update top and all") {
        check(Gen.string, Gen.int(0, 100)) { (id, rating) =>
          for {
            topRef <- TRef.make(Top(5)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            _ <- ratings.add(id, rating)
            top <- topRef.get.commit
            all <- all.get(id).commit
          } yield assertTrue(top.values.contains(rating), all.contains(rating))
        }
      },
      test("indexing many ratings should update top and all") {
        check(genRatings(20, 1000)) { list =>
          val top = 10
          for {
            topRef <- TRef.make(Top(top)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            _ <- ratings.add(list)
            allValues <- all.toMap.commit
            topValues <- ratings.top.get.map(_.values).commit
          } yield assertTrue(
            topValues == allValues.values.toList.sorted(Ordering[Int].reverse).take(top).toSet,
            list.forall { case (id, rating) => allValues(id) == rating })
        }
      },
      test("indexing many ratings should keep top and all synchronized at any point") {
        check(genRatings(20, 1000)) { list =>
          val size = 10
          for {
            topRef <- TRef.make(Top(size)).commit
            all <- TMap.empty[String, Int].commit
            ratings = Ratings(topRef, all)
            addingJob <- ratings.add(list).fork
            checkingJob <- (for {
              allValues <- all.toMap.commit
              topValues <- ratings.top.get.map(_.values).commit
              _ <- ZIO.fail("should never fail")
                .unless(
                  topValues == allValues.values.toList.sorted(Ordering[Int].reverse).take(size).toSet
                    && list.forall { case (id, rating) => allValues(id) == rating }
                )
            } yield ()).forever.fork
            _ <- addingJob.join
            _ <- checkingJob.interrupt
          } yield assertCompletes
        }
      },
    )
}

extension (ratings: Ratings) def add(values: List[(String, Int)]): UIO[Unit] =
  ZIO.foreachParDiscard(values) { value => ratings.add.tupled(value) }