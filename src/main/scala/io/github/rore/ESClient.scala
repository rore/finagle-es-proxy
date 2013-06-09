package io.github.rore

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet

import org.elasticsearch.client.Client
import org.elasticsearch.common.network.NetworkService
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.http.netty.NettyHttpChannel
import org.elasticsearch.http.netty.NettyHttpRequest
import org.elasticsearch.http.netty.NettyHttpServerTransport
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.node.internal.InternalNode
import org.elasticsearch.rest.RestController
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.slf4j.LoggerFactory

import com.twitter.conversions.storage.intToStorageUnitableWholeNumber
import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.RichHttp
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future

/**
 *
 * @author rotem
 *
 */
abstract class ESClient protected (cluster: String, hosts: String) {
	/**
	 * Send a request to ES
	 *
	 * @param request
	 * @return A future for the response from ES
	 */
	def send(request: Request): Future[Response];
	/**
	 * Close the client
	 */
	def close
}

/**
 * A proxy to ES that uses the native Java client
 * (runs a local ES client node)
 *
 * @author rotem
 *
 */
class NativeESClient(cluster: String, hosts: String) extends ESClient(cluster, hosts) {
	val logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * ES client
	 */
	protected var _client: Client = null;
	/**
	 * ES REST interface
	 */
	protected var _restController: RestController = null;
	/**
	 * ES Netty transport layer
	 */
	protected var _transport: NettyHttpServerTransport = null;

	def client = _client

	// initialize the client
	build();

	protected def controller = {
		client
		_restController;
	}

	/* (non-Javadoc)
	 * @see io.github.rore.ESClient#send(com.twitter.finagle.http.Request)
	 */
	def send(request: Request) = {
		// copy the request to the ES namespace classes
		val esReq = ESClient.copyESRequest(request.httpRequest);
		// create a mock REST channel to capture the response from ES and return it
		val mockChannel = new MockRestChannel();
		// dispatch the request to the REST controller, using our own channel to receive the response
		controller.dispatchRequest(new NettyHttpRequest(esReq), new NettyHttpChannel(_transport, mockChannel, esReq))
		mockChannel.future.asInstanceOf[Future[Response]];
	}

	protected def build() {
		this.synchronized {
			// General settings
			val props = System.getProperties();
			props.setProperty("java.net.preferIPv4Stack", "true");
			props.setProperty("transport.netty.reuse_address", "false");
			System.setProperties(props);

			val settingsBuilder = ImmutableSettings.settingsBuilder()
				.put("cluster.name", cluster);
			var initialized = false;

			settingsBuilder.put("transport.port", "9300");
			settingsBuilder.put("http.port", "9200");
			settingsBuilder.put("discovery.zen.ping.timeout", "5s");
			// if we have a host set it to initialize the discovery
			if (hosts != null)
				settingsBuilder.put("discovery.zen.ping.unicast.hosts", hosts);
			val node = nodeBuilder().settings(settingsBuilder).data(false).client(true).build;
			// start the node
			node.start();
			_client = node.client();
			val internalNode = node.asInstanceOf[InternalNode];
			// get the REST controller
			_restController = internalNode.injector().getInstance(classOf[RestController]);
			// get the transport layer
			val networkService = internalNode.injector().getInstance(classOf[NetworkService])
			_transport = new NettyHttpServerTransport(internalNode.settings(), networkService);
		}
	}

	def close {
		try {
			if (null != _client)
				_client.close()
		}
		catch {
			case e: Throwable => {
				logger.error("close", e)
			}
		}
	}
}

/**
 * 
 * An ES proxy using HTTP
 * 
 * @author rotem
 *
 */
class HttpESClient(cluster: String, hosts: String) extends ESClient(cluster, hosts) {
	val logger = LoggerFactory.getLogger(this.getClass());
	protected var _client: Service[Request, Response] = null

	// initialize the client
	build();

	/* (non-Javadoc)
	 * @see io.github.rore.ESClient#send(com.twitter.finagle.http.Request)
	 */
	def send(request: Request) = {
		_client(request);
	}

	protected def buildClient(hosts: List[String], name: String = null, statsReceiver: StatsReceiver = null) = {
		val hostStr = hosts.map(_.replaceFirst("http://", "").replaceFirst("https://", "")).mkString(",")
		var builder = ClientBuilder()
			.codec(RichHttp[Request](Http().maxResponseSize(10.megabytes)))
			.hosts(hostStr)
			.hostConnectionLimit(20) // max number of connections at a time to a host
			.tcpConnectTimeout(1.second) // max time to spend establishing a TCP connection
			.requestTimeout(10.second)
			.retries(2) // (1) per-request retries
		if (null != name)
			builder = builder.name(name)
		if (null != statsReceiver)
			builder = builder.reportTo(statsReceiver)
		builder.build()
	}

	protected def build() {
		this.synchronized {
			_client = buildClient(hosts.split(",").toList, "ESClient", ESProxy.statsReceiver)
		}
	}

	def close {
		try {
			_client.close()
		}
		catch {
			case e: Throwable => {
				logger.error("close", e)
			}
		}
	}
}

object ESClient {

	/**
	 * Copies a request from Netty objects to inner-ES Netty objects
	 * 
	 * @param request a Netty request
	 * @return
	 */
	def copyESRequest(request: HttpRequest): org.elasticsearch.common.netty.handler.codec.http.HttpRequest =
		{
			var uri = request.getUri();
			val copy = new org.elasticsearch.common.netty.handler.codec.http.DefaultHttpRequest(
				new org.elasticsearch.common.netty.handler.codec.http.HttpVersion(request.getProtocolVersion().getText()),
				new org.elasticsearch.common.netty.handler.codec.http.HttpMethod(request.getMethod().getName()),
				uri);

			copy.setChunked(request.isChunked());
			copy.setContent(org.elasticsearch.common.netty.buffer.ChannelBuffers.copiedBuffer(request.getContent().toByteBuffer()));

			val headerNames = request.getHeaderNames();
			headerNames.foreach(headerName =>
				{
					val headerValues = request.getHeaders(headerName);
					headerValues.foreach(headerValue =>
						{
							copy.addHeader(headerName, headerValue);
						})
				})
			return copy;
		}

	/**
	 * Copies a response from inner-ES Netty object to a regular Netty response
	 * 
	 * @param response
	 * @return
	 */
	def copyESResponse(response: org.elasticsearch.common.netty.handler.codec.http.HttpResponse): HttpResponse =
		{
			val copy = new DefaultHttpResponse(
				HttpVersion.valueOf(response.getProtocolVersion().getText()),
				HttpResponseStatus.valueOf(response.getStatus().getCode()))

			copy.setChunked(response.isChunked());
			copy.setContent(ChannelBuffers.copiedBuffer(response.getContent().toByteBuffer()));

			val headerNames = response.getHeaderNames();
			headerNames.foreach(headerName =>
				{
					val headerValues = response.getHeaders(headerName);
					headerValues.foreach(headerValue =>
						{
							copy.addHeader(headerName, headerValue);
						})
				})
			return copy;
		}

	def build(cluster: String, hosts: String, native: Boolean): ESClient = {
		if (native) {
			return new NativeESClient(cluster, hosts)
		}
		else {
			return new HttpESClient(cluster, hosts)
		}
	}

}