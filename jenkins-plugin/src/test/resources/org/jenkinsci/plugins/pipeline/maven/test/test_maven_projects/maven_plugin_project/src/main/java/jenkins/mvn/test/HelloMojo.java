package jenkins.mvn.test;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "hello")
public class HelloMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException {
        getLog().info( "Hello, world." );
    }
}

