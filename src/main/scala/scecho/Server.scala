package scecho

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Created by aguestuser on 1/28/15.
 */

object Main extends App {
  import scecho.Server._
  try {
    listen(getChannel(args(0).toInt))
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scecho must be a valid int")
  }
}

object Server {

  def getChannel(port: Int): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port))

  def listen(sSock: AsynchronousServerSocketChannel) : Future[Unit] = {
    println(s"Scecho listening on port ${sSock.getLocalAddress.toString}")

    val sock = accept(sSock)
    Await.result(sock, Duration.Inf)
    sock onSuccess { case c => echo(c) }
    listen(sSock)
  }

  def accept(sSock: AsynchronousServerSocketChannel): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]()
    sSock.accept(null, new CompletionHandler[AsynchronousSocketChannel, Void] {
      def completed(sock: AsynchronousSocketChannel, att: Void) = {
        println(s"Client connection received from ${sock.getLocalAddress.toString}")
        p success { sock }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def echo(sock: AsynchronousSocketChannel): Future[Unit] =
    { for {
        input <- read(sock)
        done <- dispatchInput(input, sock)
      } yield done } flatMap { _ => echo(sock) }

  def read(sock: AsynchronousSocketChannel): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
    val p = Promise[Array[Byte]]()
    sock.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println(s"Read ${numRead.toString} bytes")
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def dispatchInput(input: Array[Byte], sock: AsynchronousSocketChannel) : Future[Unit] = {
    if (input.map(_.toChar).mkString.trim == "exit") Future.successful(())
    else write(input,sock)
  }

  def write(bs: Array[Byte], sock: AsynchronousSocketChannel): Future[Unit] = {
    for {
      numWritten <- writeOnce(bs, sock)
      res <- dispatchWriteContinuation(numWritten, bs, sock)
    } yield res
  }

  def writeOnce(bs: Array[Byte], chn: AsynchronousSocketChannel): Future[Integer] = {
    val p = Promise[Integer]()
    chn.write(ByteBuffer.wrap(bs), null, new CompletionHandler[Integer, Void] {
      def completed(numWritten: Integer, att: Void) = {
        println(s"Echoed ${numWritten.toString} bytes")
        p success { numWritten }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def dispatchWriteContinuation(numWritten: Int, bs: Array[Byte], sock: AsynchronousSocketChannel): Future[Unit] = {
    if(numWritten == bs.size) Future.successful(())
    else write(bs.drop(numWritten), sock)
  }
}