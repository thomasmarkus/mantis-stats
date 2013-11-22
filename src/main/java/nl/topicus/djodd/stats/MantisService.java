package nl.topicus.djodd.stats;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.assets.AssetsBundle;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;


public class MantisService extends Service<MantisConfiguration> {

	@Override
	public void initialize(Bootstrap<MantisConfiguration> bootstrap) {
		bootstrap.setName("MantisService");
	
		bootstrap.addBundle(new AssetsBundle("/assets/", "/"));
	}

	@Override
	public void run(MantisConfiguration configuration, Environment env)
			throws Exception {
	
		final String username = configuration.getUsername();
	    final String password = configuration.getPassword();
	    final String project_id = configuration.getProject_id();
	    
	    env.addResource(new OpenIssues(username, password, project_id));
	    env.addResource(new DeveloperWorkload(username, password, project_id));
	    env.addResource(new OpenClosedVersion(username, password, project_id));
	}

	public static void main(String[] args) throws Exception {
        new MantisService().run(args);
    
	
	}

	
}
