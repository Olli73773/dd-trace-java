import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoTimeoutException
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandSucceededEvent
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.core.DDSpan
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared

import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.api.config.TraceInstrumentationConfig.DB_CLIENT_HOST_SPLIT_BY_INSTANCE

class MongoJava34ClientTest extends MongoBaseTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = new MongoClient(new ServerAddress("localhost", port),
      MongoClientOptions.builder()
      .description("some-description")
      .addCommandListener(new CommandListener() {
        @Override
        void commandStarted(CommandStartedEvent event) {
        }
        @Override
        void commandSucceeded(CommandSucceededEvent event) {
        }
        @Override
        void commandFailed(CommandFailedEvent event) {
        }
      })
      .build())
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(databaseName)
    injectSysConfig(DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService")

    when:
    db.createCollection(collectionName)

    then:
    assertTraces(1) {
      trace(1) {
        mongoSpan(it, 0, "create", "{\"create\":\"$collectionName\",\"capped\":\"?\"}", renameService)
      }
    }

    where:
    collectionName = randomCollectionName()
    renameService << [false, true]
  }

  def "test create collection no description"() {
    setup:
    MongoDatabase db = new MongoClient("localhost", port).getDatabase(databaseName)

    when:
    db.createCollection(collectionName)

    then:
    assertTraces(1) {
      trace(1) {
        mongoSpan(it, 0, "create","{\"create\":\"$collectionName\",\"capped\":\"?\"}", false, databaseName)
      }
    }

    where:
    collectionName = randomCollectionName()
  }

  def "test get collection"() {
    setup:
    MongoDatabase db = client.getDatabase(databaseName)

    when:
    int count = db.getCollection(collectionName).count()

    then:
    count == 0
    assertTraces(1) {
      trace(1) {
        mongoSpan(it, 0, "count", "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    collectionName = randomCollectionName()
  }

  def "test insert"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(databaseName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    collection.insertOne(new Document("password", "SECRET"))

    then:
    collection.count() == 1
    assertTraces(2) {
      trace(1) {
        mongoSpan(it, 0, "insert", "{\"insert\":\"$collectionName\",\"ordered\":true,\"documents\":[]}")
      }
      trace(1) {
        mongoSpan(it, 0, "count", "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    collectionName = randomCollectionName()
  }

  def "test update"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(databaseName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "OLDPW"))
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))))

    then:
    result.modifiedCount == 1
    collection.count() == 1
    assertTraces(2) {
      trace(1) {
        mongoSpan(it, 0, "update", "{\"update\":\"$collectionName\",\"ordered\":true,\"updates\":[]}")
      }
      trace(1) {
        mongoSpan(it, 0, "count", "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    collectionName = randomCollectionName()
  }

  def "test delete"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(databaseName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "SECRET"))
      return coll
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    def result = collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")))

    then:
    result.deletedCount == 1
    collection.count() == 0
    assertTraces(2) {
      trace(1) {
        mongoSpan(it, 0, "delete", "{\"delete\":\"$collectionName\",\"ordered\":true,\"deletes\":[]}")
      }
      trace(1) {
        mongoSpan(it, 0, "count", "{\"count\":\"$collectionName\",\"query\":{}}")
      }
    }

    where:
    collectionName = randomCollectionName()
  }

  def "test error"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(databaseName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    collection.updateOne(new BsonDocument(), new BsonDocument())

    then:
    thrown(IllegalArgumentException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    collectionName = randomCollectionName()
  }

  def "test client failure"() {
    setup:
    def options = MongoClientOptions.builder().serverSelectionTimeout(10).build()
    def client = new MongoClient(new ServerAddress("localhost", UNUSABLE_PORT), [], options)

    when:
    MongoDatabase db = client.getDatabase(databaseName)
    db.createCollection(collectionName)

    then:
    thrown(MongoTimeoutException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    collectionName = randomCollectionName()
  }

  def mongoSpan(TraceAssert trace, int index, String operation, String statement, boolean renameService = false, String instance = "some-description", Object parentSpan = null, Throwable exception = null) {
    trace.span {
      serviceName renameService ? instance : "mongo"
      operationName "mongo.query"
      resourceName matchesStatement(statement)
      spanType DDSpanTypes.MONGO
      if (parentSpan == null) {
        parent()
      } else {
        childOf((DDSpan) parentSpan)
      }
      topLevel true
      tags {
        "$Tags.COMPONENT" "java-mongo"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
        "$Tags.PEER_HOSTNAME" "localhost"
        "$Tags.PEER_PORT" port
        "$Tags.DB_TYPE" "mongo"
        "$Tags.DB_INSTANCE" instance
        "$Tags.DB_OPERATION" operation
        defaultTags()
      }
    }
  }
}