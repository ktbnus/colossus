package colossus.metrics

import scala.concurrent.duration._

class CounterSpec extends MetricIntegrationSpec {

  def counter = new Counter("/foo")(new Collection(CollectorConfig(List(1.second))))

  "Counter" must {
    "increment" in {
      val c = counter
      c.get() must equal(0)
      c.increment()
      c.get() must equal(1)

    }

    "decrement" in {
      val c = counter
      c.increment()
      c.get() must equal(1)
      c.decrement()
      c.get() must equal(0)
    }

    "set" in {
      val c = counter
      c.set(num = 3456)
      c.get() must equal(3456)
    }

    "correctly handle tags" in {
      val c = counter
      c.increment(Map("a" -> "a"))
      c.increment(Map("a" -> "b"))
      c.increment(Map("a" -> "b"))
      c.get(Map("a" -> "a")) must equal(1)
      c.get(Map("a" -> "b")) must equal(2)
    }

    
  }

}
