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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.yammer.dropwizard.jersey.caching.CacheControl;
import com.yammer.metrics.annotation.Timed;

@Path("/developer_workload")
@Produces(MediaType.APPLICATION_JSON)
public class DeveloperWorkload {

	private static final int CACHE_DURATION = 1;
	private String username;
	private String password;
	private Cache<String, Map<String, Map<String, Long>>> requestCache;

	//number of days to plot back (here we are only interested in the current state of affairs)
	public static final int DAYS_BACK = 1;
	public static final int VERSIONS_BACK = 5;

	
	public DeveloperWorkload(String username, String password)
	{
		this.username = username;
		this.password = password;

		this.requestCache = CacheBuilder.newBuilder()
				.maximumSize(1000)
				.expireAfterWrite(CACHE_DURATION, TimeUnit.HOURS)
				.build();
	}


	@GET
	@Timed
	@CacheControl(maxAge = CACHE_DURATION, maxAgeUnit = TimeUnit.HOURS)
	public Map<String, Map<String, Long>> doit() throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		if (requestCache.getIfPresent("developer_workload") != null)
		{
			return requestCache.getIfPresent("developer_workload");
		}

		WebClient webClient = Common.getWebClient();

		HtmlPage initialPage = Common.login(webClient, this.username, this.password);
		Common.switchToProject("210", initialPage);

		// Issues in versies over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_version = new HashMap<Integer, TreeMap<DateTime, String>>();

		// Status van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_status = new HashMap<Integer, TreeMap<DateTime, String>>();

		// 'Assigned to' van issue over tijd
		Map<Integer, TreeMap<DateTime, String>> issue_assigned_to = new HashMap<Integer, TreeMap<DateTime, String>>();

		List<HtmlOption> recent = Common.getVersions(webClient).subList(2, 2+VERSIONS_BACK);
		ImmutableList<String> discardStates = ImmutableList.of("resolved", "closed");
		HashMap<String, Map<String, Long>> result = new HashMap<String, Map<String, Long>>();

		
		for(HtmlOption version : recent)
		{
			List<Integer> issues = Common.getIssues(webClient, version.asText());

			Common.extractIssueStates(webClient, issues, issue_version, issue_status, issue_assigned_to);

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
						
						if (entry == null || !discardStates.contains(entry.getValue()))
						{
							if (!result.get(developer).containsKey(version.asText()))
								result.get(developer).put(versionString, 0l);
						
							result.get(developer).put(versionString, result.get(developer).get(versionString)+1);
							  
							if (developer.equals("tkupers")) System.out.println("Open issues for tkuper: " + issue);
						}

				}
				
			}
		}

		requestCache.put("developer_workload", result);
		return result;
	}
}