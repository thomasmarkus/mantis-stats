package nl.topicus.djodd.stats;
import java.io.IOException;
import java.net.MalformedURLException;
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

@Path("/developer_workload")
@Produces(MediaType.APPLICATION_JSON)
public class DeveloperWorkload {

	private static final int CACHE_DURATION = 1;
	private String host;
	private String username;
	private String password;
	private String project_id;
	private Cache<String, Map<String, Map<String, Long>>> requestCache;

	//number of days to plot back (here we are only interested in the current state of affairs)
	public static final int DAYS_BACK = 1;
	public static final int VERSIONS_BACK = 20;

	
	public DeveloperWorkload(String host, String username, String password, String project_id)
	{
		this.host = host;
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
	public synchronized Map<String, Map<String, Long>> doit() throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		if (requestCache.getIfPresent("developer_workload") != null)
		{
			return requestCache.getIfPresent("developer_workload");
		}

		WebClient webClient = Common.getWebClient();

		HtmlPage initialPage = Common.login(webClient, this.host, this.username, this.password);
		Common.switchToProject(project_id, initialPage);

		// Issues in versies over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_version = new HashMap<Integer, TreeMap<DateTime, String>>();

		// Status van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_status = new HashMap<Integer, TreeMap<DateTime, String>>();

		// 'Assigned to' van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_assigned_to = new HashMap<Integer, TreeMap<DateTime, String>>();

		List<HtmlOption> recent = Common.getVersions(webClient, this.host).subList(2, 2+VERSIONS_BACK);
		HashMap<String, Map<String, Long>> result = new HashMap<String, Map<String, Long>>();

		Map<String, LocalDate> releaseDates = Common.getReleaseDates(webClient, this.host, project_id);
		List<HtmlOption> filtered_recent = Common.getVersionsReleasedAfter(releaseDates, recent, LocalDate.now());
		
		for(HtmlOption version : filtered_recent)
		{
			List<Integer> issues = Common.getIssues(webClient, this.host, version.asText());

			Common.extractIssueStates(webClient, this.host, issues, issue_version, issue_status, issue_assigned_to);

			final DateTime date = DateTime.now();
			for(Integer issue : issues)
			{
				final String developer = issue_assigned_to.get(issue).floorEntry(date).getValue();
				
				if (!developer.isEmpty())
				{
					if (!result.containsKey(developer))
					{
						result.put(developer, new HashMap<String, Long>());
					}
					
						Entry<DateTime, String> entry = issue_status.get(issue).floorEntry(date);
						String versionString = version.asText();
						
						if (entry == null || !Common.discardStates.contains(entry.getValue()))
						{
							if (!result.get(developer).containsKey(version.asText()))
								result.get(developer).put(versionString, 0l);
						
							result.get(developer).put(versionString, result.get(developer).get(versionString)+1);
						}
				}
			}
		}

		requestCache.put("developer_workload", result);
		return result;
	}
}
