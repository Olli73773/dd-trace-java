package datadog.trace.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides access to Datadog internal classes. */
public final class DatadogClassLoader extends ClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final Logger log = LoggerFactory.getLogger(DatadogClassLoader.class);

  private static final long MAX_CLASSDATA_SIZE = 2 * 1024 * 1024;

  private final BootstrapProxy bootstrapProxy;

  private final JarIndex jarIndex;

  private final JarFile jarFile;

  public DatadogClassLoader(
      ClassLoader parent, BootstrapProxy bootstrapProxy, JarIndex jarIndex, JarFile jarFile) {
    super(parent);
    this.bootstrapProxy = bootstrapProxy;
    this.jarIndex = jarIndex;
    this.jarFile = jarFile;
  }

  public ClassLoader getBootstrapProxy() {
    return bootstrapProxy;
  }

  @Override
  protected Package[] getPackages() {
    System.err.println("!!!!!!!! GET PACKAGES");new Throwable().printStackTrace();
    return super.getPackages();
  }

  @Override
  protected Package getPackage(String name) {
    System.err.println("!!!!!!!! GET PACKAGE "+name);new Throwable().printStackTrace();
    return super.getPackage(name);
  }

  @Override
  public URL getResource(String name) {
    URL bootstrapResource = bootstrapProxy.getResource(name);
    if (null != bootstrapResource) {
      return bootstrapResource;
    }
    return super.getResource(name);
  }

  @Override
  protected URL findResource(String name) {
    JarEntry jarEntry = jarIndex.lookup(jarFile, name.endsWith(".class") ? name + "data" : name);
    if (null != jarEntry) {
      String location = "jar:file:" + jarFile.getName() + "!/" + jarEntry.getName();
      try {
        return new URL(location);
      } catch (MalformedURLException e) {
        log.warn("Malformed location {}", location);
      }
    }
    return null;
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    URL resource = findResource(name);
    if (null != resource) {
      return Collections.enumeration(Collections.singleton(resource));
    }
    return Collections.emptyEnumeration();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    JarEntry jarEntry = jarIndex.lookup(jarFile, name.replace('.', '/') + ".classdata");
    if (null != jarEntry) {
      if (jarEntry.getSize() < MAX_CLASSDATA_SIZE) {
        byte[] buf = new byte[(int) jarEntry.getSize()];
        try (InputStream in = jarFile.getInputStream(jarEntry)) {
          int bytesRead = in.read(buf);
          while (bytesRead < buf.length) {
            int delta = in.read(buf, bytesRead, buf.length - bytesRead);
            if (delta < 0) {
              break;
            }
            bytesRead += delta;
          }
          if (bytesRead == buf.length) {
            return defineClass(name, buf, 0, buf.length);
          } else {
            log.warn("Malformed class data at {}", jarEntry);
          }
        } catch (IOException e) {
          log.warn("Problem reading class data at {}", jarEntry, e);
        }
      } else {
        log.warn("Unexpected class data size at {}", jarEntry);
      }
    }
    throw new ClassNotFoundException(name);
  }

  @Override
  public String toString() {
    return "datadog";
  }
}
