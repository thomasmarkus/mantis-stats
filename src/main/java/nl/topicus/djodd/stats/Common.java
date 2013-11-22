package nl.topicus.djodd.stats;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class Common {

	public static ImmutableList<String> discardStates = ImmutableList.of("resolved", "closed", "afgemeld", "afgesloten");

	
	public static HtmlPage switchToProject(String project, HtmlPage currentPage) throws IOException
	{
		HtmlForm form = currentPage.getFormByName("form_set_project");
		
		HtmlSubmitInput submit;
		try
		{
			submit = form.getInputByValue("Switch");	
		}
		catch(ElementNotFoundException e)
		{
			submit = form.getInputByValue("Wisselen");
		}
		
		
		HtmlSelect select = form.getSelectByName("project_id");
		select.setSelectedAttribute("210", true);

		return submit.click();
	}

	public static HtmlPage login(WebClient webClient, String username, String password) throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		final HtmlPage page = webClient.getPage("https://bugs.topicus.nl");
		final HtmlForm form = page.getFormByName("login_form");

		final HtmlSubmitInput button = form.getInputByValue("Login");
		final HtmlTextInput Iusername = form.getInputByName("username");
		final HtmlPasswordInput Ipassword = form.getInputByName("password");

		// Change the value of the text field
		Iusername.setValueAttribute(username);
		Ipassword.setValueAttribute(password);

		// Now submit the form by clicking the button and get back the second page.
		return button.click();
	}
	
	public static List<HtmlOption> getVersions(WebClient webClient) throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		HtmlPage info_page = webClient.getPage("https://bugs.topicus.nl/view_filters_page.php?for_screen=1&target_field=target_version[]");
		HtmlForm filters = info_page.getFormByName("filters");

		HtmlSelect target_version = filters.getSelectByName("target_version[]");
		List<HtmlOption> versions = target_version.getOptions();

		return versions;
	}

	protected static void extractIssueStates(final WebClient webClient, List<Integer> issues, Map<Integer, TreeMap<DateTime, String>> issue_version, Map<Integer, TreeMap<DateTime, String>> issue_status, Map<Integer, TreeMap<DateTime, String>> issue_assigned_to)
			throws IOException, MalformedURLException {

		int index = 1;
		for(Integer issue : issues)
		{
			HtmlPage page = getIssueHistoryPage(issue, webClient);

			issue_version.put(issue, getIssueHistory(issue, page, "Target Version"));
			issue_status.put(issue, getIssueHistory(issue, page, "Status"));
			issue_assigned_to.put(issue, getIssueHistory(issue, page, "Assigned To"));
		
			index++;
			
			System.out.println(String.format("Progress %f", ((float) index / issues.size()) * 100));
		}
	}


	public static HtmlPage getIssueHistoryPage(int id, WebClient web) throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		System.out.println("Getting issues history for: " + id);

		HtmlPage page = web.getPage("https://bugs.topicus.nl/view.php?id="+id);

		return page;
	}

	public static TreeMap<DateTime, String> getIssueHistory(int id, HtmlPage page, String desiredType) throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		TreeMap<DateTime, String> result = new TreeMap<DateTime, String>();
		
		//parse 'date submitted' en 'target version', 'Status' fields
		final DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");

		DomElement info_table = page.getElementsByTagName("table").get(2);
		DateTime date = fmt.parseDateTime(info_table.getElementsByTagName("tr").get(2).getElementsByTagName("td").get(4).asText()); //first submitted
		String status = info_table.getElementsByTagName("tr").get(7).getElementsByTagName("td").get(1).asText();
		String version = info_table.getElementsByTagName("tr").get(10).getElementsByTagName("td").get(1).asText().replaceAll("\\[.*\\] ", "");
		String assigned_to = info_table.getElementsByTagName("tr").get(5).getElementsByTagName("td").get(1).asText().trim();
		
		if (desiredType.equals("Status"))
		{
			result.put(date, "New");
		}
		else if (desiredType.equals("Target Version"))
		{
			result.put(date, version);
		}
		else if (desiredType.equals("Assigned To"))
		{
			result.put(date, assigned_to);
		}
		
		
		//loop de history tabel door
		DomElement history = page.getElementById("history_open");

		if (history != null)
		{

			for(HtmlElement row : history.getElementsByTagName("tr"))
			{
				ArrayList<DomElement> columns = Lists.newArrayList(row.getChildElements());
				if (columns.size() == 4 && 
						!columns.get(0).getTextContent().contains("Date Modified") &&
						!columns.get(0).getTextContent().contains("Gewijzigd op")
						)
				{
					final DateTime timestamp = fmt.parseDateTime(columns.get(0).getTextContent().trim());
					final String user = columns.get(1).getTextContent().trim();
					final String type = columns.get(2).getTextContent().trim();
					final String value = columns.get(3).getTextContent().trim().replaceAll(".*=\\>", "").trim();

					if (type.equals((desiredType)))
					{
						//System.out.println(timestamp + " : " + type +  " = " + value);
						result.put(timestamp, value);
					}
				}
			}
		}
		else
		{
			System.out.println(String.format("History info div not available in page for issue %s", id));
		}

		return result;
	}

	/**
	 * Get the release versions, WARNING: only works with certain Mantis accounts
	 * @param webClient
	 * @return
	 * @throws IOException 
	 * @throws MalformedURLException 
	 * @throws FailingHttpStatusCodeException 
	 */
	
	public static Map<String, LocalDate> getReleaseDates(WebClient webClient, String project_id) throws FailingHttpStatusCodeException, MalformedURLException, IOException
	{
		Map<String, LocalDate> result = new HashMap<String, LocalDate>();
		HtmlPage page = webClient.getPage("https://bugs.topicus.nl/manage_proj_edit_page.php?project_id="+project_id);
		DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
		
		HtmlAnchor a = page.getAnchorByName("versions");
		
		List<HtmlElement> rows = a.getHtmlElementsByTagName("tr");
		
		for(HtmlElement row : rows)
		{
			List<HtmlElement> cells = row.getHtmlElementsByTagName("td");
		
			if (cells.size() == 5 && !cells.get(0).asText().equals("Versie") && !cells.get(0).asText().equals("Version"))
			{
				String version = cells.get(0).asText();
				LocalDate date = formatter.parseLocalDate(cells.get(3).asText().substring(0, 10));
				result.put(version, date);
			}
		}

		return result;
	}
	
	
	public static List<Integer> getIssues(WebClient webClient, String desiredVersion) throws IOException
	{
		System.out.println("Getting issues for version : " + desiredVersion);

		HtmlPage info_page = webClient.getPage("https://bugs.topicus.nl/view_filters_page.php?for_screen=1&target_field=target_version[]");
		HtmlForm filters = info_page.getFormByName("filters");
		HtmlSelect target_version = filters.getSelectByName("target_version[]");

		List<HtmlOption> versions = target_version.getOptions();
		HtmlOption version = null;
		for(HtmlOption curVersion : versions)
		{
			if (curVersion.asText().equals(desiredVersion)) version = curVersion;
		}

		HtmlSubmitInput submit = filters.getInputByName("filter");
		target_version.setSelectedAttribute(version, true);

		HtmlInput per_page = filters.getInputByName("per_page");
		per_page.setValueAttribute("5000");


		filters.getSelectByName("custom_field_78[]").setSelectedAttribute("0", true); //knownbug
		filters.getSelectByName("custom_field_40[]").setSelectedAttribute("0", true); //school
		filters.getSelectByName("custom_field_47[]").setSelectedAttribute("0", true); //SOM omgeving
		filters.getSelectByName("hide_status[]").setSelectedAttribute("-2", true);

		HtmlPage issuesPage = submit.click();
		List<Integer> issues = new ArrayList<Integer>();

		for(HtmlElement sub : issuesPage.getElementById("buglist").getHtmlElementDescendants())
		{
			if (sub.getTagName().equals("a"))
			{
				if (sub.getAttribute("href").startsWith("/view.php?id="))
				{
					issues.add(Integer.parseInt(sub.getTextContent()));
				}
			}
		}

		System.out.println(String.format("Number of issues found: %d", issues.size()));

		return issues;
	}

	public static WebClient getWebClient()
	{
		final WebClient webClient = new WebClient(BrowserVersion.CHROME);
		webClient.getOptions().setUseInsecureSSL(true);
		webClient.getOptions().setJavaScriptEnabled(false);

		return webClient;
	}
	
	public static List<HtmlOption> getVersionsReleasedAfter(Map<String, LocalDate> releaseDates, List<HtmlOption> recent, LocalDate date)
	{
		List<HtmlOption> filtered_recent = new ArrayList<HtmlOption>();	

		for( Entry<String, LocalDate> version_info : releaseDates.entrySet())
		{
			if (version_info.getValue().isAfter(date))
			{
				String version = version_info.getKey();
				for(HtmlOption option : recent)
				{
					if (option.asText().equals(version))
					{
						filtered_recent.add(option);
					}
				}
			}
		}

		return filtered_recent;
	}
	
	 
	
	

}
