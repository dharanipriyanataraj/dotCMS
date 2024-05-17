package com.dotcms;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dotcms.company.CompanyAPI;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import org.junit.BeforeClass;

import com.dotcms.contenttype.business.ContentTypeAPI;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.util.BaseMessageResources;
import com.dotmarketing.util.Config;

import java.util.TimeZone;
import org.mockito.Mockito;

public abstract class UnitTestBase extends BaseMessageResources {

	protected static final ContentTypeAPI contentTypeAPI = mock(ContentTypeAPI.class);
	protected static final CompanyAPI companyAPI = mock(CompanyAPI.class);

	public static class MyAPILocator extends APILocator {

		static {
			APILocator.instance = new MyAPILocator();
		}

		@Override
		protected ContentTypeAPI getContentTypeAPIImpl(User user, boolean respectFrontendRoles) {
			return contentTypeAPI;
		}

		@Override
		protected CompanyAPI getCompanyAPIImpl() {
			return companyAPI;
		}

	}

	@BeforeClass
	public static void prepare () throws DotDataException, DotSecurityException, Exception {

		Config.initializeConfig();
		Config.setProperty("API_LOCATOR_IMPLEMENTATION", MyAPILocator.class.getName());
		Config.setProperty("SYSTEM_EXIT_ON_STARTUP_FAILURE", false);

		MyAPILocator.destroyAndForceInit();

		final Company company = mock(Company.class);
		// Not all tests use this, so we need to make it lenient to prevent UnnecessaryStubbingException
		Mockito.lenient().when(company.getTimeZone()).thenReturn(TimeZone.getDefault());
		Mockito.lenient().when(companyAPI.getDefaultCompany()).thenReturn(company);
	}
}
