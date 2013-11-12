package nl.topicus.djodd.stats;
import org.codehaus.jackson.annotate.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import com.yammer.dropwizard.config.Configuration;


public class MantisConfiguration extends Configuration {

	
	@NotEmpty
    @JsonProperty
    private String username;

    @NotEmpty
    @JsonProperty
    private String password;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
