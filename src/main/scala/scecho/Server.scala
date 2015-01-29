package scecho

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousSocketChannel, CompletionHandler, AsynchronousServerSocketChannel}

import scala.util.{Failure, Success, Try}



/**
 * Created by aguestuser on 1/28/15.
 */

object Main extends App {
  Try(args(0).toInt) match {
    case Success(i) => Server(i)
    case Failure(e) => throw new Exception("Argument to scecho must be a valid Integer")
  }
}

case class Server(port: Int) {
  val server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))
  val buf = ByteBuffer.allocate(1024) // <- is this where i should declare this?

  server.accept(null, new CompletionHandler {
    def completed(chn: AsynchronousSocketChannel, /* what goes here? What is a `_ >:NotInferredA` ???*/) = {

      // why does intellij tell me i've passed too many arguments to server.accept?

      // should i be doing some intermediate step on the channel here
      // instead of going ahead and writing to it?

      //this is what i do with a buffer... but i'm not sure i should be doing this here??
      buf.clear()
      chn.read(buf)
      buf.flip()
      chn.write(buf)

      // UMMM.... this all feels very side-effecty! What do I return from here?
    }
  })
  // why does IntelliJ tell me: the following about server.accept:
  // `Type mismatch:
  // expected:
  //    CompletionHandler[AsynchronousSocketChannel,_ >:NotInferedA],
  // actual:
  //    CompletionHandler with Object {def completed(chn: AsynchronousSocketChannel): Future[Integer]


}
