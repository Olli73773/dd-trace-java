package datadog.trace.bootstrap;

import datadog.trace.util.ClassNameTrie;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
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

  private final String[] sections;

  private final ClassNameTrie jarIndex;

  private final JarFile jarFile;

  public DatadogClassLoader(
      ClassLoader parent,
      BootstrapProxy bootstrapProxy,
      String[] sections,
      ClassNameTrie jarIndex,
      JarFile jarFile) {
    super(parent);
    this.bootstrapProxy = bootstrapProxy;
    this.sections = sections;
    this.jarIndex = jarIndex;
    this.jarFile = jarFile;
  }

  @Override
  public URL getResource(String name) {
    URL bootstrapResource = bootstrapProxy.getResource(name);
    if (null != bootstrapResource) {
      return bootstrapResource;
    }
    return super.getResource(name);
  }

  public ClassLoader getBootstrapProxy() {
    return bootstrapProxy;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    int sectionIndex = jarIndex.apply(name);
    if (sectionIndex >= 0) {
      String path = sections[sectionIndex] + name.replace('.', '/') + ".classdata";
      ZipEntry jarEntry = jarFile.getEntry(path);
      if (null != jarEntry) {
        if (jarEntry.getSize() < MAX_CLASSDATA_SIZE) {
          byte[] buf = new byte[(int) jarEntry.getSize()];
          try (InputStream in = jarFile.getInputStream(jarEntry)) {
            if (in.read(buf) == buf.length) {
              return defineClass(name, buf, 0, buf.length);
            } else {
              log.warn("Malformed class data at {}", path);
            }
          } catch (IOException e) {
            log.warn("Problem reading class data at {}", path, e);
          }
        } else {
          log.warn("Unexpected class data size at {}", path);
        }
      }
    }
    throw new ClassNotFoundException(name);
  }

  @Override
  public String toString() {
    return "datadog";
  }
}
