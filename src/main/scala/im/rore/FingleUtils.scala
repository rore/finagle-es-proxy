package im.rore

import com.twitter.finagle.Filter
import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response

object FinagleUtils {
	val nettyToFinagle =
		Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
			service(Request(req)) map { _.httpResponse }
		}

	val finagleToNetty =
		Filter.mk[Request, Response, HttpRequest, HttpResponse] { (req, service) =>
			service(req) map { Response(_) }
		}

	implicit class NettyService(service: Service[Request, Response]) {
		def asNetty = nettyToFinagle andThen service
	}

	implicit class FinagleService(service: Service[HttpRequest, HttpResponse]) {
		def asFinagle = finagleToNetty andThen service
	}
}