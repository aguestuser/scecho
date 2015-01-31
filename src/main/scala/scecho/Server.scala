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

  def listen(chn: AsynchronousServerSocketChannel) : Unit = {
    println(s"Scecho listening on port ${chn.getLocalAddress.toString}")
    val cnxn = accept(chn)
    Await.result(cnxn, Duration.Inf)
    cnxn onSuccess { case c => echo(c) }
    listen(chn)
  }

  def accept(listener: AsynchronousServerSocketChannel): Future[AsynchronousSocketChannel] = {
    val p = Promise[AsynchronousSocketChannel]()
    listener.accept(null, new CompletionHandler[AsynchronousSocketChannel, Void] {
      def completed(cnxn: AsynchronousSocketChannel, att: Void) = {
        println(s"Client connection received from ${cnxn.getLocalAddress.toString}")
        p success { cnxn }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def echo(cnxn: AsynchronousSocketChannel): Future[Unit] = {
    for {
      input <- read(cnxn)
      done <- dispatchInput(input, cnxn)
    } yield done
  }
  
  def read(cnxn: AsynchronousSocketChannel): Future[Array[Byte]] = {
    val buf = ByteBuffer.allocate(1024) // TODO what happens to this memory allocation?
    val p = Promise[Array[Byte]]()
    cnxn.read(buf, null, new CompletionHandler[Integer, Void] {
      def completed(numRead: Integer, att: Void) = {
        println(s"Read ${numRead.toString} bytes")
        buf.flip()
        p success { buf.array() }
      }
      def failed(e: Throwable, att: Void) = p failure { e }
    })
    p.future
  }

  def dispatchInput(input: Array[Byte], cnxn: AsynchronousSocketChannel): Future[Unit] = {
    if (input.map(_.toChar).mkString.trim == "exit") Future.successful(())
    else Future { write(input,cnxn) }
  }

  def write(bs: Array[Byte], cnxn: AsynchronousSocketChannel): Future[Unit] = {
    for {
      numWritten <- writeOnce(bs, cnxn)
      res <- dispatchWrite(numWritten, bs, cnxn)
    } yield echo(cnxn)
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

  def dispatchWrite(numWritten: Int, bs: Array[Byte], cnxn: AsynchronousSocketChannel): Future[Unit] = {
    if(numWritten == bs.size) Future.successful(())
    else write(bs.drop(numWritten), cnxn)
  }
}