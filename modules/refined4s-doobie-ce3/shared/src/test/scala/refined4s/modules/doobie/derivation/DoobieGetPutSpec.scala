package refined4s.modules.doobie.derivation

import cats.*
import cats.effect.*
import cats.syntax.all.*
import doobie.syntax.all.*
import extras.doobie.RunWithDb
import extras.doobie.ce3.DbTools
import extras.hedgehog.ce3.CatsEffectRunner
import hedgehog.*
import hedgehog.runner.*
import refined4s.*
import refined4s.modules.cats.derivation.*
import refined4s.types.all.*

/** @author Kevin Lee
  * @since 2023-12-16
  */
object DoobieGetPutSpec extends Properties with CatsEffectRunner with RunWithDb {

  import effectie.instances.ce3.fx.ioFx

  type F[A] = IO[A]
  val F: IO.type = IO

  override def tests: List[Test] = List(
    propertyWithDb(
      "test DoobiePut, DoobieNewtypeGet, DoobieRefinedGet, DoobieNewtypeGetPut and DoobieRefinedGetPut all together by fetching and updating data",
      testFetchUpdateFetch,
    )
  )

  def testFetchUpdateFetch(testName: String): Property =
    for {
      example <- genExampleWithDoobieGetPut.log("example")
    } yield runIO(withDb[F](testName) { transactor =>
      val expectedFetchBefore = none[ExampleWithDoobieGetPut]
      val expectedInsert      = 1
      val expectedFetchAfter  = example.some

      val fetch = DbTools.fetchSingleRow[F][ExampleWithDoobieGetPut](
        sql"""
          SELECT id, name, note, count
            FROM db_tools_test.example
        """
      )(transactor)

      val insert = DbTools.updateSingle[F](
        sql"""
          INSERT INTO db_tools_test.example (id, name, note, count) VALUES (${example.id}, ${example.name}, ${example.note}, ${example.count})
        """
      )(transactor)

      for {
        fetchResultBefore <- fetch.map(_ ==== expectedFetchBefore)
        insertResult      <- insert.map(_ ==== expectedInsert)
        fetchResultAfter  <- fetch.map(_ ==== expectedFetchAfter)
      } yield Result.all(
        List(
          fetchResultBefore.log("Failed: fetch before"),
          insertResult.log("Failed: insert"),
          fetchResultAfter.log("Failed: fetch after"),
        )
      )
    })

  def genExampleWithDoobieGetPut: Gen[ExampleWithDoobieGetPut] = for {
    id    <- Gen.int(Range.linear(1, Int.MaxValue)).map(ExampleWithDoobieGetPut.Id(_))
    name  <- Gen.string(Gen.alphaNum, Range.linear(5, 20)).map(s => ExampleWithDoobieGetPut.Name(NonEmptyString.unsafeFrom(s)))
    note  <-
      Gen.string(Gen.alphaNum, Range.linear(5, 20)).map(s => ExampleWithDoobieGetPut.Note(ExampleWithDoobieGetPut.MyString.unsafeFrom(s)))
    count <- Gen.int(Range.linear(0, Int.MaxValue)).map(ExampleWithDoobieGetPut.Count.unsafeFrom)
  } yield ExampleWithDoobieGetPut(id, name, note, count)
}