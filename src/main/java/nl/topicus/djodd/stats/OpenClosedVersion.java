package nl.topicus.djodd.stats;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yammer.dropwizard.jersey.caching.CacheControl;
import com.yammer.metrics.annotation.Timed;

@Path("/issue_stats")
@Produces(MediaType.APPLICATION_JSON)
public class OpenClosedVersion {

	private static final int CACHE_DURATION = 1;
	private String username;
	private String password;
	private String project_id;
	
	private Cache<String, List<Map<String, Object>>> requestCache;
	
	//number of days to plot back
	private static final int VERSIONS_BACK = 20;
	
	public OpenClosedVersion(String username, String password, String project_id)
	{
		this.username = username;
		this.password = password;
		this.project_id = project_id;
		
		this.requestCache = CacheBuilder.newBuilder()
			       .maximumSize(1000)
			       .expireAfterWrite(CACHE_DURATION, TimeUnit.HOURS)
			       .build();
	}
	
	
	@GET
    @Timed
    @CacheControl(maxAge = CACHE_DURATION, maxAgeUnit = TimeUnit.HOURS)
	public synchronized List<Map<String, Object>> doit() throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		if (requestCache.getIfPresent("open_issues") != null)
		{
			return requestCache.getIfPresent("open_issues");
		}
		
		final WebClient webClient = Common.getWebClient();
		
		HtmlPage initialPage = Common.login(webClient, this.username, this.password);
		Common.switchToProject(project_id, initialPage);
		
		List<HtmlOption> recent = Common.getVersions(webClient).subList(2, 2+VERSIONS_BACK);
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		
		Map<String, LocalDate> releaseDates = Common.getReleaseDates(webClient, project_id);
		DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
		List<HtmlOption> filtered_recent = Common.getVersionsReleasedAfter(releaseDates, recent, LocalDate.now());
		
		System.out.println("filtered recent: " + filtered_recent.size());
		
		for(HtmlOption version : filtered_recent)
		{
			// Issues in versies over tijd
			Map<Integer, TreeMap<DateTime, String>> issue_version = new HashMap<Integer, TreeMap<DateTime, String>>();

			// Status van issue over tijd
			Map<Integer, TreeMap<DateTime, String>> issue_status = new HashMap<Integer, TreeMap<DateTime, String>>();

			// 'Assigned to' van issue over tijd
			Map<Integer, TreeMap<DateTime, String>> issue_assigned_to = new HashMap<Integer, TreeMap<DateTime, String>>();
			
			List<Integer> issues = Common.getIssues(webClient, version.asText());
			Common.extractIssueStates(webClient, issues, issue_version, issue_status, issue_assigned_to);
			
			HashMap<String, Object> data = new HashMap<String, Object>();
			
			Integer open = 0;
			Integer resolved = 0;
			Integer closed = 0;
			
			for(Entry<Integer, TreeMap<DateTime, String>> issue_stats : issue_status.entrySet())
			{
				int issue = issue_stats.getKey();
				Entry<DateTime, String> state_now = issue_status.get(issue).floorEntry(DateTime.now());
			
				
				if (state_now == null || !Common.discardStates.contains(state_now.getValue()))
				{
					open++;	
				}
				else if (Common.resolvedStates.contains(state_now.getValue()))
				{
					resolved++;
				}
				else if (Common.closedStates.contains(state_now.getValue()))
				{
					closed++;
				}
			}
		
			data.put("version", version.asText());
			data.put("issuesOpen", open);
			data.put("issuesResolved", resolved);
			data.put("issuesClosed", closed);
			data.put("releaseDate", formatter.print(releaseDates.get(version.asText()).toDateTimeAtCurrentTime()));
		
			result.add(data);
		}
	
		requestCache.put("open_issues", result);
		return result;
	}

}
