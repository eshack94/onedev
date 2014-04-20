package com.pmease.gitop.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.bean.validation.BeanValidationConfiguration;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.devutils.stateless.StatelessChecker;
import org.apache.wicket.markup.html.IPackageResourceGuard;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.IRequestMapper;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.resource.caching.FilenameWithVersionResourceCachingStrategy;
import org.apache.wicket.request.resource.caching.version.LastModifiedResourceVersion;
import org.apache.wicket.util.time.Duration;
import org.apache.wicket.util.time.Time;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.pmease.commons.wicket.AbstractWicketConfig;
import com.pmease.gitop.web.assets.AssetLocator;
import com.pmease.gitop.web.common.quantity.Data;
import com.pmease.gitop.web.page.error.BaseErrorPage;
import com.pmease.gitop.web.page.error.PageExpiredPage;
import com.pmease.gitop.web.page.home.HomePage;
import com.pmease.gitop.web.shiro.LoginPage;
import com.pmease.gitop.web.shiro.LogoutPage;
import com.pmease.gitop.web.shiro.ShiroWicketPlugin;

import de.agilecoders.wicket.core.Bootstrap;
import de.agilecoders.wicket.core.settings.BootstrapSettings;

@Singleton
public class GitopWebApp extends AbstractWicketConfig {
	private static final Duration DEFAULT_TIMEOUT = Duration.minutes(10);
	
	private Date startupDate;
	private byte[] defaultUserAvatar;

	public static GitopWebApp get() {
		return (GitopWebApp) Application.get();
	}

	public Date getStartupDate() {
		return startupDate;
	}

	public Duration getUptime() {
		Date start = getStartupDate();
		if (start == null) {
			return Duration.milliseconds(0);
		}

		return Duration.elapsed(Time.valueOf(start));
	}

	@Override
	public Class<? extends Page> getHomePage() {
		return HomePage.class;
	}

	@Override
	public Session newSession(Request request, Response response) {
		return new GitopSession(request);
	}

	@Override
	public WebRequest newWebRequest(HttpServletRequest servletRequest, String filterPath) {
		return new ServletWebRequest(servletRequest, filterPath) {

			@Override
			public boolean shouldPreserveClientUrl() {
				if (RequestCycle.get().getActiveRequestHandler() instanceof RenderPageRequestHandler) {
					RenderPageRequestHandler requestHandler = 
							(RenderPageRequestHandler) RequestCycle.get().getActiveRequestHandler();
					
					/*
					 *  Add this to make sure that the page url does not change upon errors, so that 
					 *  user can know which page is actually causing the error. This behavior is common
					 *  for main stream applications.   
					 */
					if (requestHandler.getPage() instanceof BaseErrorPage)
						return true;
				}
				return super.shouldPreserveClientUrl();
			}
			
		};
	}

	@Override
	protected void init() {
		this.startupDate = new Date();

		super.init();

		getMarkupSettings().setDefaultMarkupEncoding("UTF-8");

		getRequestCycleSettings().setTimeout(DEFAULT_TIMEOUT);
		
		getResourceSettings().setCachingStrategy(new FilenameWithVersionResourceCachingStrategy(new LastModifiedResourceVersion()));

		getRequestCycleListeners().add(new WicketRequestCycleListener());
		
		getApplicationSettings().setPageExpiredErrorPage(PageExpiredPage.class);
		
		// wicket bean validation
		new BeanValidationConfiguration().configure(this);

		loadDefaultUserAvatarData();

		new ShiroWicketPlugin()
				.mountLoginPage("login", LoginPage.class)
				.mountLogoutPage("logout", LogoutPage.class)
				.install(this);
		
		BootstrapSettings bootstrapSettings = new BootstrapSettings();
		bootstrapSettings.setAutoAppendResources(false);
		Bootstrap.install(this, bootstrapSettings);

		configureResources();
		
		// mount all pages and resources
		mount(new GitopMappings(this));
		
		if (usesDevelopmentConfig()) {
			getComponentPreOnBeforeRenderListeners().add(new StatelessChecker());
		}
		
		initGitConfig();
	}

	private void initGitConfig() {
		WindowCacheConfig cfg = new WindowCacheConfig();
        cfg.setStreamFileThreshold((int) Data.ONE_MB * 10);
        cfg.install();
	}
	
	public byte[] getDefaultUserAvatar() {
		return defaultUserAvatar;
	}
	
	private void loadDefaultUserAvatarData() {
		InputStream in = null;
		try {
			in = AssetLocator.class.getResourceAsStream("img/empty-avatar.jpg");
			defaultUserAvatar = ByteStreams.toByteArray(in);
		} catch (IOException e) {
			throw Throwables.propagate(e);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	private void configureResources() {
		final IPackageResourceGuard packageResourceGuard = getResourceSettings().getPackageResourceGuard();

        if (packageResourceGuard instanceof SecurePackageResourceGuard) {
            SecurePackageResourceGuard guard = (SecurePackageResourceGuard) packageResourceGuard;
            guard.addPattern("+*.woff");
            guard.addPattern("+*.eot");
            guard.addPattern("+*.svg");
            guard.addPattern("+*.ttf");
        }
	}
	
	public Iterable<IRequestMapper> getRequestMappers() {
		return getRootRequestMapperAsCompound();
	}
}
