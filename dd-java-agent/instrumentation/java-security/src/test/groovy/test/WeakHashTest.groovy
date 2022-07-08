package test

import datadog.trace.agent.test.AgentTestRunner

import java.security.MessageDigest

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class WeakHashTest extends AgentTestRunner{

  def "test instrumentation"() {
    setup:


    when:

    runUnderTrace("WeakHashingRootSpan") {
      MessageDigest.getInstance("MD2")
    }

    then:
    assertTraces(1, true) {
      trace(2){
        span{resourceName "WeakHashingRootSpan"}
        span{resourceName "WeakHashingAlgorithm"}
      }
    }
  }
}
