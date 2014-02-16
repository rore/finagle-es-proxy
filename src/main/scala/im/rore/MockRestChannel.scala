package im.rore

import org.elasticsearch.common.netty.channel.ChannelFuture
import org.elasticsearch.common.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.http.Response
import com.twitter.util.Promise

/**
 * A mock rest channel for capturing the response from Elasticsearch REST layer
 * and returning it as a finagle future
 *  
 * @author rotem
 *
 */
class MockRestChannel extends MockChannel {

	val future = new Promise[Response]

	override def write(any: Object): ChannelFuture = {
		if (any.isInstanceOf[HttpResponse]) {
			val rep = any.asInstanceOf[HttpResponse];
			val response = ESClient.copyESResponse(rep);
			future.setValue(Response(response))
		}
		return new MockChannelFuture;
	}
}