package datadog.trace.core.propagation

import datadog.trace.api.Config
import datadog.trace.core.test.DDCoreSpecification

import static datadog.trace.api.sampling.PrioritySampling.*
import static datadog.trace.api.sampling.SamplingMechanism.*

class DatadogTagsTest extends DDCoreSpecification {

  def createDatadogTagsFromHeaderValue() {
    setup:
    def config = Mock(Config)
    config.isServicePropagationEnabled() >> true
    config.getDataDogTagsLimit() >> 512
    def datadogTagsFactory = DatadogTags.factory(config)

    when:
    def datadogTags = datadogTagsFactory.fromHeaderValue(headerValue)

    then:
    datadogTags.headerValue() == expectedHeaderValue
    datadogTags.createTagMap() == tags

    where:
    headerValue                                                                                                                  | expectedHeaderValue                        | tags
    null                                                                                                                         | null                                       | [:]
    ""                                                                                                                           | null                                       | [:]
    "_dd.p.dm=934086a686-4"                                                                                                      | "_dd.p.dm=934086a686-4"                    | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.anytag=value"                                                                                                         | "_dd.p.anytag=value"                       | ["_dd.p.anytag": "value"]
    // drop _dd.p.upstream_services and any other but _dd.p.*
    "_dd.b.somekey=value"                                                                                                        | null                                       | [:]
    "_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1"                                                                            | null                                       | [:]
    "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                                                                                   | "_dd.p.dm=934086a686-4,_dd.p.anytag=value" | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.p.dm=934086a686-4,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"                                   | "_dd.p.dm=934086a686-4,_dd.p.anytag=value" | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.b.keyonly=value,_dd.p.dm=934086a686-4,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"               | "_dd.p.dm=934086a686-4,_dd.p.anytag=value" | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    // valid tag value containing spaces
    "_dd.p.ab=1 2 3"                                                                                                             | "_dd.p.ab=1 2 3"                           | ["_dd.p.ab": "1 2 3"]
    "_dd.p.ab= 123 "                                                                                                             | "_dd.p.ab= 123 "                           | ["_dd.p.ab": " 123 "]
    // decoding error
    "_dd.p.keyonly"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"]
    ",_dd.p.dm=Value"                                                                                                            | null                                       | ["_dd.propagation_error": "decoding_error"]
    ","                                                                                                                          | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid comma only tagSet
    "_dd.b.somekey=value,_dd.p.dm=934086a686-4,_dd.p.keyonly,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value" | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid _dd.p.keyonly tag without a value
    "_dd.p.keyonly,_dd.p.dm=934086a686-4,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"                     | null                                       | ["_dd.propagation_error": "decoding_error"] //
    ",_dd.p.dm=934086a686-4,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"                                  | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tagSet with a leading comma
    "_dd.p.dm=934086a686-4,,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"                                  | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tagSet with two commas in a row
    "_dd.p.dm=934086a686-4, ,_dd.p.upstream_services=bWNudWx0eS13ZWI|0|1|0.1,_dd.p.anytag=value"                                 | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tagSet with a space instead of a tag
    " _dd.p.ab=123"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag set containing leading space
    "_dd.p.a b=123"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag key containing space
    "_dd.p.ab =123"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag key containing space
    "_dd.p. ab=123"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag key containing space
    "_dd.p.a=b=1=2"                                                                                                              | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag key containing equality
    "_dd.p.1ö2=value"                                                                                                            | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag key containing not allowed char
    "_dd.p.ab=1=2"                                                                                                               | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag value containing equality
    "_dd.p.ab=1ô2"                                                                                                               | null                                       | ["_dd.propagation_error": "decoding_error"] // invalid tag value containing not allowed char
  }

