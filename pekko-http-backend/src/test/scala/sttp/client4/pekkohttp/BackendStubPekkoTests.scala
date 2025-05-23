package sttp.client4.pekkohttp

import org.apache.pekko
import pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import sttp.client4.testing.ResponseStub

class BackendStubPekkoTests extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()

  override protected def afterAll(): Unit =
    Await.result(system.terminate().map(_ => ()), 5.seconds)

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    val backend = PekkoHttpBackend.stub
      .whenRequestMatches(_ => true)
      .thenRespondCyclic(ResponseStub.adjust("a"), ResponseStub.adjust("b"), ResponseStub.adjust("c"))

    // when
    def r = basicRequest.get(uri"http://example.org/a/b/c").send(backend).futureValue

    // then
    r.body shouldBe Right("a")
    r.body shouldBe Right("b")
    r.body shouldBe Right("c")
    r.body shouldBe Right("a")
  }
}
