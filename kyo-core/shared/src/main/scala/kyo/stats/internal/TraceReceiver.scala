package kyo.stats.internal

import kyo.*

import kyo.stats.*

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

import kyo.stats.Attributes

trait TraceReceiver:

    def startSpan(
        scope: List[String],
        name: String,
        parent: Option[Span] = None,
        attributes: Attributes = Attributes.empty
    ): Span < IOs
end TraceReceiver

object TraceReceiver:

    val get: TraceReceiver =
        ServiceLoader.load(classOf[TraceReceiver]).iterator().asScala.toList match
            case Nil =>
                TraceReceiver.noop
            case head :: Nil =>
                head
            case l =>
                TraceReceiver.all(l)

    val noop: TraceReceiver =
        new TraceReceiver:
            def startSpan(
                scope: List[String],
                name: String,
                parent: Option[Span] = None,
                attributes: Attributes = Attributes.empty
            ) =
                Span.noop

    def all(receivers: List[TraceReceiver]): TraceReceiver =
        new TraceReceiver:
            def startSpan(
                scope: List[String],
                name: String,
                parent: Option[Span] = None,
                a: Attributes = Attributes.empty
            ) =
                Seqs
                    .traverse(receivers)(_.startSpan(scope, name, None, a))
                    .map(Span.all)
end TraceReceiver
