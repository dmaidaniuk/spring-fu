package org.springframework.fu.jafu.web;

import static org.springframework.beans.factory.support.BeanDefinitionReaderUtils.uniqueBeanName;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.FormCodecInitializer;
import org.springframework.boot.autoconfigure.web.reactive.MultipartCodecInitializer;
import org.springframework.boot.autoconfigure.web.reactive.ProtobufCodecInitializer;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerInitializer;
import org.springframework.boot.autoconfigure.web.reactive.ResourceCodecInitializer;
import org.springframework.boot.autoconfigure.web.reactive.StringCodecInitializer;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties;
import org.springframework.boot.web.embedded.jetty.JettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatReactiveWebServerFactory;
import org.springframework.boot.web.embedded.undertow.UndertowReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.fu.jafu.AbstractDsl;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctionDsl;
import org.springframework.web.reactive.function.server.RouterFunctions;

/**
 * Jafu DSL for server configuration.
 *
 * @author Sebastien Deleuze
 */
public class WebFluxServerDsl extends AbstractDsl {

	private final Consumer<WebFluxServerDsl> dsl;

	private ServerProperties serverProperties = new ServerProperties();

	private ResourceProperties resourceProperties = new ResourceProperties();

	private WebFluxProperties webFluxProperties = new WebFluxProperties();

	private boolean codecsConfigured = false;

	private int port = 8080;

	private ConfigurableReactiveWebServerFactory engine = null;

	private WebFluxServerDsl(Consumer<WebFluxServerDsl> dsl) {
		super();
		this.dsl = dsl;
	}

	public static ApplicationContextInitializer<GenericApplicationContext> server() {
		return new WebFluxServerDsl(webFluxServerDsl -> {});
	}

	public static ApplicationContextInitializer<GenericApplicationContext> server(Consumer<WebFluxServerDsl> dsl) {
		return new WebFluxServerDsl(dsl);
	}

	/**
	 * Define the listening port of the server.
	 */
	public WebFluxServerDsl port(int port) {
		this.port = port;
		return this;
	}

	/**
	 * Define the underlying engine used.
	 *
	 * @see #netty
	 * @see #tomcat
	 * @see #jetty
	 * @see #undertow
	 */
	public WebFluxServerDsl engine(ConfigurableReactiveWebServerFactory engine) {
		this.engine = engine;
		return this;
	}

	/**
	 * Configure routes via {@link RouterFunctions.Builder}.
	 */
	public WebFluxServerDsl router(Consumer<RouterFunctions.Builder> routerDsl) {
		RouterFunctions.Builder builder = RouterFunctions.route();
		context.registerBean(RouterFunction.class, () -> {
			routerDsl.accept(builder);
			return builder.build();
		});
		return this;
	}

	/**
	 * Import {@link RouterFunction} created via {@link RouterFunctions.Builder}.
	 */
	public WebFluxServerDsl importRouter(RouterFunction router) {
		context.registerBean(uniqueBeanName(RouterFunctionDsl.class.getName(), context), RouterFunction.class, () -> router);
		return this;
	}

	/**
	 * Configure codecs via a [dedicated DSL][WebFluxServerCodecDsl].
	 * @see WebFluxCodecDsl#resource
	 * @see WebFluxCodecDsl#string
	 * @see WebFluxCodecDsl#protobuf
	 * @see WebFluxCodecDsl#form
	 * @see WebFluxCodecDsl#multipart
	 * @see WebFluxServerCodecDsl#jackson
	 */
	public WebFluxServerDsl codecs(Consumer<WebFluxServerCodecDsl> init) {
		new WebFluxServerCodecDsl(init).initialize(context);
		this.codecsConfigured = true;
		return this;
	}

	/**
	 * @see #mustache(Consumer)
	 */
	public WebFluxServerDsl mustache() {
		return mustache(dsl -> {});
	}
	/**
	 * Configure Mustache view resolver.
	 *
	 * Require {@code org.springframework.boot:spring-boot-starter-mustache} dependency.
	 */
	public WebFluxServerDsl mustache(Consumer<MustacheDsl> dsl) {
		new MustacheDsl(dsl).initialize(context);
		return this;
	}

	/**
	 * Netty engine.
	 * @see #engine
	 */
	public ConfigurableReactiveWebServerFactory netty() {
		return new NettyDelegate().get();
	}

	/**
	 * Tomcat engine.
	 * @see #engine
	 */
	public ConfigurableReactiveWebServerFactory tomcat() {
		return new TomcatDelegate().get();
	}

	/**
	 * Jetty engine.
	 * @see #engine
	 */
	public ConfigurableReactiveWebServerFactory jetty() {
		return new JettyDelegate().get();
	}

	/**
	 * Undertow engine.
	 * @see #engine
	 */
	public ConfigurableReactiveWebServerFactory undertow() {
		return new UndertowDelegate().get();
	}


	@Override
	public void register() {
		this.dsl.accept(this);
		if (engine == null) {
			engine = netty();
		}
		engine.setPort(port);

		if (!codecsConfigured) {
			new StringCodecInitializer(false).initialize(context);
			new ResourceCodecInitializer(false).initialize(context);
		}
		if (context.containsBeanDefinition("webHandler")) {
			throw new IllegalStateException("Only one server per application is supported");
		}
		new ReactiveWebServerInitializer(serverProperties, resourceProperties, webFluxProperties, engine).initialize(context);
	}

	static public class WebFluxServerCodecDsl extends WebFluxCodecDsl {

		private final Consumer<WebFluxServerCodecDsl> dsl;

		public WebFluxServerCodecDsl(Consumer<WebFluxServerCodecDsl> dsl) {
			this.dsl = dsl;
		}

		@Override
		public void register() {
			this.dsl.accept(this);
		}

		@Override
		public WebFluxServerCodecDsl string() {
			new StringCodecInitializer(false).initialize(context);
			return this;
		}

		@Override
		public WebFluxServerCodecDsl resource() {
			new ResourceCodecInitializer(false).initialize(context);
			return this;
		}

		@Override
		public WebFluxServerCodecDsl protobuf() {
			new ProtobufCodecInitializer(false).initialize(context);
			return this;
		}

		@Override
		public WebFluxServerCodecDsl form() {
			new FormCodecInitializer(false).initialize(context);
			return this;
		}

		@Override
		public WebFluxServerCodecDsl multipart() {
			new MultipartCodecInitializer(false).initialize(context);
			return this;
		}

		@Override
		public WebFluxServerCodecDsl jackson() {
			return jackson(dsl -> {});
		}

		@Override
		public WebFluxServerCodecDsl jackson(Consumer<JacksonDsl> dsl) {
			new JacksonDsl(false, dsl).initialize(context);
			return this;
		}
	}

	private static class NettyDelegate implements Supplier<ConfigurableReactiveWebServerFactory> {
		@Override
		public ConfigurableReactiveWebServerFactory get() {
			return new NettyReactiveWebServerFactory();
		}
	}

	private static class TomcatDelegate implements Supplier<ConfigurableReactiveWebServerFactory> {
		@Override
		public ConfigurableReactiveWebServerFactory get() {
			return new TomcatReactiveWebServerFactory();
		}
	}

	private static class JettyDelegate implements Supplier<ConfigurableReactiveWebServerFactory> {
		@Override
		public ConfigurableReactiveWebServerFactory get() {
			return new JettyReactiveWebServerFactory();
		}
	}

	private static class UndertowDelegate implements Supplier<ConfigurableReactiveWebServerFactory> {
		@Override
		public ConfigurableReactiveWebServerFactory get() {
			return new UndertowReactiveWebServerFactory();
		}
	}
}
