/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.dispatch

import language.postfixOps
import com.typesafe.config.Config
import akka.actor.{ Actor, ActorSystem, Props }
import akka.testkit.{ AkkaSpec, DefaultTimeout }
import akka.util.unused

import scala.concurrent.duration._

object PriorityDispatcherSpec {

  case object Result

  val config = """
    unbounded-prio-dispatcher {
      mailbox-type = "akka.dispatch.PriorityDispatcherSpec$Unbounded"
    }
    bounded-prio-dispatcher {
      mailbox-type = "akka.dispatch.PriorityDispatcherSpec$Bounded"
    }
    """

  class Unbounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator({
        case i: Int => i //Reverse order
        case Result => Int.MaxValue
      }: Any => Int))

  class Bounded(@unused settings: ActorSystem.Settings, @unused config: Config)
      extends BoundedPriorityMailbox(PriorityGenerator({
        case i: Int => i //Reverse order
        case Result => Int.MaxValue
      }: Any => Int), 1000, 10 seconds)

}

class PriorityDispatcherSpec extends AkkaSpec(PriorityDispatcherSpec.config) with DefaultTimeout {
  import PriorityDispatcherSpec._

  "A PriorityDispatcher" must {
    "Order it's messages according to the specified comparator using an unbounded mailbox" in {
      val dispatcherKey = "unbounded-prio-dispatcher"
      testOrdering(dispatcherKey)
    }

    "Order it's messages according to the specified comparator using a bounded mailbox" in {
      val dispatcherKey = "bounded-prio-dispatcher"
      testOrdering(dispatcherKey)
    }
  }

  def testOrdering(dispatcherKey: String): Unit = {
    val msgs = (1 to 100) toList

    // It's important that the actor under test is not a top level actor
    // with RepointableActorRef, since messages might be queued in
    // UnstartedCell and the sent to the PriorityQueue and consumed immediately
    // without the ordering taking place.
    system.actorOf(Props(new Actor {
      context.actorOf(Props(new Actor {

        val acc = scala.collection.mutable.ListBuffer[Int]()

        scala.util.Random.shuffle(msgs).foreach { m =>
          self ! m
        }

        self.tell(Result, testActor)

        def receive = {
          case i: Int => acc += i
          case Result => sender() ! acc.toList
        }
      }).withDispatcher(dispatcherKey))

      def receive = Actor.emptyBehavior

    }))

    expectMsgType[List[Int]] should ===(msgs)
  }

}
