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
  server.accept(null, new CompletionHandler{
    def completed(buf: ByteBuffer) = {
      buf.clear()

    }
  })
}
