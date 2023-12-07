package com.dotcms.api.client.pull.site;

import com.dotcms.api.SiteAPI;
import com.dotcms.api.client.model.RestClientFactory;
import com.dotcms.api.client.pull.ContentFetcher;
import com.dotcms.model.ResponseEntityView;
import com.dotcms.model.site.GetSiteByNameRequest;
import com.dotcms.model.site.Site;
import com.dotcms.model.site.SiteView;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;

@Dependent
public class SiteFetcher implements ContentFetcher<SiteView>, Serializable {

    private static final long serialVersionUID = 1082298802098576444L;

    @Inject
    protected RestClientFactory clientFactory;

    @ActivateRequestContext
    @Override
    public List<SiteView> fetch(Map<String, Object> customOptions) {

        // Fetching the all the existing sites
        final List<Site> allSites = new ArrayList<>();

        final SiteIterator siteIterator = new SiteIterator(clientFactory, 100);
        while (siteIterator.hasNext()) {
            List<Site> sites = siteIterator.next();
            allSites.addAll(sites);
        }

        // Create a ForkJoinPool to process the sites in parallel
        // We need this extra logic because the site API returns when calling all sites an object
        // that is not equal to the one returned when calling by id or by name, it is a reduced and
        // different version of a site, so we need to call the API for each site to get the full object.
        var forkJoinPool = ForkJoinPool.commonPool();
        var task = new HttpRequestTask(allSites, this);
        return forkJoinPool.invoke(task);
    }

    @ActivateRequestContext
    @Override
    public SiteView fetchByKey(String siteNameOrId, Map<String, Object> customOptions)
            throws NotFoundException {

        final var siteAPI = clientFactory.getClient(SiteAPI.class);

        if (siteNameOrId.replace("-", "").matches("[a-fA-F0-9]{32}")) {
            final ResponseEntityView<SiteView> byId = siteAPI.findById(siteNameOrId);
            return byId.entity();
        }

        final ResponseEntityView<SiteView> byId = siteAPI.findByName(
                GetSiteByNameRequest.builder().siteName(siteNameOrId).build());
        return byId.entity();
    }

}