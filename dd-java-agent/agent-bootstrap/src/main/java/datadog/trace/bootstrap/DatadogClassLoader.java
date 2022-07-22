package datadog.trace.bootstrap;

import datadog.trace.util.ClassNameTrie;

import java.net.URL;
import java.util.jar.JarFile;

/** Provides access to Datadog internal classes. */
public final class DatadogClassLoader extends ClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final BootstrapProxy bootstrapProxy;

  public DatadogClassLoader(ClassLoader parent,
                            BootstrapProxy bootstrapProxy,
                            String[] sections,
                            ClassNameTrie jarIndex,
                            JarFile jarFile) {
    super(parent);
    this.bootstrapProxy = bootstrapProxy;
  }

  @Override
  public URL getResource(String resourceName) {
    URL bootstrapResource = bootstrapProxy.getResource(resourceName);
    if (null != bootstrapResource) {
      return bootstrapResource;
    }
    return super.getResource(resourceName);
  }

  public ClassLoader getBootstrapProxy() {
    return bootstrapProxy;
  }

  @Override
  public String toString() {
    return "datadog";
  }


}
