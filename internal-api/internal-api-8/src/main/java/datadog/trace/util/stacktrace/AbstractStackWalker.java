package datadog.trace.util.stacktrace;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractStackWalker implements StackWalker {

  protected static final Predicate<String> NOT_DD_TRACE_CLASS =
      (className) ->
          !className.startsWith("datadog.trace.") && !className.startsWith("com.datadog.appsec.");

  private static final Predicate<StackTraceElement> NOT_DD_TRACE_STACK_ELEMENT =
      (stackElement) -> NOT_DD_TRACE_CLASS.test(stackElement.getClassName());

  @Override
  public final <T> T walk(final Function<Stream<StackTraceElement>, T> consumer) {
    return doGetStack(input -> consumer.apply(doFilterStack(input)));
  }

  final Stream<StackTraceElement> doFilterStack(Stream<StackTraceElement> stream) {
    return stream.filter(NOT_DD_TRACE_STACK_ELEMENT);
  }

  abstract <T> T doGetStack(Function<Stream<StackTraceElement>, T> consumer);
}
