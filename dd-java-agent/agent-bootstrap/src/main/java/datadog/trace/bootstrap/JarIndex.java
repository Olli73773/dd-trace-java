package datadog.trace.bootstrap;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarIndex {
  private final String[] sections;
  private int lastIndex = 0;

  public JarIndex(String[] sections) {
    this.sections = sections;
  }

  public JarEntry lookup(JarFile jarFile, String name) {
    int index = lastIndex;
    JarEntry jarEntry = jarFile.getJarEntry(sections[index] + name);
    if (null != jarEntry) {
      return jarEntry;
    }
    for (int i = 0; i < index; i++) {
      jarEntry = jarFile.getJarEntry(sections[i] + name);
      if (null != jarEntry) {
        lastIndex = i;
        return jarEntry;
      }
    }
    for (int i = index + 1; i < sections.length; i++) {
      jarEntry = jarFile.getJarEntry(sections[i] + name);
      if (null != jarEntry) {
        lastIndex = i;
        return jarEntry;
      }
    }
    return null;
  }
}
