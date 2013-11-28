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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yammer.dropwizard.jersey.caching.CacheControl;
import com.yammer.metrics.annotation.Timed;

@Path("/open_issues")
@Produces(MediaType.APPLICATION_JSON)
public class OpenIssues {

	private static final int CACHE_DURATION = 1;
	private String username;
	private String password;
	private String project_id;

	private Cache<String, Map<String, List<Map<String, Long>>>> requestCache;
	
	//number of days to plot back
	private static final int DAYS_BACK = 60;
	private static final int VERSIONS_BACK = 20;

	
	public OpenIssues(String username, String password, String project_id)
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
	public synchronized Map<String, List<Map<String, Long>>> doit() throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		if (requestCache.getIfPresent("open_issues") != null)
		{
			return requestCache.getIfPresent("open_issues");
		}
		
		final WebClient webClient = Common.getWebClient();

		HtmlPage initialPage = Common.login(webClient, this.username, this.password);
		Common.switchToProject(project_id, initialPage);

		// Issues in versies over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_version = new HashMap<Integer, TreeMap<DateTime, String>>();

		// Status van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_status = new HashMap<Integer, TreeMap<DateTime, String>>();

		// 'Assigned to' van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_assigned_to = new HashMap<Integer, TreeMap<DateTime, String>>();

		
		List<HtmlOption> recent = Common.getVersions(webClient).subList(2, 2+VERSIONS_BACK);
		Map<String, LocalDate> releaseDates = Common.getReleaseDates(webClient, project_id);
		List<HtmlOption> filtered_recent = Common.getVersionsReleasedAfter(releaseDates, recent, LocalDate.now());
		
		
		DateTime startDate = DateTime.now().minusDays(DAYS_BACK).withHourOfDay(0).withMinuteOfHour(0);

		Map<String, List<Map<String, Long>>> result = new TreeMap<String, List<Map<String, Long>>>();
		
		
		for(HtmlOption version : filtered_recent)
		{
			List<Integer> issues = Common.getIssues(webClient, version.asText());

			Common.extractIssueStates(webClient, issues, issue_version, issue_status, issue_assigned_to);

			result.put(version.asText(), new ArrayList<Map<String, Long>>());
			
			for (DateTime date = startDate; date.isBeforeNow(); date = date.plusDays(1))
			{
				long issues_count = 0;
				for(Entry<Integer, TreeMap<DateTime, String>> issue_version_history_entry : issue_version.entrySet())
				{
					final int issue = issue_version_history_entry.getKey();
					final TreeMap<DateTime, String> issue_version_history = issue_version_history_entry.getValue();
					
					Entry<DateTime, String> entry = issue_version_history.floorEntry(date);

					if (entry != null)
					{
						String was_in_version =	entry.getValue();

						if (version.asText().contains(was_in_version))
						{
							Entry<DateTime, String> state_at_the_time = issue_status.get(issue).floorEntry(date);
							
							if (state_at_the_time == null || !Common.discardStates.contains(state_at_the_time.getValue()))
							{
								issues_count++;	
							}
						}
					}
					else
					{
						//System.err.println(String.format("No date information for date %s for issue %d", date, issue));
					}
				}
				
				HashMap<String, Long> map = new HashMap<String, Long>();
				map.put("clock", date.getMillis());
				map.put("value", issues_count);
				result.get(version.asText()).add(map);
				
				//System.out.println(String.format("Total number of issues on %s was %d", date, issues_count));
			}
		}
	
		requestCache.put("open_issues", result);
		return result;
	}







}
