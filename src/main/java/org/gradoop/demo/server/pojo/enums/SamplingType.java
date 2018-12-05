package org.gradoop.demo.server.pojo.enums;

public class SamplingType {
  public enum Type {
    PAGE_RANK,
    EDGE,
    LIMITED_DEGREE_VERTEX,
    NON_UNIFORM_VERTEX,
    VERTEX_EDGE,
    VERTEX_NEIGHBORHOOD,
    VERTEX
  }

  private Type type;

  public SamplingType(String typeString) {
    switch (typeString) {
      case "page_rank":
        this.type = Type.PAGE_RANK;
        break;
      case "edge":
        this.type = Type.EDGE;
        break;
      case "limited_degree_vertex":
        this.type = Type.LIMITED_DEGREE_VERTEX;
        break;
      case "non_uniform_vertex":
        this.type = Type.NON_UNIFORM_VERTEX;
        break;
      case "vertex_edge":
        this.type = Type.VERTEX_EDGE;
        break;
      case "vertex_neighborhood":
        this.type = Type.VERTEX_NEIGHBORHOOD;
        break;
      case "vertex":
        this.type = Type.VERTEX;
        break;
      default:
        throw new IllegalArgumentException(
          "Sampling type '" + typeString + "' was not recognized.");
    }
  }

  public Type getType() {
    return this.type;
  }
}
