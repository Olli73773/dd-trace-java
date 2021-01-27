/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.instrumentation.api.db.QueryNormalizationConfig.isQueryNormalizationEnabled;

import io.opentelemetry.javaagent.instrumentation.api.db.SqlSanitizer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CouchbaseQueryNormalizer {
  private static final boolean NORMALIZATION_ENABLED =
      isQueryNormalizationEnabled("couchbase", "couchbase-2.0");

  private static final Class<?> QUERY_CLASS;
  private static final Class<?> STATEMENT_CLASS;
  private static final Class<?> N1QL_QUERY_CLASS;
  private static final MethodHandle N1QL_GET_STATEMENT;
  private static final Class<?> ANALYTICS_QUERY_CLASS;
  private static final MethodHandle ANALYTICS_GET_STATEMENT;

  static {
    Class<?> queryClass;
    try {
      queryClass = Class.forName("com.couchbase.client.java.query.Query");
    } catch (Exception e) {
      queryClass = null;
    }
    QUERY_CLASS = queryClass;

    Class<?> statementClass;
    try {
      statementClass = Class.forName("com.couchbase.client.java.query.Statement");
    } catch (Exception e) {
      statementClass = null;
    }
    STATEMENT_CLASS = statementClass;

    Class<?> n1qlQueryClass;
    MethodHandle n1qlGetStatement;
    try {
      n1qlQueryClass = Class.forName("com.couchbase.client.java.query.N1qlQuery");
      n1qlGetStatement =
          MethodHandles.publicLookup()
              .findVirtual(
                  n1qlQueryClass,
                  "statement",
                  MethodType.methodType(
                      Class.forName("com.couchbase.client.java.query.Statement")));
    } catch (Exception e) {
      n1qlQueryClass = null;
      n1qlGetStatement = null;
    }
    N1QL_QUERY_CLASS = n1qlQueryClass;
    N1QL_GET_STATEMENT = n1qlGetStatement;

    Class<?> analyticsQueryClass;
    MethodHandle analyticsGetStatement;
    try {
      analyticsQueryClass = Class.forName("com.couchbase.client.java.analytics.AnalyticsQuery");
      analyticsGetStatement =
          MethodHandles.publicLookup()
              .findVirtual(analyticsQueryClass, "statement", MethodType.methodType(String.class));
    } catch (Exception e) {
      analyticsQueryClass = null;
      analyticsGetStatement = null;
    }
    ANALYTICS_QUERY_CLASS = analyticsQueryClass;
    ANALYTICS_GET_STATEMENT = analyticsGetStatement;
  }

  public static String normalize(Object query) {
    if (query instanceof String) {
      return normalizeString((String) query);
    }
    // Query is present in Couchbase [2.0.0, 2.2.0)
    // Statement is present starting from Couchbase 2.1.0
    if (QUERY_CLASS != null && QUERY_CLASS.isAssignableFrom(query.getClass())
        || STATEMENT_CLASS != null && STATEMENT_CLASS.isAssignableFrom(query.getClass())) {
      return normalizeString(query.toString());
    }
    // SpatialViewQuery is present starting from Couchbase 2.1.0
    String queryClassName = query.getClass().getName();
    if (queryClassName.equals("com.couchbase.client.java.view.ViewQuery")
        || queryClassName.equals("com.couchbase.client.java.view.SpatialViewQuery")) {
      return query.toString();
    }
    // N1qlQuery is present starting from Couchbase 2.2.0
    if (N1QL_QUERY_CLASS != null && N1QL_QUERY_CLASS.isAssignableFrom(query.getClass())) {
      String statement = getStatementString(N1QL_GET_STATEMENT, query);
      if (statement != null) {
        return normalizeString(statement);
      }
    }
    // AnalyticsQuery is present starting from Couchbase 2.4.3
    if (ANALYTICS_QUERY_CLASS != null && ANALYTICS_QUERY_CLASS.isAssignableFrom(query.getClass())) {
      String statement = getStatementString(ANALYTICS_GET_STATEMENT, query);
      if (statement != null) {
        return normalizeString(statement);
      }
    }
    return query.getClass().getSimpleName();
  }

  private static String getStatementString(MethodHandle handle, Object query) {
    if (handle == null) {
      return null;
    }
    try {
      return handle.invoke(query).toString();
    } catch (Throwable throwable) {
      return null;
    }
  }

  private static String normalizeString(String query) {
    if (!NORMALIZATION_ENABLED || query == null) {
      return query;
    }
    return SqlSanitizer.sanitize(query).getFullStatement();
  }

  private CouchbaseQueryNormalizer() {}
}