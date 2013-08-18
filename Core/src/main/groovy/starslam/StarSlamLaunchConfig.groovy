package starslam

import static org.ratpackframework.guice.Guice.handler
import static org.ratpackframework.handling.Handlers.chain
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import org.ratpackframework.launch.HandlerFactory
import org.ratpackframework.launch.LaunchConfig

class StarSlamLaunchConfig implements LaunchConfig {
	@Override
	public ExecutorService getBlockingExecutorService() {
		return Executors.newCachedThreadPool()
	}
	@Override
	public ByteBufAllocator getBufferAllocator() {
		return UnpooledByteBufAllocator.DEFAULT
	}
	@Override
	public int getMainThreads() {
		return 10
	}

	final String DBURL
	
	public StarSlamLaunchConfig(String dbUrl) {
		DBURL = dbUrl
	}
	@Override
	public InetAddress getAddress() {
		return null;
	}

	@Override
	public File getBaseDir() {
		return null;
	}

	@Override
	public HandlerFactory getHandlerFactory() {
		def factory = { launchConfig ->
			def modulesConfigurer = new ModuleBootstrap(DBURL)
			def guiceHandler = handler(launchConfig, modulesConfigurer, new Handler())
		} as HandlerFactory
		return factory;
	}

	@Override
	public String getOther(String arg0, String arg1) {
		return null;
	}

	@Override
	public int getPort() {
		return 5050;
	}

	@Override
	public boolean isReloadable() {
		return false;
	}
	
	@Override
	public URI getPublicAddress() {
		return null;
	}

}
