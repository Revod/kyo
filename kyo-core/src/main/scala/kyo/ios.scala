package kyo

import java.util.concurrent.ThreadLocalRandom
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.NotGiven

import core._
import tries._
import options._
import frames._
import scala.runtime.AbstractFunction0
import scala.util.control.NonFatal
import scala.Conversion

object ios {

  trait Preempt extends Safepoint[IOs] {
    def apply[T, S](v: => T > (S | IOs)) = IOs(v)
  }
  object Preempt {
    val never: Preempt =
      new Preempt {
        def apply() = false
      }
  }

  opaque type IO[+T] = T

  abstract class KyoIO[T, S] extends Kyo[IO, IOs, Unit, T, (S | IOs)] {
    def value  = ()
    def effect = ios.IOs
  }

  final class IOs private[ios] () extends Effect[IO] {

    val False: false > IOs = apply(false)
    val True: true > IOs   = apply(true)
    val Unit: Unit > IOs   = apply(())

    inline def apply[T, S](
        inline f: => T > (S | IOs)
    )(using inline fr: Frame["IOs"]): T > (S | IOs) =
      new KyoIO[T, (S | IOs)] {
        def frame = fr
        def apply(v: Unit, s: Safepoint[IOs]) =
          f
      }

    inline def isDone[T](v: T > IOs): Boolean =
      !v.isInstanceOf[Kyo[_, _, _, _, _]]

    inline def run[T](v: T > IOs): T =
      val safepoint = Safepoint.noop[IOs]
      @tailrec def runLoop(v: T > IOs): T =
        v match {
          case kyo: Kyo[IO, IOs, Unit, T, IOs] @unchecked =>
            runLoop(kyo((), safepoint))
          case _ =>
            v.asInstanceOf[T]
        }
      runLoop(v)

    inline def lazyRun[T, S](v: T > (S | IOs)): T > S =
      given ShallowHandler[IO, IOs] =
        new ShallowHandler[IO, IOs] {
          def pure[T](v: T) = v
          def apply[T, U, S](m: IO[T], f: T => U > (S | IOs)): U > (S | IOs) =
            f(m)
        }
      v < IOs

    inline def eval[T](p: Preempt)(v: T > IOs): T > IOs =
      @tailrec def evalLoop(v: T > IOs, i: Int): T > IOs =
        v match {
          case kyo: Kyo[IO, IOs, Unit, T, IOs] @unchecked =>
            val n = kyo((), p)
            if (p()) {
              n
            } else {
              evalLoop(n, i + 1)
            }
          case _ =>
            v.asInstanceOf[T > IOs]
        }
      evalLoop(v, 0)
  }
  val IOs: IOs = new IOs
}
