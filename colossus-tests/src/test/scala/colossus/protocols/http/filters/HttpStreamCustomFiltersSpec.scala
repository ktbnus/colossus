package colossus.protocols.http.filters

import akka.util.{ByteString, ByteStringBuilder}
import colossus.core.DataBlock
import colossus.protocols.http.streaming.{Data, StreamingHttp, StreamingHttpRequest, StreamingHttpResponse}
import colossus.protocols.http.{HttpCodes, HttpHeaders, HttpMethod, HttpRequestHead, HttpResponseHead, HttpVersion, TransferEncoding}
import colossus.service.Callback
import colossus.service.GenRequestHandler.PartialHandler
import colossus.streaming.Source
import colossus.util.{GzipCompressor, ZCompressor}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{MustMatchers, WordSpec}

import scala.collection.mutable.ArrayBuffer

class HttpStreamCustomFiltersSpec extends WordSpec with MustMatchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  val helloWorldPartialHandler: PartialHandler[StreamingHttp] = {
    case StreamingHttpRequest(head, source) =>
      val responseBody = Source.fromIterator(List("this is ", "a chunked ", "response").toIterator.map { s =>
        Data(DataBlock(s))
      })
      Callback.successful(StreamingHttpResponse(
        HttpResponseHead(head.version, HttpCodes.OK, Some(TransferEncoding.Chunked), None, None, None, HttpHeaders.Empty),
        responseBody
      )
      )
  }

  "Gzip custom filter" should {
    "do not compress" in {
      val filter = new HttpStreamCustomFilters.CompressionFilter()
      val e: Callback[StreamingHttpResponse] = filter.apply(helloWorldPartialHandler)(StreamingHttpRequest(
        HttpRequestHead(HttpMethod.Get, "", HttpVersion.`1.1`, HttpHeaders.Empty),
        Source.empty
      ))
      val result: Callback[ArrayBuffer[Byte]] = e.flatMap(_.body.fold(new collection.mutable.ArrayBuffer[Byte]){ (data, acc) =>
        data.data.data.foreach(acc.append(_))
        acc
      })

      new String(result.toFuture.futureValue.toArray) mustBe "this is a chunked response"

    }

    "compress if accept encoding is deflate" in {
      val filter = new HttpStreamCustomFilters.CompressionFilter()
      val e: Callback[StreamingHttpResponse] = filter.apply(helloWorldPartialHandler)(StreamingHttpRequest(
        HttpRequestHead(HttpMethod.Get, "", HttpVersion.`1.1`,  HttpHeaders() + (HttpHeaders.AcceptEncoding, "deflate")),
        Source.empty
      ))

      val compressor = new ZCompressor()
      val builder = new ByteStringBuilder
      val byteString = e.flatMap(_.body.collected).toFuture.futureValue.foldLeft(builder){ (acc, data) =>
        builder.putBytes(data.data.data)
        builder
      }.result()

      compressor.decompress(byteString).utf8String mustBe "this is a chunked response"


    }


    "compress if accept encoding is gzip" in {

      val filter = new HttpStreamCustomFilters.CompressionFilter()

      val e: Callback[StreamingHttpResponse] = filter.apply(helloWorldPartialHandler)(StreamingHttpRequest(
        HttpRequestHead(HttpMethod.Get, "", HttpVersion.`1.1`,  HttpHeaders() + (HttpHeaders.AcceptEncoding, "gzip")),
        Source.empty
      ))

      val compressor = new GzipCompressor()

      val result: Callback[ArrayBuffer[Byte]] = e.flatMap(_.body.fold(new collection.mutable.ArrayBuffer[Byte]){ (data, acc) =>
        println(data)
        println(acc)
        acc ++= data.data.data
        acc
      })

      Thread.sleep(100)
      val bytes = ByteString(result.toFuture.futureValue.toArray)
      val str = compressor.decompress(bytes).utf8String mustBe "this is a chunked response"

    }
  }
}
