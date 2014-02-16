package im.rore;

import java.util.concurrent.TimeUnit;

import org.elasticsearch.common.netty.channel.Channel;
import org.elasticsearch.common.netty.channel.ChannelFuture;
import org.elasticsearch.common.netty.channel.ChannelFutureListener;

/**
 * @author rotem
 *
 */
public class MockChannelFuture implements ChannelFuture {

	public MockChannelFuture() {}
	
	@Override
	public void addListener(ChannelFutureListener arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ChannelFuture await() throws InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean await(long arg0) throws InterruptedException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean await(long arg0, TimeUnit arg1) throws InterruptedException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ChannelFuture awaitUninterruptibly() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean awaitUninterruptibly(long arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean awaitUninterruptibly(long arg0, TimeUnit arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean cancel() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Throwable getCause() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Channel getChannel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCancelled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isDone() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSuccess() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void removeListener(ChannelFutureListener arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	@Deprecated
	public ChannelFuture rethrowIfFailed() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean setFailure(Throwable arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean setProgress(long arg0, long arg1, long arg2) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean setSuccess() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ChannelFuture sync() throws InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ChannelFuture syncUninterruptibly() {
		// TODO Auto-generated method stub
		return null;
	}

}
