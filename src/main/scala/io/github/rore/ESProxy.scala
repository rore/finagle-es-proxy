package io.github.rore

import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN
import org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.util.CharsetUtil.UTF_8

import com.twitter.finagle.Service
import com.twitter.finagle.SimpleFilter
import com.twitter.finagle.SourcedException
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.twitter.util.Future

/**
 * A proxy server to Elasticsearch
 */
object ESProxy extends TwitterServer {

	/**
	 * A comma separated list of ES hosts
	 */
	val es_hosts = flag("hosts", "http://localhost:9200", "Elasticsearch hosts (comma separated)")
	/**
	 * The ES cluster name (if connecting with a native client)
	 */
	val es_cluster = flag("cluster", "elasticsearch", "Elasticsearch cluster name")
	/**
	 * Should we use the native client?
	 */
	val use_native = flag("native", false, "Use native ES client")
	/**
	 * Should we use authorize the requests?
	 */
	val authorize = flag("authorize", false, "Use request authorization")

	// build the client
	protected val _client = ESClient.build(es_cluster(), es_hosts(), use_native())

	// our simple service - just proxy the request to ES
	val service = new Service[Request, Response] {
		def apply(request: Request) = {
			_client.send(request)
		}
	}

	/**
	 * An example of an authorization filter to secure ES access
	 *
	 */
	val authorization = new SimpleFilter[Request, Response] {
		def apply(request: Request, continue: Service[Request, Response]) = {
			if ("abracadabra" == request.getParam("token")) {
				continue(request)
			}
			else {
				Future.exception(new IllegalArgumentException("Authorization enabled, wrong token"))
			}
		}
	}

	/**
	 * An exception handler to return exception as error responses
	 */
	val handleExceptions = new SimpleFilter[Request, Response] {
		def apply(request: Request, service: Service[Request, Response]) = {

			service(request) handle {
				case error =>
					def flatten(ex: Throwable): Seq[String] =
							if (ex eq null) Seq[String]() else ex.getClass.getName +: flatten(ex.getCause)
					statsReceiver.scope("srv").scope("http").scope("failures").counter(flatten(error): _*).incr()
					error match {
						case sourced: SourcedException if sourced.serviceName != "unspecified" =>
							statsReceiver
								.scope("sourcedfailures")
								.counter(sourced.serviceName +: flatten(sourced): _*)
								.incr()
						case _ =>
					}
					var customError: String = null;
					val statusCode = error match {
						case _: IllegalArgumentException => FORBIDDEN
						case _: MatchError => NOT_FOUND
						case _ => INTERNAL_SERVER_ERROR
					}
					log.error("path: " + request.path, error);
					val errMsg = error.getMessage() match {
						case null => "Internal server error"
						case s => s
					}
					val errorResponse = Response(HTTP_1_1, statusCode)
					errorResponse.setContentType("text/html", "UTF-8")
					errorResponse.setContent(copiedBuffer(errMsg, UTF_8))
					errorResponse
			}
		}
	}
	
	def main() {
		val theService = authorize() match {
			case true => handleExceptions andThen authorization andThen service
			case false => handleExceptions andThen service
		}
		// add our proxy service
		HttpMuxer.addRichHandler("", theService)
		Await.ready(httpServer)
	}
}