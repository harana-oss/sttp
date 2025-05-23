package sttp.client4.armeria.cats

import cats.effect.{Concurrent, Resource, Sync}
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import org.reactivestreams.Publisher
import sttp.client4.armeria.ArmeriaWebClient.newClient
import sttp.client4.armeria.{AbstractArmeriaBackend, BodyFromStreamMessage}
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.internal.NoStreams
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, Backend, BackendOptions}
import sttp.monad.MonadAsyncError
import cats.effect.ExitCase

private final class ArmeriaCatsBackend[F[_]: Concurrent](client: WebClient, closeFactory: Boolean)
    extends AbstractArmeriaBackend[F, Nothing](client, closeFactory, new CatsMonadAsyncError) {

  override val streams: NoStreams = NoStreams

  override protected def bodyFromStreamMessage: BodyFromStreamMessage[F, Nothing] =
    new BodyFromStreamMessage[F, Nothing] {

      override val streams: NoStreams = NoStreams

      override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError

      override def publisherToStream(streamMessage: StreamMessage[HttpData]): Nothing =
        throw new UnsupportedOperationException("This backend does not support streaming")
    }

  override protected def streamToPublisher(stream: Nothing): Publisher[HttpData] =
    throw new UnsupportedOperationException("This backend does not support streaming")

  override protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T] =
    Concurrent[F].guaranteeCase(effect) { exit =>
      if (exit == ExitCase.Completed) Concurrent[F].unit
      else Concurrent[F].recoverWith(finalizer) { case t => Concurrent[F].delay(t.printStackTrace()) }
    }
}

object ArmeriaCatsBackend {

  /** Creates a new Armeria backend, using the given or default `SttpBackendOptions`. Due to these customisations, the
    * client will manage its own connection pool. If you'd like to reuse the default Armeria
    * [[https://armeria.dev/docs/client-factory ClientFactory]] use `.usingDefaultClient`.
    */
  def apply[F[_]: Concurrent](options: BackendOptions = BackendOptions.Default): Backend[F] =
    apply(newClient(options), closeFactory = true)

  def resource[F[_]: Concurrent](
      options: BackendOptions = BackendOptions.Default
  ): Resource[F, Backend[F]] =
    Resource.make(Sync[F].delay(apply(newClient(options), closeFactory = true)))(_.close())

  def resourceUsingClient[F[_]: Concurrent](client: WebClient): Resource[F, Backend[F]] =
    Resource.make(Sync[F].delay(apply(client, closeFactory = true)))(_.close())

  def usingDefaultClient[F[_]: Concurrent](): Backend[F] =
    apply(newClient(), closeFactory = false)

  def usingClient[F[_]: Concurrent](client: WebClient): Backend[F] =
    apply(client, closeFactory = false)

  private def apply[F[_]: Concurrent](
      client: WebClient,
      closeFactory: Boolean
  ): Backend[F] =
    wrappers.FollowRedirectsBackend(new ArmeriaCatsBackend(client, closeFactory))
}