  def updateTraceAndSpanSamplingPriorityOnlyWhenDatadogTagsEnabled() {
    setup:
    def config = Mock(Config)
    config.isServicePropagationEnabled() >> true
    config.getDataDogTagsLimit() >> 512
    def datadogTagsFactory = DatadogTags.factory(config)
    def datadogTags = datadogTagsFactory.fromHeaderValue(headerValue)

    when:
    datadogTags.updateTraceSamplingPriority(priority, mechanism, "service-1")
    datadogTags.updateSpanSamplingPriority(priority, "service-1")

    then:
    datadogTags.headerValue() == expectedHeaderValue
    datadogTags.createTagMap() == tags

    where:
    headerValue                                                 | priority     | mechanism  | expectedHeaderValue                                         | tags
    // keep the existing dm tag as is
    "_dd.p.dm=934086a686-4"                                     | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=934086a686-4"                                     | UNSET        | UNKNOWN    | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=93485302ab-2"                                     | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4", "_dd.p.atag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.atag": "value"]
    "_dd.p.dm=93485302ab-2"                                     | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | USER_DROP    | MANUAL     | "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | ["_dd.p.atag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    // add the dm tags
    ""                                                          | SAMPLER_KEEP | DEFAULT    | "_dd.p.dm=266ff5f617-0"                                     | ["_dd.dm.service_hash": "266ff5f617", "_dd.p.dm": "266ff5f617-0"]
    "_dd.p.anytag=value"                                        | USER_KEEP    | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=266ff5f617-4"                  | ["_dd.dm.service_hash": "266ff5f617", "_dd.p.anytag": "value", "_dd.p.dm": "266ff5f617-4"]
    // drop the dm tag
    "_dd.p.anytag=value,_dd.p.atag=value"                       | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.atag=value"                       | ["_dd.p.anytag": "value", "_dd.p.atag": "value"]
    ",_dd.p.dm=Value"                                           | SAMPLER_KEEP | AGENT_RATE | null                                                        | ["_dd.propagation_error": "decoding_error"]
  }

  def updateSpanSamplingPriorityOnlyWhenDatadogTagsEnabled() {
    setup:
    def config = Mock(Config)
    config.isServicePropagationEnabled() >> true
    config.getDataDogTagsLimit() >> 512
    def datadogTagsFactory = DatadogTags.factory(config)
    def datadogTags = datadogTagsFactory.fromHeaderValue(headerValue)

    when:
    datadogTags.updateSpanSamplingPriority(priority, "service-1")

    then:
    datadogTags.headerValue() == expectedHeaderValue
    datadogTags.createTagMap() == tags

    where:
    headerValue                                                 | priority     | mechanism  | expectedHeaderValue                                         | tags
    // keep the existing dm tag as is
    "_dd.p.dm=934086a686-4"                                     | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=934086a686-4"                                     | UNSET        | UNKNOWN    | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=93485302ab-2"                                     | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4", "_dd.p.atag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.atag": "value"]
    "_dd.p.dm=93485302ab-2"                                     | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | USER_DROP    | MANUAL     | "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | ["_dd.p.atag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    // add the dm.service_hash only
    ""                                                          | SAMPLER_KEEP | DEFAULT    | null                                                        | ["_dd.dm.service_hash": "266ff5f617"]
    "_dd.p.anytag=value"                                        | USER_KEEP    | MANUAL     | "_dd.p.anytag=value"                                        | ["_dd.dm.service_hash": "266ff5f617", "_dd.p.anytag": "value"]
    // drop the dm tag
    "_dd.p.anytag=value,_dd.p.atag=value"                       | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.atag=value"                       | ["_dd.p.anytag": "value", "_dd.p.atag": "value"]
    ",_dd.p.dm=Value"                                           | SAMPLER_KEEP | AGENT_RATE | null                                                        | ["_dd.propagation_error": "decoding_error"]
  }

  def updateTraceSamplingPriorityOnlyWhenDatadogTagsEnabled() {
    setup:
    def config = Mock(Config)
    config.isServicePropagationEnabled() >> true
    config.getDataDogTagsLimit() >> 512
    def datadogTagsFactory = DatadogTags.factory(config)
    def datadogTags = datadogTagsFactory.fromHeaderValue(headerValue)

    when:
    datadogTags.updateTraceSamplingPriority(priority, mechanism, "service-1")

    then:
    datadogTags.headerValue() == expectedHeaderValue
    datadogTags.createTagMap() == tags

    where:
    headerValue                                                 | priority     | mechanism  | expectedHeaderValue                                         | tags
    // keep the existing dm tag as is
    "_dd.p.dm=934086a686-4"                                     | UNSET        | UNKNOWN    | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=934086a686-4"                                     | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=93485302ab-2"                                     | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4", "_dd.p.atag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.atag": "value"]
    "_dd.p.dm=93485302ab-2"                                     | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | USER_DROP    | MANUAL     | "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | ["_dd.p.atag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    // add the dm tag
    ""                                                          | SAMPLER_KEEP | DEFAULT    | "_dd.p.dm=266ff5f617-0"                                     | ["_dd.p.dm": "266ff5f617-0"]
    "_dd.p.anytag=value"                                        | USER_KEEP    | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=266ff5f617-4"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "266ff5f617-4"]
    // drop the dm tag
    "_dd.p.anytag=value,_dd.p.atag=value"                       | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.atag=value"                       | ["_dd.p.anytag": "value", "_dd.p.atag": "value"]
    // invalid input
    ",_dd.p.dm=Value"                                           | SAMPLER_KEEP | AGENT_RATE | null                                                        | ["_dd.propagation_error": "decoding_error"]
  }


