package test

import datadog.trace.agent.test.AgentTestRunner

import java.security.MessageDigest

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class WeakHashTest extends AgentTestRunner{

  def "test instrumentation"() {
    setup:
    List<String> algorithms = Arrays.asList("MD2","MD5","SHA","SHA1","md2","md5","sha","sha1")

    when:

    runUnderTrace("WeakHashingRootSpan") {
      for (String algorithm:algorithms) {
        MessageDigest.getInstance(algorithm)
      }
    }

    then:
    assertTraces(1, true) {
      trace(algorithms.size() + 1){
        span{resourceName "WeakHashingRootSpan"}
        span{resourceName "WeakHashingAlgorithm_sha1"}
        span{resourceName "WeakHashingAlgorithm_sha"}
        span{resourceName "WeakHashingAlgorithm_md5"}
        span{resourceName "WeakHashingAlgorithm_md2"}
        span{resourceName "WeakHashingAlgorithm_SHA1"}
        span{resourceName "WeakHashingAlgorithm_SHA"}
        span{resourceName "WeakHashingAlgorithm_MD5"}
        span{resourceName "WeakHashingAlgorithm_MD2"}
      }
    }
  }
}
