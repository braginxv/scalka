package com.scalka

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.stream.scaladsl.{ Sink, Source, SourceQueueWithComplete }
import akka.stream.{ ActorMaterializer, OverflowStrategy, QueueOfferResult }

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Launch {
  def main(args: Array[String]) {
    val system = Processing.system
    val service = system.actorOf(Props[Processing])
    val log = system.log

    val counter = Iterator.from(1)
    system.scheduler.schedule(
      3 seconds, 1 second, () => Future {
        log.info("heavy preprocessing work....")
        TimeUnit.SECONDS.sleep(2)
        service ! Processing.SendMessage(s"emit value: #${counter.next}")
      })

    system.scheduler.scheduleOnce(15 seconds, service, Processing.Shutdown)

    log.info("wait system shutdown")
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

class Processing extends Actor with ActorLogging {

  import Processing._

  implicit val materializer = ActorMaterializer()

  val receiver: SourceQueueWithComplete[String] = Source
    .queue[String](QUEUE_SIZE, OverflowStrategy.backpressure)
    .async
    .map { message =>
      log.info("queue task is being run")
      TimeUnit.SECONDS.sleep(1) // heavy task
      self ! message.toUpperCase()
    }
    .to(Sink.ignore)
    .run()

  override def receive: Receive = {
    case SendMessage(message) =>
      log.info("receive message...")
      receiver.offer(message).map {
        case QueueOfferResult.Enqueued => println("enqueued")
        case QueueOfferResult.Dropped => println("dropped")
        case QueueOfferResult.Failure(error) => println(s"Offer failed ${error.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }

    case received: String =>
      log.info(s"incoming message: '$received'")

    case Shutdown =>
      log.info("shutdown")
      receiver.complete()
      receiver.watchCompletion().foreach(_ => system.terminate())
  }

}

object Processing {
  val system: ActorSystem = ActorSystem()

  val QUEUE_SIZE = 10

  case object Shutdown

  final case class SendMessage(message: String)

}
