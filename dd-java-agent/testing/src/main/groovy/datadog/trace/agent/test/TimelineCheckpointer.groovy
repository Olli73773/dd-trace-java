package datadog.trace.agent.test

import datadog.trace.api.Checkpointer
import datadog.trace.api.DDId

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class TimelineCheckpointer implements Checkpointer {

  private static String[] emptySpaces = new String[128]

  private final ConcurrentHashMap<DDId, List<Event>> spanEvents = new ConcurrentHashMap<>()
  private final ConcurrentHashMap<String, List<Event>> threadEvents = new ConcurrentHashMap<>()
  private final List<Event> encounterOrder = new CopyOnWriteArrayList<>()

  @Override
  void checkpoint(DDId traceId, DDId spanId, int flags) {
    Thread currentThread = Thread.currentThread()
    Event event = new Event(flagsToEvent(flags), traceId, spanId, currentThread.name)
    encounterOrder.add(event)
    spanEvents.putIfAbsent(spanId, new CopyOnWriteArrayList<Event>())
    threadEvents.putIfAbsent(currentThread.name, new CopyOnWriteArrayList<Event>())
    spanEvents.get(spanId).add(event)
    threadEvents.get(currentThread.name).add(event)
  }

  private String flagsToEvent(int flags) {
    switch (flags) {
      case SPAN:
        return "startSpan"
      case SPAN | END:
        return "endSpan"
      case THREAD_MIGRATION:
        return "suspend"
      case THREAD_MIGRATION | END:
        return "resume"
      case CPU | END:
        return "endTask"
      case CPU:
        return "startTask"
      default:
        return "unknown"
    }
  }

  @Override
  void onRootSpanPublished(String route, DDId traceId) {
  }

  void clear() {
    encounterOrder.clear()
    spanEvents.clear()
    threadEvents.clear()
  }

  void printActivity() {
    printTimeLine()
  }

  void printTimeLine() {
    if (!encounterOrder.isEmpty()) {
      System.err.println("Activity checkpoints by thread ordered by time")
      // allows rendering threads top to bottom by when they were first encountered
      Map<String, BitSet> timelines = new LinkedHashMap<>()
      int maxNameLength = 0
      String[] renderings = new String[encounterOrder.size()]
      for (String threadName : threadEvents.keySet()) {
        maxNameLength = Math.max(maxNameLength, threadName.length())
      }
      int position = 0
      for (Event event : encounterOrder) {
        renderings[position] = event.eventName + "/" + event.spanId
        BitSet timeline = timelines[event.threadName]
        if (null == timeline) {
          timelines[event.threadName] = timeline = new BitSet()
        }
        timeline.set(position++)
      }
      for (Map.Entry<String, BitSet> timeline : timelines) {
        String threadName = timeline.key
        System.err.print(threadName)
        System.err.print(":")
        System.err.print(repeat(" ", maxNameLength - threadName.length() + 1))
        System.err.print("|")
        BitSet positions = timeline.value
        int next = positions.nextSetBit(0)
        for (int i = 0; i < renderings.length; ++i) {
          System.err.print("-")
          if (i == next) {
            System.err.print(renderings[i])
            next = positions.nextSetBit(next + 1)
          } else {
            System.err.print(getEmptySpace(renderings[i].length()))
          }
          System.err.print("-|")
        }
        System.err.println()
      }
    }
  }

  private static String getEmptySpace(int width) {
    if (width >= emptySpaces.length) {
      return repeat("-", width)
    }
    String space = emptySpaces[width]
    if (null == space) {
      space = emptySpaces[width] = repeat("-", width)
    }
    return space
  }

  private static String repeat(String x, int length) {
    StringBuilder sb = new StringBuilder(x.length() * length)
    for (int i = 0; i < length; ++i) {
      sb.append(x)
    }
    return sb.toString()
  }

  class Event {
    private final String eventName
    private final String threadName
    private final DDId traceId
    private final DDId spanId

    Event(String eventName, DDId traceId, DDId spanId, String threadName) {
      this.eventName = eventName
      this.traceId = traceId
      this.spanId = spanId
      this.threadName = threadName
    }

    String getEventName() {
      return eventName
    }

    DDId getTraceId() {
      return traceId
    }

    DDId getSpanId() {
      return spanId
    }
  }
}