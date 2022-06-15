package datadog.remote_config.tuf;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okio.Okio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles responses from Remote Configuration */
public class RemoteConfigResponse {
  private static final Logger log = LoggerFactory.getLogger(RemoteConfigResponse.class);

  @Json(name = "client_configs")
  public List<String> clientConfigs;

  @Json(name = "targets")
  private String targetsJson;

  private transient Targets targets;
  private List<TargetFile> targetFiles;

  public static class Factory {
    private final JsonAdapter<RemoteConfigResponse> adapterRC;
    private final JsonAdapter<Targets> adapterTargets;

    public Factory(Moshi moshi) {
      this.adapterRC = moshi.adapter(RemoteConfigResponse.class);
      this.adapterTargets = moshi.adapter(Targets.class);
    }

    public Optional<RemoteConfigResponse> fromInputStream(InputStream inputStream) {
      try {
        RemoteConfigResponse response = adapterRC.fromJson(Okio.buffer(Okio.source(inputStream)));
        String targetsJsonBase64 = response.targetsJson;
        if (targetsJsonBase64 == null) {
          return Optional.empty(); // empty response -- no change
        }
        byte[] targetsJsonDecoded =
            Base64.getDecoder().decode(targetsJsonBase64.getBytes(StandardCharsets.ISO_8859_1));
        response.targets =
            (targetsJsonDecoded.length > 0)
                ? adapterTargets.fromJson(Okio.buffer(Okio.source(new ByteArrayInputStream(targetsJsonDecoded))))
                : null;
        response.targetsJson = null;
        return Optional.of(response);
      } catch (IOException e) {
        throw new RuntimeException("Failed to parse fleet response: " + e.getMessage(), e);
      }
    }
  }

  public Targets.ConfigTarget getTarget(String configKey) {
    return this.targets.targetsSigned.targets.get(configKey);
  }

  public Targets.TargetsSigned getTargetsSigned() {
    return this.targets.targetsSigned;
  }

  public Optional<byte[]> getFileContents(String filePath) {
    if (targetFiles != null) {
      try {
        for (TargetFile targetFile : this.targetFiles) {
          if (filePath.equals(targetFile.path)) {
            String raw = (String) targetFile.raw;
            return Optional.of(Base64.getDecoder().decode(raw));
          }
        }
      } catch (Exception exception) {
        throw new IntegrityCheckException(
            "Could not get file contents from fleet response", exception);
      }
    }
    return Optional.empty();
  }

  public List<String> getClientConfigs() {
    return this.clientConfigs != null ? this.clientConfigs : Collections.emptyList();
  }

  public static class Targets {
    public List<Signature> signatures;
    @Json(name = "signed")
    public TargetsSigned targetsSigned;

    public static class Signature {
      @Json(name = "keyid")
      public String keyId;

      @Json(name = "signature")
      public String signature;
    }

    public static class TargetsSigned {
      @Json(name = "_type")
      public String type;
      public TargetsCustom custom;
      public Instant expires;
      @Json(name = "spec_version")
      public String specVersion;
      public long version;
      public Map<String, ConfigTarget> targets;

      public static class TargetsCustom {
        @Json(name = "client_state")
        public ClientState clientState;
        public static class ClientState {
          @Json(name = "file_hashes")
          public List<String> fileHashes;
        }
      }
    }

    public static class ConfigTarget {
      public ConfigTargetCustom custom;
      public Map<String, String> hashes;
      public long length;

      public static class ConfigTargetCustom {
        @Json(name = "v")
        public long version;
      }
    }
  }

  public static class TargetFile {
    public String path;
    public String raw;
  }
}
