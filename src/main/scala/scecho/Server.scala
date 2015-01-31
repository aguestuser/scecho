package scecho

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Created by aguestuser on 1/28/15.
 */

object Main extends App {
  Try(args(0).toInt) match {
    case Failure(e) => throw new IllegalArgumentException("Argument to scecho must be a valid Integer")
    case Success(i) => Server.listenOn(Server.getChannel(i))
  }
}

object Server {

  def getChannel(port: Int): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def listenOn(chn: AsynchronousServerSocketChannel) : Unit = {
    println("Scecho listening on port %s".format(chn.getLocalAddress.toString))

    val anEcho = for {
      cnxn <- Server.accept(chn)
      input <- Server.read(cnxn)
      done <- Server.write(input, cnxn)
    } yield done

    Await.result(anEcho, Duration.Inf)
    listenOn(chn)
    // TODO create execution path that keeps connection to client alive
    // until message received that says to close it
  }

  def accept(listener: AsynchronousServerSocketChannel): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]()
    listener.accept(null, new CompletionHandler[AsynchronousSocketChannel, Void] {
      def completed(cnxn: AsynchronousSocketChannel, att: Void) = {
        println("Client connection received from %s".format(cnxn.getLocalAddress.toString))
        p success { cnxn }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def read(chn: AsynchronousSocketChannel): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024)
    val p = Promise[Array[Byte]]()
    chn.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println("Read %s bytes".format(numRead.toString))
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future

  }

  def write(bs: Array[Byte], chn: AsynchronousSocketChannel): Future[Unit] = {
    val done = (numWritten:Integer) => numWritten == bs.size
    for {
      nw <- writeOnce(bs, chn)
      res <- { if(done(nw)) Future.successful(()) else write(bs.drop(nw), chn) }
    } yield res
  }

  def writeOnce(bs: Array[Byte], chn: AsynchronousSocketChannel): Future[Integer] = {
    val p = Promise[Integer]()
    chn.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numWritten: Integer, att: Void) = {
        println("Echoed %s bytes".format(numWritten.toString))
        p success { numWritten }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }
}