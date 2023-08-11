package weaver

import cats.effect.Resource
import com.twitter.util.Future

trait TwitterFutureGlobalResource extends GlobalResourceF[Future] {
  def sharedResources(global: GlobalWrite): Resource[Future, Unit]
}
