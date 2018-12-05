package org.gradoop.demo.server.pojo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SamplingRequest {

  public enum SamplingType {
    PAGE_RANK,
    EDGE,
    LIMITED_DEGREE_VERTEX,
    NON_UNIFORM_VERTEX,
    VERTEX_EDGE,
    VERTEX_NEIGHBORHOOD,
    VERTEX
  }

  private String dbName;
  private SamplingType samplingType;
  private Map<String, String> properties = new HashMap<>();

  public void setDbName(String dbName) {
    this.dbName = dbName;
  }

  public void setSamplingType(String typeString) {
    switch (typeString) {
      case "page_rank":
        this.samplingType = SamplingType.PAGE_RANK;
        break;
      case "edge":
        this.samplingType = SamplingType.EDGE;
        break;
      case "limited_degree_vertex":
        this.samplingType = SamplingType.LIMITED_DEGREE_VERTEX;
        break;
      case "non_uniform_vertex":
        this.samplingType = SamplingType.NON_UNIFORM_VERTEX;
        break;
      case "vertex_edge":
        this.samplingType = SamplingType.VERTEX_EDGE;
        break;
      case "vertex_neighborhood":
        this.samplingType = SamplingType.VERTEX_NEIGHBORHOOD;
        break;
      case "vertex":
        this.samplingType = SamplingType.VERTEX;
        break;
      default:
        throw new IllegalArgumentException(
          "Sampling type '" + typeString + "' was not recognized.");
    }
  }

  public void setProperties(String[] propertyStrings) {
    this.properties.clear();

    Arrays.stream(propertyStrings).forEach(System.out::println);

    String[] propertyArray;
    for (String propertyString : propertyStrings) {
      propertyArray = propertyString.split(":");
      if (propertyArray.length == 2) {
        this.properties.put(propertyArray[0], propertyArray[1]);
      } else {
        throw new IllegalArgumentException("Property strings must contain exactly one ':'.");
      }
    }
  }

  public String getDbName() {
    return this.dbName;
  }

  public SamplingType getSamplingType() {
    return this.samplingType;
  }

  public String getProperty(String key) {
    return this.properties.get(key);
  }

}
