package rings

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable

// LockServer send the following command to LockClient
sealed trait LockClientAPI
case class Recall(lock: LockAPI) extends LockClientAPI

// Lock Class
class Lock(val symbolicName: String)

// Internal cache for storing valid locks
/**
  * LockClient implements a client's interface to the LockServer, with a lock cache.
  * Instantiate one LockClient for each actor that is a client of the LockServer.
  */
class LockClient(lockServer: ActorRef, t: Int, id: BigInt) extends Actor {
  private val lockCache = new mutable.HashMap[String, Lock]()
  implicit val timeout = Timeout(t seconds)

  def receive() = {
    case Recall(lock) =>
      recallLock(lock)
  }

  /**
    * Acquire acts as an interface for both acquiring and renewing a lock.
    * @param symbolicName name of lock
    * @return Lock object representing the lock
    */
  def acquire(symbolicName: String): Lock = {
    if(lockCache.contains(symbolicName)) {
      lockServer ! Renew(lock, id)
      lockCache.get(symbolicName).get
    } else {
      val lock = new Lock(symbolicName)
      val future = ask(lockServer, Acquire(lock, myNodeID)).mapTo[LockResponseAPI]
      Await.result(future, timeout.duration)
      lockCache.put(symbolicName, lock)
      lock
    }
  }

  /**
    * Removes the Lock from the cache if it exists there; sends notification to the lock server that
    * it is relinquishing the lock
    * @param lock Lock object to be released
    */
  def release(lock: Lock) = {
    // Notify the lock server that it is releasing said lock
    if(lockCache.contains(lock.symbolicName)) {
      lockCache.remove(lock.symbolicName)
    }
    lockServer ! Release(lock, id)
  }

  private def recallLock(lock) = {
    release(lock, id)
  }
}