  def updateDatadogTagsDisabled() {
    setup:
    def config = Mock(Config)
    config.isServicePropagationEnabled() >> false
    config.getDataDogTagsLimit() >> 512
    def datadogTagsFactory = DatadogTags.factory(config)
    def datadogTags = datadogTagsFactory.fromHeaderValue(originalTagSet)

    when:
    datadogTags.updateTraceSamplingPriority(priority, mechanism, "service-1")
    // this won't set "_dd.dm.service_hash"
    datadogTags.updateSpanSamplingPriority(priority, "service-1")

    then:
    datadogTags.headerValue() == expectedHeaderValue
    datadogTags.createTagMap() == tags

    where:
    originalTagSet                                              | priority     | mechanism  | expectedHeaderValue                                         | tags
    // keep the existing dm tag as is
    "_dd.p.dm=934086a686-4"                                     | UNSET        | UNKNOWN    | "_dd.p.dm=934086a686-4"                                     | ["_dd.p.dm": "934086a686-4"]
    "_dd.p.dm=934086a686-3"                                     | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-3"                                     | ["_dd.p.dm": "934086a686-3"]
    "_dd.p.dm=93485302ab-1"                                     | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-1"                                     | ["_dd.p.dm": "93485302ab-1"]
    "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=934086a686-4,_dd.p.anytag=value"                  | ["_dd.p.dm": "934086a686-4", "_dd.p.anytag": "value"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | SAMPLER_KEEP | AGENT_RATE | "_dd.p.anytag=value,_dd.p.dm=934086a686-4,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "934086a686-4", "_dd.p.atag": "value"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | SAMPLER_KEEP | APPSEC     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2,_dd.p.atag=value" | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.atag": "value"]
    "_dd.p.dm=93485302ab-2"                                     | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2"                                     | ["_dd.p.dm": "93485302ab-2"]
    "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=93485302ab-2"                  | ["_dd.p.anytag": "value", "_dd.p.dm": "93485302ab-2"]
    "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | USER_DROP    | MANUAL     | "_dd.p.dm=93485302ab-2,_dd.p.anytag=value"                  | ["_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | USER_DROP    | MANUAL     | "_dd.p.atag=value,_dd.p.dm=93485302ab-2,_dd.p.anytag=value" | ["_dd.p.atag": "value", "_dd.p.dm": "93485302ab-2", "_dd.p.anytag": "value"]
    // propagate sampling mechanism only
    ""                                                          | SAMPLER_KEEP | DEFAULT    | "_dd.p.dm=-0"                                               | ["_dd.p.dm": "-0"]
    ""                                                          | SAMPLER_KEEP | AGENT_RATE | "_dd.p.dm=-1"                                               | ["_dd.p.dm": "-1"]
    "_dd.p.anytag=value"                                        | USER_KEEP    | MANUAL     | "_dd.p.anytag=value,_dd.p.dm=-4"                            | ["_dd.p.anytag": "value", "_dd.p.dm": "-4"]
    "_dd.p.anytag=value,_dd.p.atag=value"                       | SAMPLER_DROP | MANUAL     | "_dd.p.anytag=value,_dd.p.atag=value"                       | ["_dd.p.anytag": "value", "_dd.p.atag": "value"]
    // invalid input
    ",_dd.p.dm=Value"                                           | SAMPLER_KEEP | AGENT_RATE | null                                                        | ["_dd.propagation_error": "decoding_error"]
  }

  def extractionLimitExceeded() {
    setup:
    def tags = "_dd.p.anytag=value"
    def limit = tags.length() - 1
    def datadogTags = DatadogTags.factory(true, limit).fromHeaderValue(tags)

    when:
    datadogTags.updateTraceSamplingPriority(USER_KEEP, MANUAL, "service-name")

    then:
    datadogTags.headerValue() == null
    datadogTags.createTagMap() == ["_dd.propagation_error": "extract_max_size"]
  }

  def injectionLimitExceeded() {
    setup:
    def tags = "_dd.p.anytag=value"
    def limit = tags.length()
    def datadogTags = DatadogTags.factory(true, limit).fromHeaderValue(tags)

    when:
    datadogTags.updateTraceSamplingPriority(USER_KEEP, MANUAL, "service-name")

    then:
    datadogTags.headerValue() == null
    datadogTags.createTagMap() == ["_dd.propagation_error": "inject_max_size"]
  }

  def injectionLimitExceededLimit0() {
    setup:
    def datadogTags = DatadogTags.factory(true, 0).fromHeaderValue("")

    when:
    datadogTags.updateTraceSamplingPriority(USER_KEEP, MANUAL, "service-name")

    then:
    datadogTags.headerValue() == null
    datadogTags.createTagMap() == ["_dd.propagation_error": "disabled"]
  }
}