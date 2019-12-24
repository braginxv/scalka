package com.scalka

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Launch {
  def main(args: Array[String]): Unit = {
    val system = Processing.system

    val service = system.actorOf(Props[Processing])

    val counter = Iterator.from(1)
    system.scheduler.schedule(
      3 seconds, 1 second, service, s"emit value: #${counter.next}")

    system.scheduler.scheduleOnce(15 seconds, service, Processing.Shutdown)

    println("wait system shutdown")
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

class Processing extends Actor {
  import Processing._
  implicit val materializer = ActorMaterializer()

  val receiver = Source
    .actorRef[String](QUEUE_SIZE, OverflowStrategy.backpressure)
    .map(_.toUpperCase())
    .to(Sink.ignore)
    .run()

  override def receive: Receive = {
    case SendMessage(message) =>
      receiver ! message

    case received: String =>
      println(s"incoming message: '$received'")

    case Shutdown =>
      receiver ! PoisonPill
  }
}

object Processing {
  implicit val system = ActorSystem()

  val QUEUE_SIZE = 10
  case object Shutdown

  final case class SendMessage(message: String)
}
